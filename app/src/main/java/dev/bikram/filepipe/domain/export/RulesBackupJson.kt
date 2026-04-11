package dev.bikram.filepipe.domain.export

import dev.bikram.filepipe.data.preferences.AppColorSource
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.data.preferences.FolderAccessMode
import dev.bikram.filepipe.data.preferences.SwipeAction
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RuleIcon
import dev.bikram.filepipe.domain.model.RuleSchedule
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.domain.model.ScheduleType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Backup JSON / Room DB schema version. Must match the **literal** `version` on [dev.bikram.filepipe.AppDatabase]
 * (`@Database`); Room KSP does not allow that annotation to reference this constant.
 */
const val APP_DATABASE_SCHEMA_VERSION = 4

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
    val settings: SettingsBackupDto? = null
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
    val schedule: ScheduleBackupDto? = null,
    val conflictPolicy: String = ConflictPolicy.RENAME_SUFFIX.name,
    val operationMode: String = OperationMode.MOVE.name,
    val scanSubdirectories: Boolean = false,
    val suppressMissingSourceFolderCardWarning: Boolean = false,
    val iconKey: String = RuleIcon.DEFAULT.name,
    val iconEmoji: String? = null,
    val filenamePattern: String? = null,
    val minFileSizeBytes: Long? = null,
    val maxFileSizeBytes: Long? = null,
    val minAgeDays: Int? = null,
    val maxAgeDays: Int? = null,
    val excludePatterns: List<String> = emptyList()
)

@Serializable
data class ScheduleBackupDto(
    val type: String,
    val dayOfWeek: Int? = null,
    val hour: Int,
    val minute: Int,
    val intervalHours: Int? = null
)

@Serializable
data class RunHistoryBackupDto(
    val ruleName: String,
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
    val copyCreatedDestFolderUris: List<String> = emptyList()
)

@Serializable
data class FileMovedBackupDto(
    val fileName: String,
    val sourceUri: String,
    val destinationUri: String,
    val fileSizeBytes: Long,
    val movedAt: Long,
    val success: Boolean,
    val skipped: Boolean = false,
    val errorMessage: String? = null
)

@Serializable
data class SettingsBackupDto(
    val themeMode: String,
    val colorSource: String? = null,
    val themePaletteStyle: String? = null,
    val useMaterialYou: Boolean? = null,
    val exportFolderUri: String = "",
    val autoExportOnRuleChange: Boolean = false,
    val scheduledExportEnabled: Boolean = false,
    val logRetentionDays: Int = 30,
    val swipeStartToEnd: String = SwipeAction.EDIT.name,
    val swipeEndToStart: String = SwipeAction.DELETE.name,
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
    val useFixedCardColors: Boolean = false,
    val customSeedHex: String? = null,
    val customSeedHexes: List<String>? = null,
    val activeCustomSeedHex: String? = null,
    val folderAccessMode: String? = null
)

private val jsonFormatter = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    // Otherwise booleans and other fields that match DTO defaults (e.g. haptic on) are omitted from the file.
    encodeDefaults = true
}

fun Rule.toBackupDto(): RuleBackupDto = RuleBackupDto(
    name = name,
    sourceFolderPaths = sourceFolderPaths,
    destinationFolderPath = destinationFolderPath,
    fileExtensions = fileExtensions,
    isEnabled = isEnabled,
    sortOrder = sortOrder,
    schedule = schedule?.toBackupDto(),
    conflictPolicy = conflictPolicy.name,
    operationMode = operationMode.name,
    scanSubdirectories = scanSubdirectories,
    suppressMissingSourceFolderCardWarning = suppressMissingSourceFolderCardWarning,
    iconKey = icon.name,
    iconEmoji = iconEmoji?.takeIf { it.isNotBlank() },
    filenamePattern = filenamePattern,
    minFileSizeBytes = minFileSizeBytes,
    maxFileSizeBytes = maxFileSizeBytes,
    minAgeDays = minAgeDays,
    maxAgeDays = maxAgeDays,
    excludePatterns = excludePatterns
)

fun RuleSchedule.toBackupDto(): ScheduleBackupDto = ScheduleBackupDto(
    type = type.name,
    dayOfWeek = dayOfWeek,
    hour = hour,
    minute = minute,
    intervalHours = intervalHours
)

fun RunHistory.toBackupDto(files: List<FileMoved> = emptyList()): RunHistoryBackupDto = RunHistoryBackupDto(
    ruleName = ruleName,
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
    copyCreatedDestFolderUris = copyCreatedDestFolderUris
)

fun FileMoved.toBackupDto(): FileMovedBackupDto = FileMovedBackupDto(
    fileName = fileName,
    sourceUri = sourceUri,
    destinationUri = destinationUri,
    fileSizeBytes = fileSizeBytes,
    movedAt = movedAt,
    success = success,
    skipped = skipped,
    errorMessage = errorMessage
)

fun AppPreferences.toBackupDto(): SettingsBackupDto = SettingsBackupDto(
    themeMode = themeMode.name,
    colorSource = colorSource.name,
    themePaletteStyle = themePaletteStyle.name,
    useMaterialYou = if (colorSource == AppColorSource.MATERIAL_YOU) true else false,
    exportFolderUri = exportFolderUri,
    autoExportOnRuleChange = autoExportOnRuleChange,
    scheduledExportEnabled = scheduledExportEnabled,
    logRetentionDays = logRetentionDays,
    swipeStartToEnd = swipeStartToEnd.name,
    swipeEndToStart = swipeEndToStart.name,
    bookmarkedFolders = bookmarkedFolders,
    hasSeenIntro = hasSeenIntro,
    hapticFeedbackEnabled = hapticFeedbackEnabled,
    progressiveBlurEnabled = progressiveBlurEnabled,
    autoCheckForUpdates = null,
    updateCheckSchedule = updateCheckSchedule.name,
    notifyOnNewUpdates = notifyOnNewUpdates,
    saveUpdateApkToDownloads = saveUpdateApkToDownloads,
    useGradientBackground = useGradientBackground,
    useFixedCardColors = useFixedCardColors,
    customSeedHex = activeCustomSeedHex.takeIf { it.isNotBlank() },
    customSeedHexes = savedCustomSeedHexes.takeIf { it.isNotEmpty() },
    activeCustomSeedHex = activeCustomSeedHex.takeIf { it.isNotBlank() },
    folderAccessMode = folderAccessMode.name
)

fun RuleBackupDto.toDomain(): Rule = Rule(
    id = 0L,
    name = name,
    sourceFolderPaths = sourceFolderPaths,
    destinationFolderPath = destinationFolderPath,
    fileExtensions = fileExtensions,
    isEnabled = isEnabled,
    sortOrder = sortOrder,
    schedule = schedule?.toDomain(),
    conflictPolicy = runCatching { ConflictPolicy.valueOf(conflictPolicy) }.getOrDefault(ConflictPolicy.RENAME_SUFFIX),
    operationMode = runCatching { OperationMode.valueOf(operationMode) }.getOrDefault(OperationMode.MOVE),
    scanSubdirectories = scanSubdirectories,
    suppressMissingSourceFolderCardWarning = suppressMissingSourceFolderCardWarning,
    icon = RuleIcon.fromStored(iconKey),
    iconEmoji = iconEmoji?.takeIf { it.isNotBlank() },
    filenamePattern = filenamePattern,
    minFileSizeBytes = minFileSizeBytes,
    maxFileSizeBytes = maxFileSizeBytes,
    minAgeDays = minAgeDays,
    maxAgeDays = maxAgeDays,
    excludePatterns = excludePatterns
)

fun ScheduleBackupDto.toDomain(): RuleSchedule? {
    val scheduleType = runCatching { ScheduleType.valueOf(type) }.getOrNull() ?: return null
    return RuleSchedule(
        type = scheduleType,
        dayOfWeek = dayOfWeek,
        hour = hour,
        minute = minute,
        intervalHours = intervalHours
    )
}

fun buildAppBackupJson(
    rules: List<Rule>,
    history: List<Pair<RunHistory, List<FileMoved>>> = emptyList(),
    settings: AppPreferences? = null
): String {
    val backup = AppBackup(
        exportedAtMillis = System.currentTimeMillis(),
        rules = rules.map { it.toBackupDto() },
        history = history.map { (run, files) -> run.toBackupDto(files) },
        settings = settings?.toBackupDto()
    )
    return jsonFormatter.encodeToString(backup)
}

/** Kept for backward compatibility */
fun buildRulesBackupJson(rules: List<Rule>): String = buildAppBackupJson(rules)

fun parseRulesBackupJson(text: String): Result<AppBackup> = runCatching {
    jsonFormatter.decodeFromString<AppBackup>(text)
}
