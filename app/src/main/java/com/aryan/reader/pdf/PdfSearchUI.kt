package com.aryan.reader.pdf

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.aryan.reader.R
import com.aryan.reader.shared.SearchHighlightMode
import com.aryan.reader.shared.SearchResult

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
    com.aryan.reader.shared.ui.SharedMobilePdfSearchNavigationPill(
        text = text,
        highlightMode = mode,
        onToggleHighlightMode = onToggleMode,
        onPrevious = onPrev,
        onNext = onNext,
        onShowResults = onTextClick,
        isPrevEnabled = isPrevEnabled,
        isNextEnabled = isNextEnabled
    )
}

@Composable
fun PdfSearchResultsPanel(
    lazyResults: LazyPagingItems<SearchResult>,
    totalPageCount: Int,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    com.aryan.reader.shared.ui.SharedReaderLazySearchResultsPanel(
        itemCount = lazyResults.itemCount,
        isRefreshing = lazyResults.loadState.refresh is LoadState.Loading,
        noResultsText = stringResource(R.string.search_no_results_simple),
        resultsCountText = stringResource(R.string.msg_results_found_pages, totalPageCount),
        itemAt = { index -> lazyResults[index] },
        onResultClick = onResultClick,
        modifier = modifier,
    )
}

@Composable
fun PdfSearchResultsList(
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    com.aryan.reader.shared.ui.SharedReaderSearchResultsPanel(
        results = results,
        isSearching = false,
        noResultsText = stringResource(R.string.search_no_results_simple),
        resultsCountText = { count ->
            context.resources.getQuantityString(R.plurals.search_matches_count, count, count)
        },
        onResultClick = onResultClick,
        modifier = modifier,
    )
}
