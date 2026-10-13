package com.aryan.reader.shared.search

import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.MATH_PLACEHOLDER_CHAR
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpacer
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTableCell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun paragraph(
    text: String,
    blockIndex: Int = 0,
    chapterCfi: String? = null,
) = SemanticParagraph(
    text = text,
    spans = emptyList(),
    style = CssStyle(),
    elementId = null,
    cfi = chapterCfi,
    startCharOffsetInSource = 0,
    blockIndex = blockIndex,
)

class SharedSearchChunkIndexerTest {

    @Test
    fun `semantic blocks keep chapter block and cfi identity`() {
        val chunks = SharedSearchChunkIndexer.fromSemanticBlocks(
            chapterIndex = 3,
            blocks = listOf(paragraph("hello world", blockIndex = 7, chapterCfi = "cfi-1")),
        )
        assertEquals(1, chunks.size)
        assertEquals(3, chunks.single().chapterIndex)
        assertEquals(7, chunks.single().blockIndex)
        assertEquals("cfi-1", chunks.single().cfi)
    }

    @Test
    fun `spacers skipped and math placeholders stripped`() {
        val chunks = SharedSearchChunkIndexer.fromSemanticBlocks(
            chapterIndex = 0,
            blocks = listOf(
                SemanticSpacer(style = CssStyle(), elementId = null, cfi = null),
                paragraph("a${MATH_PLACEHOLDER_CHAR}b"),
            ),
        )
        assertEquals(1, chunks.size)
        assertEquals("ab", chunks.single().text)
    }

    @Test
    fun `table cells flatten in order`() {
        fun cell(text: String) = SemanticTableCell(
            content = listOf(paragraph(text)),
            isHeader = false,
            colspan = 1,
            style = CssStyle(),
        )
        val chunks = SharedSearchChunkIndexer.fromSemanticBlocks(
            chapterIndex = 0,
            blocks = listOf(
                SemanticTable(
                    rows = listOf(listOf(cell("first"), cell("second"))),
                    style = CssStyle(),
                    elementId = null,
                    cfi = null,
                ),
            ),
        )
        assertEquals(listOf("first", "second"), chunks.map { it.text })
    }

    @Test
    fun `image alt text indexed without alt skipped`() {
        val chunks = SharedSearchChunkIndexer.fromSemanticBlocks(
            chapterIndex = 0,
            blocks = listOf(
                SemanticImage(
                    path = "/img/1.png",
                    altText = null,
                    intrinsicWidth = null,
                    intrinsicHeight = null,
                    style = CssStyle(),
                    elementId = null,
                    cfi = null,
                ),
                SemanticImage(
                    path = "/img/2.png",
                    altText = "chart of harvests",
                    intrinsicWidth = null,
                    intrinsicHeight = null,
                    style = CssStyle(),
                    elementId = null,
                    cfi = null,
                ),
            ),
        )
        assertEquals(1, chunks.size)
        assertEquals("chart of harvests", chunks.single().text)
    }

    @Test
    fun `pdf pages map to page indices and skip blanks`() {
        val chunks = SharedSearchChunkIndexer.fromPdfPageTexts(listOf("", "  ", "native text here"))
        assertEquals(1, chunks.size)
        assertEquals(2, chunks.single().pageIndex)
    }

    @Test
    fun `slides map order to chapters`() {
        val chunks = SharedSearchChunkIndexer.fromSlideTexts(listOf("intro", "", "conclusion"))
        assertEquals(listOf(0, 2), chunks.map { it.chapterIndex })
    }

    @Test
    fun `feeder output is searchable end to end`() {
        val coordinator = SharedGlobalSearchCoordinator()
        coordinator.upsertBookChunks(
            "b1",
            "h1",
            SharedSearchChunkIndexer.fromSemanticBlocks(0, listOf(paragraph("the harvest festival"))) +
                SharedSearchChunkIndexer.fromPdfPageTexts(listOf("unrelated page")),
        )
        val results = coordinator.search("harvest")
        assertEquals(1, results.size)
        assertTrue(results.single().hits.isNotEmpty())
        assertEquals(0, results.single().hits.first().chapterIndex)
    }
}
