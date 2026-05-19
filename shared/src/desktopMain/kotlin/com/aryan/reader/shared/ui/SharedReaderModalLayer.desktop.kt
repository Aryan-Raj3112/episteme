package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.Window as ComposeWindow
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.Window as AwtWindow
import javax.swing.RootPaneContainer

private val LocalSharedReaderModalOwnerWindow = compositionLocalOf<AwtWindow?> { null }

@Composable
actual fun SharedReaderModalOwnerWindowProvider(
    ownerWindow: Any?,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalSharedReaderModalOwnerWindow provides (ownerWindow as? AwtWindow),
        content = content
    )
}

@Composable
internal actual fun SharedReaderModalLayer(
    onDismiss: () -> Unit,
    level: SharedReaderModalLevel,
    content: @Composable () -> Unit
) {
    val anchor = LocalSharedReaderModalAnchorBounds.current
    val density = LocalDensity.current
    val explicitOwnerWindow = LocalSharedReaderModalOwnerWindow.current
    val fallbackOwnerWindow = remember { currentNonModalOwnerWindow() }
    val ownerWindow = explicitOwnerWindow ?: fallbackOwnerWindow
    val dialogSize = with(density) {
        anchor?.let {
            when {
                level.isChromeLayer() -> {
                    DpSize(
                        width = it.widthPx.toDp().coerceAtLeast(360.dp),
                        height = level.chromeLayerHeight().coerceAtMost(it.heightPx.toDp().coerceAtLeast(1.dp))
                    )
                }
                level.isEdgePanelLayer() -> {
                    DpSize(
                        width = level.edgePanelLayerWidth(it.widthPx.toDp()),
                        height = it.heightPx.toDp().coerceAtLeast(360.dp)
                    )
                }
                else -> {
                    DpSize(
                        width = it.widthPx.toDp().coerceAtLeast(360.dp),
                        height = it.heightPx.toDp().coerceAtLeast(360.dp)
                    )
                }
            }
        } ?: DpSize(720.dp, 620.dp)
    }
    val dialogPosition = sharedReaderModalLayerPosition(
        anchor = anchor,
        ownerWindow = ownerWindow,
        dialogSize = dialogSize,
        level = level,
        density = density
    )
    val state = rememberWindowState(position = dialogPosition, size = dialogSize)
    val windowTitle = when (level) {
        SharedReaderModalLevel.Panel -> "Reader Panel"
        SharedReaderModalLevel.PanelLeft -> "Reader Navigation"
        SharedReaderModalLevel.PanelRight -> "Reader Tools"
        SharedReaderModalLevel.Popup -> "Reader Popup"
        SharedReaderModalLevel.ChromeTop -> "Reader Chrome Top"
        SharedReaderModalLevel.ChromeBottom -> "Reader Chrome Bottom"
    }
    var modalVisible by remember(ownerWindow, explicitOwnerWindow, level) {
        mutableStateOf(sharedReaderModalLayerVisible(ownerWindow, explicitOwnerWindow, level))
    }

    LaunchedEffect(dialogPosition, dialogSize) {
        if (state.position != dialogPosition) {
            state.position = dialogPosition
        }
        if (state.size != dialogSize) {
            state.size = dialogSize
        }
    }
    if (!level.isChromeLayer()) {
        DisposableEffect(ownerWindow) {
            onDispose {
                ownerWindow?.restoreFocusAfterSharedReaderModal()
            }
        }
    }
    DisposableEffect(ownerWindow, explicitOwnerWindow, level) {
        if (ownerWindow == null || explicitOwnerWindow == null || !level.isChromeLayer()) {
            modalVisible = true
            onDispose {}
        } else {
            fun syncVisibility() {
                modalVisible = sharedReaderModalLayerVisible(ownerWindow, explicitOwnerWindow, level)
            }
            val listener = object : WindowAdapter() {
                override fun windowActivated(e: WindowEvent?) = syncVisibility()
                override fun windowDeactivated(e: WindowEvent?) = syncVisibility()
                override fun windowGainedFocus(e: WindowEvent?) = syncVisibility()
                override fun windowLostFocus(e: WindowEvent?) = syncVisibility()
                override fun windowIconified(e: WindowEvent?) = syncVisibility()
                override fun windowDeiconified(e: WindowEvent?) = syncVisibility()
                override fun windowClosed(e: WindowEvent?) = syncVisibility()
            }
            ownerWindow.addWindowListener(listener)
            ownerWindow.addWindowFocusListener(listener)
            syncVisibility()
            onDispose {
                ownerWindow.removeWindowListener(listener)
                ownerWindow.removeWindowFocusListener(listener)
            }
        }
    }

    if (modalVisible) {
        ComposeWindow(
            onCloseRequest = onDismiss,
            state = state,
            title = windowTitle,
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = true,
            focusable = !level.isChromeLayer()
        ) {
            val modalWindow = window
            LaunchedEffect(modalWindow, level) {
                modalWindow.name = SharedReaderModalWindowNamePrefix + level.name
                modalWindow.isAlwaysOnTop = true
                val frontAttempts = when (level) {
                    SharedReaderModalLevel.Popup -> 4
                    SharedReaderModalLevel.Panel,
                    SharedReaderModalLevel.PanelLeft,
                    SharedReaderModalLevel.PanelRight -> 3
                    SharedReaderModalLevel.ChromeTop,
                    SharedReaderModalLevel.ChromeBottom -> 1
                }
                repeat(frontAttempts) { attempt ->
                    delay(if (attempt == 0) 30L else 80L)
                    modalWindow.isAlwaysOnTop = true
                    modalWindow.toFront()
                    if (!level.isChromeLayer()) {
                        modalWindow.requestFocus()
                        modalWindow.requestFocusInWindow()
                    }
                }
            }
            content()
        }
    }
}

internal actual fun sharedReaderModalLayerUsesSizedEdgeWindow(level: SharedReaderModalLevel): Boolean {
    return level.isEdgePanelLayer()
}

private const val SharedReaderModalWindowNamePrefix = "shared-reader-modal:"
private val SharedReaderChromeTopLayerHeight = 104.dp
private val SharedReaderChromeBottomLayerHeight = 164.dp
private val SharedReaderLeftPanelWidth = 340.dp
private val SharedReaderRightPanelWidth = 380.dp
private val SharedReaderLeftNarrowPanelMaxWidth = 320.dp
private val SharedReaderRightNarrowPanelMaxWidth = 360.dp
private val SharedReaderNarrowPanelFraction = 0.92f
private val SharedReaderWidePanelBreakpoint = 1120.dp

private fun sharedReaderModalLayerVisible(
    ownerWindow: AwtWindow?,
    explicitOwnerWindow: AwtWindow?,
    level: SharedReaderModalLevel
): Boolean {
    if (explicitOwnerWindow == null || !level.isChromeLayer()) return true
    return ownerWindow?.let { window ->
        window.isShowing && window.isDisplayable && (window.isActive || window.isFocused)
    } == true
}

private fun sharedReaderModalLayerPosition(
    anchor: SharedReaderModalAnchorBounds?,
    ownerWindow: AwtWindow?,
    dialogSize: DpSize,
    level: SharedReaderModalLevel,
    density: Density
): WindowPosition {
    return with(density) {
        val ownerLocation = ownerWindow?.let { window ->
            runCatching { window.sharedReaderModalContentLocationOnScreen() }.getOrNull()
        }
        if (anchor != null && ownerLocation != null) {
            val topPx = when (level) {
                SharedReaderModalLevel.ChromeBottom -> anchor.topPx + anchor.heightPx - dialogSize.height.toPx()
                else -> anchor.topPx
            }
            val leftPx = when (level) {
                SharedReaderModalLevel.PanelRight -> anchor.leftPx + anchor.widthPx - dialogSize.width.toPx()
                else -> anchor.leftPx
            }
            WindowPosition(
                (ownerLocation.x + leftPx).toDp(),
                (ownerLocation.y + topPx).toDp()
            )
        } else {
            WindowPosition(Alignment.Center)
        }
    }
}

private fun AwtWindow.sharedReaderModalContentLocationOnScreen(): Point {
    val contentPane = (this as? RootPaneContainer)?.contentPane
    if (contentPane != null && contentPane.isShowing) {
        return contentPane.locationOnScreen
    }
    return locationOnScreen
}

private fun SharedReaderModalLevel.isChromeLayer(): Boolean {
    return this == SharedReaderModalLevel.ChromeTop || this == SharedReaderModalLevel.ChromeBottom
}

private fun SharedReaderModalLevel.isEdgePanelLayer(): Boolean {
    return this == SharedReaderModalLevel.PanelLeft || this == SharedReaderModalLevel.PanelRight
}

private fun SharedReaderModalLevel.chromeLayerHeight() = when (this) {
    SharedReaderModalLevel.ChromeTop -> SharedReaderChromeTopLayerHeight
    SharedReaderModalLevel.ChromeBottom -> SharedReaderChromeBottomLayerHeight
    else -> 0.dp
}

private fun SharedReaderModalLevel.edgePanelLayerWidth(anchorWidth: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp {
    val preferredWideWidth = when (this) {
        SharedReaderModalLevel.PanelRight -> SharedReaderRightPanelWidth
        else -> SharedReaderLeftPanelWidth
    }
    val preferredNarrowWidth = when (this) {
        SharedReaderModalLevel.PanelRight -> minOf(SharedReaderRightNarrowPanelMaxWidth, anchorWidth * SharedReaderNarrowPanelFraction)
        else -> minOf(SharedReaderLeftNarrowPanelMaxWidth, anchorWidth * SharedReaderNarrowPanelFraction)
    }
    return if (anchorWidth >= SharedReaderWidePanelBreakpoint) {
        preferredWideWidth.coerceAtMost(anchorWidth)
    } else {
        preferredNarrowWidth.coerceAtMost(anchorWidth)
    }.coerceAtLeast(1.dp)
}

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
        windowTitle.startsWith("Reader Popup") ||
        windowTitle.startsWith("Reader Chrome")
}
