package dev.bikram.filepipe.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object InAppRatingAutoPrompt {
    const val AUTO_PROMPT_DELAY_MS: Long = 24 * 60 * 60 * 1000L

    /**
     * After a manual or automatic in-app review launch attempt, suppress another auto request briefly
     * so Settings and the main gate do not stack in the same resume window.
     */
    object SessionCoordination {
        @Volatile
        var lastInAppReviewAttemptWallClockMillis: Long = 0L

        const val AUTO_VS_MANUAL_DEBOUNCE_MS: Long = 5_000L
    }

    fun packageLastUpdateTimeMillis(context: Context): Long {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        return packageInfo.lastUpdateTime
    }

    fun isEligibleForAutoPrompt(
        lastUpdateTimeMillis: Long,
        nowMillis: Long,
        neverAskAgain: Boolean,
        promptedForLastUpdateTimeMillis: Long
    ): Boolean {
        if (neverAskAgain) return false
        if (nowMillis - lastUpdateTimeMillis < AUTO_PROMPT_DELAY_MS) return false
        if (lastUpdateTimeMillis == promptedForLastUpdateTimeMillis) return false
        return true
    }
}
