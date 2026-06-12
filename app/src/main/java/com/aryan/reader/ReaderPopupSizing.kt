package com.aryan.reader

import kotlin.math.roundToInt

fun readerModalMaxHeightDp(
    screenHeightDp: Int,
    fraction: Float = 0.85f,
    verticalMarginDp: Int = 32,
    preferredMinHeightDp: Int = 220
): Int {
    val usableHeight = (screenHeightDp - verticalMarginDp).coerceAtLeast(1)
    val proportionalHeight = (screenHeightDp * fraction).roundToInt().coerceAtLeast(1)
    val cappedHeight = minOf(usableHeight, proportionalHeight)
    return if (usableHeight >= preferredMinHeightDp) {
        cappedHeight.coerceAtLeast(preferredMinHeightDp)
    } else {
        usableHeight
    }
}
