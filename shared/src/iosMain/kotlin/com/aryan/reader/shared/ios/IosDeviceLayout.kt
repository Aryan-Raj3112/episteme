package com.aryan.reader.shared.ios

import com.aryan.reader.shared.ui.SharedDeviceLayout
import com.aryan.reader.shared.ui.SharedFoldRegion

/**
 * The fold and side-column geometry the iOS host reports for iPhone Duo.
 *
 * Primitives only, and every rectangle is a fraction of the window, so nothing has to be
 * converted between UIKit points and Compose dp on the way across the bridge. Swift
 * computes this from `UIView.reservedRegions` and `UIStatusBarManager` because
 * Kotlin/Native cannot be assumed to expose iOS 27.1 symbols.
 */
internal data class IosDeviceLayoutSnapshot(
    val foldPresent: Boolean = false,
    val foldActive: Boolean = false,
    val foldLeftFraction: Double = 0.0,
    val foldTopFraction: Double = 0.0,
    val foldWidthFraction: Double = 0.0,
    val foldHeightFraction: Double = 0.0,
    /** Width of the system's side control column (Dynamic Island + status), as a window fraction. */
    val controlColumnWidthFraction: Float = 0f,
    /** How far down the window the system's status area reaches inside that column. */
    val controlColumnTopFraction: Float = 0f,
    val controlColumnOnTrailing: Boolean = true,
    /** True on any Duo display, from the platform's fold or occlusion reserved regions. */
    val isDuoDevice: Boolean = false,
)

internal fun IosDeviceLayoutSnapshot.toSharedDeviceLayout(): SharedDeviceLayout {
    if (!isDuoDevice) return SharedDeviceLayout.None
    return SharedDeviceLayout(
        fold = SharedFoldRegion(
            present = foldPresent,
            active = foldActive,
            leftFraction = foldLeftFraction.toFloat(),
            topFraction = foldTopFraction.toFloat(),
            widthFraction = foldWidthFraction.toFloat(),
            heightFraction = foldHeightFraction.toFloat(),
        ),
        controlColumnWidthFraction = controlColumnWidthFraction,
        controlColumnTopFraction = controlColumnTopFraction,
        controlColumnOnTrailing = controlColumnOnTrailing,
        isDuoDevice = true,
    )
}
