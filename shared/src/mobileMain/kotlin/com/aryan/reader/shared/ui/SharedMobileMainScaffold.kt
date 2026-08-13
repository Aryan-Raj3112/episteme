package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Android's source-of-truth bottom navigation shell, shared by both phone
 * platforms. Screen content and platform integrations remain injectable.
 */
@Composable
fun SharedMobileMainScaffold(
    selectedDestination: SharedMobileMainDestination,
    onDestinationSelected: (SharedMobileMainDestination) -> Unit,
    destinationLabel: @Composable (SharedMobileMainDestination) -> Unit = { destination ->
        Text(
            when (destination) {
                SharedMobileMainDestination.HOME -> "Home"
                SharedMobileMainDestination.LIBRARY -> "Library"
                SharedMobileMainDestination.UNIFIED_LIBRARY -> "Library Beta"
            }
        )
    },
    destinationIcon: @Composable (SharedMobileMainDestination) -> Unit = { destination ->
        val label = when (destination) {
            SharedMobileMainDestination.HOME -> "Home"
            SharedMobileMainDestination.LIBRARY -> "Library"
            SharedMobileMainDestination.UNIFIED_LIBRARY -> "Library Beta"
        }
        Icon(
            imageVector = when (destination) {
                SharedMobileMainDestination.HOME -> Icons.Default.Home
                SharedMobileMainDestination.LIBRARY -> Icons.AutoMirrored.Filled.LibraryBooks
                SharedMobileMainDestination.UNIFIED_LIBRARY -> Icons.AutoMirrored.Filled.LibraryBooks
            },
            contentDescription = label
        )
    },
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                SharedMobileMainDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selectedDestination == destination,
                        onClick = { onDestinationSelected(destination) },
                        icon = { destinationIcon(destination) },
                        label = { destinationLabel(destination) }
                    )
                }
            }
        },
        content = content
    )
}
