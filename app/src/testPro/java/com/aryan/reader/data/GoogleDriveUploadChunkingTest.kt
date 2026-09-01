package com.aryan.reader.data

import com.google.api.client.googleapis.media.MediaHttpUploader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveUploadChunkingTest {
    @Test
    fun `resumable chunk size is bounded and a valid library chunk multiple`() {
        // MediaHttpUploader.setChunkSize throws IllegalArgumentException unless the chunk size
        // is a positive multiple of MINIMUM_CHUNK_SIZE; keep it well under the 10MB library
        // default so the mark/reset buffer cannot OOM low-heap devices during uploads.
        assertTrue(CLOUD_FOLDER_RESUMABLE_UPLOAD_CHUNK_BYTES > 0)
        assertEquals(
            0,
            CLOUD_FOLDER_RESUMABLE_UPLOAD_CHUNK_BYTES % MediaHttpUploader.MINIMUM_CHUNK_SIZE,
        )
        assertTrue(CLOUD_FOLDER_RESUMABLE_UPLOAD_CHUNK_BYTES <= 2 * 1024 * 1024)
    }
}
