package com.aryan.reader.shared.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.SharedPdfAndroidHighlightColors
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedPdfAnnotationUiTest {
    @Test
    fun `text highlight annotations render with readable highlighter blending`() {
        val annotation = SharedPdfAnnotation(
            id = "highlight-1",
            pageIndex = 0,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            colorArgb = Color.Yellow.copy(alpha = 0.9f).toArgb()
        )

        val style = sharedPdfHighlightAnnotationOverlayStyle(annotation)

        assertEquals(BlendMode.Multiply, style.blendMode)
        assertEquals(SharedPdfAndroidHighlightColors.RenderAlpha, style.color.alpha)
    }

    @Test
    fun `text highlight annotations preserve lower custom opacity`() {
        val annotation = SharedPdfAnnotation(
            id = "highlight-1",
            pageIndex = 0,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            colorArgb = Color.Yellow.copy(alpha = 0.18f).toArgb()
        )

        val style = sharedPdfHighlightAnnotationOverlayStyle(annotation)

        assertEquals(0.18f, style.color.alpha, 0.005f)
    }

    @Test
    fun `interaction dock keeps reading modes before markup actions`() {
        assertEquals(
            listOf(
                SharedPdfInteractionDockItem.PAN,
                SharedPdfInteractionDockItem.SELECT_TEXT,
                SharedPdfInteractionDockItem.PEN,
                SharedPdfInteractionDockItem.HIGHLIGHTER,
                SharedPdfInteractionDockItem.TEXT_NOTE,
                SharedPdfInteractionDockItem.ERASER,
                SharedPdfInteractionDockItem.UNDO,
                SharedPdfInteractionDockItem.REDO,
                SharedPdfInteractionDockItem.CLEAR_PAGE
            ),
            sharedPdfInteractionDockItems()
        )
    }

    @Test
    fun `interaction dock only exposes available markup groups`() {
        assertEquals(
            listOf(
                SharedPdfInteractionDockItem.PAN,
                SharedPdfInteractionDockItem.SELECT_TEXT,
                SharedPdfInteractionDockItem.TEXT_NOTE,
                SharedPdfInteractionDockItem.UNDO,
                SharedPdfInteractionDockItem.REDO,
                SharedPdfInteractionDockItem.CLEAR_PAGE
            ),
            sharedPdfInteractionDockItems(tools = listOf(PdfInkTool.TEXT))
        )
    }

    @Test
    fun `tool settings palette matches highlighter colors by rgb`() {
        val paletteColor = Color(0xFFFFEB3B).copy(alpha = 0.55f).toArgb()
        val selectedColor = Color(0xFFFFEB3B).copy(alpha = 0.25f).toArgb()

        assertEquals(
            0,
            sharedPdfSettingsSelectedPaletteIndex(
                activePalette = listOf(paletteColor),
                selectedColor = selectedColor,
                matchRgbOnly = true
            )
        )
        assertEquals(
            -1,
            sharedPdfSettingsSelectedPaletteIndex(
                activePalette = listOf(paletteColor),
                selectedColor = selectedColor,
                matchRgbOnly = false
            )
        )
    }

    @Test
    fun `tool settings slider display percent clamps like android popup`() {
        val range = 0.01f..0.06f

        assertEquals(1, sharedPdfSettingsDisplayPercent(0.0f, range))
        assertEquals(50, sharedPdfSettingsDisplayPercent(0.035f, range))
        assertEquals(100, sharedPdfSettingsDisplayPercent(0.10f, range))
    }

    @Test
    fun `ink preview reveal progress clamps to animation range`() {
        assertEquals(0f, sharedPdfInkPreviewRevealProgress(-0.5f))
        assertEquals(0.45f, sharedPdfInkPreviewRevealProgress(0.45f))
        assertEquals(1f, sharedPdfInkPreviewRevealProgress(1.5f))
    }

    @Test
    fun `selection ring keeps its stroke inside the canvas`() {
        val strokeWidth = 4f
        val canvasMinDimension = 56f

        assertEquals(
            (canvasMinDimension - strokeWidth) / 2f,
            sharedPdfSelectionRingRadius(canvasMinDimension, strokeWidth)
        )
        // Outer edge of the stroke lands exactly on the canvas edge: not chipped, not inset.
        assertEquals(
            canvasMinDimension / 2f,
            sharedPdfSelectionRingRadius(canvasMinDimension, strokeWidth) + strokeWidth / 2f,
            0.0001f
        )
    }

    @Test
    fun `ink preview path has length to animate`() {
        val size = Size(132f, 300f)
        val start = Offset(
            size.width * SharedPdfPenIconInkStartXFraction,
            size.height * SharedPdfPenIconInkHeadroomFraction
        )
        for (isHighlighter in listOf(false, true)) {
            for (straight in listOf(false, true)) {
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    applySharedPdfInkPreview(
                        sharedPdfInkPreviewCommands(isHighlighter, straight).drop(1),
                        start,
                        size
                    )
                }
                val measure = PathMeasure()
                measure.setPath(path, false)
                assertTrue(
                    measure.length > 10f,
                    "ink preview path is degenerate (highlighter=$isHighlighter straight=$straight)"
                )
                // Mid-animation reveal must extract a segment, like drawInkPreview does.
                assertTrue(
                    measure.getSegment(0f, measure.length / 2f, Path(), true)
                )
            }
        }
    }

    @Test
    fun `ink preview flourish stays inside the icon canvas`() {
        // Representative stroke px for the fits check (drawInkPreview itself
        // uses Android-parity base*1000px widths via the ink pipeline).
        val canvases = listOf(44f to 100f, 44f to 150f, 88f to 200f, 132f to 300f, 200f to 120f)
        for ((width, height) in canvases) {
            val penExtents = sharedPdfInkPreviewCommands(isHighlighter = false, straight = false)
                .sharedPdfInkPreviewBounds()
            assertEquals(
                true,
                sharedPdfPreviewFitsCanvas(width, height, penExtents, 5f),
                "pen flourish clipped on ${width}x$height canvas"
            )
            for (straight in listOf(false, true)) {
                val highlighterExtents = sharedPdfInkPreviewCommands(isHighlighter = true, straight = straight)
                    .sharedPdfInkPreviewBounds()
                assertEquals(
                    true,
                    sharedPdfPreviewFitsCanvas(width, height, highlighterExtents, 16f),
                    "highlighter flourish clipped on ${width}x$height canvas (straight=$straight)"
                )
            }
        }
    }
}
