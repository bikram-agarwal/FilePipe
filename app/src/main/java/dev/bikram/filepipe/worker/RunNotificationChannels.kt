package dev.bikram.filepipe.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.bikram.filepipe.R

/**
 * Single definition + creation point for the rule-run notification channels (progress + summary).
 * The same two channels were previously created with identical, copy-pasted definitions in
 * FileOrganizerWorker, RunAllScheduledRulesWorker, ManualRunForegroundService, and
 * DevOptionsViewModel. Idempotent — safe to call before every notification.
 */
object RunNotificationChannels {
    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(FileOrganizerWorker.CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    FileOrganizerWorker.CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                },
            )
        }
        if (manager.getNotificationChannel(FileOrganizerWorker.SUMMARY_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    FileOrganizerWorker.SUMMARY_CHANNEL_ID,
                    context.getString(R.string.notification_summary_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.notification_summary_channel_desc)
                },
            )
        }
    }
}
