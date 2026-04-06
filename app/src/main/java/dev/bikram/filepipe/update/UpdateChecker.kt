package dev.bikram.filepipe.update

interface UpdateChecker {
    suspend fun checkForUpdate(): UpdateInfo?
}

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    /** GitHub release `.apk` asset file name; used for MediaStore Downloads [DISPLAY_NAME]. */
    val remoteApkFileName: String = ""
)
