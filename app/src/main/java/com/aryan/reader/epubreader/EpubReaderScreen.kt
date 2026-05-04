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
// EpubReaderScreen.kt
@file:OptIn(ExperimentalSerializationApi::class) @file:Suppress("VariableNeverRead",
    "UnusedVariable", "Unused", "SimplifyBooleanWithConstants", "KotlinConstantConditions"
)

package com.aryan.reader.epubreader

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsEndWidth
import androidx.compose.foundation.layout.windowInsetsStartWidth
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.AiDefinitionResult
import com.aryan.reader.BannerMessage
import com.aryan.reader.BuildConfig
import com.aryan.reader.BuiltInThemes
import com.aryan.reader.CustomTopBanner
import com.aryan.reader.MainViewModel
import com.aryan.reader.R
import com.aryan.reader.ReaderThemePanel
import com.aryan.reader.RenderMode
import com.aryan.reader.SearchResult
import com.aryan.reader.SummarizationResult
import com.aryan.reader.SummaryCacheManager
import com.aryan.reader.TtsSettingsSheet
import com.aryan.reader.areReaderAiFeaturesEnabled
import com.aryan.reader.countWords
import com.aryan.reader.isByokCloudTtsAvailable
import com.aryan.reader.data.CustomFontEntity
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.hasReadableExtractedContent
import com.aryan.reader.fetchAiDefinition
import com.aryan.reader.loadCustomThemes
import com.aryan.reader.loadGlobalTextureTransparency
import com.aryan.reader.loadReaderThemeId
import com.aryan.reader.loadReaderTextureBitmap
import com.aryan.reader.paginatedreader.BookPaginator
import com.aryan.reader.paginatedreader.CfiUtils
import com.aryan.reader.paginatedreader.HeaderBlock
import com.aryan.reader.paginatedreader.IPaginator
import com.aryan.reader.paginatedreader.ListItemBlock
import com.aryan.reader.paginatedreader.Locator
import com.aryan.reader.paginatedreader.LocatorConverter
import com.aryan.reader.paginatedreader.PaginatedReaderScreen
import com.aryan.reader.paginatedreader.ParagraphBlock
import com.aryan.reader.paginatedreader.QuoteBlock
import com.aryan.reader.paginatedreader.TextContentBlock
import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.paginatedreader.data.BookCacheDatabase
import com.aryan.reader.paginatedreader.semanticBlockModule
import com.aryan.reader.rememberSearchState
import com.aryan.reader.saveCustomThemes
import com.aryan.reader.saveGlobalTextureTransparency
import com.aryan.reader.saveReaderThemeId
import com.aryan.reader.tts.SpeakerSamplePlayer
import com.aryan.reader.tts.TtsPlaybackManager
import com.aryan.reader.tts.loadTtsMode
import com.aryan.reader.tts.splitTextIntoChunks
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val AUTO_SCROLL_USE_SLIDER_KEY = "auto_scroll_use_slider"
private const val AUTO_SCROLL_MIN_SPEED_KEY = "auto_scroll_min_speed"
private const val AUTO_SCROLL_MAX_SPEED_KEY = "auto_scroll_max_speed"
private const val PAGE_TURN_ANIMATION_KEY = "page_turn_animation_enabled"
private const val TTS_MODE_KEY = "tts_mode"

private const val AUTO_SCROLL_IS_LOCAL_PREFIX = "auto_scroll_is_local_"
private const val AUTO_SCROLL_LOCAL_SPEED_PREFIX = "auto_scroll_local_speed_"
private const val AUTO_SCROLL_LOCAL_MIN_PREFIX = "auto_scroll_local_min_"
private const val AUTO_SCROLL_LOCAL_MAX_PREFIX = "auto_scroll_local_max_"
private const val MUSICIAN_MODE_KEY = "musician_mode_enabled"
private const val KEEP_SCREEN_ON_KEY = "keep_screen_on_enabled"
private const val HIDDEN_TOOLS_KEY = "hidden_reader_tools"
private const val TOOL_ORDER_KEY = "reader_tool_order"
private const val BOTTOM_TOOLS_KEY = "reader_bottom_tools"
private const val TTS_LOCATE_REASON_INITIAL_RESTORE = "initial_restore"
private const val TTS_LOCATE_REASON_LIFECYCLE_RESUME = "lifecycle_resume"
private const val TTS_LOCATE_REASON_OVERLAY = "overlay"

private const val TAG_LINK_NAV = "LINK_NAV"

private fun View.bottomRoundedCornerRadiusPx(): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0

    val insets = rootWindowInsets ?: return 0
    return max(
        insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0,
        insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
    )
}

@Composable
private fun rememberBottomRoundedCornerPadding(view: View): Dp {
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

private fun saveHiddenTools(context: Context, hiddenTools: Set<String>) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putStringSet(HIDDEN_TOOLS_KEY, hiddenTools) }
}

private fun loadHiddenTools(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getStringSet(HIDDEN_TOOLS_KEY, emptySet()) ?: emptySet()
}

private fun saveToolOrder(context: Context, toolOrder: List<ReaderTool>) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(TOOL_ORDER_KEY, toolOrder.joinToString(",") { it.name }) }
}

private fun loadToolOrder(context: Context): List<ReaderTool> {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    val savedTools = prefs.getString(TOOL_ORDER_KEY, null)
        ?.split(',')
        ?.filter { it.isNotBlank() }
        ?.mapNotNull { name -> ReaderTool.entries.firstOrNull { it.name == name } }
        .orEmpty()
    return (savedTools + ReaderTool.entries.filterNot { it in savedTools }).distinct()
}

private fun saveBottomTools(context: Context, bottomTools: Set<String>) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putStringSet(BOTTOM_TOOLS_KEY, bottomTools) }
}

private fun loadBottomTools(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getStringSet(
        BOTTOM_TOOLS_KEY,
        ReaderTool.entries.filter { it.category == "Bottom Bar" }.map { it.name }.toSet()
    ) ?: ReaderTool.entries.filter { it.category == "Bottom Bar" }.map { it.name }.toSet()
}

private fun saveKeepScreenOn(context: Context, isEnabled: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(KEEP_SCREEN_ON_KEY, isEnabled) }
}

private fun loadKeepScreenOn(context: Context): Boolean {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(KEEP_SCREEN_ON_KEY, false)
}

private fun saveMusicianMode(context: Context, isEnabled: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(MUSICIAN_MODE_KEY, isEnabled) }
}

private fun loadMusicianMode(context: Context): Boolean {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(MUSICIAN_MODE_KEY, false)
}

private fun getBookIdForPrefs(title: String): String {
    return title.hashCode().toString()
}

private fun saveAutoScrollLocalMode(context: Context, bookId: String, isLocal: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(AUTO_SCROLL_IS_LOCAL_PREFIX + bookId, isLocal) }
}

private fun loadAutoScrollLocalMode(context: Context, bookId: String): Boolean {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(AUTO_SCROLL_IS_LOCAL_PREFIX + bookId, false)
}

private fun saveAutoScrollLocalSettings(context: Context, bookId: String, speed: Float, min: Float, max: Float) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit {
        putFloat(AUTO_SCROLL_LOCAL_SPEED_PREFIX + bookId, speed)
        putFloat(AUTO_SCROLL_LOCAL_MIN_PREFIX + bookId, min)
        putFloat(AUTO_SCROLL_LOCAL_MAX_PREFIX + bookId, max)
    }
}

private fun loadAutoScrollLocalSettings(context: Context, bookId: String): Triple<Float, Float, Float>? {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    if (!prefs.contains(AUTO_SCROLL_LOCAL_SPEED_PREFIX + bookId)) return null

    val speed = prefs.getFloat(AUTO_SCROLL_LOCAL_SPEED_PREFIX + bookId, 3.0f)
    val min = prefs.getFloat(AUTO_SCROLL_LOCAL_MIN_PREFIX + bookId, 0.1f)
    val max = prefs.getFloat(AUTO_SCROLL_LOCAL_MAX_PREFIX + bookId, 10.0f)
    return Triple(speed, min, max)
}

private fun savePageTurnAnimationSetting(context: Context, isEnabled: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PAGE_TURN_ANIMATION_KEY, isEnabled) }
}

private fun loadPageTurnAnimationSetting(context: Context): Boolean {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(PAGE_TURN_ANIMATION_KEY, false)
}

private fun saveAutoScrollMinSpeed(context: Context, speed: Float) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putFloat(AUTO_SCROLL_MIN_SPEED_KEY, speed) }
}

private fun loadAutoScrollMinSpeed(context: Context): Float {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getFloat(AUTO_SCROLL_MIN_SPEED_KEY, 0.1f)
}

private fun saveAutoScrollMaxSpeed(context: Context, speed: Float) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putFloat(AUTO_SCROLL_MAX_SPEED_KEY, speed) }
}

private fun loadAutoScrollMaxSpeed(context: Context): Float {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getFloat(AUTO_SCROLL_MAX_SPEED_KEY, 10.0f)
}

private fun saveAutoScrollUseSlider(context: Context, useSlider: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(AUTO_SCROLL_USE_SLIDER_KEY, useSlider) }
}

private fun loadAutoScrollUseSlider(context: Context): Boolean {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(AUTO_SCROLL_USE_SLIDER_KEY, false)
}

private fun saveTtsMode(context: Context, modeName: String) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(TTS_MODE_KEY, modeName) }
}

private const val PREF_USE_ONLINE_DICT = "use_online_dictionary"
private const val PREF_EXTERNAL_DICT_PKG = "external_dictionary_package"
private const val PREF_EXTERNAL_TRANSLATE_PKG = "external_translate_package"
private const val PREF_EXTERNAL_SEARCH_PKG = "external_search_package"

private fun loadUseOnlineDict(context: Context): Boolean {
    @Suppress("KotlinConstantConditions") if (BuildConfig.FLAVOR == "oss" && BuildConfig.IS_OFFLINE) return false
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(PREF_USE_ONLINE_DICT, true)
}

private fun saveUseOnlineDict(context: Context, useOnline: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PREF_USE_ONLINE_DICT, useOnline) }
}

private fun loadExternalDictPackage(context: Context): String? {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getString(PREF_EXTERNAL_DICT_PKG, null)
}

private fun saveExternalDictPackage(context: Context, packageName: String) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(PREF_EXTERNAL_DICT_PKG, packageName) }
}

private fun loadExternalTranslatePackage(context: Context): String? {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getString(PREF_EXTERNAL_TRANSLATE_PKG, null)
}

private fun saveExternalTranslatePackage(context: Context, packageName: String) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(PREF_EXTERNAL_TRANSLATE_PKG, packageName) }
}

private fun loadExternalSearchPackage(context: Context): String? {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getString(PREF_EXTERNAL_SEARCH_PKG, null)
}

private fun saveExternalSearchPackage(context: Context, packageName: String) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(PREF_EXTERNAL_SEARCH_PKG, packageName) }
}

const val PREF_READER_THEME = "reader_theme_id"
const val PREF_CUSTOM_THEMES = "custom_themes_json"

@UnstableApi
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun EpubReaderScreen(
    epubBook: EpubBook,
    renderMode: RenderMode,
    initialLocator: Locator?,
    initialCfi: String?,
    initialBookmarksJson: String?,
    isProUser: Boolean,
    onNavigateBack: () -> Unit,
    onSavePosition: (locator: Locator, cfiForWebView: String?, progress: Float) -> Unit,
    onBookmarksChanged: (bookmarksJson: String) -> Unit,
    onNavigateToPro: () -> Unit,
    coverImagePath: String?,
    onRenderModeChange: (RenderMode) -> Unit,
    customFonts: List<CustomFontEntity>,
    onImportFont: (Uri) -> Unit,
    viewModel: MainViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    val isReflowFile = uiState.selectedBookId?.endsWith("_reflow") == true
    val originalBookId = if (isReflowFile) uiState.selectedBookId!!.removeSuffix("_reflow") else null

    val onOpenOriginal: ((Int) -> Unit)? = if (originalBookId != null) {
        { currentChapter ->
            val originalItem = uiState.recentFiles.find { it.bookId == originalBookId }
            if (originalItem != null) {
                viewModel.switchToFileSeamlessly(originalItem, currentChapter)
            } else {
                viewModel.showBanner("Original PDF not found.", true)
            }
        }
    } else null

    val hasValidExtractionBasePath = remember(epubBook.extractionBasePath, epubBook.chapters) {
        epubBook.hasReadableExtractedContent()
    }
    var requestedContentRecovery by remember(epubBook.extractionBasePath, uiState.selectedBookId) {
        mutableStateOf(false)
    }

    LaunchedEffect(hasValidExtractionBasePath, uiState.selectedBookId, uiState.selectedEpubUri) {
        if (!hasValidExtractionBasePath && !requestedContentRecovery && uiState.selectedEpubUri != null) {
            requestedContentRecovery = true
            viewModel.recoverSelectedEpubContent()
        }
    }

    if (!hasValidExtractionBasePath) {
        val isRecovering = uiState.isLoading || (requestedContentRecovery && uiState.errorMessage == null)
        val message = uiState.errorMessage ?: if (isRecovering) {
            "Recovering book content..."
        } else {
            "Book content not found. Reopen the book to recreate its cache."
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (isRecovering) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            Text(
                text = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                color = if (uiState.errorMessage != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )
        }
        return
    }

    EpubReaderHost(
        epubBook = epubBook,
        renderMode = renderMode,
        initialLocator = initialLocator,
        initialCfi = initialCfi,
        initialBookmarksJson = initialBookmarksJson,
        initialHighlightsJson = uiState.initialHighlightsJson,
        isProUser = isProUser,
        credits = uiState.credits,
        onNavigateBack = onNavigateBack,
        onSavePosition = onSavePosition,
        onBookmarksChanged = onBookmarksChanged,
        onHighlightsChanged = { json ->
            uiState.selectedBookId?.let { id ->
                viewModel.saveHighlights(id, json)
            }
        },
        onNavigateToPro = onNavigateToPro,
        coverImagePath = coverImagePath,
        onRenderModeChange = onRenderModeChange,
        customFonts = customFonts,
        onImportFont = onImportFont,
        onToggleReflow = onOpenOriginal,
        onDeleteReflow = if (isReflowFile) {
            {
                uiState.selectedBookId?.let { id ->
                    viewModel.deleteBookPermanently(id) {
                        onNavigateBack()
                    }
                }
            }
        } else null,
        viewModel = viewModel
    )
}

@Suppress("ControlFlowWithEmptyBody")
@SuppressLint("UnusedBoxWithConstraintsScope", "ObsoleteSdkInt", "LocalContextGetResourceValueCall")
@androidx.annotation.OptIn(UnstableApi::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderHost(
    epubBook: EpubBook,
    renderMode: RenderMode,
    initialLocator: Locator?,
    initialCfi: String?,
    initialBookmarksJson: String?,
    initialHighlightsJson: String?,
    isProUser: Boolean,
    credits: Int,
    onNavigateBack: () -> Unit,
    onSavePosition: (locator: Locator, cfiForWebView: String?, progress: Float) -> Unit,
    onBookmarksChanged: (bookmarksJson: String) -> Unit,
    onHighlightsChanged: (highlightsJson: String) -> Unit,
    onNavigateToPro: () -> Unit,
    coverImagePath: String?,
    onRenderModeChange: (RenderMode) -> Unit,
    customFonts: List<CustomFontEntity>,
    onImportFont: (Uri) -> Unit,
    onToggleReflow: ((Int) -> Unit)? = null,
    onDeleteReflow: (() -> Unit)? = null,
    viewModel: MainViewModel
) {
    val view = LocalView.current
    val context = LocalContext.current
    val window = (view.context as? Activity)?.window
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var bannerMessage by remember { mutableStateOf<BannerMessage?>(null) }
    DisposableEffect(window, view) {
        onDispose {
            window?.let {
                WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    val containerFocusRequester = remember { FocusRequester() }
    var isNavigatingToPosition by remember { mutableStateOf(false) }
    var isSeamlessTransitioning by remember { mutableStateOf(false) }
    var showInsufficientCreditsDialog by remember { mutableStateOf(false) }

    var isPageSliderVisible by remember { mutableStateOf(false) }
    var sliderCurrentPage by remember { mutableFloatStateOf(0f) }
    var isFastScrubbing by remember { mutableStateOf(false) }
    val scrubDebounceJob = remember { mutableStateOf<Job?>(null) }
    val volumeScrollFocusDebounceJob = remember { mutableStateOf<Job?>(null) }
    var sliderStartPage by remember { mutableIntStateOf(0) }
    var startPageThumbnail by remember { mutableStateOf<Bitmap?>(null) }

    var pendingNoteForNewHighlight by remember { mutableStateOf(false) }
    var highlightToNoteCfi by remember { mutableStateOf<String?>(null) }
    var activeFootnoteHtml by remember { mutableStateOf<String?>(null) }

    var showJustifyWarningDialog by remember { mutableStateOf(false) }
    var isNavigatingByToc by remember { mutableStateOf(false) }

    var chunkTargetOverride by remember { mutableStateOf(initialLocator?.let { it.blockIndex / 20 }) }

    val snackbarHostState = remember { SnackbarHostState() }

    var systemUiMode by remember { mutableStateOf(loadSystemUiMode(context)) }
    var pageInfoMode by remember { mutableStateOf(loadPageInfoMode(context)) }
    var pageInfoPosition by remember { mutableStateOf(loadPageInfoPosition(context)) }
    var pullToTurnEnabled by remember { mutableStateOf(loadPullToTurn(context)) }
    var pullToTurnMultiplier by remember { mutableFloatStateOf(loadPullToTurnMultiplier(context)) }
    var showVisualOptionsSheet by remember { mutableStateOf(false) }

    var volumeScrollEnabled by remember {
        mutableStateOf(loadVolumeScrollSetting(context))
    }

    var tapToNavigateEnabled by remember {
        mutableStateOf(loadTapToNavigateSetting(context))
    }

    var isPageTurnAnimationEnabled by remember {
        mutableStateOf(loadPageTurnAnimationSetting(context))
    }

    var currentTtsMode by remember {
        mutableStateOf(
            loadTtsMode(context).let {
                if (BuildConfig.FLAVOR == "oss" && !isByokCloudTtsAvailable(context)) TtsPlaybackManager.TtsMode.BASE else it
            }
        )
    }

    val locatorConverter = remember(context) {
        LocatorConverter(
            bookCacheDao = BookCacheDatabase.getDatabase(context).bookCacheDao(),
            proto = ProtoBuf { serializersModule = semanticBlockModule },
            context = context
        )
    }

    val userHighlights = remember(epubBook.title) {
        mutableStateListOf<UserHighlight>().apply {
            if (initialHighlightsJson != null) {
                addAll(parseHighlightsJson(initialHighlightsJson))
            } else {
                addAll(loadHighlightsFromPrefs(context, epubBook.title))
            }
        }
    }

    LaunchedEffect(userHighlights.size, userHighlights.toList()) {
        val json = highlightsToJson(userHighlights)
        onHighlightsChanged(json)

        if (initialHighlightsJson == null && userHighlights.isNotEmpty()) {
            clearHighlightsFromPrefs(context, epubBook.title)
        }
    }

    var isAutoScrollCollapsed by remember { mutableStateOf(false) }
    var isTtsCollapsed by remember { mutableStateOf(false) }

    val bookId = remember(epubBook.title, epubBook.fileName) {
        if (epubBook.fileName.length > 20) epubBook.fileName else getBookIdForPrefs(epubBook.title)
    }
    var isAutoScrollLocal by remember { mutableStateOf(loadAutoScrollLocalMode(context, bookId)) }

    val initialSettings = remember(isAutoScrollLocal) {
        if (isAutoScrollLocal) {
            loadAutoScrollLocalSettings(context, bookId) ?: Triple(
                loadAutoScrollSpeed(context),
                loadAutoScrollMinSpeed(context),
                loadAutoScrollMaxSpeed(context)
            )
        } else {
            Triple(
                loadAutoScrollSpeed(context),
                loadAutoScrollMinSpeed(context),
                loadAutoScrollMaxSpeed(context)
            )
        }
    }

    var autoScrollSpeed by remember { mutableFloatStateOf(initialSettings.first) }
    var autoScrollMinSpeed by remember { mutableFloatStateOf(initialSettings.second) }
    var autoScrollMaxSpeed by remember { mutableFloatStateOf(initialSettings.third) }

    val onToggleAutoScrollMode = { newIsLocal: Boolean ->
        isAutoScrollLocal = newIsLocal
        saveAutoScrollLocalMode(context, bookId, newIsLocal)

        if (newIsLocal) {
            val existingLocal = loadAutoScrollLocalSettings(context, bookId)
            if (existingLocal == null) {
                saveAutoScrollLocalSettings(context, bookId, autoScrollSpeed, autoScrollMinSpeed, autoScrollMaxSpeed)
            } else {
                autoScrollSpeed = existingLocal.first
                autoScrollMinSpeed = existingLocal.second
                autoScrollMaxSpeed = existingLocal.third
            }
        } else {
            autoScrollSpeed = loadAutoScrollSpeed(context)
            autoScrollMinSpeed = loadAutoScrollMinSpeed(context)
            autoScrollMaxSpeed = loadAutoScrollMaxSpeed(context)
        }
    }

    val updateSpeed = { newSpeed: Float ->
        autoScrollSpeed = newSpeed
        if (isAutoScrollLocal) {
            saveAutoScrollLocalSettings(context, bookId, newSpeed, autoScrollMinSpeed, autoScrollMaxSpeed)
        } else {
            saveAutoScrollSpeed(context, newSpeed)
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

            saveAutoScrollLocalSettings(context, bookId, currentSpeed, newMin, currentMax)
        } else {
            saveAutoScrollMinSpeed(context, newMin)
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

            saveAutoScrollLocalSettings(context, bookId, currentSpeed, currentMin, newMax)
        } else {
            saveAutoScrollMaxSpeed(context, newMax)
        }
    }

    var currentHighlightPalette by remember {
        mutableStateOf(loadHighlightPalette(context))
    }

    val onUpdateHighlightPalette: (Int, HighlightColor) -> Unit = { index, newColor ->
        val newList = currentHighlightPalette.toMutableList()
        if (index in newList.indices) {
            newList[index] = newColor
            currentHighlightPalette = newList
            saveHighlightPalette(context, newList)
        }
    }

    // Dictionary
    var showAiDefinitionPopup by remember { mutableStateOf(false) }
    var selectedTextForAi by remember { mutableStateOf<String?>(null) }
    var aiDefinitionResult by remember { mutableStateOf<AiDefinitionResult?>(null) }
    var isAiDefinitionLoading by remember { mutableStateOf(false) }

    var showDictionarySettingsSheet by remember { mutableStateOf(false) }

    var useOnlineDictionary by remember {
        mutableStateOf(loadUseOnlineDict(context))
    }
    var selectedDictPackage by remember {
        mutableStateOf(loadExternalDictPackage(context))
    }
    var selectedTranslatePackage by remember {
        mutableStateOf(loadExternalTranslatePackage(context))
    }
    var selectedSearchPackage by remember {
        mutableStateOf(loadExternalSearchPackage(context))
    }

    var hiddenTools by remember { mutableStateOf(loadHiddenTools(context)) }
    var toolOrder by remember { mutableStateOf(loadToolOrder(context)) }
    var bottomTools by remember { mutableStateOf(loadBottomTools(context)) }
    var showCustomizeToolsSheet by remember { mutableStateOf(false) }

    var showDictionaryUpsellDialog by remember { mutableStateOf(false) }
    var showSummarizationUpsellDialog by remember { mutableStateOf(false) }

    @Suppress("KotlinConstantConditions") val onDictionaryLookup = { word: String ->
        val effectiveUseOnline = areReaderAiFeaturesEnabled(context) && useOnlineDictionary

        if (effectiveUseOnline) {
            val wordCount = countWords(word)
            if (BuildConfig.FLAVOR != "oss" && wordCount > 1 && !isProUser) {
                showDictionaryUpsellDialog = true
            } else {
                selectedTextForAi = word
                showAiDefinitionPopup = true
                scope.launch {
                    val token = viewModel.getAuthToken()
                    isAiDefinitionLoading = true
                    aiDefinitionResult = null
                    fetchAiDefinition(
                        text = word,
                        onUpdate = { chunk ->
                            val currentDefinition = aiDefinitionResult?.definition ?: ""
                            aiDefinitionResult = AiDefinitionResult(definition = currentDefinition + chunk)
                        },
                        authToken = token,
                        onError = { error ->
                            if (error == "INSUFFICIENT_CREDITS") {
                                showInsufficientCreditsDialog = true
                                showAiDefinitionPopup = false
                                isAiDefinitionLoading = false
                            } else {
                                aiDefinitionResult = AiDefinitionResult(error = error)
                            }
                        },
                        onFinish = { isAiDefinitionLoading = false },
                        context = context
                    )
                }
            }
        } else {
            if (!selectedDictPackage.isNullOrEmpty()) {
                ExternalDictionaryHelper.launchDictionary(context, selectedDictPackage!!, word)
            } else {
                Toast.makeText(context, context.getString(R.string.toast_select_dictionary_first), Toast.LENGTH_SHORT).show()
                showDictionarySettingsSheet = true
            }
        }
    }

    val onTranslateLookup = { text: String ->
        if (!selectedTranslatePackage.isNullOrEmpty()) {
            ExternalDictionaryHelper.launchTranslate(context, selectedTranslatePackage!!, text)
        } else {
            Toast.makeText(context, context.getString(R.string.toast_select_translate_first), Toast.LENGTH_SHORT).show()
            showDictionarySettingsSheet = true
        }
    }

    val onSearchLookup = { text: String ->
        if (!selectedSearchPackage.isNullOrEmpty()) {
            ExternalDictionaryHelper.launchSearch(context, selectedSearchPackage!!, text)
        } else {
            Toast.makeText(context, context.getString(R.string.toast_select_search_first), Toast.LENGTH_SHORT).show()
            showDictionarySettingsSheet = true
        }
    }

    val summaryCacheManager = remember(context) { SummaryCacheManager(context) }
    var showRecapPopup by remember { mutableStateOf(false) }

    var currentRenderMode by remember(renderMode) { mutableStateOf(renderMode) }
    var chapterToLoadOnSwitch by remember { mutableStateOf<Int?>(null) }
    var lastKnownLocator by remember(initialLocator) { mutableStateOf(initialLocator) }

    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val roundedCornerBottomPadding = rememberBottomRoundedCornerPadding(view)
    val pageInfoCornerBottomPadding = roundedCornerBottomPadding.coerceAtMost(8.dp)

    var bookmarks by remember(epubBook.title) {
        mutableStateOf(
            loadBookmarks(context, epubBook.title, epubBook.chapters, initialBookmarksJson).also {
                Timber.d("Initial load for '${epubBook.title}': ${it.size} bookmarks loaded -> $it")
            }
        )
    }

    LaunchedEffect(bookmarks) {
        Timber.d("Bookmarks changed, saving...")
        val stringSet = bookmarks.map { bookmark ->
            JSONObject().apply {
                put("cfi", bookmark.cfi)
                put("chapterTitle", bookmark.chapterTitle)
                put("label", bookmark.label)
                put("snippet", bookmark.snippet)
                bookmark.pageInChapter?.let { put("pageInChapter", it) }
                bookmark.totalPagesInChapter?.let { put("totalPagesInChapter", it) }
                put("chapterIndex", bookmark.chapterIndex)
            }.toString()
        }
        onBookmarksChanged(JSONArray(stringSet).toString())
    }

    var activeBookmarkInVerticalView by remember { mutableStateOf<Bookmark?>(null) }
    var addBookmarkRequest by remember { mutableStateOf(false) }
    var isChapterReadyForBookmarkCheck by remember { mutableStateOf(false) }
    var lastBookmarkCheckTime by remember { mutableLongStateOf(0L) }
    var isSwitchingToPaginated by remember { mutableStateOf(false) }

    val initialIsAppearanceLightStatusBars = remember(window, view) {
        window?.let {
            WindowCompat.getInsetsController(
                it,
                view
            ).isAppearanceLightStatusBars
        } == true
    }
    val initialSystemBarsBehavior = remember(window, view) {
        window?.let { WindowCompat.getInsetsController(it, view).systemBarsBehavior }
            ?: WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    var isSavingAndExiting by remember { mutableStateOf(false) }

    var ttsShouldStartOnChapterLoad by remember { mutableStateOf(false) }
    var userStoppedTts by remember { mutableStateOf(false) }
    var ttsChapterIndex by remember { mutableStateOf<Int?>(null) }
    var pendingTtsLocateRequest by remember { mutableStateOf(false) }
    var pendingTtsLocateReason by remember { mutableStateOf<String?>(null) }
    var hasQueuedInitialTtsLocate by remember(epubBook.title) { mutableStateOf(false) }
    var isDetachedFromVerticalTts by remember { mutableStateOf(false) }
    var detachedVerticalTtsChunkKey by remember { mutableStateOf<String?>(null) }
    var suppressNextVerticalTtsDetach by remember { mutableStateOf(false) }

    var searchHighlightTarget by remember { mutableStateOf<SearchResult?>(null) }
    var lastHighlightClickTime by remember { mutableLongStateOf(0L) }
    var lastScrollHideTime by remember { mutableLongStateOf(0L) }

    var webViewRefForTts by remember { mutableStateOf<WebView?>(null) }

    var showAiHubSheet by remember { mutableStateOf(false) }
    var summarizationResult by remember { mutableStateOf<SummarizationResult?>(null) }
    var isSummarizationLoading by remember { mutableStateOf(false) }

    var recapResult by remember { mutableStateOf<SummarizationResult?>(null) }
    var isRecapLoading by remember { mutableStateOf(false) }
    var recapProgressMessage by remember { mutableStateOf("") }
    var isRequestingRecapCfi by remember { mutableStateOf(false) }

    val epubSearcher = remember(epubBook) { createEpubSearcher(epubBook) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showBars by remember { mutableStateOf(false) }
    val chapters = remember(epubBook.chapters) { epubBook.chapters }

    var currentChapterIndex by rememberSaveable(epubBook.title) {
        mutableIntStateOf(
            initialLocator?.chapterIndex?.coerceIn(0, max(0, chapters.size - 1)) ?: 0
        )
    }

    var paginator by remember { mutableStateOf<IPaginator?>(null) }
    val paginatedPagerState = rememberPagerState(pageCount = {
        (paginator as? BookPaginator)?.totalPageCount ?: 0
    })
    var isPagerInitialized by remember(initialLocator) { mutableStateOf(initialLocator == null) }

    val ttsController = viewModel.ttsController
    val ttsState by ttsController.ttsState.collectAsState()

    val totalBookLengthChars = remember(chapters) {
        chapters.sumOf { it.plainTextContent.length.toLong() }
    }

    var topVisibleChunkIndex by remember { mutableIntStateOf(0) }
    var loadedChunkCount by remember { mutableIntStateOf(1) }
    var loadUpToChunkIndex by remember(currentChapterIndex) { mutableIntStateOf(0) }

    var chapterChunks by remember(currentChapterIndex) { mutableStateOf<List<String>>(emptyList()) }
    var chapterHead by remember(currentChapterIndex) { mutableStateOf("") }
    var isChapterParsing by remember(currentChapterIndex) { mutableStateOf(true) }

    var cfiToLoad by remember { mutableStateOf(initialCfi) }
    var fragmentToLoad by remember { mutableStateOf<String?>(null) }
    var isInitialCfiLoad by remember(initialLocator) { mutableStateOf(initialLocator != null) }
    var bookmarkPageMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(Unit) {
        Timber.tag("POS_DIAG").d("Reader Opening: initialLocator=$initialLocator, initialCfi=$initialCfi")
    }

    var initialScrollTargetForChapter by rememberSaveable(epubBook.title) {
        mutableStateOf(if (initialLocator != null) null else ChapterScrollPosition.START)
    }

    var pullToPrevProgress by remember { mutableFloatStateOf(0f) }
    var pullToNextProgress by remember { mutableFloatStateOf(0f) }

    var activeFragmentId by remember { mutableStateOf<String?>(null) }

    val density = LocalDensity.current
    val dragThresholdPx = with(density) { DRAG_TO_CHANGE_CHAPTER_THRESHOLD_DP.toPx() * pullToTurnMultiplier }

    var currentScrollYPosition by rememberSaveable(epubBook.title) {
        mutableIntStateOf(0)
    }

    var currentScrollHeightValue by remember { mutableIntStateOf(0) }
    var currentClientHeightValue by remember { mutableIntStateOf(0) }

    val currentBookProgress by remember(currentChapterIndex, currentScrollYPosition, currentScrollHeightValue, currentClientHeightValue, totalBookLengthChars) {
        derivedStateOf {
            if (totalBookLengthChars > 0) {
                val completedCharsInPreviousChapters =
                    chapters.take(currentChapterIndex)
                        .sumOf { it.plainTextContent.length.toLong() }

                val progressWithinChapter =
                    if (currentScrollHeightValue > currentClientHeightValue) {
                        val scrollableHeight = (currentScrollHeightValue - currentClientHeightValue).toFloat()
                        if (scrollableHeight > 0) (currentScrollYPosition.toFloat() / scrollableHeight).coerceIn(0f, 1f) else 1f
                    } else if (currentScrollHeightValue > 0) {
                        1f // Fully scrolled if content is smaller than viewport
                    } else {
                        0f
                    }

                val currentChapterLengthChars =
                    chapters.getOrNull(currentChapterIndex)?.plainTextContent?.length?.toLong() ?: 0L
                val charsScrolledInCurrentChapter = (progressWithinChapter * currentChapterLengthChars).toLong()
                val totalCharsScrolled = completedCharsInPreviousChapters + charsScrolledInCurrentChapter
                val calculatedProgress = ((totalCharsScrolled.toDouble() / totalBookLengthChars.toDouble()) * 100.0).toFloat()

                val isLastChapter = currentChapterIndex == chapters.size - 1
                val isAtEndOfBook = isLastChapter && (currentScrollYPosition + currentClientHeightValue) >= (currentScrollHeightValue - 2)

                if (isAtEndOfBook) 100f else calculatedProgress
            } else {
                0f
            }
        }
    }

    var showFormatAdjustmentBars by remember { mutableStateOf(false) }

    var isFormatLocal by remember { mutableStateOf(loadFormatIsLocal(context, bookId)) }
    val initialFormatSettings = remember(isFormatLocal) { loadFormatSettings(context, bookId, isFormatLocal) }

    var currentFontSizeEm by remember(initialFormatSettings) { mutableFloatStateOf(initialFormatSettings.fontSize) }
    var currentLineHeight by remember(initialFormatSettings) { mutableFloatStateOf(initialFormatSettings.lineHeight) }
    var currentParagraphGap by remember(initialFormatSettings) { mutableFloatStateOf(initialFormatSettings.paragraphGap) }
    var currentImageSize by remember(initialFormatSettings) { mutableFloatStateOf(initialFormatSettings.imageSize) }
    var currentHorizontalMargin by remember(initialFormatSettings) { mutableFloatStateOf(initialFormatSettings.horizontalMargin) }
    var currentVerticalMargin by remember(initialFormatSettings) { mutableFloatStateOf(initialFormatSettings.verticalMargin) }
    var currentTextAlign by remember(initialFormatSettings) { mutableStateOf(initialFormatSettings.textAlign) }
    var currentFontFamily by remember(initialFormatSettings) { mutableStateOf(initialFormatSettings.font) }
    var currentCustomFontPath by remember(initialFormatSettings) { mutableStateOf(initialFormatSettings.customPath) }

    val activeFontFamily = remember(currentFontFamily, currentCustomFontPath) {
        getComposeFontFamily(
            font = currentFontFamily,
            customFontPath = currentCustomFontPath,
            assetManager = context.assets
        )
    }

    var showFontSelectionSheet by remember { mutableStateOf(false) }
    val fontSheetState = rememberModalBottomSheetState()

    LaunchedEffect(currentFontSizeEm, currentLineHeight, currentParagraphGap, currentImageSize, currentHorizontalMargin, currentVerticalMargin, currentFontFamily, currentCustomFontPath, currentTextAlign, isFormatLocal) {
        if (isFormatLocal) {
            saveLocalReaderSettings(
                context, bookId, currentFontSizeEm, currentLineHeight, currentParagraphGap, currentImageSize, currentHorizontalMargin, currentVerticalMargin, currentFontFamily, currentCustomFontPath, currentTextAlign
            )
        } else {
            saveReaderSettings(
                context, currentFontSizeEm, currentLineHeight, currentParagraphGap, currentImageSize, currentHorizontalMargin, currentVerticalMargin, currentFontFamily, currentCustomFontPath, currentTextAlign
            )
        }
    }

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(2500L)
            bannerMessage = null
        }
    }

    val configuration = LocalConfiguration.current
    var lastOrientation by remember { mutableIntStateOf(configuration.orientation) }

    LaunchedEffect(configuration.orientation) {
        if (lastOrientation != configuration.orientation) {
            lastOrientation = configuration.orientation
            if (currentRenderMode == RenderMode.VERTICAL_SCROLL) {
                lastKnownLocator?.let { locator ->
                    scope.launch {
                        val cfi = locatorConverter.getCfiFromLocator(epubBook, locator)
                        if (cfi != null) {
                            delay(300L)
                            webViewRefForTts?.evaluateJavascript("javascript:window.scrollToCfi('${escapeJsString(cfi)}');", null)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(isPageSliderVisible) {
        if (!isPageSliderVisible) {
            startPageThumbnail?.recycle()
            startPageThumbnail = null
        }
    }

    LaunchedEffect(ttsState.errorMessage) {
        ttsState.errorMessage?.let { message ->
            if (message == "INSUFFICIENT_CREDITS") {
                showInsufficientCreditsDialog = true
                ttsController.stop()
            } else {
                bannerMessage = BannerMessage(message, isError = true)
            }
        }
    }

    val searchState = rememberSearchState(scope = scope, searcher = epubSearcher)
    val speakerPlayer = remember(context, scope) {
        SpeakerSamplePlayer(context, scope, getAuthToken = { viewModel.getAuthToken() })
    }

    var isAutoScrollModeActive by remember { mutableStateOf(false) }
    var isAutoScrollPlaying by remember { mutableStateOf(false) }
    var isAutoScrollTempPaused by remember { mutableStateOf(false) }
    val autoScrollResumeJob = remember { mutableStateOf<Job?>(null) }

    var isMusicianMode by remember { mutableStateOf(loadMusicianMode(context)) }
    var autoScrollUseSlider by remember { mutableStateOf(loadAutoScrollUseSlider(context)) }

    var isKeepScreenOn by remember { mutableStateOf(loadKeepScreenOn(context)) }

    DisposableEffect(isKeepScreenOn) {
        view.keepScreenOn = isKeepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Timber.d("Disposing sample MediaPlayer.")
            speakerPlayer.release()
        }
    }

    fun updateAutoScrollState(playing: Boolean, speed: Float) {
        val effectivePlaying = playing && !isAutoScrollTempPaused
        updateAutoScrollJs(webViewRefForTts, effectivePlaying, speed * 0.5f)
    }

    fun triggerAutoScrollTempPause(durationMs: Long) {
        if (!isAutoScrollModeActive || !isAutoScrollPlaying) return

        autoScrollResumeJob.value?.cancel()

        isAutoScrollTempPaused = true
        updateAutoScrollState(isAutoScrollPlaying, autoScrollSpeed)

        autoScrollResumeJob.value = scope.launch {
            delay(durationMs)
            if (isActive && isAutoScrollModeActive && isAutoScrollPlaying) {
                isAutoScrollTempPaused = false
                @Suppress("KotlinConstantConditions") updateAutoScrollState(isAutoScrollPlaying, autoScrollSpeed)
            }
        }
    }

    LaunchedEffect(isAutoScrollModeActive, isAutoScrollPlaying, autoScrollSpeed, isAutoScrollTempPaused) {
        if (isAutoScrollModeActive) {
            updateAutoScrollState(isAutoScrollPlaying, autoScrollSpeed)
        } else {
            webViewRefForTts?.evaluateJavascript("javascript:window.autoScroll.stop();", null)
        }
    }

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showTtsSettingsSheet by remember { mutableStateOf(false) }
    var showTtsControlsSheet by remember { mutableStateOf(false) }
    var showThemePanel by remember { mutableStateOf(false) }
    var showPaletteManager by remember { mutableStateOf(false) }

    var currentThemeId by remember { mutableStateOf(loadReaderThemeId(context)) }
    var customThemes by remember { mutableStateOf(loadCustomThemes(context)) }
    var globalTextureTransparency by remember { mutableFloatStateOf(loadGlobalTextureTransparency(context)) }

    val activeTheme = remember(currentThemeId, customThemes) {
        BuiltInThemes.find { it.id == currentThemeId }
            ?: customThemes.find { it.id == currentThemeId }
            ?: BuiltInThemes[0]
    }

    val systemIsDark = isSystemInDarkTheme()
    val isDarkTheme = if (activeTheme.id == "system") systemIsDark else activeTheme.isDark

    val effectiveBg = remember(activeTheme, systemIsDark) {
        if (activeTheme.id == "system") {
            if (systemIsDark) Color(0xFF121212) else Color(0xFFFFFFFF)
        } else activeTheme.backgroundColor
    }
    val effectiveText = remember(activeTheme, systemIsDark) {
        if (activeTheme.id == "system") {
            if (systemIsDark) Color(0xFFE0E0E0) else Color(0xFF000000)
        } else activeTheme.textColor
    }
    val activeTextureId = activeTheme.textureId
    val activeTextureAlpha = 1f - globalTextureTransparency
    val activeTextureBitmap = remember(activeTextureId) {
        loadReaderTextureBitmap(context, activeTextureId)
    }
    val activeTextureModifier = activeTextureBitmap?.let { bitmap ->
        Modifier.drawBehind {
            drawRect(
                brush = ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)),
                blendMode = BlendMode.SrcOver,
                alpha = activeTextureAlpha.coerceIn(0f, 1f)
            )
        }
    } ?: Modifier

    val infoBarBgColor = remember(effectiveBg, isDarkTheme) {
        val overlayAlpha = if (isDarkTheme) 0.08f else 0.06f
        val overlayColor = if (isDarkTheme) Color.White else Color.Black
        val outR = overlayColor.red * overlayAlpha + effectiveBg.red * (1 - overlayAlpha)
        val outG = overlayColor.green * overlayAlpha + effectiveBg.green * (1 - overlayAlpha)
        val outB = overlayColor.blue * overlayAlpha + effectiveBg.blue * (1 - overlayAlpha)
        Color(outR, outG, outB).copy(alpha = 0.95f)
    }

    val currentChapterInPaginatedMode by remember {
        derivedStateOf {
            if (currentRenderMode == RenderMode.PAGINATED) {
                (paginator as? BookPaginator)?.findChapterIndexForPage(paginatedPagerState.currentPage)
            } else {
                null
            }
        }
    }

    fun isActiveReaderTtsForCurrentBook(): Boolean {
        val isReaderSession = ttsState.playbackSource == "READER"
        val hasReaderSessionState =
            ttsState.isPlaying ||
                ttsState.isLoading ||
                ttsState.sessionFinished ||
                ttsState.chapterIndex != null ||
                !ttsState.currentWordSourceCfi.isNullOrBlank() ||
                !ttsState.sourceCfi.isNullOrBlank() ||
                !ttsState.currentText.isNullOrBlank()
        val isSameBook = ttsState.bookTitle == null || ttsState.bookTitle == epubBook.title
        return isReaderSession && hasReaderSessionState && isSameBook
    }

    fun getActiveTtsChapterIndex(): Int? = ttsState.chapterIndex ?: ttsChapterIndex

    fun buildTtsDiagState(): String {
        val sourceCfiPreview = ttsState.sourceCfi?.take(48)
        val pendingCfiPreview = cfiToLoad?.take(48)
        return "render=$currentRenderMode currentChapter=$currentChapterIndex activeTtsChapter=${getActiveTtsChapterIndex()} " +
            "pendingLocate=$pendingTtsLocateRequest locateReason=$pendingTtsLocateReason detached=$isDetachedFromVerticalTts suppressDetach=$suppressNextVerticalTtsDetach " +
            "chunkOverride=$chunkTargetOverride pendingCfi=$pendingCfiPreview ttsCfi=$sourceCfiPreview offset=${ttsState.startOffsetInSource}"
    }

    fun logTtsChapterDiag(message: String) {
        Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("$message | ${buildTtsDiagState()}")
    }

    fun currentTtsChunkKey(): String? {
        val cfi = ttsState.sourceCfi?.takeIf { it.isNotBlank() } ?: return null
        val offset = ttsState.startOffsetInSource.takeIf { it >= 0 } ?: return null
        return "$cfi@$offset"
    }

    fun queuePendingTtsLocate(reason: String) {
        pendingTtsLocateReason = reason
        pendingTtsLocateRequest = true
    }

    fun detachVerticalReaderFromTts(reason: String) {
        logTtsChapterDiag("Detaching vertical reader from active TTS chapter. reason=$reason")
        isDetachedFromVerticalTts = true
        detachedVerticalTtsChunkKey = currentTtsChunkKey()
        pendingTtsLocateRequest = false
        pendingTtsLocateReason = null
        isNavigatingToPosition = false
        suppressNextVerticalTtsDetach = false
    }

    fun clearPendingTtsRelocationState(reason: String) {
        logTtsChapterDiag("Clearing pending TTS relocation state. reason=$reason")
        pendingTtsLocateRequest = false
        pendingTtsLocateReason = null
        chunkTargetOverride = null
        cfiToLoad = null
        fragmentToLoad = null
        isNavigatingToPosition = false
        suppressNextVerticalTtsDetach = false
    }

    suspend fun saveResolvedLocatorPosition(locator: Locator, cfiForWebView: String?) {
        lastKnownLocator = locator

        val chapterLengthChars = chapters.getOrNull(locator.chapterIndex)?.plainTextContent?.length?.toLong() ?: 0L
        val exactOffset = locatorConverter.getTextOffset(epubBook, locator)?.coerceAtLeast(0) ?: 0
        val boundedOffset = exactOffset.coerceAtMost(chapterLengthChars.toInt()).toLong()

        val progress = if (totalBookLengthChars > 0) {
            val completedCharsInPreviousChapters =
                chapters.take(locator.chapterIndex).sumOf { it.plainTextContent.length.toLong() }
            val totalCharsScrolled = completedCharsInPreviousChapters + boundedOffset
            val calculatedProgress =
                ((totalCharsScrolled.toDouble() / totalBookLengthChars.toDouble()) * 100.0).toFloat()
            val isAtEndOfBook = locator.chapterIndex == chapters.lastIndex && chapterLengthChars > 0 && boundedOffset >= chapterLengthChars
            if (isAtEndOfBook) 100f else calculatedProgress
        } else {
            0f
        }

        Timber.tag("TTS_LOCATE")
            .d("Saving locator from TTS. chapter=${locator.chapterIndex}, block=${locator.blockIndex}, progress=$progress")
        onSavePosition(locator, cfiForWebView, progress)
    }

    fun ensureVerticalChunksLoaded(targetChunk: Int) {
        if (targetChunk >= loadedChunkCount) {
            val chunksToInject = loadedChunkCount..targetChunk
            chunksToInject.forEach { idx ->
                val content = chapterChunks.getOrNull(idx) ?: return@forEach
                webViewRefForTts?.evaluateJavascript(
                    "javascript:window.virtualization.appendChunk($idx, '${escapeJsString(content)}');",
                    null
                )
            }
            loadUpToChunkIndex = targetChunk
            loadedChunkCount = max(loadedChunkCount, targetChunk + 1)
        } else {
            chapterChunks.getOrNull(targetChunk)?.let { content ->
                webViewRefForTts?.evaluateJavascript(
                    "javascript:window.virtualization.appendChunk($targetChunk, '${escapeJsString(content)}');",
                    null
                )
            }
        }
    }

    suspend fun saveActiveTtsPosition(reason: String): Boolean {
        if (!isActiveReaderTtsForCurrentBook()) return false

        val chapterIndex = getActiveTtsChapterIndex() ?: return false
        val sourceCfi = (ttsState.currentWordSourceCfi ?: ttsState.sourceCfi)?.takeIf { it.isNotBlank() } ?: return false
        val locator = locatorConverter.getLocatorFromCfi(epubBook, chapterIndex, sourceCfi) ?: return false

        logTtsChapterDiag("Persisting active TTS position. reason=$reason chapter=$chapterIndex cfi=${sourceCfi.take(48)}")
        saveResolvedLocatorPosition(locator, sourceCfi)
        return true
    }

    suspend fun navigateToActiveTtsPosition(reason: String): Boolean {
        if (!isActiveReaderTtsForCurrentBook()) {
            logTtsChapterDiag("navigateToActiveTtsPosition aborted: inactive reader TTS. reason=$reason")
            return false
        }

        val chapterIndex = getActiveTtsChapterIndex() ?: run {
            logTtsChapterDiag("navigateToActiveTtsPosition aborted: no active TTS chapter. reason=$reason")
            return false
        }
        val sourceCfi = (ttsState.currentWordSourceCfi ?: ttsState.sourceCfi)?.takeIf { it.isNotBlank() } ?: run {
            logTtsChapterDiag("navigateToActiveTtsPosition aborted: no active source CFI. reason=$reason")
            return false
        }
        val sourceOffset =
            ttsState.currentWordStartOffset.takeIf { it >= 0 }
                ?: ttsState.startOffsetInSource.takeIf { it >= 0 }
        val locator = locatorConverter.getLocatorFromCfi(epubBook, chapterIndex, sourceCfi) ?: run {
            logTtsChapterDiag("navigateToActiveTtsPosition aborted: locator conversion failed. reason=$reason chapter=$chapterIndex cfi=${sourceCfi.take(48)}")
            return false
        }
        val targetChunk = max(0, locator.blockIndex / 20)

        saveResolvedLocatorPosition(locator, sourceCfi)
        logTtsChapterDiag("Navigating to active TTS position. reason=$reason targetChapter=$chapterIndex targetChunk=$targetChunk sourceOffset=$sourceOffset")

        when (currentRenderMode) {
            RenderMode.VERTICAL_SCROLL -> {
                isNavigatingToPosition = true
                initialScrollTargetForChapter = null
                isDetachedFromVerticalTts = false
                detachedVerticalTtsChunkKey = null
                suppressNextVerticalTtsDetach = true

                if (chapterIndex != currentChapterIndex) {
                    logTtsChapterDiag("Vertical locate switching chapters. reason=$reason from=$currentChapterIndex to=$chapterIndex targetChunk=$targetChunk")
                    chunkTargetOverride = targetChunk
                    cfiToLoad = sourceCfi
                    currentScrollYPosition = 0
                    currentScrollHeightValue = 0
                    currentChapterIndex = chapterIndex
                } else {
                    if (webViewRefForTts == null) {
                        logTtsChapterDiag("Vertical locate queued because WebView is null. reason=$reason targetChunk=$targetChunk")
                        chunkTargetOverride = targetChunk
                        cfiToLoad = sourceCfi
                    } else {
                        logTtsChapterDiag("Vertical locate in current chapter. reason=$reason targetChunk=$targetChunk usingHighlight=${ttsState.currentText?.isNotBlank() == true}")
                        ensureVerticalChunksLoaded(targetChunk)
                        val chunkText = ttsState.currentText?.takeIf { it.isNotBlank() }
                        val chunkStartOffset = ttsState.startOffsetInSource.takeIf { it >= 0 }
                        if (chunkText != null && chunkStartOffset != null) {
                            webViewRefForTts?.evaluateJavascript(
                                "javascript:window.highlightFromCfi('${escapeJsString(sourceCfi)}', '${escapeJsString(chunkText)}', $chunkStartOffset);",
                                null
                            )
                        } else {
                            webViewRefForTts?.evaluateJavascript(
                                "javascript:window.scrollToCfi('${escapeJsString(sourceCfi)}');",
                                null
                            )
                        }
                        scope.launch {
                            delay(3000L)
                            if (isNavigatingToPosition) {
                                isNavigatingToPosition = false
                            }
                        }
                    }
                }
                return true
            }

            RenderMode.PAGINATED -> {
                if (!isPagerInitialized) {
                    logTtsChapterDiag("Paginated locate aborted: pager not initialized. reason=$reason")
                    return false
                }
                val bookPaginator = paginator as? BookPaginator ?: run {
                    logTtsChapterDiag("Paginated locate aborted: paginator unavailable. reason=$reason")
                    return false
                }
                val pageIndex =
                    sourceOffset?.let { bookPaginator.findPageForCfiAndOffset(chapterIndex, sourceCfi, it) }
                        ?: bookPaginator.findPageForLocator(locator)
                        ?: bookPaginator.chapterStartPageIndices[chapterIndex] ?: run {
                            logTtsChapterDiag("Paginated locate aborted: page lookup failed. reason=$reason chapter=$chapterIndex")
                            return false
                        }

                logTtsChapterDiag("Paginated locate scrolling to page=$pageIndex. reason=$reason")
                isNavigatingToPosition = true
                paginatedPagerState.scrollToPage(pageIndex)
                isNavigatingToPosition = false
                return true
            }
        }
    }

    val onHighlightColorChange: (UserHighlight, HighlightColor) -> Unit = { targetHighlight, newColor ->
        val index = userHighlights.indexOfFirst { it.cfi == targetHighlight.cfi }
        if (index != -1) {
            userHighlights[index] = targetHighlight.copy(color = newColor)
            if (currentRenderMode == RenderMode.VERTICAL_SCROLL && targetHighlight.chapterIndex == currentChapterIndex) {
                val cssClass = newColor.cssClass
                val jsCommand = "javascript:window.HighlightBridgeHelper.updateHighlightStyle('${escapeJsString(targetHighlight.cfi)}', '$cssClass', '${newColor.id}');"
                webViewRefForTts?.evaluateJavascript(jsCommand, null)
            }
        }
    }

    fun startTts() {
        if (BuildConfig.FLAVOR != "oss" && currentTtsMode == TtsPlaybackManager.TtsMode.CLOUD && credits <= 0) {
            showInsufficientCreditsDialog = true
            return
        }

        if (isAutoScrollModeActive) {
            isAutoScrollModeActive = false
            isAutoScrollPlaying = false
        }
        Timber.d("TTS button clicked: Starting TTS")
        userStoppedTts = false

        initiateTtsPlayback(
            renderMode = currentRenderMode,
            webView = webViewRefForTts,
            onPaginatedStart = {
                scope.launch {
                    val token = viewModel.getAuthToken()
                    val currentPage = paginatedPagerState.currentPage
                    val bookPaginator = paginator as? BookPaginator
                    val chapterIndex = bookPaginator?.findChapterIndexForPage(currentPage)
                    if (chapterIndex != null) {
                        val chapterStartPage = bookPaginator.chapterStartPageIndices[chapterIndex] ?: 0
                        val pageInChapter = currentPage - chapterStartPage

                        val ttsChunks = bookPaginator.getTtsChunksForChapter(
                            chapterIndex = chapterIndex,
                            startingFromPageInChapter = pageInChapter
                        )

                        if (!ttsChunks.isNullOrEmpty()) {
                            val chapterTitle = chapters.getOrNull(chapterIndex)?.title
                            val coverUriString = coverImagePath?.let { Uri.fromFile(File(it)).toString() }
                            ttsChapterIndex = chapterIndex
                            ttsController.start(
                                chunks = ttsChunks,
                                bookTitle = epubBook.title,
                                chapterTitle = chapterTitle,
                                coverImageUri = coverUriString,
                                chapterIndex = chapterIndex,
                                totalChapters = chapters.size,
                                ttsMode = currentTtsMode,
                                playbackSource = "READER",
                                authToken = token
                            )
                        }
                    }
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ ->
            startTts()
        }
    )

    fun startTtsFromSelectionPaginated(baseCfi: String, startOffset: Int) {
        if (BuildConfig.FLAVOR != "oss" && currentTtsMode == TtsPlaybackManager.TtsMode.CLOUD && credits <= 0) {
            showInsufficientCreditsDialog = true
            return
        }

        val action = {
            scope.launch {
                val token = viewModel.getAuthToken()
                val bookPaginator = paginator as? BookPaginator
                val chapterIndex = currentChapterInPaginatedMode ?: return@launch
                val chunks = bookPaginator?.getTtsChunksForChapter(chapterIndex) ?: return@launch

                var foundIdx = -1
                for (i in chunks.indices) {
                    val c = chunks[i]
                    val cPath = CfiUtils.getPath(c.sourceCfi)
                    val bPath = CfiUtils.getPath(baseCfi)
                    if (cPath == bPath && startOffset >= c.startOffsetInSource && startOffset < c.startOffsetInSource + c.text.length) {
                        foundIdx = i
                        break
                    }
                }

                if (foundIdx != -1) {
                    val target = chunks[foundIdx]
                    val relativeOffset = startOffset - target.startOffsetInSource
                    val safeRelativeOffset = relativeOffset.coerceIn(0, target.text.length)
                    val slicedText = target.text.substring(safeRelativeOffset)
                    val newChunk = target.copy(text = slicedText, startOffsetInSource = startOffset)

                    val remainingChunks = mutableListOf(newChunk)
                    remainingChunks.addAll(chunks.subList(foundIdx + 1, chunks.size))

                    if (remainingChunks.isNotEmpty()) {
                        ttsShouldStartOnChapterLoad = false
                        ttsChapterIndex = chapterIndex
                        val chapterTitle = chapters.getOrNull(chapterIndex)?.title
                        val coverUriString = coverImagePath?.let { Uri.fromFile(File(it)).toString() }
                        ttsController.start(
                            chunks = remainingChunks,
                            bookTitle = epubBook.title,
                            chapterTitle = chapterTitle,
                            coverImageUri = coverUriString,
                            chapterIndex = chapterIndex,
                            totalChapters = chapters.size,
                            ttsMode = currentTtsMode,
                            playbackSource = "READER",
                            authToken = token
                        )
                    }
                }
            }
        }

        if (isAutoScrollModeActive) {
            isAutoScrollModeActive = false
            isAutoScrollPlaying = false
        }
        userStoppedTts = false

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else if (activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true) {
            showPermissionRationaleDialog = true
        } else {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    TtsSessionObserver(
        ttsState = ttsState,
        ttsController = ttsController,
        currentRenderMode = currentRenderMode,
        chapters = chapters,
        epubBookTitle = epubBook.title,
        coverImagePath = coverImagePath,
        webViewRef = webViewRefForTts,
        loadedChunkCount = loadedChunkCount,
        totalChunksInChapter = chapterChunks.size,
        paginator = paginator,
        pagerState = paginatedPagerState,
        ttsChapterIndex = ttsChapterIndex,
        onTtsChapterIndexChange = { newIndex -> ttsChapterIndex = newIndex },
        onNavigateToChapter = { nextIndex ->
            Timber.tag(TAG_LINK_NAV)
                .d("[CHAPTER-NAV] source=TTS_CHAPTER_CHANGE, from=$currentChapterIndex, to=$nextIndex")
            Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("TtsSessionObserver triggered onNavigateToChapter to: $nextIndex")
            initialScrollTargetForChapter = ChapterScrollPosition.START
            cfiToLoad = null
            currentScrollYPosition = 0
            currentScrollHeightValue = 0
            currentChapterIndex = nextIndex
        },
        onToggleTtsStartOnLoad = { shouldStart ->
            Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("ttsShouldStartOnChapterLoad set to: $shouldStart")
            ttsShouldStartOnChapterLoad = shouldStart
        },
        userStoppedTts = userStoppedTts,
        scope = scope,
        currentTtsMode = currentTtsMode,
        getAuthToken = { viewModel.getAuthToken() },
        locatorConverter = locatorConverter,
        epubBook = epubBook
    )

    TtsHighlightHandler(
        ttsState = ttsState,
        currentRenderMode = currentRenderMode,
        currentChapterIndex = currentChapterIndex,
        webViewRef = webViewRefForTts,
        paginator = paginator,
        pagerState = paginatedPagerState,
        ttsChapterIndex = ttsChapterIndex,
        scope = scope
    )

    EpubReaderSearchEffects(
        searchState = searchState,
        webViewRef = webViewRefForTts,
        currentChapterIndex = currentChapterIndex,
        focusRequester = searchFocusRequester
    )

    val totalPagesInCurrentChapter = remember(currentScrollHeightValue, currentClientHeightValue) {
        if (currentClientHeightValue > 0) {
            max(
                1,
                ceil(currentScrollHeightValue.toFloat() / currentClientHeightValue.toFloat()).toInt()
            )
        } else {
            1
        }
    }

    val currentPageInChapter = remember(
        currentScrollYPosition,
        currentClientHeightValue,
        currentScrollHeightValue,
        totalPagesInCurrentChapter
    ) {
        if (currentClientHeightValue > 0 && currentScrollHeightValue > 0) {
            val normalizedScrollY = max(0, currentScrollYPosition)
            if (currentScrollHeightValue <= currentClientHeightValue) {
                1
            } else {
                val isAtBottom =
                    (normalizedScrollY + currentClientHeightValue) >= (currentScrollHeightValue - 2)
                val calculatedPage = if (isAtBottom) {
                    totalPagesInCurrentChapter
                } else {
                    floor(normalizedScrollY.toFloat() / currentClientHeightValue.toFloat()).toInt() + 1
                }
                max(1, min(calculatedPage, totalPagesInCurrentChapter))
            }
        } else {
            1
        }
    }

    val latestChapterIndex by rememberUpdatedState(currentChapterIndex)

    LaunchedEffect(ttsState.bookTitle, ttsState.chapterIndex, ttsState.sourceCfi, ttsState.playbackSource) {
        if (!hasQueuedInitialTtsLocate && isActiveReaderTtsForCurrentBook()) {
            logTtsChapterDiag("Queueing initial TTS locate from active session restoration")
            queuePendingTtsLocate(TTS_LOCATE_REASON_INITIAL_RESTORE)
            hasQueuedInitialTtsLocate = true
        }
    }

    LaunchedEffect(
        pendingTtsLocateRequest,
        pendingTtsLocateReason,
        currentRenderMode,
        webViewRefForTts,
        paginator,
        isPagerInitialized,
        ttsState.bookTitle,
        ttsState.chapterIndex,
        ttsChapterIndex,
        ttsState.sourceCfi,
        loadedChunkCount,
        chapterChunks.size,
        isDetachedFromVerticalTts
    ) {
        if (!pendingTtsLocateRequest) return@LaunchedEffect
        if (!isActiveReaderTtsForCurrentBook()) {
            logTtsChapterDiag("Dropping pending TTS locate because session is no longer active for this book")
            pendingTtsLocateRequest = false
            pendingTtsLocateReason = null
            return@LaunchedEffect
        }

        if (
            currentRenderMode == RenderMode.VERTICAL_SCROLL &&
            isDetachedFromVerticalTts &&
            pendingTtsLocateReason != TTS_LOCATE_REASON_OVERLAY
        ) {
            logTtsChapterDiag("Dropping automatic TTS locate because the vertical reader is intentionally detached")
            pendingTtsLocateRequest = false
            pendingTtsLocateReason = null
            return@LaunchedEffect
        }

        logTtsChapterDiag("Processing pending TTS locate request")
        if (navigateToActiveTtsPosition("pending_request")) {
            logTtsChapterDiag("Pending TTS locate request completed successfully")
            pendingTtsLocateRequest = false
            pendingTtsLocateReason = null
        } else {
            logTtsChapterDiag("Pending TTS locate request did not navigate yet")
        }
    }

    LaunchedEffect(
        currentRenderMode,
        currentChapterIndex,
        ttsState.playbackSource,
        ttsState.chapterIndex,
        ttsChapterIndex
    ) {
        if (currentRenderMode != RenderMode.VERTICAL_SCROLL) return@LaunchedEffect
        if (!isActiveReaderTtsForCurrentBook()) {
            logTtsChapterDiag("Vertical detach effect resetting because active reader TTS is unavailable")
            isDetachedFromVerticalTts = false
            detachedVerticalTtsChunkKey = null
            suppressNextVerticalTtsDetach = false
            return@LaunchedEffect
        }

        val activeTtsChapterIndex = getActiveTtsChapterIndex() ?: return@LaunchedEffect
        if (currentChapterIndex == activeTtsChapterIndex) {
            logTtsChapterDiag("Vertical detach effect cleared because reader is back on the active TTS chapter")
            suppressNextVerticalTtsDetach = false
            isDetachedFromVerticalTts = false
            detachedVerticalTtsChunkKey = null
            return@LaunchedEffect
        }

        if (suppressNextVerticalTtsDetach) {
            val hasPendingProgrammaticNavigation =
                isNavigatingToPosition || chunkTargetOverride != null || !cfiToLoad.isNullOrBlank()
            if (hasPendingProgrammaticNavigation) {
                logTtsChapterDiag("Vertical detach suppression consumed after programmatic TTS navigation")
                suppressNextVerticalTtsDetach = false
                return@LaunchedEffect
            }

            logTtsChapterDiag("Ignoring stale vertical detach suppression and honoring manual chapter movement")
            suppressNextVerticalTtsDetach = false
        }

        if (!isDetachedFromVerticalTts) {
            detachVerticalReaderFromTts("chapter_mismatch")
        }
    }

    LaunchedEffect(
        currentRenderMode,
        isDetachedFromVerticalTts,
        ttsState.sourceCfi,
        ttsState.startOffsetInSource,
        ttsState.chapterIndex,
        ttsChapterIndex
    ) {
        if (currentRenderMode != RenderMode.VERTICAL_SCROLL) return@LaunchedEffect
        if (!isDetachedFromVerticalTts) return@LaunchedEffect
        if (!isActiveReaderTtsForCurrentBook()) return@LaunchedEffect

        val currentChunkKey = currentTtsChunkKey() ?: return@LaunchedEffect
        val detachedChunkKey = detachedVerticalTtsChunkKey

        if (detachedChunkKey == null) {
            logTtsChapterDiag("Detached vertical reader recorded first observed TTS chunk key")
            detachedVerticalTtsChunkKey = currentChunkKey
            return@LaunchedEffect
        }

        if (currentChunkKey == detachedChunkKey) {
            logTtsChapterDiag("Detached vertical reader waiting for next TTS chunk boundary before rejoining")
            return@LaunchedEffect
        }

        logTtsChapterDiag("Detached vertical reader detected next TTS chunk boundary and will try to rejoin")
        if (navigateToActiveTtsPosition("chunk_follow")) {
            logTtsChapterDiag("Detached vertical reader rejoined active TTS chapter successfully")
            isDetachedFromVerticalTts = false
            detachedVerticalTtsChunkKey = null
        } else {
            logTtsChapterDiag("Detached vertical reader failed to rejoin on this chunk boundary")
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val latestWebViewRefForTts by rememberUpdatedState(webViewRefForTts)
    val latestIsActiveReaderTtsForCurrentBook by rememberUpdatedState(isActiveReaderTtsForCurrentBook())
    val latestSaveActiveTtsPosition by rememberUpdatedState<suspend (String) -> Boolean>({ reason ->
        saveActiveTtsPosition(reason)
    })
    val latestIsDetachedFromVerticalTts by rememberUpdatedState(isDetachedFromVerticalTts)
    val latestCurrentRenderMode by rememberUpdatedState(currentRenderMode)
    val latestQueueLifecycleTtsLocate by rememberUpdatedState({
        if (latestCurrentRenderMode == RenderMode.VERTICAL_SCROLL && latestIsDetachedFromVerticalTts) {
            logTtsChapterDiag("Lifecycle resume skipped automatic TTS locate because the vertical reader is detached")
        } else {
            logTtsChapterDiag("Lifecycle resume queued a TTS locate request")
            queuePendingTtsLocate(TTS_LOCATE_REASON_LIFECYCLE_RESUME)
        }
    })

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (latestIsActiveReaderTtsForCurrentBook) {
                    scope.launch {
                        if (!latestSaveActiveTtsPosition("lifecycle_pause")) {
                            Timber.d("ON_PAUSE detected. Falling back to WebView CFI save.")
                            latestWebViewRefForTts?.evaluateJavascript("javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());", null)
                        }
                    }
                } else {
                    Timber.d("ON_PAUSE detected. Requesting final CFI for robust save.")
                    latestWebViewRefForTts?.evaluateJavascript("javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());", null)
                }
            } else if (event == Lifecycle.Event.ON_RESUME && latestIsActiveReaderTtsForCurrentBook) {
                latestQueueLifecycleTtsLocate()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Timber.d("Disposing reader. Last known chapter was ${latestChapterIndex}. Position saved periodically.")
        }
    }

    LaunchedEffect(currentScrollYPosition, isChapterReadyForBookmarkCheck) {
        if (!isChapterReadyForBookmarkCheck) return@LaunchedEffect

        delay(1500L)
        Timber.d("User stopped scrolling. Requesting CFI for auto-save...")
        webViewRefForTts?.evaluateJavascript("javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());", null)
    }

    val runRecap = { chapterIdx: Int, charLimit: Int ->
        showAiHubSheet = true
        isRecapLoading = true
        recapResult = null
        recapProgressMessage = "Checking past chapters..."

        scope.launch {
            val token = viewModel.getAuthToken()
            var currentCost: Double? = null

            executeRecapLogic(
                epubBook = epubBook,
                chapterIndex = chapterIdx,
                characterLimit = charLimit,
                summaryCacheManager = summaryCacheManager,
                paginator = paginator,
                context = context,
                onProgressUpdate = { recapProgressMessage = it },
                onCostReceived = { cost ->
                    currentCost = cost
                    recapResult = recapResult?.copy(cost = cost) ?: SummarizationResult(cost = cost)
                },
                onResultUpdate = { chunk ->
                    isRecapLoading = false
                    val current = recapResult?.summary ?: ""
                    recapResult = SummarizationResult(
                        summary = current + chunk,
                        cost = currentCost
                    )
                },
                authToken = token,
                onError = { error ->
                    if (error == "INSUFFICIENT_CREDITS") {
                        showInsufficientCreditsDialog = true
                        showRecapPopup = false
                        isRecapLoading = false
                    } else {
                        recapResult = SummarizationResult(error = error)
                    }
                },
                onFinish = { isRecapLoading = false }
            )
        }
    }

    LaunchedEffect(currentChapterIndex) {
        isChapterParsing = true
        isChapterReadyForBookmarkCheck = false
        activeFragmentId = null

        val result = loadChapterContent(
            context = context,
            epubBook = epubBook,
            chapterIndex = currentChapterIndex,
            chunkTargetOverride = chunkTargetOverride,
            isInitialCfiLoad = isInitialCfiLoad,
            cfiToLoad = cfiToLoad,
            locatorConverter = locatorConverter
        )

        chapterHead = result.head
        chapterChunks = result.chunks
        isChapterParsing = false

        if (initialScrollTargetForChapter == ChapterScrollPosition.END) {
            loadUpToChunkIndex = max(0, result.chunks.size - 1)
            loadedChunkCount = result.chunks.size
            topVisibleChunkIndex = loadUpToChunkIndex
        } else {
            loadUpToChunkIndex = result.startChunkIndex
            loadedChunkCount = min(result.chunks.size, result.startChunkIndex + 2)
            topVisibleChunkIndex = 0
        }

        Timber.tag("ReflowPaginationDiag").d("EpubReaderScreen: loadChapterContent finished. chapterChunks.size=${chapterChunks.size}, isChapterParsing=$isChapterParsing")

        if (chunkTargetOverride != null) {
            chunkTargetOverride = null
        }
        if (isInitialCfiLoad) {
            isInitialCfiLoad = false
        }
    }

    EpubReaderSystemUiController(
        window = window,
        view = view,
        showBars = showBars,
        initialIsAppearanceLightStatusBars = initialIsAppearanceLightStatusBars,
        initialSystemBarsBehavior = initialSystemBarsBehavior,
        isDarkTheme = isDarkTheme,
        systemUiMode = systemUiMode
    )

    LaunchedEffect(paginator, currentRenderMode, isPagerInitialized) {
        Timber.tag("ReflowPaginationDiag").d("EpubReaderScreen: Checking paginator init. currentRenderMode=$currentRenderMode, paginator=${paginator != null}, isPagerInitialized=$isPagerInitialized")
        if (currentRenderMode == RenderMode.PAGINATED && paginator != null && !isPagerInitialized) {
            scope.launch {
                val bookPaginator = paginator as? BookPaginator
                val targetChapterIndex = lastKnownLocator?.chapterIndex
                    ?: chapterToLoadOnSwitch
                    ?: initialLocator?.chapterIndex
                    ?: 0

                if (bookPaginator != null) {
                    withTimeoutOrNull(5000L) {
                        snapshotFlow { bookPaginator.chapterPageCounts[targetChapterIndex] }
                            .filter { it != null && it > 0 }
                            .first()
                    }
                }

                val pageToScrollTo = lastKnownLocator?.let { locator ->
                    Timber.d("Paginator ready. Finding page for locator: $locator")
                    (paginator as? BookPaginator)?.findPageForLocator(locator)
                } ?: run {
                    Timber.d("Paginator ready, but no locator. Falling back to chapter start.")
                    val chapterToLoad = chapterToLoadOnSwitch ?: initialLocator?.chapterIndex ?: 0
                    (paginator as? BookPaginator)?.chapterStartPageIndices?.get(chapterToLoad) ?: 0
                }

                @Suppress("SENSELESS_COMPARISON")
                if (pageToScrollTo != null) {
                    Timber.d("Scrolling to page: $pageToScrollTo")
                    paginatedPagerState.scrollToPage(pageToScrollTo)
                } else {
                    Timber.w("Could not determine a page to scroll to.")
                }

                delay(100)
                isPagerInitialized = true
                chapterToLoadOnSwitch = null
            }
        }
    }

    LaunchedEffect(paginatedPagerState.currentPage, paginator) {
        if (currentRenderMode == RenderMode.PAGINATED && paginator != null && isPagerInitialized) {
            delay(1500L)
            val pageToSave = paginatedPagerState.currentPage

            val locator = (paginator as? BookPaginator)?.getLocatorForPage(pageToSave)
            val chapterIndex = paginator!!.findChapterIndexForPage(pageToSave)

            if (locator != null && chapterIndex != null) {
                lastKnownLocator = locator
                val bookPaginator = paginator as? BookPaginator
                val progress = if (totalBookLengthChars > 0 && bookPaginator != null) {
                    val completedCharsInPreviousChapters = chapters.take(chapterIndex).sumOf { it.plainTextContent.length.toLong() }
                    val currentPageInChapter = (bookPaginator.chapterStartPageIndices[chapterIndex] ?: 0).let { pageToSave - it }
                    val charsScrolledInCurrentChapter = bookPaginator.getCharactersScrolledInChapter(chapterIndex, currentPageInChapter)
                    val totalCharsScrolled = completedCharsInPreviousChapters + charsScrolledInCurrentChapter
                    val calculatedProgress = ((totalCharsScrolled.toDouble() / totalBookLengthChars.toDouble()) * 100.0).toFloat()
                    val isLastPageOfBook = pageToSave == paginatedPagerState.pageCount - 1
                    if (isLastPageOfBook) 100f else calculatedProgress

                } else {
                    0f
                }

                Timber.d("Auto-saving paginated position. Page: $pageToSave, Locator: $locator, Progress: $progress%"
                )
                onSavePosition(locator, null, progress)
            } else {
                Timber.w("Could not auto-save paginated position. Locator or chapterIndex was null.")
            }
        }
    }

    val pageInfoBarHeight = PAGE_INFO_BAR_HEIGHT + pageInfoCornerBottomPadding

    val isPageInfoVisible = when (pageInfoMode) {
        PageInfoMode.DEFAULT -> !showBars
        PageInfoMode.SYNC -> showBars
        PageInfoMode.HIDDEN -> false
    }

    LaunchedEffect(bookmarks, paginator) {
        paginator ?: return@LaunchedEffect
        val bookPaginator = paginator as? BookPaginator
        if (bookPaginator == null) {
            Timber.w("Paginator is not a BookPaginator instance, cannot calculate bookmark page map.")
            return@LaunchedEffect
        }
        Timber.d("Paginator or bookmarks changed. Re-calculating bookmark page map for ${bookmarks.size} bookmarks.")
        val newMap = bookmarkPageMap.toMutableMap()

        bookmarks.forEach { bookmark ->
            if (newMap.containsKey(bookmark.cfi)) return@forEach

            scope.launch {
                val locator = locatorConverter.getLocatorFromCfi(
                    book = epubBook,
                    chapterIndex = bookmark.chapterIndex,
                    cfi = bookmark.cfi
                )

                if (locator != null) {
                    Timber.d("Bookmark map: Converted CFI '${bookmark.cfi}' to Locator: $locator")
                    val pageIndex = bookPaginator.findPageForLocator(locator)
                    if (pageIndex != null) {
                        Timber.d("Bookmark map: Found page $pageIndex for locator.")
                        newMap[bookmark.cfi] = pageIndex
                        bookmarkPageMap = newMap.toMap()
                    } else {
                        Timber.w("Bookmark map: Could not find page for locator: $locator.")
                    }
                } else {
                    Timber.w("Bookmark map: Failed to convert CFI '${bookmark.cfi}' to locator. Cannot map this bookmark.")
                }
            }
        }
    }

    LaunchedEffect(paginatedPagerState.currentPage, paginator, currentRenderMode) {
        if (currentRenderMode == RenderMode.PAGINATED && paginator != null && isPagerInitialized) {
            val chapterIndex = (paginator as? BookPaginator)?.findChapterIndexForPage(paginatedPagerState.currentPage)
            if (chapterIndex != null) {
                val chapterPath = chapters.getOrNull(chapterIndex)?.absPath
                val relevantAnchors = epubBook.tableOfContents
                    .filter { it.absolutePath == chapterPath && it.fragmentId != null }
                    .mapNotNull { it.fragmentId }

                if (relevantAnchors.isNotEmpty()) {
                    val active = paginator!!.getActiveAnchorForPage(
                        paginatedPagerState.currentPage,
                        relevantAnchors
                    )
                    if (activeFragmentId != active) {
                        Timber.tag("FRAG_NAV_DEBUG").d("P-Mode Active Anchor: $active")
                        activeFragmentId = active
                    }
                } else {
                    if (activeFragmentId != null) activeFragmentId = null
                }
            }
        }
    }

    fun triggerSaveAndExit() {
        if (!isSavingAndExiting) {
            Timber.d("Triggering final save before exiting.")
            isSavingAndExiting = true

            when (currentRenderMode) {
                RenderMode.VERTICAL_SCROLL -> {
                    webViewRefForTts?.evaluateJavascript(
                        "javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());",
                        null
                    )
                }

                RenderMode.PAGINATED -> {
                    scope.launch {
                        val pageToSave = paginatedPagerState.currentPage
                        val locator = (paginator as? BookPaginator)?.getLocatorForPage(pageToSave)
                        val chapterIndex = paginator?.findChapterIndexForPage(pageToSave)

                        if (locator != null && chapterIndex != null) {
                            val bookPaginator = paginator as? BookPaginator
                            val progress = if (totalBookLengthChars > 0 && bookPaginator != null) {
                                val completedCharsInPreviousChapters = chapters.take(chapterIndex).sumOf { it.plainTextContent.length.toLong() }
                                val currentPageInChapter = (bookPaginator.chapterStartPageIndices[chapterIndex] ?: 0).let { pageToSave - it }
                                val charsScrolledInCurrentChapter = bookPaginator.getCharactersScrolledInChapter(chapterIndex, currentPageInChapter)
                                val totalCharsScrolled = completedCharsInPreviousChapters + charsScrolledInCurrentChapter
                                val calculatedProgress = ((totalCharsScrolled.toDouble() / totalBookLengthChars.toDouble()) * 100.0).toFloat()
                                val isLastPageOfBook = pageToSave == paginatedPagerState.pageCount - 1
                                if (isLastPageOfBook) 100f else calculatedProgress

                            } else {
                                0f
                            }

                            Timber.d("Final save for paginated view. Page: $pageToSave, Locator: $locator, Progress: $progress%"
                            )
                            onSavePosition(locator, null, progress)
                        } else {
                            Timber.w("Final save for paginated view failed. Locator or chapter index is null."
                            )
                        }
                        onNavigateBack()
                    }
                    return
                }
            }

            scope.launch {
                delay(1500L)
                if (isSavingAndExiting) {
                    Timber.w("CFI save on exit timed out. Navigating back.")
                    onNavigateBack()
                }
            }
        }
    }

    fun navigateToSearchResult(index: Int) {
        Timber.tag("NavDiag").d("navigateToSearchResult index: $index")
        performSearchResultNavigation(
            index = index,
            searchState = searchState,
            renderMode = currentRenderMode,
            currentChapterIndex = currentChapterIndex,
            loadedChunkCount = loadedChunkCount,
            webView = webViewRefForTts,
            paginator = paginator,
            coroutineScope = scope,
            onVerticalChapterChange = { chapterIdx, chunkIdx, result ->
                Timber.tag("NavDiag").d("onVerticalChapterChange chapterIdx=$chapterIdx, chunkIdx=$chunkIdx, query=${result.query}")
                initialScrollTargetForChapter = null
                chunkTargetOverride = chunkIdx
                currentScrollYPosition = 0
                currentScrollHeightValue = 0
                currentChapterIndex = chapterIdx
                searchHighlightTarget = result
            },
            onVerticalScrollToResult = { result ->
                Timber.tag("NavDiag").d("onVerticalScrollToResult query=${result.query}, chunk=${result.chunkIndex}")
                val targetChunk = result.chunkIndex
                if (targetChunk >= loadedChunkCount) {
                    val chunksToInject = (loadedChunkCount..targetChunk)
                    chunksToInject.forEach { idx ->
                        val content = chapterChunks.getOrNull(idx)
                        if (content != null) {
                            val escaped = escapeJsString(content)
                            webViewRefForTts?.evaluateJavascript(
                                "javascript:window.virtualization.appendChunk($idx, '$escaped');",
                                null
                            )
                        }
                    }
                    loadUpToChunkIndex = targetChunk
                    loadedChunkCount = max(loadedChunkCount, targetChunk + 1)
                }
                searchHighlightTarget = result
            },
            onPaginatedScrollToPage = { pageIdx ->
                Timber.tag("NavDiag").d("onPaginatedScrollToPage pageIdx=$pageIdx")
                paginatedPagerState.scrollToPage(pageIdx)
            }
        )
    }

    LaunchedEffect(paginatedPagerState.currentPage, currentRenderMode) {
        if (currentRenderMode == RenderMode.PAGINATED && volumeScrollEnabled) {
            delay(200)
            containerFocusRequester.requestFocus()
            Timber.d("Paginated: Page changed to ${paginatedPagerState.currentPage}, re-requesting focus for volume keys.")
        }
    }

    BackHandler(enabled = true) {
        if (isPageSliderVisible) {
            isPageSliderVisible = false
            showBars = true
        } else if (drawerState.isOpen) {
            scope.launch {
                Timber.d("Back pressed: Closing drawer")
                drawerState.close()
            }
        } else if (isAutoScrollModeActive) {
            isAutoScrollModeActive = false
            isAutoScrollPlaying = false
            showBars = true
        } else if (searchState.isSearchActive) {
            searchState.isSearchActive = false
            searchState.onQueryChange("")
        } else {
            Timber.d("Back pressed: Navigating back. Position will be saved first.")
            triggerSaveAndExit()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            EpubReaderDrawerSheet(
                chapters = chapters,
                tableOfContents = epubBook.tableOfContents,
                activeFragmentId = activeFragmentId,
                bookmarks = bookmarks,
                userHighlights = userHighlights,
                currentChapterIndex = currentChapterIndex,
                currentChapterInPaginatedMode = currentChapterInPaginatedMode,
                renderMode = currentRenderMode,
                activeHighlightPalette = currentHighlightPalette,
                onOpenPaletteManager = { showPaletteManager = true },
                onHighlightColorChange = onHighlightColorChange,
                onNavigateToTocEntry = { entry ->
                    scope.launch {
                        drawerState.close()
                        val targetChapterIndex = chapters.indexOfFirst { it.absPath == entry.absolutePath }

                        if (targetChapterIndex != -1) {
                            if (currentRenderMode == RenderMode.VERTICAL_SCROLL) {
                                clearPendingTtsRelocationState("toc_entry_vertical")
                                fragmentToLoad = entry.fragmentId
                                if (targetChapterIndex != currentChapterIndex) {
                                    Timber.tag(TAG_LINK_NAV)
                                        .d("[CHAPTER-NAV] source=TOC_ENTRY, from=$currentChapterIndex, to=$targetChapterIndex, fragment='${entry.fragmentId}', label='${entry.label}'")
                                    initialScrollTargetForChapter = null
                                    currentScrollYPosition = 0
                                    currentScrollHeightValue = 0
                                    currentChapterIndex = targetChapterIndex
                                    logTtsChapterDiag("Manual vertical chapter switch via TOC entry. targetChapter=$targetChapterIndex fragment=${entry.fragmentId}")
                                } else {
                                    if (entry.fragmentId != null) {
                                        val js = """
                                            (function() {
                                                var targetId = '${entry.fragmentId}';
                                                var el = document.getElementById(targetId) || document.querySelector('[name="' + targetId + '"]');
                                                if (el) {
                                                    var targetScrollY = window.scrollY + el.getBoundingClientRect().top - (window.VIEWPORT_PADDING_TOP + 10);
                                                    window.scrollTo({ top: targetScrollY, behavior: 'auto' });
                                                    return -2;
                                                }
                                                if (window.virtualization && window.virtualization.chunksData) {
                                                    for (var i = 0; i < window.virtualization.chunksData.length; i++) {
                                                        var chunkHtml = window.virtualization.chunksData[i];
                                                        if (chunkHtml && (chunkHtml.indexOf('id="' + targetId + '"') !== -1 || chunkHtml.indexOf('name="' + targetId + '"') !== -1 || chunkHtml.indexOf("id='" + targetId + "'") !== -1 || chunkHtml.indexOf("name='" + targetId + "'") !== -1)) {
                                                            return i;
                                                        }
                                                    }
                                                }
                                                return -1;
                                            })()
                                        """.trimIndent()
                                        webViewRefForTts?.evaluateJavascript(js) { result ->
                                            val chunkIdx = result?.toIntOrNull() ?: -1
                                            if (chunkIdx >= 0) {
                                                if (chunkIdx >= loadedChunkCount) {
                                                    val chunksToInject = (loadedChunkCount..chunkIdx)
                                                    chunksToInject.forEach { idx ->
                                                        val content = chapterChunks.getOrNull(idx)
                                                        if (content != null) {
                                                            val escaped = escapeJsString(content)
                                                            webViewRefForTts?.evaluateJavascript(
                                                                "javascript:window.virtualization.appendChunk($idx, '$escaped');",
                                                                null
                                                            )
                                                        }
                                                    }
                                                    loadUpToChunkIndex = chunkIdx
                                                    loadedChunkCount = max(loadedChunkCount, chunkIdx + 1)
                                                }
                                                val scrollJs = """
                                                    (function() {
                                                        var chunkIndex = $chunkIdx;
                                                        var fragmentId = '${entry.fragmentId}';
                                                        var chunkDiv = document.querySelector('.chunk-container[data-chunk-index="' + chunkIndex + '"]');
                                                        if (chunkDiv) {
                                                            if (chunkDiv.innerHTML === "" && window.virtualization && window.virtualization.chunksData[chunkIndex]) {
                                                                chunkDiv.innerHTML = window.virtualization.chunksData[chunkIndex];
                                                                chunkDiv.style.height = "";
                                                            }
                                                            setTimeout(function() {
                                                                var el = document.getElementById(fragmentId) || document.querySelector('[name="' + fragmentId + '"]');
                                                                if (el) {
                                                                    var targetScrollY = window.scrollY + el.getBoundingClientRect().top - (window.VIEWPORT_PADDING_TOP + 10);
                                                                    window.scrollTo({ top: targetScrollY, behavior: 'auto' });
                                                                } else {
                                                                    var targetScrollY = window.scrollY + chunkDiv.getBoundingClientRect().top - window.VIEWPORT_PADDING_TOP;
                                                                    window.scrollTo({ top: targetScrollY, behavior: 'auto' });
                                                                }
                                                            }, 150);
                                                        }
                                                    })()
                                                """.trimIndent()
                                                webViewRefForTts?.evaluateJavascript(scrollJs, null)
                                            } else if (chunkIdx == -1) {
                                                webViewRefForTts?.evaluateJavascript("javascript:window.scrollTo(0,0);", null)
                                            }
                                        }
                                    } else {
                                        webViewRefForTts?.evaluateJavascript("javascript:window.scrollTo(0,0);", null)
                                    }
                                }
                            } else {
                                val bookPaginator = paginator as? BookPaginator
                                if (bookPaginator != null) {
                                    Timber.tag("TOC_NAV_DEBUG").d("TOC Entry Clicked: ${entry.label}, targetChapter: $targetChapterIndex, anchor: ${entry.fragmentId}")

                                    isNavigatingByToc = true

                                    bookPaginator.findPageForAnchor(targetChapterIndex, entry.fragmentId) { targetPage ->
                                        scope.launch {
                                            Timber.tag(TAG_LINK_NAV)
                                                .d("[CHAPTER-NAV] source=TOC_ENTRY_PAGINATED, from=$currentChapterIndex, to=$targetChapterIndex, page=$targetPage, anchor='${entry.fragmentId}', label='${entry.label}'")
                                            Timber.tag("TOC_NAV_DEBUG").d("Scrolling Pager to page: $targetPage")
                                            paginatedPagerState.scrollToPage(targetPage)
                                            isNavigatingByToc = false
                                        }
                                    }
                                } else {
                                    Timber.tag("TOC_NAV_DEBUG").w("Paginator not ready for TOC navigation.")
                                }
                            }
                        } else {
                            Timber.w("TOC navigation failed: Could not find chapter for path ${entry.absolutePath}")
                        }

                        if (showBars) showBars = false
                    }
                },
                onNavigateToChapter = { index ->
                    scope.launch {
                        drawerState.close()
                        when (currentRenderMode) {
                            RenderMode.VERTICAL_SCROLL -> {
                                if (index != currentChapterIndex) {
                                    clearPendingTtsRelocationState("sidebar_chapter_vertical")
                                    Timber.tag(TAG_LINK_NAV)
                                        .d("[CHAPTER-NAV] source=SIDEBAR_CHAPTER, from=$currentChapterIndex, to=$index")
                                    initialScrollTargetForChapter = ChapterScrollPosition.START
                                    currentScrollYPosition = 0
                                    currentScrollHeightValue = 0
                                    currentChapterIndex = index
                                    logTtsChapterDiag("Manual vertical chapter switch via sidebar. targetChapter=$index")
                                    pullToNextProgress = 0f
                                    pullToPrevProgress = 0f
                                    if (showBars) showBars = false
                                }
                            }
                            RenderMode.PAGINATED -> {
                                val bookPaginator = paginator as? BookPaginator
                                if (bookPaginator != null) {
                                    val currentFromPager = bookPaginator.findChapterIndexForPage(paginatedPagerState.currentPage)
                                    if (index != currentFromPager) {
                                        val targetPage = bookPaginator.chapterStartPageIndices[index]
                                        if (targetPage != null) {
                                            Timber.tag(TAG_LINK_NAV)
                                                .d("[CHAPTER-NAV] source=SIDEBAR_CHAPTER_PAGINATED, from=$currentFromPager, to=$index, page=$targetPage")
                                            paginatedPagerState.scrollToPage(targetPage)
                                            if (showBars) showBars = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                onNavigateToBookmark = { bookmark ->
                    scope.launch {
                        drawerState.close()

                        when (currentRenderMode) {
                            RenderMode.VERTICAL_SCROLL -> {
                                Timber.tag("BookmarkDiagnosis").d("Navigating to ${bookmark.cfi}")
                                cfiToLoad = bookmark.cfi

                                val locator = locatorConverter.getLocatorFromCfi(epubBook, bookmark.chapterIndex, bookmark.cfi)
                                val targetChunk = locator?.let { it.blockIndex / 20 }

                                if (bookmark.chapterIndex != currentChapterIndex) {
                                    Timber.tag(TAG_LINK_NAV)
                                        .d("[CHAPTER-NAV] source=BOOKMARK, from=$currentChapterIndex, to=${bookmark.chapterIndex}, cfi='${bookmark.cfi}', label='${bookmark.label}'")
                                    chunkTargetOverride = if (targetChunk != null && targetChunk >= 0) {
                                        targetChunk
                                    } else {
                                        0
                                    }
                                    currentScrollYPosition = 0
                                    currentScrollHeightValue = 0
                                    currentChapterIndex = bookmark.chapterIndex
                                }
                                else {
                                    if (targetChunk != null && targetChunk >= 0) {
                                        isNavigatingToPosition = true

                                        if (targetChunk >= loadedChunkCount) {
                                            Timber.tag("BookmarkDiagnosis").d("Manual Chunk Injection: Loading from $loadedChunkCount to $targetChunk")

                                            val chunksToInject = (loadedChunkCount..targetChunk)
                                            chunksToInject.forEach { idx ->
                                                val content = chapterChunks.getOrNull(idx)
                                                if (content != null) {
                                                    val escaped = escapeJsString(content)
                                                    webViewRefForTts?.evaluateJavascript(
                                                        "javascript:window.virtualization.appendChunk($idx, '$escaped');",
                                                        null
                                                    )
                                                }
                                            }
                                            loadUpToChunkIndex = targetChunk
                                            loadedChunkCount = max(loadedChunkCount, targetChunk + 1)
                                        } else {
                                            val content = chapterChunks.getOrNull(targetChunk)
                                            if (content != null) {
                                                val escaped = escapeJsString(content)
                                                webViewRefForTts?.evaluateJavascript(
                                                    "javascript:window.virtualization.appendChunk($targetChunk, '$escaped');",
                                                    null
                                                )
                                            }
                                        }

                                        webViewRefForTts?.evaluateJavascript(
                                            "javascript:window.scrollToCfi('${escapeJsString(bookmark.cfi)}');",
                                            null
                                        )

                                        scope.launch {
                                            delay(3000)
                                            if (isNavigatingToPosition) {
                                                isNavigatingToPosition = false
                                            }
                                        }
                                    } else {
                                        // Fallback if we couldn't determine chunk
                                        webViewRefForTts?.evaluateJavascript(
                                            "javascript:window.scrollToCfi('${escapeJsString(bookmark.cfi)}');",
                                            null
                                        )
                                    }
                                }
                            }
                            RenderMode.PAGINATED -> {
                                Timber.d("P-Mode Click: Navigating to bookmark. Chapter: ${bookmark.chapterIndex}, CFI: '${bookmark.cfi}'")
                                isNavigatingToPosition = true
                                val locator = locatorConverter.getLocatorFromCfi(
                                    book = epubBook,
                                    chapterIndex = bookmark.chapterIndex,
                                    cfi = bookmark.cfi
                                )

                                if (locator != null) {
                                    Timber.d("P-Mode Click: Successfully converted CFI to Locator: $locator")
                                    val pageIndex = (paginator as? BookPaginator)?.findPageForLocator(locator)
                                    if (pageIndex != null) {
                                        Timber.d("P-Mode Click: Paginator found page $pageIndex for locator. Scrolling.")
                                        paginatedPagerState.scrollToPage(pageIndex)
                                    } else {
                                        Timber.w("P-Mode Click: Paginator could not find a page for the locator. Falling back to chapter start.")
                                        val chapterStartPage = (paginator as? BookPaginator)?.chapterStartPageIndices?.get(bookmark.chapterIndex)
                                        if (chapterStartPage != null) {
                                            paginatedPagerState.scrollToPage(chapterStartPage)
                                        }
                                    }
                                    isNavigatingToPosition = false
                                } else {
                                    Timber.w("P-Mode Click: Failed to convert CFI to Locator. Using old findPageForCfi as a fallback.")
                                    paginator?.findPageForCfi(bookmark.chapterIndex, bookmark.cfi) { pageIndex ->
                                        scope.launch {
                                            paginatedPagerState.scrollToPage(pageIndex)
                                            isNavigatingToPosition = false
                                        }
                                    }
                                }
                            }
                        }
                        if (showBars) {
                            showBars = false
                        }
                    }
                },
                onNavigateToHighlight = { highlight ->
                    scope.launch {
                        drawerState.close()
                        when (currentRenderMode) {
                            RenderMode.VERTICAL_SCROLL -> {
                                cfiToLoad = highlight.cfi
                                val locator = locatorConverter.getLocatorFromCfi(epubBook, highlight.chapterIndex, highlight.cfi)
                                val targetChunk = locator?.let { it.blockIndex / 20 }

                                if (highlight.chapterIndex != currentChapterIndex) {
                                    Timber.tag(TAG_LINK_NAV)
                                        .d("[CHAPTER-NAV] source=HIGHLIGHT, from=$currentChapterIndex, to=${highlight.chapterIndex}, cfi='${highlight.cfi}'")
                                    chunkTargetOverride = if (targetChunk != null && targetChunk >= 0) targetChunk else 0
                                    currentScrollYPosition = 0
                                    currentScrollHeightValue = 0
                                    currentChapterIndex = highlight.chapterIndex
                                } else {
                                    if (targetChunk != null && targetChunk >= 0) {
                                        isNavigatingToPosition = true

                                        if (targetChunk >= loadedChunkCount) {
                                            val chunksToInject = (loadedChunkCount..targetChunk)
                                            chunksToInject.forEach { idx ->
                                                val content = chapterChunks.getOrNull(idx)
                                                if (content != null) {
                                                    val escaped = escapeJsString(content)
                                                    webViewRefForTts?.evaluateJavascript(
                                                        "javascript:window.virtualization.appendChunk($idx, '$escaped');",
                                                        null
                                                    )
                                                }
                                            }
                                            loadUpToChunkIndex = targetChunk
                                            loadedChunkCount = max(loadedChunkCount, targetChunk + 1)
                                        } else {
                                            val content = chapterChunks.getOrNull(targetChunk)
                                            if (content != null) {
                                                val escaped = escapeJsString(content)
                                                webViewRefForTts?.evaluateJavascript(
                                                    "javascript:window.virtualization.appendChunk($targetChunk, '$escaped');",
                                                    null
                                                )
                                            }
                                        }

                                        webViewRefForTts?.evaluateJavascript(
                                            "javascript:window.scrollToCfi('${escapeJsString(highlight.cfi)}');",
                                            null
                                        )

                                        scope.launch {
                                            delay(3000)
                                            if (isNavigatingToPosition) {
                                                isNavigatingToPosition = false
                                            }
                                        }
                                    } else {
                                        webViewRefForTts?.evaluateJavascript(
                                            "javascript:window.scrollToCfi('${escapeJsString(highlight.cfi)}');",
                                            null
                                        )
                                    }
                                }
                            }
                            RenderMode.PAGINATED -> {
                                isNavigatingToPosition = true
                                val locator = locatorConverter.getLocatorFromCfi(epubBook, highlight.chapterIndex, highlight.cfi)
                                if (locator != null) {
                                    val pageIndex = (paginator as? BookPaginator)?.findPageForLocator(locator)
                                    if (pageIndex != null) {
                                        paginatedPagerState.scrollToPage(pageIndex)
                                    } else {
                                        val chapterStartPage = (paginator as? BookPaginator)?.chapterStartPageIndices?.get(highlight.chapterIndex)
                                        if (chapterStartPage != null) {
                                            paginatedPagerState.scrollToPage(chapterStartPage)
                                        }
                                    }
                                    isNavigatingToPosition = false
                                } else {
                                    paginator?.findPageForCfi(highlight.chapterIndex, highlight.cfi) { pageIndex ->
                                        scope.launch {
                                            paginatedPagerState.scrollToPage(pageIndex)
                                            isNavigatingToPosition = false
                                        }
                                    }
                                }
                            }
                        }
                        if (showBars) showBars = false
                    }
                },
                onDeleteBookmark = { bookmarkToDelete ->
                    bookmarks = bookmarks - bookmarkToDelete
                },
                onRenameBookmark = { bookmark, newLabel ->
                    bookmarks = bookmarks.map {
                        if (it.cfi == bookmark.cfi) it.copy(label = newLabel) else it
                    }.toSet()
                },
                onDeleteHighlight = { highlightToDelete ->
                    userHighlights.remove(highlightToDelete)

                    if (currentRenderMode == RenderMode.VERTICAL_SCROLL &&
                        highlightToDelete.chapterIndex == currentChapterIndex) {

                        val cssClass = highlightToDelete.color.cssClass
                        val jsCommand = "javascript:window.HighlightBridgeHelper.removeHighlightByCfi('${escapeJsString(highlightToDelete.cfi)}', '$cssClass');"
                        Timber.d("Executing JS removal for highlight: ${highlightToDelete.cfi}")
                        webViewRefForTts?.evaluateJavascript(jsCommand, null)
                    }
                },
                onEditNote = { highlight ->
                    highlightToNoteCfi = highlight.cfi
                },
            )
        }
    ) {
        val isTtsSessionActive = (ttsState.currentText != null || ttsState.isLoading) && ttsState.playbackSource == "READER"

        val audioManager = remember(context) {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
        var isMusicActive by remember { mutableStateOf(audioManager.isMusicActive) }

        LaunchedEffect(Unit) {
            while(isActive) {
                val currentlyActive = audioManager.isMusicActive
                if (isMusicActive != currentlyActive) {
                    isMusicActive = currentlyActive
                    Timber.d("isMusicActive changed to: $isMusicActive")
                }
                delay(1000)
            }
        }

        volumeScrollEnabled &&
                currentRenderMode == RenderMode.VERTICAL_SCROLL &&
                !isTtsSessionActive &&
                !isMusicActive

        LaunchedEffect(Unit) {
            containerFocusRequester.requestFocus()
        }

        LaunchedEffect(volumeScrollEnabled) {
            if (volumeScrollEnabled) {
                containerFocusRequester.requestFocus()
                Timber.d("Volume scroll enabled. Re-requesting focus on the reader container.")
            }
        }

        val handleGenerateSummary: (Boolean) -> Unit = { force ->
            if (BuildConfig.FLAVOR != "oss" && !isProUser && credits <= 0) {
                showInsufficientCreditsDialog = true
                showAiHubSheet = false
            } else {
                showAiHubSheet = true
                isSummarizationLoading = true
                summarizationResult = null
                when (currentRenderMode) {
                    RenderMode.VERTICAL_SCROLL -> {
                        val cached = if (!force) summaryCacheManager.getSummary(
                            epubBook.title,
                            currentChapterIndex
                        ) else null
                        if (cached != null) {
                            summarizationResult =
                                SummarizationResult(summary = cached, isCacheHit = true)
                            isSummarizationLoading = false
                        } else {
                            webViewRefForTts?.evaluateJavascript("javascript:AiBridgeHelper.extractAndRelayTextForSummarization();") { result ->
                                Timber.d("JS summarization request: $result")
                            } ?: run {
                                isSummarizationLoading = false
                                summarizationResult =
                                    SummarizationResult(error = "WebView not available.")
                            }
                        }
                    }

                    RenderMode.PAGINATED -> {
                        scope.launch {
                            val currentPage = paginatedPagerState.currentPage
                            val token = viewModel.getAuthToken()
                            val chapterIndex =
                                (paginator as? BookPaginator)?.findChapterIndexForPage(currentPage)

                            Timber.tag("POS_DIAG")
                                .d("handleGenerateSummary (Paginated): currentPage=$currentPage -> resolved chapterIndex=$chapterIndex")

                            if (chapterIndex != null) {
                                val cached = if (!force) summaryCacheManager.getSummary(
                                    epubBook.title,
                                    chapterIndex
                                ) else null
                                if (cached != null) {
                                    summarizationResult =
                                        SummarizationResult(summary = cached, isCacheHit = true)
                                    isSummarizationLoading = false
                                    return@launch
                                }

                                val text = paginator?.getPlainTextForChapter(chapterIndex)
                                if (!text.isNullOrBlank()) {
                                    var currentCost: Double? = null
                                    var currentFreeRemaining: Int? = null
                                    val finalSummaryBuilder = StringBuilder()
                                    summarizeBookContent(
                                        content = text,
                                        context = context,
                                        authToken = token,
                                        onUsageReceived = { cost, freeRemaining ->
                                            currentCost = cost
                                            currentFreeRemaining = freeRemaining
                                            summarizationResult = summarizationResult?.copy(
                                                cost = cost, freeRemaining = freeRemaining
                                            ) ?: SummarizationResult(
                                                cost = cost,
                                                freeRemaining = freeRemaining
                                            )
                                        },
                                        onUpdate = { chunk ->
                                            finalSummaryBuilder.append(chunk)
                                            val currentSummary = summarizationResult?.summary ?: ""
                                            summarizationResult = SummarizationResult(
                                                summary = currentSummary + chunk,
                                                cost = currentCost,
                                                freeRemaining = currentFreeRemaining
                                            )
                                        },
                                        onError = { error ->
                                            if (error == "INSUFFICIENT_CREDITS") {
                                                showInsufficientCreditsDialog = true
                                                showAiHubSheet = false
                                                isSummarizationLoading = false
                                            } else {
                                                summarizationResult =
                                                    SummarizationResult(error = error)
                                            }
                                        },
                                        onFinish = {
                                            isSummarizationLoading = false
                                            val fullSummary = finalSummaryBuilder.toString()
                                            if (fullSummary.isNotBlank()) {
                                                val chapterTitle =
                                                    chapters.getOrNull(chapterIndex)?.title
                                                        ?: "Chapter ${chapterIndex + 1}"
                                                summaryCacheManager.saveSummary(
                                                    epubBook.title,
                                                    chapterIndex,
                                                    chapterTitle,
                                                    fullSummary
                                                )
                                            }
                                        })
                                } else {
                                    summarizationResult =
                                        SummarizationResult(error = "Could not get chapter content.")
                                    isSummarizationLoading = false
                                }
                            } else {
                                summarizationResult =
                                    SummarizationResult(error = "Could not determine current chapter.")
                                isSummarizationLoading = false
                            }
                        }
                    }
                }
            }
        }

        val handleGenerateRecap: () -> Unit = {
            if (BuildConfig.FLAVOR != "oss" && credits <= 0) {
                showInsufficientCreditsDialog = true
                showAiHubSheet = false
            } else {
                showAiHubSheet = true
                when (currentRenderMode) {
                    RenderMode.VERTICAL_SCROLL -> {
                        isRequestingRecapCfi = true
                        webViewRefForTts?.evaluateJavascript(
                            "javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());",
                            null
                        )
                    }

                    RenderMode.PAGINATED -> {
                        val bookPaginator = paginator as? BookPaginator
                        val chapterIndex = currentChapterInPaginatedMode

                        if (bookPaginator != null && chapterIndex != null) {
                            val startPage = bookPaginator.chapterStartPageIndices[chapterIndex] ?: 0
                            val currentPageInChapter = paginatedPagerState.currentPage - startPage
                            val charsScrolled = bookPaginator.getCharactersScrolledInChapter(
                                chapterIndex,
                                currentPageInChapter
                            )
                            runRecap(chapterIndex, charsScrolled.toInt())
                        } else {
                            bannerMessage =
                                BannerMessage("Wait for book to load fully.", isError = true)
                        }
                    }
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.statusBars,
        ) { scaffoldPaddingValues ->
            val currentTopPadding = scaffoldPaddingValues.calculateTopPadding()
            var stableTopPadding by remember { mutableStateOf(0.dp) }
            if (currentTopPadding > stableTopPadding) {
                stableTopPadding = currentTopPadding
            }

            val effectiveTopPadding = if (currentRenderMode == RenderMode.PAGINATED) {
                if (systemUiMode == SystemUiMode.HIDDEN) {
                    0.dp
                } else {
                    val insets = ViewCompat.getRootWindowInsets(view)
                    val ignoringVisibilityTopPx = insets?.getInsetsIgnoringVisibility(
                        WindowInsetsCompat.Type.statusBars())?.top ?: 0
                    val ignoringVisibilityTop = with(density) { ignoringVisibilityTopPx.toDp() }

                    if (ignoringVisibilityTop > 0.dp) {
                        ignoringVisibilityTop
                    } else if (stableTopPadding > 0.dp) {
                        stableTopPadding
                    } else {
                        24.dp
                    }
                }
            } else {
                currentTopPadding
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(effectiveBg)
                    .then(activeTextureModifier)
                    .padding(top = effectiveTopPadding)
                    .focusRequester(containerFocusRequester)
                    .focusable()
                    .volumeScrollHandler(
                        volumeScrollEnabled = volumeScrollEnabled,
                        renderMode = currentRenderMode,
                        isTtsActive = isTtsSessionActive,
                        isMusicActive = isMusicActive,
                        currentScrollY = currentScrollYPosition,
                        currentScrollHeight = currentScrollHeightValue,
                        currentClientHeight = currentClientHeightValue,
                        currentChapterIndex = currentChapterIndex,
                        totalChapters = chapters.size,
                        onScrollBy = { amount ->
                            webViewRefForTts?.evaluateJavascript(
                                "window.scrollBy({ top: $amount, behavior: 'smooth' });",
                                null
                            )
                        },
                        onNavigateChapter = { offset, target ->
                            scope.launch {
                                clearPendingTtsRelocationState("manual_chapter_change")
                                initialScrollTargetForChapter = target
                                currentScrollYPosition = 0
                                currentScrollHeightValue = 0
                                currentChapterIndex += offset
                                logTtsChapterDiag(
                                    "Manual vertical chapter switch via volume/button nav. " +
                                        "offset=$offset target=$target newChapter=$currentChapterIndex"
                                )
                            }
                        },
                        onNextPage = {
                            scope.launch {
                                val pageCount = paginatedPagerState.pageCount
                                if (pageCount > 0) {
                                    val targetPage = (paginatedPagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                                    if (targetPage != paginatedPagerState.currentPage) {
                                        if (isPageTurnAnimationEnabled) {
                                            paginatedPagerState.animateScrollToPage(targetPage, animationSpec = tween(700))
                                        } else paginatedPagerState.scrollToPage(targetPage)
                                    }
                                }
                            }
                        },
                        onPrevPage = {
                            scope.launch {
                                val targetPage = (paginatedPagerState.currentPage - 1).coerceAtLeast(0)
                                if (targetPage != paginatedPagerState.currentPage) {
                                    if (isPageTurnAnimationEnabled) {
                                        paginatedPagerState.animateScrollToPage(targetPage, animationSpec = tween(700))
                                    } else paginatedPagerState.scrollToPage(targetPage)
                                }
                            }
                        }
                    )
            ) {
                when (currentRenderMode) {
                    RenderMode.VERTICAL_SCROLL -> {
                        val pageInfoReserve = if (pageInfoMode != PageInfoMode.HIDDEN) pageInfoBarHeight else 0.dp
                        val contentTopPadding = if (pageInfoPosition == PageInfoPosition.TOP) pageInfoReserve else 0.dp
                        val contentBottomPadding = if (pageInfoPosition == PageInfoPosition.BOTTOM) pageInfoReserve else 0.dp

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = contentTopPadding)
                                .padding(bottom = contentBottomPadding)
                                .testTag("ReaderContainer")
                        ) {
                            if (chapters.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(stringResource(R.string.no_chapters_available))
                                }
                            } else {
                                AnimatedContent(
                                    targetState = currentChapterIndex,
                                    transitionSpec = {
                                        if (!pullToTurnEnabled) {
                                            fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                                        } else {
                                            if (targetState > initialState) {
                                                (slideInVertically { height -> height } + fadeIn())
                                                    .togetherWith(slideOutVertically { height -> -height } + fadeOut())
                                            } else {
                                                (slideInVertically { height -> -height } + fadeIn())
                                                    .togetherWith(slideOutVertically { height -> height } + fadeOut())
                                            }
                                        }
                                    },
                                    label = "ChapterChangeAnimation",
                                    modifier = Modifier.fillMaxSize()
                                ) { targetChapterIndex ->
                                    if (isChapterParsing) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    } else if (chapterChunks.isNotEmpty()) {
                                        var hasRequestedExtractionForThisChapter by remember(targetChapterIndex) { mutableStateOf(false) }

                                        val initialContentToLoad = remember(loadUpToChunkIndex, chapterChunks) {
                                            val targetIdx = loadUpToChunkIndex
                                            val startIdx = 0
                                            val endIdx = minOf(chapterChunks.lastIndex, targetIdx + 1)

                                            chapterChunks.indices.joinToString(separator = "\n") { index ->
                                                if (index in startIdx..endIdx) {
                                                    "<div class='chunk-container' data-chunk-index='$index'>${chapterChunks[index]}</div>"
                                                } else {
                                                    "<div class='chunk-container' data-chunk-index='$index'></div>"
                                                }
                                            }
                                        }
                                        val initialHtml = """
                                            <!DOCTYPE html>
                                            <html>
                                            <head>
                                                $chapterHead
                                            </head>
                                            <body>
                                                <div id="content-top-sentinel" style="height: 1px; width: 100%;"></div>
                                                <div id="content-container">
                                                    $initialContentToLoad
                                                </div>
                                                <div id="content-bottom-sentinel" style="height: 1px; width: 100%;"></div>
                                            </body>
                                            </html>
                                        """.trimIndent()

                                        val chapterToRender = chapters[targetChapterIndex]
                                        val chapterKeyForWebView =
                                            remember(
                                                chapterToRender.htmlFilePath,
                                                epubBook.extractionBasePath
                                            ) {
                                                "${epubBook.extractionBasePath}/${chapterToRender.htmlFilePath}"
                                            }

                                        val chapterDirectoryPath =
                                            chapterToRender.htmlFilePath.substringBeforeLast(
                                                '/',
                                                ""
                                            )
                                        val baseUrl =
                                            "file://${epubBook.extractionBasePath}/$chapterDirectoryPath/"

                                        val topPaddingPx =
                                            with(LocalDensity.current) { 16.dp.toPx() }

                                        var isWebViewReady by remember(chapterKeyForWebView) {
                                            mutableStateOf(
                                                false
                                            )
                                        }

                                        LaunchedEffect(isWebViewReady, searchHighlightTarget) {
                                            val target = searchHighlightTarget
                                            Timber.tag("NavDiag").d("Effect(isWebViewReady=$isWebViewReady, target=$target) triggered for chapter $targetChapterIndex.")

                                            if (isWebViewReady && target != null && target.locationInSource == targetChapterIndex) {
                                                Timber.tag("NavDiag").d("Highlighting condition met. Highlighting now.")
                                                delay(200)
                                                val webView = webViewRefForTts
                                                if (webView != null) {
                                                    val escapedQuery = escapeJsString(target.query)
                                                    val targetChunk = target.chunkIndex

                                                    val relativeIdx = searchState.searchResults
                                                        .filter { it.locationInSource == target.locationInSource && it.chunkIndex == targetChunk }
                                                        .indexOf(target)
                                                        .coerceAtLeast(0)

                                                    val js = "javascript:console.log('NavDiag: Executing robust search highlight JS'); window.CURRENT_SEARCH_QUERY = '${escapedQuery}'; window.highlightAllOccurrences('${escapedQuery}'); window.scrollToChunkOccurrence($targetChunk, $relativeIdx);"
                                                    Timber.tag("NavDiag").d("Executing search highlight/scroll JS: $js")
                                                    webView.evaluateJavascript(js) { result ->
                                                        Timber.tag("NavDiag").d("JS highlight/scroll result: $result")
                                                    }
                                                    searchHighlightTarget = null
                                                } else {
                                                    Timber.tag("NavDiag").w("Highlight failed: WebView was null even after ready signal.")
                                                    searchHighlightTarget = null
                                                }
                                            }
                                        }

                                        val currentChapterTocFragments = remember(epubBook.tableOfContents, currentChapterIndex) {
                                            val chapterPath = chapters.getOrNull(currentChapterIndex)?.absPath
                                            epubBook.tableOfContents
                                                .filter { it.absolutePath == chapterPath && it.fragmentId != null }
                                                .mapNotNull { it.fragmentId }
                                        }

                                        @Suppress("ControlFlowWithEmptyBody")
                                        ChapterWebView(
                                            key = chapterKeyForWebView,
                                            chapterTitle = chapterToRender.title,
                                            isDarkTheme = isDarkTheme,
                                            effectiveBg = effectiveBg,
                                            effectiveText = effectiveText,
                                            initialScrollTarget = initialScrollTargetForChapter,
                                            initialPageScrollY = currentScrollYPosition,
                                            initialCfi = cfiToLoad,
                                            initialFragmentId = fragmentToLoad.also { },
                                            userHighlights = userHighlights.filter { it.chapterIndex == targetChapterIndex },
                                            activeHighlightPalette = currentHighlightPalette,
                                            onUpdatePalette = onUpdateHighlightPalette,
                                            onHighlightCreated = { cfi, text, colorId ->
                                                Timber.d("Vertical Mode (Source): Creating Highlight. CFI: $cfi")
                                                Timber.d("Vertical Mode (Source): Text Snippet: '${text.take(50)}...'")
                                                val color = HighlightColor.entries.find { it.id == colorId } ?: HighlightColor.YELLOW

                                                val finalCfi = processAndAddHighlight(
                                                    newCfi = cfi,
                                                    newText = text,
                                                    newColor = color,
                                                    chapterIndex = currentChapterIndex,
                                                    currentList = userHighlights
                                                )

                                                if (pendingNoteForNewHighlight) {
                                                    pendingNoteForNewHighlight = false
                                                    highlightToNoteCfi = finalCfi
                                                }
                                            },
                                            onNoteRequested = { cfi ->
                                                if (cfi != null) {
                                                    highlightToNoteCfi = cfi
                                                } else {
                                                    pendingNoteForNewHighlight = true
                                                }
                                            },
                                            onHighlightDeleted = { cfi ->
                                                val toRemove = userHighlights.find { it.cfi == cfi }
                                                if (toRemove != null) {
                                                    userHighlights.remove(toRemove)
                                                    Timber.d("Deleted highlight: $cfi")
                                                }
                                            },
                                            onChapterInitiallyScrolled = {
                                                val wasCfiScroll = cfiToLoad != null
                                                Timber.tag("NavDiag").d("onChapterInitiallyScrolled for chapter $targetChapterIndex. Was CFI scroll: $wasCfiScroll")
                                                logTtsChapterDiag("Chapter initially scrolled. targetChapter=$targetChapterIndex wasCfiScroll=$wasCfiScroll")
                                                initialScrollTargetForChapter = null
                                                cfiToLoad = null
                                                fragmentToLoad = null
                                                Timber.d("Initial scroll consumed for chapter $targetChapterIndex. Was CFI scroll: $wasCfiScroll")
                                                isWebViewReady = true

                                                if (wasCfiScroll) {
                                                    scope.launch {
                                                        delay(1000L)
                                                        isChapterReadyForBookmarkCheck = true
                                                        Timber.d("Auto-save enabled after CFI scroll delay.")
                                                    }
                                                } else {
                                                    isChapterReadyForBookmarkCheck = true
                                                    Timber.d("Auto-save enabled immediately.")
                                                }

                                                if (ttsShouldStartOnChapterLoad && !hasRequestedExtractionForThisChapter) {
                                                    Timber.d("Auto-starting TTS for new chapter ($targetChapterIndex).")
                                                    logTtsChapterDiag("Auto-starting TTS extraction for chapter load")
                                                    hasRequestedExtractionForThisChapter = true
                                                    scope.launch {
                                                        delay(200)
                                                        webViewRefForTts?.evaluateJavascript(
                                                            "javascript:TtsBridgeHelper.extractAndRelayText();",
                                                            null
                                                        )
                                                    }
                                                }

                                                if (isAutoScrollModeActive && isAutoScrollPlaying) {
                                                    Timber.d("Continuing Auto-Scroll for new chapter with delay.")
                                                    triggerAutoScrollTempPause(1000L)
                                                }
                                            },
                                            onTap = {
                                                if (isAutoScrollModeActive) {
                                                    isAutoScrollPlaying = !isAutoScrollPlaying
                                                    Timber.d("Auto-scroll toggled via tap: $isAutoScrollPlaying")
                                                }

                                                if (!(isMusicianMode && isAutoScrollModeActive) && System.currentTimeMillis() - lastHighlightClickTime > 500) {
                                                    focusManager.clearFocus()
                                                    if (volumeScrollEnabled && !searchState.isSearchActive) {
                                                        containerFocusRequester.requestFocus()
                                                    }

                                                    if (System.currentTimeMillis() - lastScrollHideTime < 400) {
                                                        Timber.d("Ignoring tap toggle because bars were just hidden by scroll (sloppy tap).")
                                                    } else {
                                                        if (showBars || showFormatAdjustmentBars) {
                                                            showBars = false
                                                            showFormatAdjustmentBars = false
                                                            Timber.d("Chapter tapped, hiding all bars.")
                                                        } else {
                                                            showBars = true
                                                            Timber.d("Chapter tapped, showing main bars.")
                                                        }
                                                    }
                                                }
                                            },
                                            onPotentialScroll = {
                                                if (showBars || showFormatAdjustmentBars) {
                                                    showBars = false
                                                    showFormatAdjustmentBars = false
                                                    lastScrollHideTime = System.currentTimeMillis() // Added
                                                    Timber.d("Scroll/Drag detected, hiding bars.")
                                                }
                                                if (isAutoScrollModeActive && isAutoScrollPlaying) {
                                                    triggerAutoScrollTempPause(300L)
                                                }
                                            },
                                            onAutoScrollChapterEnd = {
                                                Timber.d("Screen: onAutoScrollChapterEnd triggered. Current Index: $currentChapterIndex")

                                                scope.launch {
                                                    if (currentChapterIndex < chapters.size - 1) {
                                                        clearPendingTtsRelocationState("auto_scroll_chapter_end")
                                                        Timber.tag(TAG_LINK_NAV)
                                                            .d("[CHAPTER-NAV] source=AUTO_SCROLL_END, from=$currentChapterIndex, to=${currentChapterIndex + 1}")
                                                        Timber.d("Screen: Moving to next chapter (${currentChapterIndex + 1}).")
                                                        initialScrollTargetForChapter = ChapterScrollPosition.START
                                                        currentScrollYPosition = 0
                                                        currentScrollHeightValue = 0
                                                        currentChapterIndex++
                                                        logTtsChapterDiag("Auto-scroll moved vertical reader to next chapter. newChapter=$currentChapterIndex")
                                                        isAutoScrollPlaying = true
                                                    } else {
                                                        Timber.d("Screen: Reached end of book. Stopping auto-scroll.")
                                                        isAutoScrollPlaying = false
                                                    }
                                                }
                                            },
                                            onOverScrollTop = { dragAmount ->
                                                if (pullToTurnEnabled) {
                                                    if (targetChapterIndex > 0) {
                                                        pullToPrevProgress = dragAmount / dragThresholdPx
                                                    }
                                                } else {
                                                    if (targetChapterIndex > 0 && dragAmount > 20f && !isSeamlessTransitioning) {
                                                        isSeamlessTransitioning = true
                                                        webViewRefForTts?.evaluateJavascript("javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());", null)
                                                        scope.launch {
                                                            clearPendingTtsRelocationState("overscroll_top_seamless")
                                                            delay(20)
                                                            initialScrollTargetForChapter = ChapterScrollPosition.END
                                                            currentScrollYPosition = 0
                                                            currentScrollHeightValue = 0
                                                            Timber.tag(TAG_LINK_NAV)
                                                                .d("[CHAPTER-NAV] source=OVERSCROLL_TOP_SEAMLESS, from=$targetChapterIndex, to=${targetChapterIndex - 1}")
                                                            currentChapterIndex--
                                                            logTtsChapterDiag("Seamless overscroll moved to previous chapter. newChapter=$currentChapterIndex")
                                                            if (showBars) showBars = false
                                                            delay(300)
                                                            isSeamlessTransitioning = false
                                                        }
                                                    }
                                                }
                                            },
                                            onOverScrollBottom = { dragAmount ->
                                                if (pullToTurnEnabled) {
                                                    if (targetChapterIndex < chapters.size - 1) {
                                                        pullToNextProgress = dragAmount / dragThresholdPx
                                                    }
                                                } else {
                                                    if (targetChapterIndex < chapters.size - 1 && dragAmount > 20f && !isSeamlessTransitioning) {
                                                        isSeamlessTransitioning = true
                                                        webViewRefForTts?.evaluateJavascript("javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());", null)
                                                        scope.launch {
                                                            clearPendingTtsRelocationState("overscroll_bottom_seamless")
                                                            delay(20)
                                                            initialScrollTargetForChapter = ChapterScrollPosition.START
                                                            currentScrollYPosition = 0
                                                            currentScrollHeightValue = 0
                                                            Timber.tag(TAG_LINK_NAV)
                                                                .d("[CHAPTER-NAV] source=OVERSCROLL_BOTTOM_SEAMLESS, from=$targetChapterIndex, to=${targetChapterIndex + 1}")
                                                            currentChapterIndex++
                                                            logTtsChapterDiag("Seamless overscroll moved to next chapter. newChapter=$currentChapterIndex")
                                                            if (showBars) showBars = false
                                                            delay(300)
                                                            isSeamlessTransitioning = false
                                                        }
                                                    }
                                                }
                                            },
                                            onReleaseOverScrollTop = {
                                                if (pullToTurnEnabled && targetChapterIndex > 0 && pullToPrevProgress >= 1.0f) {
                                                    Timber.d("Swipe-up triggered. Saving position before changing to previous chapter."
                                                    )
                                                    webViewRefForTts?.evaluateJavascript(
                                                        "javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());",
                                                        null
                                                    )
                                                    scope.launch {
                                                        clearPendingTtsRelocationState("pull_to_turn_prev")
                                                        if (isActiveReaderTtsForCurrentBook()) {
                                                            detachVerticalReaderFromTts("pull_to_turn_prev")
                                                        }
                                                        delay(50)
                                                        initialScrollTargetForChapter = ChapterScrollPosition.END
                                                        currentScrollYPosition = 0
                                                        currentScrollHeightValue = 0
                                                        Timber.tag(TAG_LINK_NAV)
                                                            .d("[CHAPTER-NAV] source=PULL_TO_TURN_PREV, from=$targetChapterIndex, to=${targetChapterIndex - 1}")
                                                        currentChapterIndex--
                                                        logTtsChapterDiag("Pull-to-turn moved to previous chapter. newChapter=$currentChapterIndex")
                                                        if (showBars) showBars = false
                                                        Timber.d("Changed to previous chapter: $currentChapterIndex, will scroll to END")
                                                    }
                                                }
                                                pullToPrevProgress = 0f
                                            },
                                            onReleaseOverScrollBottom = {
                                                if (pullToTurnEnabled && targetChapterIndex < chapters.size - 1 && pullToNextProgress >= 1.0f) {
                                                    Timber.d("Swipe-down triggered. Saving position before changing to next chapter."
                                                    )
                                                    webViewRefForTts?.evaluateJavascript(
                                                        "javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());",
                                                        null
                                                    )
                                                    scope.launch {
                                                        clearPendingTtsRelocationState("pull_to_turn_next")
                                                        if (isActiveReaderTtsForCurrentBook()) {
                                                            detachVerticalReaderFromTts("pull_to_turn_next")
                                                        }
                                                        delay(50)
                                                        initialScrollTargetForChapter = ChapterScrollPosition.START
                                                        currentScrollYPosition = 0
                                                        currentScrollHeightValue = 0
                                                        Timber.tag(TAG_LINK_NAV)
                                                            .d("[CHAPTER-NAV] source=PULL_TO_TURN_NEXT, from=$targetChapterIndex, to=${targetChapterIndex + 1}")
                                                        currentChapterIndex++
                                                        logTtsChapterDiag("Pull-to-turn moved to next chapter. newChapter=$currentChapterIndex")
                                                        if (showBars) showBars = false
                                                    }
                                                }
                                                pullToNextProgress = 0f
                                            },
                                            tocFragments = currentChapterTocFragments,
                                            onScrollStateUpdate = { scrollY, scrollHeight, clientHeight, fragId ->
                                                currentScrollYPosition = scrollY
                                                currentScrollHeightValue = scrollHeight
                                                currentClientHeightValue = clientHeight

                                                if (activeFragmentId != fragId) {
                                                    Timber.tag("FRAG_NAV_DEBUG").d("State updated to: $fragId")
                                                    activeFragmentId = fragId
                                                }

                                                if (volumeScrollEnabled && !searchState.isSearchActive) {
                                                    volumeScrollFocusDebounceJob.value?.cancel()
                                                    volumeScrollFocusDebounceJob.value = scope.launch {
                                                        delay(300L)
                                                        if (isActive) {
                                                            containerFocusRequester.requestFocus()
                                                            Timber.d("Refocusing container after scroll to re-enable volume keys.")
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            currentFontSize = currentFontSizeEm,
                                            currentLineHeight = currentLineHeight,
                                            currentParagraphGap = currentParagraphGap,
                                            currentImageSize = currentImageSize,
                                            currentHorizontalMargin = currentHorizontalMargin,
                                            currentVerticalMargin = currentVerticalMargin,
                                            currentFontFamily = currentFontFamily,
                                            customFontPath = currentCustomFontPath,
                                            currentTextAlign = currentTextAlign,
                                            activeTextureId = activeTextureId,
                                            activeTextureAlpha = activeTextureAlpha,
                                            onHighlightClicked = {
                                                lastHighlightClickTime = System.currentTimeMillis()
                                                showBars = false
                                                showFormatAdjustmentBars = false
                                                Timber.d("Highlight clicked - Forcing bars hidden")
                                            },
                                            onInternalLinkClick = { url ->
                                                scope.launch {
                                                    val basePath = "file://${epubBook.extractionBasePath}/"
                                                    val relativeUrl = url.removePrefix(basePath)
                                                    val pathPart = relativeUrl.substringBefore('#')
                                                    val fragmentPart = relativeUrl.substringAfter('#', "").takeIf { it.isNotEmpty() }

                                                    val decodedPath = try { java.net.URLDecoder.decode(pathPart, "UTF-8") } catch(e: Exception) { pathPart }
                                                    val targetChapterIndex = chapters.indexOfFirst { it.absPath == decodedPath }

                                                    Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> url: $url")
                                                    Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> basePath: $basePath")
                                                    Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> relativeUrl: $relativeUrl")
                                                    Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> pathPart: $pathPart")
                                                    Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> decodedPath: $decodedPath")
                                                    Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> fragmentPart: $fragmentPart")
                                                    Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> targetChapterIndex: $targetChapterIndex (current is $currentChapterIndex)")

                                                    if (targetChapterIndex != -1) {
                                                        if (targetChapterIndex != currentChapterIndex) {
                                                            Timber.tag(TAG_LINK_NAV).d("[CHAPTER-NAV] source=INTERNAL_LINK, from=$currentChapterIndex, to=$targetChapterIndex, fragment='$fragmentPart'")
                                                            initialScrollTargetForChapter = null
                                                            fragmentToLoad = fragmentPart
                                                            currentScrollYPosition = 0
                                                            currentScrollHeightValue = 0
                                                            currentChapterIndex = targetChapterIndex
                                                        } else {
                                                            Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> Target is current chapter. Evaluating JS for fragment.")
                                                            if (fragmentPart != null) {
                                                                val js = """
                                                                    (function() {
                                                                        var targetId = '$fragmentPart';
                                                                        var el = document.getElementById(targetId) || document.querySelector('[name="' + targetId + '"]');
                                                                        if (el) {
                                                                            var targetScrollY = window.scrollY + el.getBoundingClientRect().top - (window.VIEWPORT_PADDING_TOP + 10);
                                                                            window.scrollTo({ top: targetScrollY, behavior: 'auto' });
                                                                            return -2;
                                                                        }
                                                                        if (window.virtualization && window.virtualization.chunksData) {
                                                                            for (var i = 0; i < window.virtualization.chunksData.length; i++) {
                                                                                var chunkHtml = window.virtualization.chunksData[i];
                                                                                if (chunkHtml && (chunkHtml.indexOf('id="' + targetId + '"') !== -1 || chunkHtml.indexOf('name="' + targetId + '"') !== -1 || chunkHtml.indexOf("id='" + targetId + "'") !== -1 || chunkHtml.indexOf("name='" + targetId + "'") !== -1)) {
                                                                                    return i;
                                                                                }
                                                                            }
                                                                        }
                                                                        return -1;
                                                                    })()
                                                                """.trimIndent()
                                                                webViewRefForTts?.evaluateJavascript(js) { result ->
                                                                    val chunkIdx = result?.toIntOrNull() ?: -1
                                                                    if (chunkIdx >= 0) {
                                                                        if (chunkIdx >= loadedChunkCount) {
                                                                            val chunksToInject = (loadedChunkCount..chunkIdx)
                                                                            chunksToInject.forEach { idx ->
                                                                                val content = chapterChunks.getOrNull(idx)
                                                                                if (content != null) {
                                                                                    val escaped = escapeJsString(content)
                                                                                    webViewRefForTts?.evaluateJavascript(
                                                                                        "javascript:window.virtualization.appendChunk($idx, '$escaped');",
                                                                                        null
                                                                                    )
                                                                                }
                                                                            }
                                                                            loadUpToChunkIndex = chunkIdx
                                                                            loadedChunkCount = max(loadedChunkCount, chunkIdx + 1)
                                                                        }
                                                                        val scrollJs = """
                                                                            (function() {
                                                                                var chunkIndex = $chunkIdx;
                                                                                var fragmentId = '$fragmentPart';
                                                                                var chunkDiv = document.querySelector('.chunk-container[data-chunk-index="' + chunkIndex + '"]');
                                                                                if (chunkDiv) {
                                                                                    if (chunkDiv.innerHTML === "" && window.virtualization && window.virtualization.chunksData[chunkIndex]) {
                                                                                        chunkDiv.innerHTML = window.virtualization.chunksData[chunkIndex];
                                                                                        chunkDiv.style.height = "";
                                                                                    }
                                                                                    setTimeout(function() {
                                                                                        var el = document.getElementById(fragmentId) || document.querySelector('[name="' + fragmentId + '"]');
                                                                                        if (el) {
                                                                                            var targetScrollY = window.scrollY + el.getBoundingClientRect().top - (window.VIEWPORT_PADDING_TOP + 10);
                                                                                            window.scrollTo({ top: targetScrollY, behavior: 'auto' });
                                                                                        } else {
                                                                                            var targetScrollY = window.scrollY + chunkDiv.getBoundingClientRect().top - window.VIEWPORT_PADDING_TOP;
                                                                                            window.scrollTo({ top: targetScrollY, behavior: 'auto' });
                                                                                        }
                                                                                    }, 150);
                                                                                }
                                                                            })()
                                                                        """.trimIndent()
                                                                        webViewRefForTts?.evaluateJavascript(scrollJs, null)
                                                                    } else if (chunkIdx == -1) {
                                                                        webViewRefForTts?.evaluateJavascript("javascript:window.scrollTo(0,0);", null)
                                                                    }
                                                                }
                                                            } else {
                                                                webViewRefForTts?.evaluateJavascript("javascript:window.scrollTo(0,0);", null)
                                                            }
                                                        }
                                                        if (showBars) showBars = false
                                                    } else {
                                                        Timber.tag(TAG_LINK_NAV).w("Could not find chapter for internal link: $url")
                                                    }
                                                }
                                            },
                                            onWebViewInstanceCreated = { webView ->
                                                webViewRefForTts = webView
                                                webView.evaluateJavascript(
                                                    "javascript:window.setViewportPadding(${topPaddingPx}, 0);",
                                                    null
                                                )
                                            },
                                            onScrollFinished = { success ->
                                                Timber.tag("BookmarkDiagnosis").d("Scroll finished callback. Success: $success")
                                                isNavigatingToPosition = false
                                            },
                                            ttsScope = scope,
                                            onTtsTextReady = { jsonString ->
                                                scope.launch {
                                                    val token = viewModel.getAuthToken()
                                                    Timber.tag("TTS_LIST_DIAG").d("Vertical: Processing received JSON. Length: ${jsonString.length}")
                                                    val ttsChunks = mutableListOf<TtsChunk>()
                                                    try {
                                                        val jsonArray = JSONArray(jsonString)
                                                        for (i in 0 until jsonArray.length()) {
                                                            val jsonObject = jsonArray.getJSONObject(i)
                                                            val text = jsonObject.getString("text")
                                                            val cfiJsonObject = JSONObject(jsonObject.getString("cfi"))
                                                            val cfi = cfiJsonObject.getString("cfi")

                                                            Timber.tag("TTS_LIST_DIAG").d("Processing Chunk[$i]: text='${text.take(40)}...' cfi='$cfi'")
                                                            val baseOffset = jsonObject.optInt("startOffset", 0)

                                                            val subChunks =
                                                                splitTextIntoChunks(text)
                                                            var currentOffset = baseOffset
                                                            for (subChunk in subChunks) {
                                                                ttsChunks.add(
                                                                    TtsChunk(
                                                                        text = subChunk,
                                                                        sourceCfi = cfi,
                                                                        startOffsetInSource = currentOffset
                                                                    )
                                                                )
                                                                currentOffset += subChunk.length
                                                            }
                                                        }

                                                    } catch (e: Exception) {
                                                        Timber.e(e, "Vertical: JSON parsing failed")
                                                    }

                                                    Timber.d("Vertical: Final compiled TTS chunks size: ${ttsChunks.size}")
                                                    logTtsChapterDiag(
                                                        "Vertical TTS text ready. targetChapter=$targetChapterIndex " +
                                                            "chunkCount=${ttsChunks.size} visibleChapter=$currentChapterIndex"
                                                    )

                                                    if (ttsChunks.isNotEmpty()) {
                                                        logTtsChapterDiag("Vertical TTS extraction produced ${ttsChunks.size} chunks for chapter $targetChapterIndex")
                                                        if (BuildConfig.FLAVOR != "oss" && currentTtsMode == TtsPlaybackManager.TtsMode.CLOUD && credits <= 0) {
                                                            showInsufficientCreditsDialog = true
                                                            ttsShouldStartOnChapterLoad = false
                                                            return@launch
                                                        }

                                                        ttsShouldStartOnChapterLoad = false
                                                        userStoppedTts = false

                                                        val chapterTitle = chapters.getOrNull(targetChapterIndex)?.title
                                                        val coverUriString = coverImagePath?.let {
                                                            Uri.fromFile(File(it)).toString()
                                                        }
                                                        ttsChapterIndex = targetChapterIndex

                                                        ttsController.start(
                                                            chunks = ttsChunks,
                                                            bookTitle = epubBook.title,
                                                            chapterTitle = chapterTitle,
                                                            coverImageUri = coverUriString,
                                                            chapterIndex = targetChapterIndex,
                                                            totalChapters = chapters.size,
                                                            ttsMode = currentTtsMode,
                                                            playbackSource = "READER",
                                                            authToken = token
                                                        )
                                                    } else {
                                                        Timber.w("No TTS chunks were created from JSON, not starting TTS.")
                                                        logTtsChapterDiag("Vertical TTS extraction produced 0 chunks for chapter $targetChapterIndex")
                                                        if (ttsShouldStartOnChapterLoad) {
                                                            Timber.d("Empty chapter detected during start. Advancing UI to next chapter.")
                                                            val nextIdx = targetChapterIndex + 1
                                                            if (nextIdx < chapters.size) {
                                                                Timber.tag(TAG_LINK_NAV)
                                                                    .d("[CHAPTER-NAV] source=TTS_EMPTY_CHAPTER_SKIP, from=$targetChapterIndex, to=$nextIdx")
                                                                initialScrollTargetForChapter =
                                                                    ChapterScrollPosition.START
                                                                currentScrollYPosition = 0
                                                                currentScrollHeightValue = 0
                                                                currentChapterIndex = nextIdx
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            onWordSelectedForAiDefinition = { text ->
                                                onDictionaryLookup(text)
                                            },
                                            onTranslate = { text ->
                                                onTranslateLookup(text)
                                            },
                                            onSearch = { text ->
                                                onSearchLookup(text)
                                            },
                                            onContentReadyForSummarization = { content ->
                                                Timber.d("Content received for summarization")
                                                scope.launch {
                                                    val token = viewModel.getAuthToken()
                                                    val chapterIndexToSave = currentChapterIndex
                                                    val bookTitleToSave = epubBook.title
                                                    val finalSummaryBuilder = StringBuilder()

                                                    var currentCost: Double? = null
                                                    var currentFreeRemaining: Int? = null

                                                    summarizeBookContent(
                                                        content = content,
                                                        context = context,
                                                        authToken = token,
                                                        onUsageReceived = { cost: Double?, freeRemaining: Int? ->
                                                            currentCost = cost
                                                            currentFreeRemaining = freeRemaining
                                                            summarizationResult = summarizationResult?.copy(
                                                                cost = cost, freeRemaining = freeRemaining
                                                            ) ?: SummarizationResult(cost = cost, freeRemaining = freeRemaining)
                                                        },
                                                        onUpdate = { chunk ->
                                                            finalSummaryBuilder.append(chunk)
                                                            val currentSummary = summarizationResult?.summary ?: ""
                                                            summarizationResult = SummarizationResult(
                                                                summary = currentSummary + chunk,
                                                                cost = currentCost,
                                                                freeRemaining = currentFreeRemaining
                                                            )
                                                        },
                                                        onError = { error ->
                                                            if (error == "INSUFFICIENT_CREDITS") {
                                                                showInsufficientCreditsDialog = true
                                                                showAiHubSheet = false
                                                                isRecapLoading = false
                                                            } else {
                                                                recapResult = SummarizationResult(error = error)
                                                            }
                                                        },
                                                        onFinish = {
                                                            isSummarizationLoading = false
                                                            val fullSummary = finalSummaryBuilder.toString()
                                                            if (fullSummary.isNotBlank()) {
                                                                val chapterTitle = chapters.getOrNull(chapterIndexToSave)?.title ?: "Chapter ${chapterIndexToSave + 1}"
                                                                summaryCacheManager.saveSummary(bookTitleToSave, chapterIndexToSave, chapterTitle, fullSummary)
                                                            }
                                                        }
                                                    )
                                                }
                                            },
                                            onFootnoteRequested = { html ->
                                                activeFootnoteHtml = html
                                            },
                                            isProUser = isProUser,
                                            isOss = BuildConfig.FLAVOR == "oss",
                                            onShowDictionaryUpsellDialog = {
                                                showDictionaryUpsellDialog = true
                                            },
                                            onCfiGenerated = { cfi ->
                                                Timber.tag("PosSaveDiag").d("JS generated CFI string: '$cfi'")

                                                if (cfi.isBlank() || !cfi.startsWith('/')) {
                                                    if (isSavingAndExiting) {
                                                        isSavingAndExiting = false
                                                        onNavigateBack()
                                                    }
                                                    return@ChapterWebView
                                                }

                                                scope.launch {
                                                    val locator =
                                                        locatorConverter.getLocatorFromCfi(
                                                            epubBook,
                                                            latestChapterIndex,
                                                            cfi
                                                        )

                                                    if (locator != null) {
                                                        Timber.tag("PosSaveDiag").d("✅ Converted CFI to Locator successfully: chapter=${locator.chapterIndex}, block=${locator.blockIndex}, charOffset=${locator.charOffset}")
                                                        lastKnownLocator = locator

                                                        val progressWithinChapter =
                                                            if (currentScrollHeightValue > currentClientHeightValue) {
                                                                val scrollableHeight =
                                                                    (currentScrollHeightValue - currentClientHeightValue).toFloat()
                                                                if (scrollableHeight > 0) (currentScrollYPosition.toFloat() / scrollableHeight).coerceIn(
                                                                    0f,
                                                                    1f
                                                                ) else 1f
                                                            } else if (currentScrollHeightValue > 0) {
                                                                1f
                                                            } else {
                                                                0f
                                                            }

                                                        val currentChapterLengthChars =
                                                            chapters.getOrNull(
                                                                latestChapterIndex
                                                            )?.plainTextContent?.length?.toLong()
                                                                ?: 0L

                                                        // Handle Recap Request INTERCEPTION
                                                        if (isRequestingRecapCfi) {
                                                            Timber.d("Vertical Mode: Received CFI: $cfi")

                                                            isRequestingRecapCfi = false

                                                            // Use exact text offset from Locator if available
                                                            val exactOffset = locatorConverter.getTextOffset(epubBook, locator)

                                                            val charLimit = if (exactOffset != null) {
                                                                Timber.d("Vertical Mode: Using exact text offset from Locator: $exactOffset")
                                                                exactOffset
                                                            } else {
                                                                Timber.w("Vertical Mode: Could not calculate exact offset. Falling back to scroll percentage.")
                                                                (currentChapterLengthChars * progressWithinChapter).toInt()
                                                            }

                                                            Timber.d("Vertical Mode: Final CharLimit: $charLimit (Total Chapter Chars: $currentChapterLengthChars)")

                                                            runRecap(latestChapterIndex, charLimit)
                                                            return@launch
                                                        }

                                                        // Continue with Save Logic
                                                        val progress = if (totalBookLengthChars > 0) {
                                                            val completedCharsInPreviousChapters =
                                                                chapters.take(latestChapterIndex)
                                                                    .sumOf { it.plainTextContent.length.toLong() }

                                                            val charsScrolledInCurrentChapter =
                                                                (progressWithinChapter * currentChapterLengthChars).toLong()
                                                            val totalCharsScrolled =
                                                                completedCharsInPreviousChapters + charsScrolledInCurrentChapter
                                                            val calculatedProgress =
                                                                ((totalCharsScrolled.toDouble() / totalBookLengthChars.toDouble()) * 100.0).toFloat()
                                                            val isLastChapter =
                                                                latestChapterIndex == chapters.size - 1
                                                            val isAtEndOfBook =
                                                                isLastChapter && (currentScrollYPosition + currentClientHeightValue) >= (currentScrollHeightValue - 2)
                                                            if (isAtEndOfBook) 100f else calculatedProgress

                                                        } else {
                                                            0f
                                                        }
                                                        Timber.tag("POS_DIAG").i("Saving Position: Chapter=$latestChapterIndex, CFI=$cfi, Progress=$progress%")
                                                        onSavePosition(locator, cfi, progress)
                                                    } else {
                                                        Timber.w("Failed to convert CFI to Locator: $cfi."
                                                        )
                                                    }

                                                    if (isSwitchingToPaginated) {
                                                        isSwitchingToPaginated = false
                                                        chapterToLoadOnSwitch = latestChapterIndex
                                                        isPagerInitialized = false
                                                        Timber.d("V->P: Locator generated (success=${locator != null}). Switching to paginated mode for chapter $chapterToLoadOnSwitch."
                                                        )
                                                        currentRenderMode = RenderMode.PAGINATED
                                                        onRenderModeChange(RenderMode.PAGINATED)
                                                    }

                                                    if (isSavingAndExiting) {
                                                        Timber.d("Save attempt complete, now navigating back."
                                                        )
                                                        isSavingAndExiting = false
                                                        onNavigateBack()
                                                    }
                                                }
                                            },
                                            onBookmarkCfiGenerated = { cfi ->
                                                if (addBookmarkRequest) {
                                                    Timber.d("Vertical add: CFI received: $cfi. Now requesting snippet."
                                                    )
                                                    scope.launch {
                                                        val jsToExecute =
                                                            "javascript:SnippetBridge.onSnippetExtracted('${
                                                                escapeJsString(cfi)
                                                            }', window.getSnippetForCfi('${
                                                                escapeJsString(
                                                                    cfi
                                                                )
                                                            }'));"
                                                        Timber.d("Executing JS for snippet: $jsToExecute"
                                                        )
                                                        webViewRefForTts?.evaluateJavascript(
                                                            jsToExecute,
                                                            null
                                                        )
                                                    }
                                                    addBookmarkRequest = false
                                                }
                                            },
                                            onSnippetForBookmarkReady = { cfi, snippet ->
                                                Timber.d("Vertical add: onSnippetForBookmarkReady called. CFI: '$cfi', Snippet: '$snippet'"
                                                )
                                                val chapterTitle =
                                                    epubBook.chapters.getOrNull(currentChapterIndex)?.title
                                                        ?: "Unknown Chapter"
                                                val newBookmark = Bookmark(
                                                    cfi = cfi,
                                                    chapterTitle = chapterTitle,
                                                    label = null,
                                                    snippet = snippet,
                                                    pageInChapter = currentPageInChapter,
                                                    totalPagesInChapter = totalPagesInCurrentChapter,
                                                    chapterIndex = currentChapterIndex
                                                )
                                                bookmarks = bookmarks + newBookmark
                                                Timber.d("Vertical add: Created bookmark: $newBookmark"
                                                )
                                            },
                                            onTopChunkUpdated = { chunkIndex ->
                                                topVisibleChunkIndex = chunkIndex
                                            },
                                            initialHtmlContent = initialHtml,
                                            baseUrl = baseUrl,
                                            totalChunks = chapterChunks.size,
                                            initialChunkIndex = loadUpToChunkIndex,
                                            onChunkRequested = { index ->
                                                val chunkContent = chapterChunks.getOrNull(index)
                                                if (chunkContent != null) {
                                                    loadedChunkCount =
                                                        max(loadedChunkCount, index + 1)
                                                    val escapedContent =
                                                        escapeJsString(chunkContent)
                                                    val jsCommand =
                                                        "javascript:window.virtualization.appendChunk($index, '$escapedContent');"
                                                    webViewRefForTts?.evaluateJavascript(
                                                        jsCommand,
                                                        null
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }

                                if (pullToTurnEnabled && currentChapterIndex > 0) {
                                    ChapterChangeIndicator(
                                        text = stringResource(R.string.release_for_previous_chapter),
                                        progress = pullToPrevProgress,
                                        isPullingDown = true,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 8.dp)
                                    )
                                }

                                if (pullToTurnEnabled && currentChapterIndex < chapters.size - 1) {
                                    ChapterChangeIndicator(
                                        text = stringResource(R.string.release_for_next_chapter),
                                        progress = pullToNextProgress,
                                        isPullingDown = false,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    RenderMode.PAGINATED -> {
                        val pageInfoReserve = if (pageInfoMode != PageInfoMode.HIDDEN) pageInfoBarHeight else 0.dp
                        val contentTopPadding = if (pageInfoPosition == PageInfoPosition.TOP) pageInfoReserve else 0.dp
                        val contentBottomPadding = if (pageInfoPosition == PageInfoPosition.BOTTOM) pageInfoReserve else 0.dp

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = contentTopPadding)
                                .padding(bottom = contentBottomPadding)
                                .testTag("ReaderContainer")
                        ) {
                            PaginatedReaderScreen(
                                book = epubBook,
                                isDarkTheme = isDarkTheme,
                                effectiveBg = effectiveBg,
                                effectiveText = effectiveText,
                                pagerState = paginatedPagerState,
                                searchQuery = searchState.searchQuery,
                                fontSizeMultiplier = currentFontSizeEm,
                                lineHeightMultiplier = currentLineHeight,
                                paragraphGapMultiplier = currentParagraphGap,
                                imageSizeMultiplier = currentImageSize,
                                horizontalMarginMultiplier = currentHorizontalMargin,
                                verticalMarginMultiplier = currentVerticalMargin,
                                fontFamily = activeFontFamily,
                                textAlign = currentTextAlign,
                                activeHighlightPalette = currentHighlightPalette,
                                onUpdatePalette = onUpdateHighlightPalette,
                                isPageTurnAnimationEnabled = isPageTurnAnimationEnabled,
                                ttsHighlightInfo = TtsHighlightInfo(
                                    text = ttsState.currentText ?: "",
                                    cfi = ttsState.sourceCfi ?: "",
                                    offset = ttsState.startOffsetInSource
                                ).takeIf { ttsState.currentText != null && ttsState.sourceCfi != null && ttsState.startOffsetInSource != -1 },
                                activeTextureId = activeTextureId,
                                activeTextureAlpha = activeTextureAlpha,
                                initialChapterIndexInBook = lastKnownLocator?.chapterIndex,
                                modifier = Modifier.alpha(if (isPagerInitialized) 1f else 0f),
                                onPaginatorReady = { newPaginator ->
                                    paginator = newPaginator
                                },
                                onTap = { tapOffset ->
                                    Timber.d("PaginatedReaderScreen onTap called with offset: $tapOffset")

                                    if (volumeScrollEnabled) {
                                        containerFocusRequester.requestFocus()
                                    }

                                    if (tapOffset == null || !tapToNavigateEnabled) {
                                        focusManager.clearFocus()
                                        if (volumeScrollEnabled) containerFocusRequester.requestFocus()

                                        if (showBars || showFormatAdjustmentBars) {
                                            showBars = false
                                            showFormatAdjustmentBars = false
                                        } else {
                                            showBars = true
                                        }
                                    } else {
                                        val oneQuarterWidthPx = constraints.maxWidth / 4f
                                        when {
                                            tapOffset.x < oneQuarterWidthPx -> {
                                                scope.launch {
                                                    val targetPage = (paginatedPagerState.currentPage - 1).coerceAtLeast(0)
                                                    if (targetPage != paginatedPagerState.currentPage) {
                                                        if (isPageTurnAnimationEnabled) {
                                                            paginatedPagerState.animateScrollToPage(targetPage, animationSpec = tween(700))
                                                        } else paginatedPagerState.scrollToPage(targetPage)
                                                    }
                                                }
                                            }
                                            tapOffset.x > (constraints.maxWidth - oneQuarterWidthPx) -> {
                                                scope.launch {
                                                    val pageCount = paginatedPagerState.pageCount
                                                    if (pageCount > 0) {
                                                        val targetPage = (paginatedPagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                                                        if (targetPage != paginatedPagerState.currentPage) {
                                                            if (isPageTurnAnimationEnabled) {
                                                                paginatedPagerState.animateScrollToPage(targetPage, animationSpec = tween(700))
                                                            } else paginatedPagerState.scrollToPage(targetPage)
                                                        }
                                                    }
                                                }
                                            }
                                            else -> {
                                                focusManager.clearFocus()
                                                if (volumeScrollEnabled) containerFocusRequester.requestFocus()
                                                if (showBars || showFormatAdjustmentBars) {
                                                    showBars = false
                                                    showFormatAdjustmentBars = false
                                                } else {
                                                    showBars = true
                                                }
                                            }
                                        }
                                    }
                                },
                                isProUser = isProUser,
                                isOss = BuildConfig.FLAVOR == "oss",
                                onShowDictionaryUpsellDialog = {
                                    showDictionaryUpsellDialog = true
                                },
                                onWordSelectedForAiDefinition = { text ->
                                    onDictionaryLookup(text)
                                },
                                onTranslate = { text ->
                                    onTranslateLookup(text)
                                },
                                onSearch = { text ->
                                    onSearchLookup(text)
                                },
                                onStartTtsFromSelection = { cfi, offset ->
                                    startTtsFromSelectionPaginated(cfi, offset)
                                },
                                userHighlights = userHighlights.filter { highlight ->
                                    val currentChapter = currentChapterInPaginatedMode ?: return@filter false
                                    highlight.chapterIndex in (currentChapter - 1)..(currentChapter + 1)
                                },
                                onHighlightCreated = { cfi, text, colorId ->
                                    Timber.d("EpubReaderScreen: onHighlightCreated. CFI: $cfi")
                                    val color = HighlightColor.entries.find { it.id == colorId } ?: HighlightColor.YELLOW
                                    val finalCfi = processAndAddHighlight(
                                        newCfi = cfi,
                                        newText = text,
                                        newColor = color,
                                        chapterIndex = currentChapterInPaginatedMode ?: 0,
                                        currentList = userHighlights
                                    )
                                    if (pendingNoteForNewHighlight) {
                                        pendingNoteForNewHighlight = false
                                        highlightToNoteCfi = finalCfi
                                    }
                                },
                                onNoteRequested = { cfi ->
                                    if (cfi != null) {
                                        highlightToNoteCfi = cfi
                                    } else {
                                        pendingNoteForNewHighlight = true
                                    }
                                },
                                onFootnoteRequested = { html ->
                                    activeFootnoteHtml = html
                                },
                                onHighlightDeleted = { cfi ->
                                    val toRemove = userHighlights.find { it.cfi == cfi }
                                    if (toRemove != null) {
                                        userHighlights.remove(toRemove)
                                    }
                                }
                            )
                            if (!isPagerInitialized) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }

                val isBookmarked: Boolean
                val onBookmarkClick: () -> Unit

                when (currentRenderMode) {
                    RenderMode.VERTICAL_SCROLL -> {
                        val checkVisibleBookmarks = remember(webViewRefForTts, bookmarks, currentChapterIndex) {
                            {
                                val currentChapter = chapters.getOrNull(currentChapterIndex)
                                if (currentChapter == null) {
                                    activeBookmarkInVerticalView = null
                                    return@remember
                                }

                                val bookmarksForCurrentChapter = bookmarks.filter { it.chapterTitle == currentChapter.title }

                                if (bookmarksForCurrentChapter.isEmpty()) {
                                    if (activeBookmarkInVerticalView != null) {
                                        Timber.d("No bookmarks for this chapter, clearing active bookmark.")
                                        activeBookmarkInVerticalView = null
                                    }
                                    return@remember
                                }

                                val cfiJsonArray = "['" + bookmarksForCurrentChapter.joinToString("','") { escapeJsString(it.cfi) } + "']"

                                webViewRefForTts?.evaluateJavascript("javascript:window.findFirstVisibleCfi($cfiJsonArray)") { result ->
                                    val visibleCfi = result?.takeIf { it != "null" && it != "\"\"" }?.removeSurrounding("\"")
                                    val visibleBookmark = visibleCfi?.let { cfi -> bookmarks.find { it.cfi == cfi } }

                                    if (activeBookmarkInVerticalView != visibleBookmark) {
                                        activeBookmarkInVerticalView = visibleBookmark
                                    }
                                }
                            }
                        }

                        LaunchedEffect(isChapterReadyForBookmarkCheck, bookmarks, currentChapterIndex) {
                            if (isChapterReadyForBookmarkCheck && renderMode == RenderMode.VERTICAL_SCROLL) {
                                checkVisibleBookmarks()
                            }
                        }

                        LaunchedEffect(currentScrollYPosition) {
                            if (isChapterReadyForBookmarkCheck && renderMode == RenderMode.VERTICAL_SCROLL) {
                                val now = System.currentTimeMillis()
                                if (now - lastBookmarkCheckTime > 300L) {
                                    lastBookmarkCheckTime = now
                                    Timber.d("Bookmark check on scroll throttle.")
                                    checkVisibleBookmarks()
                                }

                                delay(400L)
                                Timber.d("Bookmark check on scroll stopped (debounced).")
                                checkVisibleBookmarks()
                            }
                        }

                        isBookmarked = activeBookmarkInVerticalView != null
                        onBookmarkClick = {
                            if (isBookmarked) {
                                activeBookmarkInVerticalView?.let { bookmarkToRemove ->
                                    Timber.d("Vertical click: Removing bookmark: $bookmarkToRemove")
                                    bookmarks = bookmarks - bookmarkToRemove
                                }
                            } else {
                                Timber.d("Vertical click: Adding bookmark. Requesting CFI.")
                                addBookmarkRequest = true
                                webViewRefForTts?.evaluateJavascript("javascript:CfiBridge.onCfiForBookmarkExtracted(window.getCurrentCfi());", null)
                            }
                        }
                    }
                    RenderMode.PAGINATED -> {
                        val pageContent = remember(paginatedPagerState.currentPage, paginator) {
                            paginator?.getPageContent(paginatedPagerState.currentPage)
                        }
                        val blocksOnPage = remember(pageContent) {
                            pageContent?.content ?: emptyList()
                        }
                        val bookPaginator = paginator as? BookPaginator

                        val bookmarkedOnPage = remember(paginatedPagerState.currentPage, bookmarkPageMap, bookmarks) {
                            bookmarks.find { bookmark ->
                                bookmarkPageMap[bookmark.cfi] == paginatedPagerState.currentPage
                            }
                        }

                        isBookmarked = bookmarkedOnPage != null

                        onBookmarkClick = {
                            if (isBookmarked) {
                                bookmarkedOnPage.let { bookmarkToRemove ->
                                    bookmarks = bookmarks - bookmarkToRemove
                                    Timber.d("Paginated click: Removing bookmark: $bookmarkToRemove")
                                }
                            } else {
                                val firstTextBlockOnPage = blocksOnPage.firstOrNull { it is TextContentBlock && it.cfi != null }
                                val targetBlockForBookmark = firstTextBlockOnPage ?: blocksOnPage.firstOrNull { it.cfi != null }

                                if (targetBlockForBookmark != null) {
                                    val baseCfi = targetBlockForBookmark.cfi!!
                                    val offset = when (targetBlockForBookmark) {
                                        is ParagraphBlock -> targetBlockForBookmark.startCharOffsetInSource
                                        is HeaderBlock -> targetBlockForBookmark.startCharOffsetInSource
                                        is QuoteBlock -> targetBlockForBookmark.startCharOffsetInSource
                                        is ListItemBlock -> targetBlockForBookmark.startCharOffsetInSource
                                        else -> 0
                                    }

                                    val finalCfi = if (offset > 0) "$baseCfi:$offset" else baseCfi

                                    val chapterIndex = paginator?.findChapterIndexForPage(paginatedPagerState.currentPage)
                                    val chapterTitle = chapterIndex?.let { epubBook.chapters.getOrNull(it)?.title } ?: "Unknown Chapter"
                                    val snippet = (targetBlockForBookmark as? TextContentBlock)?.content?.text?.take(150) ?: ""

                                    val pageInChapter: Int?
                                    val totalPages: Int?
                                    if (bookPaginator != null && chapterIndex != null) {
                                        val chapterStartPage = bookPaginator.chapterStartPageIndices[chapterIndex]
                                        totalPages = bookPaginator.chapterPageCounts[chapterIndex]
                                        pageInChapter = if (chapterStartPage != null) {
                                            paginatedPagerState.currentPage - chapterStartPage + 1
                                        } else {
                                            null
                                        }
                                    } else {
                                        pageInChapter = null
                                        totalPages = null
                                    }

                                    if (chapterIndex != null) {
                                        val newBookmark = Bookmark(
                                            cfi = finalCfi,
                                            chapterTitle = chapterTitle,
                                            label = null,
                                            snippet = snippet,
                                            pageInChapter = pageInChapter,
                                            totalPagesInChapter = totalPages,
                                            chapterIndex = chapterIndex
                                        )
                                        bookmarks = bookmarks + newBookmark
                                        Timber.d("Paginated click: Adding bookmark: $newBookmark")
                                    }
                                }
                            }
                        }
                    }
                }

                BookmarkButton(
                    isBookmarked = isBookmarked,
                    onClick = onBookmarkClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp)
                )

                // Page Info Bar (Vertical)
                AnimatedVisibility(
                    visible = currentRenderMode == RenderMode.VERTICAL_SCROLL && isPageInfoVisible,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(200)),
                    modifier = Modifier.align(
                        if (pageInfoPosition == PageInfoPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(pageInfoBarHeight)
                            .background(infoBarBgColor)
                            .then(activeTextureModifier)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val chapterTitle =
                            chapters.getOrNull(currentChapterIndex)?.title?.take(30)?.trim()
                                ?: "Chapter"

                        val displayPageInfo = if (currentScrollHeightValue <= 0 || isChapterParsing) "" else " ($currentPageInChapter/$totalPagesInCurrentChapter)"

                        Text(
                            text = "$chapterTitle$displayPageInfo",
                            style = MaterialTheme.typography.bodySmall,
                            color = effectiveText.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp)
                        )

                        if (totalBookLengthChars > 0 && currentScrollHeightValue > 0 && !isChapterParsing) {
                            Text(
                                text = "%.1f%%".format(currentBookProgress),
                                style = MaterialTheme.typography.bodySmall,
                                color = effectiveText.copy(alpha = 0.8f),
                                textAlign = TextAlign.End,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }

                // Page Info Bar (Paginated)
                AnimatedVisibility(
                    visible = currentRenderMode == RenderMode.PAGINATED && paginator != null && isPageInfoVisible && paginatedPagerState.pageCount > 0,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(200)),
                    modifier = Modifier.align(
                        if (pageInfoPosition == PageInfoPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(pageInfoBarHeight)
                            .background(infoBarBgColor)
                            .then(activeTextureModifier)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val bookPaginator = paginator as? BookPaginator
                        val chapterIndex = currentChapterInPaginatedMode

                        val textToShow = if (bookPaginator != null && chapterIndex != null) {
                            val chapterTitle =
                                chapters.getOrNull(chapterIndex)?.title?.take(30)?.trim()
                                    ?: "Chapter"
                            val totalPagesInChapter = bookPaginator.chapterPageCounts[chapterIndex]
                            val chapterStartPage = bookPaginator.chapterStartPageIndices[chapterIndex]

                            if (totalPagesInChapter != null && chapterStartPage != null && totalPagesInChapter > 0) {
                                val currentPageInChapter =
                                    paginatedPagerState.currentPage - chapterStartPage + 1
                                "$chapterTitle ($currentPageInChapter/$totalPagesInChapter)"
                            } else {
                                chapterTitle
                            }
                        } else {
                            "Page ${paginatedPagerState.currentPage + 1}/${paginatedPagerState.pageCount}"
                        }

                        Text(
                            text = textToShow,
                            style = MaterialTheme.typography.bodySmall,
                            color = effectiveText.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp)
                        )

                        // Right-aligned Percentage
                        if (paginatedPagerState.pageCount > 0) {
                            if (totalBookLengthChars > 0 && bookPaginator != null && chapterIndex != null) {
                                val completedCharsInPreviousChapters = remember(chapters, chapterIndex) {
                                    chapters.take(chapterIndex).sumOf { it.plainTextContent.length.toLong() }
                                }
                                val chapterStartPage = bookPaginator.chapterStartPageIndices[chapterIndex]
                                val currentPageInChapter = if (chapterStartPage != null) {
                                    paginatedPagerState.currentPage - chapterStartPage
                                } else {
                                    0
                                }
                                val charsScrolledInCurrentChapter = bookPaginator.getCharactersScrolledInChapter(chapterIndex, currentPageInChapter)
                                val totalCharsScrolled = completedCharsInPreviousChapters + charsScrolledInCurrentChapter
                                val calculatedProgress = (totalCharsScrolled.toDouble() / totalBookLengthChars.toDouble()) * 100.0
                                val isLastPageOfBook = paginatedPagerState.currentPage == paginatedPagerState.pageCount - 1
                                val displayProgress = if (isLastPageOfBook) {
                                    100.0
                                } else {
                                    floor(calculatedProgress.coerceAtMost(100.0) * 10) / 10.0
                                }

                                Text(
                                    text = "%.1f%%".format(displayProgress),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = effectiveText.copy(alpha = 0.8f),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            } else {
                                val totalPages = if (currentRenderMode == RenderMode.VERTICAL_SCROLL) {
                                    totalPagesInCurrentChapter
                                } else {
                                    paginatedPagerState.pageCount
                                }
                                val currentPageOneIndexed = paginatedPagerState.currentPage + 1
                                val percentage = (currentPageOneIndexed.toFloat() / totalPages.toFloat()) * 100f
                                Text(
                                    text = "%.1f%%".format(percentage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = effectiveText.copy(alpha = 0.8f),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        }
                    }
                }

                if (isMusicianMode && isAutoScrollModeActive) {
                    val density = LocalDensity.current

                    var leftPulseTrigger by remember { mutableLongStateOf(0L) }
                    var rightPulseTrigger by remember { mutableLongStateOf(0L) }

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

                        // Left Region
                        Box(
                            modifier = regionWidth
                                .then(regionHeight)
                                .align(Alignment.TopStart)
                                .offset(y = topOffset)
                                .padding(start = 8.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = leftPulseAlpha), RoundedCornerShape(12.dp))
                                .border(2.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        var isLongPress = false
                                        val job = scope.launch {
                                            val startTime = System.currentTimeMillis()
                                            while (isActive) {
                                                val elapsed = System.currentTimeMillis() - startTime
                                                if (elapsed >= 1000) {
                                                    leftHoldProgress = 0f
                                                    isLongPress = true
                                                    leftPulseTrigger = System.currentTimeMillis()
                                                    triggerAutoScrollTempPause(1000L)

                                                    scope.launch {
                                                        webViewRefForTts?.evaluateJavascript(
                                                            "window.scrollTo({ top: 0, behavior: 'auto' });", null
                                                        )
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
                                            leftPulseTrigger = System.currentTimeMillis()
                                            triggerAutoScrollTempPause(600L)
                                            val amount = (currentClientHeightValue * 0.75f).toInt()
                                            webViewRefForTts?.evaluateJavascript(
                                                "window.scrollBy({ top: -${amount}, behavior: 'smooth' });", null
                                            )
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (leftHoldProgress > 0f) {
                                CircularProgressIndicator(
                                    progress = { leftHoldProgress },
                                    modifier = Modifier.size(48.dp).alpha(0.6f),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    trackColor = Color.Transparent,
                                    strokeWidth = 4.dp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).alpha(0.6f),
                                    tint = MaterialTheme.colorScheme.onSurface
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
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = rightPulseAlpha), RoundedCornerShape(12.dp))
                                .border(2.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        var isLongPress = false
                                        val job = scope.launch {
                                            val startTime = System.currentTimeMillis()
                                            while (isActive) {
                                                val elapsed = System.currentTimeMillis() - startTime
                                                if (elapsed >= 1000) {
                                                    rightHoldProgress = 0f
                                                    isLongPress = true
                                                    rightPulseTrigger = System.currentTimeMillis()
                                                    triggerAutoScrollTempPause(1000L)

                                                    scope.launch {
                                                        webViewRefForTts?.evaluateJavascript(
                                                            "window.scrollTo({ top: document.body.scrollHeight, behavior: 'auto' });", null
                                                        )
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
                                            rightPulseTrigger = System.currentTimeMillis()
                                            triggerAutoScrollTempPause(600L)
                                            val amount = (currentClientHeightValue * 0.75f).toInt()
                                            webViewRefForTts?.evaluateJavascript(
                                                "window.scrollBy({ top: ${amount}, behavior: 'smooth' });", null
                                            )
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (rightHoldProgress > 0f) {
                                CircularProgressIndicator(
                                    progress = { rightHoldProgress },
                                    modifier = Modifier.size(48.dp).alpha(0.6f),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    trackColor = Color.Transparent,
                                    strokeWidth = 4.dp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).alpha(0.6f),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    EpubReaderSearchOverlay(
                        searchState = searchState,
                        onNavigateResult = { index -> navigateToSearchResult(index) },
                        bottomPadding = bottomPadding
                    )
                }

                val navBarScrimColor = MaterialTheme.colorScheme.surface
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                        .background(navBarScrimColor)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .windowInsetsStartWidth(WindowInsets.navigationBars)
                        .background(navBarScrimColor)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .windowInsetsEndWidth(WindowInsets.navigationBars)
                        .background(navBarScrimColor)
                )

                // Animated Top Bar
                EpubReaderTopBar(
                    isVisible = showBars,
                    searchState = searchState,
                    bookTitle = epubBook.title,
                    currentRenderMode = currentRenderMode,
                    isBookmarked = isBookmarked,
                    isTtsActive = isTtsSessionActive,
                    tapToNavigateEnabled = tapToNavigateEnabled,
                    volumeScrollEnabled = volumeScrollEnabled,
                    isPageTurnAnimationEnabled = isPageTurnAnimationEnabled,
                    hiddenTools = hiddenTools,
                    toolOrder = toolOrder,
                    bottomTools = bottomTools,
                    onCustomizeTools = { showCustomizeToolsSheet = true },
                    onNavigateBack = { triggerSaveAndExit() },
                    isKeepScreenOn = isKeepScreenOn,
                    onToggleKeepScreenOn = { enabled ->
                        isKeepScreenOn = enabled
                        saveKeepScreenOn(context, enabled)
                    },
                    onCloseSearch = {
                        searchState.isSearchActive = false
                        searchState.onQueryChange("")
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        containerFocusRequester.requestFocus()
                        webViewRefForTts?.evaluateJavascript("javascript:window.clearSearchHighlights();", null)
                    },
                    onChangeRenderMode = { newMode ->
                        Timber.tag("NavDiag").d("onChangeRenderMode to $newMode")
                        if (newMode != currentRenderMode) {
                            if (newMode == RenderMode.PAGINATED) {
                                isSwitchingToPaginated = true
                                webViewRefForTts?.evaluateJavascript("javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());", null)
                            } else {
                                scope.launch {
                                    Timber.tag("NavDiag").d("Mode changing to VERTICAL. lastKnownLocator=$lastKnownLocator")
                                    lastKnownLocator?.let { locator ->
                                        val cfi = locatorConverter.getCfiFromLocator(epubBook, locator)
                                        Timber.tag("NavDiag").d("Converted locator to CFI: $cfi")
                                        if (cfi != null) {
                                            val targetChunk = locator.blockIndex / 20
                                            chunkTargetOverride = targetChunk
                                            if (currentChapterIndex != locator.chapterIndex) {
                                                initialScrollTargetForChapter = null
                                                currentScrollYPosition = 0
                                                currentScrollHeightValue = 0
                                                currentChapterIndex = locator.chapterIndex
                                            } else {
                                                if (targetChunk > loadUpToChunkIndex) {
                                                    loadUpToChunkIndex = targetChunk
                                                    loadedChunkCount = max(loadedChunkCount, targetChunk + 1)
                                                }
                                                initialScrollTargetForChapter = null
                                            }
                                            cfiToLoad = cfi
                                        } else {
                                            currentScrollYPosition = 0
                                            currentScrollHeightValue = 0
                                            currentChapterIndex = locator.chapterIndex
                                            cfiToLoad = null
                                        }
                                        currentRenderMode = RenderMode.VERTICAL_SCROLL
                                        onRenderModeChange(RenderMode.VERTICAL_SCROLL)
                                    }
                                }
                            }
                        }
                    },
                    onToggleBookmark = onBookmarkClick,
                    onToggleTapToNavigate = { enabled ->
                        tapToNavigateEnabled = enabled
                        saveTapToNavigateSetting(context, enabled)
                    },
                    onTogglePageTurnAnimation = { enabled ->
                        isPageTurnAnimationEnabled = enabled
                        savePageTurnAnimationSetting(context, enabled)
                    },
                    onToggleVolumeScroll = { enabled ->
                        volumeScrollEnabled = enabled
                        saveVolumeScrollSetting(context, enabled)
                    },
                    onStartAutoScroll = {
                        isAutoScrollModeActive = true
                        isAutoScrollPlaying = true
                        showBars = !isMusicianMode
                    },
                    searchFocusRequester = searchFocusRequester,
                    modifier = Modifier.align(Alignment.TopCenter),
                    onOpenTtsSettings = { showTtsSettingsSheet = true },
                    onOpenDictionarySettings = { showDictionarySettingsSheet = true },
                    onOpenThemeSettings = { showThemePanel = true },
                    onOpenVisualOptions = { showVisualOptionsSheet = true },
                    onOpenAiHub = { showAiHubSheet = true },
                    onOpenSlider = {
                        when (currentRenderMode) {
                            RenderMode.VERTICAL_SCROLL -> {
                                sliderStartPage = currentPageInChapter
                                sliderCurrentPage = currentPageInChapter.toFloat()
                                isPageSliderVisible = true
                                showBars = false
                                scope.launch {
                                    webViewRefForTts?.let { webView ->
                                        startPageThumbnail = captureWebViewVisibleArea(webView)
                                    }
                                }
                            }
                            RenderMode.PAGINATED -> {
                                if (paginatedPagerState.pageCount > 0) {
                                    sliderStartPage = paginatedPagerState.currentPage + 1
                                    sliderCurrentPage = (paginatedPagerState.currentPage + 1).toFloat()
                                    isPageSliderVisible = true
                                    showBars = false
                                    startPageThumbnail = null
                                } else {
                                    bannerMessage = BannerMessage("Book is not paginated yet.")
                                }
                            }
                        }
                    },
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    },
                    onToggleFormat = {
                        showFormatAdjustmentBars = !showFormatAdjustmentBars
                        if (showFormatAdjustmentBars) {
                            searchState.showSearchResultsPanel = false
                            isPageSliderVisible = false
                        }
                    },
                    onToggleSearch = {
                        searchState.isSearchActive = true
                        searchState.showSearchResultsPanel = true
                        showBars = true
                        showFormatAdjustmentBars = false
                    },
                    onToggleTts = {
                        if (isTtsSessionActive) {
                            Timber.d("TTS button clicked: Stopping TTS")
                            userStoppedTts = true
                            ttsController.stop()
                        } else {
                            when {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED -> {
                                    startTts()
                                }
                                activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true -> {
                                    showPermissionRationaleDialog = true
                                }
                                else -> {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    },
                    onToggleReflow = if (onToggleReflow != null) {
                        {
                            val activeChapter = if (currentRenderMode == RenderMode.PAGINATED) {
                                currentChapterInPaginatedMode ?: currentChapterIndex
                            } else {
                                currentChapterIndex
                            }
                            onToggleReflow(activeChapter)
                        }
                    } else null,
                    onDeleteReflow = onDeleteReflow
                )

                val autoScrollPadding by animateDpAsState(
                    targetValue = if (showBars) (bottomPadding + 45.dp + 16.dp) else 32.dp,
                    label = "AutoScrollPadding"
                )

                val alignmentBias by animateFloatAsState(
                    targetValue = if (isAutoScrollCollapsed) 1f else 0f,
                    label = "AutoScrollAlignAnimation"
                )

                val ttsOverlayPadding by animateDpAsState(
                    targetValue = if (showBars) (bottomPadding + 45.dp + 16.dp) else 32.dp,
                    label = "TtsOverlayPadding"
                )

                val ttsAlignmentBias by animateFloatAsState(
                    targetValue = if (isTtsCollapsed) 1f else 0f,
                    label = "TtsAlignAnimation"
                )

                AnimatedVisibility(
                    visible = isTtsSessionActive && showBars,
                    enter = slideInVertically(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200)),
                    exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(200)),
                    modifier = Modifier
                        .align(BiasAlignment(ttsAlignmentBias, 1f))
                        .padding(bottom = ttsOverlayPadding)
                        .padding(horizontal = 16.dp)
                ) {
                    TtsOverlayControls(
                        ttsController = ttsController,
                        ttsState = ttsState,
                        currentTtsMode = currentTtsMode,
                        isCollapsed = isTtsCollapsed,
                        onCollapseChange = { isTtsCollapsed = it },
                        onLocateCurrentChunk = {
                            logTtsChapterDiag("Locate current chunk requested from TTS overlay")
                            queuePendingTtsLocate(TTS_LOCATE_REASON_OVERLAY)
                        },
                        onOpenTtsSettings = { showTtsSettingsSheet = true },
                        onClose = {
                            userStoppedTts = true
                            ttsController.stop()
                        },
                        credits = credits
                    )
                }

                val isAutoScrollControlsVisible = isAutoScrollModeActive

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
                                    saveAutoScrollMaxSpeed(context, newMin)
                                }
                                if (autoScrollSpeed < newMin) {
                                    autoScrollSpeed = newMin
                                    saveAutoScrollSpeed(context, newMin)
                                } else if (autoScrollSpeed > autoScrollMaxSpeed) {
                                    autoScrollSpeed = autoScrollMaxSpeed
                                    saveAutoScrollSpeed(context, autoScrollMaxSpeed)
                                }
                            }
                        },
                        onMaxSpeedChange = { newMax ->
                            updateMaxSpeed(newMax)
                            if (!isAutoScrollLocal) {
                                if (autoScrollMinSpeed > newMax) {
                                    autoScrollMinSpeed = newMax
                                    saveAutoScrollMinSpeed(context, newMax)
                                }
                                if (autoScrollSpeed > newMax) {
                                    autoScrollSpeed = newMax
                                    saveAutoScrollSpeed(context, newMax)
                                } else if (autoScrollSpeed < autoScrollMinSpeed) {
                                    autoScrollSpeed = autoScrollMinSpeed
                                    saveAutoScrollSpeed(context, autoScrollMinSpeed)
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
                            saveMusicianMode(context, newMode)
                            if (newMode) {
                                showBars = false
                                showFormatAdjustmentBars = false
                            }
                            Timber.d("Musician mode toggled: $newMode")
                        },
                        useSlider = autoScrollUseSlider,
                        onInputModeToggle = {
                            autoScrollUseSlider = !autoScrollUseSlider
                            saveAutoScrollUseSlider(context, autoScrollUseSlider)
                        },
                        isLocalMode = isAutoScrollLocal,
                        onLocalModeToggle = onToggleAutoScrollMode,
                        onScrollToTop = {
                            if (isAutoScrollPlaying) {
                                triggerAutoScrollTempPause(1000L)
                            }
                            scope.launch {
                                webViewRefForTts?.evaluateJavascript("window.scrollTo({ top: 0, behavior: 'smooth' });", null)
                            }
                        }
                    )
                }

                // Animated Bottom Bar
                EpubReaderBottomBar(
                    isVisible = showBars,
                    currentRenderMode = currentRenderMode,
                    isTtsSessionActive = isTtsSessionActive,
                    ttsState = ttsState,
                    isProUser = isProUser,
                    hiddenTools = hiddenTools,
                    toolOrder = toolOrder,
                    bottomTools = bottomTools,
                    currentTtsMode = currentTtsMode,
                    onOpenAiHub = { showAiHubSheet = true },
                    onOpenDictionarySettings = { showDictionarySettingsSheet = true },
                    onOpenThemeSettings = { showThemePanel = true },
                    onOpenSlider = {
                        when (currentRenderMode) {
                            RenderMode.VERTICAL_SCROLL -> {
                                sliderStartPage = currentPageInChapter
                                sliderCurrentPage = currentPageInChapter.toFloat()
                                isPageSliderVisible = true
                                showBars = false
                                scope.launch {
                                    webViewRefForTts?.let { webView ->
                                        startPageThumbnail = captureWebViewVisibleArea(webView)
                                    }
                                }
                            }
                            RenderMode.PAGINATED -> {
                                if (paginatedPagerState.pageCount > 0) {
                                    sliderStartPage = paginatedPagerState.currentPage + 1
                                    sliderCurrentPage = (paginatedPagerState.currentPage + 1).toFloat()
                                    isPageSliderVisible = true
                                    showBars = false
                                    startPageThumbnail = null
                                } else {
                                    bannerMessage = BannerMessage("Book is not paginated yet.")
                                }
                            }
                        }
                    },
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    },
                    onToggleFormat = {
                        showFormatAdjustmentBars = !showFormatAdjustmentBars
                        if (showFormatAdjustmentBars) {
                            searchState.showSearchResultsPanel = false
                            isPageSliderVisible = false
                        }
                    },
                    onToggleSearch = {
                        searchState.isSearchActive = true
                        searchState.showSearchResultsPanel = true
                        showBars = true
                        showFormatAdjustmentBars = false
                    },
                    onToggleTts = {
                        if (isTtsSessionActive) {
                            Timber.d("TTS button clicked: Stopping TTS")
                            userStoppedTts = true
                            ttsController.stop()
                        } else {
                            when {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED -> {
                                    startTts()
                                }
                                activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true -> {
                                    showPermissionRationaleDialog = true
                                }
                                else -> {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding)
                )

                ReaderTextFormatPanel(
                    isVisible = showFormatAdjustmentBars,
                    currentFontSize = currentFontSizeEm,
                    onFontSizeChange = { currentFontSizeEm = it },
                    currentLineHeight = currentLineHeight,
                    onLineHeightChange = { currentLineHeight = it },
                    currentParagraphGap = currentParagraphGap,
                    onParagraphGapChange = { currentParagraphGap = it },
                    currentImageSize = currentImageSize,
                    onImageSizeChange = { currentImageSize = it },
                    currentHorizontalMargin = currentHorizontalMargin,
                    onHorizontalMarginChange = { currentHorizontalMargin = it },
                    currentVerticalMargin = currentVerticalMargin,
                    onVerticalMarginChange = { currentVerticalMargin = it },
                    currentFont = currentFontFamily,
                    currentCustomFontName = if(currentCustomFontPath != null) {
                        customFonts.find { it.path == currentCustomFontPath }?.displayName ?: "Custom Font"
                    } else null,
                    onFontOptionClick = { showFontSelectionSheet = true },
                    currentTextAlign = currentTextAlign,
                    onTextAlignChange = { newAlign ->
                        currentTextAlign = newAlign
                        if (newAlign == ReaderTextAlign.JUSTIFY && currentRenderMode == RenderMode.PAGINATED) {
                            showJustifyWarningDialog = true
                        }
                    },
                    onReset = {
                        currentFontSizeEm = DEFAULT_FONT_SIZE_VAL
                        currentLineHeight = DEFAULT_LINE_HEIGHT_VAL
                        currentParagraphGap = DEFAULT_PARAGRAPH_GAP_VAL
                        currentImageSize = DEFAULT_IMAGE_SIZE_VAL
                        currentHorizontalMargin = DEFAULT_HORIZONTAL_MARGIN_VAL
                        currentVerticalMargin = DEFAULT_VERTICAL_MARGIN_VAL
                        currentFontFamily = ReaderFont.ORIGINAL
                        currentCustomFontPath = null
                        currentTextAlign = ReaderTextAlign.DEFAULT
                    },
                    isLocalMode = isFormatLocal,
                    onLocalModeToggle = {
                        isFormatLocal = it
                        saveFormatIsLocal(context, bookId, it)
                    },
                    onClose = { showFormatAdjustmentBars = false }
                )

                val effectiveCurrentChapterIndex = if (currentRenderMode == RenderMode.PAGINATED) {
                    currentChapterInPaginatedMode ?: currentChapterIndex
                } else {
                    currentChapterIndex
                }

                EpubReaderAiOverlays(
                    bookTitle = epubBook.title,
                    summaryCacheManager = summaryCacheManager,
                    summarizationResult = summarizationResult,
                    isSummarizationLoading = isSummarizationLoading,
                    showSummarizationUpsellDialog = showSummarizationUpsellDialog,
                    onDismissSummarizationUpsell = { showSummarizationUpsellDialog = false },
                    recapResult = recapResult,
                    isRecapLoading = isRecapLoading,
                    showAiDefinitionPopup = showAiDefinitionPopup,
                    selectedTextForAi = selectedTextForAi,
                    aiDefinitionResult = aiDefinitionResult,
                    isAiDefinitionLoading = isAiDefinitionLoading,
                    onDismissAiDefinition = {
                        showAiDefinitionPopup = false
                        selectedTextForAi = null
                        aiDefinitionResult = null
                        webViewRefForTts?.evaluateJavascript(
                            "javascript:if(window.getSelection){window.getSelection().removeAllRanges();} else if(document.selection){document.selection.empty();}",
                            null
                        )
                    },
                    showDictionaryUpsellDialog = showDictionaryUpsellDialog,
                    onDismissDictionaryUpsell = { showDictionaryUpsellDialog = false },
                    onNavigateToPro = onNavigateToPro,
                    isTtsSessionActive = isTtsSessionActive,
                    onOpenExternalDictionary = { text ->
                        if (!selectedDictPackage.isNullOrEmpty()) {
                            ExternalDictionaryHelper.launchDictionary(
                                context,
                                selectedDictPackage!!,
                                text
                            )
                        } else {
                            Toast.makeText(
                                context,
                                "Select an offline dictionary first.",
                                Toast.LENGTH_SHORT
                            ).show()
                            showDictionarySettingsSheet = true
                        }
                    },
                    getAuthToken = { viewModel.getAuthToken() },
                    credits = credits,
                    isProUser = isProUser,
                    currentChapterIndex = effectiveCurrentChapterIndex,
                    chapterTitle = chapters.getOrNull(effectiveCurrentChapterIndex)?.title ?: "Chapter ${effectiveCurrentChapterIndex + 1}",
                    showAiHubSheet = showAiHubSheet,
                    onGenerateSummary = handleGenerateSummary,
                    onGenerateRecap = handleGenerateRecap,
                    onDismissAiHub = { showAiHubSheet = false },
                    onClearSummary = { summarizationResult = null },
                    onClearRecap = { recapResult = null }
                )

                if (isNavigatingToPosition && currentRenderMode == RenderMode.PAGINATED) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                            .clickable(enabled = true) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.navigating_to_position),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                if (showPermissionRationaleDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermissionRationaleDialog = false },
                        title = { Text(stringResource(R.string.dialog_permission_required)) },
                        text = { Text(stringResource(R.string.dialog_permission_notification_desc)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showPermissionRationaleDialog = false
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            ) {
                                Text(stringResource(R.string.action_continue))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showPermissionRationaleDialog = false
                                    startTts()
                                }
                            ) {
                                Text(stringResource(R.string.action_not_now))
                            }
                        }
                    )
                }

                if (showJustifyWarningDialog) {
                    AlertDialog(
                        onDismissRequest = { showJustifyWarningDialog = false },
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        title = { Text(stringResource(R.string.dialog_justified_text_limitation)) },
                        text = { Text(stringResource(R.string.dialog_justified_text_limitation_desc)) },
                        confirmButton = {
                            TextButton(onClick = { showJustifyWarningDialog = false }) {
                                Text(stringResource(R.string.action_i_understand))
                            }
                        }
                    )
                }
                AnimatedVisibility(
                    visible = isNavigatingByToc,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                            .clickable(enabled = true) { },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.navigating_to_chapter),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                if (highlightToNoteCfi != null) {
                    val targetHighlight = userHighlights.find {
                        it.cfi == highlightToNoteCfi || (highlightToNoteCfi != null && it.cfi.contains(highlightToNoteCfi!!))
                    }
                    if (targetHighlight != null) {
                        AnnotationBottomSheet(
                            highlight = targetHighlight,
                            effectiveBg = effectiveBg,
                            effectiveText = effectiveText,
                            activeHighlightPalette = currentHighlightPalette,
                            onColorChange = { newColor -> onHighlightColorChange(targetHighlight, newColor) },
                            onOpenPaletteManager = { showPaletteManager = true },
                            onDismiss = { highlightToNoteCfi = null },
                            onSave = { noteText ->
                                val index = userHighlights.indexOfFirst { it.cfi == targetHighlight.cfi }
                                if (index != -1) {
                                    userHighlights[index] = targetHighlight.copy(note = noteText.takeIf { it.isNotBlank() })
                                }
                                highlightToNoteCfi = null
                            },
                            onDelete = {
                                userHighlights.remove(targetHighlight)

                                if (currentRenderMode == RenderMode.VERTICAL_SCROLL && targetHighlight.chapterIndex == currentChapterIndex) {
                                    val cssClass = targetHighlight.color.cssClass
                                    val jsCommand = "javascript:window.HighlightBridgeHelper.removeHighlightByCfi('${escapeJsString(
                                        targetHighlight.cfi)}', '$cssClass');"
                                    webViewRefForTts?.evaluateJavascript(jsCommand, null)
                                }
                                highlightToNoteCfi = null
                            },
                            onCopy = {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(context.getString(R.string.clip_label_copied_text), targetHighlight.text)
                                clipboardManager.setPrimaryClip(clip)
                                highlightToNoteCfi = null
                            },
                            onDictionary = {
                                onDictionaryLookup(targetHighlight.text)
                                highlightToNoteCfi = null
                            },
                            onTranslate = {
                                onTranslateLookup(targetHighlight.text)
                                highlightToNoteCfi = null
                            },
                            onSearch = {
                                onSearchLookup(targetHighlight.text)
                                highlightToNoteCfi = null
                            }
                        )
                    }
                }

                if (activeFootnoteHtml != null) {
                    FootnoteBottomSheet(
                        htmlContent = activeFootnoteHtml!!,
                        effectiveBg = effectiveBg,
                        effectiveText = effectiveText,
                        onDismiss = { activeFootnoteHtml = null }
                    )
                }

                CustomTopBanner(bannerMessage = bannerMessage)
            }
        }

        EpubReaderPageSlider(
            isVisible = isPageSliderVisible,
            currentRenderMode = currentRenderMode,
            totalPages = if (currentRenderMode == RenderMode.VERTICAL_SCROLL) totalPagesInCurrentChapter else paginatedPagerState.pageCount,
            sliderCurrentPage = sliderCurrentPage,
            sliderStartPage = sliderStartPage,
            startPageThumbnail = startPageThumbnail,
            paginator = paginator,
            chapters = chapters,
            onClose = {
                isPageSliderVisible = false
                showBars = true
            },
            onScrub = { newValue ->
                sliderCurrentPage = newValue
                isFastScrubbing = true
                scrubDebounceJob.value?.cancel()
                scrubDebounceJob.value = scope.launch {
                    delay(200)
                    if (isActive) {
                        val targetPage = newValue.roundToInt()
                        if (currentRenderMode == RenderMode.VERTICAL_SCROLL) {
                            val scrollY = (targetPage - 1) * currentClientHeightValue
                            webViewRefForTts?.evaluateJavascript("window.scrollTo(0, $scrollY);", null)
                        } else {
                            paginatedPagerState.scrollToPage(targetPage - 1)
                        }
                        isFastScrubbing = false
                    }
                }
            },
            onJumpToPage = { page ->
                scope.launch {
                    if (currentRenderMode == RenderMode.VERTICAL_SCROLL) {
                        sliderCurrentPage = page.toFloat()
                        val scrollY = (page - 1) * currentClientHeightValue
                        webViewRefForTts?.evaluateJavascript("window.scrollTo(0, $scrollY);", null)
                    } else {
                        sliderCurrentPage = page.toFloat()
                        paginatedPagerState.scrollToPage(page - 1)
                    }
                }
            }
        )

        if (isPageSliderVisible && isFastScrubbing) {
            val total = if (currentRenderMode == RenderMode.VERTICAL_SCROLL) totalPagesInCurrentChapter else paginatedPagerState.pageCount
            PageScrubbingAnimation(currentPage = sliderCurrentPage.roundToInt(), totalPages = total)
        }

        if (showTtsSettingsSheet) {
            TtsSettingsSheet(
                isVisible = true,
                onDismiss = { showTtsSettingsSheet = false },
                currentMode = currentTtsMode,
                onModeChange = { newMode ->
                    currentTtsMode = newMode
                    saveTtsMode(context, newMode.name)
                    ttsController.changeTtsMode(newMode.name)
                },
                currentSpeakerId = ttsState.speakerId,
                onSpeakerChange = { newSpeaker ->
                    ttsController.changeSpeaker(newSpeaker)
                },
                isTtsActive = (ttsState.isPlaying || ttsState.isLoading) && ttsState.playbackSource == "READER",
                getAuthToken = { viewModel.getAuthToken() },
                bookTitle = epubBook.title
            )
        }

        if (showCustomizeToolsSheet) {
            CustomizeToolsSheet(
                hiddenTools = hiddenTools,
                toolOrder = toolOrder,
                bottomTools = bottomTools,
                onUpdate = { newHiddenSet ->
                    hiddenTools = newHiddenSet
                    saveHiddenTools(context, newHiddenSet)
                },
                onOrderUpdate = { newOrder ->
                    toolOrder = newOrder
                    saveToolOrder(context, newOrder)
                },
                onPlacementUpdate = { newBottomTools ->
                    bottomTools = newBottomTools
                    saveBottomTools(context, newBottomTools)
                },
                onDismiss = { showCustomizeToolsSheet = false }
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
                selectedDictionaryPackageName = selectedDictPackage,
                onSelectDictionaryPackage = { pkg ->
                    selectedDictPackage = pkg
                    saveExternalDictPackage(context, pkg)
                },
                selectedTranslatePackageName = selectedTranslatePackage,
                onSelectTranslatePackage = { pkg ->
                    selectedTranslatePackage = pkg
                    saveExternalTranslatePackage(context, pkg)
                },
                selectedSearchPackageName = selectedSearchPackage,
                onSelectSearchPackage = { pkg ->
                    selectedSearchPackage = pkg
                    saveExternalSearchPackage(context, pkg)
                }
            )
        }

        if (showVisualOptionsSheet) {
            VisualOptionsSheet(
                systemUiMode = systemUiMode,
                onSystemUiModeChange = {
                    systemUiMode = it
                    saveSystemUiMode(context, it)
                },
                pageInfoMode = pageInfoMode,
                onPageInfoModeChange = {
                    pageInfoMode = it
                    savePageInfoMode(context, it)
                },
                pageInfoPosition = pageInfoPosition,
                onPageInfoPositionChange = {
                    pageInfoPosition = it
                    savePageInfoPosition(context, it)
                },
                pullToTurnEnabled = pullToTurnEnabled,
                onPullToTurnChange = {
                    pullToTurnEnabled = it
                    savePullToTurn(context, it)
                },
                pullToTurnMultiplier = pullToTurnMultiplier,
                onPullToTurnMultiplierChange = {
                    pullToTurnMultiplier = it
                    savePullToTurnMultiplier(context, it)
                },
                onDismiss = { showVisualOptionsSheet = false }
            )
        }

        if (showFontSelectionSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFontSelectionSheet = false },
                sheetState = fontSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                contentWindowInsets = { WindowInsets.navigationBars }
            ) {
                FontSelectionSheetContent(
                    currentFont = currentFontFamily,
                    currentCustomFontPath = currentCustomFontPath,
                    onFontSelected = { font, path ->
                        currentFontFamily = font
                        currentCustomFontPath = path
                    },
                    customFonts = customFonts,
                    onImportFont = onImportFont,
                    onDismiss = { showFontSelectionSheet = false }
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        if (showThemePanel) {
            ReaderThemePanel(
                isVisible = true,
                currentThemeId = currentThemeId,
                globalTextureTransparency = globalTextureTransparency,
                onGlobalTextureTransparencyChange = {
                    globalTextureTransparency = it
                    saveGlobalTextureTransparency(context, it)
                },
                onThemeSelected = {
                    currentThemeId = it
                    saveReaderThemeId(context, it)
                    showThemePanel = false
                },
                onDismiss = { showThemePanel = false },
                customThemes = customThemes,
                onCustomThemesUpdated = { customThemes = it; saveCustomThemes(context, it) }
            )
        }

        if (showInsufficientCreditsDialog) {
            AlertDialog(
                onDismissRequest = { showInsufficientCreditsDialog = false },
                icon = { Icon(painterResource(id = R.drawable.crown), contentDescription = null) },
                title = { Text(stringResource(R.string.dialog_out_of_credits_title)) },
                text = { Text(stringResource(R.string.dialog_out_of_credits_desc)) },
                confirmButton = {
                    TextButton(onClick = {
                        showInsufficientCreditsDialog = false
                        onNavigateToPro()
                    }) { Text(stringResource(R.string.action_get_pro_or_add_credits)) }
                },
                dismissButton = {
                    TextButton(onClick = { showInsufficientCreditsDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        if (showPaletteManager) {
            PaletteManagerDialog(
                currentPalette = currentHighlightPalette,
                onDismiss = { showPaletteManager = false },
                onSave = { newPalette ->
                    newPalette.forEachIndexed { index, color ->
                        onUpdateHighlightPalette(index, color)
                    }
                    showPaletteManager = false
                }
            )
        }
    }
}
