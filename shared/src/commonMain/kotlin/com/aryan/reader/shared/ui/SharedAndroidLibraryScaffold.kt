package com.aryan.reader.shared.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class SharedAndroidLibraryScaffoldStrings(
    val title: String,
    val searchPlaceholder: String,
    val closeSearchDescription: String,
    val clearQueryDescription: String,
    val addFile: String,
    val newShelf: String,
)

/** Exact Android library top-level scaffold/pager; platform page bodies and actions are slots. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SharedAndroidLibraryScaffold(
    pagerState: PagerState,
    scope: CoroutineScope,
    tabTitles: List<String>,
    hasBookSelection: Boolean,
    hasShelfSelection: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    showAddFileFab: Boolean,
    strings: SharedAndroidLibraryScaffoldStrings,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSelectFile: () -> Unit,
    onNewShelf: () -> Unit,
    onTabAnimationStarted: (index: Int, title: String) -> Unit,
    onTabAnimationFinished: (index: Int, elapsedStartNanos: Long) -> Unit,
    nowNanos: () -> Long,
    bookContextualTopBar: @Composable () -> Unit,
    shelfContextualTopBar: @Composable () -> Unit,
    normalTopBarActions: @Composable () -> Unit,
    filterChips: @Composable () -> Unit,
    pageContent: @Composable (page: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember(isSearchActive) { mutableStateOf(TextFieldValue(searchQuery, TextRange(searchQuery.length))) }
    LaunchedEffect(isSearchActive) { if (isSearchActive) focusRequester.requestFocus() }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                when {
                    hasBookSelection -> bookContextualTopBar()
                    hasShelfSelection && pagerState.currentPage == 1 -> shelfContextualTopBar()
                    isSearchActive -> SharedMobileLibrarySearchTopBar(
                        value = fieldValue,
                        onValueChange = { fieldValue = it; onSearchQueryChange(it.text) },
                        showClear = searchQuery.isNotEmpty(),
                        onClose = { onSearchActiveChange(false) },
                        onClear = { fieldValue = TextFieldValue("", TextRange.Zero); onSearchQueryChange("") },
                        placeholder = strings.searchPlaceholder,
                        closeContentDescription = strings.closeSearchDescription,
                        clearContentDescription = strings.clearQueryDescription,
                        focusRequester = focusRequester,
                        textFieldModifier = Modifier.testTag("LibrarySearchTextField"),
                    )
                    else -> {
                        SharedMobileTopAppBar(title = { Text(strings.title) }, actions = { normalTopBarActions() })
                        TabRow(selectedTabIndex = pagerState.currentPage) {
                            tabTitles.forEachIndexed { index, title ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = {
                                        onTabAnimationStarted(index, title)
                                        if (pagerState.currentPage != index) scope.launch {
                                            val start = nowNanos()
                                            pagerState.animateScrollToPage(index)
                                            onTabAnimationFinished(index, start)
                                        }
                                    },
                                    text = { Text(title) },
                                )
                            }
                        }
                        filterChips()
                    }
                }
            }
        },
        floatingActionButton = {
            if (!hasBookSelection && !hasShelfSelection) when (pagerState.currentPage) {
                0 -> if (showAddFileFab) ExtendedFloatingActionButton(
                    text = { Text(strings.addFile) },
                    icon = { Icon(Icons.Default.Add, strings.addFile) },
                    onClick = onSelectFile,
                    modifier = Modifier.padding(16.dp),
                )
                1 -> ExtendedFloatingActionButton(
                    text = { Text(strings.newShelf) },
                    icon = { Icon(Icons.Default.Add, strings.newShelf) },
                    onClick = onNewShelf,
                    modifier = Modifier.padding(16.dp).testTag("LibraryNewShelfFab"),
                )
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
            flingBehavior = PagerDefaults.flingBehavior(state = pagerState, snapPositionalThreshold = 0.25f),
            beyondViewportPageCount = 0,
            key = { it },
        ) { page -> pageContent(page) }
    }
}
