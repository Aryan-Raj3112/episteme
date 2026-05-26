package com.aryan.reader.desktop

import com.aryan.reader.shared.ReaderLocator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopEpubBridgeParsingTest {
    @Test
    fun `reader position bridge keeps semantic locator fields`() {
        val position = """
            {
              "pageIndex": 12,
              "chapterIndex": 2,
              "chapterId": "chap-2",
              "href": "text/chapter2.xhtml",
              "startOffset": 140,
              "endOffset": 140,
              "blockIndex": 9,
              "charOffset": 140,
              "textQuote": "quoted text",
              "cfi": "desktop-scroll:10:100:/4/2:3"
            }
        """.trimIndent().readerPositionOrNull()

        assertEquals(12, position?.pageIndex)
        assertEquals(2, position?.locator?.chapterIndex)
        assertEquals("chap-2", position?.locator?.chapterId)
        assertEquals("text/chapter2.xhtml", position?.locator?.href)
        assertEquals(9, position?.locator?.blockIndex)
        assertEquals(140, position?.locator?.charOffset)
        assertEquals("quoted text", position?.locator?.textQuote)
    }

    @Test
    fun `locator json sent to web view includes semantic position fields`() {
        val json = ReaderLocator(
            chapterIndex = 2,
            chapterId = "chap-2",
            href = "text/chapter2.xhtml",
            pageIndex = 12,
            startOffset = 140,
            endOffset = 155,
            blockIndex = 9,
            charOffset = 140,
            textQuote = "quoted text",
            cfi = "/4/2:3"
        ).toReaderLocatorJson()

        assertTrue(json.contains("\"chapterId\":\"chap-2\""))
        assertTrue(json.contains("\"href\":\"text/chapter2.xhtml\""))
        assertTrue(json.contains("\"blockIndex\":9"))
        assertTrue(json.contains("\"charOffset\":140"))
    }
}
