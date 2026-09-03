package dev.bikram.filepipe.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.di.IoDispatcher
import dev.bikram.filepipe.di.MainDispatcher
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.update.AppReviewLauncher
import dev.bikram.filepipe.update.PlayInAppUpdateBannerUiState
import dev.bikram.filepipe.update.PlayInAppUpdateProgressController
import dev.bikram.filepipe.update.PlayInAppUpdateStarter
import dev.bikram.filepipe.update.PlayUpdateSessionHandle
import dev.bikram.filepipe.update.UpdateAvailableNotifier
import dev.bikram.filepipe.update.UpdateChecker
import dev.bikram.filepipe.update.UpdateInfo
import dev.bikram.filepipe.update.copyUpdateApkToMediaStoreDownloads
import dev.bikram.filepipe.update.notificationDedupeKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * Owns Settings update-sheet state and check / download / install / Play-start orchestration.
 * Prefs mutations for update schedule and notifications stay on [SettingsViewModel].
 */
@HiltViewModel
class FilePipeUpdateViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val updateChecker: UpdateChecker,
        private val playUpdateSessionHandle: PlayUpdateSessionHandle,
        private val playInAppUpdateStarter: PlayInAppUpdateStarter,
        private val playInAppUpdateProgressController: PlayInAppUpdateProgressController,
        private val appReviewLauncher: AppReviewLauncher,
        private val updateAvailableNotifier: UpdateAvailableNotifier,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private enum class DevReleasePlayBannerMockStage {
            OFF,
            STARTING,
            DOWNLOADING,
            READY,
        }

        private val devReleasePlayBannerMockStage =
            MutableStateFlow(DevReleasePlayBannerMockStage.OFF)
        private var devReleasePlayBannerMockSequenceJob: Job? = null

        private val _userMessages = Channel<String>(Channel.BUFFERED)
        val userMessages = _userMessages.receiveAsFlow()

        private fun postUserMessage(message: String) {
            _userMessages.trySend(message)
        }

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

        private val _updateCheckFinishedWithoutResult = MutableStateFlow(false)
        val updateCheckFinishedWithoutResult: StateFlow<Boolean> = _updateCheckFinishedWithoutResult.asStateFlow()

        private val _showUpdateSheet = MutableStateFlow(false)
        val showUpdateSheet: StateFlow<Boolean> = _showUpdateSheet.asStateFlow()

        /**
         * Set by the alert bar / notification tap (outside Settings) to ask Settings to open the
         * update sheet when it composes.
         */
        private val _openSheetRequested = MutableStateFlow(false)
        val openSheetRequested: StateFlow<Boolean> = _openSheetRequested.asStateFlow()

        fun requestOpenSheet() {
            _openSheetRequested.value = true
        }

        fun markOpenSheetHandled() {
            _openSheetRequested.value = false
        }

        private val _openUpdateSheetFromRulesPromo = MutableStateFlow(false)
        val openUpdateSheetFromRulesPromo: StateFlow<Boolean> = _openUpdateSheetFromRulesPromo.asStateFlow()

        private val _startPlayInAppUpdateAfterRulesPromoSheet = MutableStateFlow(false)
        val startPlayInAppUpdateAfterRulesPromoSheet: StateFlow<Boolean> =
            _startPlayInAppUpdateAfterRulesPromoSheet.asStateFlow()

        private val _updatePromoBannerDismissedThisSession = MutableStateFlow(false)
        val updatePromoBannerDismissedThisSession: StateFlow<Boolean> =
            _updatePromoBannerDismissedThisSession.asStateFlow()

        // Bumped on every user-initiated update check so the alert chrome can re-present
        // itself even when the resulting update state is unchanged.
        private val _manualUpdateCheckTrigger = MutableStateFlow(0)
        val manualUpdateCheckTrigger: StateFlow<Int> = _manualUpdateCheckTrigger.asStateFlow()

        val playInAppUpdateBannerUiState: StateFlow<PlayInAppUpdateBannerUiState> =
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

        /** Opens the update sheet and kicks off a manual check (notification / alert bar). */
        fun openSheetAndCheck() {
            _showUpdateSheet.value = true
            beginManualUpdateCheckFromSheet()
            loadChangelogForUpdateSheet()
        }

        fun openSheetFromRulesPromo() {
            _showUpdateSheet.value = true
            if (BuildConfig.CHANGELOG_GITHUB_REPO.isNotBlank()) {
                loadChangelogForUpdateSheet()
            }
        }

        fun openSheetFromSettingsRow() {
            _showUpdateSheet.value = true
            beginManualUpdateCheckFromSheet()
            loadChangelogForUpdateSheet()
        }

        /**
         * Dev release: arms the global update promo (Rules / History / Settings). Swipe the card to dismiss.
         */
        fun devReleaseMockArmRulesUpdatePromoForRulesTab() {
            if (!BuildConfig.SHOW_UPDATES) return
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
            postUserMessage(context.getString(R.string.settings_play_in_app_update_canceled))
        }

        fun completePlayFlexibleUpdateIfReady(activity: Activity?) {
            if (devReleasePlayBannerMockStage.value == DevReleasePlayBannerMockStage.READY) {
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

        fun checkForUpdate(silent: Boolean = false) =
            viewModelScope.launch {
                if (!silent) {
                    _manualUpdateCheckTrigger.value += 1
                }
                _isCheckingUpdate.value = true
                _downloadProgress.value = null
                val checked = runCatching { updateChecker.checkForUpdate() }
                _isCheckingUpdate.value = false
                val info =
                    checked
                        .onFailure { error ->
                            DiagnosticLog.record(context, "Manual update check failed", error)
                            if (!silent) {
                                postUserMessage(error.message ?: context.getString(R.string.settings_update_check_failed))
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
                        postUserMessage(context.getString(R.string.settings_up_to_date))
                    }
                }
            }

        /**
         * Manual flow from the update sheet: sets checking immediately on the main thread, then runs the check.
         * No snackbar; the sheet shows up-to-date vs available.
         */
        fun beginManualUpdateCheckFromSheet() {
            _manualUpdateCheckTrigger.value += 1
            _isCheckingUpdate.value = true
            _downloadProgress.value = null
            _updateCheckFinishedWithoutResult.value = false
            viewModelScope.launch {
                val checked = runCatching { updateChecker.checkForUpdate() }
                _isCheckingUpdate.value = false
                val info =
                    checked
                        .onFailure { error ->
                            DiagnosticLog.record(context, "Update sheet check failed", error)
                            _updateCheckFinishedWithoutResult.value = true
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
                    _updateCheckFinishedWithoutResult.value = true
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
                    withContext(ioDispatcher) {
                        runCatching { fetchRawChangelog() }
                    }
                _updateSheetChangelog.value =
                    loaded.fold(
                        onSuccess = { ChangelogUiState.Ready(it) },
                        onFailure = {
                            DiagnosticLog.record(context, "Update changelog load failed", it)
                            ChangelogUiState.Failed(
                                context.getString(R.string.settings_changelog_load_failed),
                            )
                        },
                    )
            }

        fun dismissUpdateSheet() {
            _showUpdateSheet.value = false
            val bannerState = resolvePlayBannerUiStateForSessionLogic()
            val blocksPendingClear =
                bannerState is PlayInAppUpdateBannerUiState.Downloading ||
                    bannerState is PlayInAppUpdateBannerUiState.ReadyToInstall
            if (!blocksPendingClear) {
                playUpdateSessionHandle.clearPendingPlayUpdate()
            }
            _updateSheetChangelog.value = ChangelogUiState.Hidden
            _updateCheckFinishedWithoutResult.value = false
        }

        /** Close the sheet because a Play download/install began behind it (banner takes over). */
        fun closeSheetForPlayProgress() {
            _showUpdateSheet.value = false
            _downloadProgress.value = null
            _updateSheetChangelog.value = ChangelogUiState.Hidden
            _updateCheckFinishedWithoutResult.value = false
        }

        fun tryStartPlayInAppUpdate(
            activity: ComponentActivity?,
            launcher: ActivityResultLauncher<IntentSenderRequest>,
        ): Boolean {
            if (!BuildConfig.USE_PLAY_IN_APP_UPDATES) return false
            if (activity == null) {
                postUserMessage(context.getString(R.string.settings_play_in_app_update_failed))
                return false
            }
            val started = playInAppUpdateStarter.startUpdateIfPending(activity, launcher)
            if (!started) {
                postUserMessage(context.getString(R.string.settings_play_in_app_update_failed))
            }
            return started
        }

        fun downloadAndInstall(updateInfo: UpdateInfo) =
            viewModelScope.launch {
                if (BuildConfig.FLAVOR == "fdroid") {
                    return@launch
                }
                _downloadProgress.value = 0f
                val result =
                    withContext(ioDispatcher) {
                        runCatching {
                            userPreferencesRepository.clearUpdateApkDownloadsCopySucceeded()
                            val file =
                                downloadUpdateApk(context, updateInfo) { progress ->
                                    _downloadProgress.value = progress
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
                                        withContext(mainDispatcher) {
                                            postUserMessage(
                                                context.getString(
                                                    R.string.settings_update_apk_save_to_downloads_failed,
                                                ),
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
                            withContext(mainDispatcher) {
                                context.startActivity(installIntent)
                            }
                        }
                    }
                result.onFailure {
                    DiagnosticLog.record(context, "Update APK download/install failed", it)
                    postUserMessage(context.getString(R.string.settings_update_download_failed))
                }
                _downloadProgress.value = null
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
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IOException("Changelog request returned HTTP $responseCode")
                }
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

        private fun resolvePlayBannerUiStateForSessionLogic(): PlayInAppUpdateBannerUiState =
            mergeDevReleasePlayBannerMock(
                playInAppUpdateProgressController.bannerUiState.value,
                devReleasePlayBannerMockStage.value,
            )

        override fun onCleared() {
            devReleasePlayBannerMockSequenceJob?.cancel()
        }

        companion object {
            private const val MOCK_PLAY_UPDATE_BYTES_DOWNLOADED: Long = 3_000_000L
            private const val MOCK_PLAY_UPDATE_BYTES_TOTAL: Long = 10_000_000L

            /** Placeholder so GitHub update sheet shows Skip version for dev mocks. */
            private const val DEV_RELEASE_MOCK_GITHUB_ASSET_UPDATED_AT = "2000-01-01T00:00:00Z"
        }
    }
