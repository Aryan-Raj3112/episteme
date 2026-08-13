package com.aryan.reader.shared

import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSearchStateTest {
    @Test
    fun queryChangePreservesAndroidDebounceAndResultState() = runTest {
        val searches = mutableListOf<String>()
        val result = searchResult("term")
        val state = ReaderSearchState(this) { query ->
            searches += query
            listOf(result)
        }

        state.onQueryChange("term")
        advanceTimeBy(349)
        assertTrue(searches.isEmpty())

        advanceUntilIdle()

        assertEquals(listOf("term"), searches)
        assertEquals(listOf(result), state.searchResults)
        assertEquals(-1, state.currentSearchResultIndex)
        assertTrue(state.showSearchResultsPanel)
        assertFalse(state.isSearchInProgress)
    }

    @Test
    fun blankQueryCancelsPendingSearchAndClearsResults() = runTest {
        var searchCount = 0
        val state = ReaderSearchState(this) {
            searchCount += 1
            listOf(searchResult(it))
        }.apply {
            searchResults = listOf(searchResult("old"))
            currentSearchResultIndex = 0
            isSearchInProgress = true
        }

        state.onQueryChange("pending")
        state.onQueryChange("")
        advanceUntilIdle()

        assertEquals(0, searchCount)
        assertTrue(state.searchResults.isEmpty())
        assertEquals(-1, state.currentSearchResultIndex)
        assertFalse(state.isSearchInProgress)
    }

    @Test
    fun forceSearchRunsImmediatelyWithCurrentQuery() = runTest {
        val searches = mutableListOf<String>()
        val state = ReaderSearchState(this) { query ->
            searches += query
            emptyList()
        }.apply { searchQuery = "now" }

        state.forceSearch()
        advanceUntilIdle()

        assertEquals(listOf("now"), searches)
    }

    private fun searchResult(query: String) = SearchResult(
        locationInSource = 0,
        locationTitle = "Chapter",
        snippet = AnnotatedString(query),
        query = query,
        occurrenceIndexInLocation = 0,
        chunkIndex = 0,
    )
}
