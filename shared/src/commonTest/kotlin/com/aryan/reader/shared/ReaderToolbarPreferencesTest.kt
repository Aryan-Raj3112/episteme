package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderToolbarPreferencesTest {

    @Test
    fun `mobile parity tools follow android default visibility`() {
        val preferences = ReaderToolbarPreferences()

        assertFalse(preferences.isVisible(ReaderTool.BRIGHTNESS))
        assertFalse(preferences.isVisible(ReaderTool.SCREEN_ORIENTATION))
        assertTrue(preferences.isVisible(ReaderTool.FILE_INFO))
        assertTrue(ReaderTool.BRIGHTNESS in preferences.toolOrder)
        assertTrue(ReaderTool.SCREEN_ORIENTATION in preferences.toolOrder)
        assertTrue(preferences.isVisible(ReaderTool.AUTO_SCROLL))
    }

    @Test
    fun `default bottom matches android six tool benchmark`() {
        val bottom = ReaderToolbarPreferences.defaultBottomToolIds

        assertTrue(ReaderTool.SLIDER.id in bottom)
        assertTrue(ReaderTool.TOC.id in bottom)
        assertTrue(ReaderTool.FORMAT.id in bottom)
        assertTrue(ReaderTool.SEARCH.id in bottom)
        assertTrue(ReaderTool.AI_FEATURES.id in bottom)
        assertTrue(ReaderTool.TTS_CONTROLS.id in bottom)
    }

    @Test
    fun `default overflow order follows android benchmark`() {        val overflow = ReaderToolbarPreferences().toolOrder
            .filter { it.category == "Overflow Menu" }

        // Benchmark: File Information stays last; Book Word Replacements
        // precedes the TTS section.
        assertEquals(ReaderTool.FILE_INFO, overflow.last())
        val bookReplacements = overflow.indexOf(ReaderTool.BOOK_REPLACEMENTS)
        val ttsSettings = overflow.indexOf(ReaderTool.TTS_SETTINGS)
        val ttsReplacements = overflow.indexOf(ReaderTool.TTS_REPLACEMENTS)
        assertTrue(bookReplacements in 0 until ttsSettings)
        assertTrue(ttsSettings in 0 until ttsReplacements)
    }

    @Test
    fun `legacy bottom sets migrate to android six tool benchmark`() {
        val four = setOf(
            ReaderTool.SLIDER.id,
            ReaderTool.TOC.id,
            ReaderTool.FORMAT.id,
            ReaderTool.SEARCH.id
        )
        val expected = ReaderToolbarPreferences.defaultBottomToolIds

        assertEquals(expected, migrateReaderBottomToolIds(four))
        assertEquals(expected, migrateReaderBottomToolIds(four + ReaderTool.TTS_CONTROLS.id))
        assertEquals(expected, migrateReaderBottomToolIds(four + ReaderTool.AI_FEATURES.id))

        val custom = setOf(ReaderTool.SLIDER.id, ReaderTool.SEARCH.id)
        assertEquals(custom, migrateReaderBottomToolIds(custom))

        assertEquals(
            expected,
            ReaderToolbarPreferences(bottomToolIds = four).sanitized().bottomToolIds
        )
    }

    @Test
    fun `hidden defaults migrate by version like android`() {
        assertEquals(
            setOf(ReaderTool.SCREEN_ORIENTATION.id, ReaderTool.BRIGHTNESS.id),
            readerHiddenToolsIntroducedAfter(0)
        )
        assertEquals(setOf(ReaderTool.BRIGHTNESS.id), readerHiddenToolsIntroducedAfter(1))
        assertEquals(emptySet(), readerHiddenToolsIntroducedAfter(2))

        val legacy = ReaderToolbarPreferences(
            hiddenToolIds = emptySet(),
            hiddenToolsDefaultsVersion = 0
        ).sanitized()
        assertTrue(ReaderTool.SCREEN_ORIENTATION.id in legacy.hiddenToolIds)
        assertTrue(ReaderTool.BRIGHTNESS.id in legacy.hiddenToolIds)
        assertEquals(ReaderHiddenToolsDefaultsVersion, legacy.hiddenToolsDefaultsVersion)

        // Explicit user unhide on a current prefs object survives sanitize.
        val unhidden = ReaderToolbarPreferences()
            .withVisibility(ReaderTool.BRIGHTNESS, hidden = false)
            .sanitized()
        assertTrue(unhidden.isVisible(ReaderTool.BRIGHTNESS))
        assertFalse(unhidden.isVisible(ReaderTool.SCREEN_ORIENTATION))
    }
    @Test
    fun `auto scroll follows overflow visibility customization`() {
        val hidden = ReaderToolbarPreferences().withVisibility(ReaderTool.AUTO_SCROLL, hidden = true)

        assertFalse(hidden.isVisible(ReaderTool.AUTO_SCROLL))
        assertFalse(ReaderTool.AUTO_SCROLL in hidden.orderedVisibleTools())
        assertTrue(ReaderTool.AUTO_SCROLL in hidden.toolOrder)
    }

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
    fun `highlight palette reducer follows android four slot palette`() {
        val state = SharedReaderScreenState()
            .reduce(
                AppAction.ReaderHighlightPaletteChanged(
                    ReaderHighlightPalette(
                        colors = listOf(HighlightColor.CYAN, HighlightColor.CYAN, HighlightColor.YELLOW)
                    )
                )
            )

        assertEquals(ReaderHighlightPalette.defaultColors, state.readerHighlightPalette.colors)

        val customized = state.reduce(
            AppAction.ReaderHighlightPaletteChanged(
                ReaderHighlightPalette(
                    colors = listOf(HighlightColor.CYAN, HighlightColor.CYAN, HighlightColor.PINK, HighlightColor.WHITE)
                )
            )
        )

        assertEquals(
            listOf(HighlightColor.CYAN, HighlightColor.CYAN, HighlightColor.PINK, HighlightColor.WHITE),
            customized.readerHighlightPalette.colors
        )
    }
}
