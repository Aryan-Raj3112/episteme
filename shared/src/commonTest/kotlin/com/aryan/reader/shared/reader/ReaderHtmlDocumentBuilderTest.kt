package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderHtmlDocumentBuilderTest {

    @Test
    fun `page document renders only the highlighted occurrence from locator offsets`() {
        val text = "alpha beta alpha beta"
        val page = ReaderPage(
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = "One",
            text = text,
            startOffset = 0,
            endOffset = text.length
        )
        val highlight = UserHighlight(
            id = "highlight-1",
            cfi = "desktop:0:11:16",
            text = "alpha",
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator(
                chapterIndex = 0,
                pageIndex = 0,
                startOffset = 11,
                endOffset = 16,
                textQuote = "alpha",
                cfi = "desktop:0:11:16"
            )
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook(text),
            page = page,
            settings = ReaderSettings(),
            highlights = listOf(highlight)
        )

        assertEquals(1, Regex("<mark class=\"reader-user-highlight").findAll(html).count())
        assertTrue(html.contains("""alpha beta <mark class="reader-user-highlight user-highlight-yellow" data-reader-highlight-id="highlight-1" data-reader-start-offset="11" data-reader-end-offset="16">alpha</mark> beta"""))
    }

    @Test
    fun `vertical document carries active locator for shared scroll navigation`() {
        val html = ReaderHtmlDocumentBuilder.verticalDocument(
            book = SharedEpubBook(
                id = "book",
                fileName = "book.epub",
                title = "Book",
                chapters = listOf(
                    SharedEpubChapter("one", "One", "First chapter text."),
                    SharedEpubChapter("two", "Two", "Second chapter text.")
                )
            ),
            settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL),
            navigationLocator = ReaderLocator(
                chapterIndex = 1,
                startOffset = 7,
                endOffset = 14,
                cfi = "desktop:1:7:14"
            )
        )

        assertTrue(html.contains("data-reader-active-chapter-index=\"1\""))
        assertTrue(html.contains("data-reader-active-start-offset=\"7\""))
        assertTrue(html.contains("scrollToActiveLocator"))
    }

    @Test
    fun `page document keeps semantic images anchored to surrounding text page`() {
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = "Before image after image.",
                    semanticBlocks = listOf(
                        SemanticParagraph("Before image", emptyList(), CssStyle(), null, null, startCharOffsetInSource = 0),
                        SemanticImage("data:image/png;base64,abc", "Cover", null, null, CssStyle(), null, null),
                        SemanticParagraph("after image", emptyList(), CssStyle(), null, null, startCharOffsetInSource = 13)
                    )
                )
            )
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = book,
            page = ReaderPage(0, 0, "One", "Before image after image.", 0, 24),
            settings = ReaderSettings()
        )

        assertTrue(html.contains("""<img src="data:image/png;base64,abc" alt="Cover""""))
    }

    private fun repeatedWordBook(text: String): SharedEpubBook {
        return SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = text
                )
            )
        )
    }
}
