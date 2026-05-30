package dev.bikram.filepipe.ui.components

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import dev.bikram.filepipe.data.storage.absoluteStoragePathToTreeUri
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.theme.compactControlShape
import java.io.File

@Composable
fun FolderPickerButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilePipeOutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = compactControlShape,
    ) {
        FilePipeMaterialRoundedSymbol(name = "folder_open", contentDescription = null)
        Text("  $label")
    }
}

/**
 * Returns a user-friendly display label for a folder.
 * Accepts both SAF content:// URIs (new format) and legacy /storage/... absolute paths.
 * Examples:
 *   content://...tree/primary%3ADCIM%2FCamera  →  "DCIM/Camera"
 *   content://... (primary volume root)        →  [internalStorageRootDisplayName]
 *   /storage/emulated/0                        →  [internalStorageRootDisplayName] (e.g. "Internal Storage")
 *   /storage/emulated/0/Pictures               →  "Pictures"
 *   content://...tree/1A2B-3C4D%3AMovies       →  "SD Card/Movies"
 */
fun displayPath(
    path: String,
    internalStorageRootDisplayName: String,
): String {
    if (path.startsWith("file://")) {
        val filePath = path.toUri().path ?: path.removePrefix("file://")
        return displayPath(filePath, internalStorageRootDisplayName)
    }
    if (path.startsWith("content://")) {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(path.toUri())
            val relative = docId.substringAfter(":", "")
            when {
                relative.isBlank() && docId.startsWith("primary", ignoreCase = true) -> {
                    internalStorageRootDisplayName
                }

                relative.isBlank() -> {
                    docId
                }

                docId.startsWith("primary", ignoreCase = true) -> {
                    relative
                }

                else -> {
                    "SD Card/$relative"
                }
            }
        } catch (_: Exception) {
            path
        }
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
    val sdCardPrefix = Regex("^/storage/[A-F0-9]{4}-[A-F0-9]{4}/")
    return path.replace(sdCardPrefix) { "SD Card/" }
}

fun previewSourceFolderDisplayPath(
    sourcePath: String,
    fileName: String,
    internalStorageRootDisplayName: String,
): String {
    val trimmed = sourcePath.trim()
    if (trimmed.isEmpty()) return trimmed

    if (trimmed.startsWith("content://")) {
        val parentTreeUri =
            runCatching {
                val uri = trimmed.toUri()
                val authority = uri.authority ?: return@runCatching null
                val documentId =
                    when {
                        "document" in uri.pathSegments -> DocumentsContract.getDocumentId(uri)
                        "tree" in uri.pathSegments -> DocumentsContract.getTreeDocumentId(uri)
                        else -> null
                    } ?: return@runCatching null
                val parentDocumentId = parentDocumentId(documentId) ?: return@runCatching null
                DocumentsContract.buildTreeDocumentUri(authority, parentDocumentId).toString()
            }.getOrNull()
        if (parentTreeUri != null) {
            return displayPath(parentTreeUri, internalStorageRootDisplayName)
        }
        return displayPath(trimmed.substringBeforeLast("/", missingDelimiterValue = trimmed), internalStorageRootDisplayName)
    }

    val path =
        if (trimmed.startsWith("file://")) {
            trimmed.toUri().path ?: trimmed.removePrefix("file://")
        } else {
            trimmed
        }
    val parent = File(path).parent ?: path.removeSuffix(fileName).trimEnd('/', File.separatorChar)
    return displayPath(parent, internalStorageRootDisplayName)
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
