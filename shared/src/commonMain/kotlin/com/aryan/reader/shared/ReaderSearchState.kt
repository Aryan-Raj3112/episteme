package com.aryan.reader.shared

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shared reader-search UI state and debounce policy.
 *
 * Search execution remains injected so EPUB and PDF engines can keep their platform-specific
 * indexing implementations while sharing the same observable behavior.
 */
@Stable
class ReaderSearchState(
    private val scope: CoroutineScope,
    private val searcher: suspend (String) -> List<SearchResult>,
) {
    var isSearchActive by mutableStateOf(false)
    var showSearchResultsPanel by mutableStateOf(true)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<SearchResult>>(emptyList())
    var isSearchInProgress by mutableStateOf(false)
    var currentSearchResultIndex by mutableIntStateOf(-1)

    val searchResultsCount by derivedStateOf { searchResults.size }
    val hasResults by derivedStateOf { searchResults.isNotEmpty() }

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        searchQuery = newQuery
        searchJob?.cancel()
        searchJob = scope.launch {
            if (newQuery.isBlank()) {
                clearResults()
                return@launch
            }
            delay(SEARCH_DEBOUNCE_MILLIS)
            executeSearch(newQuery)
        }
    }

    fun forceSearch() {
        searchJob?.cancel()
        searchJob = scope.launch {
            if (searchQuery.isBlank()) {
                clearResults()
                return@launch
            }
            executeSearch(searchQuery)
        }
    }

    private suspend fun executeSearch(query: String) {
        showSearchResultsPanel = true
        isSearchInProgress = true
        currentSearchResultIndex = -1
        searchResults = searcher(query)
        isSearchInProgress = false
    }

    private fun clearResults() {
        searchResults = emptyList()
        currentSearchResultIndex = -1
        isSearchInProgress = false
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 350L
    }
}
