package com.aryan.reader.shared.ui

import com.aryan.reader.shared.EpubOverflowMenuSection
import com.aryan.reader.shared.ReaderTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the EPUB overflow menu's shape — Android's order and gating — because both mobile readers now
 * render it from one list. A reorder or an ungated capability would otherwise change Android's
 * benchmark menu silently.
 */
class SharedReaderEpubOverflowSectionsTest {

    /** The reader defaults hide brightness and orientation, so neither takes part in the order. */
    private val defaultHiddenTools = setOf(ReaderTool.BRIGHTNESS.id, ReaderTool.SCREEN_ORIENTATION.id)

    private fun sections(
        hasHiddenToolbarTools: Boolean = false,
        hasToggleReflow: Boolean = false,
        hasDeleteReflow: Boolean = false,
        hasExportAnnotations: Boolean = false,
        supportsVolumeScroll: Boolean = true,
        hiddenToolNamesOrIds: Set<String> = defaultHiddenTools,
    ) = sharedEpubOverflowMenuSections(
        hiddenToolNamesOrIds = hiddenToolNamesOrIds,
        hasHiddenToolbarTools = hasHiddenToolbarTools,
        hasToggleReflow = hasToggleReflow,
        hasDeleteReflow = hasDeleteReflow,
        hasExportAnnotations = hasExportAnnotations,
        supportsVolumeScroll = supportsVolumeScroll,
    )

    @Test
    fun `sections follow the Android benchmark order`() {
        assertEquals(
            listOf(
                EpubOverflowMenuSection.CUSTOMIZE_TOOLBAR,
                EpubOverflowMenuSection.VIEW_ORIGINAL_PDF,
                EpubOverflowMenuSection.READING_MODE,
                EpubOverflowMenuSection.BOOKMARK,
                EpubOverflowMenuSection.TAP_TO_TURN,
                EpubOverflowMenuSection.VOLUME_SCROLL,
                EpubOverflowMenuSection.PAGE_TURN_ANIM,
                EpubOverflowMenuSection.KEEP_SCREEN_ON,
                EpubOverflowMenuSection.VISUAL_OPTIONS,
                EpubOverflowMenuSection.AUTO_SCROLL,
                EpubOverflowMenuSection.BOOK_REPLACEMENTS,
                EpubOverflowMenuSection.TTS_SETTINGS,
                EpubOverflowMenuSection.EXPORT_ANNOTATIONS,
                EpubOverflowMenuSection.FILE_INFO,
            ),
            sections(hasToggleReflow = true, hasExportAnnotations = true)
        )
    }

    @Test
    fun `volume section is dropped where the platform cannot read the volume keys`() {
        assertTrue(EpubOverflowMenuSection.VOLUME_SCROLL in sections(supportsVolumeScroll = true))
        assertFalse(EpubOverflowMenuSection.VOLUME_SCROLL in sections(supportsVolumeScroll = false))
    }

    @Test
    fun `reflow sections only appear with a reflow source`() {
        val withoutReflow = sections()
        assertFalse(EpubOverflowMenuSection.VIEW_ORIGINAL_PDF in withoutReflow)
        assertFalse(EpubOverflowMenuSection.DELETE_TEXT_VIEW in withoutReflow)

        val withReflow = sections(hasToggleReflow = true, hasDeleteReflow = true)
        assertTrue(EpubOverflowMenuSection.VIEW_ORIGINAL_PDF in withReflow)
        assertTrue(EpubOverflowMenuSection.DELETE_TEXT_VIEW in withReflow)
    }

    @Test
    fun `a hidden overflow tool loses its section instead of reaching the menu`() {
        val hiddenBookmark = sections(hiddenToolNamesOrIds = defaultHiddenTools + ReaderTool.BOOKMARK.id)
        assertFalse(EpubOverflowMenuSection.BOOKMARK in hiddenBookmark)
    }

    @Test
    fun `the tts section survives while either tts tool is visible`() {
        val replacementsOnlyVisible = sections(
            hiddenToolNamesOrIds = defaultHiddenTools + ReaderTool.TTS_SETTINGS.id
        )
        assertTrue(EpubOverflowMenuSection.TTS_SETTINGS in replacementsOnlyVisible)

        val bothHidden = sections(
            hiddenToolNamesOrIds = defaultHiddenTools + ReaderTool.TTS_SETTINGS.id + ReaderTool.TTS_REPLACEMENTS.id
        )
        assertFalse(EpubOverflowMenuSection.TTS_SETTINGS in bothHidden)
    }

    @Test
    fun `hidden tools keep the reader's tool order and never list overflow tools`() {
        val toolOrder = listOf(
            ReaderTool.AUTO_SCROLL,
            ReaderTool.THEME,
            ReaderTool.SLIDER,
            ReaderTool.BOOKMARK,
        )
        assertEquals(
            listOf(ReaderTool.THEME, ReaderTool.SLIDER),
            sharedEpubHiddenToolbarTools(
                toolOrder = toolOrder,
                hiddenToolNamesOrIds = setOf(
                    ReaderTool.SLIDER.name,
                    ReaderTool.BOOKMARK.name,
                    ReaderTool.THEME.name,
                ),
            )
        )
    }

    @Test
    fun `hidden tools accept ids as well as names`() {
        // Hosts disagree on the shape: Android persists names, the shared preferences hold ids.
        assertEquals(
            listOf(ReaderTool.THEME, ReaderTool.SLIDER),
            sharedEpubHiddenToolbarTools(
                toolOrder = listOf(ReaderTool.THEME, ReaderTool.SLIDER),
                hiddenToolNamesOrIds = setOf(ReaderTool.THEME.id, ReaderTool.SLIDER.name),
            )
        )
    }
}
