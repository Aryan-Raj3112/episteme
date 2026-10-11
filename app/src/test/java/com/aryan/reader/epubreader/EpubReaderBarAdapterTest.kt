package com.aryan.reader.epubreader

import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ui.SharedMobileEpubLocalTtsState
import com.aryan.reader.shared.ui.SharedMobileEpubToolbarTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Android side of the shared EPUB bars.
 *
 * The bar swap's one real risk is the seam, not the widget: Android's reader persists tool *names*
 * and a flat session boolean, while the shared bars read ids and a three-state engine. These are the
 * only conversions that can be checked without a device, so they are checked here — the rendering
 * itself is the phase-end instrumentation pass.
 */
class EpubReaderBarAdapterTest {

    @Test
    fun `name-keyed preferences become the shared id-keyed model`() {
        val preferences = epubReaderToolbarPreferences(
            hiddenTools = setOf(ReaderTool.BRIGHTNESS.name, ReaderTool.SCREEN_ORIENTATION.name),
            toolOrder = listOf(ReaderTool.DICTIONARY, ReaderTool.THEME),
            bottomTools = setOf(ReaderTool.SLIDER.name, ReaderTool.TOC.name),
        )

        assertEquals(
            setOf(ReaderTool.BRIGHTNESS.id, ReaderTool.SCREEN_ORIENTATION.id),
            preferences.hiddenToolIds,
        )
        assertEquals(setOf(ReaderTool.SLIDER.id, ReaderTool.TOC.id), preferences.bottomToolIds)
        // sanitized() appends every tool the caller's order left out, so a short stored order cannot
        // silently drop a tool from the bars.
        assertEquals(ReaderTool.entries.toList(), preferences.toolOrder)
    }

    @Test
    fun `ids are accepted as well as names`() {
        val preferences = epubReaderToolbarPreferences(
            hiddenTools = setOf(ReaderTool.BRIGHTNESS.id),
            toolOrder = ReaderTool.entries.toList(),
            bottomTools = emptySet(),
        )

        assertEquals(setOf(ReaderTool.BRIGHTNESS.id), preferences.hiddenToolIds)
    }

    @Test
    fun `an unknown tool name is dropped instead of hiding a real tool`() {
        val preferences = epubReaderToolbarPreferences(
            hiddenTools = setOf("not_a_tool", ReaderTool.THEME.name),
            toolOrder = ReaderTool.entries.toList(),
            bottomTools = emptySet(),
        )

        assertEquals(setOf(ReaderTool.THEME.id), preferences.hiddenToolIds)
    }

    @Test
    fun `the pinned defaults version never re-hides a tool the reader restored`() {
        // Brightness and screen orientation are the version-2 hidden defaults. Android's own
        // preference layer has already applied that migration by the time the reader composes, so the
        // bar must not re-hide them when a reader has shown them again.
        val preferences = epubReaderToolbarPreferences(
            hiddenTools = emptySet(),
            toolOrder = ReaderTool.entries.toList(),
            bottomTools = emptySet(),
        )

        assertTrue(preferences.hiddenToolIds.isEmpty())
        assertTrue(preferences.bottomToolIds.isEmpty())
    }

    @Test
    fun `a running tts session projects to the shared speaking state`() {
        assertEquals(SharedMobileEpubLocalTtsState.SPEAKING, epubChromeReadAloudState(isTtsActive = true))
        assertEquals(SharedMobileEpubLocalTtsState.IDLE, epubChromeReadAloudState(isTtsActive = false))
    }

    @Test
    fun `android's bar tool set is the shared one`() {
        assertEquals(SharedMobileEpubToolbarTools, epubToolbarTools)
    }
}
