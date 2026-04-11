package dev.bikram.filepipe.data.storage

import android.os.Build
import android.os.Environment
import dev.bikram.filepipe.data.preferences.FolderAccessMode
import dev.bikram.filepipe.data.preferences.usesAllFilesPaths
import java.io.File
import java.io.IOException

private val adoptableSdRootRegex =
    Regex("^/storage/[0-9A-F]{4}-[0-9A-F]{4}(?:/|$)", RegexOption.IGNORE_CASE)

/**
 * User-selected paths that are plain absolute filesystem paths (not SAF `content://`).
 */
fun isFilesystemFolderPathString(path: String): Boolean {
    val trimmed = path.trim()
    return trimmed.startsWith("/") && !trimmed.startsWith("content://")
}

/**
 * Returns canonical absolute path for an existing or hypothetical folder, or null if unsafe/invalid.
 */
fun normalizeFilesystemFolderPath(raw: String): String? {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    if (!trimmed.startsWith("/")) return null
    if (trimmed.contains("..")) return null
    return try {
        File(trimmed).canonicalFile.absolutePath
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}

/**
 * Whether [canonicalPath] is under primary shared storage or an adoptable SD volume root.
 * Call only when [android.os.Environment.isExternalStorageManager] is true for mutating access.
 */
fun isCanonicalPathUnderAllowedSharedStorage(canonicalPath: String): Boolean {
    if (canonicalPath.isBlank()) return false
    val primaryRoot = try {
        Environment.getExternalStorageDirectory().canonicalPath
    } catch (_: Exception) {
        return false
    }
    if (canonicalPath == primaryRoot || canonicalPath.startsWith(primaryRoot + File.separator)) {
        return true
    }
    val match = adoptableSdRootRegex.find(canonicalPath) ?: return false
    val sdRoot = match.value.trimEnd('/')
    return canonicalPath == sdRoot || canonicalPath.startsWith(sdRoot + File.separator)
}

fun isFilesystemFolderPathAllowedForRules(rawPath: String): Boolean {
    val canonical = normalizeFilesystemFolderPath(rawPath) ?: return false
    return isCanonicalPathUnderAllowedSharedStorage(canonical)
}

/** Suggested Downloads directory when All files access is granted. */
fun primaryDownloadsDirectoryPath(): String = runCatching {
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalFile.absolutePath
}.getOrElse { "/storage/emulated/0/Download" }

/**
 * Typical device Screenshots directory on shared storage ([Environment.DIRECTORY_SCREENSHOTS] on API 31+,
 * else [Pictures]/Screenshots).
 */
fun primaryScreenshotsDirectoryPath(): String = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_SCREENSHOTS).canonicalFile.absolutePath
    } else {
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Screenshots"
        ).canonicalFile.absolutePath
    }
}.getOrElse { "/storage/emulated/0/Pictures/Screenshots" }

/** True when the user prefers filesystem paths and the OS has granted All files access. */
fun isFilesystemAccessEffective(folderAccessMode: FolderAccessMode): Boolean =
    folderAccessMode.usesAllFilesPaths() && Environment.isExternalStorageManager()
