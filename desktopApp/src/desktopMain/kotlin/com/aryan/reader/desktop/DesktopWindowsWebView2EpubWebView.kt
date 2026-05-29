package com.aryan.reader.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.ui.ReaderContentNavigationTarget
import com.aryan.reader.shared.ui.readerString
import kotlinx.coroutines.delay
import org.eclipse.swt.SWT
import org.eclipse.swt.awt.SWT_AWT
import org.eclipse.swt.browser.Browser
import org.eclipse.swt.browser.BrowserFunction
import org.eclipse.swt.browser.LocationEvent
import org.eclipse.swt.browser.LocationListener
import org.eclipse.swt.browser.ProgressAdapter
import org.eclipse.swt.browser.ProgressEvent
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.Shell
import java.awt.Canvas
import java.awt.EventQueue
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

@Composable
internal fun DesktopNativeSwtEpubWebView(
    html: String,
    appearanceScript: String,
    highlightPaletteScript: String,
    navigationTarget: ReaderContentNavigationTarget,
    highlights: List<UserHighlight>,
    onHighlightCreated: (UserHighlight) -> Unit,
    onHighlightSelected: (String) -> Unit,
    isFullscreen: Boolean,
    onKeyboardNavigation: (DesktopReaderKeyNavigation) -> Unit,
    onSelectionAction: (DesktopReaderSelectionActionPayload) -> Unit,
    onLinkClicked: (DesktopEpubLinkClick) -> Unit,
    onVisiblePageChanged: (Int, ReaderLocator?) -> Unit,
    onPointerActivity: () -> Unit = {},
    networkAccessEnabled: Boolean,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val backend = remember { desktopEpubWebViewBackend() }
    if (backend == DesktopEpubWebViewBackend.UNSUPPORTED) {
        DesktopNativeWebViewError(
            backend = backend,
            message = desktopNativeWebViewUnavailableMessage(backend),
            modifier = modifier.fillMaxSize()
        )
        return
    }
    val latestOnLinkClicked by rememberUpdatedState(onLinkClicked)
    val bridgeHandlers = rememberDesktopEpubBridgeHandlers(
        onHighlightCreated = onHighlightCreated,
        onHighlightSelected = onHighlightSelected,
        onKeyboardNavigation = onKeyboardNavigation,
        onSelectionAction = onSelectionAction,
        onLinkClicked = onLinkClicked,
        onVisiblePageChanged = onVisiblePageChanged,
        onPointerActivity = onPointerActivity
    )
    val bridgeHandlersByMethod = remember(bridgeHandlers) {
        bridgeHandlers.associateBy { it.methodName }
    }
    val hostBackground = remember(backgroundColor) { backgroundColor.toAwtColor() }
    val panel = remember { DesktopWindowsWebView2Panel(hostBackground, backend) }
    val composeDensity = LocalDensity.current
    var loaded by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(-1f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val webViewHtml = remember(html, networkAccessEnabled) {
        html.withDesktopWebView2Bootstrap(networkAccessEnabled = networkAccessEnabled)
    }

    DisposableEffect(panel) {
        onDispose {
            logDesktopWebView2("compose_dispose panel=${panel.instanceId}")
            panel.disposeWebView(waitForSwtDisposal = true)
        }
    }

    LaunchedEffect(hostBackground) {
        panel.updateBackground(hostBackground)
    }

    Box(
        modifier = modifier.fillMaxSize().onSizeChanged { size ->
            logWebViewLayoutDiag(
                "compose_webview_box panel=${panel.instanceId} size=${size.width}x${size.height} " +
                    "loaded=$loaded network=$networkAccessEnabled navMode=${navigationTarget.readingMode} " +
                    "composeDensity=${composeDensity.density.formatLogFloat()}"
            )
        }
    ) {
        SwingPanel(
            background = backgroundColor,
            factory = { panel },
            update = { currentPanel ->
                currentPanel.configure(
                    bridgeHandlersByMethod = bridgeHandlersByMethod,
                    networkAccessEnabled = networkAccessEnabled,
                    onLinkIntercepted = { link -> latestOnLinkClicked(link) },
                    onLoadStateChanged = { isLoaded, progress ->
                        loaded = isLoaded
                        loadProgress = progress
                    },
                    onError = { message ->
                        errorMessage = message
                        loaded = false
                        loadProgress = -1f
                    }
                )
            },
            modifier = Modifier
                .matchParentSize()
                .onSizeChanged { size ->
                    logWebViewLayoutDiag(
                        "compose_swing_panel panel=${panel.instanceId} size=${size.width}x${size.height} " +
                            "loaded=$loaded composeDensity=${composeDensity.density.formatLogFloat()}"
                    )
                }
        )

        LaunchedEffect(webViewHtml) {
            loaded = false
            loadProgress = -1f
            errorMessage = null
            logDesktopWebView2(
                "compose_load_request panel=${panel.instanceId} rawHtmlChars=${html.length} " +
                    "wrappedHtmlChars=${webViewHtml.length} rawHash=${html.hashCode()} wrappedHash=${webViewHtml.hashCode()} " +
                    "network=$networkAccessEnabled"
            )
            logWebViewLayoutDiag(
                "compose_load_request panel=${panel.instanceId} rawHtmlChars=${html.length} " +
                    "wrappedHtmlChars=${webViewHtml.length} navMode=${navigationTarget.readingMode} " +
                    "background=${backgroundColor.toArgb()}"
            )
            panel.loadHtml(webViewHtml)
        }

        LaunchedEffect(loaded) {
            if (!loaded) return@LaunchedEffect
            logDesktopWebView2("compose_loaded panel=${panel.instanceId} action=install_key_navigation")
            panel.executeJavaScript(DesktopEpubKeyNavigationScript)
        }

        LaunchedEffect(isFullscreen, loaded) {
            if (!loaded) return@LaunchedEffect
            logDesktopWebView2("compose_script panel=${panel.instanceId} name=fullscreen value=$isFullscreen")
            panel.executeJavaScript(
                "window.readerDesktopFullscreen = ${if (isFullscreen) "true" else "false"};" +
                    "window.dispatchEvent(new Event('resize'));"
            )
            panel.relayoutWebView("fullscreen_state_changed")
            DesktopWebView2FullscreenRelayoutDelaysMillis.forEach { delayMillis ->
                delay(delayMillis)
                panel.relayoutWebView("fullscreen_state_changed_after_${delayMillis}ms")
                panel.executeJavaScript("window.dispatchEvent(new Event('resize'));")
            }
        }

        LaunchedEffect(html, loaded) {
            if (!loaded) return@LaunchedEffect
            logDesktopWebView2("compose_script panel=${panel.instanceId} name=desktop_finished")
            panel.executeJavaScript("window.readerPaginationLayoutLog && window.readerPaginationLayoutLog('desktop_finished');")
        }

        LaunchedEffect(appearanceScript, loaded) {
            if (!loaded) return@LaunchedEffect
            logDesktopWebView2(
                "compose_script panel=${panel.instanceId} name=appearance chars=${appearanceScript.length} hash=${appearanceScript.hashCode()}"
            )
            panel.executeJavaScript(appearanceScript + "\n" + desktopWebView2DocumentProbeScript("appearance_applied"))
        }

        LaunchedEffect(highlightPaletteScript, loaded) {
            if (!loaded) return@LaunchedEffect
            logDesktopWebView2(
                "compose_script panel=${panel.instanceId} name=highlight_palette chars=${highlightPaletteScript.length} " +
                    "hash=${highlightPaletteScript.hashCode()}"
            )
            panel.executeJavaScript(highlightPaletteScript)
        }

        LaunchedEffect(
            navigationTarget.requestId,
            navigationTarget.readingMode,
            loaded
        ) {
            if (navigationTarget.readingMode != ReaderReadingMode.VERTICAL) return@LaunchedEffect
            if (!loaded) return@LaunchedEffect
            val locator = navigationTarget.locator ?: return@LaunchedEffect
            logDesktopWebView2(
                "compose_script panel=${panel.instanceId} name=scroll_locator request=${navigationTarget.requestId} " +
                    "chapter=${locator.chapterIndex} page=${locator.pageIndex}"
            )
            panel.executeJavaScript("window.readerScrollToLocator && window.readerScrollToLocator(${locator.toReaderLocatorJson()});")
        }

        LaunchedEffect(
            navigationTarget.ttsRequestId,
            navigationTarget.ttsLocator,
            navigationTarget.readingMode,
            loaded
        ) {
            if (!loaded) return@LaunchedEffect
            val locator = navigationTarget.ttsLocator
            val command = if (locator == null) {
                logDesktopTts(
                    "epub_highlight_command clear mode=${navigationTarget.readingMode} request=${navigationTarget.ttsRequestId}"
                )
                "window.readerSetTtsLocator && window.readerSetTtsLocator(null, false);"
            } else {
                val follow = navigationTarget.readingMode == ReaderReadingMode.VERTICAL
                logDesktopTts(
                    "epub_highlight_command set mode=${navigationTarget.readingMode} request=${navigationTarget.ttsRequestId} " +
                        "follow=$follow chapter=${locator.chapterIndex} page=${locator.pageIndex} " +
                        "offsets=${locator.startOffset}..${locator.endOffset} cfi=\"${locator.cfi.orEmpty().logPreview()}\" " +
                        "text=\"${locator.textQuote.orEmpty().logPreview()}\""
                )
                "window.readerSetTtsLocator && window.readerSetTtsLocator(${locator.toReaderLocatorJson()}, $follow);"
            }
            panel.executeJavaScript(command)
        }

        LaunchedEffect(highlights, loaded) {
            if (!loaded) return@LaunchedEffect
            val highlightsJson = EpubAnnotationSerializer.highlightsToJson(highlights)
            logDesktopWebView2(
                "compose_script panel=${panel.instanceId} name=apply_highlights count=${highlights.size} chars=${highlightsJson.length}"
            )
            panel.executeJavaScript("window.readerApplyHighlights && window.readerApplyHighlights($highlightsJson);")
        }

        if (errorMessage != null) {
            DesktopNativeWebViewError(
                backend = backend,
                message = errorMessage.orEmpty(),
                modifier = Modifier.fillMaxSize()
            )
        } else if (!loaded) {
            if (loadProgress in 0f..1f) {
                LinearProgressIndicator(
                    progress = { loadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DesktopNativeWebViewError(
    backend: DesktopEpubWebViewBackend,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = readerString(
                "desktop_native_webview_start_error",
                "%1\$s could not start: %2\$s",
                backend.displayName,
                message
            ),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
}

private class DesktopWindowsWebView2Panel(
    initialBackground: java.awt.Color,
    private val backend: DesktopEpubWebViewBackend
) : Canvas() {
    val instanceId: Int = nextDesktopWebView2InstanceId()

    @Volatile
    private var bridgeHandlersByMethod: Map<String, DesktopEpubBridgeHandler> = emptyMap()

    @Volatile
    private var networkAccessEnabled: Boolean = true

    @Volatile
    private var onLinkIntercepted: (DesktopEpubLinkClick) -> Unit = {}

    @Volatile
    private var onLoadStateChanged: (Boolean, Float) -> Unit = { _, _ -> }

    @Volatile
    private var onError: (String) -> Unit = {}

    private var controller: DesktopWindowsWebView2Controller? = null
    private var requestedHtml: String? = null

    @Volatile
    private var lastLoadStartedAtNanos: Long = 0L

    val hasController: Boolean get() = controller != null

    @Volatile
    private var disposeInProgress = false

    @Volatile
    private var hostWindowClosing = false

    private var hostWindow: java.awt.Window? = null
    private var hostWindowListener: WindowAdapter? = null

    init {
        background = initialBackground
        updateModeSwitchPanelState("init")
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) {
                    logDesktopWebView2("panel_resized panel=$instanceId size=${width}x${height}")
                    logWebViewLayoutDiag(
                        "awt_canvas_resized panel=$instanceId size=${width}x${height} " +
                            "bounds=${bounds.formatAwtBounds()} screen=${safeScreenLocationLog()}"
                    )
                    updateModeSwitchPanelState("component_resized")
                    controller?.resize(width, height, reason = "component_resized")
                }

                override fun componentMoved(event: ComponentEvent) {
                    logWebViewLayoutDiag(
                        "awt_canvas_moved panel=$instanceId size=${width}x${height} " +
                            "bounds=${bounds.formatAwtBounds()} screen=${safeScreenLocationLog()}"
                    )
                    updateModeSwitchPanelState("component_moved")
                    controller?.resize(width, height, reason = "component_moved")
                }

                override fun componentShown(event: ComponentEvent) {
                    logWebViewLayoutDiag(
                        "awt_canvas_shown panel=$instanceId size=${width}x${height} " +
                            "bounds=${bounds.formatAwtBounds()} screen=${safeScreenLocationLog()}"
                    )
                    updateModeSwitchPanelState("component_shown")
                    controller?.resize(width, height, reason = "component_shown")
                }
            }
        )
    }

    fun relayoutWebView(reason: String) {
        EventQueue.invokeLater {
            updateModeSwitchPanelState("relayout_$reason")
            logWebViewLayoutDiag(
                "awt_canvas_relayout panel=$instanceId reason=$reason size=${width}x${height} " +
                    "bounds=${bounds.formatAwtBounds()} screen=${safeScreenLocationLog()} displayable=$isDisplayable"
            )
            revalidate()
            repaint()
            controller?.resize(width, height, reason = reason)
        }
    }

    fun updateBackground(color: java.awt.Color) {
        EventQueue.invokeLater {
            if (background != color) {
                background = color
                repaint()
            }
        }
    }

    fun configure(
        bridgeHandlersByMethod: Map<String, DesktopEpubBridgeHandler>,
        networkAccessEnabled: Boolean,
        onLinkIntercepted: (DesktopEpubLinkClick) -> Unit,
        onLoadStateChanged: (Boolean, Float) -> Unit,
        onError: (String) -> Unit
    ) {
        updateHostWindowListener()
        this.bridgeHandlersByMethod = bridgeHandlersByMethod
        this.networkAccessEnabled = networkAccessEnabled
        this.onLinkIntercepted = onLinkIntercepted
        this.onLoadStateChanged = { isLoaded, progress ->
            if (isLoaded) {
                val startedAt = lastLoadStartedAtNanos
                if (startedAt > 0L) {
                    logDesktopReaderOpenTrace {
                        "event=desktop_webview_panel_loaded panel=$instanceId " +
                            "durationMs=${startedAt.elapsedOpenTraceMs()} progress=$progress"
                    }
                    lastLoadStartedAtNanos = 0L
                }
            }
            onLoadStateChanged(isLoaded, progress)
        }
        this.onError = { message ->
            val startedAt = lastLoadStartedAtNanos
            logDesktopReaderOpenTrace {
                "event=desktop_webview_panel_error panel=$instanceId " +
                    "durationMs=${if (startedAt > 0L) startedAt.elapsedOpenTraceMs() else -1L} " +
                    "message=\"${message.logPreview(240)}\""
            }
            lastLoadStartedAtNanos = 0L
            onError(message)
        }
        logDesktopWebView2(
            "panel_configure panel=$instanceId handlers=${bridgeHandlersByMethod.size} network=$networkAccessEnabled " +
                "controller=${controller != null}"
        )
        updateModeSwitchPanelState("configure")
    }

    fun loadHtml(html: String) {
        if (requestedHtml == html) {
            logDesktopWebView2("panel_load_skip_duplicate panel=$instanceId htmlHash=${html.hashCode()}")
            logDesktopReaderOpenTrace {
                "event=desktop_webview_panel_load_skip_duplicate panel=$instanceId htmlHash=${html.hashCode()}"
            }
            return
        }
        lastLoadStartedAtNanos = System.nanoTime()
        requestedHtml = html
        logDesktopWebView2(
            "panel_load_requested panel=$instanceId htmlChars=${html.length} htmlHash=${html.hashCode()} " +
                "controller=${controller != null}"
        )
        logDesktopReaderOpenTrace {
            "event=desktop_webview_panel_load_requested panel=$instanceId htmlChars=${html.length} " +
                "htmlHash=${html.hashCode()} controller=${controller != null} canvas=${width}x${height}"
        }
        logWebViewLayoutDiag(
            "panel_load_requested panel=$instanceId canvas=${width}x${height} " +
                "bounds=${bounds.formatAwtBounds()} controller=${controller != null}"
        )
        updateModeSwitchPanelState("load_requested")
        ensureController(reason = "load_requested")
        controller?.loadHtml(html)
    }

    fun executeJavaScript(script: String) {
        logDesktopWebView2(
            "panel_execute panel=$instanceId scriptChars=${script.length} scriptHash=${script.hashCode()} controller=${controller != null}"
        )
        controller?.executeJavaScript(script)
    }

    fun disposeWebView(
        waitForSwtDisposal: Boolean = false,
        detachAwtCanvas: Boolean = true
    ) {
        if (disposeInProgress) {
            logDesktopWebView2(
                "panel_dispose_skip panel=$instanceId reason=in_progress controller=${controller != null}"
            )
            updateModeSwitchPanelState("dispose_skip_in_progress")
            return
        }
        disposeInProgress = true
        logDesktopWebView2(
            "panel_dispose panel=$instanceId controller=${controller != null} " +
                "waitForSwtDisposal=$waitForSwtDisposal detachAwtCanvas=$detachAwtCanvas"
        )
        try {
            updateModeSwitchPanelState("dispose_begin")
            if (detachAwtCanvas) {
                retireAwtCanvasFromReaderSurface()
            }
            controller?.dispose(waitForCompletion = waitForSwtDisposal)
            controller = null
            updateModeSwitchPanelState("dispose_end")
        } finally {
            disposeInProgress = false
        }
    }

    override fun addNotify() {
        super.addNotify()
        updateHostWindowListener()
        updateModeSwitchPanelState("add_notify")
        logDesktopWebView2(
            "panel_add_notify panel=$instanceId displayable=$isDisplayable showing=$isShowing " +
                "canvas=${width}x${height} controller=${controller != null} hasHtml=${requestedHtml != null}"
        )
        logWebViewLayoutDiag(
            "awt_canvas_add_notify panel=$instanceId displayable=$isDisplayable showing=$isShowing " +
                "size=${width}x${height} bounds=${bounds.formatAwtBounds()} screen=${safeScreenLocationLog()} " +
                "hasHtml=${requestedHtml != null}"
        )
        ensureController(reason = "add_notify")
        requestedHtml?.let { html -> controller?.loadHtml(html) }
        controller?.resize(width, height, reason = "add_notify")
    }

    override fun removeNotify() {
        logDesktopWebView2("panel_remove_notify panel=$instanceId")
        updateModeSwitchPanelState("remove_notify_begin")
        updateHostWindowListener()
        disposeWebView(
            waitForSwtDisposal = true,
            detachAwtCanvas = shouldRetireAwtCanvasFromReaderSurface()
        )
        clearHostWindowListener()
        super.removeNotify()
        updateModeSwitchPanelState("remove_notify_end")
    }

    private fun updateHostWindowListener() {
        val window = SwingUtilities.getWindowAncestor(this)
        if (hostWindow === window) return
        clearHostWindowListener()
        hostWindow = window
        hostWindowClosing = false
        if (window == null) return
        val listener = object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent?) {
                hostWindowClosing = true
                logDesktopWebView2("panel_host_window_closing panel=$instanceId")
                updateModeSwitchPanelState("host_window_closing")
            }

            override fun windowClosed(event: WindowEvent?) {
                hostWindowClosing = true
                logDesktopWebView2("panel_host_window_closed panel=$instanceId")
                updateModeSwitchPanelState("host_window_closed")
            }
        }
        hostWindowListener = listener
        window.addWindowListener(listener)
    }

    private fun clearHostWindowListener() {
        hostWindowListener?.let { listener ->
            hostWindow?.removeWindowListener(listener)
        }
        hostWindowListener = null
        hostWindow = null
    }

    private fun shouldRetireAwtCanvasFromReaderSurface(): Boolean {
        val window = hostWindow ?: SwingUtilities.getWindowAncestor(this)
        val shouldRetire = desktopWebView2ShouldRetireAwtCanvas(
            hostWindowClosing = hostWindowClosing,
            hostWindowDisplayable = window?.isDisplayable == true
        )
        if (!shouldRetire) {
            logDesktopWebView2(
                "panel_retire_skip panel=$instanceId reason=host_window_closing_or_disposed " +
                    "hostClosing=$hostWindowClosing host=${window.formatAwtComponentState()}"
            )
            updateModeSwitchPanelState("retire_skip_host_window_closing_or_disposed")
        }
        return shouldRetire
    }

    private fun retireAwtCanvasFromReaderSurface() {
        if (!shouldRetireAwtCanvasFromReaderSurface()) return
        runOnAwtEventThreadBlocking(
            onError = { error ->
                logDesktopWebView2(
                    "panel_retire_failed panel=$instanceId error=\"${error.message.orEmpty().logPreview(300)}\""
                )
            }
        ) {
            logWebViewLayoutDiag(
                "awt_canvas_retire panel=$instanceId size=${width}x${height} " +
                    "bounds=${bounds.formatAwtBounds()} displayable=$isDisplayable visible=$isVisible"
            )
            updateModeSwitchPanelState("retire_begin")
            val parentContainer = parent
            val grandParent = parentContainer?.parent
            logReaderModeSwitch(
                "webview2_interop_retire_begin panel=$instanceId " +
                    "parent=${parentContainer.formatAwtComponentState()} grandParent=${grandParent.formatAwtComponentState()}"
            )
            isVisible = false
            setBounds(0, 0, 0, 0)
            parentContainer?.isVisible = false
            parentContainer?.setBounds(0, 0, 0, 0)
            parentContainer?.revalidate()
            parentContainer?.repaint()
            grandParent?.revalidate()
            grandParent?.repaint()
            repaint()
            scheduleRetiredInteropHostCleanup(parentContainer, grandParent, reason = "retire")
            updateModeSwitchPanelState("retire_end")
            logReaderModeSwitch(
                "webview2_interop_retire_end panel=$instanceId " +
                    "parent=${parentContainer.formatAwtComponentState()} grandParent=${grandParent.formatAwtComponentState()}"
            )
        }
    }

    private fun scheduleRetiredInteropHostCleanup(
        parentContainer: java.awt.Container?,
        grandParent: java.awt.Container?,
        reason: String
    ) {
        if (parentContainer == null || grandParent == null) return
        EventQueue.invokeLater {
            cleanupRetiredInteropHost(parentContainer, grandParent, "${reason}_next_event")
        }
        EventQueue.invokeLater {
            DesktopWebView2InteropHostCleanupDelaysMillis.forEach { delayMillis ->
                javax.swing.Timer(delayMillis.toInt()) { _ ->
                    cleanupRetiredInteropHost(parentContainer, grandParent, "${reason}_after_${delayMillis}ms")
                }.apply {
                    isRepeats = false
                    start()
                }
            }
        }
    }

    private fun cleanupRetiredInteropHost(
        parentContainer: java.awt.Container,
        grandParent: java.awt.Container,
        reason: String
    ) {
        val isInteropHost = parentContainer.javaClass.simpleName == DesktopSwingInteropHostClassName
        val ownsOnlyRetiredPanel = parentContainer.components.all { component ->
            component === this || !component.isDisplayable || !component.isShowing
        }
        if (!isInteropHost || !ownsOnlyRetiredPanel || parentContainer.parent !== grandParent) {
            logReaderModeSwitch(
                "webview2_interop_host_cleanup_skip panel=$instanceId reason=$reason " +
                    "isInteropHost=$isInteropHost ownsOnlyRetiredPanel=$ownsOnlyRetiredPanel " +
                    "parent=${parentContainer.formatAwtComponentState()} " +
                    "grandParent=${grandParent.formatAwtComponentState()} " +
                    "parentParent=${parentContainer.parent?.javaClass?.simpleName ?: "none"} " +
                    "children=${parentContainer.formatAwtChildrenState()}"
            )
            return
        }
        logReaderModeSwitch(
            "webview2_interop_host_cleanup_begin panel=$instanceId reason=$reason " +
                "parent=${parentContainer.formatAwtComponentState()} " +
                "grandParent=${grandParent.formatAwtComponentState()} " +
                "children=${parentContainer.formatAwtChildrenState()}"
        )
        if (parent === parentContainer) {
            parentContainer.remove(this)
        }
        parentContainer.removeAll()
        grandParent.remove(parentContainer)
        parentContainer.invalidate()
        grandParent.invalidate()
        grandParent.validate()
        grandParent.repaint()
        updateModeSwitchPanelState("interop_host_cleanup_$reason")
        logReaderModeSwitch(
            "webview2_interop_host_cleanup_end panel=$instanceId reason=$reason " +
                "parent=${parentContainer.formatAwtComponentState()} " +
                "grandParent=${grandParent.formatAwtComponentState()} " +
                "parentParent=${parentContainer.parent?.javaClass?.simpleName ?: "none"} " +
                "grandParentChildren=${grandParent.componentCount}"
        )
    }

    private fun updateModeSwitchPanelState(event: String) {
        val snapshot = modeSwitchPanelSnapshot(event)
        DesktopWebView2ModeSwitchPanelStates[instanceId] = snapshot
        logReaderModeSwitch("webview2_panel $snapshot")
    }

    fun modeSwitchPanelSnapshot(event: String): String {
        val parentContainer = parent
        val parentName = parentContainer?.javaClass?.simpleName ?: "none"
        val parentDetails = parentContainer.formatAwtComponentState()
        return "panel=$instanceId event=$event visible=$isVisible displayable=$isDisplayable " +
            "showing=$isShowing size=${width}x${height} bounds=${bounds.formatAwtBounds()} " +
            "parent=$parentName parentState=$parentDetails controller=${controller != null} hasHtml=${requestedHtml != null}"
    }

    private fun ensureController(reason: String) {
        if (controller != null) return
        if (!isDisplayable) {
            logDesktopWebView2("panel_controller_skip panel=$instanceId reason=$reason displayable=false")
            return
        }
        logDesktopWebView2(
            "panel_controller_create panel=$instanceId reason=$reason backend=${backend.logName} hasHtml=${requestedHtml != null}"
        )
        logDesktopReaderOpenTrace {
            "event=desktop_webview_controller_create panel=$instanceId reason=$reason " +
                "backend=${backend.logName} hasHtml=${requestedHtml != null} canvas=${width}x${height}"
        }
        var createdController: DesktopWindowsWebView2Controller? = null
        val newController = DesktopWindowsWebView2Controller(
            instanceId = instanceId,
            backend = backend,
            canvas = this,
            isNetworkAccessEnabled = { networkAccessEnabled },
            dispatchBridgeMessage = { method, params ->
                EventQueue.invokeLater {
                    bridgeHandlersByMethod[method]?.onMessage(params)
                }
            },
            dispatchLinkClick = { link ->
                EventQueue.invokeLater {
                    onLinkIntercepted(link)
                }
            },
            updateLoadState = { isLoaded, progress ->
                EventQueue.invokeLater {
                    onLoadStateChanged(isLoaded, progress)
                }
            },
            reportError = { error ->
                val message = error.desktopNativeWebViewMessage(backend)
                EventQueue.invokeLater {
                    createdController?.let { failedController ->
                        if (controller === failedController) {
                            controller = null
                        }
                    }
                    onError(message)
                }
            }
        )
        createdController = newController
        controller = newController
    }
}

private class DesktopWindowsWebView2Controller(
    private val instanceId: Int,
    private val backend: DesktopEpubWebViewBackend,
    private val canvas: Canvas,
    private val isNetworkAccessEnabled: () -> Boolean,
    private val dispatchBridgeMessage: (String, String) -> Unit,
    private val dispatchLinkClick: (DesktopEpubLinkClick) -> Unit,
    private val updateLoadState: (Boolean, Float) -> Unit,
    private val reportError: (Throwable) -> Unit
) {
    @Volatile
    private var disposed = false

    private var shell: Shell? = null
    private var browser: Browser? = null
    private var bridgeFunction: BrowserFunction? = null

    @Volatile
    private var pendingHtml: String? = null

    @Volatile
    private var lastBrowserBoundsLog: String = ""

    init {
        logDesktopWebView2("controller_init panel=$instanceId backend=${backend.logName} canvas=${canvas.width}x${canvas.height}")
        logDesktopReaderOpenTrace {
            "event=desktop_webview_controller_init panel=$instanceId backend=${backend.logName} " +
                "canvas=${canvas.width}x${canvas.height}"
        }
        logWebViewLayoutDiag(
            "controller_init panel=$instanceId backend=${backend.logName} canvas=${canvas.width}x${canvas.height} " +
                "canvasBounds=${canvas.bounds.formatAwtBounds()} screen=${canvas.safeScreenLocationLog()}"
        )
        DesktopSwtWebView2EventLoop.asyncExec(reportError) { display ->
            if (!disposed) createBrowser(display)
        }
    }

    fun loadHtml(html: String) {
        logDesktopWebView2(
            "controller_load_enqueue panel=$instanceId htmlChars=${html.length} htmlHash=${html.hashCode()} browser=${browser != null}"
        )
        logDesktopReaderOpenTrace {
            "event=desktop_webview_controller_load_enqueue panel=$instanceId htmlChars=${html.length} " +
                "htmlHash=${html.hashCode()} browser=${browser != null}"
        }
        logWebViewLayoutDiag(
            "controller_load_enqueue panel=$instanceId htmlChars=${html.length} browser=${browser != null} " +
                "canvas=${canvas.width}x${canvas.height} browserBounds=$lastBrowserBoundsLog"
        )
        DesktopSwtWebView2EventLoop.asyncExec(reportError) {
            if (disposed) return@asyncExec
            updateLoadState(false, -1f)
            pendingHtml = html
            val webView = browser
            if (webView == null || webView.isDisposed) {
                logDesktopWebView2("controller_load_pending panel=$instanceId reason=browser_not_ready")
                logDesktopReaderOpenTrace {
                    "event=desktop_webview_controller_load_pending panel=$instanceId reason=browser_not_ready"
                }
            } else {
                pendingHtml = null
                setBrowserText(webView, html, reason = "load")
            }
        }
    }

    fun executeJavaScript(script: String) {
        DesktopSwtWebView2EventLoop.asyncExec(reportError) {
            if (disposed) return@asyncExec
            val webView = browser
            if (webView == null || webView.isDisposed) {
                logDesktopWebView2(
                    "controller_execute_drop panel=$instanceId reason=browser_not_ready " +
                        "scriptChars=${script.length} scriptHash=${script.hashCode()}"
                )
            } else {
                val executed = webView.execute(script)
                logDesktopWebView2(
                    "controller_execute panel=$instanceId executed=$executed scriptChars=${script.length} scriptHash=${script.hashCode()}"
                )
            }
        }
    }

    fun resize(width: Int, height: Int, reason: String = "resize") {
        DesktopSwtWebView2EventLoop.asyncExec(reportError) {
            if (disposed) return@asyncExec
            applyCanvasSizeToBrowser(width, height, reason = reason)
        }
    }

    fun dispose(waitForCompletion: Boolean = false) {
        if (disposed) return
        disposed = true
        logDesktopWebView2("controller_dispose panel=$instanceId waitForCompletion=$waitForCompletion")
        logReaderModeSwitch("webview2_controller_dispose panel=$instanceId waitForCompletion=$waitForCompletion")
        if (waitForCompletion) {
            DesktopSwtWebView2EventLoop.syncExec({}) {
                disposeSwtWidgets()
            }
        } else {
            DesktopSwtWebView2EventLoop.asyncExec({}) {
                disposeSwtWidgets()
            }
        }
    }

    private fun disposeSwtWidgets() {
        logReaderModeSwitch(
            "webview2_swt_dispose_begin panel=$instanceId shell=${shell?.isDisposed == false} browser=${browser?.isDisposed == false}"
        )
        bridgeFunction?.takeUnless { it.isDisposed }?.dispose()
        bridgeFunction = null
        browser?.takeUnless { it.isDisposed }?.dispose()
        browser = null
        shell?.takeUnless { it.isDisposed }?.dispose()
        shell = null
        lastBrowserBoundsLog = ""
        logReaderModeSwitch("webview2_swt_dispose_end panel=$instanceId")
    }

    private fun createBrowser(display: Display) {
        logDesktopWebView2("controller_create_start panel=$instanceId displayDisposed=${display.isDisposed}")
        logDesktopReaderOpenTrace {
            "event=desktop_webview_controller_create_start panel=$instanceId displayDisposed=${display.isDisposed}"
        }
        runCatching {
            shell = SWT_AWT.new_Shell(display, canvas)
            logDesktopWebView2("controller_shell_created panel=$instanceId shellDisposed=${shell?.isDisposed == true}")
            logWebViewLayoutDiag(
                "swt_shell_created panel=$instanceId canvas=${canvas.width}x${canvas.height} " +
                    "canvasBounds=${canvas.bounds.formatAwtBounds()} shellBounds=${shell?.bounds?.formatSwtBounds().orEmpty()}"
            )
            val webView = Browser(shell, backend.swtBrowserStyle())
            browser = webView
            val swtBackground = org.eclipse.swt.graphics.Color(
                display,
                canvas.background.red,
                canvas.background.green,
                canvas.background.blue
            )
            shell?.background = swtBackground
            webView.background = swtBackground
            shell?.addDisposeListener {
                if (!swtBackground.isDisposed) swtBackground.dispose()
            }
            val browserType = webView.browserType.orEmpty()
            logDesktopWebView2(
                "controller_browser_created panel=$instanceId backend=${backend.logName} browserType=\"$browserType\""
            )
            logDesktopReaderOpenTrace {
                "event=desktop_webview_controller_browser_created panel=$instanceId backend=${backend.logName} " +
                    "browserType=\"${browserType.logPreview(120)}\""
            }
            logWebViewLayoutDiag(
                "swt_browser_created panel=$instanceId backend=${backend.logName} browserType=\"$browserType\" " +
                    "browserBounds=${webView.bounds.formatSwtBounds()} shellBounds=${shell?.bounds?.formatSwtBounds().orEmpty()}"
            )
            check(backend.acceptsBrowserType(browserType)) {
                "${backend.displayName} is not available; SWT opened '${browserType.ifBlank { "unknown" }}' instead."
            }
            val warmupHtml = desktopWebView2WarmupHtml(canvas.background)
            val warmupAccepted = webView.setText(warmupHtml)
            logDesktopReaderOpenTrace {
                "event=desktop_webview_warmup_loaded panel=$instanceId accepted=$warmupAccepted " +
                    "background=\"${canvas.background.toCssHex()}\""
            }
            run {
                bridgeFunction = object : BrowserFunction(webView, DesktopWebView2NativeBridgeName) {
                    override fun function(arguments: Array<out Any?>): Any? {
                        val method = arguments.getOrNull(0)?.toString().orEmpty()
                        if (method.isBlank()) return null
                        val params = arguments.getOrNull(1)?.toString() ?: "{}"
                        if (method == DesktopWebView2DiagnosticMethodName) {
                            val preview = params.logPreview(6000)
                            logDesktopWebView2("bridge_diagnostic panel=$instanceId params=\"$preview\"")
                            logWebViewLayoutDiag("document_probe panel=$instanceId params=\"$preview\"")
                        } else {
                            logDesktopWebView2(
                                "bridge_message panel=$instanceId method=$method paramsChars=${params.length} params=\"${params.logPreview()}\""
                            )
                            dispatchBridgeMessage(method, params)
                        }
                        return null
                    }
                }
                webView.addLocationListener(
                    object : LocationListener {
                        override fun changing(event: LocationEvent) {
                            val location = event.location.orEmpty()
                            logDesktopWebView2(
                                "location_changing panel=$instanceId top=${event.top} doit=${event.doit} " +
                                    "location=\"${location.logPreview()}\""
                            )
                            if (!isNetworkAccessEnabled() && location.isRemoteNetworkUrl()) {
                                logEpubLink("request_blocked_offline url=\"${location.logPreview()}\"")
                                event.doit = false
                                return
                            }
                            val link = location.readerLinkClickFromIntercept() ?: return
                            logEpubLink(
                                "request_intercept_webview2 url=\"${location.logPreview()}\" " +
                                    "href=\"${link.href.logPreview()}\""
                            )
                            event.doit = false
                            dispatchLinkClick(link.copy(source = "request"))
                        }

                        override fun changed(event: LocationEvent) = Unit
                    }
                )
                webView.addProgressListener(
                    object : ProgressAdapter() {
                        private var lastLoggedProgressBucket = -1

                        override fun changed(event: ProgressEvent) {
                            val total = event.total
                            val progress = if (total > 0) {
                                event.current.coerceIn(0, total).toFloat() / total.toFloat()
                            } else {
                                -1f
                            }
                            val bucket = if (progress < 0f) {
                                -1
                            } else {
                                (progress * 4).toInt().coerceIn(0, 4)
                            }
                            if (bucket != lastLoggedProgressBucket) {
                                lastLoggedProgressBucket = bucket
                                logDesktopWebView2(
                                    "progress_changed panel=$instanceId current=${event.current} total=${event.total} " +
                                        "progress=${if (progress < 0f) "unknown" else progress.formatLogFloat()}"
                                )
                            }
                            updateLoadState(false, progress)
                        }

                        override fun completed(event: ProgressEvent) {
                            val bridgeInjected = webView.execute(DesktopWebView2BridgeRuntimeScript)
                            applyCanvasSizeToBrowser(canvas.width, canvas.height, reason = "load_completed")
                            val probeInjected = webView.execute(desktopWebView2DocumentProbeScript("load_completed"))
                            logDesktopWebView2(
                                "progress_completed panel=$instanceId bridgeInjected=$bridgeInjected probeInjected=$probeInjected " +
                                    "current=${event.current} total=${event.total}"
                            )
                            logDesktopReaderOpenTrace {
                                "event=desktop_webview_progress_completed panel=$instanceId " +
                                    "bridgeInjected=$bridgeInjected probeInjected=$probeInjected " +
                                    "current=${event.current} total=${event.total}"
                            }
                            logWebViewLayoutDiag(
                                "progress_completed panel=$instanceId bridgeInjected=$bridgeInjected " +
                                    "current=${event.current} total=${event.total}"
                            )
                            updateLoadState(true, 1f)
                        }
                    }
                )
                pendingHtml?.let { html ->
                    pendingHtml = null
                    setBrowserText(webView, html, reason = "browser_ready")
                }
            }
            applyCanvasSizeToBrowser(canvas.width, canvas.height, reason = "open")
            shell?.open()
            logDesktopWebView2(
                "controller_open panel=$instanceId shellVisible=${shell?.isVisible == true} " +
                    "initial=${canvas.width}x${canvas.height} " +
                    "browserBounds=${browser?.bounds?.width ?: -1}x${browser?.bounds?.height ?: -1}"
            )
            logDesktopReaderOpenTrace {
                "event=desktop_webview_controller_open panel=$instanceId shellVisible=${shell?.isVisible == true} " +
                    "initial=${canvas.width}x${canvas.height} " +
                    "browserBounds=${browser?.bounds?.width ?: -1}x${browser?.bounds?.height ?: -1}"
            }
            logWebViewLayoutDiag(
                "controller_open panel=$instanceId shellVisible=${shell?.isVisible == true} " +
                    "initial=${canvas.width}x${canvas.height} " +
                    "hostScale=${canvas.webView2HostScale().scaleX.formatLogFloat()}x${canvas.webView2HostScale().scaleY.formatLogFloat()} " +
                    "shellBounds=${shell?.bounds?.formatSwtBounds().orEmpty()} " +
                    "browserBounds=${browser?.bounds?.formatSwtBounds().orEmpty()} canvasBounds=${canvas.bounds.formatAwtBounds()}"
            )
        }.onFailure { error ->
            logDesktopWebView2(
                "controller_create_failed panel=$instanceId error=\"${error.desktopNativeWebViewMessage(backend).logPreview(300)}\""
            )
            logDesktopReaderOpenTrace {
                "event=desktop_webview_controller_create_failed panel=$instanceId " +
                    "error=\"${error.desktopNativeWebViewMessage(backend).logPreview(300)}\""
            }
            reportError(error)
            dispose()
        }
    }

    private fun setBrowserText(webView: Browser, html: String, reason: String) {
        val accepted = webView.setText(html)
        logDesktopWebView2(
            "controller_set_text panel=$instanceId reason=$reason accepted=$accepted " +
                "htmlChars=${html.length} htmlHash=${html.hashCode()}"
        )
        logDesktopReaderOpenTrace {
            "event=desktop_webview_controller_set_text panel=$instanceId reason=$reason accepted=$accepted " +
                "htmlChars=${html.length} htmlHash=${html.hashCode()}"
        }
    }

    private fun applyCanvasSizeToBrowser(width: Int, height: Int, reason: String) {
        val webShell = shell ?: return
        val webBrowser = browser
        if (webShell.isDisposed || webBrowser?.isDisposed == true) return
        val hostScale = canvas.webView2HostScale()
        if (width <= 0 || height <= 0) {
            logWebViewLayoutDiag(
                "controller_resize_skip panel=$instanceId reason=$reason requested=${width}x${height} " +
                    "hostScale=${hostScale.scaleX.formatLogFloat()}x${hostScale.scaleY.formatLogFloat()} " +
                    "canvas=${canvas.width}x${canvas.height} shellBounds=${webShell.bounds.formatSwtBounds()} " +
                    "browserBounds=${webBrowser?.bounds?.formatSwtBounds().orEmpty()}"
            )
            return
        }
        val targetBounds = desktopWebView2TargetBoundsForCanvas(width, height) ?: return
        webShell.setBounds(targetBounds.x, targetBounds.y, targetBounds.width, targetBounds.height)
        webBrowser?.setBounds(targetBounds.x, targetBounds.y, targetBounds.width, targetBounds.height)
        lastBrowserBoundsLog = webBrowser?.bounds?.formatSwtBounds().orEmpty()
        logDesktopWebView2(
            "controller_resize panel=$instanceId reason=$reason requested=${width}x${height} " +
                "target=${targetBounds.width}x${targetBounds.height} axisMode=logicalCanvas_zeroOrigin " +
                "shellBounds=${webShell.bounds.x},${webShell.bounds.y} ${webShell.bounds.width}x${webShell.bounds.height} " +
                "browserBounds=${webBrowser?.bounds?.width ?: -1}x${webBrowser?.bounds?.height ?: -1}"
        )
        logWebViewLayoutDiag(
            "controller_resize panel=$instanceId reason=$reason requested=${width}x${height} " +
                "target=${targetBounds.width}x${targetBounds.height} axisMode=logicalCanvas_zeroOrigin " +
                "hostScale=${hostScale.scaleX.formatLogFloat()}x${hostScale.scaleY.formatLogFloat()} " +
                "canvas=${canvas.width}x${canvas.height} canvasBounds=${canvas.bounds.formatAwtBounds()} " +
                "shellBounds=${webShell.bounds.formatSwtBounds()} " +
                "browserBounds=${webBrowser?.bounds?.formatSwtBounds().orEmpty()}"
        )
    }
}

private object DesktopSwtWebView2EventLoop {
    private val ready = CountDownLatch(1)

    @Volatile
    private var display: Display? = null

    @Volatile
    private var startupError: Throwable? = null

    init {
        Thread(
            {
                runCatching {
                    logDesktopWebView2("swt_event_loop_start")
                    runCatching { Display.setAppName(EpistemeDesktopWindowTitle) }
                    if (desktopEpubWebViewUsesWebView2() &&
                        System.getProperty(DesktopWebView2EdgeDataDirProperty).isNullOrBlank()
                    ) {
                        System.setProperty(
                            DesktopWebView2EdgeDataDirProperty,
                            File(desktopUserCacheRoot(), "webview2").absolutePath
                        )
                    }
                    if (desktopEpubWebViewUsesWebView2()) {
                        logDesktopWebView2(
                            "swt_event_loop_user_data_dir path=\"${System.getProperty(DesktopWebView2EdgeDataDirProperty).orEmpty().logPreview(200)}\""
                        )
                    }
                    val swtDisplay = Display()
                    display = swtDisplay
                    ready.countDown()
                    logDesktopWebView2("swt_event_loop_ready")
                    while (!swtDisplay.isDisposed) {
                        if (!swtDisplay.readAndDispatch()) {
                            swtDisplay.sleep()
                        }
                    }
                }.onFailure { error ->
                    startupError = error
                    ready.countDown()
                    logDesktopWebView2("swt_event_loop_failed error=\"${error.message.orEmpty().logPreview(300)}\"")
                }
            },
            "Episteme SWT Browser"
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun asyncExec(
        onError: (Throwable) -> Unit,
        block: (Display) -> Unit
    ) {
        val displayReady = runCatching {
            ready.await(DesktopSwtReadyTimeoutSeconds, TimeUnit.SECONDS)
        }.getOrElse { error ->
            Thread.currentThread().interrupt()
            onError(error)
            return
        }
        if (!displayReady) {
            logDesktopWebView2("swt_async_timeout")
            onError(IllegalStateException("SWT display did not become ready."))
            return
        }
        startupError?.let { error ->
            logDesktopWebView2("swt_async_startup_error error=\"${error.message.orEmpty().logPreview(300)}\"")
            onError(error)
            return
        }
        val swtDisplay = display
        if (swtDisplay == null) {
            logDesktopWebView2("swt_async_display_unavailable")
            onError(IllegalStateException("SWT display is not available."))
            return
        }
        runCatching {
            swtDisplay.asyncExec {
                runCatching {
                    if (!swtDisplay.isDisposed) {
                        block(swtDisplay)
                    }
                }.onFailure(onError)
            }
        }.onFailure { error ->
            logDesktopWebView2("swt_async_enqueue_failed error=\"${error.message.orEmpty().logPreview(300)}\"")
            onError(error)
        }
    }

    fun syncExec(
        onError: (Throwable) -> Unit,
        block: (Display) -> Unit
    ) {
        val displayReady = runCatching {
            ready.await(DesktopSwtReadyTimeoutSeconds, TimeUnit.SECONDS)
        }.getOrElse { error ->
            Thread.currentThread().interrupt()
            onError(error)
            return
        }
        if (!displayReady) {
            logDesktopWebView2("swt_sync_timeout")
            onError(IllegalStateException("SWT display did not become ready."))
            return
        }
        startupError?.let { error ->
            logDesktopWebView2("swt_sync_startup_error error=\"${error.message.orEmpty().logPreview(300)}\"")
            onError(error)
            return
        }
        val swtDisplay = display
        if (swtDisplay == null) {
            logDesktopWebView2("swt_sync_display_unavailable")
            onError(IllegalStateException("SWT display is not available."))
            return
        }
        runCatching {
            swtDisplay.syncExec {
                runCatching {
                    if (!swtDisplay.isDisposed) {
                        block(swtDisplay)
                    }
                }.onFailure(onError)
            }
        }.onFailure { error ->
            logDesktopWebView2("swt_sync_enqueue_failed error=\"${error.message.orEmpty().logPreview(300)}\"")
            onError(error)
        }
    }
}

private fun Color.toAwtColor(): java.awt.Color = java.awt.Color(toArgb(), true)

private fun desktopWebView2WarmupHtml(background: java.awt.Color): String {
    val cssColor = background.toCssHex()
    return """
        <!doctype html>
        <html>
          <head>
            <meta charset="utf-8">
            <style>
              html, body {
                margin: 0;
                width: 100%;
                min-height: 100%;
                background: $cssColor;
              }
            </style>
          </head>
          <body></body>
        </html>
    """.trimIndent()
}

private fun java.awt.Color.toCssHex(): String {
    return "#${red.toTwoDigitHex()}${green.toTwoDigitHex()}${blue.toTwoDigitHex()}"
}

private fun Int.toTwoDigitHex(): String {
    return coerceIn(0, 255).toString(16).padStart(2, '0')
}

private fun runOnAwtEventThreadBlocking(
    onError: (Throwable) -> Unit = {},
    block: () -> Unit
) {
    if (EventQueue.isDispatchThread()) {
        runCatching(block).onFailure(onError)
        return
    }
    runCatching {
        EventQueue.invokeAndWait {
            runCatching(block).onFailure(onError)
        }
    }.onFailure(onError)
}

private fun java.awt.Rectangle.formatAwtBounds(): String {
    return "${x},${y} ${width}x$height"
}

private fun java.awt.Component?.formatAwtComponentState(): String {
    if (this == null) return "none"
    return "${javaClass.simpleName}{visible=$isVisible displayable=$isDisplayable showing=$isShowing " +
        "size=${width}x$height bounds=${bounds.formatAwtBounds()}}"
}

private fun java.awt.Container.formatAwtChildrenState(): String {
    if (componentCount == 0) return "none"
    return components.joinToString(prefix = "[", postfix = "]") { component ->
        component.formatAwtComponentState()
    }
}

private fun java.awt.Component.desktopWebView2Descendants(includeSelf: Boolean = false): List<java.awt.Component> {
    val descendants = mutableListOf<java.awt.Component>()
    if (includeSelf) descendants += this
    fun collect(component: java.awt.Component) {
        if (component is java.awt.Container) {
            component.components.forEach { child ->
                descendants += child
                collect(child)
            }
        }
    }
    collect(this)
    return descendants
}

private fun org.eclipse.swt.graphics.Rectangle.formatSwtBounds(): String {
    return "${x},${y} ${width}x$height"
}

private fun Canvas.safeScreenLocationLog(): String {
    return runCatching {
        val point = locationOnScreen
        "${point.x},${point.y}"
    }.getOrDefault("unavailable")
}

private data class DesktopWebView2HostScale(
    val scaleX: Float,
    val scaleY: Float
)

internal data class DesktopWebView2TargetBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

internal fun desktopWebView2TargetBoundsForCanvas(width: Int, height: Int): DesktopWebView2TargetBounds? {
    if (width <= 0 || height <= 0) return null
    return DesktopWebView2TargetBounds(
        x = 0,
        y = 0,
        width = width.coerceAtLeast(1),
        height = height.coerceAtLeast(1)
    )
}

internal fun desktopWebView2ShouldRetireAwtCanvas(
    hostWindowClosing: Boolean,
    hostWindowDisplayable: Boolean
): Boolean {
    return !hostWindowClosing && hostWindowDisplayable
}

private fun java.awt.Component.webView2HostScale(): DesktopWebView2HostScale {
    val transform = graphicsConfiguration?.defaultTransform
    return DesktopWebView2HostScale(
        scaleX = transform?.scaleX?.takeIf { it.isFinite() && it > 0.0 }?.toFloat() ?: 1f,
        scaleY = transform?.scaleY?.takeIf { it.isFinite() && it > 0.0 }?.toFloat() ?: 1f
    )
}

private fun String.withDesktopWebView2Bootstrap(networkAccessEnabled: Boolean): String {
    val injection = buildString {
        if (!networkAccessEnabled) {
            append(DesktopWebView2OfflineCspMetaTag)
            append('\n')
        }
        append(DesktopWebView2ReaderSurfaceCssTag)
        append('\n')
        append(DesktopWebView2BridgeScriptTag)
    }
    val headStart = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE).find(this)
    if (headStart != null) {
        val insertAt = headStart.range.last + 1
        return substring(0, insertAt) + "\n" + injection + "\n" + substring(insertAt)
    }
    return "$injection\n$this"
}

internal fun desktopNativeWebViewUnavailableMessage(
    backend: DesktopEpubWebViewBackend,
    detail: String? = null
): String {
    val base = when (backend) {
        DesktopEpubWebViewBackend.WINDOWS_WEBVIEW2 ->
            "Microsoft Edge WebView2 runtime is unavailable. Install or repair the WebView2 Runtime."
        DesktopEpubWebViewBackend.WEBKIT ->
            "WebKitGTK is unavailable. Install WebKitGTK from your Linux distribution packages."
        DesktopEpubWebViewBackend.UNSUPPORTED ->
            "Native webview is unavailable on this desktop platform."
    }
    val trimmedDetail = detail?.trim().orEmpty()
    return if (trimmedDetail.isBlank()) base else "$base $trimmedDetail"
}

private fun Throwable.desktopNativeWebViewMessage(backend: DesktopEpubWebViewBackend): String {
    return desktopNativeWebViewUnavailableMessage(
        backend = backend,
        detail = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
    )
}

private fun desktopWebView2DocumentProbeScript(eventName: String): String {
    return """
        (function () {
          try {
            var body = document.body;
            var root = document.documentElement;
            var firstChapter = document.querySelector('.chapter');
            var firstContent = document.querySelector('.reader-content');
            var blockSelector = 'p, div, h1, h2, h3, h4, h5, h6, li, blockquote, figure, table, pre';
            function round(value) {
              return Math.round(Number(value || 0));
            }
            function cssValue(element, name) {
              if (!element) return '';
              var style = window.getComputedStyle(element);
              return style ? (style.getPropertyValue(name) || '') : '';
            }
            function cssVar(name) {
              return cssValue(root, name).trim();
            }
            function rectPayload(element) {
              if (!element) return null;
              var rect = element.getBoundingClientRect();
              var centerX = rect.left + (rect.width / 2);
              var centerY = rect.top + (rect.height / 2);
              var viewportHeight = window.innerHeight || 0;
              return {
                left: round(rect.left),
                top: round(rect.top),
                right: round(rect.right),
                bottom: round(rect.bottom),
                width: round(rect.width),
                height: round(rect.height),
                centerX: round(centerX),
                centerDelta: round(centerX - ((window.innerWidth || 0) / 2)),
                centerY: round(centerY),
                viewportHeightDelta: round(rect.height - viewportHeight),
                marginLeft: cssValue(element, 'margin-left').trim(),
                marginRight: cssValue(element, 'margin-right').trim(),
                paddingLeft: cssValue(element, 'padding-left').trim(),
                paddingRight: cssValue(element, 'padding-right').trim(),
                paddingTop: cssValue(element, 'padding-top').trim(),
                paddingBottom: cssValue(element, 'padding-bottom').trim(),
                textAlign: cssValue(element, 'text-align').trim(),
                display: cssValue(element, 'display').trim(),
                cssFloat: cssValue(element, 'float').trim(),
                clear: cssValue(element, 'clear').trim(),
                cssWidth: cssValue(element, 'width').trim(),
                maxWidth: cssValue(element, 'max-width').trim(),
                minHeight: cssValue(element, 'min-height').trim(),
                boxSizing: cssValue(element, 'box-sizing').trim()
              };
            }
            function visibleChapter() {
              var chapters = Array.prototype.slice.call(document.querySelectorAll('[data-reader-chapter-index]'));
              var viewportTop = 0;
              var viewportBottom = window.innerHeight || 0;
              var best = null;
              var bestVisibleHeight = -1;
              chapters.forEach(function (candidate) {
                var rect = candidate.getBoundingClientRect();
                var visibleHeight = Math.min(rect.bottom, viewportBottom) - Math.max(rect.top, viewportTop);
                if (visibleHeight > bestVisibleHeight && rect.bottom >= viewportTop && rect.top <= viewportBottom) {
                  best = candidate;
                  bestVisibleHeight = visibleHeight;
                }
              });
              return best || firstChapter;
            }
            function visibleBlockIn(content) {
              if (!content) return null;
              var blocks = Array.prototype.slice.call(content.querySelectorAll(blockSelector));
              for (var i = 0; i < blocks.length; i++) {
                var rect = blocks[i].getBoundingClientRect();
                if (rect.width > 0 && rect.height > 0 && rect.bottom >= 0 && rect.top <= (window.innerHeight || 0)) {
                  return blocks[i];
                }
              }
              return blocks[0] || null;
            }
            var chapter = visibleChapter();
            var content = chapter ? (chapter.querySelector('.reader-content') || chapter) : firstContent;
            var firstBlock = firstContent ? firstContent.querySelector(blockSelector) : null;
            var visibleBlock = visibleBlockIn(content);
            var viewportCenterX = Math.max(0, Math.min((window.innerWidth || 0) - 1, Math.round((window.innerWidth || 0) / 2)));
            var viewportTopY = Math.max(0, Math.min((window.innerHeight || 0) - 1, 8));
            var topElement = document.elementFromPoint(viewportCenterX, viewportTopY);
            var topBlock = topElement && topElement.closest ? topElement.closest(blockSelector) : null;
            var sampledElement = document.elementFromPoint(
              viewportCenterX,
              Math.max(0, Math.min((window.innerHeight || 0) - 1, Math.round((window.innerHeight || 0) / 2)))
            );
            var sampledBlock = sampledElement && sampledElement.closest ? sampledElement.closest(blockSelector) : null;
            var payload = {
              event: '$eventName',
              readyState: document.readyState || '',
              title: document.title || '',
              url: location.href || '',
              devicePixelRatio: window.devicePixelRatio || 1,
              bodyClass: body ? body.className : '',
              rootClass: root ? root.className : '',
              readerAlign: cssVar('--reader-align'),
              readerMarginX: cssVar('--reader-margin-x'),
              readerMarginY: cssVar('--reader-margin-y'),
              readerVerticalMarginY: cssVar('--reader-vertical-margin-y'),
              readerVerticalContentWidth: cssVar('--reader-vertical-content-width'),
              readerVerticalPageWidth: cssVar('--reader-vertical-page-width'),
              readerFontSize: cssVar('--reader-font-size'),
              bodyZoom: cssValue(body, 'zoom').trim(),
              bodyChildren: body ? body.children.length : -1,
              bodyTextChars: body && body.innerText ? body.innerText.length : 0,
              bodyHtmlChars: body && body.innerHTML ? body.innerHTML.length : 0,
              bodyClientWidth: body ? body.clientWidth : -1,
              bodyScrollWidth: body ? body.scrollWidth : -1,
              rootClientWidth: root ? root.clientWidth : -1,
              rootScrollWidth: root ? root.scrollWidth : -1,
              scrollHeight: root ? root.scrollHeight : -1,
              clientHeight: root ? root.clientHeight : -1,
              viewportWidth: window.innerWidth || -1,
              viewportHeight: window.innerHeight || -1,
              visualViewportWidth: window.visualViewport ? round(window.visualViewport.width) : -1,
              visualViewportHeight: window.visualViewport ? round(window.visualViewport.height) : -1,
              visualViewportScale: window.visualViewport ? window.visualViewport.scale : -1,
              scrollX: window.scrollX || 0,
              topElementTag: topElement ? topElement.tagName : '',
              topElementClass: topElement && topElement.className ? String(topElement.className) : '',
              topBlockTag: topBlock ? topBlock.tagName : '',
              topBlockRect: rectPayload(topBlock),
              bodyRect: rectPayload(body),
              rootRect: rectPayload(root),
              firstChapterRect: rectPayload(firstChapter),
              firstContentRect: rectPayload(firstContent),
              visibleChapterIndex: chapter ? chapter.getAttribute('data-reader-chapter-index') : '',
              chapterRect: rectPayload(chapter),
              contentRect: rectPayload(content),
              firstBlockTag: firstBlock ? firstBlock.tagName : '',
              firstBlockRect: rectPayload(firstBlock),
              visibleBlockTag: visibleBlock ? visibleBlock.tagName : '',
              visibleBlockRect: rectPayload(visibleBlock),
              sampledBlockTag: sampledBlock ? sampledBlock.tagName : '',
              sampledBlockRect: rectPayload(sampledBlock)
            };
            if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
              window.kmpJsBridge.callNative('$DesktopWebView2DiagnosticMethodName', JSON.stringify(payload));
            }
          } catch (error) {
            if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
              window.kmpJsBridge.callNative('$DesktopWebView2DiagnosticMethodName', JSON.stringify({
                event: '$eventName',
                error: String(error && error.message ? error.message : error)
              }));
            }
          }
        })();
    """.trimIndent()
}

private var DesktopWebView2InstanceSeed = 0
private val DesktopWebView2ModeSwitchPanelStates = ConcurrentHashMap<Int, String>()

@Synchronized
private fun nextDesktopWebView2InstanceId(): Int {
    DesktopWebView2InstanceSeed += 1
    return DesktopWebView2InstanceSeed
}

internal fun logDesktopWebView2ModeSwitchSnapshot(reason: String) {
    val states = DesktopWebView2ModeSwitchPanelStates
        .toSortedMap()
        .values
        .joinToString(separator = " | ")
        .ifBlank { "none" }
    logReaderModeSwitch(
        "webview2_snapshot reason=$reason knownPanelCount=${DesktopWebView2ModeSwitchPanelStates.size} panels=$states"
    )
}

internal fun cleanupRetiredDesktopWebView2InteropHosts(window: java.awt.Window?, reason: String) {
    EventQueue.invokeLater {
        if (window == null) {
            logReaderModeSwitch("webview2_interop_host_sweep_skip reason=$reason window=null")
            return@invokeLater
        }
        val interopHosts = window
            .desktopWebView2Descendants()
            .filterIsInstance<java.awt.Container>()
            .filter { component -> component.javaClass.simpleName == DesktopSwingInteropHostClassName }
        if (interopHosts.isEmpty()) {
            logReaderModeSwitch(
                "webview2_interop_host_sweep reason=$reason window=${window.formatAwtComponentState()} hosts=none"
            )
            return@invokeLater
        }
        interopHosts.forEach { host ->
            cleanupRetiredDesktopWebView2InteropHost(window, host, reason)
        }
    }
}

private fun cleanupRetiredDesktopWebView2InteropHost(
    window: java.awt.Window,
    host: java.awt.Container,
    reason: String
) {
    val panels = host
        .desktopWebView2Descendants(includeSelf = true)
        .filterIsInstance<DesktopWindowsWebView2Panel>()
    val hostRetired = !host.isShowing || !host.isVisible || host.width <= 0 || host.height <= 0
    val panelsRetired = panels.isNotEmpty() && panels.all { panel ->
        !panel.isDisplayable || !panel.isShowing || panel.width <= 0 || panel.height <= 0 || !panel.hasController
    }
    val parent = host.parent
    val removable = parent != null && hostRetired && panelsRetired
    val panelStates = panels.joinToString(prefix = "[", postfix = "]") { panel ->
        "panel=${panel.instanceId}{visible=${panel.isVisible} displayable=${panel.isDisplayable} " +
            "showing=${panel.isShowing} size=${panel.width}x${panel.height} controller=${panel.hasController}}"
    }.ifBlank { "none" }
    logReaderModeSwitch(
        "webview2_interop_host_sweep_candidate reason=$reason removable=$removable " +
            "hostRetired=$hostRetired panelsRetired=$panelsRetired " +
            "window=${window.formatAwtComponentState()} host=${host.formatAwtComponentState()} " +
            "parent=${parent.formatAwtComponentState()} panels=$panelStates children=${host.formatAwtChildrenState()}"
    )
    if (!removable) return
    panels.forEach { panel ->
        if (panel.parent === host) {
            host.remove(panel)
        }
    }
    host.removeAll()
    parent?.remove(host)
    host.invalidate()
    parent?.invalidate()
    parent?.validate()
    parent?.repaint()
    window.invalidate()
    window.validate()
    window.repaint()
    panels.forEach { panel ->
        DesktopWebView2ModeSwitchPanelStates[panel.instanceId] =
            panel.modeSwitchPanelSnapshot("interop_host_sweep_removed_$reason")
        logReaderModeSwitch("webview2_panel ${DesktopWebView2ModeSwitchPanelStates[panel.instanceId]}")
    }
    logReaderModeSwitch(
        "webview2_interop_host_sweep_removed reason=$reason " +
            "window=${window.formatAwtComponentState()} host=${host.formatAwtComponentState()} " +
            "parent=${parent.formatAwtComponentState()} parentChildren=${parent?.componentCount ?: -1}"
    )
}

private const val DesktopSwtReadyTimeoutSeconds = 10L
private val DesktopWebView2FullscreenRelayoutDelaysMillis = longArrayOf(180L, 260L, 420L)
private val DesktopWebView2InteropHostCleanupDelaysMillis = longArrayOf(80L, 220L)
private const val DesktopWebView2NativeBridgeName = "epistemeCallNative"
private const val DesktopWebView2DiagnosticMethodName = "readerWebView2Diagnostic"
private const val DesktopSwingInteropHostClassName = "SwingInteropViewGroup"
private const val DesktopWebView2EdgeDataDirProperty = "org.eclipse.swt.browser.EdgeDataDir"

private fun DesktopEpubWebViewBackend.swtBrowserStyle(): Int {
    return when (this) {
        DesktopEpubWebViewBackend.WINDOWS_WEBVIEW2 -> SWT.EDGE
        DesktopEpubWebViewBackend.WEBKIT -> SWT.WEBKIT
        DesktopEpubWebViewBackend.UNSUPPORTED -> SWT.NONE
    }
}

private fun DesktopEpubWebViewBackend.acceptsBrowserType(browserType: String): Boolean {
    return when (this) {
        DesktopEpubWebViewBackend.WINDOWS_WEBVIEW2 -> browserType.equals("edge", ignoreCase = true)
        DesktopEpubWebViewBackend.WEBKIT ->
            browserType.contains("webkit", ignoreCase = true) || browserType.equals("safari", ignoreCase = true)
        DesktopEpubWebViewBackend.UNSUPPORTED -> false
    }
}

private val DesktopWebView2BridgeRuntimeScript = """
    (function () {
      window.kmpJsBridge = window.kmpJsBridge || {};
      window.kmpJsBridge.callNative = function (method, params) {
        if (!window.$DesktopWebView2NativeBridgeName) return null;
        var payload = '{}';
        if (typeof params === 'string') {
          payload = params;
        } else {
          try { payload = JSON.stringify(params || {}); } catch (error) { payload = '{}'; }
        }
        return window.$DesktopWebView2NativeBridgeName(String(method || ''), payload);
      };
    })();
""".trimIndent()

private val DesktopWebView2ReaderSurfaceCssTag = """
    <style id="episteme-webview2-reader-surface">
      html,
      body.reader-vertical {
        width: 100% !important;
        overflow-x: hidden !important;
        max-width: 100vw !important;
        min-width: 0 !important;
      }
      body.reader-vertical {
        min-height: 100vh !important;
        min-height: 100dvh !important;
        overflow-y: auto !important;
        padding: var(--reader-vertical-margin-y) 0 !important;
        scrollbar-gutter: stable !important;
      }
      html.reader-vertical-root,
      body.reader-vertical {
        scrollbar-width: thin !important;
      }
      html.reader-vertical-root::-webkit-scrollbar,
      body.reader-vertical::-webkit-scrollbar {
        width: 12px !important;
        height: 12px !important;
        display: block !important;
      }
      body.reader-vertical > .chapter,
      body.reader-vertical > :not(.chapter):not(#reader-selection-menu):not(.reader-selection-handle):not(script):not(style),
      body.reader-vertical > .chapter > :not(.reader-content),
      body.reader-vertical > .chapter > .chapter-title,
      body.reader-vertical > .chapter > .reader-content {
        box-sizing: border-box !important;
        min-width: 0 !important;
      }
      body.reader-vertical > .chapter {
        width: 100% !important;
        max-width: none !important;
        margin: 0 !important;
      }
      body.reader-vertical > :not(.chapter):not(#reader-selection-menu):not(.reader-selection-handle):not(script):not(style),
      body.reader-vertical > .chapter > :not(.reader-content),
      body.reader-vertical > .chapter > .chapter-title,
      body.reader-vertical > .chapter > .reader-content {
        width: var(--reader-vertical-page-width) !important;
        max-width: none !important;
        margin-left: auto !important;
        margin-right: auto !important;
      }
      body.reader-vertical > :not(.chapter):not(#reader-selection-menu):not(.reader-selection-handle):not(script):not(style),
      body.reader-vertical > .chapter > :not(.reader-content) {
        position: static !important;
        left: auto !important;
        right: auto !important;
        top: auto !important;
        bottom: auto !important;
        transform: none !important;
        float: none !important;
        clear: none !important;
      }
      body.reader-vertical .reader-content :where(h1, h2, h3, h4, h5, h6, hgroup, center, [class*="title" i], [id*="title" i], [class*="heading" i], [id*="heading" i], [class*="dedication" i], [id*="dedication" i]) {
        box-sizing: border-box !important;
        width: auto !important;
        max-width: 100% !important;
        min-width: 0 !important;
        margin-left: 0 !important;
        margin-right: 0 !important;
        padding-left: 0 !important;
        padding-right: 0 !important;
        text-indent: 0 !important;
        position: static !important;
        left: auto !important;
        right: auto !important;
        transform: none !important;
        float: none !important;
        clear: none !important;
      }
      body.reader-vertical .reader-content,
      body.reader-vertical .reader-content p,
      body.reader-vertical .reader-content li,
      body.reader-vertical .reader-content div,
      body.reader-vertical .reader-content h1,
      body.reader-vertical .reader-content h2,
      body.reader-vertical .reader-content h3,
      body.reader-vertical .reader-content h4,
      body.reader-vertical .reader-content h5,
      body.reader-vertical .reader-content h6,
      body.reader-vertical .reader-content blockquote {
        text-align: var(--reader-align) !important;
      }
      body.reader-vertical .reader-content p,
      body.reader-vertical .reader-content div,
      body.reader-vertical .reader-content h1,
      body.reader-vertical .reader-content h2,
      body.reader-vertical .reader-content h3,
      body.reader-vertical .reader-content h4,
      body.reader-vertical .reader-content h5,
      body.reader-vertical .reader-content h6,
      body.reader-vertical .reader-content blockquote,
      body.reader-vertical .reader-content section,
      body.reader-vertical .reader-content article,
      body.reader-vertical .reader-content header,
      body.reader-vertical .reader-content footer,
      body.reader-vertical .reader-content aside,
      body.reader-vertical .reader-content figure,
      body.reader-vertical .reader-content table,
      body.reader-vertical .reader-content pre {
        box-sizing: border-box !important;
        max-width: 100% !important;
        min-width: 0 !important;
        position: static !important;
        left: auto !important;
        right: auto !important;
        top: auto !important;
        bottom: auto !important;
        transform: none !important;
        float: none !important;
        clear: none !important;
      }
      body.reader-vertical .reader-content div,
      body.reader-vertical .reader-content section,
      body.reader-vertical .reader-content article,
      body.reader-vertical .reader-content header,
      body.reader-vertical .reader-content footer,
      body.reader-vertical .reader-content aside,
      body.reader-vertical .reader-content figure {
        width: auto !important;
        margin-left: 0 !important;
        margin-right: 0 !important;
      }
      body.reader-vertical .reader-content > p,
      body.reader-vertical .reader-content > div,
      body.reader-vertical .reader-content > h1,
      body.reader-vertical .reader-content > h2,
      body.reader-vertical .reader-content > h3,
      body.reader-vertical .reader-content > h4,
      body.reader-vertical .reader-content > h5,
      body.reader-vertical .reader-content > h6,
      body.reader-vertical .reader-content > blockquote,
      body.reader-vertical .reader-content > section,
      body.reader-vertical .reader-content > article,
      body.reader-vertical .reader-content > header,
      body.reader-vertical .reader-content > footer,
      body.reader-vertical .reader-content > aside,
      body.reader-vertical .reader-content > figure,
      body.reader-vertical .reader-content > table,
      body.reader-vertical .reader-content > pre {
        margin-left: 0 !important;
        margin-right: 0 !important;
      }
      body.reader-vertical img,
      body.reader-vertical svg,
      body.reader-vertical video,
      body.reader-vertical table,
      body.reader-vertical pre {
        max-width: 100% !important;
      }
    </style>
""".trimIndent()

private val DesktopWebView2HorizontalClampScript = """
    (function () {
      if (window.readerWebView2HorizontalClampInstalled) return;
      window.readerWebView2HorizontalClampInstalled = true;
      var clampQueued = false;
      function clampHorizontalScroll() {
        clampQueued = false;
        var root = document.documentElement;
        var body = document.body;
        var changed = false;
        if (window.scrollX) {
          window.scrollTo({ top: window.scrollY || 0, left: 0, behavior: 'auto' });
          changed = true;
        }
        if (root && root.scrollLeft) {
          root.scrollLeft = 0;
          changed = true;
        }
        if (body && body.scrollLeft) {
          body.scrollLeft = 0;
          changed = true;
        }
        if (changed && window.kmpJsBridge && window.kmpJsBridge.callNative) {
          try {
            window.kmpJsBridge.callNative('$DesktopWebView2DiagnosticMethodName', JSON.stringify({
              event: 'horizontal_scroll_clamped'
            }));
          } catch (error) {}
        }
      }
      function scheduleClamp() {
        if (clampQueued) return;
        clampQueued = true;
        window.requestAnimationFrame(clampHorizontalScroll);
      }
      window.addEventListener('scroll', scheduleClamp, { passive: true });
      window.addEventListener('resize', scheduleClamp, { passive: true });
      document.addEventListener('DOMContentLoaded', scheduleClamp, { once: true });
      window.addEventListener('load', scheduleClamp, { once: true });
      scheduleClamp();
    })();
""".trimIndent()

private val DesktopWebView2BridgeScriptTag = """
    <script>
    ${DesktopWebView2BridgeRuntimeScript}
    ${DesktopWebView2HorizontalClampScript}
    </script>
""".trimIndent()

private const val DesktopWebView2OfflineCspMetaTag =
    "<meta http-equiv=\"Content-Security-Policy\" " +
        "content=\"default-src 'self' data: blob: file: 'unsafe-inline'; " +
        "script-src 'self' data: blob: file: 'unsafe-inline'; " +
        "style-src 'self' data: blob: file: 'unsafe-inline'; " +
        "img-src 'self' data: blob: file:; " +
        "font-src 'self' data: blob: file:; " +
        "media-src 'self' data: blob: file:; " +
        "connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'\">"
