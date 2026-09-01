package com.aryan.reader.shared.ui

import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpan
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import com.aryan.reader.shared.reader.SharedEpubTocEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SharedEpubLinkResolutionTest {

    @Test
    fun `link with fragment resolves to the exact page holding the anchor`() {
        val book = anchorBook()
        val pages = listOf(
            page(0, chapterIndex = 0, startOffset = 0, endOffset = 50, text = "Chapter one opening."),
            page(1, chapterIndex = 0, startOffset = 51, endOffset = 100, text = "Second page of the chapter."),
            page(2, chapterIndex = 0, startOffset = 101, endOffset = 150, text = "Final page holding the anchor.")
        )

        val (locator, fragment) = assertNotNull(book.locatorForLink("one.xhtml#anchor-three", "one.xhtml", pages))

        assertEquals(0, locator.chapterIndex)
        assertEquals(2, locator.pageIndex)
        assertEquals("anchor-three", fragment)
        assertEquals(101, locator.startOffset)
        assertEquals(101, locator.endOffset)
    }

    @Test
    fun `link without fragment resolves to the chapter first page`() {
        val book = anchorBook()
        val pages = listOf(
            page(0, chapterIndex = 0, startOffset = 0, endOffset = 50, text = "Chapter one opening."),
            page(1, chapterIndex = 0, startOffset = 51, endOffset = 100, text = "Second page.")
        )

        val (locator, fragment) = assertNotNull(book.locatorForLink("one.xhtml", "one.xhtml", pages))

        assertEquals(0, locator.pageIndex)
        assertEquals(0, locator.startOffset)
        assertNull(fragment)
    }

    @Test
    fun `toc entry with fragment resolves to the exact page`() {
        val book = anchorBook()
        val pages = listOf(
            page(0, chapterIndex = 0, startOffset = 0, endOffset = 50, text = "Chapter one opening."),
            page(1, chapterIndex = 0, startOffset = 51, endOffset = 100, text = "Second page.")
        )
        val entry = SharedEpubTocEntry(
            label = "Anchor Two",
            href = "one.xhtml#anchor-two",
            fragmentId = "anchor-two",
            depth = 1
        )

        val locator = assertNotNull(book.locatorForTocEntry(entry, pages))

        assertEquals(0, locator.chapterIndex)
        assertEquals(1, locator.pageIndex)
        assertEquals(51, locator.startOffset)
    }

    @Test
    fun `relative link resolves against the owner chapter directory`() {
        val book = anchorBook()
        val pages = listOf(
            page(0, chapterIndex = 0, startOffset = 0, endOffset = 50, text = "Chapter one opening."),
            page(1, chapterIndex = 1, startOffset = 51, endOffset = 100, text = "Chapter two.")
        )

        val (locator, _) = assertNotNull(book.locatorForLink("chapters/two.xhtml#anchor-three", "one.xhtml", pages))

        assertEquals(1, locator.chapterIndex)
        assertEquals(1, locator.pageIndex)
    }

    @Test
    fun `unknown fragment falls back to the chapter first page`() {
        val book = anchorBook()
        val pages = listOf(
            page(0, chapterIndex = 0, startOffset = 0, endOffset = 50, text = "Chapter one opening."),
            page(1, chapterIndex = 0, startOffset = 51, endOffset = 100, text = "Second page.")
        )

        val (locator, _) = assertNotNull(book.locatorForLink("one.xhtml#missing-anchor", "one.xhtml", pages))

        assertEquals(0, locator.pageIndex)
        assertEquals(0, locator.startOffset)
    }

    @Test
    fun `link to unknown chapter returns null`() {
        val book = anchorBook()
        val pages = listOf(
            page(0, chapterIndex = 0, startOffset = 0, endOffset = 50, text = "Chapter one opening.")
        )

        assertNull(book.locatorForLink("missing.xhtml", "one.xhtml", pages))
    }

    private fun anchorBook(): SharedEpubBook {
        val first = SemanticParagraph(
            text = "Chapter one opening.",
            spans = emptyList(),
            style = CssStyle(),
            elementId = "anchor-one",
            cfi = "epubcfi(/6/4!/4/2)",
            startCharOffsetInSource = 0
        )
        val second = SemanticParagraph(
            text = "Middle section text.",
            spans = emptyList(),
            style = CssStyle(),
            elementId = "anchor-two",
            cfi = "epubcfi(/6/4!/4/6)",
            startCharOffsetInSource = 51
        )
        val third = SemanticParagraph(
            text = "Final anchored section.",
            spans = emptyList(),
            style = CssStyle(),
            elementId = "anchor-three",
            cfi = "epubcfi(/6/4!/4/10)",
            startCharOffsetInSource = 101
        )
        return SharedEpubBook(
            id = "anchor-book",
            fileName = "anchor.epub",
            title = "Anchor Book",
            author = "Author",
            chapters = listOf(
                SharedEpubChapter(
                    id = "chapter-one",
                    title = "One",
                    plainText = "Chapter one opening. Middle section text. Final anchored section.",
                    htmlContent = """
                        <html><body>
                        <p id="anchor-one">Chapter one opening.</p>
                        <p id="anchor-two">Middle section text.</p>
                        <p id="anchor-three">Final anchored section.</p>
                        </body></html>
                    """.trimIndent(),
                    baseHref = "one.xhtml",
                    semanticBlocks = listOf(first, second, third)
                ),
                SharedEpubChapter(
                    id = "chapter-two",
                    title = "Two",
                    plainText = "Chapter two text.",
                    htmlContent = "<html><body><p id=\"anchor-three\">Chapter two text.</p></body></html>",
                    baseHref = "chapters/two.xhtml",
                    semanticBlocks = listOf(
                        SemanticParagraph(
                            text = "Chapter two text.",
                            spans = emptyList(),
                            style = CssStyle(),
                            elementId = "anchor-three",
                            cfi = "epubcfi(/6/6!/4/2)",
                            startCharOffsetInSource = 0
                        )
                    )
                )
            )
        )
    }

    private fun page(
        pageIndex: Int,
        chapterIndex: Int,
        startOffset: Int,
        endOffset: Int,
        text: String
    ): ReaderPage {
        return ReaderPage(
            pageIndex = pageIndex,
            chapterIndex = chapterIndex,
            chapterTitle = "Chapter",
            text = text,
            startOffset = startOffset,
            endOffset = endOffset
        )
    }
}