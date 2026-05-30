package dev.bikram.filepipe.ui.screens.history

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.data.repository.RunHistoryRepository
import dev.bikram.filepipe.domain.model.HistorySortDirection
import dev.bikram.filepipe.domain.model.HistorySortKey
import dev.bikram.filepipe.domain.model.HistoryStatusFilter
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.domain.model.RunStatus
import dev.bikram.filepipe.domain.model.isEffectivelyUndone
import dev.bikram.filepipe.domain.model.isNoChangesRun
import dev.bikram.filepipe.domain.usecase.RulesAutoExportTrigger
import dev.bikram.filepipe.domain.usecase.ScheduleRulesUseCase
import dev.bikram.filepipe.domain.usecase.UndoRunUseCase
import dev.bikram.filepipe.ui.feedback.toUserMessage
import dev.bikram.filepipe.ui.navigation.Screen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

enum class HistoryStatusSection {
    SUCCESS,
    FAILED,
    PARTIAL,
    NO_CHANGES,
    IN_PROGRESS,
    CANCELLED,
    UNDONE,
}

sealed interface HistoryItem {
    data class Entry(
        val history: RunHistory,
    ) : HistoryItem

    data class DateHeader(
        val label: String,
    ) : HistoryItem

    data class RuleHeader(
        val ruleName: String,
        val count: Int,
    ) : HistoryItem

    data class StatusHeader(
        val section: HistoryStatusSection,
        val count: Int,
    ) : HistoryItem
}

enum class HistoryViewMode { BY_DATE, BY_RULE, BY_STATUS }

enum class HistorySection { RUNS, TRASH }

data class HistoryUiState(
    val statusFilter: HistoryStatusFilter = HistoryStatusFilter.ALL,
    val viewMode: HistoryViewMode = HistoryViewMode.BY_DATE,
    val sortKey: HistorySortKey = HistorySortKey.LAST_RAN,
    val sortDirection: HistorySortDirection = HistorySortDirection.DESCENDING,
) {
    val isFilterActive: Boolean
        get() = statusFilter != HistoryStatusFilter.ALL || viewMode != HistoryViewMode.BY_DATE
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val runHistoryRepository: RunHistoryRepository,
        private val ruleRepository: RuleRepository,
        private val scheduleRulesUseCase: ScheduleRulesUseCase,
        private val rulesAutoExportTrigger: RulesAutoExportTrigger,
        private val undoRunUseCase: UndoRunUseCase,
        @param:ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        val filterRuleId: Long? =
            savedStateHandle
                .get<Long>(Screen.HistoryForRule.ARG_RULE_ID)
                ?.takeIf { it > 0 }

        private val historySourceFlow: Flow<List<RunHistory>> =
            if (filterRuleId != null) {
                runHistoryRepository.getHistoryForRule(filterRuleId)
            } else {
                runHistoryRepository.getAllHistory()
            }

        private val _statusFilter = MutableStateFlow(HistoryStatusFilter.ALL)
        private val _viewMode = MutableStateFlow(HistoryViewMode.BY_DATE)
        private val _sortKey = MutableStateFlow(HistorySortKey.LAST_RAN)
        private val _sortDirection = MutableStateFlow(HistorySortDirection.DESCENDING)
        private val _section = MutableStateFlow(HistorySection.RUNS)
        val section: StateFlow<HistorySection> = _section.asStateFlow()

        val uiState: StateFlow<HistoryUiState> =
            combine(
                _statusFilter,
                _viewMode,
                _sortKey,
                _sortDirection,
            ) { status, mode, sortKey, sortDir ->
                HistoryUiState(
                    statusFilter = status,
                    viewMode = mode,
                    sortKey = sortKey,
                    sortDirection = sortDir,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

        val hasAnyHistory: StateFlow<Boolean> =
            runHistoryRepository
                .observeHasAnyHistory()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val trashedRules =
            ruleRepository
                .getTrashedRules()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val availableStatusFilters: StateFlow<Set<HistoryStatusFilter>> =
            historySourceFlow
                .map { histories ->
                    buildSet {
                        add(HistoryStatusFilter.ALL)
                        listOf(
                            HistoryStatusFilter.SUCCESS,
                            HistoryStatusFilter.FAILED,
                            HistoryStatusFilter.PARTIAL,
                            HistoryStatusFilter.NO_CHANGES,
                            HistoryStatusFilter.CANCELLED,
                            HistoryStatusFilter.UNDONE,
                        ).forEach { statusFilter ->
                            if (histories.any { history -> history.matchesHistoryStatusFilter(statusFilter) }) {
                                add(statusFilter)
                            }
                        }
                    }
                }.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    setOf(HistoryStatusFilter.ALL),
                )

        val historyPagingFlow: Flow<PagingData<HistoryItem>> =
            combine(
                _sortKey,
                _sortDirection,
            ) { sortKey, sortDir -> sortKey to sortDir }
                .flatMapLatest { (sortKey, sortDir) ->
                    val baseFlow =
                        if (filterRuleId != null) {
                            runHistoryRepository.getHistoryForRulePaged(filterRuleId, sortKey, sortDir)
                        } else {
                            runHistoryRepository.getAllHistoryPaged(sortKey, sortDir)
                        }
                    baseFlow.map { pagingData ->
                        pagingData
                            .map { history -> HistoryItem.Entry(history) as HistoryItem }
                            .insertSeparators { before, after ->
                                val afterEntry = after as? HistoryItem.Entry ?: return@insertSeparators null
                                val beforeEntry = before as? HistoryItem.Entry
                                if (beforeEntry == null || !isSameDay(beforeEntry.history.startedAt, afterEntry.history.startedAt)) {
                                    HistoryItem.DateHeader(formatDateLabel(afterEntry.history.startedAt))
                                } else {
                                    null
                                }
                            }
                    }
                }.cachedIn(viewModelScope)

        val filteredHistoryItems: StateFlow<List<HistoryItem>> =
            combine(
                historySourceFlow,
                _statusFilter,
                _viewMode,
                _sortKey,
                _sortDirection,
            ) { all, status, mode, sortKey, sortDir ->
                val filtered = all.filter { history -> history.matchesHistoryStatusFilter(status) }
                val sorted = sortHistories(filtered, sortKey, sortDir)
                when (mode) {
                    HistoryViewMode.BY_DATE -> buildDateGroupedItems(sorted)
                    HistoryViewMode.BY_RULE -> buildRuleGroupedItems(sorted)
                    HistoryViewMode.BY_STATUS -> buildStatusGroupedItems(sorted)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val _userMessage = MutableStateFlow<String?>(null)
        val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

        fun clearUserMessage() {
            _userMessage.value = null
        }

        fun setStatusFilter(status: HistoryStatusFilter) {
            _statusFilter.value = status
        }

        fun setViewMode(mode: HistoryViewMode) {
            _viewMode.value = mode
        }

        fun setSort(
            key: HistorySortKey,
            direction: HistorySortDirection,
        ) {
            _sortKey.value = key
            _sortDirection.value = direction
        }

        fun setSection(section: HistorySection) {
            _section.value = section
        }

        fun clearFilters() {
            _statusFilter.value = HistoryStatusFilter.ALL
            _viewMode.value = HistoryViewMode.BY_DATE
        }

        fun clearAllHistory() =
            viewModelScope.launch {
                runHistoryRepository.clearAllHistory()
            }

        fun deleteHistoryEntry(historyId: Long) =
            viewModelScope.launch {
                runHistoryRepository.deleteHistoryById(historyId)
            }

        fun restoreRule(ruleId: Long) =
            viewModelScope.launch {
                val rule = trashedRules.value.firstOrNull { it.id == ruleId }
                ruleRepository.restoreRuleFromTrash(ruleId)
                if (rule?.isEnabled == true && rule.schedule != null) {
                    scheduleRulesUseCase.scheduleRule(rule)
                }
                rulesAutoExportTrigger.maybeExportAfterRuleChange()
            }

        fun deleteRuleForever(ruleId: Long) =
            viewModelScope.launch {
                ruleRepository.deleteRuleForever(ruleId)
                rulesAutoExportTrigger.maybeExportAfterRuleChange()
            }

        fun emptyTrashForever() =
            viewModelScope.launch {
                ruleRepository.emptyTrashForever()
                rulesAutoExportTrigger.maybeExportAfterRuleChange()
            }

        fun undoRun(historyId: Long) =
            viewModelScope.launch {
                val result = undoRunUseCase(historyId)
                _userMessage.value = result.toUserMessage(appContext)
            }

        private fun sortHistories(
            list: List<RunHistory>,
            sortKey: HistorySortKey,
            sortDirection: HistorySortDirection,
        ): List<RunHistory> =
            when (sortKey) {
                HistorySortKey.LAST_RAN, HistorySortKey.MY_ORDER -> {
                    when (sortDirection) {
                        HistorySortDirection.DESCENDING -> list.sortedByDescending { it.startedAt }
                        HistorySortDirection.ASCENDING -> list.sortedBy { it.startedAt }
                    }
                }

                HistorySortKey.RULE_NAME -> {
                    when (sortDirection) {
                        HistorySortDirection.ASCENDING -> {
                            list.sortedWith(
                                compareBy<RunHistory> { it.ruleName.lowercase(Locale.getDefault()) }
                                    .thenByDescending { it.startedAt },
                            )
                        }

                        HistorySortDirection.DESCENDING -> {
                            list.sortedWith(
                                compareByDescending<RunHistory> { it.ruleName.lowercase(Locale.getDefault()) }
                                    .thenByDescending { it.startedAt },
                            )
                        }
                    }
                }
            }

        private fun buildDateGroupedItems(list: List<RunHistory>): List<HistoryItem> {
            if (list.isEmpty()) return emptyList()
            val result = mutableListOf<HistoryItem>()
            val zone = ZoneId.systemDefault()
            var lastDay: LocalDate? = null
            for (history in list) {
                val day = Instant.ofEpochMilli(history.startedAt).atZone(zone).toLocalDate()
                if (day != lastDay) {
                    result.add(HistoryItem.DateHeader(formatDateLabel(history.startedAt)))
                    lastDay = day
                }
                result.add(HistoryItem.Entry(history))
            }
            return result
        }

        private fun buildRuleGroupedItems(list: List<RunHistory>): List<HistoryItem> {
            if (list.isEmpty()) return emptyList()
            val result = mutableListOf<HistoryItem>()
            val groups = list.groupBy { it.ruleName }
            for ((ruleName, entries) in groups) {
                result.add(HistoryItem.RuleHeader(ruleName = ruleName, count = entries.size))
                entries.forEach { result.add(HistoryItem.Entry(it)) }
            }
            return result
        }

        private fun buildStatusGroupedItems(list: List<RunHistory>): List<HistoryItem> {
            if (list.isEmpty()) return emptyList()
            val successWork = mutableListOf<RunHistory>()
            val failed = mutableListOf<RunHistory>()
            val partial = mutableListOf<RunHistory>()
            val noChanges = mutableListOf<RunHistory>()
            val inProgress = mutableListOf<RunHistory>()
            val cancelled = mutableListOf<RunHistory>()
            val undone = mutableListOf<RunHistory>()
            for (history in list) {
                when {
                    history.isEffectivelyUndone() -> undone.add(history)
                    history.isNoChangesRun() -> noChanges.add(history)
                    history.status == RunStatus.IN_PROGRESS -> inProgress.add(history)
                    history.status == RunStatus.CANCELLED -> cancelled.add(history)
                    history.status == RunStatus.FAILED -> failed.add(history)
                    history.status == RunStatus.PARTIAL_FAILURE -> partial.add(history)
                    history.status == RunStatus.SUCCESS -> successWork.add(history)
                }
            }
            val result = mutableListOf<HistoryItem>()

            fun appendSection(
                section: HistoryStatusSection,
                entries: List<RunHistory>,
            ) {
                if (entries.isEmpty()) return
                result.add(HistoryItem.StatusHeader(section = section, count = entries.size))
                entries.forEach { result.add(HistoryItem.Entry(it)) }
            }
            appendSection(HistoryStatusSection.SUCCESS, successWork)
            appendSection(HistoryStatusSection.FAILED, failed)
            appendSection(HistoryStatusSection.PARTIAL, partial)
            appendSection(HistoryStatusSection.NO_CHANGES, noChanges)
            appendSection(HistoryStatusSection.IN_PROGRESS, inProgress)
            appendSection(HistoryStatusSection.CANCELLED, cancelled)
            appendSection(HistoryStatusSection.UNDONE, undone)
            return result
        }

        private fun formatDateLabel(timestampMs: Long): String {
            val zone = ZoneId.systemDefault()
            val day = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()
            val today = LocalDate.now(zone)
            return when (day) {
                today -> appContext.getString(R.string.history_date_today)
                today.minusDays(1) -> appContext.getString(R.string.history_date_yesterday)
                else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            }
        }

        private fun isSameDay(
            t1: Long,
            t2: Long,
        ): Boolean {
            val zone = ZoneId.systemDefault()
            return Instant.ofEpochMilli(t1).atZone(zone).toLocalDate() ==
                Instant.ofEpochMilli(t2).atZone(zone).toLocalDate()
        }
    }

private fun RunHistory.matchesHistoryStatusFilter(filter: HistoryStatusFilter): Boolean =
    when (filter) {
        HistoryStatusFilter.ALL -> {
            true
        }

        HistoryStatusFilter.SUCCESS -> {
            status == RunStatus.SUCCESS && !isNoChangesRun() && !isEffectivelyUndone()
        }

        HistoryStatusFilter.FAILED -> {
            status == RunStatus.FAILED
        }

        HistoryStatusFilter.PARTIAL -> {
            status == RunStatus.PARTIAL_FAILURE
        }

        HistoryStatusFilter.NO_CHANGES -> {
            isNoChangesRun()
        }

        HistoryStatusFilter.CANCELLED -> {
            status == RunStatus.CANCELLED
        }

        HistoryStatusFilter.UNDONE -> {
            isEffectivelyUndone()
        }
    }
