package dev.bikram.filepipe.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Both pattern boxes, in both modes: one pattern per line ([splitPatternLines]). */
class PatternListTest {
    @Test
    fun eachLineIsOnePatternTrimmedWithBlankLinesDropped() {
        assertEquals(listOf("IMG_*", "PXL_*", "Invoice, *"), splitPatternLines(" IMG_*\n\nPXL_* \r\nInvoice, *\n  "))
    }

    @Test
    fun commasAreOrdinaryCharacters() {
        val wildcard = buildFilenameRegexes("Invoice, *.pdf", isRegexPattern = false)
        assertTrue(matchesFilename("Invoice, March.pdf", wildcard, isRegexPattern = false))

        val regex = buildFilenameRegexes("^tmp_\\d{2,4}$", isRegexPattern = true)
        assertTrue(matchesFilename("tmp_123", regex, isRegexPattern = true))
    }

    @Test
    fun anyLineMatching() {
        val regexes = buildFilenameRegexes("^IMG_\n^PXL_", isRegexPattern = true)
        assertTrue(matchesFilename("IMG_0001.jpg", regexes, isRegexPattern = true))
        assertTrue(matchesFilename("PXL_0001.jpg", regexes, isRegexPattern = true))
        assertFalse(matchesFilename("DSC_0001.jpg", regexes, isRegexPattern = true))
    }

    @Test
    fun legacyWildcardCommasBecomeLinesAndRegexIsUntouched() {
        assertEquals("IMG_*\nPXL_*", legacyFilenamePatternAsLines("IMG_*, PXL_* ,", isRegexPattern = false))
        // A RegEx was one expression, where every comma was literal: it is already one line.
        assertEquals("^backup, old \\d{2,4}", legacyFilenamePatternAsLines("^backup, old \\d{2,4}", isRegexPattern = true))
        // Only commas matched nothing before; it is left as it was.
        assertEquals(" , ", legacyFilenamePatternAsLines(" , ", isRegexPattern = false))
    }

    @Test
    fun legacyWildcardConversionMatchesExactlyWhatTheOldCommaSplitDid() {
        val legacy = "IMG_*, *.png,report?.pdf"
        val oldSplit =
            legacy
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { globToRegex(it) }
        val converted = buildFilenameRegexes(legacyFilenamePatternAsLines(legacy, isRegexPattern = false), isRegexPattern = false)
        for (name in listOf("IMG_1.jpg", "a.png", "report1.pdf", "report12.pdf", "IMG_*, *.png")) {
            assertEquals(name, matchesFilename(name, oldSplit, isRegexPattern = false), matchesFilename(name, converted, isRegexPattern = false))
        }
    }

    @Test
    fun invalidRegexIsReportedByItsLineAsTheEditorShowsIt() {
        assertEquals(3, firstInvalidFilenameRegexLine("^IMG_\n\n[\n("))
        assertNull(firstInvalidFilenameRegexLine("^IMG_\\d{2,4}\n^PXL_"))
        assertEquals(2, firstInvalidExcludeRegexLine("\\.tmp$\n[/"))
        assertNull(firstInvalidExcludeRegexLine("\\.thumbnails/\n^cache$/"))
        assertFalse(isFilenameRegexValid("^IMG_\n["))
        assertTrue(isFilenameRegexValid(null))
    }
}
