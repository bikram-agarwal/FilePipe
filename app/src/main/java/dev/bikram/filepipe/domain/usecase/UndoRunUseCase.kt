package dev.bikram.filepipe.domain.usecase

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.data.repository.FileEntry
import dev.bikram.filepipe.data.repository.FileOperationRepository
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
    private val fileOperationRepository: FileOperationRepository
) {
    private val authority = "com.android.externalstorage.documents"

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

        if (reversed > 0) {
            runHistoryRepository.markRunReversed(historyId)
        }

        UndoResult(reversed, failed, errors, operationMode = operationMode)
    }

    /**
     * Derives the parent folder as a SAF tree URI string from a document URI.
     * e.g. content://...document/primary%3ADCIM%2FCamera%2Fphoto.jpg
     *   → content://...tree/primary%3ADCIM%2FCamera
     */
    private fun parentTreeUriString(documentUriString: String): String? {
        if (!documentUriString.startsWith("content://")) return null
        return try {
            val docId = DocumentsContract.getDocumentId(Uri.parse(documentUriString))
            val relativePath = docId.substringAfter(":", "")
            val parentDocId = if ('/' in relativePath) {
                docId.substringBeforeLast('/')
            } else {
                // File is directly at the volume root — parent is the root itself
                docId.substringBefore(':') + ":"
            }
            DocumentsContract.buildTreeDocumentUri(authority, parentDocId).toString()
        } catch (_: Exception) { null }
    }
}
