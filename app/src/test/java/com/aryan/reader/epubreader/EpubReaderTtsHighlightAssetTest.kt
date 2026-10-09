package com.aryan.reader.epubreader

import org.junit.Assert.assertFalse
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
    fun `vertical webview tts starts at the top visible line of its block`() {
        val js = epubReaderAsset().readText()

        // A chapter whose prose is one element has no per-line element to start from, so the block
        // the viewport starts in must be sliced at its first visible line instead of at its start.
        assertTrue(js.contains("function topVisibleOffsetWithinBlock(block, readingTop)"))
        assertTrue(js.contains("const lineOffset = topVisibleOffsetWithinBlock(node, viewportTop);"))
        assertTrue(js.contains("results.push({ cfi: cfiObj, text: sliced, startOffset: lineOffset });"))
        // The line is found from layout, not from document order, and the offset it produces is
        // measured in the text that is emitted.
        assertTrue(js.contains("function readerTtsFirstVisibleLineRect(nodes, readingTop)"))
        assertTrue(js.contains("function readerTtsRenderedTextOffset(block, node, offset)"))
        // Block and line read the same top, and that top is the reading area itself. Pushing it
        // further down skips the line straddling the top, which is the line the reader is looking
        // at - the rule native vertical uses when it takes the line containing the viewport top.
        assertTrue(js.contains("if (rect.bottom <= readingTop) continue;"))
        assertTrue(js.contains("if (rect.bottom > viewportTop) {"))
        val extraction = js
            .substringAfter("window.extractTextWithCfiFromTop = function ()")
            .substringBefore("\n    window.")
        assertFalse(
            "the reading-area top must be used as given, without a guard that skips the top line",
            extraction.contains("+ 10") || extraction.contains("+10")
        )
    }

    @Test
    fun `vertical webview tts highlight is placed by the text it narrates`() {
        val js = epubReaderAsset().readText()

        // Stored offsets are measured in the text pagination produced while a text-node walk
        // counts raw characters, so the offset places the range approximately. The spoken text is
        // in the same block and places it exactly.
        assertTrue(js.contains("function readerTtsRangeForText(root, text, hintOffset)"))
        assertTrue(js.contains("let range = readerTtsRangeForText(highlightRoot, textToHighlight, hintOffset);"))
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

        // A decoded bitmap with no layout box is unambiguously broken, whatever the
        // publication CSS did: Standard Ebooks figures collapse through a parent-relative
        // max-height, and fixed-layout comics/manga collapse through the containment rule's
        // percentage max-width resolving against a zero-width position:absolute wrapper.
        assertTrue(js.contains("function recoverCollapsedReaderImage(img)"))
        assertTrue(js.contains("window.recoverCollapsedReaderImages = function ()"))
        assertTrue(js.contains("if (!img || img.naturalWidth <= 0 || img.naturalHeight <= 0) return false;"))
        assertTrue(js.contains("recoverCollapsedReaderImage(img);"))
        assertTrue(js.contains("android_img_recovered"))
    }

    @Test
    fun `vertical webview recovery overrides the percentage caps that defeat it`() {
        val js = epubReaderAsset().readText()

        // The recovery used to set only width/height, so `max-width: min(100%, ..)` from
        // imageCss re-clamped the width to 0 against the collapsed wrapper and the image
        // stayed invisible. Every bound has to be pinned inline, where !important
        // outranks the injected stylesheet.
        assertTrue(js.contains("""img.style.setProperty("width", width + "px", "important");"""))
        assertTrue(js.contains("""img.style.setProperty("max-width", width + "px", "important");"""))
        assertTrue(js.contains("""img.style.setProperty("min-width", width + "px", "important");"""))
        assertTrue(js.contains("""img.style.setProperty("height", height + "px", "important");"""))
        assertTrue(js.contains("""img.style.setProperty("max-height", height + "px", "important");"""))
        assertTrue(js.contains("""img.style.setProperty("min-height", height + "px", "important");"""))
    }

    @Test
    fun `vertical webview recovery releases only degenerate fixed layout wrappers`() {
        val js = epubReaderAsset().readText()

        assertTrue(js.contains("function releaseDegenerateReaderAncestors(img)"))
        // The walk stops at the content box, so reflowable chapters keep authored geometry.
        assertTrue(js.contains("var boundary = document.getElementById(\"content-container\") || document.body;"))
        assertTrue(js.contains("while (node && node !== boundary && node !== document.body && node !== document.documentElement) {"))
        assertTrue(js.contains("if (node.clientWidth === 0 && positioned) {"))
        assertTrue(js.contains("releasedAncestors="))
    }

    @Test
    fun `vertical webview re-runs recovery after style and chunk updates`() {
        val js = epubReaderAsset().readText()

        assertTrue(js.contains("if (window.recoverCollapsedReaderImages) {"))
        assertTrue(js.contains("setTimeout(window.checkImagesForDiagnosis, 100);"))
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
