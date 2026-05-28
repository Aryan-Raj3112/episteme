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
import androidx.compose.ui.unit.Dp
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
import java.util.Collections
import java.util.WeakHashMap
import javax.swing.RootPaneContainer

private val LocalSharedReaderModalOwnerWindow = compositionLocalOf<AwtWindow?> { null }
private val SharedReaderModalOwnerByWindow: MutableMap<AwtWindow, AwtWindow> =
    Collections.synchronizedMap(WeakHashMap<AwtWindow, AwtWindow>())

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
    val focusableOverride = LocalSharedReaderModalFocusableOverride.current
    val explicitOwnerWindow = LocalSharedReaderModalOwnerWindow.current
    val fallbackOwnerWindow = remember { currentNonModalOwnerWindow() }
    val ownerWindow = explicitOwnerWindow ?: fallbackOwnerWindow
    val modalWindowFocusable = sharedReaderModalLayerWindowFocusable(
        level = level,
        focusableOverride = focusableOverride
    )
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
                        width = sharedReaderModalEdgePanelLayerWidth(
                            level = level,
                            anchorWidth = it.widthPx.toDp()
                        ),
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
        mutableStateOf(sharedReaderModalLayerVisible(ownerWindow, explicitOwnerWindow, null))
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
        if (ownerWindow == null || explicitOwnerWindow == null) {
            modalVisible = true
            onDispose {}
        } else {
            var disposed = false
            fun hideImmediately(ownerClosing: Boolean) {
                if (disposed) return
                if (
                    sharedReaderModalLayerShouldHideImmediately(
                        ownerShowing = ownerWindow.isShowing,
                        ownerDisplayable = ownerWindow.isDisplayable,
                        ownerClosing = ownerClosing
                    )
                ) {
                    modalVisible = false
                }
            }
            fun syncVisibility(oppositeWindow: AwtWindow?) {
                if (disposed) return
                if (
                    sharedReaderModalLayerShouldHideImmediately(
                        ownerShowing = ownerWindow.isShowing,
                        ownerDisplayable = ownerWindow.isDisplayable,
                        ownerClosing = false
                    )
                ) {
                    hideImmediately(ownerClosing = false)
                    return
                }
                val nextVisible = sharedReaderModalLayerVisible(ownerWindow, explicitOwnerWindow, oppositeWindow)
                if (nextVisible) {
                    modalVisible = true
                } else {
                    EventQueue.invokeLater {
                        if (!disposed) {
                            modalVisible = sharedReaderModalLayerVisible(ownerWindow, explicitOwnerWindow, null)
                        }
                    }
                }
            }
            val listener = object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) = hideImmediately(ownerClosing = true)
                override fun windowClosed(e: WindowEvent?) = hideImmediately(ownerClosing = true)
                override fun windowActivated(e: WindowEvent?) = syncVisibility(e?.oppositeWindow)
                override fun windowDeactivated(e: WindowEvent?) = syncVisibility(e?.oppositeWindow)
                override fun windowGainedFocus(e: WindowEvent?) = syncVisibility(e?.oppositeWindow)
                override fun windowLostFocus(e: WindowEvent?) = syncVisibility(e?.oppositeWindow)
                override fun windowIconified(e: WindowEvent?) = syncVisibility(e?.oppositeWindow)
                override fun windowDeiconified(e: WindowEvent?) = syncVisibility(e?.oppositeWindow)
            }
            ownerWindow.addWindowListener(listener)
            ownerWindow.addWindowFocusListener(listener)
            syncVisibility(null)
            onDispose {
                disposed = true
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
            focusable = modalWindowFocusable
        ) {
            val modalWindow = window
            DisposableEffect(modalWindow, ownerWindow, explicitOwnerWindow, level) {
                modalWindow.name = SharedReaderModalWindowNamePrefix + level.name
                if (ownerWindow != null && explicitOwnerWindow != null) {
                    synchronized(SharedReaderModalOwnerByWindow) {
                        SharedReaderModalOwnerByWindow[modalWindow] = ownerWindow
                    }
                }
                var disposed = false
                fun syncVisibility(oppositeWindow: AwtWindow?) {
                    if (disposed) return
                    if (ownerWindow != null && explicitOwnerWindow != null) {
                        if (
                            sharedReaderModalLayerShouldHideImmediately(
                                ownerShowing = ownerWindow.isShowing,
                                ownerDisplayable = ownerWindow.isDisplayable,
                                ownerClosing = false
                            )
                        ) {
                            modalVisible = false
                            return
                        }
                        val nextVisible = sharedReaderModalLayerVisible(ownerWindow, explicitOwnerWindow, oppositeWindow)
                        if (nextVisible) {
                            modalVisible = true
                        } else {
                            EventQueue.invokeLater {
                                if (!disposed) {
                                    modalVisible = sharedReaderModalLayerVisible(ownerWindow, explicitOwnerWindow, null)
                                }
                            }
                        }
                    }
                }
                val listener = object : WindowAdapter() {
                    override fun windowActivated(e: WindowEvent?) = syncVisibility(e?.oppositeWindow)
                    override fun windowDeactivated(e: WindowEvent?) = syncVisibility(e?.oppositeWindow)
                    override fun windowGainedFocus(e: WindowEvent?) = syncVisibility(e?.oppositeWindow)
                    override fun windowLostFocus(e: WindowEvent?) = syncVisibility(e?.oppositeWindow)
                    override fun windowIconified(e: WindowEvent?) = syncVisibility(e?.oppositeWindow)
                    override fun windowClosed(e: WindowEvent?) = syncVisibility(e?.oppositeWindow)
                }
                modalWindow.addWindowListener(listener)
                modalWindow.addWindowFocusListener(listener)
                onDispose {
                    disposed = true
                    modalWindow.removeWindowListener(listener)
                    modalWindow.removeWindowFocusListener(listener)
                    synchronized(SharedReaderModalOwnerByWindow) {
                        SharedReaderModalOwnerByWindow.remove(modalWindow)
                    }
                }
            }
            LaunchedEffect(modalWindow, level, modalWindowFocusable) {
                modalWindow.name = SharedReaderModalWindowNamePrefix + level.name
                modalWindow.isAlwaysOnTop = true
                modalWindow.setFocusableWindowState(modalWindowFocusable)
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
                    if (!modalWindow.isDisplayable) return@repeat
                    runCatching {
                        modalWindow.isAlwaysOnTop = true
                        modalWindow.toFront()
                        if (modalWindowFocusable && !level.isEdgePanelLayer()) {
                            modalWindow.requestFocus()
                            modalWindow.requestFocusInWindow()
                        }
                    }
                }
            }
            content()
        }
    }
}

internal fun sharedReaderModalLayerWindowFocusable(
    level: SharedReaderModalLevel,
    focusableOverride: Boolean?
): Boolean {
    return focusableOverride ?: !level.isChromeLayer()
}

internal actual fun sharedReaderModalLayerUsesSizedEdgeWindow(level: SharedReaderModalLevel): Boolean {
    return level.isEdgePanelLayer()
}

private const val SharedReaderModalWindowNamePrefix = "shared-reader-modal:"
private val SharedReaderChromeTopLayerHeight = 104.dp
private val SharedReaderChromeBottomLayerHeight = 164.dp
private val SharedReaderChromeBottomLayerOverlap = 8.dp
private val SharedReaderLeftPanelWidth = 340.dp
private val SharedReaderRightPanelWidth = 380.dp
private val SharedReaderLeftNarrowPanelMaxWidth = 320.dp
private val SharedReaderRightNarrowPanelMaxWidth = 360.dp
private val SharedReaderNarrowPanelFraction = 0.92f
private val SharedReaderWidePanelBreakpoint = 1120.dp

private fun sharedReaderModalLayerVisible(
    ownerWindow: AwtWindow?,
    explicitOwnerWindow: AwtWindow?,
    oppositeWindow: AwtWindow?
): Boolean {
    if (explicitOwnerWindow == null) return true
    return ownerWindow?.sharedReaderChromeLayerVisible(oppositeWindow) == true
}

internal fun sharedReaderModalChromeLayerVisible(
    ownerShowing: Boolean,
    ownerDisplayable: Boolean,
    ownerMinimized: Boolean,
    ownerActive: Boolean,
    ownerFocused: Boolean,
    ownerModalActive: Boolean
): Boolean {
    return ownerShowing &&
        ownerDisplayable &&
        !ownerMinimized &&
        (ownerActive || ownerFocused || ownerModalActive)
}

internal fun sharedReaderModalLayerShouldHideImmediately(
    ownerShowing: Boolean,
    ownerDisplayable: Boolean,
    ownerClosing: Boolean
): Boolean {
    return ownerClosing || !ownerShowing || !ownerDisplayable
}

private fun AwtWindow.sharedReaderChromeLayerVisible(oppositeWindow: AwtWindow?): Boolean {
    return sharedReaderModalChromeLayerVisible(
        ownerShowing = isShowing,
        ownerDisplayable = isDisplayable,
        ownerMinimized = (this as? java.awt.Frame)?.let { frame ->
            frame.extendedState and java.awt.Frame.ICONIFIED != 0
        } == true,
        ownerActive = isActive,
        ownerFocused = isFocused,
        ownerModalActive = oppositeWindow.isSharedReaderModalWindowForOwner(this) ||
            sharedReaderModalWindowActiveForOwner(this)
    )
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
                SharedReaderModalLevel.ChromeBottom -> sharedReaderModalChromeBottomLayerTopPx(
                    anchorTopPx = anchor.topPx,
                    anchorHeightPx = anchor.heightPx,
                    dialogHeightPx = dialogSize.height.toPx(),
                    overlapPx = SharedReaderChromeBottomLayerOverlap.toPx()
                )
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

internal fun sharedReaderModalChromeBottomLayerTopPx(
    anchorTopPx: Float,
    anchorHeightPx: Float,
    dialogHeightPx: Float,
    overlapPx: Float
): Float {
    return anchorTopPx + anchorHeightPx - dialogHeightPx + overlapPx
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

internal fun sharedReaderModalEdgePanelLayerWidth(
    level: SharedReaderModalLevel,
    anchorWidth: Dp
): Dp {
    val preferredWideWidth = when (level) {
        SharedReaderModalLevel.PanelLeft -> SharedReaderLeftPanelWidth
        else -> SharedReaderRightPanelWidth
    }
    val preferredNarrowWidth = when (level) {
        SharedReaderModalLevel.PanelLeft -> minOf(SharedReaderLeftNarrowPanelMaxWidth, anchorWidth * SharedReaderNarrowPanelFraction)
        else -> minOf(SharedReaderRightNarrowPanelMaxWidth, anchorWidth * SharedReaderNarrowPanelFraction)
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

private fun AwtWindow?.isSharedReaderModalWindowForOwner(ownerWindow: AwtWindow): Boolean {
    val window = this ?: return false
    return window.sharedReaderModalOwnerInWindowChain() == ownerWindow
}

private fun sharedReaderModalWindowActiveForOwner(ownerWindow: AwtWindow): Boolean {
    return AwtWindow.getWindows().any { window ->
        window.isShowing &&
            window.isDisplayable &&
            window.isSharedReaderModalWindowForOwner(ownerWindow) &&
            (window.isActive || window.isFocused)
    }
}

private fun AwtWindow.sharedReaderModalOwnerInWindowChain(): AwtWindow? {
    var current: AwtWindow? = this
    while (current != null) {
        val window = current
        synchronized(SharedReaderModalOwnerByWindow) {
            SharedReaderModalOwnerByWindow[window]
        }?.let { owner -> return owner }
        current = window.owner
    }
    return null
}
