package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPdfAnnotationSessionTest {
    @Test
    fun `annotation session protects active book from stale sidecar completion`() {
        val loading = SharedPdfAnnotationSessionState().reduce(
            SharedPdfAnnotationSessionAction.LoadStarted("book-a"),
        )
        val stale = loading.reduce(
            SharedPdfAnnotationSessionAction.LoadCompleted("book-b", 9, 8, 7),
        )
        val ready = loading.reduce(
            SharedPdfAnnotationSessionAction.LoadCompleted("book-a", 3, 2, 1),
        )

        assertEquals(loading, stale)
        assertTrue(ready.canUseFor("book-a"))
        assertFalse(ready.canUseFor("book-b"))
        assertEquals(3, ready.inkCount)
        assertEquals(2, ready.textBoxCount)
        assertEquals(1, ready.highlightCount)
        assertEquals(SharedPdfAnnotationSessionState(), ready.reduce(SharedPdfAnnotationSessionAction.Reset))
    }

    @Test
    fun `reset during an in-flight sidecar load invalidates the later completion`() {
        val loading = SharedPdfAnnotationSessionState().reduce(
            SharedPdfAnnotationSessionAction.LoadStarted("book-a"),
        )
        val reset = loading.reduce(SharedPdfAnnotationSessionAction.Reset)
        val completed = reset.reduce(
            SharedPdfAnnotationSessionAction.LoadCompleted("book-a", 3, 2, 1),
        )

        // The sidecar load only restarts when the reader book id changes, so a
        // reset landing between LoadStarted and LoadCompleted strands the
        // session non-ready forever. Document-open passes must not reset for a
        // book the reader already loaded (split panes re-run them).
        assertEquals(SharedPdfAnnotationSessionState(), completed)
        assertFalse(completed.canUseFor("book-a"))
    }
}
