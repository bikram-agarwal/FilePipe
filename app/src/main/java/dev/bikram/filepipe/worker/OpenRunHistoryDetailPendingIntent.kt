package dev.bikram.filepipe.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.bikram.filepipe.MainActivity
import dev.bikram.filepipe.shortcuts.PendingShortcutRepository

internal fun openRunHistoryDetailPendingIntent(context: Context, historyId: Long): PendingIntent {
    val tapIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(PendingShortcutRepository.EXTRA_OPEN_HISTORY_DETAIL_ID, historyId)
    }
    return PendingIntent.getActivity(
        context,
        historyId.toInt(),
        tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
