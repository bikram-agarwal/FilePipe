package dev.bikram.filepipe.data.preferences

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_for_updates")
    val USE_GRADIENT_BACKGROUND = booleanPreferencesKey("use_gradient_background")
    val FIXED_CARD_COLORS = booleanPreferencesKey("fixed_card_colors")
    /** Legacy; migrated into [CUSTOM_SEED_HEX_LIST] + [ACTIVE_CUSTOM_SEED_HEX]. */
    val CUSTOM_SEED_HEX = stringPreferencesKey("custom_seed_hex")
    val CUSTOM_SEED_HEX_LIST = stringPreferencesKey("custom_seed_hex_list")
    val ACTIVE_CUSTOM_SEED_HEX = stringPreferencesKey("active_custom_seed_hex")
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
            autoCheckForUpdates = prefs[PrefKeys.AUTO_CHECK_UPDATES] ?: true,
            useGradientBackground = prefs[PrefKeys.USE_GRADIENT_BACKGROUND] ?: true,
            useFixedCardColors = prefs[PrefKeys.FIXED_CARD_COLORS] ?: false
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

    suspend fun setAutoCheckForUpdates(enabled: Boolean) {
        dataStore.edit { it[PrefKeys.AUTO_CHECK_UPDATES] = enabled }
    }

    suspend fun setUseGradientBackground(enabled: Boolean) {
        dataStore.edit { it[PrefKeys.USE_GRADIENT_BACKGROUND] = enabled }
    }

    suspend fun setUseFixedCardColors(enabled: Boolean) {
        dataStore.edit { it[PrefKeys.FIXED_CARD_COLORS] = enabled }
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
            prefs[PrefKeys.AUTO_CHECK_UPDATES] = dto.autoCheckForUpdates
            prefs[PrefKeys.USE_GRADIENT_BACKGROUND] = dto.useGradientBackground
            prefs[PrefKeys.FIXED_CARD_COLORS] = dto.useFixedCardColors

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
