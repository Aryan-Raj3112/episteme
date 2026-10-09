package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun `record name split is the inverse of the namespaced form`() {
        for (type in listOf(
            CLOUDKIT_RECORD_BOOK_STATE,
            CLOUDKIT_RECORD_BOOK_CONTENT,
            CLOUDKIT_RECORD_PDF_SIDECAR,
            CLOUDKIT_RECORD_SHELF,
            CLOUDKIT_RECORD_FONT_META,
            CLOUDKIT_RECORD_FONT_CONTENT,
            CLOUDKIT_RECORD_BOOK_TOMBSTONE,
        )) {
            val ref = cloudKitSplitLibraryRecordName(
                cloudKitLibraryRecordName(type, "entity-1")
            )!!
            assertEquals(type, ref.recordType)
            assertEquals("entity-1", ref.id)
        }
    }

    @Test
    fun `record name split keeps colons inside the entity id`() {
        // isbn-style ids are common; splitting on the last colon would route
        // the deletion to the wrong entity id.
        val ref = cloudKitSplitLibraryRecordName("BookState:urn:isbn:9780134685991")!!
        assertEquals(CLOUDKIT_RECORD_BOOK_STATE, ref.recordType)
        assertEquals("urn:isbn:9780134685991", ref.id)
    }

    @Test
    fun `a device local path is rejected as a cloud record id`() {
        // Regression: a catalog import left the id null, so
        // SharedImportPlanner.stableImportId fell back to localPath and the
        // CloudKit record name became this device's absolute path. It contains
        // '/' (rejected by CloudKit) and embeds the UDID plus the app-container
        // GUID, so the same book could never match on another device.
        assertFalse(isValidCloudKitLibraryId("/Users/aryan/Documents/Pride_and_Prejudice.epub"))
        assertFalse(
            isValidCloudKitLibraryId(
                "/Users/aryan/Library/Developer/CoreSimulator/Devices/" +
                    "A8975043/data/Containers/Data/Application/AF01/Documents/book.epub"
            )
        )
    }

    @Test
    fun `stable portable ids are accepted as cloud record ids`() {
        for (id in listOf(
            "ios_import_pride_and_prejudice_epub",
            "3f2a1b9c8d7e6f5a4b3c2d1e0f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c",
            "abc-123_XYZ.9",
        )) {
            assertTrue(isValidCloudKitLibraryId(id), id)
        }
        assertFalse(isValidCloudKitLibraryId(""))
        assertFalse(isValidCloudKitLibraryId("   "))
        assertFalse(isValidCloudKitLibraryId("a:b"))
    }

    @Test
    fun `record name split rejects malformed names`() {
        assertNull(cloudKitSplitLibraryRecordName("BookState"))
        assertNull(cloudKitSplitLibraryRecordName("BookState:"))
        assertNull(cloudKitSplitLibraryRecordName(":b1"))
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
