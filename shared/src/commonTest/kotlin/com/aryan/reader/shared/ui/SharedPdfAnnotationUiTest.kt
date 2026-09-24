package com.aryan.reader.shared.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.SharedPdfAndroidHighlightColors
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import kotlin.math.sqrt
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
                // PathMeasure/Path.getBounds are android.graphics stubs on
                // Android host tests (returnDefaultValues → length 0). Sample
                // the pure-Kotlin preview commands instead.
                val length = approximatePreviewPathLength(
                    sharedPdfInkPreviewCommands(isHighlighter, straight),
                    start,
                    size,
                )
                assertTrue(
                    length > 10f,
                    "ink preview path is degenerate (highlighter=$isHighlighter straight=$straight length=$length)"
                )
            }
        }
    }

    /**
     * Approximates path length by sampling each command's polyline/bezier —
     * mirrors what PathMeasure.length reports for the path drawInkPreview builds.
     */
    private fun approximatePreviewPathLength(
        commands: List<SharedPdfInkPreviewCommand>,
        start: Offset,
        size: Size,
    ): Float {
        var x = start.x
        var y = start.y
        var length = 0f
        fun lineTo(dx: Float, dy: Float) {
            val nx = start.x + dx * size.width
            val ny = start.y + dy * size.height
            length += sqrt((nx - x) * (nx - x) + (ny - y) * (ny - y))
            x = nx
            y = ny
        }
        fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, ex: Float, ey: Float) {
            val p0x = x
            val p0y = y
            val p1x = start.x + c1x * size.width
            val p1y = start.y + c1y * size.height
            val p2x = start.x + c2x * size.width
            val p2y = start.y + c2y * size.height
            val p3x = start.x + ex * size.width
            val p3y = start.y + ey * size.height
            val steps = 16
            var prevX = p0x
            var prevY = p0y
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                val mt = 1f - t
                val bx = mt * mt * mt * p0x + 3f * mt * mt * t * p1x + 3f * mt * t * t * p2x + t * t * t * p3x
                val by = mt * mt * mt * p0y + 3f * mt * mt * t * p1y + 3f * mt * t * t * p2y + t * t * t * p3y
                length += sqrt((bx - prevX) * (bx - prevX) + (by - prevY) * (by - prevY))
                prevX = bx
                prevY = by
            }
            x = p3x
            y = p3y
        }
        // drawInkPreview starts at [start] and drops the initial MoveTo.
        commands.drop(1).forEach { command ->
            when (command) {
                is SharedPdfInkPreviewCommand.MoveTo -> {
                    x = start.x + command.dxWidthFraction * size.width
                    y = start.y + command.dyHeightFraction * size.height
                }
                is SharedPdfInkPreviewCommand.LineTo ->
                    lineTo(command.dxWidthFraction, command.dyHeightFraction)
                is SharedPdfInkPreviewCommand.CubicTo ->
                    cubicTo(
                        command.control1DxWidthFraction,
                        command.control1DyHeightFraction,
                        command.control2DxWidthFraction,
                        command.control2DyHeightFraction,
                        command.dxWidthFraction,
                        command.dyHeightFraction,
                    )
            }
        }
        return length
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
