package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Ai
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.CachedSummary
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderAiResultState

/**
 * Phone AI hub bottom sheet shared by the EPUB and PDF readers.
 *
 * Android parity (`AiHubBottomSheet`): "AI features" title with a credits
 * indicator, Summary / Recap tabs, and a Cache tab backed by
 * [com.aryan.reader.shared.SharedSummaryCache]. Summary and recap results
 * render inline (markdown + usage badge + Regenerate / TTS / Copy) instead
 * of a separate sheet; callers keep the hub open across generation and pass
 * the live [aiResult] through.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileAiHubSheet(
    sectionTitle: String,
    bookTitle: String,
    cachedSummary: CachedSummary?,
    cacheEntries: List<CachedSummary>,
    showCacheTab: Boolean,
    credits: Int?,
    aiResult: ReaderAiResultState,
    isMainTtsActive: Boolean,
    onGenerateSummary: () -> Unit,
    onGenerateRecap: () -> Unit,
    onClearAiResult: () -> Unit,
    onDeleteCached: (CachedSummary) -> Unit,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(min = 300.dp, max = 600.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f))
                Text(
                    readerString("ai_features_title", "AI Features"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(2f),
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (credits != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = "⭐ $credits",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
            val summaryTab = readerString("ai_tab_summary", "Summary")
            val recapTab = readerString("ai_tab_recap", "Recap")
            val cacheTab = readerString("ai_tab_cache", "Cache")
            val tabs = buildList {
                add(summaryTab)
                add(recapTab)
                if (showCacheTab) add(cacheTab)
            }
            TabRow(
                selectedTabIndex = selectedTabIndex.coerceIn(tabs.indices),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                    ) {
                        Text(
                            title,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
            val activeTab = tabs.getOrNull(selectedTabIndex) ?: summaryTab
            when (activeTab) {
                summaryTab -> {
                    val summaryLive = aiResult.takeIf {
                        it.title == ReaderAiFeature.SUMMARIZE.displayName && it.hasContent
                    }
                    val isSummaryLoading = summaryLive?.isLoading == true
                    if (summaryLive == null && !isSummaryLoading) {
                        if (cachedSummary != null) {
                            SharedMobileAiResultContent(
                                title = cachedSummary.sectionTitle,
                                markdownText = cachedSummary.summary,
                                errorText = null,
                                isLoading = false,
                                cost = null,
                                freeRemaining = null,
                                isCacheHit = true,
                                onRegenerate = onGenerateSummary,
                                onClear = null,
                                onOpenExternalDictionary = null,
                                isMainTtsActive = isMainTtsActive,
                                ttsBookTitle = bookTitle,
                                ttsChapterTitle = cachedSummary.sectionTitle,
                                // iOS parity (IosFeatureGating.SHOW_CREDITS_PURCHASE):
                                // no credits UI until credits launch; the badge
                                // lights up automatically once credits flow.
                                showUsageBadge = credits != null,
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        readerString(
                                            "ai_no_summary_for_chapter",
                                            "No summary for %1\$s yet.",
                                            sectionTitle.lowercase(),
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 16.dp),
                                    )
                                    Button(
                                        onClick = onGenerateSummary,
                                        modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Ai,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            readerString(
                                                "ai_generate_summary_for_chapter",
                                                "Generate Summary for %1\$s",
                                                sectionTitle,
                                            ),
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        SharedMobileAiResultContent(
                            title = sectionTitle,
                            markdownText = summaryLive?.text.orEmpty(),
                            errorText = summaryLive?.errorMessage,
                            isLoading = isSummaryLoading,
                            cost = summaryLive?.cost,
                            freeRemaining = summaryLive?.freeRemaining,
                            isCacheHit = false,
                            onRegenerate = onGenerateSummary,
                            onClear = null,
                            onOpenExternalDictionary = null,
                            isMainTtsActive = isMainTtsActive,
                            ttsBookTitle = bookTitle,
                            ttsChapterTitle = sectionTitle,
                            showUsageBadge = credits != null,
                        )
                    }
                }
                recapTab -> {
                    val recapLive = aiResult.takeIf {
                        it.title == ReaderAiFeature.RECAP.displayName && it.hasContent
                    }
                    val isRecapLoading = recapLive?.isLoading == true
                    if (recapLive == null && !isRecapLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Ai,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    readerString(
                                        "ai_recap_desc",
                                        "Get a recap of the story up to your current position."
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                                Button(
                                    onClick = onGenerateRecap,
                                    modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Ai,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        readerString("ai_generate_story_recap", "Generate Story Recap"),
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        SharedMobileAiResultContent(
                            title = readerString("ai_story_recap", "Story Recap"),
                            markdownText = recapLive?.text.orEmpty(),
                            errorText = recapLive?.errorMessage,
                            isLoading = isRecapLoading,
                            cost = recapLive?.cost,
                            freeRemaining = recapLive?.freeRemaining,
                            isCacheHit = false,
                            onRegenerate = onGenerateRecap,
                            onClear = onClearAiResult,
                            onOpenExternalDictionary = null,
                            isMainTtsActive = isMainTtsActive,
                            ttsBookTitle = bookTitle,
                            ttsChapterTitle = readerString("ai_output_title", "AI Output"),
                            showUsageBadge = credits != null,
                        )
                    }
                }
                else -> {
                    SharedMobileAiCacheTab(
                        cacheEntries = cacheEntries,
                        onDeleteCached = onDeleteCached,
                        onClearCache = onClearCache,
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedMobileAiCacheTab(
    cacheEntries: List<CachedSummary>,
    onDeleteCached: (CachedSummary) -> Unit,
    onClearCache: () -> Unit,
) {
    if (cacheEntries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                readerString("ai_no_cached_summaries", "No cached summaries yet."),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            items(cacheEntries, key = { it.sectionIndex }) { entry ->
                var expanded by remember(entry.sectionIndex) { mutableStateOf(false) }
                Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                    ListItem(
                        headlineContent = {
                            Text(
                                entry.sectionTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeleteCached(entry) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = readerString("action_delete", "Delete"),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    )
                    if (expanded) {
                        SharedMarkdownText(
                            markdown = entry.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onClearCache) {
                Text(
                    readerString("clear_all", "Clear all"),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
