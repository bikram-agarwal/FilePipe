package dev.bikram.filepipe.domain.usecase

import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RuleSchedule
import dev.bikram.filepipe.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class RuleScheduleBatchingTest {
    @Test
    fun coalescedGroupsIncludeOnlyEnabledScheduledRulesWithIdenticalSchedules() {
        val dailyNine = RuleSchedule(ScheduleType.DAILY, hour = 9, minute = 0)
        val everyTwoHours = RuleSchedule(ScheduleType.EVERY_N_HOURS, hour = 0, minute = 0, intervalHours = 2)
        val groups =
            coalescedRuleScheduleGroups(
                listOf(
                    rule(id = 3L, schedule = dailyNine),
                    rule(id = 1L, schedule = dailyNine),
                    rule(id = 9L, schedule = dailyNine, isEnabled = false),
                    rule(id = 10L, schedule = null),
                    rule(id = 5L, schedule = everyTwoHours),
                ),
            )

        assertEquals(2, groups.size)
        assertEquals(dailyNine, groups[0].schedule)
        assertEquals(listOf(3L, 1L), groups[0].ruleIds)
        assertEquals(everyTwoHours, groups[1].schedule)
        assertEquals(listOf(5L), groups[1].ruleIds)
    }

    @Test
    fun batchTagSortsRuleIdsForStableUniqueWorkNames() {
        assertEquals("batch_1_3_9", batchTagForRuleIds(longArrayOf(9L, 1L, 3L)))
    }

    @Test
    fun nextDailyRunUsesTodayWhenTimeIsStillAhead() {
        val now = millisFor(day = 10, hour = 8, minute = 30)
        val next =
            nextRunAtMillis(
                RuleSchedule(ScheduleType.DAILY, hour = 9, minute = 15),
                nowMillis = now,
            )

        assertEquals(millisFor(day = 10, hour = 9, minute = 15), next)
    }

    @Test
    fun nextDailyRunMovesToTomorrowWhenTimeAlreadyPassed() {
        val now = millisFor(day = 10, hour = 10, minute = 0)
        val next =
            nextRunAtMillis(
                RuleSchedule(ScheduleType.DAILY, hour = 9, minute = 15),
                nowMillis = now,
            )

        assertEquals(millisFor(day = 11, hour = 9, minute = 15), next)
    }

    @Test
    fun nextIntervalRunCanBeImmediateForNewSchedulesAndDelayedForRecurringAlarms() {
        val now = millisFor(day = 10, hour = 8, minute = 30)
        val schedule = RuleSchedule(ScheduleType.EVERY_N_HOURS, hour = 0, minute = 0, intervalHours = 3)

        assertEquals(now, nextRunAtMillis(schedule, nowMillis = now, allowImmediateIntervalRun = true))
        assertEquals(
            now + TimeUnit.HOURS.toMillis(3),
            nextRunAtMillis(schedule, nowMillis = now, allowImmediateIntervalRun = false),
        )
    }

    private fun rule(
        id: Long,
        schedule: RuleSchedule?,
        isEnabled: Boolean = true,
    ): Rule =
        Rule(
            id = id,
            name = "Rule $id",
            sourceFolderPaths = listOf("content://source/$id"),
            destinationFolderPath = "content://destination",
            fileExtensions = listOf("jpg"),
            isEnabled = isEnabled,
            schedule = schedule,
        )

    private fun millisFor(
        day: Int,
        hour: Int,
        minute: Int,
    ): Long =
        Calendar
            .getInstance()
            .apply {
                clear()
                set(2026, Calendar.JANUARY, day, hour, minute, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
}
