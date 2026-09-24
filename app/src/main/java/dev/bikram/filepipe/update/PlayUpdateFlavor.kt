package dev.bikram.filepipe.update

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

interface PlayUpdateSessionHandle {
    fun clearPendingPlayUpdate()
}

interface PlayInAppUpdateStarter {
    fun startUpdateIfPending(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ): Boolean
}

interface PlayStoreUpdateChecker {
    /** Null when Play reports no update; throws when Play can't be queried, so callers can show "check failed". */
    suspend fun checkForUpdate(): UpdateInfo?
}
