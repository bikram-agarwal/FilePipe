package dev.bikram.filepipe.data.repository

import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dev.bikram.filepipe.data.storage.folderPathForFilesystemAccess
import dev.bikram.filepipe.data.storage.isCanonicalPathUnderAllowedSharedStorage
import dev.bikram.filepipe.data.storage.isFilesystemFolderPathString
import dev.bikram.filepipe.data.storage.normalizeFilesystemFolderPath
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.domain.model.FileOrientation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

internal const val MAX_SCAN_DEPTH = 32

internal data class ScanCacheKey(
    val folderUriString: String,
    val extensions: List<String>,
    val scanSubdirectories: Boolean,
    val filenamePattern: String?,
    val minFileSizeBytes: Long?,
    val maxFileSizeBytes: Long?,
    val minAgeDays: Int?,
    val maxAgeDays: Int?,
    val excludePatterns: List<String>,
    val maxDepth: Int,
    val orientation: FileOrientation?,
    val filesystemAccessEnabled: Boolean,
    val isRegexPattern: Boolean,
    val isExcludeRegexPattern: Boolean,
)

internal data class CacheEntry(
    val files: List<FileEntry>,
    val timestamp: Long,
)

internal data class SafDocEntry(
    val documentId: String,
    val uri: Uri,
    val name: String,
    val size: Long,
    val lastModifiedMs: Long,
    val sizeKnown: Boolean,
    val lastModifiedKnown: Boolean,
    val isFile: Boolean,
    val isDirectory: Boolean,
    val parentDocumentUri: Uri? = null,
    val supportsMove: Boolean = false,
)

internal data class DocumentMetadata(
    val size: Long?,
    val lastModifiedMs: Long?,
    val supportsMove: Boolean = false,
)

internal suspend fun FileOperationRepository.listMatchingFilesScan(
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
): List<FileEntry> =
    withContext(ioDispatcher) {
        val effectiveFolderUriString =
            folderPathForFilesystemAccess(folderUriString, filesystemAccessEnabled)
        if (isFilesystemFolderPathString(effectiveFolderUriString)) {
            if (!filesystemAccessEnabled) return@withContext emptyList()
            val canonical = normalizeFilesystemFolderPath(effectiveFolderUriString) ?: return@withContext emptyList()
            if (!isCanonicalPathUnderAllowedSharedStorage(canonical)) return@withContext emptyList()
            val rootDir = File(canonical)
            if (!rootDir.isDirectory || !rootDir.canRead()) return@withContext emptyList()
            return@withContext listMatchingFilesFromFilesystemRoot(
                rootDir = rootDir,
                extensions = extensions,
                scanSubdirectories = scanSubdirectories,
                filenamePattern = filenamePattern,
                minFileSizeBytes = minFileSizeBytes,
                maxFileSizeBytes = maxFileSizeBytes,
                minAgeDays = minAgeDays,
                maxAgeDays = maxAgeDays,
                excludePatterns = excludePatterns,
                maxDepth = maxDepth,
                orientation = orientation,
                isRegexPattern = isRegexPattern,
                isExcludeRegexPattern = isExcludeRegexPattern,
            )
        }

        if (!effectiveFolderUriString.startsWith("content://")) return@withContext emptyList()

        val treeUri = effectiveFolderUriString.toUri()
        val folder =
            try {
                DocumentFile.fromTreeUri(context, treeUri)
            } catch (_: SecurityException) {
                return@withContext emptyList()
            } ?: return@withContext emptyList()

        if (!folder.exists() || !folder.canRead()) return@withContext emptyList()

        val filterContext =
            FileScanFilterContext(
                extensions = extensions,
                filenameRegexes = buildFilenameRegexes(filenamePattern, isRegexPattern),
                isRegexPattern = isRegexPattern,
                excludeRegexes = buildExcludeRegexes(excludePatterns, isExcludeRegexPattern),
                isExcludeRegexPattern = isExcludeRegexPattern,
                minFileSizeBytes = minFileSizeBytes,
                maxFileSizeBytes = maxFileSizeBytes,
                minAgeMs = minAgeDays?.let { TimeUnit.DAYS.toMillis(it.toLong()) },
                maxAgeMs = maxAgeDays?.let { TimeUnit.DAYS.toMillis(it.toLong()) },
                nowMs = System.currentTimeMillis(),
            )

        val scanContext = currentCoroutineContext()
        var depthTruncated = false

        val candidates =
            try {
                val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                collectSafFiles(
                    treeUri = treeUri,
                    rootDocumentId = rootDocumentId,
                    scanSubdirectories = scanSubdirectories,
                    maxDepth = maxDepth,
                    includeFile = { document ->
                        passesCheapScanFilters(
                            fileName = document.name,
                            fileSizeBytes = document.size,
                            lastModifiedMs = document.lastModifiedMs,
                            filters = filterContext,
                        )
                    },
                    onDepthLimitReached = { depthTruncated = true },
                )
            } catch (_: SecurityException) {
                return@withContext emptyList()
            } catch (_: IllegalArgumentException) {
                return@withContext emptyList()
            }

        if (depthTruncated) {
            DiagnosticLog.record(
                context,
                "Recursive SAF scan reached the depth limit: folder=$effectiveFolderUriString, maxDepth=$maxDepth",
            )
        }
        candidates
            .asSequence()
            .filter { (doc, _) ->
                scanContext.ensureActive()
                if (orientation == null) return@filter true
                getDocumentUriOrientation(context, doc.name, doc.uri) == orientation
            }.map { (doc, relativeParentSegments) ->
                FileEntry(
                    uri = doc.uri,
                    name = doc.name,
                    size = doc.size,
                    lastModifiedMs = doc.lastModifiedMs,
                    sizeKnown = doc.sizeKnown,
                    lastModifiedKnown = doc.lastModifiedKnown,
                    relativeParentSegments = relativeParentSegments,
                    parentDocumentUri = doc.parentDocumentUri,
                    supportsMove = doc.supportsMove,
                )
            }.toList()
    }

internal suspend fun FileOperationRepository.listMatchingFilesFromFilesystemRoot(
    rootDir: File,
    extensions: List<String>,
    scanSubdirectories: Boolean,
    filenamePattern: String?,
    minFileSizeBytes: Long?,
    maxFileSizeBytes: Long?,
    minAgeDays: Int?,
    maxAgeDays: Int?,
    excludePatterns: List<String>,
    maxDepth: Int,
    orientation: FileOrientation?,
    isRegexPattern: Boolean = false,
    isExcludeRegexPattern: Boolean = false,
): List<FileEntry> {
    val scanContext = currentCoroutineContext()
    val filterContext =
        FileScanFilterContext(
            extensions = extensions,
            filenameRegexes = buildFilenameRegexes(filenamePattern, isRegexPattern),
            isRegexPattern = isRegexPattern,
            excludeRegexes = buildExcludeRegexes(excludePatterns, isExcludeRegexPattern),
            isExcludeRegexPattern = isExcludeRegexPattern,
            minFileSizeBytes = minFileSizeBytes,
            maxFileSizeBytes = maxFileSizeBytes,
            minAgeMs = minAgeDays?.let { TimeUnit.DAYS.toMillis(it.toLong()) },
            maxAgeMs = maxAgeDays?.let { TimeUnit.DAYS.toMillis(it.toLong()) },
            nowMs = System.currentTimeMillis(),
        )
    var depthTruncated = false
    val sequence: Sequence<Pair<File, List<String>>> =
        if (scanSubdirectories) {
            walkDiskFilesWithRelativeParents(
                dir = rootDir,
                maxDepth = maxDepth,
                relativeParentSegments = emptyList(),
                onDepthLimitReached = { depthTruncated = true },
            )
        } else {
            (rootDir.listFiles()?.asSequence() ?: emptySequence())
                .filter { it.isFile }
                .map { it to emptyList() }
        }
    val results =
        sequence
            .onEach { scanContext.ensureActive() }
            .filter { (file, _) ->
                passesCheapScanFilters(
                    fileName = file.name,
                    fileSizeBytes = file.length(),
                    lastModifiedMs = file.lastModified(),
                    filters = filterContext,
                )
            }.filter { (file, _) ->
                if (orientation == null) return@filter true
                val fileOrientation = getDiskFileOrientation(file)
                fileOrientation == orientation
            }.map { (file, relativeParentSegments) ->
                FileEntry(
                    uri = file.toUri(),
                    name = file.name,
                    size = file.length(),
                    lastModifiedMs = file.lastModified(),
                    relativeParentSegments = relativeParentSegments,
                )
            }.toList()
    if (depthTruncated) {
        DiagnosticLog.record(
            context,
            "Recursive filesystem scan reached the depth limit: folder=${rootDir.path}, maxDepth=$maxDepth",
        )
    }
    return results
}

internal fun walkDiskFilesWithRelativeParents(
    dir: File,
    maxDepth: Int,
    relativeParentSegments: List<String>,
    onDepthLimitReached: (() -> Unit)? = null,
): Sequence<Pair<File, List<String>>> =
    sequence {
        if (maxDepth <= 0) return@sequence
        dir.listFiles()?.forEach { child ->
            val segment = child.name.trim()
            if (child.isFile) {
                yield(child to relativeParentSegments)
            } else if (child.isDirectory && segment.isNotEmpty() && segment != "." && segment != "..") {
                if (maxDepth <= 1) {
                    onDepthLimitReached?.invoke()
                } else {
                    yieldAll(
                        walkDiskFilesWithRelativeParents(
                            child,
                            maxDepth - 1,
                            relativeParentSegments + segment,
                            onDepthLimitReached,
                        ),
                    )
                }
            }
        }
    }

internal fun FileOperationRepository.querySafChildren(
    treeUri: Uri,
    parentDocumentId: String,
): List<SafDocEntry> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
    val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
    val projection =
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
    val results = mutableListOf<SafDocEntry>()
    try {
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val flagsIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
            if (idIdx == -1) return emptyList()
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idIdx) ?: continue
                val mimeType = if (mimeIdx != -1) cursor.getString(mimeIdx) else null
                val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                val isFile = !mimeType.isNullOrEmpty() && !isDirectory
                val flags = if (flagsIdx != -1 && !cursor.isNull(flagsIdx)) cursor.getInt(flagsIdx) else 0
                results +=
                    SafDocEntry(
                        documentId = documentId,
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                        name = (if (nameIdx != -1) cursor.getString(nameIdx) else null).orEmpty(),
                        size = if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L,
                        lastModifiedMs = if (modifiedIdx != -1 && !cursor.isNull(modifiedIdx)) cursor.getLong(modifiedIdx) else 0L,
                        sizeKnown = sizeIdx != -1 && !cursor.isNull(sizeIdx),
                        lastModifiedKnown = modifiedIdx != -1 && !cursor.isNull(modifiedIdx),
                        isFile = isFile,
                        isDirectory = isDirectory,
                        parentDocumentUri = parentDocUri,
                        supportsMove = flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0,
                    )
            }
        }
    } catch (_: Exception) {
        return emptyList()
    }
    return results
}

/**
 * True when the folder at [folderDocumentUri] has no children, false when it has some, and null
 * when the provider could not be asked.
 *
 * The three-way answer is the point: a caller deleting empty folders must treat "could not tell"
 * as "leave it alone". [folderDocumentUri] has to be tree-scoped, since a plain document URI
 * carries no grant to enumerate children with — one that isn't reports null rather than empty.
 *
 * Not expressible through `DocumentFile`: a folder URI wrapped by `DocumentFile.fromSingleUri`
 * produces a `SingleDocumentFile`, whose `listFiles()` throws `UnsupportedOperationException`
 * unconditionally.
 */
internal fun FileOperationRepository.isSafFolderEmpty(folderDocumentUri: Uri): Boolean? =
    try {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                folderDocumentUri,
                DocumentsContract.getDocumentId(folderDocumentUri),
            )
        context.contentResolver
            .query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
            )?.use { cursor -> cursor.count == 0 }
    } catch (_: Exception) {
        null
    }

internal fun FileOperationRepository.queryDocumentMetadata(documentUri: Uri): DocumentMetadata? {
    val projection =
        arrayOf(
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
    return try {
        context.contentResolver.query(documentUri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val flagsIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
            val flags = if (flagsIndex != -1 && !cursor.isNull(flagsIndex)) cursor.getInt(flagsIndex) else 0
            DocumentMetadata(
                supportsMove = flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0,
                size =
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        cursor.getLong(sizeIndex)
                    } else {
                        null
                    },
                lastModifiedMs =
                    if (modifiedIndex != -1 && !cursor.isNull(modifiedIndex)) {
                        cursor.getLong(modifiedIndex)
                    } else {
                        null
                    },
            )
        }
    } catch (_: Exception) {
        null
    }
}

internal suspend fun FileOperationRepository.collectSafFiles(
    treeUri: Uri,
    rootDocumentId: String,
    scanSubdirectories: Boolean,
    maxDepth: Int,
    includeFile: (SafDocEntry) -> Boolean = { true },
    onDepthLimitReached: (() -> Unit)? = null,
): List<Pair<SafDocEntry, List<String>>> {
    val scanContext = currentCoroutineContext()
    val out = mutableListOf<Pair<SafDocEntry, List<String>>>()

    fun visit(
        parentDocumentId: String,
        relativeParents: List<String>,
        depth: Int,
    ) {
        if (depth <= 0) return
        for (child in querySafChildren(treeUri, parentDocumentId)) {
            scanContext.ensureActive()
            if (child.isFile && includeFile(child)) {
                out += child to relativeParents
            } else if (scanSubdirectories && child.isDirectory) {
                val segment = child.name.trim()
                if (segment.isNotEmpty() && segment != "." && segment != "..") {
                    if (depth <= 1) {
                        onDepthLimitReached?.invoke()
                    } else {
                        visit(child.documentId, relativeParents + segment, depth - 1)
                    }
                }
            }
        }
    }

    visit(rootDocumentId, emptyList(), if (scanSubdirectories) maxDepth else 1)
    return out
}
