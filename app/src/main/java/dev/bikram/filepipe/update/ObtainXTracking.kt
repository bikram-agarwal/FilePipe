package dev.bikram.filepipe.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.R
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// PARITY: Remember has the same file (dev.bikram.remember.update.ObtainXTracking); only the app
// constants differ.

/** ObtainX, the author's Obtainium fork. Its GitHub and F-Droid builds share this package id. */
internal const val OBTAINX_PACKAGE_ID = "dev.bikram.obtainx"

private const val RELEASES_GITHUB_URL = "https://github.com/${BuildConfig.GITHUB_REPO}"
private const val TRACKED_APP_AUTHOR = "bikram-agarwal"
private const val TRACKED_APP_NAME = "FilePipe"
private const val DEV_RELEASE_APPLICATION_ID_SUFFIX = ".dev"

/** Play builds are updated by Play; every other flavor can hand itself to ObtainX. */
internal val supportsObtainXTracking: Boolean
    get() = BuildConfig.FLAVOR != "playstore"

/** The package public releases ship under. devRelease appends `.dev` to local installs only. */
internal fun releaseApplicationId(applicationId: String = BuildConfig.APPLICATION_ID): String = applicationId.removeSuffix(DEV_RELEASE_APPLICATION_ID_SUFFIX)

/**
 * The `obtainium://app/` import payload for [flavor], or null where tracking isn't offered.
 *
 * - [releaseAppId] must be the package inside the APK ObtainX installs, or it rejects the update
 *   as an app-id change.
 * - `additionalSettings` is a JSON *string*: ObtainX decodes it a second time.
 * - The APK filter is anchored because ObtainX matches it against the asset name *or* its download
 *   URL, and every GitHub download URL contains "github".
 * - No `overrideSource`: ObtainX picks the GitHub or F-Droid source from the URL host.
 * - No `preferredApkIndex`: ObtainX treats a missing index as 0, and the index doesn't pick the CPU
 *   architecture anyway (ObtainX filters APKs by the device's ABI first).
 */
internal fun buildObtainXTrackingJson(
    flavor: String,
    releaseAppId: String,
): String? {
    val (url, apkFilterRegEx) =
        when (flavor) {
            "github" -> RELEASES_GITHUB_URL to """-github\.apk$"""

            "offline" -> RELEASES_GITHUB_URL to """-offline\.apk$"""

            // The F-Droid listing, so F-Droid users keep getting F-Droid-published builds.
            "fdroid" -> "https://f-droid.org/packages/$releaseAppId" to null

            else -> return null
        }
    return buildJsonObject {
        put("id", releaseAppId)
        put("url", url)
        put("author", TRACKED_APP_AUTHOR)
        put("name", TRACKED_APP_NAME)
        if (apkFilterRegEx != null) {
            put("additionalSettings", buildJsonObject { put("apkFilterRegEx", apkFilterRegEx) }.toString())
        }
    }.toString()
}

/** Needs the `<queries>` package entry in the manifest to see ObtainX on Android 11+. */
internal fun isObtainXInstalled(context: Context): Boolean = context.packageManager.getLaunchIntentForPackage(OBTAINX_PACKAGE_ID) != null

/**
 * Hands this build to ObtainX, which asks the user to confirm the import. Falls back to ObtainX's
 * web page when it isn't installed. `setPackage` keeps upstream Obtainium, which registers the same
 * `obtainium://` scheme, from intercepting the link.
 *
 * NEW_TASK is load-bearing: ObtainX's activity is singleTop with an empty task affinity, so without
 * it a second ObtainX instance is stacked inside FilePipe's task instead of opening ObtainX's own.
 */
internal fun trackUpdatesViaObtainX(context: Context) {
    val payload = buildObtainXTrackingJson(BuildConfig.FLAVOR, releaseApplicationId()) ?: return
    if (isObtainXInstalled(context)) {
        val importIntent =
            Intent(Intent.ACTION_VIEW, "obtainium://app/${Uri.encode(payload)}".toUri())
                .setPackage(OBTAINX_PACKAGE_ID)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(importIntent)
            return
        } catch (_: ActivityNotFoundException) {
            // Uninstalled between the check and the tap: fall through to the web page.
        }
    }
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, context.getString(R.string.settings_about_obtainx_website_url).toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
