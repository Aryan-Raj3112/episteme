package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.MobileUnifiedLibraryFilter
import com.aryan.reader.shared.ui.SharedAndroidUnifiedHomeStrings
import com.aryan.reader.shared.ui.SharedAndroidUnifiedLibraryHome
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home's three "nothing in the grid" states used to collapse into one sentence.
 *
 * A running first scan, a filtered-to-nothing view, and a genuinely empty library now render
 * differently, and the filtered case offers the action that actually helps.
 */
@RunWith(AndroidJUnit4::class)
class SharedAndroidUnifiedLibraryHomeStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val strings = SharedAndroidUnifiedHomeStrings(
        noBooks = "No books in this view",
        filterBooks = "Filter books",
        gridView = "Grid view",
        listView = "List view",
        filterLabels = MobileUnifiedLibraryFilter.entries.associateWith { it.name },
    )

    private fun setHome(
        isLoading: Boolean = false,
        emptyActionLabel: String? = null,
        onEmptyAction: (() -> Unit)? = null,
        sectionTitle: String? = null,
        onControls: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                SharedAndroidUnifiedLibraryHome<String>(
                    books = emptyList(),
                    continueReading = null,
                    filter = MobileUnifiedLibraryFilter.ALL,
                    sortLabel = "Recent",
                    advancedFilterCount = 0,
                    useListView = false,
                    strings = strings,
                    itemKey = { it },
                    onFilterChange = {},
                    onControls = onControls,
                    onAdvancedFilters = {},
                    onListViewChange = {},
                    continueCard = { _, _ -> },
                    bookCard = { Text(it) },
                    bookListItem = { Text(it) },
                    isLoading = isLoading,
                    emptyActionLabel = emptyActionLabel,
                    onEmptyAction = onEmptyAction,
                    sectionTitle = sectionTitle,
                )
            }
        }
    }

    @Test
    fun firstScanShowsTheGridSkeletonInsteadOfAnEmptyMessage() {
        setHome(isLoading = true)

        composeTestRule.onNodeWithTag("UnifiedLibraryHomeLoading").assertExists()
        composeTestRule.onNodeWithText("No books in this view").assertDoesNotExist()
    }

    @Test
    fun filteredEmptyViewOffersTheClearAction() {
        var cleared = false
        setHome(emptyActionLabel = "Clear filters", onEmptyAction = { cleared = true })

        composeTestRule.onNodeWithText("Clear filters").performClick()

        composeTestRule.runOnIdle {
            assertThat(cleared).isTrue()
        }
    }

    @Test
    fun theHeaderCarriesTheSectionTitleAndTheSortControl() {
        var controlsOpened = false
        setHome(sectionTitle = "Your books", onControls = { controlsOpened = true })

        composeTestRule.onNodeWithTag("UnifiedLibraryHomeSectionTitle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your books").assertIsDisplayed()

        composeTestRule.onNodeWithTag("UnifiedLibraryHomeSort").performClick()

        composeTestRule.runOnIdle {
            assertThat(controlsOpened).isTrue()
        }
    }

    @Test
    fun anEmptyLibraryShowsTheIllustratedStateNotJustASentence() {
        setHome(sectionTitle = "Your books")

        composeTestRule.onNodeWithTag("UnifiedLibraryHomeEmpty").assertExists()
        composeTestRule.onNodeWithText("No books in this view").assertIsDisplayed()
    }

    @Test
    fun aPlainEmptyLibraryHasNoActionAndNoSkeleton() {
        setHome()

        composeTestRule.onNodeWithText("No books in this view").assertExists()
        composeTestRule.onNodeWithTag("UnifiedLibraryHomeLoading").assertDoesNotExist()
    }
}
