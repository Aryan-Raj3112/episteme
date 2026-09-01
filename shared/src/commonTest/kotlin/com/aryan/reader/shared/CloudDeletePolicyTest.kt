package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudDeletePolicyTest {
    @Test
    fun `drive delete treats success and already missing as idempotent`() {
        assertTrue(isIdempotentCloudDeleteStatus(200))
        assertTrue(isIdempotentCloudDeleteStatus(204))
        assertTrue(isIdempotentCloudDeleteStatus(404))
        assertFalse(isIdempotentCloudDeleteStatus(401))
        assertFalse(isIdempotentCloudDeleteStatus(500))
    }

    @Test
    fun `newer tombstone without type preserves older known type`() {
        val merged = mergeCloudBookTombstones(
            listOf(
                CloudBookTombstone("book", "PDF", 10L),
                CloudBookTombstone("book", null, 20L),
            )
        )

        assertEquals(listOf(CloudBookTombstone("book", "PDF", 20L)), merged)
    }

    @Test
    fun `same clock prefers known type deterministically`() {
        val merged = mergeCloudBookTombstones(
            listOf(
                CloudBookTombstone("book", null, 20L),
                CloudBookTombstone("book", " EPUB ", 20L),
            )
        )

        assertEquals(listOf(CloudBookTombstone("book", "EPUB", 20L)), merged)
    }
}
