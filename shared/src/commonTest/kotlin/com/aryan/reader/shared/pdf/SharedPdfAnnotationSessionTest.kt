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
}
