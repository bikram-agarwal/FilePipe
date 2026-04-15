package dev.bikram.filepipe.update

interface UpdateChecker {
    suspend fun checkForUpdate(): UpdateInfo?
}

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    /** GitHub release `.apk` asset file name; used for MediaStore Downloads [DISPLAY_NAME]. */
    val remoteApkFileName: String = "",
    /**
     * GitHub: ISO 8601 `updated_at` of the chosen `.apk` asset (for hotfix + ack fingerprint).
     * Play: empty.
     */
    val remoteApkAssetUpdatedAt: String = "",
    /**
     * Play only: [com.google.android.play.core.install.model.UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS].
     * Used for copy and UI so in-progress is not shown as plain "up to date".
     */
    val isPlayStoreUpdateInProgress: Boolean = false,
    /**
     * Dev release only: [SettingsViewModel] arms a fake update for the global promo without Play or GitHub.
     */
    val isDevReleaseMock: Boolean = false
)

/** Used for update notifications dedupe and GitHub release acknowledgment. */
fun UpdateInfo.notificationDedupeKey(): String =
    if (remoteApkAssetUpdatedAt.isNotBlank()) "$versionName|$remoteApkAssetUpdatedAt" else versionName
