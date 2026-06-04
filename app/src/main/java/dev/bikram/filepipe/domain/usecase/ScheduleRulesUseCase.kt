package dev.bikram.filepipe.domain.usecase

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RuleSchedule
import dev.bikram.filepipe.receiver.ScheduledRuleAlarmReceiver
import javax.inject.Inject

class ScheduleRulesUseCase
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val workManager: WorkManager,
    ) {
        private val alarmManager: AlarmManager
            get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        fun scheduleRule(rule: Rule) {
            val schedule =
                rule.schedule ?: run {
                    cancelRule(rule)
                    return
                }
            if (!rule.isEnabled) {
                cancelRule(rule)
                return
            }

            cancelRule(rule)
            scheduleRuleAlarm(rule, allowImmediateIntervalRun = true)
        }

        fun cancelRule(rule: Rule) {
            cancelRuleAlarm(rule.id)
            workManager.cancelAllWorkByTag(scheduledRuleWorkTag(rule.id))
        }

        fun cancelRuleById(ruleId: Long) {
            cancelRuleAlarm(ruleId)
            workManager.cancelAllWorkByTag(scheduledRuleWorkTag(ruleId))
        }

        /**
         * Batch-schedules a list of enabled rules by grouping rules that share an identical schedule
         * configuration into a single alarm-triggered worker pass.
         *
         * Rules with unique schedules still get their own worker, but rules that would otherwise
         * fire at the same time are coalesced into one foreground service pass - reducing wake-lock
         * overhead and notification noise when many rules share the same schedule.
         *
         * Existing per-rule scheduled work for all provided rules is cancelled before batch alarms
         * are scheduled.
         */
        fun scheduleCoalesced(rules: List<Rule>) {
            val enabled = rules.filter { it.isEnabled && it.schedule != null }
            enabled.forEach { cancelRule(it) }

            coalescedRuleScheduleGroups(rules)
                .forEach { group ->
                    val ruleIds = group.ruleIds.toLongArray()
                    cancelBatchAlarm(ruleIds)
                    workManager.cancelAllWorkByTag(scheduledBatchWorkTag(ruleIds))
                    scheduleBatchAlarm(group.schedule, ruleIds, allowImmediateIntervalRun = true)
                }
        }

        internal fun scheduleNextRuleAlarm(rule: Rule) {
            if (rule.isEnabled && rule.schedule != null) {
                scheduleRuleAlarm(rule, allowImmediateIntervalRun = false)
            }
        }

        internal fun scheduleNextCoalescedAlarm(rules: List<Rule>) {
            coalescedRuleScheduleGroups(rules)
                .forEach { group ->
                    scheduleBatchAlarm(
                        schedule = group.schedule,
                        ruleIds = group.ruleIds.toLongArray(),
                        allowImmediateIntervalRun = false,
                    )
                }
        }

        private fun scheduleRuleAlarm(
            rule: Rule,
            allowImmediateIntervalRun: Boolean,
        ) {
            val schedule = rule.schedule ?: return
            val triggerAtMillis = nextRunAtMillis(schedule, allowImmediateIntervalRun = allowImmediateIntervalRun)
            val intent =
                Intent(context, ScheduledRuleAlarmReceiver::class.java).apply {
                    action = ScheduledRuleAlarmReceiver.ACTION_RUN_RULE
                    putExtra(ScheduledRuleAlarmReceiver.EXTRA_RULE_ID, rule.id)
                    putExtra(ScheduledRuleAlarmReceiver.EXTRA_EXPECTED_SCHEDULE_KEY, scheduleKey(schedule))
                    putExtra(ScheduledRuleAlarmReceiver.EXTRA_TRIGGER_AT_MILLIS, triggerAtMillis)
                }
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    alarmRequestCodeForRule(rule.id),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            scheduleAlarm(
                triggerAtMillis = triggerAtMillis,
                pendingIntent = pendingIntent,
            )
        }

        private fun scheduleBatchAlarm(
            schedule: RuleSchedule,
            ruleIds: LongArray,
            allowImmediateIntervalRun: Boolean,
        ) {
            val triggerAtMillis = nextRunAtMillis(schedule, allowImmediateIntervalRun = allowImmediateIntervalRun)
            val intent =
                Intent(context, ScheduledRuleAlarmReceiver::class.java).apply {
                    action = ScheduledRuleAlarmReceiver.ACTION_RUN_BATCH
                    putExtra(ScheduledRuleAlarmReceiver.EXTRA_RULE_IDS, ruleIds)
                    putExtra(ScheduledRuleAlarmReceiver.EXTRA_EXPECTED_SCHEDULE_KEY, scheduleKey(schedule))
                    putExtra(ScheduledRuleAlarmReceiver.EXTRA_TRIGGER_AT_MILLIS, triggerAtMillis)
                }
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    alarmRequestCodeForBatch(ruleIds),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            scheduleAlarm(
                triggerAtMillis = triggerAtMillis,
                pendingIntent = pendingIntent,
            )
        }

        private fun scheduleAlarm(
            triggerAtMillis: Long,
            pendingIntent: PendingIntent,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }

        private fun cancelRuleAlarm(ruleId: Long) {
            val intent =
                Intent(context, ScheduledRuleAlarmReceiver::class.java).apply {
                    action = ScheduledRuleAlarmReceiver.ACTION_RUN_RULE
                }
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    alarmRequestCodeForRule(ruleId),
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }

        private fun cancelBatchAlarm(ruleIds: LongArray) {
            val intent =
                Intent(context, ScheduledRuleAlarmReceiver::class.java).apply {
                    action = ScheduledRuleAlarmReceiver.ACTION_RUN_BATCH
                }
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    alarmRequestCodeForBatch(ruleIds),
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }

        private fun alarmRequestCodeForRule(ruleId: Long): Int = ruleId.hashCode()

        private fun alarmRequestCodeForBatch(ruleIds: LongArray): Int = batchTagForRuleIds(ruleIds).hashCode()
    }
