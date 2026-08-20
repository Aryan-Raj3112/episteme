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
        assertEquals(primary.bookId, state.primary?.bookId)
        assertEquals(primary.uriString, state.primary?.uriString)
        assertNull(state.secondary)
        assertEquals(PdfSplitPane.PRIMARY, state.focusedPane)
    }

    @Test
    fun closingPrimaryPromotesSecondary() {
        val state = openState().reduce(PdfSplitWorkspaceAction.PaneClosed(PdfSplitPane.PRIMARY))

        assertEquals(secondary.bookId, state.primary?.bookId)
        assertEquals(secondary.uriString, state.primary?.uriString)
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

        assertEquals(secondary.bookId, state.primary?.bookId)
        assertEquals(primary.bookId, state.secondary?.bookId)
        assertTrue(state.isSplit)
    }

    @Test
    fun openingAssignsDistinctRendererSessionsAndRevision() {
        val state = openState()
        val primarySession = checkNotNull(state.primary).sessionId
        val secondarySession = checkNotNull(state.secondary).sessionId

        assertTrue(state.revision > 0)
        assertTrue(primarySession != UnassignedPdfSplitSessionId)
        assertTrue(secondarySession != UnassignedPdfSplitSessionId)
        assertTrue(primarySession != secondarySession)
        assertTrue(state.isCurrentPaneSession(PdfSplitPane.PRIMARY, primarySession))
    }

    @Test
    fun focusFollowsDocumentWhenPanesAreSwapped() {
        val focused = openState().reduce(PdfSplitWorkspaceAction.FocusChanged(PdfSplitPane.SECONDARY))
        val focusedDocument = focused.focusedDocument
        val swapped = focused.reduce(PdfSplitWorkspaceAction.PanesSwapped)

        assertEquals(focusedDocument?.bookId, swapped.focusedDocument?.bookId)
        assertEquals(PdfSplitPane.PRIMARY, swapped.focusedPane)
    }

    @Test
    fun closingFocusedPaneUsesTheSurvivingDocumentAsFocusAndExitTarget() {
        val focused = openState().reduce(PdfSplitWorkspaceAction.FocusChanged(PdfSplitPane.SECONDARY))
        val closed = focused.reduce(PdfSplitWorkspaceAction.PaneClosed(PdfSplitPane.SECONDARY))

        assertEquals(primary.bookId, closed.focusedDocument?.bookId)
        assertEquals(primary.bookId, closed.exitTargetDocument?.bookId)
    }

    @Test
    fun dividerPositionsAreStoredPerOrientation() {
        val vertical = openState().reduce(PdfSplitWorkspaceAction.DividerChanged(0.3f))
        val horizontal = vertical.reduce(
            PdfSplitWorkspaceAction.OrientationChanged(PdfSplitOrientation.HORIZONTAL)
        ).reduce(PdfSplitWorkspaceAction.DividerChanged(0.7f))
        val restoredVertical = horizontal.reduce(
            PdfSplitWorkspaceAction.OrientationChanged(PdfSplitOrientation.VERTICAL)
        )

        assertEquals(0.3f, restoredVertical.dividerFraction)
        assertEquals(0.3f, restoredVertical.verticalDividerFraction)
        assertEquals(0.7f, restoredVertical.horizontalDividerFraction)
    }

    @Test
    fun canonicalDocumentMatchingIgnoresUriAuthorityCaseAndFragment() {
        val first = PdfSplitPaneState("first", " CONTENT://Provider/Books/a.pdf#page=2 ")
        val second = PdfSplitPaneState("different", "content://provider/Books/a.pdf#page=9")

        assertTrue(first.samePdfDocument(second))
        assertEquals("content://provider/Books/a.pdf", first.canonicalUriString)
    }

    @Test
    fun staleRevisionAndSessionActionsAreIgnored() {
        val state = openState()
        val staleRevision = state.reduce(
            PdfSplitWorkspaceAction.FocusChanged(
                pane = PdfSplitPane.SECONDARY,
                expectedRevision = state.revision - 1,
            )
        )
        val staleSession = state.reduce(
            PdfSplitWorkspaceAction.PaneClosed(
                pane = PdfSplitPane.PRIMARY,
                expectedSessionId = state.primary!!.sessionId + 1,
            )
        )

        assertEquals(state, staleRevision)
        assertEquals(state, staleSession)
    }

    @Test
    fun missingPrimaryPromotesTheSurvivingSecondary() {
        val state = openState()
        val recovery = state.recoverMissingPanes(
            primaryAvailable = false,
            secondaryAvailable = true,
        )

        assertEquals(secondary.bookId, recovery.workspace.primary?.bookId)
        assertNull(recovery.workspace.secondary)
        assertEquals(setOf(PdfSplitPane.PRIMARY), recovery.missingPanes)
        assertEquals(secondary.bookId, recovery.survivingDocument?.bookId)
    }

    @Test
    fun missingSecondaryKeepsThePrimaryAndUsesItAsExitTarget() {
        val state = openState().reduce(
            PdfSplitWorkspaceAction.FocusChanged(PdfSplitPane.SECONDARY),
        )
        val recovery = state.recoverMissingPanes(
            primaryAvailable = true,
            secondaryAvailable = false,
        )

        assertEquals(primary.bookId, recovery.workspace.primary?.bookId)
        assertNull(recovery.workspace.secondary)
        assertEquals(PdfSplitPane.PRIMARY, recovery.workspace.focusedPane)
        assertEquals(primary.bookId, recovery.workspace.exitTargetDocument?.bookId)
        assertEquals(setOf(PdfSplitPane.SECONDARY), recovery.missingPanes)
    }

    @Test
    fun missingBothPanesClosesTheWorkspace() {
        val recovery = openState().recoverMissingPanes(
            primaryAvailable = false,
            secondaryAvailable = false,
        )

        assertFalse(recovery.workspace.isOpen)
        assertNull(recovery.survivingDocument)
        assertEquals(
            setOf(PdfSplitPane.PRIMARY, PdfSplitPane.SECONDARY),
            recovery.missingPanes,
        )
    }

    @Test
    fun staleCloseCannotUndoAReplacementAfterRecoverySnapshot() {
        val state = openState()
        val oldSecondarySession = state.secondary!!.sessionId
        val replaced = state.reduce(
            PdfSplitWorkspaceAction.PaneOpened(
                pane = PdfSplitPane.SECONDARY,
                document = PdfSplitPaneState("replacement", "content://replacement"),
                expectedRevision = state.revision,
                expectedSessionId = oldSecondarySession,
            ),
        )
        val staleRecoveryClose = replaced.reduce(
            PdfSplitWorkspaceAction.PaneClosed(
                pane = PdfSplitPane.SECONDARY,
                expectedRevision = state.revision,
                expectedSessionId = oldSecondarySession,
            ),
        )

        assertEquals(replaced, staleRecoveryClose)
        assertEquals("replacement", staleRecoveryClose.secondary?.bookId)
    }

    @Test
    fun secondaryOnlyStateIsPromotedToPrimary() {
        val state = PdfSplitWorkspaceState(
            primary = null,
            secondary = secondary,
            focusedPane = PdfSplitPane.SECONDARY,
        ).sanitized()

        assertEquals(secondary.bookId, state.primary?.bookId)
        assertNull(state.secondary)
        assertEquals(PdfSplitPane.PRIMARY, state.focusedPane)
    }

    @Test
    fun adaptiveLayoutFallsBackToStackedBeforeSinglePane() {
        val vertical = openState()
        val stacked = vertical.resolveLayout(
            availableWidthPx = 500,
            availableHeightPx = 1_000,
            minPaneWidthPx = 300,
            minPaneHeightPx = 300,
            dividerThicknessPx = 2,
        )
        val single = vertical.resolveLayout(
            availableWidthPx = 500,
            availableHeightPx = 500,
            minPaneWidthPx = 300,
            minPaneHeightPx = 300,
            dividerThicknessPx = 2,
        )

        assertEquals(PdfSplitPresentation.SPLIT, stacked.presentation)
        assertEquals(PdfSplitOrientation.HORIZONTAL, stacked.orientation)
        assertTrue(stacked.firstPaneSizePx >= 300)
        assertTrue(stacked.secondPaneSizePx >= 300)
        assertEquals(PdfSplitPresentation.SINGLE, single.presentation)
    }

    @Test
    fun adaptiveLayoutClampsDividerToActualMinimumDimensions() {
        val state = openState().reduce(PdfSplitWorkspaceAction.DividerChanged(0.25f))
        val plan = state.resolveLayout(
            availableWidthPx = 1_001,
            availableHeightPx = 700,
            minPaneWidthPx = 400,
            minPaneHeightPx = 300,
            dividerThicknessPx = 1,
        )

        assertEquals(PdfSplitPresentation.SPLIT, plan.presentation)
        assertTrue(plan.firstPaneSizePx >= 400)
        assertTrue(plan.secondPaneSizePx >= 400)
        assertEquals(1_000, plan.firstPaneSizePx + plan.secondPaneSizePx)
    }

    @Test
    fun adaptiveLayoutRequiresBothPaneDimensions() {
        val plan = openState().resolveLayout(
            availableWidthPx = 1_200,
            availableHeightPx = 200,
            minPaneWidthPx = 300,
            minPaneHeightPx = 300,
            dividerThicknessPx = 1,
        )

        assertEquals(PdfSplitPresentation.SINGLE, plan.presentation)
    }

    @Test
    fun durableJsonRoundTripKeepsDocumentsFocusAndDividerPreferencesButRefreshesSessions() {
        val state = openState()
            .reduce(PdfSplitWorkspaceAction.FocusChanged(PdfSplitPane.SECONDARY))
            .reduce(PdfSplitWorkspaceAction.DividerChanged(0.31f))
            .reduce(PdfSplitWorkspaceAction.OrientationChanged(PdfSplitOrientation.HORIZONTAL))
            .reduce(PdfSplitWorkspaceAction.DividerChanged(0.69f))
        val encoded = PdfSplitWorkspaceJson.encode(state)
        val restored = PdfSplitWorkspaceJson.decodeOrEmpty(encoded)

        assertEquals(state.primary?.bookId, restored.primary?.bookId)
        assertEquals(state.secondary?.bookId, restored.secondary?.bookId)
        assertEquals(state.focusedDocument?.bookId, restored.focusedDocument?.bookId)
        assertEquals(0.31f, restored.verticalDividerFraction)
        assertEquals(0.69f, restored.horizontalDividerFraction)
        assertEquals(0L, restored.revision)
        assertTrue(restored.primary!!.sessionId != state.primary!!.sessionId)
        assertTrue(!encoded.contains("sessionId"))
    }

    @Test
    fun durableJsonReadsLegacySingleDividerAndInvalidPayloadAsClosed() {
        val legacy = """
            {"dividerFraction":0.37,"primary":{"bookId":"one","uriString":"content://one"},"secondary":{"bookId":"two","uriString":"content://two"}}
        """.trimIndent()
        val restored = PdfSplitWorkspaceJson.decodeOrEmpty(legacy)

        assertEquals(0.37f, restored.verticalDividerFraction)
        assertEquals(0.37f, restored.horizontalDividerFraction)
        assertTrue(restored.isSplit)
        assertFalse(PdfSplitWorkspaceJson.decodeOrEmpty("not json").isOpen)
    }

    private fun openState(): PdfSplitWorkspaceState = PdfSplitWorkspaceState().reduce(
        PdfSplitWorkspaceAction.Open(primary = primary, secondary = secondary)
    )
}
