package dev.bikram.filepipe.data.repository

import dev.bikram.filepipe.data.local.dao.RuleDao
import dev.bikram.filepipe.data.local.entity.toDomain
import dev.bikram.filepipe.data.local.entity.toEntity
import dev.bikram.filepipe.domain.model.Rule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleRepository @Inject constructor(
    private val ruleDao: RuleDao
) {
    fun getAllRules(): Flow<List<Rule>> =
        ruleDao.getAllRules().map { it.map { entity -> entity.toDomain() } }

    suspend fun getRuleById(id: Long): Rule? =
        ruleDao.getRuleById(id)?.toDomain()

    suspend fun getEnabledRules(): List<Rule> =
        ruleDao.getEnabledRules().map { it.toDomain() }

    suspend fun saveRule(rule: Rule): Long {
        val entity = if (rule.id == 0L) {
            val nextOrder = ruleDao.getMaxSortOrder() + 1
            rule.copy(sortOrder = nextOrder).toEntity()
        } else {
            rule.toEntity()
        }
        return ruleDao.upsertRule(entity)
    }

    suspend fun updateRule(rule: Rule) =
        ruleDao.updateRule(rule.toEntity())

    /** One transaction so observers emit once; preserves drag order until sort mode updates. */
    suspend fun persistOrderedSortIndices(ordered: List<Rule>) {
        val entities = ordered.mapIndexed { index, rule ->
            rule.copy(sortOrder = index).toEntity()
        }
        ruleDao.updateRulesSortOrders(entities)
    }

    suspend fun deleteRule(ruleId: Long) =
        ruleDao.deleteRuleById(ruleId)

    suspend fun getAllRuleIds(): List<Long> = ruleDao.getAllRuleIds()

    suspend fun deleteAllRules() = ruleDao.deleteAllRules()

    suspend fun replaceAllRules(rules: List<Rule>) {
        ruleDao.deleteAllRules()
        rules.forEachIndexed { index, rule ->
            ruleDao.upsertRule(rule.copy(id = 0L, sortOrder = index).toEntity())
        }
    }
}
