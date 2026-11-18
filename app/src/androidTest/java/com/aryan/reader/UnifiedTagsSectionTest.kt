package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.SortOrder
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tags had no surface after Library Beta replaced the legacy screen: the generated
 * `tag_*` shelves were filtered out of the shelf lists. The Tags destination reads
 * `allTags` directly, so it must keep showing tags that have no books yet.
 */
@RunWith(AndroidJUnit4::class)
class UnifiedTagsSectionTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun tagsListIncludesEmptyTagsAndReportsSelection() {
        var selectedTagId: String? = null
        composeTestRule.setContent {
            MaterialTheme {
                UnifiedTagsSection(
                    modifier = Modifier,
                    tags = listOf(
                        TagEntity(id = "fiction", name = "Fiction", color = null, createdAt = 1L),
                        TagEntity(id = "empty", name = "Empty tag", color = null, createdAt = 2L),
                    ),
                    books = emptyList(),
                    selectedTagId = null,
                    selectedBookIds = emptySet(),
                    downloadingBookIds = emptySet(),
                    usePdfFileNameAsDisplayName = false,
                    useListView = true,
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    sortOrder = SortOrder.RECENT,
                    onTagSelected = { selectedTagId = it },
                    onListViewChange = {},
                    onSortOrderChange = {},
                    onBookClick = {},
                    onBookLongClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Fiction").assertIsDisplayed()
        composeTestRule.onNodeWithText("Empty tag").assertIsDisplayed()

        composeTestRule.onNodeWithTag("UnifiedTagRow_empty").performClick()
        composeTestRule.runOnIdle { assertThat(selectedTagId).isEqualTo("empty") }
    }
}
