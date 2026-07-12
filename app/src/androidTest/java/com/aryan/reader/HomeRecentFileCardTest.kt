package com.aryan.reader

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.data.RecentFileItem
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeRecentFileCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun recentFileCardShowsProgressAndUnavailableState() {
        val item = recentBook(
            bookId = "home_unavailable_epub",
            title = "Unavailable Field Guide",
            author = "Casey Example",
            progress = 42f,
            isAvailable = false
        )

        setRecentFileCard(item = item)

        composeTestRule.onNodeWithTag("HomeRecentFileCard_home_unavailable_epub").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unavailable Field Guide").assertIsDisplayed()
        composeTestRule.onNodeWithText("Casey Example").assertIsDisplayed()
        composeTestRule.onNodeWithText("42%").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription(text(R.string.not_available_locally))[0]
            .assertIsDisplayed()
    }

    @Test
    fun recentFileCardClickLongClickAndSelectedOverlayWork() {
        val item = recentBook(
            bookId = "home_selected_pdf",
            title = "Selected Position Notes",
            author = "Morgan Example",
            progress = 7f
        )
        var clicked = false
        var longClicked = false

        setRecentFileCard(
            item = item,
            isSelected = true,
            onClick = { clicked = true },
            onLongClick = { longClicked = true }
        )

        composeTestRule.onAllNodesWithContentDescription(text(R.string.content_desc_selected))[0]
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("HomeRecentFileCard_home_selected_pdf").performClick()
        composeTestRule.onNodeWithTag("HomeRecentFileCard_home_selected_pdf").performTouchInput {
            down(center)
            advanceEventTime(600)
            up()
        }

        assertThat(clicked).isTrue()
        assertThat(longClicked).isTrue()
    }

    @Test
    fun availableAndUnavailableCardsHaveTheSameHeight() {
        val available = recentBook("same_height_available", "Available", "A", 20f)
        val unavailable = recentBook("same_height_unavailable", "Unavailable", "B", 20f, isAvailable = false)

        composeTestRule.setContent {
            MaterialTheme {
                Row(modifier = androidx.compose.ui.Modifier.width(280.dp)) {
                    RecentFileCard(
                        item = available,
                        isSelected = false,
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                        onClick = {}, onLongClick = {}, isDownloading = false
                    )
                    RecentFileCard(
                        item = unavailable,
                        isSelected = false,
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                        onClick = {}, onLongClick = {}, isDownloading = false
                    )
                }
            }
        }

        val availableHeight = composeTestRule.onNodeWithTag("HomeRecentFileCard_same_height_available")
            .fetchSemanticsNode().boundsInRoot.height
        val unavailableHeight = composeTestRule.onNodeWithTag("HomeRecentFileCard_same_height_unavailable")
            .fetchSemanticsNode().boundsInRoot.height

        assertThat(availableHeight).isEqualTo(unavailableHeight)
    }

    private fun setRecentFileCard(
        item: RecentFileItem,
        isSelected: Boolean = false,
        isPinned: Boolean = false,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                RecentFileCard(
                    item = item,
                    isSelected = isSelected,
                    isPinned = isPinned,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    isDownloading = false,
                    usePdfFileNameAsDisplayName = false
                )
            }
        }
    }

    private fun recentBook(
        bookId: String,
        title: String,
        author: String,
        progress: Float,
        isAvailable: Boolean = true
    ): RecentFileItem {
        return RecentFileItem(
            bookId = bookId,
            uriString = "content://home-test/$bookId",
            type = FileType.EPUB,
            displayName = "$bookId.epub",
            timestamp = 1_000L,
            title = title,
            author = author,
            progressPercentage = progress,
            isRecent = true,
            isAvailable = isAvailable
        )
    }

    private fun text(resId: Int): String {
        return context.getString(resId)
    }
}
