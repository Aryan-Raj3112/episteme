package com.aryan.reader.shared.reader

/**
 * The persisted field name is retained for snapshot compatibility, but Android
 * stores it in `reader_pull_to_turn_enabled`. Keep the runtime meaning explicit
 * at platform boundaries so the user-facing seamless-transition switch does not
 * accidentally acquire the opposite meaning on another platform.
 */
val ReaderSettings.pullToTurnEnabled: Boolean
    get() = seamlessChapterNavigation

/** Whether the user-facing "Seamless Chapter Transition" switch is on. */
val ReaderSettings.seamlessChapterTransitionEnabled: Boolean
    get() = !pullToTurnEnabled
