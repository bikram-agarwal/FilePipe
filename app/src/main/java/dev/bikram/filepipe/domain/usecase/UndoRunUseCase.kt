package dev.bikram.filepipe.domain.usecase

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.FileOperationRepository
import dev.bikram.filepipe.data.repository.RunHistoryRepository
import dev.bikram.filepipe.data.repository.documentIdWithoutTrailingSegments
import dev.bikram.filepipe.data.repository.fileWithoutTrailingSegments
import dev.bikram.filepipe.data.repository.isSafFolderEmpty
import dev.bikram.filepipe.data.repository.normalizeDestinationParentSegments
import dev.bikram.filepipe.data.repository.parentDocumentIdOrNull
import dev.bikram.filepipe.data.storage.isFilesystemAccessEffective
import dev.bikram.filepipe.data.storage.normalizeFilesystemFolderPath
import dev.bikram.filepipe.devtools.DevMockFileMove
import dev.bikram.filepipe.di.IoDispatcher
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.FileUndoStatus
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.hasRecoverableDestination
import dev.bikram.filepipe.domain.model.isEffectivelyUndone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class UndoResult(
    val totalReversed: Int,
    val totalFailed: Int,
    val errors: List<String>,
    val operationMode: OperationMode = OperationMode.MOVE,
    val isAlreadyInProgress: Boolean = false,
)

data class UndoProgress(
    val processedFiles: Int,
    val totalFiles: Int,
    val processedBytes: Long,
    val totalBytes: Long,
)

private const val TAG = "UndoRunUseCase"

@Singleton
class UndoRunUseCase
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val runHistoryRepository: RunHistoryRepository,
        private val fileOperationRepository: FileOperationRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val _activeUndoProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
        val activeUndoProgress: StateFlow<Map<Long, Float>> = _activeUndoProgress.asStateFlow()
        private val undoMutex = Mutex()

        fun isUndoInProgress(historyId: Long): Boolean = _activeUndoProgress.value.containsKey(historyId)

        fun getUndoProgress(historyId: Long): Float? = _activeUndoProgress.value[historyId]

        suspend operator fun invoke(
            historyId: Long,
            onProgress: (UndoProgress) -> Unit = {},
        ): UndoResult =
            withContext(ioDispatcher) {
                if (!undoMutex.tryLock()) {
                    return@withContext UndoResult(
                        totalReversed = 0,
                        totalFailed = 0,
                        errors = emptyList(),
                        isAlreadyInProgress = true,
                    )
                }
                _activeUndoProgress.update { it + (historyId to 0f) }
                try {
                    performUndo(historyId, onProgress)
                } finally {
                    try {
                        withContext(NonCancellable) {
                            syncRunUndoStatus(historyId)
                        }
                    } finally {
                        _activeUndoProgress.update { it - historyId }
                        undoMutex.unlock()
                    }
                }
            }

        private suspend fun performUndo(
            historyId: Long,
            onProgress: (UndoProgress) -> Unit,
        ): UndoResult {
            val history =
                runHistoryRepository.getHistoryById(historyId)
                    ?: return UndoResult(0, 0, listOf("Run not found"))

            if (history.isEffectivelyUndone()) {
                return UndoResult(
                    0,
                    0,
                    listOf("This run has already been undone"),
                    operationMode = history.operationMode,
                )
            }

            val operationMode = history.operationMode
            if (operationMode == OperationMode.DELETE) {
                return UndoResult(
                    0,
                    0,
                    listOf("Delete operations cannot be undone"),
                    operationMode = OperationMode.DELETE,
                )
            }

            val movedFiles =
                runHistoryRepository
                    .getFilesForRunOnce(historyId)
                    .filter { fileMoved -> fileMoved.hasRecoverableDestination }
            val pendingFiles = movedFiles.filter { shouldAttemptUndo(it.undoStatus) }
            val totalBytes = pendingFiles.sumOf { fileMoved -> fileMoved.fileSizeBytes.coerceAtLeast(0L) }
            var processedFiles = 0
            var processedBytes = 0L
            onProgress(
                UndoProgress(
                    processedFiles = processedFiles,
                    totalFiles = pendingFiles.size,
                    processedBytes = processedBytes,
                    totalBytes = totalBytes,
                ),
            )

            if (pendingFiles.isEmpty()) {
                runHistoryRepository.markRunReversed(historyId)
                return UndoResult(
                    totalReversed = 0,
                    totalFailed = 0,
                    errors = emptyList(),
                    operationMode = operationMode,
                )
            }

            if (isMockMoveRun(operationMode, movedFiles)) {
                return undoMockRun(
                    historyId = historyId,
                    movedFiles = pendingFiles,
                    totalBytes = totalBytes,
                    onProgress = onProgress,
                )
            }

            val filesystemAccessEnabled =
                isFilesystemAccessEffective(userPreferencesRepository.preferencesFlow.first().folderAccessMode)

            var reversed = 0
            var failed = 0
            val errors = mutableListOf<String>()

            pendingFiles.forEach { fileMoved ->
                var physicalUndoCompleted = false
                var outcomeCounted = false
                try {
                    runHistoryRepository.markFileUndoStatus(fileMoved.id, FileUndoStatus.IN_PROGRESS)
                    val outcome =
                        when (operationMode) {
                            OperationMode.COPY -> {
                                undoCopyFile(fileMoved)
                            }

                            OperationMode.MOVE -> {
                                if (fileMoved.success) {
                                    undoMoveFile(fileMoved, filesystemAccessEnabled)
                                } else {
                                    undoCopyFile(fileMoved)
                                }
                            }

                            OperationMode.DELETE -> {
                                SingleFileUndoOutcome(
                                    reversed = false,
                                    failed = true,
                                    physicalUndoCompleted = false,
                                    error = "${fileMoved.fileName}: cannot undo delete",
                                )
                            }
                        }
                    if (outcome.reversed) reversed++
                    if (outcome.failed) failed++
                    physicalUndoCompleted = outcome.physicalUndoCompleted
                    outcomeCounted = outcome.reversed || outcome.failed
                    outcome.error?.let { errors.add(it) }

                    if (outcome.reversed) {
                        runHistoryRepository.markFileUndoStatus(fileMoved.id, FileUndoStatus.UNDONE)
                    } else if (outcome.failed) {
                        runHistoryRepository.markFileUndoStatus(fileMoved.id, FileUndoStatus.FAILED)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    if (!outcomeCounted) {
                        failed++
                    }
                    errors.add(
                        "${fileMoved.fileName}: ${error.message ?: context.getString(R.string.undo_unknown_error)}",
                    )
                    if (undoStatusAfterFailure(physicalUndoCompleted) == FileUndoStatus.FAILED) {
                        runCatching {
                            runHistoryRepository.markFileUndoStatus(fileMoved.id, FileUndoStatus.FAILED)
                        }.onFailure { persistenceError ->
                            Log.e(TAG, "Failed to persist undo failure for file ${fileMoved.id}", persistenceError)
                        }
                    }
                } finally {
                    processedFiles++
                    processedBytes += fileMoved.fileSizeBytes.coerceAtLeast(0L)
                    val undoProg =
                        UndoProgress(
                            processedFiles = processedFiles,
                            totalFiles = pendingFiles.size,
                            processedBytes = processedBytes,
                            totalBytes = totalBytes,
                        )
                    val fraction =
                        when {
                            totalBytes > 0L -> processedBytes.toFloat() / totalBytes.toFloat()
                            pendingFiles.isNotEmpty() -> processedFiles.toFloat() / pendingFiles.size.toFloat()
                            else -> 1f
                        }.coerceIn(0f, 1f)
                    _activeUndoProgress.update { it + (historyId to fraction) }
                    onProgress(undoProg)
                }
            }

            // Undo removes only what the run added, and that includes folders: sweep the ones the
            // run is recorded as having created, and nothing else. A folder the user already had is
            // theirs even once it's empty, so it is never a candidate — which is why this works off
            // the recorded list rather than deriving parents from the file paths. Emptiness is
            // re-checked per folder, so a partial undo or leftover content also keeps it.
            if (operationMode != OperationMode.DELETE) {
                // Folders FilePipe created for this rule, across every recorded run — not just this
                // one. A subfolder created by an earlier run and merely reused by this one is still
                // FilePipe's to clean up once it's empty; a folder the user made is never recorded,
                // so it is never a candidate.
                val sweepCandidates =
                    (
                        history.copyCreatedDestFolderUris +
                            (
                                history.ruleId?.let { ruleId ->
                                    runHistoryRepository.getCreatedDestFolderUrisForRule(ruleId)
                                } ?: emptyList()
                            )
                    ).distinct()
                if (sweepCandidates.isEmpty()) {
                    // Distinguishes "no run created these folders" from "the sweep declined to
                    // delete them", which look identical outside and have opposite causes.
                    DiagnosticLog.record(context, "Undo folder sweep: no run recorded creating destination folders")
                } else {
                    deleteEmptyRecordedDestFolders(sweepCandidates)
                }
            }

            // Filesystem-mode undo moved and deleted through raw File I/O, which notifies nobody.
            // Without this, other apps keep listing files and folders undo already took away.
            fileOperationRepository.flushMediaScans()

            if (failed > 0) {
                DiagnosticLog.record(
                    context,
                    "Undo completed with failures: historyId=$historyId, reversed=$reversed, failed=$failed",
                )
            }
            return UndoResult(reversed, failed, errors, operationMode = operationMode)
        }

        private suspend fun syncRunUndoStatus(historyId: Long) {
            val persistedUndoFiles =
                runHistoryRepository
                    .getFilesForRunOnce(historyId)
                    .filter { fileMoved -> fileMoved.hasRecoverableDestination }
            val undoneFileCount = persistedUndoFiles.count { it.undoStatus == FileUndoStatus.UNDONE }
            if (persistedUndoFiles.isNotEmpty() && undoneFileCount == persistedUndoFiles.size) {
                runHistoryRepository.markRunReversed(historyId)
            } else if (undoneFileCount > 0) {
                runHistoryRepository.markRunPartiallyUndone(historyId)
            }
        }

        private fun isMockMoveRun(
            operationMode: OperationMode,
            movedFiles: List<FileMoved>,
        ): Boolean {
            if (operationMode != OperationMode.MOVE || movedFiles.isEmpty()) return false
            return movedFiles.all { fileMoved ->
                DevMockFileMove.isMockMovedFile(
                    sourceUri = fileMoved.sourceUri,
                    destinationUri = fileMoved.destinationUri,
                )
            }
        }

        private suspend fun undoMockRun(
            historyId: Long,
            movedFiles: List<FileMoved>,
            totalBytes: Long,
            onProgress: (UndoProgress) -> Unit,
        ): UndoResult {
            if (movedFiles.isEmpty()) {
                _activeUndoProgress.update { it + (historyId to 1f) }
                onProgress(UndoProgress(0, 0, 0L, 0L))
                runHistoryRepository.markRunReversed(historyId)
                return UndoResult(
                    totalReversed = 0,
                    totalFailed = 0,
                    errors = emptyList(),
                    operationMode = OperationMode.MOVE,
                )
            }
            var processedBytes = 0L
            movedFiles.forEachIndexed { index, fileMoved ->
                runHistoryRepository.markFileUndoStatus(fileMoved.id, FileUndoStatus.IN_PROGRESS)
                delay(DevMockFileMove.FILE_OPERATION_DELAY_MILLIS)
                processedBytes += fileMoved.fileSizeBytes.coerceAtLeast(0L)
                runHistoryRepository.markFileUndoStatus(fileMoved.id, FileUndoStatus.UNDONE)
                val fraction =
                    when {
                        totalBytes > 0L -> processedBytes.toFloat() / totalBytes.toFloat()
                        movedFiles.isNotEmpty() -> (index + 1).toFloat() / movedFiles.size.toFloat()
                        else -> 1f
                    }.coerceIn(0f, 1f)
                _activeUndoProgress.update { it + (historyId to fraction) }
                onProgress(
                    UndoProgress(
                        processedFiles = index + 1,
                        totalFiles = movedFiles.size,
                        processedBytes = processedBytes,
                        totalBytes = totalBytes,
                    ),
                )
            }
            _activeUndoProgress.update { it + (historyId to 1f) }
            runHistoryRepository.markRunReversed(historyId)
            return UndoResult(
                totalReversed = movedFiles.size,
                totalFailed = 0,
                errors = emptyList(),
                operationMode = OperationMode.MOVE,
            )
        }

        /**
         * Removes destination folders that were created during the run, deepest first, only when
         * still empty (so pre-existing folders or folders with leftover content stay).
         */
        private fun deleteEmptyRecordedDestFolders(folderUriStrings: List<String>) {
            val distinctSorted = folderUriStrings.distinct().sortedByDescending { documentPathDepth(it) }
            var deleted = 0
            val skipped = mutableListOf<String>()
            for (uriString in distinctSorted) {
                try {
                    if (uriString.startsWith("file:")) {
                        val path = uriString.toUri().path
                        if (path == null) {
                            skipped += "$uriString (no path)"
                            continue
                        }
                        val dir = File(path)
                        if (!dir.isDirectory) {
                            skipped += "$path (not a directory)"
                            continue
                        }
                        val listed = runCatching { dir.list() }.getOrNull()
                        if (listed == null) {
                            skipped += "$path (could not list)"
                            continue
                        }
                        if (listed.isNotEmpty()) {
                            skipped += "$path (${listed.size} entries left)"
                            continue
                        }
                        if (runCatching { dir.delete() }.getOrDefault(false)) {
                            deleted++
                            // Scan the parent, not the folder: the row to drop is the folder itself.
                            fileOperationRepository.queueMediaScanForFile(dir.path)
                        } else {
                            skipped += "$path (delete refused)"
                        }
                        continue
                    }
                    // Recorded by ensureDestParentFolder as tree-scoped document URIs, so they can
                    // be enumerated directly. A folder that has since been removed answers null
                    // here, same as one we cannot inspect, and is left alone either way.
                    val folderUri = uriString.toUri()
                    when (fileOperationRepository.isSafFolderEmpty(folderUri)) {
                        true -> {
                            deleteDocumentUriWithFallback(folderUri)
                            deleted++
                        }

                        false -> {
                            skipped += "$uriString (not empty)"
                        }

                        null -> {
                            skipped += "$uriString (could not inspect)"
                        }
                    }
                } catch (e: Exception) {
                    skipped += "$uriString (${e.javaClass.simpleName})"
                    Log.w(TAG, "Failed to delete empty recorded dest folder $uriString", e)
                }
            }
            DiagnosticLog.record(
                context,
                buildString {
                    append("Undo folder sweep: ${distinctSorted.size} recorded, $deleted deleted")
                    if (skipped.isNotEmpty()) append(", kept ${skipped.joinToString("; ")}")
                },
            )
        }

        private fun deleteDocumentUriWithFallback(documentUri: Uri) {
            try {
                if (DocumentsContract.deleteDocument(context.contentResolver, documentUri)) {
                    return
                }
            } catch (_: Exception) {
            }
            val doc =
                try {
                    DocumentFile.fromSingleUri(context, documentUri)
                } catch (_: Exception) {
                    null
                }
            try {
                doc?.delete()
            } catch (e: Exception) {
                Log.w(TAG, "DocumentFile fallback delete failed for $documentUri", e)
            }
        }

        private fun documentPathDepth(uriString: String): Int {
            if (uriString.startsWith("file:")) {
                val path = uriString.toUri().path ?: return 0
                return path.trimEnd('/').count { it == '/' }
            }
            if (!uriString.startsWith("content://")) return 0
            return try {
                val docId = DocumentsContract.getDocumentId(uriString.toUri())
                val path = docId.substringAfter(':', "")
                path.count { it == '/' }
            } catch (_: Exception) {
                0
            }
        }

        /**
         * Where to put the file back, as the exact inverse of the forward transfer.
         *
         * The forward move resolved its destination as *root + relative subfolders*, so undoing it
         * has to name the root and let the transfer recreate the same subfolders — not name the
         * file's immediate parent and *also* pass the subfolders, which would nest them twice
         * (`/DCIM/sub` + `sub` → `/DCIM/sub/sub`).
         *
         * Naming the root rather than the parent matters for a second reason on SAF: the parent of
         * a file found in a subfolder is itself a subfolder, and a tree URI synthesized for it
         * carries no grant, whereas the root is the folder the user actually picked.
         *
         * If the recorded segments don't match the source layout, falls back to the immediate
         * parent with no segments — still the correct destination, just without the root anchor.
         */
        private fun restoreTargetForUndo(fileMoved: FileMoved): UndoRestoreTarget? {
            val segments = normalizeDestinationParentSegments(fileMoved.relativeParentSegments)
            val sourceUriString = fileMoved.sourceUri
            if (sourceUriString.startsWith("file:")) {
                val path = sourceUriString.toUri().path ?: return null
                val parent = File(path).parentFile ?: return null
                val root = if (segments.isEmpty()) null else fileWithoutTrailingSegments(parent, segments)
                val folder = normalizeFilesystemFolderPath((root ?: parent).absolutePath) ?: return null
                return UndoRestoreTarget(
                    folderUriString = folder,
                    relativeParentSegments = if (root != null) segments else emptyList(),
                )
            }
            if (sourceUriString.startsWith("content://")) {
                val parsed = sourceUriString.toUri()
                val authority = parsed.authority ?: return null
                val documentId =
                    try {
                        DocumentsContract.getDocumentId(parsed)
                    } catch (_: Exception) {
                        return null
                    }
                val parentDocumentId = parentDocumentIdOrNull(documentId) ?: return null
                val rootDocumentId =
                    if (segments.isEmpty()) null else documentIdWithoutTrailingSegments(parentDocumentId, segments)
                return UndoRestoreTarget(
                    folderUriString =
                        DocumentsContract
                            .buildTreeDocumentUri(authority, rootDocumentId ?: parentDocumentId)
                            .toString(),
                    relativeParentSegments = if (rootDocumentId != null) segments else emptyList(),
                )
            }
            return null
        }

        private fun originalSourceMatches(fileMoved: FileMoved): Boolean =
            try {
                when {
                    fileMoved.sourceUri.startsWith("file:") -> {
                        val path = fileMoved.sourceUri.toUri().path
                        val sourceFile = path?.let(::File)
                        sourceFile != null &&
                            sourceFile.isFile &&
                            (fileMoved.fileSizeBytes <= 0L || sourceFile.length() == fileMoved.fileSizeBytes)
                    }

                    fileMoved.sourceUri.startsWith("content://") -> {
                        val sourceDocument = DocumentFile.fromSingleUri(context, fileMoved.sourceUri.toUri())
                        sourceDocument != null &&
                            sourceDocument.exists() &&
                            sourceDocument.isFile &&
                            (fileMoved.fileSizeBytes <= 0L || sourceDocument.length() == fileMoved.fileSizeBytes)
                    }

                    else -> {
                        false
                    }
                }
            } catch (_: Exception) {
                false
            }

        private suspend fun undoCopyFile(fileMoved: FileMoved): SingleFileUndoOutcome {
            if (fileMoved.destinationUri.startsWith("file:")) {
                val path = fileMoved.destinationUri.toUri().path
                if (path.isNullOrBlank()) {
                    return SingleFileUndoOutcome(
                        reversed = false,
                        failed = true,
                        physicalUndoCompleted = false,
                        error = "${fileMoved.fileName}: invalid destination path",
                    )
                }
                val destFile = File(path)
                if (!destFile.isFile) {
                    return SingleFileUndoOutcome(reversed = true, failed = false, physicalUndoCompleted = true)
                }
                val deleted =
                    try {
                        destFile.delete()
                    } catch (_: SecurityException) {
                        false
                    }
                return if (deleted) {
                    SingleFileUndoOutcome(
                        reversed = true,
                        failed = false,
                        physicalUndoCompleted = true,
                    )
                } else {
                    SingleFileUndoOutcome(
                        reversed = false,
                        failed = true,
                        physicalUndoCompleted = false,
                        error = "${fileMoved.fileName}: could not delete at destination",
                    )
                }
            } else {
                val destUri = fileMoved.destinationUri.toUri()
                val destDoc = DocumentFile.fromSingleUri(context, destUri)
                if (destDoc == null) {
                    return SingleFileUndoOutcome(
                        reversed = false,
                        failed = true,
                        physicalUndoCompleted = false,
                        error = "${fileMoved.fileName}: could not open destination document",
                    )
                }
                if (!destDoc.exists()) {
                    return SingleFileUndoOutcome(reversed = true, failed = false, physicalUndoCompleted = true)
                }
                val deleted =
                    try {
                        destDoc.delete()
                    } catch (_: SecurityException) {
                        false
                    }
                return if (deleted) {
                    SingleFileUndoOutcome(
                        reversed = true,
                        failed = false,
                        physicalUndoCompleted = true,
                    )
                } else {
                    SingleFileUndoOutcome(
                        reversed = false,
                        failed = true,
                        physicalUndoCompleted = false,
                        error = "${fileMoved.fileName}: could not delete at destination",
                    )
                }
            }
        }

        private suspend fun undoMoveFile(
            fileMoved: FileMoved,
            filesystemAccessEnabled: Boolean,
        ): SingleFileUndoOutcome {
            val destUri = fileMoved.destinationUri.toUri()
            val restoreTarget =
                restoreTargetForUndo(fileMoved)
                    ?: return SingleFileUndoOutcome(
                        reversed = false,
                        failed = true,
                        physicalUndoCompleted = false,
                        error = "${fileMoved.fileName}: cannot determine original source folder",
                    )
            val wasInterrupted = fileMoved.undoStatus == FileUndoStatus.IN_PROGRESS
            // Reads the file's real timestamp and move support, not just its size. Restoring a file
            // must not restamp it as modified now, and for SAF that is only achievable by having
            // the provider relocate the document — a stream copy cannot carry a timestamp.
            val sourceEntry =
                fileOperationRepository.entryForExistingFile(
                    uri = destUri,
                    name = fileMoved.fileName,
                    relativeParentSegments = restoreTarget.relativeParentSegments,
                )
            if (sourceEntry == null) {
                return if (wasInterrupted && originalSourceMatches(fileMoved)) {
                    SingleFileUndoOutcome(reversed = true, failed = false, physicalUndoCompleted = true)
                } else {
                    SingleFileUndoOutcome(
                        reversed = false,
                        failed = true,
                        physicalUndoCompleted = false,
                        error = "${fileMoved.fileName}: file no longer exists at destination",
                    )
                }
            }

            val reverseResult =
                fileOperationRepository.moveFile(
                    sourceEntry = sourceEntry,
                    destFolderUriString = restoreTarget.folderUriString,
                    conflictPolicy = ConflictPolicy.SKIP,
                    operationMode = OperationMode.MOVE,
                    filesystemAccessEnabled = filesystemAccessEnabled,
                )
            return if (reverseResult.success && !reverseResult.skipped) {
                SingleFileUndoOutcome(
                    reversed = true,
                    failed = false,
                    physicalUndoCompleted = true,
                )
            } else {
                val reverseError =
                    reverseResult.errorMessage
                        ?: context.getString(R.string.undo_original_source_conflict)
                SingleFileUndoOutcome(
                    reversed = false,
                    failed = true,
                    physicalUndoCompleted = false,
                    error = "${fileMoved.fileName}: $reverseError",
                )
            }
        }
    }

private data class SingleFileUndoOutcome(
    val reversed: Boolean,
    val failed: Boolean,
    val physicalUndoCompleted: Boolean,
    val error: String? = null,
)

/**
 * Folder a file is restored into, plus the subfolders the transfer should recreate beneath it. The
 * two travel together because they are only correct as a pair — see `restoreTargetForUndo`.
 */
private data class UndoRestoreTarget(
    val folderUriString: String,
    val relativeParentSegments: List<String>,
)

@Suppress("ktlint:standard:function-expression-body")
internal fun shouldAttemptUndo(undoStatus: FileUndoStatus): Boolean {
    return undoStatus != FileUndoStatus.UNDONE
}

@Suppress("ktlint:standard:function-expression-body")
internal fun undoStatusAfterFailure(physicalUndoCompleted: Boolean): FileUndoStatus {
    return if (physicalUndoCompleted) {
        FileUndoStatus.IN_PROGRESS
    } else {
        FileUndoStatus.FAILED
    }
}
