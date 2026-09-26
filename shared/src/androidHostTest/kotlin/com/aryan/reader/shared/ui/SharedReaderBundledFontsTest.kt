package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedReaderBundledFontsTest {

    @Test
    fun `selectable families resolve to bundled typefaces like Android assets`() {
        // The shared native reader renders these real files (the same set
        // ships in app assets for the Android native reader); system
        // families resolve to null and fall back to platform fonts.
        assertEquals("files/fonts/merriweather.ttf", sharedReaderBundledFontFile("Merriweather"))
        assertEquals("files/fonts/lora.ttf", sharedReaderBundledFontFile("Lora"))
        assertEquals("files/fonts/lato.ttf", sharedReaderBundledFontFile("Lato"))
        assertEquals("files/fonts/lexend.ttf", sharedReaderBundledFontFile("Lexend"))
        assertEquals("files/fonts/roboto_mono.ttf", sharedReaderBundledFontFile("Roboto Mono"))
        assertNull(sharedReaderBundledFontFile("Serif"))
        assertNull(sharedReaderBundledFontFile("Sans"))
        assertNull(sharedReaderBundledFontFile("Mono"))
        assertNull(sharedReaderBundledFontFile("Original"))
        assertNull(sharedReaderBundledFontFile("Unknown"))
    }
}
