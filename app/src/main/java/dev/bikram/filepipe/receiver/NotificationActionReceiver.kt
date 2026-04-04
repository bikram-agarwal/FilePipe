package dev.bikram.filepipe.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.domain.usecase.UndoRunUseCase
import dev.bikram.filepipe.ui.feedback.toUserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var undoRunUseCase: UndoRunUseCase

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UNDO_RUN) return

        val historyId = intent.getLongExtra(EXTRA_HISTORY_ID, -1L)
        if (historyId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = undoRunUseCase(historyId)
                val message = result.toUserMessage(context)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            } finally {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val summaryNotificationId = intent.getIntExtra(EXTRA_SUMMARY_NOTIFICATION_ID, -1)
                if (summaryNotificationId != -1) {
                    manager.cancel(summaryNotificationId)
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        val ACTION_UNDO_RUN: String = "${BuildConfig.APPLICATION_ID}.ACTION_UNDO_RUN"
        const val EXTRA_HISTORY_ID = "extra_history_id"
        const val EXTRA_SUMMARY_NOTIFICATION_ID = "extra_summary_notification_id"
    }
}
