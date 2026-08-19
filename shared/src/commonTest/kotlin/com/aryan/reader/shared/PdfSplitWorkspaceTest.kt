package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfSplitWorkspaceTest {
    private val primary = PdfSplitPaneState("primary", "content://primary")
    private val secondary = PdfSplitPaneState("secondary", "content://secondary")

    @Test
    fun openingCreatesAFocusedTwoPaneWorkspace() {
        val state = PdfSplitWorkspaceState().reduce(
            PdfSplitWorkspaceAction.Open(
                primary = primary,
                secondary = secondary,
                orientation = PdfSplitOrientation.HORIZONTAL,
            )
        )

        assertTrue(state.isOpen)
        assertTrue(state.isSplit)
        assertEquals(PdfSplitOrientation.HORIZONTAL, state.orientation)
        assertEquals(PdfSplitPane.PRIMARY, state.focusedPane)
    }

    @Test
    fun closingSecondaryLeavesPrimaryInSinglePaneMode() {
        val state = openState().reduce(PdfSplitWorkspaceAction.PaneClosed(PdfSplitPane.SECONDARY))

        assertTrue(state.isOpen)
        assertFalse(state.isSplit)
        assertEquals(primary, state.primary)
        assertNull(state.secondary)
        assertEquals(PdfSplitPane.PRIMARY, state.focusedPane)
    }

    @Test
    fun closingPrimaryPromotesSecondary() {
        val state = openState().reduce(PdfSplitWorkspaceAction.PaneClosed(PdfSplitPane.PRIMARY))

        assertEquals(secondary, state.primary)
        assertNull(state.secondary)
        assertEquals(PdfSplitPane.PRIMARY, state.focusedPane)
    }

    @Test
    fun dividerIsClampedToReadableBounds() {
        val tooSmall = openState().reduce(PdfSplitWorkspaceAction.DividerChanged(0.1f))
        val tooLarge = openState().reduce(PdfSplitWorkspaceAction.DividerChanged(0.9f))

        assertEquals(MinimumPdfSplitDividerFraction, tooSmall.dividerFraction)
        assertEquals(MaximumPdfSplitDividerFraction, tooLarge.dividerFraction)
    }

    @Test
    fun duplicatePanesAreSanitized() {
        val state = PdfSplitWorkspaceState().reduce(
            PdfSplitWorkspaceAction.Open(primary = primary, secondary = primary)
        )

        assertTrue(state.isOpen)
        assertFalse(state.isSplit)
        assertNull(state.secondary)
    }

    @Test
    fun panesWithTheSameUriAreNotOpenedTwice() {
        val state = PdfSplitWorkspaceState().reduce(
            PdfSplitWorkspaceAction.Open(
                primary = primary,
                secondary = secondary.copy(bookId = "different-id", uriString = primary.uriString),
            )
        )

        assertFalse(state.isSplit)
        assertNull(state.secondary)
    }

    @Test
    fun swappingPanesPreservesTheSplitArrangement() {
        val state = openState().reduce(PdfSplitWorkspaceAction.PanesSwapped)

        assertEquals(secondary, state.primary)
        assertEquals(primary, state.secondary)
        assertTrue(state.isSplit)
    }

    private fun openState(): PdfSplitWorkspaceState = PdfSplitWorkspaceState().reduce(
        PdfSplitWorkspaceAction.Open(primary = primary, secondary = secondary)
    )
}
