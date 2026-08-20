package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.PdfDisplayMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfReaderPaneHostTest {
    private val session = SharedPdfReaderSessionKey(" book-1 ", sessionId = 7L)

    @Test
    fun fullScreenHostOwnsEveryGlobalResourceByDefault() {
        val config = SharedPdfReaderHostConfig.fullScreen("book-1")

        SharedPdfReaderGlobalResource.entries.forEach { resource ->
            assertTrue(config.owns(resource), "full-screen host should own $resource")
        }
    }

    @Test
    fun unfocusedOrInactivePaneCannotOwnGlobalResources() {
        val resources = SharedPdfReaderGlobalResource.entries

        resources.forEach { resource ->
            assertFalse(
                SharedPdfReaderHostConfig(session, isFocused = false).owns(resource),
                "unfocused pane should not own $resource",
            )
            assertFalse(
                SharedPdfReaderHostConfig(session, isAppActive = false).owns(resource),
                "inactive app should not own $resource",
            )
        }
    }

    @Test
    fun focusHandoffHasExactlyOneActiveOwnerWhenTheAppIsForegrounded() {
        val first = SharedPdfReaderHostConfig(session, isFocused = true, isAppActive = true)
        val second = SharedPdfReaderHostConfig(
            SharedPdfReaderSessionKey("book-2", sessionId = 8L),
            isFocused = false,
            isAppActive = true,
        )

        SharedPdfReaderGlobalResource.entries.forEach { resource ->
            assertTrue(first.owns(resource), "focused pane should own $resource")
            assertFalse(second.owns(resource), "unfocused pane should not own $resource")
        }

        val handedOff = second.copy(isFocused = true)
        SharedPdfReaderGlobalResource.entries.forEach { resource ->
            assertFalse(first.copy(isFocused = false).owns(resource))
            assertTrue(handedOff.owns(resource))
        }
    }

    @Test
    fun sessionMatchingUsesCanonicalBookIdAndSessionId() {
        assertTrue(session.matches(SharedPdfReaderSessionKey("book-1", sessionId = 7L)))
        assertTrue(session.matches("book-1", 7L))
        assertTrue(SharedPdfReaderSessionKey("book-1", sessionId = -1L).isValid)
        assertFalse(session.matches(SharedPdfReaderSessionKey("book-2", sessionId = 7L)))
        assertFalse(session.matches(SharedPdfReaderSessionKey("book-1", sessionId = 8L)))
    }

    @Test
    fun staleReaderCallbackIsRejectedAfterSessionReplacement() {
        val initial = SharedPdfReaderHostState(
            sessionKey = session,
            readerState = SharedPdfReaderState.initial(pageCount = 10, initialPageIndex = 2),
            viewport = SharedPdfReaderViewport(
                pageIndex = 2,
                displayMode = PdfDisplayMode.VERTICAL_SCROLL,
                zoom = 1.25f,
            ),
        )
        val replacementSession = SharedPdfReaderSessionKey("book-1", sessionId = 8L)

        assertNull(
            initial.withReaderState(
                callbackSession = replacementSession,
                nextState = initial.readerState.copy(pageIndex = 4),
            )
        )
        assertNull(
            initial.withViewport(
                callbackSession = replacementSession,
                nextViewport = initial.viewport.copy(pageIndex = 4),
            )
        )
        assertTrue(initial.isCurrent(session))
    }

    @Test
    fun currentSessionCallbackAdvancesRevisionWithoutLosingViewport() {
        val initial = SharedPdfReaderHostState(sessionKey = session)
        val nextState = initial.readerState.copy(pageIndex = 3)

        val updated = initial.withReaderState(session, nextState)

        assertNotNull(updated)
        assertEquals(nextState, updated.readerState)
        assertEquals(initial.viewport, updated.viewport)
        assertEquals(1L, updated.revision)
    }

    @Test
    fun invalidSessionCannotAcceptCallbacks() {
        val invalid = SharedPdfReaderHostConfig(
            sessionKey = SharedPdfReaderSessionKey(" ", sessionId = 1L),
        )

        assertFalse(invalid.sessionKey.isValid)
        assertFalse(invalid.owns(SharedPdfReaderGlobalResource.SYSTEM_UI))
        assertFalse(invalid.acceptsCallback(invalid.sessionKey))
    }
}
