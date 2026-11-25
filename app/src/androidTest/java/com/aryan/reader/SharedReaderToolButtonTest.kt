package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedReaderToolButton
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reader bar's tool button contract, shared by the EPUB and PDF bars on both hosts: one tap
 * target that announces its label, a busy state that replaces the glyph instead of disabling the
 * tool silently, and no callback while it is disabled.
 */
@RunWith(AndroidJUnit4::class)
class SharedReaderToolButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun theButtonAnnouncesItsLabelAndRunsOnTap() {
        var clicks = 0

        composeTestRule.setContent {
            MaterialTheme {
                SharedReaderToolButton(label = "Theme", onClick = { clicks += 1 }) {
                    androidx.compose.material3.Text("glyph")
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Theme").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Theme").performClick()

        composeTestRule.runOnIdle { assertThat(clicks).isEqualTo(1) }
    }

    @Test
    fun aDisabledToolDoesNotFire() {
        var clicks = 0

        composeTestRule.setContent {
            MaterialTheme {
                SharedReaderToolButton(label = "Search", onClick = { clicks += 1 }, enabled = false) {
                    androidx.compose.material3.Text("glyph")
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Search").performClick()

        composeTestRule.runOnIdle { assertThat(clicks).isEqualTo(0) }
    }

    @Test
    fun aBusyToolShowsProgressInsteadOfTheGlyph() {
        // A tool that is working must not look merely disabled: the spinner replaces the glyph.
        composeTestRule.setContent {
            MaterialTheme {
                SharedReaderToolButton(label = "Highlighting", onClick = {}, loading = true) {
                    androidx.compose.material3.Text("glyph")
                }
            }
        }

        composeTestRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("glyph").assertDoesNotExist()
    }
}
