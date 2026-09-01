@file:OptIn(ExperimentalMaterial3Api::class) @file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import com.aryan.reader.shared.SummarizationResult

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import android.widget.Toast
import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.tts.TtsPlaybackManager
import com.aryan.reader.tts.loadTtsMode
import com.aryan.reader.tts.rememberTtsController
import com.aryan.reader.tts.splitTextIntoChunks
import kotlinx.coroutines.launch
import org.commonmark.node.ListItem
import org.commonmark.node.Text
import kotlin.math.max
import kotlin.math.min

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHubBottomSheet(
    bookTitle: String,
    currentChapterIndex: Int,
    chapterTitle: String,
    summaryCacheManager: SummaryCacheManager? = null,
    summarizationResult: SummarizationResult?,
    isSummarizationLoading: Boolean,
    onGenerateSummary: (Boolean) -> Unit,
    onClearSummary: () -> Unit = {},
    recapResult: SummarizationResult? = null,
    isRecapLoading: Boolean = false,
    onGenerateRecap: (() -> Unit)? = null,
    onClearRecap: () -> Unit = {},
    onDismiss: () -> Unit,
    isMainTtsActive: Boolean,
    getAuthToken: suspend () -> String?,
    credits: Int,
    isProUser: Boolean
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val ttsController = rememberTtsController()
    val ttsState by ttsController.ttsState.collectAsState()

    LaunchedEffect(currentChapterIndex) {
        onClearSummary()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (ttsState.playbackSource == "POPUP" && (ttsState.isPlaying || ttsState.isLoading)) {
                ttsController.stop()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 300.dp, max = 600.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.ai_features_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(2f)
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (BuildConfig.FLAVOR != "oss") {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "⭐ $credits",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            val summaryTab = stringResource(R.string.ai_tab_summary)
            val recapTab = stringResource(R.string.ai_tab_recap)
            val cacheTab = stringResource(R.string.ai_tab_cache)
            val tabs = mutableListOf(summaryTab)
            if (onGenerateRecap != null) tabs.add(recapTab)
            if (summaryCacheManager != null) tabs.add(cacheTab)

            TabRow(selectedTabIndex = selectedTabIndex, modifier = Modifier.padding(bottom = 16.dp)) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }) {
                        Text(title, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            val activeTab = tabs.getOrNull(selectedTabIndex) ?: summaryTab
            var cacheRefreshTrigger by remember { mutableIntStateOf(0) }

            when (activeTab) {
                summaryTab -> {
                    val cachedSummary = remember(currentChapterIndex, cacheRefreshTrigger) { summaryCacheManager?.getSummary(bookTitle, currentChapterIndex) }
                    val effectiveResult = summarizationResult ?: if (cachedSummary != null) SummarizationResult(summary = cachedSummary, isCacheHit = true) else null

                    if (effectiveResult == null && !isSummarizationLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painterResource(R.drawable.summarize), contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.ai_no_summary_for_chapter, chapterTitle.lowercase()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { onGenerateSummary(false) },
                                    modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 8.dp)
                                ) {
                                    Icon(painterResource(R.drawable.ai), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.ai_generate_summary_for_chapter, chapterTitle))
                                }
                            }
                        }
                    } else {
                        AiResultContentView(
                            title = chapterTitle,
                            result = effectiveResult,
                            isLoading = isSummarizationLoading,
                            isMainTtsActive = isMainTtsActive,
                            ttsController = ttsController,
                            ttsState = ttsState,
                            getAuthToken = getAuthToken,
                            onRegenerate = { onGenerateSummary(true) }
                        )
                    }
                }
                recapTab -> {
                    // Recap Tab
                    if (recapResult == null && !isRecapLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painterResource(R.drawable.ai), contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.ai_recap_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { onGenerateRecap?.invoke() },
                                    modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 8.dp)
                                ) {
                                    Icon(painterResource(R.drawable.ai), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.ai_generate_story_recap))
                                }
                            }
                        }
                    } else {
                        AiResultContentView(
                            title = stringResource(R.string.ai_story_recap),
                            result = recapResult,
                            isLoading = isRecapLoading,
                            isMainTtsActive = isMainTtsActive,
                            ttsController = ttsController,
                            ttsState = ttsState,
                            getAuthToken = getAuthToken,
                            onRegenerate = { onGenerateRecap?.invoke() },
                            onClear = onClearRecap
                        )
                    }
                }
                cacheTab -> {
                    if (summaryCacheManager != null) {
                        ManageCacheTab(bookTitle, summaryCacheManager, onCacheChanged = {
                            cacheRefreshTrigger++
                            onClearSummary()
                        })
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AiResultContentView(
    title: String,
    result: SummarizationResult?,
    isLoading: Boolean,
    isMainTtsActive: Boolean,
    ttsController: com.aryan.reader.tts.TtsController,
    ttsState: TtsPlaybackManager.TtsState,
    getAuthToken: suspend () -> String?,
    onRegenerate: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )

            val showUsageBadge = result?.isCacheHit == true || (BuildConfig.FLAVOR != "oss" && (result?.cost != null || isLoading))
            if (result != null && showUsageBadge && (!result.summary.isNullOrBlank() || isLoading)) {
                val cost = result.cost
                val freeRemaining = result.freeRemaining
                val isFreeGeneratedResult = cost == 0.0 && freeRemaining != null
                Surface(
                    color = if (result.isCacheHit || isFreeGeneratedResult) Color(
                        0xFF4CAF50
                    ).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (result.isCacheHit) {
                            stringResource(R.string.ai_cache_hit_free)
                        } else if (cost != null) {
                            if (isFreeGeneratedResult) {
                                safeStringResource(R.string.ai_generated_free_remaining,
                                    freeRemaining
                                )
                            } else {
                                safeStringResource(R.string.ai_generated_cost, cost.toString())
                            }
                        } else {
                            stringResource(R.string.ai_generating_cost_calculating)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (result.isCacheHit || isFreeGeneratedResult) Color(
                            0xFF388E3C
                        ) else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (isLoading && (result?.summary.isNullOrBlank() && result?.error.isNullOrBlank())) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.ai_thinking), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                }
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

                    if (onClear != null && !isLoading) {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_clear),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (onRegenerate != null) {
                        TextButton(onClick = onRegenerate) {
                            Text(stringResource(R.string.ai_regenerate))
                        }
                    }

                    IconButton(
                        onClick = {
                            if (isTtsSessionActive) {
                                ttsController.stop()
                            } else {
                                val chunks = splitTextIntoChunks(textToUse).map { TtsChunk(it, "", -1) }
                                if (chunks.isNotEmpty()) {
                                    scope.launch {
                                        val token = getAuthToken()
                                        ttsController.start(
                                            chunks = chunks,
                                            bookTitle = title,
                                            chapterTitle = context.getString(R.string.ai_output_title),
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
                            contentDescription = stringResource(R.string.action_read_aloud)
                        )
                    }
                    IconButton(onClick = {
                        val copied = copyPlainTextToClipboard(
                            context = context,
                            label = context.getString(R.string.action_copy),
                            text = textToUse,
                        )
                        if (!copied) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.error_copy_to_clipboard),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.action_copy)
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
                    modifier = Modifier.verticalScroll(scrollState).weight(1f, fill = false),
                    onTextLayout = { textLayoutResult = it }
                )
            }
        }
    }
}

@Composable
fun ManageCacheTab(bookTitle: String, summaryCacheManager: SummaryCacheManager, onCacheChanged: () -> Unit = {}) {
    var cachedItems by androidx.compose.runtime.remember { mutableStateOf(summaryCacheManager.getAllSummaries(bookTitle)) }

    if (cachedItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.ai_no_cached_summaries), style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                items(cachedItems.size) { index ->
                    val item = cachedItems[index]
                    var expanded by androidx.compose.runtime.remember { mutableStateOf(false) }

                    Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = item.chapterTitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    summaryCacheManager.deleteSummary(bookTitle, item.chapterIndex)
                                    cachedItems = summaryCacheManager.getAllSummaries(bookTitle)
                                    onCacheChanged()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                        AnimatedVisibility(visible = expanded) {
                            Text(
                                text = item.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
            TextButton(
                onClick = {
                    summaryCacheManager.clearBookCache(bookTitle)
                    cachedItems = emptyList()
                    onCacheChanged()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.clear_all), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
