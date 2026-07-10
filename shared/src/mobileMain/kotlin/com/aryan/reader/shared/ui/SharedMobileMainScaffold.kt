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
 * The two top-level destinations shared by Android and iOS phone layouts.
 *
 * Desktop deliberately owns a different navigation model, so this type lives
 * in mobileMain rather than commonMain.
 */
enum class SharedMobileMainDestination(
    val label: String
) {
    HOME("Home"),
    LIBRARY("Library")
}

/**
 * Android's source-of-truth bottom navigation shell, shared by both phone
 * platforms. Screen content and platform integrations remain injectable.
 */
@Composable
fun SharedMobileMainScaffold(
    selectedDestination: SharedMobileMainDestination,
    onDestinationSelected: (SharedMobileMainDestination) -> Unit,
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
                        icon = {
                            Icon(
                                imageVector = when (destination) {
                                    SharedMobileMainDestination.HOME -> Icons.Default.Home
                                    SharedMobileMainDestination.LIBRARY -> Icons.AutoMirrored.Filled.LibraryBooks
                                },
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        },
        content = content
    )
}
