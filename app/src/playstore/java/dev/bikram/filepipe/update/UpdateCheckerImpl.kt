package dev.bikram.filepipe.update

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class UpdateCheckerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playInAppUpdateSession: PlayInAppUpdateSession
) : UpdateChecker {

    override suspend fun checkForUpdate(): UpdateInfo? {
        val manager = AppUpdateManagerFactory.create(context)
        val appUpdateInfo = try {
            manager.requestAppUpdateInfo()
        } catch (_: Exception) {
            playInAppUpdateSession.clearPendingPlayUpdate()
            return null
        }

        when (appUpdateInfo.updateAvailability()) {
            UpdateAvailability.UPDATE_AVAILABLE -> {
                val flexibleAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                val immediateAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                if (!flexibleAllowed && !immediateAllowed) {
                    playInAppUpdateSession.clearPendingPlayUpdate()
                    return null
                }
                playInAppUpdateSession.setPendingAppUpdateInfo(appUpdateInfo)
                val versionLabel = semanticVersionNameFromPlayUpdateInfo(appUpdateInfo)
                return UpdateInfo(
                    versionName = versionLabel,
                    downloadUrl = "",
                    releaseNotes = "",
                    isPlayStoreUpdateInProgress = false
                )
            }
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                val flexibleAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                val immediateAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                if (!flexibleAllowed && !immediateAllowed) {
                    playInAppUpdateSession.clearPendingPlayUpdate()
                    return null
                }
                playInAppUpdateSession.setPendingAppUpdateInfo(appUpdateInfo)
                val versionLabel = semanticVersionNameFromPlayUpdateInfo(appUpdateInfo)
                return UpdateInfo(
                    versionName = versionLabel,
                    downloadUrl = "",
                    releaseNotes = "",
                    isPlayStoreUpdateInProgress = true
                )
            }
            UpdateAvailability.UPDATE_NOT_AVAILABLE,
            UpdateAvailability.UNKNOWN -> {
                playInAppUpdateSession.clearPendingPlayUpdate()
                return null
            }
            else -> {
                playInAppUpdateSession.clearPendingPlayUpdate()
                return null
            }
        }
    }
}
