package com.aryan.reader.pdf

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import com.aryan.reader.BuildConfig
import com.aryan.reader.R
import com.aryan.reader.epubreader.SystemUiMode
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.reader.ReaderPageSpreadMode

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
private const val PDF_MUSICIAN_MODE_KEY = "pdf_musician_mode_enabled"
private const val PREF_USE_ONLINE_DICT = "use_online_dictionary"
private const val PREF_EXTERNAL_DICT_PKG = "external_dictionary_package"
private const val PREF_EXTERNAL_TRANSLATE_PKG = "external_translate_package"
private const val PREF_EXTERNAL_SEARCH_PKG = "external_search_package"
private const val PDF_THEME_KEY = "pdf_reader_theme"
private const val PDF_KEEP_SCREEN_ON_KEY = "pdf_keep_screen_on_enabled"
internal const val PDF_HIDDEN_TOOLS_KEY = "pdf_hidden_tools"
internal const val PDF_TOOL_ORDER_KEY = "pdf_tool_order"
internal const val PDF_BOTTOM_TOOLS_KEY = "pdf_bottom_tools"
internal const val PDF_SYSTEM_UI_MODE_KEY = "pdf_system_ui_mode"
internal const val PDF_VERTICAL_PAGE_GAP_VISIBLE_KEY = "pdf_vertical_page_gap_visible"
internal const val PDF_PAGE_NUMBER_OVERLAY_VISIBLE_KEY = "pdf_page_number_overlay_visible"
internal const val PDF_TOP_TAB_STRIP_VISIBLE_KEY = "pdf_top_tab_strip_visible"
internal const val PDF_PAGE_SPREAD_MODE_KEY = "pdf_page_spread_mode"
internal const val PDF_FIRST_PAGE_STANDALONE_IN_SPREAD_KEY = "pdf_first_page_standalone_in_spread"
internal const val PDF_LAYOUT_DEBUG_TAG = "PdfLayoutDebug"
private const val PDF_HIDDEN_TOOLS_DEFAULTS_VERSION_KEY = "pdf_hidden_tools_defaults_version"
private const val PDF_HIDDEN_TOOLS_DEFAULTS_VERSION = 3

enum class PdfReaderTool(@StringRes val titleRes: Int, val category: String) {
    DICTIONARY(R.string.tool_external_apps, "Top Bar"),
    THEME(R.string.tooltip_theme_desc, "Top Bar"),
    BRIGHTNESS(R.string.tool_brightness, "Top Bar"),
    LOCK_PANNING(R.string.tooltip_lock_pan, "Top Bar"),
    FILE_INFO(R.string.file_information, "Overflow Menu"),
    VISUAL_OPTIONS(R.string.menu_visual_options, "Overflow Menu"),
    TAP_TO_TURN(R.string.menu_tap_to_turn_pages, "Overflow Menu"),
    SLIDER(R.string.tool_navigation_slider, "Bottom Bar"),
    TOC(R.string.tool_sidebar, "Bottom Bar"),
    SEARCH(R.string.action_search, "Bottom Bar"),
    HIGHLIGHT_ALL(R.string.tool_highlight_selectable_text, "Bottom Bar"),
    AI_FEATURES(R.string.ai_features_title, "Bottom Bar"),
    EDIT_MODE(R.string.tool_edit_mode, "Bottom Bar"),
    TTS_CONTROLS(R.string.tool_tts_controls, "Bottom Bar"),
    OCR_LANGUAGE(R.string.menu_ocr_language, "Overflow Menu"),
    READING_MODE(R.string.tool_reading_mode, "Overflow Menu"),
    KEEP_SCREEN_ON(R.string.menu_keep_screen_on, "Overflow Menu"),
    SCREEN_ORIENTATION(R.string.menu_screen_orientation, "Top Bar"),
    AUTO_SCROLL(R.string.menu_auto_scroll, "Overflow Menu"),
    TTS_SETTINGS(R.string.menu_tts_settings, "Overflow Menu"),
    TTS_REPLACEMENTS(R.string.menu_tts_word_replacements, "Overflow Menu"),
    BOOKMARK(R.string.content_desc_bookmark, "Overflow Menu"),
    PAGE_MANAGEMENT(R.string.tool_page_management, "Overflow Menu"),
    REFLOW(R.string.tool_text_view_reflow, "Overflow Menu"),
    SHARE(R.string.action_share, "Overflow Menu"),
    SAVE_COPY(R.string.action_save_copy_to_device, "Overflow Menu"),
    PRINT(R.string.action_print, "Overflow Menu")
}

internal fun defaultPdfHiddenTools(): Set<String> {
    return setOf(
        PdfReaderTool.SCREEN_ORIENTATION.name,
        PdfReaderTool.HIGHLIGHT_ALL.name,
        PdfReaderTool.BRIGHTNESS.name
    )
}

internal fun isPdfReaderToolAvailable(tool: PdfReaderTool): Boolean {
    return BuildConfig.IS_PRO || tool != PdfReaderTool.OCR_LANGUAGE
}

internal fun defaultPdfToolOrder(): List<PdfReaderTool> = PdfReaderTool.entries.filter(::isPdfReaderToolAvailable)

internal fun defaultPdfBottomTools(): Set<String> {
    return defaultPdfToolOrder().filter { it.category == "Bottom Bar" }.map { it.name }.toSet()
}

val PdfBuiltInThemes = BuiltInPdfReaderThemes

private fun sanitizePdfToolNameSet(
    toolNames: Set<String>,
    includeTool: (PdfReaderTool) -> Boolean = { true }
): Set<String> {
    return toolNames.mapNotNull { toolName ->
        PdfReaderTool.entries
            .firstOrNull { it.name == toolName }
            ?.takeIf { isPdfReaderToolAvailable(it) && includeTool(it) }
            ?.name
    }.toSet()
}

internal fun sanitizePdfHiddenToolNames(toolNames: Collection<String>): Set<String> {
    return sanitizePdfToolNameSet(toolNames.toSet())
}

internal fun sanitizePdfBottomToolNames(toolNames: Collection<String>): Set<String> {
    return sanitizePdfToolNameSet(
        toolNames = toolNames.toSet(),
        includeTool = ::isPdfToolbarPlacementTool
    )
}

internal fun restorePdfToolOrderNames(toolNames: Collection<String>): List<PdfReaderTool> {
    val savedTools = toolNames
        .mapNotNull { name -> PdfReaderTool.entries.firstOrNull { it.name == name } }
        .filter(::isPdfReaderToolAvailable)
    return (savedTools + defaultPdfToolOrder().filterNot { it in savedTools }).distinct()
}

internal fun isPdfToolbarPlacementTool(tool: PdfReaderTool): Boolean {
    return when (tool) {
        PdfReaderTool.DICTIONARY,
        PdfReaderTool.THEME,
        PdfReaderTool.BRIGHTNESS,
        PdfReaderTool.LOCK_PANNING,
        PdfReaderTool.SLIDER,
        PdfReaderTool.TOC,
        PdfReaderTool.SEARCH,
        PdfReaderTool.HIGHLIGHT_ALL,
        PdfReaderTool.AI_FEATURES,
        PdfReaderTool.EDIT_MODE,
        PdfReaderTool.TTS_CONTROLS,
        PdfReaderTool.SCREEN_ORIENTATION -> true
        else -> false
    }
}

internal fun loadPdfHiddenTools(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val savedHiddenTools = sanitizePdfHiddenToolNames(prefs.getStringSet(PDF_HIDDEN_TOOLS_KEY, emptySet()).orEmpty())
    val defaultsVersion = prefs.getInt(PDF_HIDDEN_TOOLS_DEFAULTS_VERSION_KEY, 0)
    if (defaultsVersion < PDF_HIDDEN_TOOLS_DEFAULTS_VERSION) {
        val migratedHiddenTools = sanitizePdfHiddenToolNames(savedHiddenTools + pdfHiddenToolsIntroducedAfter(defaultsVersion))
        prefs.edit {
            putStringSet(PDF_HIDDEN_TOOLS_KEY, migratedHiddenTools)
            putInt(PDF_HIDDEN_TOOLS_DEFAULTS_VERSION_KEY, PDF_HIDDEN_TOOLS_DEFAULTS_VERSION)
        }
        return migratedHiddenTools
    }
    return savedHiddenTools
}

private fun pdfHiddenToolsIntroducedAfter(defaultsVersion: Int): Set<String> {
    return buildSet {
        if (defaultsVersion < 2) {
            add(PdfReaderTool.SCREEN_ORIENTATION.name)
            add(PdfReaderTool.HIGHLIGHT_ALL.name)
        }
        if (defaultsVersion < 3) add(PdfReaderTool.BRIGHTNESS.name)
    }
}

internal fun savePdfHiddenTools(context: Context, hiddenTools: Set<String>) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putStringSet(PDF_HIDDEN_TOOLS_KEY, sanitizePdfHiddenToolNames(hiddenTools))
        putInt(PDF_HIDDEN_TOOLS_DEFAULTS_VERSION_KEY, PDF_HIDDEN_TOOLS_DEFAULTS_VERSION)
    }
}

internal fun loadPdfToolOrder(context: Context): List<PdfReaderTool> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val savedToolNames = prefs.getString(PDF_TOOL_ORDER_KEY, null)
        ?.split(',')
        ?.filter { it.isNotBlank() }
        .orEmpty()
    return restorePdfToolOrderNames(savedToolNames)
}

internal fun savePdfToolOrder(context: Context, toolOrder: List<PdfReaderTool>) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val sanitizedOrder = restorePdfToolOrderNames(toolOrder.map { it.name })
    prefs.edit {
        putString(
            PDF_TOOL_ORDER_KEY,
            sanitizedOrder.joinToString(",") { it.name }
        )
    }
}

internal fun loadPdfBottomTools(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val defaultBottomTools = defaultPdfBottomTools()
    val savedBottomTools = prefs.getStringSet(PDF_BOTTOM_TOOLS_KEY, null) ?: return defaultBottomTools
    val sanitizedBottomTools = sanitizePdfBottomToolNames(savedBottomTools)
    return if (savedBottomTools.isNotEmpty() && sanitizedBottomTools.isEmpty()) defaultBottomTools else sanitizedBottomTools
}

internal fun savePdfBottomTools(context: Context, bottomTools: Set<String>) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putStringSet(PDF_BOTTOM_TOOLS_KEY, sanitizePdfBottomToolNames(bottomTools))
    }
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

internal fun savePdfVerticalPageGapVisible(context: Context, isVisible: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_VERTICAL_PAGE_GAP_VISIBLE_KEY, isVisible) }
}

internal fun loadPdfVerticalPageGapVisible(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_VERTICAL_PAGE_GAP_VISIBLE_KEY, true)
}

internal fun savePdfPageNumberOverlayVisible(context: Context, isVisible: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_PAGE_NUMBER_OVERLAY_VISIBLE_KEY, isVisible) }
}

internal fun loadPdfPageNumberOverlayVisible(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_PAGE_NUMBER_OVERLAY_VISIBLE_KEY, true)
}

internal fun savePdfPageSpreadMode(context: Context, mode: ReaderPageSpreadMode) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(PDF_PAGE_SPREAD_MODE_KEY, mode.name) }
}

internal fun loadPdfPageSpreadMode(context: Context): ReaderPageSpreadMode {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val modeName = prefs.getString(PDF_PAGE_SPREAD_MODE_KEY, ReaderPageSpreadMode.SINGLE.name)
    return runCatching { ReaderPageSpreadMode.valueOf(modeName ?: ReaderPageSpreadMode.SINGLE.name) }
        .getOrDefault(ReaderPageSpreadMode.SINGLE)
}

internal fun savePdfFirstPageStandaloneInSpread(context: Context, isEnabled: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_FIRST_PAGE_STANDALONE_IN_SPREAD_KEY, isEnabled) }
}

internal fun loadPdfFirstPageStandaloneInSpread(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_FIRST_PAGE_STANDALONE_IN_SPREAD_KEY, false)
}

internal fun savePdfTopTabStripVisible(context: Context, isVisible: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_TOP_TAB_STRIP_VISIBLE_KEY, isVisible) }
}

internal fun loadPdfTopTabStripVisible(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_TOP_TAB_STRIP_VISIBLE_KEY, true)
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
