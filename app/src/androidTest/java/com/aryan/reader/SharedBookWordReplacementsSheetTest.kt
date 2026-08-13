package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.ui.SharedBookWordReplacementLabels
import com.aryan.reader.shared.ui.SharedBookWordReplacementsSheet
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedBookWordReplacementsSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun addRuleUsesInjectedIdentityAndPreferencesCallback() {
        var updated = ReaderBookReplacementPreferences()
        composeTestRule.setContent {
            MaterialTheme {
                SharedBookWordReplacementsSheet(
                    isVisible = true,
                    bookId = "book",
                    bookTitle = "Title",
                    preferences = updated,
                    onPreferencesChange = { updated = it },
                    onDismiss = {},
                    labels = labels(),
                    newRuleId = { "new-id" },
                )
            }
        }

        composeTestRule.onNodeWithText("Add rule").performClick()
        composeTestRule.onNodeWithText("Replace").performTextInput("Alice")
        composeTestRule.onNodeWithText("With").performTextInput("Bob")
        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.runOnIdle {
            val rule = updated.rulesForFile("book").single()
            assertThat(rule.id).isEqualTo("new-id")
            assertThat(rule.from).isEqualTo("Alice")
            assertThat(rule.to).isEqualTo("Bob")
        }
    }

    private fun labels() = SharedBookWordReplacementLabels(
        title = "On-page replacements",
        currentBook = "Current book",
        close = "Close",
        addRule = "Add rule",
        empty = "No rules",
        previewDefault = "Preview",
        newReplacement = "New replacement",
        editReplacement = "Edit replacement",
        replace = "Replace",
        with = "With",
        enabled = "Enabled",
        regex = "Regex",
        wholeWord = "Whole word",
        matchCase = "Match case",
        previewInput = "Preview input",
        cancel = "Cancel",
        save = "Save",
        rules = "Rules",
        emptyReplacement = "Empty",
        edit = "Edit",
        delete = "Delete",
        plainText = "Plain text",
        caseSensitive = "Case sensitive",
    )
}

