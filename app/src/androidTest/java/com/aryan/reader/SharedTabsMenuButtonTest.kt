package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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
    fun eachGroupShowsThreeRowsAndScrollsTheRest() {
        val manyTextTabs = (1..5).map { index ->
            SharedOpenTab(bookId = "epub$index", title = "Book $index", kind = ReaderTabKind.TEXT)
        }
        composeTestRule.setContent {
            MaterialTheme {
                SharedTabsMenuButton(
                    tabs = manyTextTabs,
                    label = "Tabs",
                    openTabsDescription = "Open tabs",
                    closeTabDescription = "Close tab",
                    openCountLabel = "5 open",
                    onTabSelected = {},
                    onTabClosed = {},
                    onTabOrderCommitted = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithTag("HomeTabsButton").performClick()
        composeTestRule.onNodeWithText("5 open").assertIsDisplayed()
        composeTestRule.onNodeWithTag("HomeTab_epub3").assertIsDisplayed()
        // The group holds three rows; the rest live beyond its window instead of growing it.
        composeTestRule.onNodeWithTag("HomeTab_epub5").assertDoesNotExist()
    }

    @Test
    fun draggingARowReportsTheFinishedOrderOnceOnDrop() {
        // More rows than the window, so the group could scroll while a row is lifted.
        val textTabs = (1..5).map { index ->
            SharedOpenTab(bookId = "epub$index", title = "Book $index", kind = ReaderTabKind.TEXT)
        }
        val commits = mutableListOf<List<String>>()
        val tabsState = mutableStateOf(textTabs)
        composeTestRule.setContent {
            MaterialTheme {
                SharedTabsMenuButton(
                    tabs = tabsState.value,
                    label = "Tabs",
                    openTabsDescription = "Open tabs",
                    closeTabDescription = "Close tab",
                    openCountLabel = "5 open",
                    onTabSelected = {},
                    onTabClosed = {},
                    onTabOrderCommitted = { _, ordered ->
                        commits += ordered
                        tabsState.value = ordered.map { id -> textTabs.first { it.bookId == id } }
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag("HomeTabsButton").performClick()
        // The row merges its children, so the handle is only addressable in the unmerged tree.
        val handle = composeTestRule.onNodeWithTag("HomeTabDrag_epub1", useUnmergedTree = true)
        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, 20.dp.toPx()))
            moveBy(Offset(0f, 40.dp.toPx()))
        }
        // The row is lifted, not scrolled: its neighbours must stay where they were under the finger.
        composeTestRule.onNodeWithTag("HomeTab_epub1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("HomeTab_epub3").assertIsDisplayed()
        // The panel owns the order while the finger is down and reports it once, on drop.
        assertThat(commits).isEmpty()
        handle.performTouchInput { up() }

        composeTestRule.runOnIdle {
            assertThat(commits).containsExactly(listOf("epub2", "epub1", "epub3", "epub4", "epub5"))
        }
    }

    @Test
    fun pushingTheDragPastTheWindowEdgeScrollsTheGroup() {
        // The window follows the push instead of running on by itself, so the row stays in view
        // and the tab it passes is reordered on the way.
        val textTabs = (1..5).map { index ->
            SharedOpenTab(bookId = "epub$index", title = "Book $index", kind = ReaderTabKind.TEXT)
        }
        val commits = mutableListOf<List<String>>()
        composeTestRule.setContent {
            MaterialTheme {
                SharedTabsMenuButton(
                    tabs = textTabs,
                    label = "Tabs",
                    openTabsDescription = "Open tabs",
                    closeTabDescription = "Close tab",
                    openCountLabel = "5 open",
                    onTabSelected = {},
                    onTabClosed = {},
                    onTabOrderCommitted = { _, ordered -> commits += ordered },
                )
            }
        }

        composeTestRule.onNodeWithTag("HomeTabsButton").performClick()
        composeTestRule.onNodeWithTag("HomeTab_epub5").assertDoesNotExist()
        val handle = composeTestRule.onNodeWithTag("HomeTabDrag_epub1", useUnmergedTree = true)
        handle.performTouchInput { down(center) }
        // Push the row to the bottom of the window and keep pushing: each push scrolls the group by
        // the overflow it leaves, which is what brings the rows below it into reach.
        repeat(6) { handle.performTouchInput { moveBy(Offset(0f, 60.dp.toPx())) } }
        // The last rows are in reach only because the window scrolled along with the push.
        composeTestRule.onNodeWithTag("HomeTab_epub5").assertIsDisplayed()
        // The lifted row is still one of the group's rows, pushed along with the finger.
        composeTestRule.onNodeWithTag("HomeTab_epub1").assertExists()
        handle.performTouchInput { up() }

        composeTestRule.runOnIdle {
            // Scrolling moved the tab past the rows it revealed instead of leaving it at the top.
            assertThat(commits.single().last()).isEqualTo("epub1")
        }
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
