package com.aryan.reader.shared

import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun `pdf and epub built in themes share the standard reader palette`() {
        data class ThemeToken(
            val name: String,
            val backgroundArgb: Int,
            val textArgb: Int,
            val isDark: Boolean,
            val textureId: String?
        )

        val epubPalette = BuiltInReaderThemes
            .drop(1)
            .map { theme ->
                ThemeToken(
                    name = theme.name,
                    backgroundArgb = theme.backgroundColor.toArgb(),
                    textArgb = theme.textColor.toArgb(),
                    isDark = theme.isDark,
                    textureId = theme.textureId
                )
            }
        val pdfPalette = BuiltInPdfReaderThemes
            .drop(2)
            .map { theme ->
                ThemeToken(
                    name = theme.name,
                    backgroundArgb = theme.backgroundColor.toArgb(),
                    textArgb = theme.textColor.toArgb(),
                    isDark = theme.isDark,
                    textureId = theme.textureId
                )
            }

        assertEquals(epubPalette, pdfPalette)
    }

    @Test
    fun `pdf highlighter defaults follow the reader highlight palette`() {
        val expectedPdfColors = ReaderHighlightPalette.defaultColors
            .take(SharedPdfHighlighterPalette.MaxColors)
            .map { color ->
                (SharedPdfHighlighterPalette.DefaultAlpha shl 24) or (color.color.toArgb() and 0x00FFFFFF)
            }

        assertEquals(expectedPdfColors, SharedPdfHighlighterPalette.defaultColors)
        assertEquals(expectedPdfColors[0], SharedPdfAnnotationDefaults.configFor(PdfInkTool.HIGHLIGHTER).colorArgb)
        assertEquals(expectedPdfColors[1], SharedPdfAnnotationDefaults.configFor(PdfInkTool.HIGHLIGHTER_ROUND).colorArgb)
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
    fun `reader texture helpers normalize extensions and resolve mime types`() {
        assertEquals("jpg", normalizeReaderTextureExtension("JPEG"))
        assertEquals("webp", normalizeReaderTextureExtension(" webp "))
        assertNull(normalizeReaderTextureExtension("svg"))

        assertEquals("image/jpeg", readerTextureMimeTypeForExtension("jpg"))
        assertEquals("image/webp", readerTextureMimeTypeForExtension("webp"))
        assertEquals("image/png", readerTextureMimeTypeForExtension("unknown"))
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
