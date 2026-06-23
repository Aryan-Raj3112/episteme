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
        assertTrue(js.contains("table-layout: fixed !important;"))
        assertTrue(js.contains("viewportContainmentCss"))
        assertTrue(js.contains("gapCss, viewportContainmentCss, imageCss"))
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
