package com.aryan.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataExtractionWorkerTest {

    @Test
    fun `metadata failures retry with a bounded attempt count`() {
        assertTrue(shouldRetryMetadataExtraction(failedCount = 1, runAttemptCount = 0))
        assertTrue(shouldRetryMetadataExtraction(failedCount = 2, runAttemptCount = 2))
        assertFalse(shouldRetryMetadataExtraction(failedCount = 1, runAttemptCount = 3))
        assertFalse(shouldRetryMetadataExtraction(failedCount = 0, runAttemptCount = 0))
    }
}
