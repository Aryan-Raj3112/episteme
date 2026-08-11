// EpubReaderScreen.kt
@file:OptIn(ExperimentalSerializationApi::class) @file:Suppress("VariableNeverRead",
    "UnusedVariable", "Unused", "SimplifyBooleanWithConstants", "KotlinConstantConditions"
)

package com.aryan.reader.epubreader

import kotlinx.serialization.ExperimentalSerializationApi

import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.content.edit
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.BuildConfig
import com.aryan.reader.paginatedreader.TtsChunk
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlin.math.max
import kotlin.math.min

private const val AUTO_SCROLL_USE_SLIDER_KEY = "auto_scroll_use_slider"
private const val AUTO_SCROLL_MIN_SPEED_KEY = "auto_scroll_min_speed"
private const val AUTO_SCROLL_MAX_SPEED_KEY = "auto_scroll_max_speed"
private const val PAGE_TURN_ANIMATION_KEY = "page_turn_animation_enabled"
private const val TTS_MODE_KEY = "tts_mode"

const val PREF_READER_THEME = "reader_theme_id"
const val PREF_CUSTOM_THEMES = "custom_themes_json"

private const val AUTO_SCROLL_IS_LOCAL_PREFIX = "auto_scroll_is_local_"
private const val AUTO_SCROLL_LOCAL_SPEED_PREFIX = "auto_scroll_local_speed_"
private const val AUTO_SCROLL_LOCAL_MIN_PREFIX = "auto_scroll_local_min_"
private const val AUTO_SCROLL_LOCAL_MAX_PREFIX = "auto_scroll_local_max_"
private const val MUSICIAN_MODE_KEY = "musician_mode_enabled"
private const val KEEP_SCREEN_ON_KEY = "keep_screen_on_enabled"
private const val HIDDEN_TOOLS_KEY = "hidden_reader_tools"
private const val TOOL_ORDER_KEY = "reader_tool_order"
private const val BOTTOM_TOOLS_KEY = "reader_bottom_tools"
private const val HIDDEN_TOOLS_DEFAULTS_VERSION_KEY = "reader_hidden_tools_defaults_version"
private const val HIDDEN_TOOLS_DEFAULTS_VERSION = 2
private const val TTS_LOCATE_REASON_INITIAL_RESTORE = "initial_restore"
private const val TTS_LOCATE_REASON_LIFECYCLE_RESUME = "lifecycle_resume"
private const val TTS_LOCATE_REASON_OVERLAY = "overlay"

private const val TAG_LINK_NAV = "LINK_NAV"
private const val TAG_VERTICAL_JITTER = "EpubVerticalJitter"
private const val TAG_STABLE_PAGE_NAV = "StablePageNav"
private const val TAG_PAGINATED_HIGHLIGHT_DIAG = "PaginatedHighlightDiag"

internal fun epubHighlightDiagSnippet(text: String, maxLength: Int = 80): String {
    return text
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('\t', ' ')
        .take(maxLength)
}

internal fun List<TtsChunk>.withInitialChunkOverride(
    startChunkIndex: Int,
    initialChunk: TtsChunk?
): List<TtsChunk> {
    if (initialChunk == null || startChunkIndex !in indices) return this
    val existing = this[startChunkIndex]
    if (
        existing.text == initialChunk.text &&
        existing.sourceCfi == initialChunk.sourceCfi &&
        existing.startOffsetInSource == initialChunk.startOffsetInSource
    ) {
        return this
    }

    return toMutableList().also { chunks ->
        chunks[startChunkIndex] = initialChunk
    }
}

@Composable
internal fun rememberReaderClockTime(): String {
    val context = LocalContext.current
    val formatter = remember(context) {
        android.text.format.DateFormat.getTimeFormat(context)
    }
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            currentTimeMillis = now
            delay(60_000L - now.mod(60_000L))
        }
    }
    return formatter.format(Date(currentTimeMillis))
}

internal fun View.bottomRoundedCornerRadiusPx(): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0

    val insets = rootWindowInsets ?: return 0
    return max(
        insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0,
        insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
    )
}

@Composable
internal fun rememberBottomRoundedCornerPadding(view: View): Dp {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var radiusPx by remember(view) { mutableIntStateOf(view.bottomRoundedCornerRadiusPx()) }

    DisposableEffect(
        view,
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp
    ) {
        val listener = View.OnLayoutChangeListener { updatedView, _, _, _, _, _, _, _, _ ->
            radiusPx = updatedView.bottomRoundedCornerRadiusPx()
        }
        view.addOnLayoutChangeListener(listener)
        radiusPx = view.bottomRoundedCornerRadiusPx()

        onDispose {
            view.removeOnLayoutChangeListener(listener)
        }
    }

    return with(density) { radiusPx.toDp() }
}

internal fun saveHiddenTools(context: Context, hiddenTools: Set<String>) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit {
        putStringSet(HIDDEN_TOOLS_KEY, hiddenTools)
        putInt(HIDDEN_TOOLS_DEFAULTS_VERSION_KEY, HIDDEN_TOOLS_DEFAULTS_VERSION)
    }
}

internal fun loadHiddenTools(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    val savedHiddenTools = prefs.getStringSet(HIDDEN_TOOLS_KEY, emptySet()).orEmpty()
    val defaultsVersion = prefs.getInt(HIDDEN_TOOLS_DEFAULTS_VERSION_KEY, 0)
    if (defaultsVersion < HIDDEN_TOOLS_DEFAULTS_VERSION) {
        val migratedHiddenTools = savedHiddenTools + readerHiddenToolsIntroducedAfter(defaultsVersion)
        prefs.edit {
            putStringSet(HIDDEN_TOOLS_KEY, migratedHiddenTools)
            putInt(HIDDEN_TOOLS_DEFAULTS_VERSION_KEY, HIDDEN_TOOLS_DEFAULTS_VERSION)
        }
        return migratedHiddenTools
    }
    return savedHiddenTools
}

internal fun readerHiddenToolsIntroducedAfter(defaultsVersion: Int): Set<String> {
    return buildSet {
        if (defaultsVersion < 1) add(ReaderTool.SCREEN_ORIENTATION.name)
        if (defaultsVersion < 2) add(ReaderTool.BRIGHTNESS.name)
    }
}

internal fun saveToolOrder(context: Context, toolOrder: List<ReaderTool>) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(TOOL_ORDER_KEY, toolOrder.joinToString(",") { it.name }) }
}

internal fun loadToolOrder(context: Context): List<ReaderTool> {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    val savedTools = prefs.getString(TOOL_ORDER_KEY, null)
        ?.split(',')
        ?.filter { it.isNotBlank() }
        ?.mapNotNull { name -> ReaderTool.entries.firstOrNull { it.name == name } }
        .orEmpty()
    return (savedTools + defaultReaderToolOrder().filterNot { it in savedTools }).distinct()
}

internal fun saveBottomTools(context: Context, bottomTools: Set<String>) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putStringSet(BOTTOM_TOOLS_KEY, bottomTools) }
}

internal fun loadBottomTools(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getStringSet(
        BOTTOM_TOOLS_KEY,
        defaultReaderBottomTools()
    ) ?: defaultReaderBottomTools()
}

internal fun saveKeepScreenOn(context: Context, isEnabled: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(KEEP_SCREEN_ON_KEY, isEnabled) }
}

internal fun loadKeepScreenOn(context: Context): Boolean {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(KEEP_SCREEN_ON_KEY, false)
}

internal fun saveMusicianMode(context: Context, isEnabled: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(MUSICIAN_MODE_KEY, isEnabled) }
}

internal fun loadMusicianMode(context: Context): Boolean {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(MUSICIAN_MODE_KEY, false)
}

internal fun getBookIdForPrefs(title: String): String {
    return title.hashCode().toString()
}

internal fun saveAutoScrollLocalMode(context: Context, bookId: String, isLocal: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(AUTO_SCROLL_IS_LOCAL_PREFIX + bookId, isLocal) }
}

internal fun loadAutoScrollLocalMode(context: Context, bookId: String): Boolean {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(AUTO_SCROLL_IS_LOCAL_PREFIX + bookId, false)
}

internal fun saveAutoScrollLocalSettings(context: Context, bookId: String, speed: Float, min: Float, max: Float) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit {
        putFloat(AUTO_SCROLL_LOCAL_SPEED_PREFIX + bookId, speed)
        putFloat(AUTO_SCROLL_LOCAL_MIN_PREFIX + bookId, min)
        putFloat(AUTO_SCROLL_LOCAL_MAX_PREFIX + bookId, max)
    }
}

internal fun loadAutoScrollLocalSettings(context: Context, bookId: String): Triple<Float, Float, Float>? {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    if (!prefs.contains(AUTO_SCROLL_LOCAL_SPEED_PREFIX + bookId)) return null

    val speed = prefs.getFloat(AUTO_SCROLL_LOCAL_SPEED_PREFIX + bookId, 3.0f)
    val min = prefs.getFloat(AUTO_SCROLL_LOCAL_MIN_PREFIX + bookId, 0.1f)
    val max = prefs.getFloat(AUTO_SCROLL_LOCAL_MAX_PREFIX + bookId, 10.0f)
    return Triple(speed, min, max)
}

internal fun savePageTurnAnimationSetting(context: Context, isEnabled: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PAGE_TURN_ANIMATION_KEY, isEnabled) }
}

internal fun loadPageTurnAnimationSetting(context: Context): Boolean {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(PAGE_TURN_ANIMATION_KEY, false)
}

internal fun saveAutoScrollMinSpeed(context: Context, speed: Float) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putFloat(AUTO_SCROLL_MIN_SPEED_KEY, speed) }
}

internal fun loadAutoScrollMinSpeed(context: Context): Float {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getFloat(AUTO_SCROLL_MIN_SPEED_KEY, 0.1f)
}

internal fun saveAutoScrollMaxSpeed(context: Context, speed: Float) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putFloat(AUTO_SCROLL_MAX_SPEED_KEY, speed) }
}

internal fun loadAutoScrollMaxSpeed(context: Context): Float {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getFloat(AUTO_SCROLL_MAX_SPEED_KEY, 10.0f)
}

internal fun saveAutoScrollUseSlider(context: Context, useSlider: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(AUTO_SCROLL_USE_SLIDER_KEY, useSlider) }
}

internal fun loadAutoScrollUseSlider(context: Context): Boolean {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(AUTO_SCROLL_USE_SLIDER_KEY, false)
}

internal fun saveTtsMode(context: Context, modeName: String) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(TTS_MODE_KEY, modeName) }
}

private const val PREF_USE_ONLINE_DICT = "use_online_dictionary"
private const val PREF_EXTERNAL_DICT_PKG = "external_dictionary_package"
private const val PREF_EXTERNAL_TRANSLATE_PKG = "external_translate_package"
private const val PREF_EXTERNAL_SEARCH_PKG = "external_search_package"

internal fun loadUseOnlineDict(context: Context): Boolean {
    @Suppress("KotlinConstantConditions") if (BuildConfig.FLAVOR == "oss" && BuildConfig.IS_OFFLINE) return false
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(PREF_USE_ONLINE_DICT, true)
}

internal fun saveUseOnlineDict(context: Context, useOnline: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PREF_USE_ONLINE_DICT, useOnline) }
}

internal fun loadExternalDictPackage(context: Context): String? {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getString(PREF_EXTERNAL_DICT_PKG, null)
}

internal fun saveExternalDictPackage(context: Context, packageName: String) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(PREF_EXTERNAL_DICT_PKG, packageName) }
}

internal fun loadExternalTranslatePackage(context: Context): String? {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getString(PREF_EXTERNAL_TRANSLATE_PKG, null)
}

internal fun saveExternalTranslatePackage(context: Context, packageName: String) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(PREF_EXTERNAL_TRANSLATE_PKG, packageName) }
}

internal fun loadExternalSearchPackage(context: Context): String? {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getString(PREF_EXTERNAL_SEARCH_PKG, null)
}

internal fun saveExternalSearchPackage(context: Context, packageName: String) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(PREF_EXTERNAL_SEARCH_PKG, packageName) }
}
