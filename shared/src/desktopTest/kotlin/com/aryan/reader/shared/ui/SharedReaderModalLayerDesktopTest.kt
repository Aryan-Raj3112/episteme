package com.aryan.reader.shared.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedReaderModalLayerDesktopTest {

    @Test
    fun `left reader panel uses extension width instead of blocking full reader`() {
        assertEquals(
            340.dp,
            sharedReaderModalEdgePanelLayerWidth(
                level = SharedReaderModalLevel.PanelLeft,
                anchorWidth = 1280.dp
            )
        )
    }

    @Test
    fun `right reader panel remains an inspector width`() {
        assertEquals(
            380.dp,
            sharedReaderModalEdgePanelLayerWidth(
                level = SharedReaderModalLevel.PanelRight,
                anchorWidth = 1280.dp
            )
        )
    }

    @Test
    fun `bottom chrome layer overlaps reader edge to avoid desktop rounding gap`() {
        assertEquals(
            948f,
            sharedReaderModalChromeBottomLayerTopPx(
                anchorTopPx = 100f,
                anchorHeightPx = 1000f,
                dialogHeightPx = 160f,
                overlapPx = 8f
            )
        )
    }

    @Test
    fun `chrome top layer can opt into focus for search input`() {
        assertFalse(
            sharedReaderModalLayerWindowFocusable(
                level = SharedReaderModalLevel.ChromeTop,
                focusableOverride = null
            )
        )
        assertTrue(
            sharedReaderModalLayerWindowFocusable(
                level = SharedReaderModalLevel.ChromeTop,
                focusableOverride = true
            )
        )
    }

    @Test
    fun `chrome bottom layer stays non focusable by default`() {
        assertFalse(
            sharedReaderModalLayerWindowFocusable(
                level = SharedReaderModalLevel.ChromeBottom,
                focusableOverride = null
            )
        )
    }

    @Test
    fun `chrome layer remains visible when owner window is focused`() {
        assertTrue(
            sharedReaderModalChromeLayerVisible(
                ownerShowing = true,
                ownerDisplayable = true,
                ownerMinimized = false,
                ownerActive = false,
                ownerFocused = true,
                ownerModalActive = false
            )
        )
    }

    @Test
    fun `chrome layer remains visible while its own reader modal is active`() {
        assertTrue(
            sharedReaderModalChromeLayerVisible(
                ownerShowing = true,
                ownerDisplayable = true,
                ownerMinimized = false,
                ownerActive = false,
                ownerFocused = false,
                ownerModalActive = true
            )
        )
    }

    @Test
    fun `chrome layer hides when owner loses focus to another app`() {
        assertFalse(
            sharedReaderModalChromeLayerVisible(
                ownerShowing = true,
                ownerDisplayable = true,
                ownerMinimized = false,
                ownerActive = false,
                ownerFocused = false,
                ownerModalActive = false
            )
        )
    }

    @Test
    fun `chrome layer hides when owner window is unavailable`() {
        assertFalse(
            sharedReaderModalChromeLayerVisible(
                ownerShowing = false,
                ownerDisplayable = true,
                ownerMinimized = false,
                ownerActive = true,
                ownerFocused = true,
                ownerModalActive = false
            )
        )
        assertFalse(
            sharedReaderModalChromeLayerVisible(
                ownerShowing = true,
                ownerDisplayable = false,
                ownerMinimized = false,
                ownerActive = true,
                ownerFocused = true,
                ownerModalActive = false
            )
        )
        assertFalse(
            sharedReaderModalChromeLayerVisible(
                ownerShowing = true,
                ownerDisplayable = true,
                ownerMinimized = true,
                ownerActive = true,
                ownerFocused = true,
                ownerModalActive = false
            )
        )
    }

    @Test
    fun `modal layer hides immediately while owner window is closing`() {
        assertTrue(
            sharedReaderModalLayerShouldHideImmediately(
                ownerShowing = true,
                ownerDisplayable = true,
                ownerClosing = true
            )
        )
        assertTrue(
            sharedReaderModalLayerShouldHideImmediately(
                ownerShowing = false,
                ownerDisplayable = true,
                ownerClosing = false
            )
        )
        assertTrue(
            sharedReaderModalLayerShouldHideImmediately(
                ownerShowing = true,
                ownerDisplayable = false,
                ownerClosing = false
            )
        )
        assertFalse(
            sharedReaderModalLayerShouldHideImmediately(
                ownerShowing = true,
                ownerDisplayable = true,
                ownerClosing = false
            )
        )
    }
}
