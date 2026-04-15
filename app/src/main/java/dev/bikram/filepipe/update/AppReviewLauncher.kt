package dev.bikram.filepipe.update

import androidx.activity.ComponentActivity

/**
 * Play flavor launches Google Play In-App Review; GitHub flavor is a no-op.
 * [onFlowFinished] runs after [requestReviewFlow] / [launchReviewFlow] complete (success or failure).
 */
fun interface AppReviewLauncher {
    fun tryLaunchInAppReview(activity: ComponentActivity, onFlowFinished: () -> Unit)
}
