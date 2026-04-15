package dev.bikram.filepipe.update

import android.content.Context
import android.content.IntentSender
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayStorePlayInAppUpdateStarter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playInAppUpdateSession: PlayInAppUpdateSession,
    private val playInAppUpdateProgressController: PlayInAppUpdateProgressController
) : PlayInAppUpdateStarter {

    override fun startUpdateIfPending(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean {
        val appUpdateInfo = playInAppUpdateSession.pendingAppUpdate() ?: return false
        val manager = AppUpdateManagerFactory.create(context)
        val updateType = when {
            appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
            appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
            else -> return false
        }
        val options = AppUpdateOptions.newBuilder(updateType).build()
        return try {
            manager.startUpdateFlowForResult(appUpdateInfo, launcher, options)
            if (updateType == AppUpdateType.FLEXIBLE) {
                playInAppUpdateProgressController.onFlexibleUpdateFlowStarted()
            }
            true
        } catch (_: IntentSender.SendIntentException) {
            false
        }
    }
}
