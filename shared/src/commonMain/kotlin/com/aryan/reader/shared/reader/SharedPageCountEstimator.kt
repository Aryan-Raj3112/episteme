package com.aryan.reader.shared.reader

import kotlin.math.ceil
import kotlin.math.max

/** Android-benchmark heuristic used before a chapter has been measured by the paginator. */
fun estimateSharedChapterPageCount(
    htmlLength: Int,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    fontSizePx: Float,
    lineHeightPx: Float,
): Int {
    val viewportArea = viewportWidthPx * viewportHeightPx
    if (viewportArea <= 0) return 1

    val averageCharacterWidthPx = fontSizePx * 0.6f
    val characterArea = averageCharacterWidthPx * lineHeightPx
    val rawCharactersPerPage = viewportArea / characterArea
    val estimatedVisibleCharactersPerPage = (rawCharactersPerPage * 0.75f).toInt()
    if (estimatedVisibleCharactersPerPage <= 0) return 1

    val estimatedTextLength = (htmlLength * 0.6f).toInt()
    return max(
        1,
        ceil(estimatedTextLength.toFloat() / estimatedVisibleCharactersPerPage).toInt(),
    )
}
