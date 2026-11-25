package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.Tag
import com.aryan.reader.shared.ui.SharedMobileTagSheet
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The sheet moved to shared (`SharedMobileTagSheet`) so Android and iOS run the same
 * tri-state/delete/create flow. These tests pin the Android benchmark behaviour.
 */
@RunWith(AndroidJUnit4::class)
class SharedMobileTagSheetTest {
    @get:Rule val composeTestRule = createComposeRule()

    private val full = Tag(id = "full", name = "Full")
    private val partial = Tag(id = "partial", name = "Partial")
    private val books = listOf(
        book("one", tags = listOf(full, partial)),
        book("two", tags = listOf(full)),
    )

    @Test
    fun rowClicksToggleAgainstMembershipAndCreateUsesTrimmedQuery() {
        var toggled: Pair<String, Boolean>? = null
        var created: String? = null
        composeTestRule.setContent {
            MaterialTheme {
                SharedMobileTagSheet(
                    allTags = listOf(full, partial),
                    selectedBookIds = setOf("one", "two"),
                    books = books,
                    onCreateAndAssign = { created = it },
                    onToggleTag = { id, assign -> toggled = id to assign },
                    onDeleteTag = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Full").assertIsDisplayed()
        composeTestRule.onNodeWithText("Partial").assertIsDisplayed()

        // Both selected books have "Full", so the row removes it.
        composeTestRule.onNodeWithTag("TagSheetRow_full").performClick()
        composeTestRule.runOnIdle { assertThat(toggled).isEqualTo("full" to false) }

        // Only one selected book has "Partial", so the row adds it.
        composeTestRule.onNodeWithTag("TagSheetRow_partial").performClick()
        composeTestRule.runOnIdle { assertThat(toggled).isEqualTo("partial" to true) }

        composeTestRule.onNodeWithTag("TagSheetSearch").performTextInput("  New Tag  ")
        composeTestRule.onNodeWithTag("TagSheetCreate").performClick()
        composeTestRule.runOnIdle { assertThat(created).isEqualTo("New Tag") }
    }

    @Test
    fun deletingATagAsksForConfirmationAndReportsTheId() {
        var deleted: String? = null
        composeTestRule.setContent {
            MaterialTheme {
                SharedMobileTagSheet(
                    allTags = listOf(full),
                    selectedBookIds = setOf("one"),
                    books = books,
                    onCreateAndAssign = {},
                    onToggleTag = { _, _ -> },
                    onDeleteTag = { deleted = it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("TagSheetDelete_full").performClick()
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.runOnIdle { assertThat(deleted).isEqualTo("full") }
    }

    private fun book(id: String, tags: List<Tag>) = BookItem(
        id = id,
        path = "/library/$id.epub",
        type = FileType.EPUB,
        displayName = "$id.epub",
        timestamp = 1L,
        tags = tags,
    )
}
