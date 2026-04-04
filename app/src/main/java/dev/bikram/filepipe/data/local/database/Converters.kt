package dev.bikram.filepipe.data.local.database

import androidx.room.TypeConverter
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.RunStatus
import dev.bikram.filepipe.domain.model.ScheduleType
import dev.bikram.filepipe.domain.model.TriggerType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = json.decodeFromString(value)

    @TypeConverter
    fun fromScheduleType(value: ScheduleType?): String? = value?.name

    @TypeConverter
    fun toScheduleType(value: String?): ScheduleType? = value?.let { ScheduleType.valueOf(it) }

    @TypeConverter
    fun fromTriggerType(value: TriggerType): String = value.name

    @TypeConverter
    fun toTriggerType(value: String): TriggerType = TriggerType.valueOf(value)

    @TypeConverter
    fun fromRunStatus(value: RunStatus): String = value.name

    @TypeConverter
    fun toRunStatus(value: String): RunStatus = RunStatus.valueOf(value)

    @TypeConverter
    fun fromOperationMode(value: OperationMode): String = value.name

    @TypeConverter
    fun toOperationMode(value: String): OperationMode = OperationMode.valueOf(value)
}
