package dev.bikram.filepipe.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RuleIcon
import dev.bikram.filepipe.domain.model.RuleSchedule
import dev.bikram.filepipe.domain.model.ScheduleType

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceFolderPaths: List<String>,
    val destinationFolderPath: String,
    val fileExtensions: List<String>,
    val isEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val scheduleType: ScheduleType? = null,
    val scheduleDayOfWeek: Int? = null,
    val scheduleHour: Int? = null,
    val scheduleMinute: Int? = null,
    val scheduleIntervalHours: Int? = null,
    val workManagerTag: String? = null,
    val conflictPolicy: String = ConflictPolicy.RENAME_SUFFIX.name,
    val operationMode: String = OperationMode.MOVE.name,
    val scanSubdirectories: Boolean = false,
    @ColumnInfo(name = "suppress_missing_source_folder_card", defaultValue = "0")
    val suppressMissingSourceFolderCardWarning: Boolean = false,
    val iconKey: String = RuleIcon.DEFAULT.name,
    val iconEmoji: String? = null,
    // Advanced filters (added in DB version 2)
    val filenamePattern: String? = null,
    val minFileSizeBytes: Long? = null,
    val maxFileSizeBytes: Long? = null,
    val minAgeDays: Int? = null,
    val maxAgeDays: Int? = null,
    @ColumnInfo(defaultValue = "[]")
    val excludePatterns: List<String> = emptyList()
)

fun RuleEntity.toDomain(): Rule = Rule(
    id = id,
    name = name,
    sourceFolderPaths = sourceFolderPaths,
    destinationFolderPath = destinationFolderPath,
    fileExtensions = fileExtensions,
    isEnabled = isEnabled,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    schedule = when {
        scheduleType == ScheduleType.EVERY_N_HOURS && scheduleIntervalHours != null ->
            RuleSchedule(
                type = scheduleType,
                dayOfWeek = null,
                hour = scheduleHour ?: 0,
                minute = scheduleMinute ?: 0,
                intervalHours = scheduleIntervalHours
            )
        scheduleType != null && scheduleHour != null && scheduleMinute != null ->
            RuleSchedule(
                type = scheduleType,
                dayOfWeek = scheduleDayOfWeek,
                hour = scheduleHour,
                minute = scheduleMinute,
                intervalHours = null
            )
        else -> null
    },
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

fun Rule.toEntity(): RuleEntity = RuleEntity(
    id = id,
    name = name,
    sourceFolderPaths = sourceFolderPaths,
    destinationFolderPath = destinationFolderPath,
    fileExtensions = fileExtensions,
    isEnabled = isEnabled,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    scheduleType = schedule?.type,
    scheduleDayOfWeek = schedule?.dayOfWeek,
    scheduleHour = schedule?.hour,
    scheduleMinute = schedule?.minute,
    scheduleIntervalHours = schedule?.intervalHours,
    workManagerTag = if (id != 0L) "rule_$id" else null,
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
