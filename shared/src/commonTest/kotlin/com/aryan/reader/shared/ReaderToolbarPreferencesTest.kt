package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderToolbarPreferencesTest {

    @Test
    fun `toolbar preferences sanitize unknown ids and preserve missing tools`() {
        val preferences = ReaderToolbarPreferences(
            hiddenToolIds = setOf(ReaderTool.SEARCH.id, "missing"),
            toolOrder = listOf(ReaderTool.BOOKMARK, ReaderTool.THEME),
            bottomToolIds = setOf(ReaderTool.BOOKMARK.id, "missing")
        ).sanitized()

        assertEquals(setOf(ReaderTool.SEARCH.id), preferences.hiddenToolIds)
        assertEquals(ReaderTool.BOOKMARK, preferences.toolOrder.first())
        assertEquals(ReaderTool.THEME, preferences.toolOrder[1])
        assertTrue(ReaderTool.SEARCH in preferences.toolOrder)
        assertEquals(setOf(ReaderTool.BOOKMARK.id), preferences.bottomToolIds)
    }

    @Test
    fun `toolbar reducers update shared screen state`() {
        val state = SharedReaderScreenState()
            .reduce(AppAction.ReaderToolVisibilityChanged(ReaderTool.SEARCH, hidden = true))
            .reduce(AppAction.ReaderToolPlacementChanged(ReaderTool.BOOKMARK, bottom = true))
            .reduce(AppAction.ReaderToolOrderChanged(listOf(ReaderTool.BOOKMARK, ReaderTool.THEME)))

        assertFalse(state.readerToolbarPreferences.isVisible(ReaderTool.SEARCH))
        assertTrue(state.readerToolbarPreferences.isBottom(ReaderTool.BOOKMARK))
        assertEquals(ReaderTool.BOOKMARK, state.readerToolbarPreferences.toolOrder.first())
        assertEquals(ReaderTool.THEME, state.readerToolbarPreferences.toolOrder[1])
    }

    @Test
    fun `highlight palette reducer sanitizes colors`() {
        val state = SharedReaderScreenState()
            .reduce(
                AppAction.ReaderHighlightPaletteChanged(
                    ReaderHighlightPalette(
                        colors = listOf(HighlightColor.CYAN, HighlightColor.CYAN, HighlightColor.YELLOW)
                    )
                )
            )

        assertEquals(listOf(HighlightColor.CYAN, HighlightColor.YELLOW), state.readerHighlightPalette.colors)
    }
}
