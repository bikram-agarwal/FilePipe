package dev.bikram.filepipe.domain.model

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
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val schedule: RuleSchedule? = null,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.RENAME_SUFFIX,
    val operationMode: OperationMode = OperationMode.MOVE,
    val scanSubdirectories: Boolean = false,
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
    val excludePatterns: List<String> = emptyList()
)

enum class ScheduleType { DAILY, WEEKLY, EVERY_N_HOURS }

data class RuleSchedule(
    val type: ScheduleType,
    val dayOfWeek: Int? = null,  // Calendar.MONDAY (2) … Calendar.SUNDAY (1) — null for DAILY / EVERY_N_HOURS
    val hour: Int,
    val minute: Int,
    /** Required when [type] is [ScheduleType.EVERY_N_HOURS]; UI allows 1–24 hours (use daily for longer gaps). */
    val intervalHours: Int? = null
)

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
    val copyCreatedDestFolderUris: List<String> = emptyList()
)

enum class TriggerType { MANUAL, SCHEDULED }

enum class RunStatus { IN_PROGRESS, SUCCESS, PARTIAL_FAILURE, FAILED, CANCELLED, UNDONE }

/** Successful run with zero files moved and zero failures (shown as "No changes", not "Success"). */
fun RunHistory.isNoChangesRun(): Boolean =
    status == RunStatus.SUCCESS && totalFilesMoved == 0 && totalFilesFailed == 0

/** True after undo, including legacy rows that only set [RunHistory.isReversed]. */
fun RunHistory.isEffectivelyUndone(): Boolean =
    status == RunStatus.UNDONE || isReversed

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
    val errorMessage: String? = null
)

// ---

data class RunResult(
    val ruleId: Long,
    val ruleName: String,
    val historyId: Long,
    val filesMoved: List<FileMoved>,
    val startedAt: Long,
    val completedAt: Long,
    val copyCreatedDestFolderUris: List<String> = emptyList()
) {
    val totalMoved: Int get() = filesMoved.count { it.success && !it.skipped }
    val totalSkipped: Int get() = filesMoved.count { it.skipped }
    val totalFailed: Int get() = filesMoved.count { !it.success && !it.skipped }
    val status: RunStatus get() = when {
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
    val sizeBytes: Long
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
    val error: String? = null
) {
    companion object {
        /** Matches [ExecuteRulesUseCase] cancellation path; distinguishes success terminal from stopped mid-run. */
        const val ERROR_CANCELLED: String = "Cancelled"
    }
}
