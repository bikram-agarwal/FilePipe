package dev.bikram.filepipe.domain.usecase

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.data.repository.FileEntry
import dev.bikram.filepipe.data.repository.FileOperationRepository
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.data.repository.RunHistoryRepository
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.isEffectivelyUndone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class UndoResult(
    val totalReversed: Int,
    val totalFailed: Int,
    val errors: List<String>,
    val operationMode: OperationMode = OperationMode.MOVE
)

class UndoRunUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val runHistoryRepository: RunHistoryRepository,
    private val fileOperationRepository: FileOperationRepository,
    private val ruleRepository: RuleRepository
) {
    suspend operator fun invoke(historyId: Long): UndoResult = withContext(Dispatchers.IO) {
        val history = runHistoryRepository.getHistoryById(historyId)
            ?: return@withContext UndoResult(0, 0, listOf("Run not found"))

        if (history.isEffectivelyUndone()) {
            return@withContext UndoResult(
                0,
                0,
                listOf("This run has already been undone"),
                operationMode = history.operationMode
            )
        }

        val operationMode = history.operationMode
        val movedFiles = runHistoryRepository.getFilesForRunOnce(historyId)
            .filter { it.success && !it.skipped && it.destinationUri.isNotBlank() }

        var reversed = 0
        var failed = 0
        val errors = mutableListOf<String>()
        val copyDeletedDestinationUris = mutableListOf<String>()

        movedFiles.forEach { fileMoved ->
            val destUri = Uri.parse(fileMoved.destinationUri)
            val destDoc = DocumentFile.fromSingleUri(context, destUri)
            if (destDoc == null || !destDoc.exists()) {
                errors.add("${fileMoved.fileName}: file no longer exists at destination")
                failed++
                return@forEach
            }

            when (operationMode) {
                OperationMode.COPY -> {
                    val deleted = try {
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
                OperationMode.MOVE -> {
                    val sourceFolderUriString = parentTreeUriString(fileMoved.sourceUri)
                    if (sourceFolderUriString == null) {
                        errors.add("${fileMoved.fileName}: cannot determine original source folder")
                        failed++
                        return@forEach
                    }

                    val sourceEntry = FileEntry(
                        uri = destUri,
                        name = fileMoved.fileName,
                        size = destDoc.length()
                    )

                    val reverseResult = fileOperationRepository.moveFile(
                        sourceEntry = sourceEntry,
                        destFolderUriString = sourceFolderUriString,
                        conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                        operationMode = OperationMode.MOVE
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
            val destTreeUriString = history.ruleId?.let { ruleId ->
                ruleRepository.getRuleById(ruleId)?.destinationFolderPath?.takeIf { it.isNotBlank() }
            }
            if (destTreeUriString != null) {
                deleteEmptyDestSubfoldersAfterCopyUndo(destTreeUriString, copyDeletedDestinationUris)
            }
        }

        UndoResult(reversed, failed, errors, operationMode = operationMode)
    }

    /**
     * After copied files are removed, deletes empty subfolders under the rule destination tree that
     * were only holding those files. Skips non-empty dirs (e.g. pre-existing content).
     */
    private fun deleteEmptyDestSubfoldersAfterCopyUndo(
        destTreeUriString: String,
        deletedFileDestinationUriStrings: List<String>
    ) {
        val treeUri = Uri.parse(destTreeUriString)
        val authority = treeUri.authority ?: return
        val treeDocumentId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: IllegalArgumentException) {
            return
        }
        val folderDocumentIds = mutableSetOf<String>()
        for (fileUriString in deletedFileDestinationUriStrings) {
            folderDocumentIds.addAll(
                parentFolderDocumentIdsUnderTree(treeDocumentId, fileUriString)
            )
        }
        val deepestFirst = folderDocumentIds.sortedByDescending { documentId ->
            documentId.count { segment -> segment == '/' }
        }
        for (folderDocumentId in deepestFirst) {
            val folderUri = DocumentsContract.buildDocumentUri(authority, folderDocumentId)
            val folderDoc = DocumentFile.fromSingleUri(context, folderUri) ?: continue
            if (!folderDoc.isDirectory) continue
            val children = try {
                folderDoc.listFiles()
            } catch (_: SecurityException) {
                null
            }
            if (children != null && children.isNotEmpty()) continue
            try {
                folderDoc.delete()
            } catch (_: SecurityException) {
            }
        }
    }

    /**
     * Document IDs for folders strictly between the tree root and the file (i.e. parents of the
     * file, excluding the destination root). Empty if the file lived directly under the tree root.
     */
    private fun parentFolderDocumentIdsUnderTree(
        treeDocumentId: String,
        fileDocumentUriString: String
    ): List<String> {
        val fileDocumentId = try {
            DocumentsContract.getDocumentId(Uri.parse(fileDocumentUriString))
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
            val fullPath = if (treePathAfterColon.isEmpty()) {
                underTree
            } else {
                "$treePathAfterColon/$underTree"
            }
            result.add("$volume:$fullPath")
        }
        return result
    }

    /**
     * Derives the parent folder as a SAF tree URI string from a document URI.
     * e.g. content://...document/primary%3ADCIM%2FCamera%2Fphoto.jpg
     *   → content://...tree/primary%3ADCIM%2FCamera
     */
    /**
     * Removes destination folders that were created during the copy run, deepest first,
     * only when still empty (so pre-existing folders or folders with leftover content stay).
     */
    private fun deleteEmptyRecordedCopyFolders(folderUriStrings: List<String>) {
        val distinctSorted = folderUriStrings.distinct().sortedByDescending { documentPathDepth(it) }
        for (uriString in distinctSorted) {
            val folderDoc = DocumentFile.fromSingleUri(context, Uri.parse(uriString)) ?: continue
            if (!folderDoc.exists() || !folderDoc.isDirectory) continue
            val children = try {
                folderDoc.listFiles()
            } catch (_: SecurityException) {
                null
            }
            if (!children.isNullOrEmpty()) continue
            try {
                folderDoc.delete()
            } catch (_: SecurityException) {
            }
        }
    }

    private fun documentPathDepth(uriString: String): Int {
        if (!uriString.startsWith("content://")) return 0
        return try {
            val docId = DocumentsContract.getDocumentId(Uri.parse(uriString))
            val path = docId.substringAfter(':', "")
            path.count { it == '/' }
        } catch (_: Exception) {
            0
        }
    }

    private fun parentTreeUriString(documentUriString: String): String? {
        if (!documentUriString.startsWith("content://")) return null
        return try {
            val parsed = Uri.parse(documentUriString)
            val docAuthority = parsed.authority ?: return null
            val docId = DocumentsContract.getDocumentId(parsed)
            val relativePath = docId.substringAfter(":", "")
            val parentDocId = if ('/' in relativePath) {
                docId.substringBeforeLast('/')
            } else {
                // File is directly at the volume root — parent is the root itself
                docId.substringBefore(':') + ":"
            }
            DocumentsContract.buildTreeDocumentUri(docAuthority, parentDocId).toString()
        } catch (_: Exception) { null }
    }
}
