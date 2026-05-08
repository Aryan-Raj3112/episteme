package com.aryan.reader.shared.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.aryan.reader.paginatedreader.BlockStyle
import com.aryan.reader.paginatedreader.BorderStyle
import com.aryan.reader.paginatedreader.BoxBorders
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticListItem
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpan
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTableCell
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderTexture
import com.aryan.reader.shared.UserHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `selection menu omits ai and tts actions when disabled`() {
        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("alpha beta"),
            page = ReaderPage(
                pageIndex = 0,
                chapterIndex = 0,
                chapterTitle = "One",
                text = "alpha beta",
                startOffset = 0,
                endOffset = 10
            ),
            settings = ReaderSettings(),
            readerAiFeaturesEnabled = false,
            cloudTtsEnabled = false
        )

        assertFalse(html.contains("""data-action="define""""))
        assertFalse(html.contains("""data-action="speak""""))
        assertTrue(html.contains("""data-action="dictionary""""))
        assertTrue(html.contains("""data-action="web-search""""))
    }

    @Test
    fun `page document uses supplied texture data uri`() {
        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("alpha beta"),
            page = ReaderPage(
                pageIndex = 0,
                chapterIndex = 0,
                chapterTitle = "One",
                text = "alpha beta",
                startOffset = 0,
                endOffset = 10
            ),
            settings = ReaderSettings(
                textureId = ReaderTexture.PAPER.id,
                textureAlpha = 0.5f
            ),
            textureDataUri = "data:image/png;base64,readertexture"
        )

        assertTrue(html.contains("url('data:image/png;base64,readertexture')"))
        assertTrue(html.contains("mix-blend-mode: multiply"))
        assertTrue(html.contains("opacity: 0.5"))
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

    @Test
    fun `page document renders semantic link spans as anchors`() {
        val text = "Open the reference"
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = text,
                    semanticBlocks = listOf(
                        SemanticParagraph(
                            text = text,
                            spans = listOf(
                                SemanticSpan(
                                    start = 9,
                                    end = text.length,
                                    style = CssStyle(),
                                    linkHref = "notes.xhtml#ref",
                                    tag = "a"
                                )
                            ),
                            style = CssStyle(),
                            elementId = null,
                            cfi = null,
                            startCharOffsetInSource = 0
                        )
                    )
                )
            )
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = book,
            page = ReaderPage(0, 0, "One", text, 0, text.length),
            settings = ReaderSettings()
        )

        assertTrue(html.contains("""<a href="notes.xhtml#ref" data-reader-link="true">reference</a>"""))
        assertTrue(html.contains("readerLinkClicked"))
        assertTrue(html.contains("bridge_missing"))
        assertTrue(html.contains("readerlink://click?payload="))
        assertTrue(html.contains("fallback_navigation_error"))
        assertTrue(html.contains("event.preventDefault();"))
    }

    @Test
    fun `page document carries semantic table and inline css without forced table grid`() {
        val text = "Styled cell"
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = text,
                    semanticBlocks = listOf(
                        SemanticTable(
                            rows = listOf(
                                listOf(
                                    SemanticTableCell(
                                        content = listOf(
                                            SemanticParagraph(
                                                text = text,
                                                spans = listOf(
                                                    SemanticSpan(
                                                        start = 0,
                                                        end = 6,
                                                        style = CssStyle(
                                                            spanStyle = SpanStyle(fontWeight = FontWeight.Bold),
                                                            textTransform = "uppercase"
                                                        ),
                                                        tag = "span"
                                                    )
                                                ),
                                                style = CssStyle(),
                                                elementId = null,
                                                cfi = null,
                                                startCharOffsetInSource = 0
                                            )
                                        ),
                                        isHeader = false,
                                        colspan = 1,
                                        style = CssStyle(
                                            blockStyle = BlockStyle(
                                                padding = BoxBorders(left = 4.dp),
                                                borderBottom = BorderStyle(width = 2.dp, color = Color.Red, style = "solid")
                                            )
                                        )
                                    )
                                )
                            ),
                            style = CssStyle(),
                            elementId = null,
                            cfi = null
                        )
                    )
                )
            )
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = book,
            page = ReaderPage(0, 0, "One", text, 0, text.length),
            settings = ReaderSettings()
        )

        assertTrue(html.contains("border-bottom:2.0px solid #ff0000"))
        assertTrue(html.contains("padding-left:4.0px"))
        assertTrue(html.contains("font-weight:700"))
        assertTrue(html.contains("text-transform:uppercase"))
        assertTrue(!Regex("""td,\s*th\s*\{\s*border:""").containsMatchIn(html))
    }

    @Test
    fun `page document clips semantic lists to visible items and keeps marker styles`() {
        val first = "Chapter one"
        val second = "Chapter two"
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "toc",
                    title = "Contents",
                    plainText = "$first\n$second",
                    semanticBlocks = listOf(
                        SemanticList(
                            items = listOf(
                                SemanticListItem(
                                    text = first,
                                    spans = emptyList(),
                                    style = CssStyle(),
                                    elementId = null,
                                    cfi = null,
                                    startCharOffsetInSource = 0,
                                    itemMarkerImage = null
                                ),
                                SemanticListItem(
                                    text = second,
                                    spans = listOf(
                                        SemanticSpan(
                                            start = 0,
                                            end = second.length,
                                            style = CssStyle(),
                                            linkHref = "chap02.xhtml",
                                            tag = "a"
                                        )
                                    ),
                                    style = CssStyle(
                                        blockStyle = BlockStyle(
                                            padding = BoxBorders(left = 2.dp),
                                            listStyleImage = "icons/toc-dot.png"
                                        )
                                    ),
                                    elementId = null,
                                    cfi = null,
                                    startCharOffsetInSource = first.length + 1,
                                    itemMarkerImage = "icons/toc-dot.png"
                                )
                            ),
                            isOrdered = false,
                            style = CssStyle(
                                fontSize = 0.85.em,
                                blockStyle = BlockStyle(listStyleType = "none")
                            ),
                            elementId = null,
                            cfi = null
                        )
                    )
                )
            )
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = book,
            page = ReaderPage(0, 0, "Contents", second, first.length + 1, first.length + 1 + second.length),
            settings = ReaderSettings()
        )

        assertTrue(!html.contains(first))
        assertTrue(html.contains(second))
        assertTrue(html.contains("list-style-type:none"))
        assertTrue(html.contains("font-size:0.85em"))
        assertTrue(html.contains("list-style-image:url(&#39;icons/toc-dot.png&#39;)"))
        assertTrue(html.contains("""<a href="chap02.xhtml" data-reader-link="true">Chapter two</a>"""))
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
