package dev.bikram.filepipe

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.bikram.filepipe.manualrun.ManualRunProcessLifecycleBinder
import dev.bikram.filepipe.worker.LogPruneWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class FilePipeApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var manualRunProcessLifecycleBinder: ManualRunProcessLifecycleBinder

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        manualRunProcessLifecycleBinder.ensureRegistered()
        scheduleLogPruneWorker()
    }

    private fun scheduleLogPruneWorker() {
        val request = PeriodicWorkRequestBuilder<LogPruneWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            LogPruneWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
