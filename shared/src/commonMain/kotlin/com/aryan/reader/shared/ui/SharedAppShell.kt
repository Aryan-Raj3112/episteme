package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.AppContrastOption
import com.aryan.reader.shared.AppThemeMode
import com.aryan.reader.shared.CustomAppTheme
import com.aryan.reader.shared.SharedFeaturePolicy

enum class SharedAppTab {
    LIBRARY,
    SHELVES,
    CATALOGS,
    READER,
    SETTINGS,
    PRO,
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
    isTabsEnabled: Boolean = true,
    featurePolicy: SharedFeaturePolicy = SharedFeaturePolicy.Standard,
    onTabSelected: (SharedAppTab) -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit = {},
    onSyncRequested: () -> Unit,
    onFolderMetadataSyncRequested: (() -> Unit)? = null,
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
    val aiSettingsAvailable = onAiSettingsRequested != null && featurePolicy.aiAndCloud
    val shellModel = remember(selectedTab, aiSettingsAvailable, featurePolicy) {
        sharedAppShellModel(
            selectedTab = selectedTab,
            aiSettingsAvailable = aiSettingsAvailable,
            featurePolicy = featurePolicy
        )
    }
    var showAppThemeSettings by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            val useSidebar = maxWidth >= 900.dp
            Row(Modifier.fillMaxSize()) {
                if (shellModel.showPrimaryNavigation) {
                    if (useSidebar) {
                        SharedAppSidebar(
                            selectedTab = shellModel.selectedPrimaryTab,
                            primaryTabs = shellModel.primaryTabs,
                            onTabSelected = onTabSelected,
                            moreMenu = {
                                SharedMoreMenuButton(
                                    compact = false,
                                    moreSections = shellModel.moreSections,
                                    isTabsEnabled = isTabsEnabled,
                                    onImportFiles = onImportFiles,
                                    onImportFolder = onImportFolder,
                                    onSyncRequested = onSyncRequested,
                                    onFolderMetadataSyncRequested = onFolderMetadataSyncRequested,
                                    onAppThemeRequested = { showAppThemeSettings = true },
                                    onAiSettingsRequested = { onAiSettingsRequested?.invoke() },
                                    onOpenTab = onTabSelected,
                                    onTabsEnabledChange = onTabsEnabledChange
                                )
                            }
                        )
                    } else {
                        SharedAppCompactRail(
                            selectedTab = shellModel.selectedPrimaryTab,
                            primaryTabs = shellModel.primaryTabs,
                            onTabSelected = onTabSelected,
                            moreMenu = {
                                SharedMoreMenuButton(
                                    compact = true,
                                    moreSections = shellModel.moreSections,
                                    isTabsEnabled = isTabsEnabled,
                                    onImportFiles = onImportFiles,
                                    onImportFolder = onImportFolder,
                                    onSyncRequested = onSyncRequested,
                                    onFolderMetadataSyncRequested = onFolderMetadataSyncRequested,
                                    onAppThemeRequested = { showAppThemeSettings = true },
                                    onAiSettingsRequested = { onAiSettingsRequested?.invoke() },
                                    onOpenTab = onTabSelected,
                                    onTabsEnabledChange = onTabsEnabledChange
                                )
                            }
                        )
                    }
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    content(selectedTab)
                }
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

@Composable
private fun SharedAppSidebar(
    selectedTab: SharedAppTab,
    primaryTabs: List<SharedAppTab>,
    onTabSelected: (SharedAppTab) -> Unit,
    moreMenu: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(SharedUiTokens.sidebarWidth)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(SharedUiTokens.compactGap)
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                Text(readerString("app_name", "Episteme"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(readerString("desktop_library_and_reader", "Library and reader"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            primaryTabs.forEach { tab ->
                SharedSidebarNavItem(
                    tab = tab,
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) }
                )
            }
            Spacer(Modifier.weight(1f))
            HorizontalDivider()
            moreMenu()
        }
    }
}

@Composable
private fun SharedAppCompactRail(
    selectedTab: SharedAppTab,
    primaryTabs: List<SharedAppTab>,
    onTabSelected: (SharedAppTab) -> Unit,
    moreMenu: @Composable () -> Unit
) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        primaryTabs.forEach { tab ->
            NavigationRailItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.localizedLabel()) }
            )
        }
        Spacer(Modifier.weight(1f))
        moreMenu()
    }
}

@Composable
private fun SharedSidebarNavItem(
    tab: SharedAppTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(21.dp))
            Text(tab.localizedLabel(), style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun SharedSidebarButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SharedMoreMenuButton(
    compact: Boolean,
    moreSections: List<SharedAppMoreSection>,
    isTabsEnabled: Boolean,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onSyncRequested: () -> Unit,
    onFolderMetadataSyncRequested: (() -> Unit)?,
    onAppThemeRequested: () -> Unit,
    onAiSettingsRequested: () -> Unit,
    onOpenTab: (SharedAppTab) -> Unit,
    onTabsEnabledChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    fun runAction(action: SharedAppToolAction) {
        expanded = false
        when (action) {
            SharedAppToolAction.SETTINGS -> onOpenTab(SharedAppTab.SETTINGS)
            SharedAppToolAction.IMPORT_FILES -> onImportFiles()
            SharedAppToolAction.IMPORT_FOLDER -> onImportFolder()
            SharedAppToolAction.SYNC -> onSyncRequested()
            SharedAppToolAction.APP_THEME -> onAppThemeRequested()
            SharedAppToolAction.PRO -> onOpenTab(SharedAppTab.PRO)
            SharedAppToolAction.AI_SETTINGS -> onAiSettingsRequested()
            SharedAppToolAction.CUSTOM_FONTS -> onOpenTab(SharedAppTab.CUSTOM_FONTS)
            SharedAppToolAction.HELP_FEEDBACK -> onOpenTab(SharedAppTab.FEEDBACK)
            SharedAppToolAction.SUPPORT -> onOpenTab(SharedAppTab.SUPPORT)
            SharedAppToolAction.ABOUT -> onOpenTab(SharedAppTab.ABOUT)
            SharedAppToolAction.TABS_TOGGLE -> onTabsEnabledChange(!isTabsEnabled)
        }
    }

    Box(modifier = if (compact) Modifier else Modifier.fillMaxWidth()) {
        if (compact) {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = readerString("desktop_more_menu", "More"))
            }
        } else {
            SharedSidebarButton(
                label = readerString("desktop_more_menu", "More"),
                icon = Icons.Default.MoreVert,
                onClick = { expanded = true }
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(300.dp)
        ) {
            moreSections.forEachIndexed { sectionIndex, section ->
                SharedMoreMenuSectionLabel(section.group)
                section.actions.forEach { action ->
                    if (action == SharedAppToolAction.SYNC && onFolderMetadataSyncRequested != null) {
                        SharedMoreMenuItem(
                            icon = Icons.Default.Sync,
                            title = readerString("desktop_sync_metadata", "Sync metadata"),
                            onClick = {
                                expanded = false
                                onFolderMetadataSyncRequested()
                            }
                        )
                        SharedMoreMenuItem(
                            icon = Icons.Default.Search,
                            title = readerString("desktop_full_scan", "Full scan"),
                            onClick = { runAction(action) }
                        )
                    } else {
                        SharedMoreMenuItem(
                            icon = action.iconForMoreMenu(),
                            title = action.labelForMoreMenu(isTabsEnabled),
                            checked = if (action == SharedAppToolAction.TABS_TOGGLE) isTabsEnabled else null,
                            onClick = { runAction(action) }
                        )
                    }
                }
                if (sectionIndex != moreSections.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SharedMoreMenuSectionLabel(group: SharedAppMoreGroup) {
    Text(
        group.labelForMoreMenu(),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SharedMoreMenuItem(
    icon: ImageVector,
    title: String,
    checked: Boolean? = null,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = checked?.let { isChecked ->
            { Switch(checked = isChecked, onCheckedChange = null) }
        },
        onClick = onClick
    )
}

@Composable
private fun SharedAppMoreGroup.labelForMoreMenu(): String {
    return when (this) {
        SharedAppMoreGroup.LIBRARY -> readerString("desktop_more_library_actions", "Library actions")
        SharedAppMoreGroup.ACCOUNT -> readerString("desktop_account_and_credits", "Account & credits")
        SharedAppMoreGroup.PREFERENCES -> readerString("desktop_preferences", "Preferences")
        SharedAppMoreGroup.HELP -> readerString("desktop_help", "Help")
    }
}

@Composable
private fun SharedAppToolAction.labelForMoreMenu(isTabsEnabled: Boolean): String {
    return when (this) {
        SharedAppToolAction.SETTINGS -> readerString("settings", "Settings")
        SharedAppToolAction.IMPORT_FILES -> readerString("desktop_import_files", "Import files")
        SharedAppToolAction.IMPORT_FOLDER -> readerString("fab_add_folder", "Add folder")
        SharedAppToolAction.SYNC -> readerString("desktop_sync_folders", "Sync folders")
        SharedAppToolAction.APP_THEME -> readerString("app_theme_title", "App theme")
        SharedAppToolAction.PRO -> readerString("desktop_account_and_credits", "Account & credits")
        SharedAppToolAction.AI_SETTINGS -> readerString("ai_settings_title", "AI keys and models")
        SharedAppToolAction.CUSTOM_FONTS -> readerString("custom_fonts", "Custom fonts")
        SharedAppToolAction.HELP_FEEDBACK -> readerString("drawer_help_feedback", "Help & feedback")
        SharedAppToolAction.SUPPORT -> readerString("drawer_support_project", "Support project")
        SharedAppToolAction.ABOUT -> readerString("about_title", "About Episteme")
        SharedAppToolAction.TABS_TOGGLE -> if (isTabsEnabled) {
            readerString("desktop_reader_tabs_on", "Reader tabs on")
        } else {
            readerString("desktop_reader_tabs_off", "Reader tabs off")
        }
    }
}

private fun SharedAppToolAction.iconForMoreMenu(): ImageVector {
    return when (this) {
        SharedAppToolAction.SETTINGS -> Icons.Default.Settings
        SharedAppToolAction.IMPORT_FILES -> Icons.Default.ImportExport
        SharedAppToolAction.IMPORT_FOLDER -> Icons.Default.CreateNewFolder
        SharedAppToolAction.SYNC -> Icons.Default.Sync
        SharedAppToolAction.APP_THEME -> Icons.Default.Palette
        SharedAppToolAction.PRO -> Icons.Default.Star
        SharedAppToolAction.AI_SETTINGS -> Icons.Default.Settings
        SharedAppToolAction.CUSTOM_FONTS -> Icons.Default.TextFields
        SharedAppToolAction.HELP_FEEDBACK -> Icons.Default.Feedback
        SharedAppToolAction.SUPPORT -> Icons.Default.Favorite
        SharedAppToolAction.ABOUT -> Icons.Default.Info
        SharedAppToolAction.TABS_TOGGLE -> Icons.AutoMirrored.Filled.MenuBook
    }
}

@Composable
private fun SharedAppTab.localizedLabel(): String {
    return when (this) {
        SharedAppTab.LIBRARY -> readerString("library_title", "Library")
        SharedAppTab.SHELVES -> readerString("tab_shelves", "Shelves")
        SharedAppTab.CATALOGS -> readerString("opds_stream", "OPDS")
        SharedAppTab.READER -> readerString("desktop_reader", "Reader")
        SharedAppTab.SETTINGS -> readerString("settings", "Settings")
        SharedAppTab.PRO -> readerString("desktop_account_and_credits", "Account & credits")
        SharedAppTab.CUSTOM_FONTS -> readerString("custom_fonts", "Custom fonts")
        SharedAppTab.SUPPORT -> readerString("desktop_support", "Support")
        SharedAppTab.FEEDBACK -> readerString("desktop_feedback", "Feedback")
        SharedAppTab.ABOUT -> readerString("desktop_about", "About")
    }
}

private val SharedAppTab.icon: ImageVector
    get() = when (this) {
        SharedAppTab.LIBRARY -> Icons.AutoMirrored.Filled.LibraryBooks
        SharedAppTab.SHELVES -> Icons.Default.Folder
        SharedAppTab.CATALOGS -> Icons.Default.Cloud
        SharedAppTab.READER -> Icons.AutoMirrored.Filled.MenuBook
        SharedAppTab.SETTINGS -> Icons.Default.Settings
        SharedAppTab.PRO -> Icons.Default.Star
        SharedAppTab.CUSTOM_FONTS -> Icons.Default.TextFields
        SharedAppTab.SUPPORT -> Icons.Default.Favorite
        SharedAppTab.FEEDBACK -> Icons.Default.Feedback
        SharedAppTab.ABOUT -> Icons.Default.Info
    }
