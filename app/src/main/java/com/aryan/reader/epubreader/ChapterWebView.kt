/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
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
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.epubreader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import timber.log.Timber
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
import com.aryan.reader.countWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

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

@Suppress("unused") // function used by JavaScript
class TtsJsBridge(
    private val scope: CoroutineScope,
    private val ttsStructuredTextHandler: suspend (String) -> Unit
) {
    @JavascriptInterface
    fun onStructuredTextExtracted(json: String) {
        if (json.isNotBlank() && json != "[]") {
            scope.launch {
                ttsStructuredTextHandler(json)
            }
        } else {
            scope.launch {
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
    fun onHighlightClicked(cfi: String, text: String, left: Int, top: Int, right: Int, bottom: Int) {
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
    private val onCfiForBookmarkReady: (String) -> Unit
) {
    @JavascriptInterface
    fun onCfiExtracted(jsonResponse: String) {
        // This is called from JavaScript with the generated CFI and diagnostics
        try {
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
            Timber.e(e, "Error parsing CFI JSON response: $jsonResponse")
            // Still call back with a fallback CFI so the app doesn't hang
            onCfiReady("/4")
        }
    }
    @JavascriptInterface
    fun onCfiForBookmarkExtracted(jsonResponse: String) {
        // This is called from JavaScript with the generated CFI for a bookmark action
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
    val isExistingHighlight: Boolean = false
)

@Suppress("unused")
class AiJsBridge(
    private val scope: CoroutineScope,
    private val onContentReady: suspend (String) -> Unit
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
    initialScrollTarget: ChapterScrollPosition?,
    initialPageScrollY: Int?,
    initialCfi: String?,
    initialChunkIndex: Int,
    onTopChunkUpdated: (Int) -> Unit,
    currentFontSize: Float,
    currentLineHeight: Float,
    onChapterInitiallyScrolled: () -> Unit,
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
    ttsScope: CoroutineScope,
    tocFragments: List<String>,
    modifier: Modifier = Modifier,
    initialFragmentId: String? = null,
    onTtsTextReady: suspend (String) -> Unit,
    isProUser: Boolean,
    isOss: Boolean = false,
    onShowDictionaryUpsellDialog: () -> Unit,
    onWordSelectedForAiDefinition: (String) -> Unit,
    onContentReadyForSummarization: suspend (String) -> Unit,
    currentFontFamily: ReaderFont,
    customFontPath: String? = null,
    currentTextAlign: ReaderTextAlign,
    onHighlightClicked: () -> Unit,
    onAutoScrollChapterEnd: () -> Unit = {},
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

    LaunchedEffect(currentFontSize, currentLineHeight) {
        localWebViewRef?.evaluateJavascript(
            "javascript:if(window.getSelection) window.getSelection().removeAllRanges();",
            null
        )
    }

    val highlightsJson = remember(userHighlights) {
        val jsonArray = org.json.JSONArray()
        userHighlights.forEach { h ->
            val obj = JSONObject()
            obj.put("cfi", h.cfi)
            obj.put("text", h.text)
            obj.put("cssClass", h.color.cssClass)
            jsonArray.put(obj)
        }
        jsonArray.toString()
    }

    if (showExternalLinkDialog != null) {
        val urlToShow = showExternalLinkDialog!!
        AlertDialog(
            onDismissRequest = { showExternalLinkDialog = null },
            title = { Text("External Link") },
            text = { Text("You clicked on an external link:\n\n$urlToShow\n\nWhat would you like to do?") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, urlToShow.toUri())
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Timber.e(e, "No activity found to handle intent for URL: $urlToShow")
                            Toast.makeText(
                                context,
                                "No browser found to open the link.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        showExternalLinkDialog = null
                    }) { Text("Open") }
                    TextButton(onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Copied Link", urlToShow)
                        clipboard.setPrimaryClip(clip)
                        showExternalLinkDialog = null
                    }) { Text("Copy") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExternalLinkDialog = null }) { Text("Cancel") }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        key(
            key,
            isDarkTheme,
            currentFontSize,
            currentLineHeight,
            currentFontFamily,
            currentTextAlign
        ) {
            AndroidView(
                factory = { ctx ->
                    Timber.d(
                        "InteractiveWebView factory for $chapterTitle (Key: $key), isDarkTheme: $isDarkTheme, initialScroll: $initialScrollTarget"
                    )
                    val webView = InteractiveWebView(
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
                        }
                    ).apply {
                        localWebViewRef = this
                        onWebViewInstanceCreated(this)
                        addJavascriptInterface(
                            PageInfoBridge(onScrollStateUpdate),
                            "PageInfoReporter"
                        )
                        addJavascriptInterface(
                            ProgressJsBridge(onTopChunkUpdated),
                            "ProgressReporter"
                        )
                        addJavascriptInterface(ContentBridge(onChunkRequested), "ContentBridge")

                        addJavascriptInterface(HighlightJsBridge(
                            onCreateCallback = onHighlightCreated,
                            onClickCallback = { cfi, text, left, top, right, bottom ->

                                onHighlightClicked()

                                val densityValue = density.density
                                val locationOnScreen = IntArray(2)
                                this.getLocationOnScreen(locationOnScreen)
                                val xOffset = locationOnScreen[0]
                                val yOffset = locationOnScreen[1]

                                val rect = Rect(
                                    (left * densityValue).toInt() + xOffset,
                                    (top * densityValue).toInt() + yOffset,
                                    (right * densityValue).toInt() + xOffset,
                                    (bottom * densityValue).toInt() + yOffset
                                )

                                customMenuState = CustomMenuState(
                                    selectedText = text,
                                    selectionBounds = rect,
                                    finishActionModeCallback = {
                                        localWebViewRef?.evaluateJavascript("javascript:if(window.getSelection) window.getSelection().removeAllRanges();", null)
                                    },
                                    cfi = cfi,
                                    isExistingHighlight = true
                                )
                            }
                        ), "HighlightBridge")

                        addJavascriptInterface(
                            AutoScrollJsBridge {
                                onAutoScrollChapterEnd()
                            },
                            "AutoScrollBridge"
                        )

                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                consoleMessage?.let {
                                    val message = it.message()
                                    when {
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

                                        message.startsWith("HIGHLIGHT_DEBUG:") -> {
                                            Timber.d(
                                                "JS -> ${message.substringAfter("HIGHLIGHT_DEBUG: ")}"
                                            )
                                        }

                                        message.startsWith("ReaderFontDiagnosis") -> {
                                            Timber.d(
                                                "JS -> ${message.substringAfter("ReaderFontDiagnosis: ")}"
                                            )
                                        }

                                        message.startsWith("AutoScrollDiagnosis") -> {
                                            Timber.d(
                                                "JS -> ${message.substringAfter("AutoScrollDiagnosis: ")}"
                                            )
                                        }

                                        message.startsWith("FRAG_NAV_DEBUG") -> {
                                            Timber.tag("FRAG_NAV_DEBUG").d("JS -> ${message.substringAfter("FRAG_NAV_DEBUG: ")}")
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
                            onCfiReady = { cfi -> onCfiGenerated(cfi) },
                            onCfiForBookmarkReady = { cfi -> onBookmarkCfiGenerated(cfi) }
                        ), "CfiBridge")
                        addJavascriptInterface(SnippetJsBridge { cfi, snippet ->
                            onSnippetForBookmarkReady(
                                cfi,
                                snippet
                            )
                        }, "SnippetBridge")
                        addJavascriptInterface(TtsJsBridge(ttsScope, onTtsTextReady), "TtsBridge")
                        addJavascriptInterface(
                            AiJsBridge(ttsScope, onContentReadyForSummarization),
                            "AiBridge"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString()
                                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                                    Timber.d("Intercepted external link: $url")
                                    showExternalLinkDialog = url
                                    return true
                                }
                                return false
                            }

                            override fun onLoadResource(view: WebView?, url: String?) {
                                super.onLoadResource(view, url)
                                if (url?.contains(".jpg", true) == true ||
                                    url?.contains(".jpeg", true) == true ||
                                    url?.contains(".png", true) == true ||
                                    url?.contains(".gif", true) == true ||
                                    url?.contains(".svg", true) == true ||
                                    url?.contains("image", true) == true
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
                                view?.evaluateJavascript(
                                    "javascript:window.applyReaderTheme($isDarkTheme);",
                                    null
                                )

                                val fragmentsJson = org.json.JSONArray(tocFragments).toString()
                                Timber.tag("FRAG_NAV_DEBUG").d("onPageFinished: Re-injecting TOC_FRAGMENTS: $fragmentsJson")
                                view?.evaluateJavascript("javascript:window.TOC_FRAGMENTS = $fragmentsJson;", null)

                                view?.evaluateJavascript("javascript:setTimeout(window.auditTocFragments, 500);", null)

                                view?.evaluateJavascript("javascript:window.HighlightBridgeHelper.restoreHighlights('${escapeJsString(highlightsJson)}');", null)

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

                                view?.evaluateJavascript(
                                    "javascript:window.updateReaderStyles($currentFontSize, $currentLineHeight, '$fontNameForJs', '${currentTextAlign.cssValue}');",
                                    null
                                )

                                view?.evaluateJavascript(
                                    "javascript:window.checkImagesForDiagnosis();",
                                    null
                                )

                                view?.evaluateJavascript(
                                    "javascript:window.virtualization.init($initialChunkIndex, $totalChunks);",
                                    null
                                )

                                @Suppress("VariableNeverRead") var scrollActionTaken = false

                                if (!initialCfi.isNullOrBlank()) {
                                    val cfiJsCommand =
                                        "javascript:window.scrollToCfi('$initialCfi');"
                                    Timber.d(
                                        "WebView onPageFinished: Executing initial scroll to CFI: $initialCfi"
                                    )
                                    view?.evaluateJavascript(cfiJsCommand) {
                                        onChapterInitiallyScrolled()
                                        scrollActionTaken = true
                                    }
                                } else if (!initialFragmentId.isNullOrBlank()) {
                                    Timber.d("WebView onPageFinished: Scrolling to Element ID: $initialFragmentId")
                                    view?.evaluateJavascript(
                                        "javascript:var el = document.getElementById('$initialFragmentId'); if(el) { el.scrollIntoView(); } else { console.log('Element not found: $initialFragmentId'); }",
                                        null
                                    )
                                    onChapterInitiallyScrolled()
                                    scrollActionTaken = true
                                } else if (initialScrollTarget != null) {
                                    val scrollJsCommand = when (initialScrollTarget) {
                                        ChapterScrollPosition.END -> "javascript:window.scrollToChapterEnd();"
                                        else -> "javascript:window.scrollToChapterStart();"
                                    }
                                    Timber.d(
                                        "WebView onPageFinished: Executing initial scroll to target: $initialScrollTarget"
                                    )
                                    view?.evaluateJavascript(scrollJsCommand) {
                                        onChapterInitiallyScrolled()
                                        scrollActionTaken = true
                                    }
                                } else if (initialPageScrollY != null && initialPageScrollY > 0) {
                                    val scrollJsCommand =
                                        "javascript:window.scrollToSpecificY($initialPageScrollY);"
                                    Timber.d(
                                        "WebView onPageFinished: Executing initial scroll to Y: $initialPageScrollY"
                                    )
                                    view?.evaluateJavascript(scrollJsCommand) {
                                        onChapterInitiallyScrolled()
                                        scrollActionTaken = true
                                    }
                                } else {
                                    Timber.d(
                                        "WebView onPageFinished: No specific scroll, defaulting to start."
                                    )
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
                update = { webView ->
                    Timber.d(
                        "WebView update. Setting Font: ${currentFontFamily.fontFamilyName}"
                    )
                    localWebViewRef = webView
                    onWebViewInstanceCreated(webView)
                    val fontCss = getFontCssInjection().replace("\n", " ")
                    val customFontCss = if (customFontPath != null) {
                        "@font-face { font-family: 'CustomFont'; src: url('file://$customFontPath'); }"
                    } else ""
                    val combinedCss = "$fontCss $customFontCss"
                    val injectFontJs =
                        "var style = document.getElementById('injectedFonts'); if(!style) { style = document.createElement('style'); style.id='injectedFonts'; document.head.appendChild(style); } style.innerHTML = \"$combinedCss\";"
                    webView.evaluateJavascript("javascript:$injectFontJs", null)
                    val fontNameForJs = if (customFontPath != null) {
                        "CustomFont"
                    } else if (currentFontFamily == ReaderFont.ORIGINAL) {
                        ""
                    } else {
                        currentFontFamily.fontFamilyName
                    }
                    val fragmentsJson = org.json.JSONArray(tocFragments).toString()
                    Timber.tag("FRAG_NAV_DEBUG").d("Injecting TOC_FRAGMENTS via setter: $fragmentsJson")

                    webView.evaluateJavascript("javascript:window.setTocFragments($fragmentsJson);", null)

                    webView.evaluateJavascript(
                        "javascript:window.updateReaderStyles($currentFontSize, $currentLineHeight, '$fontNameForJs', '${currentTextAlign.cssValue}');",
                        null
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Custom Selection Menu Popup
        customMenuState?.let { state ->
            val popupPositionProvider = remember(state.selectionBounds, density, state.isExistingHighlight) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        val topMargin = with(density) { 16.dp.toPx() }.toInt()
                        val bottomMargin = with(density) {
                            if (state.isExistingHighlight) 16.dp.toPx() else 60.dp.toPx()
                        }.toInt()

                        var x = state.selectionBounds.centerX() - popupContentSize.width / 2

                        var y = state.selectionBounds.top - popupContentSize.height - topMargin
                        if (y < with(density) { 24.dp.toPx() }.toInt()) {
                            y = state.selectionBounds.bottom + bottomMargin
                        }
                        if (x < 0) x = 0
                        if (x + popupContentSize.width > windowSize.width) {
                            x = windowSize.width - popupContentSize.width
                        }
                        if (y + popupContentSize.height > windowSize.height) {
                            y = windowSize.height - popupContentSize.height
                        }
                        if (y < 0) y = 0

                        return IntOffset(
                            x.coerceIn(0, windowSize.width - popupContentSize.width),
                            y.coerceIn(0, windowSize.height - popupContentSize.height)
                        )
                    }
                }
            }

            Popup(
                popupPositionProvider = popupPositionProvider,
                onDismissRequest = {
                    state.finishActionModeCallback()
                    customMenuState = null
                }
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.width(IntrinsicSize.Max)
                    ) {
                        // 1. Color Row (Improved sizing and gaps)
                        Row(
                            modifier = Modifier
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center, // Centered colors
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HighlightColor.entries.forEach { colorEnum ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .size(24.dp)
                                        .background(colorEnum.color, CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                                        .clickable {
                                            Timber.d("Kotlin: Color clicked. Existing? ${state.isExistingHighlight}")

                                            if (state.isExistingHighlight && state.cfi != null) {
                                                // UPDATE EXISTING HIGHLIGHT
                                                Timber.d("Kotlin: Requesting UPDATE via JS for CFI: ${state.cfi}")
                                                localWebViewRef?.evaluateJavascript(
                                                    "javascript:window.HighlightBridgeHelper.updateHighlightStyle('${state.cfi}', '${colorEnum.cssClass}', '${colorEnum.id}');",
                                                    null
                                                )
                                            } else {
                                                // CREATE NEW HIGHLIGHT
                                                Timber.d("Kotlin: Requesting CREATE via JS")
                                                localWebViewRef?.evaluateJavascript(
                                                    "javascript:window.HighlightBridgeHelper.createUserHighlight('${colorEnum.cssClass}', '${colorEnum.id}');",
                                                    null
                                                )
                                            }
                                            state.finishActionModeCallback()
                                            localWebViewRef?.clearFocus()
                                            customMenuState = null
                                        }
                                )
                            }
                        }

                        // 2. Delete Option (Only for existing highlights)
                        if (state.isExistingHighlight && state.cfi != null) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // LOGGING START
                                        Timber.d("Kotlin: Popup Delete requested for clicked CFI: '${state.cfi}'")

                                        // 1. IMPROVED LOOKUP: Check if the clicked CFI exists within any split CFI string
                                        val highlightToDelete = userHighlights.find { h ->
                                            h.cfi == state.cfi || h.cfi.split("|").contains(state.cfi)
                                        }

                                        if (highlightToDelete == null) {
                                            Timber.e("Kotlin: ERROR - Lookup failed. CFI '${state.cfi}' not found in any highlight.")
                                        } else {
                                            Timber.d("Kotlin: SUCCESS - Found highlight object. Full CFI: '${highlightToDelete.cfi}', Color: ${highlightToDelete.color.id}")

                                            val cssClassToDelete = highlightToDelete.color.cssClass
                                            val allCfiParts = highlightToDelete.cfi.split("|")

                                            allCfiParts.forEach { partCfi ->
                                                Timber.d("Kotlin: Requesting JS removal for part: '$partCfi'")
                                                localWebViewRef?.evaluateJavascript(
                                                    "javascript:window.HighlightBridgeHelper.removeHighlightByCfi('${escapeJsString(partCfi)}', '$cssClassToDelete');",
                                                    null
                                                )
                                            }

                                            onHighlightDeleted(highlightToDelete.cfi)
                                        }

                                        state.finishActionModeCallback()
                                        customMenuState = null
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Remove",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        HorizontalDivider()

                        // 2. Copy Option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Copied Text", state.selectedText)
                                    clipboard.setPrimaryClip(clip)
                                    state.finishActionModeCallback()
                                    localWebViewRef?.clearFocus()
                                    localWebViewRef?.evaluateJavascript("javascript:if(window.getSelection) window.getSelection().removeAllRanges();", null)
                                    customMenuState = null
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CopyAll,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Copy",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 3. Dictionary Option (Preserving Logic)
                        if (!isOss && state.selectedText.length <= 2000) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val textToDefine = state.selectedText
                                        if (textToDefine.isNotBlank()) {
                                            val wordCount = countWords(textToDefine)
                                            if (isProUser || wordCount <= 1) {
                                                onWordSelectedForAiDefinition(textToDefine)
                                            } else {
                                                onShowDictionaryUpsellDialog()
                                            }
                                        }
                                        customMenuState = null
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.dictionary),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Dictionary",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}