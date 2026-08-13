package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedAndroidHomeTopBar
import com.aryan.reader.shared.ui.SharedAndroidHomeTopBarStrings
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedAndroidHomeTopBarTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun menusOwnSelectionCallbacksAndDismissTiming() {
        var selectedLimit: Int? = null
        var strictClicks = 0
        composeTestRule.setContent {
            MaterialTheme {
                SharedAndroidHomeTopBar(
                    strings = strings(),
                    hasUnreadFeedback = false,
                    recentFilesLimit = 10,
                    tabsEnabled = true,
                    screenCaptureProtectionEnabled = false,
                    strictFileFilterEnabled = false,
                    usePdfFileNameAsDisplayName = false,
                    initialHideReaderAi = false,
                    showReaderAiOption = true,
                    showDebugActions = false,
                    showDebugCloudActions = false,
                    onDrawer = {}, onSettings = {}, onAppTheme = {},
                    onRecentFilesLimitChange = { selectedLimit = it },
                    onAbout = {}, onTabsToggle = {}, onScreenCaptureProtectionToggle = {},
                    onExternalFileBehavior = {}, onStrictFileFilterToggle = { strictClicks++ },
                    onPdfFileNameToggle = {}, onLanguage = {}, onToggleReaderAi = {},
                    onClearBookCache = {}, onClearReflowCache = {}, onTestPanelDetection = {},
                    onTestSpeechBubbleDetection = {}, onExportLogs = {}, onShowDeviceManagement = {},
                    onClearCloudAndLocalData = {}, appThemeIcon = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Recent limit").performClick()
        composeTestRule.onNodeWithText("20 files").performClick()
        composeTestRule.onNodeWithContentDescription("More").performClick()
        composeTestRule.onNodeWithText("Strict filter").performClick()
        composeTestRule.runOnIdle {
            assertThat(selectedLimit).isEqualTo(20)
            assertThat(strictClicks).isEqualTo(1)
        }
    }

    private fun strings() = SharedAndroidHomeTopBarStrings(
        openDrawer = "Drawer", settings = "Settings", appTheme = "Theme", recentLimit = "Recent limit",
        noLimit = "No limit", limitLabels = mapOf(10 to "10 files", 20 to "20 files", 50 to "50 files", 100 to "100 files"),
        selected = "Selected", moreOptions = "More", about = "About", multiTab = "Tabs",
        screenCaptureProtection = "Capture", externalFileBehavior = "External", strictFileFilter = "Strict filter",
        pdfFileName = "PDF filename", language = "Language", hideReaderAi = "Hide AI", showReaderAi = "Show AI",
        enabled = "Enabled", clearBookCache = "Clear books", clearReflowCache = "Clear reflow",
        testPanelDetection = "Test panel", testSpeechBubbleDetection = "Test speech", exportLogs = "Export logs",
        showDeviceManagement = "Devices", clearCloudAndLocalData = "Clear data",
    )
}
