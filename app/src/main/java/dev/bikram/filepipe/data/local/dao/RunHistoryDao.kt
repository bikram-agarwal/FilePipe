package dev.bikram.filepipe.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import dev.bikram.filepipe.data.local.entity.RunHistoryEntity
import kotlinx.coroutines.flow.Flow

data class HistoryStatusAvailability(
    val hasSuccess: Boolean,
    val hasFailed: Boolean,
    val hasPartial: Boolean,
    val hasNoChanges: Boolean,
    val hasCancelled: Boolean,
    val hasUndone: Boolean,
    val hasPartialUndone: Boolean,
)

data class HistoryRuleCount(
    val ruleName: String,
    val count: Int,
)

data class HistoryStatusSectionCount(
    val section: Int,
    val count: Int,
)

@Dao
interface RunHistoryDao {
    @Query("SELECT * FROM run_history ORDER BY startedAt DESC")
    fun getAllHistory(): Flow<List<RunHistoryEntity>>

    @Query("SELECT COUNT(*) FROM run_history")
    fun observeHistoryCount(): Flow<Int>

    /**
     * Destination folders every recorded run of [ruleId] created. Undo sweeps folders FilePipe made
     * and left empty, including ones an earlier run of the same rule created and this one reused.
     */
    @Query("SELECT copyCreatedDestFolderUris FROM run_history WHERE ruleId = :ruleId")
    suspend fun getCreatedDestFolderUrisForRule(ruleId: Long): List<String>

    @Query("SELECT COUNT(*) FROM run_history")
    suspend fun countHistory(): Int

    @Query("SELECT COUNT(*) FROM run_history WHERE status = :status")
    suspend fun countHistoryByStatus(status: String): Int

    @Query(
        """
        WITH filtered_history AS (
            SELECT status, totalFilesMoved, totalFilesFailed, isReversed
            FROM run_history
            WHERE (:ruleId IS NULL OR ruleId = :ruleId)
        )
        SELECT
            EXISTS(
                SELECT 1 FROM filtered_history
                WHERE status = 'SUCCESS'
                    AND NOT (totalFilesMoved = 0 AND totalFilesFailed = 0)
                    AND isReversed = 0
            ) AS hasSuccess,
            EXISTS(SELECT 1 FROM filtered_history WHERE status = 'FAILED') AS hasFailed,
            EXISTS(SELECT 1 FROM filtered_history WHERE status = 'PARTIAL_FAILURE') AS hasPartial,
            EXISTS(
                SELECT 1 FROM filtered_history
                WHERE status = 'SUCCESS' AND totalFilesMoved = 0 AND totalFilesFailed = 0
            ) AS hasNoChanges,
            EXISTS(SELECT 1 FROM filtered_history WHERE status = 'CANCELLED') AS hasCancelled,
            EXISTS(
                SELECT 1 FROM filtered_history
                WHERE status = 'UNDONE' OR isReversed = 1
            ) AS hasUndone,
            EXISTS(SELECT 1 FROM filtered_history WHERE status = 'PARTIAL_UNDONE') AS hasPartialUndone
        """,
    )
    fun observeHistoryStatusAvailability(ruleId: Long?): Flow<HistoryStatusAvailability>

    @RawQuery(observedEntities = [RunHistoryEntity::class])
    fun getHistoryPaged(query: SupportSQLiteQuery): PagingSource<Int, RunHistoryEntity>

    @RawQuery(observedEntities = [RunHistoryEntity::class])
    fun observeFilteredHistoryCount(query: SupportSQLiteQuery): Flow<Int>

    @RawQuery(observedEntities = [RunHistoryEntity::class])
    fun observeHistoryIds(query: SupportSQLiteQuery): Flow<List<Long>>

    @RawQuery(observedEntities = [RunHistoryEntity::class])
    fun observeHistoryRuleCounts(query: SupportSQLiteQuery): Flow<List<HistoryRuleCount>>

    @RawQuery(observedEntities = [RunHistoryEntity::class])
    fun observeHistoryStatusSectionCounts(query: SupportSQLiteQuery): Flow<List<HistoryStatusSectionCount>>

    @Query("SELECT * FROM run_history ORDER BY startedAt DESC")
    fun getAllHistoryPagedLastRanDesc(): PagingSource<Int, RunHistoryEntity>

    @Query("SELECT * FROM run_history ORDER BY startedAt ASC")
    fun getAllHistoryPagedLastRanAsc(): PagingSource<Int, RunHistoryEntity>

    @Query("SELECT * FROM run_history ORDER BY ruleName COLLATE NOCASE ASC, startedAt DESC")
    fun getAllHistoryPagedRuleNameAsc(): PagingSource<Int, RunHistoryEntity>

    @Query("SELECT * FROM run_history ORDER BY ruleName COLLATE NOCASE DESC, startedAt DESC")
    fun getAllHistoryPagedRuleNameDesc(): PagingSource<Int, RunHistoryEntity>

    @Query("SELECT * FROM run_history WHERE ruleId = :ruleId ORDER BY startedAt DESC")
    fun getHistoryForRulePagedLastRanDesc(ruleId: Long): PagingSource<Int, RunHistoryEntity>

    @Query("SELECT * FROM run_history WHERE ruleId = :ruleId ORDER BY startedAt ASC")
    fun getHistoryForRulePagedLastRanAsc(ruleId: Long): PagingSource<Int, RunHistoryEntity>

    @Query("SELECT * FROM run_history WHERE ruleId = :ruleId ORDER BY ruleName COLLATE NOCASE ASC, startedAt DESC")
    fun getHistoryForRulePagedRuleNameAsc(ruleId: Long): PagingSource<Int, RunHistoryEntity>

    @Query("SELECT * FROM run_history WHERE ruleId = :ruleId ORDER BY ruleName COLLATE NOCASE DESC, startedAt DESC")
    fun getHistoryForRulePagedRuleNameDesc(ruleId: Long): PagingSource<Int, RunHistoryEntity>

    @Query("SELECT * FROM run_history ORDER BY startedAt DESC")
    suspend fun getAllHistoryOnce(): List<RunHistoryEntity>

    @Query("SELECT * FROM run_history WHERE id = :id")
    suspend fun getHistoryById(id: Long): RunHistoryEntity?

    @Query("SELECT * FROM run_history WHERE id = :id")
    fun observeHistoryById(id: Long): Flow<RunHistoryEntity?>

    @Query("SELECT * FROM run_history WHERE ruleId = :ruleId ORDER BY startedAt DESC")
    fun getHistoryForRule(ruleId: Long): Flow<List<RunHistoryEntity>>

    @Insert
    suspend fun insertHistory(history: RunHistoryEntity): Long

    @Update
    suspend fun updateHistory(history: RunHistoryEntity)

    @Query(
        """
        UPDATE run_history
        SET completedAt = :completedAt, status = 'FAILED', errorMessage = :errorMessage
        WHERE status = 'IN_PROGRESS' AND startedAt < :startedBefore
        """,
    )
    suspend fun markInterruptedRunsFailed(
        startedBefore: Long,
        completedAt: Long,
        errorMessage: String,
    ): Int

    @Query("DELETE FROM run_history WHERE startedAt < :olderThan")
    suspend fun deleteHistoryOlderThan(olderThan: Long)

    @Query("DELETE FROM run_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query(
        """
        DELETE FROM run_history
        WHERE (:ruleId IS NULL OR ruleId = :ruleId)
            AND (
                :statusFilter = 'ALL'
                OR (
                    :statusFilter = 'SUCCESS'
                    AND status = 'SUCCESS'
                    AND NOT (totalFilesMoved = 0 AND totalFilesFailed = 0)
                    AND isReversed = 0
                )
                OR (:statusFilter = 'FAILED' AND status = 'FAILED')
                OR (:statusFilter = 'PARTIAL' AND status = 'PARTIAL_FAILURE')
                OR (
                    :statusFilter = 'NO_CHANGES'
                    AND status = 'SUCCESS'
                    AND totalFilesMoved = 0
                    AND totalFilesFailed = 0
                )
                OR (:statusFilter = 'CANCELLED' AND status = 'CANCELLED')
                OR (
                    :statusFilter = 'UNDONE'
                    AND (status = 'UNDONE' OR isReversed = 1)
                )
                OR (:statusFilter = 'PARTIAL_UNDONE' AND status = 'PARTIAL_UNDONE')
            )
        """,
    )
    suspend fun deleteFilteredHistory(
        ruleId: Long?,
        statusFilter: String,
    ): Int

    @Query("DELETE FROM run_history")
    suspend fun deleteAllHistory()
}
