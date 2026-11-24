package dev.bikram.filepipe.shortcuts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PendingHistoryUndoRequest(
    val historyId: Long,
    val notificationId: Int,
)

@Singleton
class PendingShortcutRepository
    @Inject
    constructor() {
        private val _pendingPreviewRuleId = MutableStateFlow<Long?>(null)
        val pendingPreviewRuleId: StateFlow<Long?> = _pendingPreviewRuleId.asStateFlow()

        private val _pendingHistoryDetailId = MutableStateFlow<Long?>(null)
        val pendingHistoryDetailId: StateFlow<Long?> = _pendingHistoryDetailId.asStateFlow()

        private val _pendingHistoryUndoRequest = MutableStateFlow<PendingHistoryUndoRequest?>(null)
        val pendingHistoryUndoRequest: StateFlow<PendingHistoryUndoRequest?> =
            _pendingHistoryUndoRequest.asStateFlow()

        private val _pendingOpenHistory = MutableStateFlow(false)
        val pendingOpenHistory: StateFlow<Boolean> = _pendingOpenHistory.asStateFlow()

        private val _pendingOpenSettingsForUpdates = MutableStateFlow(false)
        val pendingOpenSettingsForUpdates: StateFlow<Boolean> = _pendingOpenSettingsForUpdates.asStateFlow()

        fun requestPreviewRule(ruleId: Long) {
            _pendingPreviewRuleId.value = ruleId
        }

        fun clearPendingPreviewRule() {
            _pendingPreviewRuleId.value = null
        }

        fun requestOpenHistoryDetail(
            historyId: Long,
            undoNotificationId: Int? = null,
        ) {
            _pendingHistoryDetailId.value = historyId
            if (undoNotificationId != null) {
                _pendingHistoryUndoRequest.value = PendingHistoryUndoRequest(historyId, undoNotificationId)
            }
        }

        fun clearPendingHistoryDetail() {
            _pendingHistoryDetailId.value = null
        }

        fun consumePendingHistoryUndo(request: PendingHistoryUndoRequest): Boolean {
            if (_pendingHistoryUndoRequest.compareAndSet(expect = request, update = null)) {
                return true
            }
            return false
        }

        fun requestOpenHistory() {
            _pendingOpenHistory.value = true
        }

        fun clearPendingOpenHistory() {
            _pendingOpenHistory.value = false
        }

        fun requestOpenSettingsForUpdates() {
            _pendingOpenSettingsForUpdates.value = true
        }

        fun clearPendingOpenSettingsForUpdates() {
            _pendingOpenSettingsForUpdates.value = false
        }

        companion object {
            const val EXTRA_OPEN_HISTORY = "extra_open_history"
            const val EXTRA_OPEN_HISTORY_DETAIL_ID = "extra_open_history_detail_id"
            const val EXTRA_UNDO_SUMMARY_NOTIFICATION_ID = "extra_undo_summary_notification_id"
            const val EXTRA_OPEN_SETTINGS_UPDATES = "extra_open_settings_updates"
        }
    }
