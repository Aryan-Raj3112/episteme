package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderSearchFocusDelayMillis
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.readerWordStartMatchOffsets
import com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubTocEntry
import com.aryan.reader.shared.reader.SharedMediaOverlayProjection
import com.aryan.reader.shared.reader.findElementOffset
import com.aryan.reader.paginatedreader.SemanticTextBlock
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Composable
internal fun SharedMobileEpubSearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onForceSearch: () -> Unit,
    results: List<SharedMobileEpubSearchResult>,
    isSearching: Boolean,
    showResults: Boolean,
    onShowResultsChange: (Boolean) -> Unit,
    onResultClick: (SharedMobileEpubSearchResult) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        delay(ReaderSearchFocusDelayMillis)
        focusRequester.requestFocus()
    }
    Column(modifier) {
        Surface(tonalElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = readerString("content_desc_close_search", "Close search"),
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(readerString("reader_search_hint", "Search in book")) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = readerString("reader_search_clear", "Clear search"),
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onForceSearch()
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester)
                )
                IconButton(
                    onClick = {
                        onShowResultsChange(!showResults)
                        focusManager.clearFocus()
                    },
                ) {
                    Icon(
                        if (showResults) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = if (showResults) "Hide results" else "Show results"
                    )
                }
            }
        }
        if (showResults) {
            Surface(Modifier.fillMaxWidth().weight(1f), tonalElevation = 8.dp) {
                when {
                    isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(readerString("reader_search_no_results", "No results found"), style = MaterialTheme.typography.bodyLarge)
                    }
                    else -> Column {
                        // Android parity (SharedReaderSearchResultsPanel):
                        // localized plural count header, chapter-title headline,
                        // and a bolded matched span inside the snippet.
                        Text(
                            if (results.size == 1) {
                                readerString("search_results_count_one", "1 result")
                            } else {
                                readerString("search_results_count_other", "%1\$d results", results.size)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        HorizontalDivider()
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(results) { result ->
                                ListItem(
                                    headlineContent = {
                                        Text(result.chapterTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    supportingContent = {
                                        Text(
                                            buildAnnotatedString {
                                                append(result.snippet)
                                                if (result.matchIndexInSnippet >= 0 && result.matchLengthInSnippet > 0) {
                                                    addStyle(
                                                        SpanStyle(fontWeight = FontWeight.Bold),
                                                        result.matchIndexInSnippet,
                                                        (result.matchIndexInSnippet + result.matchLengthInSnippet)
                                                            .coerceAtMost(result.snippet.length),
                                                    )
                                                }
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        onResultClick(result)
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun SharedMobileEpubSearchNavigation(current: Int, total: Int, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), tonalElevation = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
            IconButton(onClick = onPrevious, enabled = current > 0) { Icon(
                    Icons.Default.ArrowDropUp,
                    contentDescription = readerString("reader_search_previous_result", "Previous result"),
                ) }
            Text("${current + 1}/$total", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onNext, enabled = current < total - 1) { Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = readerString("reader_search_next_result", "Next result"),
                ) }
        }
    }
}

@Composable
internal fun SharedMobileEpubJumpHistoryBar(
    backLabel: String?,
    forwardLabel: String?,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, enabled = backLabel != null, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = readerString("reader_jump_back", "Jump back"),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(backLabel.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = readerString(
                        "desktop_clear_jump_history",
                        "Clear jump history",
                    ),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(readerString("action_clear", "Clear"), maxLines = 1)
            }
            TextButton(onClick = onForward, enabled = forwardLabel != null, modifier = Modifier.weight(1f)) {
                Text(forwardLabel.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(4.dp))
                Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = readerString("reader_jump_forward", "Jump forward"),
                        modifier = Modifier.size(16.dp),
                    )
            }
        }
    }
}

internal fun ReaderLocator.mobileEpubJumpLabel(book: SharedEpubBook?): String {
    val chapter = chapterIndex
    return if (chapter != null) {
        book?.chapters?.getOrNull(chapter)?.title?.takeIf(String::isNotBlank)
            ?: "Chapter ${chapter + 1}"
    } else if (pageIndex != null) {
        "Page ${pageIndex + 1}"
    } else {
        "Location"
    }
}

internal data class SharedMobileEpubSearchResult(
    val chapterIndex: Int,
    val chapterTitle: String,
    val chunkIndex: Int,
    val occurrenceIndex: Int,
    val snippet: String,
    val locator: ReaderLocator,
    /** Offset of the match within [snippet], or -1 when unknown (snippet bolding). */
    val matchIndexInSnippet: Int = -1,
    /** Length of the matched span inside [snippet]. */
    val matchLengthInSnippet: Int = 0,
)
internal data class SharedMobileEpubLink(val href: String, val chapterHref: String?)
internal data class SharedMobileEpubActiveToc(val href: String, val fragmentId: String?)
internal data class SharedMobileEpubSelectionAction(val action: String, val text: String, val locator: ReaderLocator?)

internal val SharedMobileEpubJson = Json { ignoreUnknownKeys = true }

internal const val SharedMobileEpubCaptureCurrentPositionScript =
    "window.readerCaptureCurrentPosition ? window.readerCaptureCurrentPosition() : null"

/** Normalizes the quoted result returned by Android/iOS WebView JavaScript evaluation. */
internal fun decodeSharedMobileJavascriptResult(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
    val parsed = runCatching { Json.parseToJsonElement(value) }.getOrNull()
    return parsed?.let { element ->
        runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
    }?.takeIf(String::isNotBlank) ?: value
}

internal fun String.sharedMobileEpubLocatorOrNull(): ReaderLocator? {
    val objectValue = runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    return objectValue.toMobileEpubLocator()
}

internal fun String.sharedMobileEpubHighlightOrNull(): UserHighlight? {
    val objectValue = runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    val text = objectValue["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val cfi = objectValue["cfi"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
    val locator = (objectValue["locator"] as? JsonObject)?.toMobileEpubLocator()
        ?: ReaderLocator(cfi = cfi, textQuote = text)
    val chapterIndex = locator.chapterIndex
        ?: objectValue["chapterIndex"]?.jsonPrimitive?.intOrNull
        ?: return null
    if (text.isBlank()) return null
    val color = HighlightColor.entries.firstOrNull {
        it.id == objectValue["colorId"]?.jsonPrimitive?.contentOrNull
    } ?: HighlightColor.YELLOW
    val style = HighlightStyle.fromId(objectValue["styleId"]?.jsonPrimitive?.contentOrNull)
    val normalizedLocator = locator.withFallbacks(
        chapterIndex = chapterIndex,
        cfi = cfi,
        textQuote = text
    )
    val stableId = "mobile-web-$chapterIndex-${cfi.hashCode()}-${normalizedLocator.startOffset ?: -1}-${normalizedLocator.endOffset ?: -1}"
    return UserHighlight(
        id = stableId,
        cfi = cfi,
        text = text,
        color = color,
        chapterIndex = chapterIndex,
        style = style,
        locator = normalizedLocator
    )
}

internal fun String.sharedMobileEpubSelectionActionOrNull(): SharedMobileEpubSelectionAction? {
    val objectValue = runCatching { Json.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    val action = objectValue["action"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase().orEmpty()
    val text = objectValue["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (action.isBlank() || text.isBlank()) return null
    val locator = objectValue["locator"]?.let { element ->
        runCatching { element.jsonObject.toMobileEpubLocator() }.getOrNull()
    }
    return SharedMobileEpubSelectionAction(action = action, text = text, locator = locator)
}

internal fun String.sharedMobileEpubHighlightIdOrNull(): String? {
    return runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }
        .getOrNull()
        ?.get("id")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
}

/** Common tag for highlight-shift diagnostics across WebView JS and native. */
internal const val SharedMobileEpubHighlightShiftTag = "HIGHLIGHT_SHIFT"

/** Bridge method posted by readerHighlightShiftLog in the shared document. */
internal const val SharedMobileEpubHighlightShiftBridgeMethod = "readerHighlightShiftLog"

internal fun String.sharedMobileEpubHighlightShiftMessageOrNull(): String? {
    return runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }
        .getOrNull()
        ?.get("message")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
}

/** Common tag for selection-shift diagnostics across WebView JS and native. */
internal const val SharedMobileEpubSelectionShiftTag = "SEL_SHIFT"

/** Bridge method posted by readerSelectionShiftLog in the shared document. */
internal const val SharedMobileEpubSelectionShiftBridgeMethod = "readerSelectionShiftLog"

internal fun String.sharedMobileEpubSelectionShiftMessageOrNull(): String? {
    return runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }
        .getOrNull()
        ?.get("message")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
}

/**
 * Generic `{"message": ...}` trace payload (readerDesktopPositionTraceLog and
 * siblings). Forwarded to native logs only while the EPUB restore guard is
 * armed, so scroll-trace volume stays bounded to the restore window.
 */
internal fun String.sharedMobileEpubTraceMessageOrNull(): String? {
    return runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }
        .getOrNull()
        ?.get("message")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
}

/**
 * Android parity (ChapterWebView restoreHighlights): the authoritative highlight list
 * is pushed into the WebView via window.readerApplyHighlights instead of reloading the
 * document. The JSON shape matches what the shared selection script's
 * applyHighlightObject consumes (id/cfi/text/colorId/style/locator).
 */
internal fun sharedMobileEpubHighlightsApplyScript(highlights: List<UserHighlight>): String {
    if (highlights.isEmpty()) {
        return "if (window.readerApplyHighlights) window.readerApplyHighlights([]);"
    }
    val json = buildString {
        append('[')
        highlights.forEachIndexed { index, highlight ->
            if (index > 0) append(',')
            append('{')
            append("\"id\":").append(JsonPrimitive(highlight.id))
            append(",\"cfi\":").append(JsonPrimitive(highlight.cfi))
            append(",\"text\":").append(JsonPrimitive(highlight.text))
            append(",\"colorId\":").append(JsonPrimitive(highlight.color.id))
            append(",\"style\":").append(JsonPrimitive(highlight.style.id))
            highlight.colorArgb?.let { append(",\"colorArgb\":").append(it.toLong()) }
            append(",\"chapterIndex\":").append(highlight.chapterIndex)
            append(",\"locator\":{")
            val locator = highlight.locator
            val locatorFields = buildList {
                locator.chapterIndex?.let { add("\"chapterIndex\":$it") }
                locator.pageIndex?.let { add("\"pageIndex\":$it") }
                locator.startOffset?.let { add("\"startOffset\":$it") }
                locator.endOffset?.let { add("\"endOffset\":$it") }
                locator.blockIndex?.let { add("\"blockIndex\":$it") }
                locator.charOffset?.let { add("\"charOffset\":$it") }
                locator.textQuote?.let { add("\"textQuote\":${JsonPrimitive(it)}") }
                locator.cfi?.let { add("\"cfi\":${JsonPrimitive(it)}") }
            }
            append(locatorFields.joinToString(","))
            append('}')
            append('}')
        }
        append(']')
    }
    return "if (window.readerApplyHighlights) window.readerApplyHighlights($json);"
}

internal fun String.sharedMobileEpubPullOrNull(): Pair<String, Float>? {
    val value = runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    val direction = value["direction"]?.jsonPrimitive?.contentOrNull ?: return null
    val progress = value["progress"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: return null
    return direction to progress.coerceIn(0f, 1.25f)
}

internal fun SharedEpubBook.searchMobileEpub(
    query: String,
    pages: List<ReaderPage>,
): List<SharedMobileEpubSearchResult> {
    val needle = query.trim()
    return buildList {
        chapters.forEachIndexed { chapterIndex, chapter ->
            val chapterOffsets = readerWordStartMatchOffsets(chapter.plainText, query)
            var chapterOccurrence = 0
            ReaderHtmlDocumentBuilder.verticalChapterChunks(this@searchMobileEpub, chapterIndex).forEachIndexed { chunkIndex, html ->
                val text = html.mobileEpubPlainText()
                readerWordStartMatchOffsets(text, query).forEachIndexed { chunkOccurrence, found ->
                    val snippetStart = (found - 35).coerceAtLeast(0)
                    val snippetEnd = (found + needle.length + 35).coerceAtMost(text.length)
                    val rawSnippet = text.substring(snippetStart, snippetEnd)
                    val trimmedSnippet = rawSnippet.trim()
                    // Android parity (EpubReaderSearch bolded snippet): track
                    // where the matched span lands inside the trimmed snippet
                    // so the result row can bold the query text.
                    val matchInSnippet = (found - snippetStart)
                        .minus(rawSnippet.length - rawSnippet.trimStart().length)
                        .coerceIn(0, trimmedSnippet.length)
                    val sourceOffset = chapterOffsets.getOrNull(chapterOccurrence)
                    val page = sourceOffset?.let { offset ->
                        pages.firstOrNull {
                            it.chapterIndex == chapterIndex &&
                                offset >= it.startOffset &&
                                offset < it.endOffset.coerceAtLeast(it.startOffset + 1)
                        }
                    } ?: pages.firstOrNull { it.chapterIndex == chapterIndex }
                    add(
                        SharedMobileEpubSearchResult(
                            chapterIndex = chapterIndex,
                            chapterTitle = chapter.title.ifBlank { "Chapter ${chapterIndex + 1}" },
                            chunkIndex = chunkIndex,
                            occurrenceIndex = chunkOccurrence,
                            snippet = trimmedSnippet,
                            matchIndexInSnippet = matchInSnippet,
                            matchLengthInSnippet = needle.length
                                .coerceAtMost(trimmedSnippet.length - matchInSnippet)
                                .coerceAtLeast(0),
                            locator = ReaderLocator(
                                chapterIndex = chapterIndex,
                                chapterId = chapter.id,
                                href = chapter.baseHref,
                                pageIndex = page?.pageIndex,
                                startOffset = sourceOffset ?: page?.startOffset ?: 0,
                                endOffset = sourceOffset?.plus(needle.length)
                                    ?: page?.startOffset
                                    ?: 0,
                                textQuote = text.substring(found, found + needle.length),
                            ),
                        )
                    )
                    chapterOccurrence++
                }
            }
        }
    }
}

internal fun String.mobileEpubPlainText(): String =
    replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ").trim()

internal fun JsonObject.toMobileEpubLocator(): ReaderLocator {
    fun int(name: String): Int? = get(name)?.jsonPrimitive?.intOrNull
    fun string(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull
    return ReaderLocator(
        chapterIndex = int("chapterIndex"),
        chapterId = string("chapterId"),
        href = string("href"),
        pageIndex = int("pageIndex"),
        startOffset = int("startOffset"),
        endOffset = int("endOffset"),
        blockIndex = int("blockIndex"),
        charOffset = int("charOffset"),
        textQuote = string("textQuote"),
        cfi = string("cfi")
    )
}

internal fun String.sharedMobileEpubLinkOrNull(): SharedMobileEpubLink? {
    val objectValue = runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    val href = objectValue["href"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
    return SharedMobileEpubLink(
        href = href,
        chapterHref = objectValue["chapterHref"]?.jsonPrimitive?.contentOrNull
    )
}

internal fun String.sharedMobileEpubDirectionOrNull(): String? {
    val objectValue = runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    return objectValue["direction"]?.jsonPrimitive?.contentOrNull
        ?.takeIf { it == "previous" || it == "next" }
}

internal fun String.sharedMobileEpubActiveTocOrNull(): SharedMobileEpubActiveToc? {
    val objectValue = runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    val href = objectValue["href"]?.jsonPrimitive?.contentOrNull ?: return null
    return SharedMobileEpubActiveToc(
        href = href,
        fragmentId = objectValue["fragmentId"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
    )
}

internal fun sharedMobileEpubActiveTocScript(book: SharedEpubBook, chapterIndex: Int): String {
    val chapterHref = book.chapters.getOrNull(chapterIndex)?.baseHref.orEmpty()
    val fragments = book.tableOfContents
        .filter { it.href.normalizeMobileEpubPath() == chapterHref.normalizeMobileEpubPath() }
        .mapNotNull(SharedEpubTocEntry::fragmentId)
        .distinct()
    val hrefJson = JsonPrimitive(chapterHref).toString()
    val fragmentsJson = fragments.joinToString(prefix = "[", postfix = "]") { JsonPrimitive(it).toString() }
    return """
        (function () {
          if (window.readerIosTocTrackerCleanup) window.readerIosTocTrackerCleanup();
          var href = $hrefJson;
          var fragments = $fragmentsJson;
          var lastFragment = '__reader_unset__';
          var timer = null;
          function report() {
            timer = null;
            var best = null;
            var bestTop = -Infinity;
            for (var index = 0; index < fragments.length; index++) {
              var fragment = fragments[index];
              var decoded = fragment;
              try { decoded = decodeURIComponent(fragment); } catch (_) {}
              var element = document.getElementById(fragment) || document.getElementById(decoded);
              if (!element) continue;
              var top = element.getBoundingClientRect().top;
              if (top <= 18 && top > bestTop) { best = fragment; bestTop = top; }
            }
            var current = best || '';
            if (current === lastFragment) return;
            lastFragment = current;
            if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
              window.kmpJsBridge.callNative('readerActiveTocChanged', JSON.stringify({ href: href, fragmentId: best }));
            }
          }
          function schedule() {
            if (timer !== null) window.clearTimeout(timer);
            timer = window.setTimeout(report, 90);
          }
          window.addEventListener('scroll', schedule, { passive: true });
          window.readerIosTocTrackerCleanup = function () {
            window.removeEventListener('scroll', schedule);
            if (timer !== null) window.clearTimeout(timer);
          };
          window.setTimeout(report, 0);
        })();
    """.trimIndent()
}



internal fun ReaderPage.toMobileEpubLocator(book: SharedEpubBook?): ReaderLocator {
    val chapter = book?.chapters?.getOrNull(chapterIndex)
    // Android benchmark (LocatorConverter.getCfiFromLocator +
    // semanticCfiForBlock): the CFI keeps the intra-block offset of the page
    // start, not a constant `:0`. Emitting `:0` collapsed every page that
    // starts mid-block onto the block head, which read as inaccurate resume /
    // TTS / highlight positions vs Android.
    val textBlocks = semanticBlocks
        .flatMap { it.flattenForLocator() }
        .filterIsInstance<SemanticTextBlock>()
        .filter { it.text.isNotBlank() }
    val textBlock = textBlocks.firstOrNull { block ->
        val end = block.startCharOffsetInSource + block.text.length
        end > startOffset
    } ?: textBlocks.firstOrNull()
    val androidStyleCfi = textBlock?.let { block ->
        val base = block.cfi?.takeIf { it.startsWith("/") } ?: return@let null
        // Keep the explicit `:offset` suffix (including `:0`) so emitted
        // locators stay byte-stable with previously persisted CFIs; the
        // value now carries the real intra-block offset instead of a
        // constant 0.
        val local = (startOffset - block.startCharOffsetInSource)
            .coerceIn(0, block.text.length)
        "$base:$local"
    }
    return ReaderLocator(
        chapterIndex = chapterIndex,
        chapterId = chapter?.id,
        href = chapter?.baseHref,
        pageIndex = pageIndex,
        startOffset = startOffset,
        endOffset = startOffset,
        textQuote = text.take(120),
        blockIndex = textBlock?.blockIndex,
        // Null when no semantic block backs the page (plain-text fallback);
        // otherwise the absolute page-start offset (Android Locator parity).
        charOffset = textBlock?.let { startOffset },
        cfi = androidStyleCfi
    )
}

private fun com.aryan.reader.paginatedreader.SemanticBlock.flattenForLocator(): List<com.aryan.reader.paginatedreader.SemanticBlock> {
    return when (this) {
        is com.aryan.reader.paginatedreader.SemanticList -> listOf(this) + items
        is com.aryan.reader.paginatedreader.SemanticTable -> listOf(this) +
            rows.flatMap { row -> row.flatMap { cell -> cell.content.flattenAllForLocator() } }
        is com.aryan.reader.paginatedreader.SemanticFlexContainer -> listOf(this) + children.flatMap { it.flattenForLocator() }
        is com.aryan.reader.paginatedreader.SemanticWrappingBlock -> listOf(this, floatedImage) + paragraphsToWrap
        else -> listOf(this)
    }
}

private fun List<com.aryan.reader.paginatedreader.SemanticBlock>.flattenAllForLocator(): List<com.aryan.reader.paginatedreader.SemanticBlock> {
    return flatMap { it.flattenForLocator() }
}

internal fun sharedMobileEpubNavigationScript(
    locator: ReaderLocator,
    fragment: String?,
    targetChunkIndex: Int?,
    targetChunkHtml: String?,
    preferExact: Boolean = false
): String {
    val locatorJson = buildJsonObject {
        locator.chapterIndex?.let { put("chapterIndex", it) }
        locator.chapterId?.let { put("chapterId", it) }
        locator.href?.let { put("href", it) }
        locator.pageIndex?.let { put("pageIndex", it) }
        locator.startOffset?.let { put("startOffset", it) }
        locator.endOffset?.let { put("endOffset", it) }
        locator.blockIndex?.let { put("blockIndex", it) }
        locator.charOffset?.let { put("charOffset", it) }
        locator.textQuote?.let { put("textQuote", it) }
        locator.cfi?.let { put("cfi", it) }
    }
    val fragmentJson = fragment?.let(::JsonPrimitive)?.toString() ?: "null"
    // Small chunks stay inline for instant landing. Large chunks go through the
    // chunk bridge (bounded evaluateJavaScript payloads); the script polls for
    // the target after requesting so deep links into big chapters still land.
    val chunkInjection = if (targetChunkIndex != null && targetChunkHtml != null &&
        targetChunkHtml.length <= ReaderHtmlDocumentBuilder.MaxInlineVirtualChunkChars
    ) {
        "if (window.readerVirtualization) window.readerVirtualization.provideChunk($targetChunkIndex, ${JsonPrimitive(targetChunkHtml)});"
    } else if (targetChunkIndex != null && targetChunkHtml != null) {
        "if (window.readerVirtualization && window.readerVirtualization.requestChunk) window.readerVirtualization.requestChunk($targetChunkIndex);"
    } else {
        ""
    }
    val needsChunkWait = targetChunkIndex != null && targetChunkHtml != null &&
        targetChunkHtml.length > ReaderHtmlDocumentBuilder.MaxInlineVirtualChunkChars
    // A reopen restore must never scroll approximately: the document is still
    // settling (the chapter can be collapsed to its intrinsic size until it is
    // rendered), so a ratio or host_top scroll against that document parks on
    // the chapter top and overwrites the exact landing the boot anchor and the
    // retry loop already achieved (observed: exact_range at 3166px, then
    // content_ratio at 528px of a 569px document, then chapter start). Android
    // parity: scrollToCfi retries the exact target and only falls back when it
    // genuinely cannot resolve. preferExact therefore retries exact-only and
    // never approximates; explicit user navigations keep their immediate
    // approximate feedback as the last step of the retry budget.
    val scrollBody = if (preferExact) {
        """
          if (fragment) {
            var chapter = null;
            if (locator.chapterIndex !== undefined && locator.chapterIndex !== null) {
              chapter = document.querySelector('[data-reader-chapter-index="' + locator.chapterIndex + '"]');
            }
            var target = null;
            var candidates = (chapter || document).querySelectorAll('[id]');
            for (var index = 0; index < candidates.length; index++) {
              if (candidates[index].id === fragment) { target = candidates[index]; break; }
            }
            if (target) {
              target.scrollIntoView({ block: 'start', inline: 'nearest', behavior: 'auto' });
              return true;
            }
          }
          if (window.readerScrollToLocator) {
            try {
              if (window.readerScrollToLocator(locator, { source: 'ios_mobile', exactOnly: true })) return true;
            } catch (_) {}
          }
          return false;
        """.trimIndent()
    } else {
        """
          if (fragment) {
            var chapter = null;
            if (locator.chapterIndex !== undefined && locator.chapterIndex !== null) {
              chapter = document.querySelector('[data-reader-chapter-index="' + locator.chapterIndex + '"]');
            }
            var target = null;
            var candidates = (chapter || document).querySelectorAll('[id]');
            for (var index = 0; index < candidates.length; index++) {
              if (candidates[index].id === fragment) { target = candidates[index]; break; }
            }
            if (target) {
              target.scrollIntoView({ block: 'start', inline: 'nearest', behavior: 'auto' });
              return true;
            }
          }
          if (window.readerScrollToLocator) {
            try {
              var handled = window.readerScrollToLocator(locator, { source: 'ios_mobile' });
              if (handled !== false) return true;
            } catch (_) {}
          }
          return false;
        """.trimIndent()
    }
    // Both branches retry: a skipped exact scroll (target chunk still
    // streaming in, chapter not rendered yet) must keep polling instead of
    // giving up after one attempt, which is what an undefined return value
    // used to do.
    val exactRetryLimit = if (needsChunkWait) 40 else 8
    val fallbackScroll = if (preferExact) {
        "readerDesktopPositionTraceLog('event=web_navigation_exact_unresolved ' + readerLocatorTrace(locator));"
    } else {
        "try { if (window.readerScrollToLocator && !window.readerScrollToLocator(locator, { source: 'ios_mobile_fallback' })) { } } catch (_) {}"
    }
    return """
        (function () {
          var locator = $locatorJson;
          var fragment = $fragmentJson;
          $chunkInjection
          function attempt() {
        $scrollBody
          }
          if (attempt()) return;
          var tries = 0;
          var timer = window.setInterval(function () {
            tries++;
            try {
              if (attempt()) { window.clearInterval(timer); return; }
            } catch (_) {}
            if (tries >= $exactRetryLimit) {
              window.clearInterval(timer);
              $fallbackScroll
            }
          }, 125);
        })();
        """.trimIndent()
}

/**
 * The narration band, for the WebView vertical surface.
 *
 * Its own bridge function rather than a variant of [sharedMobileEpubTtsNavigationScript] because the
 * two paint through deliberately different machinery: the read-aloud path repaints a stored
 * highlight by quote and cfi, while a media overlay anchor is resolved from the same parse that
 * produced the blocks and so is exact by construction — reaching for the quote repair there would
 * hide a genuinely wrong offset behind a fuzzy match.
 *
 * A null projection clears the band, which is what ends a narration run: without the explicit clear
 * the last narrated line would stay painted after the audio stopped.
 */
internal fun sharedMobileEpubMediaOverlayFragmentScript(
    projection: SharedMediaOverlayProjection?
): String {
    // The chapter comes from the projection rather than the fragment: a fragment is offsets in *a*
    // chapter, and only the projection knows which one.
    val chapterIndex = projection?.chapterIndex
    val fragment = projection?.fragment
    val fragmentJson = if (chapterIndex != null && fragment != null) {
        buildJsonObject {
            put("chapterIndex", chapterIndex)
            put("startOffset", fragment.startAbs)
            put("endOffset", fragment.endAbs)
            fragment.blockCfi?.let { put("cfi", it) }
        }.toString()
    } else {
        "null"
    }
    // Always ask, and let the script measure. Android's WebView path asks only on a chapter change
    // (`EpubReaderScreen.kt:1997`), because it anchors by element id and has no offsets to measure
    // against, so a chapter change is the only evidence it has. This path carries the real fragment,
    // and `mediaOverlayFragmentNeedsFollowScroll` compares it against the actual viewport — the same
    // answer the native surfaces reach by comparing against the page. Asking only on a chapter change
    // here would be the approximation where the measurement is available.
    //
    // Followable is exactly what was sent: the script scrolls by the fragment's own chapter, so a
    // request without one has nothing to scroll to.
    val follow = fragmentJson != "null"
    return "if (window.readerSetMediaOverlayFragment) window.readerSetMediaOverlayFragment($fragmentJson, $follow);"
}

internal fun sharedMobileEpubTtsNavigationScript(locator: ReaderLocator?): String {
    val locatorJson = locator?.let { target ->
        buildJsonObject {
            target.chapterIndex?.let { put("chapterIndex", it) }
            target.pageIndex?.let { put("pageIndex", it) }
            target.startOffset?.let { put("startOffset", it) }
            target.endOffset?.let { put("endOffset", it) }
            target.textQuote?.let { put("textQuote", it) }
            target.cfi?.let { put("cfi", it) }
        }.toString()
    } ?: "null"
    return "if (window.readerSetTtsLocator) window.readerSetTtsLocator($locatorJson, true);"
}

internal fun sharedMobileEpubSearchNavigationScript(result: SharedMobileEpubSearchResult, query: String, chunkHtml: String?): String {
    val injection = when {
        chunkHtml == null -> ""
        chunkHtml.length <= ReaderHtmlDocumentBuilder.MaxInlineVirtualChunkChars ->
            "if(window.readerVirtualization)window.readerVirtualization.provideChunk(${result.chunkIndex},${JsonPrimitive(chunkHtml)});"
        else ->
            "if(window.readerVirtualization&&window.readerVirtualization.requestChunk)window.readerVirtualization.requestChunk(${result.chunkIndex});"
    }
    val needsWait = chunkHtml != null && chunkHtml.length > ReaderHtmlDocumentBuilder.MaxInlineVirtualChunkChars
    val highlightBody = """
          var chunk=document.querySelector('[data-reader-chunk-index="${result.chunkIndex}"]');
          var query=${JsonPrimitive(query)};
          document.querySelectorAll('.reader-ios-search-hit').forEach(function(hit){
            var parent=hit.parentNode; while(hit.firstChild)parent.insertBefore(hit.firstChild,hit); parent.removeChild(hit); parent.normalize();
          });
          if(!chunk||!chunk.innerHTML.trim())return false;
          var walker=document.createTreeWalker(chunk,NodeFilter.SHOW_TEXT);
          var node,occurrence=0,target=null,needle=query.toLocaleLowerCase();
          while((node=walker.nextNode())&&!target){
            var value=node.nodeValue||'',lower=value.toLocaleLowerCase(),from=0,found;
            while((found=lower.indexOf(needle,from))>=0){
              var wordStart=found===0||!/[\p{L}\p{N}]/u.test(lower.charAt(found-1));
              if(wordStart){
                if(occurrence===${result.occurrenceIndex}){
                  var range=document.createRange(); range.setStart(node,found); range.setEnd(node,found+needle.length);
                  var mark=document.createElement('mark'); mark.className='reader-ios-search-hit';
                  mark.style.background='#ffdf5d'; mark.style.color='inherit'; range.surroundContents(mark); target=mark; break;
                }
                occurrence++;
              }
              from=found+Math.max(1,needle.length);
            }
          }
          (target||chunk).scrollIntoView({block:'center',behavior:'auto'});
          return true;
    """.trimIndent()
    return if (needsWait) {
        """
        (function(){
          $injection
          function attempt(){
        $highlightBody
          }
          if(attempt())return;
          var tries=0;var timer=window.setInterval(function(){tries++;try{if(attempt()){window.clearInterval(timer);return;}}catch(_){}if(tries>=40)window.clearInterval(timer);},125);
        })();
        """.trimIndent()
    } else {
        """
        (function(){
          $injection
        $highlightBody
        })();
        """.trimIndent()
    }
}






internal fun ReaderSettings.readerBackgroundColor(): Color {
    val value = backgroundColorArgb ?: if (darkMode) 0xFF121212L else 0xFFFFFFFFL
    return Color((value and 0xFFFFFFFFL).toInt())
}

internal fun ReaderSettings.readerTextColor(): Color {
    val value = textColorArgb ?: if (darkMode) 0xFFE0E0E0L else 0xFF000000L
    return Color((value and 0xFFFFFFFFL).toInt())
}

internal fun ReaderSettings.readerPageInfoBackgroundColor(): Color {
    val base = readerBackgroundColor()
    val overlayAlpha = if (darkMode) 0.08f else 0.06f
    val overlay = if (darkMode) Color.White else Color.Black
    return Color(
        red = overlay.red * overlayAlpha + base.red * (1f - overlayAlpha),
        green = overlay.green * overlayAlpha + base.green * (1f - overlayAlpha),
        blue = overlay.blue * overlayAlpha + base.blue * (1f - overlayAlpha),
        alpha = 0.95f
    )
}
