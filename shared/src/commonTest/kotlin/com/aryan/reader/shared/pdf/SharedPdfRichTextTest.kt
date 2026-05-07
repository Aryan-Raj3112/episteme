package com.aryan.reader.shared.pdf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPdfRichTextTest {

    @Test
    fun `mapper clips global rich spans into requested local range`() {
        val document = SharedPdfRichDocument(
            text = "0123456789",
            spans = listOf(
                SharedPdfRichSpan(
                    start = 2,
                    end = 6,
                    color = Color.Red.toArgb(),
                    backgroundColor = Color.Yellow.toArgb(),
                    fontSizeNorm = 0.02f,
                    isBold = true,
                    isItalic = true,
                    isUnderline = true,
                    isStrikethrough = true,
                    fontPath = "asset:fonts/lora.ttf"
                )
            )
        )

        val annotated = SharedPdfRichTextMapper.toAnnotatedString(
            document = document,
            pageHeightPx = 1_000f,
            rangeStart = 4,
            rangeEnd = 8
        )

        assertEquals("4567", annotated.text)
        val range = annotated.spanStyles.single()
        assertEquals(0, range.start)
        assertEquals(2, range.end)
        assertEquals(Color.Red, range.item.color)
        assertEquals(Color.Yellow, range.item.background)
        assertEquals(20.sp, range.item.fontSize)
        assertEquals(FontWeight.Bold, range.item.fontWeight)
        assertEquals(FontStyle.Italic, range.item.fontStyle)
        assertTrue(range.item.textDecoration!!.contains(TextDecoration.Underline))
        assertTrue(range.item.textDecoration!!.contains(TextDecoration.LineThrough))

        val roundTrip = SharedPdfRichTextMapper.fromAnnotatedString(annotated, pageHeightPx = 1_000f)
        assertEquals("4567", roundTrip.text)
        assertEquals("asset:fonts/lora.ttf", roundTrip.spans.single().fontPath)
    }

    @Test
    fun `mapper fromAnnotatedString splits overlapping styles and preserves page breaks`() {
        val text = "Hello${SHARED_PDF_PAGE_BREAK_CHAR}World"
        val annotated = buildAnnotatedString {
            append(text)
            addStyle(
                SpanStyle(
                    color = Color.Black,
                    background = Color.Transparent,
                    fontSize = 20.sp
                ),
                start = 0,
                end = text.length
            )
            addStyle(
                SpanStyle(
                    color = Color.Magenta,
                    background = Color.Cyan,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    textDecoration = TextDecoration.combine(
                        listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                    )
                ),
                start = 0,
                end = 5
            )
        }

        val document = SharedPdfRichTextMapper.fromAnnotatedString(annotated, pageHeightPx = 1_000f)

        assertEquals(text, document.text)
        assertEquals(2, document.spans.size)
        val first = document.spans[0]
        assertEquals(0, first.start)
        assertEquals(5, first.end)
        assertEquals(Color.Magenta.toArgb(), first.color)
        assertEquals(Color.Cyan.toArgb(), first.backgroundColor)
        assertEquals(0.024f, first.fontSizeNorm, 0.0001f)
        assertTrue(first.isBold)
        assertTrue(first.isItalic)
        assertTrue(first.isUnderline)
        assertTrue(first.isStrikethrough)
        val second = document.spans[1]
        assertEquals(5, second.start)
        assertEquals(text.length, second.end)
        assertEquals(Color.Black.toArgb(), second.color)
        assertFalse(second.isBold)
    }

    @Test
    fun `serializer uses android rich text sidecar schema`() {
        val document = SharedPdfRichDocument(
            text = "Saved rich text",
            spans = listOf(
                SharedPdfRichSpan(
                    start = 0,
                    end = 5,
                    color = Color.Red.toArgb(),
                    backgroundColor = Color.Transparent.toArgb(),
                    fontSizeNorm = 0.018f,
                    isBold = true,
                    isItalic = false,
                    isUnderline = true,
                    isStrikethrough = false,
                    fontPath = "asset:fonts/lora.ttf"
                )
            )
        )

        val encoded = SharedPdfRichTextSerializer.encode(document)
        val decoded = SharedPdfRichTextSerializer.decode(encoded)

        assertTrue(encoded.contains("\"s\""))
        assertTrue(encoded.contains("\"fp\""))
        assertEquals(document, decoded)
    }

    @Test
    fun `serializer returns empty document for blank and corrupt payloads`() {
        assertEquals(SharedPdfRichDocument(), SharedPdfRichTextSerializer.decode(""))
        assertEquals(SharedPdfRichDocument(), SharedPdfRichTextSerializer.decode("{not json"))
        assertEquals(
            SharedPdfRichDocument("", emptyList()),
            SharedPdfRichTextMapper.fromAnnotatedString(AnnotatedString(""), pageHeightPx = 1_000f)
        )
    }
}
