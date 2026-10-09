package com.aryan.reader.epubreader

import com.aryan.reader.shared.reader.SHARED_VERTICAL_RESTORE_SETTLE_TIMEOUT_MILLIS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubVerticalOpenStateTest {

    @Test
    fun `vertical position save waits for the restore scroll to settle`() {
        assertFalse(
            shouldSaveVerticalOpenPosition(
                isVerticalMode = true,
                hasWebView = true,
                isChapterReady = true,
                isRestoreSettled = false
            )
        )

        assertTrue(
            shouldSaveVerticalOpenPosition(
                isVerticalMode = true,
                hasWebView = true,
                isChapterReady = true,
                isRestoreSettled = true
            )
        )
    }

    @Test
    fun `vertical position save rejects surfaces without a live webview`() {
        assertFalse(
            shouldSaveVerticalOpenPosition(
                isVerticalMode = true,
                hasWebView = false,
                isChapterReady = true,
                isRestoreSettled = true
            )
        )
    }

    @Test
    fun `restore settles when the scroll finished signal arrives`() {
        assertTrue(
            resolveVerticalRestoreSettled(
                scrollFinishedReported = true,
                restoreStartedAtMillis = 1_000L,
                nowMillis = 1_100L
            )
        )
    }

    @Test
    fun `restore stays pending until the shared settle timeout`() {
        assertFalse(
            resolveVerticalRestoreSettled(
                scrollFinishedReported = false,
                restoreStartedAtMillis = 1_000L,
                nowMillis = 1_000L + SHARED_VERTICAL_RESTORE_SETTLE_TIMEOUT_MILLIS - 1L
            )
        )

        assertTrue(
            resolveVerticalRestoreSettled(
                scrollFinishedReported = false,
                restoreStartedAtMillis = 1_000L,
                nowMillis = 1_000L + SHARED_VERTICAL_RESTORE_SETTLE_TIMEOUT_MILLIS
            )
        )
    }

    @Test
    fun `restore is treated as settled when no restore is in flight`() {
        assertTrue(
            resolveVerticalRestoreSettled(
                scrollFinishedReported = false,
                restoreStartedAtMillis = 0L,
                nowMillis = 5_000L
            )
        )
    }
}
