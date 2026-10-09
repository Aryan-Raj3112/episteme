package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudSyncDecisionsTest {

    @Test
    fun `newer remote metadata applies over local metadata`() {
        assertEquals(
            SharedCloudBookMetadataWinner.REMOTE,
            sharedCloudBookMetadataWinner(
                localModifiedTimestamp = 100L,
                remoteModifiedTimestamp = 200L
            )
        )
        assertTrue(
            shouldApplyRemoteCloudBookUpdate(
                localModifiedTimestamp = 100L,
                remoteModifiedTimestamp = 200L
            )
        )
    }

    @Test
    fun `newer local sidecar wins even when book metadata is older`() {
        assertEquals(
            SharedCloudBookMetadataWinner.LOCAL,
            sharedCloudBookMetadataWinner(
                localModifiedTimestamp = 100L,
                remoteModifiedTimestamp = 200L,
                localSidecarModifiedTimestamp = 300L
            )
        )
        assertEquals(
            SharedCloudBookMetadataWinner.REMOTE,
            sharedCloudBookReadingMetadataWinner(
                localModifiedTimestamp = 100L,
                remoteModifiedTimestamp = 200L
            )
        )
        assertTrue(
            shouldUploadLocalCloudBookUpdate(
                localModifiedTimestamp = 100L,
                remoteModifiedTimestamp = 200L,
                localSidecarModifiedTimestamp = 300L
            )
        )
        assertTrue(
            shouldApplyRemoteCloudBookMetadataUpdate(
                localModifiedTimestamp = 100L,
                remoteModifiedTimestamp = 200L
            )
        )
    }

    @Test
    fun `stale remote metadata is ignored`() {
        assertFalse(
            shouldApplyRemoteCloudBookUpdate(
                localModifiedTimestamp = 300L,
                remoteModifiedTimestamp = 200L
            )
        )
        assertTrue(
            shouldUploadLocalCloudBookUpdate(
                localModifiedTimestamp = 300L,
                remoteModifiedTimestamp = 200L
            )
        )
    }

    @Test
    fun `content sync only moves changed file payloads`() {
        assertTrue(
            shouldDownloadRemoteCloudBookContent(
                localFileAvailable = true,
                localContentModifiedTimestamp = 100L,
                remoteContentModifiedTimestamp = 200L
            )
        )
        assertTrue(
            shouldDownloadRemoteCloudBookContent(
                localFileAvailable = false,
                localContentModifiedTimestamp = 0L,
                remoteContentModifiedTimestamp = 200L
            )
        )
        assertFalse(
            shouldDownloadRemoteCloudBookContent(
                localFileAvailable = true,
                localContentModifiedTimestamp = 300L,
                remoteContentModifiedTimestamp = 200L
            )
        )
        assertFalse(
            shouldDownloadRemoteCloudBookContent(
                localFileAvailable = false,
                localContentModifiedTimestamp = 0L,
                remoteContentModifiedTimestamp = 200L,
                remoteDeleted = true
            )
        )
        assertTrue(
            shouldUploadLocalCloudBookContent(
                localFileAvailable = true,
                localContentModifiedTimestamp = 300L,
                remoteContentModifiedTimestamp = 200L
            )
        )
    }

    @Test
    fun `startup merge recovers a reading position that was never pushed`() {
        // A position saved while reading and then killed before any flush only
        // exists in Room. The startup merge is the safety net: a local row
        // newer than its remote counterpart must upload, not be ignored.
        assertTrue(
            shouldUploadLocalCloudBookMetadataUpdate(
                localModifiedTimestamp = 5_000L,
                remoteModifiedTimestamp = 1_000L
            )
        )
    }

    @Test
    fun `already synced position does not re-upload on every startup`() {
        // The flip side of the safety net: without this the periodic merge
        // would re-push every book on every launch.
        assertFalse(
            shouldUploadLocalCloudBookMetadataUpdate(
                localModifiedTimestamp = 1_000L,
                remoteModifiedTimestamp = 1_000L
            )
        )
        assertFalse(
            shouldUploadLocalCloudBookMetadataUpdate(
                localModifiedTimestamp = 1_000L,
                remoteModifiedTimestamp = 9_000L
            )
        )
    }

    @Test
    fun `cloud book content file name uses shared primary extension`() {
        assertEquals("book-1.epub", sharedCloudBookContentFileName("book-1", FileType.EPUB))
        assertEquals("book-1.md", sharedCloudBookContentFileName("book-1", FileType.MD))
        assertEquals("book-1.mobi", sharedCloudBookContentFileName("book-1", FileType.MOBI))
        assertEquals(null, sharedCloudBookContentFileName("book-1", FileType.UNKNOWN))
    }
}
