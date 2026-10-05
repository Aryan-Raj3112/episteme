package com.aryan.reader

import com.aryan.reader.shared.shouldApplyRemoteCloudBookUpdate
import com.aryan.reader.shared.shouldUploadLocalCloudBookMetadataUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the reading-position flush cadence.
 *
 * Position writes used to push to the cloud on every page change: one Room
 * write, one Firestore write, and one fan-out push to every other device. The
 * fix makes Room the durable record and pushes rarely — on reader close, on
 * app background, on a slow interval, and via the startup merge for a process
 * killed without any of those.
 *
 * These tests pin the two properties that make that safe and that a future
 * edit could silently break:
 *
 *  1. a local position newer than remote must upload (no silent data loss when
 *     the process is killed before the first flush), and
 *  2. an already-synced position must not re-upload (otherwise every startup
 *     re-pushes the whole library).
 *
 * The cadence itself is a private implementation detail of `MainViewModel`, so
 * these assert the decision it depends on rather than the timer wiring.
 */
class ReadingPositionFlushCadenceTest {

    @Test
    fun `an unflushed local position is uploaded by the startup merge`() {
        // Killed mid-read: Room holds the position, the cloud never saw it.
        assertTrue(
            shouldUploadLocalCloudBookMetadataUpdate(
                localModifiedTimestamp = 5_000L,
                remoteModifiedTimestamp = 4_999L
            )
        )
    }

    @Test
    fun `a position already flushed is not re-uploaded`() {
        assertFalse(
            shouldUploadLocalCloudBookMetadataUpdate(
                localModifiedTimestamp = 5_000L,
                remoteModifiedTimestamp = 5_000L
            )
        )
    }

    @Test
    fun `a remote position ahead of local wins so a late flush cannot clobber it`() {
        // The mirror case: device 2 read further than device 1, so device 1's
        // stale local row must not overwrite the newer remote position.
        assertFalse(
            shouldUploadLocalCloudBookMetadataUpdate(
                localModifiedTimestamp = 4_000L,
                remoteModifiedTimestamp = 6_000L
            )
        )
        assertTrue(
            shouldApplyRemoteCloudBookUpdate(
                localModifiedTimestamp = 4_000L,
                remoteModifiedTimestamp = 6_000L
            )
        )
    }

    @Test
    fun `the flush interval is long enough to be cheap and short enough to feel fresh`() {
        // Two minutes is the deliberate middle: a page turn no longer costs a
        // Firestore write, but a device that is force-quit still loses at most
        // this much window, and the startup merge covers that case entirely.
        val interval = CLOUD_READING_POSITION_FLUSH_INTERVAL_MILLIS
        assertTrue("flush interval must not be shorter than a minute, was $interval", interval >= 60_000L)
        assertTrue(
            "flush interval must stay within five minutes, was $interval",
            interval <= 5L * 60L * 1_000L,
        )
        assertEquals(2L * 60L * 1_000L, interval)
    }
}