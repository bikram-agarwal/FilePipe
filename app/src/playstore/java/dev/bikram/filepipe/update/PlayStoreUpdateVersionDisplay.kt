package dev.bikram.filepipe.update

import com.google.android.play.core.appupdate.AppUpdateInfo

/**
 * Maps Play's [AppUpdateInfo.availableVersionCode] to a user-visible version string (no "v" prefix).
 * FilePipe encodes semantic version in [versionCode] as major*100 + minor*10 + patch with
 * single-digit minor and patch (for example 304 → 3.0.4).
 */
internal fun semanticVersionNameFromPlayStoreVersionCode(versionCode: Int): String {
    if (versionCode <= 0) return ""
    if (versionCode > 99_999) return versionCode.toString()
    val major = versionCode / 100
    val minor = (versionCode / 10) % 10
    val patch = versionCode % 10
    return "$major.$minor.$patch"
}

internal fun semanticVersionNameFromPlayUpdateInfo(appUpdateInfo: AppUpdateInfo): String =
    semanticVersionNameFromPlayStoreVersionCode(appUpdateInfo.availableVersionCode())
