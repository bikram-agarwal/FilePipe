package dev.bikram.filepipe.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.data.storage.isCanonicalPathUnderAllowedSharedStorage
import dev.bikram.filepipe.data.storage.isFilesystemFolderPathString
import dev.bikram.filepipe.data.storage.normalizeFilesystemFolderPath
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.FolderAccessResult
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.PreviewFileResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileOperationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

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
        maxDepth: Int = 5,
        filesystemAccessEnabled: Boolean = false
    ): List<FileEntry> = withContext(Dispatchers.IO) {
        if (isFilesystemFolderPathString(folderUriString)) {
            if (!filesystemAccessEnabled) return@withContext emptyList()
            val canonical = normalizeFilesystemFolderPath(folderUriString) ?: return@withContext emptyList()
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
                maxDepth = maxDepth
            )
        }

        if (!folderUriString.startsWith("content://")) return@withContext emptyList()

        val treeUri = Uri.parse(folderUriString)
        val folder = try {
            DocumentFile.fromTreeUri(context, treeUri)
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } ?: return@withContext emptyList()

        if (!folder.exists() || !folder.canRead()) return@withContext emptyList()

        val lowerExtensions = extensions.map {
            it.lowercase().let { e -> if (e.startsWith(".")) e else ".$e" }
        }.toSet()
        val filenameRegex = filenamePattern?.takeIf { it.isNotBlank() }?.let { globToRegex(it) }
        val excludeRegexes = excludePatterns.filter { it.isNotBlank() }.map { globToRegex(it) }
        val now = System.currentTimeMillis()
        val minAgeMs = minAgeDays?.let { TimeUnit.DAYS.toMillis(it.toLong()) }
        val maxAgeMs = maxAgeDays?.let { TimeUnit.DAYS.toMillis(it.toLong()) }

        val sequence: Sequence<Pair<DocumentFile, List<String>>> = if (scanSubdirectories) {
            walkDocFilesWithRelativeParents(folder, maxDepth, emptyList())
        } else {
            (folder.listFiles()?.asSequence() ?: emptySequence())
                .filter { it.isFile }
                .map { it to emptyList() }
        }

        try {
            sequence
                .filter { (doc, _) ->
                    val ext = ".${doc.name.orEmpty().substringAfterLast('.').lowercase()}"
                    ext in lowerExtensions
                }
                .filter { (doc, _) -> filenameRegex == null || filenameRegex.matches(doc.name.orEmpty()) }
                .filter { (doc, _) -> excludeRegexes.none { it.matches(doc.name.orEmpty()) } }
                .filter { (doc, _) -> minFileSizeBytes == null || doc.length() >= minFileSizeBytes }
                .filter { (doc, _) -> maxFileSizeBytes == null || doc.length() <= maxFileSizeBytes }
                .filter { (doc, _) ->
                    if (minAgeMs == null && maxAgeMs == null) return@filter true
                    val ageMs = now - doc.lastModified()
                    (minAgeMs == null || ageMs >= minAgeMs) && (maxAgeMs == null || ageMs <= maxAgeMs)
                }
                .map { (doc, relativeParentSegments) ->
                    FileEntry(
                        uri = doc.uri,
                        name = doc.name.orEmpty(),
                        size = doc.length(),
                        lastModifiedMs = doc.lastModified(),
                        relativeParentSegments = relativeParentSegments
                    )
                }
                .toList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun listMatchingFilesFromFilesystemRoot(
        rootDir: File,
        extensions: List<String>,
        scanSubdirectories: Boolean,
        filenamePattern: String?,
        minFileSizeBytes: Long?,
        maxFileSizeBytes: Long?,
        minAgeDays: Int?,
        maxAgeDays: Int?,
        excludePatterns: List<String>,
        maxDepth: Int
    ): List<FileEntry> {
        val lowerExtensions = extensions.map {
            it.lowercase().let { e -> if (e.startsWith(".")) e else ".$e" }
        }.toSet()
        val filenameRegex = filenamePattern?.takeIf { it.isNotBlank() }?.let { globToRegex(it) }
        val excludeRegexes = excludePatterns.filter { it.isNotBlank() }.map { globToRegex(it) }
        val now = System.currentTimeMillis()
        val minAgeMs = minAgeDays?.let { TimeUnit.DAYS.toMillis(it.toLong()) }
        val maxAgeMs = maxAgeDays?.let { TimeUnit.DAYS.toMillis(it.toLong()) }
        val sequence: Sequence<Pair<File, List<String>>> = if (scanSubdirectories) {
            walkDiskFilesWithRelativeParents(rootDir, maxDepth, emptyList())
        } else {
            (rootDir.listFiles()?.asSequence() ?: emptySequence())
                .filter { it.isFile }
                .map { it to emptyList() }
        }
        return sequence
            .filter { (file, _) ->
                val ext = ".${file.name.substringAfterLast('.').lowercase()}"
                ext in lowerExtensions
            }
            .filter { (file, _) -> filenameRegex == null || filenameRegex.matches(file.name) }
            .filter { (file, _) -> excludeRegexes.none { it.matches(file.name) } }
            .filter { (file, _) -> minFileSizeBytes == null || file.length() >= minFileSizeBytes }
            .filter { (file, _) -> maxFileSizeBytes == null || file.length() <= maxFileSizeBytes }
            .filter { (file, _) ->
                if (minAgeMs == null && maxAgeMs == null) return@filter true
                val ageMs = now - file.lastModified()
                (minAgeMs == null || ageMs >= minAgeMs) && (maxAgeMs == null || ageMs <= maxAgeMs)
            }
            .map { (file, relativeParentSegments) ->
                FileEntry(
                    uri = file.toUri(),
                    name = file.name,
                    size = file.length(),
                    lastModifiedMs = file.lastModified(),
                    relativeParentSegments = relativeParentSegments
                )
            }
            .toList()
    }

    private fun walkDiskFilesWithRelativeParents(
        dir: File,
        maxDepth: Int,
        relativeParentSegments: List<String>
    ): Sequence<Pair<File, List<String>>> = sequence {
        if (maxDepth <= 0) return@sequence
        dir.listFiles()?.forEach { child ->
            val segment = child.name.trim()
            if (child.isFile) {
                yield(child to relativeParentSegments)
            } else if (child.isDirectory && segment.isNotEmpty() && segment != "." && segment != "..") {
                yieldAll(
                    walkDiskFilesWithRelativeParents(
                        child,
                        maxDepth - 1,
                        relativeParentSegments + segment
                    )
                )
            }
        }
    }

    /**
     * Yields each file with path segments from the scanned tree root to the file's parent
     * (e.g. `Photos/vacation/img.jpg` → `["Photos","vacation"]`).
     */
    private fun walkDocFilesWithRelativeParents(
        dir: DocumentFile,
        maxDepth: Int,
        relativeParentSegments: List<String>
    ): Sequence<Pair<DocumentFile, List<String>>> = sequence {
        if (maxDepth <= 0) return@sequence
        dir.listFiles()?.forEach { child ->
            val segment = child.name?.trim().orEmpty()
            if (child.isFile) {
                yield(child to relativeParentSegments)
            } else if (child.isDirectory && segment.isNotEmpty() && segment != "." && segment != "..") {
                yieldAll(
                    walkDocFilesWithRelativeParents(
                        child,
                        maxDepth - 1,
                        relativeParentSegments + segment
                    )
                )
            }
        }
    }

    suspend fun moveFile(
        sourceEntry: FileEntry,
        destFolderUriString: String,
        conflictPolicy: ConflictPolicy,
        operationMode: OperationMode,
        destFoldersCreatedCollector: MutableCollection<String>? = null,
        filesystemAccessEnabled: Boolean = false
    ): FileMoved = withContext(Dispatchers.IO + NonCancellable) {
        val sourceIsFile = sourceEntry.uri.scheme == "file"
        val destIsFilesystem = isFilesystemFolderPathString(destFolderUriString)

        if (sourceIsFile && !filesystemAccessEnabled) {
            return@withContext FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "All files access is required for this source path"
            )
        }
        if (destIsFilesystem && !filesystemAccessEnabled) {
            return@withContext FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "All files access is required for this destination path"
            )
        }

        when {
            destIsFilesystem && sourceIsFile ->
                moveFileFilesystemToFilesystem(
                    sourceEntry,
                    destFolderUriString,
                    conflictPolicy,
                    operationMode,
                    destFoldersCreatedCollector
                )
            destIsFilesystem && !sourceIsFile ->
                moveFileDocumentToFilesystem(
                    sourceEntry,
                    destFolderUriString,
                    conflictPolicy,
                    operationMode,
                    destFoldersCreatedCollector
                )
            !destIsFilesystem && sourceIsFile ->
                moveFileFilesystemToDocument(
                    sourceEntry,
                    destFolderUriString,
                    conflictPolicy,
                    operationMode,
                    destFoldersCreatedCollector
                )
            else ->
                moveFileDocumentToDocument(
                    sourceEntry,
                    destFolderUriString,
                    conflictPolicy,
                    operationMode,
                    destFoldersCreatedCollector
                )
        }
    }

    private fun moveFileFilesystemToFilesystem(
        sourceEntry: FileEntry,
        destFolderPath: String,
        conflictPolicy: ConflictPolicy,
        operationMode: OperationMode,
        destFoldersCreatedCollector: MutableCollection<String>?
    ): FileMoved {
        val sourcePath = sourceEntry.uri.path ?: return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            success = false,
            errorMessage = "Invalid source path"
        )
        val sourceFile = File(sourcePath)
        if (!sourceFile.isFile || !sourceFile.canRead()) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "Source file not accessible"
            )
        }
        val destRootCanonical = normalizeFilesystemFolderPath(destFolderPath) ?: return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            success = false,
            errorMessage = "Invalid destination folder"
        )
        if (!isCanonicalPathUnderAllowedSharedStorage(destRootCanonical)) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "Destination outside allowed storage"
            )
        }
        val destRoot = File(destRootCanonical)
        if (!destRoot.isDirectory || !destRoot.canWrite()) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "Destination folder not accessible"
            )
        }
        val destParent = ensureDestParentFolderFile(destRoot, sourceEntry.relativeParentSegments, destFoldersCreatedCollector)
            ?: return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "Could not create destination folder structure"
            )
        var destName = sourceEntry.name
        val existing = File(destParent, destName)
        if (existing.exists()) {
            when (conflictPolicy) {
                ConflictPolicy.SKIP -> return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = existing.toUri().toString(),
                    fileSizeBytes = sourceEntry.size,
                    success = true,
                    skipped = true
                )
                ConflictPolicy.OVERWRITE -> existing.delete()
                ConflictPolicy.RENAME_SUFFIX -> {
                    destName = resolveDestNameFile(destParent, sourceEntry.name)
                }
            }
        }
        val destFile = File(destParent, destName)
        return try {
            if (operationMode == OperationMode.MOVE) {
                Files.move(
                    sourceFile.toPath(),
                    destFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            } else {
                Files.copy(
                    sourceFile.toPath(),
                    destFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = destFile.toUri().toString(),
                fileSizeBytes = sourceEntry.size,
                success = true
            )
        } catch (e: Exception) {
            FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = e.message ?: "IO error"
            )
        }
    }

    private fun moveFileFilesystemToDocument(
        sourceEntry: FileEntry,
        destFolderUriString: String,
        conflictPolicy: ConflictPolicy,
        operationMode: OperationMode,
        destFoldersCreatedCollector: MutableCollection<String>?
    ): FileMoved {
        val sourcePath = sourceEntry.uri.path ?: return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            success = false,
            errorMessage = "Invalid source path"
        )
        val sourceFile = File(sourcePath)
        if (!sourceFile.isFile || !sourceFile.canRead()) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "Source file not accessible"
            )
        }
        val destTree = DocumentFile.fromTreeUri(context, Uri.parse(destFolderUriString))
            ?: return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "Destination folder not accessible"
            )
        if (!destTree.exists() || !destTree.canWrite()) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "Destination folder not accessible"
            )
        }
        val destParent = ensureDestParentFolder(destTree, sourceEntry.relativeParentSegments, destFoldersCreatedCollector)
            ?: return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "Could not create destination folder structure"
            )
        val existing = destParent.findFile(sourceEntry.name)
        if (existing != null) {
            when (conflictPolicy) {
                ConflictPolicy.SKIP -> return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = existing.uri.toString(),
                    fileSizeBytes = sourceEntry.size,
                    success = true,
                    skipped = true
                )
                ConflictPolicy.OVERWRITE -> existing.delete()
                ConflictPolicy.RENAME_SUFFIX -> { /* below */ }
            }
        }
        val destName = if (conflictPolicy == ConflictPolicy.RENAME_SUFFIX) {
            resolveDestName(sourceEntry.name, destParent)
        } else {
            sourceEntry.name
        }
        val mimeType = mimeTypeFromName(sourceEntry.name)
        return try {
            val destDoc = destParent.createFile(mimeType, destName)
                ?: return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    success = false,
                    errorMessage = "Could not create destination file"
                )
            FileInputStream(sourceFile).use { input ->
                context.contentResolver.openOutputStream(destDoc.uri)?.use { output ->
                    input.copyTo(output)
                } ?: run {
                    destDoc.delete()
                    return FileMoved(
                        fileName = sourceEntry.name,
                        sourceUri = sourceEntry.uri.toString(),
                        destinationUri = "",
                        fileSizeBytes = sourceEntry.size,
                        success = false,
                        errorMessage = "Could not write destination file"
                    )
                }
            }
            if (operationMode == OperationMode.MOVE) {
                sourceFile.delete()
            }
            FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = destDoc.uri.toString(),
                fileSizeBytes = sourceEntry.size,
                success = true
            )
        } catch (e: IOException) {
            FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = e.message ?: "IO error"
            )
        }
    }

    private fun moveFileDocumentToFilesystem(
        sourceEntry: FileEntry,
        destFolderPath: String,
        conflictPolicy: ConflictPolicy,
        operationMode: OperationMode,
        destFoldersCreatedCollector: MutableCollection<String>?
    ): FileMoved {
        val destRootCanonical = normalizeFilesystemFolderPath(destFolderPath) ?: return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            success = false,
            errorMessage = "Invalid destination folder"
        )
        if (!isCanonicalPathUnderAllowedSharedStorage(destRootCanonical)) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "Destination outside allowed storage"
            )
        }
        val destRoot = File(destRootCanonical)
        if (!destRoot.isDirectory || !destRoot.canWrite()) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "Destination folder not accessible"
            )
        }
        val destParent = ensureDestParentFolderFile(destRoot, sourceEntry.relativeParentSegments, destFoldersCreatedCollector)
            ?: return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "Could not create destination folder structure"
            )
        var destName = sourceEntry.name
        val existing = File(destParent, destName)
        if (existing.exists()) {
            when (conflictPolicy) {
                ConflictPolicy.SKIP -> return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = existing.toUri().toString(),
                    fileSizeBytes = sourceEntry.size,
                    success = true,
                    skipped = true
                )
                ConflictPolicy.OVERWRITE -> existing.delete()
                ConflictPolicy.RENAME_SUFFIX -> {
                    destName = resolveDestNameFile(destParent, sourceEntry.name)
                }
            }
        }
        val destFile = File(destParent, destName)
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceEntry.uri)
                ?: return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    success = false,
                    errorMessage = "Could not read source file"
                )
            inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (sourceEntry.size > 0L && (!destFile.exists() || destFile.length() == 0L)) {
                destFile.delete()
                return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    success = false,
                    errorMessage = "No data was copied"
                )
            }
            if (operationMode == OperationMode.MOVE) {
                DocumentFile.fromSingleUri(context, sourceEntry.uri)?.delete()
            }
            FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = destFile.toUri().toString(),
                fileSizeBytes = sourceEntry.size,
                success = true
            )
        } catch (e: IOException) {
            destFile.delete()
            FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = e.message ?: "IO error"
            )
        }
    }

    private fun moveFileDocumentToDocument(
        sourceEntry: FileEntry,
        destFolderUriString: String,
        conflictPolicy: ConflictPolicy,
        operationMode: OperationMode,
        destFoldersCreatedCollector: MutableCollection<String>?
    ): FileMoved {
        val destTree = DocumentFile.fromTreeUri(context, Uri.parse(destFolderUriString))
        if (destTree == null || !destTree.exists() || !destTree.canWrite()) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "Destination folder not accessible"
            )
        }

        val destParent = ensureDestParentFolder(
            destTree,
            sourceEntry.relativeParentSegments,
            destFoldersCreatedCollector
        )
            ?: return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "Could not create destination folder structure"
            )

        val existing = destParent.findFile(sourceEntry.name)
        if (existing != null) {
            when (conflictPolicy) {
                ConflictPolicy.SKIP -> return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = existing.uri.toString(),
                    fileSizeBytes = sourceEntry.size,
                    success = true,
                    skipped = true
                )
                ConflictPolicy.OVERWRITE -> existing.delete()
                ConflictPolicy.RENAME_SUFFIX -> { /* handled below */ }
            }
        }

        val destName = if (conflictPolicy == ConflictPolicy.RENAME_SUFFIX) {
            resolveDestName(sourceEntry.name, destParent)
        } else {
            sourceEntry.name
        }

        val mimeType = runCatching { context.contentResolver.getType(sourceEntry.uri) }.getOrNull()
            ?: mimeTypeFromName(sourceEntry.name)

        return try {
            val destFile = destParent.createFile(mimeType, destName)
                ?: return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    success = false,
                    errorMessage = "Could not create destination file"
                )

            val inputStream = context.contentResolver.openInputStream(sourceEntry.uri)
            if (inputStream == null) {
                destFile.delete()
                return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    success = false,
                    errorMessage = "Could not read source file"
                )
            }

            val outputStream = context.contentResolver.openOutputStream(destFile.uri)
            if (outputStream == null) {
                inputStream.close()
                destFile.delete()
                return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    success = false,
                    errorMessage = "Could not write destination file"
                )
            }

            val bytesCopied = try {
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: IOException) {
                destFile.delete()
                return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    success = false,
                    errorMessage = e.message ?: "IO error"
                )
            }

            if (sourceEntry.size > 0L && bytesCopied == 0L) {
                destFile.delete()
                return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    success = false,
                    errorMessage = "No data was copied"
                )
            }

            if (operationMode == OperationMode.MOVE) {
                DocumentFile.fromSingleUri(context, sourceEntry.uri)?.delete()
            }

            FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = destFile.uri.toString(),
                fileSizeBytes = sourceEntry.size,
                success = true
            )
        } catch (e: IOException) {
            FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = e.message ?: "IO error"
            )
        }
    }

    private fun ensureDestParentFolderFile(
        destRoot: File,
        relativeParentSegments: List<String>,
        destFoldersCreatedCollector: MutableCollection<String>?
    ): File? {
        var current = destRoot
        for (rawSegment in relativeParentSegments) {
            val segment = rawSegment.trim()
            if (segment.isEmpty() || segment == "." || segment == "..") continue
            val next = File(current, segment)
            when {
                next.isDirectory -> current = next
                next.exists() -> return null
                else -> {
                    if (!next.mkdirs() && !next.isDirectory) return null
                    destFoldersCreatedCollector?.add(next.toUri().toString())
                    current = next
                }
            }
        }
        return current
    }

    private fun resolveDestNameFile(parent: File, name: String): String {
        if (!File(parent, name).exists()) return name
        val ext = name.substringAfterLast('.', "")
        val base = if (ext.isNotEmpty()) name.dropLast(ext.length + 1) else name
        var index = 1
        while (true) {
            val candidate = if (ext.isNotEmpty()) "$base($index).$ext" else "$base($index)"
            if (!File(parent, candidate).exists()) return candidate
            index++
        }
    }

    suspend fun simulateMove(
        sourceEntry: FileEntry,
        destFolderUriString: String,
        conflictPolicy: ConflictPolicy,
        filesystemAccessEnabled: Boolean = false
    ): PreviewFileResult = withContext(Dispatchers.IO) {
        val pathSuffix = relativePathSuffixForDisplay(sourceEntry.relativeParentSegments, sourceEntry.name)
        val simulatedRootPath = "$destFolderUriString/$pathSuffix"

        if (isFilesystemFolderPathString(destFolderUriString)) {
            if (!filesystemAccessEnabled) {
                return@withContext PreviewFileResult(
                    fileName = sourceEntry.name,
                    sourcePath = sourceEntry.uri.toString(),
                    simulatedDestPath = simulatedRootPath,
                    wouldSkip = false,
                    wouldOverwrite = false,
                    renamedTo = null,
                    sizeBytes = sourceEntry.size
                )
            }
            val canonical = normalizeFilesystemFolderPath(destFolderUriString) ?: return@withContext PreviewFileResult(
                fileName = sourceEntry.name,
                sourcePath = sourceEntry.uri.toString(),
                simulatedDestPath = simulatedRootPath,
                wouldSkip = false,
                wouldOverwrite = false,
                renamedTo = null,
                sizeBytes = sourceEntry.size
            )
            if (!isCanonicalPathUnderAllowedSharedStorage(canonical)) {
                return@withContext PreviewFileResult(
                    fileName = sourceEntry.name,
                    sourcePath = sourceEntry.uri.toString(),
                    simulatedDestPath = simulatedRootPath,
                    wouldSkip = false,
                    wouldOverwrite = false,
                    renamedTo = null,
                    sizeBytes = sourceEntry.size
                )
            }
            val destRoot = File(canonical)
            if (!destRoot.isDirectory) {
                return@withContext PreviewFileResult(
                    fileName = sourceEntry.name,
                    sourcePath = sourceEntry.uri.toString(),
                    simulatedDestPath = simulatedRootPath,
                    wouldSkip = false,
                    wouldOverwrite = false,
                    renamedTo = null,
                    sizeBytes = sourceEntry.size
                )
            }
            when (val resolution = peekDestParentForPreviewFile(destRoot, sourceEntry.relativeParentSegments)) {
                is DestParentFilePreview.Partial, is DestParentFilePreview.BlockedByFile -> {
                    return@withContext PreviewFileResult(
                        fileName = sourceEntry.name,
                        sourcePath = sourceEntry.uri.toString(),
                        simulatedDestPath = simulatedRootPath,
                        wouldSkip = false,
                        wouldOverwrite = false,
                        renamedTo = null,
                        sizeBytes = sourceEntry.size
                    )
                }
                is DestParentFilePreview.Resolved -> {
                    val existing = File(resolution.parent, sourceEntry.name)
                    if (!existing.exists()) {
                        return@withContext PreviewFileResult(
                            fileName = sourceEntry.name,
                            sourcePath = sourceEntry.uri.toString(),
                            simulatedDestPath = simulatedRootPath,
                            wouldSkip = false,
                            wouldOverwrite = false,
                            renamedTo = null,
                            sizeBytes = sourceEntry.size
                        )
                    }
                    when (conflictPolicy) {
                        ConflictPolicy.SKIP -> return@withContext PreviewFileResult(
                            fileName = sourceEntry.name,
                            sourcePath = sourceEntry.uri.toString(),
                            simulatedDestPath = existing.toUri().toString(),
                            wouldSkip = true,
                            wouldOverwrite = false,
                            renamedTo = null,
                            sizeBytes = sourceEntry.size
                        )
                        ConflictPolicy.OVERWRITE -> return@withContext PreviewFileResult(
                            fileName = sourceEntry.name,
                            sourcePath = sourceEntry.uri.toString(),
                            simulatedDestPath = existing.toUri().toString(),
                            wouldSkip = false,
                            wouldOverwrite = true,
                            renamedTo = null,
                            sizeBytes = sourceEntry.size
                        )
                        ConflictPolicy.RENAME_SUFFIX -> {
                            val resolvedName = resolveDestNameFile(resolution.parent, sourceEntry.name)
                            return@withContext PreviewFileResult(
                                fileName = sourceEntry.name,
                                sourcePath = sourceEntry.uri.toString(),
                                simulatedDestPath = buildSimulatedDestUriString(
                                    destFolderUriString,
                                    sourceEntry.relativeParentSegments,
                                    resolvedName
                                ),
                                wouldSkip = false,
                                wouldOverwrite = false,
                                renamedTo = if (resolvedName != sourceEntry.name) resolvedName else null,
                                sizeBytes = sourceEntry.size
                            )
                        }
                    }
                }
            }
        }

        val destTree = DocumentFile.fromTreeUri(context, Uri.parse(destFolderUriString))
        if (destTree == null || !destTree.exists()) {
            return@withContext PreviewFileResult(
                fileName = sourceEntry.name,
                sourcePath = sourceEntry.uri.toString(),
                simulatedDestPath = simulatedRootPath,
                wouldSkip = false,
                wouldOverwrite = false,
                renamedTo = null,
                sizeBytes = sourceEntry.size
            )
        }

        val resolution = peekDestParentForPreview(destTree, sourceEntry.relativeParentSegments)
        when (resolution) {
            is DestParentPreview.Partial, is DestParentPreview.BlockedByFile -> {
                PreviewFileResult(
                    fileName = sourceEntry.name,
                    sourcePath = sourceEntry.uri.toString(),
                    simulatedDestPath = simulatedRootPath,
                    wouldSkip = false,
                    wouldOverwrite = false,
                    renamedTo = null,
                    sizeBytes = sourceEntry.size
                )
            }
            is DestParentPreview.Resolved -> {
                val existing = resolution.parent.findFile(sourceEntry.name)
                if (existing == null) {
                    return@withContext PreviewFileResult(
                        fileName = sourceEntry.name,
                        sourcePath = sourceEntry.uri.toString(),
                        simulatedDestPath = simulatedRootPath,
                        wouldSkip = false,
                        wouldOverwrite = false,
                        renamedTo = null,
                        sizeBytes = sourceEntry.size
                    )
                }
                when (conflictPolicy) {
                    ConflictPolicy.SKIP -> PreviewFileResult(
                        fileName = sourceEntry.name,
                        sourcePath = sourceEntry.uri.toString(),
                        simulatedDestPath = existing.uri.toString(),
                        wouldSkip = true,
                        wouldOverwrite = false,
                        renamedTo = null,
                        sizeBytes = sourceEntry.size
                    )
                    ConflictPolicy.OVERWRITE -> PreviewFileResult(
                        fileName = sourceEntry.name,
                        sourcePath = sourceEntry.uri.toString(),
                        simulatedDestPath = existing.uri.toString(),
                        wouldSkip = false,
                        wouldOverwrite = true,
                        renamedTo = null,
                        sizeBytes = sourceEntry.size
                    )
                    ConflictPolicy.RENAME_SUFFIX -> {
                        val resolvedName = resolveDestName(sourceEntry.name, resolution.parent)
                        PreviewFileResult(
                            fileName = sourceEntry.name,
                            sourcePath = sourceEntry.uri.toString(),
                            simulatedDestPath = buildSimulatedDestUriString(
                                destFolderUriString,
                                sourceEntry.relativeParentSegments,
                                resolvedName
                            ),
                            wouldSkip = false,
                            wouldOverwrite = false,
                            renamedTo = if (resolvedName != sourceEntry.name) resolvedName else null,
                            sizeBytes = sourceEntry.size
                        )
                    }
                }
            }
        }
    }

    private sealed class DestParentFilePreview {
        data class Resolved(val parent: File) : DestParentFilePreview()
        data object Partial : DestParentFilePreview()
        data object BlockedByFile : DestParentFilePreview()
    }

    private fun peekDestParentForPreviewFile(
        destRoot: File,
        relativeParentSegments: List<String>
    ): DestParentFilePreview {
        var current = destRoot
        for (rawSegment in relativeParentSegments) {
            val segment = rawSegment.trim()
            if (segment.isEmpty() || segment == "." || segment == "..") continue
            val next = File(current, segment)
            when {
                !next.exists() -> return DestParentFilePreview.Partial
                next.isDirectory -> current = next
                else -> return DestParentFilePreview.BlockedByFile
            }
        }
        return DestParentFilePreview.Resolved(current)
    }

    private val accessCache = java.util.concurrent.ConcurrentHashMap<String, Pair<FolderAccessResult, Long>>()
    private val ACCESS_CACHE_TTL_MS = 5_000L

    fun resolveFolderAccess(
        folderPathOrUri: String,
        filesystemAccessEnabled: Boolean = false
    ): FolderAccessResult {
        val cacheKey = "${folderPathOrUri}\u0000$filesystemAccessEnabled"
        val cached = accessCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.second < ACCESS_CACHE_TTL_MS) {
            return cached.first
        }
        val resolved = when {
            isFilesystemFolderPathString(folderPathOrUri) -> {
                when {
                    !filesystemAccessEnabled -> FolderAccessResult.PermissionDenied
                    else -> {
                        val canonical = normalizeFilesystemFolderPath(folderPathOrUri)
                        when {
                            canonical == null -> FolderAccessResult.Unavailable
                            !isCanonicalPathUnderAllowedSharedStorage(canonical) -> FolderAccessResult.Unavailable
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
            folderPathOrUri.startsWith("content://") -> try {
                val document = DocumentFile.fromTreeUri(context, Uri.parse(folderPathOrUri))
                when {
                    document == null -> FolderAccessResult.Unavailable
                    !document.exists() || !document.canRead() -> FolderAccessResult.Unavailable
                    else -> FolderAccessResult.Accessible
                }
            } catch (_: SecurityException) {
                FolderAccessResult.PermissionDenied
            }
            else -> FolderAccessResult.Unavailable
        }
        accessCache[cacheKey] = resolved to System.currentTimeMillis()
        return resolved
    }

    fun isAccessible(folderPathOrUri: String, filesystemAccessEnabled: Boolean = false): Boolean =
        resolveFolderAccess(folderPathOrUri, filesystemAccessEnabled) == FolderAccessResult.Accessible

    fun invalidateAccessCache() {
        accessCache.clear()
    }

    private fun ensureDestParentFolder(
        destTree: DocumentFile,
        relativeParentSegments: List<String>,
        destFoldersCreatedCollector: MutableCollection<String>? = null
    ): DocumentFile? {
        var current = destTree
        for (rawSegment in relativeParentSegments) {
            val segment = rawSegment.trim()
            if (segment.isEmpty() || segment == "." || segment == "..") continue
            val next = current.findFile(segment)
            current = when {
                next != null && next.isDirectory -> next
                next != null -> return null
                else -> {
                    val created = current.createDirectory(segment) ?: return null
                    destFoldersCreatedCollector?.add(created.uri.toString())
                    created
                }
            }
        }
        return current
    }

    private fun peekDestParentForPreview(
        destTree: DocumentFile,
        relativeParentSegments: List<String>
    ): DestParentPreview {
        var current = destTree
        for (rawSegment in relativeParentSegments) {
            val segment = rawSegment.trim()
            if (segment.isEmpty() || segment == "." || segment == "..") continue
            val next = current.findFile(segment)
            when {
                next == null -> return DestParentPreview.Partial
                !next.isDirectory -> return DestParentPreview.BlockedByFile
                else -> current = next
            }
        }
        return DestParentPreview.Resolved(current)
    }

    private fun relativePathSuffixForDisplay(relativeParentSegments: List<String>, fileName: String): String {
        val clean = relativeParentSegments.map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        return if (clean.isEmpty()) fileName else clean.joinToString("/", postfix = "/") + fileName
    }

    private fun buildSimulatedDestUriString(
        destFolderUriString: String,
        relativeParentSegments: List<String>,
        fileName: String
    ): String {
        val clean = relativeParentSegments.map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        return if (clean.isEmpty()) {
            "$destFolderUriString/$fileName"
        } else {
            "$destFolderUriString/${clean.joinToString("/")}/$fileName"
        }
    }

    private sealed class DestParentPreview {
        data class Resolved(val parent: DocumentFile) : DestParentPreview()
        data object Partial : DestParentPreview()
        data object BlockedByFile : DestParentPreview()
    }

    private fun resolveDestName(name: String, destTree: DocumentFile): String {
        if (destTree.findFile(name) == null) return name
        val ext = name.substringAfterLast('.', "")
        val base = if (ext.isNotEmpty()) name.dropLast(ext.length + 1) else name
        var n = 1
        while (true) {
            val candidate = if (ext.isNotEmpty()) "$base($n).$ext" else "$base($n)"
            if (destTree.findFile(candidate) == null) return candidate
            n++
        }
    }

    private fun mimeTypeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun globToRegex(pattern: String): Regex {
        val sb = StringBuilder("^")
        for (ch in pattern) {
            when (ch) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                '.', '(', ')', '[', ']', '^', '$', '+', '{', '}', '|', '\\' -> {
                    sb.append('\\')
                    sb.append(ch)
                }
                else -> sb.append(ch)
            }
        }
        sb.append("$")
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }
}

data class FileEntry(
    val uri: Uri,
    val name: String,
    val size: Long,
    val lastModifiedMs: Long = 0L,
    /**
     * Directory names from the scanned source tree root down to this file's parent
     * (not including the file name). Empty when the file sits directly under the source root.
     */
    val relativeParentSegments: List<String> = emptyList()
)
