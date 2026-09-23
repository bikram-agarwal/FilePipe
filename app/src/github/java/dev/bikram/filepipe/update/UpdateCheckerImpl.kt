package dev.bikram.filepipe.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@Serializable
private data class GithubRelease(
    val tag_name: String,
    val body: String = "",
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
internal data class GithubAsset(
    val name: String,
    val browser_download_url: String,
    val updated_at: String = "",
)

class UpdateCheckerImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val userPreferencesRepository: UserPreferencesRepository,
    ) : UpdateChecker {
        private val json = Json { ignoreUnknownKeys = true }

        override suspend fun checkForUpdate(): UpdateInfo? =
            withContext(Dispatchers.IO) {
                if (BuildConfig.FLAVOR == "fdroid") {
                    return@withContext checkFdroidForUpdate()
                }
                runCatching {
                    val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = true
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 20_000
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                    val responseText =
                        try {
                            connection.connect()
                            if (connection.responseCode !in 200..299) {
                                error("GitHub returned HTTP ${connection.responseCode}")
                            }
                            connection.inputStream.use { it.readBytes().decodeToString() }
                        } finally {
                            connection.disconnect()
                        }
                    val release = json.decodeFromString<GithubRelease>(responseText)

                    val remoteVersion = release.tag_name.removePrefix("v")
                    val apkAsset =
                        selectGithubReleaseApkAsset(release.assets)
                            ?: return@runCatching null

                    val apkUpdatedAt = apkAsset.updated_at
                    val remoteReleaseFingerprint = "$remoteVersion|$apkUpdatedAt"

                    val ack = userPreferencesRepository.readGithubReleaseAck()
                    val effectiveFingerprint =
                        if (ack.forInstalledVersion == BuildConfig.VERSION_NAME) {
                            ack.fingerprint
                        } else {
                            null
                        }

                    val installedVersion = BuildConfig.VERSION_NAME
                    if (!isGithubReleaseUpdateAvailable(remoteVersion, installedVersion, remoteReleaseFingerprint, effectiveFingerprint)) {
                        return@runCatching null
                    }

                    UpdateInfo(
                        versionName = remoteVersion,
                        downloadUrl = apkAsset.browser_download_url,
                        releaseNotes = release.body,
                        remoteApkFileName = apkAsset.name,
                        remoteApkAssetUpdatedAt = apkUpdatedAt,
                    )
                }.getOrNull()
            }

        private fun checkFdroidForUpdate(): UpdateInfo? =
            runCatching {
                val url = URL("https://f-droid.org/api/v1/packages/${context.packageName}")
                val connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Accept", "application/json")
                val responseText =
                    try {
                        connection.connect()
                        if (connection.responseCode !in 200..299) {
                            error("F-Droid returned HTTP ${connection.responseCode}")
                        }
                        connection.inputStream.use { it.readBytes().decodeToString() }
                    } finally {
                        connection.disconnect()
                    }
                val packageJson = json.parseToJsonElement(responseText).jsonObject
                val packages =
                    (packageJson["packages"] as? JsonArray)
                        ?: (packageJson["versions"] as? JsonArray)
                        ?: return@runCatching null
                val latestPackage =
                    packages
                        .mapNotNull { element -> element as? JsonObject }
                        .mapNotNull { element ->
                            val versionCode = element.longOrNull("versionCode") ?: return@mapNotNull null
                            val versionName = element.stringOrNull("versionName").orEmpty()
                            FdroidPackageVersion(versionCode = versionCode, versionName = versionName)
                        }.filter { version -> version.versionCode > BuildConfig.VERSION_CODE.toLong() }
                        .maxByOrNull { version -> version.versionCode }
                        ?: return@runCatching null

                UpdateInfo(
                    versionName = latestPackage.versionName.ifBlank { latestPackage.versionCode.toString() },
                    downloadUrl = "",
                    releaseNotes = "",
                )
            }.getOrNull()
    }

private data class FdroidPackageVersion(
    val versionCode: Long,
    val versionName: String,
)

private fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.longOrNull(key: String): Long? {
    val element = this[key] as? JsonPrimitive ?: return null
    return element.longOrNull ?: element.contentOrNull?.toLongOrNull()
}

/**
 * Prefer the GitHub-flavor sideload APK (`*-github.apk`). Releases also ship `*-fdroid.apk`
 * and `*-offline.apk`; GitHub API asset order often lists fdroid first.
 */
internal fun selectGithubReleaseApkAsset(assets: List<GithubAsset>): GithubAsset? =
    assets.firstOrNull { asset ->
        asset.name.endsWith(".apk", ignoreCase = true) &&
            asset.name.contains("-github", ignoreCase = true)
    }

internal fun isGithubReleaseUpdateAvailable(
    remoteVersion: String,
    installedVersion: String,
    remoteReleaseFingerprint: String,
    effectiveAcknowledgedFingerprint: String?,
): Boolean = isRemoteVersionNewer(remoteVersion, installedVersion) && effectiveAcknowledgedFingerprint != remoteReleaseFingerprint
