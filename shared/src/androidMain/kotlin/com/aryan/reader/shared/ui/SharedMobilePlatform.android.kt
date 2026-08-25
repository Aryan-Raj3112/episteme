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
import com.aryan.reader.shared.pdf.PdfTextPageSession
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.pdf.SharedPdfSearchIndex
import com.aryan.reader.shared.reader.SharedJvmBookLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.text.DateFormat
import java.util.Date

private object AndroidSharedMobileContext {
    var applicationContext: Context? = null
}

internal fun sharedAndroidMobileApplicationContext(): Context? =
    AndroidSharedMobileContext.applicationContext

internal fun registerSharedAndroidMobileApplicationContext(context: Context) {
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
    onBridgeMessage: (method: String, payload: String) -> Unit,
    positionController: SharedMobileEpubWebViewController?,
    streamPageLoader: SharedMobileEpubStreamPageLoader?,
    streamPageUnavailableLabel: String,
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
        ) },
        onRelease = coordinator::release,
    )
}

private class AndroidEpubBridge(
    private val coordinator: AndroidEpubWebViewCoordinator,
) {
    @JavascriptInterface
    fun callNative(method: String, payload: String) = coordinator.handleBridgeMessage(method, payload)
}

private class AndroidEpubWebViewCoordinator(
    var onBridgeMessage: (String, String) -> Unit,
) {
    private var activeWebView: WebView? = null
    private var contentChunks: List<String> = emptyList()
    private var loadedHtmlHash: Int? = null
    private var loadedHtmlLength = -1
    private var appliedAppearanceHash: Int? = null
    private var appliedNavigationRequestId = Long.MIN_VALUE
    private var latestAppearanceScript = ""
    private var latestNavigationScript: String? = null
    private var latestNavigationRequestId = Long.MIN_VALUE

    fun createWebView(context: Context): WebView = WebView(context).apply {
        activeWebView = this
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        addJavascriptInterface(AndroidEpubBridge(this@AndroidEpubWebViewCoordinator), AndroidEpubBridgeName)
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(AndroidEpubBridgeBootstrapScript, null)
                latestAppearanceScript.takeIf { it.isNotBlank() }?.let {
                    view.evaluateJavascript(it, null)
                    appliedAppearanceHash = it.hashCode()
                }
                latestNavigationScript?.let {
                    view.evaluateJavascript(it, null)
                    appliedNavigationRequestId = latestNavigationRequestId
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme.orEmpty().lowercase()
                return if (scheme == "http" || scheme == "https" || scheme == "mailto") {
                    openSharedMobileEpubExternalLink(request.url.toString())
                    true
                } else {
                    false
                }
            }
        }
    }

    fun update(
        webView: WebView,
        html: String,
        contentChunks: List<String>,
        appearanceScript: String,
        navigationScript: String?,
        navigationRequestId: Long,
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
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            return
        }
        val appearanceHash = appearanceScript.hashCode()
        if (appliedAppearanceHash != appearanceHash) {
            appliedAppearanceHash = appearanceHash
            webView.evaluateJavascript(appearanceScript, null)
        }
        if (navigationScript != null && appliedNavigationRequestId != navigationRequestId) {
            appliedNavigationRequestId = navigationRequestId
            webView.evaluateJavascript(navigationScript, null)
        }
    }

    fun handleBridgeMessage(method: String, payload: String) {
        if (method == "readerChunkRequested") {
            val index = AndroidEpubChunkIndexRegex.find(payload)
                ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return
            val chunk = contentChunks.getOrNull(index) ?: return
            activeWebView?.post {
                activeWebView?.evaluateJavascript(
                    "window.readerVirtualization && window.readerVirtualization.provideChunk($index, ${JsonPrimitive(chunk)});",
                    null,
                )
            }
            return
        }
        onBridgeMessage(method, payload)
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
        webView.stopLoading()
        webView.removeJavascriptInterface(AndroidEpubBridgeName)
        webView.webViewClient = WebViewClient()
        webView.destroy()
        activeWebView = null
        contentChunks = emptyList()
    }
}

private const val AndroidEpubBridgeName = "ReaderBridge"
private val AndroidEpubChunkIndexRegex = Regex("\\\"index\\\"\\s*:\\s*(\\d+)")
private val AndroidEpubBridgeBootstrapScript = """
    (function () {
      window.kmpJsBridge = {
        callNative: function (method, payload) {
          try { window.$AndroidEpubBridgeName.callNative(String(method || ''), String(payload || '{}')); } catch (_) {}
        }
      };
      window.readerDisableLinkFallback = true;
    })();
""".trimIndent()

internal actual fun openSharedMobileEpubExternalLink(url: String): Boolean = openAndroidUrl(url)

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
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
