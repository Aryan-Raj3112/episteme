package com.aryan.reader.shared.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Shared auto-scroll contract for the mobile readers, mirroring Android's
 * `AutoScrollControls` (EpubReaderControls.kt) + its per-reader placement.
 *
 * Android renders the same control for EPUB and PDF, so the speed maths,
 * the label format and the chrome anchoring live here once instead of being
 * re-derived (and drifting) inside each reader's overlay.
 */
const val SharedMobileAutoScrollMinSpeed = 0.1f
const val SharedMobileAutoScrollMaxSpeed = 10f
const val SharedMobileAutoScrollSpeedStep = 0.1f

/** Android: `val speedOptions = listOf(0.1f, 0.5f, 1f, 1.5f, ..., 10f)`. */
val sharedMobileAutoScrollSpeedOptions: List<Float> =
    listOf(0.1f, 0.5f, 1f, 1.5f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)

/**
 * Snaps a speed to the 0.1 grid. Android does this on the slider
 * (`(it * 10f).roundToInt() / 10f`) and formats every readout as `"%.1f"`,
 * which is why Android never shows float noise. The iOS stepper used to store
 * the raw `speed + 0.1f` result, so the readout drifted to values such as
 * `3.999999`; snapping at the boundary keeps the stored value and the label in
 * agreement and matches Android's visible behaviour exactly.
 */
fun snapSharedMobileAutoScrollSpeed(value: Float): Float {
    val clamped = value.coerceIn(SharedMobileAutoScrollMinSpeed, SharedMobileAutoScrollMaxSpeed)
    return (clamped * 10f).roundToInt() / 10f
}

/** Android: `"%.1fx".format(speed)` — always one decimal, no float noise. */
fun sharedMobileAutoScrollSpeedLabel(value: Float): String {
    val tenths = (value.coerceIn(SharedMobileAutoScrollMinSpeed, SharedMobileAutoScrollMaxSpeed) * 10f)
        .roundToInt()
    return "${tenths / 10}.${tenths % 10}x"
}

/** Total width padding Android applies to the auto-scroll overlay (`16.dp`). */
val SharedMobileAutoScrollHorizontalPadding = 16.dp

/**
 * Android EpubReaderScreen: `if (showBars) bottomPadding + 45.dp + 16.dp else 32.dp`.
 * The old iOS overlay hard-coded a 52.dp lift from the screen edge, which
 * ignored the home-indicator inset and sat on top of the bottom bar.
 */
fun sharedMobileEpubAutoScrollBottomPadding(chromeVisible: Boolean, bottomInset: Dp): Dp =
    if (chromeVisible) SharedReaderEpubBottomBarHeight + 16.dp + bottomInset else 32.dp

/**
 * Android PdfViewerScreen: `if (showBottomBar) 56.dp + 16.dp + inset else 16.dp + inset`.
 */
fun sharedMobilePdfAutoScrollBottomPadding(chromeVisible: Boolean, bottomInset: Dp): Dp =
    if (chromeVisible) SharedReaderPdfBottomBarHeight + 16.dp + bottomInset else 16.dp + bottomInset

/**
 * Android keeps the control hugging the right edge while collapsed and centred
 * while expanded (`alignmentBias = if (isAutoScrollCollapsed) 1f else 0f`).
 */
fun sharedMobileAutoScrollAlignmentBias(isCollapsed: Boolean): Float = if (isCollapsed) 1f else 0f
