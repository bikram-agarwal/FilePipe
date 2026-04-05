package dev.bikram.filepipe.ui.screens.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bikram.filepipe.data.preferences.SwipeAction
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.FileOperationRepository
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.domain.model.HistorySortDirection
import dev.bikram.filepipe.domain.model.HistorySortKey
import dev.bikram.filepipe.domain.model.PreviewFileResult
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RunProgress
import dev.bikram.filepipe.domain.model.TriggerType
import dev.bikram.filepipe.domain.usecase.ExecuteRulesUseCase
import dev.bikram.filepipe.domain.usecase.RulesAutoExportTrigger
import dev.bikram.filepipe.domain.usecase.ScheduleRulesUseCase
import dev.bikram.filepipe.domain.usecase.SimulateRuleUseCase
import dev.bikram.filepipe.manualrun.ManualRunForegroundCoordinator
import dev.bikram.filepipe.shortcuts.AppShortcutsManager
import dev.bikram.filepipe.shortcuts.PendingShortcutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeleteUndoEvent(val rules: List<Rule>)

data class PreviewState(
    val ruleName: String,
    val isLoading: Boolean = true,
    val results: List<PreviewFileResult> = emptyList()
)

sealed interface RulesRunNavigation {
    data class HistoryDetail(val historyId: Long) : RulesRunNavigation
    data object HistoryList : RulesRunNavigation
}

/** Where the sole in-run Cancel control is shown for manual runs. */
sealed interface ManualRunCancelAnchor {
    data object None : ManualRunCancelAnchor
    data class SingleRule(val ruleId: Long) : ManualRunCancelAnchor
    data object RunSelectedBar : ManualRunCancelAnchor
}

data class RulesUiState(
    val rules: List<Rule> = emptyList(),
    val staleRuleIds: Set<Long> = emptySet(),
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
    val sortDirection: HistorySortDirection = HistorySortDirection.DESCENDING
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val scheduleRulesUseCase: ScheduleRulesUseCase,
    private val executeRulesUseCase: ExecuteRulesUseCase,
    private val simulateRuleUseCase: SimulateRuleUseCase,
    private val rulesAutoExportTrigger: RulesAutoExportTrigger,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val appShortcutsManager: AppShortcutsManager,
    private val pendingShortcutRepository: PendingShortcutRepository,
    private val fileOperationRepository: FileOperationRepository,
    private val manualRunForegroundCoordinator: ManualRunForegroundCoordinator
) : ViewModel() {

    // Eagerly so Room query starts before the UI renders, preventing an empty-state flash on cold start.
    private val _rules: StateFlow<List<Rule>> = ruleRepository.getAllRules()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _staleRuleIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _selectedRuleIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _progressMap = MutableStateFlow<Map<Long, RunProgress>>(emptyMap())
    private val _previewState = MutableStateFlow<PreviewState?>(null)
    private val _isCompactMode = MutableStateFlow(false)
    private val _cardModeOverrides = MutableStateFlow<Set<Long>>(emptySet())
    private val _sortKey = MutableStateFlow(HistorySortKey.LAST_RAN)
    private val _sortDirection = MutableStateFlow(HistorySortDirection.DESCENDING)
    private val _manualRunCancelAnchor = MutableStateFlow<ManualRunCancelAnchor>(ManualRunCancelAnchor.None)

    private val sortedRulesFlow = combine(_rules, _sortKey, _sortDirection) { rules, sortKey, sortDirection ->
        sortRulesList(rules, sortKey, sortDirection)
    }

    private val sortParamsFlow = combine(_sortKey, _sortDirection) { sortKey, sortDirection ->
        sortKey to sortDirection
    }

    val uiState: StateFlow<RulesUiState> = combine(
        sortedRulesFlow,
        _staleRuleIds,
        userPreferencesRepository.preferencesFlow,
        _selectedRuleIds,
        sortParamsFlow
    ) { sortedRules, stale, prefs, selected, sortParams ->
        val (sortKey, sortDirection) = sortParams
        RulesUiState(
            rules = sortedRules,
            staleRuleIds = stale,
            swipeStartToEnd = prefs.swipeStartToEnd,
            swipeEndToStart = prefs.swipeEndToStart,
            selectedRuleIds = selected,
            sortKey = sortKey,
            sortDirection = sortDirection
        )
    }
        .combine(_progressMap) { state, progress ->
            state.copy(progressMap = progress, isRunning = progress.values.any { !it.isComplete })
        }
        .combine(_previewState) { state, preview -> state.copy(previewState = preview) }
        .combine(_isCompactMode) { state, compact -> state.copy(isCompactMode = compact) }
        .combine(_cardModeOverrides) { state, overrides -> state.copy(cardModeOverrides = overrides) }
        .combine(_manualRunCancelAnchor) { state, anchor -> state.copy(manualRunCancelAnchor = anchor) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesUiState())

    private val _navigateAfterRun = MutableSharedFlow<RulesRunNavigation>(extraBufferCapacity = 1)
    val navigateAfterRun = _navigateAfterRun.asSharedFlow()

    private val _deleteUndoEvent = MutableSharedFlow<DeleteUndoEvent>(extraBufferCapacity = 1)
    val deleteUndoEvent = _deleteUndoEvent.asSharedFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private var manualRunJob: Job? = null
    private val manualRunJobLock = Any()

    fun clearUserMessage() { _userMessage.value = null }

    /**
     * Clears the folder readability cache and recomputes which rules lack access.
     * Call when returning to the rules list (e.g. after re-granting SAF permission) because
     * [folderPathsSignature] does not change when only permissions change.
     */
    fun refreshStaleFolderAccess() {
        viewModelScope.launch(Dispatchers.IO) {
            fileOperationRepository.invalidateAccessCache()
            _staleRuleIds.value = computeStaleRuleIds(_rules.value)
        }
    }

    fun cancelManualRun() {
        viewModelScope.launch {
            val toCancel = synchronized(manualRunJobLock) {
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
            var lastFolderSignature: String? = null
            _rules.collect { ruleList ->
                val signature = folderPathsSignature(ruleList)
                if (signature != lastFolderSignature) {
                    lastFolderSignature = signature
                    _staleRuleIds.value = withContext(Dispatchers.IO) {
                        computeStaleRuleIds(ruleList)
                    }
                }
            }
        }
        _rules.onEach { appShortcutsManager.updateShortcuts(it) }.launchIn(viewModelScope)
        pendingShortcutRepository.pendingRuleId.onEach { ruleId ->
            val rule = _rules.value.find { it.id == ruleId }
            if (rule != null) runRule(rule)
        }.launchIn(viewModelScope)
    }

    fun startPreview(rule: Rule) = viewModelScope.launch {
        _previewState.value = PreviewState(ruleName = rule.name, isLoading = true)
        val results = simulateRuleUseCase(rule)
        _previewState.value = PreviewState(ruleName = rule.name, isLoading = false, results = results)
    }

    fun dismissPreview() { _previewState.value = null }

    fun isCardExpanded(ruleId: Long, compact: Boolean, overrides: Set<Long>): Boolean =
        if (compact) ruleId in overrides else ruleId !in overrides

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

    fun setSort(sortKey: HistorySortKey, sortDirection: HistorySortDirection) {
        _sortKey.value = sortKey
        _sortDirection.value = sortDirection
    }

    fun deleteSelected() = viewModelScope.launch {
        val toDelete = _rules.value.filter { it.id in _selectedRuleIds.value }
        toDelete.forEach { rule ->
            scheduleRulesUseCase.cancelRule(rule)
            ruleRepository.deleteRule(rule.id)
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

    fun toggleEnabled(rule: Rule, enabled: Boolean) = viewModelScope.launch {
        val updated = rule.copy(isEnabled = enabled)
        ruleRepository.updateRule(updated)
        if (enabled) {
            scheduleRulesUseCase.scheduleRule(updated)
        } else {
            scheduleRulesUseCase.cancelRule(updated)
        }
        rulesAutoExportTrigger.maybeExportAfterRuleChange()
    }

    fun deleteRule(rule: Rule) = viewModelScope.launch {
        scheduleRulesUseCase.cancelRule(rule)
        ruleRepository.deleteRule(rule.id)
        rulesAutoExportTrigger.maybeExportAfterRuleChange()
        _deleteUndoEvent.emit(DeleteUndoEvent(listOf(rule)))
    }

    fun undoDelete(rules: List<Rule>) = viewModelScope.launch {
        rules.forEach { rule ->
            ruleRepository.saveRule(rule)
            if (rule.isEnabled && rule.schedule != null) {
                scheduleRulesUseCase.scheduleRule(rule)
            }
        }
        rulesAutoExportTrigger.maybeExportAfterRuleChange()
    }

    fun runSelected() {
        val selected = _rules.value.filter { it.id in _selectedRuleIds.value && it.isEnabled }
        enqueueManualRun(selected, ManualRunCancelAnchor.RunSelectedBar)
    }

    fun runRule(rule: Rule) {
        if (!rule.isEnabled) return
        enqueueManualRun(listOf(rule), ManualRunCancelAnchor.SingleRule(rule.id))
    }

    /**
     * Runs [rules] in-process for immediate start. [ManualRunForegroundService] is started from
     * [MainActivity] when the app goes to background while a manual run is active.
     *
     * Uses a [CoroutineStart.LAZY] job so the slot can be updated before the previous runner is
     * cancelled and joined, avoiding overlapping executions and stale [manualRunJob] identity.
     */
    private fun enqueueManualRun(rules: List<Rule>, anchor: ManualRunCancelAnchor) {
        if (rules.isEmpty()) return
        viewModelScope.launch {
            val newJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
                val selfJob = coroutineContext[Job]!!
                manualRunForegroundCoordinator.setManualRunActive(true)
                _manualRunCancelAnchor.value = anchor
                _progressMap.value = rules.associate { rule ->
                    rule.id to RunProgress(
                        ruleId = rule.id,
                        ruleName = rule.name,
                        progress = 0f
                    )
                }
                try {
                    val results = executeRulesUseCase(rules, TriggerType.MANUAL) { progress ->
                        _progressMap.update { current -> current + (progress.ruleId to progress) }
                    }
                    when {
                        results.size == 1 ->
                            _navigateAfterRun.emit(
                                RulesRunNavigation.HistoryDetail(results.first().historyId)
                            )
                        results.isNotEmpty() ->
                            _navigateAfterRun.emit(RulesRunNavigation.HistoryList)
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
            val previousJob = synchronized(manualRunJobLock) {
                val old = manualRunJob
                manualRunJob = newJob
                old
            }
            previousJob?.cancel()
            previousJob?.cancelAndJoin()
            newJob.start()
        }
    }

    fun duplicateRule(rule: Rule) = viewModelScope.launch {
        val copy = rule.copy(
            id = 0,
            name = "${rule.name} (copy)",
            isEnabled = false,
            schedule = null
        )
        ruleRepository.saveRule(copy)
        rulesAutoExportTrigger.maybeExportAfterRuleChange()
        _userMessage.value = "\"${copy.name}\" created"
    }

    private fun folderPathsSignature(ruleList: List<Rule>): String =
        ruleList.sortedBy { it.id }.joinToString("\u0000") { rule ->
            buildString {
                append(rule.id)
                append('\u0001')
                rule.sourceFolderPaths.sorted().forEach { path ->
                    append(path)
                    append('\u0002')
                }
                append('\u0001')
                append(rule.destinationFolderPath)
            }
        }

    private fun computeStaleRuleIds(ruleList: List<Rule>): Set<Long> =
        ruleList.filter { rule ->
            val allPaths = rule.sourceFolderPaths + listOfNotNull(rule.destinationFolderPath.takeIf { it.isNotBlank() })
            allPaths.any { path ->
                !path.startsWith("content://") || !fileOperationRepository.isAccessible(path)
            }
        }.map { it.id }.toSet()

    private fun sortRulesList(
        rules: List<Rule>,
        sortKey: HistorySortKey,
        sortDirection: HistorySortDirection
    ): List<Rule> {
        val comparator = when (sortKey) {
            HistorySortKey.LAST_RAN -> compareBy<Rule> { it.updatedAt }
            HistorySortKey.RULE_NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        val sorted = rules.sortedWith(comparator)
        return if (sortDirection == HistorySortDirection.DESCENDING) sorted.reversed() else sorted
    }
}
