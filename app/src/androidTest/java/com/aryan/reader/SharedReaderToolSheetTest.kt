package com.aryan.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedReaderOverflowMenu
import com.aryan.reader.shared.ui.SharedReaderOverflowMenuState
import com.aryan.reader.shared.ui.SharedReaderToolSheetRow
import com.aryan.reader.shared.ui.SharedReaderToolSheetRowItem
import com.aryan.reader.shared.ui.SharedReaderToolSheetSectionHeader
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reader's tool menu grew a row language of its own when it moved from a dropdown to a sheet.
 *
 * These lock down the parts a reader depends on: the row is one full-width tap target (not just the
 * text), the trailing control is a real switch, a selected row says so both visually and to a
 * screen reader, and an item row still reports its icon and runs its action - the last one matters
 * because every PDF menu row was mechanically migrated to this composable.
 */
@RunWith(AndroidJUnit4::class)
class SharedReaderToolSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rowShowsTitleSupportingTextAndRunsItsAction() {
        var clicks = 0
        composeTestRule.setContent {
            MaterialTheme {
                SharedReaderToolSheetRow(
                    title = "Perception Lines",
                    supporting = "Guide rails that rest the eye",
                    icon = Icons.Default.Tune,
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("row"),
                )
            }
        }

        composeTestRule.onNodeWithText("Perception Lines").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guide rails that rest the eye").assertIsDisplayed()
        composeTestRule.onNodeWithTag("row").performClick()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun rowWithoutSupportingTextRendersOneLine() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedReaderToolSheetRow(title = "Theme", onClick = {})
            }
        }

        composeTestRule.onNodeWithText("Theme").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guide rails that rest the eye").assertDoesNotExist()
    }

    @Test
    fun trailingSwitchIsASwitchNotADecoration() {
        val checkedState = androidx.compose.runtime.mutableStateOf(false)
        composeTestRule.setContent {
            MaterialTheme {
                SharedReaderToolSheetRow(
                    title = "Realistic Page Turns",
                    onClick = { checkedState.value = !checkedState.value },
                    trailing = {
                        Switch(
                            checked = checkedState.value,
                            onCheckedChange = { checkedState.value = it },
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Realistic Page Turns").performClick()
        assertThat(checkedState.value).isTrue()
    }

    @Test
    fun selectedRowAnnouncesItself() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedReaderToolSheetRow(
                    title = "Reading Themes",
                    onClick = {},
                    selected = true,
                    selectedContentDescription = "Selected",
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Selected").assertIsDisplayed()
    }

    @Test
    fun itemRowShowsItsIconAndRunsTheAction() {
        var clicks = 0
        composeTestRule.setContent {
            MaterialTheme {
                SharedReaderToolSheetRowItem(
                    text = { Text("Find in book") },
                    onClick = { clicks++ },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, contentDescription = null) },
                )
            }
        }

        composeTestRule.onNodeWithText("Find in book").assertIsDisplayed()
        composeTestRule.onNodeWithText("Find in book").performClick()
        assertThat(clicks).isEqualTo(1)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun overflowMenuOpensAsASheetWithItsRowsAndHeader() {
        val state = SharedReaderOverflowMenuState()
        state.open()
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.size(400.dp)) {
                    SharedReaderOverflowMenu(state = state, title = "More options") {
                        SharedReaderToolSheetSectionHeader("Reading")
                        SharedReaderToolSheetRow(title = "Table of Contents", onClick = {})
                        SharedReaderToolSheetRow(title = "Search", onClick = {})
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("More options").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reading").assertIsDisplayed()
        composeTestRule.onNodeWithText("Table of Contents").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search").assertIsDisplayed()
        assertThat(state.menuExpanded.value).isTrue()
    }
}
