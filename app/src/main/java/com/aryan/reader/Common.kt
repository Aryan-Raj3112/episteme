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
package com.aryan.reader

import android.content.Context
import timber.log.Timber
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.tts.rememberTtsController
import com.aryan.reader.tts.splitTextIntoChunks
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.ListItem
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import java.io.File
import androidx.compose.runtime.DisposableEffect

const val aiServerBasePath = BuildConfig.AI_WORKER_URL
const val summarizeEndpoint = "/summarize"
const val summarizationUrl = aiServerBasePath + summarizeEndpoint
const val defineEndpoint = "/define"
const val aiDefinitionUrl = aiServerBasePath + defineEndpoint
const val recapEndpoint = "/recap"
const val recapUrl = aiServerBasePath + recapEndpoint

data class SearchResult(
    val locationInSource: Int,
    val locationTitle: String,
    val snippet: AnnotatedString,
    val query: String,
    val occurrenceIndexInLocation: Int,
    val chunkIndex: Int
)

data class AiDefinitionResult(
    val definition: String? = null,
    val error: String? = null
)

data class SummarizationResult(
    val summary: String? = null,
    val error: String? = null
)

@Stable
class SearchState(
    private val scope: CoroutineScope,
    private val searcher: suspend (String) -> List<SearchResult>
) {
    var isSearchActive by mutableStateOf(false)
    var showSearchResultsPanel by mutableStateOf(true)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<SearchResult>>(emptyList())
    var isSearchInProgress by mutableStateOf(false)
    var currentSearchResultIndex by mutableIntStateOf(-1)

    val searchResultsCount by derivedStateOf { searchResults.size }
    val hasResults by derivedStateOf { searchResults.isNotEmpty() }

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        searchQuery = newQuery
        searchJob?.cancel()
        searchJob = scope.launch {
            if (newQuery.isBlank()) {
                searchResults = emptyList()
                currentSearchResultIndex = -1
                isSearchInProgress = false
                return@launch
            }
            delay(350)
            showSearchResultsPanel = true
            isSearchInProgress = true
            currentSearchResultIndex = -1
            searchResults = searcher(newQuery)
            isSearchInProgress = false
        }
    }

    fun forceSearch() {
        searchJob?.cancel()
        searchJob = scope.launch {
            if (searchQuery.isBlank()) {
                searchResults = emptyList()
                currentSearchResultIndex = -1
                isSearchInProgress = false
                return@launch
            }
            showSearchResultsPanel = true
            isSearchInProgress = true
            currentSearchResultIndex = -1
            searchResults = searcher(searchQuery)
            isSearchInProgress = false
        }
    }
}

@Composable
fun rememberSearchState(
    scope: CoroutineScope,
    searcher: suspend (String) -> List<SearchResult>
): SearchState {
    return remember {
        SearchState(scope, searcher)
    }
}

@Composable
fun SearchTopBar(
    searchState: SearchState,
    focusRequester: FocusRequester,
    onCloseSearch: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCloseSearch) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close Search"
                )
            }

            TextField(
                value = searchState.searchQuery,
                onValueChange = { searchState.onQueryChange(it) },
                placeholder = { Text("Search in book...") },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag("SearchTextField"),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    searchState.forceSearch()
                    keyboardController?.hide()
                    focusManager.clearFocus()
                })
            )

            if (searchState.searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchState.onQueryChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear Search"
                    )
                }
            }

            IconButton(onClick = {
                searchState.showSearchResultsPanel = !searchState.showSearchResultsPanel
                focusManager.clearFocus()
            }) {
                Icon(
                    imageVector = if (searchState.showSearchResultsPanel) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = if (searchState.showSearchResultsPanel) "Hide Results" else "Show Results"
                )
            }
        }
    }
}

@Composable
fun SearchNavigationControls(
    searchState: SearchState,
    onNavigate: (Int) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            IconButton(
                onClick = { onNavigate(searchState.currentSearchResultIndex - 1) },
                enabled = searchState.currentSearchResultIndex > 0
            ) {
                Icon(Icons.Default.ArrowDropUp, contentDescription = "Previous Search Result")
            }

            Text(
                text = "${searchState.currentSearchResultIndex + 1}/${searchState.searchResultsCount}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            IconButton(
                onClick = { onNavigate(searchState.currentSearchResultIndex + 1) },
                enabled = searchState.currentSearchResultIndex < searchState.searchResultsCount - 1
            ) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Next Search Result")
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SummarizationPopup(
    title: String,
    result: SummarizationResult?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    isMainTtsActive: Boolean = false,
) {
    val ttsController = rememberTtsController()
    val ttsState by ttsController.ttsState.collectAsState()
    LocalContext.current
    @Suppress("DEPRECATION") val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            if (ttsState.playbackSource == "POPUP" && (ttsState.isPlaying || ttsState.isLoading)) {
                ttsController.stop()
            }
        }
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .heightIn(min = 150.dp, max = 500.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(all = 20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isLoading && (result?.summary.isNullOrBlank() && result?.error.isNullOrBlank())) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Text("Generating summary...", modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                } else if (result != null) {
                    val summaryText = result.summary
                    val errorText = result.error

                    val styledContent = remember(summaryText, errorText) {
                        if (!summaryText.isNullOrBlank()) {
                            MarkdownParser.parse(summaryText)
                        } else {
                            AnnotatedString(errorText ?: "")
                        }
                    }
                    val textToUse = styledContent.text

                    if (textToUse.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isTtsSessionActive = ttsState.currentText != null || ttsState.isLoading

                            IconButton(
                                onClick = {
                                    if (isTtsSessionActive) {
                                        ttsController.stop()
                                    } else {
                                        val chunks = splitTextIntoChunks(textToUse).map {
                                            TtsChunk(it, "", -1)
                                        }
                                        if (chunks.isNotEmpty()) {
                                            ttsController.start(
                                                chunks = chunks,
                                                bookTitle = title,
                                                chapterTitle = "Summary",
                                                coverImageUri = null,
                                                ttsMode = "BASE",
                                                playbackSource = "POPUP"
                                            )
                                        }
                                    }
                                },
                                enabled = !isMainTtsActive || (ttsState.playbackSource == "POPUP")
                            ) {
                                Icon(
                                    imageVector = if (isTtsSessionActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isTtsSessionActive) "Stop" else "Read aloud"
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(textToUse))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy"
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (errorText != null && summaryText.isNullOrBlank()) {
                        Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                    } else if (textToUse.isNotBlank()) {
                        val scrollState = rememberScrollState()
                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                        LaunchedEffect(ttsState.currentText, textLayoutResult) {
                            val currentChunk = ttsState.currentText
                            val layoutResult = textLayoutResult
                            if (!currentChunk.isNullOrBlank() && layoutResult != null) {
                                val startIndex = textToUse.indexOf(currentChunk)
                                if (startIndex != -1) {
                                    val line = layoutResult.getLineForOffset(startIndex)
                                    val lineTop = layoutResult.getLineTop(line)
                                    val viewportHeight = scrollState.viewportSize
                                    val targetScroll = (lineTop - viewportHeight / 2).coerceAtLeast(0f)
                                    scope.launch {
                                        scrollState.animateScrollTo(targetScroll.toInt())
                                    }
                                }
                            }
                        }

                        val annotatedText = buildAnnotatedString {
                            append(styledContent)
                            val currentChunk = ttsState.currentText
                            if (!currentChunk.isNullOrBlank()) {
                                val startIndex = textToUse.indexOf(currentChunk)
                                if (startIndex != -1) {
                                    addStyle(
                                        style = SpanStyle(background = MaterialTheme.colorScheme.primaryContainer),
                                        start = startIndex,
                                        end = startIndex + currentChunk.length
                                    )
                                }
                            }
                        }
                        Text(
                            text = annotatedText,
                            modifier = Modifier.verticalScroll(scrollState),
                            onTextLayout = { textLayoutResult = it }
                        )
                    } else {
                        Text("No summary could be generated.", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun AiDefinitionPopup(
    word: String?,
    result: AiDefinitionResult?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    isMainTtsActive: Boolean = false
) {
    val ttsController = rememberTtsController()
    val ttsState by ttsController.ttsState.collectAsState()
    LocalContext.current
    @Suppress("DEPRECATION") val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            if (ttsState.playbackSource == "POPUP" && (ttsState.isPlaying || ttsState.isLoading)) {
                ttsController.stop()
            }
        }
    }

    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .heightIn(min = 150.dp, max = 400.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(all = 20.dp)) {
                if (isLoading && (result?.definition.isNullOrBlank() && result?.error.isNullOrBlank())) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Text("Thinking...", modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                } else if (result != null) {
                    word?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    val definitionText = result.definition
                    val errorText = result.error

                    val styledContent = remember(definitionText, errorText) {
                        if (!definitionText.isNullOrBlank()) {
                            MarkdownParser.parse(definitionText)
                        } else {
                            AnnotatedString(errorText ?: "")
                        }
                    }

                    val textToUse = styledContent.text

                    if (textToUse.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            definitionText ?: errorText ?: ""
                            val isTtsSessionActive = ttsState.currentText != null || ttsState.isLoading

                            IconButton(
                                onClick = {
                                    if (isTtsSessionActive) {
                                        ttsController.stop()
                                    } else {
                                        val chunks = splitTextIntoChunks(textToUse).map {
                                            TtsChunk(it, "", -1)
                                        }
                                        if (chunks.isNotEmpty()) {
                                            ttsController.start(
                                                chunks = chunks,
                                                bookTitle = "AI Definition",
                                                chapterTitle = word,
                                                coverImageUri = null,
                                                ttsMode = "BASE",
                                                playbackSource = "POPUP"
                                            )
                                        }
                                    }
                                },
                                enabled = !isMainTtsActive || (ttsState.playbackSource == "POPUP")
                            ) {
                                Icon(
                                    imageVector = if (isTtsSessionActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isTtsSessionActive) "Stop" else "Read aloud"
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(textToUse))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy"
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (errorText != null && definitionText.isNullOrBlank()) {
                        Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                    } else if (textToUse.isNotBlank()) {
                        val scrollState = rememberScrollState()
                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                        LaunchedEffect(ttsState.currentText, textLayoutResult) {
                            val currentChunk = ttsState.currentText
                            val layoutResult = textLayoutResult
                            if (!currentChunk.isNullOrBlank() && layoutResult != null) {
                                val startIndex = textToUse.indexOf(currentChunk)
                                if (startIndex != -1) {
                                    val line = layoutResult.getLineForOffset(startIndex)
                                    val lineTop = layoutResult.getLineTop(line)
                                    val viewportHeight = scrollState.viewportSize
                                    val targetScroll = (lineTop - viewportHeight / 2).coerceAtLeast(0f)
                                    scope.launch {
                                        scrollState.animateScrollTo(targetScroll.toInt())
                                    }
                                }
                            }
                        }

                        val annotatedText = buildAnnotatedString {
                            append(styledContent)
                            val currentChunk = ttsState.currentText
                            if (!currentChunk.isNullOrBlank()) {
                                val startIndex = textToUse.indexOf(currentChunk)
                                if (startIndex != -1) {
                                    addStyle(
                                        style = SpanStyle(background = MaterialTheme.colorScheme.primaryContainer),
                                        start = startIndex,
                                        end = startIndex + currentChunk.length
                                    )
                                }
                            }
                        }
                        Text(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.verticalScroll(scrollState),
                            onTextLayout = { textLayoutResult = it }
                        )
                    } else {
                        Text("AI could not provide a definition.", style = MaterialTheme.typography.bodyLarge)
                    }
                } else if (word != null) {
                    Text(
                        text = "Asking AI about '$word'...",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 24.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultsPanel(
    results: List<SearchResult>,
    isSearching: Boolean,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            isSearching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No results found.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                Column {
                    Text(
                        text = "${results.size} " + if (results.size == 1) "result found" else "results found",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.testTag("SearchResultsList")) {
                        items(results.size) { index ->
                            val result = results[index]
                            ListItem(
                                headlineContent = { Text(result.locationTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(result.snippet, style = MaterialTheme.typography.bodyMedium) },
                                modifier = Modifier
                                    .clickable { onResultClick(result) }
                                    .testTag("SearchResultItem_${result.locationInSource}")
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

suspend fun fetchAiDefinition(
    text: String,
    onUpdate: (String) -> Unit,
    onError: (String) -> Unit,
    onFinish: () -> Unit
) {
    if (text.isBlank()) {
        onError("Text is empty.")
        onFinish()
        return
    }
    Timber.d("Fetching AI definition for: '$text'")

    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(aiDefinitionUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.doInput = true

            val jsonPayload = JSONObject().apply { put("text", text) }
            connection.outputStream.use { os ->
                os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            Timber.d("Definition: Got response code $responseCode")
            if (responseCode == HttpURLConnection.HTTP_OK) {
                var hasReceivedData = false
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Timber.d("Definition: Received line: $line")
                        try {
                            val jsonResponse = JSONObject(line!!)
                            jsonResponse.optString("chunk").takeIf { it.isNotEmpty() }?.let {
                                Timber.d("Definition: Parsed chunk, calling onUpdate.")
                                onUpdate(it)
                                hasReceivedData = true
                            }
                            jsonResponse.optString("error").takeIf { it.isNotEmpty() }?.let {
                                onError(it)
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Could not parse stream line: $line")
                        }
                    }
                }
                Timber.d("Definition: Finished reading stream.")
                if (!hasReceivedData) {
                    onError("AI returned an empty definition.")
                }
            } else {
                val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
                val errorDetail = try { errorBody?.let { JSONObject(it).getString("detail") } } catch (_: Exception) { "Could not get definition." }
                onError("Error: $responseCode. ${errorDetail ?: "An unknown server error occurred."}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Network error fetching AI definition: ${e.message}")
            onError("Network error. Check connection.")
        } finally {
            connection?.disconnect()
            onFinish()
        }
    }
}

fun countWords(text: String): Int {
    return text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
}

object MarkdownParser {
    fun parse(markdown: String): AnnotatedString {
        val parser = Parser.builder().build()
        val document = parser.parse(markdown)
        val builder = AnnotatedString.Builder()

        val visitor = object : AbstractVisitor() {
            override fun visit(text: Text) {
                builder.append(text.literal)
            }

            override fun visit(emphasis: Emphasis) {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                visitChildren(emphasis)
                builder.pop()
            }

            override fun visit(strongEmphasis: StrongEmphasis) {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                visitChildren(strongEmphasis)
                builder.pop()
            }

            override fun visit(paragraph: Paragraph) {
                visitChildren(paragraph)
                // Add newline if it's not the last node
                if (paragraph.next != null) {
                    builder.append("\n\n")
                }
            }

            override fun visit(heading: Heading) {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                visitChildren(heading)
                builder.pop()
                builder.append("\n\n")
            }

            override fun visit(softLineBreak: SoftLineBreak) {
                builder.append(" ")
            }

            override fun visit(hardLineBreak: HardLineBreak) {
                builder.append("\n")
            }

            override fun visit(code: Code) {
                builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22888888)))
                builder.append(code.literal)
                builder.pop()
            }

            override fun visit(listItem: ListItem) {
                builder.append("• ")
                visitChildren(listItem)
                if (listItem.next != null) {
                    builder.append("\n")
                }
            }
        }

        document.accept(visitor)
        return builder.toAnnotatedString()
    }
}

class SummaryCacheManager(context: Context) {
    private val cacheDir = File(context.cacheDir, "chapter_summaries")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    private fun getFileName(bookTitle: String, chapterIndex: Int): String {
        // Sanitize title to be file-system safe
        val safeTitle = bookTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        return "summary_${safeTitle}_$chapterIndex.txt"
    }

    fun saveSummary(bookTitle: String, chapterIndex: Int, summary: String) {
        try {
            val file = File(cacheDir, getFileName(bookTitle, chapterIndex))
            file.writeText(summary)
            Timber.d("Saved summary for $bookTitle Ch $chapterIndex")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save summary")
        }
    }

    fun getSummary(bookTitle: String, chapterIndex: Int): String? {
        return try {
            val file = File(cacheDir, getFileName(bookTitle, chapterIndex))
            if (file.exists()) file.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    fun hasSummary(bookTitle: String, chapterIndex: Int): Boolean {
        val file = File(cacheDir, getFileName(bookTitle, chapterIndex))
        return file.exists()
    }
}

suspend fun fetchRecap(
    pastSummaries: List<String>,
    currentText: String,
    onUpdate: (String) -> Unit,
    onError: (String) -> Unit,
    onFinish: () -> Unit
) {
    if (pastSummaries.isEmpty() && currentText.isBlank()) {
        onError("Not enough context for a recap.")
        onFinish()
        return
    }

    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(recapUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15000
            connection.readTimeout = 120000
            connection.doOutput = true
            connection.doInput = true

            val jsonPayload = JSONObject().apply {
                put("past_summaries", org.json.JSONArray(pastSummaries))
                put("current_text", currentText)
            }

            connection.outputStream.use { os ->
                os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                var hasReceivedData = false
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        try {
                            val jsonResponse = JSONObject(line!!)
                            jsonResponse.optString("chunk").takeIf { it.isNotEmpty() }?.let {
                                onUpdate(it)
                                hasReceivedData = true
                            }
                            jsonResponse.optString("error").takeIf { it.isNotEmpty() }?.let {
                                onError(it)
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Could not parse stream line: $line")
                        }
                    }
                }
                if (!hasReceivedData) onError("Failed to parse recap.")
            } else {
                val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
                onError("Error: $responseCode. ${errorBody ?: ""}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Recap error: ${e.message}")
            onError("Network error during recap generation.")
        } finally {
            connection?.disconnect()
            onFinish()
        }
    }
}