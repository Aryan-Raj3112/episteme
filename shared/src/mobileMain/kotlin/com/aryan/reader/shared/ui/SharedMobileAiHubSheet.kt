package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.CachedSummary

/**
 * Phone AI hub bottom sheet shared by the EPUB and PDF readers.
 *
 * Mirrors the Android `AiHubBottomSheet` structure: an "AI features" title
 * with a credits indicator, Summary / Recap tabs, and a Cache tab backed by
 * [com.aryan.reader.shared.SharedSummaryCache] (list with read-through,
 * per-item delete, clear-all). Callers own persistence: [cachedSummary] and
 * [cacheEntries] are snapshots the host refreshes after mutations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileAiHubSheet(
    sectionTitle: String,
    cachedSummary: CachedSummary?,
    cacheEntries: List<CachedSummary>,
    showCacheTab: Boolean,
    credits: Int?,
    onGenerateSummary: () -> Unit,
    onGenerateRecap: () -> Unit,
    onDeleteCached: (CachedSummary) -> Unit,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    readerString("ai_features_title", "AI Features"),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
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
            val tabs = buildList {
                add(readerString("ai_tab_summary", "Summary"))
                add(readerString("ai_tab_recap", "Recap"))
                if (showCacheTab) add(readerString("ai_tab_cache", "Cache"))
            }
            TabRow(selectedTabIndex = selectedTabIndex.coerceIn(tabs.indices)) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                    )
                }
            }
            val activeTab = tabs.getOrNull(selectedTabIndex) ?: tabs.first()
            when (activeTab) {
                tabs[0] -> {
                    if (cachedSummary != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                cachedSummary.sectionTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                cachedSummary.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                            Button(
                                onClick = onGenerateSummary,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            ) { Text(readerString("ai_regenerate_summary", "Regenerate Summary")) }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                readerString(
                                    "ai_no_summary_for_chapter",
                                    "No summary for %1\$s yet.",
                                    sectionTitle,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = onGenerateSummary) {
                                Text(
                                    readerString(
                                        "ai_generate_summary_for_chapter",
                                        "Generate Summary for %1\$s",
                                        sectionTitle,
                                    )
                                )
                            }
                        }
                    }
                }
                tabs.getOrNull(1) -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            readerString(
                                "ai_recap_desc",
                                "Get a recap of the story up to your current position."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onGenerateRecap) {
                            Text(readerString("ai_generate_story_recap", "Generate Story Recap"))
                        }
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
        Text(
            readerString("ai_no_cached_summaries", "No cached summaries yet."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        items(cacheEntries, key = { it.sectionIndex }) { entry ->
            var expanded by remember(entry.sectionIndex) { mutableStateOf(false) }
            Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                ListItem(
                    headlineContent = {
                        Text(
                            entry.sectionTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
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
                    Text(
                        entry.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                HorizontalDivider()
            }
        }
        item {
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
}
