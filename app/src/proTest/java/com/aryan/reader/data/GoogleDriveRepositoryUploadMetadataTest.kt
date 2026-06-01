package com.aryan.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleDriveRepositoryUploadMetadataTest {
    @Test
    fun `drive upload metadata writes app data parent only on create`() {
        val createMetadata = googleDriveUploadMetadata("book.epub", isCreate = true)
        val updateMetadata = googleDriveUploadMetadata("book.epub", isCreate = false)

        assertEquals("book.epub", createMetadata.name)
        assertEquals(listOf("appDataFolder"), createMetadata.parents)
        assertEquals("book.epub", updateMetadata.name)
        assertNull(updateMetadata.parents)
    }
}
