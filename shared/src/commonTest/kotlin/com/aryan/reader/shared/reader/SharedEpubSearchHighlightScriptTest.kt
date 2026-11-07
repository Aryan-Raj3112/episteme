package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertTrue

class SharedEpubSearchHighlightScriptTest {

    @Test
    fun `payload carries escaped query and theme behind doc guard`() {
        val script = sharedEpubSearchHighlightsApplyScript("Histo\"ry", true)
        assertTrue(script.startsWith("if (window.readerApplySearchHighlights)"))
        assertTrue(script.contains("\"query\":\"Histo\\\"ry\""))
        assertTrue(script.contains("\"dark\":true"))
    }

    @Test
    fun `blank query still emits a clearing payload`() {
        val script = sharedEpubSearchHighlightsApplyScript("  ", false)
        assertTrue(script.contains("\"query\":\"  \""))
        assertTrue(script.contains("\"dark\":false"))
    }
}
