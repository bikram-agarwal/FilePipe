package dev.bikram.filepipe.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * A transfer resolves its destination as *root + relative subfolders*. Undoing one has to recover
 * the root, so these strippers are the inverse of that walk. Getting it wrong nests the subfolders a
 * second time, which is how undo used to restore `/DCIM/sub/photo.jpg` into `/DCIM/sub/sub/`.
 */
class TrailingSegmentStrippingTest {
    @Test
    fun documentIdStripsOneSegment() {
        assertEquals(
            "primary:DCIM",
            documentIdWithoutTrailingSegments("primary:DCIM/sub", listOf("sub")),
        )
    }

    @Test
    fun documentIdStripsNestedSegmentsOutermostLast() {
        assertEquals(
            "primary:DCIM",
            documentIdWithoutTrailingSegments("primary:DCIM/2026/january", listOf("2026", "january")),
        )
    }

    @Test
    fun documentIdWithNoSegmentsIsUnchanged() {
        assertEquals(
            "primary:DCIM/sub",
            documentIdWithoutTrailingSegments("primary:DCIM/sub", emptyList()),
        )
    }

    @Test
    fun documentIdStripsDownToTheVolumeRoot() {
        assertEquals("primary:", documentIdWithoutTrailingSegments("primary:sub", listOf("sub")))
    }

    @Test
    fun documentIdRefusesToStripWhenTheNamesDoNotMatch() {
        // The record disagrees with the layout, so any stripped id would name an unrelated folder.
        assertNull(documentIdWithoutTrailingSegments("primary:DCIM/sub", listOf("other")))
    }

    @Test
    fun documentIdRefusesToStripDeeperThanTheIdGoes() {
        assertNull(documentIdWithoutTrailingSegments("primary:sub", listOf("DCIM", "sub")))
    }

    @Test
    fun lastSegmentReadsThroughBothSeparators() {
        assertEquals("photo.jpg", documentIdLastSegment("primary:DCIM/Camera/photo.jpg"))
        assertEquals("photo.jpg", documentIdLastSegment("primary:photo.jpg"))
        assertEquals("DCIM", documentIdLastSegment("primary:DCIM/"))
    }

    @Test
    fun filePathStripsSegments() {
        val stripped =
            fileWithoutTrailingSegments(File("/storage/emulated/0/DCIM/2026/january"), listOf("2026", "january"))

        assertEquals(File("/storage/emulated/0/DCIM"), stripped)
    }

    @Test
    fun filePathRefusesToStripWhenTheNamesDoNotMatch() {
        assertNull(fileWithoutTrailingSegments(File("/storage/emulated/0/DCIM/sub"), listOf("other")))
    }

    @Test
    fun strippingThenRecreatingSegmentsReturnsTheOriginalFolder() {
        // The round trip undo depends on: strip to the root, then let the transfer re-append.
        val originalParent = "primary:DCIM/2026/january"
        val segments = listOf("2026", "january")

        val root = documentIdWithoutTrailingSegments(originalParent, segments)
        val rebuilt = (listOf(root) + segments).joinToString("/")

        assertEquals(originalParent, rebuilt)
    }
}
