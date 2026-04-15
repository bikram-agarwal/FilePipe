package dev.bikram.filepipe.update

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * Observes Play Core install state for flexible in-app updates and drives [PlayInAppUpdateBannerUiState].
 * Play flavor provides a real implementation; GitHub uses a no-op.
 */
interface PlayInAppUpdateProgressController {
    val bannerUiState: StateFlow<PlayInAppUpdateBannerUiState>

    /** Call after a flexible Play update flow is started successfully. */
    fun onFlexibleUpdateFlowStarted()

    /**
     * Ensures install-state callbacks run (e.g. after process start while a flexible update is already running).
     */
    fun ensureInstallStateListenerRegistered()

    fun completeFlexibleUpdateIfReady(activity: Activity)
}
