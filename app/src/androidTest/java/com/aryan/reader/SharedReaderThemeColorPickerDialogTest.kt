package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedReaderThemeColorPickerDialog
import com.aryan.reader.shared.ui.SharedReaderThemeColorPickerLabels
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedReaderThemeColorPickerDialogTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun emitsInitialColorAndSaveDismisses() {
        val initialColor = Color(0xFF2468AC)
        var liveColor: Color? = null
        var dismissed = false

        composeTestRule.setContent {
            MaterialTheme {
                SharedReaderThemeColorPickerDialog(
                    initialColor = initialColor,
                    title = "Page color",
                    backgroundColor = Color.White,
                    textColor = Color.Black,
                    editingBackground = true,
                    maxDialogHeight = 600.dp,
                    labels = labels(),
                    onDismiss = { dismissed = true },
                    onColorChanged = { liveColor = it },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertThat(liveColor).isNotNull()
            val emittedColor = checkNotNull(liveColor)
            assertThat(emittedColor.red).isWithin(0.001f).of(initialColor.red)
            assertThat(emittedColor.green).isWithin(0.001f).of(initialColor.green)
            assertThat(emittedColor.blue).isWithin(0.001f).of(initialColor.blue)
        }

        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.runOnIdle { assertThat(dismissed).isTrue() }
    }

    private fun labels() = SharedReaderThemeColorPickerLabels(
        livePreview = "Live preview",
        previewText = "Preview text",
        hex = "Hex",
        red = "R",
        green = "G",
        blue = "B",
        save = "Save",
    )
}
