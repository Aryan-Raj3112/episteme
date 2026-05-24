package com.aryan.reader.desktop

import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.shared.reader.ReaderPage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopReaderTypographyTest {

    @Test
    fun `same page layout includes semantic styling`() {
        val plain = pageWith(
            SemanticParagraph(
                text = "Styled text",
                spans = emptyList(),
                style = CssStyle(),
                elementId = null,
                cfi = null,
                startCharOffsetInSource = 0,
                blockIndex = 0
            )
        )
        val styled = pageWith(
            SemanticParagraph(
                text = "Styled text",
                spans = emptyList(),
                style = CssStyle(fontSize = 24.sp),
                elementId = null,
                cfi = null,
                startCharOffsetInSource = 0,
                blockIndex = 0
            )
        )

        assertFalse(listOf(plain).samePageLayoutAs(listOf(styled)))
    }

    @Test
    fun `same page layout still matches identical semantic pages`() {
        val page = pageWith(
            SemanticParagraph(
                text = "Styled text",
                spans = emptyList(),
                style = CssStyle(fontSize = 24.sp),
                elementId = null,
                cfi = null,
                startCharOffsetInSource = 0,
                blockIndex = 0
            )
        )

        assertTrue(listOf(page).samePageLayoutAs(listOf(page.copy())))
    }

    private fun pageWith(block: SemanticParagraph): ReaderPage {
        return ReaderPage(
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = "One",
            text = block.text,
            startOffset = 0,
            endOffset = block.text.length,
            semanticBlocks = listOf(block)
        )
    }
}
