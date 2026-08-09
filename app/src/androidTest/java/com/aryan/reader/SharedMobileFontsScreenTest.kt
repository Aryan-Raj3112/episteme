package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.AppFontPreference
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.ui.SharedMobileFontsScreen
import com.aryan.reader.shared.ui.SharedMobileFontsStrings
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedMobileFontsScreenTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun selectAllThenDeleteEmitsAllFontIds() {
        var deletedIds = emptyList<String>()
        composeTestRule.setContent {
            MaterialTheme {
                SharedMobileFontsScreen(
                    fonts = listOf(font("regular", "Family-Regular.ttf"), font("bold", "Family-Bold.ttf")),
                    appFontPreference = AppFontPreference.System,
                    showGoogleFontsOption = true,
                    isLoading = false,
                    strings = strings(),
                    onBackClick = {},
                    onImportFonts = {},
                    onDeleteFonts = { deletedIds = it },
                    onAppFontPreferenceChange = {},
                    fontFamilyForPreview = { null },
                    googleFontsSheet = {},
                    platformBackHandler = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Select all").performClick()
        composeTestRule.onNodeWithContentDescription("Delete").performClick()
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.runOnIdle {
            assertThat(deletedIds).containsExactly("regular", "bold").inOrder()
        }
    }

    private fun font(id: String, fileName: String) = CustomFontItem(
        id = id,
        displayName = "Family",
        fileName = fileName,
        fileExtension = "ttf",
        path = "/not-loaded/$fileName",
        timestamp = 1L,
    )

    private fun strings() = SharedMobileFontsStrings(
        title = "Fonts",
        backDescription = "Back",
        selectAllDescription = "Select all",
        selectedCount = { "$it selected" },
        clearSelectionDescription = "Clear",
        deleteDescription = "Delete",
        googleFonts = "Google Fonts",
        importFont = "Import font",
        emptyTitle = "No fonts",
        emptyMessage = "Import fonts",
        selectFile = "Select file",
        browseGoogleFonts = "Browse Google Fonts",
        previewText = "Preview",
        previewError = "Preview unavailable",
        variableWeight = "Variable weight",
        fileCount = { "$it files" },
        deleteSingleTitle = "Delete font",
        deleteMultipleTitle = "Delete fonts",
        deleteSingleBody = { "Delete $it?" },
        deleteMultipleBody = { "Delete $it fonts?" },
        cancelAction = "Cancel",
    )
}
