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
        // to 0 against a not-yet-laid-out figure; CSS vh units also resolve to 0 in
        // some Android WebViews, so the cap is a JS-measured px value with a `none`
        // fallback that can never collapse. Svg covers that ask for 100% width keep it.
        assertTrue(js.contains("max-height: none !important;"))
        assertTrue(js.contains("max-height: var(--reader-image-max-h, none) !important;"))
        assertTrue(js.contains("--reader-image-max-h"))
        assertTrue(js.contains("body svg[width=\"100%\"]"))
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

    @Test
    fun `asset has no backticks inside comments that would terminate template literals`() {
        val js = epubReaderAsset().readText()

        // A stray backtick inside the CSS template literal terminated the
        // string early (Unexpected identifier 'span'), which killed the whole
        // script block: every window.* reader function became "not a function".
        val withoutBlockComments = js.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        val backticksInComments = js.count { it == '`' } - withoutBlockComments.count { it == '`' }
        assertTrue("backticks inside /* */ comments would break template literals", backticksInComments == 0)
        assertTrue(
            "unbalanced backticks would break template literals",
            withoutBlockComments.count { it == '`' } % 2 == 0
        )
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
