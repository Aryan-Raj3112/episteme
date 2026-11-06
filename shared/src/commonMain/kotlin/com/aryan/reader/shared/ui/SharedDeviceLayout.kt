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
 *
 * Foldable iPhones put an app's navigation on the *side* of the display, sharing one
 * vertical column with the system's Dynamic Island and status bar. That column is measured
 * by the platform, so the values below describe it rather than hard-coding a device.
 */
data class SharedDeviceLayout(
    val fold: SharedFoldRegion = SharedFoldRegion.None,
    /**
     * Width of the platform's side control column, as a fraction of the window width
     * (0 when the display has no side column, e.g. an ordinary phone or the inner display
     * held upright). This is the strip the system reserves for the Dynamic Island and
     * status bar.
     */
    val controlColumnWidthFraction: Float = 0f,
    /**
     * Fraction of the window height, from the top, that the system's status area occupies
     * inside that column. The app's own controls start below it.
     */
    val controlColumnTopFraction: Float = 0f,
    /** True when the control column is on the trailing (right in LTR) edge. */
    val controlColumnOnTrailing: Boolean = true,
    /**
     * True on any iPhone Duo display, inner or outer, as reported by the platform's
     * reserved regions. This is the device capability the rail is gated on: an ordinary
     * iPhone, iPad, Android device or desktop never sets it.
     */
    val isDuoDevice: Boolean = false,
) {
    /**
     * True when this display participates in a fold division — i.e. the iPhone Duo's
     * inner display, flat or partially folded. False on the Duo's outer display, which is
     * narrow enough to keep the ordinary grid mapping.
     */
    val isDuoInnerDisplay: Boolean get() = fold.present

    /**
     * True when the display actually reserves a side column for the status/Dynamic Island.
     * False on the upright inner display, whose status bar runs across the top instead —
     * the one pose Apple keeps horizontal bars for.
     */
    val hasControlColumn: Boolean get() = controlColumnWidthFraction > 0.001f

    companion object {
        val None = SharedDeviceLayout()
    }
}

/**
 * Provided once at the app root. Defaults to [SharedDeviceLayout.None] so Android, the
 * desktop app and every non-Duo iOS device compose exactly as they did before.
 */
val LocalSharedDeviceLayout = staticCompositionLocalOf { SharedDeviceLayout.None }
