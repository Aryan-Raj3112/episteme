package com.aryan.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.aryan.reader.shared.ui.SharedReaderTab
import com.aryan.reader.shared.ui.SharedReaderTabStrip
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * One strip serves both readers and both platforms, so its contract - which tab is active, what a
 * tap reports, and that closing stays independent of selection - is worth covering once here.
 */
class SharedReaderTabStripTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tabs = listOf(
        SharedReaderTab(bookId = "one", title = "One"),
        SharedReaderTab(bookId = "two", title = "Two"),
    )

    @Test
    fun activeTabExposesSelectionAndTapReportsTheChosenTab() {
        var selected: String? = null
        var closed: String? = null
        var newTabClicks = 0

        composeRule.setContent {
            SharedReaderTabStrip(
                tabs = tabs,
                activeBookId = "two",
                onSelect = { selected = it },
                onClose = { closed = it },
                onNewTab = { newTabClicks += 1 },
            )
        }

        composeRule.onNodeWithTag("ReaderTab_two").assertIsSelected()
        composeRule.onNodeWithTag("ReaderTab_one").assertIsNotSelected()

        composeRule.onNodeWithTag("ReaderTab_one").performClick()
        assertEquals("one", selected)

        composeRule.onNodeWithTag("ReaderTabClose_one").performClick()
        assertEquals("one", closed)

        composeRule.onNodeWithTag("ReaderTabNew").performClick()
        assertEquals(1, newTabClicks)
    }

    @Test
    fun everyOpenTabIsRendered() {
        composeRule.setContent {
            Column {
                SharedReaderTabStrip(
                    tabs = tabs,
                    activeBookId = "one",
                    onSelect = {},
                    onClose = {},
                    onNewTab = {},
                )
            }
        }

        composeRule.onNodeWithTag("ReaderTab_one").assertIsDisplayed()
        composeRule.onNodeWithTag("ReaderTab_two").assertIsDisplayed()
    }
}
