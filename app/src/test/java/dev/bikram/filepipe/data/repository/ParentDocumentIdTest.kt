package dev.bikram.filepipe.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parent derivation feeds `DocumentsContract.moveDocument`, which enforces write access on the
 * parent it is handed. A wrong-but-plausible id sends the move somewhere unintended, so the
 * no-parent cases must stay null rather than being guessed at.
 */
class ParentDocumentIdTest {
    @Test
    fun nestedPathDropsTheLastSegment() {
        assertEquals("primary:DCIM/Camera", parentDocumentIdOrNull("primary:DCIM/Camera/photo.jpg"))
    }

    @Test
    fun singleLevelPathDropsToTheVolumeRoot() {
        assertEquals("primary:", parentDocumentIdOrNull("primary:photo.jpg"))
    }

    @Test
    fun volumeRootHasNoParent() {
        assertNull(parentDocumentIdOrNull("primary:"))
    }

    @Test
    fun nonPrimaryVolumeKeepsItsIdentifier() {
        assertEquals("1A2B-3C4D:Backup", parentDocumentIdOrNull("1A2B-3C4D:Backup/notes.txt"))
    }

    @Test
    fun trailingSlashIsIgnored() {
        assertEquals("primary:DCIM", parentDocumentIdOrNull("primary:DCIM/Camera/"))
    }

    @Test
    fun opaqueIdWithoutVolumePrefixHasNoParent() {
        // Providers such as MTP use plain numeric ids; there is no parent encoded in them.
        assertNull(parentDocumentIdOrNull("12345"))
    }

    @Test
    fun pathShapedIdWithoutVolumePrefixStillNests() {
        // Guards the specific defect the earlier implementation had: it tested for '/' only in the
        // part after ':', so a colon-less id fell through to a fabricated "<id>:" parent.
        assertEquals("folder", parentDocumentIdOrNull("folder/file.txt"))
    }
}
