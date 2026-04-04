package dev.bikram.filepipe.manualrun

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks in-app manual rule execution so [ProcessLifecycleOwner] can start
 * [ManualRunForegroundService] only while a run is active and the app is backgrounded.
 */
@Singleton
class ManualRunForegroundCoordinator @Inject constructor(
    @param:ApplicationContext private val appContext: Context
) {
    private val manualRunActive = AtomicBoolean(false)

    fun setManualRunActive(active: Boolean) {
        manualRunActive.set(active)
        if (!active) {
            appContext.stopService(Intent(appContext, ManualRunForegroundService::class.java))
        }
    }

    fun isManualRunActive(): Boolean = manualRunActive.get()
}
