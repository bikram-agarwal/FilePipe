package dev.bikram.filepipe.data.repository

import android.webkit.MimeTypeMap
import dev.bikram.filepipe.domain.model.isAllFilesExtension
import dev.bikram.filepipe.domain.model.isNoExtensionToken

/**
 * Pure, stateless helpers for filtering scanned files by name / extension / exclusion pattern.
 * Extracted from [FileOperationRepository] so that class stays under detekt's LargeClass limit;
 * none of these depend on injected repository state (context, dispatcher, caches), so they live
 * as module-internal top-level functions in the same package.
 */

internal data class ExcludeFolderPattern(
    val regex: Regex,
    val matchRelativePath: Boolean,
)

internal data class FileScanFilterContext(
    val extensions: List<String>,
    val filenameRegexes: List<Regex>?,
    val isRegexPattern: Boolean,
    val excludeRegexes: List<Regex>?,
    val isExcludeRegexPattern: Boolean,
    val minFileSizeBytes: Long?,
    val maxFileSizeBytes: Long?,
    val minAgeMs: Long?,
    val maxAgeMs: Long?,
    val nowMs: Long,
    val excludeFolderPatterns: List<ExcludeFolderPattern>? = emptyList(),
)

internal fun passesCheapScanFilters(
    fileName: String,
    fileSizeBytes: Long,
    lastModifiedMs: Long,
    filters: FileScanFilterContext,
): Boolean {
    if (!matchesExtensions(fileName, filters.extensions)) return false
    if (!matchesFilename(fileName, filters.filenameRegexes, filters.isRegexPattern)) return false
    if (shouldExclude(fileName, filters.excludeRegexes, filters.isExcludeRegexPattern)) return false
    if (filters.minFileSizeBytes != null && fileSizeBytes < filters.minFileSizeBytes) return false
    if (filters.maxFileSizeBytes != null && fileSizeBytes > filters.maxFileSizeBytes) return false
    if (filters.minAgeMs == null && filters.maxAgeMs == null) return true
    val ageMs = filters.nowMs - lastModifiedMs
    return (filters.minAgeMs == null || ageMs >= filters.minAgeMs) &&
        (filters.maxAgeMs == null || ageMs <= filters.maxAgeMs)
}

internal fun mimeTypeFromName(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}

internal fun globToRegex(pattern: String): Regex {
    val sb = StringBuilder("^")
    for (ch in pattern) {
        when (ch) {
            '*' -> {
                sb.append(".*")
            }

            '?' -> {
                sb.append(".")
            }

            '.', '(', ')', '[', ']', '^', '$', '+', '{', '}', '|', '\\' -> {
                sb.append('\\')
                sb.append(ch)
            }

            else -> {
                sb.append(ch)
            }
        }
    }
    sb.append("$")
    return Regex(sb.toString(), RegexOption.IGNORE_CASE)
}

internal fun buildFilenameRegexes(
    filenamePattern: String?,
    isRegexPattern: Boolean,
): List<Regex>? {
    val trimmed = filenamePattern?.takeIf { it.isNotBlank() } ?: return null
    if (isRegexPattern) {
        return runCatching {
            listOf(Regex(trimmed, RegexOption.IGNORE_CASE))
        }.getOrElse { emptyList() }
    }
    return trimmed
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { globToRegex(it) }
}

internal fun matchesFilename(
    name: String,
    filenameRegexes: List<Regex>?,
    isRegexPattern: Boolean,
): Boolean {
    if (filenameRegexes == null) return true
    if (filenameRegexes.isEmpty()) return false
    return if (isRegexPattern) {
        filenameRegexes.any { it.containsMatchIn(name) }
    } else {
        filenameRegexes.any { it.matches(name) }
    }
}

private fun isFolderExcludePattern(
    trimmed: String,
    isRegexPattern: Boolean,
): Boolean = trimmed.endsWith("/") && (!isRegexPattern || !trimmed.endsWith("\\/"))

private fun stripTrailingFolderSlash(trimmed: String): String = trimmed.trimEnd('/')

internal fun buildExcludeRegexes(
    excludePatterns: List<String>,
    isRegexPattern: Boolean,
): List<Regex>? {
    val trimmed = excludePatterns.map { it.trim() }.filter { it.isNotEmpty() }
    if (trimmed.isEmpty()) return emptyList()
    if (isRegexPattern) {
        val allValid =
            trimmed.all { pattern ->
                val raw = if (isFolderExcludePattern(pattern, true)) stripTrailingFolderSlash(pattern) else pattern
                raw.isEmpty() || runCatching { Regex(raw, RegexOption.IGNORE_CASE) }.isSuccess
            }
        if (!allValid) return null
    }
    val filePatterns = trimmed.filterNot { isFolderExcludePattern(it, isRegexPattern) }
    if (filePatterns.isEmpty()) return emptyList()
    return if (isRegexPattern) {
        filePatterns.map { pattern -> Regex(pattern, RegexOption.IGNORE_CASE) }
    } else {
        filePatterns.map { pattern -> globToRegex(pattern) }
    }
}

internal fun buildExcludeFolderPatterns(
    excludePatterns: List<String>,
    isRegexPattern: Boolean,
): List<ExcludeFolderPattern>? {
    val trimmed = excludePatterns.map { it.trim() }.filter { it.isNotEmpty() }
    if (trimmed.isEmpty()) return emptyList()
    if (isRegexPattern) {
        val allValid =
            trimmed.all { pattern ->
                val raw = if (isFolderExcludePattern(pattern, true)) stripTrailingFolderSlash(pattern) else pattern
                raw.isEmpty() || runCatching { Regex(raw, RegexOption.IGNORE_CASE) }.isSuccess
            }
        if (!allValid) return null
    }
    val folderPatterns =
        trimmed
            .filter { isFolderExcludePattern(it, isRegexPattern) }
            .map { stripTrailingFolderSlash(it) }
            .filter { it.isNotEmpty() }
    if (folderPatterns.isEmpty()) return emptyList()
    return folderPatterns.map { pattern ->
        val regex =
            if (isRegexPattern) {
                Regex(pattern, RegexOption.IGNORE_CASE)
            } else {
                globToRegex(pattern)
            }
        ExcludeFolderPattern(
            regex = regex,
            matchRelativePath = pattern.contains('/'),
        )
    }
}

internal fun shouldExclude(
    name: String,
    excludeRegexes: List<Regex>?,
    isRegexPattern: Boolean,
): Boolean {
    if (excludeRegexes == null) return true
    if (excludeRegexes.isEmpty()) return false
    return if (isRegexPattern) {
        excludeRegexes.any { it.containsMatchIn(name) }
    } else {
        excludeRegexes.any { it.matches(name) }
    }
}

internal fun shouldExcludeFolder(
    relativeSegments: List<String>,
    excludeFolderPatterns: List<ExcludeFolderPattern>?,
    isRegexPattern: Boolean,
): Boolean {
    if (excludeFolderPatterns == null) return true
    if (excludeFolderPatterns.isEmpty() || relativeSegments.isEmpty()) return false
    val folderName = relativeSegments.last()
    val relativePath by lazy(LazyThreadSafetyMode.NONE) { relativeSegments.joinToString("/") }
    return excludeFolderPatterns.any { entry ->
        val target = if (entry.matchRelativePath) relativePath else folderName
        if (isRegexPattern) {
            entry.regex.containsMatchIn(target)
        } else {
            entry.regex.matches(target)
        }
    }
}

internal fun matchesExtensions(
    fileName: String,
    extensions: List<String>,
): Boolean {
    if (extensions.isEmpty()) return false
    if (extensions.any { isAllFilesExtension(it) }) return true

    // Use the LAST dot so a leading-dot file that still has a real suffix
    // (e.g. ".config.json", ".env.local") is treated as having extension "json"/"local",
    // not as "no extension". Only pure dotfiles (".gitignore") and trailing-dot names
    // ("file.") count as extensionless.
    val lastDot = fileName.lastIndexOf('.')
    val hasNoExtension = lastDot <= 0 || fileName.endsWith('.')
    if (hasNoExtension) return extensions.any { isNoExtensionToken(it) }

    val formattedFileExt = fileName.substring(lastDot).lowercase()
    val lowerExtensions =
        extensions
            .map {
                val lower = it.lowercase()
                if (lower.startsWith(".")) lower else ".$lower"
            }.toSet()

    return formattedFileExt in lowerExtensions
}
