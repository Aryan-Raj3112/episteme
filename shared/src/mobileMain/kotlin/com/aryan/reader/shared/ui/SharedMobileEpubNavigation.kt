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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderSearchFocusDelayMillis
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.readerWordStartMatchOffsets
import com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubTocEntry
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
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search in book") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
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
                        Text("No results found", style = MaterialTheme.typography.bodyLarge)
                    }
                    else -> Column {
                        Text(
                            "${results.size} results",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        HorizontalDivider()
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(results) { result ->
                                Column(
                                    Modifier.fillMaxWidth().clickable {
                                        onResultClick(result)
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }.padding(horizontal = 20.dp, vertical = 14.dp)
                                ) {
                                    Text(result.chapterTitle, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(result.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
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
            IconButton(onClick = onPrevious, enabled = current > 0) { Icon(Icons.Default.ArrowDropUp, contentDescription = "Previous result") }
            Text("${current + 1}/$total", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onNext, enabled = current < total - 1) { Icon(Icons.Default.ArrowDropDown, contentDescription = "Next result") }
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Jump back", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(backLabel.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Close, contentDescription = "Clear jump history", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear", maxLines = 1)
            }
            TextButton(onClick = onForward, enabled = forwardLabel != null, modifier = Modifier.weight(1f)) {
                Text(forwardLabel.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Jump forward", modifier = Modifier.size(16.dp))
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
                            snippet = text.substring(snippetStart, snippetEnd).trim(),
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
    val textBlock = semanticBlocks
        .flatMap { it.flattenForLocator() }
        .filterIsInstance<SemanticTextBlock>()
        .firstOrNull { it.text.isNotBlank() }
    val localCharOffset = 0
    val androidStyleCfi = textBlock?.cfi
        ?.takeIf { it.startsWith("/") }
        ?.let { "$it:$localCharOffset" }
    return ReaderLocator(
        chapterIndex = chapterIndex,
        chapterId = chapter?.id,
        href = chapter?.baseHref,
        pageIndex = pageIndex,
        startOffset = startOffset,
        endOffset = startOffset,
        textQuote = text.take(120),
        blockIndex = textBlock?.blockIndex,
        charOffset = textBlock?.startCharOffsetInSource,
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
    targetChunkHtml: String?
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
    val chunkInjection = if (targetChunkIndex != null && targetChunkHtml != null) {
        "if (window.readerVirtualization) window.readerVirtualization.provideChunk($targetChunkIndex, ${JsonPrimitive(targetChunkHtml)});"
    } else {
        ""
    }
    return """
        (function () {
          var locator = $locatorJson;
          var fragment = $fragmentJson;
          $chunkInjection
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
              return;
            }
          }
          if (window.readerScrollToLocator) window.readerScrollToLocator(locator, { source: 'ios_mobile' });
        })();
    """.trimIndent()
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
    val injection = chunkHtml?.let { "if(window.readerVirtualization)window.readerVirtualization.provideChunk(${result.chunkIndex},${JsonPrimitive(it)});" }.orEmpty()
    return """
        (function(){
          $injection
          var chunk=document.querySelector('[data-reader-chunk-index="${result.chunkIndex}"]');
          var query=${JsonPrimitive(query)};
          document.querySelectorAll('.reader-ios-search-hit').forEach(function(hit){
            var parent=hit.parentNode; while(hit.firstChild)parent.insertBefore(hit.firstChild,hit); parent.removeChild(hit); parent.normalize();
          });
          if(!chunk)return;
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
        })();
    """.trimIndent()
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
