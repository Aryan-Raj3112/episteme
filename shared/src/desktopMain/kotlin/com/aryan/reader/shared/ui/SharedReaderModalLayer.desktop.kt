package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.Window as ComposeWindow
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.awt.Window as AwtWindow

@Composable
internal actual fun SharedReaderModalLayer(
    onDismiss: () -> Unit,
    level: SharedReaderModalLevel,
    content: @Composable () -> Unit
) {
    val anchor = LocalSharedReaderModalAnchorBounds.current
    val density = LocalDensity.current
    val ownerWindow = remember { currentNonModalOwnerWindow() }
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
    val state = rememberWindowState(position = dialogPosition, size = dialogSize)
    val windowTitle = when (level) {
        SharedReaderModalLevel.Panel -> "Reader Panel"
        SharedReaderModalLevel.Popup -> "Reader Popup"
    }

    LaunchedEffect(dialogPosition, dialogSize) {
        state.position = dialogPosition
        state.size = dialogSize
    }
    DisposableEffect(ownerWindow) {
        onDispose {
            ownerWindow?.restoreFocusAfterSharedReaderModal()
        }
    }

    ComposeWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = windowTitle,
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = true
    ) {
        val modalWindow = window
        LaunchedEffect(modalWindow, level) {
            modalWindow.name = SharedReaderModalWindowNamePrefix + level.name
            modalWindow.isAlwaysOnTop = true
            val frontAttempts = if (level == SharedReaderModalLevel.Popup) 4 else 3
            repeat(frontAttempts) { attempt ->
                delay(if (attempt == 0) 30L else 80L)
                modalWindow.isAlwaysOnTop = true
                modalWindow.toFront()
                modalWindow.requestFocus()
                modalWindow.requestFocusInWindow()
            }
        }
        content()
    }
}

private const val SharedReaderModalWindowNamePrefix = "shared-reader-modal:"

private fun AwtWindow.restoreFocusAfterSharedReaderModal() {
    EventQueue.invokeLater {
        if (!isDisplayable || !isShowing) return@invokeLater
        if (this is java.awt.Frame && extendedState and java.awt.Frame.ICONIFIED != 0) {
            extendedState = extendedState and java.awt.Frame.ICONIFIED.inv()
        }
        toFront()
        requestFocus()
        requestFocusInWindow()
        focusOwner?.requestFocus()
    }
}

private fun currentNonModalOwnerWindow(): AwtWindow? {
    val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    if (activeWindow != null && !activeWindow.isSharedReaderModalWindow()) {
        return activeWindow
    }
    return AwtWindow.getWindows()
        .filter { window -> window.isShowing && window.isDisplayable && !window.isSharedReaderModalWindow() }
        .maxByOrNull { window ->
            when {
                window.isFocused -> 3
                window.isActive -> 2
                window.isVisible -> 1
                else -> 0
            }
        }
}

private fun AwtWindow.isSharedReaderModalWindow(): Boolean {
    val windowTitle = when (this) {
        is java.awt.Dialog -> title
        is java.awt.Frame -> title
        else -> ""
    }
    return name?.startsWith(SharedReaderModalWindowNamePrefix) == true ||
        windowTitle.startsWith("Reader Panel") ||
        windowTitle.startsWith("Reader Popup")
}
