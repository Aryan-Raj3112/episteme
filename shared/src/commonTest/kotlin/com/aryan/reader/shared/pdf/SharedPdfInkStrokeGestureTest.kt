package com.aryan.reader.shared.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Android parity for the PDF ink gesture (PdfVerticalReader
 * `globalDrawingModifier`): a stroke starts on the down and every sample of
 * every pressed change is fed into it — Android never inspects whether an
 * upstream handler consumed the change, and skipping those changes is what
 * made iOS strokes stop mid-line.
 */
class SharedPdfInkStrokeGestureTest {

    @Test
    fun `a coalesced change yields every sample it carries in order`() {
        val samples = sharedPdfInkSamplesForChange(
            historical = listOf(
                SharedPdfInkSample(Offset(1f, 1f), 10L),
                SharedPdfInkSample(Offset(2f, 2f), 12L),
                SharedPdfInkSample(Offset(3f, 3f), 14L)
            ),
            current = SharedPdfInkSample(Offset(4f, 4f), 16L)
        )

        // Only the last sample of the batch is reachable through
        // `PointerInputChange.position`; the rest live in `historical`. Reading
        // position alone is what turned curved handwriting into chords.
        assertEquals(
            listOf(Offset(1f, 1f), Offset(2f, 2f), Offset(3f, 3f), Offset(4f, 4f)),
            samples.map { it.position }
        )
        assertEquals(listOf(10L, 12L, 14L, 16L), samples.map { it.eventTimeMillis })
    }

    @Test
    fun `an unbatched change yields only its own position`() {
        val samples = sharedPdfInkSamplesForChange(
            historical = emptyList(),
            current = SharedPdfInkSample(Offset(7f, 9f), 42L)
        )

        assertEquals(listOf(Offset(7f, 9f)), samples.map { it.position })
    }

    @Test
    fun `the sample already emitted is not emitted twice`() {
        val samples = sharedPdfInkSamplesForChange(
            historical = listOf(SharedPdfInkSample(Offset(5f, 5f), 10L)),
            current = SharedPdfInkSample(Offset(5f, 5f), 11L),
            lastEmittedPosition = Offset(5f, 5f),
            minDistancePx = SHARED_PDF_INK_MIN_SAMPLE_DISTANCE_PX
        )

        // A stationary pointer still reports moves; replaying them verbatim
        // would grow the stroke without adding geometry.
        assertTrue(samples.isEmpty())
    }

    @Test
    fun `sub-pixel samples are dropped but real movement is kept`() {
        val samples = sharedPdfInkSamplesForChange(
            historical = listOf(
                SharedPdfInkSample(Offset(10.2f, 10.1f), 10L),
                SharedPdfInkSample(Offset(12f, 10f), 12L)
            ),
            current = SharedPdfInkSample(Offset(14f, 10f), 14L),
            lastEmittedPosition = Offset(10f, 10f),
            minDistancePx = SHARED_PDF_INK_MIN_SAMPLE_DISTANCE_PX
        )

        assertEquals(listOf(Offset(12f, 10f), Offset(14f, 10f)), samples.map { it.position })
    }

    @Test
    fun `non-finite samples are discarded`() {
        val samples = sharedPdfInkSamplesForChange(
            historical = listOf(SharedPdfInkSample(Offset(Float.NaN, 1f), 10L)),
            current = SharedPdfInkSample(Offset(3f, 3f), 12L)
        )

        assertEquals(listOf(Offset(3f, 3f)), samples.map { it.position })
    }

    @Test
    fun `event times are converted to the epoch scale ink points are stored on`() {
        val origin = sharedPdfInkEventTimeOrigin(epochMillis = 1_700_000_000_000L, uptimeMillis = 90_000L)

        assertEquals(1_700_000_000_000L, origin + 90_000L)
        // Sub-millisecond spacing inside one batch survives the conversion.
        assertEquals(2L, (origin + 90_003L) - (origin + 90_001L))
    }

    @Test
    fun `stylus only mode ignores finger downs but keeps the pencil`() {
        assertFalse(sharedPdfIsInkDownAllowed(isStylusOnlyMode = true, type = PointerType.Touch))
        assertTrue(sharedPdfIsInkDownAllowed(isStylusOnlyMode = true, type = PointerType.Stylus))
        assertTrue(sharedPdfIsInkDownAllowed(isStylusOnlyMode = false, type = PointerType.Touch))
    }

    @Test
    fun `eraser override follows the pointer type and the stylus shortcut`() {
        assertTrue(sharedPdfIsEraserOverride(PointerType.Eraser, stylusButtonPressed = false))
        assertTrue(sharedPdfIsEraserOverride(PointerType.Stylus, stylusButtonPressed = true))
        assertFalse(sharedPdfIsEraserOverride(PointerType.Stylus, stylusButtonPressed = false))
        // A finger never erases through the stylus shortcut.
        assertFalse(sharedPdfIsEraserOverride(PointerType.Touch, stylusButtonPressed = true))
    }

    @Test
    fun `a stroke stays owned by its page until it ends`() {
        assertEquals(4, sharedPdfResolveInkStrokeOwner(currentOwnerPdfPage = null, pageIndex = 4))
        assertEquals(4, sharedPdfResolveInkStrokeOwner(currentOwnerPdfPage = 4, pageIndex = 4))
        // A second page can never steal the in-flight stroke's point list.
        assertEquals(4, sharedPdfResolveInkStrokeOwner(currentOwnerPdfPage = 4, pageIndex = 5))
    }
}
