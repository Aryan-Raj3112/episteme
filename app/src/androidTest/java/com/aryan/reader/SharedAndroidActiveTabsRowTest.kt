package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedAndroidActiveTabsRow
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The active-tabs strip used to stack a large header plus a 48dp close button above the
 * chips. It is now a single compact row, and a lone open tab is hidden because the
 * Continue Reading card already surfaces it.
 */
@RunWith(AndroidJUnit4::class)
class SharedAndroidActiveTabsRowTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun compactRowShowsTabsAndReportsActions() {
        var opened: String? = null
        var closed: String? = null
        var closeAll = false
        composeTestRule.setContent {
            MaterialTheme {
                SharedAndroidActiveTabsRow(
                    openTabs = listOf("Alpha", "Beta"),
                    tabsEnabled = true,
                    closeAllTabsDescription = "Close all tabs",
                    closeTabDescription = "Close tab",
                    itemKey = { it },
                    itemTitle = { it },
                    onItemClick = { opened = it },
                    onCloseTab = { closed = it },
                    onCloseAllTabs = { closeAll = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeTestRule.onNodeWithText("Beta").assertIsDisplayed()
        val rowHeight = composeTestRule.onNodeWithTag("ActiveTabsRow").getUnclippedBoundsInRoot().height
        assertThat(rowHeight.value).isAtMost(48f)

        // Tap the label side of the chip, not its center: the per-tab close target sits
        // at the trailing edge and must not swallow a tap meant to open the tab.
        composeTestRule.onNodeWithTag("LibraryTab_Beta").performTouchInput {
            click(Offset(width * 0.15f, centerY))
        }
        composeTestRule.runOnIdle { assertThat(opened).isEqualTo("Beta") }

        composeTestRule.onNodeWithTag("LibraryTabClose_Beta").performClick()
        composeTestRule.runOnIdle { assertThat(closed).isEqualTo("Beta") }

        composeTestRule.onNodeWithTag("CloseAllTabs").performClick()
        composeTestRule.runOnIdle { assertThat(closeAll).isTrue() }
    }

    @Test
    fun singleOpenTabHidesTheStrip() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedAndroidActiveTabsRow(
                    openTabs = listOf("Alpha"),
                    tabsEnabled = true,
                    closeAllTabsDescription = "Close all tabs",
                    closeTabDescription = "Close tab",
                    itemKey = { it },
                    itemTitle = { it },
                    onItemClick = {},
                    onCloseTab = {},
                    onCloseAllTabs = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("ActiveTabsRow").assertDoesNotExist()
    }

    @Test
    fun disabledTabsHideTheStrip() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedAndroidActiveTabsRow(
                    openTabs = listOf("Alpha", "Beta"),
                    tabsEnabled = false,
                    closeAllTabsDescription = "Close all tabs",
                    closeTabDescription = "Close tab",
                    itemKey = { it },
                    itemTitle = { it },
                    onItemClick = {},
                    onCloseTab = {},
                    onCloseAllTabs = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("ActiveTabsRow").assertDoesNotExist()
    }
}
