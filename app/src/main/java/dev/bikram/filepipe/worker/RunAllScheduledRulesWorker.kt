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
import kotlin.math.min

/**
 * Coalesced worker that processes multiple rules in a single foreground service pass.
 * Use [ScheduleRulesUseCase.scheduleCoalesced] to schedule rules with the same schedule
 * configuration under one worker instead of spinning up separate workers per rule.
 */
@HiltWorker
class RunAllScheduledRulesWorker
    @AssistedInject
    constructor(
        @Assisted private val appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val executeRulesUseCase: ExecuteRulesUseCase,
        private val ruleRepository: RuleRepository,
    ) : CoroutineWorker(appContext, workerParams) {
        private val notificationManager: NotificationManager
            get() = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        private val progressNotifyLock = Any()

        /** Rules that have reported [RunProgress.isComplete]; used so one finished rule does not show Finishing while others run. */
        private val completedRuleIdsInBatch = mutableSetOf<Long>()

        override suspend fun getForegroundInfo(): ForegroundInfo {
            val ruleIds = inputData.getLongArray(KEY_RULE_IDS)
            val ruleCount = ruleIds?.size ?: 0
            return createForegroundInfo(ruleCount)
        }

        override suspend fun doWork(): Result {
            val ruleIds =
                inputData.getLongArray(KEY_RULE_IDS)
                    ?: run {
                        DiagnosticLog.record(appContext, "Scheduled batch worker failed: missing rule ids")
                        return Result.failure()
                    }
            val rules =
                buildList {
                    for (id in ruleIds) {
                        ruleRepository.getRuleById(id)?.let { if (it.isEnabled) add(it) }
                    }
                }
            if (rules.isEmpty()) return Result.success()

            try {
                setForeground(createForegroundInfo(rules.size))
            } catch (e: Exception) {
                DiagnosticLog.record(appContext, "Failed to setForeground for batch size=${rules.size}", e)
            }
            val firstRule = rules.first()
            synchronized(progressNotifyLock) {
                completedRuleIdsInBatch.clear()
                refreshBatchProgressNotification(
                    rules,
                    RunProgress(firstRule.id, firstRule.name, 0f, totalFiles = 0),
                )
            }

            return try {
                val results =
                    executeRulesUseCase(rules, TriggerType.SCHEDULED) { progress ->
                        synchronized(progressNotifyLock) {
                            refreshBatchProgressNotification(rules, progress)
                        }
                    }
                results.forEach { result ->
                    val rule = rules.find { it.id == result.ruleId } ?: return@forEach
                    val movedFileNames =
                        result.filesMoved
                            .filter { it.success && !it.skipped }
                            .map { it.fileName }
                    postSummaryNotification(
                        ruleName = rule.name,
                        moved = result.totalMoved,
                        failed = result.totalFailed,
                        operationMode = rule.operationMode,
                        historyId = result.historyId,
                        movedFileNames = movedFileNames,
                    )
                    if (result.totalFailed > 0) {
                        DiagnosticLog.record(
                            appContext,
                            "Scheduled batch rule completed with failures: ruleId=${rule.id}, moved=${result.totalMoved}, failed=${result.totalFailed}",
                        )
                    }
                }
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticLog.record(
                    appContext,
                    "Scheduled batch worker threw: ruleCount=${rules.size}, attempt=$runAttemptCount",
                    e,
                )
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
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
                    .Builder(appContext, FileOrganizerWorker.SUMMARY_CHANNEL_ID)
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

            val summaryNotificationId = (SUMMARY_NOTIFICATION_BASE_ID + historyId).toInt()
            if (moved > 0) {
                val undoIntent =
                    Intent(appContext, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_UNDO_RUN
                        putExtra(NotificationActionReceiver.EXTRA_HISTORY_ID, historyId)
                        putExtra(
                            NotificationActionReceiver.EXTRA_SUMMARY_NOTIFICATION_ID,
                            summaryNotificationId,
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

            notificationManager.notify(summaryNotificationId, builder.build())
        }

        private fun refreshBatchProgressNotification(
            rules: List<Rule>,
            progress: RunProgress,
        ) {
            if (rules.size > 1) {
                if (progress.isComplete) {
                    completedRuleIdsInBatch.add(progress.ruleId)
                }
                val allRulesInBatchFinished = completedRuleIdsInBatch.size >= rules.size
                if (!allRulesInBatchFinished && progress.isComplete) {
                    return
                }
                val builder =
                    NotificationCompat
                        .Builder(appContext, FileOrganizerWorker.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setContentTitle(
                            appContext.resources.getQuantityString(
                                R.plurals.notification_running_batch,
                                rules.size,
                                rules.size,
                            ),
                        )
                if (allRulesInBatchFinished) {
                    builder.setProgress(0, 0, false)
                    if (progress.error != null) {
                        builder.setOngoing(false)
                    }
                    builder.setContentText(appContext.notificationContentTextForTerminalProgress(progress))
                } else {
                    builder.setProgress(0, 0, true)
                    val fileLabel =
                        progress.currentFileName.ifBlank {
                            appContext.getString(R.string.notification_working)
                        }
                    builder.setContentText(
                        appContext.getString(R.string.notification_batch_rule_file, progress.ruleName, fileLabel),
                    )
                }
                notificationManager.notify(NOTIFICATION_ID, builder.build())
                return
            }

            val builder =
                NotificationCompat
                    .Builder(appContext, FileOrganizerWorker.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentTitle(
                        appContext.resources.getQuantityString(
                            R.plurals.notification_running_batch,
                            rules.size,
                            rules.size,
                        ),
                    )

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
                    val rule = rules.first()
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

            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }

        private fun createForegroundInfo(ruleCount: Int): ForegroundInfo {
            ensureProgressChannel()
            val notification =
                NotificationCompat
                    .Builder(appContext, FileOrganizerWorker.CHANNEL_ID)
                    .setContentTitle(
                        appContext.resources.getQuantityString(
                            R.plurals.notification_running_batch,
                            ruleCount,
                            ruleCount,
                        ),
                    ).setContentText(appContext.getString(R.string.notification_preparing))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setOngoing(true)
                    .setProgress(0, 0, true)
                    .build()
            return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }

        private fun ensureProgressChannel() {
            if (notificationManager.getNotificationChannel(FileOrganizerWorker.CHANNEL_ID) == null) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        FileOrganizerWorker.CHANNEL_ID,
                        appContext.getString(R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = appContext.getString(R.string.notification_channel_desc)
                    },
                )
            }
        }

        private fun ensureSummaryChannel() {
            if (notificationManager.getNotificationChannel(FileOrganizerWorker.SUMMARY_CHANNEL_ID) == null) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        FileOrganizerWorker.SUMMARY_CHANNEL_ID,
                        appContext.getString(R.string.notification_summary_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = appContext.getString(R.string.notification_summary_channel_desc)
                    },
                )
            }
        }

        companion object {
            const val KEY_RULE_IDS = "rule_ids"
            const val NOTIFICATION_ID = 1003
            const val SUMMARY_NOTIFICATION_BASE_ID = 2000
        }
    }
