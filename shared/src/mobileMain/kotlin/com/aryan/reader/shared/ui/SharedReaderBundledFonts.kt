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
 * Loads an imported custom font file into a reader [FontFamily], or null when
 * the file is missing/undecodable. Android's native reader does this with
 * `Font(File, ...)` (`getComposeFontFamily`); the shared native EPUB surfaces
 * need the same file so measurement and rendering share the real typeface.
 */
internal expect suspend fun loadSharedReaderCustomFontFamily(fontPath: String): FontFamily?

/**
 * Real reader typeface for the shared native EPUB surfaces (measurement and
 * rendering must share it or pagination drifts from what is drawn). Prefers an
 * imported custom font file when selected, then the bundled file, then the
 * system mapping; pagination keys on the resolved family so pages re-measure.
 */
@Composable
internal fun rememberSharedReaderFontFamily(
    fontFamilyName: String,
    customFontPath: String? = null,
): FontFamily {
    var loaded by remember(fontFamilyName, customFontPath) { mutableStateOf<FontFamily?>(null) }
    LaunchedEffect(fontFamilyName, customFontPath) {
        loaded = runCatching {
            val custom = customFontPath?.takeIf { it.isNotBlank() }
                ?.let { loadSharedReaderCustomFontFamily(it) }
            custom ?: loadSharedReaderBundledFontFamily(fontFamilyName)
        }.getOrNull()
    }
    return loaded ?: ReaderSettings(fontFamily = fontFamilyName).toSharedReaderFontFamily()
}
