package dev.bikram.filepipe.ui.screens.rules

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.FolderAccessMode
import dev.bikram.filepipe.data.preferences.SwipeAction
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.FileOperationRepository
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.data.repository.RunHistoryRepository
import dev.bikram.filepipe.data.storage.isFilesystemAccessEffective
import dev.bikram.filepipe.data.storage.isFolderPathAllFilesAccessLocationForRules
import dev.bikram.filepipe.devtools.DevMockFileMove
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.FolderAccessResult
import dev.bikram.filepipe.domain.model.HistorySortDirection
import dev.bikram.filepipe.domain.model.HistorySortKey
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.PreviewFileResult
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RunProgress
import dev.bikram.filepipe.domain.model.RunResult
import dev.bikram.filepipe.domain.model.TriggerType
import dev.bikram.filepipe.domain.usecase.ExecuteRulesUseCase
import dev.bikram.filepipe.domain.usecase.RulesAutoExportTrigger
import dev.bikram.filepipe.domain.usecase.ScheduleRulesUseCase
import dev.bikram.filepipe.domain.usecase.SimulateRuleUseCase
import dev.bikram.filepipe.manualrun.ManualRunForegroundCoordinator
import dev.bikram.filepipe.shortcuts.AppShortcutsManager
import dev.bikram.filepipe.shortcuts.PendingShortcutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

data class DeleteUndoEvent(
    val rules: List<Rule>,
)

data class PreviewState(
    val ruleName: String,
    val isLoading: Boolean = true,
    val results: List<PreviewFileResult> = emptyList(),
    val selectedRuleCount: Int? = null,
    val ruleGroups: List<PreviewRuleGroup> = emptyList(),
)

data class PreviewRuleGroup(
    val ruleId: Long,
    val ruleName: String,
    val operationMode: OperationMode,
    val results: List<PreviewFileResult>,
)

enum class RuleFolderIssueSeverity {
    WARNING,
    ERROR,
}

sealed interface RulesRunNavigation {
    data class HistoryDetail(
        val historyId: Long,
    ) : RulesRunNavigation

    data object HistoryList : RulesRunNavigation
}

/** Where the sole in-run Cancel control is shown for manual runs. */
sealed interface ManualRunCancelAnchor {
    data object None : ManualRunCancelAnchor

    data class SingleRule(
        val ruleId: Long,
    ) : ManualRunCancelAnchor

    data object RunSelectedBar : ManualRunCancelAnchor
}

data class RulesUiState(
    val rules: List<Rule> = emptyList(),
    val staleRuleIds: Set<Long> = emptySet(),
    val staleRuleWarningIds: Set<Long> = emptySet(),
    val staleRuleErrorIds: Set<Long> = emptySet(),
    val swipeStartToEnd: SwipeAction = SwipeAction.EDIT,
    val swipeEndToStart: SwipeAction = SwipeAction.DELETE,
    val selectedRuleIds: Set<Long> = emptySet(),
    val progressMap: Map<Long, RunProgress> = emptyMap(),
    val isRunning: Boolean = false,
    val manualRunCancelAnchor: ManualRunCancelAnchor = ManualRunCancelAnchor.None,
    val previewState: PreviewState? = null,
    val isCompactMode: Boolean = false,
    val cardModeOverrides: Set<Long> = emptySet(),
    val sortKey: HistorySortKey = HistorySortKey.LAST_RAN,
    val sortDirection: HistorySortDirection = HistorySortDirection.DESCENDING,
)

@HiltViewModel
class RulesViewModel
    @Inject
    constructor(
        private val ruleRepository: RuleRepository,
        private val runHistoryRepository: RunHistoryRepository,
        private val scheduleRulesUseCase: ScheduleRulesUseCase,
        private val executeRulesUseCase: ExecuteRulesUseCase,
        private val simulateRuleUseCase: SimulateRuleUseCase,
        private val rulesAutoExportTrigger: RulesAutoExportTrigger,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val appShortcutsManager: AppShortcutsManager,
        private val pendingShortcutRepository: PendingShortcutRepository,
        private val fileOperationRepository: FileOperationRepository,
        private val manualRunForegroundCoordinator: ManualRunForegroundCoordinator,
        @param:ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        // Eagerly so Room query starts before the UI renders, preventing an empty-state flash on cold start.
        private val _rules: StateFlow<List<Rule>> =
            ruleRepository
                .getAllRules()
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        private val _staleRuleIssues = MutableStateFlow<Map<Long, RuleFolderIssueSeverity>>(emptyMap())
        private val _selectedRuleIds = MutableStateFlow<Set<Long>>(emptySet())
        private val _progressMap = MutableStateFlow<Map<Long, RunProgress>>(emptyMap())
        private val _previewState = MutableStateFlow<PreviewState?>(null)
        private val _isCompactMode = MutableStateFlow(false)
        private val _cardModeOverrides = MutableStateFlow<Set<Long>>(emptySet())
        private val _sortKey = MutableStateFlow(HistorySortKey.LAST_RAN)
        private val _sortDirection = MutableStateFlow(HistorySortDirection.DESCENDING)
        private val _manualRunCancelAnchor = MutableStateFlow<ManualRunCancelAnchor>(ManualRunCancelAnchor.None)

        private val lastRunStartedAtByRuleId: StateFlow<Map<Long, Long>> =
            runHistoryRepository
                .observeLastRunStartedAtByRuleId()
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

        private val sortedRulesFlow =
            combine(
                _rules,
                _sortKey,
                _sortDirection,
                lastRunStartedAtByRuleId,
            ) { rules, sortKey, sortDirection, lastRunMap ->
                sortRulesList(rules, sortKey, sortDirection, lastRunMap)
            }

        private val sortParamsFlow =
            combine(_sortKey, _sortDirection) { sortKey, sortDirection ->
                sortKey to sortDirection
            }

        val uiState: StateFlow<RulesUiState> =
            combine(
                sortedRulesFlow,
                _staleRuleIssues,
                userPreferencesRepository.preferencesFlow,
                _selectedRuleIds,
                sortParamsFlow,
            ) { sortedRules, staleIssues, prefs, selected, sortParams ->
                val (sortKey, sortDirection) = sortParams
                val staleWarningIds =
                    staleIssues
                        .filterValues { it == RuleFolderIssueSeverity.WARNING }
                        .keys
                val staleErrorIds =
                    staleIssues
                        .filterValues { it == RuleFolderIssueSeverity.ERROR }
                        .keys
                RulesUiState(
                    rules = sortedRules,
                    staleRuleIds = staleIssues.keys,
                    staleRuleWarningIds = staleWarningIds,
                    staleRuleErrorIds = staleErrorIds,
                    swipeStartToEnd = prefs.swipeStartToEnd,
                    swipeEndToStart = prefs.swipeEndToStart,
                    selectedRuleIds = selected,
                    sortKey = sortKey,
                    sortDirection = sortDirection,
                )
            }.combine(_progressMap) { state, progress ->
                state.copy(progressMap = progress, isRunning = progress.values.any { !it.isComplete })
            }.combine(_previewState) { state, preview -> state.copy(previewState = preview) }
                .combine(_isCompactMode) { state, compact -> state.copy(isCompactMode = compact) }
                .combine(_cardModeOverrides) { state, overrides -> state.copy(cardModeOverrides = overrides) }
                .combine(_manualRunCancelAnchor) { state, anchor -> state.copy(manualRunCancelAnchor = anchor) }
                // Eagerly: WhileSubscribed stops collecting when RuleDetail is shown (RulesScreen leaves
                // composition), so Room updates would not refresh the list until the ViewModel was recreated.
                .stateIn(viewModelScope, SharingStarted.Eagerly, RulesUiState())

        private val _navigateAfterRun = MutableSharedFlow<RulesRunNavigation>(extraBufferCapacity = 1)
        val navigateAfterRun = _navigateAfterRun.asSharedFlow()

        private val _deleteUndoEvent = MutableSharedFlow<DeleteUndoEvent>(extraBufferCapacity = 1)
        val deleteUndoEvent = _deleteUndoEvent.asSharedFlow()

        private val _userMessage = MutableStateFlow<String?>(null)
        val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

        private var manualRunJob: Job? = null
        private val manualRunJobLock = Any()

        fun clearUserMessage() {
            _userMessage.value = null
        }

        /**
         * Clears the folder readability cache and recomputes which rules lack access.
         * Call when returning to the rules list (e.g. after re-granting SAF permission) because
         * [folderPathsSignature] does not change when only permissions change.
         */
        fun refreshStaleFolderAccess() {
            viewModelScope.launch(Dispatchers.IO) {
                fileOperationRepository.invalidateAccessCache()
                val prefs = userPreferencesRepository.preferencesFlow.first()
                val filesystemAccessEnabled = isFilesystemAccessEffective(prefs.folderAccessMode)
                _staleRuleIssues.value = computeStaleRuleIssues(_rules.value, filesystemAccessEnabled)
            }
        }

        fun cancelManualRun() {
            viewModelScope.launch {
                val toCancel =
                    synchronized(manualRunJobLock) {
                        val current = manualRunJob
                        manualRunJob = null
                        current
                    }
                toCancel?.cancel(CancellationException("User cancelled"))
                toCancel?.join()
                _manualRunCancelAnchor.value = ManualRunCancelAnchor.None
                manualRunForegroundCoordinator.setManualRunActive(false)
                if (toCancel == null) {
                    clearRunProgressOnly()
                }
            }
        }

        init {
            viewModelScope.launch {
                var lastFolderSignature: FolderSignature? = null
                combine(_rules, userPreferencesRepository.preferencesFlow) { ruleList, prefs ->
                    Triple(
                        ruleList,
                        folderPathsSignature(ruleList, prefs.folderAccessMode),
                        isFilesystemAccessEffective(prefs.folderAccessMode),
                    )
                }.collect { (ruleList, signature, filesystemAccessEnabled) ->
                    if (signature != lastFolderSignature) {
                        lastFolderSignature = signature
                        _staleRuleIssues.value =
                            withContext(Dispatchers.IO) {
                                computeStaleRuleIssues(ruleList, filesystemAccessEnabled)
                            }
                    }
                }
            }
            _rules.onEach { appShortcutsManager.updateShortcuts(it) }.launchIn(viewModelScope)
            pendingShortcutRepository.pendingRuleId
                .combine(_rules) { pendingRuleId, rules -> pendingRuleId to rules }
                .onEach { (pendingRuleId, rules) ->
                    val ruleId = pendingRuleId ?: return@onEach
                    val rule = rules.find { it.id == ruleId }
                    if (rule == null) {
                        if (rules.isNotEmpty()) {
                            pendingShortcutRepository.clearPendingRule()
                        }
                        return@onEach
                    }
                    pendingShortcutRepository.clearPendingRule()
                    runRule(rule)
                }.launchIn(viewModelScope)
        }

        fun startPreview(rule: Rule) =
            viewModelScope.launch {
                if (DevMockFileMove.isMockRule(rule)) {
                    _previewState.value =
                        PreviewState(
                            ruleName = rule.name,
                            isLoading = false,
                            results = mockFileMovePreviewResults(),
                            ruleGroups =
                                listOf(
                                    PreviewRuleGroup(
                                        ruleId = rule.id,
                                        ruleName = rule.name,
                                        operationMode = OperationMode.MOVE,
                                        results = mockFileMovePreviewResults(),
                                    ),
                                ),
                        )
                    return@launch
                }
                _previewState.value = PreviewState(ruleName = rule.name, isLoading = true)
                val results = simulateRuleUseCase(rule)
                _previewState.value =
                    PreviewState(
                        ruleName = rule.name,
                        isLoading = false,
                        results = results,
                        ruleGroups =
                            listOf(
                                PreviewRuleGroup(
                                    ruleId = rule.id,
                                    ruleName = rule.name,
                                    operationMode = rule.operationMode,
                                    results = results,
                                ),
                            ),
                    )
            }

        fun startPreviewSelected() =
            viewModelScope.launch {
                val selectedRules = _rules.value.filter { rule -> rule.id in _selectedRuleIds.value }
                if (selectedRules.isEmpty()) return@launch
                _previewState.value =
                    PreviewState(
                        ruleName = "",
                        isLoading = true,
                        selectedRuleCount = selectedRules.size,
                    )
                val ruleGroups =
                    selectedRules.map { rule ->
                        val results =
                            if (DevMockFileMove.isMockRule(rule)) {
                                mockFileMovePreviewResults()
                            } else {
                                simulateRuleUseCase(rule)
                            }
                        PreviewRuleGroup(
                            ruleId = rule.id,
                            ruleName = rule.name,
                            operationMode = rule.operationMode,
                            results = results,
                        )
                    }
                _previewState.value =
                    PreviewState(
                        ruleName = "",
                        isLoading = false,
                        results = ruleGroups.flatMap { it.results },
                        selectedRuleCount = selectedRules.size,
                        ruleGroups = ruleGroups,
                    )
            }

        fun dismissPreview() {
            _previewState.value = null
        }

        fun runPreviewedRules() {
            val preview = _previewState.value ?: return
            val rulesById = _rules.value.associateBy { rule -> rule.id }
            val rulesToRun =
                preview.ruleGroups
                    .filter { ruleGroup -> ruleGroup.results.any { result -> !result.wouldSkip } }
                    .mapNotNull { ruleGroup -> rulesById[ruleGroup.ruleId] }
                    .filter { rule -> rule.isEnabled }
            if (rulesToRun.isEmpty()) return

            _previewState.value = null
            if (rulesToRun.size == 1 && DevMockFileMove.isMockRule(rulesToRun.first())) {
                enqueueMockFileMoveRun(rulesToRun.first())
                return
            }
            val realRulesToRun = rulesToRun.filterNot(DevMockFileMove::isMockRule)
            if (realRulesToRun.isEmpty()) return
            val anchor =
                if (realRulesToRun.size == 1) {
                    ManualRunCancelAnchor.SingleRule(realRulesToRun.first().id)
                } else {
                    ManualRunCancelAnchor.RunSelectedBar
                }
            enqueueManualRun(realRulesToRun, anchor)
        }

        fun isCardExpanded(
            ruleId: Long,
            compact: Boolean,
            overrides: Set<Long>,
        ): Boolean = if (compact) ruleId in overrides else ruleId !in overrides

        fun toggleCardExpansion(ruleId: Long) {
            _cardModeOverrides.update { if (ruleId in it) it - ruleId else it + ruleId }
        }

        fun toggleGlobalViewMode() {
            _isCompactMode.update { !it }
            _cardModeOverrides.value = emptySet()
        }

        fun toggleSelection(ruleId: Long) {
            _selectedRuleIds.update { current ->
                if (ruleId in current) current - ruleId else current + ruleId
            }
        }

        fun clearSelection() {
            _selectedRuleIds.value = emptySet()
        }

        fun selectAll() {
            _selectedRuleIds.value = _rules.value.map { it.id }.toSet()
        }

        fun setSort(
            sortKey: HistorySortKey,
            sortDirection: HistorySortDirection,
        ) {
            _sortKey.value = sortKey
            _sortDirection.value = sortDirection
        }

        fun deleteSelected() =
            viewModelScope.launch {
                val toDelete = _rules.value.filter { it.id in _selectedRuleIds.value }
                toDelete.forEach { rule ->
                    scheduleRulesUseCase.cancelRule(rule)
                    ruleRepository.moveRuleToTrash(rule.id)
                }
                rulesAutoExportTrigger.maybeExportAfterRuleChange()
                clearSelection()
                if (toDelete.isNotEmpty()) _deleteUndoEvent.emit(DeleteUndoEvent(toDelete))
            }

        fun clearRunProgressOnly() {
            _progressMap.value = emptyMap()
        }

        fun clearProgress() {
            clearRunProgressOnly()
            _selectedRuleIds.value = emptySet()
        }

        fun toggleEnabled(
            rule: Rule,
            enabled: Boolean,
        ) = viewModelScope.launch {
            val updated = rule.copy(isEnabled = enabled)
            ruleRepository.updateRule(updated)
            if (enabled) {
                scheduleRulesUseCase.scheduleRule(updated)
            } else {
                scheduleRulesUseCase.cancelRule(updated)
            }
            rulesAutoExportTrigger.maybeExportAfterRuleChange()
        }

        fun deleteRule(rule: Rule) =
            viewModelScope.launch {
                scheduleRulesUseCase.cancelRule(rule)
                ruleRepository.moveRuleToTrash(rule.id)
                rulesAutoExportTrigger.maybeExportAfterRuleChange()
                _deleteUndoEvent.emit(DeleteUndoEvent(listOf(rule)))
            }

        fun undoDelete(rules: List<Rule>) =
            viewModelScope.launch {
                rules.forEach { rule ->
                    ruleRepository.restoreRuleFromTrash(rule.id)
                    if (rule.isEnabled && rule.schedule != null) {
                        scheduleRulesUseCase.scheduleRule(rule)
                    }
                }
                rulesAutoExportTrigger.maybeExportAfterRuleChange()
            }

        fun runSelected() {
            val selected = _rules.value.filter { it.id in _selectedRuleIds.value && it.isEnabled }
            if (selected.size == 1 && DevMockFileMove.isMockRule(selected.first())) {
                enqueueMockFileMoveRun(selected.first())
                return
            }
            enqueueManualRunAfterPreview(selected, ManualRunCancelAnchor.RunSelectedBar)
        }

        fun runRule(rule: Rule) {
            if (!rule.isEnabled) return
            if (DevMockFileMove.isMockRule(rule)) {
                enqueueMockFileMoveRun(rule)
                return
            }
            enqueueManualRunAfterPreview(listOf(rule), ManualRunCancelAnchor.SingleRule(rule.id))
        }

        private fun enqueueManualRunAfterPreview(
            rules: List<Rule>,
            anchor: ManualRunCancelAnchor,
        ) {
            val realRules = rules.filterNot(DevMockFileMove::isMockRule)
            if (realRules.isEmpty()) return
            viewModelScope.launch {
                val rulesWithAffectedFiles =
                    realRules.filter { rule ->
                        simulateRuleUseCase(rule).any { result -> !result.wouldSkip }
                    }
                if (rulesWithAffectedFiles.isEmpty()) {
                    _userMessage.value = appContext.getString(R.string.history_no_files_affected)
                    return@launch
                }
                enqueueManualRun(rulesWithAffectedFiles, anchor)
            }
        }

        /**
         * Runs [rules] in-process for immediate start. [ManualRunForegroundService] is started from
         * [MainActivity] when the app goes to background while a manual run is active.
         *
         * Uses a [CoroutineStart.LAZY] job so the slot can be updated before the previous runner is
         * cancelled and joined, avoiding overlapping executions and stale [manualRunJob] identity.
         */
        private fun enqueueManualRun(
            rules: List<Rule>,
            anchor: ManualRunCancelAnchor,
        ) {
            if (rules.isEmpty()) return
            viewModelScope.launch {
                val newJob =
                    viewModelScope.launch(start = CoroutineStart.LAZY) {
                        val selfJob = coroutineContext[Job]!!
                        manualRunForegroundCoordinator.setManualRunActive(true)
                        _manualRunCancelAnchor.value = anchor
                        _progressMap.value =
                            rules.associate { rule ->
                                rule.id to
                                    RunProgress(
                                        ruleId = rule.id,
                                        ruleName = rule.name,
                                        progress = 0f,
                                    )
                            }
                        try {
                            val results =
                                executeRulesUseCase(rules, TriggerType.MANUAL) { progress ->
                                    _progressMap.update { current -> current + (progress.ruleId to progress) }
                                }
                            when {
                                results.size == 1 -> {
                                    _navigateAfterRun.emit(
                                        RulesRunNavigation.HistoryDetail(results.first().historyId),
                                    )
                                }

                                results.isNotEmpty() -> {
                                    _navigateAfterRun.emit(RulesRunNavigation.HistoryList)
                                }
                            }
                        } catch (_: CancellationException) {
                            // History finalized inside ExecuteRulesUseCase
                        } finally {
                            manualRunForegroundCoordinator.setManualRunActive(false)
                            synchronized(manualRunJobLock) {
                                if (manualRunJob === selfJob) {
                                    manualRunJob = null
                                }
                            }
                            _manualRunCancelAnchor.value = ManualRunCancelAnchor.None
                            clearProgress()
                        }
                    }
                val previousJob =
                    synchronized(manualRunJobLock) {
                        val old = manualRunJob
                        manualRunJob = newJob
                        old
                    }
                previousJob?.cancel()
                previousJob?.cancelAndJoin()
                newJob.start()
            }
        }

        private fun enqueueMockFileMoveRun(rule: Rule) {
            viewModelScope.launch {
                val newJob =
                    viewModelScope.launch(start = CoroutineStart.LAZY) {
                        val selfJob = coroutineContext[Job]!!
                        val fileNames = mockLargeFileNames()
                        manualRunForegroundCoordinator.setManualRunActive(true)
                        _manualRunCancelAnchor.value = ManualRunCancelAnchor.SingleRule(rule.id)
                        _progressMap.value =
                            mapOf(
                                rule.id to
                                    RunProgress(
                                        ruleId = rule.id,
                                        ruleName = rule.name,
                                        progress = 0f,
                                        totalFiles = fileNames.size,
                                    ),
                            )
                        try {
                            val startedAt = System.currentTimeMillis()
                            fileNames.forEachIndexed { index, fileName ->
                                if (!isActive) throw CancellationException("User cancelled")
                                _progressMap.update { current ->
                                    current + (
                                        rule.id to
                                            RunProgress(
                                                ruleId = rule.id,
                                                ruleName = rule.name,
                                                progress = index.toFloat() / fileNames.size.toFloat(),
                                                currentFileName = fileName,
                                                filesMoved = index,
                                                totalFiles = fileNames.size,
                                            )
                                    )
                                }
                                delay(450L)
                            }
                            _progressMap.update { current ->
                                current + (
                                    rule.id to
                                        RunProgress(
                                            ruleId = rule.id,
                                            ruleName = rule.name,
                                            progress = 1f,
                                            currentFileName = fileNames.lastOrNull().orEmpty(),
                                            filesMoved = fileNames.size,
                                            totalFiles = fileNames.size,
                                            isComplete = true,
                                        )
                                )
                            }
                            val historyId =
                                runHistoryRepository.startRun(
                                    ruleId = rule.id,
                                    ruleName = rule.name,
                                    triggerType = TriggerType.MANUAL,
                                    operationMode = OperationMode.MOVE,
                                )
                            val completedAt = System.currentTimeMillis()
                            val movedFiles =
                                fileNames.mapIndexed { index, fileName ->
                                    FileMoved(
                                        fileName = fileName,
                                        sourceUri = DevMockFileMove.sourceUri(fileName),
                                        destinationUri = DevMockFileMove.destinationUri(fileName),
                                        fileSizeBytes = DevMockFileMove.FILE_SIZE_BYTES,
                                        movedAt = startedAt + ((completedAt - startedAt) * (index + 1) / fileNames.size),
                                        success = true,
                                    )
                                }
                            runHistoryRepository.completeRun(
                                RunResult(
                                    ruleId = rule.id,
                                    ruleName = rule.name,
                                    historyId = historyId,
                                    filesMoved = movedFiles,
                                    startedAt = startedAt,
                                    completedAt = completedAt,
                                ),
                            )
                            _navigateAfterRun.emit(RulesRunNavigation.HistoryDetail(historyId))
                        } catch (_: CancellationException) {
                            // The mock run never touches storage, so cancellation only clears UI progress.
                        } finally {
                            manualRunForegroundCoordinator.setManualRunActive(false)
                            synchronized(manualRunJobLock) {
                                if (manualRunJob === selfJob) {
                                    manualRunJob = null
                                }
                            }
                            _manualRunCancelAnchor.value = ManualRunCancelAnchor.None
                            clearProgress()
                        }
                    }
                val previousJob =
                    synchronized(manualRunJobLock) {
                        val old = manualRunJob
                        manualRunJob = newJob
                        old
                    }
                previousJob?.cancel()
                previousJob?.cancelAndJoin()
                newJob.start()
            }
        }

        fun duplicateRule(rule: Rule) =
            viewModelScope.launch {
                if (DevMockFileMove.isMockRule(rule)) return@launch
                val copy =
                    rule.copy(
                        id = 0,
                        name = "${rule.name} (copy)",
                        isEnabled = false,
                        schedule = null,
                    )
                ruleRepository.saveRule(copy)
                rulesAutoExportTrigger.maybeExportAfterRuleChange()
                _userMessage.value = "\"${copy.name}\" created"
            }

        private fun mockLargeFileNames(): List<String> =
            appContext.resources
                .getStringArray(R.array.dev_options_mock_large_file_names)
                .toList()

        private fun mockFileMovePreviewResults(): List<PreviewFileResult> =
            mockLargeFileNames().map { fileName ->
                PreviewFileResult(
                    fileName = fileName,
                    sourcePath = DevMockFileMove.sourceUri(fileName),
                    simulatedDestPath = DevMockFileMove.destinationUri(fileName),
                    wouldSkip = false,
                    wouldOverwrite = false,
                    renamedTo = null,
                    sizeBytes = DevMockFileMove.FILE_SIZE_BYTES,
                )
            }

        private data class FolderSignature(
            val ruleIds: List<Long>,
            val sourcePaths: List<List<String>>,
            val destPaths: List<String>,
            val suppressFlags: List<Boolean>,
            val accessMode: FolderAccessMode,
            val isExternalStorageManager: Boolean,
        )

        private fun folderPathsSignature(
            ruleList: List<Rule>,
            folderAccessMode: FolderAccessMode,
        ): FolderSignature {
            val sorted = ruleList.sortedBy { it.id }
            return FolderSignature(
                ruleIds = sorted.map { it.id },
                sourcePaths = sorted.map { it.sourceFolderPaths.sorted() },
                destPaths = sorted.map { it.destinationFolderPath },
                suppressFlags = sorted.map { it.suppressMissingSourceFolderCardWarning },
                accessMode = folderAccessMode,
                isExternalStorageManager = Environment.isExternalStorageManager(),
            )
        }

        private fun computeStaleRuleIssues(
            ruleList: List<Rule>,
            filesystemAccessEnabled: Boolean,
        ): Map<Long, RuleFolderIssueSeverity> =
            ruleList
                .mapNotNull { rule ->
                    ruleFolderIssueSeverity(rule, filesystemAccessEnabled)?.let { severity -> rule.id to severity }
                }.toMap()

        /**
         * Stale banner on the rule list card. Honors [Rule.suppressMissingSourceFolderCardWarning] only
         * when every problem is an [FolderAccessResult.Unavailable] on a **source** path; destination
         * issues and permission denials always show.
         */
        private fun ruleFolderIssueSeverity(
            rule: Rule,
            filesystemAccessEnabled: Boolean,
        ): RuleFolderIssueSeverity? {
            if (DevMockFileMove.isMockRule(rule)) return null
            var sourcePermissionDenied = false
            var sourceHasUnavailable = false
            var sourceHasBlockedLocation = false
            for (path in rule.sourceFolderPaths) {
                when (fileOperationRepository.resolveFolderAccess(path, filesystemAccessEnabled)) {
                    FolderAccessResult.PermissionDenied -> {
                        sourcePermissionDenied = true
                    }

                    FolderAccessResult.Unavailable -> {
                        when {
                            isFolderPathAllFilesAccessLocationForRules(path) -> sourceHasBlockedLocation = true
                            else -> sourceHasUnavailable = true
                        }
                    }

                    FolderAccessResult.Accessible -> {}
                }
            }
            val destinationPath = rule.destinationFolderPath.takeIf { it.isNotBlank() }
            var destinationShowsStale = false
            destinationPath?.let { path ->
                when (fileOperationRepository.resolveFolderAccess(path, filesystemAccessEnabled)) {
                    FolderAccessResult.PermissionDenied -> {
                        return RuleFolderIssueSeverity.ERROR
                    }

                    FolderAccessResult.Unavailable -> {
                        if (isFolderPathAllFilesAccessLocationForRules(path)) {
                            return RuleFolderIssueSeverity.ERROR
                        } else {
                            destinationShowsStale = true
                        }
                    }

                    FolderAccessResult.Accessible -> {
                        Unit
                    }
                }
            }
            if (sourcePermissionDenied) return RuleFolderIssueSeverity.ERROR
            if (sourceHasBlockedLocation) return RuleFolderIssueSeverity.ERROR
            if (destinationShowsStale) return RuleFolderIssueSeverity.ERROR
            if (sourceHasUnavailable) {
                return if (rule.suppressMissingSourceFolderCardWarning) {
                    null
                } else {
                    RuleFolderIssueSeverity.WARNING
                }
            }
            return null
        }

        private fun sortRulesList(
            rules: List<Rule>,
            sortKey: HistorySortKey,
            sortDirection: HistorySortDirection,
            lastRunStartedAtByRuleId: Map<Long, Long>,
        ): List<Rule> {
            when (sortKey) {
                HistorySortKey.MY_ORDER -> {
                    return rules.sortedWith(compareBy({ it.sortOrder }, { it.id }))
                }

                HistorySortKey.LAST_RAN -> {
                    val locale = Locale.getDefault()
                    return when (sortDirection) {
                        HistorySortDirection.DESCENDING -> {
                            rules.sortedWith(
                                compareByDescending<Rule> { lastRunStartedAtByRuleId[it.id] ?: Long.MIN_VALUE }
                                    .thenBy { it.name.lowercase(locale) }
                                    .thenBy { it.id },
                            )
                        }

                        HistorySortDirection.ASCENDING -> {
                            rules.sortedWith(
                                compareBy<Rule> { lastRunStartedAtByRuleId[it.id] ?: Long.MAX_VALUE }
                                    .thenBy { it.name.lowercase(locale) }
                                    .thenBy { it.id },
                            )
                        }
                    }
                }

                HistorySortKey.RULE_NAME -> {
                    val sorted =
                        rules.sortedWith(
                            compareBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                        )
                    return if (sortDirection == HistorySortDirection.DESCENDING) sorted.reversed() else sorted
                }
            }
        }

        fun persistMyOrder(ordered: List<Rule>) =
            viewModelScope.launch(Dispatchers.IO) {
                persistSortOrderIndices(ordered)
                rulesAutoExportTrigger.maybeExportAfterRuleChange()
            }

        /**
         * Persists [ordered] as [Rule.sortOrder] indices. Optionally switches the rules list sort to
         * [HistorySortKey.MY_ORDER] after IO so Room emits updated rows before the UI treats order as canonical.
         */
        fun applyDraggedOrder(
            ordered: List<Rule>,
            alsoSwitchSortToMyOrder: Boolean,
        ) = viewModelScope.launch(Dispatchers.IO) {
            persistSortOrderIndices(ordered)
            rulesAutoExportTrigger.maybeExportAfterRuleChange()
            if (alsoSwitchSortToMyOrder) {
                withContext(Dispatchers.Main.immediate) {
                    _sortKey.value = HistorySortKey.MY_ORDER
                    _sortDirection.value = HistorySortDirection.ASCENDING
                }
            }
        }

        private suspend fun persistSortOrderIndices(ordered: List<Rule>) {
            ruleRepository.persistOrderedSortIndices(ordered)
        }
    }
