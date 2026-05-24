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
    fun `same path split block outside stored offsets is ignored`() {
        val block = paragraph(
            text = "repeat",
            cfi = "/4/2",
            startOffset = 20
        )
        val highlight = highlight(
            cfi = "/4/2:0|/4/2:6",
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
    fun `paginated page highlights are scoped to page chapter`() {
        val chapterFourHighlight = highlight(
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
        startOffset: Int
    ): ParagraphBlock {
        return ParagraphBlock(
            content = AnnotatedString(text),
            cfi = cfi,
            startCharOffsetInSource = startOffset,
            endCharOffsetInSource = startOffset + text.length,
            blockIndex = startOffset
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
