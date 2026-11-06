package com.aryan.reader.shared.ios

import com.aryan.reader.shared.ui.SharedDeviceLayout
import com.aryan.reader.shared.ui.SharedFoldRegion

/**
 * The fold geometry the iOS host reports for iPhone Duo.
 *
 * Primitives only, and the rectangle is in fractions of the window bounds, so nothing has
 * to be converted between UIKit points and Compose dp on the way across the bridge.
 * Swift computes this from `UIView.reservedRegions` because Kotlin/Native cannot be
 * assumed to expose iOS 27.1 symbols.
 */
internal data class IosDeviceLayoutSnapshot(
    val foldPresent: Boolean = false,
    val foldActive: Boolean = false,
    val foldLeftFraction: Double = 0.0,
    val foldTopFraction: Double = 0.0,
    val foldWidthFraction: Double = 0.0,
    val foldHeightFraction: Double = 0.0,
)

internal fun IosDeviceLayoutSnapshot.toSharedDeviceLayout(): SharedDeviceLayout {
    if (!foldPresent) return SharedDeviceLayout.None
    return SharedDeviceLayout(
        fold = SharedFoldRegion(
            present = true,
            active = foldActive,
            leftFraction = foldLeftFraction.toFloat(),
            topFraction = foldTopFraction.toFloat(),
            widthFraction = foldWidthFraction.toFloat(),
            heightFraction = foldHeightFraction.toFloat(),
        ),
    )
}
