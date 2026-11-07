package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.search.SharedGlobalSearchHit

/**
 * Where a content hit lives, shared so every shell labels the same
 * location the same way ("Chapter N", "Page N", both, or blank when
 * neither axis is known). Indices are 0-based in the model, 1-based
 * for people. Android keeps its own localized formatter as the
 * benchmark; this is the shared fallback iOS renders.
 */
fun sharedContentSearchLocationLabel(hit: SharedGlobalSearchHit): String {
    val chapter = hit.chapterIndex?.takeIf { it >= 0 }?.let { "Chapter ${it + 1}" }
    val page = hit.pageIndex?.takeIf { it >= 0 }?.let { "Page ${it + 1}" }
    return listOfNotNull(chapter, page).joinToString(" · ")
}

/**
 * One inside-file match line: the chunk of book text the query hit, with every
 * matched term highlighted, and where in the book it came from.
 *
 * Match ranges come from the shared indexer, so the identical spans land on
 * iOS/desktop without a platform-specific highlighter.
 */
@Composable
fun SharedContentSearchSnippet(
    snippet: String,
    matchRanges: List<IntRange>,
    locationLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = rememberHighlightedText(snippet, matchRanges),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (locationLabel.isNotBlank()) {
            Text(
                text = locationLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun rememberHighlightedText(text: String, ranges: List<IntRange>): AnnotatedString {
    val highlightColor = MaterialTheme.colorScheme.primaryContainer
    val highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    return remember(text, ranges, highlightColor, highlightTextColor) {
        buildAnnotatedString {
            var cursor = 0
            ranges.sortedBy { it.first }.forEach { range ->
                val start = range.first.coerceIn(0, text.length)
                val end = (range.last + 1).coerceIn(start, text.length)
                if (start > cursor) append(text.substring(cursor, start))
                withStyle(
                    SpanStyle(
                        background = highlightColor,
                        color = highlightTextColor,
                        fontWeight = FontWeight.Medium,
                    )
                ) {
                    append(text.substring(start, end))
                }
                cursor = end
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }
}
