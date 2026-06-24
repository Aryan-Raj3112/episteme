package com.aryan.reader.pdf

import android.graphics.RectF
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.VirtualPage
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.pdf.SharedPdfAnnotationComment
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfiumAnnotationExporterTest {

    @Test
    fun `buildPayload flattens ink annotations and skips unsupported ink and text annotations`() {
        val payload = PdfiumAnnotationExporter.buildPayload(
            inkAnnotations = mapOf(
                2 to listOf(
                    PdfAnnotation(
                        type = AnnotationType.INK,
                        inkType = InkType.PEN,
                        pageIndex = 99,
                        points = listOf(PdfPoint(0.1f, 0.2f), PdfPoint(0.3f, 0.4f)),
                        color = Color(0xFF336699),
                        strokeWidth = 0.0125f,
                        id = "ink-1"
                    ),
                    PdfAnnotation(
                        type = AnnotationType.INK,
                        inkType = InkType.ERASER,
                        pageIndex = 2,
                        points = listOf(PdfPoint(0.5f, 0.6f), PdfPoint(0.7f, 0.8f)),
                        color = Color.Black,
                        strokeWidth = 0.1f
                    ),
                    PdfAnnotation(
                        type = AnnotationType.TEXT,
                        inkType = InkType.PEN,
                        pageIndex = 2,
                        points = listOf(PdfPoint(0.2f, 0.3f), PdfPoint(0.4f, 0.5f)),
                        color = Color.Black,
                        strokeWidth = 0.1f
                    )
                )
            ),
            textBoxes = emptyList(),
            highlights = emptyList()
        )

        assertArrayEquals(intArrayOf(2), payload.inkPageIndices)
        assertArrayEquals(intArrayOf(InkType.PEN.ordinal), payload.inkTypes)
        assertArrayEquals(intArrayOf(Color(0xFF336699).toArgb()), payload.inkColors)
        assertArrayEquals(floatArrayOf(0.0125f), payload.inkStrokeWidths, 0.0001f)
        assertArrayEquals(intArrayOf(0), payload.inkPointOffsets)
        assertArrayEquals(intArrayOf(2), payload.inkPointCounts)
        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), payload.inkPoints, 0.0001f)
        assertEquals("", payload.inkContents.single())
        assertEquals("ink-1", payload.inkNames.single())
    }

    @Test
    fun `buildPayload trims chisel highlighter endpoints for pdf ink caps`() {
        val payload = PdfiumAnnotationExporter.buildPayload(
            inkAnnotations = mapOf(
                0 to listOf(
                    PdfAnnotation(
                        type = AnnotationType.INK,
                        inkType = InkType.HIGHLIGHTER,
                        pageIndex = 0,
                        points = listOf(PdfPoint(0.1f, 0.2f), PdfPoint(0.9f, 0.2f)),
                        color = Color(0x8CFFEB3B.toInt()),
                        strokeWidth = 0.1f,
                        id = "highlighter"
                    )
                )
            ),
            textBoxes = emptyList(),
            highlights = emptyList(),
            pageSizes = listOf(PdfiumPageSize(width = 100, height = 100))
        )

        assertArrayEquals(floatArrayOf(0.165f, 0.2f, 0.835f, 0.2f), payload.inkPoints, 0.0001f)
    }

    @Test
    fun `buildPayload normalizes highlight rects and preserves names and content notes`() {
        val payload = PdfiumAnnotationExporter.buildPayload(
            inkAnnotations = emptyMap(),
            textBoxes = emptyList(),
            highlights = listOf(
                PdfUserHighlight(
                    id = "highlight-1",
                    pageIndex = 1,
                    bounds = listOf(RectF(10f, 90f, 40f, 80f), RectF(50f, 70f, 60f, 65f)),
                    color = PdfHighlightColor.BLUE,
                    text = "Selected text",
                    range = 0 to 13,
                    note = "Important",
                    comments = listOf(
                        SharedPdfAnnotationComment(
                            id = "comment-1",
                            author = "Ada",
                            contents = "First comment",
                            createdAt = 1_700_000_000_000L,
                            modifiedAt = 1_700_000_010_000L
                        ),
                        SharedPdfAnnotationComment(
                            id = "comment-2",
                            author = "Bea",
                            contents = "Second top-level comment",
                            createdAt = 1_700_000_020_000L
                        )
                    )
                )
            ),
            pageSizes = listOf(
                PdfiumPageSize(width = 612, height = 792),
                PdfiumPageSize(width = 100, height = 100)
            )
        )

        assertArrayEquals(intArrayOf(1), payload.highlightPageIndices)
        assertArrayEquals(intArrayOf(PdfHighlightColor.BLUE.color.toArgb()), payload.highlightColors)
        assertArrayEquals(intArrayOf(0), payload.highlightRectOffsets)
        assertArrayEquals(intArrayOf(2), payload.highlightRectCounts)
        assertArrayEquals(
            floatArrayOf(0.1f, 0.1f, 0.4f, 0.2f, 0.5f, 0.3f, 0.6f, 0.35f),
            payload.highlightRects,
            0.0001f
        )
        assertEquals("highlight-1", payload.highlightNames.single())
        assertEquals("Important", payload.highlightContents.single())
        assertArrayEquals(intArrayOf(0), payload.highlightCommentOffsets)
        assertArrayEquals(intArrayOf(1), payload.highlightCommentCounts)
        assertArrayEquals(intArrayOf(-1), payload.highlightCommentParentIndices)
        assertEquals(listOf("highlight-1_comments"), payload.highlightCommentNames.toList())
        assertEquals(listOf("Ada"), payload.highlightCommentAuthors.toList())
        assertEquals(
            listOf("Ada:\nFirst comment\n\nBea:\nSecond top-level comment"),
            payload.highlightCommentContents.toList()
        )
        assertEquals("D:20231114221320Z", payload.highlightCommentCreatedDates[0])
        assertEquals("D:20231114221340Z", payload.highlightCommentModifiedDates[0])
    }

    @Test
    fun `buildPayload exports custom highlight palette color instead of legacy slot default`() {
        val customColor = Color(0xFF7B1FA2)
        val payload = PdfiumAnnotationExporter.buildPayload(
            inkAnnotations = emptyMap(),
            textBoxes = emptyList(),
            highlights = listOf(
                PdfUserHighlight(
                    id = "custom-highlight",
                    pageIndex = 0,
                    bounds = listOf(RectF(10f, 90f, 40f, 80f)),
                    color = PdfHighlightColor.YELLOW,
                    text = "Selected text",
                    range = 0 to 13
                )
            ),
            customHighlightColors = mapOf(PdfHighlightColor.YELLOW to customColor),
            pageSizes = listOf(PdfiumPageSize(width = 100, height = 100))
        )

        assertArrayEquals(intArrayOf(customColor.toArgb()), payload.highlightColors)
    }

    @Test
    fun `buildPayload exports persisted highlight argb when custom palette is unavailable`() {
        val persistedColor = Color(0xFF00695C)
        val payload = PdfiumAnnotationExporter.buildPayload(
            inkAnnotations = emptyMap(),
            textBoxes = emptyList(),
            highlights = listOf(
                PdfUserHighlight(
                    id = "persisted-highlight",
                    pageIndex = 0,
                    bounds = listOf(RectF(10f, 90f, 40f, 80f)),
                    color = PdfHighlightColor.YELLOW,
                    colorArgb = persistedColor.toArgb(),
                    text = "Selected text",
                    range = 0 to 13
                )
            ),
            pageSizes = listOf(PdfiumPageSize(width = 100, height = 100))
        )

        assertArrayEquals(intArrayOf(persistedColor.toArgb()), payload.highlightColors)
    }

    @Test
    fun `buildPayload maps highlight styles to native PDF text markup subtypes`() {
        val highlights = listOf(
            HighlightStyle.BACKGROUND,
            HighlightStyle.UNDERLINE,
            HighlightStyle.WAVY_UNDERLINE,
            HighlightStyle.STRIKETHROUGH
        ).mapIndexed { index, style ->
            PdfUserHighlight(
                id = "highlight-$index",
                pageIndex = 0,
                bounds = listOf(RectF(10f, 90f, 40f, 80f)),
                color = PdfHighlightColor.YELLOW,
                style = style,
                text = "Selected text",
                range = 0 to 13
            )
        }

        val payload = PdfiumAnnotationExporter.buildPayload(
            inkAnnotations = emptyMap(),
            textBoxes = emptyList(),
            highlights = highlights,
            pageSizes = listOf(PdfiumPageSize(width = 100, height = 100))
        )

        assertArrayEquals(
            intArrayOf(
                PDFIUM_ANNOT_HIGHLIGHT,
                PDFIUM_ANNOT_UNDERLINE,
                PDFIUM_ANNOT_SQUIGGLY,
                PDFIUM_ANNOT_STRIKEOUT
            ),
            payload.highlightSubtypes
        )
    }

    @Test
    fun `buildPayload flattens raster text overlays and leaves native text empty`() {
        val pixels = intArrayOf(
            0x00000000,
            0xFF112233.toInt(),
            0x80123456.toInt(),
            0x00000000
        )
        val payload = PdfiumAnnotationExporter.buildPayload(
            inkAnnotations = emptyMap(),
            textBoxes = emptyList(),
            highlights = emptyList(),
            rasterOverlays = listOf(
                PdfiumRasterOverlay(
                    pageIndex = 3,
                    left = 0.1f,
                    top = 0.2f,
                    right = 0.8f,
                    bottom = 0.4f,
                    width = 2,
                    height = 2,
                    pixels = pixels
                )
            )
        )

        assertTrue(payload.textPageIndices.isEmpty())
        assertTrue(payload.textValues.isEmpty())
        assertArrayEquals(intArrayOf(3), payload.rasterPageIndices)
        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.8f, 0.4f), payload.rasterBounds, 0.0001f)
        assertArrayEquals(intArrayOf(2), payload.rasterWidths)
        assertArrayEquals(intArrayOf(2), payload.rasterHeights)
        assertArrayEquals(intArrayOf(0), payload.rasterPixelOffsets)
        assertArrayEquals(pixels, payload.rasterPixels)
        assertTrue(payload.hasAnnotations())
    }

    @Test
    fun `buildPayload omits blank text boxes and empty highlight bounds`() {
        val payload = PdfiumAnnotationExporter.buildPayload(
            inkAnnotations = emptyMap(),
            textBoxes = listOf(
                PdfTextBox(
                    id = "blank-box",
                    pageIndex = 0,
                    relativeBounds = Rect(0.1f, 0.2f, 0.3f, 0.4f),
                    text = "   ",
                    color = Color.Black,
                    backgroundColor = Color.Transparent,
                    fontSize = 12f
                )
            ),
            highlights = listOf(
                PdfUserHighlight(
                    pageIndex = 0,
                    bounds = emptyList(),
                    color = PdfHighlightColor.YELLOW,
                    text = "Selected",
                    range = 0 to 8
                )
            )
        )

        assertFalse(payload.hasAnnotations())
        assertTrue(payload.textValues.isEmpty())
        assertTrue(payload.rasterPixels.isEmpty())
        assertTrue(payload.highlightContents.isEmpty())
    }

    @Test
    fun `supportsOriginalPageOrder rejects reordered or blank virtual layouts`() {
        assertTrue(PdfiumAnnotationExporter.supportsOriginalPageOrder(null))
        assertTrue(
            PdfiumAnnotationExporter.supportsOriginalPageOrder(
                listOf(VirtualPage.PdfPage(0), VirtualPage.PdfPage(1))
            )
        )
        assertFalse(PdfiumAnnotationExporter.supportsOriginalPageOrder(listOf(VirtualPage.PdfPage(1))))
        assertFalse(
            PdfiumAnnotationExporter.supportsOriginalPageOrder(
                listOf(VirtualPage.PdfPage(0), VirtualPage.BlankPage("blank", 300, 400))
            )
        )
    }
}
