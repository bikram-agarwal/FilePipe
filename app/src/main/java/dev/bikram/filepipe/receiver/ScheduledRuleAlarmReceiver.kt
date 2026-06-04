package dev.bikram.filepipe.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.usecase.ScheduleRulesUseCase
import dev.bikram.filepipe.domain.usecase.scheduleKey
import dev.bikram.filepipe.domain.usecase.scheduledBatchRunWorkName
import dev.bikram.filepipe.domain.usecase.scheduledBatchWorkTag
import dev.bikram.filepipe.domain.usecase.scheduledRuleRunWorkName
import dev.bikram.filepipe.domain.usecase.scheduledRuleWorkTag
import dev.bikram.filepipe.worker.FileOrganizerWorker
import dev.bikram.filepipe.worker.RunAllScheduledRulesWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScheduledRuleAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var workManager: WorkManager

    @Inject lateinit var ruleRepository: RuleRepository

    @Inject lateinit var scheduleRulesUseCase: ScheduleRulesUseCase

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (action != ACTION_RUN_RULE && action != ACTION_RUN_BATCH) {
            return
        }

        DiagnosticLog.record(context, "ScheduledRuleAlarmReceiver: received action=$action")

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_RUN_RULE -> handleRuleAlarm(context, intent)
                    ACTION_RUN_BATCH -> handleBatchAlarm(context, intent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleRuleAlarm(context: Context, intent: Intent) {
        val ruleId = intent.getLongExtra(EXTRA_RULE_ID, MISSING_RULE_ID)
        if (ruleId == MISSING_RULE_ID) {
            DiagnosticLog.record(context, "handleRuleAlarm: missing rule id")
            return
        }

        val expectedScheduleKey = intent.getStringExtra(EXTRA_EXPECTED_SCHEDULE_KEY)
        if (expectedScheduleKey == null) {
            DiagnosticLog.record(context, "handleRuleAlarm: missing expected schedule key for rule=$ruleId")
            return
        }

        val rule = ruleRepository.getRuleById(ruleId)
        if (rule == null) {
            DiagnosticLog.record(context, "handleRuleAlarm: rule=$ruleId not found in repository")
            return
        }

        val schedule = rule.schedule
        if (schedule == null) {
            DiagnosticLog.record(context, "handleRuleAlarm: rule=${rule.name} has no schedule")
            return
        }

        val currentKey = scheduleKey(schedule)
        if (!rule.isEnabled || currentKey != expectedScheduleKey) {
            DiagnosticLog.record(
                context,
                "handleRuleAlarm skipped: rule=${rule.name}, enabled=${rule.isEnabled}, currentKey=$currentKey, expectedKey=$expectedScheduleKey"
            )
            return
        }

        DiagnosticLog.record(context, "handleRuleAlarm: triggering rule=${rule.name}")
        scheduleRulesUseCase.scheduleNextRuleAlarm(rule)
        enqueueRuleWork(rule.id)
    }

    private suspend fun handleBatchAlarm(context: Context, intent: Intent) {
        val expectedRuleIds = intent.getLongArrayExtra(EXTRA_RULE_IDS)
        if (expectedRuleIds == null || expectedRuleIds.isEmpty()) {
            DiagnosticLog.record(context, "handleBatchAlarm: missing expected rule ids")
            return
        }

        val expectedScheduleKey = intent.getStringExtra(EXTRA_EXPECTED_SCHEDULE_KEY)
        if (expectedScheduleKey == null) {
            DiagnosticLog.record(context, "handleBatchAlarm: missing expected schedule key")
            return
        }

        val currentRulesForSchedule =
            ruleRepository
                .getEnabledRules()
                .filter { rule ->
                    val schedule = rule.schedule
                    schedule != null && scheduleKey(schedule) == expectedScheduleKey
                }

        if (!sameRuleIds(currentRulesForSchedule, expectedRuleIds)) {
            DiagnosticLog.record(
                context,
                "handleBatchAlarm: rule list changed. expected=${expectedRuleIds.toList()}, current=${currentRulesForSchedule.map { it.id }}"
            )
            return
        }

        DiagnosticLog.record(context, "handleBatchAlarm: triggering batch of ${currentRulesForSchedule.size} rules")
        scheduleRulesUseCase.scheduleNextCoalescedAlarm(currentRulesForSchedule)
        enqueueBatchWork(currentRulesForSchedule.map { rule -> rule.id }.toLongArray())
    }

    private fun sameRuleIds(
        rules: List<Rule>,
        expectedRuleIds: LongArray,
    ): Boolean = rules.map { rule -> rule.id }.sorted() == expectedRuleIds.toList().sorted()

    private fun enqueueRuleWork(ruleId: Long) {
        val request =
            OneTimeWorkRequestBuilder<FileOrganizerWorker>()
                .setInputData(workDataOf(FileOrganizerWorker.KEY_RULE_ID to ruleId))
                .addTag(scheduledRuleWorkTag(ruleId))
                .build()

        workManager.enqueueUniqueWork(
            scheduledRuleRunWorkName(ruleId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun enqueueBatchWork(ruleIds: LongArray) {
        val request =
            OneTimeWorkRequestBuilder<RunAllScheduledRulesWorker>()
                .setInputData(workDataOf(RunAllScheduledRulesWorker.KEY_RULE_IDS to ruleIds))
                .addTag(scheduledBatchWorkTag(ruleIds))
                .build()

        workManager.enqueueUniqueWork(
            scheduledBatchRunWorkName(ruleIds),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val ACTION_RUN_RULE = "dev.bikram.filepipe.action.RUN_SCHEDULED_RULE"
        const val ACTION_RUN_BATCH = "dev.bikram.filepipe.action.RUN_SCHEDULED_RULE_BATCH"
        const val EXTRA_RULE_ID = "extra_rule_id"
        const val EXTRA_RULE_IDS = "extra_rule_ids"
        const val EXTRA_EXPECTED_SCHEDULE_KEY = "extra_expected_schedule_key"

        private const val MISSING_RULE_ID = -1L
    }
}
