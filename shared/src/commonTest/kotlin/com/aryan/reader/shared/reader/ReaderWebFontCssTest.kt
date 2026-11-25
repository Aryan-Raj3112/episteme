package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WebView reader font CSS parity with Android's ChapterWebView:
 *
 * Android injects an `@font-face` for the selected reader font and forces the
 * family onto every text element with `!important`. The shared WebView must do
 * the same or a publication's `p { font-family: ... }` rules keep drawing the
 * book's font in some places while the rest of the page uses the reader font.
 */
class ReaderWebFontCssTest {

    @Test
    fun `custom font gets a face and is forced over publication css`() {
        val settings = ReaderSettings(
            fontFamily = "Imported",
            customFontPath = "C:/Fonts/Imported-Regular.ttf",
        )

        val css = settings.readerWebFontCss(source = "url('data:font/ttf;base64,AA==')")

        assertTrue(css.faceCss.contains("@font-face"), css.toString())
        assertTrue(css.faceCss.contains("'ReaderCustomFont'"))
        assertTrue(css.faceCss.contains("data:font/ttf"))
        assertEquals("'ReaderCustomFont', Georgia, 'Times New Roman', serif", css.familyCss)
        assertTrue(css.overrideCss.contains("!important"), css.toString())
        assertTrue(css.overrideCss.contains("p, span"), css.toString())
    }

    @Test
    fun `a custom font missing from this platform keeps Android fallback and force`() {
        val settings = ReaderSettings(
            fontFamily = "Imported",
            customFontPath = "C:/Fonts/Missing.ttf",
        )

        val css = settings.readerWebFontCss(source = null)

        assertTrue(css.faceCss.isEmpty())
        assertEquals("'ReaderCustomFont', Georgia, 'Times New Roman', serif", css.familyCss)
        assertTrue(css.overrideCss.contains("!important"), css.toString())
    }

    @Test
    fun `bundled family gets a local face when the platform can supply one`() {
        val css = ReaderSettings(fontFamily = "Lora")
            .readerWebFontCss(source = "url('data:font/ttf;base64,AA==')")

        assertTrue(css.faceCss.contains("'ReaderBundledFont'"), css.toString())
        assertTrue(css.familyCss.startsWith("'ReaderBundledFont', "))
        assertTrue(css.overrideCss.contains("!important"), css.toString())
    }

    @Test
    fun `original leaves publication families and book fonts alone`() {
        val css = ReaderSettings(fontFamily = "Original")
            .readerWebFontCss(source = null)

        assertTrue(css.faceCss.isEmpty())
        assertTrue(css.overrideCss.isEmpty())
        assertTrue(css.familyCss.contains("system-ui"))
    }

    @Test
    fun `explicit generic family is still forced`() {
        val css = ReaderSettings(fontFamily = "Serif")
            .readerWebFontCss(source = null)

        assertTrue(css.faceCss.isEmpty())
        assertEquals("Georgia, 'Times New Roman', serif", css.familyCss)
        assertTrue(css.overrideCss.contains("!important"), css.toString())
        assertFalse(css.overrideCss.contains("ReaderCustomFont"))
    }
}
