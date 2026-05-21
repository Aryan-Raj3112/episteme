package com.aryan.reader.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.RecapResult
import com.aryan.reader.shared.SummarizationResult
import com.aryan.reader.shared.ui.SharedMarkdownText
import com.aryan.reader.shared.ui.readerString

@Composable
internal fun DesktopAiHubSheet(
    bookKey: String,
    bookTitle: String,
    itemIndex: Int,
    itemTitle: String,
    summaryCacheStore: DesktopSummaryCacheStore,
    summaryResult: SummarizationResult?,
    isSummaryLoading: Boolean,
    recapResult: RecapResult?,
    isRecapLoading: Boolean,
    recapProgressMessage: String?,
    onGenerateSummary: (force: Boolean) -> Unit,
    onClearSummary: () -> Unit,
    onGenerateRecap: (() -> Unit)?,
    onClearRecap: () -> Unit,
    onDismiss: () -> Unit,
    credits: Int,
    showCredits: Boolean
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var cacheRefresh by remember { mutableIntStateOf(0) }
    val cachedSummary = remember(bookKey, itemIndex, cacheRefresh) {
        summaryCacheStore.getSummary(bookKey, itemIndex)
    }
    val effectiveSummary = summaryResult ?: cachedSummary?.let {
        SummarizationResult(summary = it, isCacheHit = true)
    }
    val summaryTab = readerString("label_summary", "Summary")
    val recapTab = readerString("ai_tab_recap", "Recap")
    val cacheTab = readerString("ai_tab_cache", "Cache")
    val tabs = buildList {
        add(summaryTab)
        if (onGenerateRecap != null) add(recapTab)
        add(cacheTab)
    }
    val selectedTabIndex = selectedTab.coerceIn(0, tabs.lastIndex)
    val activeTab = tabs.getOrElse(selectedTabIndex) { summaryTab }

    DesktopReaderBottomSheet(
        title = readerString("desktop_ai_hub", "AI hub"),
        onDismiss = onDismiss
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(bookTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(itemTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showCredits) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        readerString("credits_count", "%1\$d credits", credits),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        ScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 0.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (activeTab) {
            summaryTab -> {
                DesktopAiHubResultView(
                    title = itemTitle,
                    resultText = effectiveSummary?.summary,
                    errorText = effectiveSummary?.error,
                    cost = effectiveSummary?.cost,
                    freeRemaining = effectiveSummary?.freeRemaining,
                    isCacheHit = effectiveSummary?.isCacheHit == true,
                    isLoading = isSummaryLoading,
                    loadingLabel = readerString("generating_summary", "Generating summary..."),
                    emptyTitle = readerString("desktop_no_summary_cached_section", "No summary cached for this section."),
                    primaryActionLabel = readerString("desktop_generate_summary", "Generate summary"),
                    onPrimaryAction = { onGenerateSummary(false) },
                    onRegenerate = { onGenerateSummary(true) },
                    onClear = {
                        summaryCacheStore.deleteSummary(bookKey, itemIndex)
                        cacheRefresh++
                        onClearSummary()
                    }
                )
            }
            recapTab -> {
                DesktopAiHubResultView(
                    title = readerString("ai_story_recap", "Story recap"),
                    resultText = recapResult?.recap,
                    errorText = recapResult?.error,
                    cost = recapResult?.cost,
                    freeRemaining = recapResult?.freeRemaining,
                    isCacheHit = false,
                    isLoading = isRecapLoading,
                    loadingLabel = recapProgressMessage?.takeIf { it.isNotBlank() } ?: readerString("ai_generating_recap", "Generating recap..."),
                    emptyTitle = readerString("desktop_create_recap_current_position", "Create a recap up to your current position."),
                    primaryActionLabel = readerString("desktop_generate_recap", "Generate recap"),
                    onPrimaryAction = { onGenerateRecap?.invoke() },
                    onRegenerate = { onGenerateRecap?.invoke() },
                    onClear = onClearRecap
                )
            }
            cacheTab -> {
                DesktopSummaryCachePanel(
                    bookKey = bookKey,
                    summaryCacheStore = summaryCacheStore,
                    onCacheChanged = {
                        cacheRefresh++
                        onClearSummary()
                    }
                )
            }
        }
    }
}

@Composable
private fun DesktopAiHubResultView(
    title: String,
    resultText: String?,
    errorText: String?,
    cost: Double?,
    freeRemaining: Int?,
    isCacheHit: Boolean,
    isLoading: Boolean,
    loadingLabel: String,
    emptyTitle: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    onRegenerate: () -> Unit,
    onClear: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val hasText = !resultText.isNullOrBlank()
    val hasError = !errorText.isNullOrBlank()
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 430.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            DesktopAiUsageBadge(
                isCacheHit = isCacheHit,
                cost = cost,
                freeRemaining = freeRemaining,
                isLoading = isLoading
            )
        }
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(loadingLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            hasText -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(enabled = !isLoading, onClick = onRegenerate) {
                        Text(readerString("ai_regenerate", "Regenerate"))
                    }
                    TextButton(enabled = !isLoading, onClick = onClear) {
                        Text(readerString("action_clear", "Clear"))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { clipboard.setText(AnnotatedString(resultText.orEmpty())) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = readerString("action_copy", "Copy"))
                    }
                }
                HorizontalDivider()
                Column(modifier = Modifier.weight(1f).heightIn(min = 120.dp).padding(end = 2.dp).verticalScroll(rememberScrollState())) {
                    SharedMarkdownText(resultText.orEmpty())
                }
            }
            hasError -> {
                Text(errorText.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(onClick = onPrimaryAction, enabled = !isLoading) {
                    Text(primaryActionLabel)
                }
            }
            !isLoading -> {
                Text(emptyTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onPrimaryAction) {
                    Text(primaryActionLabel)
                }
            }
        }
    }
}

@Composable
private fun DesktopAiUsageBadge(
    isCacheHit: Boolean,
    cost: Double?,
    freeRemaining: Int?,
    isLoading: Boolean
) {
    val text = when {
        isCacheHit -> readerString("desktop_cached", "Cached")
        cost == 0.0 && freeRemaining != null -> readerString("desktop_free_remaining_format", "Free, %1\$d left", freeRemaining)
        cost != null -> readerString("desktop_credits_decimal_format", "%1\$s credits", cost)
        isLoading -> readerString("desktop_cost_calculating", "Cost calculating")
        else -> null
    } ?: return
    Surface(
        color = if (isCacheHit || cost == 0.0) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (isCacheHit || cost == 0.0) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
        )
    }
}

@Composable
private fun DesktopSummaryCachePanel(
    bookKey: String,
    summaryCacheStore: DesktopSummaryCacheStore,
    onCacheChanged: () -> Unit
) {
    var cachedItems by remember(bookKey) { mutableStateOf(summaryCacheStore.getAllSummaries(bookKey)) }
    if (cachedItems.isEmpty()) {
        Text(readerString("desktop_no_cached_summaries_book", "No cached summaries for this book yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cachedItems.forEach { item ->
            var expanded by remember(item.index, item.summary) { mutableStateOf(false) }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(readerString("desktop_cached_summary", "Cached summary"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) readerString("desktop_hide", "Hide") else readerString("desktop_view", "View"))
                        }
                        IconButton(
                            onClick = {
                                summaryCacheStore.deleteSummary(bookKey, item.index)
                                cachedItems = summaryCacheStore.getAllSummaries(bookKey)
                                onCacheChanged()
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = readerString("desktop_delete_summary", "Delete summary"), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (expanded) {
                        SharedMarkdownText(item.summary)
                    }
                }
            }
        }
        TextButton(
            onClick = {
                summaryCacheStore.clearBookCache(bookKey)
                cachedItems = emptyList()
                onCacheChanged()
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(readerString("clear_all", "Clear all"), color = MaterialTheme.colorScheme.error)
        }
    }
}
