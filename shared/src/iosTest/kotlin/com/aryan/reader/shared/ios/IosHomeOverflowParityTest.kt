package com.aryan.reader.shared.ios

import com.aryan.reader.shared.ui.SharedMobileHomeOverflowAction
import com.aryan.reader.shared.ui.SharedMobileHomeOverflowCapabilities
import com.aryan.reader.shared.ui.SharedMobileHomeOverflowState
import com.aryan.reader.shared.ui.sharedMobileHomeOverflowItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class IosHomeOverflowParityTest {
    @Test
    fun `ios home overflow exposes portable Android actions`() {
        val actions = sharedMobileHomeOverflowItems(
            state = SharedMobileHomeOverflowState(
                tabsEnabled = false,
                screenCaptureProtectionEnabled = false,
                strictFileFilterEnabled = false,
                usePdfFileNameAsDisplayName = false,
                hideReaderAi = false,
            ),
            capabilities = SharedMobileHomeOverflowCapabilities(
                readerAi = true,
                clearReflowCache = true,
                exportLogs = true,
            ),
        ).map { it.action }

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
            actions,
        )
        assertFalse(SharedMobileHomeOverflowAction.SCREEN_CAPTURE_PROTECTION in actions)
        assertFalse(SharedMobileHomeOverflowAction.CLEAR_BOOK_CACHE in actions)
        assertFalse(SharedMobileHomeOverflowAction.TEST_PANEL_DETECTION in actions)
        assertFalse(SharedMobileHomeOverflowAction.TEST_SPEECH_BUBBLE_DETECTION in actions)
    }
}
