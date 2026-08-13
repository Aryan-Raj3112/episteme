package com.aryan.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ui.SharedReaderThemeBuilder
import com.aryan.reader.shared.ui.SharedReaderThemeBuilderLabels
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedReaderThemeBuilderTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun saveBuildsAndroidCompatibleCustomTheme() {
        var saved: ReaderTheme? = null
        composeTestRule.setContent {
            MaterialTheme {
                SharedReaderThemeBuilder(
                    initialTheme = null,
                    isTexturedMode = true,
                    globalTextureAlpha = 0.7f,
                    defaultTextureId = "texture",
                    labels = labels(),
                    newThemeId = { "1234" },
                    onSave = { saved = it },
                    onCancel = {},
                    texturePreview = { _, _, _ -> Box {} },
                    texturePickerContent = { _, _ -> },
                    colorPickerContent = { _, _, _, _, _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.runOnIdle {
            assertThat(saved?.id).isEqualTo("1234")
            assertThat(saved?.name).isEqualTo("Custom textured")
            assertThat(saved?.textureId).isEqualTo("texture")
            assertThat(saved?.isCustom).isTrue()
        }
    }

    private fun labels() = SharedReaderThemeBuilderLabels(
        customTexturedDefault="Custom textured", customSolidDefault="Custom solid",
        newTheme="New theme", editTheme="Edit theme", themeName="Theme name",
        previewQuote="Quote", previewAuthor="Author", lowContrastWarning="Low contrast",
        pageColor="Page color", textColor="Text color", cancel="Cancel", save="Save",
    )
}
