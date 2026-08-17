package dev.bikram.filepipe.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.storage.isCanonicalPathUnderAllowedSharedStorage
import dev.bikram.filepipe.data.storage.isFilesystemFolderPathString
import dev.bikram.filepipe.data.storage.normalizeFilesystemFolderPath
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.PreviewFileResult
import dev.bikram.filepipe.domain.model.resolveRenameSuffixName
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.abs

internal fun FileOperationRepository.moveFileFilesystemToFilesystem(
    sourceEntry: FileEntry,
    destFolderPath: String,
    conflictPolicy: ConflictPolicy,
    operationMode: OperationMode,
    destFoldersCreatedCollector: MutableCollection<String>?,
): FileMoved {
    val sourcePath =
        sourceEntry.uri.path ?: return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = "Invalid source path",
        )
    val sourceFile = File(sourcePath)
    if (!sourceFile.isFile || !sourceFile.canRead()) {
        return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = "Source file not accessible",
        )
    }
    val destRootCanonical =
        normalizeFilesystemFolderPath(destFolderPath) ?: return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = "Invalid destination folder",
        )
    if (!isCanonicalPathUnderAllowedSharedStorage(destRootCanonical)) {
        return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = "Destination outside allowed storage",
        )
    }
    val destRoot = File(destRootCanonical)
    if (!destRoot.isDirectory || !destRoot.canWrite()) {
        return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = "Destination folder not accessible",
        )
    }
    val destParent =
        ensureDestParentFolderFile(destRoot, sourceEntry.relativeParentSegments, destFoldersCreatedCollector)
            ?: return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = "Could not create destination folder structure",
            )
    var destName = sourceEntry.name
    val existing = File(destParent, destName)
    if (existing.exists()) {
        when (conflictPolicy) {
            ConflictPolicy.SKIP -> {
                return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = existing.toUri().toString(),
                    fileSizeBytes = sourceEntry.size,
                    relativeParentSegments = sourceEntry.relativeParentSegments,
                    success = true,
                    skipped = true,
                )
            }

            ConflictPolicy.OVERWRITE -> {}

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
                StandardCopyOption.REPLACE_EXISTING,
            )
        } else {
            // Files.move needs no equivalent option: a same-volume move is a rename, and the
            // cross-volume fallback already forces attribute copying
            // (sun.nio.fs.UnixCopyFile.Flags.fromMoveOptions) — passing COPY_ATTRIBUTES to it
            // would in fact throw UnsupportedOperationException.
            val sourceLastModifiedMs = sourceFile.lastModified()
            Files.copy(
                sourceFile.toPath(),
                destFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES,
            )
            // Read off the source rather than from scan data, which may predate the run.
            preserveLastModified(destFile, sourceLastModifiedMs)
        }
        queueMediaScanForFile(destFile.path)
        if (operationMode == OperationMode.MOVE) queueMediaScanForFile(sourceFile.path)
        FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = destFile.toUri().toString(),
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = true,
        )
    } catch (e: Exception) {
        FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = e.message ?: "IO error",
        )
    }
}

internal fun FileOperationRepository.moveFileFilesystemToDocument(
    sourceEntry: FileEntry,
    destFolderUriString: String,
    conflictPolicy: ConflictPolicy,
    operationMode: OperationMode,
    destFoldersCreatedCollector: MutableCollection<String>?,
    destinationFolderCache: DestinationFolderCache?,
): FileMoved {
    val sourcePath =
        sourceEntry.uri.path ?: return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = "Invalid source path",
        )
    val sourceFile = File(sourcePath)
    if (!sourceFile.isFile || !sourceFile.canRead()) {
        return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = "Source file not accessible",
        )
    }

    val cachedDestTree = destinationFolderCache?.safRoots?.get(destFolderUriString)
    val destTree =
        try {
            cachedDestTree ?: DocumentFile.fromTreeUri(context, destFolderUriString.toUri())
        } catch (e: SecurityException) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = e.message ?: "Permission denied for destination folder",
            )
        } ?: return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = "Invalid destination folder URI",
        )
    if (cachedDestTree == null) {
        destinationFolderCache?.safRoots?.put(destFolderUriString, destTree)
    }

    if (!destTree.exists() || !destTree.canWrite()) {
        return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = "Destination folder not accessible",
        )
    }

    val destParent =
        ensureDestParentFolder(
            destTree = destTree,
            destinationRoot = destFolderUriString,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            destFoldersCreatedCollector = destFoldersCreatedCollector,
            destinationFolderCache = destinationFolderCache,
        )
            ?: return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = "Could not create destination folder structure",
            )

    val existing = destParent.findFile(sourceEntry.name)
    if (existing != null) {
        when (conflictPolicy) {
            ConflictPolicy.SKIP -> {
                return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = existing.uri.toString(),
                    fileSizeBytes = sourceEntry.size,
                    relativeParentSegments = sourceEntry.relativeParentSegments,
                    success = true,
                    skipped = true,
                )
            }

            ConflictPolicy.OVERWRITE -> {
                existing.delete()
            }

            ConflictPolicy.RENAME_SUFFIX -> {}
        }
    }

    val destName =
        if (conflictPolicy == ConflictPolicy.RENAME_SUFFIX) {
            resolveDestName(sourceEntry.name, destParent)
        } else {
            sourceEntry.name
        }

    val mimeType = mimeTypeFromName(sourceEntry.name)
    val destFile =
        destParent.createFile(mimeType, destName)
            ?: return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = "Could not create destination file",
            )

    return try {
        val inputStream = FileInputStream(sourceFile)
        val outputStream = context.contentResolver.openOutputStream(destFile.uri)
        if (outputStream == null) {
            inputStream.close()
            destFile.delete()
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = "Could not write destination file",
            )
        }
        val bytesCopied = copyStreamWithProgress(inputStream, outputStream)
        if (!isCompleteCopy(sourceEntry.size, bytesCopied, sourceEntry.sizeKnown)) {
            val destinationRemoved = runCatching { destFile.delete() }.getOrDefault(false)
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = if (destinationRemoved) "" else destFile.uri.toString(),
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = context.getString(R.string.file_operation_incomplete_copy),
            )
        }

        if (operationMode == OperationMode.MOVE) {
            val sourceDeleted =
                try {
                    sourceFile.delete()
                } catch (_: SecurityException) {
                    false
                }
            if (!sourceDeleted) {
                return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = destFile.uri.toString(),
                    fileSizeBytes = sourceEntry.size,
                    relativeParentSegments = sourceEntry.relativeParentSegments,
                    success = false,
                    errorMessage = context.getString(R.string.file_operation_source_delete_failed),
                )
            }
            // The SAF destination is the provider's to reindex; the vacated source is ours.
            queueMediaScanForFile(sourceFile.path)
        }

        FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = destFile.uri.toString(),
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = true,
        )
    } catch (e: IOException) {
        destFile.delete()
        FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = e.message ?: "IO error",
        )
    }
}

internal fun FileOperationRepository.moveFileDocumentToFilesystem(
    sourceEntry: FileEntry,
    destFolderPath: String,
    conflictPolicy: ConflictPolicy,
    operationMode: OperationMode,
    destFoldersCreatedCollector: MutableCollection<String>?,
): FileMoved {
    val destRootCanonical =
        normalizeFilesystemFolderPath(destFolderPath) ?: return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = "Invalid destination folder",
        )
    if (!isCanonicalPathUnderAllowedSharedStorage(destRootCanonical)) {
        return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = "Destination outside allowed storage",
        )
    }
    val destRoot = File(destRootCanonical)
    if (!destRoot.isDirectory || !destRoot.canWrite()) {
        return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = "Destination folder not accessible",
        )
    }
    val destParent =
        ensureDestParentFolderFile(destRoot, sourceEntry.relativeParentSegments, destFoldersCreatedCollector)
            ?: return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = "Could not create destination folder structure",
            )
    var destName = sourceEntry.name
    val existing = File(destParent, destName)
    if (existing.exists()) {
        when (conflictPolicy) {
            ConflictPolicy.SKIP -> {
                return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = existing.toUri().toString(),
                    fileSizeBytes = sourceEntry.size,
                    relativeParentSegments = sourceEntry.relativeParentSegments,
                    success = true,
                    skipped = true,
                )
            }

            ConflictPolicy.OVERWRITE -> {}

            ConflictPolicy.RENAME_SUFFIX -> {
                destName = resolveDestNameFile(destParent, sourceEntry.name)
            }
        }
    }
    val destFile = File(destParent, destName)
    return try {
        val inputStream =
            context.contentResolver.openInputStream(sourceEntry.uri)
                ?: return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    relativeParentSegments = sourceEntry.relativeParentSegments,
                    success = false,
                    errorMessage = "Could not read source document",
                )
        val outputStream = FileOutputStream(destFile)
        val bytesCopied = copyStreamWithProgress(inputStream, outputStream)
        if (!isCompleteCopy(sourceEntry.size, bytesCopied, sourceEntry.sizeKnown)) {
            val destinationRemoved = runCatching { destFile.delete() }.getOrDefault(false)
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = if (destinationRemoved) "" else destFile.toUri().toString(),
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = context.getString(R.string.file_operation_incomplete_copy),
            )
        }

        // A stream copy has no equivalent of COPY_ATTRIBUTES, so stamp the timestamp on by hand.
        // The scan is the only source of it here — the source is a document, not a File.
        if (sourceEntry.lastModifiedKnown) {
            preserveLastModified(destFile, sourceEntry.lastModifiedMs)
        }

        if (operationMode == OperationMode.MOVE) {
            val sourceDeleted =
                runCatching {
                    DocumentFile.fromSingleUri(context, sourceEntry.uri)?.delete() == true
                }.getOrDefault(false)
            if (!sourceDeleted) {
                return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = destFile.toUri().toString(),
                    fileSizeBytes = sourceEntry.size,
                    relativeParentSegments = sourceEntry.relativeParentSegments,
                    success = false,
                    errorMessage = context.getString(R.string.file_operation_source_delete_failed),
                )
            }
        }

        queueMediaScanForFile(destFile.path)
        FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = destFile.toUri().toString(),
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = true,
        )
    } catch (e: IOException) {
        destFile.delete()
        FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = e.message ?: "IO error",
        )
    }
}

internal fun FileOperationRepository.moveFileDocumentToDocument(
    sourceEntry: FileEntry,
    destFolderUriString: String,
    conflictPolicy: ConflictPolicy,
    operationMode: OperationMode,
    destFoldersCreatedCollector: MutableCollection<String>?,
    destinationFolderCache: DestinationFolderCache?,
): FileMoved {
    val cachedDestTree = destinationFolderCache?.safRoots?.get(destFolderUriString)
    val destTree =
        try {
            cachedDestTree ?: DocumentFile.fromTreeUri(context, destFolderUriString.toUri())
        } catch (e: SecurityException) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = e.message ?: "Permission denied for destination folder",
            )
        } ?: return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = "Invalid destination folder URI",
        )
    if (cachedDestTree == null) {
        destinationFolderCache?.safRoots?.put(destFolderUriString, destTree)
    }

    if (!destTree.exists() || !destTree.canWrite()) {
        return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = "Destination folder not accessible",
        )
    }

    val destParent =
        ensureDestParentFolder(
            destTree = destTree,
            destinationRoot = destFolderUriString,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            destFoldersCreatedCollector = destFoldersCreatedCollector,
            destinationFolderCache = destinationFolderCache,
        )
            ?: return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = "Could not create destination folder structure",
            )

    val existing = destParent.findFile(sourceEntry.name)
    if (existing != null) {
        when (conflictPolicy) {
            ConflictPolicy.SKIP -> {
                return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = existing.uri.toString(),
                    fileSizeBytes = sourceEntry.size,
                    relativeParentSegments = sourceEntry.relativeParentSegments,
                    success = true,
                    skipped = true,
                )
            }

            ConflictPolicy.OVERWRITE -> {
                existing.delete()
            }

            ConflictPolicy.RENAME_SUFFIX -> {}
        }
    }

    val destName =
        if (conflictPolicy == ConflictPolicy.RENAME_SUFFIX) {
            resolveDestName(sourceEntry.name, destParent)
        } else {
            sourceEntry.name
        }

    tryMoveDocumentInProvider(
        sourceEntry = sourceEntry,
        destParent = destParent,
        destName = destName,
        operationMode = operationMode,
    )?.let { return it }

    val mimeType =
        runCatching { context.contentResolver.getType(sourceEntry.uri) }.getOrNull()
            ?: mimeTypeFromName(sourceEntry.name)

    return try {
        val destFile =
            destParent.createFile(mimeType, destName)
                ?: return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    relativeParentSegments = sourceEntry.relativeParentSegments,
                    success = false,
                    errorMessage = "Could not create destination file",
                )

        val inputStream = context.contentResolver.openInputStream(sourceEntry.uri)
        if (inputStream == null) {
            destFile.delete()
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = "Could not read source file",
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
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = "Could not write destination file",
            )
        }

        val bytesCopied = copyStreamWithProgress(inputStream, outputStream)
        if (!isCompleteCopy(sourceEntry.size, bytesCopied, sourceEntry.sizeKnown)) {
            val destinationRemoved = runCatching { destFile.delete() }.getOrDefault(false)
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = if (destinationRemoved) "" else destFile.uri.toString(),
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = context.getString(R.string.file_operation_incomplete_copy),
            )
        }

        if (operationMode == OperationMode.MOVE) {
            val sourceDeleted =
                runCatching {
                    DocumentFile
                        .fromSingleUri(context, sourceEntry.uri)
                        ?.delete() == true
                }.getOrDefault(false)
            if (!sourceDeleted) {
                return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = destFile.uri.toString(),
                    fileSizeBytes = sourceEntry.size,
                    relativeParentSegments = sourceEntry.relativeParentSegments,
                    success = false,
                    errorMessage = context.getString(R.string.file_operation_source_delete_failed),
                )
            }
        }

        FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = destFile.uri.toString(),
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = true,
        )
    } catch (e: IOException) {
        FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = false,
            errorMessage = e.message ?: "IO error",
        )
    }
}

internal fun FileOperationRepository.deleteFile(
    sourceEntry: FileEntry,
    filesystemAccessEnabled: Boolean,
    requireUnchangedSource: Boolean,
): FileMoved {
    val sourceIsFile = sourceEntry.uri.scheme == "file"
    if (sourceIsFile) {
        if (!filesystemAccessEnabled) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = "All files access is required for this source path",
            )
        }
        val path = sourceEntry.uri.path
        if (path.isNullOrBlank()) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = "Invalid source path",
            )
        }
        val sourceFile = File(path)
        if (!sourceFile.isFile) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = "Source file not accessible",
            )
        }
        val sourceChanged =
            requireUnchangedSource &&
                (
                    sourceFile.length() != sourceEntry.size ||
                        (
                            sourceEntry.lastModifiedMs > 0L &&
                                sourceFile.lastModified() != sourceEntry.lastModifiedMs
                        )
                )
        if (sourceChanged) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = context.getString(R.string.file_operation_source_changed_after_confirmation),
            )
        }
        val deleted =
            try {
                sourceFile.delete()
            } catch (_: SecurityException) {
                false
            }
        if (deleted) queueMediaScanForFile(sourceFile.path)
        return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = deleted,
            errorMessage = if (deleted) null else "Could not delete file",
        )
    } else {
        val doc =
            try {
                DocumentFile.fromSingleUri(context, sourceEntry.uri)
            } catch (_: SecurityException) {
                null
            }
        if (doc == null || !doc.exists()) {
            return FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                success = false,
                errorMessage = "Source document not accessible",
            )
        }
        if (requireUnchangedSource) {
            val meta = queryDocumentMetadata(sourceEntry.uri)
            val docSize = meta?.size
            val docModified = meta?.lastModifiedMs
            val sizeChanged = docSize != null && docSize != sourceEntry.size
            val modChanged =
                sourceEntry.lastModifiedMs > 0L && docModified != null && docModified > 0L && docModified != sourceEntry.lastModifiedMs
            if (sizeChanged || modChanged) {
                return FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    relativeParentSegments = sourceEntry.relativeParentSegments,
                    success = false,
                    errorMessage = context.getString(R.string.file_operation_source_changed_after_confirmation),
                )
            }
        }
        val deleted =
            try {
                doc.delete()
            } catch (_: SecurityException) {
                false
            }
        return FileMoved(
            fileName = sourceEntry.name,
            sourceUri = sourceEntry.uri.toString(),
            destinationUri = "",
            fileSizeBytes = sourceEntry.size,
            relativeParentSegments = sourceEntry.relativeParentSegments,
            success = deleted,
            errorMessage = if (deleted) null else "Could not delete document",
        )
    }
}

internal fun copyStreamWithProgress(
    inputStream: InputStream,
    outputStream: OutputStream,
): Long {
    var bytesCopied = 0L
    inputStream.use { input ->
        outputStream.use { output ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                output.write(buffer, 0, read)
                bytesCopied += read
            }
        }
    }
    return bytesCopied
}

internal fun ensureDestParentFolderFile(
    destRoot: File,
    relativeParentSegments: List<String>,
    destFoldersCreatedCollector: MutableCollection<String>?,
): File? {
    var current = destRoot
    for (rawSegment in relativeParentSegments) {
        val segment = rawSegment.trim()
        if (segment.isEmpty() || segment == "." || segment == "..") continue
        val next = File(current, segment)
        when {
            next.isDirectory -> {
                current = next
            }

            next.exists() -> {
                return null
            }

            else -> {
                if (!next.mkdirs() && !next.isDirectory) return null
                destFoldersCreatedCollector?.add(next.toUri().toString())
                current = next
            }
        }
    }
    return current
}

internal fun FileOperationRepository.ensureDestParentFolder(
    destTree: DocumentFile,
    destinationRoot: String,
    relativeParentSegments: List<String>,
    destFoldersCreatedCollector: MutableCollection<String>? = null,
    destinationFolderCache: DestinationFolderCache? = null,
): DocumentFile? {
    val normalizedSegments = normalizeDestinationParentSegments(relativeParentSegments)
    val fullPathKey =
        SafDestinationParentKey(
            destinationRoot = destinationRoot,
            relativeParentSegments = normalizedSegments,
        )
    destinationFolderCache?.safParents?.get(fullPathKey)?.let { cachedParent ->
        return cachedParent
    }

    var current = destTree
    val resolvedSegments = mutableListOf<String>()
    for (segment in normalizedSegments) {
        resolvedSegments += segment
        val prefixKey =
            SafDestinationParentKey(
                destinationRoot = destinationRoot,
                relativeParentSegments = resolvedSegments.toList(),
            )
        val cachedPrefix = destinationFolderCache?.safParents?.get(prefixKey)
        if (cachedPrefix != null) {
            current = cachedPrefix
            continue
        }
        val next = current.findFile(segment)
        current =
            when {
                next != null && next.isDirectory -> {
                    next
                }

                next != null -> {
                    return null
                }

                else -> {
                    val created = current.createDirectory(segment) ?: return null
                    destFoldersCreatedCollector?.add(created.uri.toString())
                    created
                }
            }
        destinationFolderCache?.safParents?.put(prefixKey, current)
    }
    destinationFolderCache?.safParents?.put(fullPathKey, current)
    return current
}

internal fun resolveDestNameFile(
    parent: File,
    name: String,
): String = resolveRenameSuffixName(name) { candidate -> File(parent, candidate).exists() }

internal fun resolveDestName(
    name: String,
    destTree: DocumentFile,
): String = resolveRenameSuffixName(name) { candidate -> destTree.findFile(candidate) != null }

internal fun unchangedPreviewResult(
    sourceEntry: FileEntry,
    simulatedDestPath: String,
): PreviewFileResult =
    PreviewFileResult(
        fileName = sourceEntry.name,
        sourcePath = sourceEntry.uri.toString(),
        simulatedDestPath = simulatedDestPath,
        wouldSkip = false,
        wouldOverwrite = false,
        renamedTo = null,
        sizeBytes = sourceEntry.size,
    )

internal fun FileOperationRepository.simulateFilesystemMove(
    sourceEntry: FileEntry,
    destFolderUriString: String,
    conflictPolicy: ConflictPolicy,
    filesystemAccessEnabled: Boolean,
    simulatedRootPath: String,
): PreviewFileResult {
    if (!filesystemAccessEnabled) {
        return unchangedPreviewResult(sourceEntry, simulatedRootPath)
    }
    val canonical =
        normalizeFilesystemFolderPath(destFolderUriString) ?: return unchangedPreviewResult(sourceEntry, simulatedRootPath)
    if (!isCanonicalPathUnderAllowedSharedStorage(canonical)) {
        return unchangedPreviewResult(sourceEntry, simulatedRootPath)
    }
    val destRoot = File(canonical)
    if (!destRoot.isDirectory) {
        return unchangedPreviewResult(sourceEntry, simulatedRootPath)
    }

    return when (val resolution = peekDestParentForPreviewFile(destRoot, sourceEntry.relativeParentSegments)) {
        is DestParentFilePreview.Partial, is DestParentFilePreview.BlockedByFile -> {
            unchangedPreviewResult(sourceEntry, simulatedRootPath)
        }

        is DestParentFilePreview.Resolved -> {
            val existing = File(resolution.parent, sourceEntry.name)
            if (!existing.exists()) {
                unchangedPreviewResult(sourceEntry, simulatedRootPath)
            } else {
                simulateExistingFilesystemMove(
                    sourceEntry = sourceEntry,
                    destFolderUriString = destFolderUriString,
                    conflictPolicy = conflictPolicy,
                    existing = existing,
                    parent = resolution.parent,
                )
            }
        }
    }
}

internal fun simulateExistingFilesystemMove(
    sourceEntry: FileEntry,
    destFolderUriString: String,
    conflictPolicy: ConflictPolicy,
    existing: File,
    parent: File,
): PreviewFileResult =
    when (conflictPolicy) {
        ConflictPolicy.SKIP -> {
            PreviewFileResult(
                fileName = sourceEntry.name,
                sourcePath = sourceEntry.uri.toString(),
                simulatedDestPath = existing.toUri().toString(),
                wouldSkip = true,
                wouldOverwrite = false,
                renamedTo = null,
                sizeBytes = sourceEntry.size,
            )
        }

        ConflictPolicy.OVERWRITE -> {
            PreviewFileResult(
                fileName = sourceEntry.name,
                sourcePath = sourceEntry.uri.toString(),
                simulatedDestPath = existing.toUri().toString(),
                wouldSkip = false,
                wouldOverwrite = true,
                renamedTo = null,
                sizeBytes = sourceEntry.size,
            )
        }

        ConflictPolicy.RENAME_SUFFIX -> {
            val resolvedName = resolveDestNameFile(parent, sourceEntry.name)
            PreviewFileResult(
                fileName = sourceEntry.name,
                sourcePath = sourceEntry.uri.toString(),
                simulatedDestPath =
                    buildSimulatedDestPreviewPath(
                        destFolderUriString,
                        sourceEntry.relativeParentSegments,
                        resolvedName,
                    ),
                wouldSkip = false,
                wouldOverwrite = false,
                renamedTo = if (resolvedName != sourceEntry.name) resolvedName else null,
                sizeBytes = sourceEntry.size,
            )
        }
    }

internal fun FileOperationRepository.simulateSafMove(
    sourceEntry: FileEntry,
    destFolderUriString: String,
    conflictPolicy: ConflictPolicy,
    simulatedRootPath: String,
    destinationFolderCache: DestinationFolderCache?,
): PreviewFileResult {
    val cachedDestTree = destinationFolderCache?.safRoots?.get(destFolderUriString)
    val destTree =
        try {
            cachedDestTree ?: DocumentFile.fromTreeUri(context, destFolderUriString.toUri())
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SecurityException) {
            null
        }
    if (destTree == null || !destTree.exists()) {
        return unchangedPreviewResult(sourceEntry, simulatedRootPath)
    }
    if (cachedDestTree == null) {
        destinationFolderCache?.safRoots?.put(destFolderUriString, destTree)
    }

    return when (
        val resolution =
            peekDestParentForPreview(
                destTree = destTree,
                destinationRoot = destFolderUriString,
                relativeParentSegments = sourceEntry.relativeParentSegments,
                destinationFolderCache = destinationFolderCache,
            )
    ) {
        is DestParentPreview.Partial, is DestParentPreview.BlockedByFile -> {
            unchangedPreviewResult(sourceEntry, simulatedRootPath)
        }

        is DestParentPreview.Resolved -> {
            val existing = resolution.parent.findFile(sourceEntry.name)
            if (existing == null) {
                unchangedPreviewResult(sourceEntry, simulatedRootPath)
            } else {
                simulateExistingSafMove(
                    sourceEntry = sourceEntry,
                    destFolderUriString = destFolderUriString,
                    conflictPolicy = conflictPolicy,
                    existing = existing,
                    parent = resolution.parent,
                )
            }
        }
    }
}

internal fun simulateExistingSafMove(
    sourceEntry: FileEntry,
    destFolderUriString: String,
    conflictPolicy: ConflictPolicy,
    existing: DocumentFile,
    parent: DocumentFile,
): PreviewFileResult =
    when (conflictPolicy) {
        ConflictPolicy.SKIP -> {
            PreviewFileResult(
                fileName = sourceEntry.name,
                sourcePath = sourceEntry.uri.toString(),
                simulatedDestPath = existing.uri.toString(),
                wouldSkip = true,
                wouldOverwrite = false,
                renamedTo = null,
                sizeBytes = sourceEntry.size,
            )
        }

        ConflictPolicy.OVERWRITE -> {
            PreviewFileResult(
                fileName = sourceEntry.name,
                sourcePath = sourceEntry.uri.toString(),
                simulatedDestPath = existing.uri.toString(),
                wouldSkip = false,
                wouldOverwrite = true,
                renamedTo = null,
                sizeBytes = sourceEntry.size,
            )
        }

        ConflictPolicy.RENAME_SUFFIX -> {
            val resolvedName = resolveDestName(sourceEntry.name, parent)
            PreviewFileResult(
                fileName = sourceEntry.name,
                sourcePath = sourceEntry.uri.toString(),
                simulatedDestPath =
                    buildSimulatedDestPreviewPath(
                        destFolderUriString,
                        sourceEntry.relativeParentSegments,
                        resolvedName,
                    ),
                wouldSkip = false,
                wouldOverwrite = false,
                renamedTo = if (resolvedName != sourceEntry.name) resolvedName else null,
                sizeBytes = sourceEntry.size,
            )
        }
    }

internal sealed class DestParentFilePreview {
    data class Resolved(
        val parent: File,
    ) : DestParentFilePreview()

    data object Partial : DestParentFilePreview()

    data object BlockedByFile : DestParentFilePreview()
}

internal fun peekDestParentForPreviewFile(
    destRoot: File,
    relativeParentSegments: List<String>,
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

internal fun peekDestParentForPreview(
    destTree: DocumentFile,
    destinationRoot: String,
    relativeParentSegments: List<String>,
    destinationFolderCache: DestinationFolderCache? = null,
): DestParentPreview {
    val normalizedSegments = normalizeDestinationParentSegments(relativeParentSegments)
    val fullPathKey =
        SafDestinationParentKey(
            destinationRoot = destinationRoot,
            relativeParentSegments = normalizedSegments,
        )
    destinationFolderCache?.safParents?.get(fullPathKey)?.let { cachedParent ->
        return DestParentPreview.Resolved(cachedParent)
    }

    var current = destTree
    val resolvedSegments = mutableListOf<String>()
    for (segment in normalizedSegments) {
        resolvedSegments += segment
        val prefixKey =
            SafDestinationParentKey(
                destinationRoot = destinationRoot,
                relativeParentSegments = resolvedSegments.toList(),
            )
        val cachedPrefix = destinationFolderCache?.safParents?.get(prefixKey)
        if (cachedPrefix != null) {
            current = cachedPrefix
            continue
        }
        val next = current.findFile(segment)
        when {
            next == null -> {
                return DestParentPreview.Partial
            }

            !next.isDirectory -> {
                return DestParentPreview.BlockedByFile
            }

            else -> {
                current = next
                destinationFolderCache?.safParents?.put(prefixKey, current)
            }
        }
    }
    destinationFolderCache?.safParents?.put(fullPathKey, current)
    return DestParentPreview.Resolved(current)
}

internal fun relativePathSuffixForDisplay(
    relativeParentSegments: List<String>,
    fileName: String,
): String {
    val clean =
        relativeParentSegments
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
    return if (clean.isEmpty()) fileName else clean.joinToString("/", postfix = "/") + fileName
}

internal fun buildSimulatedDestPreviewPath(
    destFolderUriString: String,
    relativeParentSegments: List<String>,
    fileName: String,
): String {
    val pathSuffix = relativePathSuffixForDisplay(relativeParentSegments, fileName)
    return when {
        destFolderUriString.startsWith("content://") -> pathSuffix
        destFolderUriString.endsWith("/") -> destFolderUriString + pathSuffix
        else -> "$destFolderUriString/$pathSuffix"
    }
}

internal sealed class DestParentPreview {
    data class Resolved(
        val parent: DocumentFile,
    ) : DestParentPreview()

    data object Partial : DestParentPreview()

    data object BlockedByFile : DestParentPreview()
}

/**
 * Fast path for SAF→SAF moves: let the provider relocate the document in place. That preserves the
 * file's last-modified time, which the stream-copy fallback cannot, and moves no bytes at all.
 *
 * Deliberately narrow, because the fallback is only safe while the source still exists:
 *  - requires [FileEntry.parentDocumentUri] and `FLAG_SUPPORTS_MOVE`, so providers that can't move
 *    cost nothing rather than one thrown Binder exception per file;
 *  - requires the destination name to be unchanged. Renaming after the move would need a second
 *    call that can fail once the source is already gone, leaving a moved file that we report as
 *    failed and can no longer locate. `RENAME_SUFFIX` conflicts therefore always return null and
 *    take the copy path — no loss in practice, since AOSP's `FileSystemProvider.moveDocument`
 *    rejects a move whose target name already exists anyway.
 *
 * Returns a successful [FileMoved] when the provider move lands; null means the caller should
 * stream-copy (cross-volume moves and provider rejections leave the source untouched).
 */
internal fun FileOperationRepository.tryMoveDocumentInProvider(
    sourceEntry: FileEntry,
    destParent: DocumentFile,
    destName: String,
    operationMode: OperationMode,
): FileMoved? {
    val sourceParentUri = sourceEntry.parentDocumentUri ?: return null
    if (!canUseInProviderMove(
            operationMode = operationMode,
            supportsMove = sourceEntry.supportsMove,
            destNameUnchanged = destName == sourceEntry.name,
        )
    ) {
        return null
    }
    val movedUri =
        try {
            DocumentsContract.moveDocument(
                context.contentResolver,
                sourceEntry.uri,
                sourceParentUri,
                destParent.uri,
            )
        } catch (e: Exception) {
            // Cross-volume moves and providers that reject the target both land here; the
            // source is untouched, so the stream copy below can still run.
            recordDiagnosticOnce(
                key = "saf-move-fast-path-unavailable",
                message = "SAF in-provider move unavailable, copying instead: ${e.javaClass.simpleName}: ${e.message}",
            )
            null
        } ?: return null
    return FileMoved(
        fileName = sourceEntry.name,
        sourceUri = sourceEntry.uri.toString(),
        destinationUri = destinationUriForMovedDocument(movedUri, destParent.uri).toString(),
        fileSizeBytes = sourceEntry.size,
        relativeParentSegments = sourceEntry.relativeParentSegments,
        success = true,
    )
}

/**
 * Parent of [documentId], or null when it has no derivable parent.
 *
 * Path-shaped ids nest with `/` (`primary:DCIM/Camera/a.jpg` → `primary:DCIM/Camera`); an id
 * directly under a volume root has only the volume prefix left (`primary:a.jpg` → `primary:`).
 * Anything else — notably an id with no `:` at all — has no parent we can name, and guessing one
 * would send a move to a fabricated location.
 */
internal fun parentDocumentIdOrNull(documentId: String): String? {
    val clean = documentId.trimEnd('/')
    val slashIndex = clean.lastIndexOf('/')
    val parent =
        if (slashIndex >= 0) {
            clean.substring(0, slashIndex)
        } else {
            val colonIndex = clean.indexOf(':')
            if (colonIndex < 0) return null
            clean.substring(0, colonIndex + 1)
        }
    // A volume root reduces to itself. Answering "its own parent" would be a fixed point the
    // callers would have to know to reject, so report no parent instead.
    return parent.takeIf { it.isNotEmpty() && it != clean }
}

/**
 * Filesystem timestamp granularity to tolerate when deciding whether a stamp took effect. FAT and
 * exFAT volumes round to whole seconds or worse, so an exact comparison would read a successful
 * write as a failure.
 */
private const val LAST_MODIFIED_TOLERANCE_MS = 2_000L

/**
 * Makes [destFile] carry [lastModifiedMs], if it doesn't already.
 *
 * Needed even after [StandardCopyOption.COPY_ATTRIBUTES]: on a copy, `sun.nio.fs.UnixCopyFile`
 * swallows a failed `utimes()` and reports success anyway (only a *move* sets
 * `failIfUnableToCopyBasic`), so a destination volume that rejects the timestamp silently ends up
 * stamped with the time of the copy. Checking the result and re-stamping covers that.
 *
 * Best-effort by design — a volume that refuses timestamps entirely shouldn't fail an otherwise
 * complete transfer — so it logs once instead of returning an error.
 */
internal fun FileOperationRepository.preserveLastModified(
    destFile: File,
    lastModifiedMs: Long,
) {
    if (lastModifiedMs <= 0L) return

    fun isPreserved(): Boolean =
        runCatching { abs(destFile.lastModified() - lastModifiedMs) <= LAST_MODIFIED_TOLERANCE_MS }
            .getOrDefault(false)

    if (isPreserved()) return
    runCatching { destFile.setLastModified(lastModifiedMs) }
    if (!isPreserved()) {
        recordDiagnosticOnce(
            key = "dest-set-last-modified-failed",
            message = "Destination volume would not accept the source's last-modified time: ${destFile.parent}",
        )
    }
}

/** Trailing name of [documentId] — the component a folder or file is displayed under. */
internal fun documentIdLastSegment(documentId: String): String = documentId.trimEnd('/').substringAfterLast('/').substringAfterLast(':')

/**
 * Walks [documentId] up one level per entry in [segments], last segment first, and returns where it
 * lands — or null if the trailing names don't match [segments], meaning the id doesn't actually sit
 * that deep and stripping would name an unrelated folder.
 *
 * Inverts the subfolder recreation that [ensureDestParentFolder] performs on the way out, which is
 * how a transfer's destination can be turned back into the root it was resolved from.
 */
internal fun documentIdWithoutTrailingSegments(
    documentId: String,
    segments: List<String>,
): String? {
    var current = documentId
    for (segment in segments.asReversed()) {
        if (documentIdLastSegment(current) != segment) return null
        current = parentDocumentIdOrNull(current) ?: return null
    }
    return current
}

/** [documentIdWithoutTrailingSegments] for filesystem paths. */
internal fun fileWithoutTrailingSegments(
    directory: File,
    segments: List<String>,
): File? {
    var current = directory
    for (segment in segments.asReversed()) {
        if (current.name != segment) return null
        current = current.parentFile ?: return null
    }
    return current
}

/**
 * Containing folder of [documentUri] as a document URI carrying the same tree grant.
 *
 * [DocumentsContract.moveDocument] needs the source's parent and enforces write access on it, so
 * this only answers for tree-scoped URIs — a single-document URI grants nothing on its parent.
 * Scan results already carry [FileEntry.parentDocumentUri]; this covers entries assembled from a
 * recorded URI instead, such as undo restoring a file to its original folder.
 */
internal fun parentDocumentUriUnderSameTree(documentUri: Uri): Uri? =
    try {
        if (!DocumentsContract.isTreeUri(documentUri)) {
            null
        } else {
            val parentDocumentId = parentDocumentIdOrNull(DocumentsContract.getDocumentId(documentUri))
            parentDocumentId?.let { DocumentsContract.buildDocumentUriUsingTree(documentUri, it) }
        }
    } catch (_: Exception) {
        null
    }

/**
 * Whether a SAF→SAF transfer may be handed to [DocumentsContract.moveDocument] instead of being
 * stream-copied. Each condition is load-bearing — see the call site in
 * [tryMoveDocumentInProvider] — so relaxing one reintroduces either a thrown Binder exception per
 * file or a file that moved successfully but is recorded as failed with no recoverable destination.
 */
internal fun canUseInProviderMove(
    operationMode: OperationMode,
    supportsMove: Boolean,
    destNameUnchanged: Boolean,
): Boolean = operationMode == OperationMode.MOVE && supportsMove && destNameUnchanged

/**
 * Destination URI to record for a document relocated by [DocumentsContract.moveDocument].
 *
 * The URI that call returns cannot be used: `DocumentsProvider` builds it from the *source*
 * document URI (`buildDocumentUriMaybeUsingTree`), so for a tree-scoped source it names a document
 * that is not a descendant of the tree it is scoped to. `DocumentsProvider.enforceTree` then
 * rejects every subsequent query and open on it, which would silently break run history
 * thumbnails, tap-to-open, and undo. Re-scope the new document id under the destination grant
 * ([destParentUri]) instead, which is the grant that actually covers it.
 */
internal fun destinationUriForMovedDocument(
    movedUri: Uri,
    destParentUri: Uri,
): Uri =
    try {
        val movedDocumentId = DocumentsContract.getDocumentId(movedUri)
        if (DocumentsContract.isTreeUri(destParentUri)) {
            DocumentsContract.buildDocumentUriUsingTree(destParentUri, movedDocumentId)
        } else {
            val authority = destParentUri.authority ?: return movedUri
            DocumentsContract.buildDocumentUri(authority, movedDocumentId)
        }
    } catch (_: Exception) {
        movedUri
    }
