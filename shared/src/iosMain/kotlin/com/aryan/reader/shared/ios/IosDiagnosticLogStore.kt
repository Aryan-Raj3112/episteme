package com.aryan.reader.shared.ios

import com.aryan.reader.shared.SharedDiagnosticLogBuffer
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.pdf.IosPdfOcrMetrics
import platform.Foundation.NSLock

/**
 * In-process diagnostics retained for the iOS "Export logs" action.
 *
 * iOS does not provide an app-readable equivalent of Android's `logcat -d`.
 * Keeping a bounded, app-owned stream gives support a useful recent trace
 * without requesting private logging entitlements or retaining unbounded
 * reader output in memory.
 */
internal object IosDiagnosticLogStore {
    private val buffer = SharedDiagnosticLogBuffer()
    private val lock = NSLock()

    fun record(tag: String, message: String) {
        lock.lock()
        try {
            buffer.append("${currentTimestamp()} [$tag] $message")
        } finally {
            lock.unlock()
        }
    }

    fun snapshot(): List<String> {
        lock.lock()
        val entries = try {
            buffer.snapshot().toMutableList()
        } finally {
            lock.unlock()
        }
        val pdf = IosPdfPerformanceMetrics.snapshot()
        val ocr = IosPdfOcrMetrics.snapshot()
        if (pdf.hasData()) {
            entries += "${currentTimestamp()} [PdfPerformance] " +
                "interactions=${pdf.interactionCount} cameraUpdates=${pdf.cameraUpdateCount} " +
                "flings=${pdf.flingCount} frames=${pdf.frameCount} slowFrames=${pdf.slowFrameCount} " +
                "lastFrameMs=${pdf.lastFrameDurationMillis} maxFrameMs=${pdf.maxFrameDurationMillis} " +
                "renders=${pdf.renderCount} tileRenders=${pdf.tileRenderCount} " +
                "slowRenders=${pdf.slowRenderCount} " +
                "lastRender=${pdf.lastRenderWidthPx}x${pdf.lastRenderHeightPx} " +
                "lastRenderMs=${pdf.lastRenderDurationMillis} maxRenderMs=${pdf.maxRenderDurationMillis} " +
                "lastRenderBytes=${pdf.lastRenderBytes} peakRenderBytes=${pdf.peakRenderBytes}"
        }
        if (ocr.recognitionCount > 0 || ocr.cacheHits > 0) {
            entries += "${currentTimestamp()} [PdfOcr] cacheHits=${ocr.cacheHits} " +
                "recognitions=${ocr.recognitionCount} lastRecognitionMs=${ocr.lastRecognitionDurationMillis} " +
                "maxRecognitionMs=${ocr.maxRecognitionDurationMillis}"
        }
        return entries
    }

    fun clear() {
        lock.lock()
        try {
            buffer.clear()
        } finally {
            lock.unlock()
        }
        IosPdfPerformanceMetrics.reset()
        IosPdfOcrMetrics.reset()
    }

    private fun IosPdfPerformanceSnapshot.hasData(): Boolean =
        interactionCount > 0 || cameraUpdateCount > 0 || flingCount > 0 || frameCount > 0 || renderCount > 0
}
