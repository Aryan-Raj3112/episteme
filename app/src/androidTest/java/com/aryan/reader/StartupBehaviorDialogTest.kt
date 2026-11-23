package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.MobileStartupBehavior
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBehaviorDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bothChoicesAreOfferedWithExactlyOneSelected() {
        composeTestRule.setContent {
            MaterialTheme {
                StartupBehaviorDialog(
                    currentBehavior = MobileStartupBehavior.OPEN_LAST_READING,
                    onDismiss = {},
                    onSelect = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Reopen the last book").assertExists()
        composeTestRule.onNodeWithText("Start at your library").assertExists()
        composeTestRule.onAllNodes(isSelectable()).assertCountEquals(2)
        composeTestRule.onNodeWithText("Reopen the last book").assertIsSelected()
    }

    @Test
    fun switchingTheChoiceMovesTheSelectedRadio() {
        composeTestRule.setContent {
            MaterialTheme {
                StartupBehaviorDialog(
                    currentBehavior = MobileStartupBehavior.HOME,
                    onDismiss = {},
                    onSelect = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Start at your library").assertIsSelected()
    }

    @Test
    fun pickingTheLibraryReportsThatChoice() {
        var selected: MobileStartupBehavior? = null

        composeTestRule.setContent {
            MaterialTheme {
                StartupBehaviorDialog(
                    currentBehavior = MobileStartupBehavior.OPEN_LAST_READING,
                    onDismiss = {},
                    onSelect = { selected = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Start at your library").performClick()

        composeTestRule.runOnIdle {
            assertThat(selected).isEqualTo(MobileStartupBehavior.HOME)
        }
    }
}
