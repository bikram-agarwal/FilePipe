package dev.bikram.filepipe.update

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class GithubPlayUpdateNoOp @Inject constructor() :
    PlayUpdateSessionHandle,
    PlayInAppUpdateStarter,
    PlayInAppUpdateProgressController,
    AppReviewLauncher {

    private val bannerState = MutableStateFlow<PlayInAppUpdateBannerUiState>(PlayInAppUpdateBannerUiState.Hidden)
    override val bannerUiState: StateFlow<PlayInAppUpdateBannerUiState> = bannerState.asStateFlow()

    override fun clearPendingPlayUpdate() {}

    override fun startUpdateIfPending(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean = false

    override fun onFlexibleUpdateFlowStarted() {}

    override fun ensureInstallStateListenerRegistered() {}

    override fun completeFlexibleUpdateIfReady(activity: Activity) {}

    override fun tryLaunchInAppReview(activity: ComponentActivity, onFlowFinished: () -> Unit) {
        onFlowFinished()
    }
}
