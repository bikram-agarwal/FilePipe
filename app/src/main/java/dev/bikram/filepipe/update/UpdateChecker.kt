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
    val remoteApkAssetUpdatedAt: String = ""
)

/** Used for update notifications dedupe and GitHub release acknowledgment. */
fun UpdateInfo.notificationDedupeKey(): String =
    if (remoteApkAssetUpdatedAt.isNotBlank()) "$versionName|$remoteApkAssetUpdatedAt" else versionName
