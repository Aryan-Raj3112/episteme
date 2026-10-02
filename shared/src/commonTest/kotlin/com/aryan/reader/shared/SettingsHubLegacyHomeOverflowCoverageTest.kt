package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The standalone Home screen's overflow menu was retired along with the bottom navigation
 * bar. Every action it offered must remain reachable, so this maps each legacy overflow
 * action to the settings action that replaced it and asserts the Extra page lists them all.
 */
class SettingsHubLegacyHomeOverflowCoverageTest {

    private val legacyHomeOverflowToSettingsAction = mapOf(
        "ABOUT" to SharedSettingsAction.ABOUT,
        "TABS_TOGGLE" to SharedSettingsAction.TABS_TOGGLE,
        "SCREEN_CAPTURE_PROTECTION" to SharedSettingsAction.SCREEN_CAPTURE_PROTECTION,
        "EXTERNAL_FILE_BEHAVIOR" to SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR,
        "STRICT_FILE_FILTER" to SharedSettingsAction.STRICT_FILE_FILTER,
        "PDF_FILENAME_DISPLAY_NAME" to SharedSettingsAction.PDF_FILENAME_DISPLAY_NAME,
        "LANGUAGE" to SharedSettingsAction.LANGUAGE,
        "TOGGLE_READER_AI" to SharedSettingsAction.HIDE_READER_AI,
        "CLEAR_BOOK_CACHE" to SharedSettingsAction.CLEAR_BOOK_CACHE,
        "CLEAR_REFLOW_CACHE" to SharedSettingsAction.CLEAR_REFLOW_CACHE,
        "TEST_PANEL_DETECTION" to SharedSettingsAction.TEST_PANEL_DETECTION,
        "TEST_SPEECH_BUBBLE_DETECTION" to SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION,
        "EXPORT_LOGS" to SharedSettingsAction.EXPORT_LOGS,
        "SHOW_FPS_OVERLAY" to SharedSettingsAction.FPS_OVERLAY,
        "DEVICE_MANAGEMENT" to SharedSettingsAction.DEVICE_MANAGEMENT,
        "CLEAR_CLOUD_LOCAL_DATA" to SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA,
    )

    private fun debugHub() = sharedSettingsHubModel(
        SharedSettingsHubInput(
            platform = SharedSettingsPlatform.ANDROID,
            isDebugBuild = true,
            includeScreenCaptureProtection = true,
            includePdfFileNameDisplayName = true,
            includeCloudLocalDataClear = true,
            includeDiagnosticLogExport = true,
            includeFpsOverlayToggle = true,
            fpsOverlayEnabled = true,
            syncAvailable = true,
            isSignedIn = true,
        )
    )

    @Test
    fun `every action the retired home overflow owned is still reachable`() {
        // Deliberately not pinned to the Extra page: Help & About is now its own root category,
        // so About lives there. What must hold is the original promise — none of these actions
        // became unreachable when their row moved.
        val reachable = debugHub().rootCategories
            .flatMap { debugHub().page(it.destination).items }
            .mapNotNull { it.action }
            .toSet()

        val missing = legacyHomeOverflowToSettingsAction.values.filterNot { it in reachable }
        assertEquals(
            emptyList(),
            missing,
            "every retired Home overflow action must remain reachable somewhere in Settings",
        )
    }

    @Test
    fun `fps overlay toggle reports its current state and is debug only`() {
        val enabled = debugHub().page(SharedSettingsDestination.EXTRA)
            .items
            .single { it.action == SharedSettingsAction.FPS_OVERLAY }
        assertEquals(true, enabled.checked)

        val release = sharedSettingsHubModel(
            SharedSettingsHubInput(platform = SharedSettingsPlatform.ANDROID, isDebugBuild = false)
        ).page(SharedSettingsDestination.EXTRA).items.mapNotNull { it.action }
        assertTrue(
            SharedSettingsAction.FPS_OVERLAY !in release,
            "The FPS overlay is a debug diagnostic and must not ship in release builds",
        )
    }

    @Test
    fun `device management moved onto the extra page`() {
        val extraActions = debugHub().page(SharedSettingsDestination.EXTRA)
            .items
            .mapNotNull { it.action }
        assertTrue(
            SharedSettingsAction.DEVICE_MANAGEMENT in extraActions,
            "Device management was only reachable from the retired Home overflow",
        )
    }
}