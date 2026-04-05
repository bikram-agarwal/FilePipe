package dev.bikram.filepipe.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import dev.bikram.filepipe.data.local.dao.FileMovedDao
import dev.bikram.filepipe.data.local.dao.RunHistoryDao
import dev.bikram.filepipe.data.local.entity.FileMovedEntity
import dev.bikram.filepipe.data.local.entity.RunHistoryEntity
import dev.bikram.filepipe.data.local.entity.toDomain
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.export.FileMovedBackupDto
import dev.bikram.filepipe.domain.export.RunHistoryBackupDto
import dev.bikram.filepipe.domain.model.HistorySortDirection
import dev.bikram.filepipe.domain.model.HistorySortKey
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.domain.model.RunResult
import dev.bikram.filepipe.domain.model.RunStatus
import dev.bikram.filepipe.domain.model.TriggerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunHistoryRepository @Inject constructor(
    private val runHistoryDao: RunHistoryDao,
    private val fileMovedDao: FileMovedDao
) {
    fun getAllHistory(): Flow<List<RunHistory>> =
        runHistoryDao.getAllHistory().map { it.map { entity -> entity.toDomain() } }

    /** For rules list "last run" sort: map rule id to most recent run [RunHistory.startedAt]. */
    fun observeLastRunStartedAtByRuleId(): Flow<Map<Long, Long>> =
        runHistoryDao.observeLastStartedAtByRuleId().map { rows ->
            rows.associate { row -> row.ruleId to row.lastStartedAt }
        }

    suspend fun getAllHistoryOnce(): List<RunHistory> =
        runHistoryDao.getAllHistoryOnce().map { it.toDomain() }

    fun getHistoryForRule(ruleId: Long): Flow<List<RunHistory>> =
        runHistoryDao.getHistoryForRule(ruleId).map { it.map { entity -> entity.toDomain() } }

    fun getAllHistoryPaged(
        sortKey: HistorySortKey,
        sortDirection: HistorySortDirection,
    ): Flow<PagingData<RunHistory>> =
        Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
            when (sortKey) {
                HistorySortKey.LAST_RAN, HistorySortKey.MY_ORDER -> when (sortDirection) {
                    HistorySortDirection.DESCENDING -> runHistoryDao.getAllHistoryPagedLastRanDesc()
                    HistorySortDirection.ASCENDING -> runHistoryDao.getAllHistoryPagedLastRanAsc()
                }
                HistorySortKey.RULE_NAME -> when (sortDirection) {
                    HistorySortDirection.ASCENDING -> runHistoryDao.getAllHistoryPagedRuleNameAsc()
                    HistorySortDirection.DESCENDING -> runHistoryDao.getAllHistoryPagedRuleNameDesc()
                }
            }
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }

    fun getHistoryForRulePaged(
        ruleId: Long,
        sortKey: HistorySortKey,
        sortDirection: HistorySortDirection,
    ): Flow<PagingData<RunHistory>> =
        Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
            when (sortKey) {
                HistorySortKey.LAST_RAN, HistorySortKey.MY_ORDER -> when (sortDirection) {
                    HistorySortDirection.DESCENDING -> runHistoryDao.getHistoryForRulePagedLastRanDesc(ruleId)
                    HistorySortDirection.ASCENDING -> runHistoryDao.getHistoryForRulePagedLastRanAsc(ruleId)
                }
                HistorySortKey.RULE_NAME -> when (sortDirection) {
                    HistorySortDirection.ASCENDING -> runHistoryDao.getHistoryForRulePagedRuleNameAsc(ruleId)
                    HistorySortDirection.DESCENDING -> runHistoryDao.getHistoryForRulePagedRuleNameDesc(ruleId)
                }
            }
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }

    suspend fun getHistoryById(id: Long): RunHistory? =
        runHistoryDao.getHistoryById(id)?.toDomain()

    fun getFilesForRun(runHistoryId: Long): Flow<List<FileMoved>> =
        fileMovedDao.getFilesForRun(runHistoryId).map { it.map { entity -> entity.toDomain() } }

    suspend fun getFilesForRunOnce(runHistoryId: Long): List<FileMoved> =
        fileMovedDao.getFilesForRunOnce(runHistoryId).map { it.toDomain() }

    suspend fun startRun(
        ruleId: Long?,
        ruleName: String,
        triggerType: TriggerType,
        operationMode: OperationMode
    ): Long =
        runHistoryDao.insertHistory(
            RunHistoryEntity(
                ruleId = ruleId,
                ruleName = ruleName,
                triggeredBy = triggerType,
                startedAt = System.currentTimeMillis(),
                status = RunStatus.IN_PROGRESS,
                operationMode = operationMode
            )
        )

    suspend fun completeRun(result: RunResult) {
        val history = runHistoryDao.getHistoryById(result.historyId) ?: return
        runHistoryDao.updateHistory(
            history.copy(
                completedAt = result.completedAt,
                status = result.status,
                totalFilesFound = result.filesMoved.size,
                cancelledUnprocessedCount = 0,
                totalFilesMoved = result.totalMoved,
                totalFilesFailed = result.totalFailed,
                copyCreatedDestFolderUris = result.copyCreatedDestFolderUris
            )
        )
        fileMovedDao.insertFilesMoved(
            result.filesMoved.map { fileMoved ->
                FileMovedEntity(
                    runHistoryId = result.historyId,
                    fileName = fileMoved.fileName,
                    sourceUri = fileMoved.sourceUri,
                    destinationUri = fileMoved.destinationUri,
                    fileSizeBytes = fileMoved.fileSizeBytes,
                    movedAt = fileMoved.movedAt,
                    success = fileMoved.success,
                    skipped = fileMoved.skipped,
                    errorMessage = fileMoved.errorMessage
                )
            }
        )
    }

    suspend fun markRunFailed(historyId: Long, errorMessage: String) {
        val history = runHistoryDao.getHistoryById(historyId) ?: return
        runHistoryDao.updateHistory(
            history.copy(
                completedAt = System.currentTimeMillis(),
                status = RunStatus.FAILED,
                errorMessage = errorMessage
            )
        )
    }

    suspend fun finishRunUserCancelled(historyId: Long, totalPlanned: Int) {
        val history = runHistoryDao.getHistoryById(historyId) ?: return
        runHistoryDao.updateHistory(
            history.copy(
                completedAt = System.currentTimeMillis(),
                status = RunStatus.CANCELLED,
                totalFilesFound = totalPlanned,
                cancelledUnprocessedCount = totalPlanned,
                totalFilesMoved = 0,
                totalFilesFailed = 0,
                errorMessage = null
            )
        )
    }

    suspend fun completeRunUserCancelledPartial(result: RunResult, totalPlanned: Int) {
        val history = runHistoryDao.getHistoryById(result.historyId) ?: return
        val unprocessed = (totalPlanned - result.filesMoved.size).coerceAtLeast(0)
        runHistoryDao.updateHistory(
            history.copy(
                completedAt = result.completedAt,
                status = RunStatus.CANCELLED,
                totalFilesFound = totalPlanned,
                cancelledUnprocessedCount = unprocessed,
                totalFilesMoved = result.totalMoved,
                totalFilesFailed = result.totalFailed,
                errorMessage = null,
                copyCreatedDestFolderUris = result.copyCreatedDestFolderUris
            )
        )
        fileMovedDao.insertFilesMoved(
            result.filesMoved.map { fileMoved ->
                FileMovedEntity(
                    runHistoryId = result.historyId,
                    fileName = fileMoved.fileName,
                    sourceUri = fileMoved.sourceUri,
                    destinationUri = fileMoved.destinationUri,
                    fileSizeBytes = fileMoved.fileSizeBytes,
                    movedAt = fileMoved.movedAt,
                    success = fileMoved.success,
                    skipped = fileMoved.skipped,
                    errorMessage = fileMoved.errorMessage
                )
            }
        )
    }

    suspend fun markRunReversed(historyId: Long) {
        val history = runHistoryDao.getHistoryById(historyId) ?: return
        runHistoryDao.updateHistory(
            history.copy(isReversed = true, status = RunStatus.UNDONE)
        )
    }

    suspend fun deleteHistoryById(historyId: Long) {
        runHistoryDao.deleteHistoryById(historyId)
    }

    suspend fun pruneOldHistory(retentionDays: Int) {
        if (retentionDays <= 0) return
        val threshold = System.currentTimeMillis() - (retentionDays * 86_400_000L)
        runHistoryDao.deleteHistoryOlderThan(threshold)
    }

    suspend fun clearAllHistory() {
        runHistoryDao.deleteAllHistory()
    }

    /**
     * Replaces all history with backup rows. [ruleNameToId] maps rule name to the current DB rule id
     * (first match if duplicate names). Clears existing history first.
     */
    suspend fun replaceHistoryFromBackup(
        backupRuns: List<RunHistoryBackupDto>,
        ruleNameToId: Map<String, Long>
    ) {
        clearAllHistory()
        for (dto in backupRuns) {
            val triggeredBy = runCatching { TriggerType.valueOf(dto.triggeredBy) }.getOrDefault(TriggerType.MANUAL)
            val status = runCatching { RunStatus.valueOf(dto.status) }.getOrDefault(RunStatus.SUCCESS)
            val operationMode =
                runCatching { OperationMode.valueOf(dto.operationMode) }.getOrDefault(OperationMode.MOVE)
            val filesFound = dto.files.size.coerceAtLeast(dto.totalFilesMoved + dto.totalFilesFailed)
            val entity = RunHistoryEntity(
                id = 0L,
                ruleId = ruleNameToId[dto.ruleName],
                ruleName = dto.ruleName,
                triggeredBy = triggeredBy,
                startedAt = dto.startedAt,
                completedAt = dto.completedAt,
                status = status,
                totalFilesFound = filesFound,
                cancelledUnprocessedCount = dto.cancelledUnprocessedCount,
                totalFilesMoved = dto.totalFilesMoved,
                totalFilesFailed = dto.totalFilesFailed,
                errorMessage = dto.errorMessage,
                isReversed = dto.isReversed,
                operationMode = operationMode,
                copyCreatedDestFolderUris = dto.copyCreatedDestFolderUris
            )
            val newHistoryId = runHistoryDao.insertHistory(entity)
            if (dto.files.isNotEmpty()) {
                fileMovedDao.insertFilesMoved(dto.files.map { it.toEntity(newHistoryId) })
            }
        }
    }

    private fun FileMovedBackupDto.toEntity(runHistoryId: Long): FileMovedEntity =
        FileMovedEntity(
            id = 0L,
            runHistoryId = runHistoryId,
            fileName = fileName,
            sourceUri = sourceUri,
            destinationUri = destinationUri,
            fileSizeBytes = fileSizeBytes,
            movedAt = movedAt,
            success = success,
            skipped = skipped,
            errorMessage = errorMessage
        )
}
