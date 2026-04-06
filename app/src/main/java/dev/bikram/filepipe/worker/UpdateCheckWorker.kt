package dev.bikram.filepipe.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.data.preferences.UpdateCheckSchedule
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.update.UpdateAvailableNotifier
import dev.bikram.filepipe.update.UpdateCheckWorkScheduler
import dev.bikram.filepipe.update.UpdateChecker

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val updateChecker: UpdateChecker,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val updateAvailableNotifier: UpdateAvailableNotifier,
    private val updateCheckWorkScheduler: UpdateCheckWorkScheduler
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!BuildConfig.SHOW_UPDATES) {
            updateCheckWorkScheduler.syncFromPreferences()
            return Result.success()
        }
        val prefs = userPreferencesRepository.getPreferencesSnapshot()
        if (prefs.updateCheckSchedule != UpdateCheckSchedule.DAILY_AT_21 &&
            prefs.updateCheckSchedule != UpdateCheckSchedule.WEEKLY_MONDAY_AT_21
        ) {
            updateCheckWorkScheduler.syncFromPreferences()
            return Result.success()
        }
        val info = updateChecker.checkForUpdate()
        if (info != null) {
            updateAvailableNotifier.notifyIfNewUpdateAvailable(info, prefs)
        }
        updateCheckWorkScheduler.syncFromPreferences()
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "update_check_work"
    }
}
