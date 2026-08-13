package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ui.SharedMobilePdfNativeAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderIosBridgeEffectsTest {
    @Test
    fun readerSystemEffectsPreserveTheInitialBrightnessUntilRestore() {
        val effects = FakeReaderSystemEffects(initialBrightness = 0.62)
        val bridge = bridge(effects = effects)

        bridge.setKeepScreenOn(true)
        bridge.setReaderBrightness(0.25f)
        bridge.setReaderBrightness(0.4f)
        bridge.restoreReaderBrightness()

        assertTrue(effects.keepScreenOn)
        assertEquals(listOf(0.25, 0.4, 0.62), effects.brightnessWrites.map(::rounded))

        bridge.setKeepScreenOn(false)
        assertFalse(effects.keepScreenOn)
    }

    @Test
    fun lifecycleEventsAreMonotonicEvenWhenTheActiveValueRepeats() {
        val bridge = bridge()
        val initialEventId = bridge.appLifecycleState.eventId

        bridge.updateAppActive(false)
        bridge.updateAppActive(false)
        bridge.updateAppActive(true)

        assertTrue(bridge.appLifecycleState.isActive)
        assertEquals(initialEventId + 3, bridge.appLifecycleState.eventId)
    }

    @Test
    fun pdfNativeActionsAreForwardedThroughTheInjectablePresenter() {
        val calls = mutableListOf<Pair<String, SharedMobilePdfNativeAction>>()
        val bridge = ReaderIosBridge(
            readerSystemEffects = FakeReaderSystemEffects(),
            pdfNativeActionPresenter = IosPdfNativeActionPresenter { book, action ->
                calls += book.id to action
                action == SharedMobilePdfNativeAction.PRINT
            },
        )
        val book = BookItem(
            id = "pdf",
            path = "/tmp/Book.pdf",
            type = FileType.PDF,
            displayName = "Book.pdf",
            timestamp = 1L,
        )

        assertFalse(bridge.performPdfNativeAction(book, SharedMobilePdfNativeAction.SHARE))
        assertTrue(bridge.performPdfNativeAction(book, SharedMobilePdfNativeAction.PRINT))
        assertEquals(
            listOf(
                "pdf" to SharedMobilePdfNativeAction.SHARE,
                "pdf" to SharedMobilePdfNativeAction.PRINT,
            ),
            calls,
        )
    }

    private fun bridge(
        effects: IosReaderSystemEffects = FakeReaderSystemEffects(),
    ): ReaderIosBridge = ReaderIosBridge(
        readerSystemEffects = effects,
        pdfNativeActionPresenter = IosPdfNativeActionPresenter { _, _ -> false },
    )
}

private class FakeReaderSystemEffects(
    initialBrightness: Double = 0.5,
) : IosReaderSystemEffects {
    override var brightness: Double = initialBrightness
        private set
    var keepScreenOn: Boolean = false
        private set
    val brightnessWrites = mutableListOf<Double>()

    override fun setKeepScreenOn(enabled: Boolean) {
        keepScreenOn = enabled
    }

    override fun setBrightness(value: Double) {
        brightness = value
        brightnessWrites += value
    }
}

private fun rounded(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
