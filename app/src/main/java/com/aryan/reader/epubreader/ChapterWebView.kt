/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
// ChapterWebView.kt
package com.aryan.reader.epubreader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.core.net.toUri
import com.aryan.reader.R
import com.aryan.reader.getReaderTextureDataUri
import com.aryan.reader.shared.ui.SharedSelectionMenuRect
import com.aryan.reader.shared.ui.SharedSelectionMenuSize
import com.aryan.reader.shared.ui.SharedSelectionMenuViewport
import com.aryan.reader.shared.ui.sharedSelectionMenuPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

private const val TAG_LINK_NAV = "LINK_NAV"
private const val TAG_VERTICAL_JITTER = "EpubVerticalJitter"
private const val TAG_ANDROID_HIGHLIGHT_RENDER_DIAG = "AndroidHighlightRenderDiag"
private val READER_WEB_VIEW_JS_INTERFACES = arrayOf(
    "PageInfoReporter",
    "ProgressReporter",
    "ContentBridge",
    "HighlightBridge",
    "AutoScrollBridge",
    "CfiBridge",
    "SnippetBridge",
    "TtsBridge",
    "AiBridge",
    "FootnoteBridge",
    "LinkNavBridge"
)

private class WebViewRuntimeApplierState {
    var fontCss: String? = null
    var styleSignature: String? = null
    var tocFragmentsJson: String? = null
    var highlightsJson: String? = null
    private var unchangedUpdateCount = 0
    private var pendingUpdateCount = 0
    private var lastUnchangedUpdateLogAt = 0L
    private var lastPendingUpdateLogAt = 0L

    fun logApplied(
        chapterTitle: String,
        fontCssChanged: Boolean,
        styleChanged: Boolean,
        tocFragmentsChanged: Boolean,
        highlightsChanged: Boolean
    ) {
        if (unchangedUpdateCount > 0) {
            Timber.tag(TAG_VERTICAL_JITTER).d(
                "androidUpdate resumeAfterUnchanged count=$unchangedUpdateCount chapter='$chapterTitle'"
            )
            unchangedUpdateCount = 0
        }
        if (pendingUpdateCount > 0) {
            Timber.tag(TAG_VERTICAL_JITTER).d(
                "androidUpdate resumeAfterPending count=$pendingUpdateCount chapter='$chapterTitle'"
            )
            pendingUpdateCount = 0
        }
        lastUnchangedUpdateLogAt = System.currentTimeMillis()
        lastPendingUpdateLogAt = lastUnchangedUpdateLogAt
        Timber.tag(TAG_VERTICAL_JITTER).d(
            "androidUpdate applied chapter='$chapterTitle' fontCss=$fontCssChanged " +
                "style=$styleChanged toc=$tocFragmentsChanged highlights=$highlightsChanged"
        )
    }

    fun logUnchanged(chapterTitle: String) {
        unchangedUpdateCount++
        val now = System.currentTimeMillis()
        if (now - lastUnchangedUpdateLogAt >= 1000L) {
            Timber.tag(TAG_VERTICAL_JITTER).d(
                "androidUpdate unchanged count=$unchangedUpdateCount chapter='$chapterTitle'"
            )
            unchangedUpdateCount = 0
            lastUnchangedUpdateLogAt = now
        }
    }

    fun logPending(chapterTitle: String) {
        pendingUpdateCount++
        val now = System.currentTimeMillis()
        if (now - lastPendingUpdateLogAt >= 1000L) {
            Timber.tag(TAG_VERTICAL_JITTER).d(
                "androidUpdate pendingPageLoad count=$pendingUpdateCount chapter='$chapterTitle'"
            )
            pendingUpdateCount = 0
            lastPendingUpdateLogAt = now
        }
    }
}

private fun WebView.releaseReaderResources() {
    try {
        stopLoading()
        READER_WEB_VIEW_JS_INTERFACES.forEach { removeJavascriptInterface(it) }
        webChromeClient = null
        webViewClient = WebViewClient()
        loadDataWithBaseURL(null, "", "text/html", "UTF-8", null)
        clearHistory()
        removeAllViews()
        destroy()
    } catch (e: Exception) {
        Timber.w(e, "Failed to fully release EPUB WebView resources")
    }
}

private fun getFontCssInjection(): String {
    return """
        @font-face { font-family: 'Merriweather'; src: url('file:///android_asset/fonts/merriweather.ttf'); }
        @font-face { font-family: 'Lato'; src: url('file:///android_asset/fonts/lato.ttf'); }
        @font-face { font-family: 'Lora'; src: url('file:///android_asset/fonts/lora.ttf'); }
        @font-face { font-family: 'Roboto Mono'; src: url('file:///android_asset/fonts/roboto_mono.ttf'); }
        @font-face { font-family: 'Lexend'; src: url('file:///android_asset/fonts/lexend.ttf'); }
    """.trimIndent()
}

private fun getJsToInject(context: Context): String {
    return try {
        context.assets.open("epub_reader.js").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Error reading epub_reader.js from assets")
        "" // Return empty string on error
    }
}

@Suppress("unused")
class AutoScrollJsBridge(
    private val callback: () -> Unit
) {
    @JavascriptInterface
    fun onChapterEnd() {
        Timber.d("Bridge: onChapterEnd called from JavaScript. Invoking callback.")
        callback()
    }
}

@Suppress("unused")
class TtsJsBridge(
    private val scope: CoroutineScope,
    private val ttsStructuredTextHandler: suspend (String) -> Unit
) {
    @JavascriptInterface
    fun onStructuredTextExtracted(json: String) {
        Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("JS Bridge received JSON. Length: ${json.length}")
        if (json.isNotBlank() && json != "[]") {
            scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                ttsStructuredTextHandler(json)
            }
        } else {
            Timber.tag("TTS_CHAPTER_CHANGE_DIAG").w("JS Bridge received empty or blank JSON. This may trigger a chapter skip.")
            scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                ttsStructuredTextHandler("[]")
            }
        }
    }
}

@Suppress("unused")
class HighlightJsBridge(
    private val onCreateCallback: (String, String, String) -> Unit, // Renamed to avoid recursion
    private val onClickCallback: ((String, String, Int, Int, Int, Int) -> Unit)? = null // Renamed
) {
    @JavascriptInterface
    fun onHighlightCreated(cfi: String, text: String, colorId: String) {
        onCreateCallback(cfi, text, colorId) // Calls the lambda property
    }

    @JavascriptInterface
    fun onHighlightClicked(
        cfi: String,
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        onClickCallback?.invoke(cfi, text, left, top, right, bottom)
    }
}

@Suppress("unused")
class ContentBridge(
    private val onChunkRequested: (index: Int) -> Unit
) {
    @JavascriptInterface
    fun requestChunk(index: Int) {
        onChunkRequested(index)
    }
}

@Suppress("unused")
class CfiJsBridge(
    private val onCfiReady: (String) -> Unit,
    private val onCfiForBookmarkReady: (String) -> Unit,
    private val onScrollFinishedCallback: (Boolean) -> Unit
) {
    @JavascriptInterface
    fun onCfiExtracted(jsonResponse: String) {
        try {
            // --- ADDED LOG ---
            Timber.tag("PosSaveDiag")
                .d("CfiJsBridge.onCfiExtracted: Raw JSON received from JS: $jsonResponse")

            val json = JSONObject(jsonResponse)
            val cfi = json.optString("cfi", "/4")
            val logArray = json.optJSONArray("log")

            Timber.d("--- Start CFI Save Diagnostics ---")
            Timber.d("Received CFI for saving: $cfi")
            if (logArray != null) {
                for (i in 0 until logArray.length()) {
                    Timber.d(logArray.getString(i))
                }
            } else {
                Timber.d("No log array received. Raw response: $jsonResponse")
            }
            Timber.d("--- End CFI Save Diagnostics ---")

            if (cfi.isNotBlank()) {
                onCfiReady(cfi)
            }
        } catch (e: Exception) {
            Timber.tag("PosSaveDiag")
                .e(e, "CfiJsBridge.onCfiExtracted: Error parsing CFI JSON response: $jsonResponse")
            onCfiReady("/4")
        }
    }

    @JavascriptInterface
    fun onCfiForBookmarkExtracted(jsonResponse: String) {
        try {
            val json = JSONObject(jsonResponse)
            val cfi = json.optString("cfi")
            val logArray = json.optJSONArray("log")

            Timber.d("--- Start CFI Diagnostics (Bookmark) ---")
            Timber.d("Received CFI for bookmark: $cfi")
            if (logArray != null) {
                for (i in 0 until logArray.length()) {
                    Timber.d(logArray.getString(i))
                }
            } else {
                Timber.d("No log array received. Raw response: $jsonResponse")
            }
            Timber.d("--- End CFI Diagnostics (Bookmark) ---")

            if (cfi != null) {
                onCfiForBookmarkReady(cfi)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing CFI JSON for bookmark: $jsonResponse")
        }
    }

    @JavascriptInterface
    fun onScrollFinished(success: Boolean) {
        Timber.tag("BookmarkDiagnosis").d("JS reported scroll finished. Success: $success")
        onScrollFinishedCallback(success)
    }
}

@Suppress("unused")
class SnippetJsBridge(
    private val onSnippetReady: (String, String) -> Unit
) {
    @JavascriptInterface
    fun onSnippetExtracted(cfi: String, snippet: String) {
        Timber.d("SnippetJsBridge.onSnippetExtracted received. CFI: '$cfi', Snippet: '$snippet'")
        onSnippetReady(cfi, snippet)
    }
}

@Suppress("unused")
class ProgressJsBridge(
    private val onTopChunkUpdated: (Int) -> Unit
) {
    private var lastReportedChunk = -1

    @JavascriptInterface
    fun updateTopChunk(chunkIndex: Int) {
        if (chunkIndex != lastReportedChunk) {
            lastReportedChunk = chunkIndex
            onTopChunkUpdated(chunkIndex)
        }
    }
}

private data class CustomMenuState(
    val selectedText: String,
    val selectionBounds: Rect,
    val finishActionModeCallback: () -> Unit,
    val cfi: String? = null,
    val isExistingHighlight: Boolean = false,
    val note: String? = null,
    val selectedColor: HighlightColor? = null
)

internal fun highlightsJsonForWebView(userHighlights: List<UserHighlight>): String {
    val jsonArray = org.json.JSONArray()
    userHighlights.forEach { highlight ->
        val obj = JSONObject()
        obj.put("id", highlight.id)
        obj.put("cfi", highlight.cfi)
        obj.put("text", highlight.text)
        obj.put("cssClass", highlight.color.cssClass)
        obj.put("colorId", highlight.color.id)
        obj.put("chapterIndex", highlight.chapterIndex)
        obj.put(
            "locator",
            JSONObject().apply {
                highlight.locator.chapterIndex?.let { put("chapterIndex", it) }
                highlight.locator.chapterId?.let { put("chapterId", it) }
                highlight.locator.href?.let { put("href", it) }
                highlight.locator.pageIndex?.let { put("pageIndex", it) }
                highlight.locator.startOffset?.let { put("startOffset", it) }
                highlight.locator.endOffset?.let { put("endOffset", it) }
                highlight.locator.blockIndex?.let { put("blockIndex", it) }
                highlight.locator.charOffset?.let { put("charOffset", it) }
                highlight.locator.textQuote?.let { put("textQuote", it) }
                highlight.locator.cfi?.let { put("cfi", it) }
            }
        )
        jsonArray.put(obj)
    }
    return jsonArray.toString()
}

@Suppress("unused")
class AiJsBridge(
    private val scope: CoroutineScope, private val onContentReady: suspend (String) -> Unit
) {
    @JavascriptInterface
    fun onContentExtractedForSummarization(text: String) {
        Timber.d("Content extracted for summarization, length: ${text.length}")
        if (text.isNotBlank()) {
            scope.launch {
                onContentReady(text)
            }
        }
    }
}

@Suppress("unused")
class FootnoteJsBridge(
    private val onFootnoteRequestCallback: (String) -> Unit
) {
    @JavascriptInterface
    fun onFootnoteRequested(htmlContent: String) {
        Timber.tag("FootnoteDiag").d("Kotlin Bridge received footnote content. Length: ${htmlContent.length}")
        onFootnoteRequestCallback(htmlContent)
    }
}

@Suppress("unused")
class LinkNavJsBridge(
    private val currentChapterTitle: String,
    private val onInternalLinkClick: (String) -> Unit
) {
    @JavascriptInterface
    fun onLinkClicked(href: String, epubType: String, linkText: String) {
        Timber.tag(TAG_LINK_NAV)
            .d("[JS-CLICK] href='$href', epub:type='$epubType', label='$linkText' | currentChapter='$currentChapterTitle'")
        onInternalLinkClick(href)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChapterWebView(
    key: Any,
    initialHtmlContent: String,
    baseUrl: String,
    totalChunks: Int,
    userHighlights: List<UserHighlight>,
    onHighlightCreated: (String, String, String) -> Unit,
    onHighlightDeleted: (String) -> Unit,
    onChunkRequested: (Int) -> Unit,
    chapterTitle: String,
    isDarkTheme: Boolean,
    effectiveBg: androidx.compose.ui.graphics.Color,
    effectiveText: androidx.compose.ui.graphics.Color,
    initialScrollTarget: ChapterScrollPosition?,
    initialPageScrollY: Int?,
    initialCfi: String?,
    initialChunkIndex: Int,
    onTopChunkUpdated: (Int) -> Unit,
    currentFontSize: Float,
    currentLineHeight: Float,
    currentParagraphGap: Float,
    currentImageSize: Float,
    currentHorizontalMargin: Float,
    currentVerticalMargin: Float,
    onChapterInitiallyScrolled: () -> Unit,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onPotentialScroll: () -> Unit,
    onOverScrollTop: (dragAmount: Float) -> Unit,
    onOverScrollBottom: (dragAmount: Float) -> Unit,
    onReleaseOverScrollTop: () -> Unit,
    onReleaseOverScrollBottom: () -> Unit,
    onScrollStateUpdate: (scrollY: Int, scrollHeight: Int, clientHeight: Int, activeFragmentId: String?) -> Unit,
    onWebViewInstanceCreated: (WebView) -> Unit,
    onCfiGenerated: (cfi: String) -> Unit,
    onBookmarkCfiGenerated: (cfi: String) -> Unit,
    onSnippetForBookmarkReady: (cfi: String, snippet: String) -> Unit,
    onScrollFinished: (Boolean) -> Unit = {},
    ttsScope: CoroutineScope,
    tocFragments: List<String>,
    initialFragmentId: String? = null,
    initialImageSource: String? = null,
    initialImageOriginalSource: String? = null,
    initialImageOrdinal: Int = 0,
    onTtsTextReady: suspend (String) -> Unit,
    isProUser: Boolean,
    isOss: Boolean = false,
    onShowDictionaryUpsellDialog: () -> Unit,
    onWordSelectedForAiDefinition: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onNoteRequested: (String?) -> Unit,
    onContentReadyForSummarization: suspend (String) -> Unit,
    onFootnoteRequested: (String) -> Unit,
    currentFontFamily: ReaderFont,
    customFontPath: String? = null,
    currentTextAlign: ReaderTextAlign,
    onHighlightClicked: () -> Unit,
    onAutoScrollChapterEnd: () -> Unit = {},
    activeHighlightPalette: List<HighlightColor>,
    onUpdatePalette: (Int, HighlightColor) -> Unit,
    onInternalLinkClick: (String) -> Unit,
    onWebViewDisposed: (WebView) -> Unit = {},
    activeTextureId: String? = null,
    activeTextureAlpha: Float = 0.55f
) {
    Timber.d(
        "RenderChapterViaWebView for '$chapterTitle', Key: $key, isDarkTheme: $isDarkTheme, initialScrollTarget: $initialScrollTarget"
    )

    var showExternalLinkDialog by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val density = LocalDensity.current
    var localWebViewRef by remember { mutableStateOf<WebView?>(null) }

    var customMenuState by remember { mutableStateOf<CustomMenuState?>(null) }

    val jsToInject = remember(context) { getJsToInject(context) }

    var showPaletteManager by remember { mutableStateOf(false) }
    val latestUserHighlights by rememberUpdatedState(userHighlights)

    val textureBase64 by remember(activeTextureId) {
        mutableStateOf(
            getReaderTextureDataUri(context, activeTextureId)
        )
    }

    val currentOnSnippetForBookmarkReady by rememberUpdatedState(onSnippetForBookmarkReady)
    val currentOnCfiGenerated by rememberUpdatedState(onCfiGenerated)
    val currentOnBookmarkCfiGenerated by rememberUpdatedState(onBookmarkCfiGenerated)
    val currentOnScrollFinished by rememberUpdatedState(onScrollFinished)

    LaunchedEffect(currentFontSize, currentLineHeight) {
        localWebViewRef?.evaluateJavascript(
            "javascript:if(window.getSelection) window.getSelection().removeAllRanges();", null
        )
    }

    val highlightsJson = remember(userHighlights) { highlightsJsonForWebView(userHighlights) }

    if (showExternalLinkDialog != null) {
        val urlToShow = showExternalLinkDialog!!
        AlertDialog(
            onDismissRequest = { showExternalLinkDialog = null },
            title = { Text(stringResource(R.string.dialog_external_link_title)) },
            text = { Text(stringResource(R.string.dialog_external_link_desc, urlToShow)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, urlToShow.toUri())
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Timber.e(e, "No activity found to handle intent for URL: $urlToShow")
                            Toast.makeText(context, context.getString(R.string.error_no_browser), Toast.LENGTH_LONG).show()
                        }
                        showExternalLinkDialog = null
                    }) { Text(stringResource(R.string.action_open)) }
                    TextButton(onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(context.getString(R.string.clip_label_copied_link), urlToShow)
                        clipboard.setPrimaryClip(clip)
                        showExternalLinkDialog = null
                    }) { Text(stringResource(R.string.action_copy)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExternalLinkDialog = null }) { Text(stringResource(R.string.action_cancel)) }
            })
    }

    Box(modifier = modifier.fillMaxSize()) {

        LaunchedEffect(isDarkTheme, effectiveBg, effectiveText, textureBase64, activeTextureAlpha) {
            val bgHex = String.format("#%06X", (0xFFFFFF and effectiveBg.toArgb()))
            val textHex = String.format("#%06X", (0xFFFFFF and effectiveText.toArgb()))
            localWebViewRef?.evaluateJavascript("javascript:window.applyReaderTheme($isDarkTheme, '$bgHex', '$textHex', ${textureBase64?.let { "'$it'" } ?: "null"}, ${activeTextureAlpha.coerceIn(0f, 1f)});", null)
        }

        key(
            key,
            currentFontSize,
            currentLineHeight,
            currentParagraphGap,
            currentImageSize,
            currentHorizontalMargin,
            currentVerticalMargin,
            currentFontFamily,
            currentTextAlign
        ) {
            val runtimeApplierState = remember { WebViewRuntimeApplierState() }
            var isReaderRuntimeReady by remember { mutableStateOf(false) }

            AndroidView(
                factory = { ctx ->
                Timber.d(
                    "InteractiveWebView factory for $chapterTitle (Key: $key), isDarkTheme: $isDarkTheme, initialScroll: $initialScrollTarget"
                )
                @Suppress("unused") val webView = InteractiveWebView(
                    context = ctx,
                    onSingleTap = onTap,
                    onPotentialScroll = onPotentialScroll,
                    onOverScrollTop = onOverScrollTop,
                    onOverScrollBottom = onOverScrollBottom,
                    onReleaseOverScrollTop = onReleaseOverScrollTop,
                    onReleaseOverScrollBottom = onReleaseOverScrollBottom,
                    onShowCustomSelectionMenu = { text, bounds, finishCallback ->
                        if (text.isNotBlank() && !bounds.isEmpty) {
                            customMenuState = CustomMenuState(
                                selectedText = text,
                                selectionBounds = Rect(bounds),
                                finishActionModeCallback = finishCallback,
                                isExistingHighlight = false
                            )
                        } else {
                            customMenuState = null
                            finishCallback()
                        }
                    },
                    onHideCustomSelectionMenu = {
                        if (customMenuState?.isExistingHighlight != true) {
                            customMenuState = null
                        }
                    }).apply {
                    localWebViewRef = this
                    onWebViewInstanceCreated(this)
                    val debugWebViewId = System.identityHashCode(this).toString(16)
                    addJavascriptInterface(
                        PageInfoBridge { scrollY, scrollHeight, clientHeight, activeFragmentId ->
                            this.post { onScrollStateUpdate(scrollY, scrollHeight, clientHeight, activeFragmentId) }
                        }, "PageInfoReporter"
                    )
                    addJavascriptInterface(
                        ProgressJsBridge { chunkIndex ->
                            this.post { onTopChunkUpdated(chunkIndex) }
                        }, "ProgressReporter"
                    )
                    addJavascriptInterface(ContentBridge { index ->
                        this.post { onChunkRequested(index) }
                    }, "ContentBridge")

                    addJavascriptInterface(
                        HighlightJsBridge(
                            onCreateCallback = { cfi, text, colorId ->
                                this.post { onHighlightCreated(cfi, text, colorId) }
                            },
                            onClickCallback = { cfi, text, left, top, right, bottom ->
                                this.post {
                                    onHighlightClicked()
                                    localWebViewRef?.evaluateJavascript(
                                        "javascript:if(window.getSelection) window.getSelection().removeAllRanges();",
                                        null
                                    )
                                    onNoteRequested(cfi)
                                }
                            }
                        ), "HighlightBridge"
                    )

                    addJavascriptInterface(
                        AutoScrollJsBridge {
                            this.post { onAutoScrollChapterEnd() }
                        }, "AutoScrollBridge"
                    )

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            consoleMessage?.let {
                                val message = it.message()
                                when {
                                    message.startsWith("LINK_NAV:") -> {
                                        Timber.tag(TAG_LINK_NAV)
                                            .d("JS -> ${message.substringAfter("LINK_NAV: ")}")
                                    }

                                    message.startsWith("FootnoteDiag:") -> {
                                        Timber.tag("FootnoteDiag")
                                            .d("JS -> ${message.substringAfter("FootnoteDiag: ")}")
                                    }

                                    message.startsWith("TTS_CHAPTER_CHANGE_DIAG:") -> {
                                        Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("JS -> ${message.substringAfter("TTS_CHAPTER_CHANGE_DIAG: ")}")
                                    }

                                    message.startsWith("BookmarkDiagnosis") -> {
                                        Timber.tag("BookmarkDiagnosis")
                                            .d("JS -> ${message.substringAfter("BookmarkDiagnosis: ")}")
                                    }

                                    message.startsWith("TTS_LIST_DIAG:") -> {
                                        Timber.tag("TTS_LIST_DIAG").d("JS -> ${message.substringAfter("TTS_LIST_DIAG: ")}")
                                    }

                                    message.startsWith("CFI_DIAGNOSIS:") -> {
                                        Timber.d(
                                            "JS -> ${message.substringAfter("CFI_DIAGNOSIS: ")}"
                                        )
                                    }

                                    message.startsWith("ImageDiagnosis") -> {
                                        Timber.d("JS -> $message")
                                    }

                                    message.startsWith("TTS_HIGHLIGHT_DIAGNOSIS:") -> {
                                        Timber.d(
                                            "JS -> ${message.substringAfter("TTS_HIGHLIGHT_DIAGNOSIS: ")}"
                                        )
                                    }

                                    message.startsWith("PosSaveDiag:") -> {
                                        Timber.tag("PosSaveDiag")
                                            .d("JS -> ${message.substringAfter("PosSaveDiag: ")}")
                                    }

                                    message.startsWith("HIGHLIGHT_DEBUG:") -> {
                                        Timber.d(
                                            "JS -> ${message.substringAfter("HIGHLIGHT_DEBUG: ")}"
                                        )
                                    }

                                    message.startsWith("$TAG_ANDROID_HIGHLIGHT_RENDER_DIAG:") -> {
                                        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG)
                                            .d("JS -> ${message.substringAfter("$TAG_ANDROID_HIGHLIGHT_RENDER_DIAG: ")}")
                                    }

                                    message.startsWith("ReaderFontDiagnosis") -> {
                                        Timber.d(
                                            "JS -> ${message.substringAfter("ReaderFontDiagnosis: ")}"
                                        )
                                    }

                                    message.startsWith("NavDiag:") -> {
                                        Timber.tag("NavDiag")
                                            .d("JS -> ${message.substringAfter("NavDiag: ")}")
                                    }

                                    message.startsWith("AutoScrollDiagnosis") -> {
                                        Timber.d(
                                            "JS -> ${message.substringAfter("AutoScrollDiagnosis: ")}"
                                        )
                                    }

                                    message.startsWith("FRAG_NAV_DEBUG") -> {
                                        Timber.tag("FRAG_NAV_DEBUG")
                                            .d("JS -> ${message.substringAfter("FRAG_NAV_DEBUG: ")}")
                                    }

                                    message.startsWith("$TAG_VERTICAL_JITTER:") -> {
                                        Timber.tag(TAG_VERTICAL_JITTER)
                                            .d("webView=$debugWebViewId chapter='$chapterTitle' JS -> ${message.substringAfter("$TAG_VERTICAL_JITTER: ")}")
                                    }

                                    else -> {
                                        Timber.d(
                                            "[${it.sourceId()}:${it.lineNumber()}] ${it.message()}"
                                        )
                                    }
                                }
                            }
                            return true
                        }
                    }
                    addJavascriptInterface(
                        CfiJsBridge(
                            onCfiReady = { cfi -> this.post { currentOnCfiGenerated(cfi) } },
                            onCfiForBookmarkReady = { cfi -> this.post { currentOnBookmarkCfiGenerated(cfi) } },
                            onScrollFinishedCallback = { success ->
                                this.post { currentOnScrollFinished(success) }
                            }
                        ), "CfiBridge"
                    )

                    addJavascriptInterface(
                        SnippetJsBridge { cfi, snippet ->
                            this.post { currentOnSnippetForBookmarkReady(cfi, snippet) }
                        }, "SnippetBridge"
                    )
                    addJavascriptInterface(TtsJsBridge(ttsScope, onTtsTextReady), "TtsBridge")
                    addJavascriptInterface(
                        AiJsBridge(ttsScope, onContentReadyForSummarization), "AiBridge"
                    )

                    addJavascriptInterface(
                        FootnoteJsBridge { html ->
                            this.post { onFootnoteRequested(html) }
                        }, "FootnoteBridge"
                    )

                    addJavascriptInterface(
                        LinkNavJsBridge(chapterTitle) { href ->
                            this.post { onInternalLinkClick(href) }
                        }, "LinkNavBridge"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString()
                            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                                Timber.tag(TAG_LINK_NAV)
                                    .d("[EXTERNAL-INTERCEPT] url='$url' from chapter '$chapterTitle'")
                                showExternalLinkDialog = url
                                return true
                            }
                            if (url != null && url.startsWith("file://")) {
                                Timber.tag(TAG_LINK_NAV)
                                    .d("[INTERNAL-LINK-INTERCEPTED] url='$url' from chapter '$chapterTitle'")
                                onInternalLinkClick(url)
                                return true
                            }
                            if (url != null) {
                                Timber.tag(TAG_LINK_NAV)
                                    .d("[INTERNAL-LINK-PASSED] url='$url' from chapter '$chapterTitle' — allowing WebView to handle")
                            }
                            return false
                        }

                        override fun onLoadResource(view: WebView?, url: String?) {
                            super.onLoadResource(view, url)
                            if (url?.contains(".jpg", true) == true || url?.contains(
                                    ".jpeg",
                                    true
                                ) == true || url?.contains(
                                    ".png",
                                    true
                                ) == true || url?.contains(
                                    ".gif",
                                    true
                                ) == true || url?.contains(
                                    ".svg",
                                    true
                                ) == true || url?.contains("image", true) == true
                            ) {
                                Timber.d(
                                    "WebView is attempting to load resource: $url"
                                )
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Timber.d(
                                "onPageFinished. Injecting CSS and Font: ${currentFontFamily.fontFamilyName}"
                            )

                            view?.evaluateJavascript(jsToInject, null)

                            val bgHex = String.format("#%06X", (0xFFFFFF and effectiveBg.toArgb()))
                            val textHex =
                                String.format("#%06X", (0xFFFFFF and effectiveText.toArgb()))
                            view?.evaluateJavascript("javascript:window.applyReaderTheme($isDarkTheme, '$bgHex', '$textHex', ${textureBase64?.let { "'$it'" } ?: "null"}, ${activeTextureAlpha.coerceIn(0f, 1f)});",
                                null)

                            val fragmentsJson = org.json.JSONArray(tocFragments).toString()
                            Timber.tag("FRAG_NAV_DEBUG")
                                .d("onPageFinished: Re-injecting TOC_FRAGMENTS: $fragmentsJson")
                            view?.evaluateJavascript(
                                "javascript:window.TOC_FRAGMENTS = $fragmentsJson;",
                                null
                            )

                            view?.evaluateJavascript(
                                "javascript:setTimeout(window.auditTocFragments, 500);",
                                null
                            )

                            view?.evaluateJavascript(
                                """
                                    javascript:(function() {
                                        if (window.__readerInternalLinkBridgeInstalled) return;
                                        window.__readerInternalLinkBridgeInstalled = true;
                                        document.addEventListener('click', function(event) {
                                            var target = event.target;
                                            var anchor = target && target.closest ? target.closest('a[href]') : null;
                                            if (!anchor && target && target.parentElement && target.parentElement.closest) {
                                                anchor = target.parentElement.closest('a[href]');
                                            }
                                            if (!anchor) return;
                                            var rawHref = anchor.getAttribute('href') || '';
                                            if (!rawHref) return;
                                            if (/^(https?:|mailto:|tel:|javascript:)/i.test(rawHref)) return;
                                            if (/^\/\//.test(rawHref)) return;
                                            event.preventDefault();
                                            var resolvedHref = anchor.href || rawHref;
                                            if (window.LinkNavBridge && window.LinkNavBridge.onLinkClicked) {
                                                window.LinkNavBridge.onLinkClicked(
                                                    resolvedHref,
                                                    anchor.getAttribute('epub:type') || '',
                                                    anchor.textContent || ''
                                                );
                                            }
                                        }, true);
                                    })();
                                """.trimIndent(),
                                null
                            )

                            view?.evaluateJavascript(
                                "javascript:window.CURRENT_HIGHLIGHTS = '${
                                    escapeJsString(
                                        highlightsJson
                                    )
                                }'; window.HighlightBridgeHelper.restoreHighlights(window.CURRENT_HIGHLIGHTS);", null
                            )

                            val fontCss = getFontCssInjection().replace("\n", " ")
                            val customFontCss = if (customFontPath != null) {
                                "@font-face { font-family: 'CustomFont'; src: url('file://$customFontPath'); }"
                            } else ""
                            val combinedCss = "$fontCss $customFontCss"

                            val injectFontJs =
                                "var style = document.createElement('style'); style.id='injectedFonts'; style.innerHTML = \"$combinedCss\"; document.head.appendChild(style);"
                            view?.evaluateJavascript("javascript:$injectFontJs") {
                                Timber.d("CSS Injection result: $it")
                            }

                            val fontNameForJs = if (customFontPath != null) {
                                "CustomFont"
                            } else if (currentFontFamily == ReaderFont.ORIGINAL) {
                                ""
                            } else {
                                currentFontFamily.fontFamilyName
                            }

                            runtimeApplierState.fontCss = combinedCss
                            runtimeApplierState.styleSignature = listOf(
                                currentFontSize,
                                currentLineHeight,
                                fontNameForJs,
                                currentTextAlign.cssValue,
                                currentParagraphGap,
                                currentImageSize,
                                currentHorizontalMargin,
                                currentVerticalMargin
                            ).joinToString(separator = "|")
                            runtimeApplierState.tocFragmentsJson = fragmentsJson
                            runtimeApplierState.highlightsJson = highlightsJson

                            view?.evaluateJavascript(
                                "javascript:window.updateReaderStyles($currentFontSize, $currentLineHeight, '$fontNameForJs', '${currentTextAlign.cssValue}', $currentParagraphGap, $currentImageSize, $currentHorizontalMargin, $currentVerticalMargin);",
                                null
                            )

                            view?.evaluateJavascript(
                                "javascript:window.checkImagesForDiagnosis();", null
                            )

                            view?.evaluateJavascript(
                                "javascript:window.virtualization.init($initialChunkIndex, $totalChunks);",
                                null
                            )

                            @Suppress("VariableNeverRead") var scrollActionTaken = false

                            if (!initialCfi.isNullOrBlank()) {
                                val cfiJsCommand = "javascript:window.scrollToCfi('$initialCfi');"
                                Timber.tag("NavDiag").d("WebView onPageFinished: Triggering scroll to initialCfi: $initialCfi")
                                view?.evaluateJavascript(cfiJsCommand) {
                                    onChapterInitiallyScrolled()
                                    scrollActionTaken = true
                                }
                            } else if (!initialFragmentId.isNullOrBlank()) {
                                Timber.tag("NavDiag").d("WebView onPageFinished: Scrolling to Element ID: $initialFragmentId")
                                val js = """
                                    (function() {
                                        var targetId = '$initialFragmentId';
                                        var el = document.getElementById(targetId) || document.querySelector('[name="' + targetId + '"]');
                                        if (el) {
                                            var targetScrollY = window.scrollY + el.getBoundingClientRect().top - (window.VIEWPORT_PADDING_TOP + 10);
                                            window.scrollTo({ top: targetScrollY, behavior: 'auto' });
                                            return -2;
                                        }
                                        if (window.virtualization && window.virtualization.chunksData) {
                                            for (var i = 0; i < window.virtualization.chunksData.length; i++) {
                                                var chunkHtml = window.virtualization.chunksData[i];
                                                if (chunkHtml && (chunkHtml.indexOf('id="' + targetId + '"') !== -1 || chunkHtml.indexOf('name="' + targetId + '"') !== -1 || chunkHtml.indexOf("id='" + targetId + "'") !== -1 || chunkHtml.indexOf("name='" + targetId + "'") !== -1)) {
                                                    return i;
                                                }
                                            }
                                        }
                                        return -1;
                                    })()
                                """.trimIndent()

                                view?.evaluateJavascript(js) { result ->
                                    val chunkIdx = result?.toIntOrNull() ?: -1
                                    if (chunkIdx >= 0) {
                                        for (i in 0..chunkIdx) {
                                            onChunkRequested(i)
                                        }
                                        val scrollJs = """
                                            (function() {
                                                var chunkIndex = $chunkIdx;
                                                var fragmentId = '$initialFragmentId';
                                                setTimeout(function() {
                                                    var chunkDiv = document.querySelector('.chunk-container[data-chunk-index="' + chunkIndex + '"]');
                                                    if (chunkDiv && chunkDiv.innerHTML === "" && window.virtualization && window.virtualization.chunksData[chunkIndex]) {
                                                        chunkDiv.innerHTML = window.virtualization.chunksData[chunkIndex];
                                                        chunkDiv.style.height = "";
                                                    }
                                                    setTimeout(function() {
                                                        var el = document.getElementById(fragmentId) || document.querySelector('[name="' + fragmentId + '"]');
                                                        if (el) {
                                                            var targetScrollY = window.scrollY + el.getBoundingClientRect().top - (window.VIEWPORT_PADDING_TOP + 10);
                                                            window.scrollTo({ top: targetScrollY, behavior: 'auto' });
                                                        } else if (chunkDiv) {
                                                            var targetScrollY = window.scrollY + chunkDiv.getBoundingClientRect().top - window.VIEWPORT_PADDING_TOP;
                                                            window.scrollTo({ top: targetScrollY, behavior: 'auto' });
                                                        }
                                                    }, 50);
                                                }, 200);
                                            })()
                                        """.trimIndent()
                                        view.evaluateJavascript(scrollJs, null)
                                    }
                                }
                                onChapterInitiallyScrolled()
                                scrollActionTaken = true
                            } else if (!initialImageSource.isNullOrBlank()) {
                                val imageJsCommand =
                                    "javascript:window.scrollToReaderImageSource('${escapeJsString(initialImageSource)}', $initialImageOrdinal, '${escapeJsString(initialImageOriginalSource.orEmpty())}');"
                                Timber.tag("NavDiag").d("WebView onPageFinished: Scrolling to image source: $initialImageSource")
                                view?.evaluateJavascript(imageJsCommand) {
                                    onChapterInitiallyScrolled()
                                    scrollActionTaken = true
                                }
                            } else if (initialScrollTarget != null) {
                                val scrollJsCommand = when (initialScrollTarget) {
                                    ChapterScrollPosition.END -> "javascript:window.scrollToChapterEnd();"
                                    else -> "javascript:window.scrollToChapterStart();"
                                }
                                Timber.tag("NavDiag").d("WebView onPageFinished: Executing initial scroll to target: $initialScrollTarget")
                                view?.evaluateJavascript(scrollJsCommand) {
                                    onChapterInitiallyScrolled()
                                    scrollActionTaken = true
                                }
                            } else if (initialPageScrollY != null && initialPageScrollY > 0) {
                                val scrollJsCommand =
                                    "javascript:window.scrollToSpecificY($initialPageScrollY);"
                                Timber.tag("NavDiag").d("WebView onPageFinished: Executing initial scroll to Y: $initialPageScrollY")
                                view?.evaluateJavascript(scrollJsCommand) {
                                    onChapterInitiallyScrolled()
                                    scrollActionTaken = true
                                }
                            } else {
                                Timber.tag("NavDiag").d("WebView onPageFinished: No specific scroll, defaulting to start.")
                                view?.evaluateJavascript("javascript:window.scrollToChapterStart();") {
                                    onChapterInitiallyScrolled()
                                    scrollActionTaken = true
                                }
                            }

                            view?.clearFocus()
                            view?.evaluateJavascript(
                                "javascript:if(window.getSelection) window.getSelection().removeAllRanges();",
                                null
                            )
                            isReaderRuntimeReady = true
                            Timber.tag(TAG_VERTICAL_JITTER)
                                .d("androidPageFinished runtimeReady webView=$debugWebViewId chapter='$chapterTitle'")
                        }
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        domStorageEnabled = true
                        layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                        setNeedInitialFocus(false)
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    this.setBackgroundColor(Color.TRANSPARENT)
                    Timber.d(
                        "WebView loading initial data with base URL: $baseUrl (Key: $key)"
                    )
                    loadDataWithBaseURL(baseUrl, initialHtmlContent, "text/html", "UTF-8", null)
                }
                webView
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { releasedWebView ->
                isReaderRuntimeReady = false
                if (localWebViewRef === releasedWebView) {
                    localWebViewRef = null
                }
                customMenuState?.finishActionModeCallback?.invoke()
                customMenuState = null
                onWebViewDisposed(releasedWebView)
                releasedWebView.releaseReaderResources()
            },
            update = { webView ->
                localWebViewRef = webView
                onWebViewInstanceCreated(webView)
                if (!isReaderRuntimeReady) {
                    runtimeApplierState.logPending(chapterTitle)
                } else {
                    val fontCss = getFontCssInjection().replace("\n", " ")
                    val customFontCss = if (customFontPath != null) {
                        "@font-face { font-family: 'CustomFont'; src: url('file://$customFontPath'); }"
                    } else ""
                    val combinedCss = "$fontCss $customFontCss"
                    val fontNameForJs = if (customFontPath != null) {
                        "CustomFont"
                    } else if (currentFontFamily == ReaderFont.ORIGINAL) {
                        ""
                    } else {
                        currentFontFamily.fontFamilyName
                    }
                    val fragmentsJson = org.json.JSONArray(tocFragments).toString()
                    val styleSignature = listOf(
                        currentFontSize,
                        currentLineHeight,
                        fontNameForJs,
                        currentTextAlign.cssValue,
                        currentParagraphGap,
                        currentImageSize,
                        currentHorizontalMargin,
                        currentVerticalMargin
                    ).joinToString(separator = "|")
                    val fontCssChanged = runtimeApplierState.fontCss != combinedCss
                    val styleChanged = runtimeApplierState.styleSignature != styleSignature
                    val tocFragmentsChanged = runtimeApplierState.tocFragmentsJson != fragmentsJson
                    val highlightsChanged = runtimeApplierState.highlightsJson != highlightsJson

                    if (fontCssChanged || styleChanged || tocFragmentsChanged || highlightsChanged) {
                        runtimeApplierState.logApplied(
                            chapterTitle = chapterTitle,
                            fontCssChanged = fontCssChanged,
                            styleChanged = styleChanged,
                            tocFragmentsChanged = tocFragmentsChanged,
                            highlightsChanged = highlightsChanged
                        )
                    } else {
                        runtimeApplierState.logUnchanged(chapterTitle)
                    }

                    if (fontCssChanged) {
                        runtimeApplierState.fontCss = combinedCss
                        val injectFontJs =
                            "var style = document.getElementById('injectedFonts'); if(!style) { style = document.createElement('style'); style.id='injectedFonts'; document.head.appendChild(style); } style.innerHTML = \"$combinedCss\";"
                        webView.evaluateJavascript("javascript:$injectFontJs", null)
                    }

                    if (tocFragmentsChanged) {
                        runtimeApplierState.tocFragmentsJson = fragmentsJson
                        Timber.tag("FRAG_NAV_DEBUG").d("Injecting TOC_FRAGMENTS via setter: $fragmentsJson")
                        webView.evaluateJavascript(
                            "javascript:window.setTocFragments($fragmentsJson);",
                            null
                        )
                    }

                    if (styleChanged) {
                        runtimeApplierState.styleSignature = styleSignature
                        webView.evaluateJavascript(
                            "javascript:window.updateReaderStyles($currentFontSize, $currentLineHeight, '$fontNameForJs', '${currentTextAlign.cssValue}', $currentParagraphGap, $currentImageSize, $currentHorizontalMargin, $currentVerticalMargin);",
                            null
                        )
                    }

                    if (highlightsChanged) {
                        runtimeApplierState.highlightsJson = highlightsJson
                        val escapedHighlights = escapeJsString(highlightsJson)
                        webView.evaluateJavascript(
                            "javascript:window.CURRENT_HIGHLIGHTS = '${escapedHighlights}'; window.HighlightBridgeHelper.restoreHighlights(window.CURRENT_HIGHLIGHTS);",
                            null
                        )
                    }
                }
            }
            )
        }

        // Custom Selection Menu Popup
        customMenuState?.let { state ->
            val configuration = LocalConfiguration.current
            val selectionMenuMaxHeight = (configuration.screenHeightDp.dp - 32.dp).coerceAtLeast(160.dp)
            val popupPositionProvider =
                remember(state.selectionBounds, density, state.isExistingHighlight) {
                    object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize
                        ): IntOffset {
                            val marginPx = with(density) { 16.dp.toPx() }
                            val gapPx = with(density) {
                                if (state.isExistingHighlight) 16.dp.toPx() else 60.dp.toPx()
                            }
                            val placement = sharedSelectionMenuPlacement(
                                viewport = SharedSelectionMenuViewport(windowSize.width, windowSize.height),
                                popup = SharedSelectionMenuSize(popupContentSize.width, popupContentSize.height),
                                selection = SharedSelectionMenuRect(
                                    left = state.selectionBounds.left.toFloat(),
                                    top = state.selectionBounds.top.toFloat(),
                                    right = state.selectionBounds.right.toFloat(),
                                    bottom = state.selectionBounds.bottom.toFloat()
                                ),
                                marginPx = marginPx,
                                gapPx = gapPx
                            )
                            return IntOffset(placement.x, placement.y)
                        }
                    }
                }

            Popup(
                popupPositionProvider = popupPositionProvider, onDismissRequest = {
                    state.finishActionModeCallback()
                    customMenuState = null
                }) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .heightIn(max = selectionMenuMaxHeight)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            activeHighlightPalette.forEachIndexed { index, colorEnum ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .size(28.dp)
                                        .background(colorEnum.color, CircleShape)
                                        .pointerInput(colorEnum) {
                                            detectTapGestures(onTap = {
                                                Timber.d("Kotlin: Color clicked. Existing? ${state.isExistingHighlight}")
                                                if (state.isExistingHighlight && state.cfi != null) {
                                                    localWebViewRef?.evaluateJavascript(
                                                        "javascript:window.HighlightBridgeHelper.updateHighlightStyle('${state.cfi}', '${colorEnum.cssClass}', '${colorEnum.id}');",
                                                        null
                                                    )
                                                } else {
                                                    localWebViewRef?.evaluateJavascript(
                                                        "javascript:window.HighlightBridgeHelper.createUserHighlight('${colorEnum.cssClass}', '${colorEnum.id}');",
                                                        null
                                                    )
                                                }
                                                state.finishActionModeCallback()
                                                localWebViewRef?.clearFocus()
                                                customMenuState = null
                                            }, onLongPress = {
                                                showPaletteManager = true
                                            })
                                        })
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            SpectrumButton(
                                onClick = { showPaletteManager = true }, size = 28.dp
                            )
                        }

                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PaginatedTextSelectionMenu(
                                onCopy = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(context.getString(R.string.clip_label_copied_text), state.selectedText)
                                clipboard.setPrimaryClip(clip)
                                state.finishActionModeCallback()
                                localWebViewRef?.clearFocus()
                                localWebViewRef?.evaluateJavascript(
                                    "javascript:if(window.getSelection) window.getSelection().removeAllRanges();",
                                    null
                                )
                                customMenuState = null
                            },
                                onSelectAll = null,
                                onDictionary = {
                                    val textToDefine = state.selectedText
                                    if (textToDefine.isNotBlank()) {
                                        onWordSelectedForAiDefinition(textToDefine)
                                    }
                                    customMenuState = null
                                },
                                onTranslate = {
                                    val textToDefine = state.selectedText
                                    if (textToDefine.isNotBlank()) {
                                        onTranslate(textToDefine)
                                    }
                                    customMenuState = null
                                },
                                onSearch = {
                                    val textToDefine = state.selectedText
                                    if (textToDefine.isNotBlank()) {
                                        onSearch(textToDefine)
                                    }
                                    customMenuState = null
                                },
                                onNote = {
                                    if (state.isExistingHighlight && state.cfi != null) {
                                        onNoteRequested(state.cfi)
                                    } else {
                                        onNoteRequested(null)
                                        localWebViewRef?.evaluateJavascript(
                                            "javascript:window.HighlightBridgeHelper.createUserHighlight('${HighlightColor.YELLOW.cssClass}', '${HighlightColor.YELLOW.id}');", null
                                        )
                                    }
                                    state.finishActionModeCallback()
                                    customMenuState = null
                                },
                                onHighlight = null,
                                onTts = {
                                    localWebViewRef?.evaluateJavascript(
                                        "javascript:window.TtsBridgeHelper.extractAndRelayTextFromSelection();",
                                        null
                                    )
                                    state.finishActionModeCallback()
                                    localWebViewRef?.clearFocus()
                                    localWebViewRef?.evaluateJavascript(
                                        "javascript:if(window.getSelection) window.getSelection().removeAllRanges();",
                                        null
                                    )
                                    customMenuState = null
                                },
                                onDelete = if (state.isExistingHighlight && state.cfi != null) {
                                    {
                                        val highlightToDelete = userHighlights.find { h ->
                                            h.cfi == state.cfi || h.cfi.split("|").contains(state.cfi)
                                        }
                                        if (highlightToDelete != null) {
                                            val cssClassToDelete = highlightToDelete.color.cssClass
                                            localWebViewRef?.evaluateJavascript(
                                                "javascript:window.HighlightBridgeHelper.removeHighlightByCfi('${escapeJsString(highlightToDelete.cfi)}', '$cssClassToDelete');", null
                                            )
                                            onHighlightDeleted(highlightToDelete.cfi)
                                        }
                                        state.finishActionModeCallback()
                                        customMenuState = null
                                    }
                                } else null,
                                isProUser = isProUser,
                                isOss = isOss,
                                existingNote = state.note,
                                selectedColor = state.selectedColor
                            )
                        }
                    }
                }
            }
        }
        if (showPaletteManager) {
            PaletteManagerDialog(
                currentPalette = activeHighlightPalette,
                onDismiss = { showPaletteManager = false },
                onSave = { newPalette ->
                    newPalette.forEachIndexed { index, color ->
                        onUpdatePalette(index, color)
                    }
                    showPaletteManager = false
                })
        }
    }
}
