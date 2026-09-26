package com.aryan.reader.shared.reader

/**
 * How long a rotation waits before the reading position is restored.
 *
 * Android is the benchmark: `EpubReaderScreen` lets the vertical WebView
 * relayout for 300 ms and only then scrolls it back to the CFI it held, so the
 * restore does not race the reflow it is compensating for.
 */
const val SharedReaderOrientationRestoreDelayMillis = 300L

/**
 * Whether a viewport change flipped the reader between portrait and landscape.
 *
 * Android keys its orientation work on `Configuration.orientation`; shared
 * mobile code only sees the Compose viewport, so the same coarse signal is
 * derived from the aspect flip. Zero, negative, and square viewports never
 * report a flip, which keeps the first real layout, split-divider drags, and
 * chrome inset changes from being mistaken for a rotation.
 */
fun sharedReaderViewportFlippedOrientation(
    previousWidthPx: Int,
    previousHeightPx: Int,
    currentWidthPx: Int,
    currentHeightPx: Int,
): Boolean {
    if (previousWidthPx <= 0 || previousHeightPx <= 0) return false
    if (currentWidthPx <= 0 || currentHeightPx <= 0) return false
    return (previousWidthPx > previousHeightPx) != (currentWidthPx > currentHeightPx)
}
