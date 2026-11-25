package com.aryan.reader

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
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
import com.aryan.reader.shared.ui.SharedAndroidHomeRecentCard
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

        // Two weight(1f) cards split the row's width, which can differ by a single pixel when the total
        // width is odd; the aspect-ratio cover then differs by ~1px. The point of the test is that
        // the unavailable overlay does not change the card's height, not that it is pixel-identical.
        assertThat(availableHeight).isWithin(1f).of(unavailableHeight)
    }

    /**
     * Selection used to paint a 20% primary wash over the cover and drop a 48dp check on top of it,
     * which hid the artwork of the very book you had just picked. The card now moves its own
     * container and grows a corner check instead, so the cover pixels must be untouched.
     *
     * Both cards are rendered in one tree because a `ComposeTestRule` only accepts one `setContent`.
     */
    @Test
    fun selectingACardLeavesTheArtworkUntouched() {
        val coverColor = Color(0xFF3F51B5)
        val plain = recentBook("card_plain", "Plain", "A", 30f)
        val selected = recentBook("card_selected", "Chosen", "A", 30f)

        composeTestRule.setContent {
            MaterialTheme {
                Row(modifier = androidx.compose.ui.Modifier.width(420.dp)) {
                    SharedCardForTest(plain, isSelected = false, coverColor = coverColor, modifier = androidx.compose.ui.Modifier.weight(1f))
                    SharedCardForTest(selected, isSelected = true, coverColor = coverColor, modifier = androidx.compose.ui.Modifier.weight(1f))
                }
            }
        }

        val plainCover = coverCenterPixel("HomeRecentFileCardCover_card_plain")
        val selectedCover = coverCenterPixel("HomeRecentFileCardCover_card_selected")

        assertThat(selectedCover.red).isWithin(0.02f).of(plainCover.red)
        assertThat(selectedCover.green).isWithin(0.02f).of(plainCover.green)
        assertThat(selectedCover.blue).isWithin(0.02f).of(plainCover.blue)
    }

    private fun coverCenterPixel(tag: String): Color {
        val pixels = composeTestRule
            .onNodeWithTag(tag, useUnmergedTree = true)
            .captureToImage()
            .toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
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

    @Composable
    private fun SharedCardForTest(
        item: RecentFileItem,
        isSelected: Boolean,
        coverColor: Color,
        modifier: Modifier = Modifier,
    ) {
        SharedAndroidHomeRecentCard(
            bookId = item.bookId,
            title = item.cardTitle(),
            author = item.cardAuthor(),
            progressPercent = item.progressPercentValue(),
            isAvailable = item.isAvailable,
            isDownloading = false,
            isSelected = isSelected,
            hasCustomCover = false,
            showStatusBadges = false,
            unavailableDescription = text(R.string.not_available_locally),
            selectedDescription = text(R.string.content_desc_selected),
            onClick = {},
            onLongClick = {},
            cover = { coverModifier ->
                Box(
                    coverModifier
                        .testTag("HomeRecentFileCardCover_${item.bookId}")
                        .background(coverColor)
                )
            },
            statusBadges = {},
            fileTypeBadge = { },
            modifier = modifier,
        )
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
