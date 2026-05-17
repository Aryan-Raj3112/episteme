package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticFlexContainer
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticParagraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderImageModelsTest {

    @Test
    fun `reader image references keep chapter order and page locators`() {
        val style = CssStyle()
        val image = SemanticImage(
            path = "data:image/png;base64,abc",
            altText = "Cover art",
            intrinsicWidth = 320f,
            intrinsicHeight = 240f,
            style = style,
            elementId = "cover",
            cfi = "/4/2",
            blockIndex = 2
        )
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "chapter-one",
                    title = "One",
                    plainText = "Before after",
                    semanticBlocks = listOf(
                        SemanticParagraph("Before", emptyList(), style, null, "/4/1", startCharOffsetInSource = 0, blockIndex = 1),
                        image,
                        SemanticParagraph("after", emptyList(), style, null, "/4/3", startCharOffsetInSource = 7, blockIndex = 3)
                    ),
                    baseHref = "one.xhtml"
                )
            )
        )
        val pages = listOf(
            ReaderPage(
                pageIndex = 4,
                chapterIndex = 0,
                chapterTitle = "One",
                text = "Before after",
                startOffset = 0,
                endOffset = 12,
                semanticBlocks = book.chapters.first().semanticBlocks
            )
        )

        val references = book.readerImageReferences(pages)

        assertEquals(1, references.size)
        assertEquals("Cover art", references.first().displayTitle)
        assertEquals("320x240", references.first().dimensionLabel)
        assertEquals(4, references.first().locator.pageIndex)
        assertNull(references.first().locator.startOffset)
        assertEquals("/4/2", references.first().locator.cfi)
        assertEquals("Cover art.png", references.first().suggestedDownloadFileName())
    }

    @Test
    fun `reader image references include nested images`() {
        val style = CssStyle()
        val nestedImage = SemanticImage(
            path = "OPS/images/chart.webp",
            altText = null,
            intrinsicWidth = null,
            intrinsicHeight = null,
            style = style,
            elementId = null,
            cfi = null,
            blockIndex = 9
        )
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "chapter-one",
                    title = "One",
                    plainText = "",
                    semanticBlocks = listOf(
                        SemanticFlexContainer(
                            children = listOf(nestedImage),
                            style = style,
                            elementId = null,
                            cfi = null,
                            blockIndex = 8
                        )
                    )
                )
            )
        )

        val references = book.readerImageReferences()

        assertEquals(1, references.size)
        assertEquals("chart", references.first().displayTitle)
        assertEquals("chart.webp", references.first().suggestedDownloadFileName())
    }
}
