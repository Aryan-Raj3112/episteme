package com.aryan.reader.shared.ios

import com.aryan.reader.shared.SharedSettingsAction

/**
 * iOS parity scope for every action exposed by the shared settings model.
 *
 * Keep this exhaustive: adding a shared setting must require an explicit iOS parity decision.
 */
internal enum class IosSettingsActionDisposition {
    SHARED_NAVIGATION,
    IMPLEMENTED_ON_IOS,
    PARITY_GAP,
    DEFERRED_CLOUD_OR_PAID,
    DEBUG_ONLY,
}

internal fun SharedSettingsAction.iosDisposition(): IosSettingsActionDisposition = when (this) {
    SharedSettingsAction.TEXT_READER_DEFAULTS,
    SharedSettingsAction.PDF_READER_DEFAULTS,
    SharedSettingsAction.READER_TOOLBAR,
    SharedSettingsAction.TTS_REPLACEMENTS,
    SharedSettingsAction.LOCAL_OVERRIDE_NOTE -> IosSettingsActionDisposition.SHARED_NAVIGATION

    SharedSettingsAction.APP_THEME,
    SharedSettingsAction.LANGUAGE,
    SharedSettingsAction.TABS_TOGGLE,
    SharedSettingsAction.RECENT_LIMIT,
    SharedSettingsAction.STRICT_FILE_FILTER,
    SharedSettingsAction.PDF_FILENAME_DISPLAY_NAME,
    SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR,
    SharedSettingsAction.CUSTOM_FONTS,
    SharedSettingsAction.SIGN_IN,
    SharedSettingsAction.SIGN_OUT,
    SharedSettingsAction.FOLDER_SYNC,
    SharedSettingsAction.TTS_SETTINGS,
    SharedSettingsAction.CLEAR_REFLOW_CACHE,
    SharedSettingsAction.HELP_FEEDBACK,
    SharedSettingsAction.SUPPORT,
    SharedSettingsAction.ABOUT -> IosSettingsActionDisposition.IMPLEMENTED_ON_IOS

    SharedSettingsAction.SCREEN_CAPTURE_PROTECTION,
    SharedSettingsAction.HIDE_READER_AI,
    SharedSettingsAction.CLEAR_BOOK_CACHE -> IosSettingsActionDisposition.PARITY_GAP

    SharedSettingsAction.CLOUD_SYNC,
    SharedSettingsAction.DEVICE_MANAGEMENT,
    SharedSettingsAction.AI_SETTINGS,
    SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA -> IosSettingsActionDisposition.DEFERRED_CLOUD_OR_PAID

    SharedSettingsAction.TEST_PANEL_DETECTION,
    SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION,
    SharedSettingsAction.EXPORT_LOGS,
    SharedSettingsAction.DEBUG_ACTIONS -> IosSettingsActionDisposition.DEBUG_ONLY
}
