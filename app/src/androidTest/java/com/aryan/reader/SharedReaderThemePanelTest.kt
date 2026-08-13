package com.aryan.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ui.SharedReaderThemePanel
import com.aryan.reader.shared.ui.SharedReaderThemePanelLabels
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedReaderThemePanelTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun presetSelectionUsesAndroidCallback() {
        var selected: String? = null
        composeTestRule.setContent {
            MaterialTheme {
                SharedReaderThemePanel(
                    isVisible = true,
                    currentThemeId = "system",
                    customThemes = emptyList(),
                    builtInThemes = listOf(
                        ReaderTheme("system", "System", Color.Unspecified, Color.Unspecified, false),
                        ReaderTheme("light", "Light", Color.White, Color.Black, false),
                    ),
                    globalTextureTransparency = 0f,
                    onGlobalTextureTransparencyChange = {},
                    onThemeSelected = { selected = it },
                    onCustomThemesUpdated = {},
                    onDismiss = {},
                    labels = labels(),
                    texturePreview = { _, _, _ -> Box {} },
                    builderContent = { _, _, _, _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithText("Light").performClick()
        composeTestRule.runOnIdle { assertThat(selected).isEqualTo("light") }
    }

    private fun labels() = SharedReaderThemePanelLabels(
        title="Reading themes", solidColors="Solid colors", textured="Textured",
        textureTransparency="Texture transparency", preserveImageColors="Preserve image colors",
        preserveImageColorsDescription="Keep image colors", presets="Presets", myThemes="My themes",
        newTheme="New", noCustomThemes="No custom themes", edit="Edit", delete="Delete", preview="Aa",
    )
}
