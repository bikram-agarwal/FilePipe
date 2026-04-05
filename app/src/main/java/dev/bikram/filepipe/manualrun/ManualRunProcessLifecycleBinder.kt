package dev.bikram.filepipe.manualrun

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single [ProcessLifecycleOwner] observer for manual-run foreground promotion. Call
 * [ensureRegistered] once from [dev.bikram.filepipe.FilePipeApp.onCreate] so the process lifecycle
 * never accumulates duplicate observers across activity recreations.
 */
@Singleton
class ManualRunProcessLifecycleBinder @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val coordinator: ManualRunForegroundCoordinator
) : DefaultLifecycleObserver {

    private val registered = AtomicBoolean(false)

    fun ensureRegistered() {
        if (!registered.compareAndSet(false, true)) return
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        appContext.stopService(Intent(appContext, ManualRunForegroundService::class.java))
    }

    override fun onStop(owner: LifecycleOwner) {
        if (coordinator.isManualRunActive()) {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, ManualRunForegroundService::class.java)
            )
        }
    }
}
