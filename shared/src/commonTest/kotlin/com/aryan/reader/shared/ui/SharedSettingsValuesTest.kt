package com.aryan.reader.shared.ui

import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.SharedSettingsDestination
import com.aryan.reader.shared.reader.ReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedSettingsValuesTest {

    private fun values(
        epubDefaults: ReaderSettings = ReaderSettings(),
        pdfDefaults: ReaderSettings = ReaderSettings(),
        toolbarPreferences: ReaderToolbarPreferences = ReaderToolbarPreferences(),
        customFontCount: Int = 0,
        versionLabel: String? = "Version 1.2.3",
    ): Map<String, String> = sharedSettingsItemValues(
        epubDefaults = epubDefaults,
        pdfDefaults = pdfDefaults,
        toolbarPreferences = toolbarPreferences,
        customFontCount = customFontCount,
        fontSizeLabel = "18sp",
        toolCountLabel = "12 of 24 tools",
        customFontCountLabel = "3 fonts",
        versionLabel = versionLabel,
    )

    @Test
    fun `defaults preview the text defaults tool count and app version`() {
        val previews = values()

        assertEquals(
            "Default · 18sp",
            previews[SharedSettingsDestination.EPUB_FORMAT.name],
        )
        assertEquals(
            "12 of 24 tools",
            previews[SharedSettingsDestination.READER_TOOLBAR_DEFAULTS.name],
        )
        assertEquals("Version 1.2.3", previews[SharedSettingsAction.ABOUT.name])
        assertFalse(SharedSettingsDestination.EPUB_THEME_TEXTURE.name in previews)
        assertFalse(SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS.name in previews)
        assertFalse(SharedSettingsAction.CUSTOM_FONTS.name in previews)
    }

    @Test
    fun `theme ids resolve to display names and custom fonts only appear when present`() {
        val previews = values(
            epubDefaults = ReaderSettings(themeId = "sepia"),
            pdfDefaults = ReaderSettings(themeId = "reverse"),
            customFontCount = 3,
        )

        assertEquals("Sepia", previews[SharedSettingsDestination.EPUB_THEME_TEXTURE.name])
        assertEquals("Reverse", previews[SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS.name])
        assertEquals("3 fonts", previews[SharedSettingsAction.CUSTOM_FONTS.name])
    }

    @Test
    fun `a blank font family leaves no dangling separator`() {
        val previews = values(epubDefaults = ReaderSettings(fontFamily = ""))

        assertEquals("18sp", previews[SharedSettingsDestination.EPUB_FORMAT.name])
    }

    @Test
    fun `a missing app version omits the about preview`() {
        val previews = values(versionLabel = null)

        assertTrue(SharedSettingsAction.ABOUT.name !in previews)
    }
}
