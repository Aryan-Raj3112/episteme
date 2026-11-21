package com.aryan.reader.shared.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 8dp spacing scale used by every non-reader surface.
 *
 * Material 3 lays margins, padding, and gaps on an 8dp grid (4dp for dense chrome), so screens that
 * mix 14dp and 20dp gutters read as "almost Material". Named steps rather than raw values keep a
 * screen coherent when one step changes, and let the settings hub, the library, and the dialogs
 * share one scale instead of each declaring its own.
 */
internal object SharedSpacing {
    /** Hairline gaps: badge padding, icon-to-label sits. */
    val Xxs: Dp = 2.dp

    /** Dense chrome: chip internals, tight label stacks. */
    val Xs: Dp = 4.dp

    /** Component internals and the default gap between related items. */
    val Sm: Dp = 8.dp

    /** Grid gaps and compact padding. */
    val Md: Dp = 12.dp

    /** Screen gutter on compact widths and default list padding. */
    val Lg: Dp = 16.dp

    /** Screen gutter on expanded widths and section separation. */
    val Xl: Dp = 24.dp

    /** Hero/empty-state separation. */
    val Xxl: Dp = 32.dp
}
