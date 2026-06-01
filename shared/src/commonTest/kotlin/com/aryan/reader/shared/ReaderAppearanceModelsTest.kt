package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.SharedPdfAndroidHighlightColors
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign
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
    fun `pdf highlighter defaults follow android pdf highlight slots`() {
        val expectedPdfColors = SharedPdfAndroidHighlightColors.palette.take(4)

        assertEquals(4, SharedPdfHighlighterPalette.MaxColors)
        assertEquals(expectedPdfColors, SharedPdfHighlighterPalette.defaultColors)
        assertEquals(expectedPdfColors[0], SharedPdfAnnotationDefaults.configFor(PdfInkTool.HIGHLIGHTER).colorArgb)
        assertEquals(expectedPdfColors[1], SharedPdfAnnotationDefaults.configFor(PdfInkTool.HIGHLIGHTER_ROUND).colorArgb)

        val custom = SharedPdfHighlighterPalette(
            colors = expectedPdfColors + listOf(0xFFFF00FF.toInt())
        ).sanitized()
        assertEquals(4, custom.colors.size)
        assertEquals(expectedPdfColors, custom.colors)
    }

    @Test
    fun `custom reader themes keep android persisted shape`() {
        val first = ReaderTheme(
            id = "custom",
            name = "Custom",
            backgroundColor = Color(0xFFF5F5F5),
            textColor = Color(0xFF111111),
            isDark = false,
            isCustom = true
        )
        val replacement = first.copy(name = "Replacement")
        val builtIn = BuiltInReaderThemes.first().copy(isCustom = false)

        assertEquals(listOf(replacement), listOf(first, builtIn, replacement).sanitizeCustomReaderThemes())
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

    @Test
    fun `reset reader format settings keeps reader mode and appearance choices`() {
        val settings = ReaderSettings(
            fontSize = 26,
            lineSpacing = 2.0f,
            margin = 96,
            horizontalMargin = 128,
            verticalMargin = 72,
            textAlign = SharedReaderTextAlign.RIGHT,
            pageWidth = 1040,
            fontFamily = "Imported",
            customFontPath = "C:\\fonts\\Imported.ttf",
            paragraphSpacing = 2.2f,
            imageScale = 1.8f,
            readingMode = ReaderReadingMode.PAGINATED,
            pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE,
            themeId = "sepia",
            textureId = ReaderTexture.PAPER.id,
            textureAlpha = 0.35f,
            darkMode = true,
            backgroundColorArgb = 0xFF101010,
            textColorArgb = 0xFFEAEAEA
        )

        val reset = settings.resetReaderFormatSettings()
        val defaults = ReaderSettings()

        assertEquals(defaults.fontSize, reset.fontSize)
        assertEquals(defaults.lineSpacing, reset.lineSpacing)
        assertEquals(defaults.margin, reset.margin)
        assertEquals(defaults.horizontalMargin, reset.horizontalMargin)
        assertEquals(defaults.verticalMargin, reset.verticalMargin)
        assertEquals(defaults.textAlign, reset.textAlign)
        assertEquals(defaults.pageWidth, reset.pageWidth)
        assertEquals(defaults.fontFamily, reset.fontFamily)
        assertEquals(defaults.customFontPath, reset.customFontPath)
        assertEquals(defaults.paragraphSpacing, reset.paragraphSpacing)
        assertEquals(defaults.imageScale, reset.imageScale)

        assertEquals(ReaderReadingMode.PAGINATED, reset.readingMode)
        assertEquals(ReaderPageSpreadMode.TWO_PAGE, reset.pageSpreadMode)
        assertEquals("sepia", reset.themeId)
        assertEquals(ReaderTexture.PAPER.id, reset.textureId)
        assertEquals(0.35f, reset.textureAlpha)
        assertEquals(true, reset.darkMode)
        assertEquals(0xFF101010, reset.backgroundColorArgb)
        assertEquals(0xFFEAEAEA, reset.textColorArgb)
    }

    @Test
    fun `axis margin updates keep the opposite axis fixed`() {
        val defaultSettings = ReaderSettings()

        val horizontalOnly = defaultSettings.withHorizontalReaderMargin(96)
        assertEquals(96, horizontalOnly.resolvedHorizontalMargin)
        assertEquals(defaultSettings.resolvedVerticalMargin, horizontalOnly.resolvedVerticalMargin)
        assertEquals(96, horizontalOnly.margin)
        assertEquals(defaultSettings.resolvedVerticalMargin, horizontalOnly.verticalMargin)

        val verticalOnly = horizontalOnly.withVerticalReaderMargin(24)
        assertEquals(96, verticalOnly.resolvedHorizontalMargin)
        assertEquals(24, verticalOnly.resolvedVerticalMargin)
        assertEquals(96, verticalOnly.margin)
        assertEquals(96, verticalOnly.horizontalMargin)
    }

    @Test
    fun `page width format control is only shown for paginated mode`() {
        assertEquals(
            false,
            ReaderSettings(readingMode = ReaderReadingMode.VERTICAL).shouldShowPageWidthFormatControl()
        )
        assertEquals(
            true,
            ReaderSettings(readingMode = ReaderReadingMode.PAGINATED).shouldShowPageWidthFormatControl()
        )
    }
}
