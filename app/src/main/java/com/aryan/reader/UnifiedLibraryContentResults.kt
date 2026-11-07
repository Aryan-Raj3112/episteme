package com.aryan.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.search.SharedContentSearchHitVisibility
import com.aryan.reader.shared.search.SharedGlobalSearchBookResult
import com.aryan.reader.shared.search.SharedGlobalSearchHit
import com.aryan.reader.shared.ui.SharedContentSearchSnippet

/**
 * One inside-file content match for Library Beta: book identity, the chunk of
 * book text the query hit, and an affordance for the rest of that book's
 * matches. Only the best chunk shows until expanded, which keeps the list
 * readable when one book repeats the query term many times.
 *
 * Tapping a snippet deep-links into the hit ([onHitClick]); tapping anywhere
 * else resumes the book ([onClick]).
 */
@Composable
internal fun LibraryContentSearchResultRow(
    item: RecentFileItem,
    result: SharedGlobalSearchBookResult,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onHitClick: (SharedGlobalSearchHit) -> Unit = {},
    usePdfFileNameAsDisplayName: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val visibleHits = SharedContentSearchHitVisibility.hitsFor(result, expanded)
    val hiddenMatches = SharedContentSearchHitVisibility.hiddenMatchCount(result, expanded)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThemedBookCover(
                    item = item,
                    contentDescription = item.displayName,
                    modifier = Modifier.size(width = 40.dp, height = 56.dp),
                )
                Column(
                    Modifier.weight(1f).padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = item.cardTitle(usePdfFileNameAsDisplayName),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    val author = item.author?.takeIf { it.isNotBlank() } ?: ""
                    if (author.isNotBlank()) {
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (result.matchingChunkCount > 1) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.library_content_search_match_count,
                            result.matchingChunkCount,
                            result.matchingChunkCount,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            visibleHits.forEach { hit ->
                SharedContentSearchSnippet(
                    snippet = hit.snippet,
                    matchRanges = hit.matchRanges,
                    locationLabel = contentSearchLocationLabel(hit),
                    modifier = Modifier.padding(top = 8.dp).clickable { onHitClick(hit) },
                )
            }
            if (hiddenMatches > 0 || (expanded && result.hits.size > 1)) {
                TextButton(onClick = onToggleExpanded) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.library_content_search_fewer_matches)
                        } else {
                            stringResource(
                                R.string.library_content_search_more_matches,
                                hiddenMatches,
                            )
                        },
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/** "Chapter 4", "Page 12", or both — whatever the format can address. */
@Composable
internal fun contentSearchLocationLabel(hit: SharedGlobalSearchHit): String {
    val chapter = hit.chapterIndex?.let { index ->
        stringResource(R.string.library_content_search_chapter, index + 1)
    }
    val page = hit.pageIndex?.let { index ->
        stringResource(R.string.library_content_search_page, index + 1)
    }
    return listOfNotNull(chapter, page).joinToString(" · ")
}
