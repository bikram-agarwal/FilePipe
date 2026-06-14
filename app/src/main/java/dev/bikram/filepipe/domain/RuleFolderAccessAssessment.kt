package dev.bikram.filepipe.domain

import dev.bikram.filepipe.domain.model.FolderAccessResult

/** Aggregate folder-access severity for a rule. See `docs/FOLDER_ACCESS_ERRORS.md`. */
enum class RuleFolderSeverity { NONE, WARNING, ERROR }

data class RuleFolderAccessAssessment(
    /**
     * Aggregate severity BEFORE the per-rule "hide missing source folder warnings" preference is
     * applied. The rule card applies that preference on top; the detail screen does not.
     */
    val severity: RuleFolderSeverity,
    /**
     * True when the only problems are *suppressible* missing-source warnings — a source folder that
     * is merely [FolderAccessResult.Unavailable] and not a blocked/all-files location — and the
     * destination is fine. These are the amber warnings the rule card may hide; the detail screen
     * always surfaces them.
     */
    val onlySuppressibleSourceWarnings: Boolean,
    /**
     * True when there are source issues and every one of them is a suppressible amber warning
     * (regardless of the destination). Drives the *source folder* highlight color on the detail
     * screen — amber vs red — independently of the destination's own highlight.
     */
    val sourceIssuesAllSuppressible: Boolean,
)

/**
 * Single source of truth for the folder-access severity matrix in `docs/FOLDER_ACCESS_ERRORS.md`.
 *
 * Inputs are the already-resolved access results: [sourceIssues] holds only the source paths that
 * are NOT [FolderAccessResult.Accessible], and [destinationIssue] is null when the destination is
 * blank or accessible. [isBlockedLocation] reports whether a path is a location that can't be used
 * in the current mode (internal-storage / public-Download root, or an all-files path while in
 * Selective access) — injected so this stays a pure, unit-testable function with no Android deps.
 *
 * Severity rules:
 * - Any destination issue (lost permission, missing, or blocked) is a red error.
 * - A source folder that lost permission, or is a blocked/all-files location, is a red error.
 * - A source folder that is merely Unavailable and not a blocked location is a suppressible amber
 *   warning ("source missing; the rule may still work with its other sources").
 */
fun assessRuleFolderAccess(
    sourceIssues: Map<String, FolderAccessResult>,
    destinationIssue: FolderAccessResult?,
    isBlockedLocation: (String) -> Boolean,
): RuleFolderAccessAssessment {
    val hasRedSourceIssue =
        sourceIssues.any { (path, result) ->
            result != FolderAccessResult.Unavailable || isBlockedLocation(path)
        }
    val hasSuppressibleSourceWarning =
        sourceIssues.any { (path, result) ->
            result == FolderAccessResult.Unavailable && !isBlockedLocation(path)
        }
    val hasDestinationIssue = destinationIssue != null

    val severity =
        when {
            hasDestinationIssue || hasRedSourceIssue -> RuleFolderSeverity.ERROR
            hasSuppressibleSourceWarning -> RuleFolderSeverity.WARNING
            else -> RuleFolderSeverity.NONE
        }
    val sourceIssuesAllSuppressible = sourceIssues.isNotEmpty() && !hasRedSourceIssue
    return RuleFolderAccessAssessment(
        severity = severity,
        onlySuppressibleSourceWarnings = !hasDestinationIssue && sourceIssuesAllSuppressible,
        sourceIssuesAllSuppressible = sourceIssuesAllSuppressible,
    )
}
