package dev.bikram.filepipe.update

import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@Serializable
private data class GithubRelease(
    val tag_name: String,
    val body: String = "",
    val assets: List<GithubAsset> = emptyList()
)

@Serializable
private data class GithubAsset(
    val name: String,
    val browser_download_url: String,
    val updated_at: String = ""
)

class UpdateCheckerImpl @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val responseText = connection.inputStream.use { it.readBytes().decodeToString() }
            val release = json.decodeFromString<GithubRelease>(responseText)

            val remoteVersion = release.tag_name.removePrefix("v")
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: return@runCatching null

            val apkUpdatedAt = apkAsset.updated_at
            val currentFingerprint = "$remoteVersion|$apkUpdatedAt"

            val ack = userPreferencesRepository.readGithubReleaseAck()
            val effectiveFingerprint = if (ack.forInstalledVersion == BuildConfig.VERSION_NAME) {
                ack.fingerprint
            } else {
                null
            }

            val installedVersion = BuildConfig.VERSION_NAME
            val versionCmp = compareVersionNames(remoteVersion, installedVersion)

            when {
                versionCmp < 0 -> null
                versionCmp > 0 -> {
                    if (effectiveFingerprint == currentFingerprint) null
                    else UpdateInfo(
                        versionName = remoteVersion,
                        downloadUrl = apkAsset.browser_download_url,
                        releaseNotes = release.body,
                        remoteApkFileName = apkAsset.name,
                        remoteApkAssetUpdatedAt = apkUpdatedAt
                    )
                }
                else -> {
                    if (effectiveFingerprint == null) {
                        userPreferencesRepository.writeGithubReleaseAck(
                            fingerprint = currentFingerprint,
                            installedVersionName = installedVersion
                        )
                        null
                    } else if (effectiveFingerprint == currentFingerprint) {
                        null
                    } else {
                        UpdateInfo(
                            versionName = remoteVersion,
                            downloadUrl = apkAsset.browser_download_url,
                            releaseNotes = release.body,
                            remoteApkFileName = apkAsset.name,
                            remoteApkAssetUpdatedAt = apkUpdatedAt
                        )
                    }
                }
            }
        }.getOrNull()
    }
}
