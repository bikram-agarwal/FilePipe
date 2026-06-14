package dev.bikram.filepipe.domain

import dev.bikram.filepipe.domain.model.FolderAccessResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the folder-access severity matrix in docs/FOLDER_ACCESS_ERRORS.md. The blocked-location
 * predicate is injected, so any path containing "BLOCKED" is treated as a blocked/all-files
 * location (internal-storage root, public Download, or an all-files path in Selective access).
 */
class RuleFolderAccessAssessmentTest {
    private val blockedIfNamed: (String) -> Boolean = { it.contains("BLOCKED") }

    private fun assess(
        source: Map<String, FolderAccessResult> = emptyMap(),
        destination: FolderAccessResult? = null,
    ) = assessRuleFolderAccess(source, destination, blockedIfNamed)

    @Test
    fun everythingAccessibleIsNoIssue() {
        val result = assess()
        assertEquals(RuleFolderSeverity.NONE, result.severity)
        assertFalse(result.onlySuppressibleSourceWarnings)
    }

    @Test
    fun missingSourceIsSuppressibleAmberWarning() {
        val result = assess(source = mapOf("/src" to FolderAccessResult.Unavailable))
        assertEquals(RuleFolderSeverity.WARNING, result.severity)
        assertTrue(result.onlySuppressibleSourceWarnings)
    }

    @Test
    fun blockedSourceLocationIsRedError() {
        val result = assess(source = mapOf("/src/BLOCKED" to FolderAccessResult.Unavailable))
        assertEquals(RuleFolderSeverity.ERROR, result.severity)
        assertFalse(result.onlySuppressibleSourceWarnings)
    }

    @Test
    fun sourcePermissionDeniedIsRedError() {
        val result = assess(source = mapOf("/src" to FolderAccessResult.PermissionDenied))
        assertEquals(RuleFolderSeverity.ERROR, result.severity)
        assertFalse(result.onlySuppressibleSourceWarnings)
    }

    @Test
    fun missingDestinationIsRedErrorEvenWhenNotBlocked() {
        val result = assess(destination = FolderAccessResult.Unavailable)
        assertEquals(RuleFolderSeverity.ERROR, result.severity)
        assertFalse(result.onlySuppressibleSourceWarnings)
    }

    @Test
    fun destinationPermissionDeniedIsRedError() {
        val result = assess(destination = FolderAccessResult.PermissionDenied)
        assertEquals(RuleFolderSeverity.ERROR, result.severity)
    }

    @Test
    fun suppressibleSourceWithDestinationIssueIsRedAndNotSuppressible() {
        val result =
            assess(
                source = mapOf("/src" to FolderAccessResult.Unavailable),
                destination = FolderAccessResult.Unavailable,
            )
        assertEquals(RuleFolderSeverity.ERROR, result.severity)
        // The card must NOT hide this even though the source warning alone would be suppressible.
        assertFalse(result.onlySuppressibleSourceWarnings)
    }

    @Test
    fun mixOfSuppressibleAndBlockedSourcesIsRed() {
        val result =
            assess(
                source =
                    mapOf(
                        "/src/ok-but-missing" to FolderAccessResult.Unavailable,
                        "/src/BLOCKED" to FolderAccessResult.Unavailable,
                    ),
            )
        assertEquals(RuleFolderSeverity.ERROR, result.severity)
        assertFalse(result.onlySuppressibleSourceWarnings)
    }

    @Test
    fun multipleMissingSourcesStaySuppressibleWhenNoneBlockedAndDestinationFine() {
        val result =
            assess(
                source =
                    mapOf(
                        "/a" to FolderAccessResult.Unavailable,
                        "/b" to FolderAccessResult.Unavailable,
                    ),
            )
        assertEquals(RuleFolderSeverity.WARNING, result.severity)
        assertTrue(result.onlySuppressibleSourceWarnings)
    }
}
