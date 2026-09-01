@file:OptIn(ExperimentalMaterial3Api::class) @file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import com.aryan.reader.shared.ReaderTheme

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import com.aryan.reader.epubreader.PREF_CUSTOM_THEMES
import com.aryan.reader.epubreader.PREF_READER_THEME
import com.aryan.reader.shared.ReaderTextureFilePrefix
import com.aryan.reader.shared.pdf.PdfReverseColorMode
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File

fun saveReaderThemeId(context: Context, themeId: String) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(PREF_READER_THEME, themeId) }
}

fun loadReaderThemeId(context: Context): String {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getString(PREF_READER_THEME, "system") ?: "system"
}

const val PREF_GLOBAL_TEXTURE_TRANSPARENCY = "global_texture_transparency"

fun saveGlobalTextureTransparency(context: Context, transparency: Float) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putFloat(PREF_GLOBAL_TEXTURE_TRANSPARENCY, transparency) }
}

fun loadGlobalTextureTransparency(context: Context): Float {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getFloat(PREF_GLOBAL_TEXTURE_TRANSPARENCY, 0f)
}

fun getImportedTextures(context: Context): List<String> {
    return try {
        val dir = File(context.filesDir, READER_TEXTURE_DIR)
        if (!dir.exists()) emptyList()
        else dir.listFiles()
            ?.filter { it.isFile }
            ?.map { ReaderTextureFilePrefix + it.absolutePath }
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

const val PREF_EXCLUDE_IMAGES = "exclude_images"
const val PREF_PDF_REVERSE_COLOR_MODE = "pdf_reverse_color_mode"

fun saveExcludeImages(context: Context, excludeImages: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PREF_EXCLUDE_IMAGES, excludeImages) }
}

fun loadExcludeImages(context: Context): Boolean {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(PREF_EXCLUDE_IMAGES, false)
}

fun savePdfReverseColorMode(context: Context, mode: PdfReverseColorMode) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(PREF_PDF_REVERSE_COLOR_MODE, mode.id) }
}

fun loadPdfReverseColorMode(context: Context): PdfReverseColorMode {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return PdfReverseColorMode.fromId(prefs.getString(PREF_PDF_REVERSE_COLOR_MODE, null))
}

fun saveCustomThemes(context: Context, themes: List<ReaderTheme>) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    themes.filter { it.isCustom }.forEach { theme ->
        val obj = JSONObject().apply {
            put("id", theme.id)
            put("name", theme.name)
            put("bgColor", theme.backgroundColor.toArgb())
            put("textColor", theme.textColor.toArgb())
            put("isDark", theme.isDark)
            theme.textureId?.let { put("textureId", it) }
        }
        jsonArray.put(obj)
    }
    prefs.edit { putString(PREF_CUSTOM_THEMES, jsonArray.toString()) }
}

fun loadCustomThemes(context: Context): List<ReaderTheme> {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    val jsonString = prefs.getString(PREF_CUSTOM_THEMES, "[]") ?: "[]"
    val themes = mutableListOf<ReaderTheme>()
    try {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            themes.add(
                ReaderTheme(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    backgroundColor = Color(obj.getInt("bgColor")),
                    textColor = Color(obj.getInt("textColor")),
                    isDark = obj.getBoolean("isDark"),
                    textureId = if (obj.has("textureId")) obj.getString("textureId") else null,
                    isCustom = true
                )
            )
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse custom themes")
    }
    return themes
}
