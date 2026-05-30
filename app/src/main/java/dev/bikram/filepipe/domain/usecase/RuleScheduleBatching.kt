package dev.bikram.filepipe.domain.usecase

import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RuleSchedule
import dev.bikram.filepipe.domain.model.ScheduleType
import java.util.Calendar
import java.util.concurrent.TimeUnit

internal data class CoalescedRuleScheduleGroup(
    val schedule: RuleSchedule,
    val ruleIds: List<Long>,
)

internal fun coalescedRuleScheduleGroups(rules: List<Rule>): List<CoalescedRuleScheduleGroup> =
    rules
        .filter { rule -> rule.isEnabled && rule.schedule != null }
        .groupBy { rule -> scheduleKey(rule.schedule!!) }
        .values
        .map { group ->
            CoalescedRuleScheduleGroup(
                schedule = group.first().schedule!!,
                ruleIds = group.map { rule -> rule.id },
            )
        }

internal fun batchTagForRuleIds(ruleIds: LongArray): String = "batch_${ruleIds.sorted().joinToString("_")}"

internal fun scheduledRuleWorkTag(ruleId: Long): String = "rule_$ruleId"

internal fun scheduledBatchWorkTag(ruleIds: LongArray): String = batchTagForRuleIds(ruleIds)

internal fun scheduledRuleRunWorkName(ruleId: Long): String = "scheduled_rule_run_$ruleId"

internal fun scheduledBatchRunWorkName(ruleIds: LongArray): String = "scheduled_${batchTagForRuleIds(ruleIds)}"

internal fun scheduleKey(schedule: RuleSchedule): String = "${schedule.type}_${schedule.hour}_${schedule.minute}_${schedule.dayOfWeek}_${schedule.intervalHours}"

internal fun nextRunAtMillis(
    schedule: RuleSchedule,
    nowMillis: Long = System.currentTimeMillis(),
    allowImmediateIntervalRun: Boolean = true,
): Long {
    if (schedule.type == ScheduleType.EVERY_N_HOURS) {
        if (allowImmediateIntervalRun) {
            return nowMillis
        }
        val hours = schedule.intervalHours?.toLong()?.coerceIn(1L, 24L) ?: 1L
        return nowMillis + TimeUnit.HOURS.toMillis(hours)
    }

    val now =
        Calendar.getInstance().apply {
            timeInMillis = nowMillis
        }
    val target =
        Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, schedule.hour)
            set(Calendar.MINUTE, schedule.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (schedule.type == ScheduleType.WEEKLY && schedule.dayOfWeek != null) {
                set(Calendar.DAY_OF_WEEK, schedule.dayOfWeek)
            }
        }

    if (!target.after(now)) {
        if (schedule.type == ScheduleType.WEEKLY) {
            target.add(Calendar.WEEK_OF_YEAR, 1)
        } else {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    return target.timeInMillis
}
