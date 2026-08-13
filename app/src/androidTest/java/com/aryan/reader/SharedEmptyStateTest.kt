package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedEmptyStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun androidWrapperPreservesContentAndActions() {
        var primaryClicks = 0
        var secondaryClicks = 0

        composeTestRule.setContent {
            MaterialTheme {
                EmptyState(
                    title = "Your library is empty",
                    message = "Select a file to start reading.",
                    primaryButtonText = "Select file",
                    onSelectFileClick = { primaryClicks += 1 },
                    secondaryButtonText = "Set up folder sync",
                    onSecondaryClick = { secondaryClicks += 1 },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("No files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your library is empty").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select a file to start reading.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select file").performClick()
        composeTestRule.onNodeWithText("Set up folder sync").performClick()

        composeTestRule.runOnIdle {
            assertThat(primaryClicks).isEqualTo(1)
            assertThat(secondaryClicks).isEqualTo(1)
        }
    }

    @Test
    fun androidBannerWrapperPreservesVisibilityAndText() {
        var banner = androidx.compose.runtime.mutableStateOf<BannerMessage?>(
            BannerMessage(message = "Folder sync complete"),
        )

        composeTestRule.setContent {
            MaterialTheme {
                CustomTopBanner(bannerMessage = banner.value)
            }
        }

        composeTestRule.onNodeWithText("Folder sync complete").assertIsDisplayed()

        composeTestRule.runOnIdle { banner.value = null }
        composeTestRule.onAllNodesWithText("Folder sync complete").assertCountEquals(0)
    }
}
