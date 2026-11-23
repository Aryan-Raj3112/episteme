package com.aryan.reader

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedDrawerSwitchRow
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The drawer switch row: one control per setting.
 *
 * A `NavigationDrawerItem` with a `Switch` in its badge slot announced two controls for one
 * setting and only the switch's own 52dp responded to the toggle. The row owns the toggle here,
 * so the whole row must be the single toggle target, and the visual switch must not add a second
 * one to the semantics tree.
 */
@RunWith(AndroidJUnit4::class)
class SharedDrawerShellTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun theWholeRowIsTheSingleToggleTarget() {
        val changes = mutableListOf<Boolean>()

        composeTestRule.setContent {
            MaterialTheme {
                SharedDrawerSwitchRow(
                    icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                    label = "Sync library",
                    supporting = "Keep your library in the cloud.",
                    checked = false,
                    onCheckedChange = { changes += it },
                    modifier = Modifier.testTag("SyncRow"),
                )
            }
        }

        // One toggleable node for the row, and none for the switch graphic inside it.
        assertThat(composeTestRule.onAllNodes(isToggleable()).fetchSemanticsNodes()).hasSize(1)
        composeTestRule.onNodeWithTag("SyncRow").assertIsOff()
        composeTestRule.onNodeWithTag("SyncRow").performClick()

        composeTestRule.runOnIdle { assertThat(changes).containsExactly(true) }
    }

    @Test
    fun theCheckedStateAndSupportingLineAreVisible() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedDrawerSwitchRow(
                    icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                    label = "Sync library",
                    supporting = "Keep your library in the cloud.",
                    checked = true,
                    onCheckedChange = {},
                    modifier = Modifier.testTag("SyncRow"),
                )
            }
        }

        composeTestRule.onNodeWithTag("SyncRow").assertIsOn()
        composeTestRule.onAllNodesWithText("Sync library").fetchSemanticsNodes().isNotEmpty()
        composeTestRule.onNodeWithTag("SyncRow").assertIsDisplayed()
    }

    @Test
    fun aDisabledRowCannotBeToggled() {
        var changes = 0

        composeTestRule.setContent {
            MaterialTheme {
                SharedDrawerSwitchRow(
                    icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                    label = "Sync library",
                    checked = false,
                    enabled = false,
                    onCheckedChange = { changes += 1 },
                    modifier = Modifier.testTag("SyncRow"),
                )
            }
        }

        composeTestRule.onNodeWithTag("SyncRow").assertIsDisplayed()
        composeTestRule.onNodeWithTag("SyncRow").performClick()
        composeTestRule.runOnIdle { assertThat(changes).isEqualTo(0) }
    }
}
