package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** M3 rail width: wide enough for an icon plus the selected item's label. */
private val SharedLibraryRailWidth = 80.dp

/**
 * M3 navigation rail for library windows that are wider than a phone.
 *
 * Hiding primary navigation behind a hamburger on a tablet is the opposite of what the adaptive
 * guidance asks for at ≥600dp, and the library already has a drawer model with stable destination
 * semantics, so the rail reuses that model instead of introducing a second navigation state.
 *
 * `alwaysShowLabel = false` keeps the rail 80dp wide while the active item still shows its label, so
 * the rail does not eat the grid's width.
 */
@Composable
fun SharedLibraryNavigationRail(
    destinations: List<MobileUnifiedLibraryDrawerDestination>,
    selectedSection: MobileUnifiedLibrarySection,
    appearance: List<MobileUnifiedLibraryDrawerAppearance>,
    onDestinationSelected: (MobileUnifiedLibraryDrawerDestination) -> Unit,
    onAppearanceSelected: (MobileUnifiedLibraryDrawerAppearance) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        // An explicit width: `NavigationRail` wraps its content, and with `alwaysShowLabel = false`
        // the labels are hidden, which measured the rail against the Row instead of the M3 80dp.
        modifier = modifier
            .width(SharedLibraryRailWidth)
            .fillMaxHeight(),
        // Both system bars: the last appearance item must clear the gesture/navigation bar.
        windowInsets = WindowInsets.systemBars,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Spacer(Modifier.height(SharedSpacing.Sm))
        destinations.forEach { destination ->
            NavigationRailItem(
                selected = selectedSection == destination.section,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon(),
                        contentDescription = destination.label.readerString(),
                    )
                },
                label = { Text(destination.label.readerString(), maxLines = 1) },
                alwaysShowLabel = false,
                modifier = Modifier.testTag("UnifiedLibraryRail_${destination.name}"),
            )
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(Modifier.padding(horizontal = SharedSpacing.Md, vertical = SharedSpacing.Sm))
        appearance.forEach { item ->
            NavigationRailItem(
                selected = false,
                onClick = { onAppearanceSelected(item) },
                icon = {
                    Icon(
                        imageVector = item.icon(),
                        contentDescription = item.label.readerString(),
                    )
                },
                label = { Text(item.label.readerString(), maxLines = 1) },
                alwaysShowLabel = false,
                modifier = Modifier.testTag("UnifiedLibraryRail_${item.name}"),
            )
        }
    }
}

/**
 * Chooses between the modal drawer and a permanent rail for one library shell.
 *
 * Both modes render the *same* content lambda, so a wide window cannot silently lose a section, an
 * action, or the FAB relative to the compact layout.
 */
@Composable
fun SharedAdaptiveLibraryNavigation(
    showRail: Boolean,
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    rail: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (showRail) {
        Row(Modifier.fillMaxSize()) {
            rail()
            Box(Modifier.weight(1f)) { content() }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = { drawerContent() },
            content = content,
        )
    }
}
