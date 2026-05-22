package com.aryan.reader.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.ui.ReaderContentNavigationTarget
import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.request.RequestInterceptor
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebContent
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.WebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import kotlinx.coroutines.launch

@Composable
internal fun DesktopEpubWebView(
    html: String,
    appearanceScript: String,
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
    modifier: Modifier = Modifier
) {
    val latestOnHighlightCreated by rememberUpdatedState(onHighlightCreated)
    val latestOnHighlightSelected by rememberUpdatedState(onHighlightSelected)
    val latestOnKeyboardNavigation by rememberUpdatedState(onKeyboardNavigation)
    val latestOnSelectionAction by rememberUpdatedState(onSelectionAction)
    val latestOnLinkClicked by rememberUpdatedState(onLinkClicked)
    val latestOnVisiblePageChanged by rememberUpdatedState(onVisiblePageChanged)
    val latestOnPointerActivity by rememberUpdatedState(onPointerActivity)
    val scope = rememberCoroutineScope()
    val linkRequestInterceptor = remember(scope, networkAccessEnabled) {
        object : RequestInterceptor {
            override fun onInterceptUrlRequest(
                request: WebRequest,
                navigator: WebViewNavigator
            ): WebRequestInterceptResult {
                if (!networkAccessEnabled && request.url.isRemoteNetworkUrl()) {
                    logEpubLink("request_blocked_offline url=\"${request.url.logPreview()}\"")
                    return WebRequestInterceptResult.Reject
                }
                if (!request.isForMainFrame) return WebRequestInterceptResult.Allow
                val link = request.url.readerLinkClickFromIntercept() ?: return WebRequestInterceptResult.Allow
                logEpubLink(
                    "request_intercept method=${request.method} redirect=${request.isRedirect} " +
                        "url=\"${request.url.logPreview()}\" href=\"${link.href.logPreview()}\""
                )
                scope.launch {
                    latestOnLinkClicked(link.copy(source = "request"))
                }
                return WebRequestInterceptResult.Reject
            }
        }
    }
    val navigator = rememberWebViewNavigator(requestInterceptor = linkRequestInterceptor)
    val bridge = rememberWebViewJsBridge()

    DisposableEffect(bridge) {
        val handlers = listOf(
            desktopEpubBridgeHandler("readerHighlightCreated") { message ->
                val highlight = EpubAnnotationSerializer.parseHighlightJsonLenient(message.params)
                if (highlight == null) {
                    logEpubSelectionDebug("highlight_parse_failed params=${message.params.logPreview(900)}")
                } else {
                    scope.launch { latestOnHighlightCreated(highlight) }
                }
            },
            desktopEpubBridgeHandler("readerHighlightClicked") { message ->
                message.params.readerHighlightClickOrNull()?.let { highlightClick ->
                    scope.launch { latestOnHighlightSelected(highlightClick.highlightId) }
                }
            },
            desktopEpubBridgeHandler("readerPositionChanged") { message ->
                message.params.readerPositionOrNull()?.let { position ->
                    scope.launch { latestOnVisiblePageChanged(position.pageIndex, position.locator) }
                }
            },
            desktopEpubBridgeHandler("readerSelectionAction") { message ->
                val selectionAction = message.params.readerSelectionActionOrNull()
                if (selectionAction != null) {
                    scope.launch { latestOnSelectionAction(selectionAction.action, selectionAction.text) }
                }
            },
            desktopEpubBridgeHandler("readerKeyNavigation") { message ->
                message.params.readerKeyNavigationOrNull()?.let { action ->
                    scope.launch { latestOnKeyboardNavigation(action) }
                }
            },
            desktopEpubBridgeHandler("readerPointerActivity") { _ ->
                scope.launch { latestOnPointerActivity() }
            },
            desktopEpubBridgeHandler("readerTtsHighlightLog") { message ->
                logDesktopTts("epub_highlight_js ${message.params.logPreview(500)}")
            },
            desktopEpubBridgeHandler("readerSelectionDebugLog") { message ->
                logEpubSelectionDebug(message.params.readerSelectionDebugMessageOrNull() ?: message.params.logPreview(900))
            },
            desktopEpubBridgeHandler("readerPaginationLayoutLog") { message ->
                logEpubPagination(message.params.readerPaginationLogMessageOrNull() ?: message.params.logPreview(900))
            },
            desktopEpubBridgeHandler("readerGapLayoutLog") { message ->
                logReaderGap(message.params.readerPaginationLogMessageOrNull() ?: message.params.logPreview(900))
            },
            desktopEpubBridgeHandler("readerLinkClicked") { message ->
                logEpubLink("bridge_message params=\"${message.params.logPreview()}\"")
                val link = message.params.readerLinkClickOrNull()
                if (link == null) {
                    logEpubLink("bridge_message_ignored reason=parse_failed")
                } else {
                    logEpubLink(
                        "bridge_message_parsed href=\"${link.href.logPreview()}\" " +
                            "chapterIndex=${link.chapterIndex} chapterHref=\"${link.chapterHref.orEmpty().logPreview()}\""
                    )
                    scope.launch { latestOnLinkClicked(link) }
                }
            }
        )
        handlers.forEach { bridge.register(it) }
        onDispose {
            handlers.forEach { bridge.unregister(it) }
        }
    }

    val state = remember {
        WebViewState(
            WebContent.Data(
                data = html,
                baseUrl = null,
                encoding = "utf-8",
                mimeType = "text/html",
                historyUrl = null
            )
        )
    }

    LaunchedEffect(html) {
        navigator.loadHtml(
            html = html,
            baseUrl = null,
            mimeType = "text/html",
            encoding = "utf-8",
            historyUrl = null
        )
    }

    Box(modifier = modifier) {
        WebView(
            state = state,
            modifier = Modifier.fillMaxSize(),
            captureBackPresses = false,
            navigator = navigator,
            webViewJsBridge = bridge
        )

        LaunchedEffect(state.loadingState) {
            if (!state.loadingState.isFinished()) return@LaunchedEffect
            navigator.evaluateJavaScript(DesktopEpubKeyNavigationScript)
        }

        LaunchedEffect(isFullscreen, state.loadingState) {
            if (!state.loadingState.isFinished()) return@LaunchedEffect
            navigator.evaluateJavaScript("window.readerDesktopFullscreen = ${if (isFullscreen) "true" else "false"};")
        }

        LaunchedEffect(html, state.loadingState) {
            if (!state.loadingState.isFinished()) return@LaunchedEffect
            navigator.evaluateJavaScript("window.readerPaginationLayoutLog && window.readerPaginationLayoutLog('desktop_finished');")
        }

        LaunchedEffect(appearanceScript, state.loadingState) {
            if (!state.loadingState.isFinished()) return@LaunchedEffect
            navigator.evaluateJavaScript(appearanceScript)
        }

        LaunchedEffect(
            navigationTarget.requestId,
            navigationTarget.readingMode,
            state.loadingState
        ) {
            if (navigationTarget.readingMode != ReaderReadingMode.VERTICAL) return@LaunchedEffect
            if (!state.loadingState.isFinished()) return@LaunchedEffect
            val locator = navigationTarget.locator ?: return@LaunchedEffect
            navigator.evaluateJavaScript("window.readerScrollToLocator && window.readerScrollToLocator(${locator.toReaderLocatorJson()});")
        }

        LaunchedEffect(
            navigationTarget.ttsRequestId,
            navigationTarget.ttsLocator,
            navigationTarget.readingMode,
            state.loadingState
        ) {
            if (!state.loadingState.isFinished()) return@LaunchedEffect
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
            navigator.evaluateJavaScript(command)
        }

        LaunchedEffect(highlights, state.loadingState) {
            if (!state.loadingState.isFinished()) return@LaunchedEffect
            val highlightsJson = EpubAnnotationSerializer.highlightsToJson(highlights)
            navigator.evaluateJavaScript("window.readerApplyHighlights && window.readerApplyHighlights($highlightsJson);")
        }

        val loadingState = state.loadingState
        if (loadingState is LoadingState.Loading) {
            LinearProgressIndicator(
                progress = { loadingState.progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun desktopEpubBridgeHandler(
    methodName: String,
    onMessage: (JsMessage) -> Unit
): IJsMessageHandler {
    return object : IJsMessageHandler {
        override fun methodName(): String = methodName

        override fun handle(
            message: JsMessage,
            navigator: WebViewNavigator?,
            callback: (String) -> Unit
        ) {
            onMessage(message)
        }
    }
}

private fun LoadingState.isFinished(): Boolean = this is LoadingState.Finished

private val DesktopEpubKeyNavigationScript = """
    (function () {
      if (!window.readerDesktopChromeTapInstalled) {
        window.readerDesktopChromeTapInstalled = true;
        var chromeTapStart = null;
        function notifyChromeTap() {
          if (!window.kmpJsBridge || !window.kmpJsBridge.callNative) return;
          window.kmpJsBridge.callNative('readerPointerActivity', '{}');
        }
        function chromeTapIgnored(target) {
          if (!target || !target.closest) return false;
          return !!target.closest(
            'a[href], button, input, textarea, select, [contenteditable="true"], #reader-selection-menu, .reader-selection-handle'
          );
        }
        function hasActiveReaderSelection() {
          var selection = window.getSelection && window.getSelection();
          return !!selection && selection.toString().trim().length > 0;
        }
        function beginChromeTap(event) {
          if (event.button !== undefined && event.button !== 0) return;
          if (chromeTapIgnored(event.target)) {
            chromeTapStart = null;
            return;
          }
          chromeTapStart = {
            pointerId: event.pointerId,
            x: event.clientX || 0,
            y: event.clientY || 0,
            at: Date.now()
          };
        }
        function finishChromeTap(event) {
          if (!chromeTapStart) return;
          if (event.pointerId !== undefined && chromeTapStart.pointerId !== undefined && event.pointerId !== chromeTapStart.pointerId) return;
          var dx = (event.clientX || 0) - chromeTapStart.x;
          var dy = (event.clientY || 0) - chromeTapStart.y;
          var elapsed = Date.now() - chromeTapStart.at;
          chromeTapStart = null;
          if ((dx * dx + dy * dy) > 64 || elapsed > 650) return;
          if (chromeTapIgnored(event.target) || hasActiveReaderSelection()) return;
          notifyChromeTap();
        }
        document.addEventListener('pointerdown', beginChromeTap, true);
        document.addEventListener('pointerup', finishChromeTap, true);
        document.addEventListener('pointercancel', function () { chromeTapStart = null; }, true);
        document.addEventListener('click', function (event) {
          if (window.PointerEvent) return;
          beginChromeTap(event);
          finishChromeTap(event);
        }, true);
      }
      if (window.readerDesktopKeyNavigationInstalled) return;
      window.readerDesktopKeyNavigationInstalled = true;
      document.addEventListener('keydown', function (event) {
        var target = event.target;
        var tag = target && target.tagName ? target.tagName.toLowerCase() : '';
        if (target && (target.isContentEditable || tag === 'input' || tag === 'textarea' || tag === 'select')) return;
        var action = null;
        if (event.ctrlKey && (event.key === 'f' || event.key === 'F')) action = 'search';
        else if (event.ctrlKey && (event.key === 'g' || event.key === 'G')) action = 'nextSearch';
        else if (event.key === 'ArrowRight' || event.key === 'PageDown') action = 'next';
        else if (event.key === 'ArrowLeft' || event.key === 'PageUp') action = 'previous';
        else if (event.key === 'Home') action = 'first';
        else if (event.key === 'End') action = 'last';
        else if (event.key === 'Escape' && window.readerDesktopFullscreen) action = 'exitFullscreen';
        if (!action || !window.kmpJsBridge || !window.kmpJsBridge.callNative) return;
        event.preventDefault();
        event.stopPropagation();
        window.kmpJsBridge.callNative('readerKeyNavigation', JSON.stringify({ action: action }));
      }, true);
    })();
""".trimIndent()
