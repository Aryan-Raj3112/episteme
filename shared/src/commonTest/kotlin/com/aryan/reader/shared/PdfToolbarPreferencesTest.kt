package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfToolbarPreferencesTest {
    @Test
    fun `pdf navigation tools follow android tts enablement`() {
        assertFalse(isPdfReaderToolEnabledDuringTts(PdfReaderTool.SLIDER, true))
        assertFalse(isPdfReaderToolEnabledDuringTts(PdfReaderTool.TOC, true))
        assertFalse(isPdfReaderToolEnabledDuringTts(PdfReaderTool.SEARCH, true))
        assertTrue(isPdfReaderToolEnabledDuringTts(PdfReaderTool.EDIT_MODE, true))
        assertTrue(isPdfReaderToolEnabledDuringTts(PdfReaderTool.SEARCH, false))
    }

    @Test
    fun `defaults mirror android visibility and placement`() {
        val preferences = PdfToolbarPreferences()

        assertFalse(preferences.isVisible(PdfReaderTool.BRIGHTNESS))
        assertFalse(preferences.isVisible(PdfReaderTool.SCREEN_ORIENTATION))
        assertFalse(preferences.isVisible(PdfReaderTool.HIGHLIGHT_ALL))
        assertTrue(preferences.isBottom(PdfReaderTool.SLIDER))
        assertFalse(preferences.isBottom(PdfReaderTool.THEME))
    }

    @Test
    fun `sanitizing removes unavailable tools and invalid placement`() {
        val available = setOf(PdfReaderTool.THEME, PdfReaderTool.FILE_INFO, PdfReaderTool.SEARCH)
        val sanitized = PdfToolbarPreferences(
            hiddenToolIds = setOf(PdfReaderTool.AI_FEATURES.id, PdfReaderTool.SEARCH.id),
            bottomToolIds = setOf(PdfReaderTool.FILE_INFO.id, PdfReaderTool.SEARCH.id),
        ).sanitized(available)

        assertEquals(listOf(PdfReaderTool.THEME, PdfReaderTool.FILE_INFO, PdfReaderTool.SEARCH), sanitized.toolOrder)
        assertEquals(setOf(PdfReaderTool.SEARCH.id), sanitized.hiddenToolIds)
        assertEquals(setOf(PdfReaderTool.SEARCH.id), sanitized.bottomToolIds)
    }

    @Test
    fun `move keeps complete unique android order`() {
        val moved = PdfToolbarPreferences().move(PdfReaderTool.SEARCH, -1)
        assertEquals(
            moved.toolOrder.indexOf(PdfReaderTool.TOC) - 1,
            moved.toolOrder.indexOf(PdfReaderTool.SEARCH),
        )
        assertEquals(PdfReaderTool.entries.size, moved.toolOrder.distinct().size)
    }

    @Test
    fun `move within ios tools skips unavailable catalog entries`() {
        val available = setOf(PdfReaderTool.THEME, PdfReaderTool.SLIDER, PdfReaderTool.SEARCH)
        val moved = PdfToolbarPreferences().moveWithinAvailable(
            tool = PdfReaderTool.SEARCH,
            delta = -1,
            availableTools = available,
        )

        assertEquals(
            listOf(PdfReaderTool.THEME, PdfReaderTool.SEARCH, PdfReaderTool.SLIDER),
            moved.toolOrder.filter { it in available },
        )
        assertEquals(PdfReaderTool.entries.size, moved.toolOrder.distinct().size)
    }
}
