package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.AddBooksSource
import com.aryan.reader.shared.SortOrder
import com.aryan.reader.shared.ui.SharedAndroidAddBooksModeScreen
import com.aryan.reader.shared.ui.SharedAndroidShelfScreenStrings
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedAndroidAddBooksModeScreenTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun sourceAndSelectedAddCallbacksRemainOwnedBySharedScreen() {
        var source: AddBooksSource? = null
        var addClicks = 0
        composeTestRule.setContent {
            MaterialTheme {
                SharedAndroidAddBooksModeScreen(
                    shelfName = "Reading",
                    books = listOf("book"),
                    selectedCount = 1,
                    source = AddBooksSource.UNSHELVED,
                    sortOrder = SortOrder.RECENT,
                    strings = strings(),
                    bookKey = { it },
                    onSortOrderChange = {},
                    onSourceChange = { source = it },
                    onBack = {},
                    onAddSelectedBooks = { addClicks++ },
                    bookRow = {},
                    sortIcon = {},
                )
            }
        }

        composeTestRule.onNodeWithText("All books").performClick()
        composeTestRule.onNodeWithText("Add (1)", useUnmergedTree = true).performClick()
        composeTestRule.runOnIdle {
            assertThat(source).isEqualTo(AddBooksSource.ALL_BOOKS)
            assertThat(addClicks).isEqualTo(1)
        }
    }

    private fun strings() = SharedAndroidShelfScreenStrings(
        back = "Back", closeSearch = "Close", clearQuery = "Clear", searchPlaceholder = "Search",
        sortDescription = "Sort", selectedDescription = "Selected", searchShelfDescription = "Search shelf",
        moreOptionsDescription = "More", renameShelf = "Rename", deleteShelf = "Delete", addBooks = "Add books",
        emptyShelf = "Empty", noResults = { "No $it" }, foldersSection = "Folders", filesSection = "Files",
        addToShelfTitle = { "Add to $it" }, addCount = { "Add ($it)" }, noUnshelvedBooks = "No unshelved",
        allBooksInShelf = "All already added", sortLabels = SortOrder.entries.associateWith { it.name },
        sourceLabels = mapOf(AddBooksSource.UNSHELVED to "Unshelved", AddBooksSource.ALL_BOOKS to "All books"),
    )
}
