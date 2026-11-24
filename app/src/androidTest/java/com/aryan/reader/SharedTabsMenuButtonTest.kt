package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ReaderTabKind
import com.aryan.reader.shared.ui.SharedOpenTab
import com.aryan.reader.shared.ui.SharedTabsMenuButton
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The top-bar tab switcher that replaced Home's tab strip. It lists both readers in one
 * panel, so the library can show what is open without a row of chips.
 */
@RunWith(AndroidJUnit4::class)
class SharedTabsMenuButtonTest {
    @get:Rule val composeTestRule = createComposeRule()

    private val tabs = listOf(
        SharedOpenTab(
            bookId = "epub",
            title = "Dune",
            kind = ReaderTabKind.TEXT,
            isActive = true,
            chapterNumber = 5,
            progressPercent = 62,
        ),
        SharedOpenTab(
            bookId = "pdf",
            title = "Manual",
            kind = ReaderTabKind.FIXED_LAYOUT,
            pageNumber = 12,
        ),
    )

    @Test
    fun panelListsBothReadersAndReportsSelectionAndClose() {
        var selected: String? = null
        var closed: String? = null
        var closeAll = false
        composeTestRule.setContent {
            MaterialTheme {
                SharedTabsMenuButton(
                    tabs = tabs,
                    label = "Tabs",
                    openTabsDescription = "Open tabs",
                    closeTabDescription = "Close tab",
                    openCountLabel = "2 open",
                    epubLabel = "EPUB",
                    pdfLabel = "PDF",
                    closeAllLabel = "Close all tabs",
                    onCloseAllTabs = { closeAll = true },
                    onTabSelected = { selected = it.bookId },
                    onTabClosed = { closed = it.bookId },
                )
            }
        }

        composeTestRule.onNodeWithTag("HomeTabsPanel").assertDoesNotExist()
        composeTestRule.onNodeWithText("Tabs").assertIsDisplayed()
        // The trigger keeps just the label and the caret; the count lives in the panel header.
        composeTestRule.onNodeWithText("2").assertDoesNotExist()

        composeTestRule.onNodeWithTag("HomeTabsButton").performClick()

        composeTestRule.onNodeWithTag("HomeTabsPanel").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 open").assertIsDisplayed()
        composeTestRule.onNodeWithText("EPUB").assertIsDisplayed()
        composeTestRule.onNodeWithText("PDF").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chapter 5 · 62%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Page 12").assertIsDisplayed()

        composeTestRule.onNodeWithTag("HomeTab_pdf").performClick()
        composeTestRule.runOnIdle { assertThat(selected).isEqualTo("pdf") }
        composeTestRule.onNodeWithTag("HomeTabsPanel").assertDoesNotExist()

        composeTestRule.onNodeWithTag("HomeTabsButton").performClick()
        composeTestRule.onNodeWithTag("HomeTabClose_epub").performClick()
        composeTestRule.runOnIdle { assertThat(closed).isEqualTo("epub") }
        // Closing a row removes it without leaving the library.
        composeTestRule.onNodeWithTag("HomeTabsPanel").assertIsDisplayed()

        composeTestRule.onNodeWithTag("HomeTabsCloseAll").performClick()
        composeTestRule.runOnIdle { assertThat(closeAll).isTrue() }
        composeTestRule.onNodeWithTag("HomeTabsPanel").assertDoesNotExist()
    }

    @Test
    fun buttonIsHiddenWithoutTabs() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedTabsMenuButton(
                    tabs = emptyList(),
                    label = "Tabs",
                    openTabsDescription = "Open tabs",
                    closeTabDescription = "Close tab",
                    onTabSelected = {},
                    onTabClosed = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("HomeTabsButton").assertDoesNotExist()
    }
}
