package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class SharedAppTab {
    HOME,
    LIBRARY,
    SHELVES,
    READER
}

@Composable
fun SharedAppShell(
    selectedTab: SharedAppTab,
    snackbarHostState: SnackbarHostState,
    onTabSelected: (SharedAppTab) -> Unit,
    onImportFiles: () -> Unit,
    onSyncRequested: () -> Unit,
    content: @Composable (SharedAppTab) -> Unit
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationRailItem(
                    selected = selectedTab == SharedAppTab.HOME,
                    onClick = { onTabSelected(SharedAppTab.HOME) },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationRailItem(
                    selected = selectedTab == SharedAppTab.LIBRARY,
                    onClick = { onTabSelected(SharedAppTab.LIBRARY) },
                    icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
                    label = { Text("Library") }
                )
                NavigationRailItem(
                    selected = selectedTab == SharedAppTab.SHELVES,
                    onClick = { onTabSelected(SharedAppTab.SHELVES) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("Shelves") }
                )
                NavigationRailItem(
                    selected = selectedTab == SharedAppTab.READER,
                    onClick = { onTabSelected(SharedAppTab.READER) },
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                    label = { Text("Reader") }
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onImportFiles) {
                    Icon(Icons.Default.ImportExport, contentDescription = "Import files")
                }
                IconButton(onClick = onSyncRequested) {
                    Icon(Icons.Default.Sync, contentDescription = "Sync")
                }
            }

            Box(Modifier.fillMaxSize()) {
                content(selectedTab)
            }
        }
    }
}
