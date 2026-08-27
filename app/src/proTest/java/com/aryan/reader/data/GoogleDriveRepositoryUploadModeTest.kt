package com.aryan.reader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleDriveRepositoryUploadModeTest {
    @Test
    fun `known small payloads use one-shot multipart and large or unknown stay resumable`() {
        assertEquals("multipart", cloudFolderUploadMode(0L))
        assertEquals("multipart", cloudFolderUploadMode(379L))
        assertEquals("multipart", cloudFolderUploadMode(CLOUD_FOLDER_DIRECT_UPLOAD_MAX_BYTES))
        assertEquals("resumable", cloudFolderUploadMode(CLOUD_FOLDER_DIRECT_UPLOAD_MAX_BYTES + 1L))
        assertEquals("resumable", cloudFolderUploadMode(-1L))
    }
}
