package dev.bikram.filepipe.domain.usecase

import dev.bikram.filepipe.data.repository.splitPatternLines
import dev.bikram.filepipe.domain.model.ALL_FILES_EXTENSION
import dev.bikram.filepipe.domain.model.NO_EXTENSION_TOKEN
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.normalizeExtension

sealed class RuleConflict {
    data class NoExtensionPatternConflict(
        val pattern: String,
        val patternExtension: String,
    ) : RuleConflict()

    data class ExtensionPatternMismatch(
        val patternExtension: String,
        val selectedExtensions: List<String>,
    ) : RuleConflict()

    data object InvalidSizeRange : RuleConflict()

    data object InvalidAgeRange : RuleConflict()

    data class ExcludeAllPattern(
        val pattern: String,
    ) : RuleConflict()
}

object RuleConflictDetector {
    fun detectConflicts(rule: Rule): List<RuleConflict> =
        buildList {
            // 1. Range inversions
            if (rule.minFileSizeBytes != null &&
                rule.maxFileSizeBytes != null &&
                rule.minFileSizeBytes > rule.maxFileSizeBytes
            ) {
                add(RuleConflict.InvalidSizeRange)
            }
            if (rule.minAgeDays != null &&
                rule.maxAgeDays != null &&
                rule.minAgeDays > rule.maxAgeDays
            ) {
                add(RuleConflict.InvalidAgeRange)
            }

            // 2. Exclusion pattern wipes out all files
            rule.excludePatterns.firstOrNull { it.trim() == "*" || it.trim() == ".*" }?.let { wildcardPattern ->
                add(RuleConflict.ExcludeAllPattern(wildcardPattern))
            }

            // 3. Extension vs. Name pattern contradictions
            val pattern = rule.filenamePattern?.trim()
            if (!pattern.isNullOrBlank()) {
                val impliedExts = impliedPatternExtensions(pattern, rule.isRegexPattern)
                if (!impliedExts.isNullOrEmpty()) {
                    val hasAllFiles = rule.fileExtensions.contains(ALL_FILES_EXTENSION)
                    val normalizedAllowedExts = rule.fileExtensions.map { normalizeExtension(it) }.toSet()

                    if (!hasAllFiles && impliedExts.none { it in normalizedAllowedExts }) {
                        val displayExt = impliedExts.first()
                        val onlyNoExt = rule.fileExtensions.size == 1 && rule.fileExtensions.contains(NO_EXTENSION_TOKEN)
                        if (onlyNoExt) {
                            add(RuleConflict.NoExtensionPatternConflict(pattern, displayExt))
                        } else {
                            add(RuleConflict.ExtensionPatternMismatch(displayExt, rule.fileExtensions))
                        }
                    }
                }
            }
        }

    /**
     * The set of extensions a filename pattern can match, or null when the pattern does not pin the
     * extension (at least one alternative can match files of any type). A filename pattern, glob or
     * RegEx, is a disjunction of its lines (mirrors buildFilenameRegexes via splitPatternLines), so the
     * rule only matches nothing when EVERY alternative pins an extension and none of them are selected.
     * Extracting only the last line (as a naive single-pattern read would) wrongly flags "*.pdf" and
     * "*.png" on two lines against [pdf] as a conflict even though "*.pdf" matches.
     */
    private fun impliedPatternExtensions(
        pattern: String,
        isRegex: Boolean,
    ): Set<String>? {
        // Split exactly as a scan splits the box: one pattern per line, in both modes.
        val segments = splitPatternLines(pattern)
        if (segments.isEmpty()) return null
        val extensions = LinkedHashSet<String>()
        for (segment in segments) {
            // An unconstrained alternative (no extension pinned) can match a selected type → no conflict.
            val ext = extractExtensionFromPattern(segment, isRegex) ?: return null
            extensions += ext
        }
        return extensions
    }

    /** Extracts the extension a single glob segment / regex pins, or null if it pins none. */
    fun extractExtensionFromPattern(
        pattern: String,
        isRegex: Boolean,
    ): String? {
        if (!isRegex) {
            // Single glob segment like "*.json", "doc_*.pdf", "invoice.zip"
            if (pattern.contains('.') && !pattern.endsWith('.')) {
                val candidate = pattern.substringAfterLast('.').lowercase().trim()
                if (candidate.isNotEmpty() && candidate.all { it.isLetterOrDigit() }) {
                    return candidate
                }
            }
        } else {
            // Regex pattern like ".*\.json$", ".*\.pdf$", ".*\.docx"
            val match =
                Regex("""\.([a-zA-Z0-9]+)\$$""").find(pattern)
                    ?: Regex("""\.([a-zA-Z0-9]+)$""").find(pattern)
            if (match != null) {
                return match.groupValues[1].lowercase()
            }
        }
        return null
    }
}
