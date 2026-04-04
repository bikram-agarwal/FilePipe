package dev.bikram.filepipe.shortcuts

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingShortcutRepository @Inject constructor() {
    private val _pendingRuleId = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val pendingRuleId: SharedFlow<Long> = _pendingRuleId.asSharedFlow()

    private val _pendingHistoryDetailId = MutableStateFlow<Long?>(null)
    val pendingHistoryDetailId: StateFlow<Long?> = _pendingHistoryDetailId.asStateFlow()

    fun requestRunRule(ruleId: Long) {
        _pendingRuleId.tryEmit(ruleId)
    }

    fun requestOpenHistoryDetail(historyId: Long) {
        _pendingHistoryDetailId.value = historyId
    }

    fun clearPendingHistoryDetail() {
        _pendingHistoryDetailId.value = null
    }

    companion object {
        const val EXTRA_OPEN_HISTORY_DETAIL_ID = "extra_open_history_detail_id"
    }
}
