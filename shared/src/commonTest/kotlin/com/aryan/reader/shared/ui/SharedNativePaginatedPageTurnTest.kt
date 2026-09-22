package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedNativePaginatedPageTurnTest {
    @Test
    fun `forward turn offsets match android pager identity`() {
        // Position animates 0 -> 1: the outgoing page curls away (offset in (-1, 0))
        // while the incoming page slides beneath it (offset in (0, 1)).
        assertEquals(-0.5f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 0, setLeadSlots = 0, turnDistanceSlots = 1, direction = 1, fraction = 0.5f))
        assertEquals(0.5f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 0, setLeadSlots = 1, turnDistanceSlots = 1, direction = 1, fraction = 0.5f))
        assertEquals(-1f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 0, setLeadSlots = 0, turnDistanceSlots = 1, direction = 1, fraction = 1f))
        assertEquals(0f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 0, setLeadSlots = 1, turnDistanceSlots = 1, direction = 1, fraction = 1f))
        assertEquals(1f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 0, setLeadSlots = 1, turnDistanceSlots = 1, direction = 1, fraction = 0f))
    }

    @Test
    fun `backward turn offsets uncurl the incoming page on top`() {
        // Position animates 0 -> -1: the previous page un-curls on top (offset in (-1, 0))
        // while the current page rests flat beneath (offset in (0, 1)).
        assertEquals(0.25f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 0, setLeadSlots = 0, turnDistanceSlots = 1, direction = -1, fraction = 0.25f))
        assertEquals(-0.75f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 0, setLeadSlots = 1, turnDistanceSlots = 1, direction = -1, fraction = 0.25f))
        assertEquals(0f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 0, setLeadSlots = 1, turnDistanceSlots = 1, direction = -1, fraction = 1f))
        assertEquals(-1f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 0, setLeadSlots = 1, turnDistanceSlots = 1, direction = -1, fraction = 0f))
    }

    @Test
    fun `two page spread offsets track each slot`() {
        // Position animates 0 -> 2 across the spread: mid-turn the second outgoing
        // page is the settled current page while the incoming spread approaches.
        assertEquals(-1f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 0, setLeadSlots = 0, turnDistanceSlots = 2, direction = 1, fraction = 0.5f))
        assertEquals(0f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 1, setLeadSlots = 0, turnDistanceSlots = 2, direction = 1, fraction = 0.5f))
        assertEquals(1f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 0, setLeadSlots = 2, turnDistanceSlots = 2, direction = 1, fraction = 0.5f))
        assertEquals(2f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 1, setLeadSlots = 2, turnDistanceSlots = 2, direction = 1, fraction = 0.5f))
        assertEquals(-2f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 0, setLeadSlots = 0, turnDistanceSlots = 2, direction = 1, fraction = 1f))
        assertEquals(0f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 0, setLeadSlots = 2, turnDistanceSlots = 2, direction = 1, fraction = 1f))
        assertEquals(1f, sharedPaginatedTurnPageOffset(slotOffsetInSet = 1, setLeadSlots = 2, turnDistanceSlots = 2, direction = 1, fraction = 1f))
    }

    @Test
    fun `only single visible step turns animate like android tap navigation`() {
        assertTrue(
            sharedPaginatedTurnShouldAnimate(
                animationEnabled = true,
                outgoingFirstPageIndex = 4,
                incomingFirstPageIndex = 5,
                visiblePageCount = 1
            )
        )
        assertTrue(
            sharedPaginatedTurnShouldAnimate(
                animationEnabled = true,
                outgoingFirstPageIndex = 5,
                incomingFirstPageIndex = 4,
                visiblePageCount = 1
            )
        )
    }

    @Test
    fun `multi page jumps settle instantly like android scrollToPage`() {
        assertFalse(
            sharedPaginatedTurnShouldAnimate(
                animationEnabled = true,
                outgoingFirstPageIndex = 4,
                incomingFirstPageIndex = 9,
                visiblePageCount = 1
            )
        )
        assertFalse(
            sharedPaginatedTurnShouldAnimate(
                animationEnabled = true,
                outgoingFirstPageIndex = 4,
                incomingFirstPageIndex = 4,
                visiblePageCount = 1
            )
        )
    }

    @Test
    fun `two page spread animates a full spread step`() {
        assertTrue(
            sharedPaginatedTurnShouldAnimate(
                animationEnabled = true,
                outgoingFirstPageIndex = 4,
                incomingFirstPageIndex = 6,
                visiblePageCount = 2
            )
        )
        assertFalse(
            sharedPaginatedTurnShouldAnimate(
                animationEnabled = true,
                outgoingFirstPageIndex = 4,
                incomingFirstPageIndex = 5,
                visiblePageCount = 2
            )
        )
    }

    @Test
    fun `disabled animation or missing pages never animate`() {
        assertFalse(
            sharedPaginatedTurnShouldAnimate(
                animationEnabled = false,
                outgoingFirstPageIndex = 4,
                incomingFirstPageIndex = 5,
                visiblePageCount = 1
            )
        )
        assertFalse(
            sharedPaginatedTurnShouldAnimate(
                animationEnabled = true,
                outgoingFirstPageIndex = null,
                incomingFirstPageIndex = 5,
                visiblePageCount = 1
            )
        )
        assertFalse(
            sharedPaginatedTurnShouldAnimate(
                animationEnabled = true,
                outgoingFirstPageIndex = 4,
                incomingFirstPageIndex = null,
                visiblePageCount = 1
            )
        )
    }

    @Test
    fun `paper darkness drives the flap tint like the android dark theme flag`() {
        assertFalse(sharedReaderPaperIsDark(androidx.compose.ui.graphics.Color.White))
        assertTrue(sharedReaderPaperIsDark(androidx.compose.ui.graphics.Color(0xFF1C1B1F)))
    }

    @Test
    fun `drag release commits past halfway like the android pager snap`() {
        assertEquals(
            SharedPaginatedDragRelease.COMMIT_FORWARD,
            sharedPaginatedDragReleaseTarget(0.6f, 0f, 1, canDragForward = true, canDragBackward = true)
        )
        assertEquals(
            SharedPaginatedDragRelease.COMMIT_BACKWARD,
            sharedPaginatedDragReleaseTarget(-0.6f, 0f, 1, canDragForward = true, canDragBackward = true)
        )
        assertEquals(
            SharedPaginatedDragRelease.CANCEL,
            sharedPaginatedDragReleaseTarget(0.4f, 0f, 1, canDragForward = true, canDragBackward = true)
        )
        assertEquals(
            SharedPaginatedDragRelease.CANCEL,
            sharedPaginatedDragReleaseTarget(0f, 0f, 1, canDragForward = true, canDragBackward = true)
        )
    }

    @Test
    fun `drag release flings to the neighbor from a short drag`() {
        assertEquals(
            SharedPaginatedDragRelease.COMMIT_FORWARD,
            sharedPaginatedDragReleaseTarget(0.2f, 2.5f, 1, canDragForward = true, canDragBackward = true)
        )
        assertEquals(
            SharedPaginatedDragRelease.COMMIT_BACKWARD,
            sharedPaginatedDragReleaseTarget(-0.2f, -2.5f, 1, canDragForward = true, canDragBackward = true)
        )
    }

    @Test
    fun `opposite fling vetoes a past-halfway commit`() {
        assertEquals(
            SharedPaginatedDragRelease.CANCEL,
            sharedPaginatedDragReleaseTarget(0.6f, -2.5f, 1, canDragForward = true, canDragBackward = true)
        )
        assertEquals(
            SharedPaginatedDragRelease.CANCEL,
            sharedPaginatedDragReleaseTarget(-0.6f, 2.5f, 1, canDragForward = true, canDragBackward = true)
        )
    }

    @Test
    fun `drag release respects page boundaries`() {
        assertEquals(
            SharedPaginatedDragRelease.CANCEL,
            sharedPaginatedDragReleaseTarget(0.9f, 3f, 1, canDragForward = false, canDragBackward = true)
        )
        assertEquals(
            SharedPaginatedDragRelease.CANCEL,
            sharedPaginatedDragReleaseTarget(-0.9f, -3f, 1, canDragForward = true, canDragBackward = false)
        )
    }

    @Test
    fun `two page spread drags commit at one full page`() {
        assertEquals(
            SharedPaginatedDragRelease.COMMIT_FORWARD,
            sharedPaginatedDragReleaseTarget(1.2f, 0f, 2, canDragForward = true, canDragBackward = true)
        )
        assertEquals(
            SharedPaginatedDragRelease.CANCEL,
            sharedPaginatedDragReleaseTarget(0.8f, 0f, 2, canDragForward = true, canDragBackward = true)
        )
    }

    @Test
    fun `raw drag fraction maps to toward-next pages with rtl flip`() {
        // LTR: next page is to the left, so a leftward (negative) drag moves forward.
        assertEquals(0.5f, sharedPaginatedDragPositionPages(-0.5f, 1, rightToLeftPagination = false))
        assertEquals(-0.5f, sharedPaginatedDragPositionPages(0.5f, 1, rightToLeftPagination = false))
        // RTL: next page is to the right, so a rightward drag moves forward.
        assertEquals(0.5f, sharedPaginatedDragPositionPages(0.5f, 1, rightToLeftPagination = true))
        assertEquals(-1f, sharedPaginatedDragPositionPages(-0.5f, 2, rightToLeftPagination = true))
    }

    @Test
    fun `turn layers keep the beneath set opaque and the top set transparent`() {
        val paper = androidx.compose.ui.graphics.Color(0xFFFFFCF5)
        val transparent = androidx.compose.ui.graphics.Color.Transparent
        // Settled content stays fully opaque.
        assertEquals(
            paper to paper,
            sharedPaginatedTurnLayerBackgrounds(turnActive = false, overlayFirst = false, baseBackground = paper)
        )
        // Forward turn: main (incoming) beneath opaque, overlay (outgoing) on top transparent.
        assertEquals(
            paper to transparent,
            sharedPaginatedTurnLayerBackgrounds(turnActive = true, overlayFirst = false, baseBackground = paper)
        )
        // Backward turn: overlay beneath opaque, main (incoming) on top transparent.
        assertEquals(
            transparent to paper,
            sharedPaginatedTurnLayerBackgrounds(turnActive = true, overlayFirst = true, baseBackground = paper)
        )
    }

    @Test
    fun `spread spine crease only draws with animation on and spread mode`() {
        assertTrue(shouldDrawSpreadSpineCrease(animationEnabled = true, isTwoPageSpread = true))
        assertFalse(shouldDrawSpreadSpineCrease(animationEnabled = false, isTwoPageSpread = true))
        assertFalse(shouldDrawSpreadSpineCrease(animationEnabled = true, isTwoPageSpread = false))
        assertFalse(shouldDrawSpreadSpineCrease(animationEnabled = false, isTwoPageSpread = false))
    }

    @Test
    fun `spread turn offset is uniform across every slot in the set`() {
        val outgoing = List(2) {
            sharedPaginatedSpreadTurnPageOffset(setLeadSlots = 0, direction = 1, fraction = 0.5f)
        }
        assertEquals(outgoing[0], outgoing[1])
        assertEquals(-0.5f, outgoing[0])
        val incoming = List(2) {
            sharedPaginatedSpreadTurnPageOffset(setLeadSlots = 1, direction = 1, fraction = 0.5f)
        }
        assertEquals(incoming[0], incoming[1])
        assertEquals(0.5f, incoming[0])
        // Ends of the turn park the sheets like a one-page pager step.
        assertEquals(-1f, sharedPaginatedSpreadTurnPageOffset(setLeadSlots = 0, direction = 1, fraction = 1f))
        assertEquals(0f, sharedPaginatedSpreadTurnPageOffset(setLeadSlots = 1, direction = 1, fraction = 1f))
    }

    @Test
    fun `spread fold sweeps across the spine without nan geometry`() {
        val width = 800f
        val height = 600f
        val spineX = width / 2f
        val early = spreadPageCurlFold(width, height, progress = 0.1f, touchY = null)
        val mid = spreadPageCurlFold(width, height, progress = 0.5f, touchY = null)
        val late = spreadPageCurlFold(width, height, progress = 0.9f, touchY = null)
        assertTrue(early.valid && mid.valid && late.valid)
        // Fold starts on the outer edge and moves left past the spine.
        assertTrue(early.dragX > spineX)
        assertTrue(mid.dragX < spineX)
        assertTrue(late.dragX < 0f)
        // No NaNs in the normal.
        assertEquals(mid.nx, mid.nx)
        assertEquals(mid.ny, mid.ny)
    }

    @Test
    fun `spread fold corner follows touch y like the single page curl`() {
        val bottomTouch = spreadPageCurlFold(800f, 600f, progress = 0.3f, touchY = 600f)
        val topTouch = spreadPageCurlFold(800f, 600f, progress = 0.3f, touchY = 0f)
        assertTrue(bottomTouch.valid && topTouch.valid)
        assertEquals(600f, bottomTouch.cornerY)
        assertEquals(0f, topTouch.cornerY)
    }

    @Test
    fun `book flip hinges at spine with a hint of corner curl`() {
        val width = 800f
        val height = 600f
        // Center touch (tap turns without a corner bias) stays a pure hinge.
        val helper = spreadBookFlipFold(width, height, progress = 0.3f)
        assertTrue(helper.valid)
        assertTrue(helper.isVerticalBookHinge())
        // Corner touches keep the corner side but only tilt the fold a little.
        val bottomForced = spreadPageCurlFold(width, height, progress = 0.3f, touchY = 600f, forceBookFlip = true)
        val topForced = spreadPageCurlFold(width, height, progress = 0.3f, touchY = 0f, forceBookFlip = true)
        assertTrue(bottomForced.valid && topForced.valid)
        assertEquals(600f, bottomForced.cornerY)
        assertEquals(0f, topForced.cornerY)
        assertTrue(kotlin.math.abs(bottomForced.ny) < 0.30f)
        assertTrue(kotlin.math.abs(topForced.ny) < 0.30f)
        assertTrue(bottomForced.nx > 0.9f)
        assertTrue(topForced.nx > 0.9f)
        // Same sweep as the helper regardless of touch height.
        assertEquals(helper.midX, bottomForced.midX)
        assertEquals(helper.dragX, bottomForced.dragX)
    }

    @Test
    fun `corner peel keeps pager exit sweep while book flip parks at spine`() {
        val width = 800f
        val height = 600f
        // Single-page corner peel exits past the left edge like a pager slot.
        val peelSettled = spreadPageCurlFold(width, height, progress = 1f, touchY = null)
        assertTrue(peelSettled.valid)
        assertTrue(peelSettled.dragX < 0f)
        // Spread book leaf parks at the spine instead.
        val flipSettled = spreadPageCurlFold(width, height, progress = 1f, touchY = null, forceBookFlip = true)
        assertTrue(flipSettled.valid)
        assertEquals(0f, flipSettled.dragX)
        assertEquals(width / 2f, flipSettled.midX)
    }

    @Test
    fun `book flip peels from right and settles onto the left page`() {
        val width = 800f
        val height = 600f
        val spineX = width / 2f
        val early = spreadBookFlipFold(width, height, progress = 0.1f)
        val mid = spreadBookFlipFold(width, height, progress = 0.5f)
        val settled = spreadBookFlipFold(width, height, progress = 1f)
        assertTrue(early.valid && mid.valid && settled.valid)
        assertTrue(early.isVerticalBookHinge())
        assertTrue(mid.isVerticalBookHinge())
        assertTrue(settled.isVerticalBookHinge())
        // Peel starts at the right edge and crosses toward the spine.
        assertTrue(early.dragX > spineX)
        assertTrue(mid.dragX <= spineX)
        // Settles with the fold parked at the spine — the right leaf lands on
        // the left page instead of flying past it.
        assertEquals(0f, settled.dragX)
        assertEquals(spineX, settled.midX)
        // No NaNs in the normal.
        assertEquals(mid.nx, mid.nx)
        assertEquals(mid.ny, mid.ny)
    }
}
