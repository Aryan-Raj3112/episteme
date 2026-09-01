package com.aryan.reader.shared.pdf

import androidx.compose.ui.text.font.FontFamily
import com.aryan.reader.shared.CustomFontItem

/**
 * Resolves imported font files for the mobile PDF annotation surface.
 *
 * The file format and loading mechanism are platform-specific.  Returning only
 * successfully loaded families is deliberate: a font that is present in the
 * library but cannot be decoded by the platform must not be offered as a
 * selectable annotation font and then silently fall back at render time.
 */
internal expect fun loadSharedPdfCustomFontFamilies(
    customFonts: List<CustomFontItem>,
): Map<String, FontFamily>
