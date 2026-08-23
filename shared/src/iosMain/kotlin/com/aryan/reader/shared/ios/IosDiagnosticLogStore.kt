package com.aryan.reader.shared.ios

import com.aryan.reader.shared.SharedDiagnosticLogBuffer
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.pdf.IosPdfOcrMetrics
import platform.Foundation.NSLock

/**
 * In-process diagnostics retained for the iOS "Export logs" action.
 *
 * Android exports `logcat -d`; the closest app-readable equivalent on iOS is
 * the process-scoped unified log ([OSLogStore]) plus the app-owned event
 * buffer. The unified log adds framework/os_log output the ring buffer never
 * sees, so exports include both, each bounded to keep the file shareable.
 */
internal object IosDiagnosticLogStore {
    private val buffer = SharedDiagnosticLogBuffer()
    private val lock = NSLock()

    /** Matches Android's bounded `logcat -d -t 5000` export size. */
    private const val MaxUnifiedLogEntries = 5_000

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

    /**
     * Recent process unified-log entries, oldest first; empty when unavailable.
     * The unified log is captured natively (OSLogStore) and handed to Kotlin
     * through the bridge's [ReaderIosBridge.unifiedDiagnosticsProvider].
     */
    fun unifiedLogSnapshot(provider: (() -> String?)?): List<String> {
        val captured = provider?.invoke().orEmpty()
        if (captured.isBlank()) return emptyList()
        val lines = captured.lines().filter(String::isNotBlank)
        return if (lines.size <= MaxUnifiedLogEntries) {
            lines
        } else {
            lines.subList(lines.size - MaxUnifiedLogEntries, lines.size)
        }
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
