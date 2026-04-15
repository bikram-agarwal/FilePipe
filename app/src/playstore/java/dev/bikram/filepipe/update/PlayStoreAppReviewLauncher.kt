package dev.bikram.filepipe.update

import android.content.Context
import androidx.activity.ComponentActivity
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayStoreAppReviewLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AppReviewLauncher {

    override fun tryLaunchInAppReview(activity: ComponentActivity, onFlowFinished: () -> Unit) {
        InAppRatingAutoPrompt.SessionCoordination.lastInAppReviewAttemptWallClockMillis =
            System.currentTimeMillis()
        val reviewManager = ReviewManagerFactory.create(context)
        reviewManager.requestReviewFlow().addOnCompleteListener { requestTask ->
            if (requestTask.isSuccessful) {
                val reviewInfo = requestTask.result
                reviewManager.launchReviewFlow(activity, reviewInfo).addOnCompleteListener {
                    onFlowFinished()
                }
            } else {
                onFlowFinished()
            }
        }
    }
}
