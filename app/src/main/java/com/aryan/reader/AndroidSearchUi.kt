// Common.kt
@file:OptIn(ExperimentalMaterial3Api::class) @file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import com.aryan.reader.shared.ReaderSearchState as SearchState

import com.aryan.reader.shared.AiDefinitionResult

import com.aryan.reader.shared.SearchResult

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.tts.loadTtsMode
import com.aryan.reader.tts.rememberTtsController
import com.aryan.reader.tts.splitTextIntoChunks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.commonmark.node.Text
import timber.log.Timber
import java.io.File
import kotlin.math.max




data class CachedSummaryItem(
    val chapterIndex: Int,
    val chapterTitle: String,
    val summary: String,
    val file: File
)

class SummaryCacheManager(context: Context) {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, "chapter_summaries")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    private fun getFileName(bookTitle: String, chapterIndex: Int): String {
        val safeTitle = bookTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        return "summary_${safeTitle}_$chapterIndex.txt"
    }

    fun saveSummary(bookTitle: String, chapterIndex: Int, chapterTitle: String, summary: String) {
        try {
            val file = File(cacheDir, getFileName(bookTitle, chapterIndex))
            val contentToSave = "$chapterTitle\n$summary"
            file.writeText(contentToSave)
            Timber.d("Saved summary with title for $bookTitle Ch $chapterIndex")
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

    fun getAllSummaries(bookTitle: String): List<CachedSummaryItem> {
        val safeTitle = bookTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val prefix = "summary_${safeTitle}_"
        val files = cacheDir.listFiles()?.filter { it.name.startsWith(prefix) && it.name.endsWith(".txt") } ?: emptyList()

        return files.mapNotNull { file ->
            try {
                val indexStr = file.name.removePrefix(prefix).removeSuffix(".txt")
                val index = indexStr.toInt()

                val fullText = file.readText()
                val lines = fullText.lines()

                val title = lines.firstOrNull()?.trim() ?: appContext.getString(R.string.chapter_number_format, index + 1)
                val summaryText = if (lines.size > 1) lines.drop(1).joinToString("\n") else ""

                Timber.d("Cache Load: Ch $index, Title: $title")

                CachedSummaryItem(index, title, summaryText, file)
            } catch (e: Exception) {
                Timber.e(e, "Error parsing cache file: ${file.name}")
                null
            }
        }.sortedBy { it.chapterIndex }
    }

    fun deleteSummary(bookTitle: String, chapterIndex: Int) {
        val file = File(cacheDir, getFileName(bookTitle, chapterIndex))
        if (file.exists()) file.delete()
    }

    fun clearBookCache(bookTitle: String) {
        getAllSummaries(bookTitle).forEach { it.file.delete() }
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
    com.aryan.reader.shared.ui.SharedTooltipIconButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        description = description,
        content = content,
    )
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
                    contentDescription = stringResource(R.string.tooltip_close_search)
                )
            }

            TextField(
                value = searchState.searchQuery,
                onValueChange = { searchState.onQueryChange(it) },
                placeholder = { Text(stringResource(R.string.search_in_book)) },
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
                        contentDescription = stringResource(R.string.tooltip_clear_search)
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
                    contentDescription = stringResource(
                        if (searchState.showSearchResultsPanel) R.string.tooltip_hide_results
                        else R.string.tooltip_show_results
                    )
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
                Icon(Icons.Default.ArrowDropUp, contentDescription = stringResource(R.string.tooltip_prev_result))
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
                Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.tooltip_next_result))
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
    onOpenExternalDictionary: () -> Unit,
    getAuthToken: suspend () -> String?
) {
    val ttsController = rememberTtsController()
    val ttsState by ttsController.ttsState.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val maxPopupHeight = readerModalMaxHeightDp(
        screenHeightDp = configuration.screenHeightDp,
        fraction = 0.65f,
        verticalMarginDp = 48,
        preferredMinHeightDp = 180
    ).dp
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
                .heightIn(max = maxPopupHeight),
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
                        Text(stringResource(R.string.ai_thinking), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
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
                    val aiDefinitionTitle = stringResource(R.string.ai_definition_title)

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
                                            scope.launch {
                                                val token = getAuthToken()
                                                ttsController.start(
                                                    chunks = chunks,
                                                    bookTitle = aiDefinitionTitle,
                                                    chapterTitle = word,
                                                    coverImageUri = null,
                                                    ttsMode = loadTtsMode(context),
                                                    playbackSource = "POPUP",
                                                    authToken = token
                                                )
                                            }
                                        }
                                    }
                                },
                                enabled = !isMainTtsActive || (ttsState.playbackSource == "POPUP")
                            ) {
                                Icon(
                                    imageVector = if (isTtsSessionActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = stringResource(if (isTtsSessionActive) R.string.action_stop else R.string.action_read_aloud)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(textToUse))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.action_copy)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = onOpenExternalDictionary) {
                                Icon(
                                    painter = painterResource(id = R.drawable.dictionary),
                                    contentDescription = stringResource(R.string.content_desc_open_dictionary)
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
                        Text(stringResource(R.string.ai_no_definition), style = MaterialTheme.typography.bodyLarge)
                    }
                } else if (word != null) {
                    Text(
                        text = stringResource(R.string.ai_asking_about, word),
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
    val resources = LocalContext.current.resources
    com.aryan.reader.shared.ui.SharedReaderSearchResultsPanel(
        results = results,
        isSearching = isSearching,
        noResultsText = stringResource(R.string.search_no_results_simple),
        resultsCountText = { count ->
            resources.getQuantityString(R.plurals.search_results_count, count, count)
        },
        onResultClick = onResultClick,
        modifier = modifier,
    )
}
