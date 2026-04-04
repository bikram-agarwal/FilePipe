package dev.bikram.filepipe.manualrun

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.bikram.filepipe.R
import dev.bikram.filepipe.worker.FileOrganizerWorker

/**
 * Lightweight foreground service shown only while the app is in the background during
 * an in-process manual run ([RulesViewModel]), so the system is less likely to kill the process.
 */
class ManualRunForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val title = getString(R.string.notification_running, getString(R.string.app_name))
        val notification = NotificationCompat.Builder(this, FileOrganizerWorker.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(getString(R.string.notification_preparing))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(FileOrganizerWorker.CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    FileOrganizerWorker.CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = getString(R.string.notification_channel_desc)
                }
            )
        }
    }

    override fun onDestroy() {
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 9001
    }
}
