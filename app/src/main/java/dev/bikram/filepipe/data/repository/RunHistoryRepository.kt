package dev.bikram.filepipe.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import dev.bikram.filepipe.AppDatabase
import dev.bikram.filepipe.data.local.dao.FileMovedDao
import dev.bikram.filepipe.data.local.dao.RuleDao
import dev.bikram.filepipe.data.local.dao.RunHistoryDao
import dev.bikram.filepipe.data.local.database.Converters
import dev.bikram.filepipe.data.local.entity.FileMovedEntity
import dev.bikram.filepipe.data.local.entity.RunHistoryEntity
import dev.bikram.filepipe.data.local.entity.toDomain
import dev.bikram.filepipe.data.local.entity.toEntity
import dev.bikram.filepipe.domain.export.FileMovedBackupDto
import dev.bikram.filepipe.domain.export.RunHistoryBackupDto
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.FileUndoStatus
import dev.bikram.filepipe.domain.model.HistorySortDirection
import dev.bikram.filepipe.domain.model.HistorySortKey
import dev.bikram.filepipe.domain.model.HistoryStatusFilter
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.domain.model.RunResult
import dev.bikram.filepipe.domain.model.RunStatus
import dev.bikram.filepipe.domain.model.TriggerType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunHistoryRepository
    @Inject
    constructor(
        private val appDatabase: AppDatabase,
        private val ruleDao: RuleDao,
        private val runHistoryDao: RunHistoryDao,
        private val fileMovedDao: FileMovedDao,
    ) {
        fun getAllHistory(): Flow<List<RunHistory>> = runHistoryDao.getAllHistory().map { it.map { entity -> entity.toDomain() } }

        fun observeHasAnyHistory(): Flow<Boolean> = runHistoryDao.observeHistoryCount().map { count -> count > 0 }

        fun observeAvailableHistoryStatusFilters(ruleId: Long?): Flow<Set<HistoryStatusFilter>> =
            runHistoryDao
                .observeHistoryStatusAvailability(ruleId)
                .map { availability ->
                    buildSet {
                        add(HistoryStatusFilter.ALL)
                        if (availability.hasSuccess) add(HistoryStatusFilter.SUCCESS)
                        if (availability.hasFailed) add(HistoryStatusFilter.FAILED)
                        if (availability.hasPartial) add(HistoryStatusFilter.PARTIAL)
                        if (availability.hasNoChanges) add(HistoryStatusFilter.NO_CHANGES)
                        if (availability.hasCancelled) add(HistoryStatusFilter.CANCELLED)
                        if (availability.hasUndone) add(HistoryStatusFilter.UNDONE)
                        if (availability.hasPartialUndone) add(HistoryStatusFilter.PARTIAL_UNDONE)
                    }
                }

        suspend fun getAllHistoryOnce(): List<RunHistory> = runHistoryDao.getAllHistoryOnce().map { it.toDomain() }

        suspend fun getBackupSnapshot(): BackupSnapshot =
            appDatabase.withTransaction {
                BackupSnapshot(
                    rules = ruleDao.getAllRulesOrderedBySortOrder().map { ruleEntity -> ruleEntity.toDomain() },
                    historyWithFiles =
                        runHistoryDao.getAllHistoryOnce().map { historyEntity ->
                            historyEntity.toDomain() to
                                fileMovedDao
                                    .getFilesForRunOnce(historyEntity.id)
                                    .map { fileMovedEntity -> fileMovedEntity.toDomain() }
                        },
                )
            }

        suspend fun getRestoreRollbackSnapshot(): RestoreRollbackSnapshot =
            appDatabase.withTransaction {
                RestoreRollbackSnapshot(
                    rulesIncludingTrash =
                        ruleDao
                            .getAllRulesIncludingTrashed()
                            .map { ruleEntity -> ruleEntity.toDomain() },
                    backupSnapshot =
                        BackupSnapshot(
                            rules =
                                ruleDao
                                    .getAllRulesOrderedBySortOrder()
                                    .map { ruleEntity -> ruleEntity.toDomain() },
                            historyWithFiles =
                                runHistoryDao.getAllHistoryOnce().map { historyEntity ->
                                    historyEntity.toDomain() to
                                        fileMovedDao
                                            .getFilesForRunOnce(historyEntity.id)
                                            .map { fileMovedEntity -> fileMovedEntity.toDomain() }
                                },
                        ),
                )
            }

        suspend fun restoreSnapshotAtomically(
            rulesIncludingTrash: List<Rule>,
            snapshot: BackupSnapshot,
        ) {
            appDatabase.withTransaction {
                runHistoryDao.deleteAllHistory()
                ruleDao.deleteAllRules()
                rulesIncludingTrash.forEach { rule ->
                    ruleDao.upsertRule(rule.toEntity())
                }
                snapshot.historyWithFiles.forEach { (history, files) ->
                    runHistoryDao.insertHistory(
                        RunHistoryEntity(
                            id = history.id,
                            ruleId = history.ruleId,
                            ruleName = history.ruleName,
                            triggeredBy = history.triggeredBy,
                            startedAt = history.startedAt,
                            completedAt = history.completedAt,
                            status = history.status,
                            totalFilesFound = history.totalFilesFound,
                            cancelledUnprocessedCount = history.cancelledUnprocessedCount,
                            totalFilesMoved = history.totalFilesMoved,
                            totalFilesFailed = history.totalFilesFailed,
                            errorMessage = history.errorMessage,
                            isReversed = history.isReversed,
                            operationMode = history.operationMode,
                            copyCreatedDestFolderUris = history.copyCreatedDestFolderUris,
                        ),
                    )
                    if (files.isNotEmpty()) {
                        fileMovedDao.insertFilesMoved(
                            files.map { fileMoved -> fileMoved.toEntity(history.id) },
                        )
                    }
                }
            }
        }

        fun getHistoryForRule(ruleId: Long): Flow<List<RunHistory>> = runHistoryDao.getHistoryForRule(ruleId).map { it.map { entity -> entity.toDomain() } }

        fun getAllHistoryPaged(
            sortKey: HistorySortKey,
            sortDirection: HistorySortDirection,
        ): Flow<PagingData<RunHistory>> =
            Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                when (sortKey) {
                    HistorySortKey.LAST_RAN, HistorySortKey.MY_ORDER -> {
                        when (sortDirection) {
                            HistorySortDirection.DESCENDING -> runHistoryDao.getAllHistoryPagedLastRanDesc()
                            HistorySortDirection.ASCENDING -> runHistoryDao.getAllHistoryPagedLastRanAsc()
                        }
                    }

                    HistorySortKey.RULE_NAME -> {
                        when (sortDirection) {
                            HistorySortDirection.ASCENDING -> runHistoryDao.getAllHistoryPagedRuleNameAsc()
                            HistorySortDirection.DESCENDING -> runHistoryDao.getAllHistoryPagedRuleNameDesc()
                        }
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
                    HistorySortKey.LAST_RAN, HistorySortKey.MY_ORDER -> {
                        when (sortDirection) {
                            HistorySortDirection.DESCENDING -> runHistoryDao.getHistoryForRulePagedLastRanDesc(ruleId)
                            HistorySortDirection.ASCENDING -> runHistoryDao.getHistoryForRulePagedLastRanAsc(ruleId)
                        }
                    }

                    HistorySortKey.RULE_NAME -> {
                        when (sortDirection) {
                            HistorySortDirection.ASCENDING -> runHistoryDao.getHistoryForRulePagedRuleNameAsc(ruleId)
                            HistorySortDirection.DESCENDING -> runHistoryDao.getHistoryForRulePagedRuleNameDesc(ruleId)
                        }
                    }
                }
            }.flow.map { pagingData -> pagingData.map { it.toDomain() } }

        fun getHistoryPaged(
            ruleId: Long?,
            statusFilter: HistoryStatusFilter,
            groupByRule: Boolean,
            groupByStatus: Boolean,
            sortKey: HistorySortKey,
            sortDirection: HistorySortDirection,
        ): Flow<PagingData<RunHistory>> {
            val query =
                buildHistoryQuery(
                    selectClause = "SELECT *",
                    ruleId = ruleId,
                    statusFilter = statusFilter,
                    groupByRule = groupByRule,
                    groupByStatus = groupByStatus,
                    sortKey = sortKey,
                    sortDirection = sortDirection,
                )
            return Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                runHistoryDao.getHistoryPaged(query)
            }.flow.map { pagingData -> pagingData.map { entity -> entity.toDomain() } }
        }

        fun observeVisibleHistoryCount(
            ruleId: Long?,
            statusFilter: HistoryStatusFilter,
        ): Flow<Int> {
            val (whereClause, arguments) = buildHistoryWhereClause(ruleId, statusFilter)
            return runHistoryDao.observeFilteredHistoryCount(
                SimpleSQLiteQuery(
                    "SELECT COUNT(*) FROM run_history $whereClause",
                    arguments,
                ),
            )
        }

        fun observeVisibleHistoryIds(
            ruleId: Long?,
            statusFilter: HistoryStatusFilter,
            groupByRule: Boolean,
            groupByStatus: Boolean,
            sortKey: HistorySortKey,
            sortDirection: HistorySortDirection,
        ): Flow<List<Long>> =
            runHistoryDao.observeHistoryIds(
                buildHistoryQuery(
                    selectClause = "SELECT id",
                    ruleId = ruleId,
                    statusFilter = statusFilter,
                    groupByRule = groupByRule,
                    groupByStatus = groupByStatus,
                    sortKey = sortKey,
                    sortDirection = sortDirection,
                ),
            )

        fun observeHistoryRuleCounts(
            ruleId: Long?,
            statusFilter: HistoryStatusFilter,
        ): Flow<Map<String, Int>> {
            val (whereClause, arguments) = buildHistoryWhereClause(ruleId, statusFilter)
            val query =
                SimpleSQLiteQuery(
                    "SELECT ruleName, COUNT(*) AS count FROM run_history $whereClause GROUP BY ruleName",
                    arguments,
                )
            return runHistoryDao
                .observeHistoryRuleCounts(query)
                .map { counts -> counts.associate { count -> count.ruleName to count.count } }
        }

        fun observeHistoryStatusSectionCounts(
            ruleId: Long?,
            statusFilter: HistoryStatusFilter,
        ): Flow<Map<Int, Int>> {
            val (whereClause, arguments) = buildHistoryWhereClause(ruleId, statusFilter)
            val query =
                SimpleSQLiteQuery(
                    """
                    SELECT $HISTORY_STATUS_SECTION_SQL AS section, COUNT(*) AS count
                    FROM run_history
                    $whereClause
                    GROUP BY $HISTORY_STATUS_SECTION_SQL
                    """.trimIndent(),
                    arguments,
                )
            return runHistoryDao
                .observeHistoryStatusSectionCounts(query)
                .map { counts -> counts.associate { count -> count.section to count.count } }
        }

        suspend fun getHistoryById(id: Long): RunHistory? = runHistoryDao.getHistoryById(id)?.toDomain()

        /**
         * Destination folders that any recorded run of [ruleId] created, flattened across runs. The
         * column stores a JSON list per run, so the rows are decoded and unioned.
         */
        suspend fun getCreatedDestFolderUrisForRule(ruleId: Long): List<String> =
            runHistoryDao
                .getCreatedDestFolderUrisForRule(ruleId)
                .flatMap { encoded -> Converters().toStringList(encoded) }
                .distinct()

        fun observeHistoryById(id: Long): Flow<RunHistory?> = runHistoryDao.observeHistoryById(id).map { entity -> entity?.toDomain() }

        fun getFilesForRun(runHistoryId: Long): Flow<List<FileMoved>> = fileMovedDao.getFilesForRun(runHistoryId).map { it.map { entity -> entity.toDomain() } }

        fun getFilesForRunPaged(runHistoryId: Long): Flow<PagingData<FileMoved>> =
            Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                fileMovedDao.getFilesForRunPaged(runHistoryId)
            }.flow.map { pagingData -> pagingData.map { entity -> entity.toDomain() } }

        fun observeFileCountForRun(runHistoryId: Long): Flow<Int> = fileMovedDao.observeFileCountForRun(runHistoryId)

        suspend fun getFilesForRunOnce(runHistoryId: Long): List<FileMoved> = fileMovedDao.getFilesForRunOnce(runHistoryId).map { it.toDomain() }

        suspend fun startRun(
            ruleId: Long?,
            ruleName: String,
            triggerType: TriggerType,
            operationMode: OperationMode,
        ): Long =
            runHistoryDao.insertHistory(
                RunHistoryEntity(
                    ruleId = ruleId,
                    ruleName = ruleName,
                    triggeredBy = triggerType,
                    startedAt = System.currentTimeMillis(),
                    status = RunStatus.IN_PROGRESS,
                    operationMode = operationMode,
                ),
            )

        suspend fun completeRun(result: RunResult) {
            appDatabase.withTransaction {
                val history = runHistoryDao.getHistoryById(result.historyId) ?: return@withTransaction
                runHistoryDao.updateHistory(
                    history.copy(
                        completedAt = result.completedAt,
                        status = result.status,
                        totalFilesFound = result.filesMoved.size,
                        cancelledUnprocessedCount = 0,
                        totalFilesMoved = result.totalMoved,
                        totalFilesFailed = result.totalFailed,
                        copyCreatedDestFolderUris = result.copyCreatedDestFolderUris,
                    ),
                )
                fileMovedDao.insertFilesMoved(
                    result.filesMoved.map { fileMoved ->
                        FileMovedEntity(
                            runHistoryId = result.historyId,
                            fileName = fileMoved.fileName,
                            sourceUri = fileMoved.sourceUri,
                            destinationUri = fileMoved.destinationUri,
                            fileSizeBytes = fileMoved.fileSizeBytes,
                            relativeParentSegments = fileMoved.relativeParentSegments,
                            movedAt = fileMoved.movedAt,
                            success = fileMoved.success,
                            skipped = fileMoved.skipped,
                            errorMessage = fileMoved.errorMessage,
                            undoStatus = fileMoved.undoStatus,
                        )
                    },
                )
            }
        }

        suspend fun markRunFailed(
            historyId: Long,
            errorMessage: String,
        ) {
            val history = runHistoryDao.getHistoryById(historyId) ?: return
            runHistoryDao.updateHistory(
                history.copy(
                    completedAt = System.currentTimeMillis(),
                    status = RunStatus.FAILED,
                    errorMessage = errorMessage,
                ),
            )
        }

        suspend fun reconcileInterruptedRuns(
            startedBefore: Long,
            errorMessage: String,
        ): Int =
            runHistoryDao.markInterruptedRunsFailed(
                startedBefore = startedBefore,
                completedAt = System.currentTimeMillis(),
                errorMessage = errorMessage,
            )

        suspend fun finishRunUserCancelled(
            historyId: Long,
            totalPlanned: Int,
        ) {
            withContext(NonCancellable) {
                val history = runHistoryDao.getHistoryById(historyId) ?: return@withContext
                runHistoryDao.updateHistory(
                    history.copy(
                        completedAt = System.currentTimeMillis(),
                        status = RunStatus.CANCELLED,
                        totalFilesFound = totalPlanned,
                        cancelledUnprocessedCount = totalPlanned,
                        totalFilesMoved = 0,
                        totalFilesFailed = 0,
                        errorMessage = null,
                    ),
                )
            }
        }

        suspend fun completeRunUserCancelledPartial(
            result: RunResult,
            totalPlanned: Int,
        ) {
            withContext(NonCancellable) {
                appDatabase.withTransaction {
                    val history = runHistoryDao.getHistoryById(result.historyId) ?: return@withTransaction
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
                            copyCreatedDestFolderUris = result.copyCreatedDestFolderUris,
                        ),
                    )
                    fileMovedDao.insertFilesMoved(
                        result.filesMoved.map { fileMoved ->
                            FileMovedEntity(
                                runHistoryId = result.historyId,
                                fileName = fileMoved.fileName,
                                sourceUri = fileMoved.sourceUri,
                                destinationUri = fileMoved.destinationUri,
                                fileSizeBytes = fileMoved.fileSizeBytes,
                                relativeParentSegments = fileMoved.relativeParentSegments,
                                movedAt = fileMoved.movedAt,
                                success = fileMoved.success,
                                skipped = fileMoved.skipped,
                                errorMessage = fileMoved.errorMessage,
                                undoStatus = fileMoved.undoStatus,
                            )
                        },
                    )
                }
            }
        }

        suspend fun markRunReversed(historyId: Long) {
            val history = runHistoryDao.getHistoryById(historyId) ?: return
            runHistoryDao.updateHistory(
                history.copy(isReversed = true, status = RunStatus.UNDONE),
            )
        }

        suspend fun markRunPartiallyUndone(historyId: Long) {
            val history = runHistoryDao.getHistoryById(historyId) ?: return
            runHistoryDao.updateHistory(
                history.copy(isReversed = false, status = RunStatus.PARTIAL_UNDONE),
            )
        }

        suspend fun markFileUndoStatus(
            fileMovedId: Long,
            status: FileUndoStatus,
        ) {
            fileMovedDao.updateUndoStatus(fileMovedId, status)
        }

        suspend fun deleteHistoryById(historyId: Long) {
            runHistoryDao.deleteHistoryById(historyId)
        }

        suspend fun deleteFilteredHistory(
            ruleId: Long?,
            statusFilter: HistoryStatusFilter,
        ) {
            runHistoryDao.deleteFilteredHistory(ruleId, statusFilter.name)
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
         * Replaces all history with backup rows. [resolveRuleId] returns the DB rule id for each row
         * ([RunHistoryBackupDto.ruleIndexInBackup] preferred when present; else match by name).
         */
        suspend fun replaceHistoryFromBackup(
            backupRuns: List<RunHistoryBackupDto>,
            resolveRuleId: (RunHistoryBackupDto) -> Long?,
        ) {
            clearAllHistory()
            for (dto in backupRuns) {
                val triggeredBy = runCatching { TriggerType.valueOf(dto.triggeredBy) }.getOrDefault(TriggerType.MANUAL)
                val status = runCatching { RunStatus.valueOf(dto.status) }.getOrDefault(RunStatus.SUCCESS)
                val operationMode =
                    runCatching { OperationMode.valueOf(dto.operationMode) }.getOrDefault(OperationMode.MOVE)
                val filesFound = dto.files.size.coerceAtLeast(dto.totalFilesMoved + dto.totalFilesFailed)
                val entity =
                    RunHistoryEntity(
                        id = 0L,
                        ruleId = resolveRuleId(dto),
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
                        copyCreatedDestFolderUris = dto.copyCreatedDestFolderUris,
                    )
                val newHistoryId = runHistoryDao.insertHistory(entity)
                if (dto.files.isNotEmpty()) {
                    fileMovedDao.insertFilesMoved(
                        dto.files.map {
                            it.toEntity(
                                runHistoryId = newHistoryId,
                                runStatus = status,
                                isRunReversed = dto.isReversed,
                            )
                        },
                    )
                }
            }
        }

        private fun FileMovedBackupDto.toEntity(
            runHistoryId: Long,
            runStatus: RunStatus,
            isRunReversed: Boolean,
        ): FileMovedEntity {
            val backedUpUndoStatus =
                runCatching { FileUndoStatus.valueOf(undoStatus) }
                    .getOrDefault(FileUndoStatus.PENDING)
            val restoredUndoStatus =
                when {
                    runStatus == RunStatus.UNDONE || isRunReversed -> {
                        FileUndoStatus.UNDONE
                    }

                    else -> {
                        backedUpUndoStatus
                    }
                }
            return FileMovedEntity(
                id = 0L,
                runHistoryId = runHistoryId,
                fileName = fileName,
                sourceUri = sourceUri,
                destinationUri = destinationUri,
                fileSizeBytes = fileSizeBytes,
                relativeParentSegments = relativeParentSegments,
                movedAt = movedAt,
                success = success,
                skipped = skipped,
                errorMessage = errorMessage,
                undoStatus = restoredUndoStatus,
            )
        }

        private fun buildHistoryQuery(
            selectClause: String,
            ruleId: Long?,
            statusFilter: HistoryStatusFilter,
            groupByRule: Boolean,
            groupByStatus: Boolean,
            sortKey: HistorySortKey,
            sortDirection: HistorySortDirection,
        ): SimpleSQLiteQuery {
            val (whereClause, arguments) = buildHistoryWhereClause(ruleId, statusFilter)
            val orderTerms = mutableListOf<String>()
            if (groupByStatus) {
                orderTerms += "$HISTORY_STATUS_SECTION_SQL ASC"
            }
            if (groupByRule) {
                if (sortKey == HistorySortKey.RULE_NAME) {
                    if (sortDirection == HistorySortDirection.DESCENDING) {
                        orderTerms += "ruleName COLLATE NOCASE DESC"
                        orderTerms += "ruleName DESC"
                    } else {
                        orderTerms += "ruleName COLLATE NOCASE ASC"
                        orderTerms += "ruleName ASC"
                    }
                } else if (sortDirection == HistorySortDirection.DESCENDING) {
                    orderTerms +=
                        "(SELECT MAX(grouped.startedAt) FROM run_history AS grouped " +
                        "WHERE grouped.ruleName = run_history.ruleName) DESC"
                    orderTerms += "ruleName COLLATE NOCASE ASC"
                    orderTerms += "ruleName ASC"
                } else {
                    orderTerms +=
                        "(SELECT MIN(grouped.startedAt) FROM run_history AS grouped " +
                        "WHERE grouped.ruleName = run_history.ruleName) ASC"
                    orderTerms += "ruleName COLLATE NOCASE ASC"
                    orderTerms += "ruleName ASC"
                }
            }
            when (sortKey) {
                HistorySortKey.LAST_RAN, HistorySortKey.MY_ORDER -> {
                    orderTerms +=
                        if (sortDirection == HistorySortDirection.DESCENDING) {
                            "startedAt DESC"
                        } else {
                            "startedAt ASC"
                        }
                }

                HistorySortKey.RULE_NAME -> {
                    if (!groupByRule) {
                        orderTerms +=
                            if (sortDirection == HistorySortDirection.DESCENDING) {
                                "ruleName COLLATE NOCASE DESC"
                            } else {
                                "ruleName COLLATE NOCASE ASC"
                            }
                    }
                    orderTerms += "startedAt DESC"
                }
            }
            orderTerms +=
                if (sortDirection == HistorySortDirection.DESCENDING) {
                    "id DESC"
                } else {
                    "id ASC"
                }
            return SimpleSQLiteQuery(
                "$selectClause FROM run_history $whereClause ORDER BY ${orderTerms.joinToString()}",
                arguments,
            )
        }

        private fun buildHistoryWhereClause(
            ruleId: Long?,
            statusFilter: HistoryStatusFilter,
        ): Pair<String, Array<Any>> {
            val conditions = mutableListOf<String>()
            val arguments = mutableListOf<Any>()
            if (ruleId != null) {
                conditions += "ruleId = ?"
                arguments += ruleId
            }
            when (statusFilter) {
                HistoryStatusFilter.ALL -> {
                    conditions += "1 = 1"
                }

                HistoryStatusFilter.SUCCESS -> {
                    conditions +=
                        "status = 'SUCCESS' AND NOT (totalFilesMoved = 0 AND totalFilesFailed = 0) AND isReversed = 0"
                }

                HistoryStatusFilter.FAILED -> {
                    conditions += "status = 'FAILED'"
                }

                HistoryStatusFilter.PARTIAL -> {
                    conditions += "status = 'PARTIAL_FAILURE'"
                }

                HistoryStatusFilter.NO_CHANGES -> {
                    conditions += "status = 'SUCCESS' AND totalFilesMoved = 0 AND totalFilesFailed = 0"
                }

                HistoryStatusFilter.CANCELLED -> {
                    conditions += "status = 'CANCELLED'"
                }

                HistoryStatusFilter.UNDONE -> {
                    conditions += "(status = 'UNDONE' OR isReversed = 1)"
                }

                HistoryStatusFilter.PARTIAL_UNDONE -> {
                    conditions += "status = 'PARTIAL_UNDONE'"
                }
            }
            val whereClause =
                if (conditions.isEmpty()) {
                    ""
                } else {
                    "WHERE ${conditions.joinToString(" AND ", prefix = "(", postfix = ")")}"
                }
            return whereClause to arguments.toTypedArray()
        }
    }

data class BackupSnapshot(
    val rules: List<Rule>,
    val historyWithFiles: List<Pair<RunHistory, List<FileMoved>>>,
)

data class RestoreRollbackSnapshot(
    val rulesIncludingTrash: List<Rule>,
    val backupSnapshot: BackupSnapshot,
)

private const val HISTORY_STATUS_SECTION_SQL =
    """
    CASE
        WHEN status = 'UNDONE' OR isReversed = 1 THEN 7
        WHEN status = 'SUCCESS' AND totalFilesMoved = 0 AND totalFilesFailed = 0 THEN 3
        WHEN status = 'IN_PROGRESS' THEN 4
        WHEN status = 'CANCELLED' THEN 5
        WHEN status = 'FAILED' THEN 1
        WHEN status = 'PARTIAL_FAILURE' THEN 2
        WHEN status = 'PARTIAL_UNDONE' THEN 6
        WHEN status = 'SUCCESS' THEN 0
        ELSE 8
    END
    """
