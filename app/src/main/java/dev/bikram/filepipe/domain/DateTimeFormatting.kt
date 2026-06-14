package dev.bikram.filepipe.domain

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Wall-clock time-of-day (hour:minute) formatted with the system 12/24-hour setting and the
 * device locale — including a *localized* AM/PM marker. Shared by the schedule summary
 * ([dev.bikram.filepipe.domain.model.RuleSchedule.toReadableString]) and the schedule dialog so
 * neither hand-rolls the format (the old inline versions hardcoded English "AM"/"PM").
 */
fun formatTimeOfDay(
    context: Context,
    hour: Int,
    minute: Int,
): String {
    val calendar =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

/**
 * Stable, sortable, ASCII timestamp for backup file names. Intentionally locale-independent
 * (fixed pattern + [Locale.US]) so exported names stay portable and chronologically ordered
 * regardless of device locale or 12h/24h setting.
 */
fun backupFileTimestamp(): String = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
