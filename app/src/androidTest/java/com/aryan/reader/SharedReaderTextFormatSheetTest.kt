package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedReaderTextFormatSheet
import com.aryan.reader.shared.ui.SharedReaderTextFormatSheetLabels
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedReaderTextFormatSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun resetAndFontSelectionUseAndroidCallbacks() {
        var resetCount = 0
        var fontClickCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                SharedReaderTextFormatSheet(
                    isVisible = true,
                    isLocalMode = false,
                    onLocalModeToggle = {},
                    onReset = { resetCount++ },
                    onClose = {},
                    maxSheetHeight = 700.dp,
                    previewFontFamily = FontFamily.Default,
                    currentFontSize = 1f,
                    currentFontWeight = 0,
                    currentLetterSpacing = 0f,
                    currentLineHeight = 1f,
                    currentFontName = "Serif",
                    onFontOptionClick = { fontClickCount++ },
                    labels = labels(),
                    alignmentControl = { Text("Alignment control") },
                    typographyControls = { Text("Typography controls") },
                    layoutControls = { Text("Layout controls") }
                )
            }
        }

        composeTestRule.onNodeWithText("Reset").performClick()
        composeTestRule.onNodeWithContentDescription("Select font family").performClick()
        composeTestRule.runOnIdle {
            assertThat(resetCount).isEqualTo(1)
            assertThat(fontClickCount).isEqualTo(1)
        }
    }

    private fun labels() = SharedReaderTextFormatSheetLabels(
        local = "Local",
        global = "Global",
        localDescription = "Saved for this file",
        globalDescription = "Applies to all files",
        selectMode = "Select mode",
        reset = "Reset",
        close = "Close",
        fontAlignmentSection = "Font & alignment",
        typographySection = "Typography",
        layoutSpacingSection = "Layout & spacing",
        selectFontFamily = "Select font family",
        fontPreview = "Aa"
    )
}
