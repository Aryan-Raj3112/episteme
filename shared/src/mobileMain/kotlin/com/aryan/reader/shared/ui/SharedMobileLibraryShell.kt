package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The navigation shell around the unified library.
 *
 * [SharedLibraryNavMode.DRAWER] renders exactly the `ModalNavigationDrawer` the mobile
 * library has always used. [SharedLibraryNavMode.RAIL] swaps it for a permanent rail on
 * the display's control edge, which is only ever selected on an iPhone Duo inner display
 * (see [DuoLayoutPolicy.navMode]).
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
    railOnTrailing: Boolean = true,
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

        // On a foldable iPhone the system keeps its Dynamic Island and status bar on the
        // trailing edge of the display, so the app's rail belongs on that same edge: the
        // two share one vertical column and the content keeps the whole other side. In the
        // left half of Split View the platform's column moves to the leading edge, and the
        // rail follows it.
        SharedLibraryNavMode.RAIL -> Row(modifier.fillMaxSize()) {
            if (railOnTrailing) {
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                railContent()
            } else {
                railContent()
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        }

        // The upright inner display has no side column: its destinations ride in the top
        // bar instead, so the shell adds nothing around the content.
        SharedLibraryNavMode.HORIZONTAL -> content()
    }
}

/**
 * The destinations as a row, for the one pose that keeps horizontal bars (the inner display
 * held upright). Same items, same order and same 48dp targets as the rail, so switching
 * between poses does not move anything the user has learned. The row starts at the leading
 * edge and any slack falls at the trailing end, beside the system status indicator.
 */
@Composable
internal fun SharedMobileLibraryHorizontalNav(
    destinations: List<MobileUnifiedLibraryDrawerDestination>,
    selectedSection: MobileUnifiedLibrarySection,
    appearance: List<MobileUnifiedLibraryDrawerAppearance>,
    onDestinationSelected: (MobileUnifiedLibraryDrawerDestination) -> Unit,
    onAppearanceSelected: (MobileUnifiedLibraryDrawerAppearance) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEach { destination ->
            SharedMobileRailItem(
                icon = destination.icon(),
                contentDescription = destination.label.readerString(),
                selected = selectedSection == destination.section,
                onClick = { onDestinationSelected(destination) },
                testTag = "UnifiedLibraryRail_${destination.name}",
            )
        }
        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 6.dp))
        appearance.forEach { item ->
            SharedMobileRailItem(
                icon = item.icon(),
                contentDescription = item.label.readerString(),
                selected = false,
                onClick = { onAppearanceSelected(item) },
                testTag = "UnifiedLibraryRail_${item.name}",
            )
        }
    }
}

/**
 * Permanent navigation rail for a Duo display. Reuses the drawer's model and label/icon
 * resolvers; it deliberately introduces no navigation state of its own.
 *
 * [width] and [topInset] come from [DuoLayoutPolicy] so the rail covers the platform's
 * reserved control column and starts below the system status area inside it.
 *
 * Items are 48dp tall with no inter-item gap, matching the 48pt controls and tight pitch
 * of Apple's Duo bar, rather than Material's taller `NavigationRailItem` (which overflowed
 * the column and left the last row pressed against the display's rounded corner).
 */
@Composable
internal fun SharedMobileLibraryRail(
    destinations: List<MobileUnifiedLibraryDrawerDestination>,
    selectedSection: MobileUnifiedLibrarySection,
    appearance: List<MobileUnifiedLibraryDrawerAppearance>,
    onDestinationSelected: (MobileUnifiedLibraryDrawerDestination) -> Unit,
    onAppearanceSelected: (MobileUnifiedLibraryDrawerAppearance) -> Unit,
    width: Dp,
    topInset: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        // Fill the height so the column's background reaches the top of the display, behind
        // the system status. Without it the surface wrapped its content and the strip above
        // the first item showed the content colour instead, leaving the column looking
        // detached from the status area.
        modifier = modifier.width(width).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // The background runs the full height, painting behind the system status area, so
        // the column reads as one surface. Only the items are pushed down past it, and the
        // home indicator's inset keeps the last row off the display's bottom corner.
        // Scrollable because the outer display is short and its camera/island reserve is
        // deep; a shorter display can never clip the last item.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(Modifier.height(topInset))
            destinations.forEach { destination ->
                SharedMobileRailItem(
                    icon = destination.icon(),
                    contentDescription = destination.label.readerString(),
                    selected = selectedSection == destination.section,
                    onClick = { onDestinationSelected(destination) },
                    testTag = "UnifiedLibraryRail_${destination.name}",
                )
            }
            // No weight spacer here: the appearance group sits directly under the divider
            // so there is no dead band in the middle of the column. The leftover space
            // falls below the last item, which reads as a bottom margin.
            HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
            appearance.forEach { item ->
                SharedMobileRailItem(
                    icon = item.icon(),
                    contentDescription = item.label.readerString(),
                    selected = false,
                    onClick = { onAppearanceSelected(item) },
                    testTag = "UnifiedLibraryRail_${item.name}",
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SharedMobileRailItem(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
    }
}
