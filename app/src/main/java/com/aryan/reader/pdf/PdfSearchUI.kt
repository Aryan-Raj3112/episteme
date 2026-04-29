package com.aryan.reader.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.aryan.reader.R
import com.aryan.reader.SearchResult

@Composable
internal fun SearchNavigationPill(
    text: String,
    mode: SearchHighlightMode,
    onToggleMode: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTextClick: () -> Unit,
    isPrevEnabled: Boolean = true,
    isNextEnabled: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .shadow(6.dp, RoundedCornerShape(50))
            .height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            // Toggle Mode
            IconButton(onClick = onToggleMode) {
                Icon(
                    imageVector = if (mode == SearchHighlightMode.ALL) Icons.Default.Visibility
                    else Icons.Default.VisibilityOff,
                    contentDescription = stringResource(R.string.content_desc_toggle_search_highlights),
                    tint = if (mode == SearchHighlightMode.ALL) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Vertical Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.3f
                        )
                    )
            )

            // Prev
            IconButton(onClick = onPrev, enabled = isPrevEnabled) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.tooltip_prev_result),
                    tint = if (isPrevEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            // Counter/Text
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTextClick
                    )
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Next
            IconButton(onClick = onNext, enabled = isNextEnabled) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.tooltip_next_result),
                    tint = if (isNextEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}

@Composable
fun PdfSearchResultsPanel(
    lazyResults: LazyPagingItems<SearchResult>,
    totalPageCount: Int,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (lazyResults.itemCount == 0 && lazyResults.loadState.refresh !is LoadState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.search_no_results_simple), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column {
                Text(
                    text = stringResource(R.string.msg_results_found_pages, totalPageCount),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                HorizontalDivider()

                LazyColumn(modifier = Modifier.testTag("SearchResultsList")) {
                    items(count = lazyResults.itemCount, key = lazyResults.itemKey {
                        "${it.locationInSource}_${it.occurrenceIndexInLocation}"
                    }, contentType = lazyResults.itemContentType { "SearchResult" }) { index ->
                        val result = lazyResults[index]
                        if (result != null) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        result.locationTitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }, supportingContent = {
                                    Text(
                                        result.snippet, style = MaterialTheme.typography.bodyMedium
                                    )
                                }, modifier = Modifier
                                    .testTag(
                                        "SearchResultItem_${result.locationInSource}"
                                    )
                                    .clickable { onResultClick(result) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        if (lazyResults.loadState.refresh is LoadState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun PdfSearchResultsList(
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.search_no_results_simple), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column {
                Text(
                    text = context.resources.getQuantityString(
                        R.plurals.search_matches_count,
                        results.size,
                        results.size
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                HorizontalDivider()

                LazyColumn(modifier = Modifier.testTag("SearchResultsList")) {
                    itemsIndexed(results) { _, result ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    result.locationTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }, supportingContent = {
                                Text(
                                    result.snippet, style = MaterialTheme.typography.bodyMedium
                                )
                            }, modifier = Modifier
                                .testTag(
                                    "SearchResultItem_${result.locationInSource}"
                                )
                                .clickable { onResultClick(result) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
