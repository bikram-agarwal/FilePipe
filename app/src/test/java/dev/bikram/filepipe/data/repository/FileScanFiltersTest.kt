package dev.bikram.filepipe.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileScanFiltersTest {
    @Test
    fun invalidFilenameRegexMatchesNothing() {
        val compiledPatterns = buildFilenameRegexes("[", isRegexPattern = true)

        assertFalse(matchesFilename("photo.jpg", compiledPatterns, isRegexPattern = true))
    }

    @Test
    fun missingFilenamePatternStillMatches() {
        val compiledPatterns = buildFilenameRegexes(null, isRegexPattern = true)

        assertTrue(matchesFilename("photo.jpg", compiledPatterns, isRegexPattern = true))
    }

    @Test
    fun invalidExcludeRegexExcludesEverything() {
        val compiledPatterns = buildExcludeRegexes(listOf("["), isRegexPattern = true)

        assertTrue(shouldExclude("photo.jpg", compiledPatterns, isRegexPattern = true))
    }

    @Test
    fun cheapFiltersRejectNonMatchingFilesBeforeOrientationProbe() {
        val filters =
            FileScanFilterContext(
                extensions = listOf("jpg"),
                filenameRegexes = buildFilenameRegexes("holiday*", isRegexPattern = false),
                isRegexPattern = false,
                excludeRegexes = buildExcludeRegexes(listOf("*private*"), isRegexPattern = false),
                isExcludeRegexPattern = false,
                minFileSizeBytes = 100L,
                maxFileSizeBytes = 1_000L,
                minAgeMs = 1_000L,
                maxAgeMs = 10_000L,
                nowMs = 20_000L,
            )

        assertTrue(passesCheapScanFilters("holiday.jpg", 500L, 15_000L, filters))
        assertFalse(passesCheapScanFilters("holiday.png", 500L, 15_000L, filters))
        assertFalse(passesCheapScanFilters("holiday-private.jpg", 500L, 15_000L, filters))
        assertFalse(passesCheapScanFilters("holiday.jpg", 99L, 15_000L, filters))
        assertFalse(passesCheapScanFilters("holiday.jpg", 500L, 19_500L, filters))
    }

    @Test
    fun cheapFiltersPreserveInvalidRegexFailClosedBehavior() {
        val invalidFilenameFilters =
            FileScanFilterContext(
                extensions = listOf("jpg"),
                filenameRegexes = buildFilenameRegexes("[", isRegexPattern = true),
                isRegexPattern = true,
                excludeRegexes = emptyList(),
                isExcludeRegexPattern = false,
                minFileSizeBytes = null,
                maxFileSizeBytes = null,
                minAgeMs = null,
                maxAgeMs = null,
                nowMs = 0L,
            )
        val invalidExcludeFilters =
            invalidFilenameFilters.copy(
                filenameRegexes = null,
                excludeRegexes = buildExcludeRegexes(listOf("["), isRegexPattern = true),
                isExcludeRegexPattern = true,
            )

        assertFalse(passesCheapScanFilters("photo.jpg", 100L, 0L, invalidFilenameFilters))
        assertFalse(passesCheapScanFilters("photo.jpg", 100L, 0L, invalidExcludeFilters))
    }

    @Test
    fun trailingSlashExcludePatternsMatchFoldersOnlyAndNotFiles() {
        val patterns = listOf(".thumbnails/", "thumbnails/", "cache/", "thumb*.*")
        val fileRegexes = buildExcludeRegexes(patterns, isRegexPattern = false)
        val folderPatterns = buildExcludeFolderPatterns(patterns, isRegexPattern = false)

        // File exclude pattern matches thumb*.* only, not files named cache or genuine photos
        assertTrue(shouldExclude("thumb_01.jpg", fileRegexes, isRegexPattern = false))
        assertFalse(shouldExclude("cache", fileRegexes, isRegexPattern = false))
        assertFalse(shouldExclude("IMG_1234.jpg", fileRegexes, isRegexPattern = false))

        // Folder exclude patterns match folder segments at any depth, case-insensitively
        assertTrue(shouldExcludeFolder(listOf(".thumbnails"), folderPatterns, isRegexPattern = false))
        assertTrue(shouldExcludeFolder(listOf("Instagram", "Thumbnails"), folderPatterns, isRegexPattern = false))
        assertTrue(shouldExcludeFolder(listOf("Sub", "CACHE"), folderPatterns, isRegexPattern = false))
        assertFalse(shouldExcludeFolder(listOf("Camera"), folderPatterns, isRegexPattern = false))
        assertFalse(shouldExcludeFolder(listOf("thumb.backup"), folderPatterns, isRegexPattern = false))
    }

    @Test
    fun relativePathFolderExcludePatternMatchesNestedRelativePath() {
        val patterns = listOf("DCIM/cache/")
        val folderPatterns = buildExcludeFolderPatterns(patterns, isRegexPattern = false)

        assertTrue(shouldExcludeFolder(listOf("DCIM", "cache"), folderPatterns, isRegexPattern = false))
        assertFalse(shouldExcludeFolder(listOf("Pictures", "cache"), folderPatterns, isRegexPattern = false))
    }

    @Test
    fun regexFolderExcludePatternSupportsTrailingSlashAndFailsClosedOnInvalidRegex() {
        val valid = buildExcludeFolderPatterns(listOf("""\.thumbnails/""", "cache/"), isRegexPattern = true)
        assertTrue(shouldExcludeFolder(listOf(".thumbnails"), valid, isRegexPattern = true))
        assertTrue(shouldExcludeFolder(listOf("app_cache"), valid, isRegexPattern = true))
        assertFalse(shouldExcludeFolder(listOf("Camera"), valid, isRegexPattern = true))

        val invalidFolder = buildExcludeFolderPatterns(listOf("["), isRegexPattern = true)
        assertTrue(shouldExcludeFolder(listOf("Camera"), invalidFolder, isRegexPattern = true))

        val invalidMixed = buildExcludeRegexes(listOf("thumb.*", "[/"), isRegexPattern = true)
        assertTrue(shouldExclude("photo.jpg", invalidMixed, isRegexPattern = true))
    }

    @Test
    fun incompleteKnownSizeCopyIsRejected() {
        assertFalse(isCompleteCopy(expectedBytes = 100L, copiedBytes = 99L))
        assertTrue(isCompleteCopy(expectedBytes = 100L, copiedBytes = 100L))
        assertTrue(isCompleteCopy(expectedBytes = 0L, copiedBytes = 0L))
        assertFalse(isCompleteCopy(expectedBytes = 0L, copiedBytes = 1L))
        assertTrue(isCompleteCopy(expectedBytes = 0L, copiedBytes = 1L, sizeKnown = false))
    }
}
