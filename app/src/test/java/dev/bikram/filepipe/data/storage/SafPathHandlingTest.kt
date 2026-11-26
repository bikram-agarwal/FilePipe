package dev.bikram.filepipe.data.storage

import dev.bikram.filepipe.ui.components.displayPath
import dev.bikram.filepipe.ui.components.previewSourceFolderDisplayPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafPathHandlingTest {
    private val internalStorageLabel = "Internal storage"
    private val sdCardLabel = "SD card"

    @Test
    fun rsafRemotePathsDisplayRemoteNameInsteadOfSdCard() {
        assertEquals(
            "Mega/Test",
            displayPath(
                "content://com.chiller3.rsaf.documents/tree/Mega%3ATest",
                internalStorageLabel,
                sdCardLabel,
            ),
        )
        assertEquals(
            "Mega/Test/Photos",
            displayPath(
                "content://com.chiller3.rsaf.documents/tree/Mega%3ATest%2FPhotos",
                internalStorageLabel,
                sdCardLabel,
            ),
        )
        assertEquals(
            "Mega",
            displayPath(
                "content://com.chiller3.rsaf.documents/tree/Mega%3A",
                internalStorageLabel,
                sdCardLabel,
            ),
        )
    }

    @Test
    fun rsafDocumentUriPrefersDocumentPathAndPreviewResolvesParentFolder() {
        val nestedFileUri =
            "content://com.chiller3.rsaf.documents/tree/Mega%3ATest/document/Mega%3ATest%2FSub%2Freport.pdf"
        val rootFileUri =
            "content://com.chiller3.rsaf.documents/tree/Mega%3A/document/Mega%3Areport.pdf"

        assertEquals(
            "Mega/Test/Sub/report.pdf",
            displayPath(nestedFileUri, internalStorageLabel, sdCardLabel),
        )
        assertEquals(
            "Mega/Test/Sub",
            previewSourceFolderDisplayPath(nestedFileUri, "report.pdf", internalStorageLabel, sdCardLabel),
        )
        assertEquals(
            "Mega",
            previewSourceFolderDisplayPath(rootFileUri, "report.pdf", internalStorageLabel, sdCardLabel),
        )
    }

    @Test
    fun externalStoragePrimaryHomeAndRemovableSdVolumesDisplayCleanly() {
        assertEquals(
            internalStorageLabel,
            displayPath(
                "content://com.android.externalstorage.documents/tree/primary%3A",
                internalStorageLabel,
                sdCardLabel,
            ),
        )
        assertEquals(
            "Download/Quick Share",
            displayPath(
                "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FQuick%20Share",
                internalStorageLabel,
                sdCardLabel,
            ),
        )
        assertEquals(
            "Folder+Name",
            displayPath(
                "content://com.android.externalstorage.documents/tree/primary%3AFolder+Name",
                internalStorageLabel,
                sdCardLabel,
            ),
        )
        assertEquals(
            "Documents/Work",
            displayPath(
                "content://com.android.externalstorage.documents/tree/home%3AWork",
                internalStorageLabel,
                sdCardLabel,
            ),
        )
        assertEquals(
            sdCardLabel,
            displayPath(
                "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A",
                internalStorageLabel,
                sdCardLabel,
            ),
        )
        assertEquals(
            "$sdCardLabel/Movies",
            displayPath(
                "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AMovies",
                internalStorageLabel,
                sdCardLabel,
            ),
        )
    }

    @Test
    fun downloadsProviderAndFilesystemPathsFormatExpectedLabels() {
        assertEquals(
            "Download/Quick Share",
            displayPath(
                "content://com.android.providers.downloads.documents/tree/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2FQuick%20Share",
                internalStorageLabel,
                sdCardLabel,
            ),
        )
        assertEquals(
            internalStorageLabel,
            displayPath("/storage/emulated/0", internalStorageLabel, sdCardLabel),
        )
        assertEquals(
            "Pictures",
            displayPath("/storage/emulated/0/Pictures", internalStorageLabel, sdCardLabel),
        )
        assertEquals(
            sdCardLabel,
            displayPath("/storage/1a2b-3c4d", internalStorageLabel, sdCardLabel),
        )
        assertEquals(
            "$sdCardLabel/Movies",
            displayPath("/storage/1A2B-3C4D/Movies", internalStorageLabel, sdCardLabel),
        )
    }

    @Test
    fun safTreeUriToPathOnlyMapsExternalStorageProviderVolumes() {
        assertEquals(
            "/storage/emulated/0/DCIM/Camera",
            safTreeUriToPath("content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FCamera"),
        )
        assertEquals(
            "/storage/1A2B-3C4D/Movies",
            safTreeUriToPath("content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AMovies"),
        )
        assertNull(
            safTreeUriToPath("content://com.chiller3.rsaf.documents/tree/Mega%3ATest"),
        )

        val rsafUri = "content://com.chiller3.rsaf.documents/tree/Mega%3ATest"
        assertEquals(
            rsafUri,
            folderPathForFilesystemAccess(rsafUri, filesystemAccessEnabled = true),
        )
    }

    @Test
    fun blockedSafFolderChecksIgnoreThirdPartyProviders() {
        assertTrue(
            isSafTreeUriPrimarySharedStorageRoot(
                "content://com.android.externalstorage.documents/tree/primary%3A",
            ),
        )
        assertTrue(
            isSafTreeUriPublicDownloadRoot(
                "content://com.android.externalstorage.documents/tree/primary%3ADownload",
            ),
        )
        assertTrue(
            isSafTreeUriPublicDownloadRoot(
                "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3ADownload",
            ),
        )
        assertFalse(
            isSafTreeUriPublicDownloadRoot(
                "content://com.chiller3.rsaf.documents/tree/Mega%3ADownload",
            ),
        )
        assertFalse(
            isSafTreeUriPrimarySharedStorageRoot(
                "content://com.chiller3.rsaf.documents/tree/primary%3A",
            ),
        )
    }

    @Test
    fun childFolderDetectionChecksAuthorityAndHandlesRemoteRoot() {
        val rsafRoot = "content://com.chiller3.rsaf.documents/tree/Mega%3A"
        val rsafTest = "content://com.chiller3.rsaf.documents/tree/Mega%3ATest"
        val rsafTestSub = "content://com.chiller3.rsaf.documents/tree/Mega%3ATest%2FSub"
        val externalSamePrefix = "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3ATest"
        val rsafSamePrefixSub = "content://com.chiller3.rsaf.documents/tree/1A2B-3C4D%3ATest%2FSub"

        assertTrue(isSafOrFilesystemChildFolder(child = rsafTest, parent = rsafRoot))
        assertTrue(isSafOrFilesystemChildFolder(child = rsafTestSub, parent = rsafTest))
        assertFalse(isSafOrFilesystemChildFolder(child = rsafRoot, parent = rsafRoot))
        assertFalse(isSafOrFilesystemChildFolder(child = rsafSamePrefixSub, parent = externalSamePrefix))
    }
}
