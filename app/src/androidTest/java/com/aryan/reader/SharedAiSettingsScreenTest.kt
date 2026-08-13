package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.isToggleable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ui.SharedAiSettingsScreen
import com.aryan.reader.shared.ui.SharedAiSettingsStrings
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedAiSettingsScreenTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun modelModeAndKeyConfirmationEmitPlatformCallbacks() {
        var updated: ReaderAiByokSettings? = null
        var savedProvider = ""
        var savedKey = ""
        composeTestRule.setContent {
            MaterialTheme {
                SharedAiSettingsScreen(
                    settings = ReaderAiByokSettings(useOneModel = true),
                    maskedKeys = emptyMap(),
                    strings = strings(),
                    onBackClick = {},
                    onSaveKey = { provider, key -> savedProvider = provider; savedKey = key },
                    onDeleteKey = {},
                    onSettingsChange = { updated = it },
                )
            }
        }

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.onNodeWithText("Save key").assertIsNotEnabled()
        composeTestRule.onNodeWithText("API key").performTextInput("secret-value")
        composeTestRule.onNodeWithText("Save key").performClick()
        composeTestRule.onNodeWithText("Save Gemini key?").performClick()
        composeTestRule.runOnIdle {
            assertThat(updated?.useOneModel).isFalse()
            assertThat(savedProvider).isEqualTo("gemini")
            assertThat(savedKey).isEqualTo("secret-value")
        }
    }

    private fun strings() = SharedAiSettingsStrings(
        title = "AI settings",
        backDescription = "Back",
        savedKeys = "Saved keys",
        noKeySaved = "No key",
        addOrReplaceKey = "Add key",
        providerLabel = "Provider",
        apiKeyLabel = "API key",
        saveKey = "Save key",
        useOneModel = "Use one model",
        useOneModelDescription = "One model description",
        allFeatures = "All features",
        allFeaturesDescription = "All description",
        smartDictionary = "Dictionary",
        smartDictionaryDescription = "Dictionary description",
        summaries = "Summaries",
        summariesDescription = "Summaries description",
        recaps = "Recaps",
        recapsDescription = "Recaps description",
        cloudTts = "Cloud TTS",
        cloudTtsDescription = "Cloud description",
        modelLabel = "Model",
        noModelSelected = "No model",
        saveDialogDescription = "Save description",
        deleteDialogDescription = "Delete description",
        saveAction = "Save Gemini key?",
        deleteAction = "Delete",
        cancelAction = "Cancel",
        providerLabels = mapOf("gemini" to "Gemini", "groq" to "Groq"),
        saveDialogTitle = { "Save $it" },
        deleteDialogTitle = { "Delete $it" },
        deleteKeyDescription = { "Delete $it key" },
    )
}
