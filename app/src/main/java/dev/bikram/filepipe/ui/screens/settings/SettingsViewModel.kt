package dev.bikram.filepipe.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.bikram.filepipe.data.preferences.AppColorSource
import dev.bikram.filepipe.data.preferences.FolderAccessMode
import dev.bikram.filepipe.data.preferences.AppThemeMode
import dev.bikram.filepipe.data.preferences.SwipeAction
import dev.bikram.filepipe.data.preferences.ThemePaletteStyle
import dev.bikram.filepipe.data.preferences.UpdateCheckSchedule
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.R
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.domain.usecase.ExportRulesUseCase
import dev.bikram.filepipe.domain.usecase.BackupImportPickAction
import dev.bikram.filepipe.domain.usecase.ImportRulesUseCase
import dev.bikram.filepipe.domain.usecase.RulesAutoExportTrigger
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import dev.bikram.filepipe.update.FILEPIPE_UPDATE_APK_CACHE_NAME
import dev.bikram.filepipe.update.PlayInAppUpdateBannerUiState
import dev.bikram.filepipe.update.PlayInAppUpdateProgressController
import dev.bikram.filepipe.update.AppReviewLauncher
import dev.bikram.filepipe.update.PlayInAppUpdateStarter
import dev.bikram.filepipe.update.PlayUpdateSessionHandle
import dev.bikram.filepipe.update.UpdateAvailableNotifier
import dev.bikram.filepipe.update.UpdateCheckWorkScheduler
import dev.bikram.filepipe.update.UpdateChecker
import dev.bikram.filepipe.update.UpdateInfo
import dev.bikram.filepipe.update.notificationDedupeKey
import dev.bikram.filepipe.update.copyUpdateApkToMediaStoreDownloads
import dev.bikram.filepipe.worker.ScheduledRulesExportWorker
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.data.storage.isFilesystemFolderPathString
import dev.bikram.filepipe.data.storage.treeUriFromDocumentUri
import dev.bikram.filepipe.domain.model.Rule
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** Used from Help to scroll the settings list to a section after navigation. */
enum class SettingsBringIntoViewSection {
    None,
    FolderAccess,
    Notifications
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
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
    private val ruleRepository: RuleRepository
) : ViewModel() {

    private val isDevReleaseBuild = BuildConfig.BUILD_TYPE == "devRelease"

    private enum class DevReleasePlayBannerMockStage {
        OFF,
        DOWNLOADING,
        READY
    }

    private val devReleasePlayBannerMockStage =
        MutableStateFlow(DevReleasePlayBannerMockStage.OFF)
    private var devReleasePlayBannerMockSequenceJob: Job? = null

    val preferencesFlow = userPreferencesRepository.preferencesFlow

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
        if (isDevReleaseBuild) {
            combine(
                playInAppUpdateProgressController.bannerUiState,
                devReleasePlayBannerMockStage
            ) { realState, mockStage ->
                mergeDevReleasePlayBannerMock(realState, mockStage)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = mergeDevReleasePlayBannerMock(
                    playInAppUpdateProgressController.bannerUiState.value,
                    devReleasePlayBannerMockStage.value
                )
            )
        } else {
            playInAppUpdateProgressController.bannerUiState
        }

    private val _manualExportPickerRequested = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val manualExportPickerRequested = _manualExportPickerRequested.asSharedFlow()

    private val _bringIntoViewSection = MutableStateFlow(SettingsBringIntoViewSection.None)
    val bringIntoViewSection: StateFlow<SettingsBringIntoViewSection> = _bringIntoViewSection.asStateFlow()

    private val _folderAccessSectionHighlight = MutableStateFlow(false)
    val folderAccessSectionHighlight: StateFlow<Boolean> = _folderAccessSectionHighlight.asStateFlow()

    private val _notificationsSectionHighlight = MutableStateFlow(false)
    val notificationsSectionHighlight: StateFlow<Boolean> = _notificationsSectionHighlight.asStateFlow()

    fun requestBringSettingsSectionIntoView(section: SettingsBringIntoViewSection) {
        if (section != SettingsBringIntoViewSection.None) {
            _bringIntoViewSection.value = section
        }
    }

    fun clearBringIntoViewSectionRequest() {
        _bringIntoViewSection.value = SettingsBringIntoViewSection.None
    }

    fun requestFolderAccessSectionHighlight() {
        _folderAccessSectionHighlight.value = true
    }

    fun clearFolderAccessSectionHighlight() {
        _folderAccessSectionHighlight.value = false
    }

    fun requestNotificationsSectionHighlight() {
        _notificationsSectionHighlight.value = true
    }

    fun clearNotificationsSectionHighlight() {
        _notificationsSectionHighlight.value = false
    }

    init {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.getPreferencesSnapshot()
            if (prefs.scheduledExportEnabled && prefs.exportFolderUri.isNotBlank()) {
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
    suspend fun countRulesUsingFilesystemFolderPaths(): Int = withContext(Dispatchers.IO) {
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

    fun markIntroSeen(onComplete: () -> Unit = {}) = viewModelScope.launch {
        userPreferencesRepository.markIntroSeen()
        onComplete()
    }

    fun setThemeMode(mode: AppThemeMode) = viewModelScope.launch {
        userPreferencesRepository.setThemeMode(mode)
    }

    fun setColorSource(source: AppColorSource) = viewModelScope.launch {
        userPreferencesRepository.setColorSource(source)
    }

    fun addCustomSeedHex(hex: String) = viewModelScope.launch {
        userPreferencesRepository.addCustomSeedHex(hex)
    }

    fun selectCustomSeedHex(hex: String) = viewModelScope.launch {
        userPreferencesRepository.selectCustomSeedHex(hex)
    }

    fun removeCustomSeedHex(hex: String) = viewModelScope.launch {
        userPreferencesRepository.removeCustomSeedHex(hex)
    }

    fun setThemePaletteStyle(style: ThemePaletteStyle) = viewModelScope.launch {
        userPreferencesRepository.setThemePaletteStyle(style)
    }

    fun setExportFolderUri(uriString: String) = viewModelScope.launch {
        persistExportFolderUri(uriString)
    }

    private suspend fun persistExportFolderUri(uriString: String) {
        if (uriString.startsWith("content://")) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    Uri.parse(uriString),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        userPreferencesRepository.setExportFolderUri(uriString)
        if (uriString.isBlank()) {
            userPreferencesRepository.setAutoExportOnRuleChange(false)
            userPreferencesRepository.setScheduledExportEnabled(false)
            workManager.cancelUniqueWork(SCHEDULED_EXPORT_WORK_NAME)
        }
    }

    fun setAutoExportOnChange(enabled: Boolean) = viewModelScope.launch {
        if (enabled && userPreferencesRepository.getPreferencesSnapshot().exportFolderUri.isBlank()) {
            _userMessage.value = context.getString(R.string.settings_export_select_folder_first)
            return@launch
        }
        userPreferencesRepository.setAutoExportOnRuleChange(enabled)
        if (enabled) {
            rulesAutoExportTrigger.maybeExportAfterRuleChange()
        }
    }

    fun setScheduledExportEnabled(enabled: Boolean) = viewModelScope.launch {
        if (enabled && userPreferencesRepository.getPreferencesSnapshot().exportFolderUri.isBlank()) {
            _userMessage.value = context.getString(R.string.settings_export_select_folder_first)
            return@launch
        }
        userPreferencesRepository.setScheduledExportEnabled(enabled)
        if (enabled) {
            enqueueScheduledExportWork()
        } else {
            workManager.cancelUniqueWork(SCHEDULED_EXPORT_WORK_NAME)
        }
    }

    private fun enqueueScheduledExportWork() {
        val request = PeriodicWorkRequestBuilder<ScheduledRulesExportWorker>(1, TimeUnit.DAYS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            SCHEDULED_EXPORT_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun setLogRetentionDays(days: Int) = viewModelScope.launch {
        userPreferencesRepository.setLogRetentionDays(days)
    }

    fun setSwipeStartToEnd(action: SwipeAction) = viewModelScope.launch {
        userPreferencesRepository.setSwipeStartToEnd(action)
    }

    fun setSwipeEndToStart(action: SwipeAction) = viewModelScope.launch {
        userPreferencesRepository.setSwipeEndToStart(action)
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setHapticFeedbackEnabled(enabled)
    }

    fun setProgressiveBlurEnabled(enabled: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setProgressiveBlurEnabled(enabled)
    }

    fun setUseGradientBackground(enabled: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setUseGradientBackground(enabled)
    }

    fun setUseFixedCardColors(enabled: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setUseFixedCardColors(enabled)
    }

    fun setUpdateCheckSchedule(schedule: UpdateCheckSchedule) = viewModelScope.launch {
        userPreferencesRepository.setUpdateCheckSchedule(schedule)
        if (schedule == UpdateCheckSchedule.NEVER) {
            userPreferencesRepository.setNotifyOnNewUpdates(false)
        }
        updateCheckWorkScheduler.syncFromPreferences()
    }

    fun setNotifyOnNewUpdates(enabled: Boolean) = viewModelScope.launch {
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
        if (!isDevReleaseBuild || !BuildConfig.SHOW_UPDATES) return
        _updateInfo.value = UpdateInfo(
            versionName = "9.9.9",
            downloadUrl = "",
            releaseNotes = "",
            remoteApkAssetUpdatedAt = if (BuildConfig.FLAVOR == "github") {
                DEV_RELEASE_MOCK_GITHUB_ASSET_UPDATED_AT
            } else {
                ""
            },
            isDevReleaseMock = true
        )
        _updatePromoBannerDismissedThisSession.value = false
    }

    /**
     * Dev release: global Play-style banner as downloading, then ready to install (all flavors; GitHub uses no-op real).
     */
    fun devReleaseMockStartPlayUpdateBannerSequence() {
        if (!isDevReleaseBuild) return
        devReleasePlayBannerMockSequenceJob?.cancel()
        devReleasePlayBannerMockSequenceJob = viewModelScope.launch {
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
        if (isDevReleaseBuild &&
            devReleasePlayBannerMockStage.value == DevReleasePlayBannerMockStage.READY
        ) {
            devReleasePlayBannerMockSequenceJob?.cancel()
            devReleasePlayBannerMockStage.value = DevReleasePlayBannerMockStage.OFF
            return
        }
        if (activity == null) return
        playInAppUpdateProgressController.completeFlexibleUpdateIfReady(activity)
    }

    fun launchPlayInAppReviewFromSettings(activity: ComponentActivity?, onFlowFinished: () -> Unit) {
        val hostActivity = activity ?: return
        appReviewLauncher.tryLaunchInAppReview(hostActivity, onFlowFinished)
    }

    fun markPlayAutoReviewPromptHandledForCurrentInstall(lastUpdateTimeMillis: Long) = viewModelScope.launch {
        userPreferencesRepository.setPlayAutoReviewPromptedForLastUpdateTime(lastUpdateTimeMillis)
    }

    fun setInAppReviewAutoNeverAskAgain(neverAskAgain: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setInAppReviewAutoNeverAskAgain(neverAskAgain)
    }

    fun skipAcknowledgedGithubRelease(updateInfo: UpdateInfo) = viewModelScope.launch {
        if (BuildConfig.FLAVOR != "github") return@launch
        if (updateInfo.remoteApkAssetUpdatedAt.isBlank()) return@launch
        userPreferencesRepository.writeGithubReleaseAck(
            fingerprint = updateInfo.notificationDedupeKey(),
            installedVersionName = BuildConfig.VERSION_NAME
        )
        _updateInfo.value = null
    }

    fun setSaveUpdateApkToDownloads(enabled: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setSaveUpdateApkToDownloads(enabled)
    }

    fun requestManualExportPicker() {
        _manualExportPickerRequested.tryEmit(defaultManualExportFileName())
    }

    fun completeManualExportToUri(targetUri: Uri) = viewModelScope.launch {
        exportRulesUseCase.exportBackupJsonToDocumentUri(targetUri).fold(
            onSuccess = { displayName ->
                val prefs = userPreferencesRepository.getPreferencesSnapshot()
                if (prefs.exportFolderUri.isBlank()) {
                    val treeUri = treeUriFromDocumentUri(context, targetUri)
                    if (treeUri != null) {
                        persistExportFolderUri(treeUri.toString())
                    }
                }
                _userMessage.value = context.getString(R.string.settings_export_success, displayName)
            },
            onFailure = { err ->
                _userMessage.value = "Export failed: ${err.message}"
            }
        )
    }

    private fun defaultManualExportFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "filepipe_backup_$stamp.json"
    }

    fun importFromUri(uri: Uri, action: BackupImportPickAction) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().decodeToString()
            }
        } ?: run {
            _userMessage.value = "Could not read file"
            return@launch
        }
        when (action) {
            BackupImportPickAction.ImportMerge ->
                importRulesUseCase.mergeRulesFromJson(text).fold(
                    onSuccess = { result ->
                        _userMessage.value = context.getString(
                            R.string.settings_import_merge_success,
                            result.rulesAdded,
                            result.rulesUpdated
                        )
                    },
                    onFailure = { _userMessage.value = "Import failed: ${it.message}" }
                )
            BackupImportPickAction.RestoreFull ->
                importRulesUseCase.restoreFromBackupJson(text).fold(
                    onSuccess = { result ->
                        val parts = buildList {
                            add("${result.rulesImported} rules")
                            if (result.historyRunsImported > 0) {
                                add("${result.historyRunsImported} history runs")
                            }
                            if (result.settingsRestored) add("settings")
                        }
                        _userMessage.value = context.getString(
                            R.string.settings_restore_success,
                            parts.joinToString(", ")
                        )
                    },
                    onFailure = { _userMessage.value = "Restore failed: ${it.message}" }
                )
        }
    }

    fun checkForUpdate(silent: Boolean = false) = viewModelScope.launch {
        _isCheckingUpdate.value = true
        _downloadProgress.value = null
        val info = updateChecker.checkForUpdate()
        _isCheckingUpdate.value = false
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
            val info = updateChecker.checkForUpdate()
            _isCheckingUpdate.value = false
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

    fun loadChangelogForUpdateSheet() = viewModelScope.launch {
        if (BuildConfig.CHANGELOG_GITHUB_REPO.isBlank()) {
            _updateSheetChangelog.value = ChangelogUiState.Failed(
                context.getString(R.string.settings_changelog_load_failed)
            )
            return@launch
        }
        _updateSheetChangelog.value = ChangelogUiState.Loading
        val loaded = withContext(Dispatchers.IO) {
            runCatching { fetchRawChangelog() }
        }
        _updateSheetChangelog.value = loaded.fold(
            onSuccess = { ChangelogUiState.Ready(it) },
            onFailure = {
                ChangelogUiState.Failed(
                    it.message ?: context.getString(R.string.settings_changelog_load_failed)
                )
            }
        )
    }

    fun dismissUpdateSheet() {
        val bannerState = resolvePlayBannerUiStateForSessionLogic()
        val blocksPendingClear = bannerState is PlayInAppUpdateBannerUiState.Downloading ||
            bannerState is PlayInAppUpdateBannerUiState.ReadyToInstall
        if (!blocksPendingClear) {
            playUpdateSessionHandle.clearPendingPlayUpdate()
        }
        _updateSheetChangelog.value = ChangelogUiState.Hidden
        _manualUpdateNoResult.value = false
    }

    fun tryStartPlayInAppUpdate(
        activity: ComponentActivity?,
        launcher: ActivityResultLauncher<IntentSenderRequest>
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

    fun downloadAndInstall(updateInfo: UpdateInfo) = viewModelScope.launch {
        val downloadUrl = updateInfo.downloadUrl
        _downloadProgress.value = 0f
        val result = withContext(Dispatchers.IO) {
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
                            val copyResult = copyUpdateApkToMediaStoreDownloads(
                                context,
                                file,
                                updateInfo.remoteApkFileName
                            )
                            if (copyResult.isFailure) {
                                withContext(Dispatchers.Main) {
                                    _userMessage.value = context.getString(
                                        R.string.settings_update_apk_save_to_downloads_failed
                                    )
                                }
                            } else {
                                userPreferencesRepository.markUpdateApkDownloadsCopySucceeded()
                            }
                        }
                    }
                    _downloadProgress.value = -1f
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    withContext(Dispatchers.Main) {
                        context.startActivity(installIntent)
                    }
                    if (BuildConfig.FLAVOR == "github" && updateInfo.remoteApkAssetUpdatedAt.isNotBlank()) {
                        userPreferencesRepository.writeGithubReleaseAck(
                            fingerprint = updateInfo.notificationDedupeKey(),
                            installedVersionName = BuildConfig.VERSION_NAME
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
        result.onFailure {
            _userMessage.value = "Download failed: ${it.message}"
        }
        _downloadProgress.value = null
    }

    fun openAppNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openManageAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val manageIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        manageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(manageIntent) }
    }

    private fun fetchRawChangelog(): String {
        val repo = BuildConfig.CHANGELOG_GITHUB_REPO
        val branch = BuildConfig.CHANGELOG_GITHUB_BRANCH
        val connection =
            URL("https://raw.githubusercontent.com/$repo/$branch/CHANGELOG.md").openConnection() as HttpURLConnection
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
        mockStage: DevReleasePlayBannerMockStage
    ): PlayInAppUpdateBannerUiState = when (mockStage) {
        DevReleasePlayBannerMockStage.OFF -> realState
        DevReleasePlayBannerMockStage.DOWNLOADING -> PlayInAppUpdateBannerUiState.Downloading(
            bytesDownloaded = MOCK_PLAY_UPDATE_BYTES_DOWNLOADED,
            totalBytesToDownload = MOCK_PLAY_UPDATE_BYTES_TOTAL,
            indeterminateProgress = false
        )
        DevReleasePlayBannerMockStage.READY -> PlayInAppUpdateBannerUiState.ReadyToInstall
    }

    private fun resolvePlayBannerUiStateForSessionLogic(): PlayInAppUpdateBannerUiState {
        if (!isDevReleaseBuild) {
            return playInAppUpdateProgressController.bannerUiState.value
        }
        return mergeDevReleasePlayBannerMock(
            playInAppUpdateProgressController.bannerUiState.value,
            devReleasePlayBannerMockStage.value
        )
    }

    override fun onCleared() {
        devReleasePlayBannerMockSequenceJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val SCHEDULED_EXPORT_WORK_NAME = "scheduled_rules_export"
        private const val MOCK_PLAY_UPDATE_BYTES_DOWNLOADED: Long = 3_000_000L
        private const val MOCK_PLAY_UPDATE_BYTES_TOTAL: Long = 10_000_000L
        /** Placeholder so GitHub update sheet shows Skip version for dev mocks. */
        private const val DEV_RELEASE_MOCK_GITHUB_ASSET_UPDATED_AT = "2000-01-01T00:00:00Z"
    }
}

sealed class ChangelogUiState {
    data object Hidden : ChangelogUiState()
    data object Loading : ChangelogUiState()
    data class Ready(val text: String) : ChangelogUiState()
    data class Failed(val message: String) : ChangelogUiState()
}
