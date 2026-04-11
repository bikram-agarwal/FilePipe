package dev.bikram.filepipe.domain.usecase

import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.FileOperationRepository
import dev.bikram.filepipe.data.repository.RunHistoryRepository
import dev.bikram.filepipe.data.storage.isFilesystemAccessEffective
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RunProgress
import dev.bikram.filepipe.domain.model.RunResult
import dev.bikram.filepipe.domain.model.TriggerType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield
import javax.inject.Inject

class ExecuteRulesUseCase @Inject constructor(
    private val fileOperationRepository: FileOperationRepository,
    private val runHistoryRepository: RunHistoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(
        rules: List<Rule>,
        triggerType: TriggerType,
        onProgress: (RunProgress) -> Unit = {}
    ): List<RunResult> = coroutineScope {
        rules.map { rule ->
            async { executeRule(rule, triggerType, onProgress) }
        }.awaitAll()
    }

    private suspend fun executeRule(
        rule: Rule,
        triggerType: TriggerType,
        onProgress: (RunProgress) -> Unit
    ): RunResult {
        val startedAt = System.currentTimeMillis()
        val historyId = runHistoryRepository.startRun(rule.id, rule.name, triggerType, rule.operationMode)

        onProgress(RunProgress(rule.id, rule.name, 0f, totalFiles = 0))

        val allFiles = mutableListOf<FileMoved>()
        var totalPlanned = 0
        var completedSuccessfulMoves = 0
        val copyCreatedDestFolders: MutableSet<String> = linkedSetOf()

        try {
            val filesystemAccessEnabled =
                isFilesystemAccessEffective(userPreferencesRepository.preferencesFlow.first().folderAccessMode)
            // Collect all matching files across all source folders
            val fileEntries = rule.sourceFolderPaths.flatMap { sourcePath ->
                fileOperationRepository.listMatchingFiles(
                    folderUriString = sourcePath,
                    extensions = rule.fileExtensions,
                    scanSubdirectories = rule.scanSubdirectories,
                    filenamePattern = rule.filenamePattern,
                    minFileSizeBytes = rule.minFileSizeBytes,
                    maxFileSizeBytes = rule.maxFileSizeBytes,
                    minAgeDays = rule.minAgeDays,
                    maxAgeDays = rule.maxAgeDays,
                    excludePatterns = rule.excludePatterns,
                    filesystemAccessEnabled = filesystemAccessEnabled
                )
            }

            val total = fileEntries.size
            totalPlanned = total
            fileEntries.forEachIndexed { index, entry ->
                yield()
                onProgress(
                    RunProgress(
                        ruleId = rule.id,
                        ruleName = rule.name,
                        progress = index.toFloat() / total.coerceAtLeast(1),
                        currentFileName = entry.name,
                        filesMoved = completedSuccessfulMoves,
                        totalFiles = total
                    )
                )

                val result = fileOperationRepository.moveFile(
                    sourceEntry = entry,
                    destFolderUriString = rule.destinationFolderPath,
                    conflictPolicy = rule.conflictPolicy,
                    operationMode = rule.operationMode,
                    destFoldersCreatedCollector = if (rule.operationMode == OperationMode.COPY) {
                        copyCreatedDestFolders
                    } else {
                        null
                    },
                    filesystemAccessEnabled = filesystemAccessEnabled
                )
                // Job may be cancelled as soon as moveFile returns; record the outcome so undo/history match disk.
                withContext(NonCancellable) {
                    allFiles.add(result)
                    if (result.success && !result.skipped) {
                        completedSuccessfulMoves++
                    }
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                if (allFiles.isEmpty()) {
                    runHistoryRepository.finishRunUserCancelled(historyId, totalPlanned)
                } else {
                    val completedAt = System.currentTimeMillis()
                    runHistoryRepository.completeRunUserCancelledPartial(
                        RunResult(
                            ruleId = rule.id,
                            ruleName = rule.name,
                            historyId = historyId,
                            filesMoved = allFiles,
                            startedAt = startedAt,
                            completedAt = completedAt,
                            copyCreatedDestFolderUris = copyCreatedDestFolders.toList()
                        ),
                        totalPlanned = totalPlanned
                    )
                }
            }
            onProgress(
                RunProgress(
                    rule.id,
                    rule.name,
                    1f,
                    isComplete = true,
                    error = RunProgress.ERROR_CANCELLED
                )
            )
            throw e
        } catch (e: Exception) {
            val result = RunResult(
                ruleId = rule.id,
                ruleName = rule.name,
                historyId = historyId,
                filesMoved = allFiles,
                startedAt = startedAt,
                completedAt = System.currentTimeMillis(),
                copyCreatedDestFolderUris = copyCreatedDestFolders.toList()
            )
            runHistoryRepository.completeRun(result)
            onProgress(
                RunProgress(rule.id, rule.name, 1f, isComplete = true, error = e.message)
            )
            return result
        }

        val completedAt = System.currentTimeMillis()
        val result = RunResult(
            ruleId = rule.id,
            ruleName = rule.name,
            historyId = historyId,
            filesMoved = allFiles,
            startedAt = startedAt,
            completedAt = completedAt,
            copyCreatedDestFolderUris = copyCreatedDestFolders.toList()
        )
        runHistoryRepository.completeRun(result)

        onProgress(
            RunProgress(
                ruleId = rule.id,
                ruleName = rule.name,
                progress = 1f,
                filesMoved = result.totalMoved,
                totalFiles = allFiles.size,
                isComplete = true
            )
        )

        return result
    }
}
