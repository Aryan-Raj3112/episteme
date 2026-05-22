package com.aryan.reader.desktop

import com.aryan.reader.shared.SharedReaderScreenState

internal fun desktopFolderSyncCompletedState(
    state: SharedReaderScreenState,
    message: String,
    failedFolderCount: Int,
    showBanner: Boolean
): SharedReaderScreenState {
    return if (showBanner) {
        state.withBanner(message, isError = failedFolderCount > 0)
    } else {
        state
    }
}
