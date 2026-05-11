package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState

@Composable
internal actual fun SharedReaderModalLayer(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(width = 720.dp, height = 620.dp),
        title = "Reader",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true
    ) {
        content()
    }
}
