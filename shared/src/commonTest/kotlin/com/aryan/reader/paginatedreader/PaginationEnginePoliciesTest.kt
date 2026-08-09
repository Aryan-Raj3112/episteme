package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaginationEnginePoliciesTest {
    @Test
    fun breakPoliciesMatchAndroidCssAliases() {
        assertTrue(BlockStyle(pageBreakInsideAvoid = true).avoidsReaderPageBreakInside())
        assertTrue(BlockStyle(breakInside = "avoid-page").avoidsReaderPageBreakInside())
        assertFalse(BlockStyle(breakInside = "auto").avoidsReaderPageBreakInside())
        listOf("page", "always", "left", "right", "recto", "verso").forEach { value ->
            assertTrue(BlockStyle(breakBefore = value).forcesReaderPageBreakBefore())
            assertTrue(BlockStyle(breakAfter = value).forcesReaderPageBreakAfter())
        }
        assertFalse(BlockStyle(breakBefore = "avoid").forcesReaderPageBreakBefore())
    }

    @Test
    fun committedPageDropsOnlyLastBottomMargin() {
        val first = ParagraphBlock(
            AnnotatedString("first"),
            style = BlockStyle(margin = BoxBorders(bottom = 4.dp)),
            blockIndex = 1,
            expectedHeight = 20
        )
        val last = ParagraphBlock(
            AnnotatedString("last"),
            style = BlockStyle(margin = BoxBorders(top = 2.dp, bottom = 8.dp)),
            blockIndex = 2,
            expectedHeight = 30
        )
        val blocks = mutableListOf<ContentBlock>(first, last)
        zeroReaderLastBottomMargin(blocks)

        assertEquals(first, blocks[0])
        val adjusted = blocks[1] as ParagraphBlock
        assertEquals(2.dp, adjusted.style.margin.top)
        assertEquals(0.dp, adjusted.style.margin.bottom)
        assertEquals(30, adjusted.expectedHeight)
    }
}
