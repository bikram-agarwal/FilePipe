package dev.bikram.filepipe.update

import android.app.Activity
import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.InstallStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayStorePlayInAppUpdateCoordinator
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val playInAppUpdateSession: PlayInAppUpdateSession,
    ) : PlayInAppUpdateProgressController {
        private val appUpdateManager get() = AppUpdateManagerFactory.create(context)

        private val bannerState = MutableStateFlow<PlayInAppUpdateBannerUiState>(PlayInAppUpdateBannerUiState.Hidden)
        override val bannerUiState: StateFlow<PlayInAppUpdateBannerUiState> = bannerState.asStateFlow()

        private var listenerRegistered = false

        private val installStateListener =
            InstallStateUpdatedListener { installState ->
                when (installState.installStatus()) {
                    InstallStatus.DOWNLOADING -> {
                        val totalBytes = installState.totalBytesToDownload()
                        bannerState.value =
                            PlayInAppUpdateBannerUiState.Downloading(
                                bytesDownloaded = installState.bytesDownloaded(),
                                totalBytesToDownload = totalBytes,
                                indeterminateProgress = totalBytes <= 0L,
                            )
                    }

                    InstallStatus.DOWNLOADED -> {
                        bannerState.value = PlayInAppUpdateBannerUiState.ReadyToInstall
                    }

                    InstallStatus.INSTALLING,
                    InstallStatus.PENDING,
                    -> {
                        bannerState.value =
                            PlayInAppUpdateBannerUiState.Downloading(
                                bytesDownloaded = installState.bytesDownloaded(),
                                totalBytesToDownload = installState.totalBytesToDownload(),
                                indeterminateProgress = true,
                            )
                    }

                    InstallStatus.INSTALLED -> {
                        unregisterInstallStateListener()
                        playInAppUpdateSession.clearPendingPlayUpdate()
                        bannerState.value = PlayInAppUpdateBannerUiState.Hidden
                    }

                    InstallStatus.FAILED,
                    InstallStatus.CANCELED,
                    -> {
                        unregisterInstallStateListener()
                        bannerState.value = PlayInAppUpdateBannerUiState.Hidden
                    }

                    InstallStatus.UNKNOWN -> {}

                    else -> {}
                }
            }

        override fun onFlexibleUpdateFlowStarted() {
            registerInstallStateListenerIfNeeded()
        }

        override fun ensureInstallStateListenerRegistered() {
            if (playInAppUpdateSession.pendingAppUpdate() != null) {
                registerInstallStateListenerIfNeeded()
            }
        }

        @Suppress("UnusedParameter")
        override fun completeFlexibleUpdateIfReady(activity: Activity) {
            if (bannerState.value != PlayInAppUpdateBannerUiState.ReadyToInstall) return
            runCatching { appUpdateManager.completeUpdate() }
        }

        private fun registerInstallStateListenerIfNeeded() {
            if (listenerRegistered) return
            appUpdateManager.registerListener(installStateListener)
            listenerRegistered = true
        }

        private fun unregisterInstallStateListener() {
            if (!listenerRegistered) return
            appUpdateManager.unregisterListener(installStateListener)
            listenerRegistered = false
        }
    }
