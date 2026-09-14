package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import com.aryan.reader.epubreader.HighlightColor
import com.aryan.reader.epubreader.UserHighlight
import com.aryan.reader.shared.ReaderLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaginatedHighlightMappingTest {

    @Test
    fun `single cfi highlight does not leak onto later matching block`() {
        val block = paragraph(
            text = "repeat",
            cfi = "/4/4",
            startOffset = 20
        )
        val highlight = highlight(
            cfi = "/4/2:0",
            text = "repeat"
        )

        assertNull(getHighlightOffsetsInBlock(block, highlight))
    }

    @Test
    fun `multipart highlight can fill strict intermediate block`() {
        val block = paragraph(
            text = "middle",
            cfi = "/4/4",
            startOffset = 20
        )
        val highlight = highlight(
            cfi = "/4/2:0|/4/6:10",
            text = "start middle end"
        )

        assertEquals(0 until 6, getHighlightOffsetsInBlock(block, highlight))
    }

    @Test
    fun `same path split uses cfi offsets as local to block`() {
        val block = paragraph(
            text = "repeat",
            cfi = "/4/2",
            startOffset = 20
        )
        val highlight = highlight(
            cfi = "/4/2:0|/4/2:6",
            text = "repeat"
        )

        assertEquals(0 until 6, getHighlightOffsetsInBlock(block, highlight))
    }

    @Test
    fun `same path split block outside stored offsets is ignored`() {
        val block = paragraph(
            text = "repeat",
            cfi = "/4/2",
            startOffset = 20
        )
        val highlight = highlight(
            cfi = "/4/2:40|/4/2:46",
            text = "repeat"
        )

        assertNull(getHighlightOffsetsInBlock(block, highlight))
    }

    @Test
    fun `desktop locator highlight maps by source offsets`() {
        val block = paragraph(
            text = "alpha beta gamma",
            cfi = null,
            startOffset = 20
        )
        val highlight = highlight(
            cfi = "desktop:0:26:30",
            text = "beta"
        )

        assertEquals(6 until 10, getHighlightOffsetsInBlock(block, highlight))
    }

    @Test
    fun `locator offsets win over cfi offsets for synced highlights`() {
        val block = paragraph(
            text = "alpha beta gamma",
            cfi = "/4/2",
            startOffset = 200
        )
        val highlight = highlight(
            cfi = "/4/2:6|/4/2:10",
            text = "beta",
            locator = ReaderLocator(
                chapterIndex = 0,
                startOffset = 206,
                endOffset = 210,
                cfi = "/4/2:6|/4/2:10",
                textQuote = "beta"
            )
        )

        assertEquals(6 until 10, getHighlightOffsetsInBlock(block, highlight))
    }

    @Test
    fun `locator offsets prevent cfi fallback from painting unrelated duplicate block`() {
        val block = paragraph(
            text = "alpha beta gamma",
            cfi = "/4/4",
            startOffset = 300
        )
        val highlight = highlight(
            cfi = "/4/2:6|/4/2:10",
            text = "beta",
            locator = ReaderLocator(
                chapterIndex = 0,
                startOffset = 206,
                endOffset = 210,
                cfi = "/4/2:6|/4/2:10",
                textQuote = "beta"
            )
        )

        assertNull(getHighlightOffsetsInBlock(block, highlight))
    }

    @Test
    fun `block local locator offsets do not paint sibling blocks with overlapping local ranges`() {
        val highlight = highlight(
            cfi = "/4/4/6:124|/4/4/6:248",
            text = "selected text",
            locator = ReaderLocator(
                chapterIndex = 8,
                pageIndex = 49,
                startOffset = 124,
                endOffset = 248,
                blockIndex = 1,
                charOffset = 124,
                textQuote = "selected text",
                cfi = "/4/4/6:124|/4/4/6:248"
            )
        )
        val selectedBlock = paragraph(
            text = "x".repeat(260),
            cfi = "/4/4/6",
            startOffset = 0,
            blockIndex = 1
        )
        val siblingBlock = paragraph(
            text = "x".repeat(684),
            cfi = "/4/4/8",
            startOffset = 0,
            blockIndex = 2
        )

        assertEquals(124 until 248, getHighlightOffsetsInBlock(selectedBlock, highlight))
        assertNull(getHighlightOffsetsInBlock(siblingBlock, highlight))
    }

    @Test
    fun `source cfi local offsets map within nonzero source block`() {
        val block = paragraph(
            text = "alpha beta gamma",
            cfi = "/4/2",
            startOffset = 200
        )
        val highlight = highlight(
            cfi = "/4/2:6|/4/2:10",
            text = "beta"
        )

        assertEquals(6 until 10, getHighlightOffsetsInBlock(block, highlight))
    }

    @Test
    fun `legacy absolute cfi offsets remain supported for synced highlights`() {
        val block = paragraph(
            text = "alpha beta gamma",
            cfi = "/4/2",
            startOffset = 200
        )
        val highlight = highlight(
            cfi = "/4/2:206|/4/2:210",
            text = "beta"
        )

        assertEquals(6 until 10, getHighlightOffsetsInBlock(block, highlight))
    }

    @Test
    fun `ranged highlight does not quote-paint parent block with same text`() {
        // Same sentence duplicated across pages: the CFI is related (parent path) but the
        // absolute range lives elsewhere. Quote fallback must not relocate it here.
        val block = paragraph(
            text = "alpha beta gamma",
            cfi = "/4",
            startOffset = 500,
            blockIndex = 9
        )
        val highlight = highlight(
            cfi = "/4/2:6|/4/2:10",
            text = "beta",
            locator = ReaderLocator(
                chapterIndex = 0,
                startOffset = 206,
                endOffset = 210,
                blockIndex = 2,
                charOffset = 206,
                cfi = "/4/2:6|/4/2:10",
                textQuote = "beta"
            )
        )

        assertNull(getHighlightOffsetsInBlock(block, highlight))
    }

    @Test
    fun `legacy highlight without range keeps quote recovery on related block`() {
        val block = paragraph(
            text = "repeat",
            cfi = "/4",
            startOffset = 20,
            blockIndex = 9
        )
        val highlight = highlight(
            cfi = "/4/2:0|/4/2:6",
            text = "repeat"
        )

        assertEquals(0 until 6, getHighlightOffsetsInBlock(block, highlight))
    }

    @Test
    fun `cfi offset outside block resolves to null instead of clamped tail`() {
        assertEquals(10, cfiOffsetToBlockLocal(10, 0, 500, 500))
        assertEquals(20, cfiOffsetToBlockLocal(520, 500, 1000, 500))
        assertNull(cfiOffsetToBlockLocal(800, 0, 500, 500))
        assertNull(cfiOffsetToBlockLocal(5000, 0, 200, 200))
    }

    @Test
    fun `page filter drops ranged highlight that misses the page`() {
        val onPage = highlight(
            cfi = "/4/2:6|/4/2:10",
            text = "beta",
            locator = ReaderLocator(
                chapterIndex = 0,
                startOffset = 206,
                endOffset = 210,
                blockIndex = 2,
                charOffset = 206,
                cfi = "/4/2:6|/4/2:10",
                textQuote = "beta"
            )
        )
        val otherChapter = highlight(
            cfi = "/4/2:6|/4/2:10",
            text = "beta",
            chapterIndex = 1,
            locator = ReaderLocator(
                chapterIndex = 1,
                startOffset = 206,
                endOffset = 210,
                cfi = "/4/2:6|/4/2:10",
                textQuote = "beta"
            )
        )
        val pageBlocks = listOf(
            paragraph(text = "alpha beta gamma", cfi = "/4/2", startOffset = 200, blockIndex = 2)
        )

        assertEquals(
            listOf(onPage),
            highlightsForPaginatedPage(
                pageChapterIndex = 0,
                userHighlights = listOf(onPage, otherChapter),
                pageStartOffset = 200,
                pageEndOffset = 300,
                pageBlocks = pageBlocks
            )
        )
        assertEquals(
            emptyList<UserHighlight>(),
            highlightsForPaginatedPage(
                pageChapterIndex = 0,
                userHighlights = listOf(onPage),
                pageStartOffset = 300,
                pageEndOffset = 400,
                pageBlocks = listOf(
                    paragraph(text = "delta epsilon", cfi = "/4/4", startOffset = 300, blockIndex = 4)
                )
            )
        )
    }

    @Test
    fun `page filter keeps range miss when cfi touches page block`() {
        val highlight = highlight(
            cfi = "/4/2:6|/4/2:10",
            text = "beta",
            locator = ReaderLocator(
                chapterIndex = 0,
                startOffset = 206,
                endOffset = 210,
                blockIndex = 2,
                charOffset = 206,
                cfi = "/4/2:6|/4/2:10",
                textQuote = "beta"
            )
        )
        // Stale absolute range, but the CFI still names a block on this page: keep for
        // CFI-anchored recovery instead of hiding the highlight.
        val pageBlocks = listOf(
            paragraph(text = "alpha beta gamma", cfi = "/4/2", startOffset = 1000, blockIndex = 2)
        )

        assertEquals(
            listOf(highlight),
            highlightsForPaginatedPage(
                pageChapterIndex = 0,
                userHighlights = listOf(highlight),
                pageStartOffset = 1000,
                pageEndOffset = 1100,
                pageBlocks = pageBlocks
            )
        )
    }

    @Test
    fun `page filter without range keeps legacy chapter-wide behavior`() {
        val legacy = highlight(
            cfi = "/4/2:0|/4/2:6",
            text = "repeat"
        )

        assertEquals(
            listOf(legacy),
            highlightsForPaginatedPage(
                pageChapterIndex = 0,
                userHighlights = listOf(legacy),
                pageStartOffset = 9000,
                pageEndOffset = 9100,
                pageBlocks = listOf(
                    paragraph(text = "other", cfi = "/4/9", startOffset = 9000, blockIndex = 9)
                )
            )
        )
    }

    @Test
    fun `paginated page highlights are scoped to page chapter`() {        val chapterFourHighlight = highlight(
            cfi = "/4/10:11|/4/12:79",
            text = "Original chapter text",
            chapterIndex = 4
        )
        val chapterFiveHighlight = highlight(
            cfi = "/4/10:11|/4/12:79",
            text = "Different chapter text",
            chapterIndex = 5
        )

        assertEquals(
            listOf(chapterFiveHighlight),
            highlightsForPaginatedPage(
                pageChapterIndex = 5,
                userHighlights = listOf(chapterFourHighlight, chapterFiveHighlight)
            )
        )
        assertEquals(
            emptyList<UserHighlight>(),
            highlightsForPaginatedPage(
                pageChapterIndex = null,
                userHighlights = listOf(chapterFourHighlight)
            )
        )
    }

    private fun paragraph(
        text: String,
        cfi: String?,
        startOffset: Int,
        blockIndex: Int = startOffset
    ): ParagraphBlock {
        return ParagraphBlock(
            content = AnnotatedString(text),
            cfi = cfi,
            startCharOffsetInSource = startOffset,
            endCharOffsetInSource = startOffset + text.length,
            blockIndex = blockIndex
        )
    }

    private fun highlight(
        cfi: String,
        text: String,
        chapterIndex: Int = 0,
        locator: ReaderLocator = ReaderLocator.fromLegacy(
            chapterIndex = chapterIndex,
            cfi = cfi,
            textQuote = text
        )
    ): UserHighlight {
        return UserHighlight(
            id = "highlight",
            cfi = cfi,
            text = text,
            color = HighlightColor.YELLOW,
            chapterIndex = chapterIndex,
            locator = locator
        )
    }
}
