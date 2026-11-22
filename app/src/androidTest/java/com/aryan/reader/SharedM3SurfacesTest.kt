package com.aryan.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedEmptyState
import com.aryan.reader.shared.ui.SharedSectionHeader
import com.aryan.reader.shared.ui.SharedTonalIconBadge
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shared surfaces every non-reader screen now builds on.
 *
 * These exist because the same "nothing here" moment and the same section header had been written
 * five different ways across the library, settings and catalog screens; a screen could silently
 * reintroduce its own version without any test noticing.
 */
@RunWith(AndroidJUnit4::class)
class SharedM3SurfacesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyStateRendersItsTitleAndReportsBothActions() {
        var primary = false
        var secondary = false
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.size(400.dp)) {
                    SharedEmptyState(
                        icon = Icons.Default.Folder,
                        title = "No shelves yet",
                        body = "Shelves you create show up here.",
                        actionLabel = "Create shelf",
                        onAction = { primary = true },
                        secondaryActionLabel = "Import",
                        onSecondaryAction = { secondary = true },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("No shelves yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shelves you create show up here.").assertIsDisplayed()

        composeTestRule.onNodeWithText("Create shelf").performClick()
        composeTestRule.onNodeWithText("Import").performClick()

        composeTestRule.runOnIdle {
            assertThat(primary).isTrue()
            assertThat(secondary).isTrue()
        }
    }

    @Test
    fun emptyStateWithoutAnActionHasNoButtons() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedEmptyState(icon = Icons.Default.Folder, title = "No tags yet")
            }
        }

        composeTestRule.onNodeWithText("No tags yet").assertIsDisplayed()
        assertThat(composeTestRule.onAllNodesWithText("Import").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun emptyStateCarriesTheHostProvidedTestTag() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedEmptyState(
                    icon = Icons.Default.Folder,
                    title = "Folder is empty",
                    testTag = "UnifiedShelfEmpty",
                )
            }
        }

        composeTestRule.onNodeWithTag("UnifiedShelfEmpty").assertIsDisplayed()
    }

    @Test
    fun sectionHeaderKeepsItsTitleAndHostsTrailingActions() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                SharedSectionHeader(
                    title = "Your books",
                    titleTestTag = "SectionTitle",
                ) {
                    TextButton(onClick = { clicked = true }) { Text("Sort") }
                }
            }
        }

        composeTestRule.onNodeWithTag("SectionTitle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your books").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sort").performClick()

        composeTestRule.runOnIdle {
            assertThat(clicked).isTrue()
        }
    }

    @Test
    fun aTitledSectionHeaderFallsBackToAnEmptyTitleWithoutLosingItsActions() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedSectionHeader(title = null) {
                    TextButton(onClick = {}) { Text("Filter") }
                }
            }
        }

        composeTestRule.onNodeWithText("Filter").assertIsDisplayed()
    }

    @Test
    fun theIconBadgeIsDecorativeSoScreenReadersSkipIt() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedTonalIconBadge(icon = Icons.Default.Folder, modifier = Modifier.testTag("Badge"))
            }
        }

        // A badge repeats the row's title; giving it a description made TalkBack read every row
        // twice ("Folder, Folder").
        composeTestRule.onNodeWithTag("Badge").assertIsDisplayed()
        assertThat(composeTestRule.onAllNodesWithText("Folder").fetchSemanticsNodes()).isEmpty()
    }
}
