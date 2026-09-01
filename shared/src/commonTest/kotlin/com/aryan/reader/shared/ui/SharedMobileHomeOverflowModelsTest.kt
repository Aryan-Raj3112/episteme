package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedMobileHomeOverflowModelsTest {
    @Test
    fun `full capability set keeps Android benchmark order`() {
        val items = sharedMobileHomeOverflowItems(
            state = SharedMobileHomeOverflowState(
                tabsEnabled = true,
                screenCaptureProtectionEnabled = true,
                strictFileFilterEnabled = false,
                usePdfFileNameAsDisplayName = true,
                hideReaderAi = false,
            ),
            capabilities = SharedMobileHomeOverflowCapabilities(
                screenCaptureProtection = true,
                readerAi = true,
                clearBookCache = true,
                clearReflowCache = true,
                testMlDiagnostics = true,
                exportLogs = true,
                deviceManagement = true,
                clearCloudAndLocalData = true,
            ),
        )

        assertEquals(
            listOf(
                SharedMobileHomeOverflowAction.ABOUT,
                SharedMobileHomeOverflowAction.TABS_TOGGLE,
                SharedMobileHomeOverflowAction.SCREEN_CAPTURE_PROTECTION,
                SharedMobileHomeOverflowAction.EXTERNAL_FILE_BEHAVIOR,
                SharedMobileHomeOverflowAction.STRICT_FILE_FILTER,
                SharedMobileHomeOverflowAction.PDF_FILENAME_DISPLAY_NAME,
                SharedMobileHomeOverflowAction.LANGUAGE,
                SharedMobileHomeOverflowAction.TOGGLE_READER_AI,
                SharedMobileHomeOverflowAction.CLEAR_BOOK_CACHE,
                SharedMobileHomeOverflowAction.CLEAR_REFLOW_CACHE,
                SharedMobileHomeOverflowAction.TEST_PANEL_DETECTION,
                SharedMobileHomeOverflowAction.TEST_SPEECH_BUBBLE_DETECTION,
                SharedMobileHomeOverflowAction.EXPORT_LOGS,
                SharedMobileHomeOverflowAction.DEVICE_MANAGEMENT,
                SharedMobileHomeOverflowAction.CLEAR_CLOUD_LOCAL_DATA,
            ),
            items.map { it.action },
        )
        assertTrue(items.first { it.action == SharedMobileHomeOverflowAction.TABS_TOGGLE }.checked)
        assertTrue(items.first { it.action == SharedMobileHomeOverflowAction.SCREEN_CAPTURE_PROTECTION }.checked)
        assertTrue(items.first { it.action == SharedMobileHomeOverflowAction.PDF_FILENAME_DISPLAY_NAME }.checked)
        assertFalse(items.first { it.action == SharedMobileHomeOverflowAction.TOGGLE_READER_AI }.checked)
    }

    @Test
    fun `ios portable capabilities omit Android-only maintenance and diagnostics`() {
        val items = sharedMobileHomeOverflowItems(
            state = SharedMobileHomeOverflowState(
                tabsEnabled = false,
                screenCaptureProtectionEnabled = false,
                strictFileFilterEnabled = false,
                usePdfFileNameAsDisplayName = false,
                hideReaderAi = true,
            ),
            capabilities = SharedMobileHomeOverflowCapabilities(
                readerAi = true,
                clearReflowCache = true,
                exportLogs = true,
            ),
        )

        assertEquals(
            listOf(
                SharedMobileHomeOverflowAction.ABOUT,
                SharedMobileHomeOverflowAction.TABS_TOGGLE,
                SharedMobileHomeOverflowAction.EXTERNAL_FILE_BEHAVIOR,
                SharedMobileHomeOverflowAction.STRICT_FILE_FILTER,
                SharedMobileHomeOverflowAction.PDF_FILENAME_DISPLAY_NAME,
                SharedMobileHomeOverflowAction.LANGUAGE,
                SharedMobileHomeOverflowAction.TOGGLE_READER_AI,
                SharedMobileHomeOverflowAction.CLEAR_REFLOW_CACHE,
                SharedMobileHomeOverflowAction.EXPORT_LOGS,
            ),
            items.map { it.action },
        )
        assertTrue(items.first { it.action == SharedMobileHomeOverflowAction.TOGGLE_READER_AI }.checked)
        assertFalse(SharedMobileHomeOverflowAction.SCREEN_CAPTURE_PROTECTION in items.map { it.action })
        assertFalse(SharedMobileHomeOverflowAction.CLEAR_BOOK_CACHE in items.map { it.action })
        assertFalse(SharedMobileHomeOverflowAction.TEST_PANEL_DETECTION in items.map { it.action })
        assertFalse(SharedMobileHomeOverflowAction.TEST_SPEECH_BUBBLE_DETECTION in items.map { it.action })
    }
}
