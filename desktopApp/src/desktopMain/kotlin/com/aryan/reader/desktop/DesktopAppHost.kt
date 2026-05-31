package com.aryan.reader.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aryan.reader.shared.AppContrastOption
import com.aryan.reader.shared.AppThemeMode
import com.aryan.reader.shared.ui.SharedAppTheme
import com.aryan.reader.shared.ui.readerString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.EventQueue
import java.awt.Frame
import java.awt.GraphicsDevice
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.KeyEvent as AwtKeyEvent
import java.util.concurrent.atomic.AtomicReference

internal val DesktopDefaultAppSeedColor = Color(0xFFFFB300)

private val DesktopReaderWindowFullscreenExitFocusRetryDelaysMillis = longArrayOf(160L, 200L)

internal fun launchEpistemeDesktopApplication(startupSplash: DesktopStartupSplash? = null) {
    configureComposeSwingInterop()
    application {
        val windowDefaults = remember { epistemeDesktopWindowDefaults() }
        val windowStateStore = remember { DesktopWindowStateStore() }
        val restoredWindowState = remember { windowStateStore.load() }
        val windowState = rememberWindowState(
            placement = restoredWindowState?.toWindowPlacement()
                ?: DesktopWindowStateSnapshot.default().toWindowPlacement(),
            position = restoredWindowState?.toWindowPosition() ?: WindowPosition(Alignment.Center),
            size = restoredWindowState?.toWindowSize(windowDefaults.defaultSize) ?: windowDefaults.defaultSize
        )
        var readerFullscreen by remember { mutableStateOf(false) }
        DesktopWindowStatePersistenceEffect(
            windowState = windowState,
            store = windowStateStore,
            enabled = !readerFullscreen
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = windowDefaults.title,
            state = windowState,
            icon = painterResource(windowDefaults.iconResourcePath)
        ) {
            DisposableEffect(window, windowDefaults.minimumSize) {
                window.minimumSize = windowDefaults.minimumSize
                onDispose {
                    startupSplash?.close()
                }
            }
            EpistemeDesktopStartupGate(
                window = window,
                startupSplash = startupSplash,
                appWindowPlacement = windowState.placement,
                readerFullscreen = readerFullscreen,
                onReaderFullscreenChange = { readerFullscreen = it }
            )
        }
    }
}

@Composable
private fun EpistemeDesktopStartupGate(
    window: Component?,
    startupSplash: DesktopStartupSplash?,
    appWindowPlacement: WindowPlacement,
    readerFullscreen: Boolean,
    onReaderFullscreenChange: (Boolean) -> Unit
) {
    var showApp by remember { mutableStateOf(false) }

    DisposableEffect(startupSplash) {
        onDispose {
            startupSplash?.close()
        }
    }

    if (showApp) {
        EpistemeDesktopApp(
            window = window,
            appWindowPlacement = appWindowPlacement,
            readerFullscreen = readerFullscreen,
            onReaderFullscreenChange = onReaderFullscreenChange
        )
    } else {
        EpistemeDesktopStartupScreen(window = window)
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        startupSplash?.close()
        delay(80L)
        showApp = true
    }
}

@Composable
private fun EpistemeDesktopStartupScreen(window: Component?) {
    val appTitle = remember { epistemeDesktopWindowDefaults().title }
    SharedAppTheme(
        appThemeMode = AppThemeMode.SYSTEM,
        appContrastOption = AppContrastOption.STANDARD,
        appTextDimFactorLight = 1.0f,
        appTextDimFactorDark = 1.0f,
        appSeedColor = DesktopDefaultAppSeedColor
    ) {
        EpistemeDesktopWindowChromeEffect(
            window = window,
            captionColor = MaterialTheme.colorScheme.surface,
            textColor = MaterialTheme.colorScheme.onSurface,
            borderColor = MaterialTheme.colorScheme.background
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Image(
                    painter = painterResource(EpistemeDesktopWindowIconResource),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = appTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = readerString("desktop_opening_your_library", "Opening your library"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

internal const val ComposeInteropBlendingProperty = "compose.interop.blending"
internal const val ComposeInteropBlendingEnabled = "true"
private const val DesktopWindowStatePersistDebounceMillis = 450L

internal fun composeInteropBlendingDefault(
    platform: DesktopPlatform = currentDesktopPlatform()
): String? {
    return if (desktopEpubWebViewUsesNativeSwtBrowser(platform)) {
        null
    } else {
        ComposeInteropBlendingEnabled
    }
}

internal fun configureComposeSwingInterop(
    platform: DesktopPlatform = currentDesktopPlatform()
) {
    // Must run before Compose creates the desktop window. Vertical EPUB embeds native SWT/AWT
    // browser surfaces; the blending path can prevent those native children from painting.
    if (System.getProperty(ComposeInteropBlendingProperty).isNullOrBlank()) {
        composeInteropBlendingDefault(platform)?.let { defaultValue ->
            System.setProperty(ComposeInteropBlendingProperty, defaultValue)
        }
    }
    logDesktopWebView2(
        "compose_interop platform=${platform.os} blending=${System.getProperty(ComposeInteropBlendingProperty).orEmpty().ifBlank { "default" }}"
    )
}

@Composable
internal fun DesktopWindowStatePersistenceEffect(
    windowState: WindowState,
    store: DesktopWindowStateStore,
    enabled: Boolean,
    transformSnapshot: (DesktopWindowStateSnapshot) -> DesktopWindowStateSnapshot? = { it },
    onSnapshotSaved: (DesktopWindowStateSnapshot) -> Unit = {}
) {
    val persistenceEnabled by rememberUpdatedState(enabled)
    val latestTransformSnapshot by rememberUpdatedState(transformSnapshot)
    val latestOnSnapshotSaved by rememberUpdatedState(onSnapshotSaved)
    LaunchedEffect(windowState, store) {
        snapshotFlow { DesktopWindowStateSnapshot.fromWindowState(windowState) }
            .distinctUntilChanged()
            .collectLatest { snapshot ->
                if (!persistenceEnabled || snapshot == null) return@collectLatest
                val persistableSnapshot = latestTransformSnapshot(snapshot) ?: return@collectLatest
                delay(DesktopWindowStatePersistDebounceMillis)
                if (persistenceEnabled) {
                    withContext(Dispatchers.IO) {
                        store.save(persistableSnapshot)
                    }
                    latestOnSnapshotSaved(persistableSnapshot)
                }
            }
    }
    DisposableEffect(windowState, store) {
        onDispose {
            if (persistenceEnabled) {
                DesktopWindowStateSnapshot.fromWindowState(windowState)
                    ?.let(latestTransformSnapshot)
                    ?.let { snapshot ->
                        runCatching { store.save(snapshot) }
                        latestOnSnapshotSaved(snapshot)
                    }
            }
        }
    }
}

@Composable
internal fun DesktopReaderFullscreenEffect(
    window: Component?,
    enabled: Boolean
) {
    val awtWindow = window as? java.awt.Window ?: return
    val fullscreenSnapshot = remember(awtWindow) {
        AtomicReference<DesktopReaderFullscreenSnapshot?>()
    }
    val pendingExitSnapshot = remember(awtWindow) {
        AtomicReference<DesktopReaderFullscreenSnapshot?>()
    }

    LaunchedEffect(awtWindow, enabled) {
        if (!enabled && fullscreenSnapshot.get() == null) {
            return@LaunchedEffect
        }
        if (enabled) {
            EventQueue.invokeLater {
                awtWindow.captureDesktopReaderFullscreenSnapshot(fullscreenSnapshot)
            }
        }
        delay(if (enabled) 180L else 80L)
        EventQueue.invokeLater {
            if (enabled) {
                pendingExitSnapshot.set(null)
                awtWindow.enterDesktopReaderFullscreen(fullscreenSnapshot)
            } else {
                awtWindow.beginDesktopReaderFullscreenExit(fullscreenSnapshot, pendingExitSnapshot)
            }
        }
        delay(120L)
        EventQueue.invokeLater {
            if (!enabled) {
                awtWindow.restoreDesktopReaderFullscreenExitBounds(pendingExitSnapshot.getAndSet(null))
            }
            awtWindow.refreshDesktopReaderWindowFocus()
        }
        if (!enabled) {
            for (delayMillis in DesktopReaderWindowFullscreenExitFocusRetryDelaysMillis) {
                delay(delayMillis)
                EventQueue.invokeLater {
                    awtWindow.refreshDesktopReaderWindowFocus()
                }
            }
        }
    }

    DisposableEffect(awtWindow) {
        onDispose {
            EventQueue.invokeLater {
                awtWindow.beginDesktopReaderFullscreenExit(fullscreenSnapshot)
            }
        }
    }
}

private data class DesktopReaderFullscreenSnapshot(
    val device: GraphicsDevice?,
    val frameState: Int?,
    val frameBounds: Rectangle?,
    val alwaysOnTop: Boolean
)

private fun java.awt.Window.enterDesktopReaderFullscreen(
    snapshotRef: AtomicReference<DesktopReaderFullscreenSnapshot?>
) {
    if (!isDisplayable) return
    focusableWindowState = true
    captureDesktopReaderFullscreenSnapshot(snapshotRef)
    applyDesktopReaderBorderlessFullscreen(snapshotRef.get())
    refreshDesktopReaderWindowFocus()
}

private fun java.awt.Window.captureDesktopReaderFullscreenSnapshot(
    snapshotRef: AtomicReference<DesktopReaderFullscreenSnapshot?>
) {
    snapshotRef.compareAndSet(
        null,
        DesktopReaderFullscreenSnapshot(
            device = graphicsConfiguration?.device,
            frameState = (this as? Frame)?.extendedState,
            frameBounds = bounds.desktopReaderCopy(),
            alwaysOnTop = isAlwaysOnTop
        )
    )
}

private fun java.awt.Window.applyDesktopReaderBorderlessFullscreen(snapshot: DesktopReaderFullscreenSnapshot?) {
    if (!isDisplayable) return
    val device = snapshot?.device ?: graphicsConfiguration?.device
    val frame = this as? Frame
    focusableWindowState = true
    if (frame != null) {
        frame.extendedState = frame.extendedState and Frame.ICONIFIED.inv() and Frame.MAXIMIZED_BOTH.inv()
        frame.state = Frame.NORMAL
    }
    device?.let { fullscreenDevice ->
        runCatching {
            if (fullscreenDevice.fullScreenWindow == this) {
                fullscreenDevice.fullScreenWindow = null
            }
        }
    }
    runCatching {
        bounds = device?.desktopReaderScreenBounds() ?: graphicsConfiguration?.bounds?.desktopReaderCopy() ?: bounds
    }
    runCatching {
        isAlwaysOnTop = true
    }
}

private fun java.awt.Window.beginDesktopReaderFullscreenExit(
    snapshotRef: AtomicReference<DesktopReaderFullscreenSnapshot?>,
    pendingExitSnapshotRef: AtomicReference<DesktopReaderFullscreenSnapshot?>? = null
) {
    val snapshot = snapshotRef.getAndSet(null)
    if (snapshot == null) return
    pendingExitSnapshotRef?.set(snapshot)
    val device = snapshot.device ?: graphicsConfiguration?.device
    device?.let { fullscreenDevice ->
        runCatching {
            if (fullscreenDevice.fullScreenWindow == this) {
                fullscreenDevice.fullScreenWindow = null
            }
        }
    }
    runCatching {
        isAlwaysOnTop = snapshot.alwaysOnTop
    }
    if (!isVisible) {
        isVisible = true
    }
    (this as? Frame)?.let { frame ->
        frame.state = Frame.NORMAL
        frame.extendedState = Frame.NORMAL
    }
}

private fun java.awt.Window.restoreDesktopReaderFullscreenExitBounds(snapshot: DesktopReaderFullscreenSnapshot?) {
    if (snapshot == null) return
    runCatching {
        isAlwaysOnTop = snapshot.alwaysOnTop
    }
    if (!isVisible) {
        isVisible = true
    }
    val frame = this as? Frame
    if (frame == null) {
        bounds = snapshot.frameBounds.desktopReaderRestoreBounds(snapshot.device)
        return
    }
    val restoreMaximized = snapshot.frameState?.let { state ->
        state and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH
    } == true
    frame.extendedState = Frame.NORMAL
    frame.state = Frame.NORMAL
    frame.bounds = snapshot.frameBounds.desktopReaderRestoreBounds(snapshot.device)
    if (restoreMaximized) {
        frame.maximizedBounds = snapshot.device?.desktopReaderUsableBounds()
        EventQueue.invokeLater {
            if (frame.isDisplayable && frame.isShowing) {
                frame.extendedState = Frame.MAXIMIZED_BOTH
            }
        }
    }
    frame.toFront()
    frame.requestFocus()
    frame.validate()
}

private fun GraphicsDevice.desktopReaderScreenBounds(): Rectangle {
    return defaultConfiguration.bounds.desktopReaderCopy()
}

private fun GraphicsDevice.desktopReaderUsableBounds(): Rectangle? {
    val configuration = defaultConfiguration ?: return null
    return runCatching {
        val bounds = configuration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            (bounds.width - insets.left - insets.right).coerceAtLeast(1),
            (bounds.height - insets.top - insets.bottom).coerceAtLeast(1)
        )
    }.getOrNull()
}

private fun Rectangle?.desktopReaderRestoreBounds(device: GraphicsDevice?): Rectangle {
    val usableBounds = device?.desktopReaderUsableBounds()
        ?: return this?.desktopReaderCopy() ?: Rectangle(80, 80, 1280, 820)
    val source = this ?: usableBounds
    val width = source.width.coerceIn(640, usableBounds.width.coerceAtLeast(640))
    val height = source.height.coerceIn(480, usableBounds.height.coerceAtLeast(480))
    val looksFullscreen = source.x <= usableBounds.x &&
        source.y <= usableBounds.y &&
        source.width >= usableBounds.width &&
        source.height >= usableBounds.height
    if (looksFullscreen) {
        return usableBounds.desktopReaderCopy()
    }
    val maxX = (usableBounds.x + usableBounds.width - width).coerceAtLeast(usableBounds.x)
    val maxY = (usableBounds.y + usableBounds.height - height).coerceAtLeast(usableBounds.y)
    return Rectangle(
        source.x.coerceIn(usableBounds.x, maxX),
        source.y.coerceIn(usableBounds.y, maxY),
        width,
        height
    )
}

private fun Rectangle.desktopReaderCopy(): Rectangle {
    return Rectangle(x, y, width, height)
}

private fun java.awt.Window.refreshDesktopReaderWindowFocus() {
    if (!isDisplayable) return
    if (this is Frame && extendedState and Frame.ICONIFIED != 0) {
        extendedState = extendedState and Frame.ICONIFIED.inv()
    }
    toFront()
    requestFocus()
    requestFocusInWindow()
    focusOwner?.requestFocus()
}

@Composable
internal fun DesktopReaderFullscreenKeyEffect(
    enabled: Boolean,
    onKeyPressed: (AwtKeyEvent) -> Boolean
) {
    DesktopReaderKeyDispatcherEffect(
        enabled = enabled,
        allowChromeModalWindows = false,
        onKeyPressed = onKeyPressed
    )
}

@Composable
internal fun DesktopReaderKeyDispatcherEffect(
    enabled: Boolean,
    allowChromeModalWindows: Boolean = false,
    allowPanelModalWindows: Boolean = false,
    dispatchWhenOwnerWindowActive: Boolean = true,
    onKeyPressed: (AwtKeyEvent) -> Boolean
) {
    val currentOnKeyPressed by rememberUpdatedState(onKeyPressed)
    DisposableEffect(
        enabled,
        allowChromeModalWindows,
        allowPanelModalWindows,
        dispatchWhenOwnerWindowActive
    ) {
        if (!enabled) {
            onDispose {}
        } else {
            val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            val dispatcher = java.awt.KeyEventDispatcher { event ->
                val keyWindow = focusManager.focusedWindow ?: focusManager.activeWindow
                val activeReaderModalKind = keyWindow?.desktopReaderModalWindowKind()
                val activeWindowAllowed = desktopReaderKeyDispatchAllowedForActiveWindowKind(
                    activeReaderModalKind = activeReaderModalKind,
                    allowChromeModalWindows = allowChromeModalWindows,
                    allowPanelModalWindows = allowPanelModalWindows,
                    dispatchWhenOwnerWindowActive = dispatchWhenOwnerWindowActive
                )
                activeWindowAllowed && event.id == AwtKeyEvent.KEY_PRESSED && currentOnKeyPressed(event)
            }
            focusManager.addKeyEventDispatcher(dispatcher)
            onDispose {
                focusManager.removeKeyEventDispatcher(dispatcher)
            }
        }
    }
}

internal enum class DesktopReaderModalWindowKind {
    CHROME,
    PANEL,
    POPUP
}

internal fun desktopReaderKeyDispatchAllowedForActiveWindowKind(
    activeReaderModalKind: DesktopReaderModalWindowKind?,
    allowChromeModalWindows: Boolean,
    allowPanelModalWindows: Boolean,
    dispatchWhenOwnerWindowActive: Boolean
): Boolean {
    return when (activeReaderModalKind) {
        null -> dispatchWhenOwnerWindowActive
        DesktopReaderModalWindowKind.CHROME -> allowChromeModalWindows
        DesktopReaderModalWindowKind.PANEL -> allowPanelModalWindows
        DesktopReaderModalWindowKind.POPUP -> false
    }
}

private fun java.awt.Window.desktopReaderModalWindowKind(): DesktopReaderModalWindowKind? {
    val windowTitle = when (this) {
        is java.awt.Dialog -> title
        is Frame -> title
        else -> ""
    }
    return desktopReaderModalWindowKind(
        windowName = name.orEmpty(),
        windowTitle = windowTitle
    )
}

internal fun desktopReaderModalWindowKind(
    windowName: String,
    windowTitle: String
): DesktopReaderModalWindowKind? {
    return when {
        windowName == "${DesktopReaderModalWindowNamePrefix}ChromeTop" ||
            windowName == "${DesktopReaderModalWindowNamePrefix}ChromeBottom" ||
            windowTitle.startsWith("Reader Chrome") -> DesktopReaderModalWindowKind.CHROME

        windowName == "${DesktopReaderModalWindowNamePrefix}Panel" ||
            windowName == "${DesktopReaderModalWindowNamePrefix}PanelLeft" ||
            windowName == "${DesktopReaderModalWindowNamePrefix}PanelRight" ||
            windowTitle.startsWith("Reader Panel") ||
            windowTitle.startsWith("Reader Navigation") ||
            windowTitle.startsWith("Reader Tools") -> DesktopReaderModalWindowKind.PANEL

        windowName.startsWith(DesktopReaderModalWindowNamePrefix) ||
            windowTitle.startsWith("Reader Popup") -> DesktopReaderModalWindowKind.POPUP

        else -> null
    }
}

internal const val DesktopReaderModalWindowNamePrefix = "shared-reader-modal:"

internal data class DesktopWebViewRuntimeState(
    val initialized: Boolean = false,
    val restartRequired: Boolean = false,
    val downloadProgress: Float = -1f,
    val errorMessage: String? = null
)

internal enum class DesktopEpubWebViewBackend(
    val logName: String,
    val displayName: String
) {
    WINDOWS_WEBVIEW2("webview2", "Microsoft Edge WebView2"),
    WEBKIT("webkit", "WebKit"),
    UNSUPPORTED("unsupported", "native webview")
}

internal fun desktopEpubWebViewBackend(
    platform: DesktopPlatform = currentDesktopPlatform()
): DesktopEpubWebViewBackend {
    return when (platform.os) {
        DesktopOperatingSystem.WINDOWS -> DesktopEpubWebViewBackend.WINDOWS_WEBVIEW2
        DesktopOperatingSystem.LINUX,
        DesktopOperatingSystem.MACOS -> DesktopEpubWebViewBackend.WEBKIT
        DesktopOperatingSystem.OTHER -> DesktopEpubWebViewBackend.UNSUPPORTED
    }
}

internal fun desktopEpubWebViewUsesNativeSwtBrowser(
    platform: DesktopPlatform = currentDesktopPlatform()
): Boolean {
    return desktopEpubWebViewBackend(platform) != DesktopEpubWebViewBackend.UNSUPPORTED
}

internal fun desktopEpubWebViewUsesWebView2(
    platform: DesktopPlatform = currentDesktopPlatform()
): Boolean {
    return desktopEpubWebViewBackend(platform) == DesktopEpubWebViewBackend.WINDOWS_WEBVIEW2
}

internal fun desktopEpubWebViewCanRender(
    state: DesktopWebViewRuntimeState,
    platform: DesktopPlatform = currentDesktopPlatform()
): Boolean {
    return desktopEpubWebViewUsesNativeSwtBrowser(platform)
}

@Composable
internal fun DesktopWebViewRuntimeIndicator(
    state: DesktopWebViewRuntimeState,
    modifier: Modifier = Modifier
) {
    val platform = currentDesktopPlatform()
    val message = when {
        !desktopEpubWebViewUsesNativeSwtBrowser(platform) ->
            readerString("desktop_webview_unsupported", "Embedded webview is unavailable on this desktop platform.")
        state.errorMessage != null -> readerString("desktop_webview_start_error", "Embedded webview could not start: %1\$s", state.errorMessage)
        state.restartRequired -> readerString("desktop_webview_restart_required", "Embedded webview installed. Restart Episteme to finish setup.")
        state.downloadProgress >= 0f -> readerString("desktop_webview_preparing_progress", "Preparing bundled embedded webview %1\$d%%", state.downloadProgress.toInt())
        else -> readerString("desktop_webview_preparing", "Preparing embedded webview...")
    }

    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.errorMessage == null && !state.restartRequired) {
                if (desktopEpubWebViewUsesNativeSwtBrowser(platform)) {
                    CircularProgressIndicator()
                }
            }
            Text(
                text = message,
                color = if (state.errorMessage == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            if (state.downloadProgress in 0f..100f) {
                LinearProgressIndicator(
                    progress = { state.downloadProgress / 100f },
                    modifier = Modifier.width(260.dp)
                )
            }
        }
    }
}
