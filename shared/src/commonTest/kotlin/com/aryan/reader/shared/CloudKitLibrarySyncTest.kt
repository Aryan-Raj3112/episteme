package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudKitLibrarySyncTest {

    @Test
    fun `position-only change stays metadata-only`() {
        val sets = classifyCloudKitBookDirty(
            CloudKitBookClocks(
                bookId = "b1",
                lastModifiedTimestamp = 300L,
                fileContentModifiedTimestamp = 100L,
                localFileAvailable = true,
                remoteModifiedTimestamp = 200L,
                remoteContentModifiedTimestamp = 100L,
            )
        )
        assertEquals(setOf("b1"), sets.metadataBookIds)
        assertTrue(sets.contentBookIds.isEmpty())
    }

    @Test
    fun `unchanged bytes never trigger asset save`() {
        val sets = classifyCloudKitBookDirty(
            CloudKitBookClocks(
                bookId = "b1",
                lastModifiedTimestamp = 100L,
                fileContentModifiedTimestamp = 100L,
                localFileAvailable = true,
                remoteModifiedTimestamp = 200L,
                remoteContentModifiedTimestamp = 100L,
            )
        )
        assertTrue(sets.metadataBookIds.isEmpty())
        assertTrue(sets.contentBookIds.isEmpty())
    }

    @Test
    fun `new local bytes trigger content save only when newer`() {
        val dirty = classifyCloudKitBookDirty(
            CloudKitBookClocks(
                bookId = "b1",
                lastModifiedTimestamp = 100L,
                fileContentModifiedTimestamp = 300L,
                localFileAvailable = true,
                remoteModifiedTimestamp = 100L,
                remoteContentModifiedTimestamp = 200L,
            )
        )
        assertEquals(setOf("b1"), dirty.contentBookIds)
    }

    @Test
    fun `sidecar clock wins independently`() {
        val sets = classifyCloudKitBookDirty(
            CloudKitBookClocks(
                bookId = "pdf-1",
                lastModifiedTimestamp = 100L,
                sidecarModifiedTimestamp = 400L,
                localFileAvailable = false,
                remoteModifiedTimestamp = 200L,
            )
        )
        assertEquals(setOf("pdf-1"), sets.sidecarBookIds)
    }

    @Test
    fun `retry honors server retryAfter`() {
        assertEquals(42_000L, cloudKitLibraryRetryDelayMs(attempt = 3, retryAfterMs = 42_000L))
        assertEquals(
            SHARED_BACKGROUND_CLOUD_SYNC_RETRY_BASE_MS,
            cloudKitLibraryRetryDelayMs(attempt = 0, retryAfterMs = null),
        )
        assertEquals(
            SHARED_BACKGROUND_CLOUD_SYNC_RETRY_MAX_MS,
            cloudKitLibraryRetryDelayMs(attempt = 30, retryAfterMs = null),
        )
    }

    @Test
    fun `deterministic errors do not retry`() {
        assertFalse(shouldRetryCloudKitLibrary(CloudKitLibraryRetryKind.DETERMINISTIC))
        assertTrue(shouldRetryCloudKitLibrary(CloudKitLibraryRetryKind.TRANSIENT))
        assertTrue(shouldRetryCloudKitLibrary(CloudKitLibraryRetryKind.RETRY_AFTER))
    }

    @Test
    fun `record names are stable and namespaced`() {
        assertEquals("BookState:b1", cloudKitBookStateRecordName("b1"))
        assertEquals("BookContent:b1", cloudKitBookContentRecordName("b1"))
    }

    @Test
    fun `tombstone publishes only when remote is missing or older`() {
        assertTrue(shouldPublishCloudKitTombstone(localDeletedAt = 100L, remoteTombstoneClock = null))
        assertTrue(shouldPublishCloudKitTombstone(localDeletedAt = 200L, remoteTombstoneClock = 100L))
        // Equal clock = already published: this is the re-save loop that made
        // every tombstone appear in each pass's dirty set.
        assertFalse(shouldPublishCloudKitTombstone(localDeletedAt = 100L, remoteTombstoneClock = 100L))
        assertFalse(shouldPublishCloudKitTombstone(localDeletedAt = 50L, remoteTombstoneClock = 100L))
    }
}
