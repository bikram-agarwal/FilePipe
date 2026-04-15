package dev.bikram.filepipe.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.update.AppReviewLauncher
import dev.bikram.filepipe.update.InAppRatingAutoPrompt
import kotlinx.coroutines.launch

/**
 * Play flavor: when eligible, requests Google's in-app review flow with no FilePipe UI beforehand
 * (see Play policy: do not ask the user questions before the rating flow).
 */
@Composable
fun InAppRatingAutoPromptHost(
    preferences: AppPreferences,
    activity: ComponentActivity,
    userPreferencesRepository: UserPreferencesRepository,
    appReviewLauncher: AppReviewLauncher
) {
    if (BuildConfig.FLAVOR != "playstore") return

    val context = LocalContext.current

    LaunchedEffect(
        preferences.inAppReviewAutoNeverAskAgain,
        preferences.playAutoReviewPromptedForLastUpdateTime,
        preferences.hasSeenIntro
    ) {
        if (!preferences.hasSeenIntro) return@LaunchedEffect
        val lastUpdateMillis = InAppRatingAutoPrompt.packageLastUpdateTimeMillis(context)
        val nowMillis = System.currentTimeMillis()
        val debounceOk =
            nowMillis - InAppRatingAutoPrompt.SessionCoordination.lastInAppReviewAttemptWallClockMillis >=
                InAppRatingAutoPrompt.SessionCoordination.AUTO_VS_MANUAL_DEBOUNCE_MS
        val eligible = debounceOk && InAppRatingAutoPrompt.isEligibleForAutoPrompt(
            lastUpdateTimeMillis = lastUpdateMillis,
            nowMillis = nowMillis,
            neverAskAgain = preferences.inAppReviewAutoNeverAskAgain,
            promptedForLastUpdateTimeMillis = preferences.playAutoReviewPromptedForLastUpdateTime
        )
        if (!eligible) return@LaunchedEffect

        appReviewLauncher.tryLaunchInAppReview(activity) {
            activity.lifecycleScope.launch {
                userPreferencesRepository.setPlayAutoReviewPromptedForLastUpdateTime(lastUpdateMillis)
            }
        }
    }
}
