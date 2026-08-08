package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedDictionarySettingsLabels
import com.aryan.reader.shared.ui.SharedDictionarySettingsSheet
import com.aryan.reader.shared.ui.SharedExternalAppOption
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedDictionarySettingsSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun engineAndDictionarySelectionUseAndroidCallbacks() {
        var online = true
        var selectedPackage: String? = "dictionary.one"
        composeTestRule.setContent {
            MaterialTheme {
                SharedDictionarySettingsSheet(
                    isVisible = true,
                    aiFeaturesEnabled = true,
                    useOnlineDictionary = online,
                    onToggleOnlineDictionary = { online = it },
                    dictionaryApps = listOf(SharedExternalAppOption("dictionary.one", "Dictionary One", false)),
                    searchApps = emptyList(),
                    selectedDictionaryPackageName = selectedPackage,
                    onSelectDictionaryPackage = { selectedPackage = it },
                    selectedTranslatePackageName = null,
                    onSelectTranslatePackage = {},
                    selectedSearchPackageName = null,
                    onSelectSearchPackage = {},
                    maxSheetHeight = 700.dp,
                    labels = testLabels(),
                    appIcon = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("External app").performClick()
        composeTestRule.onNodeWithText("Dictionary One").performClick()
        composeTestRule.onNodeWithText("None").performClick()

        composeTestRule.runOnIdle {
            assertThat(online).isFalse()
            assertThat(selectedPackage).isEmpty()
        }
    }

    private fun testLabels() = SharedDictionarySettingsLabels(
        title = "Lookup settings",
        dictionaryEngine = "Dictionary engine",
        smartAi = "Smart AI",
        externalApp = "External app",
        aiDescription = "AI description",
        externalDescription = "External description",
        fallbackApp = "Fallback app",
        dictionaryApp = "Dictionary app",
        dictionary = "Dictionary",
        translate = "Translate",
        translateDescription = "Translate description",
        search = "Search",
        searchDescription = "Search description",
        selectApp = "Select app",
        none = "None",
        selected = "Selected"
    )
}
