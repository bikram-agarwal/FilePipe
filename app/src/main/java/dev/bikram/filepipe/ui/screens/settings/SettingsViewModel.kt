package dev.bikram.filepipe.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.BuildConfig
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
import dev.bikram.filepipe.data.storage.isFilesystemFolderPathString
import dev.bikram.filepipe.data.storage.treeUriFromDocumentUri
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.usecase.BackupImportPickAction
import dev.bikram.filepipe.domain.usecase.ExportRulesUseCase
import dev.bikram.filepipe.domain.usecase.ImportRulesUseCase
import dev.bikram.filepipe.domain.usecase.RulesAutoExportTrigger
import dev.bikram.filepipe.update.AppReviewLauncher
import dev.bikram.filepipe.update.FILEPIPE_UPDATE_APK_CACHE_NAME
import dev.bikram.filepipe.update.PlayInAppUpdateBannerUiState
import dev.bikram.filepipe.update.PlayInAppUpdateProgressController
import dev.bikram.filepipe.update.PlayInAppUpdateStarter
import dev.bikram.filepipe.update.PlayUpdateSessionHandle
import dev.bikram.filepipe.update.UpdateAvailableNotifier
import dev.bikram.filepipe.update.UpdateCheckWorkScheduler
import dev.bikram.filepipe.update.UpdateChecker
import dev.bikram.filepipe.update.UpdateInfo
import dev.bikram.filepipe.update.copyUpdateApkToMediaStoreDownloads
import dev.bikram.filepipe.update.notificationDedupeKey
import dev.bikram.filepipe.worker.ScheduledRulesExportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        private val updateChecker: UpdateChecker,
        private val playUpdateSessionHandle: PlayUpdateSessionHandle,
        private val playInAppUpdateStarter: PlayInAppUpdateStarter,
        private val playInAppUpdateProgressController: PlayInAppUpdateProgressController,
        private val appReviewLauncher: AppReviewLauncher,
        private val updateAvailableNotifier: UpdateAvailableNotifier,
        private val updateCheckWorkScheduler: UpdateCheckWorkScheduler,
        private val ruleRepository: RuleRepository,
    ) : ViewModel() {
        private val updateMocksAvailable = BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "devRelease"

        private enum class DevReleasePlayBannerMockStage {
            OFF,
            STARTING,
            DOWNLOADING,
            READY,
        }

        private val devReleasePlayBannerMockStage =
            MutableStateFlow(DevReleasePlayBannerMockStage.OFF)
        private var devReleasePlayBannerMockSequenceJob: Job? = null

        val preferencesFlow = userPreferencesRepository.preferencesFlow
        val preferencesState: StateFlow<AppPreferences?> =
            preferencesFlow
                .map<AppPreferences, AppPreferences?> { preferences -> preferences }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)
        val developerOptionsEnabledFlow = userPreferencesRepository.developerOptionsEnabledFlow

        private val _userMessage = MutableStateFlow<String?>(null)
        val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

        private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
        val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

        private val _isCheckingUpdate = MutableStateFlow(false)
        val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

        /**
         * null = not downloading. 0f..100f = determinate progress. -2f = indeterminate download (unknown size).
         * -1f = installing (package installer).
         */
        private val _downloadProgress = MutableStateFlow<Float?>(null)
        val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

        private val _updateSheetChangelog = MutableStateFlow<ChangelogUiState>(ChangelogUiState.Hidden)
        val updateSheetChangelog: StateFlow<ChangelogUiState> = _updateSheetChangelog.asStateFlow()

        private val _manualUpdateNoResult = MutableStateFlow(false)
        val manualUpdateNoResult: StateFlow<Boolean> = _manualUpdateNoResult.asStateFlow()

        private val _openUpdateSheetFromNotification = MutableStateFlow(false)
        val openUpdateSheetFromNotification: StateFlow<Boolean> = _openUpdateSheetFromNotification.asStateFlow()

        private val _openUpdateSheetFromRulesPromo = MutableStateFlow(false)
        val openUpdateSheetFromRulesPromo: StateFlow<Boolean> = _openUpdateSheetFromRulesPromo.asStateFlow()

        private val _startPlayInAppUpdateAfterRulesPromoSheet = MutableStateFlow(false)
        val startPlayInAppUpdateAfterRulesPromoSheet: StateFlow<Boolean> =
            _startPlayInAppUpdateAfterRulesPromoSheet.asStateFlow()

        private val _updatePromoBannerDismissedThisSession = MutableStateFlow(false)
        val updatePromoBannerDismissedThisSession: StateFlow<Boolean> =
            _updatePromoBannerDismissedThisSession.asStateFlow()

        val playInAppUpdateBannerUiState: StateFlow<PlayInAppUpdateBannerUiState> =
            if (updateMocksAvailable) {
                combine(
                    playInAppUpdateProgressController.bannerUiState,
                    devReleasePlayBannerMockStage,
                ) { realState, mockStage ->
                    mergeDevReleasePlayBannerMock(realState, mockStage)
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue =
                        mergeDevReleasePlayBannerMock(
                            playInAppUpdateProgressController.bannerUiState.value,
                            devReleasePlayBannerMockStage.value,
                        ),
                )
            } else {
                playInAppUpdateProgressController.bannerUiState
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

        fun clearUserMessage() {
            _userMessage.value = null
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
            withContext(Dispatchers.IO) {
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
                persistBackupFolderUri(uriString)
                userPreferencesRepository.setExportFolderUri(uriString)
                disableAutomationsIfNoBackupDestination()
            }

        fun setCloudExportFolderUri(uriString: String) =
            viewModelScope.launch {
                persistBackupFolderUri(uriString)
                userPreferencesRepository.setCloudExportFolderUri(uriString)
                disableAutomationsIfNoBackupDestination()
            }

        private suspend fun persistBackupFolderUri(uriString: String) {
            if (uriString.startsWith("content://")) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uriString.toUri(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
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
                    _userMessage.value = context.getString(R.string.settings_export_select_folder_first)
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
                    _userMessage.value = context.getString(R.string.settings_export_select_folder_first)
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

        fun flagOpenUpdateSheetFromNotification() {
            _openUpdateSheetFromNotification.value = true
        }

        fun consumeOpenUpdateSheetFromNotification() {
            _openUpdateSheetFromNotification.value = false
        }

        fun flagOpenUpdateSheetFromRulesPromo() {
            if (BuildConfig.USE_PLAY_IN_APP_UPDATES) {
                _startPlayInAppUpdateAfterRulesPromoSheet.value = true
            } else {
                _openUpdateSheetFromRulesPromo.value = true
            }
        }

        fun consumeOpenUpdateSheetFromRulesPromo() {
            _openUpdateSheetFromRulesPromo.value = false
        }

        fun consumeStartPlayInAppUpdateAfterRulesPromoSheet() {
            _startPlayInAppUpdateAfterRulesPromoSheet.value = false
        }

        fun dismissUpdatePromoBanner() {
            _updatePromoBannerDismissedThisSession.value = true
            if (_updateInfo.value?.isDevReleaseMock == true) {
                _updateInfo.value = null
            }
        }

        /**
         * Dev release: arms the global update promo (Rules / History / Settings). Swipe the card to dismiss.
         */
        fun devReleaseMockArmRulesUpdatePromoForRulesTab() {
            if (!updateMocksAvailable || !BuildConfig.SHOW_UPDATES) return
            _updateInfo.value =
                UpdateInfo(
                    versionName = "9.9.9",
                    downloadUrl = "",
                    releaseNotes = "",
                    remoteApkAssetUpdatedAt =
                        if (BuildConfig.FLAVOR == "github") {
                            DEV_RELEASE_MOCK_GITHUB_ASSET_UPDATED_AT
                        } else {
                            ""
                        },
                    isDevReleaseMock = true,
                )
            _updatePromoBannerDismissedThisSession.value = false
        }

        /**
         * Dev release: global Play-style banner as downloading, then ready to install (all flavors; GitHub uses no-op real).
         */
        fun devReleaseMockStartPlayUpdateBannerSequence() {
            if (!updateMocksAvailable) return
            devReleasePlayBannerMockSequenceJob?.cancel()
            devReleasePlayBannerMockSequenceJob =
                viewModelScope.launch {
                    devReleasePlayBannerMockStage.value = DevReleasePlayBannerMockStage.STARTING
                    delay(1_200L)
                    if (!isActive) return@launch
                    devReleasePlayBannerMockStage.value = DevReleasePlayBannerMockStage.DOWNLOADING
                    delay(2_500L)
                    if (!isActive) return@launch
                    devReleasePlayBannerMockStage.value = DevReleasePlayBannerMockStage.READY
                }
        }

        fun onPlayInAppUpdateUserCanceled() {
            _userMessage.value = context.getString(R.string.settings_play_in_app_update_canceled)
        }

        fun completePlayFlexibleUpdateIfReady(activity: Activity?) {
            if (updateMocksAvailable &&
                devReleasePlayBannerMockStage.value == DevReleasePlayBannerMockStage.READY
            ) {
                devReleasePlayBannerMockSequenceJob?.cancel()
                devReleasePlayBannerMockStage.value = DevReleasePlayBannerMockStage.OFF
                if (_updateInfo.value?.isDevReleaseMock == true) {
                    _updateInfo.value = null
                    _updatePromoBannerDismissedThisSession.value = false
                }
                return
            }
            if (activity == null) return
            playInAppUpdateProgressController.completeFlexibleUpdateIfReady(activity)
        }

        fun launchPlayInAppReviewFromSettings(
            activity: ComponentActivity?,
            onFlowFinished: () -> Unit,
        ) {
            val hostActivity = activity ?: return
            appReviewLauncher.tryLaunchInAppReview(hostActivity, onFlowFinished)
        }

        fun markPlayAutoReviewPromptHandledForCurrentInstall(lastUpdateTimeMillis: Long) =
            viewModelScope.launch {
                userPreferencesRepository.setPlayAutoReviewPromptedForLastUpdateTime(lastUpdateTimeMillis)
            }

        fun setInAppReviewAutoNeverAskAgain(neverAskAgain: Boolean) =
            viewModelScope.launch {
                userPreferencesRepository.setInAppReviewAutoNeverAskAgain(neverAskAgain)
            }

        fun skipAcknowledgedGithubRelease(updateInfo: UpdateInfo) =
            viewModelScope.launch {
                if (BuildConfig.FLAVOR != "github") return@launch
                if (updateInfo.remoteApkAssetUpdatedAt.isBlank()) return@launch
                userPreferencesRepository.writeGithubReleaseAck(
                    fingerprint = updateInfo.notificationDedupeKey(),
                    installedVersionName = BuildConfig.VERSION_NAME,
                )
                _updateInfo.value = null
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
                        val prefs = userPreferencesRepository.getPreferencesSnapshot()
                        if (prefs.exportFolderUri.isBlank()) {
                            val treeUri = treeUriFromDocumentUri(context, targetUri)
                            if (treeUri != null) {
                                persistBackupFolderUri(treeUri.toString())
                                userPreferencesRepository.setExportFolderUri(treeUri.toString())
                            }
                        }
                        _userMessage.value = context.getString(R.string.settings_export_success, displayName)
                    },
                    onFailure = { err ->
                        DiagnosticLog.record(context, "Manual backup export failed", err)
                        _userMessage.value = "Export failed: ${err.message}"
                    },
                )
            }

        fun completeCloudBackupDocumentSelection(targetUri: Uri) =
            viewModelScope.launch {
                persistBackupFolderUri(targetUri.toString())
                userPreferencesRepository.setCloudExportFolderUri(targetUri.toString())
                exportRulesUseCase.exportBackupJsonToDocumentUri(targetUri).fold(
                    onSuccess = {
                        val providerName = providerDisplayName(targetUri.authority)
                        _userMessage.value =
                            if (providerName != null) {
                                context.getString(R.string.settings_backup_export_success_to, providerName)
                            } else {
                                context.getString(R.string.settings_backup_export_success)
                            }
                    },
                    onFailure = { err ->
                        DiagnosticLog.record(context, "Cloud backup export failed", err)
                        _userMessage.value =
                            context.getString(
                                R.string.settings_backup_export_failed,
                                err.message.orEmpty(),
                            )
                    },
                )
            }

        private fun providerDisplayName(authority: String?): String? {
            val providerAuthority = authority?.takeIf { it.isNotBlank() } ?: return null
            val normalizedAuthority = providerAuthority.lowercase()
            return when {
                normalizedAuthority.contains("google.android.apps.docs") -> {
                    context.getString(R.string.cloud_provider_google_drive)
                }

                normalizedAuthority.contains("skydrive") || normalizedAuthority.contains("onedrive") -> {
                    context.getString(R.string.cloud_provider_onedrive)
                }

                normalizedAuthority.contains("dropbox") -> {
                    context.getString(R.string.cloud_provider_dropbox)
                }

                normalizedAuthority.contains("box.android") -> {
                    context.getString(R.string.cloud_provider_box)
                }

                else -> {
                    null
                }
            }
        }

        private fun defaultManualExportFileName(): String {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
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
                    _userMessage.value = context.getString(R.string.settings_export_select_folder_first)
                    return@launch
                }
                exportRulesUseCase.exportRulesToTreeUris(backupDestinations).fold(
                    onSuccess = { fileNames ->
                        DiagnosticLog.record(
                            context,
                            "Configured backup export completed: destinations=${backupDestinations.size}, files=${fileNames.size}",
                        )
                        _userMessage.value =
                            context.resources.getQuantityString(
                                R.plurals.settings_backup_exported_to_destinations,
                                fileNames.size,
                                fileNames.size,
                            )
                    },
                    onFailure = { error ->
                        _userMessage.value = context.getString(R.string.settings_backup_export_failed, error.message.orEmpty())
                        DiagnosticLog.record(context, "Configured backup export failed: destinations=${backupDestinations.size}", error)
                    },
                )
            }

        fun importFromUri(
            uri: Uri,
            action: BackupImportPickAction,
        ) = viewModelScope.launch {
            val text =
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.readBytes().decodeToString()
                        }
                    }
                }.onFailure { error ->
                    DiagnosticLog.record(context, "Backup file read failed for $action", error)
                }.getOrNull() ?: run {
                    _userMessage.value = "Could not read file"
                    return@launch
                }
            when (action) {
                BackupImportPickAction.ImportMerge -> {
                    importRulesUseCase.mergeRulesFromJson(text).fold(
                        onSuccess = { result ->
                            _userMessage.value =
                                context.resources.getQuantityString(
                                    R.plurals.settings_import_merge_success,
                                    result.rulesAdded,
                                    result.rulesAdded,
                                    result.rulesUpdated,
                                )
                        },
                        onFailure = {
                            DiagnosticLog.record(context, "Backup merge import failed", it)
                            _userMessage.value = "Import failed: ${it.message}"
                        },
                    )
                }

                BackupImportPickAction.RestoreFull -> {
                    importRulesUseCase.restoreFromBackupJson(text).fold(
                        onSuccess = { result ->
                            val parts =
                                buildList {
                                    add("${result.rulesImported} rules")
                                    if (result.historyRunsImported > 0) {
                                        add("${result.historyRunsImported} history runs")
                                    }
                                    if (result.settingsRestored) add("settings")
                                }
                            _userMessage.value =
                                context.getString(
                                    R.string.settings_restore_success,
                                    parts.joinToString(", "),
                                )
                        },
                        onFailure = {
                            DiagnosticLog.record(context, "Full backup restore failed", it)
                            _userMessage.value = "Restore failed: ${it.message}"
                        },
                    )
                }
            }
        }

        fun checkForUpdate(silent: Boolean = false) =
            viewModelScope.launch {
                _isCheckingUpdate.value = true
                _downloadProgress.value = null
                val checked = runCatching { updateChecker.checkForUpdate() }
                _isCheckingUpdate.value = false
                val info =
                    checked
                        .onFailure { error ->
                            DiagnosticLog.record(context, "Manual update check failed", error)
                            if (!silent) {
                                _userMessage.value = error.message ?: context.getString(R.string.settings_update_check_failed)
                            }
                        }.getOrNull()
                if (checked.isFailure) return@launch
                if (info != null) {
                    _updateInfo.value = info
                    _updatePromoBannerDismissedThisSession.value = false
                    if (BuildConfig.USE_PLAY_IN_APP_UPDATES && info.isPlayStoreUpdateInProgress) {
                        playInAppUpdateProgressController.ensureInstallStateListenerRegistered()
                    }
                    if (BuildConfig.SHOW_UPDATES) {
                        val prefsSnapshot = userPreferencesRepository.getPreferencesSnapshot()
                        updateAvailableNotifier.notifyIfNewUpdateAvailable(info, prefsSnapshot)
                    }
                } else {
                    _updateInfo.value = null
                    if (!silent) {
                        _userMessage.value = null
                        yield()
                        _userMessage.value = context.getString(R.string.settings_up_to_date)
                    }
                }
            }

        /**
         * Manual flow from the update sheet: sets checking immediately on the main thread, then runs the check.
         * No snackbar; the sheet shows up-to-date vs available.
         */
        fun beginManualUpdateCheckFromSheet() {
            _isCheckingUpdate.value = true
            _downloadProgress.value = null
            _manualUpdateNoResult.value = false
            viewModelScope.launch {
                val checked = runCatching { updateChecker.checkForUpdate() }
                _isCheckingUpdate.value = false
                val info =
                    checked
                        .onFailure { error ->
                            DiagnosticLog.record(context, "Update sheet check failed", error)
                            _manualUpdateNoResult.value = true
                        }.getOrNull()
                if (checked.isFailure) return@launch
                if (info != null) {
                    _updateInfo.value = info
                    _updatePromoBannerDismissedThisSession.value = false
                    if (BuildConfig.USE_PLAY_IN_APP_UPDATES && info.isPlayStoreUpdateInProgress) {
                        playInAppUpdateProgressController.ensureInstallStateListenerRegistered()
                    }
                    if (BuildConfig.SHOW_UPDATES) {
                        val prefsSnapshot = userPreferencesRepository.getPreferencesSnapshot()
                        updateAvailableNotifier.notifyIfNewUpdateAvailable(info, prefsSnapshot)
                    }
                } else {
                    _updateInfo.value = null
                    _manualUpdateNoResult.value = true
                }
            }
        }

        fun loadChangelogForUpdateSheet() =
            viewModelScope.launch {
                if (BuildConfig.CHANGELOG_GITHUB_REPO.isBlank()) {
                    _updateSheetChangelog.value =
                        ChangelogUiState.Failed(
                            context.getString(R.string.settings_changelog_load_failed),
                        )
                    return@launch
                }
                _updateSheetChangelog.value = ChangelogUiState.Loading
                val loaded =
                    withContext(Dispatchers.IO) {
                        runCatching { fetchRawChangelog() }
                    }
                _updateSheetChangelog.value =
                    loaded.fold(
                        onSuccess = { ChangelogUiState.Ready(it) },
                        onFailure = {
                            DiagnosticLog.record(context, "Update changelog load failed", it)
                            ChangelogUiState.Failed(
                                it.message ?: context.getString(R.string.settings_changelog_load_failed),
                            )
                        },
                    )
            }

        fun dismissUpdateSheet() {
            val bannerState = resolvePlayBannerUiStateForSessionLogic()
            val blocksPendingClear =
                bannerState is PlayInAppUpdateBannerUiState.Downloading ||
                    bannerState is PlayInAppUpdateBannerUiState.ReadyToInstall
            if (!blocksPendingClear) {
                playUpdateSessionHandle.clearPendingPlayUpdate()
            }
            _updateSheetChangelog.value = ChangelogUiState.Hidden
            _manualUpdateNoResult.value = false
        }

        fun tryStartPlayInAppUpdate(
            activity: ComponentActivity?,
            launcher: ActivityResultLauncher<IntentSenderRequest>,
        ): Boolean {
            if (!BuildConfig.USE_PLAY_IN_APP_UPDATES) return false
            if (activity == null) {
                _userMessage.value = context.getString(R.string.settings_play_in_app_update_failed)
                return false
            }
            val started = playInAppUpdateStarter.startUpdateIfPending(activity, launcher)
            if (!started) {
                _userMessage.value = context.getString(R.string.settings_play_in_app_update_failed)
            }
            return started
        }

        fun downloadAndInstall(updateInfo: UpdateInfo) =
            viewModelScope.launch {
                val downloadUrl = updateInfo.downloadUrl
                _downloadProgress.value = 0f
                val result =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val connection = URL(downloadUrl).openConnection() as HttpURLConnection
                            connection.instanceFollowRedirects = true
                            try {
                                connection.connect()
                                val contentLength = connection.contentLength
                                userPreferencesRepository.clearUpdateApkDownloadsCopySucceeded()
                                val file = File(context.cacheDir, FILEPIPE_UPDATE_APK_CACHE_NAME)
                                connection.inputStream.use { input ->
                                    file.outputStream().use { output ->
                                        val buffer = ByteArray(8192)
                                        var totalRead = 0L
                                        var readCount: Int
                                        if (contentLength > 0) {
                                            while (input.read(buffer).also { readCount = it } != -1) {
                                                output.write(buffer, 0, readCount)
                                                totalRead += readCount
                                                val percent = (100f * totalRead / contentLength).coerceIn(0f, 100f)
                                                _downloadProgress.value = percent
                                            }
                                        } else {
                                            _downloadProgress.value = -2f
                                            while (input.read(buffer).also { readCount = it } != -1) {
                                                output.write(buffer, 0, readCount)
                                            }
                                        }
                                    }
                                }
                                if (BuildConfig.FLAVOR == "github") {
                                    val prefs = userPreferencesRepository.getPreferencesSnapshot()
                                    if (prefs.saveUpdateApkToDownloads && updateInfo.remoteApkFileName.isNotBlank()) {
                                        val copyResult =
                                            copyUpdateApkToMediaStoreDownloads(
                                                context,
                                                file,
                                                updateInfo.remoteApkFileName,
                                            )
                                        if (copyResult.isFailure) {
                                            DiagnosticLog.record(
                                                context,
                                                "Update APK copy to Downloads failed",
                                                copyResult.exceptionOrNull(),
                                            )
                                            withContext(Dispatchers.Main) {
                                                _userMessage.value =
                                                    context.getString(
                                                        R.string.settings_update_apk_save_to_downloads_failed,
                                                    )
                                            }
                                        } else {
                                            userPreferencesRepository.markUpdateApkDownloadsCopySucceeded()
                                        }
                                    }
                                }
                                _downloadProgress.value = -1f
                                val uri =
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file,
                                    )
                                val installIntent =
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/vnd.android.package-archive")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                withContext(Dispatchers.Main) {
                                    context.startActivity(installIntent)
                                }
                                if (BuildConfig.FLAVOR == "github" && updateInfo.remoteApkAssetUpdatedAt.isNotBlank()) {
                                    userPreferencesRepository.writeGithubReleaseAck(
                                        fingerprint = updateInfo.notificationDedupeKey(),
                                        installedVersionName = BuildConfig.VERSION_NAME,
                                    )
                                }
                            } finally {
                                connection.disconnect()
                            }
                        }
                    }
                result.onFailure {
                    DiagnosticLog.record(context, "Update APK download/install failed", it)
                    _userMessage.value = "Download failed: ${it.message}"
                }
                _downloadProgress.value = null
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

        private fun fetchRawChangelog(): String {
            val repo = BuildConfig.CHANGELOG_GITHUB_REPO
            val branch = BuildConfig.CHANGELOG_GITHUB_BRANCH
            val connection =
                URL("https://raw.githubusercontent.com/$repo/$branch/docs/CHANGELOG.md").openConnection() as
                    HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            return try {
                connection.connect()
                connection.inputStream.bufferedReader().use { reader -> reader.readText() }
            } finally {
                connection.disconnect()
            }
        }

        private fun mergeDevReleasePlayBannerMock(
            realState: PlayInAppUpdateBannerUiState,
            mockStage: DevReleasePlayBannerMockStage,
        ): PlayInAppUpdateBannerUiState =
            when (mockStage) {
                DevReleasePlayBannerMockStage.OFF -> {
                    realState
                }

                DevReleasePlayBannerMockStage.STARTING -> {
                    PlayInAppUpdateBannerUiState.Downloading(
                        bytesDownloaded = 0L,
                        totalBytesToDownload = 0L,
                        indeterminateProgress = true,
                    )
                }

                DevReleasePlayBannerMockStage.DOWNLOADING -> {
                    PlayInAppUpdateBannerUiState.Downloading(
                        bytesDownloaded = MOCK_PLAY_UPDATE_BYTES_DOWNLOADED,
                        totalBytesToDownload = MOCK_PLAY_UPDATE_BYTES_TOTAL,
                        indeterminateProgress = false,
                    )
                }

                DevReleasePlayBannerMockStage.READY -> {
                    PlayInAppUpdateBannerUiState.ReadyToInstall
                }
            }

        private fun resolvePlayBannerUiStateForSessionLogic(): PlayInAppUpdateBannerUiState {
            if (!updateMocksAvailable) {
                return playInAppUpdateProgressController.bannerUiState.value
            }
            return mergeDevReleasePlayBannerMock(
                playInAppUpdateProgressController.bannerUiState.value,
                devReleasePlayBannerMockStage.value,
            )
        }

        override fun onCleared() {
            devReleasePlayBannerMockSequenceJob?.cancel()
            super.onCleared()
        }

        companion object {
            private const val MOCK_PLAY_UPDATE_BYTES_DOWNLOADED: Long = 3_000_000L
            private const val MOCK_PLAY_UPDATE_BYTES_TOTAL: Long = 10_000_000L

            /** Placeholder so GitHub update sheet shows Skip version for dev mocks. */
            private const val DEV_RELEASE_MOCK_GITHUB_ASSET_UPDATED_AT = "2000-01-01T00:00:00Z"
        }
    }

sealed class ChangelogUiState {
    data object Hidden : ChangelogUiState()

    data object Loading : ChangelogUiState()

    data class Ready(
        val text: String,
    ) : ChangelogUiState()

    data class Failed(
        val message: String,
    ) : ChangelogUiState()
}
