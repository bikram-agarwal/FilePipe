package dev.bikram.filepipe.data.preferences

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import dev.bikram.filepipe.domain.export.SettingsBackupDto
import dev.bikram.filepipe.ui.theme.normalizeCustomSeedHexOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings"
)

private val customSeedHexListJson = Json {
    ignoreUnknownKeys = true
}

data class GithubReleaseAckState(
    val fingerprint: String?,
    val forInstalledVersion: String?
)

private fun decodeCustomSeedHexList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        customSeedHexListJson.decodeFromString<List<String>>(json)
    }.getOrElse { emptyList() }
}

private object PrefKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val USE_MATERIAL_YOU = booleanPreferencesKey("use_material_you")
    val COLOR_SOURCE = stringPreferencesKey("color_source")
    val THEME_PALETTE_STYLE = stringPreferencesKey("theme_palette_style")
    val EXPORT_FOLDER_URI = stringPreferencesKey("export_folder_uri")
    val AUTO_EXPORT_ON_CHANGE = booleanPreferencesKey("auto_export_on_change")
    val SCHEDULED_EXPORT = booleanPreferencesKey("scheduled_export_enabled")
    val LOG_RETENTION_DAYS = intPreferencesKey("log_retention_days")
    val SWIPE_START_TO_END = stringPreferencesKey("swipe_start_to_end")
    val SWIPE_END_TO_START = stringPreferencesKey("swipe_end_to_start")
    val BOOKMARKED_FOLDERS = stringPreferencesKey("bookmarked_folders")
    val HAS_SEEN_INTRO = booleanPreferencesKey("has_seen_intro")
    val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback_enabled")
    val PROGRESSIVE_BLUR = booleanPreferencesKey("progressive_blur_enabled")
    /** Legacy; migrated into [UPDATE_CHECK_SCHEDULE]. */
    val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_for_updates")
    val UPDATE_CHECK_SCHEDULE = stringPreferencesKey("update_check_schedule")
    val NOTIFY_ON_NEW_UPDATES = booleanPreferencesKey("notify_on_new_updates")
    val UPDATE_LAST_NOTIFIED_DEDUPE_KEY = stringPreferencesKey("update_last_notified_dedupe_key")
    val GITHUB_ACK_FINGERPRINT = stringPreferencesKey("github_last_acknowledged_release_fingerprint")
    val GITHUB_ACK_INSTALLED_VERSION = stringPreferencesKey("github_acknowledged_for_installed_version")
    val SAVE_UPDATE_APK_TO_DOWNLOADS = booleanPreferencesKey("save_update_apk_to_downloads")
    val UPDATE_APK_DOWNLOADS_COPY_SUCCEEDED = booleanPreferencesKey("update_apk_downloads_copy_succeeded")
    val USE_GRADIENT_BACKGROUND = booleanPreferencesKey("use_gradient_background")
    val FIXED_CARD_COLORS = booleanPreferencesKey("fixed_card_colors")
    /** Legacy; migrated into [CUSTOM_SEED_HEX_LIST] + [ACTIVE_CUSTOM_SEED_HEX]. */
    val CUSTOM_SEED_HEX = stringPreferencesKey("custom_seed_hex")
    val CUSTOM_SEED_HEX_LIST = stringPreferencesKey("custom_seed_hex_list")
    val ACTIVE_CUSTOM_SEED_HEX = stringPreferencesKey("active_custom_seed_hex")
    val FOLDER_ACCESS_MODE = stringPreferencesKey("folder_access_mode")
    val IN_APP_REVIEW_AUTO_NEVER_ASK_AGAIN = booleanPreferencesKey("in_app_review_auto_never_ask_again")
    val PLAY_AUTO_REVIEW_PROMPTED_FOR_LAST_UPDATE_TIME =
        longPreferencesKey("play_auto_review_prompted_for_last_update_time")
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val dataStore = context.userPreferencesDataStore

    val preferencesFlow: Flow<AppPreferences> = dataStore.data.map { prefs ->
        val rawTheme = prefs[PrefKeys.THEME_MODE]
        val parsedMode = rawTheme?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
        val legacyMaterialYou = rawTheme == "MATERIAL_YOU"
        val themeMode = when {
            legacyMaterialYou -> AppThemeMode.DARK
            parsedMode != null -> parsedMode
            else -> AppThemeMode.SYSTEM
        }
        val storedColorSource = prefs[PrefKeys.COLOR_SOURCE]?.let { raw ->
            runCatching { AppColorSource.valueOf(raw) }.getOrNull()
        }
        val colorSource = storedColorSource ?: when {
            legacyMaterialYou -> AppColorSource.MATERIAL_YOU
            else -> {
                val legacyToggle = prefs[PrefKeys.USE_MATERIAL_YOU] ?: true
                if (legacyToggle) AppColorSource.MATERIAL_YOU else AppColorSource.DEFAULT
            }
        }
        val themePaletteStyle = prefs[PrefKeys.THEME_PALETTE_STYLE]?.let { raw ->
            runCatching { ThemePaletteStyle.valueOf(raw) }.getOrNull()
        } ?: ThemePaletteStyle.TONAL_SPOT
        val listFromStore = prefs[PrefKeys.CUSTOM_SEED_HEX_LIST]?.let { decodeCustomSeedHexList(it) }
        val legacyHex = prefs[PrefKeys.CUSTOM_SEED_HEX].orEmpty().trim()
        val savedCustomSeedHexes = when {
            listFromStore != null -> listFromStore
            legacyHex.isNotBlank() -> listOf(legacyHex)
            else -> emptyList()
        }
        val activeFromKey = prefs[PrefKeys.ACTIVE_CUSTOM_SEED_HEX].orEmpty().trim()
        val activeCustomSeedHex = when {
            activeFromKey.isNotBlank() -> activeFromKey
            savedCustomSeedHexes.isNotEmpty() -> savedCustomSeedHexes.first()
            else -> ""
        }
        AppPreferences(
            themeMode = themeMode,
            colorSource = colorSource,
            savedCustomSeedHexes = savedCustomSeedHexes,
            activeCustomSeedHex = activeCustomSeedHex,
            themePaletteStyle = themePaletteStyle,
            exportFolderUri = prefs[PrefKeys.EXPORT_FOLDER_URI].orEmpty(),
            autoExportOnRuleChange = prefs[PrefKeys.AUTO_EXPORT_ON_CHANGE] ?: false,
            scheduledExportEnabled = prefs[PrefKeys.SCHEDULED_EXPORT] ?: false,
            logRetentionDays = prefs[PrefKeys.LOG_RETENTION_DAYS] ?: 30,
            swipeStartToEnd = prefs[PrefKeys.SWIPE_START_TO_END]
                ?.let { runCatching { SwipeAction.valueOf(it) }.getOrNull() }
                ?: SwipeAction.EDIT,
            swipeEndToStart = prefs[PrefKeys.SWIPE_END_TO_START]
                ?.let { runCatching { SwipeAction.valueOf(it) }.getOrNull() }
                ?: SwipeAction.DELETE,
            bookmarkedFolders = prefs[PrefKeys.BOOKMARKED_FOLDERS]
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?: emptyList(),
            hasSeenIntro = prefs[PrefKeys.HAS_SEEN_INTRO] ?: false,
            hapticFeedbackEnabled = prefs[PrefKeys.HAPTIC_FEEDBACK] ?: true,
            progressiveBlurEnabled = prefs[PrefKeys.PROGRESSIVE_BLUR] ?: true,
            updateCheckSchedule = prefs[PrefKeys.UPDATE_CHECK_SCHEDULE]?.let { raw ->
                runCatching { UpdateCheckSchedule.valueOf(raw) }.getOrNull()
            } ?: when (prefs[PrefKeys.AUTO_CHECK_UPDATES]) {
                false -> UpdateCheckSchedule.NEVER
                else -> UpdateCheckSchedule.AT_APP_START
            },
            notifyOnNewUpdates = prefs[PrefKeys.NOTIFY_ON_NEW_UPDATES] ?: false,
            updateLastNotifiedDedupeKey = prefs[PrefKeys.UPDATE_LAST_NOTIFIED_DEDUPE_KEY].orEmpty(),
            saveUpdateApkToDownloads = prefs[PrefKeys.SAVE_UPDATE_APK_TO_DOWNLOADS] ?: false,
            updateApkDownloadsCopySucceeded = prefs[PrefKeys.UPDATE_APK_DOWNLOADS_COPY_SUCCEEDED] ?: false,
            useGradientBackground = prefs[PrefKeys.USE_GRADIENT_BACKGROUND] ?: true,
            useFixedCardColors = prefs[PrefKeys.FIXED_CARD_COLORS] ?: false,
            folderAccessMode = normalizeFolderAccessModeStored(
                prefs[PrefKeys.FOLDER_ACCESS_MODE]?.let { raw ->
                    runCatching { FolderAccessMode.valueOf(raw) }.getOrNull()
                }
            ),
            inAppReviewAutoNeverAskAgain = prefs[PrefKeys.IN_APP_REVIEW_AUTO_NEVER_ASK_AGAIN] ?: false,
            playAutoReviewPromptedForLastUpdateTime =
                prefs[PrefKeys.PLAY_AUTO_REVIEW_PROMPTED_FOR_LAST_UPDATE_TIME] ?: 0L
        )
    }

    suspend fun getPreferencesSnapshot(): AppPreferences = preferencesFlow.first()

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[PrefKeys.THEME_MODE] = mode.name }
    }

    suspend fun setColorSource(source: AppColorSource) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.COLOR_SOURCE] = source.name
            prefs.remove(PrefKeys.USE_MATERIAL_YOU)
        }
    }

    suspend fun setThemePaletteStyle(style: ThemePaletteStyle) {
        dataStore.edit { it[PrefKeys.THEME_PALETTE_STYLE] = style.name }
    }

    suspend fun setExportFolderUri(uriString: String) {
        dataStore.edit { it[PrefKeys.EXPORT_FOLDER_URI] = uriString }
    }

    suspend fun setAutoExportOnRuleChange(enabled: Boolean) {
        dataStore.edit { it[PrefKeys.AUTO_EXPORT_ON_CHANGE] = enabled }
    }

    suspend fun setScheduledExportEnabled(enabled: Boolean) {
        dataStore.edit { it[PrefKeys.SCHEDULED_EXPORT] = enabled }
    }

    suspend fun setLogRetentionDays(days: Int) {
        dataStore.edit { it[PrefKeys.LOG_RETENTION_DAYS] = days }
    }

    suspend fun setSwipeStartToEnd(action: SwipeAction) {
        dataStore.edit { it[PrefKeys.SWIPE_START_TO_END] = action.name }
    }

    suspend fun setSwipeEndToStart(action: SwipeAction) {
        dataStore.edit { it[PrefKeys.SWIPE_END_TO_START] = action.name }
    }

    suspend fun addBookmark(path: String) {
        dataStore.edit { prefs ->
            val current = prefs[PrefKeys.BOOKMARKED_FOLDERS]?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
            if (path !in current) {
                prefs[PrefKeys.BOOKMARKED_FOLDERS] = (current + path).joinToString("|")
            }
        }
    }

    suspend fun removeBookmark(path: String) {
        dataStore.edit { prefs ->
            val current = prefs[PrefKeys.BOOKMARKED_FOLDERS]?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
            prefs[PrefKeys.BOOKMARKED_FOLDERS] = current.filter { it != path }.joinToString("|")
        }
    }

    suspend fun markIntroSeen() {
        dataStore.edit { it[PrefKeys.HAS_SEEN_INTRO] = true }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        dataStore.edit { it[PrefKeys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setProgressiveBlurEnabled(enabled: Boolean) {
        dataStore.edit { it[PrefKeys.PROGRESSIVE_BLUR] = enabled }
    }

    suspend fun setUpdateCheckSchedule(schedule: UpdateCheckSchedule) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.UPDATE_CHECK_SCHEDULE] = schedule.name
            prefs.remove(PrefKeys.AUTO_CHECK_UPDATES)
        }
    }

    suspend fun setNotifyOnNewUpdates(enabled: Boolean) {
        dataStore.edit { it[PrefKeys.NOTIFY_ON_NEW_UPDATES] = enabled }
    }

    suspend fun setUpdateLastNotifiedDedupeKey(key: String) {
        dataStore.edit { it[PrefKeys.UPDATE_LAST_NOTIFIED_DEDUPE_KEY] = key }
    }

    suspend fun readGithubReleaseAck(): GithubReleaseAckState {
        val prefs = dataStore.data.first()
        return GithubReleaseAckState(
            fingerprint = prefs[PrefKeys.GITHUB_ACK_FINGERPRINT],
            forInstalledVersion = prefs[PrefKeys.GITHUB_ACK_INSTALLED_VERSION]
        )
    }

    suspend fun writeGithubReleaseAck(fingerprint: String, installedVersionName: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.GITHUB_ACK_FINGERPRINT] = fingerprint
            prefs[PrefKeys.GITHUB_ACK_INSTALLED_VERSION] = installedVersionName
        }
    }

    /**
     * One-time: `auto_check_for_updates` boolean -> [PrefKeys.UPDATE_CHECK_SCHEDULE]; removes legacy key.
     */
    suspend fun migrateLegacyAutoCheckToScheduleIfNeeded() {
        dataStore.edit { prefs ->
            if (prefs[PrefKeys.UPDATE_CHECK_SCHEDULE] != null) {
                prefs.remove(PrefKeys.AUTO_CHECK_UPDATES)
                return@edit
            }
            val legacy = prefs[PrefKeys.AUTO_CHECK_UPDATES]
            val scheduleName = when (legacy) {
                false -> UpdateCheckSchedule.NEVER.name
                else -> UpdateCheckSchedule.AT_APP_START.name
            }
            prefs[PrefKeys.UPDATE_CHECK_SCHEDULE] = scheduleName
            prefs.remove(PrefKeys.AUTO_CHECK_UPDATES)
        }
    }

    suspend fun setSaveUpdateApkToDownloads(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.SAVE_UPDATE_APK_TO_DOWNLOADS] = enabled
            prefs.remove(PrefKeys.UPDATE_APK_DOWNLOADS_COPY_SUCCEEDED)
        }
    }

    suspend fun clearUpdateApkDownloadsCopySucceeded() {
        dataStore.edit { it.remove(PrefKeys.UPDATE_APK_DOWNLOADS_COPY_SUCCEEDED) }
    }

    suspend fun markUpdateApkDownloadsCopySucceeded() {
        dataStore.edit { it[PrefKeys.UPDATE_APK_DOWNLOADS_COPY_SUCCEEDED] = true }
    }

    suspend fun setUseGradientBackground(enabled: Boolean) {
        dataStore.edit { it[PrefKeys.USE_GRADIENT_BACKGROUND] = enabled }
    }

    suspend fun setUseFixedCardColors(enabled: Boolean) {
        dataStore.edit { it[PrefKeys.FIXED_CARD_COLORS] = enabled }
    }

    suspend fun setFolderAccessMode(mode: FolderAccessMode) {
        dataStore.edit { it[PrefKeys.FOLDER_ACCESS_MODE] = mode.name }
    }

    suspend fun setInAppReviewAutoNeverAskAgain(neverAskAgain: Boolean) {
        dataStore.edit { it[PrefKeys.IN_APP_REVIEW_AUTO_NEVER_ASK_AGAIN] = neverAskAgain }
    }

    suspend fun setPlayAutoReviewPromptedForLastUpdateTime(lastUpdateTimeMillis: Long) {
        dataStore.edit {
            it[PrefKeys.PLAY_AUTO_REVIEW_PROMPTED_FOR_LAST_UPDATE_TIME] = lastUpdateTimeMillis
        }
    }

    /**
     * One-time: persist [FolderAccessMode.SAF_ONLY] when legacy [FolderAccessMode.DEFERRED] was stored.
     * Safe to call repeatedly.
     */
    suspend fun migrateDeferredFolderAccessIfNeeded() {
        dataStore.edit { prefs ->
            if (prefs[PrefKeys.FOLDER_ACCESS_MODE] != FolderAccessMode.DEFERRED.name) return@edit
            prefs[PrefKeys.FOLDER_ACCESS_MODE] = FolderAccessMode.SAF_ONLY.name
        }
    }

    private fun normalizeFolderAccessModeStored(mode: FolderAccessMode?): FolderAccessMode =
        when (mode) {
            null -> FolderAccessMode.SAF_ONLY
            FolderAccessMode.DEFERRED -> FolderAccessMode.SAF_ONLY
            else -> mode
        }

    /**
     * One-time migration from single [PrefKeys.CUSTOM_SEED_HEX] to list + active keys.
     * Safe to call repeatedly.
     */
    suspend fun migrateLegacyCustomSeedIfNeeded() {
        dataStore.edit { prefs ->
            if (prefs[PrefKeys.CUSTOM_SEED_HEX_LIST] != null) return@edit
            val legacy = prefs[PrefKeys.CUSTOM_SEED_HEX]?.trim()?.takeIf { it.isNotBlank() }
            if (legacy != null) {
                prefs[PrefKeys.CUSTOM_SEED_HEX_LIST] =
                    customSeedHexListJson.encodeToString(listOf(legacy))
                prefs[PrefKeys.ACTIVE_CUSTOM_SEED_HEX] = legacy
            } else {
                prefs[PrefKeys.CUSTOM_SEED_HEX_LIST] =
                    customSeedHexListJson.encodeToString(emptyList<String>())
            }
            prefs.remove(PrefKeys.CUSTOM_SEED_HEX)
        }
    }

    suspend fun addCustomSeedHex(rawInput: String) {
        migrateLegacyCustomSeedIfNeeded()
        val normalized = normalizeCustomSeedHexOrNull(rawInput) ?: return
        dataStore.edit { prefs ->
            val current = decodeCustomSeedHexList(prefs[PrefKeys.CUSTOM_SEED_HEX_LIST]).toMutableList()
            current.removeAll { existing ->
                normalizeCustomSeedHexOrNull(existing) == normalized
            }
            current.add(normalized)
            prefs[PrefKeys.CUSTOM_SEED_HEX_LIST] =
                customSeedHexListJson.encodeToString(current)
            prefs[PrefKeys.ACTIVE_CUSTOM_SEED_HEX] = normalized
            prefs[PrefKeys.COLOR_SOURCE] = AppColorSource.CUSTOM.name
            prefs.remove(PrefKeys.USE_MATERIAL_YOU)
            prefs.remove(PrefKeys.CUSTOM_SEED_HEX)
        }
    }

    suspend fun selectCustomSeedHex(hex: String) {
        migrateLegacyCustomSeedIfNeeded()
        val targetNorm = normalizeCustomSeedHexOrNull(hex) ?: return
        dataStore.edit { prefs ->
            val current = decodeCustomSeedHexList(prefs[PrefKeys.CUSTOM_SEED_HEX_LIST])
            val matchedStored = current.firstOrNull { stored ->
                normalizeCustomSeedHexOrNull(stored) == targetNorm
            } ?: return@edit
            prefs[PrefKeys.ACTIVE_CUSTOM_SEED_HEX] =
                normalizeCustomSeedHexOrNull(matchedStored) ?: matchedStored
            prefs[PrefKeys.COLOR_SOURCE] = AppColorSource.CUSTOM.name
            prefs.remove(PrefKeys.USE_MATERIAL_YOU)
        }
    }

    suspend fun removeCustomSeedHex(hex: String) {
        migrateLegacyCustomSeedIfNeeded()
        val targetNorm = normalizeCustomSeedHexOrNull(hex) ?: return
        dataStore.edit { prefs ->
            val current = decodeCustomSeedHexList(prefs[PrefKeys.CUSTOM_SEED_HEX_LIST])
            val filtered = current.filterNot { stored ->
                normalizeCustomSeedHexOrNull(stored) == targetNorm
            }
            prefs[PrefKeys.CUSTOM_SEED_HEX_LIST] =
                customSeedHexListJson.encodeToString(filtered)
            val activeRaw = prefs[PrefKeys.ACTIVE_CUSTOM_SEED_HEX].orEmpty()
            val activeNorm = normalizeCustomSeedHexOrNull(activeRaw)
            val wasActive = activeNorm == targetNorm
            if (wasActive) {
                if (filtered.isEmpty()) {
                    prefs.remove(PrefKeys.ACTIVE_CUSTOM_SEED_HEX)
                    prefs[PrefKeys.COLOR_SOURCE] = AppColorSource.DEFAULT.name
                } else {
                    val next = normalizeCustomSeedHexOrNull(filtered.first()) ?: filtered.first()
                    prefs[PrefKeys.ACTIVE_CUSTOM_SEED_HEX] = next
                }
            }
            prefs.remove(PrefKeys.CUSTOM_SEED_HEX)
        }
    }

    suspend fun applySettingsFromBackup(dto: SettingsBackupDto) {
        val exportUriString = dto.exportFolderUri
        if (exportUriString.startsWith("content://")) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    Uri.parse(exportUriString),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        dataStore.edit { prefs ->
            val themeMode = runCatching { AppThemeMode.valueOf(dto.themeMode) }.getOrDefault(AppThemeMode.SYSTEM)
            prefs[PrefKeys.THEME_MODE] = themeMode.name

            val parsedColorSource = dto.colorSource?.let { raw ->
                runCatching { AppColorSource.valueOf(raw) }.getOrNull()
            }
            when {
                parsedColorSource != null -> {
                    prefs[PrefKeys.COLOR_SOURCE] = parsedColorSource.name
                    prefs.remove(PrefKeys.USE_MATERIAL_YOU)
                }
                dto.useMaterialYou == true -> {
                    prefs.remove(PrefKeys.COLOR_SOURCE)
                    prefs[PrefKeys.USE_MATERIAL_YOU] = true
                }
                dto.useMaterialYou == false -> {
                    prefs.remove(PrefKeys.COLOR_SOURCE)
                    prefs[PrefKeys.USE_MATERIAL_YOU] = false
                }
            }

            dto.themePaletteStyle?.let { raw ->
                runCatching { ThemePaletteStyle.valueOf(raw) }.getOrNull()?.let { style ->
                    prefs[PrefKeys.THEME_PALETTE_STYLE] = style.name
                }
            }

            prefs[PrefKeys.EXPORT_FOLDER_URI] = exportUriString
            prefs[PrefKeys.AUTO_EXPORT_ON_CHANGE] = dto.autoExportOnRuleChange
            prefs[PrefKeys.SCHEDULED_EXPORT] = dto.scheduledExportEnabled
            prefs[PrefKeys.LOG_RETENTION_DAYS] = dto.logRetentionDays

            runCatching { SwipeAction.valueOf(dto.swipeStartToEnd) }.getOrNull()?.let { action ->
                prefs[PrefKeys.SWIPE_START_TO_END] = action.name
            }
            runCatching { SwipeAction.valueOf(dto.swipeEndToStart) }.getOrNull()?.let { action ->
                prefs[PrefKeys.SWIPE_END_TO_START] = action.name
            }

            prefs[PrefKeys.BOOKMARKED_FOLDERS] =
                dto.bookmarkedFolders.filter { it.isNotBlank() }.joinToString("|")

            prefs[PrefKeys.HAS_SEEN_INTRO] = dto.hasSeenIntro
            prefs[PrefKeys.HAPTIC_FEEDBACK] = dto.hapticFeedbackEnabled
            prefs[PrefKeys.PROGRESSIVE_BLUR] = dto.progressiveBlurEnabled
            val resolvedSchedule = when {
                !dto.updateCheckSchedule.isNullOrBlank() ->
                    runCatching { UpdateCheckSchedule.valueOf(dto.updateCheckSchedule) }.getOrNull()
                        ?: UpdateCheckSchedule.AT_APP_START
                dto.autoCheckForUpdates == false -> UpdateCheckSchedule.NEVER
                else -> UpdateCheckSchedule.AT_APP_START
            }
            prefs[PrefKeys.UPDATE_CHECK_SCHEDULE] = resolvedSchedule.name
            prefs.remove(PrefKeys.AUTO_CHECK_UPDATES)
            prefs[PrefKeys.NOTIFY_ON_NEW_UPDATES] = dto.notifyOnNewUpdates ?: false
            prefs[PrefKeys.SAVE_UPDATE_APK_TO_DOWNLOADS] = dto.saveUpdateApkToDownloads
            prefs.remove(PrefKeys.UPDATE_APK_DOWNLOADS_COPY_SUCCEEDED)
            prefs[PrefKeys.USE_GRADIENT_BACKGROUND] = dto.useGradientBackground
            prefs[PrefKeys.FIXED_CARD_COLORS] = dto.useFixedCardColors

            dto.folderAccessMode?.let { raw ->
                runCatching { FolderAccessMode.valueOf(raw) }.getOrNull()?.let { mode ->
                    val toStore = if (mode == FolderAccessMode.DEFERRED) FolderAccessMode.SAF_ONLY else mode
                    prefs[PrefKeys.FOLDER_ACCESS_MODE] = toStore.name
                }
            }

            val restoredList: List<String>? = when {
                dto.customSeedHexes != null -> dto.customSeedHexes
                !dto.activeCustomSeedHex.isNullOrBlank() ->
                    listOf(dto.activeCustomSeedHex.trim())
                !dto.customSeedHex.isNullOrBlank() ->
                    listOf(dto.customSeedHex.trim())
                else -> null
            }
            if (restoredList != null) {
                val normalizedList = restoredList.mapNotNull { normalizeCustomSeedHexOrNull(it) }.distinct()
                prefs[PrefKeys.CUSTOM_SEED_HEX_LIST] =
                    customSeedHexListJson.encodeToString(normalizedList)
                if (normalizedList.isEmpty()) {
                    prefs.remove(PrefKeys.ACTIVE_CUSTOM_SEED_HEX)
                    val sourceAfterColor = prefs[PrefKeys.COLOR_SOURCE]?.let { raw ->
                        runCatching { AppColorSource.valueOf(raw) }.getOrNull()
                    }
                    if (sourceAfterColor == AppColorSource.CUSTOM) {
                        prefs[PrefKeys.COLOR_SOURCE] = AppColorSource.DEFAULT.name
                    }
                } else {
                    val activeCandidate = when {
                        !dto.activeCustomSeedHex.isNullOrBlank() ->
                            normalizeCustomSeedHexOrNull(dto.activeCustomSeedHex.trim())
                        !dto.customSeedHex.isNullOrBlank() ->
                            normalizeCustomSeedHexOrNull(dto.customSeedHex.trim())
                        else -> normalizedList.firstOrNull()
                    }
                    val activeNorm = when {
                        activeCandidate != null &&
                            normalizedList.any { normalizeCustomSeedHexOrNull(it) == activeCandidate } ->
                            activeCandidate
                        else ->
                            normalizeCustomSeedHexOrNull(normalizedList.first()) ?: normalizedList.first()
                    }
                    prefs[PrefKeys.ACTIVE_CUSTOM_SEED_HEX] = activeNorm
                }
                prefs.remove(PrefKeys.CUSTOM_SEED_HEX)
            }
        }
    }
}
