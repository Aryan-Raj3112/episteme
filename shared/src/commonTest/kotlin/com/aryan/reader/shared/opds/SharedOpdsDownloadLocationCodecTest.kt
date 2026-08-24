package com.aryan.reader.shared.opds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedOpdsDownloadLocationCodecTest {
    @Test
    fun `encode and decode folder location round trips`() {
        val location = SharedOpdsDownloadLocation(
            folderUriString = "content://tree/comics",
            folderName = "Comics"
        )
        val encoded = SharedOpdsDownloadLocationCodec.encode(location)

        assertEquals(location, SharedOpdsDownloadLocationCodec.decode(encoded))
    }

    @Test
    fun `encode folder location without name tolerates blank name`() {
        val location = SharedOpdsDownloadLocation(folderUriString = "content://tree/comics")

        assertEquals(location, SharedOpdsDownloadLocationCodec.decode(SharedOpdsDownloadLocationCodec.encode(location)))
    }

    @Test
    fun `app storage location and blank input decode to null`() {
        assertNull(SharedOpdsDownloadLocationCodec.encode(SharedOpdsDownloadLocation()))
        assertNull(SharedOpdsDownloadLocationCodec.encode(null))
        assertNull(SharedOpdsDownloadLocationCodec.decode(null))
        assertNull(SharedOpdsDownloadLocationCodec.decode(""))
        assertNull(SharedOpdsDownloadLocationCodec.decode("not json"))
    }

    @Test
    fun `folder matching uses the persisted uri instead of the display name`() {
        val location = SharedOpdsDownloadLocation(
            folderUriString = "folder://books-a",
            folderName = "Books",
        )

        assertEquals(true, location.matchesFolderUri("folder://books-a"))
        assertEquals(false, location.matchesFolderUri("folder://books-b"))
        assertEquals(false, location.matchesFolderUri(null))
    }

    @Test
    fun `transfer progress clamps to a valid fraction and handles unknown length`() {
        assertEquals(0.5f, SharedOpdsTransferProgress(50L, 100L).fraction)
        assertEquals(1f, SharedOpdsTransferProgress(150L, 100L).fraction)
        assertEquals(null, SharedOpdsTransferProgress(50L, null).fraction)
    }
}
