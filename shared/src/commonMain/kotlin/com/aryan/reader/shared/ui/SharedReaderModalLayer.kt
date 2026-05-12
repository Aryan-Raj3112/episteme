package com.aryan.reader.shared.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class SharedReaderModalAnchorBounds(
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Float,
    val heightPx: Float
)

internal val LocalSharedReaderModalAnchorBounds = compositionLocalOf<SharedReaderModalAnchorBounds?> { null }

internal enum class SharedReaderModalLevel {
    Panel,
    Popup
}

val SharedReaderPopupDefaultMaxWidth = 440.dp
private val SharedReaderPopupMinWidth = 320.dp
private const val SharedReaderPopupWidthFraction = 0.58f

fun sharedReaderPopupWidth(
    availableWidth: Dp,
    maxWidth: Dp = SharedReaderPopupDefaultMaxWidth,
    minWidth: Dp = SharedReaderPopupMinWidth,
    widthFraction: Float = SharedReaderPopupWidthFraction
): Dp {
    if (availableWidth <= 0.dp) return 0.dp
    val lowerBound = minWidth.coerceAtMost(availableWidth)
    val upperBound = maxWidth.coerceAtMost(availableWidth).coerceAtLeast(lowerBound)
    return (availableWidth * widthFraction.coerceIn(0f, 1f)).coerceIn(lowerBound, upperBound)
}

@Composable
internal expect fun SharedReaderModalLayer(
    onDismiss: () -> Unit,
    level: SharedReaderModalLevel = SharedReaderModalLevel.Popup,
    content: @Composable () -> Unit
)

@Composable
fun SharedReaderPopupLayer(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    SharedReaderModalLayer(
        onDismiss = onDismiss,
        level = SharedReaderModalLevel.Popup,
        content = content
    )
}
