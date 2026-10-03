package com.aryan.reader.shared.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/** The min margin above and below the menu, relative to the screen. */
internal val SharedDropdownMenuVerticalMargin = SharedDropdownMenuMetrics.VerticalMargin

/** Where a [SharedDropdownMenu] should be drawn, and the origin it scales from. */
internal data class SharedDropdownMenuPlacement(
    val offset: IntOffset,
    val transformOrigin: TransformOrigin,
)

/**
 * The anchor and the drawable area, expressed in the coordinate space the menu is actually
 * painted in.
 *
 * A `Popup` is its own window, and that window is **not** laid out over the whole screen: Android
 * positions it at `getWindowVisibleDisplayFrame`, so its content starts below the status bar and
 * ends above the navigation bar. Everything measured in the host window is therefore in
 * screen/host coordinates, while the card is painted in popup-content coordinates, and the two
 * differ by exactly those insets. On a 1080x2400 phone with a 74px status bar the reader's
 * overflow menu landed 74px below its button, which read as a gap between the toolbar and the menu.
 *
 * Doing this conversion up front — rather than computing a screen-space placement and subtracting
 * the popup origin afterwards — also fixes the fit checks: the menu must fit the popup's drawable
 * area (2400 - 74 - 63 = 2263px tall here), not the full container height, or a tall menu near the
 * bottom of the screen is allowed to overflow off-screen.
 *
 * @param anchorBounds the anchor in host-window coordinates.
 * @param containerSize the host window's full size, as `LocalWindowInfo.containerSize`.
 * @param insets how far the popup window's content origin sits inside the host window.
 */
internal fun sharedDropdownMenuPopupSpace(
    anchorBounds: IntRect,
    containerSize: IntSize,
    insets: SharedDropdownMenuInsets,
): PopupSpace {
    val anchor = IntRect(
        left = anchorBounds.left - insets.left,
        top = anchorBounds.top - insets.top,
        right = anchorBounds.right - insets.left,
        bottom = anchorBounds.bottom - insets.top,
    )
    val drawable = IntSize(
        width = (containerSize.width - insets.left - insets.right).coerceAtLeast(0),
        height = (containerSize.height - insets.top - insets.bottom).coerceAtLeast(0),
    )
    return PopupSpace(anchor = anchor, drawableSize = drawable)
}

/** How far a popup window's content origin sits inside its host window, per edge. */
internal data class SharedDropdownMenuInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** Anchor and drawable area in popup-content coordinates. */
internal data class PopupSpace(
    val anchor: IntRect,
    val drawableSize: IntSize,
)

/**
 * Android benchmark (`DropdownMenuPositionProvider` in material3's `Menu.kt`).
 *
 * Kept as a pure function so the placement rules are unit-testable on the host JVM
 * and so [SharedDropdownMenu] can run the identical algorithm without a `Popup`.
 */
internal fun sharedDropdownMenuPlacement(
    anchorBounds: IntRect,
    menuSize: IntSize,
    windowSize: IntSize,
    contentOffset: DpOffset,
    density: Density,
    layoutDirection: LayoutDirection,
): SharedDropdownMenuPlacement {
    val verticalMargin = with(density) { SharedDropdownMenuVerticalMargin.roundToPx() }
    val contentOffsetX = with(density) {
        contentOffset.x.roundToPx() * if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    }
    val contentOffsetY = with(density) { contentOffset.y.roundToPx() }

    val leftToAnchorLeft = anchorBounds.left + contentOffsetX
    val rightToAnchorRight = anchorBounds.right - menuSize.width + contentOffsetX
    val rightToWindowRight = windowSize.width - menuSize.width
    val leftToWindowLeft = 0
    val x = if (layoutDirection == LayoutDirection.Ltr) {
        sequenceOf(
            leftToAnchorLeft,
            rightToAnchorRight,
            // If the anchor gets outside of the window on the left, position to the
            // window's left edge for proximity to the anchor. Otherwise to the right.
            if (anchorBounds.left >= 0) rightToWindowRight else leftToWindowLeft,
        )
    } else {
        sequenceOf(
            rightToAnchorRight,
            leftToAnchorLeft,
            if (anchorBounds.right <= windowSize.width) leftToWindowLeft else rightToWindowRight,
        )
    }
        .firstOrNull { it >= 0 && it + menuSize.width <= windowSize.width }
        ?: rightToAnchorRight

    val topToAnchorBottom = maxOf(anchorBounds.bottom + contentOffsetY, verticalMargin)
    val bottomToAnchorTop = anchorBounds.top - menuSize.height + contentOffsetY
    val centerToAnchorTop = anchorBounds.top - menuSize.height / 2 + contentOffsetY
    val bottomToWindowBottom = windowSize.height - menuSize.height - verticalMargin
    val y = sequenceOf(
        topToAnchorBottom,
        bottomToAnchorTop,
        centerToAnchorTop,
        bottomToWindowBottom,
    )
        .firstOrNull {
            it >= verticalMargin && it + menuSize.height <= windowSize.height - verticalMargin
        }
        ?: bottomToAnchorTop

    val position = IntOffset(x, y)
    val menuBounds = IntRect(position, menuSize)
    return SharedDropdownMenuPlacement(
        offset = position,
        transformOrigin = sharedDropdownMenuTransformOrigin(anchorBounds, menuBounds),
    )
}

/**
 * The point the menu scales out of, so the open animation appears to grow from the
 * anchor rather than from the menu's own centre. Android benchmark
 * (`calculateTransformOrigin` in material3's `Menu.kt`).
 */
internal fun sharedDropdownMenuTransformOrigin(
    anchorBounds: IntRect,
    menuBounds: IntRect,
): TransformOrigin {
    val pivotX = if (anchorBounds.right < menuBounds.right) {
        anchorBounds.right - menuBounds.left
    } else {
        anchorBounds.center.x - menuBounds.left
    }.toFloat() / menuBounds.width
    val intersectionCenter =
        (maxOf(anchorBounds.top, menuBounds.top) + minOf(anchorBounds.bottom, menuBounds.bottom)) / 2
    val pivotY = (intersectionCenter - menuBounds.top).toFloat() / menuBounds.height
    return TransformOrigin(pivotX.coerceIn(0f, 1f), pivotY)
}

/**
 * Height budget for the menu before it starts scrolling, so a long list can never grow
 * past the window. Android gets this for free from the popup's measure policy; the inline
 * menu has to reserve the space itself.
 */
internal fun sharedDropdownMenuMaxHeight(
    anchorBounds: IntRect,
    windowSize: IntSize,
    density: Density,
): Int {
    val verticalMargin = with(density) { SharedDropdownMenuVerticalMargin.roundToPx() }
    val spaceBelow = windowSize.height - anchorBounds.bottom
    val spaceAbove = anchorBounds.top
    // Prefer whichever side has more room: placement already falls back to the other side
    // when the preferred one cannot fit the measured menu.
    val available = maxOf(spaceBelow, spaceAbove) - verticalMargin
    // A window smaller than the margin leaves no valid range to coerce into, so clamp
    // with explicit min/max rather than `coerceIn`.
    val capped = minOf(available, windowSize.height - 2 * verticalMargin)
    return maxOf(1, capped)
}

internal object SharedDropdownMenuMetrics {
    val VerticalMargin = androidx.compose.ui.unit.Dp(48f)
}
