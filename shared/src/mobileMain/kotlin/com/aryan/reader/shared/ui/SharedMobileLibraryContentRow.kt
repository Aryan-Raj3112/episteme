package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.cardAuthor
import com.aryan.reader.shared.cardTitle
import com.aryan.reader.shared.search.SharedContentSearchHitVisibility
import com.aryan.reader.shared.search.SharedGlobalSearchBookResult
import com.aryan.reader.shared.search.SharedGlobalSearchHit

/**
 * Shared inside-file content row for the mobile unified library (iOS; Android
 * keeps its own app-layer row as the benchmark). One row per matching book:
 * cover + title/author + uncapped match badge, then the best chunk snippet(s)
 * with shared highlight spans.
 *
 * Snippet taps deep-link into the hit ([onHitClick]); tapping anywhere else
 * resumes the book ([onOpenBook]); long-press selects ([onLongPressBook]).
 * Labels are English v1 through [sharedContentSearchLocationLabel];
 * localization follows the host's string table when it lands.
 */
@Composable
fun SharedMobileLibraryContentRow(
    book: BookItem,
    result: SharedGlobalSearchBookResult,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenBook: () -> Unit,
    onLongPressBook: () -> Unit = {},
    onHitClick: (SharedGlobalSearchHit) -> Unit = {},
    matchCountText: (Int) -> String = { count -> if (count == 1) "1 match" else "$count matches" },
    moreMatchesText: (Int) -> String = { hidden -> "Show $hidden more" },
    fewerMatchesText: String = "Show fewer",
    modifier: Modifier = Modifier,
) {
    val visibleHits = SharedContentSearchHitVisibility.hitsFor(result, expanded)
    val hiddenMatches = SharedContentSearchHitVisibility.hiddenMatchCount(result, expanded)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpenBook,
                onLongClick = onLongPressBook,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SharedMobileBookCover(
                    book = book,
                    selected = false,
                    pinned = false,
                    onTogglePinned = {},
                    modifier = Modifier.size(width = 40.dp, height = 56.dp),
                    compact = true,
                )
                Column(
                    Modifier.weight(1f).padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = book.cardTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    val author = book.author?.takeIf { it.isNotBlank() }
                    if (!author.isNullOrBlank()) {
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    } else {
                        // Keep row height stable with the no-author fallback the
                        // cards use, so content rows align with metadata rows.
                        Text(
                            text = book.cardAuthor(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (result.matchingChunkCount > 1) {
                    Text(
                        text = matchCountText(result.matchingChunkCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            visibleHits.forEach { hit ->
                SharedContentSearchSnippet(
                    snippet = hit.snippet,
                    matchRanges = hit.matchRanges,
                    locationLabel = sharedContentSearchLocationLabel(hit),
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
                        text = if (expanded) fewerMatchesText else moreMatchesText(hiddenMatches),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}
