package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable

@Composable
internal actual fun SharedReaderModalLayer(
    onDismiss: () -> Unit,
    level: SharedReaderModalLevel,
    content: @Composable () -> Unit
) {
    content()
}

internal actual fun sharedReaderModalLayerUsesSizedEdgeWindow(level: SharedReaderModalLevel): Boolean {
    return false
}

@Composable
actual fun SharedReaderModalOwnerWindowProvider(
    ownerWindow: Any?,
    content: @Composable () -> Unit
) {
    content()
}
