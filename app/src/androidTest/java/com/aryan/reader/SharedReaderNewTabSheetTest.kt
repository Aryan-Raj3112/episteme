package com.aryan.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedReaderNewTabCandidate
import com.aryan.reader.shared.ui.SharedReaderNewTabSheetContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "add a tab" sheet is shared by both readers, so its row contract (title, optional author,
 * tap target, empty state) is asserted once here instead of in each reader's tests.
 */
@RunWith(AndroidJUnit4::class)
class SharedReaderNewTabSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun candidatesRenderTitleAndAuthor() {
        composeRule.setContent {
            SharedReaderNewTabSheetContent(
                title = "Add book to tab",
                emptyMessage = "No other books found",
                candidates = listOf(
                    SharedReaderNewTabCandidate("epub-1", "Sonnets", "William Shakespeare"),
                    SharedReaderNewTabCandidate("epub-2", "Barlasch of the Guard", null),
                ),
                onPick = {},
            )
        }

        composeRule.onNodeWithText("Add book to tab").assertIsDisplayed()
        composeRule.onNodeWithText("Sonnets").assertIsDisplayed()
        composeRule.onNodeWithText("William Shakespeare").assertIsDisplayed()
        composeRule.onNodeWithText("Barlasch of the Guard").assertIsDisplayed()
        composeRule.onNodeWithText("No other books found").assertDoesNotExist()
    }

    @Test
    fun tappingARowReportsThatBooksId() {
        val picked = mutableListOf<String>()
        composeRule.setContent {
            SharedReaderNewTabSheetContent(
                title = "Add book to tab",
                emptyMessage = "No other books found",
                candidates = listOf(
                    SharedReaderNewTabCandidate("epub-1", "Sonnets", "William Shakespeare"),
                    SharedReaderNewTabCandidate("epub-2", "Barlasch of the Guard", "Henry Seton Merriman"),
                ),
                onPick = { picked += it },
            )
        }

        composeRule.onNodeWithText("Barlasch of the Guard").performClick()

        assertEquals(listOf("epub-2"), picked)
    }

    @Test
    fun emptyCandidatesShowTheEmptyMessageInsteadOfRows() {
        var picked = false
        composeRule.setContent {
            SharedReaderNewTabSheetContent(
                title = "Add book to tab",
                emptyMessage = "No other books found",
                candidates = emptyList(),
                onPick = { picked = true },
            )
        }

        composeRule.onNodeWithText("No other books found").assertIsDisplayed()
        composeRule.onNodeWithText("Add book to tab").assertIsDisplayed()
        assertTrue(!picked)
    }
}
