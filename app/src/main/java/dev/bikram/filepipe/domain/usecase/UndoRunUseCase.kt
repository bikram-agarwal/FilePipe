package dev.bikram.filepipe.domain.usecase

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.FileEntry
import dev.bikram.filepipe.data.repository.FileOperationRepository
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.data.repository.RunHistoryRepository
import dev.bikram.filepipe.data.storage.isFilesystemAccessEffective
import dev.bikram.filepipe.data.storage.isFilesystemFolderPathString
import dev.bikram.filepipe.data.storage.normalizeFilesystemFolderPath
import dev.bikram.filepipe.di.IoDispatcher
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.isEffectivelyUndone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class UndoResult(
    val totalReversed: Int,
    val totalFailed: Int,
    val errors: List<String>,
    val operationMode: OperationMode = OperationMode.MOVE,
)

private const val TAG = "UndoRunUseCase"

class UndoRunUseCase
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val runHistoryRepository: RunHistoryRepository,
        private val fileOperationRepository: FileOperationRepository,
        private val ruleRepository: RuleRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend operator fun invoke(historyId: Long): UndoResult =
            withContext(ioDispatcher) {
                val history =
                    runHistoryRepository.getHistoryById(historyId)
                        ?: return@withContext UndoResult(0, 0, listOf("Run not found"))

                if (history.isEffectivelyUndone()) {
                    return@withContext UndoResult(
                        0,
                        0,
                        listOf("This run has already been undone"),
                        operationMode = history.operationMode,
                    )
                }

                val operationMode = history.operationMode
                val movedFiles =
                    runHistoryRepository
                        .getFilesForRunOnce(historyId)
                        .filter { it.success && !it.skipped && it.destinationUri.isNotBlank() }

                val filesystemAccessEnabled =
                    isFilesystemAccessEffective(userPreferencesRepository.preferencesFlow.first().folderAccessMode)

                var reversed = 0
                var failed = 0
                val errors = mutableListOf<String>()
                val copyDeletedDestinationUris = mutableListOf<String>()

                movedFiles.forEach { fileMoved ->
                    when (operationMode) {
                        OperationMode.COPY -> {
                            if (fileMoved.destinationUri.startsWith("file:")) {
                                val path = fileMoved.destinationUri.toUri().path
                                if (path.isNullOrBlank()) {
                                    errors.add("${fileMoved.fileName}: invalid destination path")
                                    failed++
                                    return@forEach
                                }
                                val destFile = File(path)
                                if (!destFile.isFile) {
                                    reversed++
                                    return@forEach
                                }
                                val deleted =
                                    try {
                                        destFile.delete()
                                    } catch (_: SecurityException) {
                                        false
                                    }
                                if (deleted) {
                                    reversed++
                                    copyDeletedDestinationUris.add(fileMoved.destinationUri)
                                } else {
                                    failed++
                                    errors.add("${fileMoved.fileName}: could not delete at destination")
                                }
                            } else {
                                val destUri = fileMoved.destinationUri.toUri()
                                val destDoc = DocumentFile.fromSingleUri(context, destUri)
                                if (destDoc == null) {
                                    errors.add("${fileMoved.fileName}: could not open destination document")
                                    failed++
                                    return@forEach
                                }
                                if (!destDoc.exists()) {
                                    reversed++
                                    return@forEach
                                }
                                val deleted =
                                    try {
                                        destDoc.delete()
                                    } catch (_: SecurityException) {
                                        false
                                    }
                                if (deleted) {
                                    reversed++
                                    copyDeletedDestinationUris.add(fileMoved.destinationUri)
                                } else {
                                    failed++
                                    errors.add("${fileMoved.fileName}: could not delete at destination")
                                }
                            }
                        }

                        OperationMode.MOVE -> {
                            val destUri = fileMoved.destinationUri.toUri()
                            val sourceFolderUriString = parentSourceFolderForUndo(fileMoved.sourceUri)
                            if (sourceFolderUriString == null) {
                                errors.add("${fileMoved.fileName}: cannot determine original source folder")
                                failed++
                                return@forEach
                            }
                            val sizeBytes =
                                when {
                                    fileMoved.destinationUri.startsWith("file:") -> {
                                        val path = destUri.path
                                        if (path.isNullOrBlank()) {
                                            errors.add("${fileMoved.fileName}: invalid destination path")
                                            failed++
                                            return@forEach
                                        }
                                        val destFile = File(path)
                                        if (!destFile.isFile) {
                                            errors.add("${fileMoved.fileName}: file no longer exists at destination")
                                            failed++
                                            return@forEach
                                        }
                                        destFile.length()
                                    }

                                    else -> {
                                        val destDoc = DocumentFile.fromSingleUri(context, destUri)
                                        if (destDoc == null || !destDoc.exists()) {
                                            errors.add("${fileMoved.fileName}: file no longer exists at destination")
                                            failed++
                                            return@forEach
                                        }
                                        destDoc.length()
                                    }
                                }

                            val sourceEntry =
                                FileEntry(
                                    uri = destUri,
                                    name = fileMoved.fileName,
                                    size = sizeBytes,
                                    relativeParentSegments = fileMoved.relativeParentSegments,
                                )

                            val reverseResult =
                                fileOperationRepository.moveFile(
                                    sourceEntry = sourceEntry,
                                    destFolderUriString = sourceFolderUriString,
                                    conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                                    operationMode = OperationMode.MOVE,
                                    filesystemAccessEnabled = filesystemAccessEnabled,
                                )

                            if (reverseResult.success) {
                                reversed++
                            } else {
                                failed++
                                reverseResult.errorMessage?.let { errors.add("${fileMoved.fileName}: $it") }
                            }
                        }
                    }
                }

                if (operationMode == OperationMode.COPY && history.copyCreatedDestFolderUris.isNotEmpty()) {
                    deleteEmptyRecordedCopyFolders(history.copyCreatedDestFolderUris)
                }

                if (reversed > 0) {
                    runHistoryRepository.markRunReversed(historyId)
                }

                if (operationMode == OperationMode.COPY && copyDeletedDestinationUris.isNotEmpty()) {
                    val destTreeUriString =
                        history.ruleId?.let { ruleId ->
                            ruleRepository.getRuleById(ruleId)?.destinationFolderPath?.takeIf { it.isNotBlank() }
                        }
                    if (destTreeUriString != null) {
                        deleteEmptyDestSubfoldersAfterCopyUndo(destTreeUriString, copyDeletedDestinationUris)
                    }
                }

                if (failed > 0) {
                    DiagnosticLog.record(
                        context,
                        "Undo completed with failures: historyId=$historyId, reversed=$reversed, failed=$failed",
                    )
                }
                return@withContext UndoResult(reversed, failed, errors, operationMode = operationMode)
            }

        /**
         * After copied files are removed, deletes empty subfolders under the rule destination tree that
         * were only holding those files. Skips non-empty dirs (e.g. pre-existing content).
         */
        private fun deleteEmptyDestSubfoldersAfterCopyUndo(
            destTreeUriString: String,
            deletedFileDestinationUriStrings: List<String>,
        ) {
            if (isFilesystemFolderPathString(destTreeUriString)) {
                deleteEmptyFilesystemFoldersAfterCopyUndo(destTreeUriString, deletedFileDestinationUriStrings)
                return
            }
            val treeUri = destTreeUriString.toUri()
            val authority = treeUri.authority ?: return
            val treeDocumentId =
                try {
                    DocumentsContract.getTreeDocumentId(treeUri)
                } catch (_: IllegalArgumentException) {
                    return
                }
            val folderDocumentIds = mutableSetOf<String>()
            for (fileUriString in deletedFileDestinationUriStrings) {
                folderDocumentIds.addAll(
                    parentFolderDocumentIdsUnderTree(treeDocumentId, fileUriString),
                )
            }
            val deepestFirst =
                folderDocumentIds.sortedByDescending { documentId ->
                    documentId.count { segment -> segment == '/' }
                }
            for (folderDocumentId in deepestFirst) {
                try {
                    val folderUri = DocumentsContract.buildDocumentUri(authority, folderDocumentId)
                    val folderDoc =
                        try {
                            DocumentFile.fromSingleUri(context, folderUri)
                        } catch (_: Exception) {
                            null
                        } ?: continue
                    val isDirectory =
                        try {
                            folderDoc.isDirectory
                        } catch (_: Exception) {
                            continue
                        }
                    if (!isDirectory) continue
                    val children =
                        try {
                            folderDoc.listFiles()
                        } catch (_: Exception) {
                            null
                        }
                    if (children != null && children.isNotEmpty()) continue
                    deleteDocumentUriWithFallback(folderUri)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete empty dest subfolder $folderDocumentId", e)
                }
            }
        }

        /**
         * Document IDs for folders strictly between the tree root and the file (i.e. parents of the
         * file, excluding the destination root). Empty if the file lived directly under the tree root.
         */
        private fun parentFolderDocumentIdsUnderTree(
            treeDocumentId: String,
            fileDocumentUriString: String,
        ): List<String> {
            val fileDocumentId =
                try {
                    DocumentsContract.getDocumentId(fileDocumentUriString.toUri())
                } catch (_: IllegalArgumentException) {
                    return emptyList()
                }
            val treePrefix = "$treeDocumentId/"
            if (!fileDocumentId.startsWith(treePrefix)) return emptyList()
            val relative = fileDocumentId.removePrefix(treePrefix)
            val segments = relative.split('/').filter { segment -> segment.isNotEmpty() }
            if (segments.size < 2) return emptyList()
            val volume = treeDocumentId.substringBefore(':')
            val treePathAfterColon = treeDocumentId.substringAfter(':', "")
            val result = mutableListOf<String>()
            for (depth in 1 until segments.size) {
                val underTree = segments.take(depth).joinToString("/")
                val fullPath =
                    if (treePathAfterColon.isEmpty()) {
                        underTree
                    } else {
                        "$treePathAfterColon/$underTree"
                    }
                result.add("$volume:$fullPath")
            }
            return result
        }

        /**
         * Removes destination folders that were created during the copy run, deepest first,
         * only when still empty (so pre-existing folders or folders with leftover content stay).
         */
        private fun deleteEmptyRecordedCopyFolders(folderUriStrings: List<String>) {
            val distinctSorted = folderUriStrings.distinct().sortedByDescending { documentPathDepth(it) }
            for (uriString in distinctSorted) {
                try {
                    if (uriString.startsWith("file:")) {
                        val path = uriString.toUri().path ?: continue
                        val dir = File(path)
                        if (!dir.isDirectory) continue
                        val listed =
                            try {
                                dir.list()
                            } catch (_: Exception) {
                                null
                            }
                        if (!listed.isNullOrEmpty()) continue
                        try {
                            dir.delete()
                        } catch (_: Exception) {
                        }
                        continue
                    }
                    val folderUri = uriString.toUri()
                    val folderDoc =
                        try {
                            DocumentFile.fromSingleUri(context, folderUri)
                        } catch (_: Exception) {
                            null
                        } ?: continue
                    val exists =
                        try {
                            folderDoc.exists()
                        } catch (_: Exception) {
                            continue
                        }
                    if (!exists) continue
                    val isDirectory =
                        try {
                            folderDoc.isDirectory
                        } catch (_: Exception) {
                            continue
                        }
                    if (!isDirectory) continue
                    val children =
                        try {
                            folderDoc.listFiles()
                        } catch (_: Exception) {
                            null
                        }
                    if (!children.isNullOrEmpty()) continue
                    deleteDocumentUriWithFallback(folderUri)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete empty recorded copy folder $uriString", e)
                }
            }
        }

        private fun deleteEmptyFilesystemFoldersAfterCopyUndo(
            destRootRaw: String,
            deletedFileUriStrings: List<String>,
        ) {
            val destRoot = normalizeFilesystemFolderPath(destRootRaw) ?: return
            val folderPaths = mutableSetOf<String>()
            for (uriStr in deletedFileUriStrings) {
                if (!uriStr.startsWith("file:")) continue
                val filePath = uriStr.toUri().path ?: continue
                val file = File(filePath)
                var parent = file.parentFile ?: continue
                while (true) {
                    val canon =
                        try {
                            parent.canonicalPath
                        } catch (_: Exception) {
                            break
                        }
                    if (canon == destRoot) break
                    if (!canon.startsWith(destRoot + File.separator)) break
                    folderPaths.add(canon)
                    parent = parent.parentFile ?: break
                }
            }
            val deepestFirst =
                folderPaths.sortedByDescending { folderPath ->
                    folderPath.count { segment -> segment == '/' }
                }
            for (folderPath in deepestFirst) {
                val dir = File(folderPath)
                try {
                    if (!dir.isDirectory) continue
                    val listed = dir.list()
                    if (listed != null && listed.isNotEmpty()) continue
                    dir.delete()
                } catch (_: Exception) {
                }
            }
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

        private fun parentSourceFolderForUndo(sourceUriString: String): String? {
            if (sourceUriString.startsWith("content://")) return parentTreeUriString(sourceUriString)
            if (sourceUriString.startsWith("file:")) {
                val path = sourceUriString.toUri().path ?: return null
                val parent = File(path).parentFile ?: return null
                return normalizeFilesystemFolderPath(parent.absolutePath)
            }
            return null
        }

        /**
         * Derives the parent folder as a SAF tree URI string from a document URI.
         * e.g. content://...document/primary%3ADCIM%2FCamera%2Fphoto.jpg
         *   → content://...tree/primary%3ADCIM%2FCamera
         */
        private fun parentTreeUriString(documentUriString: String): String? {
            if (!documentUriString.startsWith("content://")) return null
            return try {
                val parsed = documentUriString.toUri()
                val docAuthority = parsed.authority ?: return null
                val docId = DocumentsContract.getDocumentId(parsed)
                val relativePath = docId.substringAfter(":", "")
                val parentDocId =
                    if ('/' in relativePath) {
                        docId.substringBeforeLast('/')
                    } else {
                        // File is directly at the volume root — parent is the root itself
                        docId.substringBefore(':') + ":"
                    }
                DocumentsContract.buildTreeDocumentUri(docAuthority, parentDocId).toString()
            } catch (_: Exception) {
                null
            }
        }
    }
