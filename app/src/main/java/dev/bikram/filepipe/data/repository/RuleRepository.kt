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
class RuleRepository
    @Inject
    constructor(
        private val ruleDao: RuleDao,
    ) {
        companion object {
            const val TRASH_RETENTION_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L
        }

        fun getAllRules(): Flow<List<Rule>> = ruleDao.getAllRules().map { it.map { entity -> entity.toDomain() } }

        fun getTrashedRules(): Flow<List<Rule>> = ruleDao.getTrashedRules().map { it.map { entity -> entity.toDomain() } }

        suspend fun getRuleById(id: Long): Rule? = ruleDao.getRuleById(id)?.toDomain()

        suspend fun getAllRulesOrderedBySortOrder(): List<Rule> = ruleDao.getAllRulesOrderedBySortOrder().map { entity -> entity.toDomain() }

        suspend fun getEnabledRules(): List<Rule> = ruleDao.getEnabledRules().map { it.toDomain() }

        suspend fun saveRule(rule: Rule): Long {
            val entity =
                if (rule.id == 0L) {
                    val nextOrder = ruleDao.getMaxSortOrder() + 1
                    rule.copy(sortOrder = nextOrder).toEntity()
                } else {
                    rule.toEntity()
                }
            return ruleDao.upsertRule(entity)
        }

        suspend fun updateRule(rule: Rule) = ruleDao.updateRule(rule.toEntity())

        /** One transaction so observers emit once; preserves drag order until sort mode updates. */
        suspend fun persistOrderedSortIndices(ordered: List<Rule>) {
            val entities =
                ordered.mapIndexed { index, rule ->
                    rule.copy(sortOrder = index).toEntity()
                }
            ruleDao.updateRulesSortOrders(entities)
        }

        suspend fun moveRuleToTrash(ruleId: Long) = ruleDao.moveRuleToTrash(ruleId, System.currentTimeMillis())

        suspend fun restoreRuleFromTrash(ruleId: Long) = ruleDao.restoreRuleFromTrash(ruleId, System.currentTimeMillis())

        suspend fun updateCardModeOverride(
            ruleId: Long,
            override: Boolean,
        ) = ruleDao.updateCardModeOverride(ruleId, override)

        suspend fun clearCardModeOverrides() = ruleDao.clearCardModeOverrides()

        suspend fun deleteRuleForever(ruleId: Long) = ruleDao.deleteRuleById(ruleId)

        suspend fun autoEmptyTrashOlderThan(cutoffMillis: Long) = ruleDao.deleteTrashedRulesOlderThan(cutoffMillis)

        suspend fun emptyTrashForever() = ruleDao.deleteAllTrashedRules()

        suspend fun deleteRule(ruleId: Long) = deleteRuleForever(ruleId)

        suspend fun getAllRuleIds(): List<Long> = ruleDao.getAllRuleIds()

        suspend fun deleteAllRules() = ruleDao.deleteAllRules()

        suspend fun replaceAllRules(rules: List<Rule>) {
            ruleDao.deleteAllRules()
            rules.forEachIndexed { index, rule ->
                ruleDao.upsertRule(rule.copy(id = 0L, sortOrder = index).toEntity())
            }
        }
    }
