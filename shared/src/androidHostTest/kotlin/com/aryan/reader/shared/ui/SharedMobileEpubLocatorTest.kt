package com.aryan.reader.shared.ui

import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticListItem
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import com.aryan.reader.shared.reader.sharedEpubHtmlToSemanticBlocks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedMobileEpubLocatorTest {

    @Test
    fun `page locator carries block cfi and source offsets`() {
        val blocks = sharedEpubHtmlToSemanticBlocks("<h1>Title</h1><p>Body text here</p>")
        val page = ReaderPage(
            pageIndex = 3,
            chapterIndex = 1,
            chapterTitle = "chapter",
            text = "Title\nBody text here",
            startOffset = 0,
            endOffset = 18,
            semanticBlocks = blocks
        )
        val locator = page.toMobileEpubLocator(
            SharedEpubBook(
                id = "book",
                fileName = "book.epub",
                title = "Book",
                chapters = listOf(
                    SharedEpubChapter(id = "a", title = "a", plainText = "x"),
                    SharedEpubChapter(id = "b", title = "chapter", plainText = "Title\nBody text here")
                )
            )
        )
        assertEquals(1, locator.chapterIndex)
        assertEquals(3, locator.pageIndex)
        val firstTextBlock = blocks.filterIsInstance<SemanticParagraph>().first()
        val header = blocks[0] as SemanticHeader
        assertEquals(header.blockIndex, locator.blockIndex)
        assertEquals(header.startCharOffsetInSource, locator.charOffset)
        assertEquals("/4/2:0", locator.cfi)
        assertEquals("Title\nBody text here", locator.textQuote?.take(120))
    }

    @Test
    fun `list items resolve through the list container`() {
        val blocks = sharedEpubHtmlToSemanticBlocks("<ul><li>Alpha</li><li>Beta</li></ul>")
        val list = blocks[0] as SemanticList
        val page = ReaderPage(
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = "",
            text = "Alpha\nBeta",
            startOffset = 0,
            endOffset = 10,
            semanticBlocks = listOf(list)
        )
        val locator = page.toMobileEpubLocator(null)
        assertEquals(list.items[0].blockIndex, locator.blockIndex)
        assertEquals(list.items[0].startCharOffsetInSource, locator.charOffset)
        assertEquals("/4/2/2:0", locator.cfi)
    }

    @Test
    fun `header page produces cfi from header block`() {
        val blocks = sharedEpubHtmlToSemanticBlocks("<h1>Intro</h1>")
        val header = blocks[0] as SemanticHeader
        val page = ReaderPage(
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = "",
            text = "Intro",
            startOffset = 0,
            endOffset = 5,
            semanticBlocks = listOf(header)
        )
        val locator = page.toMobileEpubLocator(null)
        assertEquals(header.blockIndex, locator.blockIndex)
        assertEquals(header.startCharOffsetInSource, locator.charOffset)
        assertEquals("/4/2:0", locator.cfi)
    }

    @Test
    fun `missing blocks fall back to plain text locator`() {
        val page = ReaderPage(
            pageIndex = 1,
            chapterIndex = 0,
            chapterTitle = "",
            text = "just text",
            startOffset = 4,
            endOffset = 9
        )
        val locator = page.toMobileEpubLocator(null)
        assertEquals(1, locator.pageIndex)
        assertEquals(4, locator.startOffset)
        assertNull(locator.blockIndex)
        assertNull(locator.charOffset)
        assertNull(locator.cfi)
        assertEquals("just text", locator.textQuote?.take(120))
    }
}