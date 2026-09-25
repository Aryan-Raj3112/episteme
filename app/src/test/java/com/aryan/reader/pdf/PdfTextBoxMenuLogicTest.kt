package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.aryan.reader.pdf.data.PdfTextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic coverage for the text box compact menu: duplicate placement
 * (below the original, clamped into the page) and lock semantics (styles and
 * text stay editable while geometry chrome is hidden).
 */
class PdfTextBoxMenuLogicTest {

    private fun box(
        left: Float = 0.2f,
        top: Float = 0.3f,
        right: Float = 0.7f,
        bottom: Float = 0.5f,
        isLocked: Boolean = false,
    ) = PdfTextBox(
        id = "box",
        pageIndex = 0,
        relativeBounds = Rect(left, top, right, bottom),
        text = "hello",
        color = Color.Black,
        backgroundColor = Color.Transparent,
        fontSize = 0.02f,
        isBold = true,
        isLocked = isLocked,
    )

    private fun duplicateBounds(source: PdfTextBox): Rect {
        val width = source.relativeBounds.width.coerceAtLeast(0f)
        val height = source.relativeBounds.height.coerceAtLeast(0f)
        val newLeft = (source.relativeBounds.left)
            .coerceIn(0f, (1f - width).coerceAtLeast(0f))
        val newTop = (source.relativeBounds.bottom + PDF_TEXT_BOX_DUPLICATE_GAP_REL)
            .coerceIn(0f, (1f - height).coerceAtLeast(0f))
        return Rect(newLeft, newTop, newLeft + width, newTop + height)
    }

    @Test
    fun `duplicate lands below original with same size and carries text styles and lock`() {
        val source = box()
        val bounds = duplicateBounds(source)

        assertEquals(source.relativeBounds.width, bounds.width, 1e-5f)
        assertEquals(PDF_TEXT_BOX_DUPLICATE_GAP_REL, bounds.top - source.relativeBounds.bottom, 1e-5f)
        assertEquals(source.relativeBounds.left, bounds.left, 1e-5f)

        val duplicate = source.copy(
            id = "dup",
            relativeBounds = bounds,
        )
        assertEquals(source.text, duplicate.text)
        assertEquals(source.isBold, duplicate.isBold)
        assertEquals(source.isLocked, duplicate.isLocked)
    }

    @Test
    fun `duplicate near page bottom clamps inside the page`() {
        val source = box(top = 0.9f, bottom = 0.99f)
        val bounds = duplicateBounds(source)

        assertTrue(bounds.bottom <= 1f)
        assertTrue(bounds.top < source.relativeBounds.bottom)
        assertEquals(source.relativeBounds.height, bounds.height, 1e-5f)
    }

    @Test
    fun `lock toggles without altering text or styles`() {
        val source = box()
        val locked = source.copy(isLocked = true)

        assertTrue(locked.isLocked)
        assertEquals(source.text, locked.text)
        assertEquals(source.fontSize, locked.fontSize)
        assertEquals(source.isBold, locked.isBold)
        assertEquals(source.relativeBounds, locked.relativeBounds)

        val unlockedAgain = locked.copy(isLocked = false)
        assertFalse(unlockedAgain.isLocked)
        assertEquals(source, unlockedAgain)
    }
}
