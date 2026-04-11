package dev.bikram.filepipe.domain.usecase

import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.data.repository.RunHistoryRepository
import dev.bikram.filepipe.domain.export.parseRulesBackupJson
import dev.bikram.filepipe.domain.export.toDomain
import kotlinx.coroutines.flow.first
import javax.inject.Inject

enum class BackupImportPickAction {
    ImportMerge,
    RestoreFull
}

data class MergeRulesImportResult(
    val rulesAdded: Int,
    val rulesUpdated: Int
)

data class RestoreBackupResult(
    val rulesImported: Int,
    val historyRunsImported: Int,
    val settingsRestored: Boolean
)

class ImportRulesUseCase @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val runHistoryRepository: RunHistoryRepository,
    private val scheduleRulesUseCase: ScheduleRulesUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    /**
     * Adds rules from the file that do not exist locally (by name) and updates existing rules
     * when names match. Does not change run history or settings.
     */
    suspend fun mergeRulesFromJson(jsonText: String): Result<MergeRulesImportResult> {
        val backup = parseRulesBackupJson(jsonText).getOrElse { return Result.failure(it) }
        val existingByName = ruleRepository.getAllRules().first()
            .groupBy { rule -> rule.name }
            .mapValues { entry -> entry.value.first() }
            .toMutableMap()

        val rulesFromFile = backup.rules
            .groupBy { dto -> dto.name }
            .mapValues { entry -> entry.value.last() }
            .values

        var rulesAdded = 0
        var rulesUpdated = 0

        for (dto in rulesFromFile) {
            val incoming = dto.toDomain()
            val existing = existingByName[incoming.name]
            if (existing != null) {
                scheduleRulesUseCase.cancelRuleById(existing.id)
                val merged = incoming.copy(
                    id = existing.id,
                    sortOrder = existing.sortOrder,
                    createdAt = existing.createdAt,
                    updatedAt = System.currentTimeMillis()
                )
                ruleRepository.updateRule(merged)
                if (merged.isEnabled && merged.schedule != null) {
                    scheduleRulesUseCase.scheduleRule(merged)
                }
                existingByName[incoming.name] = merged
                rulesUpdated++
            } else {
                val newId = ruleRepository.saveRule(incoming.copy(id = 0L))
                val saved = ruleRepository.getRuleById(newId)
                    ?: return Result.failure(IllegalStateException("Rule not found after save"))
                existingByName[incoming.name] = saved
                if (saved.isEnabled && saved.schedule != null) {
                    scheduleRulesUseCase.scheduleRule(saved)
                }
                rulesAdded++
            }
        }

        return Result.success(MergeRulesImportResult(rulesAdded = rulesAdded, rulesUpdated = rulesUpdated))
    }

    /**
     * Replaces all rules, run history, and (when present in the file) settings with backup contents.
     */
    suspend fun restoreFromBackupJson(jsonText: String): Result<RestoreBackupResult> {
        val backup = parseRulesBackupJson(jsonText).getOrElse { return Result.failure(it) }
        val oldIds = ruleRepository.getAllRuleIds()
        oldIds.forEach { ruleId -> scheduleRulesUseCase.cancelRuleById(ruleId) }

        val rules = backup.rules.map { it.toDomain() }
        ruleRepository.replaceAllRules(rules)
        val savedOrdered = ruleRepository.getAllRulesOrderedBySortOrder()
        val nameToFirstRuleId = savedOrdered.groupBy { rule -> rule.name }
            .mapValues { entry -> entry.value.first().id }

        runHistoryRepository.replaceHistoryFromBackup(backup.history) { dto ->
            val index = dto.ruleIndexInBackup
            if (index != null && index in savedOrdered.indices) {
                savedOrdered[index].id
            } else {
                nameToFirstRuleId[dto.ruleName]
            }
        }

        val settingsApplied = backup.settings != null
        backup.settings?.let { userPreferencesRepository.applySettingsFromBackup(it) }

        savedOrdered.filter { rule -> rule.isEnabled && rule.schedule != null }.forEach { rule ->
            scheduleRulesUseCase.scheduleRule(rule)
        }

        return Result.success(
            RestoreBackupResult(
                rulesImported = savedOrdered.size,
                historyRunsImported = backup.history.size,
                settingsRestored = settingsApplied
            )
        )
    }
}
