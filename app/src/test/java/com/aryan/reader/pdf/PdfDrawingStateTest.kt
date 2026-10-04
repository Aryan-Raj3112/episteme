package com.aryan.reader.pdf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The in-flight stroke is fed one sample at a time from the pointer-input
 * coroutine, on the main thread, while the reader is rendering the page under
 * it. These cover the state contract that loop depends on.
 */
class PdfDrawingStateTest {

    private fun point(x: Float, y: Float, timestamp: Long = 0L) = PdfPoint(x, y, timestamp)

    private fun state() = PdfDrawingState()

    @Test
    fun `a stroke starts as a single point`() {
        val drawingState = state()

        drawingState.onDrawStart(3, point(0.1f, 0.2f), InkType.PEN, Color.Red, 0.01f)

        val annotation = requireNotNull(drawingState.currentAnnotation)
        assertEquals(3, annotation.pageIndex)
        assertEquals(InkType.PEN, annotation.inkType)
        assertEquals(Color.Red.toArgb(), annotation.color.toArgb())
        assertEquals(listOf(point(0.1f, 0.2f)), annotation.points)
    }

    @Test
    fun `appended samples extend the stroke in order`() {
        val drawingState = state()
        drawingState.onDrawStart(0, point(0f, 0f), InkType.PEN, Color.Black, 0.01f)

        drawingState.onDraw(point(0.1f, 0f))
        drawingState.onDraw(point(0.2f, 0.1f))

        assertEquals(
            listOf(0f to 0f, 0.1f to 0f, 0.2f to 0.1f),
            requireNotNull(drawingState.currentAnnotation).points.map { it.x to it.y }
        )
    }

    @Test
    fun `samples drawn before a stroke starts are ignored`() {
        val drawingState = state()

        drawingState.onDraw(point(0.5f, 0.5f))

        assertNull(drawingState.currentAnnotation)
    }

    /**
     * The stroke list must never be copied per sample: a stroke can carry
     * hundreds of coalesced samples and copying it every time made the whole
     * stroke O(n^2) on the main thread. Only `onDrawEnd` materialises a
     * snapshot, and that snapshot has to be detached from the live list.
     */
    @Test
    fun `the in-flight stroke shares one point list and commits a snapshot`() {
        val drawingState = state()
        drawingState.onDrawStart(0, point(0f, 0f), InkType.PEN, Color.Black, 0.01f)

        val liveList = requireNotNull(drawingState.currentAnnotation).points
        drawingState.onDraw(point(0.5f, 0.5f))
        assertTrue(
            "per-sample copies reintroduce the O(n^2) append",
            liveList === requireNotNull(drawingState.currentAnnotation).points
        )

        val committed = requireNotNull(drawingState.onDrawEnd())
        assertNotSame(liveList, committed.points)
        assertEquals(listOf(0f to 0f, 0.5f to 0.5f), committed.points.map { it.x to it.y })

        // The committed stroke must not follow the live list once drawing moves
        // on to the next stroke.
        assertNull(drawingState.currentAnnotation)
        drawingState.onDrawStart(1, point(0f, 0f), InkType.PEN, Color.Black, 0.01f)
        assertEquals(listOf(0f to 0f, 0.5f to 0.5f), committed.points.map { it.x to it.y })
    }

    @Test
    fun `ending with nothing in flight returns nothing`() {
        val drawingState = state()

        assertNull(drawingState.onDrawEnd())
        assertNull(drawingState.currentAnnotation)
    }

    @Test
    fun `a cancelled stroke is discarded`() {
        val drawingState = state()
        drawingState.onDrawStart(2, point(0f, 0f), InkType.PEN, Color.Black, 0.01f)
        drawingState.onDraw(point(0.5f, 0.5f))

        drawingState.onDrawCancel()

        assertNull(drawingState.currentAnnotation)
        assertNull(drawingState.onDrawEnd())
        // The cancelled stroke's samples must not leak into the next one.
        drawingState.onDrawStart(2, point(0f, 0f), InkType.PEN, Color.Black, 0.01f)
        assertEquals(listOf(0f to 0f), requireNotNull(drawingState.currentAnnotation).points.map { it.x to it.y })
    }

    /**
     * A snapped highlighter is deliberately two points long: it is a straight
     * line, so the samples it discards are not loss.
     */
    @Test
    fun `a snapped highlighter drag collapses to start and current`() {
        val drawingState = state()
        drawingState.onDrawStart(4, point(0.1f, 0.1f), InkType.HIGHLIGHTER, Color.Yellow, 0.04f)
        drawingState.onDraw(point(0.2f, 0.15f))
        drawingState.onDraw(point(0.3f, 0.2f))

        drawingState.updateDrag(point(0.4f, 0.25f))

        val annotation = requireNotNull(drawingState.currentAnnotation)
        assertEquals(4, annotation.pageIndex)
        assertEquals(listOf(0.1f to 0.1f, 0.4f to 0.25f), annotation.points.map { it.x to it.y })
    }

    @Test
    fun `a snapped drag before a stroke starts is ignored`() {
        val drawingState = state()

        drawingState.updateDrag(point(0.4f, 0.25f))

        assertNull(drawingState.currentAnnotation)
    }
}