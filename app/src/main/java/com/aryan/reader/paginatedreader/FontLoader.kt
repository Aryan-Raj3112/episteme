/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
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
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.paginatedreader

import timber.log.Timber
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.aryan.reader.ReaderFontDiagnosticsTag
import com.aryan.reader.readerFontDiagnosticSummary
import com.aryan.reader.shared.supportsVariableWeightAxis
import java.io.File
import java.security.MessageDigest

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
    val expandedFontFaces = expandFontFacesWithSiblings(fontFaces, extractionPath)
    Timber.tag(ReaderFontDiagnosticsTag).i(
        "native.load.start inputCount=${fontFaces.size} expandedCount=${expandedFontFaces.size} extractionPath='$extractionPath'"
    )
    Timber.d("Loading ${expandedFontFaces.size} font faces from extraction path: $extractionPath")

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

    val fontsByFamily = expandedFontFaces.groupBy {
        it.fontFamily.trim().removeSurrounding("'").removeSurrounding("\"").lowercase()
    }
    Timber.tag(ReaderFontDiagnosticsTag).i(
        "native.load.grouped families=${
            fontsByFamily.mapValues { (_, infos) ->
                infos.joinToString { "${it.src}:${it.fontWeight}:${it.fontStyle}" }
            }
        }"
    )
    Timber.d("Grouped font faces by normalized family: ${fontsByFamily.keys}")

    return fontsByFamily.mapValues { (familyName, fontInfos) ->
        val seenVariants = mutableSetOf<String>()
        val fontList = fontInfos.flatMap { fontInfo ->
            try {
                Timber.d("Attempting to load font '$familyName' from resolved src path: '${fontInfo.src}'")
                var fontFile = File(fontInfo.src).let { source ->
                    if (source.isAbsolute) source else File(extractionPath, fontInfo.src)
                }

                if (!fontFile.exists()) {
                    Timber.tag(ReaderFontDiagnosticsTag).w(
                        "native.load.missing family='$familyName' src='${fontInfo.src}' resolved='${fontFile.absolutePath}'"
                    )
                    Timber.w("Font file not found at: ${fontFile.absolutePath}")
                    return@flatMap emptyList()
                }
                val sourceName = fontFile.nameWithoutExtension
                Timber.tag(ReaderFontDiagnosticsTag).i(
                    "native.load.candidate family='$familyName' src='${fontInfo.src}' file='${fontFile.name}' " +
                        readerFontDiagnosticSummary(sourceName)
                )

                // Handle WOFF2 conversion and global caching
                if (fontFile.extension.equals("woff2", ignoreCase = true)) {
                    // 3. Generate a unique, deterministic cache key for the font.
                    val cacheKey = getCacheKeyForFont(bookId, fontInfo.src)
                    val cachedTtfFile = File(fontCacheDir, cacheKey)

                    if (cachedTtfFile.exists()) {
                        // Use the globally cached TTF file if it exists
                        fontFile = cachedTtfFile
                        Timber.tag(ReaderFontDiagnosticsTag).i(
                            "native.load.woff2CacheHit src='${fontInfo.src}' cached='${cachedTtfFile.absolutePath}'"
                        )
                        Timber.d("Using globally cached TTF for '${fontInfo.src}'")
                    } else {
                        // Convert and save the TTF to the global cache if it doesn't exist
                        Timber.tag(ReaderFontDiagnosticsTag).i(
                            "native.load.woff2Convert src='${fontInfo.src}' source='${fontFile.absolutePath}'"
                        )
                        Timber.d("Converting woff2 font: ${fontFile.name}")
                        val woff2Data = fontFile.readBytes()
                        val ttfData = Woff2Converter.convertWoff2ToTtf(woff2Data)

                        if (ttfData != null) {
                            cachedTtfFile.writeBytes(ttfData)
                            fontFile = cachedTtfFile // Use the newly created TTF file
                            Timber.tag(ReaderFontDiagnosticsTag).i(
                                "native.load.woff2Converted src='${fontInfo.src}' cached='${cachedTtfFile.absolutePath}' bytes=${cachedTtfFile.length()}"
                            )
                            Timber.d("Successfully converted and globally cached woff2 as '${cachedTtfFile.name}'")
                        } else {
                            Timber.tag(ReaderFontDiagnosticsTag).e(
                                "native.load.woff2ConvertFailed src='${fontInfo.src}' source='${fontFile.absolutePath}'"
                            )
                            Timber.e("Failed to convert woff2 font: ${fontFile.name}")
                            return@flatMap emptyList()
                        }
                    }
                }

                val weights = if (sourceName.supportsVariableWeightAxis()) {
                    variableEpubFontWeights
                } else {
                    listOf(fontInfo.fontWeight ?: FontWeight.Normal)
                }
                Timber.tag(ReaderFontDiagnosticsTag).i(
                    "native.load.registerPlan family='$familyName' file='${fontFile.name}' " +
                        "style=${fontInfo.fontStyle ?: FontStyle.Normal} weights=${weights.joinToString { it.weight.toString() }}"
                )
                weights.mapNotNull { weight ->
                    val style = fontInfo.fontStyle ?: FontStyle.Normal
                    if (seenVariants.add("${weight.weight}|$style")) {
                        Font(fontFile, weight, style)
                    } else {
                        Timber.tag(ReaderFontDiagnosticsTag).i(
                            "native.load.skipDuplicate family='$familyName' file='${fontFile.name}' weight=${weight.weight} style=$style"
                        )
                        null
                    }
                }
            } catch (e: Exception) {
                Timber.tag(ReaderFontDiagnosticsTag).e(e, "native.load.failed family='$familyName' src='${fontInfo.src}'")
                Timber.e(e, "Error loading font: ${fontInfo.src}")
                emptyList()
            }
        }

        if (fontList.isNotEmpty()) {
            Timber.tag(ReaderFontDiagnosticsTag).i(
                "native.load.loaded family='$familyName' registeredVariants=${seenVariants.joinToString()}"
            )
            Timber.d("Loaded family '$familyName' with ${fontList.size} font styles.")
            FontFamily(fontList)
        } else {
            Timber.tag(ReaderFontDiagnosticsTag).w("native.load.empty family='$familyName'")
            Timber.w("Could not load any font styles for family '$familyName'.")
            null
        }
    }.filterValues { it != null }.mapValues { it.value!! }
}

private val variableEpubFontWeights = listOf(
    FontWeight.Thin,
    FontWeight.ExtraLight,
    FontWeight.Light,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
    FontWeight.Black
)
