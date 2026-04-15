package dev.bikram.filepipe.data.preferences

enum class SwipeAction {
    EDIT, DELETE, DUPLICATE, PREVIEW, VIEW_HISTORY
}

data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val colorSource: AppColorSource = AppColorSource.MATERIAL_YOU,
    /** Saved custom accent seeds (canonical `#RRGGBB`); order is display order. */
    val savedCustomSeedHexes: List<String> = emptyList(),
    /** Active seed when [colorSource] is [AppColorSource.CUSTOM]. */
    val activeCustomSeedHex: String = "",
    val themePaletteStyle: ThemePaletteStyle = ThemePaletteStyle.TONAL_SPOT,
    val exportFolderUri: String = "",
    val autoExportOnRuleChange: Boolean = false,
    val scheduledExportEnabled: Boolean = false,
    val logRetentionDays: Int = 30,
    val swipeStartToEnd: SwipeAction = SwipeAction.EDIT,
    val swipeEndToStart: SwipeAction = SwipeAction.DELETE,
    val bookmarkedFolders: List<String> = emptyList(),
    val hasSeenIntro: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val progressiveBlurEnabled: Boolean = true,
    val updateCheckSchedule: UpdateCheckSchedule = UpdateCheckSchedule.AT_APP_START,
    val notifyOnNewUpdates: Boolean = false,
    /** Last [UpdateInfo.notificationDedupeKey] we posted an update notification for. */
    val updateLastNotifiedDedupeKey: String = "",
    /** GitHub flavor: copy downloaded update APK to MediaStore Downloads after download. */
    val saveUpdateApkToDownloads: Boolean = false,
    /**
     * GitHub flavor: last update APK download completed a successful copy to Downloads (MediaStore).
     * Used so cache cleanup runs only after a saved copy exists, not merely when the toggle is on.
     */
    val updateApkDownloadsCopySucceeded: Boolean = false,
    val useGradientBackground: Boolean = true,
    /** Neutral light/dark greys for list cards instead of accent-tinted elevated surfaces. */
    val useFixedCardColors: Boolean = false,
    /** SAF vs All files. Legacy [FolderAccessMode.DEFERRED] is migrated to [FolderAccessMode.SAF_ONLY]. */
    val folderAccessMode: FolderAccessMode = FolderAccessMode.SAF_ONLY,
    /**
     * Play flavor: user turned off automatic in-app rating in Settings. When true, the app does not
     * auto-request Google Play's in-app review flow after updates.
     */
    val inAppReviewAutoNeverAskAgain: Boolean = false,
    /**
     * Play flavor: [android.content.pm.PackageInfo.lastUpdateTime] for which we already requested the
     * automatic in-app review flow (or it completed) for this install/update cycle.
     */
    val playAutoReviewPromptedForLastUpdateTime: Long = 0L
) {
    companion object {
        val DEFAULT = AppPreferences()
    }
}
