package dev.bikram.filepipe.domain.usecase

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.data.repository.RunHistoryRepository
import dev.bikram.filepipe.di.IoDispatcher
import dev.bikram.filepipe.domain.backupFileTimestamp
import dev.bikram.filepipe.domain.export.buildAppBackupJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

class ExportRulesUseCase
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val ruleRepository: RuleRepository,
        private val runHistoryRepository: RunHistoryRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend fun exportRulesToTreeUri(folderPath: String): Result<String> =
            withContext(ioDispatcher) {
                if (folderPath.isBlank()) return@withContext Result.failure(IllegalStateException("No export folder"))

                val exportResult = exportRulesToTreeUris(listOf(folderPath))
                exportResult.fold(
                    onSuccess = { fileNames -> Result.success(fileNames.first()) },
                    onFailure = { error -> Result.failure(error) },
                )
            }

        suspend fun exportRulesToTreeUris(folderPaths: List<String>): Result<List<String>> =
            withContext(ioDispatcher) {
                val destinations = folderPaths.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                if (destinations.isEmpty()) return@withContext Result.failure(IllegalStateException("No export folder"))

                val rules = ruleRepository.getAllRules().first()
                val allHistory = runHistoryRepository.getAllHistoryOnce()
                val historyWithFiles =
                    allHistory.map { run ->
                        run to runHistoryRepository.getFilesForRunOnce(run.id)
                    }
                val settings = userPreferencesRepository.getPreferencesSnapshot()

                val json = buildAppBackupJson(rules, historyWithFiles, settings)
                val stamp = backupFileTimestamp()
                val fileName = "filepipe_backup_$stamp.json"

                val failures = mutableListOf<Throwable>()
                val exportedFileNames = mutableListOf<String>()
                destinations.forEach { destinationPath ->
                    val result =
                        if (destinationPath.startsWith("content://")) {
                            writeToContentDestination(destinationPath, fileName, json).map { fileName }
                        } else {
                            writeToFilePath(destinationPath, fileName, json).map { fileName }
                        }
                    result.fold(
                        onSuccess = { exportedFileNames.add(it) },
                        onFailure = { failures.add(it) },
                    )
                }

                if (failures.isEmpty()) {
                    Result.success(exportedFileNames)
                } else {
                    Result.failure(failures.first())
                }
            }

        /**
         * Writes the same backup JSON as [exportRulesToTreeUri] to a URI from [androidx.activity.result.contract.ActivityResultContracts.CreateDocument].
         */
        suspend fun exportBackupJsonToDocumentUri(targetUri: Uri): Result<String> =
            withContext(ioDispatcher) {
                val rules = ruleRepository.getAllRules().first()
                val allHistory = runHistoryRepository.getAllHistoryOnce()
                val historyWithFiles =
                    allHistory.map { run ->
                        run to runHistoryRepository.getFilesForRunOnce(run.id)
                    }
                val settings = userPreferencesRepository.getPreferencesSnapshot()
                val json = buildAppBackupJson(rules, historyWithFiles, settings)
                runCatching {
                    context.contentResolver.openOutputStream(targetUri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("Failed to open output stream for export")
                    friendlyFileNameFromDocumentUri(targetUri)
                }.fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(it) })
            }

        /**
         * [Uri.getLastPathSegment] for SAF document URIs is the full document id (e.g. `primary:Download/foo.json`).
         * For snackbars we only want the leaf file name (e.g. `foo.json`).
         */
        private fun friendlyFileNameFromDocumentUri(documentUri: Uri): String {
            val segment = documentUri.lastPathSegment ?: return "filepipe_backup.json"
            val decoded = Uri.decode(segment)
            val lastSlash = decoded.lastIndexOf('/')
            return if (lastSlash >= 0) {
                decoded.substring(lastSlash + 1)
            } else {
                val lastColon = decoded.lastIndexOf(':')
                if (lastColon >= 0) decoded.substring(lastColon + 1) else decoded
            }
        }

        private fun writeToContentDestination(
            destinationUriString: String,
            fileName: String,
            json: String,
        ): Result<Unit> {
            val destinationUri = destinationUriString.toUri()
            return runCatching {
                if (DocumentsContract.isTreeUri(destinationUri)) {
                    val docTreeUri =
                        DocumentsContract.buildDocumentUriUsingTree(
                            destinationUri,
                            DocumentsContract.getTreeDocumentId(destinationUri),
                        )
                    val docUri =
                        DocumentsContract.createDocument(
                            context.contentResolver,
                            docTreeUri,
                            "application/json",
                            fileName,
                        ) ?: throw IOException("Failed to create document in backup folder")
                    context.contentResolver.openOutputStream(docUri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("Failed to open output stream for backup document")
                } else {
                    context.contentResolver.openOutputStream(destinationUri, "wt")?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("Failed to open output stream for backup document")
                }
            }.fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(it) })
        }

        private fun writeToFilePath(
            folderPath: String,
            fileName: String,
            json: String,
        ): Result<Unit> {
            val folder = File(folderPath)
            if (!folder.exists() || !folder.canWrite()) {
                return Result.failure(IllegalStateException("Export folder not accessible: $folderPath"))
            }
            return runCatching {
                File(folder, fileName).writeText(json, Charsets.UTF_8)
            }.fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(it) })
        }
    }
