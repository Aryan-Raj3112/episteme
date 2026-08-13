package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.ui.SharedTtsWordReplacementLabels
import com.aryan.reader.shared.ui.SharedTtsWordReplacementsSheet
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedTtsWordReplacementsSheetTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun globalEnableSwitchUsesPreferencesCallback() {
        var updated = ReaderTtsReplacementPreferences()
        composeTestRule.setContent {
            MaterialTheme {
                SharedTtsWordReplacementsSheet(
                    isVisible = true,
                    bookId = "book",
                    bookTitle = "Title",
                    preferences = updated,
                    onPreferencesChange = { updated = it },
                    onDismiss = {},
                    labels = labels(),
                    newRuleId = { it },
                )
            }
        }

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.runOnIdle { assertThat(updated.isEnabled).isTrue() }
    }

    private fun labels() = SharedTtsWordReplacementLabels(
        title="TTS replacements", currentBook="Current book", close="Close",
        globalTab="Global", thisBookTab="This book",
        enable="Enable replacements", enableDescription="Enable description",
        addRule="Add rule", addBookRule="Add book rule",
        emptyGlobal="No global rules", emptyBook="No book rules",
        useGlobalHere="Use global", useGlobalHereDescription="Use global description",
        enableBookRules="Enable book rules", enableBookRulesDescription="Book rules description",
        inheritedGlobalRules="Inherited", noGlobalRules="No inherited rules",
        allowedInBook="Allowed", disabledForBook="Disabled",
        silence="Silence", suggestions="Suggestions",
        previewDefault="Preview", newReplacement="New replacement", editReplacement="Edit replacement",
        replace="Replace", speakAs="Speak as", enabled="Enabled", regex="Regex",
        wholeWord="Whole word", matchCase="Match case", previewInput="Preview input",
        cancel="Cancel", save="Save", rules="Rules", edit="Edit", delete="Delete",
        plainText="Plain text", caseSensitive="Case sensitive",
    )
}
