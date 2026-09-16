package com.aryan.reader.epubreader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EpubReaderTtsHighlightAssetTest {

    @Test
    fun `tts highlight is constrained to one readable block and does not inherit spacing`() {
        val js = epubReaderAsset().readText()

        assertTrue(js.contains("const TTS_HIGHLIGHT_BLOCK_SELECTOR"))
        assertTrue(js.contains("getTtsHighlightBlock(baseNode)"))
        assertTrue(js.contains("document.createTreeWalker(highlightRoot, NodeFilter.SHOW_TEXT"))
        assertTrue(js.contains("text-align-last: auto !important;"))
        assertTrue(js.contains("letter-spacing: normal !important;"))
        assertTrue(js.contains("word-spacing: normal !important;"))
    }

    @Test
    fun `vertical webview CSS constrains chapter content to viewport width`() {
        val js = epubReaderAsset().readText()

        assertTrue(js.contains("overflow-x: hidden !important;"))
        assertTrue(js.contains("#content-container *,"))
        assertTrue(js.contains("body > *"))
        assertTrue(js.contains("max-width: 100% !important;"))
        assertTrue(js.contains("min-width: 0 !important;"))
        assertTrue(js.contains("overflow-wrap: anywhere !important;"))
        assertTrue(js.contains("viewportContainmentCss"))
        assertTrue(js.contains("gapCss, viewportContainmentCss, imageCss"))
    }

    @Test
    fun `vertical webview image sizing does not force authored images to full width`() {
        val js = epubReaderAsset().readText()

        assertTrue(js.contains("width: auto;"))
        assertTrue(js.contains("max-width: min(100%, calc(100% * var(--reader-image-size))) !important;"))
        assertTrue(!js.lineSequence().any { it.trim() == "width: min(100%, calc(100% * var(--reader-image-size))) !important;" })
    }

    @Test
    fun `vertical webview image css never collapses figure images to zero`() {
        val js = epubReaderAsset().readText()

        // Parent-relative max-height (100%/60% in Standard Ebooks local.css) resolves
        // to 0 against a not-yet-laid-out figure; viewport-relative caps cannot.
        assertTrue(js.contains("max-height: none !important;"))
        assertTrue(js.contains("max-height: 92vh !important;"))
        assertTrue(js.contains("body figure {"))
        assertTrue(js.contains("body figure img {"))
    }

    @Test
    fun `vertical webview linearizes shoulder-note asides without hiding them`() {
        val js = epubReaderAsset().readText()

        assertTrue(js.contains("div.aside {"))
        assertTrue(js.contains("float: none !important;"))
        assertTrue(js.contains("visibility: visible !important;"))
        assertTrue(js.contains("border: 1px solid currentColor !important;"))
    }

    @Test
    fun `vertical webview recovers fully collapsed zero by zero images`() {
        val js = epubReaderAsset().readText()

        assertTrue(js.contains("fully-collapsed-0x0"))
        assertTrue(js.contains("android_img_correct"))
        assertTrue(js.contains("android_img_corrected"))
    }

    @Test
    fun `vertical webview applies reader font weight and letter spacing`() {
        val js = epubReaderAsset().readText()

        assertTrue(js.contains("newFontWeight"))
        assertTrue(js.contains("newLetterSpacing"))
        assertTrue(js.contains("font-weight: ${'$'}{newFontWeight} !important;"))
        assertTrue(js.contains("letter-spacing: ${'$'}{newLetterSpacing}em !important;"))
    }

    private fun epubReaderAsset(): File {
        val candidates = listOf(
            File("src/main/assets/epub_reader.js"),
            File("app/src/main/assets/epub_reader.js")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate epub_reader.js from ${File(".").absolutePath}")
    }
}
