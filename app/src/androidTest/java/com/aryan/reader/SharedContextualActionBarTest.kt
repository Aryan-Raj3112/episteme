package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedContextualActionBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun compactActionsPreserveAndroidOrderingAndCallbacks() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var addToShelfClicks = 0
        var clearClicks = 0

        composeTestRule.setContent {
            MaterialTheme {
                ContextualTopAppBar(
                    selectedItemCount = 1,
                    onNavIconClick = {},
                    onInfoClick = {},
                    onPinClick = {},
                    onSelectAllClick = {},
                    onDeleteClick = {},
                    onAddToShelfClick = { addToShelfClicks += 1 },
                    onClearSelectionClick = { clearClicks += 1 },
                    compactSelectionActions = true,
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.items_selected_count, 1)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.info)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.pin_unpin)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.select_all)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_delete)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.content_desc_more_options)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.desktop_add_to_shelf)).performClick()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.content_desc_more_options)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.action_clear)).performClick()

        composeTestRule.runOnIdle {
            assertThat(addToShelfClicks).isEqualTo(1)
            assertThat(clearClicks).isEqualTo(1)
        }
    }
}
