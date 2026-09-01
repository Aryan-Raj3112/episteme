package com.aryan.reader.shared.ui

/**
 * Small platform port for PDF reader performance counters.
 *
 * Calls are intentionally primitive-only: the iOS implementation stores counters under a
 * lock, while Android keeps its existing logging/diagnostics behavior unchanged. No log
 * messages or collections are created on the gesture/render hot path.
 */
internal expect fun recordSharedPdfInteraction()
internal expect fun recordSharedPdfCameraUpdate()
internal expect fun recordSharedPdfFling()
internal expect fun recordSharedPdfFrame(durationMillis: Long)
