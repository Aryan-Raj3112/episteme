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
}