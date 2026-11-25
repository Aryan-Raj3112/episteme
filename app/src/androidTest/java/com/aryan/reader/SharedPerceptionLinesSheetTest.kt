package com.aryan.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.reader.DefaultPerceptionLinesInsetFraction
import com.aryan.reader.shared.reader.DefaultPerceptionLinesOpacity
import com.aryan.reader.shared.reader.DefaultPerceptionLinesWidthDp
import com.aryan.reader.shared.reader.ReaderPerceptionLinesSettings
import com.aryan.reader.shared.ui.SharedPerceptionLinesAxTags
import com.aryan.reader.shared.ui.SharedPerceptionLinesOverlay
import com.aryan.reader.shared.ui.SharedPerceptionLinesSheet
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPerceptionLinesSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun switchTogglesTheRailsAndKeepsTheOtherValues() {
        var settings by mutableStateOf(
            ReaderPerceptionLinesSettings(enabled = false, insetFraction = 0.2f, lineWidthDp = 4f)
        )
        composeTestRule.setContent {
            MaterialTheme {
                SharedPerceptionLinesSheet(
                    settings = settings,
                    onSettingsChange = { settings = it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(SharedPerceptionLinesAxTags.SWITCH).performClick()
        composeTestRule.waitForIdle()

        assertThat(settings.enabled).isTrue()
        // Toggling the rails must not reset the customisation around them.
        assertThat(settings.insetFraction).isWithin(0.001f).of(0.2f)
        assertThat(settings.lineWidthDp).isWithin(0.001f).of(4f)
    }

    @Test
    fun sheetShowsTheCustomisationRowsItAdvertises() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedPerceptionLinesSheet(
                    settings = ReaderPerceptionLinesSettings(),
                    onSettingsChange = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Show lines").assertExists()
        composeTestRule.onNodeWithText("Preview").assertExists()
        composeTestRule.onNodeWithTag(SharedPerceptionLinesAxTags.PREVIEW).assertExists()
        composeTestRule.onNodeWithText("Color").assertExists()
        composeTestRule.onNodeWithText("Custom color").assertExists()
        composeTestRule.onNodeWithText("Opacity").assertExists()
        composeTestRule.onNodeWithText("Line width").assertExists()
        composeTestRule.onNodeWithText("Line position").assertExists()
        composeTestRule.onNodeWithTag(SharedPerceptionLinesAxTags.OPACITY_SLIDER).assertExists()
        composeTestRule.onNodeWithTag(SharedPerceptionLinesAxTags.WIDTH_SLIDER).assertExists()
        composeTestRule.onNodeWithTag(SharedPerceptionLinesAxTags.POSITION_SLIDER).assertExists()
    }

    @Test
    fun previewSurvivesEveryCustomisationIncludingTheSwitch() {
        var settings by mutableStateOf(ReaderPerceptionLinesSettings())
        composeTestRule.setContent {
            MaterialTheme {
                SharedPerceptionLinesSheet(
                    settings = settings,
                    onSettingsChange = { settings = it },
                    onDismiss = {},
                )
            }
        }

        // The preview answers "what would the lines look like", so it keeps rendering the rails
        // while the switch is off and after it is turned on.
        composeTestRule.onNodeWithTag(SharedPerceptionLinesAxTags.PREVIEW).assertExists()

        composeTestRule.onNodeWithTag(SharedPerceptionLinesAxTags.SWITCH).performClick()
        composeTestRule.waitForIdle()
        assertThat(settings.enabled).isTrue()
        composeTestRule.onNodeWithTag(SharedPerceptionLinesAxTags.PREVIEW).assertExists()

        composeTestRule.runOnUiThread {
            // Drive the same reducer the sliders and the reconciliation pass use, so the assertion
            // covers the state the preview recomposes from.
            settings = settings.copy(opacity = 0.25f, lineWidthDp = 6f, insetFraction = 0.3f).sanitized()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(SharedPerceptionLinesAxTags.PREVIEW).assertExists()
        composeTestRule.onNodeWithText("25%").assertExists()
        composeTestRule.onNodeWithText("6 dp").assertExists()
        composeTestRule.onNodeWithText("30%").assertExists()
    }

    @Test
    fun resetButtonRestoresTheSlidersAndKeepsColourAndSwitch() {
        var settings by mutableStateOf(
            ReaderPerceptionLinesSettings(
                enabled = true,
                colorArgb = 0xFF1976D2L,
                opacity = 0.25f,
                lineWidthDp = 6f,
                insetFraction = 0.3f,
            )
        )
        composeTestRule.setContent {
            MaterialTheme {
                SharedPerceptionLinesSheet(
                    settings = settings,
                    onSettingsChange = { settings = it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(SharedPerceptionLinesAxTags.RESET).performClick()
        composeTestRule.waitForIdle()

        assertThat(settings.opacity).isWithin(0.001f).of(DefaultPerceptionLinesOpacity)
        assertThat(settings.lineWidthDp).isWithin(0.001f).of(DefaultPerceptionLinesWidthDp)
        assertThat(settings.insetFraction).isWithin(0.001f).of(DefaultPerceptionLinesInsetFraction)
        assertThat(settings.colorArgb).isEqualTo(0xFF1976D2L)
        assertThat(settings.enabled).isTrue()
        // Back at the defaults, so the button has nothing left to do.
        composeTestRule.onNodeWithTag(SharedPerceptionLinesAxTags.RESET).assertIsNotEnabled()
    }

    @Test
    fun resetButtonIsDisabledOnADefaultSheet() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedPerceptionLinesSheet(
                    settings = ReaderPerceptionLinesSettings(enabled = true),
                    onSettingsChange = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(SharedPerceptionLinesAxTags.RESET).assertIsNotEnabled()
    }

    @Test
    fun overlayOnlyExistsWhileTheRailsAreEnabled() {
        var settings by mutableStateOf(ReaderPerceptionLinesSettings(enabled = true))
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.size(320.dp)) {
                    SharedPerceptionLinesOverlay(settings = settings)
                }
            }
        }

        composeTestRule.onNodeWithTag(SharedPerceptionLinesAxTags.OVERLAY).assertExists()

        settings = settings.copy(enabled = false)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(SharedPerceptionLinesAxTags.OVERLAY).assertDoesNotExist()
    }
}
