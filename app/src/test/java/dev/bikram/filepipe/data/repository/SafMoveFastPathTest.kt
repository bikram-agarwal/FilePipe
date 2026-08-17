package dev.bikram.filepipe.data.repository

import dev.bikram.filepipe.domain.model.OperationMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the conditions under which a SAF→SAF transfer is handed to
 * `DocumentsContract.moveDocument` instead of being stream-copied. Each `false` case here prevents
 * a specific failure, so a change that flips one should have to change this test too.
 */
class SafMoveFastPathTest {
    @Test
    fun moveWithProviderSupportAndUnchangedNameUsesInProviderMove() {
        assertTrue(
            canUseInProviderMove(
                operationMode = OperationMode.MOVE,
                supportsMove = true,
                destNameUnchanged = true,
            ),
        )
    }

    @Test
    fun copyNeverUsesInProviderMove() {
        // A copy must leave the source in place; moveDocument would relocate it.
        assertFalse(
            canUseInProviderMove(
                operationMode = OperationMode.COPY,
                supportsMove = true,
                destNameUnchanged = true,
            ),
        )
    }

    @Test
    fun deleteNeverUsesInProviderMove() {
        assertFalse(
            canUseInProviderMove(
                operationMode = OperationMode.DELETE,
                supportsMove = true,
                destNameUnchanged = true,
            ),
        )
    }

    @Test
    fun documentWithoutMoveFlagFallsBackToCopy() {
        // Without FLAG_SUPPORTS_MOVE the call throws, and attempting it once per file in a run is
        // the cost this flag exists to avoid.
        assertFalse(
            canUseInProviderMove(
                operationMode = OperationMode.MOVE,
                supportsMove = false,
                destNameUnchanged = true,
            ),
        )
    }

    @Test
    fun conflictRenameFallsBackToCopy() {
        // Renaming after the move needs a second call that can fail once the source is already
        // gone, which would strand a moved file that we report as failed.
        assertFalse(
            canUseInProviderMove(
                operationMode = OperationMode.MOVE,
                supportsMove = true,
                destNameUnchanged = false,
            ),
        )
    }
}
