package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt

fun measuredTextHeightForPagination(
    layoutHeightPx: Int,
    lastLineBottomPx: Float
): Int = maxOf(layoutHeightPx, ceil(lastLineBottomPx.toDouble()).toInt())

fun effectiveTopMarginPxForPagination(
    isPageStart: Boolean,
    currentTopMarginPx: Float
): Float = if (isPageStart) 0f else currentTopMarginPx

fun collapsedVerticalMarginPxForPagination(
    previousBottomMarginPx: Float?,
    currentTopMarginPx: Float
): Int {
    val currentTop = currentTopMarginPx.coerceAtLeast(0f)
    val collapsed = previousBottomMarginPx?.let { previousBottom ->
        maxOf(previousBottom.coerceAtLeast(0f), currentTop)
    } ?: currentTop
    return collapsed.roundToInt()
}

fun availableBlockWidthPxForPagination(
    containerWidthPx: Int,
    marginLeftPx: Float,
    marginRightPx: Float,
    isCenterAligned: Boolean
): Float {
    if (isCenterAligned) return containerWidthPx.toFloat().coerceAtLeast(0f)
    return (containerWidthPx.toFloat() - marginLeftPx.coerceAtLeast(0f) - marginRightPx.coerceAtLeast(0f))
        .coerceAtLeast(0f)
}

fun parseSvgDimension(
    dimension: String?,
    fontSizePx: Float,
    containerWidthPx: Int,
    density: Density
): Float? {
    if (dimension.isNullOrBlank()) return null
    return when {
        dimension.endsWith("ex") -> dimension.removeSuffix("ex").toFloatOrNull()?.let { it * 0.5f * fontSizePx }
        dimension.endsWith("em") -> dimension.removeSuffix("em").toFloatOrNull()?.let { it * fontSizePx }
        dimension.endsWith("px") -> dimension.removeSuffix("px").toFloatOrNull()
        dimension.endsWith("pt") -> dimension.removeSuffix("pt").toFloatOrNull()?.let { it * 1.333f * density.density }
        dimension.endsWith("%") -> dimension.removeSuffix("%").toFloatOrNull()?.let { (it / 100f) * containerWidthPx }
        else -> dimension.toFloatOrNull()
    }
}

fun centeredTextSafetyPaddingPx(
    style: TextStyle,
    density: Density,
    enabled: Boolean = true
): Int {
    if (!enabled || style.textAlign != TextAlign.Center) return 0

    val fallbackLineHeight = if (style.fontSize.isSpecified) {
        style.fontSize * 1.2f
    } else {
        16.sp * 1.2f
    }
    val effectiveLineHeight = if (style.lineHeight.isSpecified) style.lineHeight else fallbackLineHeight
    return with(density) { effectiveLineHeight.toPx().roundToInt() }
}

fun intrinsicImageWidthPx(
    intrinsicWidth: Float,
    density: Density,
    maxWidthPx: Float
): Float {
    if (intrinsicWidth <= 0f || maxWidthPx <= 0f) return 0f
    return with(density) { intrinsicWidth.dp.toPx() }.coerceAtMost(maxWidthPx)
}
