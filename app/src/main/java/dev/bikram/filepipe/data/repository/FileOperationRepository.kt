package dev.bikram.filepipe.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.PreviewFileResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileOperationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    suspend fun listMatchingFiles(
        folderUriString: String,
        extensions: List<String>,
        scanSubdirectories: Boolean = false,
        filenamePattern: String? = null,
        minFileSizeBytes: Long? = null,
        maxFileSizeBytes: Long? = null,
        minAgeDays: Int? = null,
        maxAgeDays: Int? = null,
        excludePatterns: List<String> = emptyList(),
        maxDepth: Int = 5
    ): List<FileEntry> = withContext(Dispatchers.IO) {
        if (!folderUriString.startsWith("content://")) return@withContext emptyList()

        val treeUri = Uri.parse(folderUriString)
        val folder = try {
            DocumentFile.fromTreeUri(context, treeUri)
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } ?: return@withContext emptyList()

        if (!folder.exists() || !folder.canRead()) return@withContext emptyList()

        val lowerExtensions = extensions.map {
            it.lowercase().let { e -> if (e.startsWith(".")) e else ".$e" }
        }.toSet()
        val filenameRegex = filenamePattern?.takeIf { it.isNotBlank() }?.let { globToRegex(it) }
        val excludeRegexes = excludePatterns.filter { it.isNotBlank() }.map { globToRegex(it) }
        val now = System.currentTimeMillis()
        val minAgeMs = minAgeDays?.let { TimeUnit.DAYS.toMillis(it.toLong()) }
        val maxAgeMs = maxAgeDays?.let { TimeUnit.DAYS.toMillis(it.toLong()) }

        val sequence = if (scanSubdirectories) {
            walkDocFiles(folder, maxDepth)
        } else {
            (folder.listFiles()?.asSequence() ?: emptySequence()).filter { it.isFile }
        }

        try {
            sequence
                .filter { doc ->
                    val ext = ".${doc.name.orEmpty().substringAfterLast('.').lowercase()}"
                    ext in lowerExtensions
                }
                .filter { doc -> filenameRegex == null || filenameRegex.matches(doc.name.orEmpty()) }
                .filter { doc -> excludeRegexes.none { it.matches(doc.name.orEmpty()) } }
                .filter { doc -> minFileSizeBytes == null || doc.length() >= minFileSizeBytes }
                .filter { doc -> maxFileSizeBytes == null || doc.length() <= maxFileSizeBytes }
                .filter { doc ->
                    if (minAgeMs == null && maxAgeMs == null) return@filter true
                    val ageMs = now - doc.lastModified()
                    (minAgeMs == null || ageMs >= minAgeMs) && (maxAgeMs == null || ageMs <= maxAgeMs)
                }
                .map { doc ->
                    FileEntry(
                        uri = doc.uri,
                        name = doc.name.orEmpty(),
                        size = doc.length(),
                        lastModifiedMs = doc.lastModified()
                    )
                }
                .toList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun walkDocFiles(dir: DocumentFile, maxDepth: Int): Sequence<DocumentFile> = sequence {
        if (maxDepth <= 0) return@sequence
        dir.listFiles()?.forEach { child ->
            if (child.isFile) yield(child)
            else if (child.isDirectory) yieldAll(walkDocFiles(child, maxDepth - 1))
        }
    }

    suspend fun moveFile(
        sourceEntry: FileEntry,
        destFolderUriString: String,
        conflictPolicy: ConflictPolicy,
        operationMode: OperationMode
    ): FileMoved = withContext(Dispatchers.IO + NonCancellable) {
        val destTree = DocumentFile.fromTreeUri(context, Uri.parse(destFolderUriString))
        if (destTree == null || !destTree.exists() || !destTree.canWrite()) {
            return@withContext FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = "Destination folder not accessible"
            )
        }

        val existing = destTree.findFile(sourceEntry.name)
        if (existing != null) {
            when (conflictPolicy) {
                ConflictPolicy.SKIP -> return@withContext FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = existing.uri.toString(),
                    fileSizeBytes = sourceEntry.size,
                    success = true,
                    skipped = true
                )
                ConflictPolicy.OVERWRITE -> existing.delete()
                ConflictPolicy.RENAME_SUFFIX -> { /* handled below */ }
            }
        }

        val destName = if (conflictPolicy == ConflictPolicy.RENAME_SUFFIX) {
            resolveDestName(sourceEntry.name, destTree)
        } else {
            sourceEntry.name
        }

        val mimeType = runCatching { context.contentResolver.getType(sourceEntry.uri) }.getOrNull()
            ?: mimeTypeFromName(sourceEntry.name)

        return@withContext try {
            val destFile = destTree.createFile(mimeType, destName)
                ?: return@withContext FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    success = false,
                    errorMessage = "Could not create destination file"
                )

            val inputStream = context.contentResolver.openInputStream(sourceEntry.uri)
            if (inputStream == null) {
                destFile.delete()
                return@withContext FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    success = false,
                    errorMessage = "Could not read source file"
                )
            }

            val outputStream = context.contentResolver.openOutputStream(destFile.uri)
            if (outputStream == null) {
                inputStream.close()
                destFile.delete()
                return@withContext FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    success = false,
                    errorMessage = "Could not write destination file"
                )
            }

            val bytesCopied = try {
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: IOException) {
                destFile.delete()
                return@withContext FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    success = false,
                    errorMessage = e.message ?: "IO error"
                )
            }

            if (sourceEntry.size > 0L && bytesCopied == 0L) {
                destFile.delete()
                return@withContext FileMoved(
                    fileName = sourceEntry.name,
                    sourceUri = sourceEntry.uri.toString(),
                    destinationUri = "",
                    fileSizeBytes = sourceEntry.size,
                    success = false,
                    errorMessage = "No data was copied"
                )
            }

            if (operationMode == OperationMode.MOVE) {
                DocumentFile.fromSingleUri(context, sourceEntry.uri)?.delete()
            }

            FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = destFile.uri.toString(),
                fileSizeBytes = sourceEntry.size,
                success = true
            )
        } catch (e: IOException) {
            FileMoved(
                fileName = sourceEntry.name,
                sourceUri = sourceEntry.uri.toString(),
                destinationUri = "",
                fileSizeBytes = sourceEntry.size,
                success = false,
                errorMessage = e.message ?: "IO error"
            )
        }
    }

    suspend fun simulateMove(
        sourceEntry: FileEntry,
        destFolderUriString: String,
        conflictPolicy: ConflictPolicy
    ): PreviewFileResult = withContext(Dispatchers.IO) {
        val destTree = DocumentFile.fromTreeUri(context, Uri.parse(destFolderUriString))
        val existing = destTree?.findFile(sourceEntry.name)

        if (existing == null) {
            return@withContext PreviewFileResult(
                fileName = sourceEntry.name,
                sourcePath = sourceEntry.uri.toString(),
                simulatedDestPath = "$destFolderUriString/${sourceEntry.name}",
                wouldSkip = false,
                wouldOverwrite = false,
                renamedTo = null,
                sizeBytes = sourceEntry.size
            )
        }

        when (conflictPolicy) {
            ConflictPolicy.SKIP -> PreviewFileResult(
                fileName = sourceEntry.name,
                sourcePath = sourceEntry.uri.toString(),
                simulatedDestPath = existing.uri.toString(),
                wouldSkip = true,
                wouldOverwrite = false,
                renamedTo = null,
                sizeBytes = sourceEntry.size
            )
            ConflictPolicy.OVERWRITE -> PreviewFileResult(
                fileName = sourceEntry.name,
                sourcePath = sourceEntry.uri.toString(),
                simulatedDestPath = existing.uri.toString(),
                wouldSkip = false,
                wouldOverwrite = true,
                renamedTo = null,
                sizeBytes = sourceEntry.size
            )
            ConflictPolicy.RENAME_SUFFIX -> {
                val resolvedName = resolveDestName(sourceEntry.name, destTree)
                PreviewFileResult(
                    fileName = sourceEntry.name,
                    sourcePath = sourceEntry.uri.toString(),
                    simulatedDestPath = "$destFolderUriString/$resolvedName",
                    wouldSkip = false,
                    wouldOverwrite = false,
                    renamedTo = if (resolvedName != sourceEntry.name) resolvedName else null,
                    sizeBytes = sourceEntry.size
                )
            }
        }
    }

    private val accessCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val ACCESS_CACHE_TTL_MS = 5_000L

    fun isAccessible(folderUriString: String): Boolean {
        if (!folderUriString.startsWith("content://")) return false
        val cached = accessCache[folderUriString]
        if (cached != null && System.currentTimeMillis() - cached.second < ACCESS_CACHE_TTL_MS) {
            return cached.first
        }
        val result = try {
            DocumentFile.fromTreeUri(context, Uri.parse(folderUriString))?.canRead() == true
        } catch (_: SecurityException) {
            false
        }
        accessCache[folderUriString] = result to System.currentTimeMillis()
        return result
    }

    fun invalidateAccessCache() {
        accessCache.clear()
    }

    private fun resolveDestName(name: String, destTree: DocumentFile): String {
        if (destTree.findFile(name) == null) return name
        val ext = name.substringAfterLast('.', "")
        val base = if (ext.isNotEmpty()) name.dropLast(ext.length + 1) else name
        var n = 1
        while (true) {
            val candidate = if (ext.isNotEmpty()) "$base($n).$ext" else "$base($n)"
            if (destTree.findFile(candidate) == null) return candidate
            n++
        }
    }

    private fun mimeTypeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun globToRegex(pattern: String): Regex {
        val sb = StringBuilder("^")
        for (ch in pattern) {
            when (ch) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                '.', '(', ')', '[', ']', '^', '$', '+', '{', '}', '|', '\\' -> {
                    sb.append('\\')
                    sb.append(ch)
                }
                else -> sb.append(ch)
            }
        }
        sb.append("$")
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }
}

data class FileEntry(
    val uri: Uri,
    val name: String,
    val size: Long,
    val lastModifiedMs: Long = 0L
)
