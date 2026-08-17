package dev.bikram.filepipe.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.DestinationFolderCache
import dev.bikram.filepipe.data.repository.FileEntry
import dev.bikram.filepipe.data.repository.FileOperationRepository
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.data.repository.RunHistoryRepository
import dev.bikram.filepipe.data.repository.canonicalIdentity
import dev.bikram.filepipe.data.repository.normalizeSourcePath
import dev.bikram.filepipe.data.storage.isFilesystemAccessEffective
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RunProgress
import dev.bikram.filepipe.domain.model.RunResult
import dev.bikram.filepipe.domain.model.TriggerType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import javax.inject.Inject

internal suspend fun <Input, Output> executeSequentially(
    inputs: List<Input>,
    executeInput: suspend (Input) -> Output,
): List<Output> {
    val outputs = ArrayList<Output>(inputs.size)
    for (input in inputs) {
        outputs += executeInput(input)
    }
    return outputs
}

class ExecuteRulesUseCase
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val fileOperationRepository: FileOperationRepository,
        private val runHistoryRepository: RunHistoryRepository,
        private val ruleRepository: RuleRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
    ) {
        suspend operator fun invoke(
            rules: List<Rule>,
            triggerType: TriggerType,
            useCache: Boolean = false,
            preparedFileEntriesByRuleId: Map<Long, List<FileEntry>> = emptyMap(),
            onProgress: (RunProgress) -> Unit = {},
        ): List<RunResult> =
            executeSequentially(rules) { rule ->
                executeRule(
                    rule = rule,
                    triggerType = triggerType,
                    useCache = useCache,
                    preparedFileEntries = preparedFileEntriesByRuleId[rule.id],
                    onProgress = onProgress,
                )
            }

        private suspend fun executeRule(
            rule: Rule,
            triggerType: TriggerType,
            useCache: Boolean,
            preparedFileEntries: List<FileEntry>?,
            onProgress: (RunProgress) -> Unit,
        ): RunResult {
            val startedAt = System.currentTimeMillis()
            ruleRepository.markRuleRan(rule.id, startedAt)
            val historyId = runHistoryRepository.startRun(rule.id, rule.name, triggerType, rule.operationMode)

            onProgress(RunProgress(rule.id, rule.name, 0f, totalFiles = 0))

            val allFiles = mutableListOf<FileMoved>()
            var totalPlanned = 0
            var completedSuccessfulMoves = 0
            // Destination folders this run created. Named for the persisted field, which predates
            // moves being swept too (renaming it would change a DB column and a backup JSON key).
            val copyCreatedDestFolders: MutableSet<String> = linkedSetOf()
            val destinationFolderCache = DestinationFolderCache()

            try {
                val filesystemAccessEnabled =
                    isFilesystemAccessEffective(userPreferencesRepository.preferencesFlow.first().folderAccessMode)
                // Collect all matching files across all source folders
                val fileEntries =
                    preparedFileEntries
                        ?.distinctBy { entry -> entry.canonicalIdentity() }
                        ?: rule.sourceFolderPaths
                            .distinctBy { path -> normalizeSourcePath(path, filesystemAccessEnabled) }
                            .flatMap { sourcePath ->
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
                                    filesystemAccessEnabled = filesystemAccessEnabled,
                                    orientation = rule.orientation,
                                    isRegexPattern = rule.isRegexPattern,
                                    isExcludeRegexPattern = rule.isExcludeRegexPattern,
                                    useCache = useCache,
                                )
                            }.distinctBy { entry -> entry.canonicalIdentity() }

                val total = fileEntries.size
                totalPlanned = total
                fileEntries.forEachIndexed { index, entry ->
                    val destinationEntry =
                        if (rule.recreateDestinationSubfolders) {
                            entry
                        } else {
                            entry.copy(relativeParentSegments = emptyList())
                        }
                    yield()
                    onProgress(
                        RunProgress(
                            ruleId = rule.id,
                            ruleName = rule.name,
                            progress = index.toFloat() / total.coerceAtLeast(1),
                            currentFileName = entry.name,
                            filesMoved = completedSuccessfulMoves,
                            totalFiles = total,
                        ),
                    )

                    val result =
                        fileOperationRepository.moveFile(
                            sourceEntry = destinationEntry,
                            destFolderUriString = rule.destinationFolderPath,
                            conflictPolicy = rule.conflictPolicy,
                            operationMode = rule.operationMode,
                            // Recorded for moves as well as copies: undoing either empties the
                            // destination subfolders the run created, and undo can only sweep
                            // folders it knows the run is responsible for.
                            destFoldersCreatedCollector =
                                if (rule.operationMode == OperationMode.DELETE) {
                                    null
                                } else {
                                    copyCreatedDestFolders
                                },
                            filesystemAccessEnabled = filesystemAccessEnabled,
                            requireUnchangedSource = preparedFileEntries != null,
                            destinationFolderCache = destinationFolderCache,
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
                                copyCreatedDestFolderUris = copyCreatedDestFolders.toList(),
                            ),
                            totalPlanned = totalPlanned,
                        )
                    }
                }
                onProgress(
                    RunProgress(
                        rule.id,
                        rule.name,
                        1f,
                        isComplete = true,
                        error = RunProgress.ERROR_CANCELLED,
                    ),
                )
                throw e
            } catch (e: Exception) {
                val result =
                    RunResult(
                        ruleId = rule.id,
                        ruleName = rule.name,
                        historyId = historyId,
                        filesMoved = allFiles,
                        startedAt = startedAt,
                        completedAt = System.currentTimeMillis(),
                        copyCreatedDestFolderUris = copyCreatedDestFolders.toList(),
                    )
                runHistoryRepository.completeRun(result)
                DiagnosticLog.record(
                    context,
                    "Rule execution failed: ruleId=${rule.id}, trigger=$triggerType, planned=$totalPlanned, completed=${allFiles.size}",
                    e,
                )
                onProgress(
                    RunProgress(rule.id, rule.name, 1f, isComplete = true, error = e.message),
                )
                return result
            }

            val completedAt = System.currentTimeMillis()
            val result =
                RunResult(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    historyId = historyId,
                    filesMoved = allFiles,
                    startedAt = startedAt,
                    completedAt = completedAt,
                    copyCreatedDestFolderUris = copyCreatedDestFolders.toList(),
                )
            // One scan request per touched directory, after the run rather than per file, so other
            // apps see the moves. SAF transfers need none — the provider reindexes both sides itself.
            fileOperationRepository.flushMediaScans()
            runHistoryRepository.completeRun(result)
            if (result.totalFailed > 0) {
                DiagnosticLog.record(
                    context,
                    "Rule execution completed with file failures: ruleId=${rule.id}, trigger=$triggerType, moved=${result.totalMoved}, failed=${result.totalFailed}",
                )
            }

            onProgress(
                RunProgress(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    progress = 1f,
                    filesMoved = result.totalMoved,
                    totalFiles = allFiles.size,
                    isComplete = true,
                ),
            )

            return result
        }
    }
