package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.toSharedReaderFontFamily

/**
 * Compose-resources path of the bundled reader typeface for [fontFamilyName],
 * or null when the family resolves to a platform system family. The same TTF
 * set ships in `app` assets (Android native reader) and shared resources
 * (this path), so both render identical glyphs.
 */
internal fun sharedReaderBundledFontFile(fontFamilyName: String): String? = when (fontFamilyName) {
    "Merriweather" -> "files/fonts/merriweather.ttf"
    "Lora" -> "files/fonts/lora.ttf"
    "Lato" -> "files/fonts/lato.ttf"
    "Lexend" -> "files/fonts/lexend.ttf"
    "Roboto Mono" -> "files/fonts/roboto_mono.ttf"
    else -> null
}

/**
 * Loads the bundled reader typeface, or null when the family has no bundled
 * file or the platform cannot decode it. iOS serves the shared reader UI and
 * decodes the files from compose resources; the Android app uses its own
 * asset-backed reader, so its shared-UI fallback stays system fonts.
 */
internal expect suspend fun loadSharedReaderBundledFontFamily(fontFamilyName: String): FontFamily?

/**
 * Real reader typeface for the shared native EPUB surfaces (measurement and
 * rendering must share it or pagination drifts from what is drawn). Starts
 * on the system family mapping and converges to the bundled file once
 * loaded; pagination keys on the resolved family so pages re-measure.
 */
@Composable
internal fun rememberSharedReaderFontFamily(fontFamilyName: String): FontFamily {
    var loaded by remember(fontFamilyName) { mutableStateOf<FontFamily?>(null) }
    LaunchedEffect(fontFamilyName) {
        loaded = runCatching { loadSharedReaderBundledFontFamily(fontFamilyName) }.getOrNull()
    }
    return loaded ?: ReaderSettings(fontFamily = fontFamilyName).toSharedReaderFontFamily()
}
