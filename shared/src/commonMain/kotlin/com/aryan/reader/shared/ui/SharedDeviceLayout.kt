package com.aryan.reader.shared.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The fold/hinge *division* region reported by a foldable display.
 *
 * The rectangle is normalised to the window bounds (0..1) because the platform that
 * produces it (UIKit on iOS) works in points and Compose works in dp. Normalising means
 * callers multiply by the dp size Compose already measured, so there is no unit
 * conversion and no chance of a density mistake.
 *
 * [active] is false when the device is flat; the region is still [present] in that case,
 * which is what makes it usable as a "this is a foldable display" capability signal.
 */
data class SharedFoldRegion(
    val present: Boolean = false,
    val active: Boolean = false,
    val leftFraction: Float = 0f,
    val topFraction: Float = 0f,
    val widthFraction: Float = 0f,
    val heightFraction: Float = 0f,
) {
    companion object {
        val None = SharedFoldRegion()
    }
}

/**
 * Everything the shared UI knows about the physical display beyond its size.
 *
 * This is deliberately platform-neutral (not named for iOS) so the reader can consume it
 * later. Its default value, [None], means "an ordinary single-screen display": every
 * consumer must behave exactly as it did before this type existed when it reads [None].
 */
data class SharedDeviceLayout(
    val fold: SharedFoldRegion = SharedFoldRegion.None,
) {
    /**
     * True when this display participates in a fold division — i.e. the iPhone Duo's
     * inner display, flat or partially folded. False on the Duo's outer display and on
     * every ordinary phone and tablet, which is exactly the set of surfaces that must
     * keep the pre-Duo layout.
     */
    val isDuoInnerDisplay: Boolean get() = fold.present

    companion object {
        val None = SharedDeviceLayout()
    }
}

/**
 * Provided once at the app root. Defaults to [SharedDeviceLayout.None] so Android, the
 * desktop app and every non-Duo iOS device compose exactly as they did before.
 */
val LocalSharedDeviceLayout = staticCompositionLocalOf { SharedDeviceLayout.None }
