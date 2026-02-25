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
package com.aryan.reader.pdf.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.pdf.InkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import androidx.core.graphics.toColorInt
import androidx.core.content.edit

@Serializable
data class ToolConfig(
    val colorArgb: Int,
    val thickness: Float
)

@Serializable
data class TextStyleConfig(
    val colorArgb: Int = android.graphics.Color.BLACK,
    val backgroundColorArgb: Int = android.graphics.Color.TRANSPARENT,
    val fontSize: Float = 16f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikeThrough: Boolean = false,
    val fontPath: String? = null,
    val fontName: String? = null
)

@Serializable
data class AnnotationToolSettings(
    val selectedToolName: String = "PEN",
    val lastActivePenType: String = "PEN",
    val toolConfigs: Map<String, ToolConfig> = emptyMap(),
    val penPaletteArgb: List<Int> = listOf(
        android.graphics.Color.BLACK,
        android.graphics.Color.RED,
        android.graphics.Color.BLUE,
        android.graphics.Color.rgb(76, 175, 80),
        android.graphics.Color.WHITE
    ),
    val highlighterPaletteArgb: List<Int> = listOf(
        "#8CFF9800".toColorInt(), // Orange (Default)
        "#8CFFEB3B".toColorInt(), // Yellow
        "#8C81C784".toColorInt(), // Green
        "#8C64B5F6".toColorInt(), // Blue
        "#8CE1BEE7".toColorInt(), // Purple
    ),
    val textStyle: TextStyleConfig = TextStyleConfig()
) {
    fun getActiveTool(): InkType = try {
        InkType.valueOf(selectedToolName)
    } catch (_: Exception) {
        InkType.PEN
    }

    fun getLastPenTool(): InkType = try {
        InkType.valueOf(lastActivePenType)
    } catch (_: Exception) {
        InkType.PEN
    }

    fun getToolColor(type: InkType): Color {
        val config = toolConfigs[type.name] ?: AnnotationSettingsRepository.getDefaultConfig(type)
        return Color(config.colorArgb)
    }

    fun getToolThickness(type: InkType): Float {
        val config = toolConfigs[type.name] ?: AnnotationSettingsRepository.getDefaultConfig(type)
        return config.thickness
    }

    fun getPenPalette(): List<Color> = penPaletteArgb.map { Color(it) }
    fun getHighlighterPalette(): List<Color> = highlighterPaletteArgb.map { Color(it) }
}

class AnnotationSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("annotation_settings_global", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val keySettings = "tool_settings_v4_defaults"

    private val _settings = MutableStateFlow(loadSettings())
    val settings = _settings.asStateFlow()

    companion object {
        fun getDefaultConfig(type: InkType): ToolConfig {
            return when (type) {
                InkType.PEN -> ToolConfig(android.graphics.Color.RED, 0.008f)
                InkType.FOUNTAIN_PEN -> ToolConfig(android.graphics.Color.BLUE, 0.008f)
                InkType.PENCIL -> ToolConfig(android.graphics.Color.DKGRAY, 0.008f)
                InkType.HIGHLIGHTER -> ToolConfig("#8CFF9800".toColorInt(), 0.035f)
                InkType.HIGHLIGHTER_ROUND -> ToolConfig("#8CFFEB3B".toColorInt(), 0.035f)
                InkType.ERASER -> ToolConfig(android.graphics.Color.WHITE, 0.03f)
                InkType.TEXT -> ToolConfig(android.graphics.Color.BLACK, 0.02f)
            }
        }
    }

    private fun loadSettings(): AnnotationToolSettings {
        val jsonString = prefs.getString(keySettings, null)
        return if (jsonString != null) {
            try {
                json.decodeFromString(jsonString)
            } catch (e: Exception) {
                Timber.e(e, "Failed to decode annotation settings")
                AnnotationToolSettings()
            }
        } else {
            AnnotationToolSettings()
        }
    }

    private fun saveSettings(newSettings: AnnotationToolSettings) {
        _settings.update { newSettings }
        scope.launch {
            val jsonString = json.encodeToString(newSettings)
            prefs.edit { putString(keySettings, jsonString) }
        }
    }

    fun updateSelectedTool(tool: InkType) {
        var currentSettings = _settings.value.copy(selectedToolName = tool.name)

        if (tool == InkType.PEN || tool == InkType.FOUNTAIN_PEN || tool == InkType.PENCIL) {
            currentSettings = currentSettings.copy(lastActivePenType = tool.name)
        }

        saveSettings(currentSettings)
    }

    fun updateToolColor(tool: InkType, color: Color) {
        val currentMap = _settings.value.toolConfigs.toMutableMap()
        val currentConfig = currentMap[tool.name] ?: getDefaultConfig(tool)
        currentMap[tool.name] = currentConfig.copy(colorArgb = color.toArgb())
        saveSettings(_settings.value.copy(toolConfigs = currentMap))
    }

    fun updateToolThickness(tool: InkType, thickness: Float) {
        val currentMap = _settings.value.toolConfigs.toMutableMap()
        val currentConfig = currentMap[tool.name] ?: getDefaultConfig(tool)
        currentMap[tool.name] = currentConfig.copy(thickness = thickness)
        saveSettings(_settings.value.copy(toolConfigs = currentMap))
    }

    fun updatePenPalette(colors: List<Color>) {
        saveSettings(_settings.value.copy(penPaletteArgb = colors.map { it.toArgb() }))
    }

    fun updateHighlighterPalette(colors: List<Color>) {
        saveSettings(_settings.value.copy(highlighterPaletteArgb = colors.map { it.toArgb() }))
    }

    fun updateTextStyle(style: TextStyleConfig) {
        saveSettings(_settings.value.copy(textStyle = style))
    }
}