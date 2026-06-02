package dev.bikram.filepipe.manualrun

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks in-app manual rule execution and keeps [ManualRunForegroundService] active for the
 * whole run so copy/move work survives after the app goes to the background.
 */
@Singleton
class ManualRunForegroundCoordinator
    @Inject
    constructor(
        @param:ApplicationContext private val appContext: Context,
    ) {
        private val manualRunActive = AtomicBoolean(false)

        fun setManualRunActive(active: Boolean) {
            val previousActive = manualRunActive.getAndSet(active)
            if (active && !previousActive) {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, ManualRunForegroundService::class.java),
                )
            } else if (!active) {
                appContext.stopService(Intent(appContext, ManualRunForegroundService::class.java))
            }
        }

        fun isManualRunActive(): Boolean = manualRunActive.get()
    }
