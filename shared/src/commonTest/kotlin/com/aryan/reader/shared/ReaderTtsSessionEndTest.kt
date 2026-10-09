package com.aryan.reader.shared

import com.aryan.reader.shared.reader.ReaderReadingMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderTtsSessionEndTest {
    @Test
    fun `session end refreshes navigation in the vertical webview`() {
        assertTrue(
            shouldRefreshReaderNavigationOnTtsSessionEnd(
                sessionWasActive = true,
                sessionIsActive = false,
                readingMode = ReaderReadingMode.VERTICAL,
                useNativeVerticalRenderer = false,
            )
        )
    }

    @Test
    fun `no refresh while the session stays active`() {
        assertFalse(
            shouldRefreshReaderNavigationOnTtsSessionEnd(
                sessionWasActive = true,
                sessionIsActive = true,
                readingMode = ReaderReadingMode.VERTICAL,
                useNativeVerticalRenderer = false,
            )
        )
    }

    @Test
    fun `no refresh without a prior session`() {
        assertFalse(
            shouldRefreshReaderNavigationOnTtsSessionEnd(
                sessionWasActive = false,
                sessionIsActive = false,
                readingMode = ReaderReadingMode.VERTICAL,
                useNativeVerticalRenderer = false,
            )
        )
    }

    @Test
    fun `no refresh for native renderers clearing via recomposition`() {
        assertFalse(
            shouldRefreshReaderNavigationOnTtsSessionEnd(
                sessionWasActive = true,
                sessionIsActive = false,
                readingMode = ReaderReadingMode.VERTICAL,
                useNativeVerticalRenderer = true,
            )
        )
        assertFalse(
            shouldRefreshReaderNavigationOnTtsSessionEnd(
                sessionWasActive = true,
                sessionIsActive = false,
                readingMode = ReaderReadingMode.PAGINATED,
                useNativeVerticalRenderer = false,
            )
        )
    }
}
