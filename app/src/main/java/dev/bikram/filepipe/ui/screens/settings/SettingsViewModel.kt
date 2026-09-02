package dev.bikram.filepipe.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.AppColorSource
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.data.preferences.AppThemeMode
import dev.bikram.filepipe.data.preferences.FolderAccessMode
import dev.bikram.filepipe.data.preferences.SwipeAction
import dev.bikram.filepipe.data.preferences.ThemePaletteStyle
import dev.bikram.filepipe.data.preferences.UpdateCheckSchedule
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.data.storage.PersistedUriGrantManager
import dev.bikram.filepipe.data.storage.isFilesystemFolderPathString
import dev.bikram.filepipe.di.IoDispatcher
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.domain.backupFileTimestamp
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.usecase.BackupImportPickAction
import dev.bikram.filepipe.domain.usecase.ExportRulesUseCase
import dev.bikram.filepipe.domain.usecase.ImportRulesUseCase
import dev.bikram.filepipe.domain.usecase.InvalidBackupRuleRegexException
import dev.bikram.filepipe.domain.usecase.RulesAutoExportTrigger
import dev.bikram.filepipe.ui.theme.CustomFontStorage
import dev.bikram.filepipe.update.UpdateCheckWorkScheduler
import dev.bikram.filepipe.worker.ScheduledRulesExportWorker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val exportRulesUseCase: ExportRulesUseCase,
        private val importRulesUseCase: ImportRulesUseCase,
        private val workManager: WorkManager,
        private val rulesAutoExportTrigger: RulesAutoExportTrigger,
        private val updateCheckWorkScheduler: UpdateCheckWorkScheduler,
        private val ruleRepository: RuleRepository,
        private val persistedUriGrantManager: PersistedUriGrantManager,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        val preferencesFlow = userPreferencesRepository.preferencesFlow
        val preferencesState: StateFlow<AppPreferences?> =
            preferencesFlow
                .map<AppPreferences, AppPreferences?> { preferences -> preferences }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        val developerOptionsEnabledFlow = userPreferencesRepository.developerOptionsEnabledFlow

        // One-shot snackbar messages: a Channel so each is delivered exactly once (no rotation
        // replay, no conflation of identical/rapid messages).
        private val _userMessages = Channel<String>(Channel.BUFFERED)
        val userMessages: Flow<String> = _userMessages.receiveAsFlow()

        private fun postUserMessage(message: String) {
            _userMessages.trySend(message)
        }

        private val _manualExportPickerRequested =
            MutableSharedFlow<String>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val manualExportPickerRequested = _manualExportPickerRequested.asSharedFlow()

        init {
            viewModelScope.launch {
                val prefs = userPreferencesRepository.getPreferencesSnapshot()
                val backupDestinations =
                    listOf(
                        prefs.exportFolderUri,
                        prefs.cloudExportFolderUri,
                    ).filter { it.isNotBlank() }
                if (prefs.scheduledExportEnabled && backupDestinations.isNotEmpty()) {
                    enqueueScheduledExportWork()
                }
            }
        }

        fun setFolderAccessMode(mode: FolderAccessMode) {
            viewModelScope.launch {
                userPreferencesRepository.setFolderAccessMode(mode)
            }
        }

        suspend fun setFolderAccessModeNow(mode: FolderAccessMode) {
            userPreferencesRepository.setFolderAccessMode(mode)
        }

        /**
         * Rules that use filesystem paths (not SAF) will stop working when switching to Selective Access
         * until the user re-picks folders with the system picker.
         */
        suspend fun countRulesUsingFilesystemFolderPaths(): Int =
            withContext(ioDispatcher) {
                val rules = ruleRepository.getAllRules().first()
                rules.count { rule -> ruleUsesFilesystemFolderPaths(rule) }
            }

        private fun ruleUsesFilesystemFolderPaths(rule: Rule): Boolean {
            val paths = rule.sourceFolderPaths.toMutableList()
            if (rule.destinationFolderPath.isNotBlank()) {
                paths.add(rule.destinationFolderPath)
            }
            return paths.any { path -> isFilesystemFolderPathString(path) }
        }

        fun markIntroSeen(onComplete: () -> Unit = {}) =
            viewModelScope.launch {
                userPreferencesRepository.markIntroSeen()
                onComplete()
            }

        fun setThemeMode(mode: AppThemeMode) =
            viewModelScope.launch {
                userPreferencesRepository.setThemeMode(mode)
            }

        fun setUseBlackTheme(enabled: Boolean) =
            viewModelScope.launch {
                userPreferencesRepository.setUseBlackTheme(enabled)
            }

        fun setColorSource(source: AppColorSource) =
            viewModelScope.launch {
                userPreferencesRepository.setColorSource(source)
            }

        fun addCustomSeedHex(hex: String) =
            viewModelScope.launch {
                userPreferencesRepository.addCustomSeedHex(hex)
            }

        fun selectCustomSeedHex(hex: String) =
            viewModelScope.launch {
                userPreferencesRepository.selectCustomSeedHex(hex)
            }

        fun previewCustomSeedHex(hex: String) =
            viewModelScope.launch {
                userPreferencesRepository.previewCustomSeedHex(hex)
            }

        fun removeCustomSeedHex(hex: String) =
            viewModelScope.launch {
                userPreferencesRepository.removeCustomSeedHex(hex)
            }

        fun setThemePaletteStyle(style: ThemePaletteStyle) =
            viewModelScope.launch {
                userPreferencesRepository.setThemePaletteStyle(style)
            }

        fun setSettingsCollapsedSectionKeys(sectionKeys: Collection<String>) =
            viewModelScope.launch {
                userPreferencesRepository.setSettingsCollapsedSectionKeys(sectionKeys)
            }

        fun setExportFolderUri(uriString: String) =
            viewModelScope.launch {
                val previousUri = userPreferencesRepository.getPreferencesSnapshot().exportFolderUri
                if (!persistBackupFolderUri(uriString)) {
                    postUserMessage(context.getString(R.string.settings_export_folder_permission_failed))
                    return@launch
                }
                userPreferencesRepository.setExportFolderUri(uriString)
                releaseReplacedBackupGrant(previousUri)
                disableAutomationsIfNoBackupDestination()
            }

        fun setCloudExportFolderUri(uriString: String) =
            viewModelScope.launch {
                val previousUri = userPreferencesRepository.getPreferencesSnapshot().cloudExportFolderUri
                if (!persistBackupFolderUri(uriString)) {
                    postUserMessage(context.getString(R.string.settings_export_folder_permission_failed))
                    return@launch
                }
                userPreferencesRepository.setCloudExportFolderUri(uriString)
                releaseReplacedBackupGrant(previousUri)
                disableAutomationsIfNoBackupDestination()
            }

        private suspend fun persistBackupFolderUri(uriString: String): Boolean {
            if (uriString.startsWith("content://")) {
                return runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uriString.toUri(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    true
                }.getOrElse { error ->
                    DiagnosticLog.record(context, "Failed to take persistable URI permission for $uriString", error)
                    false
                }
            }
            return true
        }

        private suspend fun releaseReplacedBackupGrant(previousUri: String) {
            if (!previousUri.startsWith("content://")) return
            val preferences = userPreferencesRepository.getPreferencesSnapshot()
            val retainedRuleUris =
                ruleRepository
                    .getAllRulesIncludingTrashed()
                    .flatMap { rule -> rule.sourceFolderPaths + rule.destinationFolderPath }
            persistedUriGrantManager.releaseUnused(
                candidateUris = listOf(previousUri),
                retainedUris =
                    retainedRuleUris +
                        listOf(preferences.exportFolderUri, preferences.cloudExportFolderUri) +
                        preferences.bookmarkedFolders,
            )
        }

        private suspend fun disableAutomationsIfNoBackupDestination() {
            val prefs = userPreferencesRepository.getPreferencesSnapshot()
            val backupDestinations =
                listOf(
                    prefs.exportFolderUri,
                    prefs.cloudExportFolderUri,
                ).filter { it.isNotBlank() }
            if (backupDestinations.isEmpty()) {
                userPreferencesRepository.setAutoExportOnRuleChange(false)
                userPreferencesRepository.setScheduledExportEnabled(false)
                workManager.cancelUniqueWork(ScheduledRulesExportWorker.WORK_NAME)
            }
        }

        fun setAutoExportOnChange(enabled: Boolean) =
            viewModelScope.launch {
                val prefs = userPreferencesRepository.getPreferencesSnapshot()
                val backupDestinations =
                    listOf(
                        prefs.exportFolderUri,
                        prefs.cloudExportFolderUri,
                    ).filter { it.isNotBlank() }
                if (enabled && backupDestinations.isEmpty()) {
                    postUserMessage(context.getString(R.string.settings_export_select_folder_first))
                    return@launch
                }
                userPreferencesRepository.setAutoExportOnRuleChange(enabled)
                if (enabled) {
                    rulesAutoExportTrigger.maybeExportAfterRuleChange()
                }
            }

        fun setScheduledExportEnabled(enabled: Boolean) =
            viewModelScope.launch {
                val prefs = userPreferencesRepository.getPreferencesSnapshot()
                val backupDestinations =
                    listOf(
                        prefs.exportFolderUri,
                        prefs.cloudExportFolderUri,
                    ).filter { it.isNotBlank() }
                if (enabled && backupDestinations.isEmpty()) {
                    postUserMessage(context.getString(R.string.settings_export_select_folder_first))
                    return@launch
                }
                userPreferencesRepository.setScheduledExportEnabled(enabled)
                if (enabled) {
                    enqueueScheduledExportWork()
                } else {
                    workManager.cancelUniqueWork(ScheduledRulesExportWorker.WORK_NAME)
                }
            }

        private fun enqueueScheduledExportWork() {
            val request =
                PeriodicWorkRequestBuilder<ScheduledRulesExportWorker>(1, TimeUnit.DAYS)
                    .build()
            workManager.enqueueUniquePeriodicWork(
                ScheduledRulesExportWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun setDeveloperOptionsEnabled(enabled: Boolean) {
            viewModelScope.launch {
                userPreferencesRepository.setDeveloperOptionsEnabled(enabled)
            }
        }

        fun setLogRetentionDays(days: Int) =
            viewModelScope.launch {
                userPreferencesRepository.setLogRetentionDays(days)
            }

        fun setSwipeStartToEnd(action: SwipeAction) =
            viewModelScope.launch {
                userPreferencesRepository.setSwipeStartToEnd(action)
            }

        fun setSwipeEndToStart(action: SwipeAction) =
            viewModelScope.launch {
                userPreferencesRepository.setSwipeEndToStart(action)
            }

        fun setHapticFeedbackEnabled(enabled: Boolean) =
            viewModelScope.launch {
                userPreferencesRepository.setHapticFeedbackEnabled(enabled)
            }

        fun setProgressiveBlurEnabled(enabled: Boolean) =
            viewModelScope.launch {
                userPreferencesRepository.setProgressiveBlurEnabled(enabled)
            }

        fun setUseGradientBackground(enabled: Boolean) =
            viewModelScope.launch {
                userPreferencesRepository.setUseGradientBackground(enabled)
            }

        fun setUseEnhancedShading(enabled: Boolean) =
            viewModelScope.launch {
                userPreferencesRepository.setUseEnhancedShading(enabled)
            }

        fun setShadingIntensity(intensity: Float) =
            viewModelScope.launch {
                userPreferencesRepository.setShadingIntensity(intensity)
            }

        fun setUiScale(scale: Float) =
            viewModelScope.launch {
                userPreferencesRepository.setUiScale(scale)
            }

        fun setUpdateCheckSchedule(schedule: UpdateCheckSchedule) =
            viewModelScope.launch {
                userPreferencesRepository.setUpdateCheckSchedule(schedule)
                if (schedule == UpdateCheckSchedule.NEVER) {
                    userPreferencesRepository.setNotifyOnNewUpdates(false)
                }
                updateCheckWorkScheduler.syncFromPreferences()
            }

        fun setNotifyOnNewUpdates(enabled: Boolean) =
            viewModelScope.launch {
                userPreferencesRepository.setNotifyOnNewUpdates(enabled)
            }

        fun markPlayAutoReviewPromptHandledForCurrentInstall(lastUpdateTimeMillis: Long) =
            viewModelScope.launch {
                userPreferencesRepository.setPlayAutoReviewPromptedForLastUpdateTime(lastUpdateTimeMillis)
            }

        fun setInAppReviewAutoNeverAskAgain(neverAskAgain: Boolean) =
            viewModelScope.launch {
                userPreferencesRepository.setInAppReviewAutoNeverAskAgain(neverAskAgain)
            }

        fun setSaveUpdateApkToDownloads(enabled: Boolean) =
            viewModelScope.launch {
                userPreferencesRepository.setSaveUpdateApkToDownloads(enabled)
            }

        fun requestManualExportPicker() {
            _manualExportPickerRequested.tryEmit(defaultManualExportFileName())
        }

        fun completeManualExportToUri(targetUri: Uri) =
            viewModelScope.launch {
                exportRulesUseCase.exportBackupJsonToDocumentUri(targetUri).fold(
                    onSuccess = { displayName ->
                        postUserMessage(context.getString(R.string.settings_export_success, displayName))
                    },
                    onFailure = { err ->
                        DiagnosticLog.record(context, "Manual backup export failed", err)
                        postUserMessage(context.getString(R.string.settings_backup_export_failed, err.message.orEmpty()))
                    },
                )
            }

        private fun defaultManualExportFileName(): String {
            val stamp = backupFileTimestamp()
            return "filepipe_backup_$stamp.json"
        }

        fun exportToConfiguredBackupFolders() =
            viewModelScope.launch {
                val prefs = userPreferencesRepository.getPreferencesSnapshot()
                val backupDestinations =
                    listOf(
                        prefs.exportFolderUri,
                        prefs.cloudExportFolderUri,
                    ).filter { it.isNotBlank() }
                if (backupDestinations.isEmpty()) {
                    postUserMessage(context.getString(R.string.settings_export_select_folder_first))
                    return@launch
                }
                exportRulesUseCase.exportRulesToTreeUris(backupDestinations).fold(
                    onSuccess = { fileNames ->
                        DiagnosticLog.record(
                            context,
                            "Configured backup export completed: destinations=${backupDestinations.size}, files=${fileNames.size}",
                        )
                        postUserMessage(
                            context.resources.getQuantityString(
                                R.plurals.settings_backup_exported_to_destinations,
                                fileNames.size,
                                fileNames.size,
                            ),
                        )
                    },
                    onFailure = { error ->
                        postUserMessage(context.getString(R.string.settings_backup_export_failed, error.message.orEmpty()))
                        DiagnosticLog.record(context, "Configured backup export failed: destinations=${backupDestinations.size}", error)
                    },
                )
            }

        @Suppress("ktlint:standard:function-expression-body")
        private suspend fun <ImportResult> importBackupFromUri(
            uri: Uri,
            importBlock: suspend (InputStream) -> Result<ImportResult>,
        ): Result<ImportResult> {
            return withContext(ioDispatcher) {
                runCatching {
                    val inputStream =
                        context.contentResolver.openInputStream(uri)
                            ?: throw IOException("Could not open backup input stream")
                    inputStream.use { stream ->
                        importBlock(stream).getOrThrow()
                    }
                }
            }
        }

        fun importFromUri(
            uri: Uri,
            action: BackupImportPickAction,
        ) = viewModelScope.launch {
            when (action) {
                BackupImportPickAction.ImportMerge -> {
                    importBackupFromUri(uri) { stream ->
                        importRulesUseCase.mergeRulesFromStream(stream)
                    }.fold(
                        onSuccess = { result ->
                            postUserMessage(
                                context.resources.getQuantityString(
                                    R.plurals.settings_import_merge_success,
                                    result.rulesAdded,
                                    result.rulesAdded,
                                    result.rulesUpdated,
                                ),
                            )
                        },
                        onFailure = {
                            DiagnosticLog.record(context, "Backup merge import failed", it)
                            if (it is InvalidBackupRuleRegexException) {
                                postUserMessage(
                                    context.getString(
                                        R.string.settings_backup_invalid_rule_regex,
                                        it.ruleNames.joinToString(),
                                    ),
                                )
                            } else {
                                postUserMessage(
                                    context.getString(
                                        R.string.settings_backup_import_failed,
                                        it.message.orEmpty(),
                                    ),
                                )
                            }
                        },
                    )
                }

                BackupImportPickAction.RestoreFull -> {
                    importBackupFromUri(uri) { stream ->
                        importRulesUseCase.restoreFromBackupStream(stream)
                    }.fold(
                        onSuccess = { result ->
                            runCatching {
                                val restoredPreferences = userPreferencesRepository.getPreferencesSnapshot()
                                val backupDestinations =
                                    listOf(
                                        restoredPreferences.exportFolderUri,
                                        restoredPreferences.cloudExportFolderUri,
                                    ).filter { destination -> destination.isNotBlank() }
                                if (restoredPreferences.scheduledExportEnabled && backupDestinations.isNotEmpty()) {
                                    enqueueScheduledExportWork()
                                } else {
                                    workManager.cancelUniqueWork(ScheduledRulesExportWorker.WORK_NAME)
                                }
                            }.onFailure { error ->
                                DiagnosticLog.record(context, "Restored backup could not reconcile scheduled exports", error)
                            }
                            runCatching { updateCheckWorkScheduler.syncFromPreferences() }
                                .onFailure { error ->
                                    DiagnosticLog.record(context, "Restored backup could not reconcile update checks", error)
                                }
                            val parts =
                                buildList {
                                    add(
                                        context.resources.getQuantityString(
                                            R.plurals.settings_restore_part_rules,
                                            result.rulesImported,
                                            result.rulesImported,
                                        ),
                                    )
                                    if (result.historyRunsImported > 0) {
                                        add(
                                            context.resources.getQuantityString(
                                                R.plurals.settings_restore_part_history_runs,
                                                result.historyRunsImported,
                                                result.historyRunsImported,
                                            ),
                                        )
                                    }
                                    if (result.settingsRestored) {
                                        add(context.getString(R.string.settings_restore_part_settings))
                                    }
                                }
                            postUserMessage(
                                context.getString(
                                    R.string.settings_restore_success,
                                    parts.joinToString(", "),
                                ),
                            )
                            if (result.foldersNeedingReselection > 0) {
                                postUserMessage(
                                    context.resources.getQuantityString(
                                        R.plurals.settings_restore_folders_need_reselection,
                                        result.foldersNeedingReselection,
                                        result.foldersNeedingReselection,
                                    ),
                                )
                            }
                            if (result.automationsDisabled) {
                                postUserMessage(context.getString(R.string.settings_restore_automation_disabled_no_folder))
                            }
                        },
                        onFailure = {
                            DiagnosticLog.record(context, "Full backup restore failed", it)
                            if (it is InvalidBackupRuleRegexException) {
                                postUserMessage(
                                    context.getString(
                                        R.string.settings_backup_invalid_rule_regex,
                                        it.ruleNames.joinToString(),
                                    ),
                                )
                            } else {
                                postUserMessage(
                                    context.getString(
                                        R.string.settings_backup_restore_failed,
                                        it.message.orEmpty(),
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }

        fun openAppNotificationSettings() {
            val intent =
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }

        fun openManageAllFilesAccessSettings() {
            val manageIntent =
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:${context.packageName}".toUri()
                }
            manageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(manageIntent) }
        }

        fun importCustomFont(uri: Uri) {
            viewModelScope.launch(ioDispatcher) {
                when (val result = CustomFontStorage.importFromUri(context, uri)) {
                    is CustomFontStorage.ImportResult.Success -> {
                        userPreferencesRepository.setCustomFont(result.path, result.displayName)
                        postUserMessage(context.getString(R.string.settings_custom_font_success))
                    }

                    CustomFontStorage.ImportResult.InvalidFont -> {
                        postUserMessage(context.getString(R.string.settings_custom_font_error_invalid))
                    }
                }
            }
        }

        fun clearCustomFont() {
            viewModelScope.launch(ioDispatcher) {
                userPreferencesRepository.clearCustomFont()
                postUserMessage(context.getString(R.string.settings_custom_font_reset_success))
            }
        }
    }
