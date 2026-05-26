package com.aryan.reader.epubreader

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ChapterWebViewBridgeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun cfiJsBridge_onCfiExtracted_callsCallbackWithCorrectCfi() {
        var receivedCfi = ""
        val bridge = CfiJsBridge(
            onCfiReady = { cfi -> receivedCfi = cfi },
            onCfiForBookmarkReady = {},
            onScrollFinishedCallback = {}
        )

        val cfi = "/4/2[chapter1]/6:10"
        val jsonResponse = JSONObject().apply {
            put("cfi", cfi)
            put("log", JSONArray(listOf("log message 1", "log message 2")))
        }.toString()

        bridge.onCfiExtracted(jsonResponse)

        assertThat(receivedCfi).isEqualTo(cfi)
    }

    @Test
    fun cfiJsBridge_onCfiExtracted_withInvalidJson_callsCallbackWithFallbackCfi() {
        var receivedCfi = ""
        val bridge = CfiJsBridge(
            onCfiReady = { cfi -> receivedCfi = cfi },
            onCfiForBookmarkReady = {},
            onScrollFinishedCallback = {}
        )

        val invalidJson = "this is not json"

        bridge.onCfiExtracted(invalidJson)

        assertThat(receivedCfi).isEqualTo("/4")
    }

    @Test
    fun cfiJsBridge_onCfiExtracted_withEmptyCfi_callsCallbackWithCfi() {
        var receivedCfi: String? = null
        val bridge = CfiJsBridge(
            onCfiReady = { cfi -> receivedCfi = cfi },
            onCfiForBookmarkReady = {},
            onScrollFinishedCallback = {}
        )

        val jsonResponse = JSONObject().apply {
            put("cfi", "")
            put("log", JSONArray())
        }.toString()

        bridge.onCfiExtracted(jsonResponse)

        // The handler is only called if the CFI is not blank, so it should remain null
        assertThat(receivedCfi).isNull()
    }

    @Test
    fun ttsJsBridge_onStructuredTextExtracted_callsHandlerWithJson() {
        val latch = CountDownLatch(1)
        var receivedJson: String? = null
        val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val bridge = TtsJsBridge(
                scope = bridgeScope,
                ttsStructuredTextHandler = { json ->
                    receivedJson = json
                    latch.countDown()
                }
            )
            val jsonPayload = "[{\"text\":\"Hello world\",\"cfi\":\"/4/2\"}]"

            bridge.onStructuredTextExtracted(jsonPayload)

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(receivedJson).isEqualTo(jsonPayload)
        } finally {
            bridgeScope.cancel()
        }
    }

    @Test
    fun ttsJsBridge_onStructuredTextExtracted_withEmptyJson_callsHandlerWithEmptyArray() {
        val latch = CountDownLatch(1)
        var receivedJson: String? = null
        val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val bridge = TtsJsBridge(
                scope = bridgeScope,
                ttsStructuredTextHandler = { json ->
                    receivedJson = json
                    latch.countDown()
                }
            )
            val jsonPayload = ""

            bridge.onStructuredTextExtracted(jsonPayload)

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(receivedJson).isEqualTo("[]")
        } finally {
            bridgeScope.cancel()
        }
    }

    @Test
    fun snippetJsBridge_onSnippetExtracted_callsCallbackWithCfiAndSnippet() {
        var receivedCfi = ""
        var receivedSnippet = ""
        val bridge = SnippetJsBridge(
            onSnippetReady = { cfi, snippet ->
                receivedCfi = cfi
                receivedSnippet = snippet
            }
        )

        val cfi = "/4/8:5"
        val snippet = "This is the bookmark snippet."
        bridge.onSnippetExtracted(cfi, snippet)

        assertThat(receivedCfi).isEqualTo(cfi)
        assertThat(receivedSnippet).isEqualTo(snippet)
    }

    @Test
    fun progressJsBridge_onTopChunkUpdated_invokesCallback() {
        var updatedChunk = -1
        val bridge = ProgressJsBridge(onTopChunkUpdated = { index -> updatedChunk = index })

        bridge.updateTopChunk(5)
        assertThat(updatedChunk).isEqualTo(5)

        bridge.updateTopChunk(10)
        assertThat(updatedChunk).isEqualTo(10)
    }

    @Test
    fun progressJsBridge_onTopChunkUpdated_doesNotCallForSameIndex() {
        var callCount = 0
        val bridge = ProgressJsBridge(onTopChunkUpdated = { callCount++ })

        bridge.updateTopChunk(3)
        assertThat(callCount).isEqualTo(1)

        // Reporting the same index should not trigger the callback again
        bridge.updateTopChunk(3)
        assertThat(callCount).isEqualTo(1)

        bridge.updateTopChunk(4)
        assertThat(callCount).isEqualTo(2)
    }

    @Test
    fun aiJsBridge_onContentExtracted_invokesCallback() = runTest {
        var receivedContent: String? = null
        val bridge = AiJsBridge(
            scope = this,
            onContentReady = { content -> receivedContent = content }
        )
        val content = "This is the chapter content for summarization."
        bridge.onContentExtractedForSummarization(content)
        advanceUntilIdle()
        assertThat(receivedContent).isEqualTo(content)
    }
}
