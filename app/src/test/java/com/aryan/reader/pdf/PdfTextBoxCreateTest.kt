package com.aryan.reader.pdf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.pdf.data.TextStyleConfig
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.SharedPdfRichListType
import com.aryan.reader.shared.pdf.SharedPdfRichParagraph
import com.aryan.reader.shared.pdf.SharedPdfRichTextAlign
import com.aryan.reader.shared.pdf.SharedPdfTextBoxSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextBoxCreateTest {

    private fun style() = TextStyleConfig(
        colorArgb = Color.Red.toArgb(),
        backgroundColorArgb = Color.Transparent.toArgb(),
        fontSize = 18f,
        isBold = true,
        isItalic = false,
        isUnderline = true,
        isStrikeThrough = false,
        fontPath = null,
        fontName = null,
    )

    @Test
    fun `tap box centers on tap with default size`() {
        val box = buildTextBoxAtTap(
            id = "tap",
            pageIndex = 2,
            xRel = 0.5f,
            yRel = 0.5f,
            style = style(),
            fontSizeNorm = 0.03f,
        )

        assertEquals(2, box.pageIndex)
        assertEquals("", box.text)
        assertEquals(0.3f, box.relativeBounds.left, 0.0001f)
        assertEquals(0.45f, box.relativeBounds.top, 0.0001f)
        assertEquals(0.7f, box.relativeBounds.right, 0.0001f)
        assertEquals(0.55f, box.relativeBounds.bottom, 0.0001f)
        assertEquals(Color.Red.toArgb(), box.color.toArgb())
        assertEquals(0.03f, box.fontSize, 0.0001f)
        assertTrue(box.isBold)
        assertTrue(box.isUnderline)
        assertTrue(box.paragraphs.isEmpty())
    }

    @Test
    fun `tap box clamps inside the page`() {
        val box = buildTextBoxAtTap(
            id = "corner",
            pageIndex = 0,
            xRel = 0.99f,
            yRel = 0.01f,
            style = style(),
            fontSizeNorm = 0.03f,
        )

        assertEquals(0.6f, box.relativeBounds.left, 0.0001f)
        assertEquals(1f, box.relativeBounds.right, 0.0001f)
        assertEquals(0f, box.relativeBounds.top, 0.0001f)
        assertEquals(0.1f, box.relativeBounds.bottom, 0.0001f)
    }

    @Test
    fun `font size norm follows the dock formula`() {
        // 18sp at density 2 on a 1080px-wide page with 0.5 aspect.
        val norm = pdfTextBoxFontSizeNorm(
            displayFontSizeSp = 18f,
            spToPx = { sp -> sp * 2f },
            pageRatio = 0.5f,
            containerWidthPx = 1080f,
        )
        assertEquals(36f / 2160f, norm, 0.0001f)
    }

    @Test
    fun `spec maps to a styled box with converter bounds`() {
        val spec = SharedPdfTextBoxSpec(
            pageIndex = 4,
            text = "• hello",
            paragraphs = listOf(
                SharedPdfRichParagraph(
                    alignment = SharedPdfRichTextAlign.CENTER,
                    listType = SharedPdfRichListType.BULLET,
                )
            ),
            colorArgb = Color.Blue.toArgb(),
            fontSizeNorm = 0.04f,
            isItalic = true,
            fontPath = "asset:fonts/lora.ttf",
            bounds = PdfPageBounds(0.1f, 0.08f, 0.9f, 0.2f),
        )
        val box = buildTextBoxFromSpec(spec, "spec-1")

        assertEquals(4, box.pageIndex)
        assertEquals("• hello", box.text)
        assertEquals(Color.Blue.toArgb(), box.color.toArgb())
        assertEquals(0.04f, box.fontSize, 0.0001f)
        assertTrue(box.isItalic)
        assertEquals("asset:fonts/lora.ttf", box.fontPath)
        assertNull(box.fontName)
        assertEquals(spec.paragraphs, box.paragraphs)
        assertEquals(0.1f, box.relativeBounds.left, 0.0001f)
        assertEquals(0.08f, box.relativeBounds.top, 0.0001f)
        assertEquals(0.9f, box.relativeBounds.right, 0.0001f)
        assertEquals(0.2f, box.relativeBounds.bottom, 0.0001f)
    }
}
