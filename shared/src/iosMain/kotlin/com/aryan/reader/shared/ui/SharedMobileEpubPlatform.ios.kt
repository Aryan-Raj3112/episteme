@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.ios.loadIosEpubBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.darwin.NSObject

@Composable
internal actual fun rememberSharedMobileEpubLoadState(book: BookItem): SharedMobileEpubLoadState {
    var state by remember(book.id, book.path) { mutableStateOf(SharedMobileEpubLoadState()) }
    LaunchedEffect(book.id, book.path) {
        state = SharedMobileEpubLoadState(isLoading = true)
        state = runCatching {
            withContext(Dispatchers.Default) { loadIosEpubBook(book) }
        }.fold(
            onSuccess = { SharedMobileEpubLoadState(isLoading = false, book = it) },
            onFailure = { error ->
                SharedMobileEpubLoadState(
                    isLoading = false,
                    errorMessage = error.message ?: "Could not open this EPUB"
                )
            }
        )
    }
    return state
}

@Composable
internal actual fun SharedMobileEpubWebView(
    html: String,
    contentChunks: List<String>,
    appearanceScript: String,
    navigationScript: String?,
    navigationRequestId: Long,
    onBridgeMessage: (method: String, payload: String) -> Unit,
    modifier: Modifier
) {
    val latestBridgeMessage by rememberUpdatedState(onBridgeMessage)
    val coordinator = remember {
        IosEpubWebViewCoordinator { method, payload -> latestBridgeMessage(method, payload) }
    }
    coordinator.onBridgeMessage = { method, payload -> latestBridgeMessage(method, payload) }
    UIKitView(
        factory = coordinator::createWebView,
        modifier = modifier,
        update = { webView ->
            coordinator.update(
                webView = webView,
                html = html,
                contentChunks = contentChunks,
                appearanceScript = appearanceScript,
                navigationScript = navigationScript,
                navigationRequestId = navigationRequestId
            )
        },
        onRelease = coordinator::release,
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true
        )
    )
}

internal actual fun openSharedMobileEpubExternalLink(url: String): Boolean {
    val normalized = if (url.trim().startsWith("//")) "https:${url.trim()}" else url.trim()
    val target = NSURL.URLWithString(normalized) ?: return false
    return UIApplication.sharedApplication.openURL(target)
}

@Composable
internal actual fun rememberSharedMobileEpubLocalTts(): SharedMobileEpubLocalTts {
    val controller = remember { IosSharedMobileEpubLocalTts() }
    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }
    return controller
}

private class IosSharedMobileEpubLocalTts : SharedMobileEpubLocalTts {
    private val synthesizer = AVSpeechSynthesizer()
    private val delegate = IosSharedMobileEpubSpeechDelegate { state = it }
    override var state by mutableStateOf(SharedMobileEpubLocalTtsState.IDLE)

    init {
        synthesizer.delegate = delegate
    }

    override fun speak(text: String) {
        val normalized = text.trim()
        if (normalized.isBlank()) return
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        synthesizer.speakUtterance(AVSpeechUtterance(string = normalized))
    }

    override fun pause() {
        synthesizer.pauseSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    override fun resume() {
        synthesizer.continueSpeaking()
    }

    override fun stop() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        state = SharedMobileEpubLocalTtsState.IDLE
    }
}

private class IosSharedMobileEpubSpeechDelegate(
    private val onStateChange: (SharedMobileEpubLocalTtsState) -> Unit
) : NSObject(), AVSpeechSynthesizerDelegateProtocol {

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didStartSpeechUtterance: AVSpeechUtterance
    ) {
        onStateChange(SharedMobileEpubLocalTtsState.SPEAKING)
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didFinishSpeechUtterance: AVSpeechUtterance
    ) {
        onStateChange(SharedMobileEpubLocalTtsState.IDLE)
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didPauseSpeechUtterance: AVSpeechUtterance
    ) {
        onStateChange(SharedMobileEpubLocalTtsState.PAUSED)
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didContinueSpeechUtterance: AVSpeechUtterance
    ) {
        onStateChange(SharedMobileEpubLocalTtsState.SPEAKING)
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didCancelSpeechUtterance: AVSpeechUtterance
    ) {
        onStateChange(SharedMobileEpubLocalTtsState.IDLE)
    }
}

private class IosEpubWebViewCoordinator(
    var onBridgeMessage: (String, String) -> Unit
) {
    private val messageHandler = IosEpubScriptMessageHandler(::handleBridgeMessage)
    private val navigationDelegate = IosEpubNavigationDelegate(::documentDidFinishLoading)
    private var activeWebView: WKWebView? = null
    private var contentChunks: List<String> = emptyList()
    private var loadedHtmlHash: Int? = null
    private var loadedHtmlLength: Int = -1
    private var appliedAppearanceHash: Int? = null
    private var appliedNavigationRequestId: Long = Long.MIN_VALUE
    private var latestAppearanceScript: String = ""
    private var latestNavigationScript: String? = null
    private var latestNavigationRequestId: Long = Long.MIN_VALUE

    fun createWebView(): WKWebView {
        val contentController = WKUserContentController()
        contentController.addUserScript(
            WKUserScript(
                source = IosEpubBridgeBootstrapScript,
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                forMainFrameOnly = false
            )
        )
        contentController.addScriptMessageHandler(messageHandler, name = IosEpubBridgeName)
        val configuration = WKWebViewConfiguration().apply {
            userContentController = contentController
            defaultWebpagePreferences.allowsContentJavaScript = true
        }
        return WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
            activeWebView = this
            navigationDelegate = this@IosEpubWebViewCoordinator.navigationDelegate
            opaque = false
            backgroundColor = UIColor.clearColor
            scrollView.backgroundColor = UIColor.clearColor
            scrollView.bounces = true
            scrollView.alwaysBounceVertical = true
            scrollView.alwaysBounceHorizontal = false
            scrollView.showsHorizontalScrollIndicator = false
        }
    }

    fun update(
        webView: WKWebView,
        html: String,
        contentChunks: List<String>,
        appearanceScript: String,
        navigationScript: String?,
        navigationRequestId: Long
    ) {
        activeWebView = webView
        this.contentChunks = contentChunks
        latestAppearanceScript = appearanceScript
        latestNavigationScript = navigationScript
        latestNavigationRequestId = navigationRequestId
        val htmlHash = html.hashCode()
        if (loadedHtmlHash != htmlHash || loadedHtmlLength != html.length) {
            loadedHtmlHash = htmlHash
            loadedHtmlLength = html.length
            appliedAppearanceHash = null
            appliedNavigationRequestId = Long.MIN_VALUE
            webView.loadHTMLString(html, baseURL = null)
            return
        }

        val appearanceHash = appearanceScript.hashCode()
        if (appliedAppearanceHash != appearanceHash) {
            appliedAppearanceHash = appearanceHash
            webView.evaluateJavaScript(appearanceScript, completionHandler = null)
        }
        if (
            navigationScript != null &&
            appliedNavigationRequestId != navigationRequestId
        ) {
            appliedNavigationRequestId = navigationRequestId
            webView.evaluateJavaScript(navigationScript, completionHandler = null)
        }
    }

    private fun documentDidFinishLoading(webView: WKWebView) {
        if (latestAppearanceScript.isNotBlank()) {
            webView.evaluateJavaScript(latestAppearanceScript, completionHandler = null)
            appliedAppearanceHash = latestAppearanceScript.hashCode()
        }
        latestNavigationScript?.let { script ->
            webView.evaluateJavaScript(script, completionHandler = null)
            appliedNavigationRequestId = latestNavigationRequestId
        }
    }

    private fun handleBridgeMessage(method: String, payload: String) {
        if (method == "readerChunkRequested") {
            val index = IosEpubChunkIndexRegex.find(payload)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return
            val chunk = contentChunks.getOrNull(index) ?: return
            activeWebView?.evaluateJavaScript(
                "window.readerVirtualization && window.readerVirtualization.provideChunk($index, ${JsonPrimitive(chunk)});",
                completionHandler = null
            )
            return
        }
        onBridgeMessage(method, payload)
    }

    fun release(webView: WKWebView) {
        webView.stopLoading()
        webView.navigationDelegate = null
        webView.configuration.userContentController.removeScriptMessageHandlerForName(IosEpubBridgeName)
        activeWebView = null
        contentChunks = emptyList()
        loadedHtmlHash = null
        loadedHtmlLength = -1
    }
}

private class IosEpubNavigationDelegate(
    private val onFinished: (WKWebView) -> Unit
) : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onFinished(webView)
    }
}

private class IosEpubScriptMessageHandler(
    private val callback: (String, String) -> Unit
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        val raw = didReceiveScriptMessage.body as? String ?: return
        val separator = raw.indexOf('\n')
        if (separator <= 0) return
        callback(raw.substring(0, separator), raw.substring(separator + 1))
    }
}

private const val IosEpubBridgeName = "reader"
private val IosEpubChunkIndexRegex = Regex("\\\"index\\\"\\s*:\\s*(\\d+)")

private val IosEpubBridgeBootstrapScript = """
    (function () {
      function post(method, payload) {
        try {
          window.webkit.messageHandlers.$IosEpubBridgeName.postMessage(String(method || '') + '\n' + String(payload || '{}'));
        } catch (_) {}
      }
      window.kmpJsBridge = {
        callNative: function (method, payload) { post(method, payload); }
      };
      window.readerDisableLinkFallback = true;
      if (!window.readerIosPointerBridgeInstalled) {
        window.readerIosPointerBridgeInstalled = true;
        var start = null;
        document.addEventListener('touchstart', function (event) {
          if (!event.touches || event.touches.length !== 1) { start = null; return; }
          var touch = event.touches[0];
          var root = document.scrollingElement || document.documentElement;
          var maxScroll = Math.max(0, root.scrollHeight - window.innerHeight);
          start = {
            x: touch.clientX,
            y: touch.clientY,
            at: Date.now(),
            atTop: window.scrollY <= 2,
            atBottom: window.scrollY >= maxScroll - 2
          };
        }, { passive: true, capture: true });
        document.addEventListener('touchmove', function (event) {
          if (!start || !event.touches || event.touches.length !== 1) return;
          if (window.readerIosPullEnabled === false) return;
          var touch = event.touches[0];
          var dx = touch.clientX - start.x;
          var dy = touch.clientY - start.y;
          if (Math.abs(dy) <= Math.abs(dx) * 1.25) return;
          var multiplier = Math.max(0.5, Math.min(2.0, Number(window.readerIosPullMultiplier || 1)));
          var threshold = 100 * multiplier;
          if (start.atTop && dy > 0) {
            post('readerChapterPull', JSON.stringify({ direction: 'previous', progress: Math.min(1.25, dy / threshold) }));
          } else if (start.atBottom && dy < 0) {
            post('readerChapterPull', JSON.stringify({ direction: 'next', progress: Math.min(1.25, -dy / threshold) }));
          }
        }, { passive: true, capture: true });
        document.addEventListener('touchend', function (event) {
          if (!start || !event.changedTouches || event.changedTouches.length !== 1) { start = null; return; }
          var touch = event.changedTouches[0];
          var dx = touch.clientX - start.x;
          var dy = touch.clientY - start.y;
          var elapsed = Date.now() - start.at;
          var startedAtTop = start.atTop;
          var startedAtBottom = start.atBottom;
          start = null;
          var selection = window.getSelection && window.getSelection();
          if (selection && selection.toString().trim()) return;
          var target = event.target;
          if (target && target.closest && target.closest('a,button,input,textarea,select,#reader-selection-menu,.reader-selection-handle')) return;
          var multiplier = Math.max(0.5, Math.min(2.0, Number(window.readerIosPullMultiplier || 1)));
          var threshold = 100 * multiplier;
          post('readerChapterPull', JSON.stringify({ direction: dy >= 0 ? 'previous' : 'next', progress: 0 }));
          if (window.readerIosPullEnabled !== false && elapsed <= 1400 && Math.abs(dy) >= threshold && Math.abs(dy) > Math.abs(dx) * 1.25) {
            if (startedAtTop && dy > 0) {
              post('readerChapterBoundary', JSON.stringify({ direction: 'previous' }));
              return;
            }
            if (startedAtBottom && dy < 0) {
              post('readerChapterBoundary', JSON.stringify({ direction: 'next' }));
              return;
            }
          }
          if ((dx * dx + dy * dy) > 100 || elapsed > 650) return;
          post('readerPointerActivity', '{}');
        }, { passive: true, capture: true });
        document.addEventListener('touchcancel', function () {
          start = null;
          post('readerChapterPull', JSON.stringify({ direction: 'next', progress: 0 }));
        }, { passive: true, capture: true });
      }
    })();
""".trimIndent()
