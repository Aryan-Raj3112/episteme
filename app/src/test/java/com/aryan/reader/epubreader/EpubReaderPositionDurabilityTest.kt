package com.aryan.reader.epubreader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The vertical reader can only persist its position once the virtualized restore scroll has
 * settled, and it must be able to flush the position it already resolved when the app leaves the
 * foreground. Both behaviours live inside the reader composable, so these invariants are guarded
 * at the source level the way [com.aryan.reader.StartupDeferralPolicyTest] does for startup.
 */
class EpubReaderPositionDurabilityTest {

    @Test
    fun `scroll stop auto save is gated on the restore settle state`() {
        val source = readEpubReaderScreenSource()
        val debounce = source.substringAfter("if (!shouldSaveVerticalOpenPosition(")
            .substringBefore("webViewRefForTts?.evaluateJavascript")

        assertTrue(debounce.contains("isRestoreSettled = isVerticalRestoreSettled"))
        assertTrue(debounce.contains("delay(1500L)"))
    }

    @Test
    fun `lifecycle observer flushes the resolved position on pause and stop`() {
        val source = readEpubReaderScreenSource()
        val observer = source.substringAfter("val observer = LifecycleEventObserver")
            .substringBefore("lifecycleOwner.lifecycle.addObserver(observer)")

        assertTrue(observer.contains("Lifecycle.Event.ON_PAUSE"))
        assertTrue(observer.contains("Lifecycle.Event.ON_STOP"))
        assertTrue(observer.contains("latestFlushInMemoryReadingPosition(\"lifecycle_pause\")"))
        assertTrue(observer.contains("latestFlushInMemoryReadingPosition(\"lifecycle_stop\")"))
    }

    @Test
    fun `reader teardown flushes the resolved position`() {
        val source = readEpubReaderScreenSource()
        val dispose = source.substringAfter("DisposableEffect(Unit) {")
            .substringBefore("LaunchedEffect(verticalRestoreStartedAtMillis")

        assertTrue(dispose.contains("flushInMemoryReadingPosition(\"reader_dispose\")"))
    }

    @Test
    fun `tab switches capture the live position and switch only afterwards`() {
        val source = readEpubReaderScreenSource()
        // Both the strip and the drawer route a tab tap through the same sequence: capture first,
        // switch second. A flush-only capture would reopen at a stale locator, and a switch that
        // does not wait for the capture would read the row before the write lands.
        listOf(
            source.substringAfter("val textTabStrip:"),
            source.substringAfter("onTextTabSelected = { tabBookId ->"),
            source.substringAfter("onPick = { tabBookId ->"),
        ).forEach { callSite ->
            val capture = callSite.substringAfter("captureLiveReadingPosition").substringBefore("}")
            val switch = capture.substringAfter("viewModel.switchTab")
            assertTrue(switch.isNotBlank())
        }
    }

    @Test
    fun `live capture waits for the webview and falls back to the flush`() {
        val source = readEpubReaderScreenSource()
        val capture = source.substringAfter("suspend fun captureLiveReadingPosition")
            .substringBefore("fun ensureVerticalChunksLoaded")

        assertTrue(capture.contains("withTimeoutOrNull(CAPTURE_LIVE_POSITION_TIMEOUT_MS)"))
        assertTrue(capture.contains("readingPositionCfiFromJavascriptResult"))
        assertTrue(capture.contains("flushInMemoryReadingPosition(reason)"))
        assertTrue(capture.contains("saveResolvedLocatorPosition"))
    }

    @Test
    fun `text tab switch joins pending position writes before reopening`() {
        val source = readSource("MainViewModel.kt")
        val switch = source.substringAfter("ReaderTabKind.TEXT -> viewModelScope.launch {")
            .substringBefore("openBook(")

        assertTrue(switch.contains("awaitAllPendingReaderStateWrites()"))
    }

    @Test
    fun `restore settle state is cleared by the scroll finished signal`() {
        val source = readEpubReaderRenderSurfacesSource()
        val callback = source.substringAfter("onScrollFinished = { success ->")
            .substringBefore("ttsScope = scope")

        assertTrue(callback.contains("isVerticalRestoreSettled = true"))
    }

    private fun readEpubReaderScreenSource(): String =
        readSource("epubreader/EpubReaderScreen.kt")

    private fun readEpubReaderRenderSurfacesSource(): String =
        readSource("epubreader/EpubReaderRenderSurfaces.kt")

    private fun readSource(relativePath: String): String {
        return listOf(
            File("src/main/java/com/aryan/reader/$relativePath"),
            File("app/src/main/java/com/aryan/reader/$relativePath")
        ).first { it.isFile }.readText()
    }
}
