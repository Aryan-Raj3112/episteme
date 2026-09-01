package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

data class SharedAndroidHomeTopBarStrings(
    val openDrawer: String,
    val settings: String,
    val appTheme: String,
    val recentLimit: String,
    val noLimit: String,
    val limitLabels: Map<Int, String>,
    val selected: String,
    val moreOptions: String,
    val about: String,
    val multiTab: String,
    val screenCaptureProtection: String,
    val externalFileBehavior: String,
    val strictFileFilter: String,
    val pdfFileName: String,
    val language: String,
    val hideReaderAi: String,
    val showReaderAi: String,
    val enabled: String,
    val clearBookCache: String,
    val clearReflowCache: String,
    val testPanelDetection: String,
    val testSpeechBubbleDetection: String,
    val exportLogs: String,
    val showDeviceManagement: String,
    val clearCloudAndLocalData: String,
)

/** Exact Android Home top bar and menus. Platform supplies strings, palette icon and persistence actions. */
@Composable
fun SharedAndroidHomeTopBar(
    strings: SharedAndroidHomeTopBarStrings,
    hasUnreadFeedback: Boolean,
    recentFilesLimit: Int,
    tabsEnabled: Boolean,
    screenCaptureProtectionEnabled: Boolean,
    strictFileFilterEnabled: Boolean,
    usePdfFileNameAsDisplayName: Boolean,
    initialHideReaderAi: Boolean,
    showReaderAiOption: Boolean,
    showDebugActions: Boolean,
    showDebugCloudActions: Boolean,
    onDrawer: () -> Unit,
    onSettings: () -> Unit,
    onAppTheme: () -> Unit,
    onRecentFilesLimitChange: (Int) -> Unit,
    onAbout: () -> Unit,
    onTabsToggle: () -> Unit,
    onScreenCaptureProtectionToggle: () -> Unit,
    onExternalFileBehavior: () -> Unit,
    onStrictFileFilterToggle: () -> Unit,
    onPdfFileNameToggle: () -> Unit,
    onLanguage: () -> Unit,
    onToggleReaderAi: () -> Unit,
    onClearBookCache: () -> Unit,
    onClearReflowCache: () -> Unit,
    onTestPanelDetection: () -> Unit,
    onTestSpeechBubbleDetection: () -> Unit,
    onExportLogs: () -> Unit,
    onShowDeviceManagement: () -> Unit,
    onClearCloudAndLocalData: () -> Unit,
    appThemeIcon: @Composable () -> Unit,
) {
    var optionsExpanded by remember { mutableStateOf(false) }
    var limitsExpanded by remember { mutableStateOf(false) }
    var hideReaderAi by remember(initialHideReaderAi) { mutableStateOf(initialHideReaderAi) }
    val overflowItems = sharedMobileHomeOverflowItems(
        state = SharedMobileHomeOverflowState(
            tabsEnabled = tabsEnabled,
            screenCaptureProtectionEnabled = screenCaptureProtectionEnabled,
            strictFileFilterEnabled = strictFileFilterEnabled,
            usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            hideReaderAi = hideReaderAi,
        ),
        capabilities = SharedMobileHomeOverflowCapabilities(
            screenCaptureProtection = true,
            readerAi = showReaderAiOption,
            clearBookCache = true,
            clearReflowCache = true,
            testMlDiagnostics = showDebugActions,
            exportLogs = showDebugActions,
            deviceManagement = showDebugCloudActions,
            clearCloudAndLocalData = showDebugCloudActions,
        ),
    )

    fun itemLabel(action: SharedMobileHomeOverflowAction): String = when (action) {
        SharedMobileHomeOverflowAction.ABOUT -> strings.about
        SharedMobileHomeOverflowAction.TABS_TOGGLE -> strings.multiTab
        SharedMobileHomeOverflowAction.SCREEN_CAPTURE_PROTECTION -> strings.screenCaptureProtection
        SharedMobileHomeOverflowAction.EXTERNAL_FILE_BEHAVIOR -> strings.externalFileBehavior
        SharedMobileHomeOverflowAction.STRICT_FILE_FILTER -> strings.strictFileFilter
        SharedMobileHomeOverflowAction.PDF_FILENAME_DISPLAY_NAME -> strings.pdfFileName
        SharedMobileHomeOverflowAction.LANGUAGE -> strings.language
        SharedMobileHomeOverflowAction.TOGGLE_READER_AI -> if (hideReaderAi) {
            strings.showReaderAi
        } else {
            strings.hideReaderAi
        }
        SharedMobileHomeOverflowAction.CLEAR_BOOK_CACHE -> strings.clearBookCache
        SharedMobileHomeOverflowAction.CLEAR_REFLOW_CACHE -> strings.clearReflowCache
        SharedMobileHomeOverflowAction.TEST_PANEL_DETECTION -> strings.testPanelDetection
        SharedMobileHomeOverflowAction.TEST_SPEECH_BUBBLE_DETECTION -> strings.testSpeechBubbleDetection
        SharedMobileHomeOverflowAction.EXPORT_LOGS -> strings.exportLogs
        SharedMobileHomeOverflowAction.DEVICE_MANAGEMENT -> strings.showDeviceManagement
        SharedMobileHomeOverflowAction.CLEAR_CLOUD_LOCAL_DATA -> strings.clearCloudAndLocalData
    }

    fun onItemClick(action: SharedMobileHomeOverflowAction) {
        when (action) {
            SharedMobileHomeOverflowAction.ABOUT -> onAbout()
            SharedMobileHomeOverflowAction.TABS_TOGGLE -> onTabsToggle()
            SharedMobileHomeOverflowAction.SCREEN_CAPTURE_PROTECTION -> onScreenCaptureProtectionToggle()
            SharedMobileHomeOverflowAction.EXTERNAL_FILE_BEHAVIOR -> onExternalFileBehavior()
            SharedMobileHomeOverflowAction.STRICT_FILE_FILTER -> onStrictFileFilterToggle()
            SharedMobileHomeOverflowAction.PDF_FILENAME_DISPLAY_NAME -> onPdfFileNameToggle()
            SharedMobileHomeOverflowAction.LANGUAGE -> onLanguage()
            SharedMobileHomeOverflowAction.TOGGLE_READER_AI -> {
                onToggleReaderAi()
                hideReaderAi = !hideReaderAi
            }
            SharedMobileHomeOverflowAction.CLEAR_BOOK_CACHE -> onClearBookCache()
            SharedMobileHomeOverflowAction.CLEAR_REFLOW_CACHE -> onClearReflowCache()
            SharedMobileHomeOverflowAction.TEST_PANEL_DETECTION -> onTestPanelDetection()
            SharedMobileHomeOverflowAction.TEST_SPEECH_BUBBLE_DETECTION -> onTestSpeechBubbleDetection()
            SharedMobileHomeOverflowAction.EXPORT_LOGS -> onExportLogs()
            SharedMobileHomeOverflowAction.DEVICE_MANAGEMENT -> onShowDeviceManagement()
            SharedMobileHomeOverflowAction.CLEAR_CLOUD_LOCAL_DATA -> onClearCloudAndLocalData()
        }
    }

    SharedMobileTopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onDrawer) {
                BadgedBox(badge = { if (hasUnreadFeedback) Badge() }) {
                    Icon(Icons.Default.Menu, strings.openDrawer)
                }
            }
        },
        actions = {
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, strings.settings) }
            IconButton(onClick = onAppTheme) { appThemeIcon() }
            Box {
                IconButton(onClick = { limitsExpanded = true }) { Icon(Icons.Default.FormatListNumbered, strings.recentLimit) }
                DropdownMenu(expanded = limitsExpanded, onDismissRequest = { limitsExpanded = false }) {
                    listOf(0, 10, 20, 50, 100).forEach { limit ->
                        DropdownMenuItem(
                            text = { Text(if (limit == 0) strings.noLimit else strings.limitLabels.getValue(limit)) },
                            onClick = { onRecentFilesLimitChange(limit); limitsExpanded = false },
                            trailingIcon = if (recentFilesLimit == limit) ({ Icon(Icons.Default.Check, strings.selected) }) else null,
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { optionsExpanded = true }) { Icon(Icons.Default.MoreVert, strings.moreOptions) }
                DropdownMenu(expanded = optionsExpanded, onDismissRequest = { optionsExpanded = false }) {
                    overflowItems.forEachIndexed { index, overflowItem ->
                        if (index > 0 && overflowItems[index - 1].section != overflowItem.section) {
                            HorizontalDivider()
                        }
                        item(
                            label = itemLabel(overflowItem.action),
                            checked = overflowItem.checked,
                            enabledDescription = strings.enabled,
                        ) {
                            onItemClick(overflowItem.action)
                            optionsExpanded = false
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.item(
    label: String,
    checked: Boolean = false,
    enabledDescription: String,
    action: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = { action() },
        trailingIcon = if (checked) ({ Icon(Icons.Default.Check, enabledDescription) }) else null,
    )
}
