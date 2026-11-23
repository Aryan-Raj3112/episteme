package com.aryan.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedDrawerTabRow
import com.aryan.reader.shared.ui.SharedDrawerTabsHeader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The drawer tab row is the only way to reach a tab whose strip is hidden, and it is shared by both
 * readers, so its two independent hit targets (select and close) are asserted once here.
 */
@RunWith(AndroidJUnit4::class)
class SharedDrawerTabRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedRowReportsSelectedAndUnselectedDoesNot() {
        composeRule.setContent {
            androidx.compose.foundation.layout.Column {
                SharedDrawerTabRow(
                    title = "Sonnets",
                    isSelected = true,
                    onSelect = {},
                    onClose = {},
                    closeContentDescription = "Close tab",
                    testTag = "Tab_selected",
                )
                SharedDrawerTabRow(
                    title = "Barlasch of the Guard",
                    supportingText = "Henry Seton Merriman",
                    isSelected = false,
                    onSelect = {},
                    onClose = {},
                    closeContentDescription = "Close tab",
                    testTag = "Tab_unselected",
                )
            }
        }

        composeRule.onNodeWithTag("Tab_selected").assertIsSelected()
        composeRule.onNodeWithTag("Tab_unselected").assertIsNotSelected()
        composeRule.onNodeWithText("Henry Seton Merriman").assertIsDisplayed()
    }

    @Test
    fun tappingTheRowSelectsInsteadOfClosing() {
        var selected: String? = null
        var closed: String? = null
        composeRule.setContent {
            SharedDrawerTabRow(
                title = "Sonnets",
                isSelected = false,
                onSelect = { selected = "Sonnets" },
                onClose = { closed = "Sonnets" },
                closeContentDescription = "Close tab",
                testTag = "Tab_row",
                closeTestTag = "Tab_row_close",
            )
        }

        composeRule.onNodeWithTag("Tab_row").performClick()

        assertEquals("Sonnets", selected)
        assertEquals(null, closed)
    }

    @Test
    fun headerShowsTheOpenTabCountAndStartsANewTab() {
        var newTabRequested = false
        composeRule.setContent {
            SharedDrawerTabsHeader(
                title = "Active Tabs",
                openTabCount = 3,
                newTabContentDescription = "New tab",
                onNewTab = { newTabRequested = true },
            )
        }

        composeRule.onNodeWithText("Active Tabs").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("New tab").performClick()

        assertEquals(true, newTabRequested)
    }

    @Test
    fun closeButtonClosesTheTabWithoutSelectingIt() {
        var selected: String? = null
        var closed: String? = null
        composeRule.setContent {
            SharedDrawerTabRow(
                title = "Sonnets",
                isSelected = false,
                onSelect = { selected = "Sonnets" },
                onClose = { closed = "Sonnets" },
                closeContentDescription = "Close tab",
                testTag = "Tab_row",
                closeTestTag = "Tab_row_close",
            )
        }

        composeRule.onNodeWithTag("Tab_row_close").performClick()

        assertEquals("Sonnets", closed)
        assertEquals(null, selected)
    }
}
