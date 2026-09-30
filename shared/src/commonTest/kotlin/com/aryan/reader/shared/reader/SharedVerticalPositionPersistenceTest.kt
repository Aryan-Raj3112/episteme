package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedVerticalPositionPersistenceTest {
    @Test
    fun `vertical position stays unsaveable until the restore scroll settles`() {
        assertFalse(
            shouldSaveSharedVerticalOpenPosition(
                isVerticalMode = true,
                hasWebView = true,
                isChapterReady = true,
                isRestoreSettled = false,
            )
        )

        assertTrue(
            shouldSaveSharedVerticalOpenPosition(
                isVerticalMode = true,
                hasWebView = true,
                isChapterReady = true,
                isRestoreSettled = true,
            )
        )
    }

    @Test
    fun `vertical position requires an active webview and a ready chapter`() {
        assertFalse(
            shouldSaveSharedVerticalOpenPosition(
                isVerticalMode = true,
                hasWebView = false,
                isChapterReady = true,
                isRestoreSettled = true,
            )
        )

        assertFalse(
            shouldSaveSharedVerticalOpenPosition(
                isVerticalMode = true,
                hasWebView = true,
                isChapterReady = false,
                isRestoreSettled = true,
            )
        )
    }

    @Test
    fun `vertical position is never saved outside vertical mode`() {
        assertFalse(
            shouldSaveSharedVerticalOpenPosition(
                isVerticalMode = false,
                hasWebView = true,
                isChapterReady = true,
                isRestoreSettled = true,
            )
        )
    }

    @Test
    fun `restore settles immediately when the scroll finished signal arrives`() {
        assertTrue(
            resolveSharedVerticalRestoreSettled(
                scrollFinishedReported = true,
                elapsedMillis = 0L,
            )
        )
    }

    @Test
    fun `restore stays pending until the settle timeout elapses`() {
        assertFalse(
            resolveSharedVerticalRestoreSettled(
                scrollFinishedReported = false,
                elapsedMillis = SHARED_VERTICAL_RESTORE_SETTLE_TIMEOUT_MILLIS - 1L,
            )
        )

        assertTrue(
            resolveSharedVerticalRestoreSettled(
                scrollFinishedReported = false,
                elapsedMillis = SHARED_VERTICAL_RESTORE_SETTLE_TIMEOUT_MILLIS,
            )
        )
    }
}
