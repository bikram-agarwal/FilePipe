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

    @Query("SELECT * FROM rules ORDER BY updatedAt DESC")
    fun getAllRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getRuleById(id: Long): RuleEntity?

    @Query("SELECT * FROM rules WHERE isEnabled = 1")
    suspend fun getEnabledRules(): List<RuleEntity>

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

    @Query("SELECT id FROM rules")
    suspend fun getAllRuleIds(): List<Long>

    @Query("SELECT IFNULL(MAX(sortOrder), -1) FROM rules")
    suspend fun getMaxSortOrder(): Int

    @Query("DELETE FROM rules")
    suspend fun deleteAllRules()
}
