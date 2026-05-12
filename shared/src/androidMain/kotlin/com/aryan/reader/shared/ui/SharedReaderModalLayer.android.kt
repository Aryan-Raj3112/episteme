package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal actual fun SharedReaderModalLayer(
    onDismiss: () -> Unit,
    level: SharedReaderModalLevel,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        content()
    }
}
