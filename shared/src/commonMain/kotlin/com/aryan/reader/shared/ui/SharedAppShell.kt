package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.AppContrastOption
import com.aryan.reader.shared.AppThemeMode
import com.aryan.reader.shared.CustomAppTheme

enum class SharedAppTab {
    HOME,
    LIBRARY,
    SHELVES,
    CATALOGS,
    READER,
    CUSTOM_FONTS,
    SUPPORT,
    FEEDBACK,
    ABOUT
}

@Composable
fun SharedAppShell(
    selectedTab: SharedAppTab,
    snackbarHostState: SnackbarHostState,
    appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    appContrastOption: AppContrastOption = AppContrastOption.STANDARD,
    appTextDimFactorLight: Float = 1.0f,
    appTextDimFactorDark: Float = 1.0f,
    appSeedColor: Color? = null,
    customAppThemes: List<CustomAppTheme> = emptyList(),
    isTabsEnabled: Boolean = false,
    onTabSelected: (SharedAppTab) -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit = {},
    onSyncRequested: () -> Unit,
    onAppThemeModeChange: (AppThemeMode) -> Unit = {},
    onAppContrastOptionChange: (AppContrastOption) -> Unit = {},
    onAppTextDimFactorLightChange: (Float) -> Unit = {},
    onAppTextDimFactorDarkChange: (Float) -> Unit = {},
    onAppSeedColorChange: (Color?) -> Unit = {},
    onCustomAppThemeAdded: (CustomAppTheme) -> Unit = {},
    onCustomAppThemeDeleted: (String) -> Unit = {},
    onTabsEnabledChange: (Boolean) -> Unit = {},
    onAiSettingsRequested: (() -> Unit)? = null,
    content: @Composable (SharedAppTab) -> Unit
) {
    var optionsExpanded by remember { mutableStateOf(false) }
    var showAppThemeSettings by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                    selected = selectedTab == SharedAppTab.CATALOGS,
                    onClick = { onTabSelected(SharedAppTab.CATALOGS) },
                    icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                    label = { Text("OPDS") }
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
                IconButton(onClick = onImportFolder) {
                    Icon(Icons.Default.Folder, contentDescription = "Import folder")
                }
                IconButton(onClick = onSyncRequested) {
                    Icon(Icons.Default.Sync, contentDescription = "Sync")
                }
                IconButton(onClick = { optionsExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = optionsExpanded, onDismissRequest = { optionsExpanded = false }) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                        text = { Text("App theme") },
                        onClick = {
                            optionsExpanded = false
                            showAppThemeSettings = true
                        }
                    )
                    if (onAiSettingsRequested != null) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            text = { Text("AI keys and models") },
                            onClick = {
                                optionsExpanded = false
                                onAiSettingsRequested()
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                        text = { Text("Custom fonts") },
                        onClick = {
                            optionsExpanded = false
                            onTabSelected(SharedAppTab.CUSTOM_FONTS)
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Feedback, contentDescription = null) },
                        text = { Text("Help & feedback") },
                        onClick = {
                            optionsExpanded = false
                            onTabSelected(SharedAppTab.FEEDBACK)
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                        text = { Text("Support project") },
                        onClick = {
                            optionsExpanded = false
                            onTabSelected(SharedAppTab.SUPPORT)
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        text = { Text("About Episteme") },
                        onClick = {
                            optionsExpanded = false
                            onTabSelected(SharedAppTab.ABOUT)
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (isTabsEnabled) "Disable active tabs" else "Enable active tabs") },
                        onClick = {
                            optionsExpanded = false
                            onTabsEnabledChange(!isTabsEnabled)
                        }
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                content(selectedTab)
            }
        }
    }

    if (showAppThemeSettings) {
        SharedAppThemeSettingsDialog(
            appThemeMode = appThemeMode,
            appContrastOption = appContrastOption,
            appTextDimFactorLight = appTextDimFactorLight,
            appTextDimFactorDark = appTextDimFactorDark,
            appSeedColor = appSeedColor,
            customAppThemes = customAppThemes,
            onThemeModeChanged = onAppThemeModeChange,
            onContrastOptionChanged = onAppContrastOptionChange,
            onTextDimFactorLightChanged = onAppTextDimFactorLightChange,
            onTextDimFactorDarkChanged = onAppTextDimFactorDarkChange,
            onSeedColorChanged = onAppSeedColorChange,
            onCustomThemeAdded = onCustomAppThemeAdded,
            onCustomThemeDeleted = onCustomAppThemeDeleted,
            onDismiss = { showAppThemeSettings = false }
        )
    }
}
