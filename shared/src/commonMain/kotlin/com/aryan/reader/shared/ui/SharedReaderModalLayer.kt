package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable

@Composable
internal expect fun SharedReaderModalLayer(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
)
