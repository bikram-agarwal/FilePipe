package dev.bikram.filepipe.worker

import android.content.Context
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.model.RunProgress

private const val MAX_ERROR_LENGTH_FOR_NOTIFICATION = 120

/**
 * [RunProgress.isComplete] is set for success, cancellation, and errors; only the success path has
 * [RunProgress.error] null.
 */
internal fun Context.notificationContentTextForTerminalProgress(progress: RunProgress): String {
    val errorMessage = progress.error
    if (errorMessage == null) {
        return getString(R.string.notification_finishing)
    }
    if (errorMessage == RunProgress.ERROR_CANCELLED) {
        return getString(R.string.notification_run_stopped_early)
    }
    val trimmed = errorMessage.trim()
    val shortened =
        if (trimmed.length > MAX_ERROR_LENGTH_FOR_NOTIFICATION) {
            trimmed.take(MAX_ERROR_LENGTH_FOR_NOTIFICATION - 1) + "…"
        } else {
            trimmed
        }
    return getString(R.string.notification_run_failed_abbr, shortened)
}
