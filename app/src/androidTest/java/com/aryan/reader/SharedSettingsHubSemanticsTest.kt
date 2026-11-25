package com.aryan.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.SharedSettingsDestination
import com.aryan.reader.shared.SharedSettingsHubInput
import com.aryan.reader.shared.SharedSettingsPlatform
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.sharedSettingsHubModel
import com.aryan.reader.shared.ui.SharedSettingsHub
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Locks in the settings hub's row semantics.
 *
 * The audited screen gave every toggle row two interactive nodes (the row and its switch), so a
 * screen reader stopped twice per setting and the row itself had no switch role. It also let a
 * search result run a setting's action directly, which meant a cache clear could fire out of a
 * result list. Both are regressions this file refuses to allow back.
 */
@RunWith(AndroidJUnit4::class)
class SharedSettingsHubSemanticsTest {
    @get:Rule val composeTestRule = createComposeRule()

    private fun model() = sharedSettingsHubModel(
        SharedSettingsHubInput(
            platform = SharedSettingsPlatform.ANDROID,
            includePdfFileNameDisplayName = true,
        )
    )

    private fun setHub(
        destination: SharedSettingsDestination,
        query: String = "",
        onAction: (SharedSettingsAction) -> Unit = {},
        onNavigate: (SharedSettingsDestination) -> Unit = {},
    ) {
        val model = model()
        composeTestRule.setContent {
            MaterialTheme {
                SharedSettingsHub(
                    model = model,
                    query = query,
                    onQueryChange = {},
                    readerDefaultSettings = ReaderSettings(),
                    onReaderDefaultSettingsChange = {},
                    ttsReplacementPreferences = ReaderTtsReplacementPreferences(),
                    onTtsReplacementPreferencesChange = {},
                    onAction = onAction,
                    destination = destination,
                    onDestinationChange = onNavigate,
                )
            }
        }
    }

    /** Drives the hub like a host does, so navigation actually changes the rendered destination. */
    private fun setStatefulHub(initial: SharedSettingsDestination) {
        val model = model()
        composeTestRule.setContent {
            var destination by remember { mutableStateOf(initial) }
            MaterialTheme {
                SharedSettingsHub(
                    model = model,
                    query = "",
                    onQueryChange = {},
                    readerDefaultSettings = ReaderSettings(),
                    onReaderDefaultSettingsChange = {},
                    ttsReplacementPreferences = ReaderTtsReplacementPreferences(),
                    onTtsReplacementPreferencesChange = {},
                    onAction = {},
                    destination = destination,
                    onDestinationChange = { destination = it },
                )
            }
        }
    }

    /**
     * Renders the hub inside a tablet-width window so the expanded two-pane layout is the one under
     * test. `requiredWidth` forces the available width the hub measures, which is how the real
     * 840dp breakpoint is reached in a phone-sized test host.
     */
    @Composable
    private fun WideHubHost(destination: SharedSettingsDestination, onNavigate: (SharedSettingsDestination) -> Unit) {
        val model = model()
        Box(Modifier.requiredWidth(900.dp).requiredHeight(900.dp)) {
            SharedSettingsHub(
                model = model,
                query = "",
                onQueryChange = {},
                readerDefaultSettings = ReaderSettings(),
                onReaderDefaultSettingsChange = {},
                ttsReplacementPreferences = ReaderTtsReplacementPreferences(),
                onTtsReplacementPreferencesChange = {},
                onAction = {},
                destination = destination,
                onDestinationChange = onNavigate,
            )
        }
    }

    private fun setWideHub(destination: SharedSettingsDestination) {
        composeTestRule.setContent {
            MaterialTheme { WideHubHost(destination) {} }
        }
    }

    private fun setWideStatefulHub(
        initial: SharedSettingsDestination,
        onNavigate: (SharedSettingsDestination) -> Unit,
    ) {
        composeTestRule.setContent {
            var destination by remember { mutableStateOf(initial) }
            MaterialTheme {
                WideHubHost(destination) { next ->
                    destination = next
                    onNavigate(next)
                }
            }
        }
    }

    @Test
    fun eachToggleRowIsOneToggleableNode() {
        val toggleCount = model()
            .page(SharedSettingsDestination.APP_LIBRARY)
            .items
            .count { it.checked != null }

        setHub(SharedSettingsDestination.APP_LIBRARY)

        // A row with a separately-toggling Switch would report twice as many nodes as rows.
        composeTestRule.onAllNodes(isToggleable())
            .assertCountEquals(toggleCount)
    }

    @Test
    fun tappingAToggleRowReportsItsActionOnce() {
        val actions = mutableListOf<SharedSettingsAction>()
        setHub(SharedSettingsDestination.APP_LIBRARY, onAction = { actions += it })

        composeTestRule.onAllNodes(isToggleable()).onFirst().performClick()

        composeTestRule.runOnIdle {
            assertThat(actions).containsExactly(SharedSettingsAction.TABS_TOGGLE)
        }
    }

    @Test
    fun categoryRowNavigatesInsteadOfRunningTheAction() {
        val actions = mutableListOf<SharedSettingsAction>()
        val navigations = mutableListOf<SharedSettingsDestination>()
        setHub(
            destination = SharedSettingsDestination.ROOT,
            onAction = { actions += it },
            onNavigate = { navigations += it },
        )

        composeTestRule.onAllNodesWithText("EPUB & Text").onFirst().performClick()

        // A category row carries a navigation destination, not an action: running the action would
        // do nothing because no host maps a category destination to a setting change.
        composeTestRule.runOnIdle {
            assertThat(navigations).containsExactly(SharedSettingsDestination.EPUB_TEXT)
            assertThat(actions).isEmpty()
        }
    }

    @Test
    fun epubPageExplainsThatReaderChangesStayPerBook() {
        setHub(SharedSettingsDestination.EPUB_TEXT)

        composeTestRule
            .onNodeWithText(
                "Font, theme, and layout changes made inside the reader apply to that book only."
            )
            .assertExists()
    }

    @Test
    fun drillingIntoACategoryAnimatesThePageSwap() {
        composeTestRule.mainClock.autoAdvance = false
        setStatefulHub(SharedSettingsDestination.ROOT)

        composeTestRule.onAllNodesWithText("EPUB & Text").onFirst().performClick()
        composeTestRule.mainClock.advanceTimeByFrame()

        // A hard cut would have replaced the root list on the very next frame. The shared-axis
        // transition keeps it composed while the category page slides in, which is the whole point
        // of the animation: the user can see that the page came from the row they tapped.
        composeTestRule.onAllNodesWithText("PDF & Comics").assertCountEquals(1)

        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.mainClock.autoAdvance = true

        composeTestRule.onAllNodesWithText("PDF & Comics").assertCountEquals(0)
        composeTestRule
            .onNodeWithText("Default text layout, EPUB appearance, and reader tools")
            .assertExists()
    }

    @Test
    fun searchResultNavigatesInsteadOfRunningTheAction() {
        val actions = mutableListOf<SharedSettingsAction>()
        val navigations = mutableListOf<SharedSettingsDestination>()
        setHub(
            destination = SharedSettingsDestination.ROOT,
            query = "clear book cache",
            onAction = { actions += it },
            onNavigate = { navigations += it },
        )

        composeTestRule.onNodeWithText("Clear book cache").performClick()

        composeTestRule.runOnIdle {
            assertThat(navigations).containsExactly(SharedSettingsDestination.STORAGE_PRIVACY)
            assertThat(actions).isEmpty()
        }
    }

    /**
     * The expanded layout's category list is a real `NavigationDrawerItem`, so selection is state,
     * not a hand-painted background. Losing that would leave the wide layout with no way to tell
     * which category the right-hand pane belongs to.
     */
    @Test
    fun expandedLayoutMarksTheCurrentCategoryAsSelected() {
        setWideHub(SharedSettingsDestination.EPUB_TEXT)

        composeTestRule.onNodeWithText("EPUB & Text").assertIsSelected()
    }

    @Test
    fun expandedNavigationPaneSwitchesPages() {
        val navigations = mutableListOf<SharedSettingsDestination>()
        setWideStatefulHub(SharedSettingsDestination.ROOT, onNavigate = { navigations += it })

        composeTestRule.onNodeWithText("PDF & Comics").performClick()

        composeTestRule.runOnIdle {
            assertThat(navigations).containsExactly(SharedSettingsDestination.PDF_COMICS)
        }
    }
}
