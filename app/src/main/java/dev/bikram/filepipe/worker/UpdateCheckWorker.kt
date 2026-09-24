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
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.update.FilePipeUpdateChecker
import dev.bikram.filepipe.update.PlayStoreUpdateChecker
import dev.bikram.filepipe.update.UpdateAvailableNotifier
import dev.bikram.filepipe.update.UpdateCheckWorkScheduler

@HiltWorker
class UpdateCheckWorker
    @AssistedInject
    constructor(
        @Assisted private val appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val filePipeUpdateChecker: FilePipeUpdateChecker,
        private val playStoreUpdateChecker: PlayStoreUpdateChecker,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val updateAvailableNotifier: UpdateAvailableNotifier,
        private val updateCheckWorkScheduler: UpdateCheckWorkScheduler,
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
            runCatching {
                val info =
                    if (BuildConfig.USE_PLAY_IN_APP_UPDATES) {
                        playStoreUpdateChecker.checkForUpdate()
                    } else {
                        filePipeUpdateChecker.checkForUpdate()
                    }
                if (info != null) {
                    updateAvailableNotifier.notifyIfNewUpdateAvailable(info, prefs)
                }
            }.onFailure { error ->
                DiagnosticLog.record(appContext, "Scheduled update check failed: attempt=$runAttemptCount", error)
                if (runAttemptCount < MAX_IMMEDIATE_RETRIES) {
                    return Result.retry()
                }
                updateCheckWorkScheduler.syncFromPreferences()
                return Result.success()
            }
            updateCheckWorkScheduler.syncFromPreferences()
            return Result.success()
        }

        companion object {
            const val UNIQUE_WORK_NAME = "update_check_work"
            private const val MAX_IMMEDIATE_RETRIES = 3
        }
    }
