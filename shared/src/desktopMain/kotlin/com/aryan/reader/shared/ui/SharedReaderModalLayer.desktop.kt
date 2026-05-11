package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import java.awt.KeyboardFocusManager

@Composable
internal actual fun SharedReaderModalLayer(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val anchor = LocalSharedReaderModalAnchorBounds.current
    val density = LocalDensity.current
    val ownerWindow = remember { KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow }
    val dialogSize = with(density) {
        anchor?.let {
            DpSize(
                width = it.widthPx.toDp().coerceAtLeast(360.dp),
                height = it.heightPx.toDp().coerceAtLeast(360.dp)
            )
        } ?: DpSize(720.dp, 620.dp)
    }
    val dialogPosition = with(density) {
        val ownerLocation = ownerWindow?.let { window ->
            runCatching { window.locationOnScreen }.getOrNull()
        }
        if (anchor != null && ownerLocation != null) {
            WindowPosition(
                (ownerLocation.x + anchor.leftPx).toDp(),
                (ownerLocation.y + anchor.topPx).toDp()
            )
        } else {
            WindowPosition(Alignment.Center)
        }
    }
    val state = rememberDialogState(position = dialogPosition, size = dialogSize)

    LaunchedEffect(dialogPosition, dialogSize) {
        state.position = dialogPosition
        state.size = dialogSize
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = "Reader",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true
    ) {
        content()
    }
}
