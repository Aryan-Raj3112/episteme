package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedRenameShelfDialog
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedRenameShelfDialogTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun unchangedNameCannotConfirmButChangedNameIsEmitted() {
        var renamedTo: String? = null
        composeTestRule.setContent {
            MaterialTheme {
                SharedRenameShelfDialog(
                    initialName = "Current",
                    title = "Rename shelf",
                    namePlaceholder = "Shelf name",
                    confirmLabel = "Rename",
                    cancelLabel = "Cancel",
                    onConfirm = { renamedTo = it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Rename").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Current").performTextReplacement("Updated")
        composeTestRule.onNodeWithText("Rename").performClick()
        composeTestRule.runOnIdle { assertThat(renamedTo).isEqualTo("Updated") }
    }
}
