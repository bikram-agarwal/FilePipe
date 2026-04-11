package dev.bikram.filepipe.data.storage

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
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

/**
 * True when [rawPath] resolves to the primary shared storage root (e.g. `/storage/emulated/0`).
 * That root is typically not grantable as a SAF tree; use All files access instead.
 */
fun isFilesystemPathPrimarySharedStorageRoot(rawPath: String): Boolean {
    val primaryRoot = runCatching {
        Environment.getExternalStorageDirectory().canonicalPath
    }.getOrNull() ?: return false
    val pathCanonical = runCatching {
        File(rawPath.trim().trimEnd('/')).canonicalPath
    }.getOrNull() ?: return false
    return pathCanonical == primaryRoot
}

/**
 * True when [treeUriString] is a SAF tree for the primary volume root (`primary:` with no path segment).
 */
fun isSafTreeUriPrimarySharedStorageRoot(treeUriString: String): Boolean {
    if (!treeUriString.startsWith("content://")) return false
    val treeDocumentId = runCatching {
        DocumentsContract.getTreeDocumentId(Uri.parse(treeUriString))
    }.getOrNull() ?: return false
    if (!treeDocumentId.startsWith("primary", ignoreCase = true)) return false
    val relative = treeDocumentId.substringAfter(":", "").trim().trimEnd('/')
    return relative.isEmpty()
}

/** Suggested Downloads directory when All files access is granted. */
fun primaryDownloadsDirectoryPath(): String = runCatching {
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalFile.absolutePath
}.getOrElse { "/storage/emulated/0/Download" }

private fun isUserAccessibleStorageVolumeRoot(canonicalParent: String): Boolean {
    val primaryRoot = runCatching {
        Environment.getExternalStorageDirectory().canonicalPath
    }.getOrNull() ?: return false
    if (canonicalParent == primaryRoot) return true
    val match = adoptableSdRootRegex.find(canonicalParent) ?: return false
    val sdRoot = match.value.trimEnd('/')
    return canonicalParent == sdRoot
}

/**
 * True when [rawPath] is the `Download` folder on primary shared storage or on an adoptable SD volume root.
 * That directory is typically not grantable as a SAF tree on modern Android; use All files access instead.
 */
fun isPublicDownloadsDirectoryOnUserAccessibleVolume(rawPath: String): Boolean {
    val canonical = normalizeFilesystemFolderPath(rawPath) ?: return false
    if (!isCanonicalPathUnderAllowedSharedStorage(canonical)) return false
    val folder = File(canonical)
    if (!folder.name.equals("Download", ignoreCase = true)) return false
    val parent = folder.parentFile ?: return false
    return isUserAccessibleStorageVolumeRoot(parent.canonicalPath)
}

/**
 * True when [treeUriString] is a SAF tree whose document id is the volume's `Download` root (e.g. `primary:Download`).
 */
fun isSafTreeUriPublicDownloadRoot(treeUriString: String): Boolean {
    if (!treeUriString.startsWith("content://")) return false
    val treeDocumentId = runCatching {
        DocumentsContract.getTreeDocumentId(Uri.parse(treeUriString))
    }.getOrNull() ?: return false
    val colonIndex = treeDocumentId.indexOf(':')
    if (colonIndex < 0) return false
    val relative = treeDocumentId.substring(colonIndex + 1).trim().trimEnd('/')
    return relative.equals("Download", ignoreCase = true)
}

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
