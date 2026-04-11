package dev.bikram.filepipe.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.bikram.filepipe.domain.model.FileMoved

@Entity(
    tableName = "files_moved",
    foreignKeys = [
        ForeignKey(
            entity = RunHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["runHistoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("runHistoryId")]
)
data class FileMovedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runHistoryId: Long,
    val fileName: String,
    val sourceUri: String,
    val destinationUri: String,
    val fileSizeBytes: Long,
    val relativeParentSegments: List<String> = emptyList(),
    val movedAt: Long,
    val success: Boolean,
    val skipped: Boolean = false,
    val errorMessage: String? = null
)

fun FileMovedEntity.toDomain(): FileMoved = FileMoved(
    id = id,
    runHistoryId = runHistoryId,
    fileName = fileName,
    sourceUri = sourceUri,
    destinationUri = destinationUri,
    fileSizeBytes = fileSizeBytes,
    relativeParentSegments = relativeParentSegments,
    movedAt = movedAt,
    success = success,
    skipped = skipped,
    errorMessage = errorMessage
)

fun FileMoved.toEntity(runHistoryId: Long): FileMovedEntity = FileMovedEntity(
    id = id,
    runHistoryId = runHistoryId,
    fileName = fileName,
    sourceUri = sourceUri,
    destinationUri = destinationUri,
    fileSizeBytes = fileSizeBytes,
    relativeParentSegments = relativeParentSegments,
    movedAt = movedAt,
    success = success,
    skipped = skipped,
    errorMessage = errorMessage
)
