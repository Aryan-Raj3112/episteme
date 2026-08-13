package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfTextBoxChromePolicyTest {
    @Test
    fun `selected chrome preserves text origin with top and bottom pills`() {
        val bounds = Rect(100f, 200f, 180f, 260f)

        listOf(false, true).forEach { isHandleAtTop ->
            val layout = calculateTextBoxChromeLayout(
                textBoundsPx = bounds,
                isSelected = true,
                isHandleAtTop = isHandleAtTop,
                handleSizePx = 10f,
                dragPillWidthPx = 120f,
                dragPillHeightPx = 48f,
                dragPillGapPx = 8f,
            )

            assertEquals(120f, layout.containerWidthPx)
            assertEquals(126f, layout.containerHeightPx)
            assertEquals(bounds.left, layout.outerTranslationX + layout.contentOffsetX + 5f)
            assertEquals(bounds.top, layout.outerTranslationY + layout.contentOffsetY + 5f)
            assertTrue(layout.dragPillLeftPx >= 0f)
            assertTrue(layout.dragPillTopPx >= 0f)
            assertTrue(layout.dragPillTopPx + 48f <= layout.containerHeightPx)
        }
    }

    @Test
    fun `unselected chrome adds only resize handle extent`() {
        val layout = calculateTextBoxChromeLayout(
            textBoundsPx = Rect(20f, 30f, 100f, 90f),
            isSelected = false,
            isHandleAtTop = true,
            handleSizePx = 12f,
            dragPillWidthPx = 72f,
            dragPillHeightPx = 48f,
            dragPillGapPx = 8f,
        )

        assertEquals(92f, layout.containerWidthPx)
        assertEquals(72f, layout.containerHeightPx)
        assertEquals(0f, layout.contentOffsetX)
        assertEquals(0f, layout.contentOffsetY)
        assertEquals(14f, layout.outerTranslationX)
        assertEquals(24f, layout.outerTranslationY)
    }
}
