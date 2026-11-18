package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedFontFilesTest {

    @Test
    fun androidPickerAcceptsFontAliasesAndGenericProviderStreams() {
        // Document providers report .ttf/.otf as a generic stream MIME or an
        // older alias; without these the picker hides the file entirely.
        assertTrue("application/octet-stream" in SharedFontFiles.androidPickerMimeTypes)
        assertTrue("font/*" in SharedFontFiles.androidPickerMimeTypes)
        assertTrue("font/ttf" in SharedFontFiles.androidPickerMimeTypes)
        assertTrue("application/x-font-ttf" in SharedFontFiles.androidPickerMimeTypes)
        assertTrue("application/x-font-otf" in SharedFontFiles.androidPickerMimeTypes)
        assertTrue("application/x-font-opentype" in SharedFontFiles.androidPickerMimeTypes)
        assertTrue("application/font-woff2" in SharedFontFiles.androidPickerMimeTypes)
        assertEquals(
            SharedFontFiles.androidPickerMimeTypes.distinct(),
            SharedFontFiles.androidPickerMimeTypes,
        )
    }

    @Test
    fun supportedExtensionsMatchThePickerAndTheImporter() {
        assertEquals(setOf("ttf", "otf", "woff2"), SharedFontFiles.supportedExtensions)
        assertTrue(SharedFontFiles.isSupportedFileName("OpenDyslexic-Regular.otf"))
        assertTrue(SharedFontFiles.isSupportedFileName("OPEN SANS.TTF"))
        assertTrue(SharedFontFiles.isSupportedFileName("font.woff2"))
        assertFalse(SharedFontFiles.isSupportedFileName("font.woff"))
        assertFalse(SharedFontFiles.isSupportedFileName("font.zip"))
        assertFalse(SharedFontFiles.isSupportedFileName("font"))
    }

    @Test
    fun catalogLookupMapsDisplayedFamilyNameToItsCssFaceEntry() {
        val catalog = listOf("Buda:300", "Literata", "Molle:400italic")

        assertEquals("Buda:300", googleFontCatalogEntry("Buda", catalog))
        assertEquals("Buda:300", googleFontCatalogEntry("buda", catalog))
        assertEquals("Literata", googleFontCatalogEntry("Literata", catalog))
        // Unknown names fall back to the plain family so the server still answers.
        assertEquals("Inter", googleFontCatalogEntry("Inter", catalog))
        assertEquals("Buda", googleFontFamilyName("Buda:300"))
        assertEquals("Literata", googleFontFamilyName("Literata"))
    }

    @Test
    fun cssParserExtractsTheFirstHttpsFontUrl() {
        val css = """
            @font-face {
              font-family: 'Literata';
              src: url(https://fonts.gstatic.com/s/literata/v35/font.ttf) format('truetype');
            }
        """.trimIndent()

        assertEquals(
            "https://fonts.gstatic.com/s/literata/v35/font.ttf",
            googleFontDownloadUrlFromCss(css),
        )
        assertEquals(
            "ttf",
            googleFontDownloadExtension("https://fonts.gstatic.com/s/literata/v35/font.ttf?foo=bar"),
        )
        assertEquals(null, googleFontDownloadUrlFromCss("no urls here"))
    }
}
