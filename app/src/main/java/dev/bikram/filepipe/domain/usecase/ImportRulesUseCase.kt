package dev.bikram.filepipe.domain.usecase

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.AppDatabase
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.BackupSnapshot
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.data.repository.RunHistoryRepository
import dev.bikram.filepipe.data.repository.areExcludePatternsValid
import dev.bikram.filepipe.data.repository.isFilenameRegexValid
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.domain.export.AppBackup
import dev.bikram.filepipe.domain.export.RunHistoryBackupDto
import dev.bikram.filepipe.domain.export.parseRulesBackupJson
import dev.bikram.filepipe.domain.export.toDomain
import dev.bikram.filepipe.domain.model.Rule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

enum class BackupImportPickAction {
    ImportMerge,
    RestoreFull,
}

data class MergeRulesImportResult(
    val rulesAdded: Int,
    val rulesUpdated: Int,
)

data class RestoreBackupResult(
    val rulesImported: Int,
    val historyRunsImported: Int,
    val settingsRestored: Boolean,
    val foldersNeedingReselection: Int,
    val automationsDisabled: Boolean,
)

class InvalidBackupRuleRegexException(
    val ruleNames: List<String>,
) : IllegalArgumentException("Backup contains invalid rule regular expressions")

internal fun findRulesWithInvalidRegexPatterns(rules: List<Rule>): List<String> =
    rules
        .filter { rule ->
            // The same checks as the rule editor and save-time validation.
            val invalidFilenameRegex = rule.isRegexPattern && !isFilenameRegexValid(rule.filenamePattern)
            val invalidExcludeRegex = rule.isExcludeRegexPattern && !areExcludePatternsValid(rule.excludePatterns, isRegexPattern = true)
            invalidFilenameRegex || invalidExcludeRegex
        }.map { rule -> rule.name }

class ImportRulesUseCase
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val appDatabase: AppDatabase,
        private val ruleRepository: RuleRepository,
        private val runHistoryRepository: RunHistoryRepository,
        private val scheduleRulesUseCase: ScheduleRulesUseCase,
        private val userPreferencesRepository: UserPreferencesRepository,
    ) {
        /**
         * Adds rules from the file that do not exist locally (by name) and updates existing rules
         * when names match. Does not change run history or settings.
         */
        suspend fun mergeRulesFromJson(jsonText: String): Result<MergeRulesImportResult> {
            val backup = parseRulesBackupJson(jsonText).getOrElse { return Result.failure(it) }
            return mergeRules(backup)
        }

        suspend fun mergeRulesFromStream(inputStream: InputStream): Result<MergeRulesImportResult> {
            val backup = parseRulesBackupJson(inputStream).getOrElse { return Result.failure(it) }
            return mergeRules(backup)
        }

        private suspend fun mergeRules(backup: AppBackup): Result<MergeRulesImportResult> {
            val incomingRules = backup.rules.map { ruleDto -> ruleDto.toDomain() }
            val invalidRegexRuleNames = findRulesWithInvalidRegexPatterns(incomingRules)
            if (invalidRegexRuleNames.isNotEmpty()) {
                return Result.failure(InvalidBackupRuleRegexException(invalidRegexRuleNames))
            }
            val existingByName =
                ruleRepository
                    .getAllRules()
                    .first()
                    .groupBy { rule -> rule.name }
                    .mapValues { entry -> entry.value.first() }
                    .toMutableMap()

            val rulesFromFile =
                incomingRules
                    .groupBy { rule -> rule.name }
                    .mapValues { entry -> entry.value.last() }
                    .values

            var rulesAdded = 0
            var rulesUpdated = 0

            for (incoming in rulesFromFile) {
                val existing = existingByName[incoming.name]
                if (existing != null) {
                    scheduleRulesUseCase.cancelRuleById(existing.id)
                    val merged =
                        incoming.copy(
                            id = existing.id,
                            sortOrder = existing.sortOrder,
                            createdAt = existing.createdAt,
                            updatedAt = System.currentTimeMillis(),
                        )
                    ruleRepository.updateRule(merged)
                    if (merged.isEnabled && merged.schedule != null) {
                        scheduleRulesUseCase.scheduleRule(merged)
                    }
                    existingByName[incoming.name] = merged
                    rulesUpdated++
                } else {
                    val newId = ruleRepository.saveRule(incoming.copy(id = 0L))
                    val saved =
                        ruleRepository.getRuleById(newId)
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
            return restoreFromBackup(backup)
        }

        suspend fun restoreFromBackupStream(inputStream: InputStream): Result<RestoreBackupResult> {
            val backup = parseRulesBackupJson(inputStream).getOrElse { return Result.failure(it) }
            return restoreFromBackup(backup)
        }

        private suspend fun restoreFromBackup(backup: AppBackup): Result<RestoreBackupResult> {
            val rules = backup.rules.map { ruleDto -> ruleDto.toDomain() }
            val invalidRegexRuleNames = findRulesWithInvalidRegexPatterns(rules)
            if (invalidRegexRuleNames.isNotEmpty()) {
                return Result.failure(InvalidBackupRuleRegexException(invalidRegexRuleNames))
            }

            val rollbackSnapshot = runHistoryRepository.getRestoreRollbackSnapshot()
            val previousSnapshot = rollbackSnapshot.backupSnapshot
            val previousRulesIncludingTrash = rollbackSnapshot.rulesIncludingTrash
            val previousPreferences = userPreferencesRepository.getPreferencesSnapshot()
            val oldIds = ruleRepository.getAllRuleIds()
            val previousUris =
                ruleRepository.getAllRuleFolderUris() +
                    setOf(previousPreferences.exportFolderUri, previousPreferences.cloudExportFolderUri) +
                    previousPreferences.bookmarkedFolders

            val savedOrdered =
                try {
                    replaceRoomBackupData(rules, backup.history)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    return Result.failure(error)
                }

            val settingsApplied = backup.settings != null
            var foldersNeedingReselection = 0
            var automationsDisabled = false
            try {
                backup.settings?.let { settings ->
                    val settingsOutcome = userPreferencesRepository.applySettingsFromBackup(settings)
                    foldersNeedingReselection = settingsOutcome.foldersNeedingReselection
                    automationsDisabled = settingsOutcome.automationsDisabled
                }
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    rollbackRoomRestore(
                        rulesIncludingTrash = previousRulesIncludingTrash,
                        snapshot = previousSnapshot,
                        importedRules = rules,
                        backup = backup,
                        originalError = error,
                    )
                }
                throw error
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    rollbackRoomRestore(
                        rulesIncludingTrash = previousRulesIncludingTrash,
                        snapshot = previousSnapshot,
                        importedRules = rules,
                        backup = backup,
                        originalError = error,
                    )
                }
                return Result.failure(error)
            }

            withContext(NonCancellable) {
                oldIds.forEach { ruleId ->
                    runCatching { scheduleRulesUseCase.cancelRuleById(ruleId) }
                        .onFailure { error ->
                            DiagnosticLog.record(context, "Restored backup could not cancel old rule schedule: ruleId=$ruleId", error)
                        }
                }
                runCatching { ruleRepository.releaseUnusedRuleGrants(previousUris) }
                    .onFailure { error ->
                        DiagnosticLog.record(context, "Restored backup could not release old folder grants", error)
                    }
                savedOrdered.filter { rule -> rule.isEnabled && rule.schedule != null }.forEach { rule ->
                    runCatching { scheduleRulesUseCase.scheduleRule(rule) }
                        .onFailure { error ->
                            DiagnosticLog.record(context, "Restored backup could not schedule rule: ruleId=${rule.id}", error)
                        }
                }
            }

            return Result.success(
                RestoreBackupResult(
                    rulesImported = savedOrdered.size,
                    historyRunsImported = backup.history.size,
                    settingsRestored = settingsApplied,
                    foldersNeedingReselection = foldersNeedingReselection,
                    automationsDisabled = automationsDisabled,
                ),
            )
        }

        private suspend fun replaceRoomBackupData(
            rules: List<Rule>,
            backupRuns: List<RunHistoryBackupDto>,
        ): List<Rule> =
            appDatabase.withTransaction {
                ruleRepository.replaceAllRulesInDatabase(rules)
                val savedOrdered = ruleRepository.getAllRulesOrderedBySortOrder()
                val nameToFirstRuleId =
                    savedOrdered
                        .groupBy { rule -> rule.name }
                        .mapValues { entry -> entry.value.first().id }
                runHistoryRepository.replaceHistoryFromBackup(backupRuns) { historyDto ->
                    val ruleIndex = historyDto.ruleIndexInBackup
                    if (ruleIndex != null) {
                        savedOrdered.getOrNull(ruleIndex)?.id
                    } else {
                        nameToFirstRuleId[historyDto.ruleName]
                    }
                }
                savedOrdered
            }

        private suspend fun rollbackRoomRestore(
            rulesIncludingTrash: List<Rule>,
            snapshot: BackupSnapshot,
            importedRules: List<Rule>,
            backup: AppBackup,
            originalError: Throwable,
        ) {
            runCatching {
                runHistoryRepository.restoreSnapshotAtomically(rulesIncludingTrash, snapshot)
            }.exceptionOrNull()?.let { rollbackError ->
                originalError.addSuppressed(rollbackError)
            }
            val importedUris =
                buildSet {
                    importedRules.forEach { rule ->
                        addAll(rule.sourceFolderPaths)
                        add(rule.destinationFolderPath)
                    }
                    backup.settings?.let { settings ->
                        add(settings.exportFolderUri)
                        add(settings.cloudExportFolderUri)
                        addAll(settings.bookmarkedFolders)
                    }
                }
            runCatching { ruleRepository.releaseUnusedRuleGrants(importedUris) }
                .onFailure { cleanupError ->
                    originalError.addSuppressed(cleanupError)
                }
        }
    }
