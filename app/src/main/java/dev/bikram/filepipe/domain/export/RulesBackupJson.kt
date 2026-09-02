@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.bikram.filepipe.domain.export

import dev.bikram.filepipe.data.preferences.AppColorSource
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.data.preferences.SwipeAction
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.FileOrientation
import dev.bikram.filepipe.domain.model.FileUndoStatus
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RuleIcon
import dev.bikram.filepipe.domain.model.RuleSchedule
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.domain.model.ScheduleType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

/**
 * Backup JSON / Room DB schema version. Must match the **literal** `version` on [dev.bikram.filepipe.AppDatabase]
 * (`@Database`); Room KSP does not allow that annotation to reference this constant.
 */
const val APP_DATABASE_SCHEMA_VERSION = 12

/**
 * Root object for `filepipe_backup_*.json`.
 *
 * [version] defaults to [APP_DATABASE_SCHEMA_VERSION]. Room’s exported files under `app/schemas/` repeat the same
 * integer because the Room compiler generates them from `@Database`; you do not edit `database.version` there by hand.
 * Older backup files may still show a larger number (legacy backup-format counter); import does not branch on [version].
 */
@Serializable
data class AppBackup(
    val version: Int = APP_DATABASE_SCHEMA_VERSION,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val rules: List<RuleBackupDto>,
    val history: List<RunHistoryBackupDto> = emptyList(),
    val settings: SettingsBackupDto? = null,
)

/** Kept for backward compatibility - parsed the same as AppBackup */
typealias RulesBackup = AppBackup

@Serializable
data class RuleBackupDto(
    val name: String,
    val sourceFolderPaths: List<String>,
    val destinationFolderPath: String,
    val fileExtensions: List<String>,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    /** Null in backups written before the rule carried its own last-run time. */
    val lastRunStartedAt: Long? = null,
    val cardModeOverride: Boolean = false,
    val schedule: ScheduleBackupDto? = null,
    val conflictPolicy: String = ConflictPolicy.RENAME_SUFFIX.name,
    val operationMode: String = OperationMode.MOVE.name,
    val scanSubdirectories: Boolean = false,
    val recreateDestinationSubfolders: Boolean? = null,
    val suppressMissingSourceFolderCardWarning: Boolean = false,
    val iconKey: String = RuleIcon.DEFAULT.name,
    val iconEmoji: String? = null,
    val filenamePattern: String? = null,
    val minFileSizeBytes: Long? = null,
    val maxFileSizeBytes: Long? = null,
    val minAgeDays: Int? = null,
    val maxAgeDays: Int? = null,
    val excludePatterns: List<String> = emptyList(),
    val orientation: String? = null,
    val isRegexPattern: Boolean = false,
    val isExcludeRegexPattern: Boolean = false,
)

@Serializable
data class ScheduleBackupDto(
    val type: String,
    val dayOfWeek: Int? = null,
    val hour: Int,
    val minute: Int,
    val intervalHours: Int? = null,
    val usesStartTime: Boolean? = null,
)

@Serializable
data class RunHistoryBackupDto(
    val ruleName: String,
    /**
     * Index of the rule in the backup's [AppBackup.rules] list at export time. When set, restore maps
     * history to the correct rule even if several rules share the same [ruleName]. Null in older backups.
     */
    val ruleIndexInBackup: Int? = null,
    val triggeredBy: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val status: String,
    val totalFilesMoved: Int,
    val totalFilesFailed: Int,
    val errorMessage: String? = null,
    val isReversed: Boolean = false,
    val operationMode: String = "MOVE",
    val cancelledUnprocessedCount: Int = 0,
    val files: List<FileMovedBackupDto> = emptyList(),
    val copyCreatedDestFolderUris: List<String> = emptyList(),
)

@Serializable
data class FileMovedBackupDto(
    val fileName: String,
    val sourceUri: String,
    val destinationUri: String,
    val fileSizeBytes: Long,
    val relativeParentSegments: List<String> = emptyList(),
    val movedAt: Long,
    val success: Boolean,
    val skipped: Boolean = false,
    val errorMessage: String? = null,
    val undoStatus: String = FileUndoStatus.PENDING.name,
)

@Serializable
data class SettingsBackupDto(
    val themeMode: String,
    val useBlackTheme: Boolean? = null,
    val colorSource: String? = null,
    val themePaletteStyle: String? = null,
    val useMaterialYou: Boolean? = null,
    val exportFolderUri: String = "",
    val cloudExportFolderUri: String = "",
    val autoExportOnRuleChange: Boolean = false,
    val scheduledExportEnabled: Boolean = false,
    val logRetentionDays: Int = 30,
    val swipeStartToEnd: String = SwipeAction.EDIT.name,
    val swipeEndToStart: String = SwipeAction.DELETE.name,
    val rulesSortKey: String? = null,
    val rulesSortDirection: String? = null,
    val rulesCompactMode: Boolean = false,
    val historySortKey: String? = null,
    val historySortDirection: String? = null,
    val settingsCollapsedSectionKeys: List<String> = emptyList(),
    val bookmarkedFolders: List<String> = emptyList(),
    val hasSeenIntro: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val progressiveBlurEnabled: Boolean = true,
    /** Legacy import only; prefer [updateCheckSchedule]. */
    val autoCheckForUpdates: Boolean? = null,
    val updateCheckSchedule: String? = null,
    val notifyOnNewUpdates: Boolean = false,
    val saveUpdateApkToDownloads: Boolean = false,
    val useGradientBackground: Boolean = true,
    val shadingIntensity: Float? = null,
    val uiScale: Float? = null,
    @JsonNames("useFixedCardColors")
    val useEnhancedShading: Boolean = false,
    val customSeedHex: String? = null,
    val customSeedHexes: List<String>? = null,
    val activeCustomSeedHex: String? = null,
    val folderAccessMode: String? = null,
    val customFontPath: String = "",
    val customFontName: String = "",
)

private val jsonFormatter =
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        // Otherwise booleans and other fields that match DTO defaults (e.g. haptic on) are omitted from the file.
        encodeDefaults = true
    }

fun Rule.toBackupDto(): RuleBackupDto =
    RuleBackupDto(
        name = name,
        sourceFolderPaths = sourceFolderPaths,
        destinationFolderPath = destinationFolderPath,
        fileExtensions = fileExtensions,
        isEnabled = isEnabled,
        sortOrder = sortOrder,
        lastRunStartedAt = lastRunStartedAt,
        cardModeOverride = cardModeOverride,
        schedule = schedule?.toBackupDto(),
        conflictPolicy = conflictPolicy.name,
        operationMode = operationMode.name,
        scanSubdirectories = scanSubdirectories,
        recreateDestinationSubfolders = recreateDestinationSubfolders,
        suppressMissingSourceFolderCardWarning = suppressMissingSourceFolderCardWarning,
        iconKey = icon.name,
        iconEmoji = iconEmoji?.takeIf { it.isNotBlank() },
        filenamePattern = filenamePattern,
        minFileSizeBytes = minFileSizeBytes,
        maxFileSizeBytes = maxFileSizeBytes,
        minAgeDays = minAgeDays,
        maxAgeDays = maxAgeDays,
        excludePatterns = excludePatterns,
        orientation = orientation?.name,
        isRegexPattern = isRegexPattern,
        isExcludeRegexPattern = isExcludeRegexPattern,
    )

fun RuleSchedule.toBackupDto(): ScheduleBackupDto =
    ScheduleBackupDto(
        type = type.name,
        dayOfWeek = dayOfWeek,
        hour = hour,
        minute = minute,
        intervalHours = repeatInterval,
        usesStartTime = usesStartTime,
    )

fun RunHistory.toBackupDto(
    files: List<FileMoved> = emptyList(),
    ruleIndexInBackup: Int? = null,
): RunHistoryBackupDto =
    RunHistoryBackupDto(
        ruleName = ruleName,
        ruleIndexInBackup = ruleIndexInBackup,
        triggeredBy = triggeredBy.name,
        startedAt = startedAt,
        completedAt = completedAt,
        status = status.name,
        totalFilesMoved = totalFilesMoved,
        totalFilesFailed = totalFilesFailed,
        errorMessage = errorMessage,
        isReversed = isReversed,
        operationMode = operationMode.name,
        cancelledUnprocessedCount = cancelledUnprocessedCount,
        files = files.map { it.toBackupDto() },
        copyCreatedDestFolderUris = copyCreatedDestFolderUris,
    )

fun FileMoved.toBackupDto(): FileMovedBackupDto =
    FileMovedBackupDto(
        fileName = fileName,
        sourceUri = sourceUri,
        destinationUri = destinationUri,
        fileSizeBytes = fileSizeBytes,
        relativeParentSegments = relativeParentSegments,
        movedAt = movedAt,
        success = success,
        skipped = skipped,
        errorMessage = errorMessage,
        undoStatus = undoStatus.name,
    )

fun AppPreferences.toBackupDto(): SettingsBackupDto =
    SettingsBackupDto(
        themeMode = themeMode.name,
        useBlackTheme = useBlackTheme,
        colorSource = colorSource.name,
        themePaletteStyle = themePaletteStyle.name,
        useMaterialYou = if (colorSource == AppColorSource.MATERIAL_YOU) true else false,
        exportFolderUri = exportFolderUri,
        cloudExportFolderUri = cloudExportFolderUri,
        autoExportOnRuleChange = autoExportOnRuleChange,
        scheduledExportEnabled = scheduledExportEnabled,
        logRetentionDays = logRetentionDays,
        swipeStartToEnd = swipeStartToEnd.name,
        swipeEndToStart = swipeEndToStart.name,
        rulesSortKey = rulesSortKey.name,
        rulesSortDirection = rulesSortDirection.name,
        rulesCompactMode = rulesCompactMode,
        historySortKey = historySortKey.name,
        historySortDirection = historySortDirection.name,
        settingsCollapsedSectionKeys = settingsCollapsedSectionKeys,
        bookmarkedFolders = bookmarkedFolders,
        hasSeenIntro = hasSeenIntro,
        hapticFeedbackEnabled = hapticFeedbackEnabled,
        progressiveBlurEnabled = progressiveBlurEnabled,
        autoCheckForUpdates = null,
        updateCheckSchedule = updateCheckSchedule.name,
        notifyOnNewUpdates = notifyOnNewUpdates,
        saveUpdateApkToDownloads = saveUpdateApkToDownloads,
        useGradientBackground = useGradientBackground,
        shadingIntensity = shadingIntensity,
        uiScale = uiScale,
        useEnhancedShading = useEnhancedShading,
        customSeedHex = activeCustomSeedHex.takeIf { it.isNotBlank() },
        customSeedHexes = savedCustomSeedHexes.takeIf { it.isNotEmpty() },
        activeCustomSeedHex = activeCustomSeedHex.takeIf { it.isNotBlank() },
        folderAccessMode = folderAccessMode.name,
        customFontPath = customFontPath,
        customFontName = customFontName,
    )

fun RuleBackupDto.toDomain(): Rule =
    Rule(
        id = 0L,
        name = name,
        sourceFolderPaths = sourceFolderPaths,
        destinationFolderPath = destinationFolderPath,
        fileExtensions = fileExtensions,
        isEnabled = isEnabled,
        sortOrder = sortOrder,
        lastRunStartedAt = lastRunStartedAt,
        cardModeOverride = cardModeOverride,
        schedule = schedule?.toDomain(),
        conflictPolicy = runCatching { ConflictPolicy.valueOf(conflictPolicy) }.getOrDefault(ConflictPolicy.RENAME_SUFFIX),
        operationMode = runCatching { OperationMode.valueOf(operationMode) }.getOrDefault(OperationMode.MOVE),
        scanSubdirectories = scanSubdirectories,
        recreateDestinationSubfolders = recreateDestinationSubfolders ?: scanSubdirectories,
        suppressMissingSourceFolderCardWarning = suppressMissingSourceFolderCardWarning,
        icon = RuleIcon.fromStored(iconKey),
        iconEmoji = iconEmoji?.takeIf { it.isNotBlank() },
        filenamePattern = filenamePattern,
        minFileSizeBytes = minFileSizeBytes,
        maxFileSizeBytes = maxFileSizeBytes,
        minAgeDays = minAgeDays,
        maxAgeDays = maxAgeDays,
        excludePatterns = excludePatterns,
        orientation = orientation?.let { runCatching { FileOrientation.valueOf(it) }.getOrNull() },
        isRegexPattern = isRegexPattern,
        isExcludeRegexPattern = isExcludeRegexPattern,
    )

fun ScheduleBackupDto.toDomain(): RuleSchedule? {
    val scheduleType = runCatching { ScheduleType.valueOf(type) }.getOrNull() ?: return null
    return RuleSchedule(
        type = scheduleType,
        dayOfWeek = dayOfWeek,
        hour = hour,
        minute = minute,
        repeatInterval = intervalHours,
        usesStartTime = usesStartTime ?: true,
    )
}

fun buildAppBackupJson(
    rules: List<Rule>,
    history: List<Pair<RunHistory, List<FileMoved>>> = emptyList(),
    settings: AppPreferences? = null,
): String {
    val ruleIdToIndexInBackup = rules.mapIndexed { index, rule -> rule.id to index }.toMap()
    val backup =
        AppBackup(
            exportedAtMillis = System.currentTimeMillis(),
            rules = rules.map { it.toBackupDto() },
            history =
                history.map { (run, files) ->
                    run.toBackupDto(
                        files = files,
                        ruleIndexInBackup = run.ruleId?.let { ruleId -> ruleIdToIndexInBackup[ruleId] },
                    )
                },
            settings = settings?.toBackupDto(),
        )
    return jsonFormatter.encodeToString(backup)
}

/** Kept for backward compatibility */
fun buildRulesBackupJson(rules: List<Rule>): String = buildAppBackupJson(rules)

fun parseRulesBackupJson(text: String): Result<AppBackup> =
    runCatching {
        jsonFormatter.decodeFromString<AppBackup>(text)
    }

fun parseRulesBackupJson(inputStream: InputStream): Result<AppBackup> =
    runCatching {
        jsonFormatter.decodeFromStream<AppBackup>(inputStream)
    }
