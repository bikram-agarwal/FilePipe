package dev.bikram.filepipe.domain.model

import android.content.Context
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.formatTimeOfDay
import java.util.Calendar

enum class ConflictPolicy { SKIP, OVERWRITE, RENAME_SUFFIX }

enum class OperationMode { MOVE, COPY }

data class Rule(
    val id: Long = 0,
    val name: String,
    val sourceFolderPaths: List<String>,
    val destinationFolderPath: String,
    val fileExtensions: List<String>,
    val isEnabled: Boolean = true,
    /** Display order when sorting by [HistorySortKey.MY_ORDER]; lower first. */
    val sortOrder: Int = 0,
    /** Overrides the global expanded/collapsed display mode for this rule card. */
    val cardModeOverride: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val trashedAt: Long? = null,
    val schedule: RuleSchedule? = null,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.RENAME_SUFFIX,
    val operationMode: OperationMode = OperationMode.MOVE,
    val scanSubdirectories: Boolean = false,
    val recreateDestinationSubfolders: Boolean = false,
    /**
     * When true, missing/unreadable **source** trees do not show the stale-folder banner on the rule
     * card; rule edit still shows full folder diagnostics. Destination and permission-denied issues
     * are never suppressed on the card.
     */
    val suppressMissingSourceFolderCardWarning: Boolean = false,
    val icon: RuleIcon = RuleIcon.DEFAULT,
    /** When non-blank, shown instead of [icon] in UI (system emoji font). */
    val iconEmoji: String? = null,
    // Advanced filters
    val filenamePattern: String? = null,
    val minFileSizeBytes: Long? = null,
    val maxFileSizeBytes: Long? = null,
    val minAgeDays: Int? = null,
    val maxAgeDays: Int? = null,
    val excludePatterns: List<String> = emptyList(),
)

enum class ScheduleType { DAILY, WEEKLY, EVERY_N_HOURS }

data class RuleSchedule(
    val type: ScheduleType,
    val dayOfWeek: Int? = null, // Calendar.MONDAY (2) … Calendar.SUNDAY (1) — null for DAILY / EVERY_N_HOURS
    val hour: Int,
    val minute: Int,
    /** Repeat count in the unit implied by [type]: hours, days, or weeks. */
    val repeatInterval: Int? = null,
    val usesStartTime: Boolean = true,
) {
    fun toReadableString(context: Context): String {
        val timeStr = formatTimeOfDay(context, hour, minute)
        val interval = repeatInterval ?: DEFAULT_REPEAT_INTERVAL
        return when (type) {
            ScheduleType.EVERY_N_HOURS -> {
                if (!usesStartTime) {
                    if (interval == 1) {
                        context.getString(R.string.schedule_every_hour)
                    } else {
                        context.getString(R.string.schedule_every_hours, interval)
                    }
                } else if (interval == 1) {
                    context.getString(R.string.schedule_every_hour_starting, timeStr)
                } else {
                    context.getString(R.string.schedule_every_hours_starting, interval, timeStr)
                }
            }

            ScheduleType.DAILY -> {
                if (interval == 1) {
                    context.getString(R.string.schedule_daily_at, timeStr)
                } else {
                    context.getString(R.string.schedule_every_days_at, interval, timeStr)
                }
            }

            ScheduleType.WEEKLY -> {
                val dayResIds =
                    arrayOf(
                        R.string.day_sun,
                        R.string.day_mon,
                        R.string.day_tue,
                        R.string.day_wed,
                        R.string.day_thu,
                        R.string.day_fri,
                        R.string.day_sat,
                    )
                val daysList = bitmaskToDaysOfWeek(dayOfWeek)
                val sortedDays = daysList.sorted()
                val daysStr =
                    sortedDays.joinToString(", ") { day ->
                        context.getString(dayResIds.getOrNull(day - 1) ?: R.string.day_mon)
                    }
                if (interval == 1) {
                    context.getString(R.string.schedule_weekly_on, daysStr, timeStr)
                } else {
                    context.getString(R.string.schedule_every_weeks_on, interval, daysStr, timeStr)
                }
            }
        }
    }

    companion object {
        const val DEFAULT_REPEAT_INTERVAL = 1
        const val MAX_HOURLY_REPEAT_INTERVAL = 24
        const val MAX_DAILY_REPEAT_INTERVAL = 365
        const val MAX_WEEKLY_REPEAT_INTERVAL = 52

        fun isRepeatIntervalValid(
            type: ScheduleType,
            interval: Int?,
        ): Boolean {
            val repeatInterval = interval ?: DEFAULT_REPEAT_INTERVAL
            return repeatInterval in DEFAULT_REPEAT_INTERVAL..maxRepeatIntervalFor(type)
        }

        fun maxRepeatIntervalFor(type: ScheduleType): Int =
            when (type) {
                ScheduleType.EVERY_N_HOURS -> MAX_HOURLY_REPEAT_INTERVAL
                ScheduleType.DAILY -> MAX_DAILY_REPEAT_INTERVAL
                ScheduleType.WEEKLY -> MAX_WEEKLY_REPEAT_INTERVAL
            }

        fun bitmaskToDaysOfWeek(bitmask: Int?): List<Int> {
            if (bitmask == null) return listOf(Calendar.MONDAY)
            if (bitmask and (1 shl 8) == 0) {
                return if (bitmask in 1..7) listOf(bitmask) else listOf(Calendar.MONDAY)
            }
            val days = mutableListOf<Int>()
            for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
                if (bitmask and (1 shl day) != 0) {
                    days.add(day)
                }
            }
            return if (days.isEmpty()) listOf(Calendar.MONDAY) else days
        }

        fun daysOfWeekToBitmask(days: List<Int>): Int {
            var bitmask = 1 shl 8
            for (day in days) {
                bitmask = bitmask or (1 shl day)
            }
            return bitmask
        }
    }
}

// ---

data class RunHistory(
    val id: Long = 0,
    val ruleId: Long?,
    val ruleName: String,
    val triggeredBy: TriggerType,
    val startedAt: Long,
    val completedAt: Long? = null,
    val status: RunStatus,
    val totalFilesFound: Int = 0,
    /** Files matched but not processed because the user cancelled (partial run). */
    val cancelledUnprocessedCount: Int = 0,
    val totalFilesMoved: Int = 0,
    val totalFilesFailed: Int = 0,
    val errorMessage: String? = null,
    val isReversed: Boolean = false,
    val operationMode: OperationMode = OperationMode.MOVE,
    /** Destination folder document URIs created during a copy run (for undo to remove empty dirs). */
    val copyCreatedDestFolderUris: List<String> = emptyList(),
)

enum class TriggerType { MANUAL, SCHEDULED }

enum class RunStatus { IN_PROGRESS, SUCCESS, PARTIAL_FAILURE, FAILED, CANCELLED, UNDONE }

/** Successful run with zero files moved and zero failures (shown as "No changes", not "Success"). */
fun RunHistory.isNoChangesRun(): Boolean = status == RunStatus.SUCCESS && totalFilesMoved == 0 && totalFilesFailed == 0

/** True after undo, including legacy rows that only set [RunHistory.isReversed]. */
fun RunHistory.isEffectivelyUndone(): Boolean = status == RunStatus.UNDONE || isReversed

enum class HistoryStatusFilter {
    ALL,
    SUCCESS,
    FAILED,
    PARTIAL,
    NO_CHANGES,
    CANCELLED,
    UNDONE,
}

// ---

data class FileMoved(
    val id: Long = 0,
    val runHistoryId: Long = 0,
    val fileName: String,
    val sourceUri: String,
    val destinationUri: String,
    val fileSizeBytes: Long,
    val relativeParentSegments: List<String> = emptyList(),
    val movedAt: Long = System.currentTimeMillis(),
    val success: Boolean,
    val skipped: Boolean = false,
    val errorMessage: String? = null,
)

// ---

data class RunResult(
    val ruleId: Long,
    val ruleName: String,
    val historyId: Long,
    val filesMoved: List<FileMoved>,
    val startedAt: Long,
    val completedAt: Long,
    val copyCreatedDestFolderUris: List<String> = emptyList(),
) {
    val totalMoved: Int get() = filesMoved.count { it.success && !it.skipped }
    val totalSkipped: Int get() = filesMoved.count { it.skipped }
    val totalFailed: Int get() = filesMoved.count { !it.success && !it.skipped }
    val status: RunStatus get() =
        when {
            filesMoved.isEmpty() -> RunStatus.SUCCESS
            totalFailed == 0 -> RunStatus.SUCCESS
            totalMoved == 0 && totalSkipped == 0 -> RunStatus.FAILED
            else -> RunStatus.PARTIAL_FAILURE
        }
}

// ---

data class PreviewFileResult(
    val fileName: String,
    val sourcePath: String,
    val simulatedDestPath: String,
    val wouldSkip: Boolean,
    val wouldOverwrite: Boolean,
    val renamedTo: String?,
    val sizeBytes: Long,
)

// ---

data class RunProgress(
    val ruleId: Long,
    val ruleName: String,
    val progress: Float = 0f,
    val currentFileName: String = "",
    val filesMoved: Int = 0,
    val totalFiles: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null,
) {
    companion object {
        /** Matches [ExecuteRulesUseCase] cancellation path; distinguishes success terminal from stopped mid-run. */
        const val ERROR_CANCELLED: String = "Cancelled"
    }
}
