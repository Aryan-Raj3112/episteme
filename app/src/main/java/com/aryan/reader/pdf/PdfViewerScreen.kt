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
// PdfViewerScreen.kt
@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH", "Unused", "UnusedVariable",
    "SimplifyBooleanWithConstants"
)

package com.aryan.reader.pdf

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.work.WorkInfo
import com.aryan.reader.AiDefinitionPopup
import com.aryan.reader.AiDefinitionResult
import com.aryan.reader.BuildConfig
import com.aryan.reader.DeviceVoiceSettingsSheet
import com.aryan.reader.MainViewModel
import com.aryan.reader.R
import com.aryan.reader.SearchResult
import com.aryan.reader.SearchTopBar
import com.aryan.reader.SummarizationPopup
import com.aryan.reader.SummarizationResult
import com.aryan.reader.TtsSettingsSheet
import com.aryan.reader.countWords
import com.aryan.reader.epubreader.AutoScrollControls
import com.aryan.reader.epubreader.DictionarySettingsDialog
import com.aryan.reader.epubreader.ExternalDictionaryHelper
import com.aryan.reader.fetchAiDefinition
import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.pdf.data.AnnotationSettingsRepository
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfAnnotationRepository
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.PdfTextBoxRepository
import com.aryan.reader.pdf.data.PdfTextRepository
import com.aryan.reader.pdf.data.SmartSearchResult
import com.aryan.reader.pdf.data.TextStyleConfig
import com.aryan.reader.pdf.data.VirtualPage
import com.aryan.reader.rememberSearchState
import com.aryan.reader.summarizationUrl
import com.aryan.reader.tts.SpeakerSamplePlayer
import com.aryan.reader.tts.TtsPlaybackManager
import com.aryan.reader.tts.loadTtsMode
import com.aryan.reader.tts.rememberTtsController
import com.aryan.reader.tts.splitTextIntoChunks
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfPasswordException
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import io.legere.pdfiumandroid.suspend.PdfPageKt
import io.legere.pdfiumandroid.suspend.PdfTextPageKt
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

private const val VERTICAL_SCROLL_TAG = "PdfVerticalScroll"
private const val SETTINGS_PREFS_NAME = "epub_reader_settings"
private const val TTS_MODE_KEY = "tts_mode"
private const val DISPLAY_MODE_KEY = "pdf_display_mode"
private const val PDF_DARK_MODE_KEY = "pdf_dark_mode"
private const val OCR_LANGUAGE_KEY = "ocr_language_key"
private const val OCR_LANGUAGE_SELECTED_KEY = "ocr_language_selected_key"
private const val DOCK_LOCATION_KEY = "dock_location"
private const val DOCK_OFFSET_X_KEY = "dock_offset_x"
private const val DOCK_OFFSET_Y_KEY = "dock_offset_y"
private const val PDF_AUTO_SCROLL_SPEED_KEY = "pdf_auto_scroll_speed"
private const val PDF_AUTO_SCROLL_USE_SLIDER_KEY = "pdf_auto_scroll_use_slider"
private const val PDF_AUTO_SCROLL_MIN_SPEED_KEY = "pdf_auto_scroll_min_speed"
private const val PDF_AUTO_SCROLL_MAX_SPEED_KEY = "pdf_auto_scroll_max_speed"
private const val STYLUS_ONLY_MODE_KEY = "stylus_only_mode"

private const val PDF_AUTO_SCROLL_IS_LOCAL_PREFIX = "pdf_as_local_"
private const val PDF_AUTO_SCROLL_LOCAL_SPEED_PREFIX = "pdf_as_local_speed_"
private const val PDF_AUTO_SCROLL_LOCAL_MIN_PREFIX = "pdf_as_local_min_"
private const val PDF_AUTO_SCROLL_LOCAL_MAX_PREFIX = "pdf_as_local_max_"
private const val PDF_SCROLL_LOCKED_PREFIX = "pdf_sl_local_"
private const val PDF_FULL_SCREEN_PREFIX = "pdf_fs_local_"
private const val PDF_MUSICIAN_MODE_KEY = "pdf_musician_mode_enabled"
private const val PREF_USE_ONLINE_DICT = "use_online_dictionary"
private const val PREF_EXTERNAL_DICT_PKG = "external_dictionary_package"

private fun loadUseOnlineDict(context: Context): Boolean {
    @Suppress("KotlinConstantConditions") if (BuildConfig.FLAVOR == "oss") return false
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PREF_USE_ONLINE_DICT, true)
}

private fun saveUseOnlineDict(context: Context, useOnline: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PREF_USE_ONLINE_DICT, useOnline) }
}

private fun loadExternalDictPackage(context: Context): String? {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(PREF_EXTERNAL_DICT_PKG, null)
}

private fun saveExternalDictPackage(context: Context, packageName: String) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(PREF_EXTERNAL_DICT_PKG, packageName) }
}

private fun savePdfMusicianMode(context: Context, isEnabled: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_MUSICIAN_MODE_KEY, isEnabled) }
}

private fun loadPdfMusicianMode(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_MUSICIAN_MODE_KEY, false)
}

private fun savePdfScrollLocked(context: Context, bookId: String, isLocked: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_SCROLL_LOCKED_PREFIX + bookId, isLocked) }
}

private fun loadPdfScrollLocked(context: Context, bookId: String): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_SCROLL_LOCKED_PREFIX + bookId, false)
}

private fun savePdfFullScreen(context: Context, bookId: String, isFull: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_FULL_SCREEN_PREFIX + bookId, isFull) }
}

private fun loadPdfFullScreen(context: Context, bookId: String): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_FULL_SCREEN_PREFIX + bookId, false)
}

private fun savePdfAutoScrollLocalMode(context: Context, bookId: String, isLocal: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_AUTO_SCROLL_IS_LOCAL_PREFIX + bookId, isLocal) }
}

private fun loadPdfAutoScrollLocalMode(context: Context, bookId: String): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_AUTO_SCROLL_IS_LOCAL_PREFIX + bookId, false)
}

private fun savePdfAutoScrollLocalSettings(context: Context, bookId: String, speed: Float, min: Float, max: Float) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putFloat(PDF_AUTO_SCROLL_LOCAL_SPEED_PREFIX + bookId, speed)
        putFloat(PDF_AUTO_SCROLL_LOCAL_MIN_PREFIX + bookId, min)
        putFloat(PDF_AUTO_SCROLL_LOCAL_MAX_PREFIX + bookId, max)
    }
}

private fun loadPdfAutoScrollLocalSettings(context: Context, bookId: String): Triple<Float, Float, Float>? {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    if (!prefs.contains(PDF_AUTO_SCROLL_LOCAL_SPEED_PREFIX + bookId)) return null

    val speed = prefs.getFloat(PDF_AUTO_SCROLL_LOCAL_SPEED_PREFIX + bookId, 3.0f)
    val min = prefs.getFloat(PDF_AUTO_SCROLL_LOCAL_MIN_PREFIX + bookId, 0.1f)
    val max = prefs.getFloat(PDF_AUTO_SCROLL_LOCAL_MAX_PREFIX + bookId, 10.0f)
    return Triple(speed, min, max)
}

private fun savePdfAutoScrollMinSpeed(context: Context, speed: Float) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putFloat(PDF_AUTO_SCROLL_MIN_SPEED_KEY, speed) }
}

private fun saveStylusOnlyMode(context: Context, isEnabled: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(STYLUS_ONLY_MODE_KEY, isEnabled) }
}

private fun loadStylusOnlyMode(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(STYLUS_ONLY_MODE_KEY, false)
}

private fun loadPdfAutoScrollMinSpeed(context: Context): Float {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getFloat(PDF_AUTO_SCROLL_MIN_SPEED_KEY, 0.1f)
}

private fun savePdfAutoScrollMaxSpeed(context: Context, speed: Float) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putFloat(PDF_AUTO_SCROLL_MAX_SPEED_KEY, speed) }
}

private fun loadPdfAutoScrollMaxSpeed(context: Context): Float {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getFloat(PDF_AUTO_SCROLL_MAX_SPEED_KEY, 10.0f)
}

private fun savePdfAutoScrollUseSlider(context: Context, useSlider: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_AUTO_SCROLL_USE_SLIDER_KEY, useSlider) }
}

private fun loadPdfAutoScrollUseSlider(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_AUTO_SCROLL_USE_SLIDER_KEY, false)
}

private fun savePdfAutoScrollSpeed(context: Context, speed: Float) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putFloat(PDF_AUTO_SCROLL_SPEED_KEY, speed) }
}

private fun loadPdfAutoScrollSpeed(context: Context): Float {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getFloat(PDF_AUTO_SCROLL_SPEED_KEY, 3.0f)
}

private fun generateShortId(): String {
    return Random.nextInt(1000, 9999).toString()
}

private fun getSuggestedFilename(originalName: String?, isAnnotated: Boolean): String {
    val base = originalName?.substringBeforeLast('.') ?: "Document"
    val safeBase = base.replace("[^a-zA-Z0-9._-]".toRegex(), "_").take(50)

    val suffix = if (isAnnotated) "_annotated" else ""
    val shortId = generateShortId()

    return "${safeBase}${suffix}_${shortId}.pdf"
}

private enum class SaveMode {
    ORIGINAL, ANNOTATED
}

enum class SearchHighlightMode {
    FOCUSED, ALL
}

private sealed interface HistoryAction {
    data class Add(val pageIndex: Int, val annotation: PdfAnnotation) : HistoryAction
    data class Remove(val items: Map<Int, List<PdfAnnotation>>) : HistoryAction
}

private enum class DockLocation {
    TOP, BOTTOM, FLOATING
}

private fun saveOcrLanguage(context: Context, language: OcrLanguage) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putString(OCR_LANGUAGE_KEY, language.name)
        putBoolean(OCR_LANGUAGE_SELECTED_KEY, true)
    }
}

private fun loadOcrLanguage(context: Context): OcrLanguage {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val name = prefs.getString(OCR_LANGUAGE_KEY, OcrLanguage.LATIN.name)
    return try {
        OcrLanguage.valueOf(name ?: OcrLanguage.LATIN.name)
    } catch (_: Exception) {
        OcrLanguage.LATIN
    }
}

private fun hasUserSelectedOcrLanguage(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(OCR_LANGUAGE_SELECTED_KEY, false)
}

private fun saveDockState(context: Context, location: DockLocation, offset: Offset) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putString(DOCK_LOCATION_KEY, location.name)
        putFloat(DOCK_OFFSET_X_KEY, offset.x)
        putFloat(DOCK_OFFSET_Y_KEY, offset.y)
    }
}

private fun loadDockState(context: Context): Pair<DockLocation, Offset> {
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

private enum class DisplayMode {
    PAGINATION, VERTICAL_SCROLL
}

private fun saveDisplayMode(context: Context, mode: DisplayMode) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(DISPLAY_MODE_KEY, mode.name) }
}

private fun loadDisplayMode(context: Context): DisplayMode {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val modeName = prefs.getString(DISPLAY_MODE_KEY, DisplayMode.VERTICAL_SCROLL.name)
    return try {
        DisplayMode.valueOf(modeName ?: DisplayMode.VERTICAL_SCROLL.name)
    } catch (_: IllegalArgumentException) {
        DisplayMode.VERTICAL_SCROLL
    }
}

internal data class PdfBookmark(val pageIndex: Int, val title: String, val totalPages: Int)

private fun loadPdfBookmarksFromJson(bookmarksJson: String?): Set<PdfBookmark> {
    if (bookmarksJson.isNullOrBlank()) return emptySet()
    return try {
        val jsonArray = JSONArray(bookmarksJson)
        (0 until jsonArray.length()).mapNotNull { i ->
            try {
                val json = jsonArray.getJSONObject(i)
                PdfBookmark(
                    pageIndex = json.getInt("pageIndex"),
                    title = json.getString("title"),
                    totalPages = json.getInt("totalPages")
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse bookmark from JSON object")
                null
            }
        }.toSet()
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse bookmarks from JSON string: $bookmarksJson")
        emptySet()
    }
}

private fun savePdfDarkMode(context: Context, isDark: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_DARK_MODE_KEY, isDark) }
}

private fun loadPdfDarkMode(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_DARK_MODE_KEY, false)
}

private data class TtsPageData(
    val pageIndex: Int, val processedText: ProcessedText, val fromOcr: Boolean
)

private data class TocEntry(val title: String, val pageIndex: Int, val nestLevel: Int)

private fun flattenToc(bookmarks: List<PdfDocument.Bookmark>, level: Int = 0): List<TocEntry> {
    val entries = mutableListOf<TocEntry>()
    for (bookmark in bookmarks) {
        entries.add(
            TocEntry(
                title = bookmark.title ?: "Untitled Chapter",
                pageIndex = bookmark.pageIdx.toInt(),
                nestLevel = level
            )
        )
        if (bookmark.children.isNotEmpty()) {
            entries.addAll(flattenToc(bookmark.children, level + 1))
        }
    }
    return entries
}

@OptIn(UnstableApi::class)
@Suppress("unused")
private fun saveTtsMode(context: Context, mode: TtsPlaybackManager.TtsMode) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(TTS_MODE_KEY, mode.name) }
}

private suspend fun renderPageToBitmap(doc: PdfDocumentKt, pageIndex: Int): Bitmap? {
    return withContext(Dispatchers.IO) {
        var page: PdfPageKt? = null
        try {
            page = doc.openPage(pageIndex)

            val bitmapWidth = 1080
            val aspectRatio =
                page.getPageWidthPoint().toFloat() / page.getPageHeightPoint().toFloat()
            if (aspectRatio.isNaN() || aspectRatio <= 0) {
                Timber.e("Invalid aspect ratio for page $pageIndex")
                return@withContext null
            }
            val bitmapHeight = (bitmapWidth / aspectRatio).toInt()

            if (bitmapHeight <= 0) {
                Timber.e("Invalid calculated bitmap height for page $pageIndex")
                return@withContext null
            }

            val bitmap = createBitmap(bitmapWidth, bitmapHeight)
            page.renderPageBitmap(
                bitmap = bitmap,
                startX = 0,
                startY = 0,
                drawSizeX = bitmapWidth,
                drawSizeY = bitmapHeight,
                renderAnnot = true
            )
            bitmap
        } catch (e: Exception) {
            Timber.e(e, "Error rendering page $pageIndex to bitmap for summarization")
            null
        } finally {
            try {
                page?.close()
            } catch (e: Exception) {
                Timber.w(e, "Error closing page in renderPageToBitmap")
            }
        }
    }
}

private fun getFastFileId(context: Context, uri: Uri): String {
    var result = uri.toString()
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                val size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                val name = if (nameIndex != -1) cursor.getString(nameIndex) else "unknown"

                result = "${name}_${size}"
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to generate fast file ID")
    }
    return result
}

@Suppress("KotlinConstantConditions")
@SuppressLint("UnusedBoxWithConstraintsScope", "ObsoleteSdkInt")
@ExperimentalMaterial3Api
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfUri: Uri,
    initialPage: Int?,
    initialBookmarksJson: String?,
    isProUser: Boolean,
    onNavigateBack: () -> Unit,
    onSavePosition: (page: Int, totalPages: Int) -> Unit,
    onBookmarksChanged: (bookmarksJson: String) -> Unit,
    onNavigateToPro: () -> Unit,
    viewModel: MainViewModel
) {
    SideEffect { Timber.tag("PdfDrawPerf").v("ROOT: PdfViewerScreen Recomposing") }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        PdfFontCache.init(context.assets)
    }
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var displayMode by remember { mutableStateOf(loadDisplayMode(context)) }
    var isPdfDarkMode by remember { mutableStateOf(loadPdfDarkMode(context)) }
    var pageAspectRatios by remember { mutableStateOf<List<Float>>(emptyList()) }
    var showBars by remember { mutableStateOf(true) }
    var isFullScreen by remember { mutableStateOf(false) }
    var documentPassword by remember { mutableStateOf<String?>(null) }
    var isScrollLocked by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    LocalView.current

    var ocrLanguage by remember { mutableStateOf(loadOcrLanguage(context)) }
    var hasSelectedOcrLanguage by remember { mutableStateOf(hasUserSelectedOcrLanguage(context)) }
    var showOcrLanguageDialog by remember { mutableStateOf(false) }
    var showReindexDialog by remember { mutableStateOf<OcrLanguage?>(null) }
    var pendingActionAfterOcrSelection by remember { mutableStateOf<(() -> Unit)?>(null) }

    val executeWithOcrCheck = remember(hasSelectedOcrLanguage) {
        { action: () -> Unit ->
            if (hasSelectedOcrLanguage) {
                action()
            } else {
                pendingActionAfterOcrSelection = action
                showOcrLanguageDialog = true
            }
        }
    }

    var searchHighlightMode by remember { mutableStateOf(SearchHighlightMode.ALL) }

    var isBackgroundIndexing by remember { mutableStateOf(false) }
    var backgroundIndexingProgress by remember { mutableFloatStateOf(0f) }

    var currentBookId by remember { mutableStateOf<String?>(null) }
    val bookId = currentBookId ?: pdfUri.toString().hashCode().toString()

    LaunchedEffect(bookId) {
        isScrollLocked = loadPdfScrollLocked(context, bookId)
        isFullScreen = loadPdfFullScreen(context, bookId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val reflowBookId = remember(bookId) { "${bookId}_reflow" }
    val hasReflowFile by remember(uiState.allRecentFiles, reflowBookId) {
        derivedStateOf {
            uiState.allRecentFiles.any { it.bookId == reflowBookId && !it.isDeleted }
        }
    }
    val originalFileName by remember(uiState.recentFiles, pdfUri) {
        derivedStateOf {
            uiState.recentFiles.find { it.uriString == pdfUri.toString() }?.displayName
                ?: pdfUri.lastPathSegment ?: "Document.pdf"
        }
    }

    var isDockDragging by remember { mutableStateOf(false) }

    var isAutoScrollModeActive by remember { mutableStateOf(false) }
    var isAutoScrollPlaying by remember { mutableStateOf(false) }
    var isAutoScrollTempPaused by remember { mutableStateOf(false) }
    val autoScrollResumeJob = remember { mutableStateOf<Job?>(null) }
    var isAutoScrollCollapsed by remember { mutableStateOf(false) }

    var isMusicianMode by remember { mutableStateOf(loadPdfMusicianMode(context)) }
    var autoScrollUseSlider by remember { mutableStateOf(loadPdfAutoScrollUseSlider(context)) }
    var isStylusOnlyMode by remember { mutableStateOf(loadStylusOnlyMode(context)) }
    var currentTtsMode by remember { mutableStateOf(loadTtsMode(context)) }
    var showTtsSettingsSheet by remember { mutableStateOf(false) }

    var showDictionarySettingsSheet by remember { mutableStateOf(false) }
    var useOnlineDictionary by remember { mutableStateOf(loadUseOnlineDict(context)) }
    var selectedDictPackage by remember { mutableStateOf(loadExternalDictPackage(context)) }

    var showDeviceVoiceSettingsSheet by remember { mutableStateOf(false) }

    fun triggerAutoScrollTempPause(durationMs: Long) {
        if (!isAutoScrollModeActive || !isAutoScrollPlaying) return
        autoScrollResumeJob.value?.cancel()
        isAutoScrollTempPaused = true
        autoScrollResumeJob.value = coroutineScope.launch {
            delay(durationMs)
            if (isActive && isAutoScrollModeActive && isAutoScrollPlaying) {
                isAutoScrollTempPaused = false
            }
        }
    }

    val onAutoScrollInteraction = remember {
        {
            if (isAutoScrollPlaying) {
                triggerAutoScrollTempPause(300L)
            }
        }
    }

    var paginationDraggingBoxId by remember { mutableStateOf<String?>(null) }

    val customFonts by viewModel.customFonts.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val onOcrStateChange: (Boolean) -> Unit = {}

    var showZoomIndicator by remember { mutableStateOf(false) }
    var bookmarks by remember(pdfUri) { mutableStateOf(loadPdfBookmarksFromJson(initialBookmarksJson)) }

    var showPenPlayground by remember { mutableStateOf(false) }

    var isEditMode by remember { mutableStateOf(false) }

    var isDockMinimized by remember { mutableStateOf(false) }

    val isDrawingActive by remember(isEditMode, isDockMinimized) {
        derivedStateOf { isEditMode && !isDockMinimized }
    }

    var isAutoScrollLocal by remember { mutableStateOf(loadPdfAutoScrollLocalMode(context, bookId)) }

    LaunchedEffect(bookId) {
        isAutoScrollLocal = loadPdfAutoScrollLocalMode(context, bookId)
    }

    val initialSettings = remember(isAutoScrollLocal, bookId) {
        if (isAutoScrollLocal) {
            loadPdfAutoScrollLocalSettings(context, bookId) ?: Triple(
                loadPdfAutoScrollSpeed(context),
                loadPdfAutoScrollMinSpeed(context),
                loadPdfAutoScrollMaxSpeed(context)
            )
        } else {
            Triple(
                loadPdfAutoScrollSpeed(context),
                loadPdfAutoScrollMinSpeed(context),
                loadPdfAutoScrollMaxSpeed(context)
            )
        }
    }

    var autoScrollSpeed by remember { mutableFloatStateOf(initialSettings.first) }
    var autoScrollMinSpeed by remember { mutableFloatStateOf(initialSettings.second) }
    var autoScrollMaxSpeed by remember { mutableFloatStateOf(initialSettings.third) }

    LaunchedEffect(initialSettings) {
        autoScrollSpeed = initialSettings.first
        autoScrollMinSpeed = initialSettings.second
        autoScrollMaxSpeed = initialSettings.third
    }

    val onToggleAutoScrollMode = { newIsLocal: Boolean ->
        isAutoScrollLocal = newIsLocal
        savePdfAutoScrollLocalMode(context, bookId, newIsLocal)

        if (newIsLocal) {
            val existingLocal = loadPdfAutoScrollLocalSettings(context, bookId)
            if (existingLocal == null) {
                savePdfAutoScrollLocalSettings(context, bookId, autoScrollSpeed, autoScrollMinSpeed, autoScrollMaxSpeed)
            } else {
                autoScrollSpeed = existingLocal.first
                autoScrollMinSpeed = existingLocal.second
                autoScrollMaxSpeed = existingLocal.third
            }
        } else {
            autoScrollSpeed = loadPdfAutoScrollSpeed(context)
            autoScrollMinSpeed = loadPdfAutoScrollMinSpeed(context)
            autoScrollMaxSpeed = loadPdfAutoScrollMaxSpeed(context)
        }
    }

    val updateSpeed = { newSpeed: Float ->
        autoScrollSpeed = newSpeed
        if (isAutoScrollLocal) {
            savePdfAutoScrollLocalSettings(context, bookId, newSpeed, autoScrollMinSpeed, autoScrollMaxSpeed)
        } else {
            savePdfAutoScrollSpeed(context, newSpeed)
        }
    }

    val updateMinSpeed = { newMin: Float ->
        autoScrollMinSpeed = newMin
        if (isAutoScrollLocal) {
            var currentMax = autoScrollMaxSpeed
            var currentSpeed = autoScrollSpeed
            if (currentMax < newMin) { currentMax = newMin; autoScrollMaxSpeed = newMin }
            if (currentSpeed < newMin) { currentSpeed = newMin; autoScrollSpeed = newMin }
            else if (currentSpeed > currentMax) { currentSpeed = currentMax; autoScrollSpeed = currentMax }
            savePdfAutoScrollLocalSettings(context, bookId, currentSpeed, newMin, currentMax)
        } else {
            savePdfAutoScrollMinSpeed(context, newMin)
        }
    }

    val updateMaxSpeed = { newMax: Float ->
        autoScrollMaxSpeed = newMax
        if (isAutoScrollLocal) {
            var currentMin = autoScrollMinSpeed
            var currentSpeed = autoScrollSpeed
            if (currentMin > newMax) { currentMin = newMax; autoScrollMinSpeed = newMax }
            if (currentSpeed > newMax) { currentSpeed = newMax; autoScrollSpeed = newMax }
            else if (currentSpeed < currentMin) { currentSpeed = currentMin; autoScrollSpeed = currentMin }
            savePdfAutoScrollLocalSettings(context, bookId, currentSpeed, currentMin, newMax)
        } else {
            savePdfAutoScrollMaxSpeed(context, newMax)
        }
    }

    val (initialDockLocation, initialDockOffset) = remember(context) { loadDockState(context) }

    var dockLocation by remember { mutableStateOf(initialDockLocation) }
    var dockOffset by remember { mutableStateOf(initialDockOffset) }
    var snapPreviewLocation by remember { mutableStateOf<DockLocation?>(null) }
    var paginationDraggingOffset by remember { mutableStateOf(Offset.Zero) }
    var paginationDraggingSize by remember { mutableStateOf(Size.Zero) }
    var paginationDragPageHeight by remember { mutableFloatStateOf(0f) }
    var paginationOriginalRelSize by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(Unit) {
        if (dockLocation == DockLocation.FLOATING && dockOffset == Offset.Zero) {
            dockLocation = DockLocation.BOTTOM
        }
    }

    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    LaunchedEffect(isFullScreen) {
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            if (isFullScreen) {
                showBars = false
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                showBars = true
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val dockHeight = 64.dp
    val dockHeightPx = with(LocalDensity.current) { dockHeight.toPx() }

    val verticalHeaderHeight by remember(
        dockLocation,
        snapPreviewLocation,
        isEditMode,
        isDockDragging
    ) {
        derivedStateOf {
            if (!isEditMode) {
                0.dp
            } else {
                val isStickyTop = dockLocation == DockLocation.TOP && !isDockDragging
                val isPreviewingTop = snapPreviewLocation == DockLocation.TOP

                if (isStickyTop || isPreviewingTop) dockHeight else 0.dp
            }
        }
    }

    val verticalFooterHeight by remember(
        dockLocation,
        snapPreviewLocation,
        isEditMode,
        isDockDragging
    ) {
        derivedStateOf {
            if (!isEditMode) {
                0.dp
            } else {
                val isStickyBottom = dockLocation == DockLocation.BOTTOM && !isDockDragging
                val isPreviewingBottom = snapPreviewLocation == DockLocation.BOTTOM

                if (isStickyBottom || isPreviewingBottom) dockHeight else 0.dp
            }
        }
    }

    LaunchedEffect(ocrLanguage) { OcrHelper.init(ocrLanguage) }

    LaunchedEffect(displayMode) { saveDisplayMode(context, displayMode) }
    LaunchedEffect(isPdfDarkMode) { savePdfDarkMode(context, isPdfDarkMode) }

    val annotationSettingsRepo = remember(context) { AnnotationSettingsRepository(context) }
    val toolSettings by annotationSettingsRepo.settings.collectAsState()

    var showToolSettings by remember { mutableStateOf(false) }

    val isHighlighterSnapEnabled = toolSettings.isHighlighterSnapEnabled

    val selectedTool = toolSettings.getActiveTool()

    val lastPenTool = toolSettings.getLastPenTool()
    val dockPenColor = toolSettings.getToolColor(lastPenTool)
    val dockHighlighterColor = toolSettings.getToolColor(InkType.HIGHLIGHTER)

    val activeToolColor = toolSettings.getToolColor(selectedTool)
    val activeToolThickness = toolSettings.getToolThickness(selectedTool)

    val fountainPenColor = toolSettings.getToolColor(InkType.FOUNTAIN_PEN)
    val markerColor = toolSettings.getToolColor(InkType.PEN)
    val pencilColor = toolSettings.getToolColor(InkType.PENCIL)
    val highlighterColor = toolSettings.getToolColor(InkType.HIGHLIGHTER)
    val highlighterRoundColor = toolSettings.getToolColor(InkType.HIGHLIGHTER_ROUND)

    val isCurrentToolHighlighter =
        selectedTool == InkType.HIGHLIGHTER || selectedTool == InkType.HIGHLIGHTER_ROUND

    val currentSnapEnabled by rememberUpdatedState(isHighlighterSnapEnabled)
    val currentIsHighlighter by rememberUpdatedState(isCurrentToolHighlighter)

    val penPalette = remember(toolSettings.penPaletteArgb) { toolSettings.getPenPalette() }
    val highlighterPalette =
        remember(toolSettings.highlighterPaletteArgb) { toolSettings.getHighlighterPalette() }

    val currentStrokeColor by remember(activeToolColor) { derivedStateOf { activeToolColor } }
    val currentStrokeWidth by remember(activeToolThickness) { derivedStateOf { activeToolThickness } }

    val pdfTextRepository = remember(context) { PdfTextRepository(context) }
    val annotationRepository = remember(context) { PdfAnnotationRepository(context) }
    val textBoxRepository = remember(context) { PdfTextBoxRepository(context) }
    val highlightRepository = remember(context) { com.aryan.reader.pdf.data.PdfHighlightRepository(context) }

    var allAnnotations by remember { mutableStateOf<Map<Int, List<PdfAnnotation>>>(emptyMap()) }

    val undoStack = remember { mutableStateListOf<HistoryAction>() }
    val redoStack = remember { mutableStateListOf<HistoryAction>() }

    val erasedAnnotationsFromStroke = remember {
        mutableStateMapOf<Int, MutableList<PdfAnnotation>>()
    }

    var areAnnotationsLoaded by remember { mutableStateOf(false) }

    val richTextRepository = remember(context) { PdfRichTextRepository(context) }
    val richTextController = remember(currentBookId) {
        if (currentBookId != null) RichTextController(
            richTextRepository,
            coroutineScope,
            currentBookId!!
        )
        else null
    }
    var pdfDocument by remember { mutableStateOf<PdfDocumentKt?>(null) }
    var pfdState by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var totalPages by remember { mutableIntStateOf(0) }
    var currentPageScale by remember { mutableFloatStateOf(1f) }
    val textBoxes = remember { mutableStateListOf<PdfTextBox>() }
    var selectedTextBoxId by remember { mutableStateOf<String?>(null) }
    val userHighlights = remember { mutableStateListOf<PdfUserHighlight>() }
    val drawingState = remember { PdfDrawingState() }
    val pdfiumCore = remember(context) { PdfiumCoreKt(Dispatchers.Default) }
    val verticalReaderState = rememberVerticalPdfReaderState()
    var virtualPages by remember { mutableStateOf<List<VirtualPage>>(emptyList()) }
    val totalDisplayPages by remember(virtualPages, totalPages) {
        derivedStateOf { if (virtualPages.isNotEmpty()) virtualPages.size else totalPages }
    }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { totalDisplayPages })
    val currentPage by remember {
        derivedStateOf {
            when (displayMode) {
                DisplayMode.PAGINATION -> pagerState.currentPage
                DisplayMode.VERTICAL_SCROLL -> verticalReaderState.currentPage
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val saveMutex = remember { Mutex() }

    var initialScrollDone by remember { mutableStateOf(false) }
    var isDocumentReady by remember { mutableStateOf(false) }

    val lastSavedHashes = remember(currentBookId) { IntArray(5) { 0 } }

    val currentAnnotations by rememberUpdatedState(allAnnotations)
    val currentTextBoxes by rememberUpdatedState(textBoxes.toList())
    val currentHighlights by rememberUpdatedState(userHighlights.toList())
    val currentBookmarks by rememberUpdatedState(bookmarks)
    val currentTotalPages by rememberUpdatedState(totalDisplayPages)
    val currentPageState by rememberUpdatedState(currentPage)

    val saveAllData = remember(currentBookId, annotationRepository, textBoxRepository, highlightRepository) {
        { force: Boolean ->
            coroutineScope.launch {
                val bookId = currentBookId ?: return@launch
                val annots = currentAnnotations
                val boxes = currentTextBoxes
                val highlights = currentHighlights
                val bms = currentBookmarks
                val page = currentPageState
                val totalPgs = currentTotalPages

                val annotsHash = annots.hashCode()
                val boxesHash = boxes.hashCode()
                val highlightsHash = highlights.hashCode()
                val bmsHash = bms.hashCode()

                // Protect the lock and I/O execution with NonCancellable
                withContext(NonCancellable) {
                    saveMutex.withLock {
                        withContext(Dispatchers.IO) {
                            var didSave = false

                            if (force || annotsHash != lastSavedHashes[0]) {
                                annotationRepository.saveAnnotations(bookId, annots)
                                lastSavedHashes[0] = annotsHash
                                didSave = true
                            }
                            if (force || boxesHash != lastSavedHashes[1]) {
                                textBoxRepository.saveTextBoxes(bookId, boxes)
                                lastSavedHashes[1] = boxesHash
                                didSave = true
                            }
                            if (force || highlightsHash != lastSavedHashes[2]) {
                                highlightRepository.saveHighlights(bookId, highlights)
                                lastSavedHashes[2] = highlightsHash
                                didSave = true
                            }
                            if (force || bmsHash != lastSavedHashes[3]) {
                                val objectList = bms.map { bookmark ->
                                    JSONObject().apply {
                                        put("pageIndex", bookmark.pageIndex)
                                        put("title", bookmark.title)
                                        put("totalPages", bookmark.totalPages)
                                    }
                                }
                                val bookmarksJson = JSONArray(objectList).toString()
                                withContext(Dispatchers.Main) {
                                    onBookmarksChanged(bookmarksJson)
                                }
                                lastSavedHashes[3] = bmsHash
                                didSave = true
                            }
                            if (force || page != lastSavedHashes[4]) {
                                if (totalPgs > 0) {
                                    withContext(Dispatchers.Main) {
                                        onSavePosition(page, totalPgs)
                                    }
                                }
                                lastSavedHashes[4] = page
                            }

                            if (didSave) {
                                Timber.tag("PdfSavePerf").d("Saved data for book $bookId")
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                Timber.tag("PdfSavePerf").i("Lifecycle $event triggered, forcing save.")
                coroutineScope.launch {
                    if (richTextController != null) {
                        withContext(NonCancellable) { richTextController.saveImmediate() }
                    }
                    saveAllData(true).join()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(
        allAnnotations,
        textBoxes.toList(),
        userHighlights.toList(),
        bookmarks,
        currentPage
    ) {
        if (areAnnotationsLoaded && currentBookId != null && initialScrollDone) {
            delay(2000) // Debounce period
            saveAllData(false)
        }
    }

    val allAnnotationsProvider = remember { { allAnnotations } }

    LaunchedEffect(Unit) {
        Timber.d("PdfViewerScreen init: initialBookmarksJson is '$initialBookmarksJson'")
        Timber.d("PdfViewerScreen init: Loaded ${bookmarks.size} bookmarks initially.")
    }

    var flatTableOfContents by remember { mutableStateOf<List<TocEntry>>(emptyList()) }
    var showDictionaryUpsellDialog by remember { mutableStateOf(false) }
    var showSummarizationUpsellDialog by remember { mutableStateOf(false) }
    var showAiDefinitionPopup by remember { mutableStateOf(false) }
    var selectedTextForAi by remember { mutableStateOf<String?>(null) }
    var aiDefinitionResult by remember { mutableStateOf<AiDefinitionResult?>(null) }
    var isAiDefinitionLoading by remember { mutableStateOf(false) }

    var isAutoPagingForTts by remember { mutableStateOf(false) }
    var showAllTextHighlights by remember { mutableStateOf(false) }
    var isHighlightingLoading by remember { mutableStateOf(false) }

    var ttsPageData by remember { mutableStateOf<TtsPageData?>(null) }
    var ttsHighlightData by remember { mutableStateOf<TtsHighlightData?>(null) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoadingDocument by remember { mutableStateOf(true) }

    var selectionClearTrigger by remember { mutableLongStateOf(0L) }

    val displayPageRatios by remember(pageAspectRatios, virtualPages) {
        derivedStateOf {
            if (virtualPages.isEmpty()) {
                pageAspectRatios
            } else {
                virtualPages.map { vp ->
                    when (vp) {
                        is VirtualPage.PdfPage -> pageAspectRatios.getOrElse(vp.pdfIndex) { 1f }
                        is VirtualPage.BlankPage -> {
                            if (vp.height > 0) vp.width.toFloat() / vp.height.toFloat() else 1f
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(richTextController, toolSettings.textStyle) {
        richTextController?.let { controller ->
            val config = toolSettings.textStyle
            val style = SpanStyle(
                color = Color(config.colorArgb),
                background = Color(config.backgroundColorArgb),
                fontSize = config.fontSize.sp,
                fontWeight = if (config.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (config.isItalic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = PdfFontCache.getFontFamily(config.fontPath),
                textDecoration = run {
                    val decorations = mutableListOf<TextDecoration>()
                    if (config.isUnderline) decorations.add(TextDecoration.Underline)
                    if (config.isStrikeThrough) decorations.add(TextDecoration.LineThrough)
                    if (decorations.isEmpty()) TextDecoration.None
                    else TextDecoration.combine(decorations)
                })

            if (controller.currentStyle != style || controller.currentFontPath != config.fontPath) {
                controller.updateCurrentStyle(style, config.fontPath, config.fontName)
            }
        }
    }

    LaunchedEffect(currentBookId) {
        if (currentBookId != null) richTextRepository.load(currentBookId!!)
    }
    LaunchedEffect(richTextController, keyboardController) {
        richTextController?.setKeyboardController(keyboardController)
    }
    LaunchedEffect(richTextController?.cursorPageIndex, isEditMode) {
        val controller = richTextController ?: return@LaunchedEffect
        val targetPage = controller.cursorPageIndex

        if (isEditMode && targetPage >= 0 && targetPage < totalDisplayPages) {

            if (displayMode == DisplayMode.PAGINATION) {
                if (pagerState.currentPage != targetPage) {
                    Timber.tag("CursorNav").d("Cursor moved to Page $targetPage. Auto-paging.")
                    pagerState.animateScrollToPage(targetPage)
                }
            }
        }
    }

    Timber.d("Derived currentPage recomposed. New value: $currentPage (Mode: $displayMode)")

    val onHighlightAdd = remember(pdfDocument, currentBookId) {
        { pageIndex: Int, range: Pair<Int, Int>, text: String, color: PdfHighlightColor ->
            Timber.tag("PdfExportDebug").i("onHighlightAdd: Adding persistent highlight. Page: $pageIndex, Text: ${text.take(20)}...")
            coroutineScope.launch {
                val doc = pdfDocument
                if (doc == null) {
                    Timber.tag("PdfHighlightDebug").e("onHighlightAdd failed: pdfDocument is null")
                    return@launch
                }

                val existingOnPage = userHighlights.filter {
                    it.pageIndex == pageIndex && it.color == color
                }

                var newStart = range.first
                var newEnd = range.second
                val highlightsToRemove = mutableListOf<PdfUserHighlight>()

                existingOnPage.forEach { h ->
                    if (max(newStart, h.range.first) <= min(newEnd, h.range.second)) {
                        newStart = min(newStart, h.range.first)
                        newEnd = max(newEnd, h.range.second)
                        highlightsToRemove.add(h)
                    }
                }

                userHighlights.removeAll(highlightsToRemove)

                withContext(Dispatchers.IO) {
                    try {
                        doc.openPage(pageIndex).use { page ->
                            page.openTextPage().use { textPage ->
                                val fullText = textPage.textPageGetText(newStart, newEnd - newStart) ?: text
                                val rects = textPage.textPageGetRectsForRanges(intArrayOf(newStart, newEnd - newStart))

                                val rawPdfRects = rects?.map { r -> r.rect } ?: emptyList()
                                val mergedPdfRects = mergePdfRectsIntoLines(rawPdfRects)

                                val newHighlight = PdfUserHighlight(
                                    pageIndex = pageIndex,
                                    bounds = mergedPdfRects,
                                    color = color,
                                    text = fullText,
                                    range = Pair(newStart, newEnd)
                                )

                                withContext(Dispatchers.Main) {
                                    userHighlights.add(newHighlight)
                                    Timber.tag("PdfExportDebug").d("userHighlights now contains ${userHighlights.size} items.")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag("PdfHighlightDebug").e(e, "Failed to create highlight")
                    }
                }
            }
            Unit
        }
    }

    val onHighlightUpdate = remember {
        { id: String, newColor: PdfHighlightColor ->
            Timber.tag("PdfHighlightDebug").d("onHighlightUpdate triggered: id=$id, newColor=$newColor")
            val index = userHighlights.indexOfFirst { it.id == id }
            if (index != -1) {
                val old = userHighlights[index]
                userHighlights[index] = old.copy(color = newColor)
                Timber.tag("PdfHighlightDebug").d("Highlight successfully updated")
            } else {
                Timber.tag("PdfHighlightDebug").w("Highlight update failed: ID $id not found")
            }
        }
    }

    val onHighlightDelete = remember {
        { id: String ->
            userHighlights.removeAll { it.id == id }
            Unit
        }
    }

    val onInsertPage: () -> Unit = {
        coroutineScope.launch {
            val targetIndex = currentPage + 1
            Timber.tag("RichTextMigration").i("INSERT: User requested blank page at index $targetIndex")

            val (refWidth, refHeight) = withContext(Dispatchers.IO) {
                if (virtualPages.isNotEmpty()) {
                    val refIndex = (currentPage).coerceIn(0, virtualPages.size - 1)
                    when (val vp = virtualPages[refIndex]) {
                        is VirtualPage.PdfPage -> {
                            var w = 595
                            var h = 842
                            try {
                                pdfDocument?.openPage(vp.pdfIndex)?.use { page ->
                                    val preRotationWidth = page.getPageWidthPoint()
                                    val preRotationHeight = page.getPageHeightPoint()
                                    val rotation = page.getPageRotation()

                                    if (rotation == 90 || rotation == 270) {
                                        w = preRotationHeight
                                        h = preRotationWidth
                                    } else {
                                        w = preRotationWidth
                                        h = preRotationHeight
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Could not get page dimensions for page ${vp.pdfIndex}. Using defaults.")
                            }
                            Pair(w, h)
                        }
                        is VirtualPage.BlankPage -> Pair(vp.width, vp.height)
                    }
                } else {
                    Pair(595, 842)
                }
            }

            if (currentBookId != null) {
                val shiftedBoxes = textBoxes.map { box ->
                    if (box.pageIndex >= targetIndex) {
                        box.copy(pageIndex = box.pageIndex + 1)
                    } else {
                        box
                    }
                }
                if (shiftedBoxes != textBoxes) {
                    textBoxes.clear()
                    textBoxes.addAll(shiftedBoxes)
                }

                val shiftedHighlights = userHighlights.map { highlight ->
                    if (highlight.pageIndex >= targetIndex) {
                        highlight.copy(pageIndex = highlight.pageIndex + 1)
                    } else {
                        highlight
                    }
                }
                if (shiftedHighlights != userHighlights.toList()) {
                    userHighlights.clear()
                    userHighlights.addAll(shiftedHighlights)
                }

                val tempNewPage = VirtualPage.BlankPage(generateShortId(), refWidth, refHeight, wasManuallyAdded = true)
                val optimisticPages = virtualPages.toMutableList()
                optimisticPages.add(targetIndex, tempNewPage)

                virtualPages = optimisticPages
                if (displayMode == DisplayMode.PAGINATION) {
                    pagerState.animateScrollToPage(targetIndex)
                } else {
                    verticalReaderState.scrollToPage(targetIndex)
                }

                Timber.tag("RichTextMigration").d("INSERT: Triggering RichTextController.insertPageBreakAt($targetIndex, count=2)")
                richTextController?.insertPageBreakAt(targetIndex, count = 2)

                val objectList = bookmarks.map { bookmark ->
                    JSONObject().apply {
                        put("pageIndex", bookmark.pageIndex)
                        put("title", bookmark.title)
                        put("totalPages", bookmark.totalPages)
                    }
                }
                val currentJson = JSONArray(objectList).toString()

                val result = viewModel.addPage(
                    bookId = currentBookId!!,
                    currentLayout = virtualPages - tempNewPage,
                    insertIndex = targetIndex,
                    currentAnnotations = allAnnotations,
                    currentBookmarksJson = currentJson,
                    referenceWidth = refWidth,
                    referenceHeight = refHeight,
                    wasManuallyAdded = true
                )

                Timber.tag("RichTextMigration").i("INSERT: Layout update complete. New virtualPages size: ${result.layout.size}")

                virtualPages = result.layout
                allAnnotations = result.annotations
                bookmarks = loadPdfBookmarksFromJson(result.bookmarksJson)
                onBookmarksChanged(result.bookmarksJson)

                snackbarHostState.showSnackbar("Page added at ${targetIndex + 1}")
            }
        }
    }

    val calculateSnappedPoint = remember(pageAspectRatios) {
        { pageIndex: Int, currentPoint: PdfPoint, startPoint: PdfPoint? ->
            if (startPoint == null) {
                currentPoint
            } else {
                val aspectRatio = pageAspectRatios.getOrElse(pageIndex) { 1f }

                val dx = (currentPoint.x - startPoint.x) * aspectRatio
                val dy = (currentPoint.y - startPoint.y)

                val angleRad = kotlin.math.atan2(dy, dx)
                val angleDeg = (angleRad * 180 / kotlin.math.PI)
                val absAngle = kotlin.math.abs(angleDeg)

                val threshold = 10.0

                val isHorizontal = absAngle < threshold || kotlin.math.abs(absAngle - 180.0) < threshold
                val isVertical = kotlin.math.abs(absAngle - 90.0) < threshold

                if (isHorizontal) {
                    currentPoint.copy(y = startPoint.y)
                } else if (isVertical) {
                    currentPoint.copy(x = startPoint.x)
                } else {
                    currentPoint
                }
            }
        }
    }

    val onDeletePage: () -> Unit = {
        coroutineScope.launch {
            if (currentBookId != null && currentPage in virtualPages.indices) {
                Timber.tag("RichTextMigration").i("DELETE: User requested deletion of page at index $currentPage")

                val boxesToKeep = textBoxes.filter { it.pageIndex != currentPage }
                val shiftedBoxes = boxesToKeep.map { box ->
                    if (box.pageIndex > currentPage) {
                        box.copy(pageIndex = box.pageIndex - 1)
                    } else {
                        box
                    }
                }
                textBoxes.clear()
                textBoxes.addAll(shiftedBoxes)

                val highlightsToKeep = userHighlights.filter { it.pageIndex != currentPage }
                val shiftedHighlights = highlightsToKeep.map { highlight ->
                    if (highlight.pageIndex > currentPage) {
                        highlight.copy(pageIndex = highlight.pageIndex - 1)
                    } else {
                        highlight
                    }
                }
                userHighlights.clear()
                userHighlights.addAll(shiftedHighlights)

                val objectList = bookmarks.map { bookmark ->
                    JSONObject().apply {
                        put("pageIndex", bookmark.pageIndex)
                        put("title", bookmark.title)
                        put("totalPages", bookmark.totalPages)
                    }
                }
                val currentJson = JSONArray(objectList).toString()

                val cleanedAnnotations = allAnnotations.filterKeys { it != currentPage }

                allAnnotations = cleanedAnnotations

                Timber.tag("RichTextMigration").d("DELETE: Wiping text and structural breaks for page $currentPage")
                richTextController?.deleteTextOnPage(currentPage)

                val result = viewModel.removePage(
                    currentBookId!!, virtualPages, currentPage, cleanedAnnotations, currentJson
                )
                Timber.tag("RichTextMigration").i("DELETE: Layout update complete. New virtualPages size: ${result.layout.size}")

                virtualPages = result.layout
                allAnnotations = result.annotations
                bookmarks = loadPdfBookmarksFromJson(result.bookmarksJson)
                onBookmarksChanged(result.bookmarksJson)

                snackbarHostState.showSnackbar("Page deleted")

                val newMax = (virtualPages.size - 1).coerceAtLeast(0)
                if (currentPage > newMax) {
                    if (displayMode == DisplayMode.PAGINATION) {
                        pagerState.scrollToPage(newMax)
                    } else {
                        verticalReaderState.scrollToPage(newMax)
                    }
                }
            }
        }
    }

    val onInsertTextBox = {
        val currentP = if (displayMode == DisplayMode.PAGINATION) pagerState.currentPage else verticalReaderState.currentPage

        Timber.tag("PdfTextBoxDebug").d("Viewer: onInsertTextBox triggered. Target Page: $currentP, DisplayMode: $displayMode")

        val defaultWidth = 0.4f
        val defaultHeight = 0.1f
        val startX = 0.3f
        val startY = 0.45f

        val newStyle = toolSettings.textStyle
        val fontSizeNorm = 0.02f

        val newBox = PdfTextBox(
            id = generateShortId(),
            pageIndex = currentP,
            relativeBounds = Rect(startX, startY, startX + defaultWidth, startY + defaultHeight),
            text = "",
            color = Color(newStyle.colorArgb),
            backgroundColor = Color(newStyle.backgroundColorArgb),
            fontSize = fontSizeNorm,
            isBold = newStyle.isBold,
            isItalic = newStyle.isItalic,
            isUnderline = newStyle.isUnderline,
            isStrikeThrough = newStyle.isStrikeThrough
        )

        textBoxes.add(newBox)
        Timber.tag("PdfTextBoxDebug").i("Viewer: Added TextBox [ID: ${newBox.id}] to list. Total boxes now: ${textBoxes.size}")
        selectedTextBoxId = newBox.id
        richTextController?.clearSelection()
        showBars = false
    }

    val onSingleTapStable = remember {
        {
            if (isAutoScrollModeActive) {
                isAutoScrollPlaying = !isAutoScrollPlaying
                Timber.d("PDF Auto-scroll toggled via tap: $isAutoScrollPlaying")
            }

            if (selectedTextBoxId != null) {
                val box = textBoxes.find { it.id == selectedTextBoxId }
                if (box != null && box.text.trim().isEmpty()) {
                    textBoxes.remove(box)
                }
                selectedTextBoxId = null
            } else {
                if (!isFullScreen && !(isMusicianMode && isAutoScrollModeActive))  {
                    showBars = !showBars
                    Timber.d("Vertical Reader Clicked. showBars now: $showBars")
                }
            }
        }
    }

    val highestRequiredTextPageIndex by remember(richTextController?.pageLayouts) {
        derivedStateOf {
            val maxIdx = richTextController?.pageLayouts?.maxOfOrNull { it.pageIndex } ?: -1
            Timber.tag("CursorDebug").v("Calc highestRequiredTextPageIndex: $maxIdx")
            maxIdx
        }
    }

    val hasTextOnPage = remember(richTextController?.pageLayouts) {
        { pageIndex: Int ->
            richTextController?.pageLayouts?.any {
                it.pageIndex == pageIndex && it.visibleText.isNotBlank()
            } == true
        }
    }

    LaunchedEffect(highestRequiredTextPageIndex, virtualPages.size, allAnnotations) {
        if (richTextController == null || !isDocumentReady) return@LaunchedEffect

        delay(500)

        @Suppress("UnusedVariable", "Unused") val lastPageIndex = virtualPages.size - 1
        val requiredPages = highestRequiredTextPageIndex + 1

        // Expansion Logic
        if (requiredPages > virtualPages.size) {
            Timber.tag("RichTextFlow").i("Text overflow detected. Required pages: $requiredPages, current: ${virtualPages.size}. Adding page.")

            val lastPage = virtualPages.lastOrNull()
            val (refWidth, refHeight) = when(lastPage) {
                is VirtualPage.PdfPage -> {
                    var w = 595; var h = 842
                    pdfDocument?.openPage(lastPage.pdfIndex)?.use { page ->
                        w = page.getPageWidthPoint()
                        h = page.getPageHeightPoint()
                    }
                    Pair(w, h)
                }
                is VirtualPage.BlankPage -> Pair(lastPage.width, lastPage.height)
                null -> Pair(595, 842)
            }

            val objectList = bookmarks.map { bookmark ->
                JSONObject().apply {
                    put("pageIndex", bookmark.pageIndex)
                    put("title", bookmark.title)
                    put("totalPages", bookmark.totalPages)
                }
            }
            val currentJson = JSONArray(objectList).toString()

            val result = viewModel.addPage(
                bookId = currentBookId!!,
                currentLayout = virtualPages,
                insertIndex = virtualPages.size,
                currentAnnotations = allAnnotations,
                currentBookmarksJson = currentJson,
                referenceWidth = refWidth,
                referenceHeight = refHeight,
                wasManuallyAdded = false // Auto-added page
            )

            virtualPages = result.layout
            allAnnotations = result.annotations
            bookmarks = loadPdfBookmarksFromJson(result.bookmarksJson)
            onBookmarksChanged(result.bookmarksJson)
        }
        // Contraction Logic
        else {
            var lastPage = virtualPages.lastOrNull()
            var currentLastIndex = virtualPages.size - 1
            var pageRemoved = false

            while (
                lastPage is VirtualPage.BlankPage &&
                !lastPage.wasManuallyAdded &&
                currentLastIndex > highestRequiredTextPageIndex &&
                !hasTextOnPage(currentLastIndex) &&
                allAnnotations[currentLastIndex].isNullOrEmpty() &&
                textBoxes.none { it.pageIndex == currentLastIndex } &&
                userHighlights.none { it.pageIndex == currentLastIndex }
            ) {
                Timber.tag("RichTextFlow").i("Auto-pruning empty page at index $currentLastIndex.")
                pageRemoved = true

                val objectList = bookmarks.map {
                    JSONObject().apply {
                        put("pageIndex", it.pageIndex)
                        put("title", it.title)
                        put("totalPages", it.totalPages)
                    }
                }
                val currentJson = JSONArray(objectList).toString()

                val result = viewModel.removePage(
                    currentBookId!!, virtualPages, currentLastIndex, allAnnotations, currentJson
                )

                virtualPages = result.layout
                allAnnotations = result.annotations
                bookmarks = loadPdfBookmarksFromJson(result.bookmarksJson)
                onBookmarksChanged(result.bookmarksJson)

                currentLastIndex--
                lastPage = virtualPages.getOrNull(currentLastIndex)
            }

            if (pageRemoved) {
                snackbarHostState.showSnackbar("Extra page removed")
            }
        }
    }

    LaunchedEffect(isDocumentReady) {
        if (isDocumentReady && !initialScrollDone) {
            val pageCount = pagerState.pageCount
            if (pageCount > 0) {
                val targetPage = initialPage?.coerceIn(0, pageCount - 1) ?: 0
                Timber.d("Initial Setup: Document is ready. Target page: $targetPage.")

                coroutineScope.launch {
                    if (pagerState.currentPage != targetPage) {
                        when (displayMode) {
                            DisplayMode.PAGINATION -> {
                                Timber.d("Initial Setup: Animating scroll to page $targetPage.")
                                pagerState.scrollToPage(targetPage)
                            }

                            DisplayMode.VERTICAL_SCROLL -> {
                                Timber.d("Initial Setup: Snapping scroll to item $targetPage.")
                                verticalReaderState.snapToPage(targetPage)
                                pagerState.scrollToPage(targetPage)
                            }
                        }
                    }
                    initialScrollDone = true
                    Timber.d("Initial Setup: Scroll complete. Page saving is now enabled.")
                }
            } else {
                Timber.w(
                    "Initial Setup: Document is ready, but pager pageCount is still 0. This may happen on rapid open/close. Scroll will be skipped."
                )
            }
        }
    }

    LaunchedEffect(isDocumentReady, currentBookId) {
        if (isDocumentReady && currentBookId != null && totalPages > 0) {
            val layout = viewModel.loadPageLayout(currentBookId!!, totalPages)
            virtualPages = layout

            if (initialPage != null && initialPage >= totalPages && initialPage < layout.size) {
                Timber.d("Restoring position to added page: $initialPage")
                if (displayMode == DisplayMode.PAGINATION) {
                    pagerState.scrollToPage(initialPage)
                } else {
                    verticalReaderState.scrollToPage(initialPage)
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        Timber.d("Pager state changed: pagerState.currentPage is now ${pagerState.currentPage}")
    }

    LaunchedEffect(displayMode) {
        coroutineScope.launch {
            if (displayMode == DisplayMode.VERTICAL_SCROLL) {
                val pageToScroll = pagerState.currentPage
                verticalReaderState.scrollToPage(pageToScroll)
            } else {
                val pageToScroll = verticalReaderState.currentPage
                pagerState.scrollToPage(pageToScroll)
            }
        }
    }

    val isBookmarked by remember(
        bookmarks, pagerState.currentPage, verticalReaderState.currentPage, displayMode
    ) {
        derivedStateOf {
            val currentPage = if (displayMode == DisplayMode.PAGINATION) {
                pagerState.currentPage
            } else {
                verticalReaderState.currentPage
            }
            bookmarks.any { it.pageIndex == currentPage }
        }
    }

    LaunchedEffect(currentPageScale) {
        if (currentPageScale != 1f) {
            showZoomIndicator = true
            delay(1500)
            showZoomIndicator = false
        } else {
            showZoomIndicator = false
        }
    }

    val onToggleBookmark: (Int) -> Unit = { pageIndex ->
        coroutineScope.launch {
            Timber.d("onToggleBookmark triggered for page index: $pageIndex")

            if (bookmarks.any { it.pageIndex == pageIndex }) {
                Timber.d("Bookmark exists. Removing...")
                bookmarks = bookmarks.filterNot { it.pageIndex == pageIndex }.toSet()
            } else {
                Timber.d("Creating new bookmark. Attempting text extraction...")
                var extractedText = ""

                if (pdfDocument != null) {
                    try {
                        withContext(Dispatchers.IO) {
                            pdfDocument!!.openPage(pageIndex).use { page ->
                                page.openTextPage().use { textPage ->
                                    val count = textPage.textPageCountChars()

                                    if (count > 0) {
                                        // Attempt to get text
                                        val rawText = textPage.textPageGetText(0, min(count, 200))
                                        extractedText = rawText ?: ""
                                    } else {
                                        Timber.w(
                                            "Pdfium: Character count is 0. Page might be image-only."
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Bookmark: Direct text extraction failed")
                    }
                } else {
                    Timber.e("pdfDocument is null. Cannot extract text.")
                }

                if (extractedText.isBlank() && currentBookId != null && pdfDocument != null) {
                    Timber.d("Extracted text is blank. Attempting repository/OCR fallback...")
                    try {
                        extractedText = pdfTextRepository.getOrExtractText(
                            currentBookId!!, pdfDocument!!, pageIndex
                        )
                        Timber.d("Repository: Extracted text length: ${extractedText.length}")
                    } catch (e: Exception) {
                        Timber.w(e, "Bookmark: Repository extraction failed")
                    }
                }

                val cleanText = extractedText.replace("\\s+".toRegex(), " ").trim()
                val words = cleanText.split(" ").filter { it.isNotBlank() }

                val contentTitle = if (words.isNotEmpty()) {
                    words.take(6).joinToString(" ") + "..."
                } else {
                    Timber.d("No words found. Falling back to 'Page X' title.")
                    "Page ${pageIndex + 1}"
                }

                val chapterTitle =
                    flatTableOfContents.lastOrNull { it.pageIndex <= pageIndex }?.title

                val finalTitle = if (!chapterTitle.isNullOrBlank()) {
                    "$contentTitle\n$chapterTitle"
                } else {
                    contentTitle
                }

                Timber.d("Final Bookmark Title: '$finalTitle'")

                bookmarks = bookmarks + PdfBookmark(
                    pageIndex = pageIndex, title = finalTitle, totalPages = totalPages
                )
            }
        }
    }

    val reflowInfo by viewModel.reflowWorkInfo.collectAsState(initial = null)

    val isReflowingThisBook by remember(reflowInfo, bookId) {
        derivedStateOf {
            reflowInfo?.tags?.contains("book_$bookId") == true &&
                    (reflowInfo?.state == WorkInfo.State.RUNNING || reflowInfo?.state == WorkInfo.State.ENQUEUED)
        }
    }

    val reflowProgressValue by remember(reflowInfo, isReflowingThisBook) {
        derivedStateOf {
            if (isReflowingThisBook) {
                reflowInfo?.progress?.getFloat(ReflowWorker.KEY_PROGRESS, 0f) ?: 0f
            } else 0f
        }
    }

    val onBookmarkClick: () -> Unit = {
        val currentPage = if (displayMode == DisplayMode.PAGINATION) {
            pagerState.currentPage
        } else {
            verticalReaderState.currentPage
        }
        onToggleBookmark(currentPage)
    }

    LaunchedEffect(pdfUri) { debugPdfLinks(context, pdfUri, pdfiumCore, this) }

    LaunchedEffect(currentBookId) {
        if (currentBookId != null) {
            val loaded = annotationRepository.loadAnnotations(currentBookId!!)
            allAnnotations = loaded
            areAnnotationsLoaded = true

            val loadedBoxes = textBoxRepository.loadTextBoxes(currentBookId!!)
            textBoxes.clear()
            textBoxes.addAll(loadedBoxes)

            val loadedHighlights = highlightRepository.loadHighlights(currentBookId!!)
            userHighlights.clear()
            userHighlights.addAll(loadedHighlights)
        }
    }

    var pendingSaveMode by remember { mutableStateOf<SaveMode?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && pendingSaveMode != null) {
            when (pendingSaveMode) {
                SaveMode.ANNOTATED -> {
                    if (currentBookId != null) {
                        coroutineScope.launch {
                            val currentRichTextLayouts = richTextController?.pageLayouts

                            Timber.tag("PdfExportDebug").i("SAVE TRIGGERED: userHighlights count: ${userHighlights.size}")
                            if (userHighlights.isEmpty()) {
                                Timber.tag("PdfExportDebug").w("Warning: userHighlights is EMPTY during save.")
                            }

                            viewModel.savePdfWithAnnotations(
                                sourceUri = pdfUri,
                                destUri = uri,
                                annotations = allAnnotations,
                                richTextPageLayouts = currentRichTextLayouts,
                                textBoxes = textBoxes.toList(),
                                highlights = userHighlights.toList(),
                                bookId = currentBookId!!
                            )
                        }
                    }
                }

                SaveMode.ORIGINAL -> {
                    viewModel.saveOriginalPdf(pdfUri, uri)
                }

                else -> {}
            }
        }
        pendingSaveMode = null
    }

    var showShareDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var isShareLoading by remember { mutableStateOf(false) }

    var ocrUsedForCurrentPageTts by remember { mutableStateOf(false) }

    var showSummarizationPopup by remember { mutableStateOf(false) }
    var summarizationResult by remember { mutableStateOf<SummarizationResult?>(null) }
    var isSummarizationLoading by remember { mutableStateOf(false) }

    var isPageSliderVisible by remember { mutableStateOf(false) }
    var sliderStartPage by remember { mutableIntStateOf(0) }
    var sliderCurrentPage by remember { mutableFloatStateOf(0f) }
    var isFastScrubbing by remember { mutableStateOf(false) }
    val scrubDebounceJob = remember { mutableStateOf<Job?>(null) }
    var startPageThumbnail by remember { mutableStateOf<Bitmap?>(null) }

    val speakerPlayer =
        remember(context, coroutineScope) { SpeakerSamplePlayer(context, coroutineScope) }

    var clickedLinkUrl by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION") val clipboardManager = LocalClipboardManager.current

    val ttsController = rememberTtsController()
    val ttsState by ttsController.ttsState.collectAsState()
    ttsState.currentText

    var showRenameBookmarkDialog by remember { mutableStateOf<PdfBookmark?>(null) }

    var isOcrModelDownloading by remember { mutableStateOf(false) }

    LaunchedEffect(isOcrModelDownloading) {
        if (isOcrModelDownloading) {
            delay(10_000)
            isOcrModelDownloading = false
        }
    }

    val saveStateAndExit = {
        val activePage = richTextController?.activePageIndex ?: -1
        Timber.tag("RichTextFlow").i("System Exit: isEditMode=$isEditMode, richActivePage=$activePage")

        if (isLoadingDocument) {
            onNavigateBack()
        } else {
            ttsController.stop()

            coroutineScope.launch {
                if (richTextController != null) {
                    withContext(NonCancellable) {
                        Timber.tag("RichTextFlow").d("Forcing RichTextController immediate sync and save...")
                        richTextController.saveImmediate()
                    }
                }

                saveAllData(true).join()

                Timber.tag("AnnotationSync").d("Save complete. Navigating back.")
                onNavigateBack()
            }
        }
    }

    val onZoomChangeStable = remember { { scale: Float -> currentPageScale = scale } }

    val onHighlightLoadingStable = remember {
        { isLoading: Boolean -> isHighlightingLoading = isLoading }
    }

    val onShowDictionaryUpsellDialogStable = remember(useOnlineDictionary) {
        {
            if (useOnlineDictionary) {
                showDictionaryUpsellDialog = true
            }
        }
    }

    val onDictionaryLookupStable = remember(isProUser, executeWithOcrCheck, useOnlineDictionary, selectedDictPackage) {
        { text: String ->
            executeWithOcrCheck {
                val isOss = BuildConfig.FLAVOR == "oss"
                val effectiveUseOnline = !isOss && useOnlineDictionary

                if (effectiveUseOnline) {
                    val wordCount = countWords(text)
                    if (isProUser || wordCount <= 1) {
                        selectedTextForAi = text
                        showAiDefinitionPopup = true
                        coroutineScope.launch {
                            isAiDefinitionLoading = true
                            aiDefinitionResult = null
                            fetchAiDefinition(
                                text = text,
                                onUpdate = { chunk ->
                                    val currentDefinition = aiDefinitionResult?.definition ?: ""
                                    aiDefinitionResult = AiDefinitionResult(
                                        definition = currentDefinition + chunk
                                    )
                                },
                                onError = { error ->
                                    aiDefinitionResult = AiDefinitionResult(error = error)
                                },
                                onFinish = {
                                    isAiDefinitionLoading = false
                                }
                            )
                        }
                    } else {
                        showDictionaryUpsellDialog = true
                    }
                } else {
                    if (selectedDictPackage != null) {
                        ExternalDictionaryHelper.launchDictionary(context, selectedDictPackage!!, text)
                    } else {
                        Toast.makeText(context, "Please select a dictionary app first.", Toast.LENGTH_SHORT).show()
                        showDictionarySettingsSheet = true
                    }
                }
            }
        }
    }

    val onLinkClickedStable = remember { { url: String -> clickedLinkUrl = url } }

    val onInternalLinkNavStable = remember(displayMode) {
        { targetPage: Int ->
            coroutineScope.launch {
                if (targetPage in 0 until totalPages) {
                    if (displayMode == DisplayMode.PAGINATION) {
                        pagerState.animateScrollToPage(targetPage)
                    } else {
                        verticalReaderState.scrollToPage(targetPage)
                    }
                }
            }
            Unit
        }
    }

    val onBookmarkClickStable =
        remember(bookmarks, pdfDocument, currentBookId, flatTableOfContents, totalPages) {
            { pageIndex: Int -> onToggleBookmark(pageIndex) }
        }

    val onOcrStateChangeStable = remember {
        { isScanning: Boolean -> onOcrStateChange(isScanning) }
    }

    val onGetOcrSearchRectsStable = remember(pdfTextRepository, pdfDocument) {
        val callback: suspend (Int, String) -> List<RectF> = { page, query ->
            if (pdfDocument != null) {
                val hasNative = pdfTextRepository.hasNativeText(pdfDocument!!, page)
                if (!hasNative) {
                    pdfTextRepository.getOcrSearchRects(
                        document = pdfDocument!!,
                        pageIndex = page,
                        query = query,
                        onModelDownloading = { isOcrModelDownloading = true })
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
        callback
    }

    suspend fun summarizeCurrentPage(
        onUpdate: (SummarizationResult) -> Unit, onFinish: () -> Unit
    ) {
        val currentPageIndex = currentPage
        val virtualPage =
            if (virtualPages.isNotEmpty() && currentPageIndex in virtualPages.indices) {
                virtualPages[currentPageIndex]
            } else {
                null
            }

        if (virtualPage is VirtualPage.BlankPage) {
            onUpdate(SummarizationResult(error = "Cannot summarize a blank page."))
            onFinish()
            return
        }

        val pdfPageIndex = (virtualPage as? VirtualPage.PdfPage)?.pdfIndex ?: currentPageIndex

        val doc = pdfDocument ?: run {
            onUpdate(SummarizationResult(error = "Document not loaded."))
            onFinish()
            return
        }
        Timber.d(
            "Starting summarization for PDF page: $pdfPageIndex (Display Page: $currentPageIndex)"
        )

        withContext(Dispatchers.IO) {
            var pageBitmap: Bitmap? = null
            var connection: HttpURLConnection? = null
            try {
                pageBitmap = renderPageToBitmap(doc, pdfPageIndex)
                if (pageBitmap == null) {
                    throw Exception("Could not render page to bitmap.")
                }

                val outputStream = ByteArrayOutputStream()
                pageBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                val imageBytes = outputStream.toByteArray()
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                pageBitmap.recycle()
                pageBitmap = null

                val url = URL(summarizationUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 15000
                connection.readTimeout = 180000
                connection.doOutput = true
                connection.doInput = true

                val jsonPayload = JSONObject().apply {
                    put("content_type", "image")
                    put("data", base64Image)
                }
                connection.outputStream.use { os ->
                    os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                Timber.d("Summarization API response code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val fullText = StringBuilder()
                    var lastResult: SummarizationResult? = null
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            try {
                                val jsonResponse = JSONObject(line!!)
                                jsonResponse.optString("chunk").takeIf { it.isNotEmpty() }?.let {
                                    fullText.append(it)
                                    lastResult = SummarizationResult(summary = fullText.toString())
                                    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION") onUpdate(lastResult!!)
                                }
                                jsonResponse.optString("error").takeIf { it.isNotEmpty() }?.let {
                                    lastResult = SummarizationResult(error = it)
                                    onUpdate(lastResult)
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "Could not parse stream line: $line")
                            }
                        }
                    }
                    if (fullText.isEmpty() && lastResult?.error == null) {
                        onUpdate(
                            SummarizationResult(
                                error = "Failed to parse summary from server response."
                            )
                        )
                    }
                } else {
                    val errorBody = try {
                        connection.errorStream?.bufferedReader()?.use { it.readText() }
                    } catch (_: Exception) {
                        null
                    }
                    Timber.e("Summarization API error: $responseCode. Body: $errorBody")
                    val errorDetail = try {
                        errorBody?.let { JSONObject(it).getString("detail") }
                    } catch (_: Exception) {
                        "Could not fetch summary."
                    }
                    onUpdate(
                        SummarizationResult(
                            error = "Error: $responseCode. ${errorDetail ?: "An unknown server error occurred."}"
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception during PDF page summarization: ${e.message}")
                onUpdate(SummarizationResult(error = "An error occurred: ${e.localizedMessage}"))
            } finally {
                pageBitmap?.recycle()
                connection?.disconnect()
                onFinish()
            }
        }
    }

    fun isAnnotationHit(
        annotation: PdfAnnotation,
        hitPoint: PdfPoint,
        pageAspectRatio: Float,
        threshold: Float = 0.025f
    ): Boolean {
        if (annotation.points.isEmpty()) return false

        if (annotation.points.size == 1) {
            val p = annotation.points[0]
            val dx = (p.x - hitPoint.x) * pageAspectRatio
            val dy = (p.y - hitPoint.y)
            return (dx * dx + dy * dy) < (threshold * threshold)
        }

        for (i in 0 until annotation.points.size - 1) {
            val a = annotation.points[i]
            val b = annotation.points[i + 1]

            val pax = (hitPoint.x - a.x) * pageAspectRatio
            val pay = (hitPoint.y - a.y)
            val bax = (b.x - a.x) * pageAspectRatio
            val bay = (b.y - a.y)

            val segmentLenSq = (bax * bax + bay * bay).coerceAtLeast(1e-6f)
            val t = (pax * bax + pay * bay) / segmentLenSq
            val tClamped = t.coerceIn(0f, 1f)

            val closestX = bax * tClamped
            val closestY = bay * tClamped

            val distSq = (pax - closestX) * (pax - closestX) + (pay - closestY) * (pay - closestY)

            if (distSq < (threshold * threshold)) return true
        }

        return false
    }

    fun startTts(pageToReadOverride: Int? = null) {
        Timber.d("TTS button clicked: Starting TTS for current page")
        if (pdfDocument == null || totalPages == 0) {
            return
        }
        coroutineScope.launch {
            val pageToRead = pageToReadOverride ?: currentPage
            var rawPageText: String? = null
            var tempPage: PdfPageKt? = null
            var tempTextPage: PdfTextPageKt? = null
            var ocrAttempted = false

            try {
                withContext(Dispatchers.IO) {
                    Timber.d("TTS: Opening page $pageToRead for Pdfium text extraction.")
                    tempPage = pdfDocument!!.openPage(pageToRead)
                    tempTextPage = tempPage.openTextPage()
                    val charCount = tempTextPage.textPageCountChars()
                    if (charCount > 0) {
                        rawPageText = tempTextPage.textPageGetText(0, charCount)?.trim()
                        if (rawPageText.isNullOrBlank()) {
                            Timber.d(
                                "TTS: Pdfium extracted text but it's blank (charCount: $charCount)."
                            )
                        } else {
                            Timber.d(
                                "TTS: Text extracted via Pdfium (length: ${rawPageText.length})."
                            )
                        }
                    } else {
                        Timber.d(
                            "TTS: No characters found by Pdfium (charCount is 0) for page $pageToRead."
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "TTS: Error extracting text via Pdfium for page $pageToRead")
            } finally {
                withContext(Dispatchers.IO) { tempTextPage?.close() }
            }

            if (rawPageText.isNullOrBlank()) {
                Timber.i(
                    "TTS: Pdfium text is blank or extraction failed. Attempting OCR for page $pageToRead."
                )
                ocrAttempted = true
                ocrUsedForCurrentPageTts = true
                var ocrBitmap: Bitmap? = null
                try {
                    if (tempPage == null) {
                        withContext(Dispatchers.IO) {
                            tempPage?.close()
                            Timber.d("TTS/OCR: Re-opening page $pageToRead for bitmap rendering.")
                            tempPage = pdfDocument!!.openPage(pageToRead)
                        }
                    }

                    val pageForOcr = tempPage ?: throw IllegalStateException(
                        "TTS/OCR: PDF page couldn't be opened for OCR."
                    )

                    val ocrBitmapWidth = 1080
                    val ocrBitmapHeight: Int

                    withContext(Dispatchers.IO) {
                        val originalWidthPoints = pageForOcr.getPageWidthPoint()
                        val originalHeightPoints = pageForOcr.getPageHeightPoint()

                        if (originalWidthPoints <= 0 || originalHeightPoints <= 0) {
                            throw IllegalStateException(
                                "TTS/OCR: Invalid page dimensions (points) from Pdfium for page $pageToRead."
                            )
                        }
                        val aspectRatio =
                            originalWidthPoints.toFloat() / originalHeightPoints.toFloat()
                        ocrBitmapHeight = (ocrBitmapWidth / aspectRatio).toInt()

                        if (ocrBitmapHeight <= 0) {
                            throw IllegalStateException(
                                "TTS/OCR: Calculated invalid bitmap dimensions for OCR ($ocrBitmapWidth x $ocrBitmapHeight) for page $pageToRead."
                            )
                        }

                        Timber.d(
                            "TTS/OCR: Rendering page $pageToRead to bitmap of size ${ocrBitmapWidth}x$ocrBitmapHeight."
                        )
                        ocrBitmap = createBitmap(ocrBitmapWidth, ocrBitmapHeight)
                        pageForOcr.renderPageBitmap(
                            bitmap = ocrBitmap,
                            startX = 0,
                            startY = 0,
                            drawSizeX = ocrBitmapWidth,
                            drawSizeY = ocrBitmapHeight,
                            renderAnnot = false
                        )
                    }
                    Timber.d("TTS/OCR: Bitmap rendered for page $pageToRead. Attempting OCR.")

                    rawPageText = OcrHelper.extractTextFromBitmap(ocrBitmap!!) {
                        isOcrModelDownloading = true
                    }?.text

                    if (!rawPageText.isNullOrBlank()) {
                        Timber.i(
                            "TTS: Text extracted via OCR for page $pageToRead (length: ${rawPageText?.length})."
                        )
                    } else {
                        Timber.w(
                            "TTS: OCR process completed for page $pageToRead but returned no text or blank text."
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "TTS: Error during OCR process for page $pageToRead")
                } finally {
                    ocrBitmap?.recycle()
                    withContext(Dispatchers.IO) {
                        tempPage?.close()
                        Timber.d("TTS/OCR: Closed page $pageToRead after OCR attempt.")
                    }
                }
            } else {
                ocrUsedForCurrentPageTts = false
                withContext(Dispatchers.IO) {
                    tempPage?.close()
                    Timber.d(
                        "TTS: Closed page $pageToRead after successful Pdfium text extraction."
                    )
                }
            }

            if (rawPageText != null && rawPageText!!.isNotBlank()) {
                val processedText = preprocessTextForTts(rawPageText!!)
                ttsPageData = TtsPageData(pageToRead, processedText, ocrUsedForCurrentPageTts)

                val chunks = splitTextIntoChunks(processedText.cleanText)

                val bookTitle = pdfDocument?.getDocumentMeta()?.title?.takeIf { it.isNotBlank() }
                    ?: pdfUri.lastPathSegment ?: "PDF Document"
                val pageTitle = "Page ${pageToRead + 1}"

                val ttsChunks = chunks.mapIndexed { index, text -> TtsChunk(text, "", index) }

                ttsController.start(
                    chunks = ttsChunks,
                    bookTitle = bookTitle,
                    chapterTitle = pageTitle,
                    coverImageUri = null,
                    ttsMode = currentTtsMode,
                    playbackSource = "READER"
                )

                if (isAutoPagingForTts) {
                    delay(500)
                    isAutoPagingForTts = false
                }
            } else {
                val finalError = when {
                    ocrAttempted -> "OCR found no text on this page."
                    else -> "Page seems empty or text not extractable."
                }
                ttsController.stop()
                isAutoPagingForTts = false
                Timber.w("TTS start failed: $finalError")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(), onResult = { _ -> startTts() })

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            Timber.d("Disposing sample MediaPlayer.")
            speakerPlayer.release()
            PdfBitmapPool.clear()
            PdfThumbnailCache.clear()
        }
    }

    LaunchedEffect(ttsState.sessionFinished) {
        if (ttsState.sessionFinished && ttsState.playbackSource == "READER") {
            val lastPlayedPage = ttsPageData?.pageIndex ?: (currentPage - 1)
            val nextPage = lastPlayedPage + 1
            if (nextPage < totalPages) {
                when (displayMode) {
                    DisplayMode.PAGINATION -> {
                        Timber.d("TTS auto-paging to next page: ${nextPage + 1}")
                        isAutoPagingForTts = true
                        coroutineScope.launch { pagerState.animateScrollToPage(nextPage) }
                    }

                    DisplayMode.VERTICAL_SCROLL -> {
                        Timber.d("TTS auto-starting on next page (no scroll): ${nextPage + 1}")
                        startTts(pageToReadOverride = nextPage)
                    }
                }
            } else {
                Timber.d("TTS finished on the last page.")
            }
        }
    }

    LaunchedEffect(isPageSliderVisible) {
        if (isPageSliderVisible) {
            val doc = pdfDocument
            if (doc != null && totalPages > 0) {
                Timber.d("Slider visible. Rendering thumbnail for page $sliderStartPage")
                startPageThumbnail = renderPageToBitmap(doc, sliderStartPage)
                Timber.d(
                    "Thumbnail rendering complete. Is bitmap null: ${startPageThumbnail == null}"
                )
            }
        } else {
            Timber.d("Slider hidden. Clearing thumbnail.")
            startPageThumbnail?.recycle()
            startPageThumbnail = null
        }
    }

    LaunchedEffect(ttsState.currentText, ttsPageData, ttsState.startOffsetInSource) {
        val currentText = ttsState.currentText
        val currentTtsData = ttsPageData
        val chunkIndex = ttsState.startOffsetInSource

        if (currentText == null || currentTtsData == null) {
            ttsHighlightData = null
            return@LaunchedEffect
        }

        if (currentTtsData.fromOcr) {
            ttsHighlightData = TtsHighlightData.Ocr(currentText)
        } else {
            var cleanStartIndex = -1

            if (chunkIndex >= 0) {
                val chunks = splitTextIntoChunks(currentTtsData.processedText.cleanText)
                if (chunkIndex < chunks.size) {
                    var runningIndex = 0
                    for (i in 0 until chunkIndex) {
                        val prevChunk = chunks[i]
                        val foundAt = currentTtsData.processedText.cleanText.indexOf(
                            prevChunk, runningIndex
                        )
                        if (foundAt != -1) {
                            runningIndex = foundAt + prevChunk.length
                        }
                    }
                    cleanStartIndex = currentTtsData.processedText.cleanText.indexOf(
                        currentText, runningIndex
                    )
                }
            }

            if (cleanStartIndex == -1) {
                cleanStartIndex = currentTtsData.processedText.cleanText.indexOf(currentText)
            }

            if (cleanStartIndex != -1) {
                val cleanEndIndex = cleanStartIndex + currentText.length
                if (cleanEndIndex <= currentTtsData.processedText.indexMap.size) {
                    val originalStartIndex = currentTtsData.processedText.indexMap[cleanStartIndex]
                    val originalEndIndex = currentTtsData.processedText.indexMap[cleanEndIndex - 1]
                    val originalLength = originalEndIndex - originalStartIndex + 1
                    ttsHighlightData = TtsHighlightData.Pdfium(originalStartIndex, originalLength)
                } else {
                    ttsHighlightData = null
                }
            } else {
                ttsHighlightData = null
            }
        }
    }

    LaunchedEffect(pdfUri, pdfiumCore, documentPassword) {
        Timber.d("LaunchedEffect: Loading PDF document for URI: $pdfUri")
        isLoadingDocument = true
        isDocumentReady = false
        errorMessage = null

        if (showPasswordDialog) isPasswordError = false

        ttsController.stop()
        ocrUsedForCurrentPageTts = false
        flatTableOfContents = emptyList()

        val fastId = getFastFileId(context, pdfUri)
        val selectedId = uiState.selectedBookId

        if (selectedId != null && selectedId != fastId) {
            Timber.tag("FolderAnnotationSync").i("Detected ID mismatch. Legacy: $fastId, Selected: $selectedId. Initiating migration.")
            viewModel.checkAndMigrateLegacyBookId(fastId, selectedId)
            currentBookId = selectedId
        } else {
            currentBookId = fastId
        }

        val oldDoc = pdfDocument
        val oldPfd = pfdState

        pdfDocument = null
        pfdState = null
        totalPages = 0

        if (oldDoc != null || oldPfd != null) {
            withContext(Dispatchers.IO) {
                oldDoc?.let {
                    try {
                        it.close()
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                }
                oldPfd?.let {
                    try {
                        it.close()
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                }
            }
        }

        var currentPfdOpened: ParcelFileDescriptor? = null
        try {
            withContext(Dispatchers.IO) {
                Timber.d("Opening ParcelFileDescriptor for URI: $pdfUri")
                currentPfdOpened = context.contentResolver.openFileDescriptor(pdfUri, "r")
                if (currentPfdOpened == null) throw Exception("Failed to open ParcelFileDescriptor")

                val doc = pdfiumCore.newDocument(currentPfdOpened, documentPassword)

                if (!isActive) {
                    doc.close()
                    currentPfdOpened.close()
                    return@withContext
                }

                pdfDocument = doc
                pfdState = currentPfdOpened
                val pagesCount = doc.getPageCount()
                totalPages = pagesCount

                if (pagesCount > 0) {
                    val cachedRatios = pdfTextRepository.getPageRatios(currentBookId!!)

                    val ratios = if (cachedRatios != null && cachedRatios.size == pagesCount) {
                        Timber.i("Loaded ${cachedRatios.size} page ratios from cache.")
                        cachedRatios
                    } else {
                        val computedRatios = ArrayList<Float>(pagesCount)
                        doc.openPage(0).use { page ->
                            val width = page.getPageWidthPoint()
                            val height = page.getPageHeightPoint()
                            val ratio = if (height > 0) width.toFloat() / height.toFloat()
                            else 1.0f
                            repeat(pagesCount) { computedRatios.add(ratio) }
                        }

                        launch(Dispatchers.IO) {
                            val refinedRatios = ArrayList<Float>(computedRatios)
                            var hasChanges = false

                            for (i in 0 until pagesCount) {
                                if (!isActive) break
                                try {
                                    doc.openPage(i).use { page ->
                                        val width = page.getPageWidthPoint()
                                        val height = page.getPageHeightPoint()
                                        val ratio =
                                            if (height > 0) width.toFloat() / height.toFloat()
                                            else 1.0f

                                        if (refinedRatios[i] != ratio) {
                                            refinedRatios[i] = ratio
                                            hasChanges = true
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.w(e, "Failed to calculate ratio for page $i")
                                }
                            }

                            if (hasChanges && isActive) {
                                withContext(Dispatchers.Main) {
                                    pageAspectRatios = ArrayList(refinedRatios)
                                }
                                // Save to cache
                                pdfTextRepository.savePageRatios(
                                    currentBookId!!, refinedRatios
                                )
                            }
                        }
                        computedRatios
                    }

                    pageAspectRatios = ratios
                    isDocumentReady = true
                    isLoadingDocument = false

                    withContext(Dispatchers.Main) {
                        showPasswordDialog = false
                        isPasswordError = false
                    }

                    launch(Dispatchers.IO) {
                        val refinedRatios = ArrayList<Float>(pageAspectRatios)
                        var hasChanges = false

                        for (i in 1 until pagesCount) {
                            if (!isActive) break
                            try {
                                doc.openPage(i).use { page ->
                                    val width = page.getPageWidthPoint()
                                    val height = page.getPageHeightPoint()
                                    val ratio = if (height > 0) width.toFloat() / height.toFloat()
                                    else 1.0f

                                    if (refinedRatios[i] != ratio) {
                                        refinedRatios[i] = ratio
                                        hasChanges = true
                                    }
                                }

                                if (hasChanges && (i % 500 == 0 || i == pagesCount - 1)) {
                                    withContext(Dispatchers.Main) {
                                        pageAspectRatios = ArrayList(refinedRatios)
                                    }
                                    hasChanges = false
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to calculate ratio for page $i")
                            }
                        }
                    }

                    launch(Dispatchers.IO) {
                        try {
                            val tableOfContents = doc.getTableOfContents()
                            val flattened = flattenToc(tableOfContents)
                            withContext(Dispatchers.Main) { flatTableOfContents = flattened }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to load TOC")
                        }
                    }
                } else {
                    isDocumentReady = true
                    isLoadingDocument = false
                }

                Timber.i("PDF document loaded optimistically. Total Pages: $totalPages.")
            }
        } catch (e: Exception) {
            if (e is PdfPasswordException || e.cause is PdfPasswordException) {
                Timber.w("PDF is password protected or password incorrect.")
                withContext(Dispatchers.Main) {
                    if (documentPassword != null) {
                        isPasswordError = true
                        showPasswordDialog = true
                    } else {
                        showPasswordDialog = true
                    }
                    isLoadingDocument = false
                }
            } else {
                Timber.e(e, "Error loading PDF document")
                errorMessage = "Error loading PDF: ${e.localizedMessage}"
                isLoadingDocument = false
            }
            if (pdfDocument == null) {
                currentPfdOpened?.let {
                    try {
                        it.close()
                    } catch (_: Exception) {
                    }
                }
                pfdState = null
            }
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress && showBars) {
            showBars = false
            Timber.d("Pager scroll detected, hiding bars.")
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            if (showBars) {
                showBars = false
                Timber.d("Pager scroll detected, hiding bars.")
            }
            if (displayMode == DisplayMode.PAGINATION && !isAutoPagingForTts && (ttsState.isPlaying || ttsState.isLoading)) {
                ttsController.stop()
            }
        }
    }

    var previousPage by remember(displayMode) { mutableIntStateOf(-1) }
    LaunchedEffect(currentPage) {
        if (previousPage != -1 && previousPage != currentPage) {
            if (isAutoPagingForTts) {
                startTts()
            } else if (displayMode == DisplayMode.PAGINATION && (ttsState.isPlaying || ttsState.isLoading)) {
                Timber.d("Page changed manually while TTS active, stopping.")
                ttsController.stop()
            }
        }
        previousPage = currentPage
    }

    LaunchedEffect(pagerState.currentPage) {
        currentPageScale = 1f
        ocrUsedForCurrentPageTts = false
    }

    DisposableEffect(Unit) {
        onDispose {
            Timber.d("DisposableEffect: Screen disposing. Closing PDF document and PFD.")
            ttsController.stop()
            PdfBitmapPool.clear()
            PdfThumbnailCache.clear()
            val docToClose = pdfDocument
            val pfdToClose = pfdState
            pdfDocument = null
            pfdState = null

            if (docToClose != null || pfdToClose != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    docToClose?.let {
                        Timber.d("Closing PDF document in onDispose.")
                        try {
                            it.close()
                        } catch (e: Exception) {
                            Timber.e(e, "Error closing document in onDispose")
                        }
                    }
                    pfdToClose?.let {
                        Timber.d("Closing ParcelFileDescriptor in onDispose: $it")
                        try {
                            it.close()
                        } catch (e: Exception) {
                            Timber.e(e, "Error closing ParcelFileDescriptor in onDispose")
                        }
                    }
                }
            }
        }
    }

    var searchHighlightTarget by remember { mutableStateOf<SearchResult?>(null) }

    var isOcrScanning by remember { mutableStateOf(false) }

    LaunchedEffect(pdfUri, currentBookId, totalPages) {
        if (currentBookId == null || totalPages == 0) return@LaunchedEffect

        if (isBackgroundIndexing && backgroundIndexingProgress > 0f) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val storedLang = pdfTextRepository.getBookLanguage(currentBookId!!)
            if (storedLang == null) {
                pdfTextRepository.setBookLanguage(currentBookId!!, ocrLanguage.name)
            }

            isBackgroundIndexing = true
            var bgPfd: ParcelFileDescriptor? = null
            var bgDoc: PdfDocumentKt? = null

            try {
                val existingPages = pdfTextRepository.getIndexedPages(currentBookId!!)
                val initialIndexedCount = existingPages.size

                if (existingPages.size >= totalPages) {
                    Timber.d("Indexer: All pages already indexed.")
                    isBackgroundIndexing = false
                    backgroundIndexingProgress = 1f
                    return@withContext
                }

                Timber.d(
                    "Indexer: Starting background indexing for ${totalPages - existingPages.size} pages."
                )

                bgPfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                if (bgPfd != null) {
                    bgDoc = pdfiumCore.newDocument(bgPfd, documentPassword)

                    val pagesToIndex = (0 until totalPages).filter { !existingPages.contains(it) }
                    val totalToDo = pagesToIndex.size
                    var completed = 0

                    for (pageIndex in pagesToIndex) {
                        if (!isActive) break

                        try {
                            pdfTextRepository.indexPage(
                                bookId = currentBookId!!,
                                document = bgDoc,
                                pageIndex = pageIndex,
                                onOcrModelDownloading = { isOcrModelDownloading = true })
                        } catch (e: Exception) {
                            Timber.e(e, "Indexer: Failed on page $pageIndex")
                        }

                        completed++
                        if (completed % 5 == 0 || completed == totalToDo) {
                            val totalIndexedSoFar = initialIndexedCount + completed
                            backgroundIndexingProgress =
                                totalIndexedSoFar.toFloat() / totalPages.toFloat()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Indexer: Fatal error")
            } finally {
                try {
                    bgDoc?.close()
                    bgPfd?.close()
                } catch (e: Exception) {
                    Timber.e(e, "Indexer: Cleanup failed")
                }
                isBackgroundIndexing = false
            }
        }
    }

    var activeQuery by remember { mutableStateOf("") }

    val dummySearcher: suspend (String) -> List<SearchResult> = { emptyList() }

    val searchState = rememberSearchState(scope = coroutineScope, searcher = dummySearcher)

    var smartSearchResult by remember { mutableStateOf<SmartSearchResult?>(null) }
    var currentPdfSearchResult by remember { mutableStateOf<SearchResult?>(null) }

    val density = LocalDensity.current
    val navBarHeight = WindowInsets.systemBars.getBottom(density)
    val imeHeight = WindowInsets.ime.getBottom(density)

    val bottomScrollLimitPx = remember(isEditMode, imeHeight, navBarHeight, dockLocation, isDockMinimized) {
        if (isEditMode) {
            if (imeHeight > 0) {
                imeHeight.toFloat()
            } else {
                if (dockLocation == DockLocation.BOTTOM && !isDockMinimized) {
                    with(density) { 100.dp.toPx() }
                } else {
                    with(density) { 16.dp.toPx() } + navBarHeight
                }
            }
        } else {
            if (showBars) {
                with(density) { 56.dp.toPx() } + navBarHeight
            } else {
                navBarHeight.toFloat()
            }
        }
    }

    val statusBarHeight = WindowInsets.statusBars.getTop(density)
    val topBarHeightPx = with(density) { 56.dp.toPx() }

    val topScrollLimitPx = remember(showBars, statusBarHeight) {
        if (showBars) {
            statusBarHeight + topBarHeightPx
        } else {
            0f
        }
    }

    LaunchedEffect(searchState.searchQuery, currentBookId) {
        val query = searchState.searchQuery
        if (query.isBlank() || currentBookId == null) {
            smartSearchResult = null
            currentPdfSearchResult = null
            return@LaunchedEffect
        }

        pdfTextRepository.searchBookSmart(currentBookId!!, query).conflate().collect { result ->
            smartSearchResult = result
        }
    }

    fun parseSnippet(rawSnippet: String): AnnotatedString {
        return buildAnnotatedString {
            val boldStyle = SpanStyle(fontWeight = FontWeight.Bold, color = Color.Blue)

            val regex = "<b>(.*?)</b>".toRegex()
            val matches = regex.findAll(rawSnippet)

            var lastAppendPosition = 0

            for (match in matches) {
                append(rawSnippet.substring(lastAppendPosition, match.range.first))
                val content = match.groupValues[1]
                pushStyle(boldStyle)
                append(content)
                pop()
                lastAppendPosition = match.range.last + 1
            }
            if (lastAppendPosition < rawSnippet.length) {
                append(rawSnippet.substring(lastAppendPosition))
            }
        }
    }

    LaunchedEffect(activeQuery, currentBookId) {
        val query = activeQuery
        if (query.isBlank() || currentBookId == null) {
            searchState.searchResults = emptyList()
            searchState.isSearchInProgress = false
            return@LaunchedEffect
        }

        searchState.isSearchInProgress = true
        delay(300)

        pdfTextRepository.searchBookFlow(currentBookId!!, query).conflate().collect { matches ->
            val results = mutableListOf<SearchResult>()

            val regexPattern = try {
                Regex("(?i)\\b${Regex.escape(query)}")
            } catch (_: Exception) {
                Regex("(?i)${Regex.escape(query)}")
            }

            matches.forEach { match ->
                val regexMatches = regexPattern.findAll(match.content)
                var hasFoundMatch = false

                regexMatches.forEachIndexed { occurrenceIndex, _ ->
                    hasFoundMatch = true
                    results.add(
                        SearchResult(
                            locationInSource = match.pageIndex,
                            locationTitle = "Page ${match.pageIndex + 1}",
                            snippet = parseSnippet(match.snippet),
                            query = query,
                            occurrenceIndexInLocation = occurrenceIndex,
                            chunkIndex = match.pageIndex
                        )
                    )
                }

                if (!hasFoundMatch) {
                    results.add(
                        SearchResult(
                            locationInSource = match.pageIndex,
                            locationTitle = "Page ${match.pageIndex + 1}",
                            snippet = parseSnippet(match.snippet),
                            query = query,
                            occurrenceIndexInLocation = 0,
                            chunkIndex = match.pageIndex
                        )
                    )
                }
            }

            searchState.searchResults = results
            searchState.isSearchInProgress = false

            delay(250)
        }
    }

    val isTtsSessionActive =
        (ttsState.currentText != null || ttsState.isLoading) && ttsState.playbackSource == "READER"

    val onInternalLinkNav: (Int) -> Unit = { targetPage ->
        coroutineScope.launch {
            if (targetPage in 0 until totalPages) {
                if (displayMode == DisplayMode.PAGINATION) {
                    pagerState.animateScrollToPage(targetPage)
                } else {
                    verticalReaderState.scrollToPage(targetPage)
                }
            }
        }
    }

    val paginationDraggingOriginPage = remember(paginationDraggingBoxId, textBoxes) {
        if (paginationDraggingBoxId == null) null
        else textBoxes.find { it.id == paginationDraggingBoxId }?.pageIndex
    }

    val dynamicBeyondViewportPageCount = remember(
        paginationDraggingOriginPage,
        pagerState.currentPage
    ) {
        if (paginationDraggingOriginPage != null) {
            val distance = kotlin.math.abs(pagerState.currentPage - paginationDraggingOriginPage)
            (distance + 1).coerceAtLeast(1)
        } else {
            1
        }
    }

    fun navigateToPdfSearchResult(result: SearchResult) {
        currentPdfSearchResult = result

        searchHighlightTarget = result

        coroutineScope.launch {
            if (displayMode == DisplayMode.PAGINATION) {
                if (pagerState.currentPage != result.locationInSource) {
                    pagerState.scrollToPage(result.locationInSource)
                }
            } else {
                verticalReaderState.scrollToPage(result.locationInSource)
            }
        }
    }

    LaunchedEffect(searchState.isSearchActive) {
        if (searchState.isSearchActive) {
            delay(100)
            focusRequester.requestFocus()
        } else {
            searchHighlightTarget = null
        }
    }

    BackHandler(enabled = true) {
        when {
            showPasswordDialog -> {
                onNavigateBack()
            }

            isFullScreen -> {
                isFullScreen = false
                savePdfFullScreen(context, bookId, false)
            }

            showReindexDialog != null -> showReindexDialog = null

            isAutoScrollModeActive -> {
                isAutoScrollModeActive = false
                isAutoScrollPlaying = false
                showBars = true
            }

            drawerState.isOpen -> {
                coroutineScope.launch { drawerState.close() }
            }

            isEditMode -> {
                richTextController?.clearSelection()
                isEditMode = false
                showBars = true
            }

            showSummarizationPopup -> showSummarizationPopup = false
            showPermissionRationaleDialog -> showPermissionRationaleDialog = false
            showSummarizationUpsellDialog -> showSummarizationUpsellDialog = false
            showAiDefinitionPopup -> showAiDefinitionPopup = false
            showDictionaryUpsellDialog -> showDictionaryUpsellDialog = false
            isPageSliderVisible -> {
                isPageSliderVisible = false
                showBars = true
            }

            searchState.isSearchActive -> {
                searchState.isSearchActive = false
                searchState.onQueryChange("")
            }

            showTtsSettingsSheet -> showTtsSettingsSheet = false

            else -> {
                saveStateAndExit()
            }
        }
    }

    val showStandardBars = showBars && !isEditMode
    val snackbarPadding by animateDpAsState(
        targetValue = if (showStandardBars && !searchState.isSearchActive) 56.dp else 0.dp,
        label = "SnackbarPadding"
    )

    ModalNavigationDrawer(
        drawerState = drawerState, gesturesEnabled = drawerState.isOpen, drawerContent = {
            ModalDrawerSheet(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                val drawerPagerState = rememberPagerState(pageCount = { 3 })
                val drawerScope = rememberCoroutineScope()

                Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = drawerPagerState.currentPage) {
                        Tab(selected = drawerPagerState.currentPage == 0, onClick = {
                            drawerScope.launch {
                                drawerPagerState.animateScrollToPage(0)
                            }
                        }, text = { Text("Chapters") })
                        Tab(
                            selected = drawerPagerState.currentPage == 1,
                            onClick = {
                                drawerScope.launch {
                                    drawerPagerState.animateScrollToPage(1)
                                }
                            },
                            text = { Text("Bookmarks") },
                            modifier = Modifier.testTag("BookmarksTab")
                        )
                        Tab(
                            selected = drawerPagerState.currentPage == 2,
                            onClick = {
                                drawerScope.launch {
                                    drawerPagerState.animateScrollToPage(2)
                                }
                            },
                            text = { Text("Highlights") },
                            modifier = Modifier.testTag("HighlightsTab")
                        )
                    }

                    HorizontalPager(
                        state = drawerPagerState, modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { page ->
                        when (page) {
                            0 -> { // Chapters Page
                                if (flatTableOfContents.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Chapters are not available for this book.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    val currentTocEntry by remember(
                                        pagerState.currentPage, flatTableOfContents
                                    ) {
                                        derivedStateOf {
                                            flatTableOfContents.lastOrNull {
                                                it.pageIndex <= pagerState.currentPage
                                            }
                                        }
                                    }
                                    LazyColumn(modifier = Modifier.fillMaxHeight()) {
                                        itemsIndexed(
                                            items = flatTableOfContents, key = { index, entry ->
                                                "toc_${index}_${entry.pageIndex}_${entry.title.hashCode()}"
                                            }) { _, entry ->
                                            val isCurrentChapter = entry == currentTocEntry
                                            ListItem(
                                                headlineContent = {
                                                    Text(
                                                        entry.title,
                                                        fontWeight = if (isCurrentChapter) FontWeight.Bold
                                                        else FontWeight.Normal,
                                                        modifier = Modifier.padding(
                                                            start = (16 * entry.nestLevel).dp
                                                        )
                                                    )
                                                }, colors = if (isCurrentChapter) {
                                                    ListItemDefaults.colors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        headlineColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                } else {
                                                    ListItemDefaults.colors()
                                                }, modifier = Modifier.clickable {
                                                    coroutineScope.launch {
                                                        drawerState.close()
                                                        if (displayMode == DisplayMode.PAGINATION) {
                                                            pagerState.scrollToPage(
                                                                entry.pageIndex
                                                            )
                                                        } else {
                                                            verticalReaderState.scrollToPage(
                                                                entry.pageIndex
                                                            )
                                                        }
                                                    }
                                                })
                                            HorizontalDivider()
                                        }
                                    }
                                }
                            }

                            1 -> { // Bookmarks Page
                                if (bookmarks.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "You haven't added any bookmarks yet.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    var bookmarkMenuExpandedFor by remember {
                                        mutableStateOf<PdfBookmark?>(null)
                                    }
                                    var showDeleteConfirmDialogFor by remember {
                                        mutableStateOf<PdfBookmark?>(null)
                                    }
                                    val sortedBookmarks = remember(bookmarks) {
                                        bookmarks.sortedBy { it.pageIndex }
                                    }

                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        itemsIndexed(
                                            items = sortedBookmarks, key = { index, bookmark ->
                                                "bm_${index}_${bookmark.pageIndex}"
                                            }) { _, bookmark ->
                                            ListItem(headlineContent = {
                                                Text(
                                                    bookmark.title,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }, supportingContent = {
                                                Text(
                                                    "Page ${bookmark.pageIndex + 1} of ${bookmark.totalPages}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }, trailingContent = {
                                                Box {
                                                    IconButton(
                                                        onClick = {
                                                            bookmarkMenuExpandedFor = bookmark
                                                        }) {
                                                        Icon(
                                                            imageVector = Icons.Default.MoreVert,
                                                            contentDescription = "More options for bookmark"
                                                        )
                                                    }
                                                    DropdownMenu(
                                                        expanded = bookmarkMenuExpandedFor == bookmark,
                                                        onDismissRequest = {
                                                            bookmarkMenuExpandedFor = null
                                                        }) {
                                                        DropdownMenuItem(text = {
                                                            Text("Rename")
                                                        }, onClick = {
                                                            showRenameBookmarkDialog = bookmark
                                                            bookmarkMenuExpandedFor = null
                                                        })
                                                        DropdownMenuItem(text = {
                                                            Text("Delete")
                                                        }, onClick = {
                                                            showDeleteConfirmDialogFor = bookmark
                                                            bookmarkMenuExpandedFor = null
                                                        })
                                                    }
                                                }
                                            }, modifier = Modifier
                                                .clickable {
                                                    coroutineScope.launch {
                                                        drawerState.close()
                                                        if (displayMode == DisplayMode.PAGINATION) {
                                                            pagerState.scrollToPage(
                                                                bookmark.pageIndex
                                                            )
                                                        } else {
                                                            verticalReaderState.scrollToPage(
                                                                bookmark.pageIndex
                                                            )
                                                        }
                                                    }
                                                }
                                                .testTag(
                                                    "BookmarkItem_${bookmark.pageIndex}"
                                                ))
                                            HorizontalDivider()
                                        }
                                    }

                                    showRenameBookmarkDialog?.let { bookmarkToRename ->
                                        var newTitle by remember { mutableStateOf("") }

                                        AlertDialog(onDismissRequest = {
                                            showRenameBookmarkDialog = null
                                        }, title = { Text("Rename Bookmark") }, text = {
                                            OutlinedTextField(
                                                value = newTitle,
                                                onValueChange = { newTitle = it },
                                                label = { Text("New Title") },
                                                placeholder = {
                                                    Text(
                                                        text = bookmarkToRename.title,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.6f
                                                        )
                                                    )
                                                },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }, confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    if (newTitle.isNotBlank()) {
                                                        val updatedBookmark = bookmarkToRename.copy(
                                                            title = newTitle
                                                        )
                                                        bookmarks =
                                                            (bookmarks - bookmarkToRename) + updatedBookmark
                                                    }
                                                    showRenameBookmarkDialog = null
                                                }) { Text("Save") }
                                        }, dismissButton = {
                                            TextButton(
                                                onClick = {
                                                    showRenameBookmarkDialog = null
                                                }) { Text("Cancel") }
                                        })
                                    }

                                    showDeleteConfirmDialogFor?.let { bookmarkToDelete ->
                                        AlertDialog(onDismissRequest = {
                                            showDeleteConfirmDialogFor = null
                                        }, title = { Text("Delete Bookmark?") }, text = {
                                            Text(
                                                "Are you sure you want to permanently delete this bookmark?"
                                            )
                                        }, confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    bookmarks = bookmarks - bookmarkToDelete
                                                    showDeleteConfirmDialogFor = null
                                                }) { Text("Delete") }
                                        }, dismissButton = {
                                            TextButton(
                                                onClick = {
                                                    showDeleteConfirmDialogFor = null
                                                }) { Text("Cancel") }
                                        })
                                    }
                                }
                            }
                            2 -> { // Highlights Page
                                if (userHighlights.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "You haven't added any highlights yet.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    var showDeleteConfirmDialogFor by remember {
                                        mutableStateOf<PdfUserHighlight?>(null)
                                    }
                                    val sortedHighlights = remember(userHighlights.toList()) {
                                        userHighlights.sortedBy { it.pageIndex }
                                    }

                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        itemsIndexed(
                                            items = sortedHighlights,
                                            key = { _, highlight -> highlight.id }
                                        ) { _, highlight ->
                                            ListItem(
                                                headlineContent = {
                                                    Text(
                                                        text = highlight.text.ifBlank { "Highlighted section" },
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier
                                                            .background(
                                                                color = highlight.color.color.copy(alpha = 0.3f),
                                                                shape = RoundedCornerShape(4.dp)
                                                            )
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                },
                                                supportingContent = {
                                                    Text(
                                                        "Page ${highlight.pageIndex + 1}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                },
                                                trailingContent = {
                                                    IconButton(
                                                        onClick = { showDeleteConfirmDialogFor = highlight }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete highlight",
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.clickable {
                                                    coroutineScope.launch {
                                                        drawerState.close()
                                                        if (displayMode == DisplayMode.PAGINATION) {
                                                            pagerState.scrollToPage(highlight.pageIndex)
                                                        } else {
                                                            verticalReaderState.scrollToPage(highlight.pageIndex)
                                                        }
                                                    }
                                                }
                                            )
                                            HorizontalDivider()
                                        }
                                    }

                                    showDeleteConfirmDialogFor?.let { highlightToDelete ->
                                        AlertDialog(
                                            onDismissRequest = { showDeleteConfirmDialogFor = null },
                                            title = { Text("Delete Highlight?") },
                                            text = { Text("Are you sure you want to permanently delete this highlight?") },
                                            confirmButton = {
                                                TextButton(
                                                    onClick = {
                                                        userHighlights.removeAll { it.id == highlightToDelete.id }
                                                        showDeleteConfirmDialogFor = null
                                                    }
                                                ) { Text("Delete") }
                                            },
                                            dismissButton = {
                                                TextButton(
                                                    onClick = { showDeleteConfirmDialogFor = null }
                                                ) { Text("Cancel") }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = snackbarPadding)
                )
            }
        ) { paddingValues ->
            BoxWithConstraints(modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)) {
                IntSize(constraints.maxWidth, constraints.maxHeight)
                val boxConstraints = constraints
                val boxMaxWidthFloat = boxConstraints.maxWidth.toFloat()
                val boxMaxHeightFloat = boxConstraints.maxHeight.toFloat()

                if (richTextController != null && isEditMode && selectedTool == InkType.TEXT) {
                    BasicTextField(
                        value = richTextController.editingValue,
                        onValueChange = { newValue ->
                            richTextController.onValueChanged(newValue)
                        },
                        textStyle = TextStyle(
                            color = richTextController.currentStyle.color,
                            fontSize = richTextController.currentStyle.fontSize,
                            fontWeight = richTextController.currentStyle.fontWeight,
                            fontStyle = richTextController.currentStyle.fontStyle,
                            textDecoration = richTextController.currentStyle.textDecoration
                        ),
                        modifier = Modifier
                            .size(1.dp)
                            .alpha(0f)
                            .clearAndSetSemantics { }
                            .focusRequester(richTextController.focusRequester)
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace) {
                                    val handled = richTextController.handleBackspaceAtStart()
                                    if (handled) Timber.tag("RichTextFlow").d("KeyEvent: Backspace consumed by controller")
                                    handled
                                } else {
                                    false
                                }
                            }
                            .align(Alignment.TopStart),
                    )
                }

                // --- Main Content Area ---
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = verticalHeaderHeight, bottom = verticalFooterHeight
                        )
                ) {
                    when {
                        isLoadingDocument -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }

                        errorMessage != null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = errorMessage ?: "Failed to load PDF.",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }

                        pdfDocument != null && totalPages > 0 -> {
                            val stablePdfDocument = remember(pdfDocument) { StableHolder(pdfDocument!!) }
                            when (displayMode) {
                                DisplayMode.PAGINATION -> {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier.fillMaxSize(),
                                            key = { it },
                                            beyondViewportPageCount = dynamicBeyondViewportPageCount,
                                            userScrollEnabled= currentPageScale == 1f && !(ttsState.isPlaying || ttsState.isLoading || searchState.isSearchActive) && !isPageSliderVisible && paginationDraggingBoxId == null
                                        ) { pageIndex ->
                                            val isVisiblePage = remember(pagerState.currentPage, pageIndex) {
                                                kotlin.math.abs(pagerState.currentPage - pageIndex) <= 1
                                            }
                                            val isPageBookmarked by remember(bookmarks, pageIndex) {
                                                derivedStateOf {
                                                    bookmarks.any { it.pageIndex == pageIndex }
                                                }
                                            }
                                            var ocrHighlightRects by remember {
                                                mutableStateOf<List<RectF>>(emptyList())
                                            }

                                            LaunchedEffect(searchHighlightTarget, pageIndex) {
                                                val target = searchHighlightTarget
                                                ocrHighlightRects = emptyList()

                                                if (target != null && target.locationInSource == pageIndex) {
                                                    Timber.d(
                                                        "LaunchedEffect triggered for Page $pageIndex. Checking Native..."
                                                    )
                                                    val hasNative = pdfTextRepository.hasNativeText(
                                                        pdfDocument!!, pageIndex
                                                    )
                                                    Timber.d(
                                                        "Page $pageIndex Has Native Text: $hasNative"
                                                    )

                                                    if (!hasNative) {
                                                        Timber.d(
                                                            "Fetching OCR rects for query: '${target.query}'"
                                                        )
                                                        val rects = pdfTextRepository.getOcrSearchRects(
                                                            document = pdfDocument!!,
                                                            pageIndex = pageIndex,
                                                            query = target.query,
                                                            onModelDownloading = {
                                                                isOcrModelDownloading = true
                                                            })
                                                        Timber.d(
                                                            "Received ${rects.size} rects from Repository."
                                                        )
                                                        ocrHighlightRects = rects
                                                    } else {
                                                        Timber.d(
                                                            "Native text present. Skipping OCR highlighting."
                                                        )
                                                    }
                                                }
                                            }

                                            val pageAnnotationsProvider =
                                                remember(pageIndex, allAnnotationsProvider) {
                                                    {
                                                        allAnnotationsProvider()[pageIndex]
                                                            ?: emptyList()
                                                    }
                                                }

                                            val stableOcrRects = remember(ocrHighlightRects) {
                                                StableHolder(ocrHighlightRects)
                                            }

                                            val currentSelectedTool by rememberUpdatedState(selectedTool)

                                            @Suppress("ControlFlowWithEmptyBody") val onDrawPagination =
                                                remember(pageIndex) {
                                                    { point: PdfPoint ->
                                                        if (currentSelectedTool == InkType.TEXT) {
                                                        } else if (currentSelectedTool == InkType.ERASER) {
                                                            val aspectRatio = pageAspectRatios.getOrElse(pageIndex) { 1f }
                                                            val existing = allAnnotations[pageIndex] ?: emptyList()
                                                            val toRemove = existing.filter {
                                                                isAnnotationHit(it, point, aspectRatio)
                                                            }
                                                            if (toRemove.isNotEmpty()) {
                                                                val batch =
                                                                    erasedAnnotationsFromStroke.getOrPut(
                                                                        pageIndex
                                                                    ) {
                                                                        mutableListOf()
                                                                    }
                                                                batch.addAll(toRemove)

                                                                val newList =
                                                                    existing - toRemove.toSet()
                                                                allAnnotations =
                                                                    allAnnotations + (pageIndex to newList)
                                                            }
                                                        } else {
                                                            if (currentIsHighlighter && currentSnapEnabled) {
                                                                val startPoint = drawingState.currentAnnotation?.points?.firstOrNull()
                                                                val effectivePoint = calculateSnappedPoint(pageIndex, point, startPoint)
                                                                drawingState.updateDrag(effectivePoint.copy(timestamp = System.currentTimeMillis()))
                                                            } else {
                                                                drawingState.onDraw(point.copy(timestamp = System.currentTimeMillis()))
                                                            }
                                                        }
                                                    }
                                                }

                                            val currentStrokeColorState by rememberUpdatedState(
                                                currentStrokeColor
                                            )
                                            val currentStrokeWidthState by rememberUpdatedState(
                                                currentStrokeWidth
                                            )

                                            @Suppress("ControlFlowWithEmptyBody") val onDrawStartPagination =
                                                remember(pageIndex) {
                                                    { point: PdfPoint ->
                                                        if (showToolSettings) {
                                                            showToolSettings = false
                                                        } else {
                                                            if (currentSelectedTool == InkType.TEXT) {
                                                            } else if (currentSelectedTool == InkType.ERASER) {
                                                                erasedAnnotationsFromStroke.clear()
                                                                val aspectRatio = pageAspectRatios.getOrElse(pageIndex) { 1f }
                                                                val existing = allAnnotations[pageIndex] ?: emptyList()
                                                                val toRemove = existing.filter {
                                                                    isAnnotationHit(it, point, aspectRatio)
                                                                }
                                                                if (toRemove.isNotEmpty()) {
                                                                    val batch =
                                                                        erasedAnnotationsFromStroke.getOrPut(
                                                                            pageIndex
                                                                        ) {
                                                                            mutableListOf()
                                                                        }
                                                                    batch.addAll(toRemove)

                                                                    val newList =
                                                                        existing - toRemove.toSet()
                                                                    allAnnotations =
                                                                        allAnnotations + (pageIndex to newList)
                                                                }
                                                            } else {
                                                                val pointWithTime = point.copy(
                                                                    timestamp = System.currentTimeMillis()
                                                                )
                                                                drawingState.onDrawStart(
                                                                    pageIndex,
                                                                    pointWithTime,
                                                                    currentSelectedTool,
                                                                    currentStrokeColorState,
                                                                    currentStrokeWidthState
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                            val virtualPage =
                                                if (virtualPages.isNotEmpty()) virtualPages.getOrNull(
                                                    pageIndex
                                                )
                                                else VirtualPage.PdfPage(pageIndex)

                                            PdfPageComposable(
                                                pdfDocument = stablePdfDocument,
                                                pageIndex = pageIndex,
                                                virtualPage = virtualPage,
                                                totalPages = totalDisplayPages,
                                                isDarkMode = isPdfDarkMode,
                                                isScrollLocked = isScrollLocked,
                                                onScaleChanged = { newScale ->
                                                    if (pagerState.currentPage == pageIndex) {
                                                        currentPageScale = newScale
                                                    }
                                                },
                                                ttsHighlightData = if (pagerState.currentPage == pageIndex) ttsHighlightData else null,
                                                searchQuery = searchState.searchQuery,
                                                searchHighlightMode = searchHighlightMode,
                                                searchResultToHighlight = if (pagerState.currentPage == pageIndex) searchHighlightTarget else null,
                                                ocrHoverHighlights = stableOcrRects,
                                                modifier = Modifier.fillMaxSize(),
                                                showAllTextHighlights = showAllTextHighlights,
                                                onHighlightLoading = { /* no-op for paginated mode */ },
                                                onSingleTap = onSingleTapStable,
                                                isProUser = isProUser,
                                                onShowDictionaryUpsellDialog = {
                                                    if (useOnlineDictionary) {
                                                        showDictionaryUpsellDialog = true
                                                    }
                                                },
                                                onWordSelectedForAiDefinition = onDictionaryLookupStable,
                                                onOcrStateChange = onOcrStateChange,
                                                onLinkClicked = { url -> clickedLinkUrl = url },
                                                onInternalLinkClicked = onInternalLinkNav,
                                                isBookmarked = isPageBookmarked,
                                                onBookmarkClick = { onToggleBookmark(pageIndex) },
                                                isZoomEnabled = true,
                                                clearSelectionTrigger = selectionClearTrigger,
                                                pageAnnotations = pageAnnotationsProvider,
                                                drawingState = drawingState,
                                                onDrawStart = onDrawStartPagination,
                                                onDraw = onDrawPagination,
                                                selectedTool = selectedTool,
                                                onDrawEnd = {
                                                    val finalAnnotation = drawingState.onDrawEnd()
                                                    if (finalAnnotation != null) {
                                                        val pageIdx = finalAnnotation.pageIndex
                                                        val existing =
                                                            allAnnotations[pageIdx] ?: emptyList()
                                                        allAnnotations =
                                                            allAnnotations + (pageIdx to (existing + finalAnnotation))
                                                        undoStack.add(
                                                            HistoryAction.Add(
                                                                pageIdx, finalAnnotation
                                                            )
                                                        )
                                                        redoStack.clear()
                                                    }

                                                    if (selectedTool == InkType.ERASER && erasedAnnotationsFromStroke.isNotEmpty()) {
                                                        val removalMap =
                                                            erasedAnnotationsFromStroke.mapValues {
                                                                it.value.toList()
                                                            }
                                                        undoStack.add(
                                                            HistoryAction.Remove(removalMap)
                                                        )
                                                        redoStack.clear()
                                                        erasedAnnotationsFromStroke.clear()
                                                    }
                                                },
                                                onOcrModelDownloading = {
                                                    isOcrModelDownloading = true
                                                },
                                                userHighlights = userHighlights.filter { it.pageIndex == pageIndex },
                                                onHighlightAdd = onHighlightAdd,
                                                onHighlightUpdate = onHighlightUpdate,
                                                onHighlightDelete = onHighlightDelete,
                                                onTwoFingerSwipe = { direction ->
                                                    coroutineScope.launch {
                                                        val targetPage =
                                                            pagerState.currentPage + direction
                                                        if (targetPage in 0 until totalDisplayPages) {
                                                            pagerState.animateScrollToPage(
                                                                targetPage
                                                            )
                                                        }
                                                    }
                                                },
                                                richTextController = richTextController,
                                                isStylusOnlyMode = isStylusOnlyMode,
                                                isHighlighterSnapEnabled = isHighlighterSnapEnabled,
                                                isEditMode = isDrawingActive,
                                                textBoxes = textBoxes.filter { it.pageIndex == pageIndex },
                                                selectedTextBoxId = selectedTextBoxId,
                                                onTextBoxChange = { updatedBox ->
                                                    val idx = textBoxes.indexOfFirst { it.id == updatedBox.id }
                                                    if (idx != -1) textBoxes[idx] = updatedBox
                                                },
                                                onTextBoxSelect = { id ->
                                                    selectedTextBoxId = id
                                                    richTextController?.clearSelection()
                                                },
                                                draggingBoxId = paginationDraggingBoxId,
                                                onTextBoxDragStart = { box, _, _ ->
                                                    val pageAspectRatio = displayPageRatios.getOrElse(pageIndex) { 1f }

                                                    val containerWidthPx = boxConstraints.maxWidth
                                                    val containerHeightPx = boxConstraints.maxHeight

                                                    var renderedWidthInt = containerWidthPx
                                                    var renderedHeightInt = (renderedWidthInt / pageAspectRatio).toInt()
                                                    if (renderedHeightInt > containerHeightPx) {
                                                        renderedHeightInt = containerHeightPx
                                                        renderedWidthInt = (renderedHeightInt * pageAspectRatio).toInt()
                                                    }

                                                    val renderedWidth = renderedWidthInt.toFloat()
                                                    val renderedHeight = renderedHeightInt.toFloat()

                                                    val offsetX = (containerWidthPx - renderedWidth) / 2f
                                                    val offsetY = (containerHeightPx - renderedHeight) / 2f

                                                    paginationDraggingBoxId = box.id
                                                    paginationOriginalRelSize = Size(box.relativeBounds.width, box.relativeBounds.height)

                                                    paginationDraggingSize = Size(
                                                        box.relativeBounds.width * renderedWidth,
                                                        box.relativeBounds.height * renderedHeight
                                                    )
                                                    paginationDraggingOffset = Offset(
                                                        offsetX + (box.relativeBounds.left * renderedWidth),
                                                        offsetY + (box.relativeBounds.top * renderedHeight)
                                                    )
                                                    paginationDragPageHeight = renderedHeight
                                                },
                                                onTextBoxDrag = { dragDelta ->
                                                    paginationDraggingOffset += dragDelta

                                                    val edgeThreshold = 60f
                                                    val screenWidth = boxConstraints.maxWidth.toFloat()

                                                    val isMovingLeft = dragDelta.x < 0
                                                    val isMovingRight = dragDelta.x > 0

                                                    if (paginationDraggingOffset.x < edgeThreshold && isMovingLeft) {
                                                        coroutineScope.launch {
                                                            if (pagerState.currentPage > 0 && !pagerState.isScrollInProgress) {
                                                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                            }
                                                        }
                                                    } else if (paginationDraggingOffset.x + paginationDraggingSize.width > screenWidth - edgeThreshold && isMovingRight) {
                                                        coroutineScope.launch {
                                                            if (pagerState.currentPage < totalDisplayPages - 1 && !pagerState.isScrollInProgress) {
                                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                            }
                                                        }
                                                    }
                                                },
                                                onTextBoxDragEnd = {
                                                    val boxId = paginationDraggingBoxId
                                                    if (boxId != null) {
                                                        coroutineScope.launch {
                                                            val targetPage = pagerState.currentPage
                                                            val targetVirtualPage = virtualPages.getOrNull(targetPage)
                                                            val pageAspectRatio = if (targetVirtualPage is VirtualPage.BlankPage) {
                                                                if (targetVirtualPage.height > 0) targetVirtualPage.width.toFloat() / targetVirtualPage.height.toFloat() else 1f
                                                            } else {
                                                                displayPageRatios.getOrElse(targetPage) { 1f }
                                                            }

                                                            val containerWidthPx = boxConstraints.maxWidth
                                                            val containerHeightPx = boxConstraints.maxHeight

                                                            var renderedWidthInt = containerWidthPx
                                                            var renderedHeightInt = (renderedWidthInt / pageAspectRatio).toInt()
                                                            if (renderedHeightInt > containerHeightPx) {
                                                                renderedHeightInt = containerHeightPx
                                                                renderedWidthInt = (renderedHeightInt * pageAspectRatio).toInt()
                                                            }

                                                            val renderedWidth = renderedWidthInt.toFloat()
                                                            val renderedHeight = renderedHeightInt.toFloat()
                                                            val offsetX = (containerWidthPx - renderedWidth) / 2f
                                                            val offsetY = (containerHeightPx - renderedHeight) / 2f

                                                            val paddingPx = with(density) { 14.dp.toPx() }
                                                            val padRelX = if (renderedWidth > 0) paddingPx / renderedWidth else 0f
                                                            val padRelY = if (renderedHeight > 0) paddingPx / renderedHeight else 0f

                                                            val relW = paginationOriginalRelSize.width
                                                            val relH = paginationOriginalRelSize.height

                                                            val rawRelX = (paginationDraggingOffset.x - offsetX) / renderedWidth
                                                            val rawRelY = (paginationDraggingOffset.y - offsetY) / renderedHeight

                                                            val maxRelX = (1f - relW - padRelX).coerceAtLeast(padRelX)
                                                            val maxRelY = (1f - relH - padRelY).coerceAtLeast(padRelY)

                                                            val finalRelX = rawRelX.coerceIn(padRelX, maxRelX)
                                                            val finalRelY = rawRelY.coerceIn(padRelY, maxRelY)

                                                            val targetOffset = Offset(
                                                                offsetX + (finalRelX * renderedWidth),
                                                                offsetY + (finalRelY * renderedHeight)
                                                            )

                                                            val startOffset = paginationDraggingOffset
                                                            Animatable(0f).animateTo(1f) {
                                                                paginationDraggingOffset = lerp(startOffset, targetOffset, value)
                                                            }

                                                            val idx = textBoxes.indexOfFirst { it.id == boxId }
                                                            if (idx != -1) {
                                                                val oldBox = textBoxes[idx]
                                                                val fontScale = if (paginationDragPageHeight > 0 && renderedHeight > 0)
                                                                    paginationDragPageHeight / renderedHeight else 1f

                                                                textBoxes[idx] = oldBox.copy(
                                                                    pageIndex = targetPage,
                                                                    relativeBounds = Rect(finalRelX, finalRelY, finalRelX + relW, finalRelY + relH),
                                                                    fontSize = oldBox.fontSize * fontScale
                                                                )
                                                                selectedTextBoxId = boxId
                                                            }
                                                            paginationDraggingBoxId = null
                                                        }
                                                    } else {
                                                        paginationDraggingBoxId = null
                                                    }
                                                },
                                                onDragPageTurn = { /* Handled in onTextBoxDrag */ },
                                                isVisible = isVisiblePage,
                                            )
                                        }

                                        if (paginationDraggingBoxId != null) {
                                            val draggedBox = textBoxes.find { it.id == paginationDraggingBoxId }
                                            if (draggedBox != null) {
                                                val fontScaleRatio = if (paginationDraggingSize.height > 0)
                                                    paginationDragPageHeight / paginationDraggingSize.height else 1f

                                                val screenHeight = boxConstraints.maxHeight.toFloat()
                                                val boxBottomY = paginationDraggingOffset.y + paginationDraggingSize.height
                                                val spaceBelow = screenHeight - boxBottomY
                                                val overlayHandlePos = if (spaceBelow < with(density) { 60.dp.toPx() }) HandlePosition.TOP else HandlePosition.BOTTOM

                                                Box(
                                                    modifier = Modifier
                                                        .offset {
                                                            IntOffset(
                                                                paginationDraggingOffset.x.roundToInt(),
                                                                paginationDraggingOffset.y.roundToInt()
                                                            )
                                                        }
                                                ) {
                                                    ResizableTextBox(
                                                        box = draggedBox.copy(
                                                            relativeBounds = Rect(0f, 0f, 1f, 1f),
                                                            fontSize = draggedBox.fontSize * fontScaleRatio
                                                        ),
                                                        isSelected = true,
                                                        isEditMode = false, // Purely visual
                                                        isDarkMode = isPdfDarkMode,
                                                        pageWidthPx = paginationDraggingSize.width,
                                                        pageHeightPx = paginationDraggingSize.height,
                                                        handlePosition = overlayHandlePos,
                                                        onBoundsChanged = {},
                                                        onTextChanged = {},
                                                        onSelect = {},
                                                        onDragStart = {},
                                                        onDrag = { _, _ -> },
                                                        onDragEnd = {}
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                DisplayMode.VERTICAL_SCROLL -> {
                                    val headerHeight = 0.dp
                                    val footerHeight = 0.dp

                                    val currentSelectedTool by rememberUpdatedState(selectedTool)
                                    val currentStrokeColorState by rememberUpdatedState(
                                        currentStrokeColor
                                    )
                                    val currentStrokeWidthState by rememberUpdatedState(
                                        currentStrokeWidth
                                    )

                                    @Suppress("ControlFlowWithEmptyBody") val onDrawStartStable =
                                        remember {
                                            { pageIndex: Int, point: PdfPoint ->
                                                if (showToolSettings) {
                                                    showToolSettings = false
                                                } else {
                                                    if (currentSelectedTool == InkType.TEXT) {
                                                    } else if (currentSelectedTool == InkType.ERASER) {
                                                        erasedAnnotationsFromStroke.clear()

                                                        val aspectRatio = pageAspectRatios.getOrElse(pageIndex) { 1f }
                                                        val existing = allAnnotations[pageIndex] ?: emptyList()
                                                        val toRemove = existing.filter {
                                                            isAnnotationHit(it, point, aspectRatio)
                                                        }
                                                        if (toRemove.isNotEmpty()) {
                                                            val batch =
                                                                erasedAnnotationsFromStroke.getOrPut(
                                                                    pageIndex
                                                                ) {
                                                                    mutableListOf()
                                                                }
                                                            batch.addAll(toRemove)

                                                            val newList =
                                                                existing - toRemove.toSet()
                                                            allAnnotations =
                                                                allAnnotations + (pageIndex to newList)
                                                        }
                                                    } else {
                                                        val pointWithTime = point.copy(
                                                            timestamp = System.currentTimeMillis()
                                                        )
                                                        drawingState.onDrawStart(
                                                            pageIndex,
                                                            pointWithTime,
                                                            currentSelectedTool,
                                                            currentStrokeColorState,
                                                            currentStrokeWidthState
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                    val onDrawStable = remember(isHighlighterSnapEnabled, isCurrentToolHighlighter, calculateSnappedPoint) {
                                        { pageIndex: Int, point: PdfPoint ->
                                            if (currentSelectedTool == InkType.ERASER) {
                                                val aspectRatio = pageAspectRatios.getOrElse(pageIndex) { 1f }
                                                val existing = allAnnotations[pageIndex] ?: emptyList()
                                                val toRemove = existing.filter {
                                                    isAnnotationHit(it, point, aspectRatio)
                                                }
                                                if (toRemove.isNotEmpty()) {
                                                    val batch =
                                                        erasedAnnotationsFromStroke.getOrPut(
                                                            pageIndex
                                                        ) { mutableListOf() }
                                                    batch.addAll(toRemove)

                                                    val newList = existing - toRemove.toSet()
                                                    allAnnotations =
                                                        allAnnotations + (pageIndex to newList)
                                                }
                                            } else {
                                                if (currentIsHighlighter && currentSnapEnabled) {
                                                    val startPoint = drawingState.currentAnnotation?.points?.firstOrNull()
                                                    val effectivePoint = calculateSnappedPoint(pageIndex, point, startPoint)
                                                    drawingState.updateDrag(effectivePoint.copy(timestamp = System.currentTimeMillis()))
                                                } else {
                                                    drawingState.onDraw(point.copy(timestamp = System.currentTimeMillis()))
                                                }
                                            }
                                        }
                                    }

                                    Box(modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RectangleShape)) {
                                        val docHolder = remember(pdfDocument) {
                                            StableHolder(pdfDocument!!)
                                        }
                                        val bookmarksHolder =
                                            remember(bookmarks) { StableHolder(bookmarks) }
                                        val ratiosHolder = remember(displayPageRatios) {
                                            StableHolder(displayPageRatios)
                                        }

                                        PdfVerticalReader(
                                            state = verticalReaderState,
                                            pdfDocument = docHolder,
                                            isDarkMode = isPdfDarkMode,
                                            isScrollLocked = isScrollLocked,
                                            totalPages = totalDisplayPages,
                                            pageAspectRatios = ratiosHolder,
                                            virtualPages = virtualPages,
                                            headerHeight = headerHeight,
                                            footerHeight = footerHeight,
                                            onPageClick = onSingleTapStable,
                                            modifier = Modifier.testTag(VERTICAL_SCROLL_TAG),
                                            onZoomChange = onZoomChangeStable,
                                            showAllTextHighlights = showAllTextHighlights,
                                            onHighlightLoading = onHighlightLoadingStable,
                                            searchQuery = searchState.searchQuery,
                                            searchHighlightMode = searchHighlightMode,
                                            searchResultToHighlight = searchHighlightTarget,
                                            isProUser = isProUser,
                                            onShowDictionaryUpsellDialog = onShowDictionaryUpsellDialogStable,
                                            onWordSelectedForAiDefinition = onDictionaryLookupStable,
                                            ttsHighlightData = ttsHighlightData,
                                            ttsReadingPage = ttsPageData?.pageIndex,
                                            userHighlights = userHighlights,
                                            onHighlightAdd = onHighlightAdd,
                                            onHighlightUpdate = onHighlightUpdate,
                                            onHighlightDelete = onHighlightDelete,
                                            onLinkClicked = onLinkClickedStable,
                                            onInternalLinkClicked = onInternalLinkNavStable,
                                            bookmarks = bookmarksHolder,
                                            onBookmarkClick = onBookmarkClickStable,
                                            onOcrStateChange = onOcrStateChangeStable,
                                            onGetOcrSearchRects = onGetOcrSearchRectsStable,
                                            allAnnotations = allAnnotationsProvider,
                                            drawingState = drawingState,
                                            onDrawStart = onDrawStartStable,
                                            isHighlighterSnapEnabled = isHighlighterSnapEnabled,
                                            onDraw = onDrawStable,
                                            onDrawEnd = {
                                                val finalAnnotation = drawingState.onDrawEnd()
                                                if (finalAnnotation != null) {
                                                    val pageIdx = finalAnnotation.pageIndex
                                                    val existing =
                                                        allAnnotations[pageIdx] ?: emptyList()
                                                    allAnnotations =
                                                        allAnnotations + (pageIdx to (existing + finalAnnotation))
                                                    undoStack.add(
                                                        HistoryAction.Add(
                                                            pageIdx, finalAnnotation
                                                        )
                                                    )
                                                    redoStack.clear()
                                                }

                                                if (selectedTool == InkType.ERASER && erasedAnnotationsFromStroke.isNotEmpty()) {
                                                    val removalMap =
                                                        erasedAnnotationsFromStroke.mapValues {
                                                            it.value.toList()
                                                        }
                                                    undoStack.add(
                                                        HistoryAction.Remove(removalMap)
                                                    )
                                                    redoStack.clear()
                                                    erasedAnnotationsFromStroke.clear()
                                                }
                                            },
                                            onOcrModelDownloading = {
                                                isOcrModelDownloading = true
                                            },
                                            selectedTool = selectedTool,
                                            richTextController = richTextController,
                                            isStylusOnlyMode = isStylusOnlyMode,
                                            isEditMode = isDrawingActive,
                                            textBoxes = textBoxes,
                                            selectedTextBoxId = selectedTextBoxId,
                                            onTextBoxChange = { updatedBox ->
                                                val idx = textBoxes.indexOfFirst { it.id == updatedBox.id }
                                                if (idx != -1) textBoxes[idx] = updatedBox
                                            },
                                            onTextBoxSelect = { id ->
                                                selectedTextBoxId = id
                                                richTextController?.clearSelection()
                                            },
                                            bottomContentPaddingPx = bottomScrollLimitPx,
                                            topContentPaddingPx = topScrollLimitPx,
                                            onTextBoxMoved = { boxId, newPageIndex, newBounds ->
                                                val idx = textBoxes.indexOfFirst { it.id == boxId }
                                                if (idx != -1) {
                                                    val oldBox = textBoxes[idx]
                                                    textBoxes[idx] = oldBox.copy(pageIndex = newPageIndex, relativeBounds = newBounds)
                                                }
                                            },
                                            isAutoScrollPlaying = isAutoScrollPlaying,
                                            isAutoScrollTempPaused = isAutoScrollTempPaused,
                                            autoScrollSpeed = autoScrollSpeed * 0.5f,
                                            onInteractionListener = onAutoScrollInteraction
                                        )
                                    }
                                }
                            }
                        }

                        totalPages == 0 && !isLoadingDocument -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "PDF is empty or could not be displayed.",
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }

                if (isMusicianMode && isAutoScrollModeActive) {
                    @Suppress("UnusedVariable", "Unused") val density = LocalDensity.current

                    var leftPulseTrigger by remember { mutableLongStateOf(0L) }
                    var rightPulseTrigger by remember { mutableLongStateOf(0L) }

                    // --- ADD THESE STATES ---
                    var leftHoldProgress by remember { mutableFloatStateOf(0f) }
                    var rightHoldProgress by remember { mutableFloatStateOf(0f) }

                    val leftPulseAlpha by animateFloatAsState(
                        targetValue = if (System.currentTimeMillis() - leftPulseTrigger < 150) 0.3f else 0f,
                        animationSpec = tween(150), label = "leftPulse"
                    )
                    val rightPulseAlpha by animateFloatAsState(
                        targetValue = if (System.currentTimeMillis() - rightPulseTrigger < 150) 0.3f else 0f,
                        animationSpec = tween(150), label = "rightPulse"
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        val regionHeight = Modifier.fillMaxHeight(0.4f)
                        val regionWidth = Modifier.fillMaxWidth(0.25f)
                        val topOffset = 100.dp

                        val scrollAmount = boxMaxHeightFloat * 0.75f

                        // Left Region
                        Box(
                            modifier = regionWidth
                                .then(regionHeight)
                                .align(Alignment.TopStart)
                                .offset(y = topOffset)
                                .padding(start = 8.dp)
                                .background(Color.White.copy(alpha = leftPulseAlpha), RoundedCornerShape(12.dp))
                                .border(2.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        var isLongPress = false
                                        val job = coroutineScope.launch {
                                            val startTime = System.currentTimeMillis()
                                            while (isActive) {
                                                val elapsed = System.currentTimeMillis() - startTime
                                                if (elapsed >= 1000) {
                                                    leftHoldProgress = 0f
                                                    isLongPress = true
                                                    leftPulseTrigger = System.currentTimeMillis()
                                                    triggerAutoScrollTempPause(1000L)

                                                    coroutineScope.launch {
                                                        verticalReaderState.scrollToPage(0)
                                                    }
                                                    break
                                                }
                                                leftHoldProgress = elapsed / 1000f
                                                delay(16)
                                            }
                                        }

                                        val up = waitForUpOrCancellation()
                                        job.cancel()
                                        leftHoldProgress = 0f

                                        if (!isLongPress && up != null) {
                                            up.consume()
                                            Timber.tag("MusicianMode").d("Left region tapped")
                                            leftPulseTrigger = System.currentTimeMillis()
                                            triggerAutoScrollTempPause(600L)
                                            coroutineScope.launch {
                                                verticalReaderState.scrollBy(-scrollAmount)
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (leftHoldProgress > 0f) {
                                CircularProgressIndicator(
                                    progress = { leftHoldProgress },
                                    modifier = Modifier.size(48.dp).alpha(0.6f),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.Transparent,
                                    strokeWidth = 4.dp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).alpha(0.6f),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Right Region
                        Box(
                            modifier = regionWidth
                                .then(regionHeight)
                                .align(Alignment.TopEnd)
                                .offset(y = topOffset)
                                .padding(end = 8.dp)
                                .background(Color.White.copy(alpha = rightPulseAlpha), RoundedCornerShape(12.dp))
                                .border(2.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .pointerInput(totalPages) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        var isLongPress = false
                                        val job = coroutineScope.launch {
                                            val startTime = System.currentTimeMillis()
                                            while (isActive) {
                                                val elapsed = System.currentTimeMillis() - startTime
                                                if (elapsed >= 1000) {
                                                    rightHoldProgress = 0f
                                                    isLongPress = true
                                                    rightPulseTrigger = System.currentTimeMillis()
                                                    triggerAutoScrollTempPause(1000L)

                                                    coroutineScope.launch {
                                                        verticalReaderState.scrollToPage(totalPages - 1)
                                                    }
                                                    break
                                                }
                                                rightHoldProgress = elapsed / 1000f
                                                delay(16)
                                            }
                                        }

                                        val up = waitForUpOrCancellation()
                                        job.cancel()
                                        rightHoldProgress = 0f

                                        if (!isLongPress && up != null) {
                                            up.consume()
                                            Timber.tag("MusicianMode").d("Right region tapped")
                                            rightPulseTrigger = System.currentTimeMillis()
                                            triggerAutoScrollTempPause(600L)
                                            coroutineScope.launch {
                                                verticalReaderState.scrollBy(scrollAmount)
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (rightHoldProgress > 0f) {
                                CircularProgressIndicator(
                                    progress = { rightHoldProgress },
                                    modifier = Modifier.size(48.dp).alpha(0.6f),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.Transparent,
                                    strokeWidth = 4.dp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).alpha(0.6f),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // OCR language download indicator
                AnimatedVisibility(
                    visible = isOcrModelDownloading,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(
                            top = if (showBars) 56.dp else 0.dp
                        ) // Push down if top bar is visible
                        .padding(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Downloading ${ocrLanguage.displayName.substringBefore("(")} language pack...",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // --- Slider UI Overlay ---
                AnimatedVisibility(
                    visible = isPageSliderVisible,
                    enter = slideInVertically { fullHeight -> fullHeight } + fadeIn(),
                    exit = slideOutVertically { fullHeight -> fullHeight } + fadeOut()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember {
                                        MutableInteractionSource()
                                    }, indication = null
                                ) {
                                    isPageSliderVisible = false
                                    showBars = true
                                })

                        if (isFastScrubbing) {
                            PageScrubbingAnimation(
                                currentPage = sliderCurrentPage.roundToInt() + 1,
                                totalPages = totalPages
                            )
                        }

                        // Top back button
                        IconButton(
                            onClick = {
                                isPageSliderVisible = false
                                showBars = true
                            }, modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit slider navigation"
                            )
                        }

                        // Bottom controls
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .clickable(
                                    indication = null, interactionSource = remember {
                                        MutableInteractionSource()
                                    }) {}, // Consume clicks
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                BoxWithConstraints(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Slider(
                                        value = sliderCurrentPage,
                                        onValueChange = { newValue ->
                                            sliderCurrentPage = newValue
                                            isFastScrubbing = true
                                            scrubDebounceJob.value?.cancel()
                                            scrubDebounceJob.value = coroutineScope.launch {
                                                delay(200)
                                                if (isActive) {
                                                    if (displayMode == DisplayMode.PAGINATION) {
                                                        pagerState.scrollToPage(
                                                            newValue.roundToInt()
                                                        )
                                                    } else {
                                                        verticalReaderState.scrollToPage(
                                                            newValue.roundToInt()
                                                        )
                                                    }
                                                    isFastScrubbing = false
                                                }
                                            }
                                        },
                                        valueRange = 0f..(totalPages - 1).toFloat()
                                            .coerceAtLeast(0f),
                                        steps = if (totalPages > 2) totalPages - 2 else 0,
                                        modifier = Modifier.fillMaxWidth(),
                                        thumb = {
                                            Surface(
                                                modifier = Modifier.size(20.dp),
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                tonalElevation = 0.dp,
                                                shadowElevation = 0.dp
                                            ) {}
                                        },
                                        track = { sliderState ->
                                            val trackHeight = 2.dp
                                            val trackShape = RoundedCornerShape(trackHeight)

                                            val range =
                                                sliderState.valueRange.endInclusive - sliderState.valueRange.start
                                            val fraction = if (range == 0f) 0f
                                            else {
                                                ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(
                                                    0f,
                                                    1f
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(trackHeight)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                        shape = trackShape
                                                    )
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(fraction)
                                                        .fillMaxHeight()
                                                        .background(
                                                            color = MaterialTheme.colorScheme.primary,
                                                            shape = trackShape
                                                        )
                                                )
                                            }
                                        })

                                    val startPageOffsetFraction = if (totalPages > 1) {
                                        sliderStartPage.toFloat() / (totalPages - 1)
                                    } else {
                                        0f
                                    }

                                    val thumbWidth = 20.dp
                                    val trackWidth = maxWidth - thumbWidth
                                    val startPagePixelPosition =
                                        (trackWidth * startPageOffsetFraction) + (thumbWidth / 2)

                                    val indicatorSize = 8.dp
                                    val indicatorOffset =
                                        startPagePixelPosition - (indicatorSize / 2)
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .offset(x = indicatorOffset)
                                            .size(indicatorSize),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary
                                    ) {}

                                    Timber.d("maxWidth: $maxWidth, trackWidth: $trackWidth")
                                    Timber.d(
                                        "startPage: $sliderStartPage, totalPages: $totalPages, fraction: $startPageOffsetFraction"
                                    )
                                    Timber.d(
                                        "Calculated X Offset (before centering): $startPagePixelPosition"
                                    )

                                    startPageThumbnail?.let { thumbnail ->
                                        ThumbnailWithIndicator(
                                            thumbnail = thumbnail,
                                            modifier = Modifier
                                                .graphicsLayer { clip = false }
                                                .align(Alignment.TopStart)
                                                .offset(
                                                    x = startPagePixelPosition - (45.dp / 2),
                                                    y = (-72).dp
                                                ),
                                            onClick = {
                                                sliderCurrentPage = sliderStartPage.toFloat()
                                                coroutineScope.launch {
                                                    if (displayMode == DisplayMode.PAGINATION) {
                                                        pagerState.scrollToPage(sliderStartPage)
                                                    } else {
                                                        verticalReaderState.scrollToPage(
                                                            sliderStartPage
                                                        )
                                                    }
                                                }
                                            })
                                    }
                                }

                                // Page number text
                                Text(
                                    text = "${sliderCurrentPage.roundToInt() + 1} / $totalPages",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (displayMode == DisplayMode.VERTICAL_SCROLL) Color.Black
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                }

                // Custom Top Bar
                AnimatedVisibility(
                    visible = showStandardBars,
                    enter = slideInVertically(animationSpec = tween(200)) { fullHeight -> -fullHeight } + fadeIn(animationSpec = tween(200)),
                    exit = slideOutVertically(animationSpec = tween(200)) { fullHeight -> -fullHeight } + fadeOut(animationSpec = tween(200)),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (searchState.isSearchActive) {
                                SearchTopBar(
                                    searchState = searchState,
                                    focusRequester = focusRequester,
                                    onCloseSearch = {
                                        searchState.isSearchActive = false
                                        searchState.onQueryChange("")
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    })
                            } else {
                                IconButton(onClick = { saveStateAndExit() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                                val currentPageForDisplay =
                                    if (displayMode == DisplayMode.PAGINATION) {
                                        pagerState.currentPage
                                    } else {
                                        verticalReaderState.currentPage
                                    }
                                val titleText = when {
                                    isLoadingDocument -> "Loading PDF..."
                                    errorMessage != null -> "Error loading PDF"
                                    totalPages > 0 && pagerState.pageCount > 0 -> "Page ${currentPageForDisplay + 1} of $totalPages"
                                    totalPages > 0 && pagerState.pageCount == 0 -> "Loading page..."
                                    else -> "PDF Viewer"
                                }
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .weight(1f)
                                        .testTag("PageNumberIndicator")
                                )

                                IconButton(onClick = { isPdfDarkMode = !isPdfDarkMode }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.dark_mode),
                                        contentDescription = if (isPdfDarkMode) "Disable Dark Mode"
                                        else "Enable Dark Mode",
                                        tint = if (isPdfDarkMode) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(onClick = {
                                    isScrollLocked = !isScrollLocked
                                    savePdfScrollLocked(context, bookId, isScrollLocked)
                                }) {
                                    Icon(
                                        imageVector = if (isScrollLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = if (isScrollLocked) "Unlock Panning" else "Lock Panning",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(onClick = {
                                    isFullScreen = true
                                    savePdfFullScreen(context, bookId, true)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Enter Full Screen",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(onClick = { showDictionarySettingsSheet = true }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.dictionary),
                                        contentDescription = "Dictionary Settings",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (BuildConfig.DEBUG) {
                                    IconButton(onClick = { showPenPlayground = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Open Pen Playground",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    IconButton(onClick = {
                                        val page = if (displayMode == DisplayMode.PAGINATION) pagerState.currentPage else verticalReaderState.currentPage

                                        coroutineScope.launch(Dispatchers.IO) {
                                            val svgAnnotations = SvgToAnnotationConverter.importSvgFromAssets(
                                                context = context,
                                                fileName = "demo_art.svg",
                                                pageIndex = page
                                            )

                                            withContext(Dispatchers.Main) {
                                                if (svgAnnotations.isNotEmpty()) {
                                                    val existing = allAnnotations[page] ?: emptyList()
                                                    allAnnotations = allAnnotations + (page to (existing + svgAnnotations))

                                                    svgAnnotations.forEach { annot ->
                                                        undoStack.add(HistoryAction.Add(page, annot))
                                                    }
                                                    redoStack.clear()

                                                    snackbarHostState.showSnackbar("Imported ${svgAnnotations.size} SVG strokes!")
                                                } else {
                                                    snackbarHostState.showSnackbar("Failed to import SVG or empty.")
                                                }
                                            }
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Brush,
                                            contentDescription = "Import SVG",
                                            tint = Color(0xFFE91E63)
                                        )
                                    }
                                }

                                Box {
                                    var showMoreMenu by remember { mutableStateOf(false) }
                                    IconButton(onClick = { showMoreMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More Options"
                                        )
                                    }

                                    // Main "More" menu
                                    DropdownMenu(
                                        expanded = showMoreMenu,
                                        onDismissRequest = { showMoreMenu = false }) {
                                        if (BuildConfig.IS_PRO) {
                                            DropdownMenuItem(
                                                text = { Text("OCR Language") },
                                                onClick = {
                                                    showMoreMenu = false
                                                    hasSelectedOcrLanguage = true
                                                    showOcrLanguageDialog = true
                                                }
                                            )
                                            HorizontalDivider()
                                        }

                                        DropdownMenuItem(
                                            text = { Text("Reading Mode: Vertical scroll") },
                                            enabled = !isTtsSessionActive,
                                            onClick = {
                                                displayMode = DisplayMode.VERTICAL_SCROLL
                                                showMoreMenu = false
                                            },
                                            trailingIcon = {
                                                if (displayMode == DisplayMode.VERTICAL_SCROLL) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = "Selected"
                                                    )
                                                }
                                            })
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Reading Mode: Paginated") },
                                            enabled = !isTtsSessionActive,
                                            onClick = {
                                                displayMode = DisplayMode.PAGINATION
                                                showMoreMenu = false
                                            },
                                            trailingIcon = {
                                                if (displayMode == DisplayMode.PAGINATION) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = "Selected"
                                                    )
                                                }
                                            })
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Auto Scroll") },
                                            enabled = !isTtsSessionActive && displayMode == DisplayMode.VERTICAL_SCROLL,
                                            onClick = {
                                                showMoreMenu = false
                                                isAutoScrollModeActive = true
                                                isAutoScrollPlaying = true
                                                showBars = !isMusicianMode
                                            }
                                        )

                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("TTS Voice Settings") },
                                            onClick = {
                                                showMoreMenu = false
                                                showDeviceVoiceSettingsSheet = true
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.GraphicEq,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        )

                                        if (BuildConfig.DEBUG) {
                                            DropdownMenuItem(
                                                text = { Text("TTS Settings (Debug)") },
                                                onClick = {
                                                    showMoreMenu = false
                                                    showTtsSettingsSheet = true
                                                },
                                                leadingIcon = {
                                                    Icon(painter = painterResource(id = R.drawable.text_to_speech), contentDescription = null, modifier = Modifier.size(20.dp))
                                                }
                                            )
                                        }

                                        HorizontalDivider()
                                        DropdownMenuItem(text = {
                                            Text(
                                                if (isBookmarked) "Remove bookmark"
                                                else "Bookmark this page"
                                            )
                                        }, onClick = {
                                            showMoreMenu = false
                                            onBookmarkClick()
                                        })
                                        HorizontalDivider()

                                        DropdownMenuItem(
                                            text = { Text("Insert Blank Page") },
                                            onClick = {
                                                showMoreMenu = false
                                                onInsertPage()
                                            })

                                        val canDelete =
                                            virtualPages.getOrNull(currentPage) is VirtualPage.BlankPage
                                        if (canDelete) {
                                            DropdownMenuItem(
                                                text = { Text("Delete Page") },
                                                onClick = {
                                                    showMoreMenu = false
                                                    onDeletePage()
                                                },
                                                colors = MenuDefaults.itemColors(
                                                    textColor = MaterialTheme.colorScheme.error
                                                )
                                            )
                                        }

                                        HorizontalDivider()

                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    when {
                                                        isReflowingThisBook -> "Generating... ${(reflowProgressValue * 100).toInt()}%"
                                                        hasReflowFile -> "Open Text View"
                                                        else -> "Generate Text View"
                                                    }
                                                )
                                            },
                                            enabled = pdfDocument != null && !isReflowingThisBook,
                                            onClick = {
                                                showMoreMenu = false

                                                coroutineScope.launch {
                                                    if (richTextController != null) {
                                                        withContext(NonCancellable) { richTextController.saveImmediate() }
                                                    }
                                                    saveAllData(true).join()

                                                    if (hasReflowFile) {
                                                        val item = uiState.allRecentFiles.find { it.bookId == reflowBookId }
                                                        if (item != null) {
                                                            viewModel.switchToFileSeamlessly(item, currentPage)
                                                        } else {
                                                            viewModel.generateAndImportReflowFile(
                                                                pdfBookId = bookId,
                                                                pdfUri = pdfUri,
                                                                originalTitle = originalFileName,
                                                                autoOpenPage = currentPage
                                                            )
                                                        }
                                                    } else {
                                                        viewModel.generateAndImportReflowFile(
                                                            pdfBookId = bookId,
                                                            pdfUri = pdfUri,
                                                            originalTitle = originalFileName,
                                                            autoOpenPage = currentPage
                                                        )
                                                    }
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.format_size),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        )

                                        HorizontalDivider()

                                        DropdownMenuItem(text = { Text("Share") }, onClick = {
                                            showMoreMenu = false
                                            showShareDialog = true
                                        }, leadingIcon = {
                                            Icon(
                                                Icons.Default.Share, contentDescription = null
                                            )
                                        })
                                        DropdownMenuItem(
                                            text = { Text("Save copy to device") },
                                            onClick = {
                                                showMoreMenu = false
                                                showSaveDialog = true
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Save, contentDescription = null
                                                )
                                            })
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showStandardBars && isReflowingThisBook,
                    enter = fadeIn(animationSpec = tween(200)) + slideInVertically(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(200)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 56.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                        shadowElevation = 4.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Generating Text View...",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${(reflowProgressValue * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { reflowProgressValue },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }

                // Search Results Panel
                AnimatedVisibility(
                    visible = searchState.isSearchActive && searchState.showSearchResultsPanel,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 56.dp)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (isBackgroundIndexing) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp, vertical = 8.dp
                                    ), verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Indexing pages... ${(backgroundIndexingProgress * 100).toInt()}% done. Search results will update automatically.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        if (!(searchState.isSearchInProgress && isOcrScanning)) {

                            // NEW PANEL LOGIC
                            val resultState = smartSearchResult
                            if (resultState is SmartSearchResult.Exact) {
                                PdfSearchResultsList(
                                    results = resultState.matches, onResultClick = { result ->
                                        navigateToPdfSearchResult(result)
                                        searchState.showSearchResultsPanel = false
                                        keyboardController?.hide()
                                    }, modifier = Modifier.fillMaxSize()
                                )
                            } else if (resultState is SmartSearchResult.Paged) {
                                val lazyPagingItems =
                                    resultState.pagingData.collectAsLazyPagingItems()
                                PdfSearchResultsPanel(
                                    lazyResults = lazyPagingItems,
                                    totalPageCount = resultState.totalPageCount,
                                    onResultClick = { result ->
                                        navigateToPdfSearchResult(result)
                                        searchState.showSearchResultsPanel = false
                                        keyboardController?.hide()
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Search Navigation Controls
                AnimatedVisibility(
                    visible = searchState.isSearchActive && !searchState.showSearchResultsPanel && smartSearchResult != null,
                    enter = slideInVertically(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200)),
                    exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(200)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
                    val currentResult = currentPdfSearchResult
                    val searchData = smartSearchResult

                    val (displayText, isPrevEnabled, isNextEnabled) = remember(
                        currentResult,
                        searchData
                    ) {
                        when (searchData) {
                            is SmartSearchResult.Exact -> {
                                val index = if (currentResult != null) searchData.matches.indexOf(
                                    currentResult
                                )
                                else -1
                                val text =
                                    if (index >= 0) "Result ${index + 1} / ${searchData.matches.size}"
                                    else "${searchData.matches.size} Results"
                                Triple(text, index > 0, index < searchData.matches.size - 1)
                            }

                            is SmartSearchResult.Paged -> {
                                val page = currentResult?.locationInSource
                                val text = if (page != null) "Page ${page + 1}"
                                else "${searchData.totalPageCount}+ Pages"
                                Triple(text, true, true)
                            }

                            else -> Triple("", false, false)
                        }
                    }

                    SearchNavigationPill(
                        text = displayText,
                        mode = searchHighlightMode,
                        onToggleMode = {
                            searchHighlightMode =
                                if (searchHighlightMode == SearchHighlightMode.ALL) SearchHighlightMode.FOCUSED
                                else SearchHighlightMode.ALL
                        },
                        onPrev = {
                            coroutineScope.launch {
                                when (searchData) {
                                    is SmartSearchResult.Exact -> {
                                        val index =
                                            if (currentResult != null) searchData.matches.indexOf(
                                                currentResult
                                            )
                                            else -1
                                        if (index > 0) {
                                            navigateToPdfSearchResult(
                                                searchData.matches[index - 1]
                                            )
                                        }
                                    }

                                    is SmartSearchResult.Paged -> {
                                        val prev = pdfTextRepository.getPrevResult(
                                            currentBookId!!,
                                            searchState.searchQuery,
                                            currentPdfSearchResult
                                        )
                                        if (prev != null) navigateToPdfSearchResult(prev)
                                    }

                                    else -> {}
                                }
                            }
                        },
                        onNext = {
                            coroutineScope.launch {
                                when (searchData) {
                                    is SmartSearchResult.Exact -> {
                                        val index =
                                            if (currentResult != null) searchData.matches.indexOf(
                                                currentResult
                                            )
                                            else -1
                                        if (index >= 0 && index < searchData.matches.size - 1) {
                                            navigateToPdfSearchResult(
                                                searchData.matches[index + 1]
                                            )
                                        } else if (index == -1 && searchData.matches.isNotEmpty()) {
                                            navigateToPdfSearchResult(searchData.matches[0])
                                        }
                                    }

                                    is SmartSearchResult.Paged -> {
                                        val next = pdfTextRepository.getNextResult(
                                            currentBookId!!,
                                            searchState.searchQuery,
                                            currentPdfSearchResult
                                        )
                                        if (next != null) navigateToPdfSearchResult(next)
                                    }

                                    else -> {}
                                }
                            }
                        },
                        onTextClick = { searchState.showSearchResultsPanel = true },
                        isPrevEnabled = isPrevEnabled,
                        isNextEnabled = isNextEnabled
                    )
                }

                // Bottom Bar
                AnimatedVisibility(
                    visible = showStandardBars && !searchState.isSearchActive,
                    enter = slideInVertically(animationSpec = tween(200)) { fullHeight -> fullHeight } + fadeIn(animationSpec = tween(200)),
                    exit = slideOutVertically(animationSpec = tween(200)) { fullHeight -> fullHeight } + fadeOut(animationSpec = tween(200)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            // Slider Navigation Trigger
                            IconButton(
                                onClick = {
                                    val currentPage = if (displayMode == DisplayMode.PAGINATION) {
                                        pagerState.currentPage
                                    } else {
                                        verticalReaderState.currentPage
                                    }
                                    sliderStartPage = currentPage
                                    sliderCurrentPage = currentPage.toFloat()
                                    isPageSliderVisible = true
                                    showBars = false
                                }, enabled = !(ttsState.isPlaying || ttsState.isLoading)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.slider),
                                    contentDescription = "Navigate with slider"
                                )
                            }

                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } },
                                enabled = !(ttsState.isPlaying || ttsState.isLoading),
                                modifier = Modifier.testTag("TocButton")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Table of Contents"
                                )
                            }

                            // Search Button
                            IconButton(
                                onClick = {
                                    executeWithOcrCheck {
                                        searchState.isSearchActive = true
                                        showBars = true
                                    }
                                },
                                enabled = !(ttsState.isPlaying || ttsState.isLoading),
                                modifier = Modifier.testTag("SearchButton")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            }

                            IconButton(
                                onClick = {
                                    val newState = !showAllTextHighlights
                                    if (newState) {
                                        if (isHighlightingLoading) return@IconButton
                                        showAllTextHighlights = true
                                        isHighlightingLoading = true
                                    } else {
                                        showAllTextHighlights = false
                                        isHighlightingLoading = false
                                    }
                                }) {
                                if (isHighlightingLoading) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.highlight_text),
                                        contentDescription = "Highlight all text",
                                        tint = if (showAllTextHighlights) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // AI feat
                            Box {
                                var showAiFeaturesMenu by remember { mutableStateOf(false) }
                                IconButton(onClick = { showAiFeaturesMenu = true }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ai),
                                        contentDescription = "AI Features"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showAiFeaturesMenu,
                                    onDismissRequest = { showAiFeaturesMenu = false }) {
                                    DropdownMenuItem(
                                        text = {
                                            Text("Summarize Page (Page ${currentPage + 1})")
                                        }, onClick = {
                                            showAiFeaturesMenu = false
                                            if (isProUser) {
                                                showSummarizationPopup = true
                                                coroutineScope.launch {
                                                    isSummarizationLoading = true
                                                    summarizationResult = null
                                                    summarizeCurrentPage(onUpdate = { result ->
                                                        summarizationResult = result
                                                    }, onFinish = {
                                                        isSummarizationLoading = false
                                                    })
                                                }
                                            } else {
                                                showSummarizationUpsellDialog = true
                                            }
                                        }, enabled = !isSummarizationLoading && pdfDocument != null
                                    )
                                }
                            }

                            // Edit Button
                            IconButton(
                                onClick = {
                                    val newEditMode = !isEditMode
                                    val currentActivePage = richTextController?.activePageIndex ?: -1

                                    Timber.tag("RichTextMigration").i("Edit Toggle: $isEditMode -> $newEditMode (ActivePage: $currentActivePage)")

                                    if (!newEditMode && richTextController != null) {
                                        coroutineScope.launch {
                                            richTextController.saveImmediate()
                                            withContext(Dispatchers.Main) {
                                                keyboardController?.hide()
                                            }
                                        }
                                    }

                                    isEditMode = newEditMode
                                    if (!newEditMode) showBars = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Toggle Editing Mode",
                                    tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // TTS
                            IconButton(
                                onClick = {
                                    if (isTtsSessionActive) {
                                        Timber.d("TTS button clicked: Stopping TTS")
                                        ttsController.stop()
                                    } else {
                                        executeWithOcrCheck {
                                            when {
                                                ContextCompat.checkSelfPermission(
                                                    context, Manifest.permission.POST_NOTIFICATIONS
                                                ) == PackageManager.PERMISSION_GRANTED -> {
                                                    startTts()
                                                }

                                                activity?.shouldShowRequestPermissionRationale(
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                ) == true -> {
                                                    showPermissionRationaleDialog = true
                                                }

                                                else -> {
                                                    permissionLauncher.launch(
                                                        Manifest.permission.POST_NOTIFICATIONS
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }) {
                                Icon(
                                    painter = if (isTtsSessionActive) painterResource(id = R.drawable.close)
                                    else painterResource(
                                        id = R.drawable.text_to_speech
                                    ),
                                    contentDescription = if (isTtsSessionActive) "Stop TTS" else "Start TTS"
                                )
                            }

                            // TTS Pause/Resume Button
                            if (isTtsSessionActive) {
                                IconButton(
                                    onClick = {
                                        if (ttsState.isPlaying) {
                                            ttsController.pause()
                                        } else {
                                            ttsController.resume()
                                        }
                                    }, enabled = !ttsState.isLoading
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (ttsState.isPlaying) R.drawable.pause
                                            else R.drawable.play
                                        ), contentDescription = if (ttsState.isPlaying) "Pause TTS"
                                        else "Resume TTS"
                                    )
                                }
                            } else {
                                Spacer(Modifier.size(48.dp))
                            }

                            // Error Message Area
                            ttsState.errorMessage?.let {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Full Screen Exit Button
                AnimatedVisibility(
                    visible = isFullScreen,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                ) {
                    val isBackgroundDark = displayMode == DisplayMode.PAGINATION || isPdfDarkMode

                    val fabContainerColor = if (isBackgroundDark) Color.White.copy(alpha = 0.25f)
                    else Color.Black.copy(alpha = 0.25f)
                    val fabContentColor = if (isBackgroundDark) Color.White else Color.Black

                    Surface(
                        onClick = {
                            isFullScreen = false
                            savePdfFullScreen(context, bookId, false)
                        },
                        color = fabContainerColor,
                        contentColor = fabContentColor,
                        shape = CircleShape,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Exit Full Screen",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                if (isEditMode) {
                    val density = LocalDensity.current

                    val popupPlacementConfig =
                        remember(dockLocation, dockOffset, boxMaxHeightFloat, dockHeightPx) {
                            val margin = 16.dp
                            with(density) { boxMaxHeightFloat.toDp() }

                            val dockTopY = when (dockLocation) {
                                DockLocation.TOP -> 0f
                                DockLocation.BOTTOM -> boxMaxHeightFloat - dockHeightPx
                                DockLocation.FLOATING -> dockOffset.y
                            }

                            val dockBottomY = dockTopY + dockHeightPx
                            val dockCenterY = dockTopY + (dockHeightPx / 2f)
                            val isDockInBottomHalf = dockCenterY > (boxMaxHeightFloat / 2f)

                            if (isDockInBottomHalf) {
                                val distFromBottom = boxMaxHeightFloat - dockTopY
                                val paddingBottom = with(density) { distFromBottom.toDp() } + margin
                                Triple(Alignment.BottomCenter, 0.dp, paddingBottom)
                            } else {
                                val paddingTop = with(density) { dockBottomY.toDp() } + margin
                                Triple(Alignment.TopCenter, paddingTop, 0.dp)
                            }
                        }

                    val (popupAlign, popupTopPad, popupBottomPad) = popupPlacementConfig

                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(
                            visible = showToolSettings,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(popupAlign)
                                .padding(top = popupTopPad, bottom = popupBottomPad)
                                .testTag("ToolSettingsPopup")
                        ) {
                            val currentPalette =
                                if (isCurrentToolHighlighter) highlighterPalette else penPalette

                            ToolSettingsPopup(
                                selectedTool = selectedTool,
                                activeToolThickness = activeToolThickness,
                                fountainPenColor = fountainPenColor,
                                markerColor = markerColor,
                                pencilColor = pencilColor,
                                highlighterColor = highlighterColor,
                                highlighterRoundColor = highlighterRoundColor,
                                activePalette = currentPalette,
                                onToolTypeChanged = { newType ->
                                    annotationSettingsRepo.updateSelectedTool(newType)
                                },
                                onColorChanged = { color ->
                                    annotationSettingsRepo.updateToolColor(selectedTool, color)
                                },
                                onThicknessChanged = { thickness ->
                                    annotationSettingsRepo.updateToolThickness(
                                        selectedTool, thickness
                                    )
                                },
                                onPaletteChange = { newPalette ->
                                    if (isCurrentToolHighlighter) {
                                        annotationSettingsRepo.updateHighlighterPalette(
                                            newPalette
                                        )
                                    } else {
                                        annotationSettingsRepo.updatePenPalette(newPalette)
                                    }
                                },
                                isHighlighterSnapEnabled = isHighlighterSnapEnabled,
                                onSnapToggle = { annotationSettingsRepo.updateHighlighterSnap(it) }
                            )
                        }

                        snapPreviewLocation?.let { location ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(dockHeight)
                                    .align(
                                        if (location == DockLocation.TOP) Alignment.TopCenter
                                        else Alignment.BottomCenter
                                    )
                                    .background(Color.Black)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    when {
                                        isDockDragging -> Modifier // Positioned manually
                                        // via offset during
                                        // drag
                                        dockLocation == DockLocation.TOP -> Modifier // Aligned via Box
                                        // Scope
                                        dockLocation == DockLocation.BOTTOM -> Modifier // Aligned via Box
                                        // Scope
                                        else -> Modifier // Positioned manually
                                        // via offset
                                    }
                                )
                        ) {
                            // Calculate drag offset to apply if floating/dragging
                            val dragModifier =
                                if (isDockDragging || dockLocation == DockLocation.FLOATING) {
                                    Modifier.offset {
                                        IntOffset(
                                            dockOffset.x.roundToInt(), dockOffset.y.roundToInt()
                                        )
                                    }
                                } else {
                                    Modifier // Sticky positions use alignment below
                                }

                            // Calculate Alignment for Sticky states
                            val alignModifier = when {
                                isDockDragging || dockLocation == DockLocation.FLOATING -> Modifier
                                dockLocation == DockLocation.TOP -> Modifier.align(Alignment.TopCenter)
                                dockLocation == DockLocation.BOTTOM -> Modifier.align(Alignment.BottomCenter)
                                else -> Modifier
                            }

                            val widthModifier =
                                if ((dockLocation == DockLocation.TOP || dockLocation == DockLocation.BOTTOM) && !isDockDragging) {
                                    Modifier.fillMaxWidth()
                                } else {
                                    Modifier.padding(
                                        horizontal = 16.dp
                                    ) // Original padding for floating capsule
                                }

                            val paddingModifier =
                                if ((dockLocation == DockLocation.TOP || dockLocation == DockLocation.BOTTOM) && !isDockDragging) {
                                    Modifier
                                } else {
                                    Modifier.padding(vertical = 16.dp)
                                }

                            val isSticky =
                                (dockLocation == DockLocation.TOP || dockLocation == DockLocation.BOTTOM) && !isDockDragging

                            Box(
                                modifier = Modifier
                                    .then(alignModifier)
                                    .then(dragModifier)
                                    .pointerInput(dockLocation, isDockMinimized) {
                                        val onDragStart: (Offset) -> Unit = {
                                            isDockDragging = true

                                            val startX = (boxMaxWidthFloat / 2) - (size.width / 2)

                                            if (dockLocation == DockLocation.BOTTOM) {
                                                dockOffset = Offset(
                                                    startX, boxMaxHeightFloat - dockHeightPx - 50f
                                                )
                                            } else if (dockLocation == DockLocation.TOP) {
                                                dockOffset = Offset(startX, 50f)
                                            }
                                        }

                                        val onDrag: (
                                            PointerInputChange, Offset
                                        ) -> Unit = { change, dragAmount ->
                                            change.consume()
                                            dockOffset += dragAmount

                                            val topSnapThreshold = 150f
                                            val bottomSnapThreshold = boxMaxHeightFloat - 250f

                                            snapPreviewLocation = when {
                                                dockOffset.y < topSnapThreshold -> DockLocation.TOP
                                                dockOffset.y > bottomSnapThreshold -> DockLocation.BOTTOM
                                                else -> null
                                            }
                                        }

                                        val onDragEnd: () -> Unit = {
                                            isDockDragging = false
                                            if (snapPreviewLocation != null) {
                                                dockLocation = snapPreviewLocation!!
                                                snapPreviewLocation = null
                                            } else {
                                                dockLocation = DockLocation.FLOATING
                                                val safeX = dockOffset.x.coerceIn(
                                                    0f, boxMaxWidthFloat - 100f
                                                )
                                                val safeY = dockOffset.y.coerceIn(
                                                    0f, boxMaxHeightFloat - dockHeightPx
                                                )
                                                dockOffset = Offset(safeX, safeY)
                                            }
                                            saveDockState(
                                                context, dockLocation, dockOffset
                                            )
                                        }

                                        val onDragCancel: () -> Unit = {
                                            isDockDragging = false
                                            snapPreviewLocation = null
                                        }

                                        if (dockLocation == DockLocation.FLOATING) {
                                            detectDragGestures(
                                                onDragStart = onDragStart,
                                                onDrag = onDrag,
                                                onDragEnd = onDragEnd,
                                                onDragCancel = onDragCancel
                                            )
                                        } else {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = onDragStart,
                                                onDrag = onDrag,
                                                onDragEnd = onDragEnd,
                                                onDragCancel = onDragCancel
                                            )
                                        }
                                    }) {
                                AnnotationDock(
                                    selectedTool = selectedTool,
                                    activePenColor = dockPenColor,
                                    activeHighlighterColor = dockHighlighterColor,
                                    lastPenTool = lastPenTool,
                                    isStylusOnlyMode = isStylusOnlyMode,
                                    onToggleStylusOnlyMode = {
                                        isStylusOnlyMode = !isStylusOnlyMode
                                        saveStylusOnlyMode(context, isStylusOnlyMode)
                                    },
                                    onToolClick = { clickedTool ->
                                        if (clickedTool == InkType.ERASER || clickedTool == InkType.TEXT) {
                                            annotationSettingsRepo.updateSelectedTool(
                                                clickedTool
                                            )
                                            showToolSettings = false
                                        } else if (selectedTool == clickedTool) {
                                            if (clickedTool == InkType.PEN || clickedTool == InkType.FOUNTAIN_PEN || clickedTool == InkType.PENCIL || clickedTool == InkType.HIGHLIGHTER || clickedTool == InkType.HIGHLIGHTER_ROUND) {
                                                showToolSettings = !showToolSettings
                                            }
                                        } else {
                                            if (showToolSettings) {
                                                coroutineScope.launch {
                                                    showToolSettings = false
                                                    delay(250)
                                                    annotationSettingsRepo.updateSelectedTool(
                                                        clickedTool
                                                    )
                                                    showToolSettings = true
                                                }
                                            } else {
                                                annotationSettingsRepo.updateSelectedTool(
                                                    clickedTool
                                                )
                                            }
                                        }
                                    },
                                    onUndo = {
                                        if (undoStack.isNotEmpty()) {
                                            val action = undoStack.removeAt(undoStack.lastIndex)
                                            when (action) {
                                                is HistoryAction.Add -> {
                                                    val pageIndex = action.pageIndex
                                                    val annotation = action.annotation
                                                    val pageAnnotations =
                                                        allAnnotations[pageIndex] ?: emptyList()

                                                    val newForPage = pageAnnotations - annotation
                                                    allAnnotations =
                                                        allAnnotations + (pageIndex to newForPage)

                                                    redoStack.add(action)
                                                }

                                                is HistoryAction.Remove -> {
                                                    var currentAllAnnotations = allAnnotations
                                                    action.items.forEach { (pageIndex, annotations) ->
                                                        val pageList =
                                                            currentAllAnnotations[pageIndex]
                                                                ?: emptyList()
                                                        currentAllAnnotations =
                                                            currentAllAnnotations + (pageIndex to (pageList + annotations))
                                                    }
                                                    allAnnotations = currentAllAnnotations

                                                    redoStack.add(action)
                                                }
                                            }
                                        }
                                    },
                                    onRedo = {
                                        if (redoStack.isNotEmpty()) {
                                            val action = redoStack.removeAt(redoStack.lastIndex)
                                            when (action) {
                                                is HistoryAction.Add -> {
                                                    val pageIndex = action.pageIndex
                                                    val annotation = action.annotation
                                                    val pageAnnotations =
                                                        allAnnotations[pageIndex] ?: emptyList()

                                                    val newForPage = pageAnnotations + annotation
                                                    allAnnotations =
                                                        allAnnotations + (pageIndex to newForPage)

                                                    undoStack.add(action)
                                                }

                                                is HistoryAction.Remove -> {
                                                    var currentAllAnnotations = allAnnotations
                                                    action.items.forEach { (pageIndex, annotations) ->
                                                        val pageList =
                                                            currentAllAnnotations[pageIndex]
                                                                ?: emptyList()
                                                        val newForPage =
                                                            pageList - annotations.toSet()
                                                        currentAllAnnotations =
                                                            currentAllAnnotations + (pageIndex to newForPage)
                                                    }
                                                    allAnnotations = currentAllAnnotations

                                                    undoStack.add(action)
                                                }
                                            }
                                        }
                                    },
                                    onClose = {
                                        richTextController?.clearSelection()
                                        isEditMode = false
                                        isDockMinimized = false
                                        showBars = true
                                    },
                                    canUndo = undoStack.isNotEmpty(),
                                    canRedo = redoStack.isNotEmpty(),
                                    isSticky = isSticky,
                                    modifier = Modifier
                                        .then(widthModifier)
                                        .then(paddingModifier),
                                    isMinimized = isDockMinimized,
                                    onToggleMinimize = { isDockMinimized = !isDockMinimized })
                            }
                        }
                    }
                }

                val ttsReadingPage = ttsPageData?.pageIndex

                val isTtsPageVisible by remember(
                    ttsReadingPage,
                    displayMode,
                    verticalReaderState.firstVisiblePage,
                    verticalReaderState.lastVisiblePage
                ) {
                    derivedStateOf {
                        if (displayMode != DisplayMode.VERTICAL_SCROLL || ttsReadingPage == null) {
                            true
                        } else {
                            ttsReadingPage in verticalReaderState.firstVisiblePage..verticalReaderState.lastVisiblePage
                        }
                    }
                }

                val showScrollToTtsFab by remember(
                    displayMode,
                    isTtsSessionActive,
                    isTtsPageVisible
                ) {
                    derivedStateOf {
                        displayMode == DisplayMode.VERTICAL_SCROLL && isTtsSessionActive && !isTtsPageVisible
                    }
                }

                AnimatedVisibility(
                    visible = showScrollToTtsFab,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (showBars) 56.dp + 16.dp else 16.dp),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val isTtsPageBelow = (ttsReadingPage ?: 0) > verticalReaderState.currentPage
                    FloatingActionButton(
                        onClick = {
                            ttsReadingPage?.let {
                                coroutineScope.launch { verticalReaderState.scrollToPage(it) }
                            }
                        },
                        shape = CircleShape,
                        containerColor = Color.Black.copy(alpha = 0.7f),
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                    ) {
                        Icon(
                            imageVector = if (isTtsPageBelow) Icons.Default.ArrowDownward
                            else Icons.Default.ArrowUpward,
                            contentDescription = "Scroll to reading page"
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showZoomIndicator,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 88.dp, end = 16.dp),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val percentage = (currentPageScale * 100).roundToInt()
                    ZoomPercentageIndicator(percentage = percentage)
                }

                val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                var isTextAnnotationPopupVisible by remember { mutableStateOf(false) }
                val showTextDock = isEditMode && selectedTool == InkType.TEXT && (isImeVisible || isTextAnnotationPopupVisible || selectedTextBoxId != null)

                if (showTextDock && richTextController != null) {

                    val bottomPadding = if (dockLocation == DockLocation.BOTTOM && !isDockMinimized) {
                        80.dp
                    } else {
                        16.dp
                    }

                    val currentDensity = LocalDensity.current

                    val effectiveStyle by remember(selectedTextBoxId, textBoxes, richTextController.currentStyle, displayPageRatios, boxMaxWidthFloat) {
                        derivedStateOf {
                            if (selectedTextBoxId != null) {
                                val box = textBoxes.find { it.id == selectedTextBoxId }
                                if (box != null) {
                                    val pageRatio = displayPageRatios.getOrElse(box.pageIndex) { 1f }
                                    val estimatedPageHeightPx = if (pageRatio > 0) boxMaxWidthFloat / pageRatio else boxMaxWidthFloat

                                    val fontSizePx = box.fontSize * estimatedPageHeightPx
                                    val fontSizeSp = with(currentDensity) { fontSizePx.toSp() }

                                    SpanStyle(
                                        color = box.color,
                                        background = box.backgroundColor,
                                        fontSize = fontSizeSp,
                                        fontWeight = if (box.isBold) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (box.isItalic) FontStyle.Italic else FontStyle.Normal,
                                        textDecoration = run {
                                            val decs = mutableListOf<TextDecoration>()
                                            if (box.isUnderline) decs.add(TextDecoration.Underline)
                                            if (box.isStrikeThrough) decs.add(TextDecoration.LineThrough)
                                            if (decs.isEmpty()) TextDecoration.None else TextDecoration.combine(decs)
                                        }
                                    )
                                } else richTextController.currentStyle
                            } else {
                                richTextController.currentStyle
                            }
                        }
                    }

                    Box(modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()) {
                        TextAnnotationDock(
                            currentStyle = effectiveStyle,
                            textColorPalette = penPalette,
                            onTextColorPaletteChange = { newPalette ->
                                annotationSettingsRepo.updatePenPalette(newPalette)
                            },
                            backgroundColorPalette = highlighterPalette,
                            onBackgroundColorPaletteChange = { newPalette ->
                                annotationSettingsRepo.updateHighlighterPalette(newPalette)
                            },
                            onUpdateStyle = { newStyle ->
                                if (selectedTextBoxId != null) {
                                    val idx = textBoxes.indexOfFirst { it.id == selectedTextBoxId }
                                    if (idx != -1) {
                                        val old = textBoxes[idx]
                                        val pageRatio = displayPageRatios.getOrElse(old.pageIndex) { 1f }
                                        val estimatedPageHeightPx = if (pageRatio > 0) boxMaxWidthFloat / pageRatio else boxMaxWidthFloat

                                        val newFontSizePx = with(currentDensity) { newStyle.fontSize.toPx() }
                                        val newFontSizeNorm = if (estimatedPageHeightPx > 0) newFontSizePx / estimatedPageHeightPx else old.fontSize

                                        textBoxes[idx] = old.copy(
                                            color = newStyle.color,
                                            backgroundColor = newStyle.background,
                                            fontSize = newFontSizeNorm,
                                            isBold = newStyle.fontWeight == FontWeight.Bold,
                                            isItalic = newStyle.fontStyle == FontStyle.Italic,
                                            isUnderline = newStyle.textDecoration?.contains(TextDecoration.Underline) == true,
                                            isStrikeThrough = newStyle.textDecoration?.contains(TextDecoration.LineThrough) == true
                                        )
                                    }
                                } else {
                                    richTextController.updateCurrentStyle(newStyle)

                                    val newConfig = TextStyleConfig(
                                        colorArgb = newStyle.color.toArgb(),
                                        backgroundColorArgb = newStyle.background.toArgb(),
                                        fontSize = newStyle.fontSize.value,
                                        isBold = newStyle.fontWeight == FontWeight.Bold,
                                        isItalic = newStyle.fontStyle == FontStyle.Italic,
                                        isUnderline = newStyle.textDecoration?.contains(TextDecoration.Underline) == true,
                                        isStrikeThrough = newStyle.textDecoration?.contains(TextDecoration.LineThrough) == true,
                                        fontPath = toolSettings.textStyle.fontPath,
                                        fontName = toolSettings.textStyle.fontName
                                    )
                                    annotationSettingsRepo.updateTextStyle(newConfig)
                                }
                            },
                            onApplyToSelection = {},
                            onClose = { keyboardController?.hide() },
                            onPopupStateChange = { isVisible ->
                                isTextAnnotationPopupVisible = isVisible
                                richTextController.showCursorOverride = !isVisible
                            },
                            onInsertTextBox = onInsertTextBox,
                            onClearTextBoxSelection = {
                                selectedTextBoxId = null
                                richTextController.clearSelection()
                            },
                            bottomDockPadding = bottomPadding,
                            customFonts = customFonts,
                            onImportFont = viewModel::importFont,
                            onFontSelected = { name, path ->
                                Timber.tag("PdfFontDebug").i("UI Action: Font Selected -> Name: $name, Path: $path")
                                if (selectedTextBoxId != null) {
                                    val idx = textBoxes.indexOfFirst { it.id == selectedTextBoxId }
                                    if (idx != -1) {
                                        val oldBox = textBoxes[idx]
                                        textBoxes[idx] = oldBox.copy(fontPath = path, fontName = name)
                                        Timber.tag("PdfTextBoxDebug").d("Updated TextBox ${oldBox.id} font to: $name ($path)")
                                    }
                                    Timber.tag("PdfFontDebug").d("UI: Updating TextBox $selectedTextBoxId with font path: $path")
                                } else {
                                    val currentConfig = toolSettings.textStyle
                                    val newConfig = currentConfig.copy(fontPath = path, fontName = name)
                                    annotationSettingsRepo.updateTextStyle(newConfig)

                                    richTextController.let { controller ->
                                        val style = SpanStyle(
                                            color = Color(newConfig.colorArgb),
                                            background = Color(newConfig.backgroundColorArgb),
                                            fontSize = newConfig.fontSize.sp,
                                            fontWeight = if (newConfig.isBold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (newConfig.isItalic) FontStyle.Italic else FontStyle.Normal,
                                            textDecoration = run {
                                                val decs = mutableListOf<TextDecoration>()
                                                if (newConfig.isUnderline) decs.add(TextDecoration.Underline)
                                                if (newConfig.isStrikeThrough) decs.add(TextDecoration.LineThrough)
                                                if (decs.isEmpty()) TextDecoration.None else TextDecoration.combine(decs)
                                            },
                                            fontFamily = PdfFontCache.getFontFamily(path)
                                        )
                                        Timber.tag("PdfFontDebug").d("UI Action: Manually updating controller with font $path")
                                        controller.updateCurrentStyle(style, path, name)
                                    }
                                }
                            },
                            currentFontName = remember(selectedTextBoxId, textBoxes, toolSettings.textStyle) {
                                if (selectedTextBoxId != null) {
                                    val box = textBoxes.find { it.id == selectedTextBoxId }
                                    box?.fontName ?: box?.fontPath?.let { File(it).nameWithoutExtension }
                                } else {
                                    toolSettings.textStyle.fontName ?: toolSettings.textStyle.fontPath?.let { File(it).nameWithoutExtension }
                                }
                            },
                        )
                    }
                }

                if (showSummarizationPopup) {
                    SummarizationPopup(
                        title = "Page Summary",
                        result = summarizationResult,
                        isLoading = isSummarizationLoading,
                        onDismiss = { showSummarizationPopup = false },
                        isMainTtsActive = isTtsSessionActive
                    )
                }
                if (showPermissionRationaleDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermissionRationaleDialog = false },
                        title = { Text("Permission Required") },
                        text = {
                            Text(
                                "To show playback controls while the app is in the background, please grant the notification permission."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showPermissionRationaleDialog = false
                                    permissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                }) { Text("Continue") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showPermissionRationaleDialog = false
                                    startTts()
                                }) { Text("Not now") }
                        })
                }
                if (showSummarizationUpsellDialog) {
                    AlertDialog(
                        onDismissRequest = { showSummarizationUpsellDialog = false },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.summarize),
                                contentDescription = null
                            )
                        },
                        title = { Text("Unlock Page Summarization") },
                        text = {
                            Text(
                                "Get concise summaries of any page with Episteme Pro. Upgrade to start using this feature."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showSummarizationUpsellDialog = false
                                    onNavigateToPro()
                                }) { Text("Learn More") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSummarizationUpsellDialog = false }) {
                                Text("Not Now")
                            }
                        })
                }

                if (showPasswordDialog) {
                    PasswordDialog(
                        isError = isPasswordError,
                        onDismiss = { onNavigateBack() },
                        onConfirm = { password -> documentPassword = password })
                }

                if (showPenPlayground) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { showPenPlayground = false },
                        contentAlignment = Alignment.Center
                    ) { PenPlayground(onClose = { showPenPlayground = false }) }
                }

                if (showAiDefinitionPopup) {
                    AiDefinitionPopup(
                        word = selectedTextForAi,
                        result = aiDefinitionResult,
                        isLoading = isAiDefinitionLoading,
                        onDismiss = {
                            showAiDefinitionPopup = false
                            selectedTextForAi = null
                            aiDefinitionResult = null
                        },
                        isMainTtsActive = isTtsSessionActive,
                        onOpenExternalDictionary = {
                            selectedTextForAi?.let { text ->
                                if (selectedDictPackage != null) {
                                    ExternalDictionaryHelper.launchDictionary(context, selectedDictPackage!!, text)
                                } else {
                                    Toast.makeText(context, "Select an offline dictionary first.", Toast.LENGTH_SHORT).show()
                                    showDictionarySettingsSheet = true
                                }
                            }
                        }
                    )
                }
                if (showDictionaryUpsellDialog) {
                    AlertDialog(onDismissRequest = { showDictionaryUpsellDialog = false }, icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ai),
                            contentDescription = null
                        )
                    }, title = { Text("Unlock Smart Dictionary") }, text = {
                        Text(
                            "Defining entire phrases and paragraphs up to 2000 characters is a Pro feature. Upgrade to get instant definitions for any selected text."
                        )
                    }, confirmButton = {
                        TextButton(
                            onClick = {
                                showDictionaryUpsellDialog = false
                                onNavigateToPro()
                            }) { Text("Learn More") }
                    }, dismissButton = {
                        TextButton(onClick = { showDictionaryUpsellDialog = false }) {
                            Text("Not Now")
                        }
                    })
                }

                showReindexDialog?.let { newLanguage ->
                    if (BuildConfig.IS_PRO) {
                        AlertDialog(
                            onDismissRequest = { showReindexDialog = null },
                            icon = { Icon(Icons.Default.Info, contentDescription = null) },
                            title = { Text("Re-index Document?") },
                            text = {
                                Text(
                                    "You are changing the OCR script to ${newLanguage.displayName}.\n\n" +
                                            "To ensure search accuracy, we need to clear the existing index and re-scan pages that require OCR using this new language.\n\n" +
                                            "This will happen in the background."
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            ocrLanguage = newLanguage
                                            saveOcrLanguage(context, newLanguage)
                                            hasSelectedOcrLanguage = true

                                            currentBookId?.let { id ->
                                                isBackgroundIndexing = true
                                                backgroundIndexingProgress = 0f
                                                withContext(Dispatchers.IO) {
                                                    pdfTextRepository.clearBookText(id)
                                                    pdfTextRepository.setBookLanguage(id, newLanguage.name)
                                                }
                                                isBackgroundIndexing = false
                                            }

                                            pendingActionAfterOcrSelection?.invoke()
                                            pendingActionAfterOcrSelection = null
                                            showReindexDialog = null
                                            showOcrLanguageDialog = false
                                        }
                                    }
                                ) { Text("Re-index") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showReindexDialog = null }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    } else {
                        showReindexDialog = null
                    }
                }

                if (showOcrLanguageDialog) {
                    OcrLanguageSelectionDialog(
                        currentLanguage = ocrLanguage,
                        isFirstRun = !hasSelectedOcrLanguage,
                        onDismiss = {
                            showOcrLanguageDialog = false
                            pendingActionAfterOcrSelection = null
                        },
                        onLanguageSelected = { selected ->
                            coroutineScope.launch {
                                val storedLangName = currentBookId?.let {
                                    pdfTextRepository.getBookLanguage(it)
                                }

                                val hasIndexedPages = currentBookId?.let {
                                    pdfTextRepository.getIndexedPages(it).isNotEmpty()
                                } == true

                                if (hasIndexedPages && storedLangName != null && storedLangName != selected.name) {
                                    showReindexDialog = selected
                                    showOcrLanguageDialog = false
                                } else {
                                    ocrLanguage = selected
                                    saveOcrLanguage(context, selected)
                                    hasSelectedOcrLanguage = true

                                    currentBookId?.let {
                                        pdfTextRepository.setBookLanguage(it, selected.name)
                                    }

                                    showOcrLanguageDialog = false
                                    pendingActionAfterOcrSelection?.invoke()
                                    pendingActionAfterOcrSelection = null
                                }
                            }
                        })
                }

                if (showTtsSettingsSheet) {
                    TtsSettingsSheet(
                        isVisible = true,
                        onDismiss = { showTtsSettingsSheet = false },
                        currentMode = currentTtsMode,
                        onModeChange = { newMode ->
                            currentTtsMode = newMode
                            saveTtsMode(context, newMode)
                            ttsController.changeTtsMode(newMode.name)
                        },
                        currentSpeakerId = ttsState.speakerId,
                        onSpeakerChange = { newSpeaker ->
                            ttsController.changeSpeaker(newSpeaker)
                        },
                        isTtsActive = isTtsSessionActive
                    )
                }

                if (showDictionarySettingsSheet) {
                    DictionarySettingsDialog(
                        isVisible = true,
                        onDismiss = { showDictionarySettingsSheet = false },
                        isProUser = isProUser,
                        useOnlineDictionary = useOnlineDictionary,
                        onToggleOnlineDictionary = { newState ->
                            useOnlineDictionary = newState
                            saveUseOnlineDict(context, newState)
                        },
                        selectedPackageName = selectedDictPackage,
                        onSelectPackage = { pkg ->
                            selectedDictPackage = pkg
                            saveExternalDictPackage(context, pkg)
                        }
                    )
                }

                if (showDeviceVoiceSettingsSheet) {
                    DeviceVoiceSettingsSheet(
                        isVisible = true,
                        onDismiss = { showDeviceVoiceSettingsSheet = false }
                    )
                }

                if (clickedLinkUrl != null) {
                    val url = clickedLinkUrl!!
                    AlertDialog(
                        onDismissRequest = { clickedLinkUrl = null },
                        title = { Text("External Link") },
                        text = { Text("You are about to navigate to:\n$url") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    try {
                                        uriHandler.openUri(url)
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to open URI")
                                    }
                                    clickedLinkUrl = null
                                }) { Text("Visit") }
                        },
                        dismissButton = {
                            Row {
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(url))
                                        clickedLinkUrl = null
                                    }) { Text("Copy") }
                                TextButton(onClick = { clickedLinkUrl = null }) {
                                    Text("Cancel")
                                }
                            }
                        })
                }

                if (showSaveDialog) {
                    AlertDialog(
                        onDismissRequest = { showSaveDialog = false },
                        title = { Text("Save to Device") },
                        text = { Text("Choose format to save:") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showSaveDialog = false
                                    pendingSaveMode = SaveMode.ANNOTATED
                                    val suggestedName = getSuggestedFilename(
                                        originalFileName, isAnnotated = true
                                    )
                                    saveLauncher.launch(suggestedName)
                                }) { Text("With Annotations") }
                        },
                        dismissButton = {
                            Row {
                                TextButton(
                                    onClick = {
                                        showSaveDialog = false
                                        pendingSaveMode = SaveMode.ORIGINAL
                                        val suggestedName = getSuggestedFilename(
                                            originalFileName, isAnnotated = false
                                        )
                                        saveLauncher.launch(suggestedName)
                                    }) { Text("Original") }

                                Spacer(Modifier.width(8.dp))

                                TextButton(
                                    onClick = {
                                        showSaveDialog = false
                                        pendingSaveMode = null
                                    }) { Text("Cancel") }
                            }
                        })
                }

                if (showShareDialog) {
                    AlertDialog(
                        onDismissRequest = { showShareDialog = false },
                        title = { Text("Share PDF") },
                        text = { Text("Choose format to share:") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showShareDialog = false
                                    isShareLoading = true
                                    Timber.tag("PdfExportDebug").i("SHARE TRIGGERED: userHighlights count: ${userHighlights.size}")
                                    val filename = getSuggestedFilename(
                                        originalFileName, isAnnotated = true
                                    )
                                    coroutineScope.launch {
                                        val currentRichTextLayouts = richTextController?.pageLayouts

                                        viewModel.sharePdf(
                                            activityContext = context,
                                            sourceUri = pdfUri,
                                            annotations = allAnnotations,
                                            richTextPageLayouts = currentRichTextLayouts,
                                            textBoxes = textBoxes.toList(),
                                            highlights = userHighlights.toList(),
                                            includeAnnotations = true,
                                            filename = filename,
                                            bookId = currentBookId
                                        )
                                        isShareLoading = false
                                    }
                                }) { Text("With Annotations") }
                        },
                        dismissButton = {
                            Row {
                                TextButton(
                                    onClick = {
                                        showShareDialog = false
                                        isShareLoading = true
                                        val filename = getSuggestedFilename(
                                            originalFileName, isAnnotated = false
                                        )
                                        coroutineScope.launch {
                                            viewModel.sharePdf(
                                                activityContext = context,
                                                sourceUri = pdfUri,
                                                annotations = allAnnotations,
                                                includeAnnotations = false,
                                                filename = filename
                                            )
                                            isShareLoading = false
                                        }
                                    }) { Text("Original") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { showShareDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        })
                }

                if (isShareLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(enabled = false) {}, contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp), strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Preparing PDF...",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }

                val autoScrollPadding by animateDpAsState(
                    targetValue = if (showBars) (56.dp + 16.dp) else 16.dp,
                    label = "AutoScrollPadding"
                )

                val isAutoScrollControlsVisible = isAutoScrollModeActive

                val alignmentBias by animateFloatAsState(
                    targetValue = if (isAutoScrollCollapsed) 1f else 0f,
                    label = "AutoScrollAlignAnimation"
                )

                AnimatedVisibility(
                    visible = isAutoScrollControlsVisible,
                    enter = slideInVertically(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200)),
                    exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(200)),
                    modifier = Modifier
                        .align(BiasAlignment(alignmentBias, 1f))
                        .padding(bottom = autoScrollPadding)
                        .padding(horizontal = 16.dp)
                ) {
                    AutoScrollControls(
                        isPlaying = isAutoScrollPlaying,
                        isTempPaused = isAutoScrollTempPaused,
                        onPlayPauseToggle = {
                            if (isAutoScrollPlaying) {
                                isAutoScrollPlaying = false
                                isAutoScrollTempPaused = false
                                autoScrollResumeJob.value?.cancel()
                            } else {
                                isAutoScrollPlaying = true
                                isAutoScrollTempPaused = false
                            }
                        },
                        speed = autoScrollSpeed,
                        minSpeed = autoScrollMinSpeed,
                        maxSpeed = autoScrollMaxSpeed,
                        onSpeedChange = { updateSpeed(it) },
                        onMinSpeedChange = { newMin ->
                            updateMinSpeed(newMin)
                            if (!isAutoScrollLocal) {
                                if (autoScrollMaxSpeed < newMin) {
                                    autoScrollMaxSpeed = newMin
                                    savePdfAutoScrollMaxSpeed(context, newMin)
                                }
                                if (autoScrollSpeed < newMin) {
                                    autoScrollSpeed = newMin
                                    savePdfAutoScrollSpeed(context, newMin)
                                } else if (autoScrollSpeed > autoScrollMaxSpeed) {
                                    autoScrollSpeed = autoScrollMaxSpeed
                                    savePdfAutoScrollSpeed(context, autoScrollMaxSpeed)
                                }
                            }
                        },
                        onMaxSpeedChange = { newMax ->
                            updateMaxSpeed(newMax)
                            if (!isAutoScrollLocal) {
                                if (autoScrollMinSpeed > newMax) {
                                    autoScrollMinSpeed = newMax
                                    savePdfAutoScrollMinSpeed(context, newMax)
                                }
                                if (autoScrollSpeed > newMax) {
                                    autoScrollSpeed = newMax
                                    savePdfAutoScrollSpeed(context, newMax)
                                } else if (autoScrollSpeed < autoScrollMinSpeed) {
                                    autoScrollSpeed = autoScrollMinSpeed
                                    savePdfAutoScrollSpeed(context, autoScrollMinSpeed)
                                }
                            }
                        },
                        onClose = {
                            isAutoScrollModeActive = false
                            isAutoScrollPlaying = false
                            showBars = true
                        },
                        isCollapsed = isAutoScrollCollapsed,
                        onCollapseChange = { isAutoScrollCollapsed = it },
                        isMusicianMode = isMusicianMode,
                        onMusicianModeToggle = {
                            val newMode = !isMusicianMode
                            isMusicianMode = newMode
                            savePdfMusicianMode(context, newMode)
                            if (newMode) {
                                showBars = false
                            }
                            Timber.d("Musician mode toggled: $newMode")
                        },
                        useSlider = autoScrollUseSlider,
                        onInputModeToggle = {
                            autoScrollUseSlider = !autoScrollUseSlider
                            savePdfAutoScrollUseSlider(context, autoScrollUseSlider)
                        },
                        isLocalMode = isAutoScrollLocal,
                        onLocalModeToggle = onToggleAutoScrollMode
                    )
                }
            }
        }
    }
}

@Composable
private fun PageScrubbingAnimation(currentPage: Int, totalPages: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(
                        alpha = 0.9f
                    ), shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.slider),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Page $currentPage of $totalPages",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ThumbnailWithIndicator(
    thumbnail: Bitmap, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .width(45.dp)
                .height(64.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(2.dp, borderColor)
        ) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = "Start page thumbnail",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(modifier = Modifier
            .offset(y = (-4).dp)
            .size(8.dp)
            .rotate(45f)
            .background(borderColor))
    }
}

private fun debugPdfLinks(
    context: Context, pdfUri: Uri, pdfiumCore: PdfiumCoreKt, coroutineScope: CoroutineScope
) {
    Timber.d("--- Starting PDF Link Analysis ---")
    Timber.d("URI: $pdfUri")

    coroutineScope.launch(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor?
        var doc: PdfDocumentKt? = null
        var page: PdfPageKt? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            if (pfd == null) {
                Timber.e("Failed to open ParcelFileDescriptor.")
                return@launch
            }
            doc = pdfiumCore.newDocument(pfd)
            val pageCount = doc.getPageCount()
            Timber.d("Document loaded. Page count: $pageCount")

            if (pageCount > 0) {
                val pageIndex = 0 // Testing the first page
                page = doc.openPage(pageIndex)
                Timber.d("Opened page $pageIndex")

                Timber.d(
                    "Performing a dummy 1x1 render with renderAnnot=true to force annotation parsing..."
                )
                val dummyBitmap = createBitmap(1, 1)
                page.renderPageBitmap(
                    bitmap = dummyBitmap,
                    startX = 0,
                    startY = 0,
                    drawSizeX = 1,
                    drawSizeY = 1,
                    renderAnnot = true // This is the crucial flag
                )
                dummyBitmap.recycle() // Clean up immediately
                Timber.d("Dummy render complete. Now checking for links again.")

                // Method 1: The one that is failing (now should work)
                val annotationLinks = page.getPageLinks()
                Timber.d("[METHOD 1] getPageLinks() found ${annotationLinks.size} links.")
                if (annotationLinks.isNotEmpty()) {
                    annotationLinks.forEachIndexed { index, link ->
                        Timber.d("  - Link ${index}: URI='${link.uri}', Bounds='${link.bounds}'")
                    }
                }

                // Method 2: The one that is working
                page.openTextPage().use { textPage ->
                    textPage.loadWebLink().use { webLinks ->
                        val webLinkCount = webLinks.countWebLinks()
                        Timber.d("[METHOD 2] loadWebLink() found $webLinkCount links.")
                        if (webLinkCount > 0) {
                            for (i in 0 until webLinkCount) {
                                val url = webLinks.getURL(i, 2048)
                                Timber.d(
                                    "  - WebLink ${i}: URL='${url?.substringBefore('\u0000')}'"
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "An error occurred during link debugging.")
        } finally {
            try {
                page?.close()
            } catch (_: Exception) {
            }
            try {
                doc?.close()
            } catch (_: Exception) {
            }
            Timber.d("--- PDF Link Analysis Finished ---")
        }
    }
}

@Composable
internal fun BookmarkButton(
    isBookmarked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(48.dp)
            .height(48.dp)
            .clip(RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ), contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(visible = isBookmarked, enter = fadeIn(), exit = fadeOut()) {
            Icon(
                painter = painterResource(id = R.drawable.bookmark),
                contentDescription = "Bookmark",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ZoomPercentageIndicator(percentage: Int) {
    Surface(
        shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f)
    ) {
        Text(
            text = "$percentage%",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PasswordDialog(isError: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Password Protected") }, text = {
        Column {
            Text("This document is encrypted. Please enter the password to view it.")
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardActions = KeyboardActions(onDone = { onConfirm(password) }),
                isError = isError,
                supportingText = if (isError) {
                    { Text("Incorrect password") }
                } else null,
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff

                    val description = if (passwordVisible) "Hide password" else "Show password"

                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, description)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }, confirmButton = {
        Button(onClick = { onConfirm(password) }, enabled = password.isNotBlank()) {
            Text("Open")
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun PenPlayground(onClose: () -> Unit) {
    var selectedPen by remember { mutableStateOf(PenType.FOUNTAIN_PEN) }
    var selectedColor by remember { mutableStateOf(Color(0xFF2196F3)) } // Default Blue
    val colors = listOf(
        Color(0xFFF44336), // Red
        Color(0xFFFFEB3B), // Yellow
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFF9C27B0), // Purple
        Color.White, // White
        Color.Black // Black
    )

    // Dark Card Background
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF1E1E1E), // Deep Matte Dark Grey
        shadowElevation = 16.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Placeholder icon (Star) on left
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.padding(start = 12.dp)
                )

                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(
                            id = R.drawable.close
                        ), // Ensure you have a close icon or use Icons.Default.Close
                        contentDescription = "Close", tint = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // --- The Pen Rack ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp), // Height for pens + ink stroke space
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                PenType.entries.forEach { type ->
                    val isSelected = selectedPen == type

                    // Selected pens float up slightly
                    val offsetY by animateDpAsState(
                        targetValue = if (isSelected) (-20).dp else 0.dp, label = "offset"
                    )

                    // Selected pens scale up
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.2f else 1.0f, label = "scale"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset(y = offsetY)
                            .scale(scale)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null // Remove ripple for cleaner look
                            ) { selectedPen = type }) {
                        // Drawing Area
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(120.dp), // Tall enough for stroke + pen
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            PenIcon(
                                color = selectedColor,
                                type = type,
                                isSelected = isSelected,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Subtle Divider
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = Color.White.copy(alpha = 0.1f),
                thickness = 1.dp
            )

            Spacer(Modifier.height(24.dp))

            // --- Color Palette ---
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                colors.forEach { color ->
                    val isSelected = selectedColor == color

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { selectedColor = color }) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(color = color)
                            if (isSelected) {
                                drawCircle(
                                    color = Color.White,
                                    radius = size.minDimension / 2,
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                        }

                        if (isSelected && color == Color.White) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (color == Color.Black) Color.White
                                else Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OcrLanguageSelectionDialog(
    currentLanguage: OcrLanguage,
    isFirstRun: Boolean,
    onDismiss: () -> Unit,
    onLanguageSelected: (OcrLanguage) -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Select OCR Language") }, text = {
        Column(Modifier.selectableGroup()) {
            Text(
                "Choose the primary language/script of this document for better text recognition results.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (isFirstRun) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Row(
                        Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "You can change this later in More Options > OCR Language.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }

            OcrLanguage.entries.forEach { language ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = (language == currentLanguage),
                            onClick = { onLanguageSelected(language) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = (language == currentLanguage), onClick = null)
                    Text(
                        text = language.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun SearchNavigationPill(
    text: String,
    mode: SearchHighlightMode,
    onToggleMode: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTextClick: () -> Unit,
    isPrevEnabled: Boolean = true,
    isNextEnabled: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .shadow(6.dp, RoundedCornerShape(50))
            .height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            // Toggle Mode
            IconButton(onClick = onToggleMode) {
                Icon(
                    imageVector = if (mode == SearchHighlightMode.ALL) Icons.Default.Visibility
                    else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle Highlights",
                    tint = if (mode == SearchHighlightMode.ALL) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Vertical Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.3f
                        )
                    )
            )

            // Prev
            IconButton(onClick = onPrev, enabled = isPrevEnabled) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Previous",
                    tint = if (isPrevEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            // Counter/Text
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTextClick
                    )
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Next
            IconButton(onClick = onNext, enabled = isNextEnabled) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Next",
                    tint = if (isNextEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}

@Composable
fun PdfSearchResultsPanel(
    lazyResults: LazyPagingItems<SearchResult>,
    totalPageCount: Int,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (lazyResults.itemCount == 0 && lazyResults.loadState.refresh !is LoadState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results found.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column {
                Text(
                    text = "Results found on ${totalPageCount}+ pages",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                HorizontalDivider()

                LazyColumn(modifier = Modifier.testTag("SearchResultsList")) {
                    items(count = lazyResults.itemCount, key = lazyResults.itemKey {
                        "${it.locationInSource}_${it.occurrenceIndexInLocation}"
                    }, contentType = lazyResults.itemContentType { "SearchResult" }) { index ->
                        val result = lazyResults[index]
                        if (result != null) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        result.locationTitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }, supportingContent = {
                                    Text(
                                        result.snippet, style = MaterialTheme.typography.bodyMedium
                                    )
                                }, modifier = Modifier
                                    .testTag(
                                        "SearchResultItem_${result.locationInSource}"
                                    )
                                    .clickable { onResultClick(result) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        if (lazyResults.loadState.refresh is LoadState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun PdfSearchResultsList(
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results found.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column {
                Text(
                    text = "${results.size} matches found",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                HorizontalDivider()

                LazyColumn(modifier = Modifier.testTag("SearchResultsList")) {
                    itemsIndexed(results) { _, result ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    result.locationTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }, supportingContent = {
                                Text(
                                    result.snippet, style = MaterialTheme.typography.bodyMedium
                                )
                            }, modifier = Modifier
                                .testTag(
                                    "SearchResultItem_${result.locationInSource}"
                                )
                                .clickable { onResultClick(result) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}