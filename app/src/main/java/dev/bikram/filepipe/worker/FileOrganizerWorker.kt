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
import dev.bikram.filepipe.diagnostics.DiagnosticLog
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
class FileOrganizerWorker
    @AssistedInject
    constructor(
        @Assisted private val appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val executeRulesUseCase: ExecuteRulesUseCase,
        private val ruleRepository: RuleRepository,
    ) : CoroutineWorker(appContext, workerParams) {
        private val notificationManager: NotificationManager by lazy {
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }

        /** Distinct from other concurrent [FileOrganizerWorker] instances (shared 1001/1002 caused clashes). */
        private val progressNotificationId: Int by lazy {
            NOTIFICATION_ID_SLOT_BASE + (abs(id.hashCode()) % NOTIFICATION_ID_SLOT_COUNT) * 2
        }

        private val summaryNotificationIdForWork: Int by lazy { progressNotificationId + 1 }

        override suspend fun getForegroundInfo(): ForegroundInfo {
            val ruleId = inputData.getLong(KEY_RULE_ID, -1L)
            val ruleName =
                if (ruleId != -1L) {
                    ruleRepository.getRuleById(ruleId)?.name
                } else {
                    null
                } ?: appContext.getString(R.string.app_name)
            return createForegroundInfo(ruleName)
        }

        override suspend fun doWork(): Result {
            val ruleId = inputData.getLong(KEY_RULE_ID, -1L)
            val scheduledTriggerAtMillis = inputData.getLong(KEY_SCHEDULED_TRIGGER_AT_MILLIS, UNKNOWN_SCHEDULED_TIME_MILLIS)
            val alarmReceivedAtMillis = inputData.getLong(KEY_ALARM_RECEIVED_AT_MILLIS, UNKNOWN_SCHEDULED_TIME_MILLIS)
            val workerStartedAtMillis = System.currentTimeMillis()
            if (ruleId == -1L) {
                DiagnosticLog.record(appContext, "Scheduled rule worker failed: missing rule id")
                return Result.failure()
            }

            DiagnosticLog.record(
                appContext,
                "Scheduled rule worker starting: ruleId=$ruleId, triggerAt=$scheduledTriggerAtMillis, " +
                    "alarmReceivedAt=$alarmReceivedAtMillis, startedAt=$workerStartedAtMillis, " +
                    "alarmToWorkerDelayMs=${elapsedMillis(alarmReceivedAtMillis, workerStartedAtMillis)}, " +
                    "triggerToWorkerDelayMs=${elapsedMillis(scheduledTriggerAtMillis, workerStartedAtMillis)}",
            )

            val rule =
                ruleRepository.getRuleById(ruleId)
                    ?: run {
                        DiagnosticLog.record(appContext, "Scheduled rule worker failed: rule $ruleId not found")
                        return Result.failure()
                    }

            try {
                setForeground(createForegroundInfo(rule.name))
            } catch (e: Exception) {
                DiagnosticLog.record(appContext, "Failed to setForeground for rule=${rule.name}", e)
            }
            refreshProgressNotification(rule, RunProgress(rule.id, rule.name, 0f, totalFiles = 0))

            return try {
                val results =
                    executeRulesUseCase(listOf(rule), TriggerType.SCHEDULED) { progress ->
                        refreshProgressNotification(rule, progress)
                    }
                val result = results.firstOrNull()
                if (result != null) {
                    val movedFileNames =
                        result.filesMoved
                            .filter { it.success && !it.skipped }
                            .map { it.fileName }
                    if (result.totalFailed > 0) {
                        DiagnosticLog.record(
                            appContext,
                            "Scheduled rule completed with failures: ruleId=${rule.id}, moved=${result.totalMoved}, failed=${result.totalFailed}",
                        )
                    }
                    postSummaryNotification(rule.name, result.totalMoved, result.totalFailed, rule.operationMode, result.historyId, movedFileNames)
                    clearProgressNotification()
                }
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticLog.record(
                    appContext,
                    "Scheduled rule worker threw: ruleId=${rule.id}, attempt=$runAttemptCount",
                    e,
                )
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
        }

        private fun elapsedMillis(
            startMillis: Long,
            endMillis: Long,
        ): Long {
            if (startMillis == UNKNOWN_SCHEDULED_TIME_MILLIS) {
                return UNKNOWN_SCHEDULED_TIME_MILLIS
            }
            return endMillis - startMillis
        }

        private fun postSummaryNotification(
            ruleName: String,
            moved: Int,
            failed: Int,
            operationMode: OperationMode,
            historyId: Long,
            movedFileNames: List<String> = emptyList(),
        ) {
            ensureSummaryChannel()
            val body =
                when {
                    failed > 0 && operationMode == OperationMode.COPY -> {
                        appContext.resources.getQuantityString(
                            R.plurals.notification_summary_body_partial_copied,
                            moved,
                            moved,
                            failed,
                        )
                    }

                    failed > 0 -> {
                        appContext.resources.getQuantityString(
                            R.plurals.notification_summary_body_partial,
                            moved,
                            moved,
                            failed,
                        )
                    }

                    operationMode == OperationMode.COPY -> {
                        appContext.resources.getQuantityString(
                            R.plurals.history_files_copied,
                            moved,
                            moved,
                        )
                    }

                    else -> {
                        appContext.resources.getQuantityString(
                            R.plurals.history_files_moved,
                            moved,
                            moved,
                        )
                    }
                }
            val builder =
                NotificationCompat
                    .Builder(appContext, SUMMARY_CHANNEL_ID)
                    .setContentTitle(appContext.getString(R.string.notification_summary_title, ruleName))
                    .setContentText(body)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setAutoCancel(true)

            if (movedFileNames.isNotEmpty()) {
                val style =
                    NotificationCompat
                        .InboxStyle()
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
                val undoIntent =
                    Intent(appContext, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_UNDO_RUN
                        putExtra(NotificationActionReceiver.EXTRA_HISTORY_ID, historyId)
                        putExtra(
                            NotificationActionReceiver.EXTRA_SUMMARY_NOTIFICATION_ID,
                            summaryNotificationIdForWork,
                        )
                    }
                val undoPendingIntent =
                    PendingIntent.getBroadcast(
                        appContext,
                        historyId.toInt(),
                        undoIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                builder.addAction(0, appContext.getString(R.string.notification_action_undo), undoPendingIntent)
            }

            notificationManager.notify(summaryNotificationIdForWork, builder.build())
        }

        private fun refreshProgressNotification(
            rule: Rule,
            progress: RunProgress,
        ) {
            val builder =
                NotificationCompat
                    .Builder(appContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentTitle(appContext.getString(R.string.notification_running, rule.name))

            when {
                progress.isComplete -> {
                    builder.setProgress(0, 0, false)
                    builder.setOngoing(false)
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
                        progress.currentFileName.ifBlank { appContext.getString(R.string.notification_working) },
                    )
                    val subText =
                        when (rule.operationMode) {
                            OperationMode.COPY -> {
                                appContext.getString(R.string.notification_copying_n_of_m, done, max)
                            }

                            OperationMode.MOVE -> {
                                appContext.getString(R.string.notification_moving_n_of_m, done, max)
                            }
                        }
                    builder.setSubText(subText)
                }
            }

            notificationManager.notify(progressNotificationId, builder.build())
        }

        private fun clearProgressNotification() {
            notificationManager.cancel(progressNotificationId)
        }

        private fun createForegroundInfo(ruleName: String): ForegroundInfo {
            createNotificationChannel()
            val notification =
                NotificationCompat
                    .Builder(appContext, CHANNEL_ID)
                    .setContentTitle(appContext.getString(R.string.notification_running, ruleName))
                    .setContentText(appContext.getString(R.string.notification_preparing))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setOngoing(true)
                    .setProgress(0, 0, true)
                    .build()
            return ForegroundInfo(
                progressNotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        }

        private fun createNotificationChannel() {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        appContext.getString(R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = appContext.getString(R.string.notification_channel_desc)
                    },
                )
            }
        }

        private fun ensureSummaryChannel() {
            if (notificationManager.getNotificationChannel(SUMMARY_CHANNEL_ID) == null) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        SUMMARY_CHANNEL_ID,
                        appContext.getString(R.string.notification_summary_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = appContext.getString(R.string.notification_summary_channel_desc)
                    },
                )
            }
        }

        companion object {
            const val KEY_RULE_ID = "rule_id"
            const val KEY_SCHEDULED_TRIGGER_AT_MILLIS = "scheduled_trigger_at_millis"
            const val KEY_ALARM_RECEIVED_AT_MILLIS = "alarm_received_at_millis"

            /** New id so upgrades get IMPORTANCE_DEFAULT without orphaned low-importance channel. */
            const val CHANNEL_ID = "rule_execution_v2"
            const val SUMMARY_CHANNEL_ID = "run_summary_channel"

            // Each worker instance occupies 2 consecutive IDs (progress + summary).
            // Base 30_000 avoids collisions with the fixed IDs used elsewhere in the app (< 1_000).
            // 12_000 slots × 2 IDs = 24_000 IDs max, well within Android's Int range.
            private const val NOTIFICATION_ID_SLOT_BASE = 30_000
            private const val NOTIFICATION_ID_SLOT_COUNT = 12_000
            private const val UNKNOWN_SCHEDULED_TIME_MILLIS = -1L
        }
    }
