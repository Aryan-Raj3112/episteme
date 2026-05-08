package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val shellModel = remember(selectedTab, onAiSettingsRequested != null) {
        sharedAppShellModel(
            selectedTab = selectedTab,
            aiSettingsAvailable = onAiSettingsRequested != null
        )
    }
    var showToolsPanel by remember { mutableStateOf(false) }
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
                if (useSidebar) {
                    SharedAppSidebar(
                        selectedTab = shellModel.selectedPrimaryTab,
                        primaryTabs = shellModel.primaryTabs,
                        onTabSelected = onTabSelected,
                        onToolsClick = { showToolsPanel = true }
                    )
                } else {
                    SharedAppCompactRail(
                        selectedTab = shellModel.selectedPrimaryTab,
                        primaryTabs = shellModel.primaryTabs,
                        onTabSelected = onTabSelected,
                        onToolsClick = { showToolsPanel = true }
                    )
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

            if (showToolsPanel) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f))
                        .clickable { showToolsPanel = false }
                )
                SharedToolsPanel(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .widthIn(max = 390.dp),
                    isTabsEnabled = isTabsEnabled,
                    aiSettingsAvailable = onAiSettingsRequested != null,
                    onClose = { showToolsPanel = false },
                    onImportFiles = {
                        showToolsPanel = false
                        onImportFiles()
                    },
                    onImportFolder = {
                        showToolsPanel = false
                        onImportFolder()
                    },
                    onSyncRequested = {
                        showToolsPanel = false
                        onSyncRequested()
                    },
                    onAppThemeRequested = {
                        showToolsPanel = false
                        showAppThemeSettings = true
                    },
                    onAiSettingsRequested = {
                        showToolsPanel = false
                        onAiSettingsRequested?.invoke()
                    },
                    onOpenTab = { tab ->
                        showToolsPanel = false
                        onTabSelected(tab)
                    },
                    onTabsEnabledChange = onTabsEnabledChange
                )
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
    onToolsClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(244.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                Text("Episteme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Desktop library", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            SharedSidebarButton(
                label = "Tools",
                icon = Icons.Default.Settings,
                onClick = onToolsClick
            )
        }
    }
}

@Composable
private fun SharedAppCompactRail(
    selectedTab: SharedAppTab,
    primaryTabs: List<SharedAppTab>,
    onTabSelected: (SharedAppTab) -> Unit,
    onToolsClick: () -> Unit
) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        primaryTabs.forEach { tab ->
            NavigationRailItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) }
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToolsClick) {
            Icon(Icons.Default.Settings, contentDescription = "Tools")
        }
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
            Text(tab.label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
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
private fun SharedToolsPanel(
    modifier: Modifier,
    isTabsEnabled: Boolean,
    aiSettingsAvailable: Boolean,
    onClose: () -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onSyncRequested: () -> Unit,
    onAppThemeRequested: () -> Unit,
    onAiSettingsRequested: () -> Unit,
    onOpenTab: (SharedAppTab) -> Unit,
    onTabsEnabledChange: (Boolean) -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Import, sync, and app settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close tools")
                }
            }

            SharedToolsSection("Library") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onImportFiles, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ImportExport, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Files")
                    }
                    OutlinedButton(onClick = onImportFolder, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Folder")
                    }
                }
                FilledTonalButton(onClick = onSyncRequested, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sync folders")
                }
            }

            SharedToolsSection("Appearance") {
                SharedToolRow(
                    icon = Icons.Default.Palette,
                    title = "App theme",
                    onClick = onAppThemeRequested
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Active reader tabs", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(if (isTabsEnabled) "Enabled" else "Disabled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isTabsEnabled,
                        onCheckedChange = onTabsEnabledChange
                    )
                }
            }

            SharedToolsSection("Settings") {
                if (aiSettingsAvailable) {
                    SharedToolRow(Icons.Default.Settings, "AI keys and models", onAiSettingsRequested)
                }
                SharedToolRow(Icons.Default.TextFields, "Custom fonts") { onOpenTab(SharedAppTab.CUSTOM_FONTS) }
            }

            SharedToolsSection("Project") {
                SharedToolRow(Icons.Default.Feedback, "Help & feedback") { onOpenTab(SharedAppTab.FEEDBACK) }
                SharedToolRow(Icons.Default.Favorite, "Support project") { onOpenTab(SharedAppTab.SUPPORT) }
                SharedToolRow(Icons.Default.Info, "About Episteme") { onOpenTab(SharedAppTab.ABOUT) }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SharedToolsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SharedToolRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val SharedAppTab.label: String
    get() = when (this) {
        SharedAppTab.HOME -> "Home"
        SharedAppTab.LIBRARY -> "Library"
        SharedAppTab.SHELVES -> "Shelves"
        SharedAppTab.CATALOGS -> "OPDS"
        SharedAppTab.READER -> "Reader"
        SharedAppTab.CUSTOM_FONTS -> "Custom fonts"
        SharedAppTab.SUPPORT -> "Support"
        SharedAppTab.FEEDBACK -> "Feedback"
        SharedAppTab.ABOUT -> "About"
    }

private val SharedAppTab.icon: ImageVector
    get() = when (this) {
        SharedAppTab.HOME -> Icons.Default.Home
        SharedAppTab.LIBRARY -> Icons.AutoMirrored.Filled.LibraryBooks
        SharedAppTab.SHELVES -> Icons.Default.Folder
        SharedAppTab.CATALOGS -> Icons.Default.Cloud
        SharedAppTab.READER -> Icons.AutoMirrored.Filled.MenuBook
        SharedAppTab.CUSTOM_FONTS -> Icons.Default.TextFields
        SharedAppTab.SUPPORT -> Icons.Default.Favorite
        SharedAppTab.FEEDBACK -> Icons.Default.Feedback
        SharedAppTab.ABOUT -> Icons.Default.Info
    }
