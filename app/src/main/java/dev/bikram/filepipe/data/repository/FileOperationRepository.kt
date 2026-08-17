package dev.bikram.filepipe.data.repository

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.data.storage.folderPathForFilesystemAccess
import dev.bikram.filepipe.data.storage.isCanonicalPathUnderAllowedSharedStorage
import dev.bikram.filepipe.data.storage.isFilesystemFolderPathString
import dev.bikram.filepipe.data.storage.normalizeFilesystemFolderPath
import dev.bikram.filepipe.di.IoDispatcher
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.FileOrientation
import dev.bikram.filepipe.domain.model.FolderAccessResult
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.PreviewFileResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** How long a cached scan stays reusable (preview/simulate → run) before it's treated as stale. */
internal const val SCAN_CACHE_TTL_MS = 300_000L

@Suppress("ktlint:standard:function-expression-body")
internal fun isCompleteCopy(
    expectedBytes: Long,
    copiedBytes: Long,
    sizeKnown: Boolean = true,
): Boolean {
    return !sizeKnown || copiedBytes == expectedBytes
}

@Singleton
class FileOperationRepository
    @Inject
    constructor(
        @param:ApplicationContext internal val context: Context,
        @IoDispatcher internal val ioDispatcher: CoroutineDispatcher,
    ) {
        internal val scanCache = ConcurrentHashMap<ScanCacheKey, CacheEntry>()
        private val accessCache = ConcurrentHashMap<String, Pair<FolderAccessResult, Long>>()
        private val accessCacheTtlMs = 5_000L

        /**
         * Directories touched by filesystem-mode operations, awaiting a MediaStore scan.
         *
         * SAF operations need no equivalent: the provider scans on our behalf (see
         * `FileSystemProvider.moveDocument`). Raw `File` I/O notifies nobody, so without this other
         * apps keep serving stale listings — a moved file still shown in its old folder, a deleted
         * folder still listed. Collected per directory and flushed once per run by [flushMediaScans]
         * rather than scanned per file, which would cost a blocking scan per file moved.
         */
        private val pendingMediaScanDirs = ConcurrentHashMap.newKeySet<String>()

        /**
         * Keys already reported through [recordDiagnosticOnce]. Transfer failures of the kinds we
         * log are systemic (a whole volume or provider behaves that way), so the first occurrence
         * carries all the diagnostic value while the thousandth would just churn the log file.
         */
        private val diagnosticsRecordedOnce = ConcurrentHashMap.newKeySet<String>()

        suspend fun listMatchingFiles(
            folderUriString: String,
            extensions: List<String>,
            scanSubdirectories: Boolean = false,
            filenamePattern: String? = null,
            minFileSizeBytes: Long? = null,
            maxFileSizeBytes: Long? = null,
            minAgeDays: Int? = null,
            maxAgeDays: Int? = null,
            excludePatterns: List<String> = emptyList(),
            maxDepth: Int = MAX_SCAN_DEPTH,
            filesystemAccessEnabled: Boolean = false,
            orientation: FileOrientation? = null,
            isRegexPattern: Boolean = false,
            isExcludeRegexPattern: Boolean = false,
            useCache: Boolean = false,
        ): List<FileEntry> =
            withContext(ioDispatcher) {
                val effectiveMaxDepth = maxDepth.coerceIn(0, MAX_SCAN_DEPTH)
                val cacheKey =
                    ScanCacheKey(
                        folderUriString = folderUriString,
                        extensions = extensions,
                        scanSubdirectories = scanSubdirectories,
                        filenamePattern = filenamePattern,
                        minFileSizeBytes = minFileSizeBytes,
                        maxFileSizeBytes = maxFileSizeBytes,
                        minAgeDays = minAgeDays,
                        maxAgeDays = maxAgeDays,
                        excludePatterns = excludePatterns,
                        maxDepth = effectiveMaxDepth,
                        orientation = orientation,
                        filesystemAccessEnabled = filesystemAccessEnabled,
                        isRegexPattern = isRegexPattern,
                        isExcludeRegexPattern = isExcludeRegexPattern,
                    )

                if (useCache) {
                    val nowTime = System.currentTimeMillis()
                    val cached = scanCache[cacheKey]
                    if (cached != null && (nowTime - cached.timestamp) < SCAN_CACHE_TTL_MS) {
                        scanCache.remove(cacheKey)
                        return@withContext cached.files
                    }
                }

                val resultList =
                    listMatchingFilesScan(
                        folderUriString = folderUriString,
                        extensions = extensions,
                        scanSubdirectories = scanSubdirectories,
                        filenamePattern = filenamePattern,
                        minFileSizeBytes = minFileSizeBytes,
                        maxFileSizeBytes = maxFileSizeBytes,
                        minAgeDays = minAgeDays,
                        maxAgeDays = maxAgeDays,
                        excludePatterns = excludePatterns,
                        maxDepth = effectiveMaxDepth,
                        filesystemAccessEnabled = filesystemAccessEnabled,
                        orientation = orientation,
                        isRegexPattern = isRegexPattern,
                        isExcludeRegexPattern = isExcludeRegexPattern,
                    )

                val writeTime = System.currentTimeMillis()
                scanCache.entries.removeAll { (_, entry) -> writeTime - entry.timestamp >= SCAN_CACHE_TTL_MS }
                scanCache[cacheKey] = CacheEntry(resultList, writeTime)
                resultList
            }

        /**
         * [FileEntry] for the file that already exists at [uri], or null if it is gone or
         * unreadable.
         *
         * Scans populate every field as they walk; this rebuilds an entry from a recorded URI —
         * undo restoring a file to its original folder being the case that matters. Reading the
         * real timestamp and move support here is what lets [moveFile] relocate a SAF document in
         * place instead of stream-copying it, and a stream copy through SAF cannot carry a
         * last-modified time at all.
         */
        suspend fun entryForExistingFile(
            uri: Uri,
            name: String,
            relativeParentSegments: List<String> = emptyList(),
        ): FileEntry? =
            withContext(ioDispatcher) {
                if (uri.scheme == "file") {
                    val path = uri.path
                    if (path.isNullOrBlank()) return@withContext null
                    val file = File(path)
                    if (!file.isFile) return@withContext null
                    val lastModifiedMs = file.lastModified()
                    return@withContext FileEntry(
                        uri = uri,
                        name = name,
                        size = file.length(),
                        lastModifiedMs = lastModifiedMs,
                        lastModifiedKnown = lastModifiedMs > 0L,
                        relativeParentSegments = relativeParentSegments,
                    )
                }

                val document = DocumentFile.fromSingleUri(context, uri)
                if (document == null || !document.exists()) return@withContext null
                val metadata = queryDocumentMetadata(uri)
                val lastModifiedMs = metadata?.lastModifiedMs ?: document.lastModified()
                FileEntry(
                    uri = uri,
                    name = name,
                    size = metadata?.size ?: document.length(),
                    lastModifiedMs = lastModifiedMs,
                    // Only the column tells us a size is real; DocumentFile.length() reports 0 both
                    // for an empty file and for one whose size the provider withheld, and treating
                    // the latter as known would fail the transfer's completeness check.
                    sizeKnown = metadata?.size != null,
                    lastModifiedKnown = lastModifiedMs > 0L,
                    relativeParentSegments = relativeParentSegments,
                    parentDocumentUri = parentDocumentUriUnderSameTree(uri),
                    supportsMove = metadata?.supportsMove == true,
                )
            }

        suspend fun moveFile(
            sourceEntry: FileEntry,
            destFolderUriString: String,
            conflictPolicy: ConflictPolicy,
            operationMode: OperationMode,
            destFoldersCreatedCollector: MutableCollection<String>? = null,
            destinationFolderCache: DestinationFolderCache? = null,
            filesystemAccessEnabled: Boolean = false,
            requireUnchangedSource: Boolean = false,
        ): FileMoved =
            withContext(ioDispatcher + NonCancellable) {
                if (operationMode == OperationMode.DELETE) {
                    return@withContext deleteFile(
                        sourceEntry = sourceEntry,
                        filesystemAccessEnabled = filesystemAccessEnabled,
                        requireUnchangedSource = requireUnchangedSource,
                    )
                }

                val effectiveDestFolder =
                    folderPathForFilesystemAccess(destFolderUriString, filesystemAccessEnabled)
                val sourceIsFile = sourceEntry.uri.scheme == "file"
                val destIsFilesystem = isFilesystemFolderPathString(effectiveDestFolder)

                if (sourceIsFile && !filesystemAccessEnabled) {
                    return@withContext FileMoved(
                        fileName = sourceEntry.name,
                        sourceUri = sourceEntry.uri.toString(),
                        destinationUri = "",
                        fileSizeBytes = sourceEntry.size,
                        relativeParentSegments = sourceEntry.relativeParentSegments,
                        success = false,
                        errorMessage = "All files access is required for this source path",
                    )
                }
                if (destIsFilesystem && !filesystemAccessEnabled) {
                    return@withContext FileMoved(
                        fileName = sourceEntry.name,
                        sourceUri = sourceEntry.uri.toString(),
                        destinationUri = "",
                        fileSizeBytes = sourceEntry.size,
                        relativeParentSegments = sourceEntry.relativeParentSegments,
                        success = false,
                        errorMessage = "All files access is required for this destination path",
                    )
                }

                when {
                    destIsFilesystem && sourceIsFile -> {
                        moveFileFilesystemToFilesystem(
                            sourceEntry,
                            effectiveDestFolder,
                            conflictPolicy,
                            operationMode,
                            destFoldersCreatedCollector,
                        )
                    }

                    destIsFilesystem && !sourceIsFile -> {
                        moveFileDocumentToFilesystem(
                            sourceEntry,
                            effectiveDestFolder,
                            conflictPolicy,
                            operationMode,
                            destFoldersCreatedCollector,
                        )
                    }

                    !destIsFilesystem && sourceIsFile -> {
                        moveFileFilesystemToDocument(
                            sourceEntry,
                            effectiveDestFolder,
                            conflictPolicy,
                            operationMode,
                            destFoldersCreatedCollector,
                            destinationFolderCache,
                        )
                    }

                    else -> {
                        moveFileDocumentToDocument(
                            sourceEntry,
                            effectiveDestFolder,
                            conflictPolicy,
                            operationMode,
                            destFoldersCreatedCollector,
                            destinationFolderCache,
                        )
                    }
                }
            }

        suspend fun simulateMove(
            sourceEntry: FileEntry,
            destFolderUriString: String,
            conflictPolicy: ConflictPolicy,
            operationMode: OperationMode = OperationMode.MOVE,
            destinationFolderCache: DestinationFolderCache? = null,
            filesystemAccessEnabled: Boolean = false,
        ): PreviewFileResult =
            withContext(ioDispatcher) {
                if (operationMode == OperationMode.DELETE) {
                    return@withContext unchangedPreviewResult(sourceEntry, "")
                }

                val effectiveDestFolder =
                    folderPathForFilesystemAccess(destFolderUriString, filesystemAccessEnabled)
                val simulatedRootPath =
                    buildSimulatedDestPreviewPath(
                        effectiveDestFolder,
                        sourceEntry.relativeParentSegments,
                        sourceEntry.name,
                    )

                if (effectiveDestFolder.isBlank()) {
                    return@withContext unchangedPreviewResult(sourceEntry, simulatedRootPath)
                }

                if (isFilesystemFolderPathString(effectiveDestFolder)) {
                    return@withContext simulateFilesystemMove(
                        sourceEntry = sourceEntry,
                        destFolderUriString = effectiveDestFolder,
                        conflictPolicy = conflictPolicy,
                        filesystemAccessEnabled = filesystemAccessEnabled,
                        simulatedRootPath = simulatedRootPath,
                    )
                }

                simulateSafMove(
                    sourceEntry = sourceEntry,
                    destFolderUriString = effectiveDestFolder,
                    conflictPolicy = conflictPolicy,
                    simulatedRootPath = simulatedRootPath,
                    destinationFolderCache = destinationFolderCache,
                )
            }

        fun resolveFolderAccess(
            folderPathOrUri: String,
            filesystemAccessEnabled: Boolean = false,
        ): FolderAccessResult {
            val effectivePath =
                folderPathForFilesystemAccess(folderPathOrUri, filesystemAccessEnabled)
            val cacheKey = "${folderPathOrUri}\u0000$filesystemAccessEnabled"
            val cached = accessCache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.second < accessCacheTtlMs) {
                return cached.first
            }
            val resolved =
                when {
                    isFilesystemFolderPathString(effectivePath) -> {
                        when {
                            !filesystemAccessEnabled -> {
                                FolderAccessResult.PermissionDenied
                            }

                            else -> {
                                val canonical = normalizeFilesystemFolderPath(effectivePath)
                                when {
                                    canonical == null -> {
                                        FolderAccessResult.Unavailable
                                    }

                                    !isCanonicalPathUnderAllowedSharedStorage(canonical) -> {
                                        FolderAccessResult.Unavailable
                                    }

                                    else -> {
                                        val dir = File(canonical)
                                        when {
                                            !dir.exists() || !dir.isDirectory -> FolderAccessResult.Unavailable
                                            !dir.canRead() -> FolderAccessResult.PermissionDenied
                                            else -> FolderAccessResult.Accessible
                                        }
                                    }
                                }
                            }
                        }
                    }

                    effectivePath.startsWith("content://") -> {
                        try {
                            val document = DocumentFile.fromTreeUri(context, effectivePath.toUri())
                            when {
                                document == null -> FolderAccessResult.Unavailable
                                !document.exists() -> FolderAccessResult.Unavailable
                                !document.canRead() -> FolderAccessResult.PermissionDenied
                                else -> FolderAccessResult.Accessible
                            }
                        } catch (_: SecurityException) {
                            FolderAccessResult.PermissionDenied
                        }
                    }

                    else -> {
                        FolderAccessResult.Unavailable
                    }
                }
            accessCache[cacheKey] = resolved to System.currentTimeMillis()
            return resolved
        }

        fun isAccessible(
            folderPathOrUri: String,
            filesystemAccessEnabled: Boolean = false,
        ): Boolean = resolveFolderAccess(folderPathOrUri, filesystemAccessEnabled) == FolderAccessResult.Accessible

        fun invalidateAccessCache() {
            accessCache.clear()
        }

        /** Queues the directory containing [filePath] for a MediaStore scan. */
        internal fun queueMediaScanForFile(filePath: String?) {
            val parent = filePath?.let { File(it).parent } ?: return
            pendingMediaScanDirs.add(parent)
        }

        /** Queues [directoryPath] itself, for when the directory is what changed. */
        internal fun queueMediaScanForDirectory(directoryPath: String?) {
            if (directoryPath.isNullOrBlank()) return
            pendingMediaScanDirs.add(directoryPath)
        }

        /**
         * Scans every queued directory so other apps see the run's changes, then clears the queue.
         * Scanning a directory reconciles it, which is what drops index entries for files and
         * folders the run removed. Best-effort: a failed scan costs freshness, not correctness.
         */
        suspend fun flushMediaScans() {
            if (pendingMediaScanDirs.isEmpty()) return
            val directories = pendingMediaScanDirs.toList()
            pendingMediaScanDirs.removeAll(directories.toSet())
            withContext(ioDispatcher) {
                // MediaStore.scanFile is @SystemApi; MediaScannerConnection is the app-facing
                // equivalent and takes the whole batch in one request.
                runCatching {
                    MediaScannerConnection.scanFile(context, directories.toTypedArray(), null, null)
                }.onFailure {
                    recordDiagnosticOnce(
                        key = "media-scan-failed",
                        message = "MediaStore scan request failed for ${directories.size} directories: ${it.message}",
                    )
                }
            }
        }

        /**
         * Records [message] the first time [key] is seen. Used by the transfer helpers, which run
         * once per file and would otherwise write one diagnostic line per file in a run.
         */
        internal fun recordDiagnosticOnce(
            key: String,
            message: String,
        ) {
            if (diagnosticsRecordedOnce.add(key)) {
                DiagnosticLog.record(context, message)
            }
        }
    }

data class FileEntry(
    val uri: Uri,
    val name: String,
    val size: Long,
    val lastModifiedMs: Long = 0L,
    val sizeKnown: Boolean = true,
    val lastModifiedKnown: Boolean = true,
    val relativeParentSegments: List<String> = emptyList(),
    /**
     * Containing folder, as a document URI scoped to the same tree grant as [uri]. Required by
     * [DocumentsContract.moveDocument]; only populated for SAF entries produced by a scan.
     */
    val parentDocumentUri: Uri? = null,
    /**
     * Whether the provider advertised `FLAG_SUPPORTS_MOVE` for this document at scan time. Gates
     * the in-provider move fast path, so an entry that never saw the flag column simply gets the
     * stream-copy path instead of one thrown Binder exception per file.
     */
    val supportsMove: Boolean = false,
)

fun FileEntry.canonicalIdentity(): String {
    if (uri.scheme == "content") {
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            "${uri.authority}:$docId"
        } catch (_: Exception) {
            uri.toString()
        }
    }
    val rawPath = uri.path ?: uri.toString()
    return canonicalFilesystemIdentity(rawPath)
}

internal fun canonicalFilesystemIdentity(rawPath: String): String =
    try {
        File(rawPath).canonicalPath
    } catch (_: Exception) {
        normalizeFilesystemFolderPath(rawPath) ?: rawPath.trimEnd('/')
    }

fun normalizeSourcePath(
    path: String,
    filesystemAccessEnabled: Boolean,
): String {
    val effectivePath = folderPathForFilesystemAccess(path, filesystemAccessEnabled)
    if (effectivePath.startsWith("content://")) {
        return canonicalSafTreeIdentity(effectivePath)
    }
    if (effectivePath.startsWith("file:")) {
        val rawPath = effectivePath.toUri().path ?: return effectivePath
        return try {
            File(rawPath).canonicalPath
        } catch (_: Exception) {
            rawPath.trimEnd('/')
        }
    }
    if (effectivePath.startsWith("/")) {
        return try {
            File(effectivePath).canonicalPath
        } catch (_: Exception) {
            effectivePath.trimEnd('/')
        }
    }
    return effectivePath.trimEnd('/')
}

internal fun canonicalSafTreeIdentity(uriString: String): String =
    runCatching {
        val parsedUri = URI(uriString)
        val rawSegments = parsedUri.rawPath.split('/').filter { it.isNotBlank() }
        val treeSegmentIndex = rawSegments.indexOf("tree")
        val documentSegmentIndex = rawSegments.indexOf("document")
        val documentId =
            when {
                treeSegmentIndex >= 0 && treeSegmentIndex + 1 < rawSegments.size -> {
                    rawSegments[treeSegmentIndex + 1]
                }

                documentSegmentIndex >= 0 && documentSegmentIndex + 1 < rawSegments.size -> {
                    rawSegments[documentSegmentIndex + 1]
                }

                else -> {
                    return@runCatching uriString.trimEnd('/')
                }
            }
        val decodedDocumentId = URLDecoder.decode(documentId.replace("+", "%2B"), "UTF-8")
        "content://${parsedUri.authority.lowercase()}/$decodedDocumentId"
    }.getOrElse {
        uriString.trimEnd('/')
    }
