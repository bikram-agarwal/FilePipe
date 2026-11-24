package dev.bikram.filepipe.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The RegEx toggle's rewrite ([convertPatternsToRegex] / [convertPatternsToWildcards]) must never change what a box matches. */
class PatternModeConversionTest {
    private val fileGlobs = listOf("IMG_*", "*.jpg", "report?.pdf", "Invoice, *.pdf", "?*", "*?", "a.b(c)[d]^e\$f+g{h}i|j\\k")
    private val names =
        listOf(
            "IMG_0001.jpg",
            "img_x",
            "photo.jpg",
            "jpg",
            "report1.pdf",
            "report12.pdf",
            "Invoice, March.pdf",
            "Invoice March.pdf",
            "a",
            "",
            "a.b(c)[d]^e\$f+g{h}i|j\\k",
            "xIMG_1",
        )

    @Test
    fun fileWildcardsBecomeRegexesMatchingExactlyTheSameNames() {
        for (glob in fileGlobs) {
            val regex = convertPatternsToRegex(glob, folderPatterns = false)
            val asWildcard = buildFilenameRegexes(glob, isRegexPattern = false)
            val asRegex = buildFilenameRegexes(regex, isRegexPattern = true)
            for (name in names) {
                assertEquals(
                    "$glob -> $regex on '$name'",
                    matchesFilename(name, asWildcard, isRegexPattern = false),
                    matchesFilename(name, asRegex, isRegexPattern = true),
                )
            }
        }
    }

    @Test
    fun convertingToRegexAndBackRestoresTheWildcards() {
        val text = fileGlobs.joinToString("\n")
        val back = convertPatternsToWildcards(convertPatternsToRegex(text, folderPatterns = false), folderPatterns = false)
        assertEquals(text, back.text)
        assertTrue(back.unconvertibleLines.isEmpty())
    }

    @Test
    fun folderWildcardsKeepTheirSlashesAndMeaning() {
        assertEquals("^cache$/", convertPatternsToRegex("cache/", folderPatterns = true))
        assertEquals("/^cache$/", convertPatternsToRegex("/cache/", folderPatterns = true))
        assertEquals("^DCIM/cache$/", convertPatternsToRegex("DCIM/cache/", folderPatterns = true))
        assertEquals("^thumb.*$/", convertPatternsToRegex("thumb*/", folderPatterns = true))
        // In the include box a trailing "/" is just a character, converted like any other.
        assertEquals("^cache/$", convertPatternsToRegex("cache/", folderPatterns = false))
    }

    @Test
    fun folderWildcardsAndTheirRegexesExcludeTheSameFolders() {
        val paths =
            listOf(
                listOf("cache"),
                listOf("a", "cache"),
                listOf("app_cache"),
                listOf("DCIM", "cache"),
                listOf("x", "DCIM", "cache"),
                listOf("thumbnails"),
                listOf("Camera", "thumbs"),
            )
        for (glob in listOf("cache/", "/cache/", "DCIM/cache/", "/DCIM/cache/", "thumb*/", "*cache/")) {
            val regex = convertPatternsToRegex(glob, folderPatterns = true)
            val asWildcard = parseExcludePatterns(listOf(glob), isRegexPattern = false).folderPatterns
            val asRegex = parseExcludePatterns(listOf(regex), isRegexPattern = true).folderPatterns
            for (path in paths) {
                assertEquals(
                    "$glob -> $regex on $path",
                    shouldExcludeFolder(path, asWildcard, isRegexPattern = false),
                    shouldExcludeFolder(path, asRegex, isRegexPattern = true),
                )
            }
            assertEquals(glob, convertPatternsToWildcards(regex, folderPatterns = true).text)
        }
    }

    @Test
    fun realRegexLinesAreReportedByLineAndLeftAsTyped() {
        val text = "^IMG_.*$\n\n\\d{4}\n^a\\$\n  ^x?y$"
        val result = convertPatternsToWildcards(text, folderPatterns = false)

        assertEquals("IMG_*\n\n\\d{4}\n^a\\$\n^x?y$", result.text)
        assertEquals(listOf(3, 4, 5), result.unconvertibleLines)
    }

    @Test
    fun aLeadingSlashAnchorsARegexFolderPatternToTheSourceFolder() {
        val anchored = parseExcludePatterns(listOf("/^cache$/"), isRegexPattern = true).folderPatterns
        assertTrue(shouldExcludeFolder(listOf("cache"), anchored, isRegexPattern = true))
        assertFalse(shouldExcludeFolder(listOf("a", "cache"), anchored, isRegexPattern = true))
    }

    @Test
    fun blankLinesSurviveBothWays() {
        assertEquals("^a$\n\n^b$", convertPatternsToRegex("a\n\nb", folderPatterns = false))
        assertEquals("a\n\nb", convertPatternsToWildcards("^a$\n\n^b$", folderPatterns = false).text)
    }
}
