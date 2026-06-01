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
        assertEquals("/4/2:3", position?.locator?.cfi)
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

    @Test
    fun `selection action bridge keeps locator fields for selected tts`() {
        val payload = """
            {
              "action": "speak",
              "text": "selected text",
              "locator": {
                "chapterIndex": 3,
                "chapterId": "chap-3",
                "href": "text/chapter3.xhtml",
                "pageIndex": 41,
                "startOffset": 900,
                "endOffset": 913,
                "blockIndex": 7,
                "charOffset": 900,
                "textQuote": "selected text",
                "cfi": "desktop-scroll:10:20:/4/8:12|/4/8:25"
              }
            }
        """.trimIndent().readerSelectionActionOrNull()

        assertEquals(DesktopReaderSelectionAction.SPEAK, payload?.action)
        assertEquals("selected text", payload?.text)
        assertEquals(3, payload?.locator?.chapterIndex)
        assertEquals("chap-3", payload?.locator?.chapterId)
        assertEquals("text/chapter3.xhtml", payload?.locator?.href)
        assertEquals(41, payload?.locator?.pageIndex)
        assertEquals(900, payload?.locator?.startOffset)
        assertEquals(913, payload?.locator?.endOffset)
        assertEquals(7, payload?.locator?.blockIndex)
        assertEquals(900, payload?.locator?.charOffset)
        assertEquals("selected text", payload?.locator?.textQuote)
        assertEquals("/4/8:12|/4/8:25", payload?.locator?.cfi)
    }

    @Test
    fun `selection action bridge parses highlight palette manager action`() {
        val payload = """
            {
              "action": "palette",
              "text": "selected text"
            }
        """.trimIndent().readerSelectionActionOrNull()

        assertEquals(DesktopReaderSelectionAction.PALETTE, payload?.action)
        assertEquals("selected text", payload?.text)
    }

    @Test
    fun `desktop epub chrome tap script keeps click fallback for pointer-capable webviews`() {
        assertTrue(DesktopEpubKeyNavigationScript.contains("var lastChromeTapNotifiedAt = 0;"))
        assertTrue(DesktopEpubKeyNavigationScript.contains("function maybeNotifyChromeTapFromClick(event)"))
        assertTrue(DesktopEpubKeyNavigationScript.contains("if (window.PointerEvent) {"))
        assertTrue(DesktopEpubKeyNavigationScript.contains("maybeNotifyChromeTapFromClick(event);"))
    }
}
