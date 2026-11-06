package dev.bikram.filepipe.ui.screens.rules

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.data.preferences.FolderAccessMode
import dev.bikram.filepipe.data.preferences.SwipeAction
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.FileEntry
import dev.bikram.filepipe.data.repository.FileOperationRepository
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.data.repository.RunHistoryRepository
import dev.bikram.filepipe.data.storage.isFilesystemAccessEffective
import dev.bikram.filepipe.data.storage.isFolderPathAllFilesAccessLocationForRules
import dev.bikram.filepipe.devtools.DevMockFileMove
import dev.bikram.filepipe.di.IoDispatcher
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.domain.RuleFolderSeverity
import dev.bikram.filepipe.domain.assessRuleFolderAccess
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DeleteUndoEvent(
    val rules: List<Rule>,
)

data class PreviewState(
    val isLoading: Boolean = true,
    val results: List<PreviewFileResult> = emptyList(),
    val ruleGroups: List<PreviewRuleGroup> = emptyList(),
)

data class PreviewRuleGroup(
    val ruleId: Long,
    val ruleName: String,
    val operationMode: OperationMode,
    val results: List<PreviewFileResult>,
)

/**
 * A rule's group while its preview is still scanning: no results yet, but the operation is known,
 * so the sheet's action button reads Move / Copy / Delete from the first frame.
 */
private fun Rule.toPendingPreviewGroup() = PreviewRuleGroup(ruleId = id, ruleName = name, operationMode = operationMode, results = emptyList())

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

/**
 * Pending confirmation for a manual run that would permanently delete files. Surfaced to the UI so
 * the user must explicitly confirm before an irreversible delete rule executes. Scheduled runs do
 * not go through this path and are unaffected.
 */
data class PendingDeleteConfirmation(
    val fileCount: Int,
    val sampleFileNames: List<String>,
)

/** How many affected file names to preview in the delete-confirmation dialog. */
private const val DELETE_CONFIRM_SAMPLE_SIZE = 5
private const val DELETE_CONFIRMATION_MAX_AGE_MS = 300_000L

/**
 * Builds the delete confirmation for a manual run: flattens each delete rule's simulated results,
 * keeps only files that would actually be removed (not skipped), and returns null when nothing
 * would be deleted — so no confirmation is shown and the run proceeds normally. Pure and
 * module-internal for unit testing without a full ViewModel harness.
 */
internal fun deleteConfirmationFor(
    deleteRuleResults: List<List<PreviewFileResult>>,
    sampleSize: Int = DELETE_CONFIRM_SAMPLE_SIZE,
): PendingDeleteConfirmation? {
    val affected = deleteRuleResults.flatten().filter { result -> !result.wouldSkip }
    if (affected.isEmpty()) return null
    return PendingDeleteConfirmation(
        fileCount = affected.size,
        sampleFileNames = affected.take(sampleSize).map { it.fileName },
    )
}

/** Where the sole in-run Cancel control is shown for manual runs. */
sealed interface ManualRunCancelAnchor {
    data object None : ManualRunCancelAnchor

    data class SingleRule(
        val ruleId: Long,
    ) : ManualRunCancelAnchor

    data object RunSelectedBar : ManualRunCancelAnchor
}

/** A sorted rules list together with the sort selection that produced it. */
private data class SortedRules(
    val rules: List<Rule>,
    val sortKey: HistorySortKey,
    val sortDirection: HistorySortDirection,
)

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
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
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
        private var previewJob: Job? = null
        private var previewRequestId = 0L

        // Delete rules' scanned file lists from the open preview, so the sheet's Delete button removes
        // exactly the files it listed (see runPreviewedRules). Reset with every preview request.
        private var previewPreparedDeleteEntries: Map<Long, List<FileEntry>> = emptyMap()
        private var previewPreparedAtMillis = 0L
        private val _manualRunCancelAnchor = MutableStateFlow<ManualRunCancelAnchor>(ManualRunCancelAnchor.None)

        // A manual run deferred pending the user's delete confirmation, plus the confirmation shown
        // to the UI. Kept out of the main uiState combine (mirrors _manualRunCancelAnchor's pattern).
        private var pendingManualRun: PendingManualRun? = null
        private var deleteConfirmationJob: Job? = null
        private var deleteConfirmationRequestId = 0L
        private val _pendingDeleteConfirmation = MutableStateFlow<PendingDeleteConfirmation?>(null)
        val pendingDeleteConfirmation: StateFlow<PendingDeleteConfirmation?> = _pendingDeleteConfirmation.asStateFlow()

        private val rulesCompactModeFlow =
            userPreferencesRepository.preferencesFlow
                .map { prefs -> prefs.rulesCompactMode }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    AppPreferences.DEFAULT.rulesCompactMode,
                )

        private val rulesSortPreferencesFlow =
            userPreferencesRepository.preferencesFlow
                .map { prefs -> prefs.rulesSortKey to prefs.rulesSortDirection }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    AppPreferences.DEFAULT.rulesSortKey to AppPreferences.DEFAULT.rulesSortDirection,
                )

        // The sort selection travels with the list it produced. Reading the selection from a second
        // subscription to the preferences flow let combine() pair a freshly changed sort key with the
        // previously sorted list, so the UI was told "My order" while holding name-sorted rules.
        private val sortedRulesFlow =
            combine(
                _rules,
                rulesSortPreferencesFlow,
            ) { rules, sortParams ->
                val (sortKey, sortDirection) = sortParams
                SortedRules(
                    rules = sortRulesList(rules, sortKey, sortDirection),
                    sortKey = sortKey,
                    sortDirection = sortDirection,
                )
            }

        val uiState: StateFlow<RulesUiState> =
            combine(
                sortedRulesFlow,
                _staleRuleIssues,
                userPreferencesRepository.preferencesFlow,
                _selectedRuleIds,
            ) { sortedRules, staleIssues, prefs, selected ->
                val staleWarningIds =
                    staleIssues
                        .filterValues { it == RuleFolderIssueSeverity.WARNING }
                        .keys
                val staleErrorIds =
                    staleIssues
                        .filterValues { it == RuleFolderIssueSeverity.ERROR }
                        .keys
                RulesUiState(
                    rules = sortedRules.rules,
                    staleRuleIds = staleIssues.keys,
                    staleRuleWarningIds = staleWarningIds,
                    staleRuleErrorIds = staleErrorIds,
                    swipeStartToEnd = prefs.swipeStartToEnd,
                    swipeEndToStart = prefs.swipeEndToStart,
                    selectedRuleIds = selected,
                    sortKey = sortedRules.sortKey,
                    sortDirection = sortedRules.sortDirection,
                )
            }.combine(_progressMap) { state, progress ->
                state.copy(progressMap = progress, isRunning = progress.values.any { !it.isComplete })
            }.combine(_previewState) { state, preview -> state.copy(previewState = preview) }
                .combine(rulesCompactModeFlow) { state, compact ->
                    state.copy(isCompactMode = compact)
                }.combine(_rules) { state, rules ->
                    state.copy(
                        cardModeOverrides =
                            rules
                                .filter { rule -> rule.cardModeOverride }
                                .map { rule -> rule.id }
                                .toSet(),
                    )
                }.combine(_manualRunCancelAnchor) { state, anchor ->
                    state.copy(manualRunCancelAnchor = anchor)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesUiState())

        private val _navigateAfterRun = MutableSharedFlow<RulesRunNavigation>(extraBufferCapacity = 1)
        val navigateAfterRun = _navigateAfterRun.asSharedFlow()

        private val _deleteUndoEvent = MutableSharedFlow<DeleteUndoEvent>(extraBufferCapacity = 1)
        val deleteUndoEvent = _deleteUndoEvent.asSharedFlow()

        // One-shot snackbar messages: a Channel (not a StateFlow) so each message is delivered
        // exactly once — no replay on rotation, and identical/rapid messages aren't conflated.
        private val _userMessages = Channel<String>(Channel.BUFFERED)
        val userMessages: Flow<String> = _userMessages.receiveAsFlow()

        private var manualRunJob: Job? = null
        private val manualRunJobLock = Any()

        private fun postUserMessage(message: String) {
            _userMessages.trySend(message)
        }

        /**
         * Clears the folder readability cache and recomputes which rules lack access.
         * Call when returning to the rules list (e.g. after re-granting SAF permission) because
         * [folderPathsSignature] does not change when only permissions change.
         */
        fun refreshStaleFolderAccess() {
            viewModelScope.launch(ioDispatcher) {
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
                            withContext(ioDispatcher) {
                                computeStaleRuleIssues(ruleList, filesystemAccessEnabled)
                            }
                    }
                }
            }
            _rules.onEach { appShortcutsManager.updateShortcuts(it) }.launchIn(viewModelScope)
        }

        /**
         * Opens the preview sheet for a rule launched from a launcher shortcut. Collected by
         * RulesScreen (not from init) so a request is only consumed while the sheet can actually
         * show; AppNavigation brings the Rules tab forward first.
         */
        suspend fun previewPendingShortcutRules() {
            pendingShortcutRepository.pendingPreviewRuleId
                .combine(_rules) { pendingRuleId, rules -> pendingRuleId to rules }
                .collect { (pendingRuleId, rules) ->
                    val ruleId = pendingRuleId ?: return@collect
                    val rule = rules.find { it.id == ruleId }
                    if (rule == null) {
                        if (rules.isNotEmpty()) {
                            pendingShortcutRepository.clearPendingPreviewRule()
                        }
                        return@collect
                    }
                    pendingShortcutRepository.clearPendingPreviewRule()
                    startPreview(rule)
                }
        }

        /** Scans [rule] for the preview sheet, keeping a delete rule's file list for [runPreviewedRules]. */
        private suspend fun simulateForPreview(
            rule: Rule,
            preparedDeleteEntries: MutableMap<Long, List<FileEntry>>,
        ): List<PreviewFileResult> {
            val prepared = simulateRuleUseCase.prepare(rule)
            if (rule.operationMode == OperationMode.DELETE) {
                preparedDeleteEntries[rule.id] = prepared.fileEntries
            }
            return prepared.previewResults
        }

        private fun beginPreviewRequest(): Long {
            previewJob?.cancel()
            previewRequestId += 1
            previewPreparedDeleteEntries = emptyMap()
            previewPreparedAtMillis = 0L
            return previewRequestId
        }

        fun startPreview(rule: Rule) {
            val requestId = beginPreviewRequest()
            previewJob =
                viewModelScope.launch {
                    if (DevMockFileMove.isMockRule(rule)) {
                        if (previewRequestId != requestId) return@launch
                        _previewState.value =
                            PreviewState(
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
                    if (previewRequestId != requestId) return@launch
                    _previewState.value =
                        PreviewState(
                            isLoading = true,
                            ruleGroups = listOf(rule.toPendingPreviewGroup()),
                        )
                    val scanStartedAtMillis = System.currentTimeMillis()
                    val preparedDeleteEntries = mutableMapOf<Long, List<FileEntry>>()
                    val results = simulateForPreview(rule, preparedDeleteEntries)
                    if (previewRequestId != requestId) return@launch
                    previewPreparedDeleteEntries = preparedDeleteEntries
                    previewPreparedAtMillis = scanStartedAtMillis
                    _previewState.value =
                        PreviewState(
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
        }

        fun startPreviewSelected() {
            val selectedRules = _rules.value.filter { rule -> rule.id in _selectedRuleIds.value }
            if (selectedRules.isEmpty()) return
            val requestId = beginPreviewRequest()
            previewJob =
                viewModelScope.launch {
                    if (previewRequestId != requestId) return@launch
                    _previewState.value =
                        PreviewState(
                            isLoading = true,
                            ruleGroups = selectedRules.map { rule -> rule.toPendingPreviewGroup() },
                        )
                    val scanStartedAtMillis = System.currentTimeMillis()
                    val preparedDeleteEntries = mutableMapOf<Long, List<FileEntry>>()
                    val ruleGroups =
                        selectedRules.map { rule ->
                            val results =
                                if (DevMockFileMove.isMockRule(rule)) {
                                    mockFileMovePreviewResults()
                                } else {
                                    simulateForPreview(rule, preparedDeleteEntries)
                                }
                            PreviewRuleGroup(
                                ruleId = rule.id,
                                ruleName = rule.name,
                                operationMode = rule.operationMode,
                                results = results,
                            )
                        }
                    if (previewRequestId != requestId) return@launch
                    previewPreparedDeleteEntries = preparedDeleteEntries
                    previewPreparedAtMillis = scanStartedAtMillis
                    _previewState.value =
                        PreviewState(
                            isLoading = false,
                            results = ruleGroups.flatMap { it.results },
                            ruleGroups = ruleGroups,
                        )
                }
        }

        fun dismissPreview() {
            beginPreviewRequest()
            previewJob = null
            _previewState.value = null
        }

        /**
         * The only manual-run entry point from the Rules screen: every manual run goes through the
         * preview sheet first. For delete rules the sheet itself is the confirmation (it says
         * "permanently deleted" and its button reads Delete in the error color), so the run deletes
         * exactly the files the preview scanned instead of raising the separate delete dialog. A
         * preview older than [DELETE_CONFIRMATION_MAX_AGE_MS] falls back to that dialog's fresh scan.
         */
        fun runPreviewedRules() {
            val preview = _previewState.value ?: return
            val rulesById = _rules.value.associateBy { rule -> rule.id }
            val rulesToRun =
                preview.ruleGroups
                    .filter { ruleGroup -> ruleGroup.results.any { result -> !result.wouldSkip } }
                    .mapNotNull { ruleGroup -> rulesById[ruleGroup.ruleId] }
                    .filter { rule -> rule.isEnabled }
            if (rulesToRun.isEmpty()) return

            val preparedDeleteEntries = previewPreparedDeleteEntries
            val previewAgeMillis = System.currentTimeMillis() - previewPreparedAtMillis
            dismissPreview()
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
            val deleteRuleIds =
                realRulesToRun
                    .filter { rule -> rule.operationMode == OperationMode.DELETE }
                    .map { rule -> rule.id }
            val previewConfirmsDeletes =
                deleteRuleIds.isNotEmpty() &&
                    previewAgeMillis <= DELETE_CONFIRMATION_MAX_AGE_MS &&
                    deleteRuleIds.all { ruleId -> ruleId in preparedDeleteEntries }
            if (previewConfirmsDeletes) {
                invalidateDeleteConfirmation()
                startManualRun(
                    rules = realRulesToRun,
                    anchor = anchor,
                    useCache = true,
                    preparedFileEntriesByRuleId = preparedDeleteEntries.filterKeys { ruleId -> ruleId in deleteRuleIds },
                )
            } else {
                enqueueManualRun(realRulesToRun, anchor, useCache = true)
            }
        }

        fun isCardExpanded(
            ruleId: Long,
            compact: Boolean,
            overrides: Set<Long>,
        ): Boolean = if (compact) ruleId in overrides else ruleId !in overrides

        fun toggleCardExpansion(ruleId: Long) {
            viewModelScope.launch {
                val rule = _rules.value.firstOrNull { it.id == ruleId } ?: return@launch
                ruleRepository.updateCardModeOverride(ruleId, !rule.cardModeOverride)
            }
        }

        fun toggleGlobalViewMode() {
            viewModelScope.launch {
                userPreferencesRepository.setRulesCompactMode(!rulesCompactModeFlow.value)
                ruleRepository.clearCardModeOverrides()
            }
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
        ) = viewModelScope.launch {
            userPreferencesRepository.setRulesSort(sortKey, sortDirection)
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

        private data class PendingManualRun(
            val rules: List<Rule>,
            val anchor: ManualRunCancelAnchor,
            val useCache: Boolean,
            val preparedFileEntriesByRuleId: Map<Long, List<FileEntry>>,
            val preparedAtMillis: Long,
        )

        /**
         * Entry point for every manual run. If any rule in the batch permanently deletes files, this
         * defers to a user confirmation (see [requestDeleteConfirmation]) before anything runs;
         * otherwise it starts immediately. Scheduled runs never reach here, so they stay automatic.
         */
        private fun enqueueManualRun(
            rules: List<Rule>,
            anchor: ManualRunCancelAnchor,
            useCache: Boolean = false,
        ) {
            if (rules.isEmpty()) return
            if (rules.any { it.operationMode == OperationMode.DELETE }) {
                requestDeleteConfirmation(rules, anchor, useCache)
                return
            }
            invalidateDeleteConfirmation()
            startManualRun(rules, anchor, useCache)
        }

        /**
         * Simulates the delete rules in [rules] to count/sample the files that would be permanently
         * removed, then raises a [PendingDeleteConfirmation] for the UI. If nothing would be deleted,
         * the run proceeds normally (its history row records "No changes").
         */
        private fun requestDeleteConfirmation(
            rules: List<Rule>,
            anchor: ManualRunCancelAnchor,
            useCache: Boolean,
        ) {
            invalidateDeleteConfirmation()
            val requestId = deleteConfirmationRequestId
            val scanStartedAtMillis = System.currentTimeMillis()
            deleteConfirmationJob =
                viewModelScope.launch {
                    val preparedFileEntriesByRuleId = mutableMapOf<Long, List<FileEntry>>()
                    val deleteRuleResults =
                        try {
                            rules
                                .filter { it.operationMode == OperationMode.DELETE }
                                .map { rule ->
                                    val preparedSimulation = simulateRuleUseCase.prepare(rule)
                                    preparedFileEntriesByRuleId[rule.id] = preparedSimulation.fileEntries
                                    preparedSimulation.previewResults
                                }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            if (deleteConfirmationRequestId == requestId) {
                                DiagnosticLog.record(appContext, "Delete confirmation scan failed", error)
                                postUserMessage(appContext.getString(R.string.rule_delete_confirmation_scan_failed))
                            }
                            return@launch
                        }
                    if (deleteConfirmationRequestId != requestId) return@launch

                    val confirmation = deleteConfirmationFor(deleteRuleResults)
                    if (confirmation == null) {
                        // Nothing to delete; run normally.
                        startManualRun(rules, anchor, useCache)
                        return@launch
                    }
                    pendingManualRun =
                        PendingManualRun(
                            rules = rules,
                            anchor = anchor,
                            useCache = useCache,
                            preparedFileEntriesByRuleId = preparedFileEntriesByRuleId,
                            preparedAtMillis = scanStartedAtMillis,
                        )
                    _pendingDeleteConfirmation.value = confirmation
                }
        }

        /** Proceeds with a delete run the user confirmed via the [PendingDeleteConfirmation] dialog. */
        fun confirmPendingDelete() {
            val pending = pendingManualRun ?: return
            if (System.currentTimeMillis() - pending.preparedAtMillis > DELETE_CONFIRMATION_MAX_AGE_MS) {
                invalidateDeleteConfirmation()
                requestDeleteConfirmation(
                    rules = pending.rules,
                    anchor = pending.anchor,
                    useCache = pending.useCache,
                )
                return
            }
            invalidateDeleteConfirmation()
            startManualRun(
                rules = pending.rules,
                anchor = pending.anchor,
                useCache = pending.useCache,
                preparedFileEntriesByRuleId = pending.preparedFileEntriesByRuleId,
            )
        }

        /** Cancels a pending delete run without deleting anything. */
        fun dismissPendingDelete() {
            invalidateDeleteConfirmation()
        }

        private fun invalidateDeleteConfirmation() {
            deleteConfirmationRequestId += 1
            deleteConfirmationJob?.cancel()
            deleteConfirmationJob = null
            pendingManualRun = null
            _pendingDeleteConfirmation.value = null
        }

        /**
         * Runs [rules] in-process for immediate start. [ManualRunForegroundService] starts while
         * the app is still foregrounded so the same run can continue if the app backgrounds.
         *
         * Uses a [CoroutineStart.LAZY] job so the slot can be updated before the previous runner is
         * cancelled and joined, avoiding overlapping executions and stale [manualRunJob] identity.
         */
        private fun startManualRun(
            rules: List<Rule>,
            anchor: ManualRunCancelAnchor,
            useCache: Boolean = false,
            preparedFileEntriesByRuleId: Map<Long, List<FileEntry>> = emptyMap(),
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
                        var runCompleted = false
                        try {
                            // Every rule the user ran is executed, even one that matches nothing: that
                            // run is real, so it earns a history row ("No changes", where it can be
                            // filtered by chip or deleted) and a last-run time for sorting.
                            val results =
                                executeRulesUseCase(
                                    rules,
                                    TriggerType.MANUAL,
                                    useCache = useCache,
                                    preparedFileEntriesByRuleId = preparedFileEntriesByRuleId,
                                ) { progress ->
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
                            runCompleted = true
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
                            clearRunProgressOnly()
                            if (runCompleted) {
                                clearSelection()
                            }
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
                        var runCompleted = false
                        var mockHistoryId: Long? = null
                        try {
                            val startedAt = System.currentTimeMillis()
                            val historyId =
                                runHistoryRepository.startRun(
                                    ruleId = rule.id,
                                    ruleName = rule.name,
                                    triggerType = TriggerType.MANUAL,
                                    operationMode = OperationMode.MOVE,
                                )
                            mockHistoryId = historyId
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
                                delay(DevMockFileMove.FILE_OPERATION_DELAY_MILLIS)
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
                            mockHistoryId = null
                            runCompleted = true
                            _navigateAfterRun.emit(RulesRunNavigation.HistoryDetail(historyId))
                        } catch (_: CancellationException) {
                            mockHistoryId?.let { id ->
                                runHistoryRepository.finishRunUserCancelled(id, fileNames.size)
                            }
                        } finally {
                            manualRunForegroundCoordinator.setManualRunActive(false)
                            synchronized(manualRunJobLock) {
                                if (manualRunJob === selfJob) {
                                    manualRunJob = null
                                }
                            }
                            _manualRunCancelAnchor.value = ManualRunCancelAnchor.None
                            clearRunProgressOnly()
                            if (runCompleted) {
                                clearSelection()
                            }
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
                        // The copy has never run, so it must not inherit the original's run time
                        // and sort under "last run" as though it had.
                        lastRunStartedAt = null,
                    )
                ruleRepository.saveRule(copy)
                rulesAutoExportTrigger.maybeExportAfterRuleChange()
                postUserMessage("\"${copy.name}\" created")
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
            val sourceIssues =
                rule.sourceFolderPaths
                    .mapNotNull { path ->
                        val result = fileOperationRepository.resolveFolderAccess(path, filesystemAccessEnabled)
                        if (result == FolderAccessResult.Accessible) null else path to result
                    }.toMap()
            val destinationIssue =
                rule.destinationFolderPath
                    .takeIf { it.isNotBlank() }
                    ?.let { path ->
                        fileOperationRepository
                            .resolveFolderAccess(path, filesystemAccessEnabled)
                            .takeIf { it != FolderAccessResult.Accessible }
                    }
            val assessment =
                assessRuleFolderAccess(
                    sourceIssues = sourceIssues,
                    destinationIssue = destinationIssue,
                    isBlockedLocation = ::isFolderPathAllFilesAccessLocationForRules,
                )
            return when (assessment.severity) {
                RuleFolderSeverity.ERROR -> {
                    RuleFolderIssueSeverity.ERROR
                }

                // Amber source warnings are the only severity the per-rule preference can hide.
                RuleFolderSeverity.WARNING -> {
                    if (rule.suppressMissingSourceFolderCardWarning) null else RuleFolderIssueSeverity.WARNING
                }

                RuleFolderSeverity.NONE -> {
                    null
                }
            }
        }

        private fun sortRulesList(
            rules: List<Rule>,
            sortKey: HistorySortKey,
            sortDirection: HistorySortDirection,
        ): List<Rule> {
            when (sortKey) {
                HistorySortKey.MY_ORDER -> {
                    return rules.sortedWith(compareBy({ it.sortOrder }, { it.id }))
                }

                HistorySortKey.LAST_RAN -> {
                    // A rule that has never run counts as the oldest thing in the list: newest-first
                    // sinks it to the bottom, oldest-first floats it to the top. Rules that tie fall
                    // back to the user's own order rather than to the rule name, which made this sort
                    // indistinguishable from "Rule name (A to Z)".
                    //
                    // One direction is the reverse of the other, so the second is built by reversing
                    // the first instead of re-sorting with a flipped comparator. Flipping only the
                    // timestamp comparison leaves tied rules in the same relative order in both
                    // directions, and the list then doesn't read as reversed at all.
                    val newestFirst =
                        rules.sortedWith(
                            compareByDescending<Rule> { it.lastRunStartedAt ?: Long.MIN_VALUE }
                                .then(compareBy<Rule>({ it.sortOrder }, { it.id })),
                        )
                    return if (sortDirection == HistorySortDirection.ASCENDING) {
                        newestFirst.reversed()
                    } else {
                        newestFirst
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
            viewModelScope.launch(ioDispatcher) {
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
        ) = viewModelScope.launch(ioDispatcher) {
            persistSortOrderIndices(ordered)
            rulesAutoExportTrigger.maybeExportAfterRuleChange()
            if (alsoSwitchSortToMyOrder) {
                userPreferencesRepository.setRulesSort(
                    HistorySortKey.MY_ORDER,
                    HistorySortDirection.ASCENDING,
                )
            }
        }

        private suspend fun persistSortOrderIndices(ordered: List<Rule>) {
            ruleRepository.persistOrderedSortIndices(ordered)
        }
    }
