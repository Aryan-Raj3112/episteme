package com.aryan.reader.paginatedreader

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies CSS `position: relative` offsets. Relative offsets shift the painted box without
 * changing flow measurement, which is the closest reflow-safe equivalent of the browser
 * behavior.
 *
 * `position: absolute|fixed` intentionally has no native equivalent here: overlay elements
 * cannot participate in measured pagination, so they stay in normal flow. This mirrors the
 * WebView vertical engine, which neutralizes positioned publication elements as well.
 */
fun Modifier.readerRelativeOffset(style: BlockStyle): Modifier {
    if (style.position != "relative") return this
    val horizontal = when {
        style.left.isSpecifiedDp() && style.left != 0.dp -> style.left
        style.right.isSpecifiedDp() && style.right != 0.dp -> -style.right
        else -> 0.dp
    }
    val vertical = when {
        style.top.isSpecifiedDp() && style.top != 0.dp -> style.top
        style.bottom.isSpecifiedDp() && style.bottom != 0.dp -> -style.bottom
        else -> 0.dp
    }
    if (horizontal == 0.dp && vertical == 0.dp) return this
    return offset(x = horizontal, y = vertical)
}

private fun Dp.isSpecifiedDp(): Boolean = this != Dp.Unspecified
