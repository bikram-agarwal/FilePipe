package dev.bikram.filepipe.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.domain.model.RunStatus
import dev.bikram.filepipe.domain.model.TriggerType

@Entity(
    tableName = "run_history",
    indices = [Index(value = ["ruleId"])]
)
data class RunHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long?,
    val ruleName: String,
    val triggeredBy: TriggerType,
    val startedAt: Long,
    val completedAt: Long? = null,
    val status: RunStatus,
    val totalFilesFound: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val cancelledUnprocessedCount: Int = 0,
    val totalFilesMoved: Int = 0,
    val totalFilesFailed: Int = 0,
    val errorMessage: String? = null,
    val isReversed: Boolean = false,
    @ColumnInfo(defaultValue = "MOVE")
    val operationMode: OperationMode = OperationMode.MOVE,
    @ColumnInfo(defaultValue = "[]")
    val copyCreatedDestFolderUris: List<String> = emptyList()
)

fun RunHistoryEntity.toDomain(): RunHistory = RunHistory(
    id = id,
    ruleId = ruleId,
    ruleName = ruleName,
    triggeredBy = triggeredBy,
    startedAt = startedAt,
    completedAt = completedAt,
    status = status,
    totalFilesFound = totalFilesFound,
    cancelledUnprocessedCount = cancelledUnprocessedCount,
    totalFilesMoved = totalFilesMoved,
    totalFilesFailed = totalFilesFailed,
    errorMessage = errorMessage,
    isReversed = isReversed,
    operationMode = operationMode,
    copyCreatedDestFolderUris = copyCreatedDestFolderUris
)
