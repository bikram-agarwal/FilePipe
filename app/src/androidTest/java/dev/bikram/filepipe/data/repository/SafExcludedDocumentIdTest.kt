package dev.bikram.filepipe.data.repository

import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [safExcludedDocumentId] decides which SAF folder a recursive scan prunes as the rule's destination.
 * Instrumented because it parses with the platform's Uri / DocumentsContract, which plain JVM unit
 * tests only have as throwing stubs.
 */
@RunWith(AndroidJUnit4::class)
class SafExcludedDocumentIdTest {
    private val sourceTree = DocumentsContract.buildTreeDocumentUri(EXTERNAL_STORAGE, "primary:DCIM")

    @Test
    fun destinationTreeInTheSameProviderYieldsItsDocumentId() {
        val destination = DocumentsContract.buildTreeDocumentUri(EXTERNAL_STORAGE, "primary:DCIM/Sorted")
        assertEquals("primary:DCIM/Sorted", safExcludedDocumentId(sourceTree, destination.toString()))
    }

    @Test
    fun destinationInAnotherProviderExcludesNothing() {
        // Document ids only compare within one provider.
        val destination = DocumentsContract.buildTreeDocumentUri(DOWNLOADS, "raw:/storage/emulated/0/Download")
        assertNull(safExcludedDocumentId(sourceTree, destination.toString()))
    }

    @Test
    fun filesystemPathOrMissingDestinationExcludesNothing() {
        assertNull(safExcludedDocumentId(sourceTree, "/storage/emulated/0/DCIM/Sorted"))
        assertNull(safExcludedDocumentId(sourceTree, null))
    }

    @Test
    fun nonTreeContentUriExcludesNothing() {
        val document = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE, "primary:DCIM/Sorted")
        assertNull(safExcludedDocumentId(sourceTree, document.toString()))
    }

    private companion object {
        const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
        const val DOWNLOADS = "com.android.providers.downloads.documents"
    }
}
