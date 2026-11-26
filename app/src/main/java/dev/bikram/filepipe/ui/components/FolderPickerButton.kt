package dev.bikram.filepipe.ui.components

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.bikram.filepipe.data.storage.DOWNLOADS_DOCUMENTS_AUTHORITY
import dev.bikram.filepipe.data.storage.EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY
import dev.bikram.filepipe.data.storage.absoluteStoragePathToTreeUri
import dev.bikram.filepipe.data.storage.parseSafUri
import dev.bikram.filepipe.data.storage.removableStorageVolumeIdRegex
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.theme.compactControlShape
import java.io.File
import java.net.URI
import java.net.URLDecoder

private const val DOCUMENTS_DIRECTORY_NAME = "Documents"
private const val DOWNLOAD_DIRECTORY_NAME = "Download"
private val sdCardFilesystemPathRegex =
    Regex("^/storage/([0-9A-F]{4}-[0-9A-F]{4})(?:/(.*))?$", RegexOption.IGNORE_CASE)

@Composable
fun FolderPickerButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilePipeOutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = compactControlShape,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        FilePipeMaterialRoundedSymbol(name = "folder_open", contentDescription = null)
        Text(
            text = "  $label",
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Returns a user-friendly display label for a folder or document path.
 * Accepts both SAF `content://` URIs and `/storage/...` absolute paths.
 * Examples:
 *   content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FCamera -> "DCIM/Camera"
 *   content://com.android.externalstorage.documents/tree/primary%3A              -> [internalStorageRootDisplayName]
 *   content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AMovies      -> "[sdCardRootDisplayName]/Movies"
 *   content://com.chiller3.rsaf.documents/tree/Mega%3ATest                       -> "Mega/Test"
 *   content://com.chiller3.rsaf.documents/tree/Mega%3A                           -> "Mega"
 *   /storage/emulated/0                                                          -> [internalStorageRootDisplayName]
 *   /storage/emulated/0/Pictures                                                 -> "Pictures"
 */
fun displayPath(
    path: String,
    internalStorageRootDisplayName: String,
    sdCardRootDisplayName: String,
): String {
    if (path.startsWith("file://")) {
        val filePath =
            runCatching { URI(path).path }
                .getOrNull()
                ?.takeIf { candidate -> candidate.isNotEmpty() }
                ?: runCatching {
                    URLDecoder.decode(path.removePrefix("file://").replace("+", "%2B"), Charsets.UTF_8.name())
                }.getOrElse { path.removePrefix("file://") }
        return displayPath(filePath, internalStorageRootDisplayName, sdCardRootDisplayName)
    }
    if (path.startsWith("content://")) {
        val parsed = parseSafUri(path) ?: return path
        // A document (file) URI carries the item's own path in its document id; a plain tree (folder) URI
        // only exposes the granted root. Prefer the document id when present so a file in a subdirectory
        // shows its real location (e.g. "Downloads/sub/file.jpg") instead of collapsing to the rule's root.
        val docId = parsed.effectiveDocumentId ?: return path
        return formatSafDocumentDisplayPath(
            authority = parsed.authority,
            docId = docId,
            internalStorageRootDisplayName = internalStorageRootDisplayName,
            sdCardRootDisplayName = sdCardRootDisplayName,
        )
    }
    val primaryExternalRoot =
        runCatching {
            Environment.getExternalStorageDirectory().canonicalPath
        }.getOrNull()
    if (primaryExternalRoot != null) {
        val pathCanonical =
            runCatching {
                File(path.trim().trimEnd('/')).canonicalPath
            }.getOrNull()
        if (pathCanonical == primaryExternalRoot) return internalStorageRootDisplayName
        val primaryPrefix = "$primaryExternalRoot/"
        if (path.startsWith(primaryPrefix)) {
            return path.removePrefix(primaryPrefix).trimStart('/')
        }
    } else {
        val legacyRoot = "/storage/emulated/0"
        if (path.trim().trimEnd('/') == legacyRoot) return internalStorageRootDisplayName
        val legacyPrefix = "$legacyRoot/"
        if (path.startsWith(legacyPrefix)) return path.removePrefix(legacyPrefix).trimStart('/')
    }
    val sdCardMatch = sdCardFilesystemPathRegex.matchEntire(path.trim().trimEnd('/'))
    if (sdCardMatch != null) {
        val relative = sdCardMatch.groupValues[2].trim('/')
        return if (relative.isEmpty()) sdCardRootDisplayName else "$sdCardRootDisplayName/$relative"
    }
    return path
}

private fun formatSafDocumentDisplayPath(
    authority: String,
    docId: String,
    internalStorageRootDisplayName: String,
    sdCardRootDisplayName: String,
): String {
    if (authority.equals(EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY, ignoreCase = true)) {
        val hasColon = ':' in docId
        val volume = docId.substringBefore(':')
        val relative = docId.substringAfter(':', "").trim('/')
        return when {
            volume.equals("primary", ignoreCase = true) -> {
                if (relative.isBlank()) internalStorageRootDisplayName else relative
            }

            volume.equals("home", ignoreCase = true) -> {
                if (relative.isBlank()) DOCUMENTS_DIRECTORY_NAME else "$DOCUMENTS_DIRECTORY_NAME/$relative"
            }

            removableStorageVolumeIdRegex.matches(volume) -> {
                if (relative.isBlank()) sdCardRootDisplayName else "$sdCardRootDisplayName/$relative"
            }

            hasColon -> {
                if (relative.isBlank()) volume else "$volume/$relative"
            }

            else -> {
                docId
            }
        }
    }

    if (authority.equals(DOWNLOADS_DOCUMENTS_AUTHORITY, ignoreCase = true)) {
        if (docId.startsWith("raw:", ignoreCase = true)) {
            val rawFilePath = docId.substring("raw:".length)
            return displayPath(rawFilePath, internalStorageRootDisplayName, sdCardRootDisplayName)
        }
        if (docId.equals("downloads", ignoreCase = true)) {
            return DOWNLOAD_DIRECTORY_NAME
        }
    }

    if ("://" in docId || ':' !in docId) {
        return docId
    }
    val prefix = docId.substringBefore(':').trim()
    val relative = docId.substringAfter(':', "").trim('/')
    return when {
        prefix.isEmpty() -> relative.ifBlank { docId }
        relative.isBlank() -> prefix
        else -> "$prefix/$relative"
    }
}

fun previewSourceFolderDisplayPath(
    sourcePath: String,
    fileName: String,
    internalStorageRootDisplayName: String,
    sdCardRootDisplayName: String,
): String {
    val trimmed = sourcePath.trim()
    if (trimmed.isEmpty()) return trimmed

    if (trimmed.startsWith("content://")) {
        val parsed = parseSafUri(trimmed)
        val documentId = parsed?.effectiveDocumentId
        val parentDocId = documentId?.let(::parentDocumentId)
        if (parsed != null && parentDocId != null) {
            return formatSafDocumentDisplayPath(
                authority = parsed.authority,
                docId = parentDocId,
                internalStorageRootDisplayName = internalStorageRootDisplayName,
                sdCardRootDisplayName = sdCardRootDisplayName,
            )
        }
        return displayPath(
            trimmed.substringBeforeLast("/", missingDelimiterValue = trimmed),
            internalStorageRootDisplayName,
            sdCardRootDisplayName,
        )
    }

    val path =
        if (trimmed.startsWith("file://")) {
            runCatching { URI(trimmed).path }
                .getOrNull()
                ?.takeIf { candidate -> candidate.isNotEmpty() }
                ?: trimmed.removePrefix("file://")
        } else {
            trimmed
        }
    val parent = File(path).parent ?: path.removeSuffix(fileName).trimEnd('/', File.separatorChar)
    return displayPath(parent, internalStorageRootDisplayName, sdCardRootDisplayName)
}

private fun parentDocumentId(documentId: String): String? {
    val clean = documentId.trimEnd('/')
    val slashIndex = clean.lastIndexOf('/')
    if (slashIndex >= 0) return clean.substring(0, slashIndex)
    val colonIndex = clean.indexOf(':')
    if (colonIndex >= 0) return clean.substring(0, colonIndex + 1)
    return null
}

/**
 * Document URI derived from the tree (recommended for [ActivityResultContracts.OpenDocumentTree] initial location).
 * For legacy absolute paths, converts via [absoluteStoragePathToTreeUri].
 * For content:// URIs (SAF), uses them directly.
 */
fun absoluteStoragePathToOpenTreeInitialUri(path: String): Uri? {
    if (path.startsWith("content://")) {
        // Already a SAF URI — just wrap as a document URI for the picker hint
        return try {
            val treeUri = path.toUri()
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        } catch (_: Exception) {
            null
        }
    }
    val treeUri = absoluteStoragePathToTreeUri(path) ?: return null
    val documentId = DocumentsContract.getTreeDocumentId(treeUri)
    return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
}
