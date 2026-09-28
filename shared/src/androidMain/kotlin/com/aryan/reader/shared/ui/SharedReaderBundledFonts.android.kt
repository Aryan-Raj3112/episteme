package com.aryan.reader.shared.ui

import androidx.compose.ui.text.font.FontFamily

internal actual suspend fun loadSharedReaderBundledFontFamily(fontFamilyName: String): FontFamily? {
    // The Android app renders through its own asset-backed reader surfaces,
    // so the shared-UI fallback (system families) is unchanged here. The
    // shared reader UI is served on iOS, which decodes the bundled files.
    return null
}
