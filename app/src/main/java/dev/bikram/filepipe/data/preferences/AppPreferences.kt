package dev.bikram.filepipe.data.preferences

import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.domain.model.HistorySortDirection
import dev.bikram.filepipe.domain.model.HistorySortKey

enum class SwipeAction {
    EDIT,
    DELETE,
    DUPLICATE,
    PREVIEW,
    VIEW_HISTORY,
}

fun SwipeAction.materialSymbolName(): String =
    when (this) {
        SwipeAction.EDIT -> "unfold_more"
        SwipeAction.DELETE -> "delete"
        SwipeAction.DUPLICATE -> "content_copy"
        SwipeAction.PREVIEW -> "visibility"
        SwipeAction.VIEW_HISTORY -> "history"
    }

/** Surface-shading intensity used when nothing is stored yet. 1.0 == the slider's "medium" notch. */
const val DEFAULT_SHADING_INTENSITY = 1.0f

/** ObtainX-style UI scale. Multiplies Compose density so layout, icons, and text grow together. */
const val DEFAULT_UI_SCALE = 1.0f
const val UI_SCALE_MIN = 0.75f
const val UI_SCALE_MAX = 1.25f

fun clampUiScale(raw: Float): Float {
    if (!raw.isFinite() || raw <= 0f) return DEFAULT_UI_SCALE
    return raw.coerceIn(UI_SCALE_MIN, UI_SCALE_MAX)
}

fun defaultUpdateCheckSchedule(): UpdateCheckSchedule =
    if (BuildConfig.FLAVOR == "fdroid") {
        UpdateCheckSchedule.NEVER
    } else {
        UpdateCheckSchedule.AT_APP_START
    }

data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val useBlackTheme: Boolean = false,
    val colorSource: AppColorSource = AppColorSource.MATERIAL_YOU,
    /** Saved custom accent seeds (canonical `#RRGGBB`); order is display order. */
    val savedCustomSeedHexes: List<String> = emptyList(),
    /** Active seed when [colorSource] is [AppColorSource.CUSTOM]. */
    val activeCustomSeedHex: String = "",
    val themePaletteStyle: ThemePaletteStyle = ThemePaletteStyle.TONAL_SPOT,
    val exportFolderUri: String = "",
    val cloudExportFolderUri: String = "",
    val autoExportOnRuleChange: Boolean = false,
    val scheduledExportEnabled: Boolean = false,
    val logRetentionDays: Int = 30,
    val swipeStartToEnd: SwipeAction = SwipeAction.EDIT,
    val swipeEndToStart: SwipeAction = SwipeAction.DELETE,
    val rulesSortKey: HistorySortKey = HistorySortKey.LAST_RAN,
    val rulesSortDirection: HistorySortDirection = HistorySortDirection.DESCENDING,
    val rulesCompactMode: Boolean = false,
    val historySortKey: HistorySortKey = HistorySortKey.LAST_RAN,
    val historySortDirection: HistorySortDirection = HistorySortDirection.DESCENDING,
    val settingsCollapsedSectionKeys: List<String> = emptyList(),
    val bookmarkedFolders: List<String> = emptyList(),
    val hasSeenIntro: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val progressiveBlurEnabled: Boolean = true,
    val updateCheckSchedule: UpdateCheckSchedule = defaultUpdateCheckSchedule(),
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
    val shadingIntensity: Float = DEFAULT_SHADING_INTENSITY,
    val uiScale: Float = DEFAULT_UI_SCALE,
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
    val playAutoReviewPromptedForLastUpdateTime: Long = 0L,
    /** Absolute path to a user-imported UI font under app filesDir/fonts. Empty == system font. */
    val customFontPath: String = "",
    val customFontName: String = "",
) {
    val useEnhancedShading: Boolean
        get() = shadingIntensity > 0.0f

    /** ObtainX parity: pure black only while the app is effectively on a dark theme. */
    fun blackThemeActive(isDarkTheme: Boolean): Boolean = useBlackTheme && themeMode.blackThemeEligible(isDarkTheme)

    fun effectiveShadingIntensity(blackThemeActive: Boolean): Float = if (blackThemeActive) DEFAULT_SHADING_INTENSITY else shadingIntensity

    fun effectiveUseGradient(blackThemeActive: Boolean): Boolean = if (blackThemeActive) false else useGradientBackground

    companion object {
        val DEFAULT = AppPreferences()
    }
}
