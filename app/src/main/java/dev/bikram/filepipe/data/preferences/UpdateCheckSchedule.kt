package dev.bikram.filepipe.data.preferences

enum class UpdateCheckSchedule {
    /** Run a silent check when the main UI loads after intro. */
    AT_APP_START,

    /** Local wall time every day at 21:00. */
    DAILY_AT_21,

    /** Local wall time every Monday at 21:00. */
    WEEKLY_MONDAY_AT_21,

    /** No automatic checks. */
    NEVER
}
