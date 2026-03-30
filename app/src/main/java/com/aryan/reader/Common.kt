// Common.kt
@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.aryan.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.epubreader.PREF_CUSTOM_THEMES
import com.aryan.reader.epubreader.PREF_READER_THEME
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
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

private val activeTooltipState = mutableStateOf<androidx.compose.material3.TooltipState?>(null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
    content: @Composable () -> Unit
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible) {
            val previous = activeTooltipState.value
            if (previous != null && previous !== tooltipState) {
                previous.dismiss()
            }
            activeTooltipState.value = tooltipState
        } else {
            if (activeTooltipState.value === tooltipState) {
                activeTooltipState.value = null
            }
        }
    }

    TooltipBox(
        positionProvider = if (description != null)
            TooltipDefaults.rememberRichTooltipPositionProvider()
        else
            TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            if (description != null) {
                RichTooltip(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            content()
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    colors = TooltipDefaults.richTooltipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                PlainTooltip {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        content()
                        Text(text)
                    }
                }
            }
        },
        state = tooltipState
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        ) {
            content()
        }
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
            TooltipIconButton(
                text = stringResource(R.string.tooltip_close_search),
                description = stringResource(R.string.tooltip_close_search_desc),
                onClick = onCloseSearch
            ) {
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
                TooltipIconButton(
                    text = stringResource(R.string.tooltip_clear_search),
                    description = stringResource(R.string.tooltip_clear_search_desc),
                    onClick = { searchState.onQueryChange("") }
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear Search"
                    )
                }
            }

            TooltipIconButton(
                text = if (searchState.showSearchResultsPanel)
                    stringResource(R.string.tooltip_hide_results)
                else
                    stringResource(R.string.tooltip_show_results),
                description = if (searchState.showSearchResultsPanel)
                    stringResource(R.string.tooltip_hide_results_desc)
                else
                    stringResource(R.string.tooltip_show_results_desc),
                onClick = {
                    searchState.showSearchResultsPanel = !searchState.showSearchResultsPanel
                    focusManager.clearFocus()
                }
            ) {
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
            TooltipIconButton(
                text = stringResource(R.string.tooltip_prev_result),
                description = stringResource(R.string.tooltip_prev_result_desc),
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

            TooltipIconButton(
                text = stringResource(R.string.tooltip_next_result),
                description = stringResource(R.string.tooltip_next_result_desc),
                onClick = { onNavigate(searchState.currentSearchResultIndex + 1) },
                enabled = searchState.currentSearchResultIndex < searchState.searchResultsCount - 1
            ) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Next Search Result")
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
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

@androidx.annotation.OptIn(UnstableApi::class)
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

@androidx.annotation.OptIn(UnstableApi::class)
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
                                                    try {
                                                        ttsEngine?.language = voice.locale
                                                    } catch (e: Exception) {
                                                        Timber.e(e, "Failed to set language for sample")
                                                    }
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

@Composable
fun SpectrumBox(
    hue: Float,
    saturation: Float,
    currentColor: Color,
    onHueSatChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbowColors = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
    )
    val touchPadding = 12.dp

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()

                val paddingPx = touchPadding.toPx()
                val activeWidth = size.width.toFloat() - (paddingPx * 2)
                val activeHeight = size.height.toFloat() - (paddingPx * 2)

                fun update(offset: Offset) {
                    val relativeX = offset.x - paddingPx
                    val relativeY = offset.y - paddingPx

                    val h = (relativeX / activeWidth).coerceIn(0f, 1f) * 360f
                    val s = (relativeY / activeHeight).coerceIn(0f, 1f)
                    onHueSatChanged(h, s)
                }

                update(down.position)
                drag(down.id) { change ->
                    change.consume()
                    update(change.position)
                }
            }
        }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(touchPadding)
                .clip(RoundedCornerShape(12.dp))
        ) {
            drawRect(
                brush = Brush.horizontalGradient(rainbowColors)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0f))
                )
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingPx = touchPadding.toPx()
            val activeWidth = size.width - (paddingPx * 2)
            val activeHeight = size.height - (paddingPx * 2)

            val x = paddingPx + (hue / 360f) * activeWidth
            val y = paddingPx + saturation * activeHeight

            val pointerRadius = 10.dp.toPx()
            val strokeWidth = 2.dp.toPx()

            drawCircle(
                color = Color.Black.copy(alpha = 0.25f),
                radius = pointerRadius + 1.dp.toPx(),
                center = Offset(x, y + 1.dp.toPx())
            )

            drawCircle(
                color = currentColor.copy(alpha = 1f),
                radius = pointerRadius,
                center = Offset(x, y)
            )

            drawCircle(
                color = Color.White,
                radius = pointerRadius,
                center = Offset(x, y),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

@Composable
fun BrightnessSlider(
    hue: Float,
    saturation: Float,
    value: Float,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = remember(hue, saturation) {
        Color.hsv(hue, saturation, 1f)
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                fun update(offset: Offset) {
                    val v = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onValueChanged(v)
                }
                update(down.position)
                drag(down.id) { change ->
                    change.consume()
                    update(change.position)
                }
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, baseColor)
                )
            )

            val x = value * size.width
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(x, size.height / 2)
            )
        }
    }
}

@Composable
fun RgbInputColumn(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val intValue = (value * 255).roundToInt()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 11.sp,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        RgbInput(value = intValue, onValueChange = onValueChange)
    }
}

@Composable
fun RgbInput(
    value: Int,
    onValueChange: (Float) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    LaunchedEffect(value) {
        text = value.toString()
    }

    BasicTextField(
        value = text,
        onValueChange = { newText ->
            if (newText.length <= 3 && newText.all { it.isDigit() }) {
                val intVal = newText.toIntOrNull()
                if (intVal != null) {
                    onValueChange(intVal.coerceIn(0, 255) / 255f)
                }
            }
        },
        textStyle = TextStyle(
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFF3E3E3E), RoundedCornerShape(8.dp))
            .padding(vertical = 9.dp)
    )
}

@Composable
fun HexInput(
    color: Color,
    onHexChanged: (Color) -> Unit
) {
    val hexValue = remember(color) {
        String.format("%06X", (0xFFFFFF and color.toArgb()))
    }
    var text by remember(hexValue) { mutableStateOf(hexValue) }

    LaunchedEffect(color) {
        val currentParsed = try {
            Color(("#$text").toColorInt())
        } catch (_: Exception) {
            null
        }
        if (currentParsed?.toArgb() != color.toArgb()) {
            text = String.format("%06X", (0xFFFFFF and color.toArgb()))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFF3E3E3E), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "#",
            color = Color.Gray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        BasicTextField(
            value = text,
            onValueChange = { newText ->
                if (newText.length <= 6) {
                    val uppercased = newText.uppercase()
                    if (uppercased.all { it.isDigit() || it in 'A'..'F' }) {
                        text = uppercased
                        if (uppercased.length == 6) {
                            try {
                                val parsedColorInt = "#$uppercased".toColorInt()
                                val newColor = Color(parsedColorInt)
                                onHexChanged(newColor)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            },
            textStyle = TextStyle(
                color = Color.White,
                textAlign = TextAlign.Start,
                fontSize = 13.sp
            ),
            singleLine = true,
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .padding(start = 2.dp)
                .width(50.dp)
        )
    }
}

@Composable
fun ColorComparePill(
    oldColor: Color,
    newColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
        drawRect(
            color = oldColor.copy(alpha = 1f),
            size = androidx.compose.ui.geometry.Size(size.width / 2, size.height)
        )
        drawRect(
            color = newColor.copy(alpha = 1f),
            topLeft = Offset(size.width / 2, 0f),
            size = androidx.compose.ui.geometry.Size(size.width / 2, size.height)
        )
    }
}

enum class ReaderTexture(val id: String, val resId: Int, val displayName: String) {
    PAPER("paper", R.drawable.texture_paper, "Paper"),
    CANVAS("canvas", R.drawable.texture_canvas, "Canvas"),
    EINK("eink", R.drawable.texture_eink, "E-Ink"),
    SLATE("slate", R.drawable.texture_slate, "Slate")
}

data class ReaderTheme(
    val id: String,
    val name: String,
    val backgroundColor: Color,
    val textColor: Color,
    val isDark: Boolean,
    val textureId: String? = null,
    val isCustom: Boolean = false
)

val BuiltInThemes = listOf(
    ReaderTheme("system", "System", Color.Unspecified, Color.Unspecified, false),
    ReaderTheme("light", "Light", Color(0xFFFFFFFF), Color(0xFF000000), false),
    ReaderTheme("dark", "Dark", Color(0xFF121212), Color(0xFFE0E0E0), true),
    ReaderTheme("sepia", "Sepia", Color(0xFFFBF0D9), Color(0xFF5F4B32), false),
    ReaderTheme("slate", "Slate", Color(0xFF2E3440), Color(0xFFECEFF4), true),
    ReaderTheme("oled", "OLED", Color(0xFF000000), Color(0xFFB0B0B0), true)
)

fun saveReaderThemeId(context: Context, themeId: String) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(PREF_READER_THEME, themeId) }
}

fun loadReaderThemeId(context: Context): String {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getString(PREF_READER_THEME, "system") ?: "system"
}

fun saveCustomThemes(context: Context, themes: List<ReaderTheme>) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    themes.filter { it.isCustom }.forEach { theme ->
        val obj = JSONObject().apply {
            put("id", theme.id)
            put("name", theme.name)
            put("bgColor", theme.backgroundColor.toArgb())
            put("textColor", theme.textColor.toArgb())
            put("isDark", theme.isDark)
            theme.textureId?.let { put("textureId", it) }
        }
        jsonArray.put(obj)
    }
    prefs.edit { putString(PREF_CUSTOM_THEMES, jsonArray.toString()) }
}

fun loadCustomThemes(context: Context): List<ReaderTheme> {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    val jsonString = prefs.getString(PREF_CUSTOM_THEMES, "[]") ?: "[]"
    val themes = mutableListOf<ReaderTheme>()
    try {
        val jsonArray = org.json.JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            themes.add(
                ReaderTheme(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    backgroundColor = Color(obj.getInt("bgColor")),
                    textColor = Color(obj.getInt("textColor")),
                    isDark = obj.getBoolean("isDark"),
                    textureId = if (obj.has("textureId")) obj.getString("textureId") else null,
                    isCustom = true
                )
            )
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse custom themes")
    }
    return themes
}

private fun calculateContrastRatio(color1: Color, color2: Color): Float {
    val l1 = max(color1.luminance(), color2.luminance())
    val l2 = min(color1.luminance(), color2.luminance())
    return (l1 + 0.05f) / (l2 + 0.05f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderThemePanel(
    isVisible: Boolean,
    currentThemeId: String,
    customThemes: List<ReaderTheme>,
    builtInThemes: List<ReaderTheme> = BuiltInThemes,
    onThemeSelected: (String) -> Unit,
    onCustomThemesUpdated: (List<ReaderTheme>) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return
    var showBuilder by remember { mutableStateOf(false) }
    var editingTheme by remember { mutableStateOf<ReaderTheme?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        AnimatedContent(targetState = showBuilder, label = "ThemePanelTransition") { isBuilding ->
            if (isBuilding) {
                ThemeBuilderView(
                    initialTheme = editingTheme,
                    onSave = { newTheme ->
                        val updatedList = if (editingTheme != null) {
                            customThemes.map { if (it.id == newTheme.id) newTheme else it }
                        } else {
                            customThemes + newTheme
                        }
                        onCustomThemesUpdated(updatedList)
                        onThemeSelected(newTheme.id)
                        showBuilder = false
                        editingTheme = null
                    },
                    onCancel = {
                        showBuilder = false
                        editingTheme = null
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.65f)
                        .padding(16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        "Reading Themes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text("Presets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    ThemeGrid(themes = builtInThemes, currentThemeId = currentThemeId, onThemeSelected = onThemeSelected)

                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("My Themes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { editingTheme = null; showBuilder = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Create Theme", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    if (customThemes.isEmpty()) {
                        Text("No custom themes yet. Tap '+' to create one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        ThemeGrid(
                            themes = customThemes,
                            currentThemeId = currentThemeId,
                            onThemeSelected = onThemeSelected,
                            onEdit = { editingTheme = it; showBuilder = true },
                            onDelete = { themeToDelete ->
                                val updated = customThemes.filter { it.id != themeToDelete.id }
                                onCustomThemesUpdated(updated)
                                if (currentThemeId == themeToDelete.id) onThemeSelected("system")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeGrid(
    themes: List<ReaderTheme>,
    currentThemeId: String,
    onThemeSelected: (String) -> Unit,
    onEdit: ((ReaderTheme) -> Unit)? = null,
    onDelete: ((ReaderTheme) -> Unit)? = null
) {
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(themes.size) { index ->
            val theme = themes[index]
            val isSelected = currentThemeId == theme.id
            val bgColor = if (theme.id == "system") MaterialTheme.colorScheme.surfaceVariant else theme.backgroundColor
            val textColor = if (theme.id == "system") MaterialTheme.colorScheme.onSurfaceVariant else theme.textColor
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(bgColor, CircleShape)
                        .border(if (isSelected) 3.dp else 1.dp, borderColor, CircleShape)
                        .clickable { onThemeSelected(theme.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Aa", color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = theme.name, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)

                if (theme.isCustom && onEdit != null && onDelete != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Edit, "Edit", Modifier.size(28.dp).clip(CircleShape).clickable { onEdit(theme) }.padding(6.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.Delete, "Delete", Modifier.size(28.dp).clip(CircleShape).clickable { onDelete(theme) }.padding(6.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeBuilderView(
    initialTheme: ReaderTheme?,
    onSave: (ReaderTheme) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initialTheme?.name ?: "Custom Theme") }
    var bgColor by remember { mutableStateOf(initialTheme?.backgroundColor ?: Color(0xFFF5F5F5)) }
    var txtColor by remember { mutableStateOf(initialTheme?.textColor ?: Color(0xFF111111)) }
    var textureId by remember { mutableStateOf(initialTheme?.textureId) }

    var editingColorType by remember { mutableStateOf<String?>(null) }

    val contrast = calculateContrastRatio(bgColor, txtColor)
    val isDark = bgColor.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(16.dp)
    ) {
        Text(
            text = if (initialTheme == null) "New Theme" else "Edit Theme",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Theme Name") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true
            )

            // Live Preview Card
            Surface(
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = bgColor,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                val context = LocalContext.current
                Box(modifier = Modifier.fillMaxSize().run {
                    val texRes = ReaderTexture.entries.find { it.id == textureId }?.resId
                    if (texRes != null) {
                        val bmp = ImageBitmap.imageResource(context.resources, texRes)
                        this.drawBehind {
                            drawRect(ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated)), blendMode = BlendMode.Multiply, alpha = 0.5f)
                        }
                    } else this
                }) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text(
                            text = "So many books, so little time.",
                            color = txtColor,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "- Frank Zappa",
                            color = txtColor,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            // Animated Contrast Warning
            AnimatedVisibility(visible = contrast < 4.5f) {
                Text(
                    "⚠️ Low contrast! This might cause eye strain.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Sleek Color Swatches
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ColorSwatchItem(
                    label = "Page Color",
                    color = bgColor,
                    onClick = { editingColorType = "bg" },
                    modifier = Modifier.weight(1f)
                )
                ColorSwatchItem(
                    label = "Text Color",
                    color = txtColor,
                    onClick = { editingColorType = "text" },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // Action Buttons at the bottom for better visibility
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                onSave(ReaderTheme(id = initialTheme?.id ?: System.currentTimeMillis().toString(), name = name, backgroundColor = bgColor, textColor = txtColor, isDark = isDark, textureId = textureId, isCustom = true))
            }) {
                Text("Save", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    editingColorType?.let { type ->
        ThemeColorPickerDialog(
            initialColor = if (type == "bg") bgColor else txtColor,
            title = if (type == "bg") "Page Color" else "Text Color",
            bgColor = bgColor,
            textColor = txtColor,
            editingColorType = type,
            onDismiss = { editingColorType = null },
            onColorChanged = { newColor ->
                if (type == "bg") bgColor = newColor else txtColor = newColor
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorSwatchItem(label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = color,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {}
    }
}

@Composable
fun ThemeColorPickerDialog(
    initialColor: Color,
    title: String,
    bgColor: Color,
    textColor: Color,
    editingColorType: String,
    onDismiss: () -> Unit,
    onColorChanged: (Color) -> Unit
) {
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColor by remember {
        derivedStateOf {
            val hsv = floatArrayOf(hue, saturation, value)
            val argb = android.graphics.Color.HSVToColor(255, hsv)
            Color(argb)
        }
    }

    LaunchedEffect(currentColor) {
        onColorChanged(currentColor)
    }

    fun updateFromColor(color: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF2C2C2C),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()), // Prevents elements from hiding off-screen
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF3E3E3E), RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(16.dp))

                val liveBgColor = if (editingColorType == "bg") currentColor else bgColor
                val liveTextColor = if (editingColorType == "text") currentColor else textColor

                Surface(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = liveBgColor,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Live Preview",
                            color = liveTextColor,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Reading is dreaming.",
                            color = liveTextColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                SpectrumBox(
                    hue = hue,
                    saturation = saturation,
                    currentColor = currentColor,
                    onHueSatChanged = { h, s -> hue = h; saturation = s },
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )

                Spacer(Modifier.height(20.dp))

                BrightnessSlider(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onValueChanged = { value = it },
                    modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp))
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ColorComparePill(
                        oldColor = initialColor,
                        newColor = currentColor,
                        modifier = Modifier.width(64.dp).height(36.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1.6f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Hex", color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        HexInput(color = currentColor, onHexChanged = { updateFromColor(it) })
                    }

                    Row(
                        modifier = Modifier.weight(2.4f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RgbInputColumn(
                            label = "R", value = currentColor.red,
                            onValueChange = { r -> updateFromColor(currentColor.copy(red = r)) },
                            modifier = Modifier.weight(1f)
                        )
                        RgbInputColumn(
                            label = "G", value = currentColor.green,
                            onValueChange = { g -> updateFromColor(currentColor.copy(green = g)) },
                            modifier = Modifier.weight(1f)
                        )
                        RgbInputColumn(
                            label = "B", value = currentColor.blue,
                            onValueChange = { b -> updateFromColor(currentColor.copy(blue = b)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        )
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TextureOption(name: String, resId: Int?, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape).run {
            if (resId != null) {
                val bmp = ImageBitmap.imageResource(LocalResources.current, resId)
                this.drawBehind { drawRect(ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated))) }
            } else this.background(MaterialTheme.colorScheme.surfaceVariant)
        })
        Text(name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun ColorSlider(color: Color, onColorChanged: (Color) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Slider(value = color.red, onValueChange = { onColorChanged(color.copy(red = it)) }, colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red), modifier = Modifier.weight(1f))
        Slider(value = color.green, onValueChange = { onColorChanged(color.copy(green = it)) }, colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green), modifier = Modifier.weight(1f))
        Slider(value = color.blue, onValueChange = { onColorChanged(color.copy(blue = it)) }, colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue), modifier = Modifier.weight(1f))
    }
}