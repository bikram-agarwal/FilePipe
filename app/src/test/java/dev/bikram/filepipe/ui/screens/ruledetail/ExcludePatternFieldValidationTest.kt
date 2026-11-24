package dev.bikram.filepipe.ui.screens.ruledetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The editor's live RegEx errors must agree with what save and the scan do with the text: one
 * pattern per line in both boxes, and the error names the first line that doesn't compile.
 */
class ExcludePatternFieldValidationTest {
    @Test
    fun validLinesWithCommasInsideAreFineInBothBoxes() {
        val state =
            RuleDetailUiState(
                filenamePattern = "^IMG_\\d{2,4}\n^Invoice, ",
                isRegexPattern = true,
                excludePatternsText = "^tmp_\\d{2,4}$\n\\.thumbnails/",
                isExcludeRegexPattern = true,
            )
        assertNull(state.filenamePatternInvalidLine)
        assertNull(state.excludePatternInvalidLine)
        assertFalse(state.isFilenamePatternInvalidRegex)
        assertFalse(state.isExcludePatternInvalidRegex)
    }

    @Test
    fun theFirstBadLineIsNamedInBothBoxes() {
        val state =
            RuleDetailUiState(
                filenamePattern = "^IMG_\n[",
                isRegexPattern = true,
                excludePatternsText = "\\.tmp$\n\n(",
                isExcludeRegexPattern = true,
            )
        assertEquals(2, state.filenamePatternInvalidLine)
        assertEquals(3, state.excludePatternInvalidLine)
    }

    @Test
    fun wildcardModeIsNeverARegexError() {
        val state = RuleDetailUiState(filenamePattern = "[", excludePatternsText = "[")
        assertNull(state.filenamePatternInvalidLine)
        assertNull(state.excludePatternInvalidLine)
    }
}
