package com.aryan.reader.shared.ios

import com.aryan.reader.shared.SharedSettingsAction

/**
 * iOS parity scope for every action exposed by the shared settings model.
 *
 * Keep this exhaustive: adding a shared setting must require an explicit iOS parity decision.
 *
 * Comic panel and speech-bubble diagnostics intentionally remain DEBUG_ONLY on
 * iOS. Android's panel path loads `best_float16.tflite` from its external-files
 * directory in debug builds, while the paid bubble path loads the separately
 * downloaded `manga_speech_bubble_v3.ort` through ONNX Runtime. Neither model
 * nor an iOS/Core ML runtime adapter is checked into this repository, so
 * exposing these actions on iOS would claim a capability that cannot execute.
 */
internal enum class IosSettingsActionDisposition {
    SHARED_NAVIGATION,
    IMPLEMENTED_ON_IOS,
    PARITY_GAP,
    DEFERRED_CLOUD_OR_PAID,
    INTENTIONAL_PLATFORM_DIFFERENCE,
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
    SharedSettingsAction.HIDE_READER_AI,
    SharedSettingsAction.CLEAR_REFLOW_CACHE,
    SharedSettingsAction.EXPORT_LOGS,
    SharedSettingsAction.HELP_FEEDBACK,
    SharedSettingsAction.SUPPORT,
    SharedSettingsAction.ABOUT -> IosSettingsActionDisposition.IMPLEMENTED_ON_IOS

    SharedSettingsAction.SCREEN_CAPTURE_PROTECTION,
    SharedSettingsAction.CLEAR_BOOK_CACHE -> IosSettingsActionDisposition.INTENTIONAL_PLATFORM_DIFFERENCE

    SharedSettingsAction.AI_SETTINGS -> IosSettingsActionDisposition.IMPLEMENTED_ON_IOS

    SharedSettingsAction.CLOUD_SYNC -> IosSettingsActionDisposition.IMPLEMENTED_ON_IOS

    SharedSettingsAction.DEVICE_MANAGEMENT,
    SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA -> IosSettingsActionDisposition.IMPLEMENTED_ON_IOS

    SharedSettingsAction.TEST_PANEL_DETECTION,
    SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION,
    SharedSettingsAction.DEBUG_ACTIONS -> IosSettingsActionDisposition.DEBUG_ONLY
}
