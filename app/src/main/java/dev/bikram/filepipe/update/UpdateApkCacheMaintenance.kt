package dev.bikram.filepipe.update

import android.content.Context
import android.content.pm.ApplicationInfo
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * GitHub flavor: if [save update APK to Downloads][dev.bikram.filepipe.data.preferences.AppPreferences.saveUpdateApkToDownloads]
 * is on, a copy to MediaStore Downloads succeeded for that download
 * ([AppPreferences.updateApkDownloadsCopySucceeded]), and the cached update APK matches the installed
 * base APK (SHA-256), deletes the cache file.
 * Skips when the app may be installed as split APKs ([ApplicationInfo.splitSourceDirs]).
 */
@Singleton
class UpdateApkCacheMaintenance @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    fun enqueueStartupCleanup(backgroundScope: CoroutineScope) {
        if (BuildConfig.FLAVOR != "github") return
        backgroundScope.launch(Dispatchers.IO) {
            runCatching { runCleanupIfNeeded() }
        }
    }

    private suspend fun runCleanupIfNeeded() {
        val prefs = userPreferencesRepository.getPreferencesSnapshot()
        if (!prefs.saveUpdateApkToDownloads) return
        if (!prefs.updateApkDownloadsCopySucceeded) return
        val cacheFile = File(context.cacheDir, FILEPIPE_UPDATE_APK_CACHE_NAME)
        if (!cacheFile.isFile) return
        val applicationInfo = context.applicationInfo
        if (!applicationInfo.splitSourceDirs.isNullOrEmpty()) return
        val sourcePath = applicationInfo.sourceDir ?: return
        val installedApk = File(sourcePath)
        if (!installedApk.isFile) return
        val cacheDigest = sha256HexOfFile(cacheFile) ?: return
        val installedDigest = sha256HexOfFile(installedApk) ?: return
        if (cacheDigest == installedDigest) {
            cacheFile.delete()
        }
    }
}
