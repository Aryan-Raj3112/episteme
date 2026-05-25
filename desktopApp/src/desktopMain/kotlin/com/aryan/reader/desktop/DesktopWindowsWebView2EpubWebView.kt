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
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Composable
internal fun DesktopWindowsWebView2EpubWebView(
    html: String,
    appearanceScript: String,
    highlightPaletteScript: String,
    navigationTarget: ReaderContentNavigationTarget,
    highlights: List<UserHighlight>,
    onHighlightCreated: (UserHighlight) -> Unit,
    onHighlightSelected: (String) -> Unit,
    isFullscreen: Boolean,
    onKeyboardNavigation: (DesktopReaderKeyNavigation) -> Unit,
    onSelectionAction: (DesktopReaderSelectionAction, String) -> Unit,
    onLinkClicked: (DesktopEpubLinkClick) -> Unit,
    onVisiblePageChanged: (Int, ReaderLocator?) -> Unit,
    onPointerActivity: () -> Unit = {},
    networkAccessEnabled: Boolean,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
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
    val panel = remember { DesktopWindowsWebView2Panel(hostBackground) }
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
            panel.disposeWebView()
        }
    }

    LaunchedEffect(hostBackground) {
        panel.updateBackground(hostBackground)
    }

    Box(
        modifier = modifier.onSizeChanged { size ->
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
                .fillMaxSize()
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
            panel.executeJavaScript("window.readerDesktopFullscreen = ${if (isFullscreen) "true" else "false"};")
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
            DesktopWindowsWebView2Error(
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
private fun DesktopWindowsWebView2Error(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = readerString(
                "desktop_webview2_start_error",
                "Microsoft Edge WebView2 could not start: %1\$s",
                message
            ),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
}

private class DesktopWindowsWebView2Panel(initialBackground: java.awt.Color) : Canvas() {
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

    init {
        background = initialBackground
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) {
                    logDesktopWebView2("panel_resized panel=$instanceId size=${width}x${height}")
                    logWebViewLayoutDiag(
                        "awt_canvas_resized panel=$instanceId size=${width}x${height} " +
                            "bounds=${bounds.formatAwtBounds()} screen=${safeScreenLocationLog()}"
                    )
                    controller?.resize(width, height)
                }
            }
        )
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
        this.bridgeHandlersByMethod = bridgeHandlersByMethod
        this.networkAccessEnabled = networkAccessEnabled
        this.onLinkIntercepted = onLinkIntercepted
        this.onLoadStateChanged = onLoadStateChanged
        this.onError = onError
        logDesktopWebView2(
            "panel_configure panel=$instanceId handlers=${bridgeHandlersByMethod.size} network=$networkAccessEnabled " +
                "controller=${controller != null}"
        )
    }

    fun loadHtml(html: String) {
        if (requestedHtml == html) {
            logDesktopWebView2("panel_load_skip_duplicate panel=$instanceId htmlHash=${html.hashCode()}")
            return
        }
        requestedHtml = html
        logDesktopWebView2(
            "panel_load_requested panel=$instanceId htmlChars=${html.length} htmlHash=${html.hashCode()} " +
                "controller=${controller != null}"
        )
        logWebViewLayoutDiag(
            "panel_load_requested panel=$instanceId canvas=${width}x${height} " +
                "bounds=${bounds.formatAwtBounds()} controller=${controller != null}"
        )
        controller?.loadHtml(html)
    }

    fun executeJavaScript(script: String) {
        logDesktopWebView2(
            "panel_execute panel=$instanceId scriptChars=${script.length} scriptHash=${script.hashCode()} controller=${controller != null}"
        )
        controller?.executeJavaScript(script)
    }

    fun disposeWebView() {
        logDesktopWebView2("panel_dispose panel=$instanceId controller=${controller != null}")
        controller?.dispose()
        controller = null
    }

    override fun addNotify() {
        super.addNotify()
        logDesktopWebView2(
            "panel_add_notify panel=$instanceId displayable=$isDisplayable showing=$isShowing " +
                "canvas=${width}x${height} controller=${controller != null} hasHtml=${requestedHtml != null}"
        )
        logWebViewLayoutDiag(
            "awt_canvas_add_notify panel=$instanceId displayable=$isDisplayable showing=$isShowing " +
                "size=${width}x${height} bounds=${bounds.formatAwtBounds()} screen=${safeScreenLocationLog()} " +
                "hasHtml=${requestedHtml != null}"
        )
        if (controller == null) {
            controller = DesktopWindowsWebView2Controller(
                instanceId = instanceId,
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
                    EventQueue.invokeLater {
                        onError(error.desktopWebView2Message())
                    }
                }
            )
            requestedHtml?.let { html -> controller?.loadHtml(html) }
            controller?.resize(width, height)
        }
    }

    override fun removeNotify() {
        logDesktopWebView2("panel_remove_notify panel=$instanceId")
        disposeWebView()
        super.removeNotify()
    }
}

private class DesktopWindowsWebView2Controller(
    private val instanceId: Int,
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

    init {
        logDesktopWebView2("controller_init panel=$instanceId canvas=${canvas.width}x${canvas.height}")
        logWebViewLayoutDiag(
            "controller_init panel=$instanceId canvas=${canvas.width}x${canvas.height} " +
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
        logWebViewLayoutDiag(
            "controller_load_enqueue panel=$instanceId htmlChars=${html.length} browser=${browser != null} " +
                "canvas=${canvas.width}x${canvas.height} browserBounds=${browser?.bounds?.formatSwtBounds().orEmpty()}"
        )
        DesktopSwtWebView2EventLoop.asyncExec(reportError) {
            if (disposed) return@asyncExec
            updateLoadState(false, -1f)
            val webView = browser
            if (webView == null || webView.isDisposed) {
                logDesktopWebView2("controller_load_drop panel=$instanceId reason=browser_not_ready")
            } else {
                val accepted = webView.setText(html)
                logDesktopWebView2(
                    "controller_set_text panel=$instanceId accepted=$accepted htmlChars=${html.length} htmlHash=${html.hashCode()}"
                )
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

    fun resize(width: Int, height: Int) {
        DesktopSwtWebView2EventLoop.asyncExec(reportError) {
            if (disposed) return@asyncExec
            val safeWidth = width.coerceAtLeast(1)
            val safeHeight = height.coerceAtLeast(1)
            val hostScale = canvas.webView2HostScale()
            shell?.setSize(safeWidth, safeHeight)
            browser?.setBounds(0, 0, safeWidth, safeHeight)
            logDesktopWebView2(
                "controller_resize panel=$instanceId requested=${width}x${height} applied=${safeWidth}x$safeHeight " +
                    "shellBounds=${shell?.bounds?.width ?: -1}x${shell?.bounds?.height ?: -1} " +
                    "browserBounds=${browser?.bounds?.width ?: -1}x${browser?.bounds?.height ?: -1}"
            )
            logWebViewLayoutDiag(
                "controller_resize panel=$instanceId requested=${width}x${height} applied=${safeWidth}x$safeHeight " +
                    "hostScale=${hostScale.scaleX.formatLogFloat()}x${hostScale.scaleY.formatLogFloat()} " +
                    "canvas=${canvas.width}x${canvas.height} canvasBounds=${canvas.bounds.formatAwtBounds()} " +
                    "shellBounds=${shell?.bounds?.formatSwtBounds().orEmpty()} " +
                    "browserBounds=${browser?.bounds?.formatSwtBounds().orEmpty()}"
            )
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        logDesktopWebView2("controller_dispose panel=$instanceId")
        DesktopSwtWebView2EventLoop.asyncExec({}) {
            bridgeFunction?.takeUnless { it.isDisposed }?.dispose()
            bridgeFunction = null
            browser?.takeUnless { it.isDisposed }?.dispose()
            browser = null
            shell?.takeUnless { it.isDisposed }?.dispose()
            shell = null
        }
    }

    private fun createBrowser(display: Display) {
        logDesktopWebView2("controller_create_start panel=$instanceId displayDisposed=${display.isDisposed}")
        runCatching {
            shell = SWT_AWT.new_Shell(display, canvas)
            logDesktopWebView2("controller_shell_created panel=$instanceId shellDisposed=${shell?.isDisposed == true}")
            logWebViewLayoutDiag(
                "swt_shell_created panel=$instanceId canvas=${canvas.width}x${canvas.height} " +
                    "canvasBounds=${canvas.bounds.formatAwtBounds()} shellBounds=${shell?.bounds?.formatSwtBounds().orEmpty()}"
            )
            val webView = Browser(shell, SWT.EDGE)
            browser = webView
            val browserType = webView.browserType.orEmpty()
            logDesktopWebView2("controller_browser_created panel=$instanceId browserType=\"$browserType\"")
            logWebViewLayoutDiag(
                "swt_browser_created panel=$instanceId browserType=\"$browserType\" " +
                    "browserBounds=${webView.bounds.formatSwtBounds()} shellBounds=${shell?.bounds?.formatSwtBounds().orEmpty()}"
            )
            check(browserType.equals(DesktopWebView2SwtBrowserType, ignoreCase = true)) {
                "Microsoft Edge WebView2 runtime is not available; SWT opened '$browserType' instead."
            }
            run {
                bridgeFunction = object : BrowserFunction(webView, DesktopWebView2NativeBridgeName) {
                    override fun function(arguments: Array<out Any?>): Any? {
                        val method = arguments.getOrNull(0)?.toString().orEmpty()
                        if (method.isBlank()) return null
                        val params = arguments.getOrNull(1)?.toString() ?: "{}"
                        if (method == DesktopWebView2DiagnosticMethodName) {
                            val preview = params.logPreview(2400)
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
                            val probeInjected = webView.execute(desktopWebView2DocumentProbeScript("load_completed"))
                            logDesktopWebView2(
                                "progress_completed panel=$instanceId bridgeInjected=$bridgeInjected probeInjected=$probeInjected " +
                                    "current=${event.current} total=${event.total}"
                            )
                            logWebViewLayoutDiag(
                                "progress_completed panel=$instanceId bridgeInjected=$bridgeInjected " +
                                    "current=${event.current} total=${event.total}"
                            )
                            updateLoadState(true, 1f)
                        }
                    }
                )
            }
            val initialWidth = canvas.width.coerceAtLeast(1)
            val initialHeight = canvas.height.coerceAtLeast(1)
            val initialHostScale = canvas.webView2HostScale()
            shell?.setSize(initialWidth, initialHeight)
            browser?.setBounds(0, 0, initialWidth, initialHeight)
            shell?.open()
            logDesktopWebView2(
                "controller_open panel=$instanceId shellVisible=${shell?.isVisible == true} " +
                    "initial=${canvas.width}x${canvas.height} applied=${initialWidth}x$initialHeight " +
                    "browserBounds=${browser?.bounds?.width ?: -1}x${browser?.bounds?.height ?: -1}"
            )
            logWebViewLayoutDiag(
                "controller_open panel=$instanceId shellVisible=${shell?.isVisible == true} " +
                    "initial=${canvas.width}x${canvas.height} applied=${initialWidth}x$initialHeight " +
                    "hostScale=${initialHostScale.scaleX.formatLogFloat()}x${initialHostScale.scaleY.formatLogFloat()} " +
                    "shellBounds=${shell?.bounds?.formatSwtBounds().orEmpty()} " +
                    "browserBounds=${browser?.bounds?.formatSwtBounds().orEmpty()} canvasBounds=${canvas.bounds.formatAwtBounds()}"
            )
        }.onFailure { error ->
            logDesktopWebView2(
                "controller_create_failed panel=$instanceId error=\"${error.desktopWebView2Message().logPreview(300)}\""
            )
            reportError(error)
            dispose()
        }
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
                    if (System.getProperty(DesktopWebView2EdgeDataDirProperty).isNullOrBlank()) {
                        System.setProperty(
                            DesktopWebView2EdgeDataDirProperty,
                            File(desktopUserCacheRoot(), "webview2").absolutePath
                        )
                    }
                    logDesktopWebView2(
                        "swt_event_loop_user_data_dir path=\"${System.getProperty(DesktopWebView2EdgeDataDirProperty).orEmpty().logPreview(200)}\""
                    )
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
            "Episteme SWT WebView2"
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
        if (swtDisplay == null || swtDisplay.isDisposed) {
            logDesktopWebView2("swt_async_display_unavailable")
            onError(IllegalStateException("SWT display is not available."))
            return
        }
        swtDisplay.asyncExec {
            runCatching {
                if (!swtDisplay.isDisposed) {
                    block(swtDisplay)
                }
            }.onFailure(onError)
        }
    }
}

private fun Color.toAwtColor(): java.awt.Color = java.awt.Color(toArgb(), true)

private fun java.awt.Rectangle.formatAwtBounds(): String {
    return "${x},${y} ${width}x$height"
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

private fun Throwable.desktopWebView2Message(): String {
    return message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
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
              return {
                left: round(rect.left),
                top: round(rect.top),
                right: round(rect.right),
                bottom: round(rect.bottom),
                width: round(rect.width),
                height: round(rect.height),
                centerX: round(centerX),
                centerDelta: round(centerX - ((window.innerWidth || 0) / 2)),
                marginLeft: cssValue(element, 'margin-left').trim(),
                marginRight: cssValue(element, 'margin-right').trim(),
                paddingTop: cssValue(element, 'padding-top').trim(),
                paddingBottom: cssValue(element, 'padding-bottom').trim(),
                textAlign: cssValue(element, 'text-align').trim(),
                maxWidth: cssValue(element, 'max-width').trim(),
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
            var sampledElement = document.elementFromPoint(
              Math.max(0, Math.min((window.innerWidth || 0) - 1, Math.round((window.innerWidth || 0) / 2))),
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
              readerFontSize: cssVar('--reader-font-size'),
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
              scrollX: window.scrollX || 0,
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

@Synchronized
private fun nextDesktopWebView2InstanceId(): Int {
    DesktopWebView2InstanceSeed += 1
    return DesktopWebView2InstanceSeed
}

private const val DesktopSwtReadyTimeoutSeconds = 10L
private const val DesktopWebView2NativeBridgeName = "epistemeCallNative"
private const val DesktopWebView2DiagnosticMethodName = "readerWebView2Diagnostic"
private const val DesktopWebView2SwtBrowserType = "edge"
private const val DesktopWebView2EdgeDataDirProperty = "org.eclipse.swt.browser.EdgeDataDir"

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
        padding-top: var(--reader-vertical-margin-y) !important;
        padding-bottom: var(--reader-vertical-margin-y) !important;
      }
      body.reader-vertical .chapter,
      body.reader-vertical .reader-content {
        box-sizing: border-box !important;
        min-width: 0 !important;
      }
      body.reader-vertical .chapter {
        width: 100% !important;
        max-width: var(--reader-vertical-content-width) !important;
        margin-left: auto !important;
        margin-right: auto !important;
        min-height: max(0px, calc(100vh - (var(--reader-vertical-margin-y) * 2))) !important;
      }
      body.reader-vertical .reader-content {
        width: 100% !important;
        max-width: 100% !important;
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
