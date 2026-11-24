package dev.bikram.filepipe.domain.usecase

import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.FileUndoStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoCandidatesTest {
    private val moved = file(id = 1)
    private val alreadyUndone = file(id = 2, undoStatus = FileUndoStatus.UNDONE)
    private val skipped = file(id = 3, skipped = true)
    private val failedEarlier = file(id = 4, undoStatus = FileUndoStatus.FAILED)
    private val noDestination = file(id = 5, destinationUri = "")
    private val run = listOf(moved, alreadyUndone, skipped, failedEarlier, noDestination)

    @Test
    fun wholeRunUndoTakesEveryFileStillAtItsDestination() {
        // A file whose earlier undo failed is retried; undone, skipped and destination-less ones are not.
        assertEquals(listOf(1L, 4L), undoCandidates(run).map { it.id })
    }

    @Test
    fun selectiveUndoTakesOnlySelectedCandidates() {
        // 2 is selected but already undone, so it is not undone twice.
        assertEquals(listOf(4L), undoCandidates(run, selectedFileIds = setOf(2L, 4L)).map { it.id })
    }

    @Test
    fun emptySelectionUndoesNothing() {
        assertEquals(emptyList<FileMoved>(), undoCandidates(run, selectedFileIds = emptySet()))
    }

    @Test
    fun candidateFlagMatchesTheFilter() {
        assertTrue(moved.isUndoCandidate)
        assertTrue(failedEarlier.isUndoCandidate)
        assertFalse(alreadyUndone.isUndoCandidate)
        assertFalse(skipped.isUndoCandidate)
        assertFalse(noDestination.isUndoCandidate)
    }

    private fun file(
        id: Long,
        undoStatus: FileUndoStatus = FileUndoStatus.PENDING,
        skipped: Boolean = false,
        destinationUri: String = "content://dest/$id.jpg",
    ) = FileMoved(
        id = id,
        fileName = "$id.jpg",
        sourceUri = "content://source/$id.jpg",
        destinationUri = destinationUri,
        fileSizeBytes = 10L,
        success = true,
        skipped = skipped,
        undoStatus = undoStatus,
    )
}
