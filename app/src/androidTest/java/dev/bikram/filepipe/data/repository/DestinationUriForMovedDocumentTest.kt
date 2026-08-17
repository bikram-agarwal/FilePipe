package dev.bikram.filepipe.data.repository

import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented rather than a unit test because it exercises real [Uri] and [DocumentsContract]
 * behavior; both are `android.jar` stubs that throw under plain JUnit.
 *
 * `DocumentsContract.moveDocument` returns the relocated document scoped to the *source* tree, so
 * the URI it hands back names a document that is not a descendant of the tree it carries.
 * `DocumentsProvider.enforceTree` rejects every later query and open on such a URI, which would
 * silently break run-history thumbnails, tap-to-open, and undo. These cases pin the re-scoping that
 * prevents that.
 */
@RunWith(AndroidJUnit4::class)
class DestinationUriForMovedDocumentTest {
    private val authority = "com.android.externalstorage.documents"

    private fun treeUri(
        treeDocumentId: String,
        documentId: String,
    ): Uri =
        DocumentsContract.buildDocumentUriUsingTree(
            DocumentsContract.buildTreeDocumentUri(authority, treeDocumentId),
            documentId,
        )

    @Test
    fun rescopesMovedDocumentUnderTheDestinationGrant() {
        // What moveDocument actually returns: the new document id, but carrying the *source* tree.
        val movedUri = treeUri(treeDocumentId = "primary:DCIM", documentId = "primary:Sorted/img.jpg")
        val destParentUri = treeUri(treeDocumentId = "primary:Sorted", documentId = "primary:Sorted")

        val recorded = destinationUriForMovedDocument(movedUri, destParentUri)

        assertEquals("primary:Sorted", DocumentsContract.getTreeDocumentId(recorded))
        assertEquals("primary:Sorted/img.jpg", DocumentsContract.getDocumentId(recorded))
    }

    @Test
    fun keepsTheDestinationTreeWhenTheParentIsANestedSubfolder() {
        // The destination grant is the picked root; destParent is a subfolder beneath it. The tree
        // segment must stay the root, and the document must stay the moved file — not the parent.
        val movedUri = treeUri(treeDocumentId = "primary:DCIM", documentId = "primary:Sorted/2026/img.jpg")
        val destParentUri = treeUri(treeDocumentId = "primary:Sorted", documentId = "primary:Sorted/2026")

        val recorded = destinationUriForMovedDocument(movedUri, destParentUri)

        assertEquals("primary:Sorted", DocumentsContract.getTreeDocumentId(recorded))
        assertEquals("primary:Sorted/2026/img.jpg", DocumentsContract.getDocumentId(recorded))
    }

    @Test
    fun recordedUriIsADescendantOfItsOwnTree() {
        // The invariant DocumentsProvider.enforceTree checks, and the one the raw moveDocument
        // result violates: the document id must sit under the tree document id.
        val movedUri = treeUri(treeDocumentId = "primary:DCIM", documentId = "primary:Sorted/img.jpg")
        val destParentUri = treeUri(treeDocumentId = "primary:Sorted", documentId = "primary:Sorted")

        val recorded = destinationUriForMovedDocument(movedUri, destParentUri)

        val tree = DocumentsContract.getTreeDocumentId(recorded)
        val document = DocumentsContract.getDocumentId(recorded)
        assertEquals(true, document == tree || document.startsWith("$tree/"))
        // The raw return value does not satisfy it, which is why it must not be recorded as-is.
        assertEquals(
            false,
            DocumentsContract.getDocumentId(movedUri).startsWith(
                DocumentsContract.getTreeDocumentId(movedUri) + "/",
            ),
        )
    }

    @Test
    fun usesAPlainDocumentUriWhenTheDestinationParentIsNotTreeScoped() {
        val movedUri = treeUri(treeDocumentId = "primary:DCIM", documentId = "primary:Sorted/img.jpg")
        val destParentUri = DocumentsContract.buildDocumentUri(authority, "primary:Sorted")

        val recorded = destinationUriForMovedDocument(movedUri, destParentUri)

        assertEquals(DocumentsContract.buildDocumentUri(authority, "primary:Sorted/img.jpg"), recorded)
    }

    @Test
    fun fallsBackToTheProviderResultWhenTheMovedUriCarriesNoDocumentId() {
        // Defensive: a provider returning something unparseable must not lose the only URI we have.
        val opaqueUri = "content://$authority/whatever".toUri()
        val destParentUri = treeUri(treeDocumentId = "primary:Sorted", documentId = "primary:Sorted")

        assertEquals(opaqueUri, destinationUriForMovedDocument(opaqueUri, destParentUri))
    }
}
