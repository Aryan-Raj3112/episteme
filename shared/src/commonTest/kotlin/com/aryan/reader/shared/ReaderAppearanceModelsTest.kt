package com.aryan.reader.shared

import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReaderAppearanceModelsTest {

    @Test
    fun `pdf built in themes include android pdf defaults and textured presets`() {
        assertEquals("no_theme", BuiltInPdfReaderThemes.first().id)
        assertNotNull(BuiltInPdfReaderThemes.firstOrNull { it.id == "reverse" })

        val texturedThemeIds = BuiltInPdfReaderThemes
            .filter { it.textureId != null }
            .mapTo(mutableSetOf()) { it.id }

        assertEquals(
            setOf(
                "pdf_natural_white_texture",
                "pdf_retina_texture",
                "pdf_veneer_texture",
                "pdf_grey_wash_texture",
                "pdf_fabric_texture",
                "pdf_retro_texture"
            ),
            texturedThemeIds
        )
    }

    @Test
    fun `reader textures expose shared desktop resource paths`() {
        assertTrue(ReaderTexture.entries.all { it.assetPath.startsWith("textures/") })
        assertEquals("textures/ep_naturalwhite.webp", ReaderTexture.NATURAL_WHITE.assetPath)
        assertEquals("textures/texture_paper.png", ReaderTexture.PAPER.assetPath)
    }

    @Test
    fun `file texture display names use imported file names`() {
        assertEquals("custom-paper", readerTextureDisplayName("${ReaderTextureFilePrefix}C:\\textures\\custom-paper.png"))
    }

    @Test
    fun `pdf textured theme maps into reader settings`() {
        val theme = BuiltInPdfReaderThemes.first { it.id == "pdf_fabric_texture" }
        val settings = theme.toReaderSettings()

        assertEquals("pdf_fabric_texture", settings.themeId)
        assertEquals(ReaderTexture.CLASSY_FABRIC.id, settings.textureId)
        assertTrue(settings.darkMode)
        assertEquals(theme.backgroundColor.toArgb().toLong(), settings.backgroundColorArgb)
        assertEquals(theme.textColor.toArgb().toLong(), settings.textColorArgb)
    }
}
