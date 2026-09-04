package com.aryan.reader.shared.ui

import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.UserHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedMobileEpubHighlightBridgeTest {

    @Test
    fun `highlight payload without styleId keeps background style`() {
        val payload = """
            {"cfi":"/4/2/2:0|/4/2/2:9","text":"alpha beta","colorId":"green",
             "chapterIndex":3,"locator":{"chapterIndex":3,"startOffset":0,"endOffset":10}}
        """.trimIndent()

        val highlight = payload.sharedMobileEpubHighlightOrNull()

        assertNotNull(highlight)
        assertEquals(HighlightStyle.BACKGROUND, highlight.style)
        assertEquals("green", highlight.color.id)
    }

    @Test
    fun `highlight payload maps styleId to highlight style`() {
        val payload = """
            {"cfi":"/4/2/2:0|/4/2/2:9","text":"alpha beta","colorId":"blue",
             "styleId":"wavy_underline","chapterIndex":3,
             "locator":{"chapterIndex":3,"startOffset":0,"endOffset":10}}
        """.trimIndent()

        val highlight = payload.sharedMobileEpubHighlightOrNull()

        assertNotNull(highlight)
        assertEquals(HighlightStyle.WAVY_UNDERLINE, highlight.style)
    }

    @Test
    fun `highlights apply script is empty-list no-op without highlights`() {
        val script = sharedMobileEpubHighlightsApplyScript(emptyList())

        assertTrue(script.contains("readerApplyHighlights([])"))
    }

    @Test
    fun `highlights apply script carries id cfi color style and locator offsets`() {
        val highlight = UserHighlight(
            id = "mobile-web-3-42-0-10",
            cfi = "/4/2/2:0|/4/2/2:10",
            text = "alpha beta",
            color = HighlightColor.GREEN,
            colorArgb = 0xFF388E3C.toInt(),
            chapterIndex = 3,
            style = HighlightStyle.UNDERLINE,
            locator = com.aryan.reader.shared.ReaderLocator(
                chapterIndex = 3,
                pageIndex = 7,
                startOffset = 100,
                endOffset = 110,
                textQuote = "alpha beta",
                cfi = "/4/2/2:0|/4/2/2:10"
            )
        )

        val script = sharedMobileEpubHighlightsApplyScript(listOf(highlight))

        assertTrue(script.contains("readerApplyHighlights(["))
        assertTrue(script.contains("\"id\":\"mobile-web-3-42-0-10\""))
        assertTrue(script.contains("\"colorId\":\"green\""))
        assertTrue(script.contains("\"style\":\"underline\""))
        assertTrue(script.contains("\"startOffset\":100"))
        assertTrue(script.contains("\"endOffset\":110"))
        assertTrue(script.contains("\"cfi\":\"/4/2/2:0|/4/2/2:10\""))
    }
}
