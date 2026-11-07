package com.aryan.reader.paginatedreader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment

/**
 * Flex arrangement mappings shared by both native stacks (B1 step 3, flex
 * block kind).
 *
 * Android's paginated flex branch and shared's flex branch carried identical
 * four-way `justifyContent`/`alignItems` tables. Only the tables live here.
 * Deliberately not covered: Android's vertical flex path (which ignores both
 * properties — an Android-internal inconsistency, recorded not ported), the
 * nested-flex third behavior, and chant `FlowRow` (product decision, needs
 * paginator work).
 */

fun nativeFlexRowHorizontalArrangement(justifyContent: String?): Arrangement.Horizontal = when (justifyContent) {
    "center" -> Arrangement.Center
    "flex-end" -> Arrangement.End
    "space-between" -> Arrangement.SpaceBetween
    "space-around" -> Arrangement.SpaceAround
    else -> Arrangement.Start
}

fun nativeFlexRowVerticalAlignment(alignItems: String?): Alignment.Vertical = when (alignItems) {
    "center" -> Alignment.CenterVertically
    "flex-end" -> Alignment.Bottom
    else -> Alignment.Top
}

fun nativeFlexColumnVerticalArrangement(justifyContent: String?): Arrangement.Vertical = when (justifyContent) {
    "center" -> Arrangement.Center
    "flex-end" -> Arrangement.Bottom
    "space-between" -> Arrangement.SpaceBetween
    "space-around" -> Arrangement.SpaceAround
    else -> Arrangement.Top
}

fun nativeFlexColumnHorizontalAlignment(alignItems: String?): Alignment.Horizontal = when (alignItems) {
    "center" -> Alignment.CenterHorizontally
    "flex-end" -> Alignment.End
    else -> Alignment.Start
}
