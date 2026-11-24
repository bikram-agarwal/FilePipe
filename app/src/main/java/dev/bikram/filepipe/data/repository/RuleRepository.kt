package dev.bikram.filepipe.data.repository

import dev.bikram.filepipe.data.local.dao.RuleDao
import dev.bikram.filepipe.data.local.entity.toDomain
import dev.bikram.filepipe.data.local.entity.toEntity
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.storage.PersistedUriGrantManager
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
        private val userPreferencesRepository: UserPreferencesRepository,
        private val persistedUriGrantManager: PersistedUriGrantManager,
    ) {
        companion object {
            const val TRASH_RETENTION_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L
        }

        fun getAllRules(): Flow<List<Rule>> = ruleDao.getAllRules().map { it.map { entity -> entity.toDomain() } }

        fun getTrashedRules(): Flow<List<Rule>> = ruleDao.getTrashedRules().map { it.map { entity -> entity.toDomain() } }

        suspend fun getRuleById(id: Long): Rule? = ruleDao.getRuleById(id)?.toDomain()

        suspend fun getRuleByIdIncludingTrashed(id: Long): Rule? = ruleDao.getRuleByIdIncludingTrashed(id)?.toDomain()

        suspend fun getAllRulesOrderedBySortOrder(): List<Rule> = ruleDao.getAllRulesOrderedBySortOrder().map { entity -> entity.toDomain() }

        suspend fun getAllRulesIncludingTrashed(): List<Rule> = ruleDao.getAllRulesIncludingTrashed().map { entity -> entity.toDomain() }

        fun observeAllRulesIncludingTrashed(): Flow<List<Rule>> = ruleDao.observeAllRulesIncludingTrashed().map { entities -> entities.map { it.toDomain() } }

        suspend fun getEnabledRules(): List<Rule> = ruleDao.getEnabledRules().map { it.toDomain() }

        /**
         * Inserts a new rule, or updates an existing one **without writing the columns the rule
         * editor doesn't own**. The editor builds its [Rule] from the form fields alone, so every
         * column it doesn't show comes back as a default value: a full-row write would snap the
         * card's expand/collapse override back to the list default, erase the last-run time behind
         * "sort by last run", reset the creation date to now, and un-trash a trashed rule. Those
         * columns are owned by the list and by run bookkeeping - which have their own setters -
         * so they are carried over from the stored row here.
         */
        suspend fun saveRule(rule: Rule): Long {
            if (rule.id == 0L) {
                val nextOrder = ruleDao.getMaxSortOrder() + 1
                return ruleDao.upsertRule(rule.copy(sortOrder = nextOrder).toEntity())
            }
            val edited = rule.toEntity()
            val stored = ruleDao.getRuleByIdIncludingTrashed(rule.id)
            val savedRuleId =
                ruleDao.upsertRule(
                    if (stored == null) {
                        edited
                    } else {
                        edited.copy(
                            cardModeOverride = stored.cardModeOverride,
                            groupName = stored.groupName,
                            lastRunStartedAt = stored.lastRunStartedAt,
                            createdAt = stored.createdAt,
                            trashedAt = stored.trashedAt,
                        )
                    },
                )
            stored?.toDomain()?.let { previousRule ->
                releaseUnusedRuleGrants(previousRule.folderUris())
            }
            return savedRuleId
        }

        suspend fun updateRule(rule: Rule) {
            val previousRule = ruleDao.getRuleByIdIncludingTrashed(rule.id)?.toDomain()
            ruleDao.updateRule(rule.toEntity())
            previousRule?.let { releaseUnusedRuleGrants(it.folderUris()) }
        }

        /** One transaction so observers emit once; preserves drag order until sort mode updates. */
        suspend fun persistOrderedSortIndices(ordered: List<Rule>) {
            val entities =
                ordered.mapIndexed { index, rule ->
                    rule.copy(sortOrder = index).toEntity()
                }
            ruleDao.updateRulesSortOrders(entities)
        }

        /**
         * Records that [ruleId] started a run at [startedAt]. Called for every run, including ones
         * that match no files, so the rules list can sort by last run without depending on history
         * rows (which are prunable and user-deletable).
         */
        suspend fun markRuleRan(
            ruleId: Long,
            startedAt: Long,
        ) = ruleDao.updateLastRunStartedAt(ruleId, startedAt)

        suspend fun moveRuleToTrash(ruleId: Long) = ruleDao.moveRuleToTrash(ruleId, System.currentTimeMillis())

        suspend fun restoreRuleFromTrash(ruleId: Long) = ruleDao.restoreRuleFromTrash(ruleId, System.currentTimeMillis())

        suspend fun updateCardModeOverride(
            ruleId: Long,
            override: Boolean,
        ) = ruleDao.updateCardModeOverride(ruleId, override)

        suspend fun clearCardModeOverrides() = ruleDao.clearCardModeOverrides()

        suspend fun updateRuleGroup(
            ruleId: Long,
            groupName: String?,
        ) = ruleDao.updateGroupName(ruleId, groupName?.trim()?.takeIf { it.isNotEmpty() })

        suspend fun updateRulesGroup(
            ruleIds: Set<Long>,
            groupName: String?,
        ) {
            if (ruleIds.isEmpty()) return
            ruleDao.updateGroupNames(ruleIds.toList(), groupName?.trim()?.takeIf { it.isNotEmpty() })
        }

        suspend fun deleteRuleForever(ruleId: Long) {
            val deletedRule = ruleDao.getRuleByIdIncludingTrashed(ruleId)?.toDomain()
            ruleDao.deleteRuleById(ruleId)
            deletedRule?.let { releaseUnusedRuleGrants(it.folderUris()) }
        }

        suspend fun autoEmptyTrashOlderThan(cutoffMillis: Long) {
            val previousUris = allRuleFolderUris()
            ruleDao.deleteTrashedRulesOlderThan(cutoffMillis)
            releaseUnusedRuleGrants(previousUris)
        }

        suspend fun emptyTrashForever() {
            val previousUris = allRuleFolderUris()
            ruleDao.deleteAllTrashedRules()
            releaseUnusedRuleGrants(previousUris)
        }

        suspend fun deleteRule(ruleId: Long) = deleteRuleForever(ruleId)

        suspend fun getAllRuleIds(): List<Long> = ruleDao.getAllRuleIds()

        suspend fun deleteAllRules() {
            val previousUris = allRuleFolderUris()
            ruleDao.deleteAllRules()
            releaseUnusedRuleGrants(previousUris)
        }

        suspend fun replaceAllRules(rules: List<Rule>) {
            val previousUris = allRuleFolderUris()
            replaceAllRulesInDatabase(rules)
            releaseUnusedRuleGrants(previousUris)
        }

        suspend fun replaceAllRulesInDatabase(rules: List<Rule>) {
            ruleDao.deleteAllRules()
            rules.forEachIndexed { index, rule ->
                ruleDao.upsertRule(rule.copy(id = 0L, sortOrder = index).toEntity())
            }
        }

        suspend fun getAllRuleFolderUris(): Set<String> = allRuleFolderUris()

        suspend fun releaseUnusedRuleGrants(candidateUris: Collection<String>) {
            releaseUnusedRuleGrantsInternal(candidateUris)
        }

        @Suppress("ktlint:standard:function-expression-body")
        private suspend fun allRuleFolderUris(): Set<String> {
            return ruleDao
                .getAllRulesIncludingTrashed()
                .flatMapTo(linkedSetOf()) { ruleEntity -> ruleEntity.toDomain().folderUris() }
        }

        private suspend fun releaseUnusedRuleGrantsInternal(candidateUris: Collection<String>) {
            val preferences = userPreferencesRepository.getPreferencesSnapshot()
            val retainedUris =
                allRuleFolderUris() +
                    setOf(preferences.exportFolderUri, preferences.cloudExportFolderUri) +
                    preferences.bookmarkedFolders
            persistedUriGrantManager.releaseUnused(candidateUris, retainedUris)
        }

        @Suppress("ktlint:standard:function-expression-body")
        private fun Rule.folderUris(): Set<String> {
            return sourceFolderPaths.toSet() + destinationFolderPath
        }
    }
