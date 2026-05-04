package com.aryan.reader.pdf

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import com.aryan.reader.BuildConfig
import com.aryan.reader.ReaderTheme
import com.aryan.reader.ReaderTexture
import com.aryan.reader.epubreader.SystemUiMode

internal const val VERTICAL_SCROLL_TAG = "PdfVerticalScroll"
internal const val SETTINGS_PREFS_NAME = "epub_reader_settings"
internal const val TTS_MODE_KEY = "tts_mode"
internal const val DISPLAY_MODE_KEY = "pdf_display_mode"
internal const val PDF_DARK_MODE_KEY = "pdf_dark_mode"
internal const val OCR_LANGUAGE_KEY = "ocr_language_key"
internal const val OCR_LANGUAGE_SELECTED_KEY = "ocr_language_selected_key"
internal const val DOCK_LOCATION_KEY = "dock_location"
internal const val DOCK_OFFSET_X_KEY = "dock_offset_x"
internal const val DOCK_OFFSET_Y_KEY = "dock_offset_y"
internal const val PDF_AUTO_SCROLL_SPEED_KEY = "pdf_auto_scroll_speed"
internal const val PDF_AUTO_SCROLL_USE_SLIDER_KEY = "pdf_auto_scroll_use_slider"
internal const val PDF_AUTO_SCROLL_MIN_SPEED_KEY = "pdf_auto_scroll_min_speed"
internal const val PDF_AUTO_SCROLL_MAX_SPEED_KEY = "pdf_auto_scroll_max_speed"
internal const val STYLUS_ONLY_MODE_KEY = "stylus_only_mode"

private const val PDF_AUTO_SCROLL_IS_LOCAL_PREFIX = "pdf_as_local_"
private const val PDF_AUTO_SCROLL_LOCAL_SPEED_PREFIX = "pdf_as_local_speed_"
private const val PDF_AUTO_SCROLL_LOCAL_MIN_PREFIX = "pdf_as_local_min_"
private const val PDF_AUTO_SCROLL_LOCAL_MAX_PREFIX = "pdf_as_local_max_"
private const val PDF_SCROLL_LOCKED_PREFIX = "pdf_sl_local_"
internal const val PDF_FULL_SCREEN_PREFIX = "pdf_fs_local_"
private const val PDF_MUSICIAN_MODE_KEY = "pdf_musician_mode_enabled"
private const val PREF_USE_ONLINE_DICT = "use_online_dictionary"
private const val PREF_EXTERNAL_DICT_PKG = "external_dictionary_package"
private const val PREF_EXTERNAL_TRANSLATE_PKG = "external_translate_package"
private const val PREF_EXTERNAL_SEARCH_PKG = "external_search_package"
private const val PDF_THEME_KEY = "pdf_reader_theme"
private const val PDF_KEEP_SCREEN_ON_KEY = "pdf_keep_screen_on_enabled"
private const val PDF_HIDDEN_TOOLS_KEY = "pdf_hidden_tools"
private const val PDF_TOOL_ORDER_KEY = "pdf_tool_order"
private const val PDF_BOTTOM_TOOLS_KEY = "pdf_bottom_tools"
private const val PDF_SYSTEM_UI_MODE_KEY = "pdf_system_ui_mode"
internal const val PDF_LAYOUT_DEBUG_TAG = "PdfLayoutDebug"

enum class PdfReaderTool(val title: String, val category: String) {
    DICTIONARY("External Apps", "Top Bar"),
    THEME("Theme Settings", "Top Bar"),
    LOCK_PANNING("Lock Panning", "Top Bar"),
    VISUAL_OPTIONS("Visual Options", "Overflow Menu"),
    TAP_TO_TURN("Tap to Turn Pages", "Overflow Menu"),
    FULL_SCREEN("Full Screen", "Top Bar"),
    SLIDER("Navigation Slider", "Bottom Bar"),
    TOC("Sidebar", "Bottom Bar"),
    SEARCH("Search", "Bottom Bar"),
    HIGHLIGHT_ALL("Highlight selectable text", "Bottom Bar"),
    AI_FEATURES("AI Features", "Bottom Bar"),
    EDIT_MODE("Edit Mode", "Bottom Bar"),
    TTS_CONTROLS("TTS Controls", "Bottom Bar"),
    OCR_LANGUAGE("OCR Language", "Overflow Menu"),
    READING_MODE("Reading Mode", "Overflow Menu"),
    KEEP_SCREEN_ON("Keep Screen On", "Overflow Menu"),
    AUTO_SCROLL("Auto Scroll", "Overflow Menu"),
    TTS_SETTINGS("TTS Voice Settings", "Overflow Menu"),
    BOOKMARK("Bookmark", "Overflow Menu"),
    PAGE_MANAGEMENT("Page Management", "Overflow Menu"),
    REFLOW("Text View (Reflow)", "Overflow Menu"),
    SHARE("Share", "Overflow Menu"),
    SAVE_COPY("Save Copy", "Overflow Menu"),
    PRINT("Print", "Overflow Menu")
}

val PdfBuiltInThemes = listOf(
    ReaderTheme("no_theme", "No Theme", Color.Unspecified, Color.Unspecified, false),
    ReaderTheme("reverse", "Reverse", Color.Black, Color.White, true),
    ReaderTheme("light", "Light", Color(0xFFFFFFFF), Color(0xFF000000), false),
    ReaderTheme("dark", "Dark", Color(0xFF121212), Color(0xFFE0E0E0), true),
    ReaderTheme("sepia", "Sepia", Color(0xFFFBF0D9), Color(0xFF5F4B32), false),
    ReaderTheme("slate", "Slate", Color(0xFF2E3440), Color(0xFFECEFF4), true),
    ReaderTheme("oled", "OLED", Color(0xFF000000), Color(0xFFB0B0B0), true),
    ReaderTheme("pdf_natural_white_texture", "Natural White", Color(0xFFF7F1E5), Color(0xFF1D1B18), false, textureId = ReaderTexture.NATURAL_WHITE.id),
    ReaderTheme("pdf_retina_texture", "Retina", Color(0xFFF1E4CD), Color(0xFF2A2119), false, textureId = ReaderTexture.RETINA_WOOD.id),
    ReaderTheme("pdf_veneer_texture", "Veneer", Color(0xFFF4E7CF), Color(0xFF2A2119), false, textureId = ReaderTexture.LIGHT_VENEER.id),
    ReaderTheme("pdf_grey_wash_texture", "Grey Wash", Color(0xFF202124), Color(0xFFFFFFFF), true, textureId = ReaderTexture.GREY_WASH.id),
    ReaderTheme("pdf_fabric_texture", "Fabric", Color(0xFF262626), Color(0xFFE8E2D8), true, textureId = ReaderTexture.CLASSY_FABRIC.id),
    ReaderTheme("pdf_retro_texture", "Retro", Color(0xFFF6ECD8), Color(0xFF2F2118), false, textureId = ReaderTexture.RETRO_INTRO.id)
)

internal fun loadPdfHiddenTools(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getStringSet(PDF_HIDDEN_TOOLS_KEY, emptySet()) ?: emptySet()
}

internal fun savePdfHiddenTools(context: Context, hiddenTools: Set<String>) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putStringSet(PDF_HIDDEN_TOOLS_KEY, hiddenTools) }
}

internal fun loadPdfToolOrder(context: Context): List<PdfReaderTool> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val savedTools = prefs.getString(PDF_TOOL_ORDER_KEY, null)
        ?.split(',')
        ?.filter { it.isNotBlank() }
        ?.mapNotNull { name -> PdfReaderTool.entries.firstOrNull { it.name == name } }
        .orEmpty()
    return (savedTools + PdfReaderTool.entries.filterNot { it in savedTools }).distinct()
}

internal fun savePdfToolOrder(context: Context, toolOrder: List<PdfReaderTool>) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(PDF_TOOL_ORDER_KEY, toolOrder.joinToString(",") { it.name }) }
}

internal fun loadPdfBottomTools(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val defaultBottomTools = PdfReaderTool.entries.filter { it.category == "Bottom Bar" }.map { it.name }.toSet()
    return prefs.getStringSet(PDF_BOTTOM_TOOLS_KEY, defaultBottomTools) ?: defaultBottomTools
}

internal fun savePdfBottomTools(context: Context, bottomTools: Set<String>) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putStringSet(PDF_BOTTOM_TOOLS_KEY, bottomTools) }
}

internal fun loadCustomHighlightColors(context: Context): Map<PdfHighlightColor, Color> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return PdfHighlightColor.entries.associateWith {
        val defaultArgb = it.color.toArgb()
        val savedArgb = prefs.getInt("custom_highlight_${it.name}", defaultArgb)
        Color(savedArgb)
    }
}

internal fun saveCustomHighlightColors(context: Context, colors: Map<PdfHighlightColor, Color>) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        colors.forEach { (colorEnum, color) ->
            putInt("custom_highlight_${colorEnum.name}", color.toArgb())
        }
    }
}

internal fun saveKeepScreenOn(context: Context, isEnabled: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_KEEP_SCREEN_ON_KEY, isEnabled) }
}

internal fun loadKeepScreenOn(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_KEEP_SCREEN_ON_KEY, false)
}

internal fun savePdfSystemUiMode(context: Context, mode: SystemUiMode) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putInt(PDF_SYSTEM_UI_MODE_KEY, mode.id) }
}

internal fun loadPdfSystemUiMode(context: Context): SystemUiMode {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val id = prefs.getInt(PDF_SYSTEM_UI_MODE_KEY, SystemUiMode.SYNC.id)
    return SystemUiMode.entries.find { it.id == id } ?: SystemUiMode.SYNC
}

internal fun savePdfThemeId(context: Context, themeId: String) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(PDF_THEME_KEY, themeId) }
}

internal fun loadPdfThemeId(context: Context): String {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(PDF_THEME_KEY, "no_theme") ?: "no_theme"
}

internal fun loadUseOnlineDict(context: Context): Boolean {
    @Suppress("KotlinConstantConditions") if (BuildConfig.FLAVOR == "oss" && BuildConfig.IS_OFFLINE) return false
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PREF_USE_ONLINE_DICT, true)
}

internal fun saveUseOnlineDict(context: Context, useOnline: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PREF_USE_ONLINE_DICT, useOnline) }
}

internal fun loadExternalDictPackage(context: Context): String? {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(PREF_EXTERNAL_DICT_PKG, null)
}

internal fun saveExternalDictPackage(context: Context, packageName: String) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(PREF_EXTERNAL_DICT_PKG, packageName) }
}

internal fun loadExternalTranslatePackage(context: Context): String? {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(PREF_EXTERNAL_TRANSLATE_PKG, null)
}

internal fun saveExternalTranslatePackage(context: Context, packageName: String) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(PREF_EXTERNAL_TRANSLATE_PKG, packageName) }
}

internal fun loadExternalSearchPackage(context: Context): String? {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(PREF_EXTERNAL_SEARCH_PKG, null)
}

internal fun saveExternalSearchPackage(context: Context, packageName: String) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(PREF_EXTERNAL_SEARCH_PKG, packageName) }
}

internal fun savePdfMusicianMode(context: Context, isEnabled: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_MUSICIAN_MODE_KEY, isEnabled) }
}

internal fun loadPdfMusicianMode(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_MUSICIAN_MODE_KEY, false)
}

internal fun savePdfScrollLocked(context: Context, bookId: String, isLocked: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_SCROLL_LOCKED_PREFIX + bookId, isLocked) }
}

internal fun loadPdfScrollLocked(context: Context, bookId: String): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_SCROLL_LOCKED_PREFIX + bookId, false)
}

internal fun savePdfAutoScrollLocalMode(context: Context, bookId: String, isLocal: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_AUTO_SCROLL_IS_LOCAL_PREFIX + bookId, isLocal) }
}

internal fun loadPdfAutoScrollLocalMode(context: Context, bookId: String): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_AUTO_SCROLL_IS_LOCAL_PREFIX + bookId, false)
}

internal fun savePdfAutoScrollLocalSettings(context: Context, bookId: String, speed: Float, min: Float, max: Float) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putFloat(PDF_AUTO_SCROLL_LOCAL_SPEED_PREFIX + bookId, speed)
        putFloat(PDF_AUTO_SCROLL_LOCAL_MIN_PREFIX + bookId, min)
        putFloat(PDF_AUTO_SCROLL_LOCAL_MAX_PREFIX + bookId, max)
    }
}

internal fun loadPdfAutoScrollLocalSettings(context: Context, bookId: String): Triple<Float, Float, Float>? {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    if (!prefs.contains(PDF_AUTO_SCROLL_LOCAL_SPEED_PREFIX + bookId)) return null

    val speed = prefs.getFloat(PDF_AUTO_SCROLL_LOCAL_SPEED_PREFIX + bookId, 3.0f)
    val min = prefs.getFloat(PDF_AUTO_SCROLL_LOCAL_MIN_PREFIX + bookId, 0.1f)
    val max = prefs.getFloat(PDF_AUTO_SCROLL_LOCAL_MAX_PREFIX + bookId, 10.0f)
    return Triple(speed, min, max)
}

internal fun savePdfAutoScrollMinSpeed(context: Context, speed: Float) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putFloat(PDF_AUTO_SCROLL_MIN_SPEED_KEY, speed) }
}

internal fun saveStylusOnlyMode(context: Context, isEnabled: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(STYLUS_ONLY_MODE_KEY, isEnabled) }
}

internal fun loadStylusOnlyMode(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(STYLUS_ONLY_MODE_KEY, false)
}

internal fun loadPdfAutoScrollMinSpeed(context: Context): Float {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getFloat(PDF_AUTO_SCROLL_MIN_SPEED_KEY, 0.1f)
}

internal fun savePdfAutoScrollMaxSpeed(context: Context, speed: Float) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putFloat(PDF_AUTO_SCROLL_MAX_SPEED_KEY, speed) }
}

internal fun loadPdfAutoScrollMaxSpeed(context: Context): Float {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getFloat(PDF_AUTO_SCROLL_MAX_SPEED_KEY, 10.0f)
}

internal fun savePdfAutoScrollUseSlider(context: Context, useSlider: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_AUTO_SCROLL_USE_SLIDER_KEY, useSlider) }
}

internal fun loadPdfAutoScrollUseSlider(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_AUTO_SCROLL_USE_SLIDER_KEY, false)
}

internal fun savePdfAutoScrollSpeed(context: Context, speed: Float) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putFloat(PDF_AUTO_SCROLL_SPEED_KEY, speed) }
}

internal fun loadPdfAutoScrollSpeed(context: Context): Float {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getFloat(PDF_AUTO_SCROLL_SPEED_KEY, 3.0f)
}

internal fun savePdfLockedState(context: Context, bookId: String, scale: Float, offsetX: Float, offsetY: Float) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putFloat("pdf_locked_scale_$bookId", scale)
        putFloat("pdf_locked_offset_x_$bookId", offsetX)
        putFloat("pdf_locked_offset_y_$bookId", offsetY)
    }
}

internal fun loadPdfLockedState(context: Context, bookId: String): Triple<Float, Float, Float>? {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    if (!prefs.contains("pdf_locked_scale_$bookId")) return null
    return Triple(
        prefs.getFloat("pdf_locked_scale_$bookId", 1f),
        prefs.getFloat("pdf_locked_offset_x_$bookId", 0f),
        prefs.getFloat("pdf_locked_offset_y_$bookId", 0f)
    )
}

internal fun saveOcrLanguage(context: Context, language: OcrLanguage) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putString(OCR_LANGUAGE_KEY, language.name)
        putBoolean(OCR_LANGUAGE_SELECTED_KEY, true)
    }
}

internal fun loadOcrLanguage(context: Context): OcrLanguage {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val name = prefs.getString(OCR_LANGUAGE_KEY, OcrLanguage.LATIN.name)
    return try {
        OcrLanguage.valueOf(name ?: OcrLanguage.LATIN.name)
    } catch (_: Exception) {
        OcrLanguage.LATIN
    }
}

internal fun hasUserSelectedOcrLanguage(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(OCR_LANGUAGE_SELECTED_KEY, false)
}

internal fun saveDockState(context: Context, location: DockLocation, offset: Offset) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putString(DOCK_LOCATION_KEY, location.name)
        putFloat(DOCK_OFFSET_X_KEY, offset.x)
        putFloat(DOCK_OFFSET_Y_KEY, offset.y)
    }
}

internal fun loadDockState(context: Context): Pair<DockLocation, Offset> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val locName = prefs.getString(DOCK_LOCATION_KEY, DockLocation.BOTTOM.name)
    val location = try {
        DockLocation.valueOf(locName ?: DockLocation.BOTTOM.name)
    } catch (_: Exception) {
        DockLocation.BOTTOM
    }

    val x = prefs.getFloat(DOCK_OFFSET_X_KEY, 0f)
    val y = prefs.getFloat(DOCK_OFFSET_Y_KEY, 0f)

    return location to Offset(x, y)
}

internal fun saveDisplayMode(context: Context, mode: DisplayMode) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(DISPLAY_MODE_KEY, mode.name) }
}

internal fun loadDisplayMode(context: Context): DisplayMode {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val modeName = prefs.getString(DISPLAY_MODE_KEY, DisplayMode.VERTICAL_SCROLL.name)
    return try {
        DisplayMode.valueOf(modeName ?: DisplayMode.VERTICAL_SCROLL.name)
    } catch (_: IllegalArgumentException) {
        DisplayMode.VERTICAL_SCROLL
    }
}

internal data class TtsPageData(
    val pageIndex: Int, val processedText: ProcessedText, val fromOcr: Boolean
)

internal fun savePdfDarkMode(context: Context, isDark: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_DARK_MODE_KEY, isDark) }
}

internal fun loadPdfDarkMode(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_DARK_MODE_KEY, false)
}
