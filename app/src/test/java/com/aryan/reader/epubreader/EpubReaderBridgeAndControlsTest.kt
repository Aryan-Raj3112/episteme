package com.aryan.reader.epubreader

import android.webkit.WebView
import com.aryan.reader.RenderMode
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpubReaderBridgeAndControlsTest {

    @Test
    fun `sanitizePlaceholders keeps one header per toolbar section and inserts empty placeholders`() {
        val input = listOf(
            FlatToolItem("old_header", FlatItemType.SECTION_HEADER, section = ToolbarSection.BOTTOM),
            FlatToolItem("format", FlatItemType.TOOL, tool = ReaderTool.FORMAT, section = ToolbarSection.BOTTOM),
            FlatToolItem("more_header", FlatItemType.MORE_HEADER, title = "More"),
            FlatToolItem("reading_mode", FlatItemType.MORE_TOOL, tool = ReaderTool.READING_MODE)
        )

        val sanitized = sanitizePlaceholders(input)

        assertEquals(
            listOf(
                FlatItemType.SECTION_HEADER,
                FlatItemType.EMPTY_PLACEHOLDER,
                FlatItemType.SECTION_HEADER,
                FlatItemType.TOOL,
                FlatItemType.SECTION_HEADER,
                FlatItemType.EMPTY_PLACEHOLDER,
                FlatItemType.MORE_HEADER,
                FlatItemType.MORE_TOOL
            ),
            sanitized.map { it.type }
        )
        assertEquals(listOf(ToolbarSection.TOP, ToolbarSection.BOTTOM, ToolbarSection.HIDDEN), sanitized.filter { it.type == FlatItemType.SECTION_HEADER }.map { it.section })
        assertEquals(ReaderTool.FORMAT, sanitized.single { it.type == FlatItemType.TOOL }.tool)
    }

    @Test
    fun `auto scroll bridge invokes chapter end callback`() {
        var calls = 0

        AutoScrollJsBridge { calls++ }.onChapterEnd()

        assertEquals(1, calls)
    }

    @Test
    fun `tts bridge relays nonblank structured text and normalizes blank payloads`() = runTest {
        val received = CompletableDeferred<String>()
        val bridge = TtsJsBridge(scope = this, ttsStructuredTextHandler = { received.complete(it) })

        bridge.onStructuredTextExtracted("[{\"text\":\"Hello\"}]")

        assertEquals("[{\"text\":\"Hello\"}]", received.await())

        val blankReceived = CompletableDeferred<String>()
        TtsJsBridge(scope = this, ttsStructuredTextHandler = { blankReceived.complete(it) }).onStructuredTextExtracted("   ")
        assertEquals("[]", blankReceived.await())
    }

    @Test
    fun `highlight bridge forwards create and click events`() {
        var created: Triple<String, String, String>? = null
        var clicked: List<Any>? = null
        val bridge = HighlightJsBridge(
            onCreateCallback = { cfi, text, color -> created = Triple(cfi, text, color) },
            onClickCallback = { cfi, text, left, top, right, bottom ->
                clicked = listOf(cfi, text, left, top, right, bottom)
            }
        )

        bridge.onHighlightCreated("/4", "Text", "yellow")
        bridge.onHighlightClicked("/4", "Text", 1, 2, 3, 4)

        assertEquals(Triple("/4", "Text", "yellow"), created)
        assertEquals(listOf("/4", "Text", 1, 2, 3, 4), clicked)
    }

    @Test
    fun `content snippet progress footnote and ai bridges forward callbacks`() = runTest {
        var requestedChunk = -1
        var snippet = "" to ""
        var progressCalls = 0
        var lastChunk = -1
        var footnote = ""
        val aiContent = CompletableDeferred<String>()

        ContentBridge { requestedChunk = it }.requestChunk(7)
        SnippetJsBridge { cfi, text -> snippet = cfi to text }.onSnippetExtracted("/6", "Snippet")
        val progress = ProgressJsBridge {
            progressCalls++
            lastChunk = it
        }
        progress.updateTopChunk(2)
        progress.updateTopChunk(2)
        progress.updateTopChunk(3)
        FootnoteJsBridge { footnote = it }.onFootnoteRequested("<p>Note</p>")
        AiJsBridge(scope = this, onContentReady = { aiContent.complete(it) }).onContentExtractedForSummarization("Chapter text")

        assertEquals(7, requestedChunk)
        assertEquals("/6" to "Snippet", snippet)
        assertEquals(2, progressCalls)
        assertEquals(3, lastChunk)
        assertEquals("<p>Note</p>", footnote)
        assertEquals("Chapter text", aiContent.await())
    }

    @Test
    fun `ai bridge ignores blank content`() = runTest {
        var called = false

        AiJsBridge(scope = this, onContentReady = { called = true }).onContentExtractedForSummarization("   ")

        assertFalse(called)
    }

    @Test
    fun `cfi bridge parses save bookmark and scroll callbacks with fallback for invalid save json`() {
        val saved = mutableListOf<String>()
        val bookmark = mutableListOf<String>()
        val scrollResults = mutableListOf<Boolean>()
        val bridge = CfiJsBridge(
            onCfiReady = { saved.add(it) },
            onCfiForBookmarkReady = { bookmark.add(it) },
            onScrollFinishedCallback = { scrollResults.add(it) }
        )

        bridge.onCfiExtracted(JSONObject().put("cfi", "/4/2:8").put("log", JSONArray()).toString())
        bridge.onCfiExtracted(JSONObject().put("cfi", "").toString())
        bridge.onCfiExtracted("broken")
        bridge.onCfiForBookmarkExtracted(JSONObject().put("cfi", "/6/4:1").toString())
        bridge.onCfiForBookmarkExtracted("broken")
        bridge.onScrollFinished(true)
        bridge.onScrollFinished(false)

        assertEquals(listOf("/4/2:8", "/4"), saved)
        assertEquals(listOf("/6/4:1"), bookmark)
        assertEquals(listOf(true, false), scrollResults)
    }

    @Test
    fun `cfi bridge preserves full reading position cfi payloads for save and bookmark callbacks`() {
        val saved = mutableListOf<String>()
        val bookmark = mutableListOf<String>()
        val bridge = CfiJsBridge(
            onCfiReady = { saved.add(it) },
            onCfiForBookmarkReady = { bookmark.add(it) },
            onScrollFinishedCallback = {}
        )
        val cfi = "/6/4[chapter]!/4/2/8:137"

        bridge.onCfiExtracted(JSONObject().put("cfi", cfi).put("log", JSONArray().put("exact")).toString())
        bridge.onCfiForBookmarkExtracted(JSONObject().put("cfi", cfi).put("log", JSONArray()).toString())

        assertEquals(listOf(cfi), saved)
        assertEquals(listOf(cfi), bookmark)
    }

    @Test
    fun `updateAutoScrollJs emits start and stop commands`() {
        val webView = mockk<WebView>(relaxed = true)

        updateAutoScrollJs(webView, playing = true, speed = 1.25f)
        updateAutoScrollJs(webView, playing = false, speed = 9f)

        verify { webView.evaluateJavascript("javascript:window.autoScroll.start(1.25);", null) }
        verify { webView.evaluateJavascript("javascript:window.autoScroll.stop();", null) }
    }

    @Test
    fun `initiateTtsPlayback chooses web extraction for vertical mode and callback for paginated mode`() {
        val webView = mockk<WebView>(relaxed = true)
        var paginatedStarts = 0

        initiateTtsPlayback(RenderMode.VERTICAL_SCROLL, webView) { paginatedStarts++ }
        initiateTtsPlayback(RenderMode.PAGINATED, webView) { paginatedStarts++ }

        verify { webView.evaluateJavascript("javascript:TtsBridgeHelper.extractAndRelayText();", null) }
        assertEquals(1, paginatedStarts)
    }

    @Test
    fun `reader tool metadata has stable unique names and categories`() {
        assertEquals(ReaderTool.entries.size, ReaderTool.entries.map { it.name }.toSet().size)
        assertTrue(ReaderTool.entries.any { it.category == "Top Bar" })
        assertTrue(ReaderTool.entries.any { it.category == "Bottom Bar" })
        assertTrue(ReaderTool.entries.any { it.category == "Overflow Menu" })
    }
}
