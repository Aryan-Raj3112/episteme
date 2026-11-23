package com.aryan.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedSelectionOverflowItems
import com.aryan.reader.shared.ui.SharedSelectionToolbar
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The unified library's selection UI: four direct actions and one overflow, with the file-only
 * actions appearing solely for a single selected book.
 *
 * The overflow is rendered on its own rather than through the toolbar's popup, because a popup in
 * an instrumentation test window is measured against that window's bounds instead of the device's,
 * so the menu collapses and its items never reach the semantics tree.
 */
@RunWith(AndroidJUnit4::class)
class SharedSelectionToolbarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun theDirectActionsAreOneTapAndAbsentOnesAreNotDrawn() {
        var pinClicks = 0
        var tagClicks = 0
        var shelfClicks = 0
        var deleteClicks = 0

        composeTestRule.setContent {
            MaterialTheme {
                SharedSelectionToolbar(
                    selectedCount = 2,
                    onSelectAll = {},
                    onPin = { pinClicks += 1 },
                    onAddToShelf = { shelfClicks += 1 },
                    onTag = { tagClicks += 1 },
                    onDelete = { deleteClicks += 1 },
                )
            }
        }

        composeTestRule.onNodeWithTag("SelectionToolbarPin").performClick()
        composeTestRule.onNodeWithTag("SelectionToolbarTag").performClick()
        composeTestRule.onNodeWithTag("SelectionToolbarShelf").performClick()
        composeTestRule.onNodeWithTag("SelectionToolbarDelete").performClick()

        assertThat(pinClicks).isEqualTo(1)
        assertThat(tagClicks).isEqualTo(1)
        assertThat(shelfClicks).isEqualTo(1)
        assertThat(deleteClicks).isEqualTo(1)
    }

    @Test
    fun aSelectionWithoutDeleteOffersNoDestructiveAction() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedSelectionToolbar(
                    selectedCount = 1,
                    onSelectAll = null,
                    onPin = {},
                    onDelete = null,
                )
            }
        }

        composeTestRule.onNodeWithTag("SelectionToolbarPin").assertIsDisplayed()
        composeTestRule.onNodeWithTag("SelectionToolbarDelete").assertDoesNotExist()
        composeTestRule.onNodeWithTag("SelectionToolbarTag").assertDoesNotExist()
    }

    @Test
    fun aSingleSelectionOffersTheFileActionsAndPickingOneClosesTheMenu() {
        var saveClicks = 0
        var dismissals = 0

        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    SharedSelectionOverflowItems(
                        selectedCount = 1,
                        onSelectAll = {},
                        onInfo = {},
                        onSave = { saveClicks += 1 },
                        onShare = {},
                        onExportAnnotations = {},
                        onClear = {},
                        onDismiss = { dismissals += 1 },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Book info").assertIsDisplayed()
        composeTestRule.onNodeWithText("Export annotations").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save copy to device").performClick()

        assertThat(saveClicks).isEqualTo(1)
        // The menu is told to close before the action runs, so the toolbar cannot be left open
        // over a screen the action navigated to.
        assertThat(dismissals).isEqualTo(1)
    }

    @Test
    fun aMultiSelectionDropsTheSingleBookActionsButKeepsSelectAllAndClear() {
        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    SharedSelectionOverflowItems(
                        selectedCount = 3,
                        onSelectAll = {},
                        onInfo = {},
                        onSave = {},
                        onShare = {},
                        onExportAnnotations = {},
                        onClear = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Select all").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear").assertIsDisplayed()
        composeTestRule.onNodeWithText("Book info").assertDoesNotExist()
        composeTestRule.onNodeWithText("Save copy to device").assertDoesNotExist()
        composeTestRule.onNodeWithText("Share original file").assertDoesNotExist()
        composeTestRule.onNodeWithText("Export annotations").assertDoesNotExist()
    }
}
