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
@file:OptIn(ExperimentalSerializationApi::class) @file:Suppress("VariableNeverRead")

package com.aryan.reader.epubreader

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsEndWidth
import androidx.compose.foundation.layout.windowInsetsStartWidth
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.AiDefinitionResult
import com.aryan.reader.BannerMessage
import com.aryan.reader.BuildConfig
import com.aryan.reader.CustomTopBanner
import com.aryan.reader.RenderMode
import com.aryan.reader.SearchResult
import com.aryan.reader.SummarizationResult
import com.aryan.reader.SummaryCacheManager
import com.aryan.reader.countWords
import com.aryan.reader.data.CustomFontEntity
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.fetchAiDefinition
import com.aryan.reader.paginatedreader.BookPaginator
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
import com.aryan.reader.tts.SpeakerSamplePlayer
import com.aryan.reader.tts.TtsPlaybackManager.TtsMode
import com.aryan.reader.tts.rememberTtsController
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

private const val AUTO_SCROLL_LOCKED_KEY = "auto_scroll_locked"
private const val AUTO_SCROLL_USE_SLIDER_KEY = "auto_scroll_use_slider"
private const val AUTO_SCROLL_MIN_SPEED_KEY = "auto_scroll_min_speed"
private const val AUTO_SCROLL_MAX_SPEED_KEY = "auto_scroll_max_speed"
private const val PAGE_TURN_ANIMATION_KEY = "page_turn_animation_enabled"

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


private fun saveAutoScrollLocked(context: Context, isLocked: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(AUTO_SCROLL_LOCKED_KEY, isLocked)}
}

private fun loadAutoScrollLocked(context: Context): Boolean {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(AUTO_SCROLL_LOCKED_KEY, false)
}

private fun saveAutoScrollUseSlider(context: Context, useSlider: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(AUTO_SCROLL_USE_SLIDER_KEY, useSlider) }
}

private fun loadAutoScrollUseSlider(context: Context): Boolean {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(AUTO_SCROLL_USE_SLIDER_KEY, false)
}

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
    onImportFont: (Uri) -> Unit
) {
    EpubReaderHost(
        epubBook = epubBook,
        renderMode = renderMode,
        initialLocator = initialLocator,
        initialCfi = initialCfi,
        initialBookmarksJson = initialBookmarksJson,
        isProUser = isProUser,
        onNavigateBack = onNavigateBack,
        onSavePosition = onSavePosition,
        onBookmarksChanged = onBookmarksChanged,
        onNavigateToPro = onNavigateToPro,
        coverImagePath = coverImagePath,
        onRenderModeChange = onRenderModeChange,
        customFonts = customFonts,
        onImportFont = onImportFont
    )
}

@SuppressLint("UnusedBoxWithConstraintsScope", "ObsoleteSdkInt")
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
    isProUser: Boolean,
    onNavigateBack: () -> Unit,
    onSavePosition: (locator: Locator, cfiForWebView: String?, progress: Float) -> Unit,
    onBookmarksChanged: (bookmarksJson: String) -> Unit,
    onNavigateToPro: () -> Unit,
    coverImagePath: String?,
    onRenderModeChange: (RenderMode) -> Unit,
    customFonts: List<CustomFontEntity>,
    onImportFont: (Uri) -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val window = (view.context as? Activity)?.window
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var bannerMessage by remember { mutableStateOf<BannerMessage?>(null) }
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    val containerFocusRequester = remember { FocusRequester() }
    var isNavigatingToBookmark by remember { mutableStateOf(false) }

    var isPageSliderVisible by remember { mutableStateOf(false) }
    var sliderCurrentPage by remember { mutableFloatStateOf(0f) }
    var isFastScrubbing by remember { mutableStateOf(false) }
    val scrubDebounceJob = remember { mutableStateOf<Job?>(null) }
    val volumeScrollFocusDebounceJob = remember { mutableStateOf<Job?>(null) }
    var sliderStartPage by remember { mutableIntStateOf(0) }
    var startPageThumbnail by remember { mutableStateOf<Bitmap?>(null) }

    var showJustifyWarningDialog by remember { mutableStateOf(false) }
    var isNavigatingByToc by remember { mutableStateOf(false) }
    var currentTextAlign by remember { mutableStateOf(loadTextAlign(context)) }

    var chunkTargetOverride by remember { mutableStateOf<Int?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    var volumeScrollEnabled by remember {
        mutableStateOf(loadVolumeScrollSetting(context))
    }

    var tapToNavigateEnabled by remember {
        mutableStateOf(loadTapToNavigateSetting(context))
    }

    var isPageTurnAnimationEnabled by remember {
        mutableStateOf(loadPageTurnAnimationSetting(context))
    }

    val locatorConverter = remember(context) {
        LocatorConverter(
            bookCacheDao = BookCacheDatabase.getDatabase(context).bookCacheDao(),
            proto = ProtoBuf { serializersModule = semanticBlockModule },
            context = context
        )
    }

    val userHighlights = remember {
        mutableStateListOf<UserHighlight>().apply {
            addAll(loadHighlightsFromPrefs(context, epubBook.title))
        }
    }

    var autoScrollSpeed by remember { mutableFloatStateOf(loadAutoScrollSpeed(context)) }
    var autoScrollMinSpeed by remember { mutableFloatStateOf(loadAutoScrollMinSpeed(context)) }
    var autoScrollMaxSpeed by remember { mutableFloatStateOf(loadAutoScrollMaxSpeed(context)) }
    var isAutoScrollCollapsed by remember { mutableStateOf(false) }

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

    LaunchedEffect(userHighlights.size, userHighlights.toList()) {
        saveHighlightsToPrefs(context, epubBook.title, userHighlights)
    }

    val summaryCacheManager = remember(context) { SummaryCacheManager(context) }
    var showRecapPopup by remember { mutableStateOf(false) }
    var recapResult by remember { mutableStateOf<SummarizationResult?>(null) }
    var isRecapLoading by remember { mutableStateOf(false) }
    var recapProgressMessage by remember { mutableStateOf("") }
    var isRequestingRecapCfi by remember { mutableStateOf(false) }

    var currentRenderMode by remember(renderMode) { mutableStateOf(renderMode) }
    var chapterToLoadOnSwitch by remember { mutableStateOf<Int?>(null) }
    var lastKnownLocator by remember(initialLocator) { mutableStateOf(initialLocator) }

    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
    var skipChapterRequest by remember { mutableStateOf(false) }
    var ttsChapterIndex by remember { mutableStateOf<Int?>(null) }

    var searchHighlightTarget by remember { mutableStateOf<SearchResult?>(null) }
    var lastHighlightClickTime by remember { mutableLongStateOf(0L) }

    var webViewRefForTts by remember { mutableStateOf<WebView?>(null) }

    // Dictionary
    var showAiDefinitionPopup by remember { mutableStateOf(false) }
    var selectedTextForAi by remember { mutableStateOf<String?>(null) }
    var aiDefinitionResult by remember { mutableStateOf<AiDefinitionResult?>(null) }
    var isAiDefinitionLoading by remember { mutableStateOf(false) }

    var showSummarizationPopup by remember { mutableStateOf(false) }
    var summarizationResult by remember { mutableStateOf<SummarizationResult?>(null) }
    var isSummarizationLoading by remember { mutableStateOf(false) }
    var showDictionaryUpsellDialog by remember { mutableStateOf(false) }
    var showSummarizationUpsellDialog by remember { mutableStateOf(false) }

    val epubSearcher = remember(epubBook) { createEpubSearcher(epubBook) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showBars by remember { mutableStateOf(false) }
    val chapters = remember(epubBook.chapters) { epubBook.chapters }

    var currentChapterIndex by rememberSaveable(epubBook.title) {
        mutableIntStateOf(
            initialLocator?.chapterIndex?.coerceIn(0, chapters.size - 1) ?: 0
        )
    }

    var paginator by remember { mutableStateOf<IPaginator?>(null) }
    val paginatedPagerState = rememberPagerState(pageCount = {
        (paginator as? BookPaginator)?.totalPageCount ?: 0
    })

    val ttsController = rememberTtsController()
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

    var cfiToLoad by remember { mutableStateOf<String?>(null) }
    var fragmentToLoad by remember { mutableStateOf<String?>(null) }
    var isInitialCfiLoad by remember(initialLocator) { mutableStateOf(initialLocator != null) }
    var bookmarkPageMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var initialScrollTargetForChapter by rememberSaveable(epubBook.title) {
        mutableStateOf(if (initialLocator != null) null else ChapterScrollPosition.START)
    }

    var pullToPrevProgress by remember { mutableFloatStateOf(0f) }
    var pullToNextProgress by remember { mutableFloatStateOf(0f) }

    var activeFragmentId by remember { mutableStateOf<String?>(null) }

    val density = LocalDensity.current
    val dragThresholdPx = with(density) { DRAG_TO_CHANGE_CHAPTER_THRESHOLD_DP.toPx() }

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

    var currentFontSizeEm by remember { mutableFloatStateOf(loadFontSize(context)) }
    var currentLineHeight by remember { mutableFloatStateOf(loadLineHeight(context)) }
    var showFormatAdjustmentBars by remember { mutableStateOf(false) }

    val (initialFont, initialCustomPath) = remember { loadFontSelection(context) }
    var currentFontFamily by remember { mutableStateOf(initialFont) }
    var currentCustomFontPath by remember { mutableStateOf(initialCustomPath) }
    val activeFontFamily = remember(currentFontFamily, currentCustomFontPath) {
        getComposeFontFamily(
            font = currentFontFamily,
            customFontPath = currentCustomFontPath,
            assetManager = context.assets
        )
    }

    var showFontSelectionSheet by remember { mutableStateOf(false) }
    val fontSheetState = rememberModalBottomSheetState()

    LaunchedEffect(currentFontSizeEm, currentLineHeight, currentFontFamily, currentCustomFontPath, currentTextAlign) {
        saveReaderSettings(
            context,
            currentFontSizeEm,
            currentLineHeight,
            currentFontFamily,
            currentCustomFontPath,
            currentTextAlign
        )
    }

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(2500L)
            bannerMessage = null
        }
    }

    LaunchedEffect(initialLocator, initialCfi) {
        if (currentRenderMode == RenderMode.VERTICAL_SCROLL && initialLocator != null && cfiToLoad == null) {
            if (!initialCfi.isNullOrBlank()) {
                Timber.d("V_SCROLL: Using raw initialCfi: $initialCfi")
                if (currentChapterIndex != initialLocator.chapterIndex) {
                    currentChapterIndex = initialLocator.chapterIndex
                }
                cfiToLoad = initialCfi
            } else {
                Timber.w("V_SCROLL: initialCfi is null or blank. Position cannot be restored from locator as conversion is disabled per request.")
                isInitialCfiLoad = false
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
            bannerMessage = BannerMessage(message, isError = true)
        }
    }

    LaunchedEffect(skipChapterRequest) {
        if (skipChapterRequest) {
            skipChapterRequest = false
            if (ttsShouldStartOnChapterLoad && currentChapterIndex < chapters.size - 1) {
                Timber.d("Executing skip chapter request for continuous TTS.")
                currentChapterIndex++
            } else {
                ttsShouldStartOnChapterLoad = false
            }
        }
    }

    val searchState = rememberSearchState(scope = scope, searcher = epubSearcher)
    val speakerPlayer = remember(context, scope) { SpeakerSamplePlayer(context, scope) }

    var isAutoScrollModeActive by remember { mutableStateOf(false) }
    var isAutoScrollPlaying by remember { mutableStateOf(false) }
    var isAutoScrollTempPaused by remember { mutableStateOf(false) }
    val autoScrollResumeJob = remember { mutableStateOf<Job?>(null) }

    var isAutoScrollLocked by remember { mutableStateOf(loadAutoScrollLocked(context)) }
    var autoScrollUseSlider by remember { mutableStateOf(loadAutoScrollUseSlider(context)) }

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

    fun startTts() {
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
                                ttsMode = TtsMode.BASE.name,
                                playbackSource = "READER"
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

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    val isDarkTheme = isSystemInDarkTheme()

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
            initialScrollTargetForChapter = ChapterScrollPosition.START
            cfiToLoad = null
            currentScrollYPosition = 0
            currentChapterIndex = nextIndex
        },
        onToggleTtsStartOnLoad = { shouldStart -> ttsShouldStartOnChapterLoad = shouldStart },
        userStoppedTts = userStoppedTts,
        scope = scope
    )

    TtsHighlightHandler(
        ttsState = ttsState,
        currentRenderMode = currentRenderMode,
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

    if (epubBook.extractionBasePath.isBlank() || !File(epubBook.extractionBasePath).exists()) {
        Timber.e("Extraction base path is blank or does not exist: ${epubBook.extractionBasePath}"
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Error: Book content not found. Path: ${epubBook.extractionBasePath}",
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }

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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, webViewRefForTts) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                Timber.d("ON_PAUSE detected. Requesting final CFI for robust save.")
                webViewRefForTts?.evaluateJavascript("javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());", null)
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
        showRecapPopup = true
        isRecapLoading = true
        recapResult = null
        recapProgressMessage = "Checking past chapters..."

        scope.launch {
            executeRecapLogic(
                epubBook = epubBook,
                chapterIndex = chapterIdx,
                characterLimit = charLimit,
                summaryCacheManager = summaryCacheManager,
                paginator = paginator,
                onProgressUpdate = { recapProgressMessage = it },
                onResultUpdate = { chunk ->
                    isRecapLoading = false // Start showing content
                    val current = recapResult?.summary ?: ""
                    recapResult = SummarizationResult(summary = current + chunk)
                },
                onError = { error ->
                    recapResult = SummarizationResult(error = error)
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
        loadUpToChunkIndex = result.startChunkIndex

        if (chunkTargetOverride != null) {
            chunkTargetOverride = null
        }
        if (isInitialCfiLoad) {
            isInitialCfiLoad = false
        }

        loadedChunkCount = 1
        topVisibleChunkIndex = 0
    }

    EpubReaderSystemUiController(
        window = window,
        view = view,
        showBars = showBars,
        initialIsAppearanceLightStatusBars = initialIsAppearanceLightStatusBars,
        initialSystemBarsBehavior = initialSystemBarsBehavior
    )

    var isPagerInitialized by remember(initialLocator) { mutableStateOf(initialLocator == null) }
    LaunchedEffect(paginator, currentRenderMode, isPagerInitialized) {
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

    val currentChapterInPaginatedMode by remember {
        derivedStateOf {
            if (currentRenderMode == RenderMode.PAGINATED) {
                (paginator as? BookPaginator)?.findChapterIndexForPage(paginatedPagerState.currentPage)
            } else {
                null
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
                initialScrollTargetForChapter = ChapterScrollPosition.START
                currentChapterIndex = chapterIdx
                searchHighlightTarget = result
                loadUpToChunkIndex = chunkIdx
            },
            onVerticalScrollToResult = { _ ->
                searchHighlightTarget = null
            },
            onPaginatedScrollToPage = { pageIdx ->
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
                tableOfContents = epubBook.tableOfContents, // Pass TOC
                activeFragmentId = activeFragmentId,
                bookmarks = bookmarks,
                userHighlights = userHighlights,
                currentChapterIndex = currentChapterIndex,
                currentChapterInPaginatedMode = currentChapterInPaginatedMode,
                renderMode = currentRenderMode,
                onNavigateToTocEntry = { entry ->
                    scope.launch {
                        drawerState.close()
                        val targetChapterIndex = chapters.indexOfFirst { it.absPath == entry.absolutePath }

                        if (targetChapterIndex != -1) {
                            if (currentRenderMode == RenderMode.VERTICAL_SCROLL) {
                                fragmentToLoad = entry.fragmentId
                                if (targetChapterIndex != currentChapterIndex) {
                                    initialScrollTargetForChapter = null
                                    currentScrollYPosition = 0
                                    currentChapterIndex = targetChapterIndex
                                } else {
                                    if (entry.fragmentId != null) {
                                        webViewRefForTts?.evaluateJavascript(
                                            "javascript:var el = document.getElementById('${entry.fragmentId}'); if(el) { el.scrollIntoView(); }",
                                            null
                                        )
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
                                    initialScrollTargetForChapter = ChapterScrollPosition.START
                                    currentScrollYPosition = 0
                                    currentChapterIndex = index
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

                                val directChunkIndex = try {
                                    val parts = bookmark.cfi.split('/').mapNotNull { it.toIntOrNull() }
                                    if (parts.isNotEmpty()) {
                                        val firstIndex = parts[0]
                                        (firstIndex - 2) / 2
                                    } else null
                                } catch (_: Exception) {
                                    null
                                }

                                val locator = if (directChunkIndex == null) {
                                    locatorConverter.getLocatorFromCfi(epubBook, bookmark.chapterIndex, bookmark.cfi)
                                } else {
                                    null
                                }

                                val targetChunk = directChunkIndex ?: locator?.let { it.blockIndex / 20 }

                                if (bookmark.chapterIndex != currentChapterIndex) {
                                    chunkTargetOverride = if (targetChunk != null && targetChunk >= 0) {
                                        targetChunk
                                    } else {
                                        0
                                    }
                                    currentChapterIndex = bookmark.chapterIndex
                                }
                                else {
                                    if (targetChunk != null && targetChunk >= 0) {
                                        isNavigatingToBookmark = true

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
                                            // Even if loadedChunkCount is high enough in Kotlin state,
                                            // ensure the specific chunk for the bookmark is actually in the DOM.
                                            // (Sometimes rapid jumps might leave gaps if logic was loose)
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
                                            if (isNavigatingToBookmark) {
                                                isNavigatingToBookmark = false
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
                                } else {
                                    Timber.w("P-Mode Click: Failed to convert CFI to Locator. Using old findPageForCfi as a fallback.")
                                    paginator?.findPageForCfi(bookmark.chapterIndex, bookmark.cfi) { pageIndex ->
                                        scope.launch {
                                            paginatedPagerState.scrollToPage(pageIndex)
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
                                    chunkTargetOverride = targetChunk ?: 0
                                    currentChapterIndex = highlight.chapterIndex
                                } else {
                                    if (targetChunk != null && targetChunk >= loadedChunkCount) {
                                        loadUpToChunkIndex = targetChunk
                                    } else {
                                        webViewRefForTts?.evaluateJavascript(
                                            "javascript:window.scrollToCfi('${escapeJsString(highlight.cfi)}');",
                                            null
                                        )
                                    }
                                }
                            }
                            RenderMode.PAGINATED -> {
                                val locator = locatorConverter.getLocatorFromCfi(epubBook, highlight.chapterIndex, highlight.cfi)
                                if (locator != null) {
                                    val pageIndex = (paginator as? BookPaginator)?.findPageForLocator(locator)
                                    if (pageIndex != null) paginatedPagerState.scrollToPage(pageIndex)
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
                        val cfiParts = highlightToDelete.cfi.split("|")

                        cfiParts.forEach { partCfi ->
                            val jsCommand = "javascript:window.HighlightBridgeHelper.removeHighlightByCfi('${escapeJsString(partCfi)}', '$cssClass');"
                            Timber.d("Executing JS removal for part: $partCfi")
                            webViewRefForTts?.evaluateJavascript(jsCommand, null)
                        }
                    }
                }
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

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.statusBars,
        ) { scaffoldPaddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPaddingValues)
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
                                initialScrollTargetForChapter = target
                                if (target == ChapterScrollPosition.START) currentScrollYPosition = 0
                                currentChapterIndex += offset
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = if (showBars || showFormatAdjustmentBars) 0.dp else PAGE_INFO_BAR_HEIGHT)
                                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                                .testTag("ReaderContainer")
                        ) {
                            if (chapters.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No chapters available for this book.")
                                }
                            } else {
                                AnimatedContent(
                                    targetState = currentChapterIndex,
                                    transitionSpec = {
                                        if (targetState > initialState) {
                                            (slideInVertically { height -> height } + fadeIn())
                                                .togetherWith(slideOutVertically { height -> -height } + fadeOut())
                                        } else {
                                            (slideInVertically { height -> -height } + fadeIn())
                                                .togetherWith(slideOutVertically { height -> height } + fadeOut())
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
                                        val initialContentToLoad =
                                            remember(loadUpToChunkIndex, chapterChunks) {
                                                val startIdx = maxOf(0, loadUpToChunkIndex - 1)
                                                val endIdx = minOf(chapterChunks.lastIndex, loadUpToChunkIndex + 1)

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

                                        LaunchedEffect(isWebViewReady) {
                                            val target = searchHighlightTarget
                                            Timber.d("Effect(isWebViewReady=$isWebViewReady) triggered for chapter $targetChapterIndex. Target is: $target"
                                            )

                                            if (isWebViewReady && target != null && target.locationInSource == targetChapterIndex) {
                                                Timber.d("Highlighting condition met. Highlighting now."
                                                )
                                                delay(200)
                                                val webView = webViewRefForTts
                                                if (webView != null) {
                                                    val escapedQuery = escapeJsString(target.query)
                                                    val js =
                                                        "javascript:window.highlightAllOccurrences('${escapedQuery}'); window.scrollToOccurrence(${target.occurrenceIndexInLocation});"
                                                    Timber.d("Executing search highlight/scroll JS: $js"
                                                    )
                                                    webView.evaluateJavascript(js) { result ->
                                                        Timber.d("JS highlight/scroll result: $result"
                                                        )
                                                    }
                                                    searchHighlightTarget = null
                                                } else {
                                                    Timber.w("Highlight failed: WebView was null even after ready signal."
                                                    )
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

                                        @Suppress("KotlinConstantConditions",
                                            "ControlFlowWithEmptyBody"
                                        )
                                        ChapterWebView(
                                            key = chapterKeyForWebView,
                                            chapterTitle = chapterToRender.title,
                                            isDarkTheme = isDarkTheme,
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
                                                val existingIndex = userHighlights.indexOfFirst { it.cfi == cfi }

                                                if (existingIndex != -1) {
                                                    val existing = userHighlights[existingIndex]
                                                    userHighlights[existingIndex] = existing.copy(color = color, text = text)
                                                    Timber.d("Kotlin: Updated existing highlight at index $existingIndex")
                                                } else {
                                                    val highlight = UserHighlight(
                                                        cfi = cfi,
                                                        text = text,
                                                        color = color,
                                                        chapterIndex = currentChapterIndex
                                                    )
                                                    userHighlights.add(highlight)
                                                    Timber.d("Kotlin: Added new highlight")
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

                                                if (ttsShouldStartOnChapterLoad) {
                                                    Timber.d("Auto-starting TTS for new chapter ($targetChapterIndex).")
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
                                                if (System.currentTimeMillis() - lastHighlightClickTime > 500) {
                                                    focusManager.clearFocus()
                                                    if (volumeScrollEnabled && !searchState.isSearchActive) {
                                                        containerFocusRequester.requestFocus()
                                                    }
                                                    if (showBars || showFormatAdjustmentBars) {
                                                        showBars = false
                                                        showFormatAdjustmentBars = false
                                                        Timber.d("Chapter tapped, hiding all bars.")
                                                    } else {
                                                        showBars = true
                                                        Timber.d("Chapter tapped, showing main bars.")
                                                    }
                                                }
                                            },
                                            onPotentialScroll = {
                                                if (showBars) {
                                                    showBars = false
                                                    showFormatAdjustmentBars = false
                                                    Timber.d("Scroll/Drag detected, hiding bars.")
                                                }
                                                if (isAutoScrollModeActive && isAutoScrollPlaying) {
                                                    triggerAutoScrollTempPause(1000L)
                                                }
                                            },
                                            onAutoScrollChapterEnd = {
                                                Timber.d("Screen: onAutoScrollChapterEnd triggered. Current Index: $currentChapterIndex")

                                                scope.launch {
                                                    if (currentChapterIndex < chapters.size - 1) {
                                                        Timber.d("Screen: Moving to next chapter (${currentChapterIndex + 1}).")
                                                        initialScrollTargetForChapter = ChapterScrollPosition.START
                                                        currentScrollYPosition = 0
                                                        currentChapterIndex++
                                                        isAutoScrollPlaying = true
                                                    } else {
                                                        Timber.d("Screen: Reached end of book. Stopping auto-scroll.")
                                                        isAutoScrollPlaying = false
                                                    }
                                                }
                                            },
                                            onOverScrollTop = { dragAmount ->
                                                if (targetChapterIndex > 0) {
                                                    pullToPrevProgress =
                                                        dragAmount / dragThresholdPx
                                                }
                                            },
                                            onOverScrollBottom = { dragAmount ->
                                                if (targetChapterIndex < chapters.size - 1) {
                                                    pullToNextProgress =
                                                        dragAmount / dragThresholdPx
                                                }
                                            },
                                            onReleaseOverScrollTop = {
                                                if (targetChapterIndex > 0 && pullToPrevProgress >= 1.0f) {
                                                    Timber.d("Swipe-up triggered. Saving position before changing to previous chapter."
                                                    )
                                                    webViewRefForTts?.evaluateJavascript(
                                                        "javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());",
                                                        null
                                                    )
                                                    scope.launch {
                                                        delay(50)
                                                        initialScrollTargetForChapter =
                                                            ChapterScrollPosition.END
                                                        currentChapterIndex--
                                                        if (showBars) showBars = false
                                                        Timber.d("Changed to previous chapter: $currentChapterIndex, will scroll to END"
                                                        )
                                                    }
                                                }
                                                pullToPrevProgress = 0f
                                            },
                                            onReleaseOverScrollBottom = {
                                                if (targetChapterIndex < chapters.size - 1 && pullToNextProgress >= 1.0f) {
                                                    Timber.d("Swipe-down triggered. Saving position before changing to next chapter."
                                                    )
                                                    webViewRefForTts?.evaluateJavascript(
                                                        "javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());",
                                                        null
                                                    )
                                                    scope.launch {
                                                        delay(50)
                                                        initialScrollTargetForChapter =
                                                            ChapterScrollPosition.START
                                                        currentScrollYPosition = 0
                                                        currentChapterIndex++
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
                                            currentFontFamily = currentFontFamily,
                                            customFontPath = currentCustomFontPath,
                                            currentTextAlign = currentTextAlign,
                                            onHighlightClicked = {
                                                lastHighlightClickTime = System.currentTimeMillis()
                                                showBars = false
                                                showFormatAdjustmentBars = false
                                                Timber.d("Highlight clicked - Forcing bars hidden")
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
                                                isNavigatingToBookmark = false
                                            },
                                            ttsScope = scope,
                                            onTtsTextReady = { jsonString ->
                                                scope.launch {
                                                    Timber.d("Vertical: onTtsTextReady received JSON. Length: ${jsonString.length}")
                                                    val ttsChunks =
                                                        mutableListOf<TtsChunk>()
                                                    try {
                                                        val jsonArray = JSONArray(jsonString)
                                                        Timber.d("Vertical: Parsed JSON Array. Items: ${jsonArray.length()}")
                                                        for (i in 0 until jsonArray.length()) {
                                                            val jsonObject =
                                                                jsonArray.getJSONObject(i)
                                                            val text = jsonObject.getString("text")
                                                            val cfiJsonString =
                                                                jsonObject.getString("cfi")
                                                            val cfiJsonObject =
                                                                JSONObject(cfiJsonString)
                                                            val cfi = cfiJsonObject.getString("cfi")

                                                            val subChunks =
                                                                splitTextIntoChunks(text)
                                                            var currentOffset = 0
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

                                                    if (ttsChunks.isNotEmpty()) {
                                                        ttsShouldStartOnChapterLoad = false
                                                        val chapterTitle =
                                                            chapters.getOrNull(currentChapterIndex)?.title
                                                        val coverUriString = coverImagePath?.let {
                                                            Uri.fromFile(File(it)).toString()
                                                        }
                                                        ttsChapterIndex = currentChapterIndex
                                                        ttsController.start(
                                                            chunks = ttsChunks,
                                                            bookTitle = epubBook.title,
                                                            chapterTitle = chapterTitle,
                                                            coverImageUri = coverUriString,
                                                            ttsMode = TtsMode.BASE.name,
                                                            playbackSource = "READER"
                                                        )
                                                    } else {
                                                        Timber.w("No TTS chunks were created from JSON, not starting TTS."
                                                        )
                                                        if (ttsShouldStartOnChapterLoad) {
                                                            Timber.d("Empty chapter detected during continuous TTS. Requesting skip."
                                                            )
                                                            skipChapterRequest = true
                                                        }
                                                    }
                                                }
                                            },
                                            onWordSelectedForAiDefinition = { text ->
                                                val wordCount = countWords(text)
                                                if (isProUser || wordCount <= 1) {
                                                    Timber.d("Text selected for AI definition: $text"
                                                    )
                                                    selectedTextForAi = text
                                                    showAiDefinitionPopup = true
                                                    scope.launch {
                                                        isAiDefinitionLoading = true
                                                        aiDefinitionResult = null
                                                        fetchAiDefinition(
                                                            text = text,
                                                            onUpdate = { chunk ->
                                                                val currentDefinition = aiDefinitionResult?.definition ?: ""
                                                                aiDefinitionResult = AiDefinitionResult(definition = currentDefinition + chunk)
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
                                            },
                                            onContentReadyForSummarization = { content ->
                                                Timber.d("Content received for summarization")
                                                scope.launch {
                                                    val chapterIndexToSave = currentChapterIndex
                                                    val bookTitleToSave = epubBook.title
                                                    val finalSummaryBuilder = StringBuilder()

                                                    summarizeBookContent(
                                                        content = content,
                                                        onUpdate = { chunk ->
                                                            finalSummaryBuilder.append(chunk)
                                                            val currentSummary = summarizationResult?.summary ?: ""
                                                            summarizationResult = SummarizationResult(summary = currentSummary + chunk)
                                                        },
                                                        onError = { error ->
                                                            summarizationResult = SummarizationResult(error = error)
                                                        },
                                                        onFinish = {
                                                            isSummarizationLoading = false
                                                            val fullSummary = finalSummaryBuilder.toString()
                                                            if (fullSummary.isNotBlank()) {
                                                                summaryCacheManager.saveSummary(bookTitleToSave, chapterIndexToSave, fullSummary)
                                                            }
                                                        }
                                                    )
                                                }
                                            },
                                            isProUser = isProUser,
                                            isOss = BuildConfig.FLAVOR == "oss",
                                            onShowDictionaryUpsellDialog = {
                                                showDictionaryUpsellDialog = true
                                            },
                                            onCfiGenerated = { cfi ->
                                                if (cfi.isBlank() || !cfi.startsWith('/')) {
                                                    Timber.w("onCfiGenerated received an invalid CFI, aborting save: '$cfi'"
                                                    )
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
                                                        lastKnownLocator = locator

                                                        // Calculate progress (Logic moved out of `val progress` block for scope visibility)
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
                                                        Timber.d("CFI received for saving: $cfi. Progress: $progress%"
                                                        )
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

                                if (currentChapterIndex > 0) {
                                    ChapterChangeIndicator(
                                        text = "Release for Previous Chapter",
                                        progress = pullToPrevProgress,
                                        isPullingDown = true,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 8.dp)
                                    )
                                }

                                if (currentChapterIndex < chapters.size - 1) {
                                    ChapterChangeIndicator(
                                        text = "Release for Next Chapter",
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
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = PAGE_INFO_BAR_HEIGHT)
                                .testTag("ReaderContainer")
                        ) {
                            @Suppress("KotlinConstantConditions")
                            PaginatedReaderScreen(
                                book = epubBook,
                                isDarkTheme = isDarkTheme,
                                pagerState = paginatedPagerState,
                                searchQuery = searchState.searchQuery,
                                fontSizeMultiplier = currentFontSizeEm,
                                lineHeightMultiplier = currentLineHeight,
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
                                    val wordCount = countWords(text)
                                    if (isProUser || wordCount <= 1) {
                                        Timber.d("Text selected for AI definition: $text"
                                        )
                                        selectedTextForAi = text
                                        showAiDefinitionPopup = true
                                        scope.launch {
                                            isAiDefinitionLoading = true
                                            aiDefinitionResult = null
                                            fetchAiDefinition(
                                                text = text,
                                                onUpdate = { chunk ->
                                                    val currentDefinition = aiDefinitionResult?.definition ?: ""
                                                    aiDefinitionResult = AiDefinitionResult(definition = currentDefinition + chunk)
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
                                },
                                userHighlights = userHighlights.filter { it.chapterIndex == (currentChapterInPaginatedMode ?: -1) },
                                onHighlightCreated = { cfi, text, colorId ->
                                    Timber.d("EpubReaderScreen: onHighlightCreated. CFI: $cfi")
                                    val color = HighlightColor.entries.find { it.id == colorId } ?: HighlightColor.YELLOW
                                    processAndAddHighlight(
                                        newCfi = cfi,
                                        newText = text,
                                        newColor = color,
                                        chapterIndex = currentChapterInPaginatedMode ?: 0,
                                        currentList = userHighlights
                                    )
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
                    visible = renderMode == RenderMode.VERTICAL_SCROLL && !showBars,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PAGE_INFO_BAR_HEIGHT)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                            .padding(bottom = bottomPadding)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val chapterTitle =
                            chapters.getOrNull(currentChapterIndex)?.title?.take(30)?.trim()
                                ?: "Chapter"
                        Text(
                            text = "$chapterTitle ($currentPageInChapter/$totalPagesInCurrentChapter)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp)
                        )

                        if (totalBookLengthChars > 0) {
                            Text(
                                text = "%.1f%%".format(currentBookProgress),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }

                // Page Info Bar (Paginated)
                AnimatedVisibility(
                    visible = renderMode == RenderMode.PAGINATED && paginator != null && !showBars && paginatedPagerState.pageCount > 0,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PAGE_INFO_BAR_HEIGHT)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                            .padding(bottom = bottomPadding)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.align(Alignment.CenterEnd)
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
                    onNavigateBack = { triggerSaveAndExit() },
                    onCloseSearch = {
                        searchState.isSearchActive = false
                        searchState.onQueryChange("")
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        containerFocusRequester.requestFocus()
                    },
                    onChangeRenderMode = { newMode ->
                        if (newMode != currentRenderMode) {
                            if (newMode == RenderMode.PAGINATED) {
                                isSwitchingToPaginated = true
                                webViewRefForTts?.evaluateJavascript("javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());", null)
                            } else {
                                scope.launch {
                                    lastKnownLocator?.let { locator ->
                                        val cfi = locatorConverter.getCfiFromLocator(epubBook.title, locator)
                                        if (cfi != null) {
                                            val targetChunk = locator.blockIndex / 20
                                            chunkTargetOverride = targetChunk
                                            if (currentChapterIndex != locator.chapterIndex) {
                                                currentChapterIndex = locator.chapterIndex
                                            }
                                            cfiToLoad = cfi
                                        } else {
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
                        showBars = false
                        showBars = true
                    },
                    searchFocusRequester = searchFocusRequester,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                val autoScrollPadding by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (showBars) (bottomPadding + 45.dp + 16.dp) else 32.dp,
                    label = "AutoScrollPadding"
                )

                val alignmentBias by animateFloatAsState(
                    targetValue = if (isAutoScrollCollapsed) 1f else 0f,
                    label = "AutoScrollAlignAnimation"
                )

                val isAutoScrollControlsVisible = isAutoScrollModeActive && (!isAutoScrollLocked || showBars)

                AnimatedVisibility(
                    visible = isAutoScrollControlsVisible,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
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
                        onSpeedChange = {
                            autoScrollSpeed = it
                            saveAutoScrollSpeed(context, it)
                        },
                        onMinSpeedChange = { newMin ->
                            autoScrollMinSpeed = newMin
                            saveAutoScrollMinSpeed(context, newMin)
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
                        },
                        onMaxSpeedChange = { newMax ->
                            autoScrollMaxSpeed = newMax
                            saveAutoScrollMaxSpeed(context, newMax)
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
                        },
                        onClose = {
                            isAutoScrollModeActive = false
                            isAutoScrollPlaying = false
                            showBars = true
                        },
                        isCollapsed = isAutoScrollCollapsed,
                        onCollapseChange = { isAutoScrollCollapsed = it },
                        isLocked = isAutoScrollLocked,
                        onLockToggle = {
                            isAutoScrollLocked = !isAutoScrollLocked
                            saveAutoScrollLocked(context, isAutoScrollLocked)
                        },
                        useSlider = autoScrollUseSlider,
                        onInputModeToggle = {
                            autoScrollUseSlider = !autoScrollUseSlider
                            saveAutoScrollUseSlider(context, autoScrollUseSlider)
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
                    onSummarize = {
                        if (isProUser) {
                            showSummarizationPopup = true
                            isSummarizationLoading = true
                            summarizationResult = null
                            when (currentRenderMode) {
                                RenderMode.VERTICAL_SCROLL -> {
                                    webViewRefForTts?.evaluateJavascript("javascript:AiBridgeHelper.extractAndRelayTextForSummarization();") { result ->
                                        Timber.d("JS summarization request: $result")
                                    } ?: run {
                                        isSummarizationLoading = false
                                        summarizationResult = SummarizationResult(error = "WebView not available.")
                                    }
                                }
                                RenderMode.PAGINATED -> {
                                    scope.launch {
                                        val currentPage = paginatedPagerState.currentPage
                                        val chapterIndex = (paginator as? BookPaginator)?.findChapterIndexForPage(currentPage)
                                        if (chapterIndex != null) {
                                            val text = paginator?.getPlainTextForChapter(chapterIndex)
                                            if (!text.isNullOrBlank()) {
                                                summarizeBookContent(
                                                    content = text,
                                                    onUpdate = { chunk ->
                                                        val currentSummary =
                                                            summarizationResult?.summary
                                                                ?: ""
                                                        summarizationResult =
                                                            SummarizationResult(
                                                                summary = currentSummary + chunk
                                                            )
                                                    },
                                                    onError = { error ->
                                                        summarizationResult =
                                                            SummarizationResult(
                                                                error = error
                                                            )
                                                    },
                                                    onFinish = {
                                                        isSummarizationLoading =
                                                            false
                                                    }
                                                )
                                            } else {
                                                summarizationResult = SummarizationResult(error = "Could not get chapter content.")
                                                isSummarizationLoading = false
                                            }
                                        } else {
                                            summarizationResult = SummarizationResult(error = "Could not determine current chapter.")
                                            isSummarizationLoading = false
                                        }
                                    }
                                }
                            }
                        } else {
                            showSummarizationUpsellDialog = true
                        }
                    },
                    onRecap = {
                        when (currentRenderMode) {
                            RenderMode.VERTICAL_SCROLL -> {
                                isRequestingRecapCfi = true
                                webViewRefForTts?.evaluateJavascript("javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());", null)
                            }
                            RenderMode.PAGINATED -> {
                                val bookPaginator = paginator as? BookPaginator
                                val chapterIndex = currentChapterInPaginatedMode

                                if (bookPaginator != null && chapterIndex != null) {
                                    val startPage = bookPaginator.chapterStartPageIndices[chapterIndex] ?: 0
                                    val currentPageInChapter = paginatedPagerState.currentPage - startPage

                                    val charsScrolled = bookPaginator.getCharactersScrolledInChapter(chapterIndex, currentPageInChapter)

                                    Timber.d("Paginated Mode: Chapter $chapterIndex, PageInChapter $currentPageInChapter")
                                    Timber.d("Paginated Mode: Chars Scrolled (Limit): $charsScrolled")

                                    runRecap(chapterIndex, charsScrolled.toInt())
                                } else {
                                    bannerMessage = BannerMessage("Wait for book to load fully.", isError = true)
                                }
                            }
                        }
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
                    onPlayPauseTts = {
                        if (ttsState.isPlaying) ttsController.pause() else ttsController.resume()
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
                        currentFontFamily = ReaderFont.ORIGINAL
                        currentCustomFontPath = null
                        currentTextAlign = ReaderTextAlign.DEFAULT
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding)
                )

                EpubReaderAiOverlays(
                    showSummarizationPopup = showSummarizationPopup,
                    summarizationResult = summarizationResult,
                    isSummarizationLoading = isSummarizationLoading,
                    onDismissSummarization = {
                        showSummarizationPopup = false
                        isSummarizationLoading = false
                        summarizationResult = null
                    },
                    showSummarizationUpsellDialog = showSummarizationUpsellDialog,
                    onDismissSummarizationUpsell = { showSummarizationUpsellDialog = false },

                    showRecapPopup = showRecapPopup,
                    recapResult = recapResult,
                    isRecapLoading = isRecapLoading,
                    onDismissRecap = {
                        showRecapPopup = false
                        isRecapLoading = false
                        recapResult = null
                    },

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
                    isTtsSessionActive = isTtsSessionActive
                )

                if (isNavigatingToBookmark) {
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
                                text = "Navigating to bookmark...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                if (showPermissionRationaleDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermissionRationaleDialog = false },
                        title = { Text("Permission Required") },
                        text = { Text("To show playback controls while the app is in the background, please grant the notification permission.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showPermissionRationaleDialog = false
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            ) {
                                Text("Continue")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showPermissionRationaleDialog = false
                                    startTts()
                                }
                            ) {
                                Text("Not now")
                            }
                        }
                    )
                }

                if (showJustifyWarningDialog) {
                    AlertDialog(
                        onDismissRequest = { showJustifyWarningDialog = false },
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        title = { Text("Justified Text Limitation") },
                        text = { Text("Using Justified alignment in Paginated Mode may cause text selection and highlights to be inaccurate due to layout limitations.") },
                        confirmButton = {
                            TextButton(onClick = { showJustifyWarningDialog = false }) {
                                Text("I Understand")
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
                                "Navigating to chapter...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
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
    }
}