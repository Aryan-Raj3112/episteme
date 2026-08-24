package com.aryan.reader.shared.ui

/**
 * Semantic actions exposed from the Home overflow menu.
 *
 * The menu is intentionally capability-driven.  Android has a few actions
 * that rely on Android-only services, while iOS can expose the portable
 * Reader AI, reflow-cache, and diagnostic-log actions through its own
 * adapters.  Keeping the ordering and gating here prevents the two menus
 * from drifting as platform capabilities change.
 */
enum class SharedMobileHomeOverflowAction {
    ABOUT,
    TABS_TOGGLE,
    SCREEN_CAPTURE_PROTECTION,
    EXTERNAL_FILE_BEHAVIOR,
    STRICT_FILE_FILTER,
    PDF_FILENAME_DISPLAY_NAME,
    LANGUAGE,
    TOGGLE_READER_AI,
    CLEAR_BOOK_CACHE,
    CLEAR_REFLOW_CACHE,
    TEST_PANEL_DETECTION,
    TEST_SPEECH_BUBBLE_DETECTION,
    EXPORT_LOGS,
    DEVICE_MANAGEMENT,
    CLEAR_CLOUD_LOCAL_DATA,
}

/** Sections are used by platform UIs to place dividers without owning order. */
enum class SharedMobileHomeOverflowSection {
    ABOUT,
    OPTIONS,
    LANGUAGE_AND_AI,
    MAINTENANCE,
    DEBUG,
    CLOUD,
}

data class SharedMobileHomeOverflowCapabilities(
    val screenCaptureProtection: Boolean = false,
    val readerAi: Boolean = false,
    val clearBookCache: Boolean = false,
    val clearReflowCache: Boolean = false,
    val testMlDiagnostics: Boolean = false,
    val exportLogs: Boolean = false,
    val deviceManagement: Boolean = false,
    val clearCloudAndLocalData: Boolean = false,
)

data class SharedMobileHomeOverflowState(
    val tabsEnabled: Boolean,
    val screenCaptureProtectionEnabled: Boolean,
    val strictFileFilterEnabled: Boolean,
    val usePdfFileNameAsDisplayName: Boolean,
    val hideReaderAi: Boolean,
)

data class SharedMobileHomeOverflowItem(
    val action: SharedMobileHomeOverflowAction,
    val section: SharedMobileHomeOverflowSection,
    val checked: Boolean = false,
)

/**
 * Builds the Android-benchmark order while omitting actions unavailable on a
 * platform.  The returned list is presentation-neutral; Compose owns labels,
 * icons, and callbacks for each platform.
 */
fun sharedMobileHomeOverflowItems(
    state: SharedMobileHomeOverflowState,
    capabilities: SharedMobileHomeOverflowCapabilities,
): List<SharedMobileHomeOverflowItem> = buildList {
    add(
        SharedMobileHomeOverflowItem(
            action = SharedMobileHomeOverflowAction.ABOUT,
            section = SharedMobileHomeOverflowSection.ABOUT,
        )
    )
    add(
        SharedMobileHomeOverflowItem(
            action = SharedMobileHomeOverflowAction.TABS_TOGGLE,
            section = SharedMobileHomeOverflowSection.OPTIONS,
            checked = state.tabsEnabled,
        )
    )
    if (capabilities.screenCaptureProtection) {
        add(
            SharedMobileHomeOverflowItem(
                action = SharedMobileHomeOverflowAction.SCREEN_CAPTURE_PROTECTION,
                section = SharedMobileHomeOverflowSection.OPTIONS,
                checked = state.screenCaptureProtectionEnabled,
            )
        )
    }
    add(
        SharedMobileHomeOverflowItem(
            action = SharedMobileHomeOverflowAction.EXTERNAL_FILE_BEHAVIOR,
            section = SharedMobileHomeOverflowSection.OPTIONS,
        )
    )
    add(
        SharedMobileHomeOverflowItem(
            action = SharedMobileHomeOverflowAction.STRICT_FILE_FILTER,
            section = SharedMobileHomeOverflowSection.OPTIONS,
            checked = state.strictFileFilterEnabled,
        )
    )
    add(
        SharedMobileHomeOverflowItem(
            action = SharedMobileHomeOverflowAction.PDF_FILENAME_DISPLAY_NAME,
            section = SharedMobileHomeOverflowSection.OPTIONS,
            checked = state.usePdfFileNameAsDisplayName,
        )
    )
    add(
        SharedMobileHomeOverflowItem(
            action = SharedMobileHomeOverflowAction.LANGUAGE,
            section = SharedMobileHomeOverflowSection.LANGUAGE_AND_AI,
        )
    )
    if (capabilities.readerAi) {
        add(
            SharedMobileHomeOverflowItem(
                action = SharedMobileHomeOverflowAction.TOGGLE_READER_AI,
                section = SharedMobileHomeOverflowSection.LANGUAGE_AND_AI,
                checked = state.hideReaderAi,
            )
        )
    }
    if (capabilities.clearBookCache) {
        add(
            SharedMobileHomeOverflowItem(
                action = SharedMobileHomeOverflowAction.CLEAR_BOOK_CACHE,
                section = SharedMobileHomeOverflowSection.MAINTENANCE,
            )
        )
    }
    if (capabilities.clearReflowCache) {
        add(
            SharedMobileHomeOverflowItem(
                action = SharedMobileHomeOverflowAction.CLEAR_REFLOW_CACHE,
                section = SharedMobileHomeOverflowSection.MAINTENANCE,
            )
        )
    }
    if (capabilities.testMlDiagnostics) {
        add(
            SharedMobileHomeOverflowItem(
                action = SharedMobileHomeOverflowAction.TEST_PANEL_DETECTION,
                section = SharedMobileHomeOverflowSection.DEBUG,
            )
        )
        add(
            SharedMobileHomeOverflowItem(
                action = SharedMobileHomeOverflowAction.TEST_SPEECH_BUBBLE_DETECTION,
                section = SharedMobileHomeOverflowSection.DEBUG,
            )
        )
    }
    if (capabilities.exportLogs) {
        add(
            SharedMobileHomeOverflowItem(
                action = SharedMobileHomeOverflowAction.EXPORT_LOGS,
                section = SharedMobileHomeOverflowSection.DEBUG,
            )
        )
    }
    if (capabilities.deviceManagement) {
        add(
            SharedMobileHomeOverflowItem(
                action = SharedMobileHomeOverflowAction.DEVICE_MANAGEMENT,
                section = SharedMobileHomeOverflowSection.CLOUD,
            )
        )
    }
    if (capabilities.clearCloudAndLocalData) {
        add(
            SharedMobileHomeOverflowItem(
                action = SharedMobileHomeOverflowAction.CLEAR_CLOUD_LOCAL_DATA,
                section = SharedMobileHomeOverflowSection.CLOUD,
            )
        )
    }
}
