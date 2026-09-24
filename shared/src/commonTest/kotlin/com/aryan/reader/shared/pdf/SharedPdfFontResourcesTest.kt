package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedPdfFontResourcesTest {

    @Test
    fun `maps asset preset paths onto shared compose resources`() {
        assertEquals(
            "files/fonts/lora.ttf",
            sharedPdfBundledFontResourcePath("asset:fonts/lora.ttf"),
        )
        assertEquals(
            "files/fonts/roboto_mono.ttf",
            sharedPdfBundledFontResourcePath("asset:fonts/roboto_mono.ttf"),
        )
    }

    @Test
    fun `ignores blank and non-asset paths`() {
        assertNull(sharedPdfBundledFontResourcePath(null))
        assertNull(sharedPdfBundledFontResourcePath(""))
        assertNull(sharedPdfBundledFontResourcePath("/tmp/custom.ttf"))
        assertNull(sharedPdfBundledFontResourcePath("asset:other/file.ttf"))
    }

    @Test
    fun `fallback family matches on-screen preset families`() {
        assertEquals("serif", sharedPdfFallbackExportFontFamily("asset:fonts/merriweather.ttf"))
        assertEquals("serif", sharedPdfFallbackExportFontFamily("Lora"))
        assertEquals("sans-serif", sharedPdfFallbackExportFontFamily("asset:fonts/lato.ttf"))
        assertEquals("sans-serif", sharedPdfFallbackExportFontFamily("Lexend"))
        assertEquals("monospace", sharedPdfFallbackExportFontFamily("asset:fonts/roboto_mono.ttf"))
        assertNull(sharedPdfFallbackExportFontFamily("asset:fonts/unknown.ttf"))
        assertNull(sharedPdfFallbackExportFontFamily(null))
    }
}
