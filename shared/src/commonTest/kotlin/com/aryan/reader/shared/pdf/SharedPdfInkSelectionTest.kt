package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parity tests for the shared ink-selection model (benchmark: Android
 * `PdfAnnotationSelectionTest`). Covers selection math plus the undoable
 * reducer actions the selection UI commits through.
 */
class SharedPdfInkSelectionTest {

    private fun pt(x: Float, y: Float) = PdfPagePoint(x, y, 0L)

    private fun ink(
        id: String,
        pageIndex: Int = 0,
        points: List<PdfPagePoint>,
        tool: PdfInkTool = PdfInkTool.PEN,
        strokeWidth: Float = 0.008f,
        colorArgb: Int = 0xFFFF0000.toInt(),
    ) = SharedPdfAnnotation(
        id = id,
        pageIndex = pageIndex,
        kind = PdfAnnotationKind.INK,
        tool = tool,
        points = points,
        colorArgb = colorArgb,
        strokeWidth = strokeWidth,
    )

    private fun square(id: String) = ink(
        id = id,
        points = listOf(pt(0.2f, 0.2f), pt(0.4f, 0.2f), pt(0.4f, 0.4f), pt(0.2f, 0.4f)),
    )

    @Test
    fun boundsOfEmptySelectionIsNull() {
        assertNull(sharedPdfSelectionBoundsOf(ink("a", points = emptyList())))
        val bounds = sharedPdfSelectionBoundsOf(square("a"))
        assertNotNull(bounds)
        assertEquals(0.2f, bounds.left)
        assertEquals(0.2f, bounds.top)
        assertEquals(0.4f, bounds.right)
        assertEquals(0.4f, bounds.bottom)
    }

    @Test
    fun unionBoundsSpanAllSelected() {
        val union = sharedPdfSelectionUnionBounds(
            listOf(
                square("a"),
                ink("b", points = listOf(pt(0.7f, 0.7f), pt(0.9f, 0.9f))),
            )
        )
        assertNotNull(union)
        assertEquals(0.2f, union.left)
        assertEquals(0.2f, union.top)
        assertEquals(0.9f, union.right)
        assertEquals(0.9f, union.bottom)
        assertNull(sharedPdfSelectionUnionBounds(emptyList()))
    }

    @Test
    fun moveTranslatesAndClampsToPage() {
        val moved = square("a").movedSharedSelectionBy(0.1f, -0.1f)
        assertEquals(0.3f, moved.points.first().x, 1e-5f)
        assertEquals(0.1f, moved.points.first().y, 1e-5f)
        // Raw point moves clamp per point (over-drag shaping is owned by
        // applySharedPdfSelectionTransform's delta clamp, tested below).
        val clamped = square("a").movedSharedSelectionBy(0.9f, 0.9f)
        assertEquals(1f, clamped.points.maxOf { it.x }, 1e-5f)
    }

    @Test
    fun uniformScaleScalesWidthWithinToolRange() {
        val scaled = square("a").scaledSharedSelectionAround(0.2f, 0.2f, 2f)
        assertEquals(0.6f, scaled.points[1].x, 1e-5f)
        // 0.008 * 2 = 0.016 exceeds the pen max 0.015: clamped.
        assertEquals(0.015f, scaled.strokeWidth, 1e-5f)
    }

    @Test
    fun nonUniformScaleUsesGeometricMeanWidth() {
        val scaled = square("a").scaledSharedSelectionAroundXY(0.2f, 0.2f, 4f, 1f)
        assertEquals(1f, scaled.points[1].x, 1e-5f)
        assertEquals(0.2f, scaled.points[1].y, 1e-5f)
        // sqrt(4 * 1) = 2x width: 0.008 * 2 clamped to 0.015.
        assertEquals(0.015f, scaled.strokeWidth, 1e-5f)
    }

    @Test
    fun highlighterWidthRangeIsWider() {
        val range = sharedPdfSelectionStrokeWidthRangeFor(PdfInkTool.HIGHLIGHTER)
        assertEquals(0.01f, range.start)
        assertEquals(0.06f, range.endInclusive)
    }

    @Test
    fun applyMoveStopsAtPageEdgeWithoutSmushing() {
        val annotations = listOf(square("a"))
        val moved = applySharedPdfSelectionTransform(
            annotations, setOf("a"),
            SharedPdfSelectionTransform.Move(0.9f, 0f), 1f,
        )
        // Union right edge lands exactly on 1, width preserved.
        assertEquals(1f, moved.first().points.maxOf { it.x }, 1e-4f)
        assertEquals(0.2f, moved.first().points.maxOf { it.x } - moved.first().points.minOf { it.x }, 1e-4f)
    }

    @Test
    fun applyScaleIsCappedAtPageEdge() {
        val annotations = listOf(square("a"))
        val scaled = applySharedPdfSelectionTransform(
            annotations, setOf("a"),
            SharedPdfSelectionTransform.Scale(0.2f, 0.2f, 10f), 1f,
        )
        assertTrue(scaled.first().points.all { it.x <= 1f && it.y <= 1f })
        assertEquals(1f, scaled.first().points.maxOf { it.x }, 1e-4f)
    }

    @Test
    fun applyRotateQuarterTurn() {
        val line = ink("a", points = listOf(pt(0.4f, 0.5f), pt(0.6f, 0.5f)))
        val rotated = applySharedPdfSelectionTransform(
            listOf(line), setOf("a"),
            SharedPdfSelectionTransform.Rotate(0.5f, 0.5f, 90f), 1f,
        )
        val points = rotated.first().points
        assertEquals(0.5f, points[0].x, 1e-4f)
        assertEquals(0.4f, points[0].y, 1e-4f)
        assertEquals(0.5f, points[1].x, 1e-4f)
        assertEquals(0.6f, points[1].y, 1e-4f)
    }

    @Test
    fun topmostHitWinsAndHighlightersUseBounds() {
        val bottom = square("a")
        val top = square("b")
        val hit = findSharedPdfTopmostSelectionHit(
            listOf(bottom, top), 0.3f, 0.21f, pageWidthPx = 1000f, pageAspectRatio = 1f,
        )
        assertEquals("b", hit?.id)
        val highlighter = ink(
            "h",
            points = listOf(pt(0.1f, 0.1f), pt(0.9f, 0.1f)),
            tool = PdfInkTool.HIGHLIGHTER,
            strokeWidth = 0.035f,
        )
        // Tap inside the highlighter band but off the center line still hits.
        val bandHit = findSharedPdfTopmostSelectionHit(
            listOf(highlighter), 0.5f, 0.12f, pageWidthPx = 1000f, pageAspectRatio = 1f,
        )
        assertEquals("h", bandHit?.id)
        assertNull(
            findSharedPdfTopmostSelectionHit(
                listOf(bottom), 0.8f, 0.8f, pageWidthPx = 1000f, pageAspectRatio = 1f,
            )
        )
    }

    @Test
    fun lassoTouchRuleSelectsTouchedStrokesOnly() {
        val touched = square("t")
        val far = ink("f", points = listOf(pt(0.8f, 0.8f), pt(0.9f, 0.8f)))
        // Lasso line crosses the square's bottom edge.
        val crossing = listOf(pt(0.1f, 0.2f), pt(0.3f, 0.2f))
        assertEquals(
            setOf("t"),
            findSharedPdfLassoSelectionHits(listOf(touched, far), crossing, 0.02f),
        )
        // Enclosing without touching selects nothing.
        val enclosing = listOf(pt(0.05f, 0.05f), pt(0.95f, 0.05f), pt(0.95f, 0.95f), pt(0.05f, 0.95f))
        assertTrue(
            findSharedPdfLassoSelectionHits(listOf(touched, far), enclosing, 0.0001f).isEmpty()
        )
        assertTrue(findSharedPdfLassoSelectionHits(listOf(touched), listOf(pt(0.1f, 0.1f))).isEmpty())
    }

    @Test
    fun nineHandlesWithRotateAboveTop() {
        val bounds = PdfPageBounds(0.2f, 0.2f, 0.4f, 0.4f)
        val handles = sharedPdfSelectionHandlePositions(bounds)
        assertEquals(9, handles.size)
        assertEquals(PdfPagePoint(0.2f, 0.2f, 0L), handles.getValue(SharedPdfSelectionHandle.TOP_LEFT))
        assertEquals(PdfPagePoint(0.3f, 0.2f, 0L), handles.getValue(SharedPdfSelectionHandle.TOP_MIDDLE))
        assertEquals(PdfPagePoint(0.4f, 0.3f, 0L), handles.getValue(SharedPdfSelectionHandle.RIGHT_MIDDLE))
        val rotate = handles.getValue(SharedPdfSelectionHandle.ROTATE)
        assertEquals(0.3f, rotate.x, 1e-5f)
        assertTrue(rotate.y < bounds.top)
    }

    @Test
    fun pivotsAreOppositeHandles() {
        val bounds = PdfPageBounds(0.2f, 0.2f, 0.4f, 0.4f)
        assertEquals(
            PdfPagePoint(0.4f, 0.4f, 0L),
            sharedPdfPivotForHandle(SharedPdfSelectionHandle.TOP_LEFT, bounds),
        )
        assertEquals(
            PdfPagePoint(0.3f, 0.4f, 0L),
            sharedPdfPivotForHandle(SharedPdfSelectionHandle.TOP_MIDDLE, bounds),
        )
        assertEquals(
            PdfPagePoint(0.3f, 0.3f, 0L),
            sharedPdfPivotForHandle(SharedPdfSelectionHandle.ROTATE, bounds),
        )
    }

    @Test
    fun edgeDragStretchesOneAxis() {
        val pivot = PdfPagePoint(0.4f, 0.3f, 0L)
        val (scaleX, scaleY) = sharedPdfScaleXYForEdgeDrag(
            SharedPdfSelectionHandle.RIGHT_MIDDLE,
            pivot,
            PdfPagePoint(0.2f, 0.3f, 0L),
            PdfPagePoint(0.1f, 0.3f, 0L),
        )
        assertEquals(1.5f, scaleX, 1e-5f)
        assertEquals(1f, scaleY, 1e-5f)
        // Degenerate span holds at 1 instead of exploding.
        val (degX, degY) = sharedPdfScaleXYForEdgeDrag(
            SharedPdfSelectionHandle.RIGHT_MIDDLE,
            pivot,
            PdfPagePoint(0.4f, 0.3f, 0L),
            PdfPagePoint(0.1f, 0.3f, 0L),
        )
        assertEquals(1f, degX, 1e-5f)
        assertEquals(1f, degY, 1e-5f)
    }

    @Test
    fun rotationSnapsToCardinalsWithinFiveDegrees() {
        // ~87-degree drag settles on 90.
        assertEquals(
            90f,
            sharedPdfRotationForDrag(
                centerX = 0.5f, centerY = 0.5f,
                startNorm = PdfPagePoint(0.6f, 0.5f, 0L),
                currentNorm = PdfPagePoint(0.5052f, 0.5999f, 0L),
                pageAspectRatio = 1f,
            ),
            0.5f,
        )
        // 80 degrees is outside the window and stays free.
        assertEquals(
            80f,
            sharedPdfRotationForDrag(
                centerX = 0.5f, centerY = 0.5f,
                startNorm = PdfPagePoint(0.6f, 0.5f, 0L),
                currentNorm = PdfPagePoint(0.5174f, 0.5985f, 0L),
                pageAspectRatio = 1f,
            ),
            0.5f,
        )
    }

    @Test
    fun rotationDisplayNormalizesTo0To359() {
        assertEquals(48, sharedPdfNormalizeRotationDisplay(48.4f))
        assertEquals(270, sharedPdfNormalizeRotationDisplay(-90f))
        assertEquals(180, sharedPdfNormalizeRotationDisplay(-180f))
        assertEquals(0, sharedPdfNormalizeRotationDisplay(359.6f))
        assertEquals(0, sharedPdfNormalizeRotationDisplay(0f))
    }

    @Test
    fun handleHitPicksNearestWithinSlop() {
        val handles = mapOf(
            SharedPdfSelectionHandle.TOP_LEFT to androidx.compose.ui.geometry.Offset(20f, 20f),
            SharedPdfSelectionHandle.TOP_RIGHT to androidx.compose.ui.geometry.Offset(40f, 20f),
        )
        assertEquals(
            SharedPdfSelectionHandle.TOP_LEFT,
            findSharedPdfSelectionHandleHit(
                handles, androidx.compose.ui.geometry.Offset(22f, 22f), 28f
            ),
        )
        assertNull(
            findSharedPdfSelectionHandleHit(
                handles, androidx.compose.ui.geometry.Offset(200f, 200f), 28f
            )
        )
    }

    @Test
    fun transformCommitIsASingleUndoStep() {
        val base = SharedPdfReaderState(annotations = listOf(square("a")))
        val moved = applySharedPdfSelectionTransform(
            base.annotations, setOf("a"),
            SharedPdfSelectionTransform.Move(0.1f, 0f), 1f,
        )
        val committed = base.reduce(SharedPdfReaderAction.SelectionTransformCommitted(base.annotations, moved))
        assertEquals(0.3f, committed.annotations.first().points.first().x, 1e-5f)
        assertTrue(committed.canUndoAnnotationEdit)
        val undone = committed.reduce(SharedPdfReaderAction.UndoAnnotationEdit)
        assertEquals(0.2f, undone.annotations.first().points.first().x, 1e-5f)
        val redone = undone.reduce(SharedPdfReaderAction.RedoAnnotationEdit)
        assertEquals(0.3f, redone.annotations.first().points.first().x, 1e-5f)
        // No-op commit pushes nothing (redo preserved).
        val noop = base.reduce(SharedPdfReaderAction.SelectionTransformCommitted(base.annotations, base.annotations))
        assertFalse(noop.canUndoAnnotationEdit)
    }

    @Test
    fun deleteAndDuplicateCommitAsSingleUndoSteps() {
        val base = SharedPdfReaderState(annotations = listOf(square("a"), square("b")))
        val deleted = base.reduce(SharedPdfReaderAction.SelectionDeletedCommitted(listOf(square("a"))))
        assertEquals(listOf("b"), deleted.annotations.map { it.id })
        val restored = deleted.reduce(SharedPdfReaderAction.UndoAnnotationEdit)
        assertEquals(setOf("a", "b"), restored.annotations.map { it.id }.toSet())

        val copies = listOf(square("c"))
        val duplicated = base.reduce(SharedPdfReaderAction.SelectionDuplicatedCommitted(copies))
        assertEquals(setOf("a", "b", "c"), duplicated.annotations.map { it.id }.toSet())
        val unduplicated = duplicated.reduce(SharedPdfReaderAction.UndoAnnotationEdit)
        assertEquals(setOf("a", "b"), unduplicated.annotations.map { it.id }.toSet())
    }

    @Test
    fun selectingSelectToolKeepsColorAndWidth() {
        val base = SharedPdfReaderState(
            selectedTool = PdfInkTool.PEN,
            selectedColorArgb = 0xFF123456.toInt(),
            strokeWidth = 0.01f,
        )
        val selected = base.reduce(SharedPdfReaderAction.ToolSelected(PdfInkTool.SELECT))
        assertEquals(PdfInkTool.SELECT, selected.selectedTool)
        assertEquals(0xFF123456.toInt(), selected.selectedColorArgb)
        assertEquals(0.01f, selected.strokeWidth)
    }
}
