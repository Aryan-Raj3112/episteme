package com.aryan.reader.shared.ui

import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedMobileEpubNavigationPayloadTest {

    @Test
    fun `small navigation chunk stays inline for instant landing`() {
        val script = sharedMobileEpubNavigationScript(
            locator = ReaderLocator(chapterIndex = 0, startOffset = 0),
            fragment = null,
            targetChunkIndex = 2,
            targetChunkHtml = "<p>small</p>",
        )

        assertTrue(script.contains("provideChunk(2,"))
        assertFalse(script.contains("requestChunk(2)"))
    }

    @Test
    fun `large navigation chunk uses bridge instead of inline payload`() {
        val bigChunk = "<div>" + "word ".repeat(30_000) + "</div>"
        val script = sharedMobileEpubNavigationScript(
            locator = ReaderLocator(chapterIndex = 0, startOffset = 0),
            fragment = null,
            targetChunkIndex = 4,
            targetChunkHtml = bigChunk,
        )

        assertTrue(script.contains("requestChunk(4)"))
        assertFalse(script.contains("word word word word word word word word word word"))
        assertTrue(script.contains("setInterval"))
    }

    @Test
    fun `large search chunk uses bridge with retry`() {
        val bigChunk = "<div>" + "alpha ".repeat(30_000) + "</div>"
        val result = SharedMobileEpubSearchResult(
            chapterIndex = 0,
            chapterTitle = "Chapter",
            chunkIndex = 7,
            occurrenceIndex = 0,
            snippet = "alpha",
            locator = ReaderLocator(chapterIndex = 0, startOffset = 0),
        )
        val script = sharedMobileEpubSearchNavigationScript(result, "alpha", bigChunk)

        assertTrue(script.contains("requestChunk(7)"))
        assertTrue(script.contains("setInterval"))
    }

    @Test
    fun `large scroll to end uses bridge with delayed rescroll`() {
        val bigChunk = "<div>" + "omega ".repeat(30_000) + "</div>"
        val script = sharedMobileEpubScrollToEndScript(9, bigChunk)

        assertTrue(script.contains("requestChunk(9)"))
        assertTrue(script.contains("setTimeout(scrollEnd, 500)"))
        assertTrue(bigChunk.length > ReaderHtmlDocumentBuilder.MaxInlineVirtualChunkChars)
    }
}
