package dev.bikram.filepipe.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RunProgress
import dev.bikram.filepipe.domain.model.TriggerType
import dev.bikram.filepipe.domain.usecase.ExecuteRulesUseCase
import dev.bikram.filepipe.receiver.NotificationActionReceiver
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.min

@HiltWorker
class FileOrganizerWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val executeRulesUseCase: ExecuteRulesUseCase,
    private val ruleRepository: RuleRepository
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager: NotificationManager
        get() = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Distinct from other concurrent [FileOrganizerWorker] instances (shared 1001/1002 caused clashes). */
    private val progressNotificationId: Int
        get() = NOTIFICATION_ID_SLOT_BASE + (abs(id.hashCode()) % NOTIFICATION_ID_SLOT_COUNT) * 2

    private val summaryNotificationIdForWork: Int
        get() = progressNotificationId + 1

    override suspend fun doWork(): Result {
        val ruleId = inputData.getLong(KEY_RULE_ID, -1L)
        if (ruleId == -1L) return Result.failure()

        val rule = ruleRepository.getRuleById(ruleId) ?: return Result.failure()

        setForeground(createForegroundInfo(rule.name))
        refreshProgressNotification(rule, RunProgress(rule.id, rule.name, 0f, totalFiles = 0))

        return try {
            val results = executeRulesUseCase(listOf(rule), TriggerType.SCHEDULED) { progress ->
                refreshProgressNotification(rule, progress)
            }
            val result = results.firstOrNull()
            if (result != null) {
                val movedFileNames = result.filesMoved
                    .filter { it.success && !it.skipped }
                    .map { it.fileName }
                postSummaryNotification(rule.name, result.totalMoved, result.totalFailed, rule.operationMode, result.historyId, movedFileNames)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun postSummaryNotification(
        ruleName: String,
        moved: Int,
        failed: Int,
        operationMode: OperationMode,
        historyId: Long,
        movedFileNames: List<String> = emptyList()
    ) {
        ensureSummaryChannel()
        val body = when {
            failed > 0 && operationMode == OperationMode.COPY ->
                appContext.getString(R.string.notification_summary_body_partial_copied, moved, failed)
            failed > 0 ->
                appContext.getString(R.string.notification_summary_body_partial, moved, failed)
            operationMode == OperationMode.COPY ->
                appContext.getString(R.string.notification_summary_body_copied, moved)
            else -> appContext.getString(R.string.notification_summary_body_moved, moved)
        }
        val builder = NotificationCompat.Builder(appContext, SUMMARY_CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.notification_summary_title, ruleName))
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)

        if (movedFileNames.isNotEmpty()) {
            val style = NotificationCompat.InboxStyle()
                .setBigContentTitle(appContext.getString(R.string.notification_summary_title, ruleName))
                .setSummaryText(body)
            movedFileNames.take(3).forEach { style.addLine(it) }
            if (movedFileNames.size > 3) {
                style.addLine("+ ${movedFileNames.size - 3} more")
            }
            builder.setStyle(style)
        }

        builder.setContentIntent(openRunHistoryDetailPendingIntent(appContext, historyId))

        if (moved > 0) {
            val undoIntent = Intent(appContext, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_UNDO_RUN
                putExtra(NotificationActionReceiver.EXTRA_HISTORY_ID, historyId)
                putExtra(
                    NotificationActionReceiver.EXTRA_SUMMARY_NOTIFICATION_ID,
                    summaryNotificationIdForWork
                )
            }
            val undoPendingIntent = PendingIntent.getBroadcast(
                appContext,
                historyId.toInt(),
                undoIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, appContext.getString(R.string.notification_action_undo), undoPendingIntent)
        }

        notificationManager.notify(summaryNotificationIdForWork, builder.build())
    }

    private fun refreshProgressNotification(rule: Rule, progress: RunProgress) {
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(appContext.getString(R.string.notification_running, rule.name))

        when {
            progress.isComplete -> {
                builder.setProgress(0, 0, false)
                if (progress.error != null) {
                    builder.setOngoing(false)
                }
                builder.setContentText(appContext.notificationContentTextForTerminalProgress(progress))
            }
            progress.totalFiles <= 0 -> {
                builder.setProgress(0, 0, true)
                builder.setContentText(appContext.getString(R.string.notification_preparing))
            }
            else -> {
                val max = progress.totalFiles
                val done = min((progress.progress * max).toInt(), max)
                builder.setProgress(max, done, false)
                builder.setContentText(
                    progress.currentFileName.ifBlank { appContext.getString(R.string.notification_working) }
                )
                val subText = when (rule.operationMode) {
                    OperationMode.COPY ->
                        appContext.getString(R.string.notification_copying_n_of_m, done, max)
                    OperationMode.MOVE ->
                        appContext.getString(R.string.notification_moving_n_of_m, done, max)
                }
                builder.setSubText(subText)
            }
        }

        notificationManager.notify(progressNotificationId, builder.build())
    }

    private fun createForegroundInfo(ruleName: String): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.notification_running, ruleName))
            .setContentText(appContext.getString(R.string.notification_preparing))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        return ForegroundInfo(
            progressNotificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun createNotificationChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    appContext.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = appContext.getString(R.string.notification_channel_desc)
                }
            )
        }
    }

    private fun ensureSummaryChannel() {
        if (notificationManager.getNotificationChannel(SUMMARY_CHANNEL_ID) == null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    SUMMARY_CHANNEL_ID,
                    appContext.getString(R.string.notification_summary_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = appContext.getString(R.string.notification_summary_channel_desc)
                }
            )
        }
    }

    companion object {
        const val KEY_RULE_ID = "rule_id"
        /** New id so upgrades get IMPORTANCE_DEFAULT without orphaned low-importance channel. */
        const val CHANNEL_ID = "rule_execution_v2"
        const val SUMMARY_CHANNEL_ID = "run_summary_channel"
        private const val NOTIFICATION_ID_SLOT_BASE = 30_000
        private const val NOTIFICATION_ID_SLOT_COUNT = 12_000
    }
}
