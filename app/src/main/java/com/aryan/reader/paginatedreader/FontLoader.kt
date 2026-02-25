/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.paginatedreader

import timber.log.Timber
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.security.MessageDigest

/**
 * A centralized mapper for handling conversions between generic CSS font family names
 * and Compose's FontFamily objects.
 */
object FontFamilyMapper {
    private val genericFontMap = mapOf(
        "serif" to FontFamily.Serif,
        "sans-serif" to FontFamily.SansSerif,
        "monospace" to FontFamily.Monospace,
        "cursive" to FontFamily.Cursive,
        "default" to FontFamily.Default,
        "system-ui" to FontFamily.Default,
        "ui-sans-serif" to FontFamily.Default,
        "ui-serif" to FontFamily.Default,
        "ui-monospace" to FontFamily.Default,
        "ui-rounded" to FontFamily.Default
    )

    /**
     * Converts a string name (e.g., "serif") to a Compose [FontFamily].
     */
    fun nameToFontFamily(name: String): FontFamily? {
        return genericFontMap[name.trim().lowercase()]
    }

    /**
     * Converts a Compose [FontFamily] back to its primary string name for serialization.
     * Custom fonts are not serialized by name and will return null.
     */
    fun fontFamilyToName(fontFamily: FontFamily): String? {
        return when (fontFamily) {
            FontFamily.Serif -> "serif"
            FontFamily.SansSerif -> "sans-serif"
            FontFamily.Monospace -> "monospace"
            FontFamily.Cursive -> "cursive"
            FontFamily.Default -> "default"
            else -> null
        }
    }
}

private fun getCacheKeyForFont(bookId: String, fontPath: String): String {
    val identifier = "$bookId:$fontPath"
    val digest = MessageDigest.getInstance("MD5").digest(identifier.toByteArray())
    return digest.joinToString("") { "%02x".format(it) } + ".ttf"
}

/**
 * Loads custom font faces defined in the EPUB's CSS into a map of [FontFamily] objects.
 * It handles WOFF2 fonts by converting them to TTF and storing them in a global, persistent cache.
 */
fun loadFontFamilies(fontFaces: List<FontFaceInfo>, extractionPath: String): Map<String, FontFamily> {
    if (fontFaces.isEmpty()) {
        return emptyMap()
    }
    Timber.d("Loading ${fontFaces.size} font faces from extraction path: $extractionPath")

    // 1. Define a stable, global font cache directory.
    // This assumes the parent of the extraction path is a stable base directory for epubs.
    val baseCacheDir = File(extractionPath).parentFile ?: return emptyMap()
    val fontCacheDir = File(baseCacheDir, "font_cache")
    if (!fontCacheDir.exists()) {
        fontCacheDir.mkdirs()
    }

    // 2. Get a stable identifier for the book from the extraction path.
    // e.g., "d0e205bf-65cc-4ab4-93cc-cd2d613a7bb3.epub" from a longer temp path.
    val bookId = File(extractionPath).name.substringBeforeLast("_")

    val fontsByFamily = fontFaces.groupBy {
        it.fontFamily.trim().removeSurrounding("'").removeSurrounding("\"").lowercase()
    }
    Timber.d("Grouped font faces by normalized family: ${fontsByFamily.keys}")

    return fontsByFamily.mapValues { (familyName, fontInfos) ->
        val fontList = fontInfos.mapNotNull { fontInfo ->
            try {
                Timber.d("Attempting to load font '$familyName' from resolved src path: '${fontInfo.src}'")
                var fontFile = File(extractionPath, fontInfo.src)

                if (!fontFile.exists()) {
                    Timber.w("Font file not found at: ${fontFile.absolutePath}")
                    return@mapNotNull null
                }

                // Handle WOFF2 conversion and global caching
                if (fontFile.extension.equals("woff2", ignoreCase = true)) {
                    // 3. Generate a unique, deterministic cache key for the font.
                    val cacheKey = getCacheKeyForFont(bookId, fontInfo.src)
                    val cachedTtfFile = File(fontCacheDir, cacheKey)

                    if (cachedTtfFile.exists()) {
                        // Use the globally cached TTF file if it exists
                        fontFile = cachedTtfFile
                        Timber.d("Using globally cached TTF for '${fontInfo.src}'")
                    } else {
                        // Convert and save the TTF to the global cache if it doesn't exist
                        Timber.d("Converting woff2 font: ${fontFile.name}")
                        val woff2Data = fontFile.readBytes()
                        val ttfData = Woff2Converter.convertWoff2ToTtf(woff2Data)

                        if (ttfData != null) {
                            cachedTtfFile.writeBytes(ttfData)
                            fontFile = cachedTtfFile // Use the newly created TTF file
                            Timber.d("Successfully converted and globally cached woff2 as '${cachedTtfFile.name}'")
                        } else {
                            Timber.e("Failed to convert woff2 font: ${fontFile.name}")
                            return@mapNotNull null
                        }
                    }
                }

                Font(
                    fontFile,
                    fontInfo.fontWeight ?: FontWeight.Normal,
                    fontInfo.fontStyle ?: FontStyle.Normal
                )
            } catch (e: Exception) {
                Timber.e(e, "Error loading font: ${fontInfo.src}")
                null
            }
        }

        if (fontList.isNotEmpty()) {
            Timber.d("Loaded family '$familyName' with ${fontList.size} font styles.")
            FontFamily(fontList)
        } else {
            Timber.w("Could not load any font styles for family '$familyName'.")
            null
        }
    }.filterValues { it != null }.mapValues { it.value!! }
}