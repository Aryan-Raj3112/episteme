package com.aryan.reader.shared.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aryan.reader.shared.AndroidShareArtifactManager
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.externalLookupUrl
import com.aryan.reader.shared.isReaderExternalHref
import com.aryan.reader.shared.normalizeReaderHref
import com.aryan.reader.shared.reader.SharedJvmBookLoader
import com.aryan.reader.shared.reader.sharedEpubOpenTrace
import com.aryan.reader.shared.reader.sharedEpubOpenTraceElapsedMs
import com.aryan.reader.shared.reader.sharedEpubOpenTraceMark
import com.aryan.reader.shared.reader.sharedEpubOpenTraceMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject
import java.io.File
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.aryan.reader.shared.readerPageInfoCornerClearancePx
@Composable
internal actual fun rememberSharedMobileEpubLoadState(book: BookItem): SharedMobileEpubLoadState {
    val context = rememberAndroidSharedMobileContext()
    var state by remember(book.id, book.path) { mutableStateOf(SharedMobileEpubLoadState()) }
    LaunchedEffect(book.id, book.path) {
        state = SharedMobileEpubLoadState(isLoading = true)
        state = runCatching {
            withContext(Dispatchers.IO) {
                val file = book.resolveAndroidReaderFile(context)
                SharedJvmBookLoader.load(
                    file = file,
                    type = book.type,
                    titleOverride = book.title,
                    authorOverride = book.author,
                )
            }
        }.fold(
            onSuccess = { SharedMobileEpubLoadState(isLoading = false, book = it) },
            onFailure = {
                SharedMobileEpubLoadState(
                    isLoading = false,
                    errorMessage = it.message ?: "Could not open this book",
                )
            },
        )
    }
    return state
}

private fun BookItem.resolveAndroidReaderFile(context: Context): File {
    val value = path?.trim().orEmpty()
    require(value.isNotBlank()) { "Book has no local path" }
    val uri = Uri.parse(value)
    if (uri.scheme.isNullOrBlank() || uri.scheme == "file") {
        return File(uri.path ?: value)
    }
    val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?.let { ".$it" }
        .orEmpty()
    val target = File(context.cacheDir, "shared-reader-${id.hashCode()}$extension")
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Could not open $uri" }
        target.outputStream().use(input::copyTo)
    }
    return target
}

@Composable
internal actual fun SharedMobileEpubWebView(
    html: String,
    contentChunks: List<String>,
    appearanceScript: String,
    navigationScript: String?,
    navigationRequestId: Long,
    highlightsApplyScript: String,
    onBridgeMessage: (method: String, payload: String) -> Unit,
    positionController: SharedMobileEpubWebViewController?,
    streamPageLoader: SharedMobileEpubStreamPageLoader?,
    streamPageUnavailableLabel: String,
    contentBackgroundArgb: Long,
    modifier: Modifier,
) {
    rememberAndroidSharedMobileContext()
    val coordinator = remember { AndroidEpubWebViewCoordinator(onBridgeMessage) }
    coordinator.onBridgeMessage = onBridgeMessage
    DisposableEffect(positionController, coordinator) {
        positionController?.attach { callback -> coordinator.captureCurrentLocator(callback) }
        onDispose { positionController?.detach() }
    }
    AndroidView(
        modifier = modifier,
        factory = coordinator::createWebView,
        update = { webView -> coordinator.update(
            webView, html, contentChunks, appearanceScript, navigationScript, navigationRequestId,
            highlightsApplyScript, contentBackgroundArgb,
        ) },
        onRelease = coordinator::release,
    )
}

private class AndroidEpubBridge(
    private val coordinator: AndroidEpubWebViewCoordinator,
) {
    @JavascriptInterface
    fun callNative(method: String, payload: String): Boolean =
        coordinator.handleBridgeMessage(method, payload)
}

private class AndroidEpubWebViewCoordinator(
    var onBridgeMessage: (String, String) -> Unit,
) {
    private var activeWebView: WebView? = null
    private var contentChunks: List<String> = emptyList()
    private var loadedHtmlHash: Int? = null
    private var loadedHtmlLength = -1
    private var lastHtml: String? = null
    private var appliedAppearanceHash: Int? = null
    private var appliedHighlightsHash: Int? = null
    private var appliedNavigationRequestId = Long.MIN_VALUE
    private var appliedBackgroundArgb: Long? = null
    private var latestAppearanceScript = ""
    private var latestNavigationScript: String? = null
    private var latestNavigationRequestId = Long.MIN_VALUE
    private var latestHighlightsApplyScript = ""
    private var htmlLoadStartMark: kotlin.time.TimeSource.Monotonic.ValueTimeMark? = null

    fun createWebView(context: Context): WebView = WebView(context).apply {
        activeWebView = this
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        addJavascriptInterface(AndroidEpubBridge(this@AndroidEpubWebViewCoordinator), AndroidEpubBridgeName)
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val loadMs = htmlLoadStartMark?.let { sharedEpubOpenTraceElapsedMs(it) }
                sharedEpubOpenTrace { "webview didFinishLoad ms=${loadMs?.let { sharedEpubOpenTraceMs(it) } ?: "?"}" }
                // Sequence appearance -> highlights -> navigation so a big chapter
                // finishes layout before the navigation scroll runs.
                view.evaluateJavascript(AndroidEpubBridgeBootstrapScript) { _ ->
                    applyPendingScriptsInOrder(view)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = normalizeReaderHref(request.url.toString())
                return if (isReaderExternalHref(url)) {
                    openSharedMobileEpubExternalLink(url)
                    true
                } else {
                    false
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                sharedEpubOpenTrace { "webview renderProcessGone didCrash=${detail.didCrash()}" }
                // Drop hashes so the next update reloads; reload immediately when
                // the crashed view is still the active one and we retain the doc.
                loadedHtmlHash = null
                loadedHtmlLength = -1
                appliedAppearanceHash = null
                appliedHighlightsHash = null
                appliedNavigationRequestId = Long.MIN_VALUE
                val html = lastHtml
                if (html != null && view == activeWebView) {
                    htmlLoadStartMark = sharedEpubOpenTraceMark()
                    sharedEpubOpenTrace { "webview reloadAfterCrash chars=${html.length}" }
                    view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                }
                // Returning true means we handled the crash; the WebView can be reused.
                // If the renderer crashed (not OOM-killed), destroying here would
                // break the owning AndroidView, so let the framework recover it.
                return true
            }
        }
    }

    private fun applyPendingScriptsInOrder(view: WebView) {
        val appearance = latestAppearanceScript.takeIf { it.isNotBlank() }
        val highlights = latestHighlightsApplyScript.takeIf { it.isNotBlank() }
        val navigation = latestNavigationScript
        if (appearance == null) {
            applyHighlightsThenNavigation(view, highlights, navigation)
            return
        }
        view.evaluateJavascript(appearance) { _ ->
            appliedAppearanceHash = appearance.hashCode()
            applyHighlightsThenNavigation(view, highlights, navigation)
        }
    }

    private fun applyHighlightsThenNavigation(view: WebView, highlights: String?, navigation: String?) {
        if (highlights == null) {
            applyNavigationScript(view, navigation)
            return
        }
        view.evaluateJavascript(highlights) { _ ->
            appliedHighlightsHash = highlights.hashCode()
            applyNavigationScript(view, navigation)
        }
    }

    private fun applyNavigationScript(view: WebView, navigation: String?) {
        if (navigation == null) return
        view.evaluateJavascript(navigation) { _ ->
            appliedNavigationRequestId = latestNavigationRequestId
        }
    }

    fun update(
        webView: WebView,
        html: String,
        contentChunks: List<String>,
        appearanceScript: String,
        navigationScript: String?,
        navigationRequestId: Long,
        highlightsApplyScript: String,
        contentBackgroundArgb: Long,
    ) {
        activeWebView = webView
        this.contentChunks = contentChunks
        if (appliedBackgroundArgb != contentBackgroundArgb) {
            appliedBackgroundArgb = contentBackgroundArgb
            webView.setBackgroundColor((contentBackgroundArgb and 0xFFFFFFFFL).toInt())
        }
        latestAppearanceScript = appearanceScript
        latestNavigationScript = navigationScript
        latestNavigationRequestId = navigationRequestId
        latestHighlightsApplyScript = highlightsApplyScript
        val htmlHash = html.hashCode()
        if (loadedHtmlHash != htmlHash || loadedHtmlLength != html.length) {
            loadedHtmlHash = htmlHash
            loadedHtmlLength = html.length
            lastHtml = html
            appliedAppearanceHash = null
            appliedHighlightsHash = null
            appliedNavigationRequestId = Long.MIN_VALUE
            htmlLoadStartMark = sharedEpubOpenTraceMark()
            sharedEpubOpenTrace { "webview loadData start chars=${html.length} chunks=${contentChunks.size}" }
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            return
        }
        val appearanceHash = appearanceScript.hashCode()
        if (appliedAppearanceHash != appearanceHash) {
            appliedAppearanceHash = appearanceHash
            webView.evaluateJavascript(appearanceScript, null)
        }
        val highlightsHash = highlightsApplyScript.hashCode()
        if (highlightsApplyScript.isNotBlank() && appliedHighlightsHash != highlightsHash) {
            appliedHighlightsHash = highlightsHash
            webView.evaluateJavascript(highlightsApplyScript, null)
        }
        if (navigationScript != null && appliedNavigationRequestId != navigationRequestId) {
            appliedNavigationRequestId = navigationRequestId
            webView.evaluateJavascript(navigationScript, null)
        }
    }

    fun handleBridgeMessage(method: String, payload: String): Boolean {
        if (method == "readerCopyText") {
            val text = runCatching { JSONObject(payload).optString("text") }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: return false
            return writeSharedClipboard(
                label = "Copied Text",
                text = text,
            ).success
        }
        if (method == "readerChunkRequested") {
            val index = AndroidEpubChunkIndexRegex.find(payload)
                ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return false
            val chunk = contentChunks.getOrNull(index) ?: return false
            val provideMark = sharedEpubOpenTraceMark()
            sharedEpubOpenTrace { "webview chunkProvide start index=$index chunkChars=${chunk.length}" }
            activeWebView?.post {
                activeWebView?.evaluateJavascript(
                    "window.readerVirtualization && window.readerVirtualization.provideChunk($index, ${JsonPrimitive(chunk)});",
                ) { _ ->
                    sharedEpubOpenTrace { "webview chunkProvide done index=$index dispatchMs=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(provideMark))}" }
                }
            }
            return true
        }
        onBridgeMessage(method, payload)
        return true
    }

    fun captureCurrentLocator(callback: (String?) -> Unit) {
        val webView = activeWebView
        if (webView == null) {
            callback(null)
            return
        }
        webView.post {
            webView.evaluateJavascript(SharedMobileEpubCaptureCurrentPositionScript) { raw ->
                callback(decodeSharedMobileJavascriptResult(raw))
            }
        }
    }

    fun release(webView: WebView) {
        sharedEpubOpenTrace { "webview release" }
        webView.stopLoading()
        webView.removeJavascriptInterface(AndroidEpubBridgeName)
        webView.webViewClient = WebViewClient()
        webView.destroy()
        activeWebView = null
        contentChunks = emptyList()
        loadedHtmlHash = null
        loadedHtmlLength = -1
        lastHtml = null
        appliedBackgroundArgb = null
        htmlLoadStartMark = null
    }
}

private const val AndroidEpubBridgeName = "ReaderBridge"
private val AndroidEpubChunkIndexRegex = Regex("\\\"index\\\"\\s*:\\s*(\\d+)")
private val AndroidEpubBridgeBootstrapScript = """
    (function () {
      window.kmpJsBridge = {
        callNative: function (method, payload) {
          try { return window.$AndroidEpubBridgeName.callNative(String(method || ''), String(payload || '{}')); } catch (_) { return false; }
        }
      };
      window.readerDisableLinkFallback = true;
    })();
""".trimIndent()

internal actual fun openSharedMobileEpubExternalLink(url: String): Boolean = openAndroidUrl(url)

/**
 * Real rounded-corner inset for the PageInfo bar.
 *
 * `WindowInsets.safeDrawing` covers the system bars, cutouts and waterfall only —
 * it says nothing about the corner curve, which is exactly what clips the bar's
 * edge-pinned clock and percentage on a device with generous radii and no bar
 * inset. So read the actual radii (API 31+) and report the inset the platform
 * guideline prescribes: the radius of the corners on that edge, less whatever the
 * bar already pads, never below zero.
 *
 * Returns 0.dp when there is nothing to clear — pre-API 31, a window with no
 * rounded corners, or insets not attached yet — so square screens keep their
 * exact benchmark spacing.
 */
@Composable
actual fun sharedMobileEpubPageInfoCornerClearance(): Dp {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0.dp
    val density = LocalDensity.current
    val view = LocalView.current
    val windowInsets = view.rootWindowInsets ?: return 0.dp
    // The bar can be pinned to the top or the bottom edge, so both edges have to
    // clear; the largest radius on either side governs the horizontal inset.
    val radiusPx = listOf(
        RoundedCorner.POSITION_TOP_LEFT,
        RoundedCorner.POSITION_TOP_RIGHT,
        RoundedCorner.POSITION_BOTTOM_LEFT,
        RoundedCorner.POSITION_BOTTOM_RIGHT
    ).mapNotNull { windowInsets.getRoundedCorner(it)?.radius }.maxOrNull() ?: 0
    return with(density) {
        readerPageInfoCornerClearancePx(
            maxCornerRadiusPx = radiusPx,
            barSidePaddingPx = SharedReaderPageInfoBarSidePadding.roundToPx()
        ).toDp()
    }
}

// Android benchmark: the bar sits flush at the bottom edge when chrome hides.
internal actual val sharedMobileEpubPageInfoAlwaysApplyBottomSafeInset: Boolean = false

// Android benchmark: tinted/translucent info-bar color.
internal actual val sharedMobileEpubPageInfoMatchesReaderBackground: Boolean = false

internal actual fun openSharedMobileEpubLookup(action: ReaderExternalLookupAction, text: String): Boolean =
    openAndroidUrl(externalLookupUrl(action, text))

internal actual fun shareSharedMobileEpubImage(bytes: ByteArray, fileName: String): Boolean {
    if (bytes.isEmpty()) return false
    val context = AndroidSharedMobileContext.applicationContext ?: return false
    return runCatching {
        val artifact = AndroidShareArtifactManager.create(context, fileName, write = { output ->
            output.write(bytes)
        })
        val mimeType = when (artifact.fileName.substringAfterLast('.', "").lowercase()) {
            "svg" -> "image/svg+xml"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/png"
        }
        context.startActivity(
            Intent.createChooser(
                AndroidShareArtifactManager.buildShareIntent(artifact, mimeType),
                null,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
}
