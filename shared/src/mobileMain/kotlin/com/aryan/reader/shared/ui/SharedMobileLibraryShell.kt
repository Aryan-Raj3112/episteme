package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The navigation shell around the unified library.
 *
 * [SharedLibraryNavMode.DRAWER] renders exactly the `ModalNavigationDrawer` the mobile
 * library has always used. [SharedLibraryNavMode.RAIL] swaps it for a permanent leading
 * rail, which is only ever selected on an iPhone Duo inner display (see
 * [DuoLayoutPolicy.navMode]).
 *
 * The two modes take their content from the same drawer model, so they cannot drift:
 * destinations, order and labels have a single source of truth.
 */
@Composable
fun SharedMobileLibraryShell(
    navMode: SharedLibraryNavMode,
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    railContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when (navMode) {
        SharedLibraryNavMode.DRAWER -> ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = { ModalDrawerSheet { drawerContent() } },
            modifier = modifier,
        ) {
            content()
        }

        SharedLibraryNavMode.RAIL -> Row(modifier.fillMaxSize()) {
            railContent()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                content()
            }
        }
    }
}

/**
 * The Duo rail's width. Material's `NavigationRail` does not constrain its own width (its
 * items fill whatever they are given), so inside our `Row` it would otherwise consume the
 * whole display and leave the content column nothing.
 */
private val SharedMobileLibraryRailWidth = 80.dp

/**
 * Permanent leading navigation for the Duo inner display. Reuses the drawer's model and
 * label/icon resolvers; it deliberately introduces no navigation state of its own.
 */
@Composable
internal fun SharedMobileLibraryRail(
    destinations: List<MobileUnifiedLibraryDrawerDestination>,
    selectedSection: MobileUnifiedLibrarySection,
    appearance: List<MobileUnifiedLibraryDrawerAppearance>,
    onDestinationSelected: (MobileUnifiedLibraryDrawerDestination) -> Unit,
    onAppearanceSelected: (MobileUnifiedLibraryDrawerAppearance) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier.width(SharedMobileLibraryRailWidth),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // Icon-only: Apple's vertical-bar guidance is that a control presented on the
        // vertical axis is represented by its symbol, and a 7.6" rail cannot fit labels
        // like "AI keys and models" without truncating them. The label still reaches
        // accessibility through the icon's content description.
        destinations.forEach { destination ->
            NavigationRailItem(
                selected = selectedSection == destination.section,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(destination.icon(), contentDescription = destination.label.readerString())
                },
                modifier = Modifier.testTag("UnifiedLibraryRail_${destination.name}"),
            )
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(Modifier.padding(horizontal = 12.dp))
        appearance.forEach { item ->
            NavigationRailItem(
                selected = false,
                onClick = { onAppearanceSelected(item) },
                icon = {
                    Icon(item.icon(), contentDescription = item.label.readerString())
                },
                modifier = Modifier.testTag("UnifiedLibraryRail_${item.name}"),
            )
        }
    }
}
