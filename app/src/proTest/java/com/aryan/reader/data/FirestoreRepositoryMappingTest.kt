package com.aryan.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreRepositoryMappingTest {
    @Test
    fun `book metadata map includes content reading and annotation timestamps`() {
        val metadata = BookMetadata(
            bookId = "book-1",
            displayName = "Book.epub",
            type = "EPUB",
            lastModifiedTimestamp = 2_000L,
            readingPositionModifiedTimestamp = 1_750L,
            annotationModifiedTimestamp = 1_650L,
            fileContentModifiedTimestamp = 1_500L
        )

        val fields = metadata.toFirestoreMap(originDeviceId = "device-1")

        assertTrue(fields.containsKey("fileContentModifiedTimestamp"))
        assertTrue(fields.containsKey("readingPositionModifiedTimestamp"))
        assertTrue(fields.containsKey("annotationModifiedTimestamp"))
        assertEquals(1_500L, fields["fileContentModifiedTimestamp"])
        assertEquals(1_750L, fields["readingPositionModifiedTimestamp"])
        assertEquals(1_650L, fields["annotationModifiedTimestamp"])
        assertEquals("device-1", fields["originDeviceId"])
    }
}
