package dev.bikram.filepipe

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.update.UpdateApkCacheMaintenance
import dev.bikram.filepipe.update.UpdateAvailableNotifier
import dev.bikram.filepipe.update.UpdateCheckWorkScheduler
import dev.bikram.filepipe.worker.LogPruneWorker
import dev.bikram.filepipe.worker.RuleTrashSweepWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class FilePipeApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var updateApkCacheMaintenance: UpdateApkCacheMaintenance

    @Inject
    lateinit var updateCheckWorkScheduler: UpdateCheckWorkScheduler

    @Inject
    lateinit var updateAvailableNotifier: UpdateAvailableNotifier

    private val preferencesMigrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        DiagnosticLog.installCrashHandler(this)
        DiagnosticLog.record(this, "FilePipeApp.onCreate started")
        preferencesMigrationScope.launch {
            runCatching {
                userPreferencesRepository.migrateLegacyEnhancedShadingPreferenceIfNeeded()
                userPreferencesRepository.migrateLegacyCustomSeedIfNeeded()
                userPreferencesRepository.migrateLegacyAutoCheckToScheduleIfNeeded()
                userPreferencesRepository.migrateDeferredFolderAccessIfNeeded()
                updateCheckWorkScheduler.syncFromPreferences()
            }.onFailure { error ->
                DiagnosticLog.record(this@FilePipeApp, "Startup preference migration/update scheduling failed", error)
            }
        }
        updateAvailableNotifier.ensureNotificationChannel()
        updateApkCacheMaintenance.enqueueStartupCleanup(preferencesMigrationScope)
        scheduleLogPruneWorker()
        scheduleRuleTrashSweepWorker()
    }

    private fun scheduleLogPruneWorker() {
        val request = PeriodicWorkRequestBuilder<LogPruneWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            LogPruneWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleRuleTrashSweepWorker() {
        val request = PeriodicWorkRequestBuilder<RuleTrashSweepWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RuleTrashSweepWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
