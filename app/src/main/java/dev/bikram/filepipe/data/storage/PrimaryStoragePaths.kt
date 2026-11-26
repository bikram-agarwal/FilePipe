package dev.bikram.filepipe.data.storage

import android.net.Uri
import android.provider.DocumentsContract
import java.net.URLDecoder

const val EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY = "com.android.externalstorage.documents"
const val DOWNLOADS_DOCUMENTS_AUTHORITY = "com.android.providers.downloads.documents"

internal val removableStorageVolumeIdRegex =
    Regex("^[0-9A-F]{4}-[0-9A-F]{4}$", RegexOption.IGNORE_CASE)

internal data class ParsedSafUri(
    val authority: String,
    val treeDocumentId: String?,
    val documentId: String?,
) {
    val effectiveDocumentId: String?
        get() = documentId ?: treeDocumentId

    val isExternalStorageProvider: Boolean
        get() = authority.equals(EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY, ignoreCase = true)
}

internal fun parseSafUri(uriString: String): ParsedSafUri? {
    val trimmed = uriString.trim()
    if (!trimmed.startsWith("content://", ignoreCase = true)) return null
    val withoutScheme = trimmed.substring("content://".length)
    val withoutQueryOrFragment = withoutScheme.substringBefore('#').substringBefore('?')
    val slashIndex = withoutQueryOrFragment.indexOf('/')
    val authority =
        if (slashIndex >= 0) {
            withoutQueryOrFragment.substring(0, slashIndex)
        } else {
            withoutQueryOrFragment
        }
    if (authority.isBlank()) return null
    val rawPath = if (slashIndex >= 0) withoutQueryOrFragment.substring(slashIndex + 1) else ""
    val rawSegments = rawPath.split('/').filter { segment -> segment.isNotEmpty() }

    var treeDocId: String? = null
    var docId: String? = null
    if (rawSegments.size >= 2 && rawSegments[0] == "tree") {
        treeDocId =
            runCatching {
                URLDecoder.decode(rawSegments[1].replace("+", "%2B"), Charsets.UTF_8.name())
            }.getOrElse { rawSegments[1] }
        if (rawSegments.size >= 4 && rawSegments[2] == "document") {
            docId =
                runCatching {
                    URLDecoder.decode(rawSegments[3].replace("+", "%2B"), Charsets.UTF_8.name())
                }.getOrElse { rawSegments[3] }
        }
    } else if (rawSegments.size >= 2 && rawSegments[0] == "document") {
        docId =
            runCatching {
                URLDecoder.decode(rawSegments[1].replace("+", "%2B"), Charsets.UTF_8.name())
            }.getOrElse { rawSegments[1] }
    }
    return ParsedSafUri(
        authority = authority,
        treeDocumentId = treeDocId,
        documentId = docId,
    )
}

fun safTreeUriToPath(uriString: String): String? {
    val parsed = parseSafUri(uriString) ?: return null
    if (!parsed.isExternalStorageProvider) return null
    val docId = parsed.treeDocumentId ?: return null
    val parts = docId.split(":", limit = 2)
    if (parts.size < 2) return null
    val volumeName = parts[0]
    val relativePath = parts[1].trimStart('/').trimEnd('/')
    return when {
        volumeName.equals("primary", ignoreCase = true) -> {
            if (relativePath.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$relativePath"
        }

        volumeName.equals("home", ignoreCase = true) -> {
            if (relativePath.isEmpty()) "/storage/emulated/0/Documents" else "/storage/emulated/0/Documents/$relativePath"
        }

        removableStorageVolumeIdRegex.matches(volumeName) -> {
            if (relativePath.isEmpty()) "/storage/$volumeName" else "/storage/$volumeName/$relativePath"
        }

        else -> {
            null
        }
    }
}

/**
 * When all-files access is active, maps persisted external-storage SAF tree URIs to absolute paths
 * for I/O and access checks. Non-externalstorage SAF providers (e.g. cloud/RSAF providers) and
 * unrecognized URIs are returned unchanged so they continue to operate through SAF.
 */
fun folderPathForFilesystemAccess(
    folderPathOrUri: String,
    filesystemAccessEnabled: Boolean,
): String {
    if (!filesystemAccessEnabled || !folderPathOrUri.startsWith("content://")) {
        return folderPathOrUri
    }
    return safTreeUriToPath(folderPathOrUri) ?: folderPathOrUri
}

/**
 * Normalizes user-facing paths (`/DCIM`, `DCIM/Camera`) to absolute primary emulated storage paths for SAF.
 */
fun normalizeToAbsoluteStoragePath(path: String): String {
    val trimmed = path.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    if (trimmed.startsWith("/storage/")) return trimmed
    val relative = trimmed.removePrefix("/")
    return "/storage/emulated/0/$relative"
}

/**
 * Builds a SAF tree [Uri] for [androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree].
 * Supports primary emulated storage and adoptable SD volumes (e.g. /storage/ABCD-1234/...).
 */
fun absoluteStoragePathToTreeUri(path: String): Uri? {
    val absolute = normalizeToAbsoluteStoragePath(path)
    if (absolute.isEmpty()) return null
    val primaryPrefix = "/storage/emulated/0"
    if (absolute.startsWith(primaryPrefix)) {
        val relative = absolute.removePrefix(primaryPrefix).trimStart('/')
        val documentId = if (relative.isEmpty()) "primary:" else "primary:$relative"
        return DocumentsContract.buildTreeDocumentUri(EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY, documentId)
    }
    val sdMatch = Regex("^/storage/([A-F0-9]{4}-[A-F0-9]{4})(/.*)?$", RegexOption.IGNORE_CASE).find(absolute) ?: return null
    val volumeId = sdMatch.groupValues[1]
    val tail = sdMatch.groupValues[2].trimStart('/').trimEnd('/')
    val documentId = if (tail.isEmpty()) "$volumeId:" else "$volumeId:$tail"
    return DocumentsContract.buildTreeDocumentUri(EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY, documentId)
}

internal fun isSafOrFilesystemChildFolder(
    child: String,
    parent: String,
): Boolean {
    if (child.startsWith("content://") && parent.startsWith("content://")) {
        val childParsed = parseSafUri(child) ?: return false
        val parentParsed = parseSafUri(parent) ?: return false
        if (!childParsed.authority.equals(parentParsed.authority, ignoreCase = true)) return false
        val childId = childParsed.treeDocumentId ?: return false
        val parentId = parentParsed.treeDocumentId ?: return false
        if (':' in parentId || ':' in childId) {
            if (':' !in parentId || ':' !in childId) return false
            val childRoot = childId.substringBefore(':')
            val parentRoot = parentId.substringBefore(':')
            val rootsMatch =
                if (parentParsed.isExternalStorageProvider) {
                    childRoot.equals(parentRoot, ignoreCase = true)
                } else {
                    childRoot == parentRoot
                }
            if (!rootsMatch) return false
            val childPath = childId.substringAfter(':', "").trim('/')
            val parentPath = parentId.substringAfter(':', "").trim('/')
            if (childPath.isEmpty()) return false
            return if (parentPath.isEmpty()) {
                true
            } else {
                childPath.startsWith("$parentPath/")
            }
        }
        val cleanParent = parentId.trimEnd('/')
        val cleanChild = childId.trimEnd('/')
        return cleanParent.isNotEmpty() && cleanChild.startsWith("$cleanParent/")
    }
    if (child.startsWith("content://") || parent.startsWith("content://")) {
        return false
    }
    val cleanParent = parent.trimEnd('/')
    val cleanChild = child.trimEnd('/')
    return cleanParent.isNotEmpty() && cleanChild.startsWith("$cleanParent/")
}
