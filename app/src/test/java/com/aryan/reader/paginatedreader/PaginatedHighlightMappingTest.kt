package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import com.aryan.reader.epubreader.HighlightColor
import com.aryan.reader.epubreader.UserHighlight
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

    private fun paragraph(
        text: String,
        cfi: String,
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
        text: String
    ): UserHighlight {
        return UserHighlight(
            id = "highlight",
            cfi = cfi,
            text = text,
            color = HighlightColor.YELLOW,
            chapterIndex = 0
        )
    }
}
