package com.aryan.reader.pdf

import android.graphics.RectF
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.pdf.data.AnnotationSerializer
import com.aryan.reader.pdf.data.HighlightSerializer
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.TextBoxSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfReaderSerializerTest {

    @Test
    fun `AnnotationSerializer round trips multi page annotations with precision and style`() {
        val annotations = mapOf(
            0 to listOf(
                PdfAnnotation(
                    type = AnnotationType.INK,
                    inkType = InkType.FOUNTAIN_PEN,
                    pageIndex = 0,
                    points = listOf(
                        PdfPoint(0.123456f, 0.987654f, 10L),
                        PdfPoint(0.2f, 0.3f, 11L)
                    ),
                    color = Color(0xFF336699),
                    strokeWidth = 0.0125f,
                    id = "ink-1",
                    note = "Desktop note"
                )
            ),
            2 to listOf(
                PdfAnnotation(
                    type = AnnotationType.INK,
                    inkType = InkType.HIGHLIGHTER_ROUND,
                    pageIndex = 2,
                    points = emptyList(),
                    color = Color(0x8CFFEB3B),
                    strokeWidth = 0.035f
                )
            )
        )

        val decoded = AnnotationSerializer.fromJson(AnnotationSerializer.toJson(annotations))

        assertEquals(setOf(0, 2), decoded.keys)
        val first = decoded.getValue(0).single()
        assertEquals(AnnotationType.INK, first.type)
        assertEquals("ink-1", first.id)
        assertEquals("Desktop note", first.note)
        assertEquals(InkType.FOUNTAIN_PEN, first.inkType)
        assertEquals(Color(0xFF336699).toArgb(), first.color.toArgb())
        assertEquals(0.0125f, first.strokeWidth, 0.00001f)
        assertEquals(0.12346f, first.points[0].x, 0.00001f)
        assertEquals(0.98765f, first.points[0].y, 0.00001f)
        assertEquals(10L, first.points[0].timestamp)
        assertEquals(InkType.HIGHLIGHTER_ROUND, decoded.getValue(2).single().inkType)
    }

    @Test
    fun `AnnotationSerializer supports legacy type field and malformed input fallback`() {
        val legacyJson = """
            [
              {
                "pageIndex": 4,
                "annotationType": "BROKEN",
                "type": "PENCIL",
                "color": -65536,
                "strokeWidth": 0.5,
                "points": [{ "x": 0.1, "y": 0.2 }]
              }
            ]
        """.trimIndent()

        val decoded = AnnotationSerializer.fromJson(legacyJson).getValue(4).single()

        assertEquals(AnnotationType.INK, decoded.type)
        assertEquals(InkType.PENCIL, decoded.inkType)
        assertTrue(decoded.id.isNotBlank())
        assertEquals(0L, decoded.points.single().timestamp)
        assertTrue(AnnotationSerializer.fromJson("not json").isEmpty())
        assertTrue(AnnotationSerializer.fromJson("").isEmpty())
    }

    @Test
    fun `TextBoxSerializer round trips bounds text styling and optional font data`() {
        val boxes = listOf(
            PdfTextBox(
                id = "box-1",
                pageIndex = 3,
                relativeBounds = Rect(0.1f, 0.2f, 0.8f, 0.4f),
                text = "Hello annotations",
                color = Color(0xFF112233),
                backgroundColor = Color(0x66123456),
                fontSize = 18f,
                isBold = true,
                isItalic = true,
                isUnderline = true,
                isStrikeThrough = true,
                fontPath = "/fonts/test.ttf",
                fontName = "Test Font"
            )
        )

        val decoded = TextBoxSerializer.fromJson(TextBoxSerializer.toJson(boxes)).single()

        assertEquals("box-1", decoded.id)
        assertEquals(3, decoded.pageIndex)
        assertEquals(Rect(0.1f, 0.2f, 0.8f, 0.4f), decoded.relativeBounds)
        assertEquals("Hello annotations", decoded.text)
        assertEquals(Color(0xFF112233).toArgb(), decoded.color.toArgb())
        assertEquals(Color(0x66123456).toArgb(), decoded.backgroundColor.toArgb())
        assertEquals(18f, decoded.fontSize, 0.0001f)
        assertTrue(decoded.isBold)
        assertTrue(decoded.isItalic)
        assertTrue(decoded.isUnderline)
        assertTrue(decoded.isStrikeThrough)
        assertEquals("/fonts/test.ttf", decoded.fontPath)
        assertEquals("Test Font", decoded.fontName)
    }

    @Test
    fun `TextBoxSerializer defaults missing optional style fields and rejects malformed json`() {
        val legacyJson = """
            [
              {
                "id": "legacy-box",
                "pageIndex": 1,
                "text": "Legacy",
                "color": -16777216,
                "backgroundColor": 0,
                "fontSize": 14.0,
                "bounds": { "left": 0.0, "top": 0.1, "right": 0.8, "bottom": 0.2 }
              }
            ]
        """.trimIndent()

        val decoded = TextBoxSerializer.fromJson(legacyJson).single()

        assertEquals("legacy-box", decoded.id)
        assertEquals("Legacy", decoded.text)
        assertEquals(Rect(0f, 0.1f, 0.8f, 0.2f), decoded.relativeBounds)
        assertFalse(decoded.isBold)
        assertFalse(decoded.isItalic)
        assertFalse(decoded.isUnderline)
        assertFalse(decoded.isStrikeThrough)
        assertNull(decoded.fontPath)
        assertNull(decoded.fontName)
        assertTrue(TextBoxSerializer.fromJson("broken").isEmpty())
    }

    @Test
    fun `HighlightSerializer round trips highlights and falls back on invalid color`() {
        val highlights = listOf(
            PdfUserHighlight(
                id = "highlight-1",
                pageIndex = 5,
                bounds = listOf(RectF(0f, 0f, 1f, 1f)),
                color = PdfHighlightColor.BLUE,
                text = "Selected text",
                range = 7 to 20,
                note = "Important"
            )
        )

        val decoded = HighlightSerializer.fromJson(HighlightSerializer.toJson(highlights)).single()

        assertEquals("highlight-1", decoded.id)
        assertEquals(5, decoded.pageIndex)
        assertEquals(PdfHighlightColor.BLUE, decoded.color)
        assertEquals("Selected text", decoded.text)
        assertEquals(7 to 20, decoded.range)
        assertEquals("Important", decoded.note)
        assertEquals(1, decoded.bounds.size)
        assertRectFEquals(RectF(0f, 0f, 1f, 1f), decoded.bounds.single())

        val invalidColor = """[{"pageIndex":0,"bounds":[],"color":"NOPE","text":"x"}]"""
        assertEquals(PdfHighlightColor.YELLOW, HighlightSerializer.fromJson(invalidColor).single().color)
        assertTrue(HighlightSerializer.fromJson("bad").isEmpty())
    }

    @Test
    fun `HighlightSerializer omits blank notes and defaults missing legacy values`() {
        val json = HighlightSerializer.toJson(
            listOf(
                PdfUserHighlight(
                    id = "blank-note",
                    pageIndex = 0,
                    bounds = emptyList(),
                    color = PdfHighlightColor.RED,
                    text = "Text",
                    range = 2 to 4,
                    note = "   "
                )
            )
        )

        val decodedBlankNote = HighlightSerializer.fromJson(json).single()
        assertNull(decodedBlankNote.note)

        val legacyJson = """[{"pageIndex":2,"bounds":[],"color":"GREEN"}]"""
        val decodedLegacy = HighlightSerializer.fromJson(legacyJson).single()
        assertTrue(decodedLegacy.id.isNotBlank())
        assertEquals("", decodedLegacy.text)
        assertEquals(0 to 0, decodedLegacy.range)
    }

    private fun assertRectFEquals(expected: RectF, actual: RectF) {
        assertEquals(expected.left, actual.left, 0.0001f)
        assertEquals(expected.top, actual.top, 0.0001f)
        assertEquals(expected.right, actual.right, 0.0001f)
        assertEquals(expected.bottom, actual.bottom, 0.0001f)
    }
}
