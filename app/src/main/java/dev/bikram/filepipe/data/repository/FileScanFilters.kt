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

internal fun globToRegex(pattern: String): Regex = Regex("^${globToRegexBody(pattern)}$", RegexOption.IGNORE_CASE)

/** Characters a wildcard pattern takes literally that a RegEx would not: [globToRegexBody] escapes them. */
private val GlobLiteralRegexChars = setOf('.', '(', ')', '[', ']', '^', '$', '+', '{', '}', '|', '\\')

/** RegEx syntax with no wildcard counterpart (besides `.` / `.*`, which [regexToWildcardOrNull] maps). */
private val RegexOnlyChars = setOf('(', ')', '[', ']', '^', '$', '+', '{', '}', '|', '*', '?')

/**
 * The RegEx source for a wildcard, without anchors: `*` -> `.*`, `?` -> `.`, every other RegEx
 * metacharacter escaped. The one wildcard translation - scans compile it ([globToRegex]) and the
 * RegEx toggle writes it into the box ([convertPatternsToRegex]), so the two can't drift apart.
 */
internal fun globToRegexBody(pattern: String): String =
    buildString {
        for (ch in pattern) {
            when (ch) {
                '*' -> append(".*")
                '?' -> append(".")
                in GlobLiteralRegexChars -> append('\\').append(ch)
                else -> append(ch)
            }
        }
    }

/**
 * The wildcard [regex] stands for, when it is exactly what [globToRegexBody] produces between `^` and
 * `$` - escaped literals, `.` and `.*` - else null (a real RegEx, which no wildcard can express).
 */
private fun regexToWildcardOrNull(regex: String): String? {
    if (regex.length < 2 || !regex.startsWith('^') || !regex.endsWith('$')) return null
    val inner = regex.substring(1, regex.length - 1)
    val wildcard = StringBuilder()
    var index = 0
    while (index < inner.length) {
        val char = inner[index]
        when {
            char == '\\' -> {
                // Also catches "^a\$", whose final "$" is escaped rather than an anchor.
                val escaped = inner.getOrNull(index + 1)?.takeIf { it in GlobLiteralRegexChars } ?: return null
                wildcard.append(escaped)
                index += 2
            }

            char == '.' && inner.getOrNull(index + 1) == '*' -> {
                wildcard.append('*')
                index += 2
            }

            char == '.' -> {
                wildcard.append('?')
                index++
            }

            char in RegexOnlyChars -> {
                return null
            }

            else -> {
                wildcard.append(char)
                index++
            }
        }
    }
    return wildcard.toString()
}

/**
 * One wildcard line rewritten as the RegEx that matches exactly the same names (or, as a folder
 * pattern in an Exclude box, the same folders): anchored with `^…$`, since a wildcard matches the
 * whole name and a RegEx matches anywhere. A folder pattern keeps its trailing `/`, and a leading
 * `/` too, which means "from the source folder" in both modes.
 */
private fun wildcardLineToRegex(
    line: String,
    folderPatterns: Boolean,
): String {
    val pattern = line.trim()
    if (folderPatterns && isFolderExcludePattern(pattern, isRegexPattern = false)) {
        val folder = pattern.trimEnd('/')
        val anchoredAtRoot = folder.startsWith('/')
        val body = folder.trimStart('/')
        if (body.isEmpty()) return pattern
        return (if (anchoredAtRoot) "/" else "") + "^" + globToRegexBody(body) + "$/"
    }
    return "^" + globToRegexBody(pattern) + "$"
}

/** [wildcardLineToRegex] in reverse, or null when the line is a real RegEx with no wildcard form. */
private fun regexLineToWildcardOrNull(
    line: String,
    folderPatterns: Boolean,
): String? {
    val pattern = line.trim()
    if (folderPatterns && isFolderExcludePattern(pattern, isRegexPattern = true)) {
        val folder = pattern.trimEnd('/')
        val anchoredAtRoot = folder.startsWith('/')
        val wildcard = regexToWildcardOrNull(folder.trimStart('/')) ?: return null
        return (if (anchoredAtRoot) "/" else "") + wildcard + "/"
    }
    return regexToWildcardOrNull(pattern)
}

/**
 * A pattern box's text rewritten for RegEx On: every line becomes the RegEx matching exactly what the
 * wildcard did, so flipping the toggle never changes what a rule matches. Blank lines stay, keeping
 * line numbers. [folderPatterns] for an Exclude box, where a trailing `/` marks a folder.
 */
internal fun convertPatternsToRegex(
    text: String,
    folderPatterns: Boolean,
): String = text.lines().joinToString("\n") { line -> if (line.isBlank()) line else wildcardLineToRegex(line, folderPatterns) }

/** [convertPatternsToWildcards]'s result: the rewritten text, and the 1-based lines it had to leave as typed. */
internal data class WildcardConversion(
    val text: String,
    val unconvertibleLines: List<Int>,
)

/**
 * A pattern box's text rewritten for RegEx Off. A line written like [convertPatternsToRegex]'s output
 * (`^IMG_.*$`) becomes its wildcard again; a real RegEx (`\d{4}`, groups, alternation…) has none, so
 * it stays as typed and is listed - as a wildcard it would only match a name spelled exactly like it.
 */
internal fun convertPatternsToWildcards(
    text: String,
    folderPatterns: Boolean,
): WildcardConversion {
    val unconvertible = mutableListOf<Int>()
    val converted =
        text.lines().mapIndexed { index, line ->
            if (line.isBlank()) {
                line
            } else {
                regexLineToWildcardOrNull(line, folderPatterns) ?: line.trim().also { unconvertible += index + 1 }
            }
        }
    return WildcardConversion(text = converted.joinToString("\n"), unconvertibleLines = unconvertible)
}

/**
 * How every pattern box splits its text into patterns - Filename patterns and Exclude patterns, in
 * wildcard and RegEx mode alike: one pattern per line, each trimmed, blank lines dropped. A file name
 * can't contain a line break, so no pattern ever needs one; commas and every other character are
 * simply part of the pattern, with nothing to escape.
 */
internal fun splitPatternLines(text: String): List<String> =
    text
        .lineSequence()
        .map { line -> line.trim() }
        .filter { line -> line.isNotEmpty() }
        .toList()

/**
 * A filename pattern stored before patterns were one per line, rewritten as lines so it matches
 * exactly what it matched then. A wildcard pattern was comma separated (every comma split it), so its
 * commas become line breaks. A RegEx pattern was a single expression, which is already one line, so
 * it is returned as is. Used by the 12 -> 13 migration and by import of older backups. Exclude
 * patterns need nothing: they were always stored as a list.
 */
internal fun legacyFilenamePatternAsLines(
    pattern: String,
    isRegexPattern: Boolean,
): String {
    if (isRegexPattern) return pattern
    val lines = pattern.split(",").map { piece -> piece.trim() }.filter { piece -> piece.isNotEmpty() }
    // A box of only commas matched nothing before; left as is, it still (practically) matches nothing.
    return if (lines.isEmpty()) pattern else lines.joinToString("\n")
}

internal fun buildFilenameRegexes(
    filenamePattern: String?,
    isRegexPattern: Boolean,
): List<Regex>? {
    val text = filenamePattern?.takeIf { it.isNotBlank() } ?: return null
    val patterns = splitPatternLines(text)
    if (isRegexPattern) {
        // Any line that doesn't compile fails the whole box closed (matches nothing), as before.
        return runCatching {
            patterns.map { pattern -> Regex(pattern, RegexOption.IGNORE_CASE) }
        }.getOrElse { emptyList() }
    }
    return patterns.map { globToRegex(it) }
}

/**
 * The 1-based line of the first pattern in [text] that [compiles] rejects, or null when every line
 * passes. Counts the box's own lines, blank ones included, so the number is the line the user sees.
 */
private fun firstInvalidPatternLine(
    text: String,
    compiles: (String) -> Boolean,
): Int? {
    text.lines().forEachIndexed { index, line ->
        val pattern = line.trim()
        if (pattern.isNotEmpty() && !compiles(pattern)) return index + 1
    }
    return null
}

/** The first line of a RegEx Filename patterns box that doesn't compile, if any. */
internal fun firstInvalidFilenameRegexLine(text: String?): Int? = text?.let { firstInvalidPatternLine(it, ::compilesAsRegex) }

private fun compilesAsRegex(pattern: String): Boolean = runCatching { Regex(pattern) }.isSuccess

/** The first line of a RegEx Exclude patterns box that doesn't compile as a scan reads it, if any. */
internal fun firstInvalidExcludeRegexLine(text: String): Int? = firstInvalidPatternLine(text, ::compilesAsExcludeRegex)

private fun compilesAsExcludeRegex(pattern: String): Boolean = areExcludePatternsValid(listOf(pattern), isRegexPattern = true)

/**
 * Whether every RegEx in a Filename patterns box compiles, line by line as a scan reads it. The one
 * check behind the editor's field error, save-time validation and backup import.
 */
internal fun isFilenameRegexValid(filenamePattern: String?): Boolean = firstInvalidFilenameRegexLine(filenamePattern) == null

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

/**
 * A rule's exclude patterns, split into file and folder patterns and compiled. Either list is null when
 * a RegEx pattern doesn't compile: the rule then excludes everything rather than run with part of its
 * exclusions silently dropped ([shouldExclude] / [shouldExcludeFolder] treat null that way).
 */
internal data class ExcludePatterns(
    val fileRegexes: List<Regex>?,
    val folderPatterns: List<ExcludeFolderPattern>?,
)

private val InvalidExcludePatterns = ExcludePatterns(fileRegexes = null, folderPatterns = null)

/**
 * Parses [excludePatterns] once, compiling each pattern once. A pattern ending in `/` is a folder
 * pattern (see [isFolderExcludePattern]); anything else applies to file names. A folder pattern
 * containing `/` matches the folder's path relative to the source folder rather than its name.
 * A folder pattern may start with `/` to match the folder's path from the source folder (as in
 * .gitignore): `/cache/` skips only a `cache` folder directly inside it, where `cache/` skips one at
 * any depth. The same in RegEx mode, where `/^cache$/` is the exact counterpart of wildcard
 * `/cache/` - which is what lets the RegEx toggle convert it ([convertPatternsToRegex]).
 */
internal fun parseExcludePatterns(
    excludePatterns: List<String>,
    isRegexPattern: Boolean,
): ExcludePatterns {
    val fileRegexes = mutableListOf<Regex>()
    val folderPatterns = mutableListOf<ExcludeFolderPattern>()
    for (rawPattern in excludePatterns) {
        val pattern = rawPattern.trim()
        if (pattern.isEmpty()) continue
        if (!isFolderExcludePattern(pattern, isRegexPattern)) {
            fileRegexes += compileExcludePattern(pattern, isRegexPattern) ?: return InvalidExcludePatterns
            continue
        }
        val folderPattern = pattern.trimEnd('/')
        val anchoredAtRoot = folderPattern.startsWith('/')
        val unanchored = if (anchoredAtRoot) folderPattern.trimStart('/') else folderPattern
        if (unanchored.isEmpty()) continue
        folderPatterns +=
            ExcludeFolderPattern(
                regex = compileExcludePattern(unanchored, isRegexPattern) ?: return InvalidExcludePatterns,
                matchRelativePath = anchoredAtRoot || unanchored.contains('/'),
            )
    }
    return ExcludePatterns(fileRegexes = fileRegexes, folderPatterns = folderPatterns)
}

/**
 * Whether [excludePatterns] all compile as a scan reads them ([parseExcludePatterns]). Wildcards always
 * do. The one validity check behind the editor's error and the save-time validation, so a pattern
 * can't look fine in the field yet be refused on save (or the reverse).
 */
internal fun areExcludePatternsValid(
    excludePatterns: List<String>,
    isRegexPattern: Boolean,
): Boolean = parseExcludePatterns(excludePatterns, isRegexPattern).fileRegexes != null

/** Null only for a RegEx that doesn't compile; a glob always converts. */
private fun compileExcludePattern(
    pattern: String,
    isRegexPattern: Boolean,
): Regex? =
    if (isRegexPattern) {
        runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
    } else {
        globToRegex(pattern)
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
