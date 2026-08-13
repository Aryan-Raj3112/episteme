package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ui.SharedBookInfoDialog
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedBookInfoDialogTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun displaysAndroidBookInformationAndDismissesFromCloseAction() {
        var dismissed = false
        val book = BookItem(
            id = "book-id",
            path = "/Books/example.pdf",
            type = FileType.PDF,
            displayName = "example.pdf",
            timestamp = 1L,
            title = "Example book",
            author = "Example author",
        )

        composeTestRule.setContent {
            MaterialTheme {
                SharedBookInfoDialog(
                    book = book,
                    formattedAddedDate = "1 January 2026",
                    displayLocation = "/Books/example.pdf",
                    displayTitle = "Example book",
                    onDismiss = { dismissed = true },
                    onSave = {},
                    onRestore = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Book information").assertExists()
        composeTestRule.onNodeWithContentDescription("Close").performClick()

        composeTestRule.runOnIdle { assertThat(dismissed).isTrue() }
    }
}
