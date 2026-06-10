package dev.bikram.filepipe.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.bikram.filepipe.data.local.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules WHERE trashedAt IS NULL ORDER BY updatedAt DESC")
    fun getAllRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE id = :id AND trashedAt IS NULL")
    suspend fun getRuleById(id: Long): RuleEntity?

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getRuleByIdIncludingTrashed(id: Long): RuleEntity?

    /** Order matches backup / [RuleRepository.replaceAllRules] insertion order (sortOrder index). */
    @Query("SELECT * FROM rules WHERE trashedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllRulesOrderedBySortOrder(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE isEnabled = 1 AND trashedAt IS NULL")
    suspend fun getEnabledRules(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE trashedAt IS NOT NULL ORDER BY trashedAt DESC, updatedAt DESC")
    fun getTrashedRules(): Flow<List<RuleEntity>>

    @Query("SELECT COUNT(*) FROM rules WHERE trashedAt IS NULL")
    suspend fun countRules(): Int

    @Query("SELECT COUNT(*) FROM rules WHERE isEnabled = 1 AND trashedAt IS NULL")
    suspend fun countEnabledRules(): Int

    @Query("SELECT COUNT(*) FROM rules WHERE scheduleType IS NOT NULL AND trashedAt IS NULL")
    suspend fun countScheduledRules(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: RuleEntity): Long

    @Update
    suspend fun updateRule(rule: RuleEntity)

    @Update
    suspend fun updateRules(rules: List<RuleEntity>)

    @Transaction
    suspend fun updateRulesSortOrders(rules: List<RuleEntity>) {
        updateRules(rules)
    }

    @Delete
    suspend fun deleteRule(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("UPDATE rules SET trashedAt = :trashedAt, updatedAt = :trashedAt WHERE id = :id")
    suspend fun moveRuleToTrash(
        id: Long,
        trashedAt: Long,
    )

    @Query("UPDATE rules SET trashedAt = NULL, updatedAt = :restoredAt WHERE id = :id")
    suspend fun restoreRuleFromTrash(
        id: Long,
        restoredAt: Long,
    )

    @Query("UPDATE rules SET cardModeOverride = :override WHERE id = :id")
    suspend fun updateCardModeOverride(
        id: Long,
        override: Boolean,
    )

    @Query("UPDATE rules SET cardModeOverride = 0")
    suspend fun clearCardModeOverrides()

    @Query("DELETE FROM rules WHERE trashedAt IS NOT NULL AND trashedAt < :cutoff")
    suspend fun deleteTrashedRulesOlderThan(cutoff: Long)

    @Query("DELETE FROM rules WHERE trashedAt IS NOT NULL")
    suspend fun deleteAllTrashedRules()

    @Query("SELECT id FROM rules WHERE trashedAt IS NULL")
    suspend fun getAllRuleIds(): List<Long>

    @Query("SELECT IFNULL(MAX(sortOrder), -1) FROM rules")
    suspend fun getMaxSortOrder(): Int

    @Query("DELETE FROM rules")
    suspend fun deleteAllRules()
}
