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
import kotlin.math.roundToInt

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
            applyCanvasSizeToBrowser(width, height, reason = "resize")
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
                            logWebViewLayoutDiag(
                                "progress_completed panel=$instanceId bridgeInjected=$bridgeInjected " +
                                    "current=${event.current} total=${event.total}"
                            )
                            updateLoadState(true, 1f)
                        }
                    }
                )
            }
            applyCanvasSizeToBrowser(canvas.width, canvas.height, reason = "open")
            shell?.open()
            logDesktopWebView2(
                "controller_open panel=$instanceId shellVisible=${shell?.isVisible == true} " +
                    "initial=${canvas.width}x${canvas.height} " +
                    "browserBounds=${browser?.bounds?.width ?: -1}x${browser?.bounds?.height ?: -1}"
            )
            logWebViewLayoutDiag(
                "controller_open panel=$instanceId shellVisible=${shell?.isVisible == true} " +
                    "initial=${canvas.width}x${canvas.height} " +
                    "hostScale=${canvas.webView2HostScale().scaleX.formatLogFloat()}x${canvas.webView2HostScale().scaleY.formatLogFloat()} " +
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
        val targetWidth = width.coerceAtLeast(1)
        val targetHeight = (height * hostScale.scaleY).roundToInt().coerceAtLeast(1)
        webShell.setBounds(0, 0, targetWidth, targetHeight)
        webBrowser?.setBounds(0, 0, targetWidth, targetHeight)
        logDesktopWebView2(
            "controller_resize panel=$instanceId reason=$reason requested=${width}x${height} " +
                "target=${targetWidth}x$targetHeight axisMode=logicalWidth_scaledHeight_zeroOrigin " +
                "shellBounds=${webShell.bounds.x},${webShell.bounds.y} ${webShell.bounds.width}x${webShell.bounds.height} " +
                "browserBounds=${webBrowser?.bounds?.width ?: -1}x${webBrowser?.bounds?.height ?: -1}"
        )
        logWebViewLayoutDiag(
            "controller_resize panel=$instanceId reason=$reason requested=${width}x${height} " +
                "target=${targetWidth}x$targetHeight axisMode=logicalWidth_scaledHeight_zeroOrigin " +
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
        if (DesktopDiagnosticsEnabled) {
            append('\n')
            append(DesktopWebView2LayoutOverlayTag)
        }
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

private val DesktopWebView2LayoutOverlayTag = """
    <style id="episteme-webview2-layout-overlay-style">
      .episteme-webview2-layout-overlay {
        position: fixed;
        z-index: 2147483647;
        pointer-events: none;
        box-sizing: border-box;
        font: 12px/1.35 ui-monospace, SFMono-Regular, Consolas, monospace;
      }
      #episteme-webview2-overlay-viewport {
        inset: 0;
        border: 2px solid rgba(239, 68, 68, 0.95);
      }
      #episteme-webview2-overlay-content {
        border: 2px solid rgba(34, 197, 94, 0.95);
        background: rgba(34, 197, 94, 0.06);
      }
      #episteme-webview2-overlay-block {
        border: 2px dashed rgba(59, 130, 246, 0.95);
        background: rgba(59, 130, 246, 0.05);
      }
      #episteme-webview2-overlay-center {
        top: 0;
        bottom: 0;
        width: 0;
        border-left: 1px solid rgba(234, 179, 8, 0.95);
      }
      #episteme-webview2-overlay-label {
        left: 8px;
        top: 8px;
        max-width: min(720px, calc(100vw - 16px));
        padding: 6px 8px;
        color: #111827;
        background: rgba(255, 255, 255, 0.92);
        border: 1px solid rgba(17, 24, 39, 0.32);
        border-radius: 4px;
        white-space: pre-wrap;
      }
    </style>
    <script>
    (function () {
      if (window.readerWebView2LayoutOverlayInstalled) return;
      window.readerWebView2LayoutOverlayInstalled = true;
      function overlay(id) {
        var node = document.getElementById(id);
        if (!node) {
          node = document.createElement('div');
          node.id = id;
          node.className = 'episteme-webview2-layout-overlay';
          document.documentElement.appendChild(node);
        }
        return node;
      }
      var viewport = overlay('episteme-webview2-overlay-viewport');
      var contentBox = overlay('episteme-webview2-overlay-content');
      var blockBox = overlay('episteme-webview2-overlay-block');
      var centerLine = overlay('episteme-webview2-overlay-center');
      var label = overlay('episteme-webview2-overlay-label');
      function round(value) {
        return Math.round((Number(value) || 0) * 10) / 10;
      }
      function setRect(node, rect) {
        if (!rect) {
          node.style.display = 'none';
          return;
        }
        node.style.display = 'block';
        node.style.left = round(rect.left) + 'px';
        node.style.top = round(rect.top) + 'px';
        node.style.width = Math.max(0, round(rect.width)) + 'px';
        node.style.height = Math.max(0, round(rect.height)) + 'px';
      }
      function rectText(name, rect) {
        if (!rect) return name + '=none';
        return name + '=' + round(rect.left) + ',' + round(rect.top) + ' ' +
          round(rect.width) + 'x' + round(rect.height) + ' right=' + round(rect.right);
      }
      function visibleContent() {
        var x = Math.max(0, Math.min((window.innerWidth || 0) - 1, Math.round((window.innerWidth || 0) / 2)));
        var y = Math.max(0, Math.min((window.innerHeight || 0) - 1, Math.round((window.innerHeight || 0) / 2)));
        var element = document.elementFromPoint(x, y);
        return element && element.closest
          ? (element.closest('.reader-content') || document.querySelector('.reader-content'))
          : document.querySelector('.reader-content');
      }
      function visibleBlock(content) {
        if (!content) return null;
        var blocks = Array.prototype.slice.call(content.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, blockquote, figure, table, pre'));
        var height = window.innerHeight || 0;
        for (var i = 0; i < blocks.length; i++) {
          var rect = blocks[i].getBoundingClientRect();
          if (rect.width > 0 && rect.height > 0 && rect.bottom >= 0 && rect.top <= height) return blocks[i];
        }
        return blocks[0] || null;
      }
      var queued = false;
      function update() {
        queued = false;
        var viewportWidth = window.innerWidth || 0;
        var viewportHeight = window.innerHeight || 0;
        var content = visibleContent();
        var block = visibleBlock(content);
        var contentRect = content ? content.getBoundingClientRect() : null;
        var blockRect = block ? block.getBoundingClientRect() : null;
        viewport.style.display = 'block';
        centerLine.style.left = Math.round(viewportWidth / 2) + 'px';
        setRect(contentBox, contentRect);
        setRect(blockBox, blockRect);
        label.textContent =
          'red=viewport green=visible .reader-content blue=visible block yellow=center\n' +
          'viewport=' + viewportWidth + 'x' + viewportHeight +
          ' dpr=' + (window.devicePixelRatio || 1) +
          ' scroll=' + Math.round(window.scrollX || 0) + ',' + Math.round(window.scrollY || 0) + '\n' +
          rectText('content', contentRect) + '\n' +
          rectText('block', blockRect);
      }
      function schedule() {
        if (queued) return;
        queued = true;
        window.requestAnimationFrame(update);
      }
      window.addEventListener('resize', schedule, { passive: true });
      window.addEventListener('scroll', schedule, { passive: true });
      document.addEventListener('scroll', schedule, true);
      document.addEventListener('DOMContentLoaded', schedule, { once: true });
      window.addEventListener('load', schedule, { once: true });
      window.setInterval(schedule, 1000);
      schedule();
    })();
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
