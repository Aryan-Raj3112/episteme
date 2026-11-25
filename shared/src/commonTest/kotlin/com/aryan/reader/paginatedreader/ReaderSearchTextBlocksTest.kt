package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import com.aryan.reader.shared.SearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Search hits must land on the paginator's own text.
 *
 * The hit is produced by scanning chapter source text; the page is decided from
 * paginated block text. Footnote/endnote markup, math placeholders and book
 * replacements all make those two disagree by a character or two — which used
 * to shift every later ordinal, so tapping a result paged backwards. These
 * cases pin the ordinal-first, quote-second contract.
 */
class ReaderSearchTextBlocksTest {

    @Test
    fun `word-start ordinal resolves inside its block`() {
        val blocks = listOf(
            block(index = 0, offset = 100, text = "alpha needle beta"),
            block(index = 1, offset = 200, text = "gamma needle delta needle"),
        )

        val target = readerSearchTarget(blocks, "needle", occurrenceIndexInLocation = 2, matchQuote = "")

        assertEquals(ReaderSearchTarget(blockIndex = 1, charOffsetInSource = 219), target)
    }

    @Test
    fun `quote rescues a hit whose ordinal drifted past the block text`() {
        // The searcher saw three occurrences (a hidden note it could not page
        // to); the paginator only renders two of them.
        val blocks = listOf(
            block(index = 0, offset = 100, text = "alpha needle beta"),
            block(index = 1, offset = 200, text = "gamma needle delta"),
        )

        val target = readerSearchTarget(
            textBlocks = blocks,
            query = "needle",
            occurrenceIndexInLocation = 2,
            matchQuote = "gamma needle delta",
        )

        assertEquals(ReaderSearchTarget(blockIndex = 1, charOffsetInSource = 206), target)
    }

    @Test
    fun `quote wins over a nearby ordinal that is still in range`() {
        // A math placeholder upstream removed one occurrence from the paginated
        // text: the ordinal is in range, so trusting it would page to the wrong
        // match — the exact silent failure the quote exists to prevent.
        val blocks = listOf(
            block(index = 0, offset = 0, text = "needle one. needle two."),
            block(index = 1, offset = 40, text = "tail needle three."),
        )

        val target = readerSearchTarget(
            textBlocks = blocks,
            query = "needle",
            occurrenceIndexInLocation = 1,
            matchQuote = "tail needle three",
        )

        assertEquals(ReaderSearchTarget(blockIndex = 1, charOffsetInSource = 45), target)
    }

    @Test
    fun `equal quotes fall back to the requested ordinal`() {
        val blocks = listOf(
            block(index = 0, offset = 0, text = "see needle here"),
            block(index = 1, offset = 20, text = "see needle here"),
        )

        val target = readerSearchTarget(
            textBlocks = blocks,
            query = "needle",
            occurrenceIndexInLocation = 1,
            matchQuote = "see needle here",
        )

        assertEquals(ReaderSearchTarget(blockIndex = 1, charOffsetInSource = 24), target)
    }

    @Test
    fun `quote matches across a whitespace difference`() {
        val blocks = listOf(
            block(index = 3, offset = 40, text = "the  sword was broken"),
        )

        val target = readerSearchTarget(
            textBlocks = blocks,
            query = "sword",
            occurrenceIndexInLocation = 4,
            matchQuote = "The sword was",
        )

        assertEquals(ReaderSearchTarget(blockIndex = 3, charOffsetInSource = 45), target)
    }

    @Test
    fun `unresolvable hit returns null instead of the wrong block`() {
        val blocks = listOf(block(index = 0, offset = 0, text = "nothing here"))

        assertNull(readerSearchTarget(blocks, "needle", occurrenceIndexInLocation = 0, matchQuote = "also absent"))
        assertNull(readerSearchTarget(emptyList(), "needle", 0, ""))
    }

    @Test
    fun `nested block types are part of the searchable text`() {
        val blocks = listOf<ContentBlock>(
            ParagraphBlock(content = AnnotatedString("before"), blockIndex = 4, startCharOffsetInSource = 10),
            TableBlock(
                rows = listOf(
                    listOf(
                        TableCell(
                            content = listOf(
                                ParagraphBlock(
                                    content = AnnotatedString("cell needle"),
                                    blockIndex = 5,
                                    startCharOffsetInSource = 20,
                                )
                            )
                        )
                    )
                ),
                blockIndex = 6,
            ),
        )

        val target = readerSearchTarget(readerSearchTextBlocks(blocks), "needle", 0, "")

        assertEquals(ReaderSearchTarget(blockIndex = 5, charOffsetInSource = 25), target)
    }

    @Test
    fun `styled blocks convert to the paginated text space`() {
        val textBlocks = readerSearchTextBlocks(
            listOf(ParagraphBlock(content = AnnotatedString("hello"), blockIndex = 7, startCharOffsetInSource = 55))
        )

        assertEquals(ReaderSearchTextBlock(blockIndex = 7, charOffsetInSource = 55, text = "hello"), textBlocks.single())
    }

    @Test
    fun `semantic blocks flatten to the same text space the mobile paginator measures`() {
        val textBlocks = readerSearchTextBlocksFromSemanticBlocks(
            listOf(
                SemanticParagraph(
                    text = "opening",
                    spans = emptyList(),
                    style = CssStyle(),
                    elementId = null,
                    cfi = null,
                    startCharOffsetInSource = 12,
                    blockIndex = 2,
                ),
                SemanticTable(
                    rows = listOf(
                        listOf(
                            SemanticTableCell(
                                content = listOf(paragraph("in the cell", 30, 3)),
                                isHeader = false,
                                colspan = 1,
                                style = CssStyle(),
                            )
                        )
                    ),
                    style = CssStyle(),
                    elementId = null,
                    cfi = null,
                ),
            )
        )

        assertEquals(listOf(2, 3), textBlocks.map { it.blockIndex })
        assertEquals(listOf(12, 30), textBlocks.map { it.charOffsetInSource })
    }

    @Test
    fun `search result without a quote still resolves by ordinal`() {
        val result = SearchResult(
            locationInSource = 1,
            locationTitle = "Chapter",
            snippet = AnnotatedString("second needle"),
            query = "needle",
            occurrenceIndexInLocation = 1,
            chunkIndex = 0,
        )

        val target = findSharedNavigationTargetForSearchResult(
            result,
            listOf(
                ParagraphBlock(content = AnnotatedString("first needle"), blockIndex = 1, startCharOffsetInSource = 5),
                ParagraphBlock(content = AnnotatedString("second needle"), blockIndex = 2, startCharOffsetInSource = 40),
            ),
        )

        assertNotNull(target)
        assertEquals(ReaderNavigationTarget(chapterIndex = 1, blockIndex = 2, charOffset = 47), target)
    }

    private fun block(index: Int, offset: Int, text: String) = ReaderSearchTextBlock(
        blockIndex = index,
        charOffsetInSource = offset,
        text = text,
    )

    private fun paragraph(text: String, offset: Int, blockIndex: Int) = SemanticParagraph(
        text = text,
        spans = emptyList(),
        style = CssStyle(),
        elementId = null,
        cfi = null,
        startCharOffsetInSource = offset,
        blockIndex = blockIndex,
    )
}
