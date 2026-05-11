package com.aryan.reader.shared.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable

internal data class SharedReaderModalAnchorBounds(
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Float,
    val heightPx: Float
)

internal val LocalSharedReaderModalAnchorBounds = compositionLocalOf<SharedReaderModalAnchorBounds?> { null }

@Composable
internal expect fun SharedReaderModalLayer(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
)
