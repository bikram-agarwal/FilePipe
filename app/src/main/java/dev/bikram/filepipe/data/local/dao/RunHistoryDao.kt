package dev.bikram.filepipe.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.bikram.filepipe.data.local.entity.RunHistoryEntity
import kotlinx.coroutines.flow.Flow

/** Latest [RunHistoryEntity.startedAt] per rule for rules-list sorting (not persisted on [RuleEntity]). */
data class RuleLastRunRow(
    val ruleId: Long,
    val lastStartedAt: Long
)

@Dao
interface RunHistoryDao {

    @Query("SELECT * FROM run_history ORDER BY startedAt DESC")
    fun getAllHistory(): Flow<List<RunHistoryEntity>>

    @Query(
        """
        SELECT ruleId AS ruleId, MAX(startedAt) AS lastStartedAt
        FROM run_history
        WHERE ruleId IS NOT NULL
        GROUP BY ruleId
        """
    )
    fun observeLastStartedAtByRuleId(): Flow<List<RuleLastRunRow>>

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

    @Query("SELECT * FROM run_history WHERE ruleId = :ruleId ORDER BY startedAt DESC")
    fun getHistoryForRule(ruleId: Long): Flow<List<RunHistoryEntity>>

    @Insert
    suspend fun insertHistory(history: RunHistoryEntity): Long

    @Update
    suspend fun updateHistory(history: RunHistoryEntity)

    @Query("DELETE FROM run_history WHERE startedAt < :olderThan")
    suspend fun deleteHistoryOlderThan(olderThan: Long)

    @Query("DELETE FROM run_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM run_history")
    suspend fun deleteAllHistory()
}
