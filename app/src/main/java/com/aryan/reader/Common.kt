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
package com.aryan.reader

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.tts.GOOGLE_TTS_SPEAKERS
import com.aryan.reader.tts.SpeakerSamplePlayer
import com.aryan.reader.tts.TtsPlaybackManager
import com.aryan.reader.tts.loadTtsMode
import com.aryan.reader.tts.rememberTtsController
import com.aryan.reader.tts.splitTextIntoChunks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.content.edit

const val aiServerBasePath = BuildConfig.AI_WORKER_URL
const val summarizeEndpoint = "/summarize"
const val summarizationUrl = aiServerBasePath + summarizeEndpoint
const val defineEndpoint = "/define"
const val aiDefinitionUrl = aiServerBasePath + defineEndpoint
const val recapEndpoint = "/recap"
const val recapUrl = aiServerBasePath + recapEndpoint

const val PREF_NATIVE_TTS_VOICE = "native_tts_voice_name"

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
    val context = LocalContext.current
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
                                                ttsMode = loadTtsMode(context),
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
    isMainTtsActive: Boolean = false,
    onOpenExternalDictionary: () -> Unit
) {
    val ttsController = rememberTtsController()
    val ttsState by ttsController.ttsState.collectAsState()
    val context = LocalContext.current
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
                                                ttsMode = loadTtsMode(context),
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
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = onOpenExternalDictionary) {
                                Icon(
                                    painter = painterResource(id = R.drawable.dictionary),
                                    contentDescription = "Open in Dictionary App"
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

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    currentMode: TtsPlaybackManager.TtsMode,
    onModeChange: (TtsPlaybackManager.TtsMode) -> Unit,
    currentSpeakerId: String,
    onSpeakerChange: (String) -> Unit,
    isTtsActive: Boolean
) {
    if (isVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        rememberLazyListState()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val samplePlayer = remember(context, scope) { SpeakerSamplePlayer(context, scope) }

        DisposableEffect(Unit) {
            onDispose { samplePlayer.release() }
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = { WindowInsets.navigationBars }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Text-to-Speech Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isTtsActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Please stop playback to change settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Text(
                    text = "Synthesis Mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Mode Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(25.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TtsPlaybackManager.TtsMode.entries.forEach { mode ->
                        val isSelected = currentMode == mode
                        val label = if (mode == TtsPlaybackManager.TtsMode.BASE) "On-Device" else "Cloud (HQ)"
                        val icon = if (mode == TtsPlaybackManager.TtsMode.BASE) Icons.Default.Smartphone else Icons.Default.Cloud

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(enabled = !isTtsActive) { onModeChange(mode) },
                            shape = RoundedCornerShape(25.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                if (currentMode == TtsPlaybackManager.TtsMode.CLOUD) {
                    Text(
                        text = "Voice Selection",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    ) {
                        items(GOOGLE_TTS_SPEAKERS) { (name, id) ->
                            val isSelected = currentSpeakerId == id
                            val isPlaying = samplePlayer.playingSpeakerId == id
                            val isLoading = samplePlayer.loadingSpeakerId == id

                            ListItem(
                                headlineContent = { Text(name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                leadingContent = {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    }
                                },
                                trailingContent = {
                                    if (!isTtsActive) {
                                        IconButton(onClick = { samplePlayer.playOrStop(id) }) {
                                            if (isLoading) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                    contentDescription = "Play Sample",
                                                    tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
                                ),
                                modifier = Modifier.clickable(enabled = !isTtsActive) {
                                    onSpeakerChange(id)
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Using system default engine settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

fun loadNativeVoice(context: Context): String? {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getString(PREF_NATIVE_TTS_VOICE, null)
}

private fun saveNativeVoice(context: Context, @Suppress("SameParameterValue") voiceName: String?) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    if (voiceName == null) {
        prefs.edit { remove(PREF_NATIVE_TTS_VOICE) }
    } else {
        prefs.edit { putString(PREF_NATIVE_TTS_VOICE, voiceName) }
    }
}

@Composable
fun DeviceVoiceSettingsSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        val listState = rememberLazyListState()
        val context = LocalContext.current
        @Suppress("UnusedVariable", "Unused") val scope = rememberCoroutineScope()

        var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
        var allVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
        var isTtsLoading by remember { mutableStateOf(true) }

        var savedVoiceName by remember { mutableStateOf(loadNativeVoice(context)) }

        val allLanguagesOption = "All Languages"
        var selectedLanguage by remember { mutableStateOf(allLanguagesOption) }

        val numberedVoiceNames = remember(allVoices) {
            val nameMap = mutableMapOf<String, String>()

            val groupedByLanguage = allVoices.groupBy { it.locale.displayName }

            groupedByLanguage.forEach { (langName, voiceList) ->
                if (voiceList.size > 1) {
                    voiceList.forEachIndexed { index, voice ->
                        val type = try {
                            if (voice.isNetworkConnectionRequired) "Online" else "Offline"
                        } catch (_: Exception) {
                            "Offline"
                        }

                        nameMap[voice.name] = "$langName ($type) - ${index + 1}"
                    }
                } else {
                    val voice = voiceList[0]
                    val type = try {
                        if (voice.isNetworkConnectionRequired) "Online" else "Offline"
                    } catch (_: Exception) {
                        "Offline"
                    }

                    nameMap[voice.name] = "$langName ($type)"
                }
            }
            nameMap
        }

        DisposableEffect(Unit) {
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        val enginesVoices = tts?.voices
                        if (enginesVoices != null) {
                            allVoices = enginesVoices.toList().sortedBy { it.locale.displayName }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to fetch voices")
                    } finally {
                        isTtsLoading = false
                    }
                } else {
                    isTtsLoading = false
                    Timber.e("TTS Initialization failed with status $status")
                }
            }
            ttsEngine = tts
            onDispose {
                tts.shutdown()
            }
        }

        val availableLanguages = remember(allVoices) {
            val languages =
                allVoices.asSequence().map { it.locale.displayLanguage }.filter { it.isNotBlank() }
                    .distinct().sorted().toMutableList()

            languages.add(0, allLanguagesOption)
            languages
        }

        LaunchedEffect(allVoices, savedVoiceName) {
            if (savedVoiceName != null && allVoices.isNotEmpty()) {
                val savedVoice = allVoices.find { it.name == savedVoiceName }
                if (savedVoice != null) {
                    val voiceLanguage = savedVoice.locale.displayLanguage
                    if (selectedLanguage == allLanguagesOption) {
                        selectedLanguage = voiceLanguage
                    }
                }
            }
        }

        val filteredVoices = remember(selectedLanguage, allVoices) {
            if (selectedLanguage == allLanguagesOption) {
                allVoices
            } else {
                allVoices.filter { it.locale.displayLanguage == selectedLanguage }
            }
        }

        LaunchedEffect(filteredVoices, savedVoiceName) {
            if (savedVoiceName != null && filteredVoices.isNotEmpty()) {
                val index = filteredVoices.indexOfFirst { it.name == savedVoiceName }
                if (index != -1) {
                    Timber.d("Auto-scrolling to voice at index: $index")
                    delay(300)
                    listState.animateScrollToItem(index)
                }
            }
        }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                )

                Surface(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 24.dp).padding(bottom = 24.dp, top = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "On-Device Voice Settings",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close Settings")
                            }
                        }

                        Surface(
                            color = if (savedVoiceName == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = if (savedVoiceName == null) 4.dp else 0.dp,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable {
                                savedVoiceName = null
                                saveNativeVoice(context, null)
                                Timber.d("Native TTS: Reset to System Default")
                            }) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = if (savedVoiceName == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "System Default",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Matches your Android system settings",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (savedVoiceName == null) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

                        if (isTtsLoading) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Loading voices...", modifier = Modifier.padding(top = 48.dp))
                            }
                        } else if (allVoices.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No voices available on this device.",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            var expandedLanguageMenu by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Specific Voices",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                        .clickable { expandedLanguageMenu = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = selectedLanguage,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = expandedLanguageMenu,
                                    onDismissRequest = { expandedLanguageMenu = false },
                                    modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 400.dp)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    availableLanguages.forEach { language ->
                                        DropdownMenuItem(text = {
                                            Text(
                                                text = language,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }, onClick = {
                                            selectedLanguage = language
                                            expandedLanguageMenu = false
                                        })
                                    }
                                }
                            }

                            if (filteredVoices.isNotEmpty()) {
                                Text(
                                    text = "Available Voices (${filteredVoices.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                                )

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                                        .padding(vertical = 4.dp).border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    items(
                                        filteredVoices.size,
                                        key = { filteredVoices[it].name }) { index ->
                                        val voice = filteredVoices[index]
                                        val isSelected = voice.name == savedVoiceName
                                        val friendlyName = numberedVoiceNames[voice.name]
                                            ?: voice.locale.displayName

                                        ListItem(
                                            headlineContent = {
                                            Text(
                                                text = friendlyName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                            supportingContent = if (voice.locale.variant.isNotEmpty()) {
                                                { Text("Variant: ${voice.locale.variant}") }
                                            } else null,
                                            leadingContent = {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.size(24.dp))
                                                }
                                            },
                                            trailingContent = {
                                                IconButton(onClick = {
                                                    val params = android.os.Bundle()
                                                    ttsEngine?.voice = voice
                                                    val sampleText =
                                                        "This is a sample of ${voice.locale.displayLanguage}."
                                                    ttsEngine?.speak(
                                                        sampleText,
                                                        TextToSpeech.QUEUE_FLUSH,
                                                        params,
                                                        "SAMPLE_ID"
                                                    )
                                                }) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Play Sample",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            },
                                            modifier = Modifier.clickable {
                                                savedVoiceName = voice.name
                                                saveNativeVoice(context, voice.name)
                                            }.background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.2f
                                                ) else Color.Transparent
                                            )
                                        )
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                alpha = 0.5f
                                            )
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No voices found for this language.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}