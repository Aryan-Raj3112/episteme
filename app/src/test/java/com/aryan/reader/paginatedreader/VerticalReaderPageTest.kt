package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards which paginated pages take the native tategaki renderer
 * ([VerticalPageContent]). Pagination segments mixed writing modes, so the
 * first non-spacer block decides the whole page.
 */
class VerticalReaderPageTest {

    private fun paragraph(writingMode: String?, index: Int = 0) = ParagraphBlock(
        content = AnnotatedString("あいう"),
        style = BlockStyle(writingMode = writingMode),
        blockIndex = index
    )

    private fun spacer(index: Int = 0) = SpacerBlock(blockIndex = index)

    @Test
    fun `vertical first block selects tategaki rendering`() {
        assertTrue(listOf(paragraph("vertical-rl")).isVerticalReaderPage())
        assertTrue(listOf(paragraph("vertical-lr")).isVerticalReaderPage())
    }

    @Test
    fun `leading spacers do not decide the page`() {
        assertTrue(listOf(spacer(), spacer(1), paragraph("vertical-rl", 2)).isVerticalReaderPage())
        assertFalse(listOf(spacer(), paragraph(null, 1)).isVerticalReaderPage())
    }

    @Test
    fun `horizontal and empty pages stay on the horizontal renderer`() {
        assertFalse(listOf(paragraph(null)).isVerticalReaderPage())
        assertFalse(listOf(paragraph("horizontal-tb")).isVerticalReaderPage())
        assertFalse(emptyList<ContentBlock>().isVerticalReaderPage())
        assertFalse(listOf(spacer()).isVerticalReaderPage())
    }
}
