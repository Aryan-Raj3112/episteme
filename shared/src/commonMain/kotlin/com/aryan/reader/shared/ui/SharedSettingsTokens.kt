package com.aryan.reader.shared.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Layout tokens for the settings hub.
 *
 * Every value the renderer uses lives here so the screen stops repeating literal radii, paddings,
 * and widths. Roles are semantic (gutter, group shape, row height) rather than "8dp here, 10dp
 * there", which is what lets the look stay coherent when one of them changes.
 */
internal object SharedSettingsTokens {
    val SpaceXxs = SharedSpacing.Xxs
    val SpaceXs = SharedSpacing.Xs
    val SpaceSm = SharedSpacing.Sm
    val SpaceMd = SharedSpacing.Md
    val SpaceLg = SharedSpacing.Lg
    val SpaceXl = SharedSpacing.Xl

    val ScreenGutterCompact = 16.dp
    val ScreenGutterExpanded = 24.dp
    val CompactContentMaxWidth = 720.dp
    val NavigationPaneWidth = 340.dp
    val TwoPaneMaxWidth = 1280.dp

    val RowMinHeight = 56.dp
    val RowHorizontalPadding = 16.dp
    val RowVerticalPadding = 12.dp
    val IconSize = 24.dp
    val ChevronSize = 20.dp
    /** Leading badge in a row: the icon sits inside it, so the divider starts at its end. */
    val IconBadgeSize = 36.dp
    /** Row padding + badge + the gap before the title, so dividers start under the text. */
    val DividerInset = 68.dp

    val ExpandedBreakpoint = 840.dp

    @Composable
    fun groupShape() = sharedCardShape()
}
