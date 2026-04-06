package dev.bikram.filepipe.update

import dev.bikram.filepipe.BuildConfig
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
    val browser_download_url: String
)

class UpdateCheckerImpl @Inject constructor() : UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val responseText = connection.inputStream.use { it.readBytes().decodeToString() }
            val release = json.decodeFromString<GithubRelease>(responseText)

            val remoteVersion = release.tag_name.removePrefix("v")
            if (!isRemoteVersionNewer(remoteVersion, BuildConfig.VERSION_NAME)) return@runCatching null

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: return@runCatching null

            UpdateInfo(
                versionName = remoteVersion,
                downloadUrl = apkAsset.browser_download_url,
                releaseNotes = release.body,
                remoteApkFileName = apkAsset.name
            )
        }.getOrNull()
    }
}
