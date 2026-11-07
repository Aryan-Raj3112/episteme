package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.SortOrder
import com.aryan.reader.shared.ui.SharedAndroidUnifiedShelves
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Library Beta shelf screen keeps the retired shelf screens' sort picker: the
 * current order is always visible, choosing another order reaches the host reducer,
 * and hosts that do not sort shelves never see the control.
 *
 * Shelf book ordering itself is projector-covered (`SharedLibraryProjectorTest`);
 * these pin the control and its host wiring.
 */
@RunWith(AndroidJUnit4::class)
class SharedAndroidUnifiedShelvesTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun sortControlShowsCurrentOrderAndReportsSelection() {
        var selected: SortOrder? = null
        val sortOrder = mutableStateOf(SortOrder.RECENT)
        composeTestRule.setContent {
            MaterialTheme {
                SharedAndroidUnifiedShelves(
                    visibleShelves = listOf("Reading"),
                    selectedShelf = "Reading",
                    selectedBooks = listOf("book"),
                    noShelvesLabel = "No shelves yet",
                    shelfKey = { it },
                    shelfName = { it },
                    shelfBookCountLabel = { "1 book" },
                    bookKey = { it },
                    onShelfSelected = {},
                    bookCard = {},
                    sortOrder = sortOrder.value,
                    sortLabels = SortOrder.entries.associateWith { it.name },
                    sortDescription = "Sort",
                    selectedSortDescription = "Selected",
                    onSortOrderChange = {
                        selected = it
                        sortOrder.value = it
                    },
                )
            }
        }

        composeTestRule.onNodeWithText(SortOrder.RECENT.name).assertIsDisplayed()
        composeTestRule.onNodeWithTag("ShelfSortButton").performClick()
        composeTestRule.onNodeWithText(SortOrder.TITLE_ASC.name).performClick()

        composeTestRule.runOnIdle { assertThat(selected).isEqualTo(SortOrder.TITLE_ASC) }
        composeTestRule.onNodeWithText(SortOrder.TITLE_ASC.name).assertIsDisplayed()
    }

    @Test
    fun sortControlIsHiddenWithoutHostShelfSorting() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedAndroidUnifiedShelves(
                    visibleShelves = listOf("Reading"),
                    selectedShelf = "Reading",
                    selectedBooks = listOf("book"),
                    noShelvesLabel = "No shelves yet",
                    shelfKey = { it },
                    shelfName = { it },
                    shelfBookCountLabel = { "1 book" },
                    bookKey = { it },
                    onShelfSelected = {},
                    bookCard = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("ShelfSortButton").assertDoesNotExist()
    }
}
