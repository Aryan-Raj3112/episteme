package com.aryan.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedAndroidLibraryScaffold
import com.aryan.reader.shared.ui.SharedAndroidLibraryScaffoldStrings
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalFoundationApi::class)
@RunWith(AndroidJUnit4::class)
class SharedAndroidLibraryScaffoldTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun shelfFabIsOwnedBySharedScaffoldAndHiddenDuringSelection() {
        var newShelfClicks = 0
        val hasShelfSelection = mutableStateOf(false)
        composeTestRule.setContent {
            MaterialTheme {
                val pagerState = rememberPagerState(initialPage = 1, pageCount = { 2 })
                SharedAndroidLibraryScaffold(
                    pagerState = pagerState,
                    scope = rememberCoroutineScope(),
                    tabTitles = listOf("Library", "Shelves"),
                    hasBookSelection = false,
                    hasShelfSelection = hasShelfSelection.value,
                    isSearchActive = false,
                    searchQuery = "",
                    showAddFileFab = false,
                    strings = strings(),
                    onSearchQueryChange = {},
                    onSearchActiveChange = {},
                    onSelectFile = {},
                    onNewShelf = { newShelfClicks++ },
                    onTabAnimationStarted = { _, _ -> },
                    onTabAnimationFinished = { _, _ -> },
                    nowNanos = { 0L },
                    bookContextualTopBar = {},
                    shelfContextualTopBar = {},
                    normalTopBarActions = {},
                    filterChips = {},
                    pageContent = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("LibraryNewShelfFab").performClick()
        composeTestRule.runOnIdle { assertThat(newShelfClicks).isEqualTo(1) }

        composeTestRule.runOnIdle { hasShelfSelection.value = true }
        composeTestRule.onAllNodesWithTag("LibraryNewShelfFab").assertCountEquals(0)
    }

    private fun strings() = SharedAndroidLibraryScaffoldStrings(
        title = "Library",
        searchPlaceholder = "Search",
        closeSearchDescription = "Close",
        clearQueryDescription = "Clear",
        addFile = "Add file",
        newShelf = "New shelf",
    )
}
