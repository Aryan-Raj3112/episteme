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
    }}
