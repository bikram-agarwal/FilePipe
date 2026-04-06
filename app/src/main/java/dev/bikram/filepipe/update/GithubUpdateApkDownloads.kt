package dev.bikram.filepipe.update

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Copies [cacheApkFile] into the public Downloads collection with [displayName] as shown in Files.
 * Uses [MediaStore.Downloads]; minSdk 30 is sufficient.
 */
fun copyUpdateApkToMediaStoreDownloads(
    context: Context,
    cacheApkFile: File,
    displayName: String
): Result<Unit> = runCatching {
    val safeName = displayName.replace('/', '_').replace('\\', '_').trim().ifBlank {
        FILEPIPE_UPDATE_APK_CACHE_NAME
    }
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
        put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val itemUri = resolver.insert(collection, values)
        ?: error("MediaStore insert returned null")
    try {
        resolver.openOutputStream(itemUri, "w")?.use { output ->
            FileInputStream(cacheApkFile).use { input ->
                input.copyTo(output)
            }
        } ?: error("openOutputStream returned null")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val publish = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(itemUri, publish, null, null)
        }
    } catch (t: Throwable) {
        runCatching { resolver.delete(itemUri, null, null) }
        throw t
    }
}

/** SHA-256 of file contents; reads in chunks. */
fun sha256HexOfFile(file: File): String? {
    if (!file.isFile || !file.canRead()) return null
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(8192)
    FileInputStream(file).use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
