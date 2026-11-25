package com.aryan.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerAppearance
import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerCapabilities
import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerDestination
import com.aryan.reader.shared.ui.SharedAdaptiveLibraryNavigation
import com.aryan.reader.shared.ui.SharedLibraryNavigationRail
import com.aryan.reader.shared.ui.mobileUnifiedLibraryDrawerModel
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The library used to hide every section behind a hamburger on every window size, and the shell
 * had to be reachable from the rail as well as the drawer once a wide window swapped the two.
 * These tests pin the wide-window rail to the shared drawer model and to a single content lambda,
 * so navigation cannot drift between the compact and expanded layouts.
 */
@RunWith(AndroidJUnit4::class)
class SharedLibraryNavigationRailTest {
    @get:Rule val composeTestRule = createComposeRule()

    private val model = mobileUnifiedLibraryDrawerModel(
        MobileUnifiedLibraryDrawerCapabilities(
            catalogsAvailable = true,
            aiSettingsAvailable = true,
        )
    )

    @Composable
    private fun rail(
        selected: MobileUnifiedLibraryDrawerDestination = model.destinations.first(),
        onDestinationSelected: (MobileUnifiedLibraryDrawerDestination) -> Unit = {},
        onAppearanceSelected: (MobileUnifiedLibraryDrawerAppearance) -> Unit = {},
    ) {
        MaterialTheme {
            SharedLibraryNavigationRail(
                destinations = model.destinations,
                selectedSection = selected.section,
                appearance = model.appearance,
                onDestinationSelected = onDestinationSelected,
                onAppearanceSelected = onAppearanceSelected,
                modifier = Modifier.requiredWidth(80.dp),
            )
        }
    }

    @Test
    fun railRendersEveryDestinationAndAppearanceFromTheSharedModel() {
        composeTestRule.setContent { rail() }

        model.destinations.forEach { destination ->
            composeTestRule.onNodeWithTag("UnifiedLibraryRail_${destination.name}")
                .assertIsDisplayed()
        }
        model.appearance.forEach { appearance ->
            composeTestRule.onNodeWithTag("UnifiedLibraryRail_${appearance.name}")
                .assertIsDisplayed()
        }
    }

    @Test
    fun railReportsTheTappedDestination() {
        val selected = mutableListOf<MobileUnifiedLibraryDrawerDestination>()
        composeTestRule.setContent { rail(onDestinationSelected = { selected += it }) }

        val target = model.destinations.last()
        composeTestRule.onNodeWithTag("UnifiedLibraryRail_${target.name}").performClick()

        assertThat(selected).containsExactly(target)
    }

    @Test
    fun wideWindowsComposeTheRailInsteadOfTheDrawer() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedAdaptiveLibraryNavigation(
                    showRail = true,
                    drawerState = rememberDrawerState(DrawerValue.Closed),
                    drawerContent = { Box(Modifier.testTag("drawerOnly")) {} },
                    rail = {},
                ) {
                    Box(Modifier.testTag("theRail")) {}
                }
            }
        }

        composeTestRule.onNodeWithTag("theRail").assertExists()
        assertThat(composeTestRule.onAllNodesWithTag("drawerOnly").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun compactWindowsKeepTheModalDrawer() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedAdaptiveLibraryNavigation(
                    showRail = false,
                    drawerState = rememberDrawerState(DrawerValue.Closed),
                    drawerContent = { Box(Modifier.testTag("drawerOnly")) {} },
                    rail = {},
                ) {
                    Box(Modifier.testTag("theContent")) {}
                }
            }
        }

        composeTestRule.onNodeWithTag("theContent").assertExists()
        assertThat(composeTestRule.onAllNodesWithTag("theRail").fetchSemanticsNodes()).isEmpty()
    }
}
