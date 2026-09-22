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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legere.pdfiumandroid.api.Bookmark
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.AndroidShareArtifactManager
import com.aryan.reader.shared.PdfTocEntry
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.externalLookupUrl
import com.aryan.reader.shared.isReaderExternalHref
import com.aryan.reader.shared.normalizeReaderHref
import com.aryan.reader.shared.pdf.PdfTextPageSession
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.pdf.SharedPdfSearchIndex
import com.aryan.reader.shared.reader.SharedJvmBookLoader
import com.aryan.reader.shared.reader.sharedEpubOpenTrace
import com.aryan.reader.shared.reader.sharedEpubOpenTraceElapsedMs
import com.aryan.reader.shared.reader.sharedEpubOpenTraceMark
import com.aryan.reader.shared.reader.sharedEpubOpenTraceMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date

private object AndroidSharedMobileContext {
    var applicationContext: Context? = null
}

internal fun sharedAndroidMobileApplicationContext(): Context? =
    AndroidSharedMobileContext.applicationContext

fun registerSharedAndroidMobileApplicationContext(context: Context) {
    AndroidSharedMobileContext.applicationContext = context.applicationContext
}

@Composable
private fun rememberAndroidSharedMobileContext(): Context {
    val context = LocalContext.current
    AndroidSharedMobileContext.applicationContext = context.applicationContext
    return context
}

internal actual fun formatSharedMobileDateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

internal actual fun formatSharedMobileClockTime(epochMillis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))

internal actual fun formatSharedMobileBookInfoDateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(epochMillis))

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

// Android benchmark: side padding stays exactly 16.dp, no corner allowance.
internal actual val sharedMobileEpubPageInfoCornerClearance: Dp = 0.dp

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

internal actual fun openSharedMobileExternalUrl(url: String): Boolean = openAndroidUrl(url)

private fun openAndroidUrl(url: String): Boolean {
    val context = AndroidSharedMobileContext.applicationContext ?: return false
    val normalized = normalizeReaderHref(url)
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
}

internal actual suspend fun searchSharedMobilePdf(
    book: BookItem,
    query: String,
    password: String?,
): List<SharedPdfSearchResult> {
    val context = AndroidSharedMobileContext.applicationContext ?: return emptyList()
    if (query.isBlank()) return emptyList()
    return runCatching {
        withContext(Dispatchers.IO) {
            AndroidSharedPdfiumRuntime.mutex.withLock {
                context.openSharedPdfDescriptor(book).use { pfd ->
                    AndroidSharedPdfiumRuntime.core.newDocument(pfd, password).use { document ->
                        val pageCount = document.getPageCount()
                        val index = SharedPdfSearchIndex(pageCount)
                        for (pageIndex in 0 until pageCount) {
                            currentCoroutineContext().ensureActive()
                            val text = document.openPage(pageIndex)?.use { page ->
                                page.openTextPage().use { textPage ->
                                    val count = textPage.textPageCountChars()
                                    if (count > 0) textPage.textPageGetText(0, count).orEmpty() else ""
                                }
                            }.orEmpty()
                            index.putPage(pageIndex, text.trimEnd('\u0000'))
                        }
                        index.search(query)
                    }
                }
            }
        }
    }.getOrDefault(emptyList())
}

internal actual suspend fun loadSharedMobilePdfOutline(
    book: BookItem,
    password: String?,
): List<PdfTocEntry> {
    val context = AndroidSharedMobileContext.applicationContext ?: return emptyList()
    return runCatching {
        withContext(Dispatchers.IO) {
            AndroidSharedPdfiumRuntime.mutex.withLock {
                context.openSharedPdfDescriptor(book).use { pfd ->
                    AndroidSharedPdfiumRuntime.core.newDocument(pfd, password).use { document ->
                        fun flatten(
                            bookmarks: List<io.legere.pdfiumandroid.api.Bookmark>,
                            level: Int,
                            destination: MutableList<PdfTocEntry>,
                        ) {
                            bookmarks.forEach { bookmark ->
                                destination += PdfTocEntry(
                                    title = bookmark.title ?: "Untitled Chapter",
                                    pageIndex = bookmark.pageIdx.toInt(),
                                    nestLevel = level,
                                )
                                flatten(bookmark.children, level + 1, destination)
                            }
                        }
                        buildList { flatten(document.getAndroidCompatiblePdfTableOfContents(), 0, this) }
                    }
                }
            }
        }
    }.getOrDefault(emptyList())
}

/**
 * Mirrors Android's production workaround for pdfiumandroid's depth-state leak,
 * which can truncate bookmark siblings. Reflection is intentionally isolated here
 * and falls back to the library traversal if its internals change.
 */
suspend fun PdfDocumentKt.getAndroidCompatiblePdfTableOfContents(): List<Bookmark> = runCatching {
    val documentField = PdfDocumentKt::class.java.getDeclaredField("document").apply { isAccessible = true }
    val documentWrapper = documentField.get(this) ?: return getTableOfContents()
    val nativeDocumentField = documentWrapper.javaClass.getDeclaredField("nativeDocument").apply {
        isAccessible = true
    }
    val nativeDocument = nativeDocumentField.get(documentWrapper) ?: return getTableOfContents()
    val pointerField = documentWrapper.javaClass.getDeclaredField("mNativeDocPtr").apply {
        isAccessible = true
    }
    val documentPointer = pointerField.get(documentWrapper) as Long
    val longType = Long::class.javaPrimitiveType!!
    val nativeClass = nativeDocument.javaClass
    val titleMethod = nativeClass.getMethod("getBookmarkTitle", longType)
    val destinationMethod = nativeClass.getMethod("getBookmarkDestIndex", longType, longType)
    val firstChildMethod = nativeClass.getMethod("getFirstChildBookmark", longType, longType)
    val siblingMethod = nativeClass.getMethod("getSiblingBookmark", longType, longType)
    val visited = mutableSetOf<Long>()

    fun walk(destination: MutableList<Bookmark>, startPointer: Long, level: Int) {
        var currentPointer = startPointer
        while (currentPointer != 0L && visited.add(currentPointer)) {
            val bookmark = Bookmark().apply {
                mNativePtr = currentPointer
                title = titleMethod.invoke(nativeDocument, currentPointer) as? String ?: "Untitled"
                pageIdx = destinationMethod.invoke(nativeDocument, documentPointer, currentPointer) as Long
            }
            destination += bookmark
            val firstChild = firstChildMethod.invoke(
                nativeDocument,
                documentPointer,
                currentPointer,
            ) as Long
            if (firstChild != 0L && level < AndroidSharedPdfMaxOutlineDepth) {
                walk(bookmark.children, firstChild, level + 1)
            }
            currentPointer = siblingMethod.invoke(
                nativeDocument,
                documentPointer,
                currentPointer,
            ) as Long
        }
    }

    val result = mutableListOf<Bookmark>()
    val firstRoot = firstChildMethod.invoke(nativeDocument, documentPointer, 0L) as Long
    if (firstRoot != 0L) walk(result, firstRoot, 0)
    result.ifEmpty { getTableOfContents() }
}.getOrElse { getTableOfContents() }

private const val AndroidSharedPdfMaxOutlineDepth = 128
