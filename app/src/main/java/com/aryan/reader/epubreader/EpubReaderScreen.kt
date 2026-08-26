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

import kotlinx.serialization.ExperimentalSerializationApi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.shared.AiDefinitionResult
import com.aryan.reader.BuildConfig
import com.aryan.reader.copyPlainTextToClipboard
import com.aryan.reader.BookWordReplacementsSheet
import com.aryan.reader.BuiltInThemes
import com.aryan.reader.MainViewModel
import com.aryan.reader.R
import com.aryan.reader.ReaderBrightnessEffect
import com.aryan.reader.ReaderFileInfoDialogs
import com.aryan.reader.ReaderBrightnessSheet
import com.aryan.reader.ReaderScreenOrientationEffect
import com.aryan.reader.ReaderScreenOrientationSheet
import com.aryan.reader.ReaderThemePanel
import com.aryan.reader.RenderMode
import com.aryan.reader.shared.SearchResult
import com.aryan.reader.shared.SummarizationResult
import com.aryan.reader.SummaryCacheManager
import com.aryan.reader.TtsSettingsSheet
import com.aryan.reader.TtsWordReplacementsSheet
import com.aryan.reader.areReaderAiFeaturesEnabled
import com.aryan.reader.countWords
import com.aryan.reader.isByokCloudTtsAvailable
import com.aryan.reader.data.CustomFontEntity
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.hasReadableExtractedContent
import com.aryan.reader.epub.plainTextCharacterCount
import com.aryan.reader.fetchAiDefinition
import com.aryan.reader.loadCustomThemes
import com.aryan.reader.loadGlobalTextureTransparency
import com.aryan.reader.loadBookReplacementPreferences
import com.aryan.reader.loadReaderBrightnessSettings
import com.aryan.reader.loadReaderScreenOrientationMode
import com.aryan.reader.loadEpubRightToLeftPagination
import com.aryan.reader.loadReaderThemeId
import com.aryan.reader.loadReaderSliderToggled
import com.aryan.reader.loadReaderTextureBitmap
import com.aryan.reader.loadTtsReplacementPreferences
import com.aryan.reader.readerSliderBookmarkPosition
import com.aryan.reader.readerSliderChromeColors
import com.aryan.reader.readerSliderToggleState
import com.aryan.reader.paginatedreader.CssParser
import com.aryan.reader.paginatedreader.BookPaginator
import com.aryan.reader.paginatedreader.HeaderBlock
import com.aryan.reader.paginatedreader.IPaginator
import com.aryan.reader.paginatedreader.ListItemBlock
import com.aryan.reader.paginatedreader.Locator
import com.aryan.reader.paginatedreader.LocatorConverter
import com.aryan.reader.paginatedreader.NativeVerticalLocation
import com.aryan.reader.paginatedreader.NativeVerticalReaderScreen
import com.aryan.reader.paginatedreader.PaginatedReaderScreen
import com.aryan.reader.paginatedreader.ParagraphBlock
import com.aryan.reader.paginatedreader.QuoteBlock
import com.aryan.reader.paginatedreader.TextContentBlock
import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.paginatedreader.buildEpubFontFaceCss
import com.aryan.reader.paginatedreader.data.BookCacheDatabase
import com.aryan.reader.paginatedreader.locatorForPersistence
import com.aryan.reader.paginatedreader.nativeVerticalChapterPageInfo
import com.aryan.reader.paginatedreader.nativeVerticalProgressForCompatPage
import com.aryan.reader.paginatedreader.semanticBlockModule
import com.aryan.reader.rememberSearchState
import com.aryan.reader.saveCustomThemes
import com.aryan.reader.saveGlobalTextureTransparency
import com.aryan.reader.saveBookReplacementPreferences
import com.aryan.reader.saveReaderBrightnessSettings
import com.aryan.reader.saveReaderScreenOrientationMode
import com.aryan.reader.saveEpubRightToLeftPagination
import com.aryan.reader.saveReaderThemeId
import com.aryan.reader.saveReaderSliderToggled
import com.aryan.reader.saveTtsReplacementPreferences
import com.aryan.reader.shouldRenderReaderSlider
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.ReaderLocator as SharedReaderLocator
import com.aryan.reader.shared.EpubBlockPosition
import com.aryan.reader.shared.EpubVisibleTextRange
import com.aryan.reader.shared.findEpubBookmarkForLocation
import com.aryan.reader.tts.SpeakerSamplePlayer
import com.aryan.reader.tts.TtsPlaybackManager
import com.aryan.reader.tts.loadTtsMode
import com.aryan.reader.tts.loadReaderTtsOverlaySize
import com.aryan.reader.tts.readerTtsOverlayAlignmentBias
import com.aryan.reader.tts.saveReaderTtsOverlaySize
import com.aryan.reader.tts.splitTextIntoChunks
import com.aryan.reader.withTtsReplacements
import com.aryan.reader.shared.reader.ReaderJumpHistory
import com.aryan.reader.shared.reader.mobileEpubChapterScrollFraction
import com.aryan.reader.shared.reader.mobileEpubCharacterProgress
import com.aryan.reader.shared.reader.mobileEpubCharacterDisplayProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.protobuf.ProtoBuf
import org.jsoup.Jsoup
import com.aryan.reader.shared.ui.SharedMobileReaderDrawer
import com.aryan.reader.shared.ui.SharedMobileReaderScaffold
import com.aryan.reader.shared.ui.SharedMobileReaderRecoveryGate
import com.aryan.reader.shared.ui.rememberReaderMotionPolicy
import com.aryan.reader.shared.reader.MobileEpubReaderBackAction
import com.aryan.reader.shared.reader.selectMobileEpubReaderBackAction
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
private const val HIDDEN_TOOLS_DEFAULTS_VERSION_KEY = "reader_hidden_tools_defaults_version"
private const val HIDDEN_TOOLS_DEFAULTS_VERSION = 2
private const val TTS_LOCATE_REASON_INITIAL_RESTORE = "initial_restore"
private const val TTS_LOCATE_REASON_LIFECYCLE_RESUME = "lifecycle_resume"
private const val TTS_LOCATE_REASON_OVERLAY = "overlay"

private const val TAG_LINK_NAV = "LINK_NAV"
private const val TAG_VERTICAL_JITTER = "EpubVerticalJitter"
private const val TAG_STABLE_PAGE_NAV = "StablePageNav"
private const val TAG_PAGINATED_HIGHLIGHT_DIAG = "PaginatedHighlightDiag"


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
    onImportFonts: (List<Uri>) -> Unit,
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

        SharedMobileReaderRecoveryGate(
            message = message,
            recovering = isRecovering,
            isError = uiState.errorMessage != null,
        )
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
        onImportFonts = onImportFonts,
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
        stableBookId = uiState.selectedBookId,
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
    onImportFonts: (List<Uri>) -> Unit,
    onToggleReflow: ((Int) -> Unit)? = null,
    onDeleteReflow: (() -> Unit)? = null,
    stableBookId: String? = null,
    viewModel: MainViewModel
) {
    val view = LocalView.current
    val context = LocalContext.current
    val motionPolicy = rememberReaderMotionPolicy()
    val uiState by viewModel.uiState.collectAsState()
    val window = (view.context as? Activity)?.window
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var readerBrightnessSettings by remember { mutableStateOf(loadReaderBrightnessSettings(context)) }
    var showBrightnessSheet by remember { mutableStateOf(false) }
    ReaderBrightnessEffect(window, readerBrightnessSettings)

    val updateReaderBrightness: (com.aryan.reader.ReaderBrightnessSettings) -> Unit = { settings ->
        readerBrightnessSettings = settings
        saveReaderBrightnessSettings(context, settings)
    }

    fun showBanner(message: String, isError: Boolean = false, isPersistent: Boolean = false) {
        viewModel.showBanner(message, isError, isPersistent)
    }
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
    val navigation = rememberEpubReaderNavigationState()

    val snackbarHostState = remember { SnackbarHostState() }

    val prefs = rememberEpubReaderReadingPrefsState(context)
    ReaderScreenOrientationEffect(prefs.screenOrientationMode)

    val dictTools = rememberEpubReaderDictionaryToolsState(context)

    val readerCacheBookId = remember(stableBookId, epubBook.title, epubBook.fileName) {
        stableBookId ?: if (epubBook.fileName.length > 20) epubBook.fileName else getBookIdForPrefs(epubBook.title)
    }
    val bookId = readerCacheBookId

    var isPageSliderVisible by remember(bookId) {
        mutableStateOf(loadReaderSliderToggled(context, bookId))
    }

    val locatorConverter = remember(context, readerCacheBookId) {
        LocatorConverter(
            bookCacheDao = BookCacheDatabase.getDatabase(context).bookCacheDao(),
            proto = ProtoBuf { serializersModule = semanticBlockModule },
            context = context,
            stableBookId = readerCacheBookId
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
    var ttsOverlaySize by remember(context) { mutableStateOf(loadReaderTtsOverlaySize(context)) }

    val autoScroll = rememberEpubReaderAutoScrollSpeedState(context, bookId)

    val currentHighlightPaletteState = remember {
        mutableStateOf(loadHighlightPalette(context))
    }
    var currentHighlightPalette by currentHighlightPaletteState

    val onUpdateHighlightPalette: (Int, Int) -> Unit = { index, newColor ->
        val newList = currentHighlightPalette.toMutableList()
        if (index in newList.indices) {
            newList[index] = newColor
            currentHighlightPalette = newList
            saveHighlightPalette(context, newList)
        }
    }

    // Dictionary
    val showDictionaryUpsellDialogState = remember { mutableStateOf(false) }
    var showDictionaryUpsellDialog by showDictionaryUpsellDialogState
    var showSummarizationUpsellDialog by remember { mutableStateOf(false) }

    @Suppress("KotlinConstantConditions") val onDictionaryLookup = { word: String ->
        val effectiveUseOnline = areReaderAiFeaturesEnabled(context) && dictTools.useOnlineDictionary

        if (effectiveUseOnline) {
            val wordCount = countWords(word)
            if (BuildConfig.FLAVOR != "oss" && wordCount > 1 && !isProUser) {
                showDictionaryUpsellDialog = true
            } else {
                dictTools.selectedTextForAi = word
                dictTools.showAiDefinitionPopup = true
                scope.launch {
                    val token = viewModel.getAuthToken()
                    dictTools.isAiDefinitionLoading = true
                    dictTools.aiDefinitionResult = null
                    fetchAiDefinition(
                        text = word,
                        onUpdate = { chunk ->
                            val currentDefinition = dictTools.aiDefinitionResult?.definition ?: ""
                            dictTools.aiDefinitionResult = AiDefinitionResult(definition = currentDefinition + chunk)
                        },
                        authToken = token,
                        onError = { error ->
                            if (error == "INSUFFICIENT_CREDITS") {
                                navigation.showInsufficientCreditsDialog = true
                                dictTools.showAiDefinitionPopup = false
                                dictTools.isAiDefinitionLoading = false
                            } else {
                                dictTools.aiDefinitionResult = AiDefinitionResult(error = error)
                            }
                        },
                        onFinish = { dictTools.isAiDefinitionLoading = false },
                        context = context
                    )
                }
            }
        } else {
            if (!dictTools.selectedDictPackage.isNullOrEmpty()) {
                ExternalDictionaryHelper.launchDictionary(context, dictTools.selectedDictPackage!!, word)
            } else {
                Toast.makeText(context, context.getString(R.string.toast_select_dictionary_first), Toast.LENGTH_SHORT).show()
                dictTools.showDictionarySettingsSheet = true
            }
        }
    }

    val onTranslateLookup = { text: String ->
        if (!dictTools.selectedTranslatePackage.isNullOrEmpty()) {
            ExternalDictionaryHelper.launchTranslate(context, dictTools.selectedTranslatePackage!!, text)
        } else {
            Toast.makeText(context, context.getString(R.string.toast_select_translate_first), Toast.LENGTH_SHORT).show()
            dictTools.showDictionarySettingsSheet = true
        }
    }

    val onSearchLookup = { text: String ->
        if (!dictTools.selectedSearchPackage.isNullOrEmpty()) {
            ExternalDictionaryHelper.launchSearch(context, dictTools.selectedSearchPackage!!, text)
        } else {
            Toast.makeText(context, context.getString(R.string.toast_select_search_first), Toast.LENGTH_SHORT).show()
            dictTools.showDictionarySettingsSheet = true
        }
    }

    val summaryCacheManager = remember(context) { SummaryCacheManager(context) }
    var showRecapPopup by remember { mutableStateOf(false) }

    val currentRenderModeState = remember(renderMode) { mutableStateOf(renderMode) }
    var currentRenderMode by currentRenderModeState
    var useNativeVerticalRenderer by remember { mutableStateOf(loadNativeVerticalRenderer(context)) }
    val isNativeVerticalMode = currentRenderMode == RenderMode.VERTICAL_SCROLL && useNativeVerticalRenderer
    var epubJumpHistory by remember(readerCacheBookId) { mutableStateOf(ReaderJumpHistory()) }
    val lastKnownLocatorState = remember(initialLocator) { mutableStateOf(initialLocator) }
    var lastKnownLocator by lastKnownLocatorState

    LaunchedEffect(useNativeVerticalRenderer) {
        saveNativeVerticalRenderer(context, useNativeVerticalRenderer)
    }

    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val roundedCornerBottomPadding = rememberBottomRoundedCornerPadding(view)
    val pageInfoCornerBottomPadding = roundedCornerBottomPadding.coerceAtMost(8.dp)

    val bookmarksState = remember(epubBook.title) {
        mutableStateOf(
            loadBookmarks(context, epubBook.title, epubBook.chapters, initialBookmarksJson).also {
                Timber.d("Initial load for '${epubBook.title}': ${it.size} bookmarks loaded -> $it")
            }
        )
    }
    var bookmarks by bookmarksState

    LaunchedEffect(bookmarks) {
        Timber.d("Bookmarks changed, saving...")
        onBookmarksChanged(bookmarksToJson(bookmarks))
    }

    var activeBookmarkInVerticalView by remember { mutableStateOf<Bookmark?>(null) }
    val addBookmarkRequestState = remember { mutableStateOf(false) }
    var addBookmarkRequest by addBookmarkRequestState
    val isChapterReadyForBookmarkCheckState = remember { mutableStateOf(false) }
    var isChapterReadyForBookmarkCheck by isChapterReadyForBookmarkCheckState
    var lastBookmarkCheckTime by remember { mutableLongStateOf(0L) }
    val isSwitchingToPaginatedState = remember { mutableStateOf(false) }
    var isSwitchingToPaginated by isSwitchingToPaginatedState

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
    val isSavingAndExitingState = remember { mutableStateOf(false) }
    var isSavingAndExiting by isSavingAndExitingState

    val ttsShouldStartOnChapterLoadState = remember { mutableStateOf(false) }
    var ttsShouldStartOnChapterLoad by ttsShouldStartOnChapterLoadState
    val userStoppedTtsState = remember { mutableStateOf(false) }
    var userStoppedTts by userStoppedTtsState
    val ttsChapterIndexState = remember { mutableStateOf<Int?>(null) }
    var ttsChapterIndex by ttsChapterIndexState
    var pendingTtsLocateRequest by remember { mutableStateOf(false) }
    var pendingTtsLocateReason by remember { mutableStateOf<String?>(null) }
    var hasQueuedInitialTtsLocate by remember(epubBook.title) { mutableStateOf(false) }
    var isDetachedFromVerticalTts by remember { mutableStateOf(false) }
    var detachedVerticalTtsChunkKey by remember { mutableStateOf<String?>(null) }
    var suppressNextVerticalTtsDetach by remember { mutableStateOf(false) }

    val searchHighlightTargetState = remember { mutableStateOf<SearchResult?>(null) }
    var searchHighlightTarget by searchHighlightTargetState
    val lastHighlightClickTimeState = remember { mutableLongStateOf(0L) }
    var lastHighlightClickTime by lastHighlightClickTimeState
    val lastScrollHideTimeState = remember { mutableLongStateOf(0L) }
    var lastScrollHideTime by lastScrollHideTimeState

    val webViewRefForTtsState = remember { mutableStateOf<WebView?>(null) }
    var webViewRefForTts by webViewRefForTtsState

    val showAiHubSheetState = remember { mutableStateOf(false) }
    var showAiHubSheet by showAiHubSheetState
    val summarizationResultState = remember { mutableStateOf<SummarizationResult?>(null) }
    var summarizationResult by summarizationResultState
    val isSummarizationLoadingState = remember { mutableStateOf(false) }
    var isSummarizationLoading by isSummarizationLoadingState

    val recapResultState = remember { mutableStateOf<SummarizationResult?>(null) }
    var recapResult by recapResultState
    val isRecapLoadingState = remember { mutableStateOf(false) }
    var isRecapLoading by isRecapLoadingState
    var recapProgressMessage by remember { mutableStateOf("") }
    val isRequestingRecapCfiState = remember { mutableStateOf(false) }
    var isRequestingRecapCfi by isRequestingRecapCfiState

    val epubSearcher = remember(epubBook) { createEpubSearcher(epubBook) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val showBarsState = remember { mutableStateOf(false) }
    var showBars by showBarsState
    val chapters = remember(epubBook.chapters) { epubBook.chapters }

    LaunchedEffect(epubBook, uiState.selectedBookId) {
        showBars = false
    }

    var readerImages by remember(epubBook) { mutableStateOf<List<EpubReaderImageReference>>(emptyList()) }
    var readerImagesLoaded by remember(epubBook) { mutableStateOf(false) }

    LaunchedEffect(epubBook, drawerState.isOpen) {
        if (!drawerState.isOpen || readerImagesLoaded) return@LaunchedEffect
        readerImages = withContext(Dispatchers.IO) {
            if (prefs.hideImages) emptyList() else epubBook.readerImageReferencesForDrawer()
        }
        readerImagesLoaded = true
    }

    val currentChapterIndexState = rememberSaveable(epubBook.title) {
        mutableIntStateOf(
            initialLocator?.chapterIndex?.coerceIn(0, max(0, chapters.size - 1)) ?: 0
        )
    }
    var currentChapterIndex by currentChapterIndexState

    LaunchedEffect(chapters.size) {
        epubJumpHistory = epubJumpHistory.pruned(chapters.size)
    }

    val paginatorState = remember { mutableStateOf<IPaginator?>(null) }
    var paginator by paginatorState
    val paginatedPagerState = rememberPagerState(pageCount = {
        (paginator as? BookPaginator)?.totalPageCount ?: 0
    })
    val isPagerInitializedState = remember(initialLocator) { mutableStateOf(initialLocator == null) }
    var isPagerInitialized by isPagerInitializedState
    val paginatedExplicitNavigationEpochState = remember(epubBook) { mutableLongStateOf(0L) }
    var paginatedExplicitNavigationEpoch by paginatedExplicitNavigationEpochState
    val paginatedExplicitNavigationAnchorState = remember(epubBook) { mutableStateOf<Locator?>(null) }
    var paginatedExplicitNavigationAnchor by paginatedExplicitNavigationAnchorState

    val ttsController = viewModel.ttsController
    val ttsState by ttsController.ttsState.collectAsState()

    val totalBookLengthChars = remember(chapters) {
        chapters.sumOf { it.plainTextCharacterCount().toLong() }
    }

    val topVisibleChunkIndexState = remember { mutableIntStateOf(0) }
    var topVisibleChunkIndex by topVisibleChunkIndexState
    val loadedChunkCountState = remember { mutableIntStateOf(1) }
    var loadedChunkCount by loadedChunkCountState
    val loadUpToChunkIndexState = remember(currentChapterIndex) { mutableIntStateOf(0) }
    var loadUpToChunkIndex by loadUpToChunkIndexState

    val chapterChunksState = remember(currentChapterIndex) { mutableStateOf<List<String>>(emptyList()) }
    var chapterChunks by chapterChunksState
    val chapterChunkElementStartIndicesState = remember(currentChapterIndex) { mutableStateOf<List<Int>>(emptyList()) }
    var chapterChunkElementStartIndices by chapterChunkElementStartIndicesState
    val chapterChunkElementCountsState = remember(currentChapterIndex) { mutableStateOf<List<Int>>(emptyList()) }
    var chapterChunkElementCounts by chapterChunkElementCountsState
    val chapterHeadState = remember(currentChapterIndex) { mutableStateOf("") }
    var chapterHead by chapterHeadState
    val epubFontFaceCss = remember(epubBook.css, epubBook.extractionBasePath) {
        val fontFaces = epubBook.css.flatMap { (path, content) ->
            CssParser.parseFontFaces(
                cssContent = content,
                cssPath = path,
                constraints = Constraints(maxWidth = 1, maxHeight = 1),
                isDarkTheme = false,
                adaptThemeColors = false
            )
        }
        buildEpubFontFaceCss(fontFaces, epubBook.extractionBasePath)
    }
    val isChapterParsingState = remember(currentChapterIndex) { mutableStateOf(true) }
    var isChapterParsing by isChapterParsingState

    val cfiToLoadState = remember { mutableStateOf(initialCfi) }
    var cfiToLoad by cfiToLoadState
    val fragmentToLoadState = remember { mutableStateOf<String?>(null) }
    var fragmentToLoad by fragmentToLoadState
    val imageToLoadState = remember { mutableStateOf<EpubReaderImageReference?>(null) }
    var imageToLoad by imageToLoadState
    var isInitialCfiLoad by remember(initialLocator) { mutableStateOf(initialLocator != null) }
    var bookmarkPageMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var bookmarkLocatorMap by remember { mutableStateOf<Map<String, Locator>>(emptyMap()) }

    LaunchedEffect(Unit) {
        Timber.tag("POS_DIAG").d("Reader Opening: initialLocator=$initialLocator, initialCfi=$initialCfi")
        Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d(
            "open_start mode=$currentRenderMode initialLocator=$initialLocator initialCfi=${initialCfi?.take(80)} bookId=$bookId"
        )
    }

    val initialScrollTargetForChapterState = rememberSaveable(epubBook.title) {
        mutableStateOf(if (initialLocator != null) null else ChapterScrollPosition.START)
    }
    var initialScrollTargetForChapter by initialScrollTargetForChapterState

    val pullToPrevProgressState = remember { mutableFloatStateOf(0f) }
    var pullToPrevProgress by pullToPrevProgressState
    val pullToNextProgressState = remember { mutableFloatStateOf(0f) }
    var pullToNextProgress by pullToNextProgressState


    val density = LocalDensity.current
    val dragThresholdPx = with(density) { DRAG_TO_CHANGE_CHAPTER_THRESHOLD_DP.toPx() * prefs.pullToTurnMultiplier }

    val currentScrollYPositionState = rememberSaveable(epubBook.title) {
        mutableIntStateOf(0)
    }
    var currentScrollYPosition by currentScrollYPositionState

    val currentScrollHeightValueState = remember { mutableIntStateOf(0) }
    var currentScrollHeightValue by currentScrollHeightValueState
    val currentClientHeightValueState = remember { mutableIntStateOf(0) }
    var currentClientHeightValue by currentClientHeightValueState
    val nativeVerticalCurrentPageState = rememberSaveable(epubBook.title) { mutableIntStateOf(0) }
    var nativeVerticalCurrentPage by nativeVerticalCurrentPageState
    val nativeVerticalTotalPagesState = remember { mutableIntStateOf(0) }
    var nativeVerticalTotalPages by nativeVerticalTotalPagesState
    val nativeVerticalProgressState = remember { mutableFloatStateOf(0f) }
    var nativeVerticalProgress by nativeVerticalProgressState
    val nativeVerticalLocationState = remember { mutableStateOf<NativeVerticalLocation?>(null) }
    var nativeVerticalLocation by nativeVerticalLocationState
    val verticalScrollRequests = rememberEpubReaderVerticalScrollRequests()

    fun currentNativeVerticalLocator(): Locator? {
        val bookPaginator = paginator as? BookPaginator
        val pageChapterIndex = bookPaginator?.findChapterIndexForPage(nativeVerticalCurrentPage)
        return nativeVerticalLocation?.locatorForPersistence()
            ?: lastKnownLocator?.takeIf { pageChapterIndex == null || it.chapterIndex == pageChapterIndex }
            ?: bookPaginator?.getLocatorForPage(nativeVerticalCurrentPage)
    }

    fun requestNativeVerticalLocatorScroll(
        locator: Locator?,
        fallbackPage: Int? = null,
        fallbackChapterIndex: Int? = locator?.chapterIndex,
        keepVisible: Boolean = false
    ) {
        if (locator != null) {
            verticalScrollRequests.nativeVerticalScrollRequest = null
            verticalScrollRequests.nativeVerticalProgressScrollRequest = null
            verticalScrollRequests.nativeVerticalLocatorScrollRequest = locator
            verticalScrollRequests.nativeVerticalLocatorScrollRequestId += 1L
            verticalScrollRequests.nativeVerticalLocatorScrollKeepVisible = keepVisible
            lastKnownLocator = locator
            currentChapterIndex = locator.chapterIndex
        } else if (fallbackPage != null) {
            verticalScrollRequests.nativeVerticalScrollRequest = fallbackPage
            verticalScrollRequests.nativeVerticalLocatorScrollKeepVisible = false
            fallbackChapterIndex?.let { currentChapterIndex = it }
        }
    }

    fun requestNativeVerticalProgressScroll(progressPercent: Float) {
        verticalScrollRequests.nativeVerticalProgressScrollRequest = progressPercent.coerceIn(0f, 100f)
        verticalScrollRequests.nativeVerticalProgressScrollRequestId += 1L
    }

    val currentBookProgress by remember(
        currentChapterIndex,
        currentScrollYPosition,
        currentScrollHeightValue,
        currentClientHeightValue,
        totalBookLengthChars,
        isNativeVerticalMode,
        nativeVerticalProgress
    ) {
        derivedStateOf {
            if (isNativeVerticalMode) {
                return@derivedStateOf nativeVerticalProgress.coerceIn(0f, 100f)
            }
            if (totalBookLengthChars > 0) {
                val completedCharsInPreviousChapters =
                    chapters.take(currentChapterIndex)
                        .sumOf { it.plainTextCharacterCount().toLong() }

                val progressWithinChapter = mobileEpubChapterScrollFraction(
                    scrollY = currentScrollYPosition,
                    scrollHeight = currentScrollHeightValue,
                    clientHeight = currentClientHeightValue
                )

                val currentChapterLengthChars =
                    chapters.getOrNull(currentChapterIndex)?.plainTextCharacterCount()?.toLong() ?: 0L
                val charsScrolledInCurrentChapter = (progressWithinChapter * currentChapterLengthChars).toLong()
                val isLastChapter = currentChapterIndex == chapters.size - 1
                val isAtEndOfBook = isLastChapter && (currentScrollYPosition + currentClientHeightValue) >= (currentScrollHeightValue - 2)
                mobileEpubCharacterProgress(
                    totalBookCharacters = totalBookLengthChars,
                    completedChapterCharacters = completedCharsInPreviousChapters,
                    currentChapterOffset = charsScrolledInCurrentChapter,
                    isAtEndOfBook = isAtEndOfBook
                )
            } else {
                0f
            }
        }
    }


    var isFormatLocal by remember { mutableStateOf(loadFormatIsLocal(context, bookId)) }
    val format = remember(isFormatLocal, bookId) { EpubReaderFormatState(context, bookId, isFormatLocal) }

    var showFontSelectionSheet by remember { mutableStateOf(false) }
    val fontSheetState = rememberModalBottomSheetState()

    LaunchedEffect(format.currentFontSizeEm, format.currentLineHeight, format.currentParagraphGap, format.currentImageSize, format.currentHorizontalMargin, format.currentVerticalMargin, format.currentFontFamily, format.currentCustomFontPath, format.currentTextAlign, format.currentFontWeight, format.currentLetterSpacing, isFormatLocal) {
        if (isFormatLocal) {
            saveLocalReaderSettings(
                context, bookId, format.currentFontSizeEm, format.currentLineHeight, format.currentParagraphGap, format.currentImageSize, format.currentHorizontalMargin, format.currentVerticalMargin, format.currentFontFamily, format.currentCustomFontPath, format.currentTextAlign, format.currentFontWeight, format.currentLetterSpacing
            )
        } else {
            saveReaderSettings(
                context, format.currentFontSizeEm, format.currentLineHeight, format.currentParagraphGap, format.currentImageSize, format.currentHorizontalMargin, format.currentVerticalMargin, format.currentFontFamily, format.currentCustomFontPath, format.currentTextAlign, format.currentFontWeight, format.currentLetterSpacing
            )
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

    LaunchedEffect(ttsState.errorMessage) {
        ttsState.errorMessage?.let { message ->
            if (message == "INSUFFICIENT_CREDITS") {
                navigation.showInsufficientCreditsDialog = true
                ttsController.stop()
            } else {
                showBanner(message, isError = true)
            }
        }
    }

    val searchState = rememberSearchState(scope = scope, searcher = epubSearcher)
    val isEpubSliderReady = when {
        isNativeVerticalMode -> nativeVerticalTotalPages > 0
        currentRenderMode == RenderMode.VERTICAL_SCROLL -> true
        else -> paginatedPagerState.pageCount > 0
    }
    val epubSliderChromeVisible = shouldRenderReaderSlider(
        isToggledOn = isPageSliderVisible,
        isBottomChromeVisible = showBars,
        isSearchActive = searchState.isSearchActive
    ) && isEpubSliderReady
    val speakerPlayer = remember(context, scope) {
        SpeakerSamplePlayer(context, scope, getAuthToken = { viewModel.getAuthToken() })
    }

    val isAutoScrollModeActiveState = remember { mutableStateOf(false) }
    var isAutoScrollModeActive by isAutoScrollModeActiveState
    val isAutoScrollPlayingState = remember { mutableStateOf(false) }
    var isAutoScrollPlaying by isAutoScrollPlayingState
    var isAutoScrollTempPaused by remember { mutableStateOf(false) }
    val autoScrollResumeJob = remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(isNativeVerticalMode) {
        if (isNativeVerticalMode) {
            webViewRefForTts = null
        } else {
            nativeVerticalLocation = null
        }
    }

    val isMusicianModeState = remember { mutableStateOf(loadMusicianMode(context)) }
    var isMusicianMode by isMusicianModeState
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
        updateAutoScrollState(isAutoScrollPlaying, autoScroll.autoScrollSpeed)

        autoScrollResumeJob.value = scope.launch {
            delay(durationMs)
            if (isActive && isAutoScrollModeActive && isAutoScrollPlaying) {
                isAutoScrollTempPaused = false
                @Suppress("KotlinConstantConditions") updateAutoScrollState(isAutoScrollPlaying, autoScroll.autoScrollSpeed)
            }
        }
    }

    LaunchedEffect(isAutoScrollModeActive, isAutoScrollPlaying, autoScroll.autoScrollSpeed, isAutoScrollTempPaused, isNativeVerticalMode) {
        if (isNativeVerticalMode) {
            webViewRefForTts?.evaluateJavascript("javascript:window.autoScroll.stop();", null)
        } else if (isAutoScrollModeActive) {
            updateAutoScrollState(isAutoScrollPlaying, autoScroll.autoScrollSpeed)
        } else {
            webViewRefForTts?.evaluateJavascript("javascript:window.autoScroll.stop();", null)
        }
    }

    LaunchedEffect(isNativeVerticalMode, isAutoScrollModeActive, isAutoScrollPlaying, autoScroll.autoScrollSpeed, isAutoScrollTempPaused) {
        if (!isNativeVerticalMode) return@LaunchedEffect
        while (isActive && isAutoScrollModeActive && isAutoScrollPlaying && !isAutoScrollTempPaused) {
            if (nativeVerticalLocation?.isAtEnd == true) {
                isAutoScrollPlaying = false
                break
            }
            verticalScrollRequests.nativeVerticalScrollDeltaRequestId += 1L
            verticalScrollRequests.nativeVerticalScrollDeltaAnimated = false
            verticalScrollRequests.nativeVerticalScrollDeltaRequest = autoScroll.autoScrollSpeed.coerceAtLeast(0f) * 0.5f
            delay(16L)
        }
    }

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showTtsSettingsSheet by remember { mutableStateOf(false) }
    var showTtsReplacementsSheet by remember { mutableStateOf(false) }
    var showBookReplacementsSheet by remember { mutableStateOf(false) }
    var showTtsControlsSheet by remember { mutableStateOf(false) }
    var showThemePanel by remember { mutableStateOf(false) }
    var showPaletteManager by remember { mutableStateOf(false) }
    var ttsReplacementPreferences by remember { mutableStateOf(loadTtsReplacementPreferences(context)) }
    val updateTtsReplacementPreferences: (ReaderTtsReplacementPreferences) -> Unit = { next ->
        ttsReplacementPreferences = next
        saveTtsReplacementPreferences(context, next)
    }
    val bookReplacementPreferencesState = remember { mutableStateOf(loadBookReplacementPreferences(context)) }
    var bookReplacementPreferences by bookReplacementPreferencesState
    val updateBookReplacementPreferences: (ReaderBookReplacementPreferences) -> Unit = { next ->
        bookReplacementPreferences = next
        saveBookReplacementPreferences(context, next)
    }
    val bookReplacementSignature = remember(bookReplacementPreferences, bookId) {
        bookReplacementPreferences.signatureForFile(bookId)
    }

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
    val epubReaderSliderColors = readerSliderChromeColors(
        pageBackground = effectiveBg,
        pageText = effectiveText,
        themePrimary = MaterialTheme.colorScheme.primary
    )
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
        val isSameBook = ttsState.bookId?.let { it == bookId }
            ?: (ttsState.bookTitle == null || ttsState.bookTitle == epubBook.title)
        return isReaderSession && hasReaderSessionState && isSameBook
    }

    fun getActiveTtsChapterIndex(): Int? = ttsState.chapterIndex ?: ttsChapterIndex

    fun buildTtsDiagState(): String {
        val sourceCfiPreview = ttsState.sourceCfi?.take(48)
        val pendingCfiPreview = cfiToLoad?.take(48)
        return "render=$currentRenderMode currentChapter=$currentChapterIndex activeTtsChapter=${getActiveTtsChapterIndex()} " +
            "pendingLocate=$pendingTtsLocateRequest locateReason=$pendingTtsLocateReason detached=$isDetachedFromVerticalTts suppressDetach=$suppressNextVerticalTtsDetach " +
            "chunkOverride=$navigation.chunkTargetOverride pendingCfi=$pendingCfiPreview ttsCfi=$sourceCfiPreview offset=${ttsState.startOffsetInSource}"
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
        navigation.isNavigatingToPosition = false
        suppressNextVerticalTtsDetach = false
    }

    fun clearPendingTtsRelocationState(reason: String) {
        logTtsChapterDiag("Clearing pending TTS relocation state. reason=$reason")
        pendingTtsLocateRequest = false
        pendingTtsLocateReason = null
        navigation.chunkTargetOverride = null
        cfiToLoad = null
        fragmentToLoad = null
        imageToLoad = null
        navigation.isNavigatingToPosition = false
        suppressNextVerticalTtsDetach = false
    }

    suspend fun saveResolvedLocatorPosition(locator: Locator, cfiForWebView: String?) {
        lastKnownLocator = locator

        val chapterLengthChars = chapters.getOrNull(locator.chapterIndex)?.plainTextCharacterCount()?.toLong() ?: 0L
        val exactOffset = locatorConverter.getTextOffset(epubBook, locator)?.coerceAtLeast(0) ?: 0
        val boundedOffset = exactOffset.coerceAtMost(chapterLengthChars.toInt()).toLong()

        val completedCharsInPreviousChapters =
            chapters.take(locator.chapterIndex).sumOf { it.plainTextCharacterCount().toLong() }
        val progress = mobileEpubCharacterProgress(
            totalBookCharacters = totalBookLengthChars,
            completedChapterCharacters = completedCharsInPreviousChapters,
            currentChapterOffset = boundedOffset,
            isAtEndOfBook = locator.chapterIndex == chapters.lastIndex && chapterLengthChars > 0 && boundedOffset >= chapterLengthChars
        )

        Timber.tag("TTS_LOCATE")
            .d("Saving resolved locator position. chapter=${locator.chapterIndex}, block=${locator.blockIndex}, progress=$progress")
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
        val sourceOffset = ttsState.currentWordStartOffset.takeIf { it >= 0 }
            ?: ttsState.startOffsetInSource.takeIf { it >= 0 }
        val locator = locatorConverter.getLocatorFromCfi(epubBook, chapterIndex, sourceCfi)
            ?.let { baseLocator ->
                sourceOffset?.let { baseLocator.copy(charOffset = it) } ?: baseLocator
            }
            ?: return false

        logTtsChapterDiag("Persisting active TTS position. reason=$reason chapter=$chapterIndex cfi=${sourceCfi.take(48)} sourceOffset=$sourceOffset")
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
        val locator = locatorConverter.getLocatorFromCfi(epubBook, chapterIndex, sourceCfi)?.let { baseLocator ->
            sourceOffset?.let { baseLocator.copy(charOffset = it) } ?: baseLocator
        } ?: run {
            logTtsChapterDiag("navigateToActiveTtsPosition aborted: locator conversion failed. reason=$reason chapter=$chapterIndex cfi=${sourceCfi.take(48)}")
            return false
        }
        val targetChunk = max(0, locator.blockIndex / 20)

        saveResolvedLocatorPosition(locator, sourceCfi)
        logTtsChapterDiag("Navigating to active TTS position. reason=$reason targetChapter=$chapterIndex targetChunk=$targetChunk sourceOffset=$sourceOffset")

        when (currentRenderMode) {
            RenderMode.VERTICAL_SCROLL -> {
                if (isNativeVerticalMode) {
                    val bookPaginator = paginator as? BookPaginator ?: run {
                        logTtsChapterDiag("Native vertical locate aborted: paginator unavailable. reason=$reason")
                        return false
                    }
                    val pageIndex =
                        bookPaginator.findStablePageForLocator(locator)
                            ?: bookPaginator.findStableChapterStartPage(chapterIndex) ?: run {
                                logTtsChapterDiag("Native vertical locate aborted: page lookup failed. reason=$reason chapter=$chapterIndex")
                                return false
                            }
                    logTtsChapterDiag("Native vertical locate scrolling to page=$pageIndex. reason=$reason")
                    navigation.isNavigatingToPosition = true
                    requestNativeVerticalLocatorScroll(
                        locator = locator,
                        fallbackPage = pageIndex,
                        fallbackChapterIndex = chapterIndex
                    )
                    navigation.isNavigatingToPosition = false
                    return true
                }
                navigation.isNavigatingToPosition = true
                initialScrollTargetForChapter = null
                isDetachedFromVerticalTts = false
                detachedVerticalTtsChunkKey = null
                suppressNextVerticalTtsDetach = true

                if (chapterIndex != currentChapterIndex) {
                    logTtsChapterDiag("Vertical locate switching chapters. reason=$reason from=$currentChapterIndex to=$chapterIndex targetChunk=$targetChunk")
                    navigation.chunkTargetOverride = targetChunk
                    cfiToLoad = sourceCfi
                    currentScrollYPosition = 0
                    currentScrollHeightValue = 0
                    currentChapterIndex = chapterIndex
                } else {
                    if (webViewRefForTts == null) {
                        logTtsChapterDiag("Vertical locate queued because WebView is null. reason=$reason targetChunk=$targetChunk")
                        navigation.chunkTargetOverride = targetChunk
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
                            if (navigation.isNavigatingToPosition) {
                                navigation.isNavigatingToPosition = false
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
                    bookPaginator.findStablePageForLocator(locator)
                        ?: bookPaginator.findStableChapterStartPage(chapterIndex) ?: run {
                            logTtsChapterDiag("Paginated locate aborted: page lookup failed. reason=$reason chapter=$chapterIndex")
                            return false
                        }

                logTtsChapterDiag("Paginated locate scrolling to page=$pageIndex. reason=$reason")
                navigation.isNavigatingToPosition = true
                paginatedPagerState.scrollToPage(pageIndex)
                navigation.isNavigatingToPosition = false
                return true
            }
        }
    }

    val onHighlightColorChange: (UserHighlight, Int) -> Unit = { targetHighlight, newColorArgb ->
        val index = userHighlights.indexOfFirst { it.cfi == targetHighlight.cfi }
        if (index != -1) {
            val legacyColor = legacyHighlightColorForArgb(newColorArgb)
            userHighlights[index] = targetHighlight.copy(color = legacyColor, colorArgb = newColorArgb)
            if (currentRenderMode == RenderMode.VERTICAL_SCROLL && targetHighlight.chapterIndex == currentChapterIndex) {
                val cssClass = legacyColor.cssClass
                val colorCss = String.format("#%06X", 0xFFFFFF and newColorArgb)
                val jsCommand = "javascript:window.HighlightBridgeHelper.updateHighlightStyle('${escapeJsString(targetHighlight.cfi)}', '$cssClass', '$newColorArgb', '$colorCss', '${targetHighlight.style.id}');"
                webViewRefForTts?.evaluateJavascript(jsCommand, null)
            }
        }
    }

    val onHighlightStyleChange: (UserHighlight, HighlightStyle) -> Unit = { targetHighlight, newStyle ->
        val index = userHighlights.indexOfFirst { it.cfi == targetHighlight.cfi }
        if (index != -1) {
            val current = userHighlights[index]
            userHighlights[index] = current.copy(style = newStyle)
            if (currentRenderMode == RenderMode.VERTICAL_SCROLL && targetHighlight.chapterIndex == currentChapterIndex) {
                val colorArgb = current.colorArgb ?: current.color.color.toArgb()
                val cssClass = current.color.cssClass
                val colorCss = String.format("#%06X", 0xFFFFFF and colorArgb)
                val jsCommand = "javascript:window.HighlightBridgeHelper.updateHighlightStyle('${escapeJsString(targetHighlight.cfi)}', '$cssClass', '$colorArgb', '$colorCss', '${newStyle.id}');"
                webViewRefForTts?.evaluateJavascript(jsCommand, null)
            }
        }
    }

    fun startTts() {
        if (BuildConfig.FLAVOR != "oss" && prefs.currentTtsMode == TtsPlaybackManager.TtsMode.CLOUD && credits <= 0) {
            navigation.showInsufficientCreditsDialog = true
            return
        }

        if (isAutoScrollModeActive) {
            isAutoScrollModeActive = false
            isAutoScrollPlaying = false
        }
        Timber.d("TTS button clicked: Starting TTS")
        userStoppedTts = false

        initiateTtsPlayback(
            renderMode = if (isNativeVerticalMode) RenderMode.PAGINATED else currentRenderMode,
            webView = if (isNativeVerticalMode) null else webViewRefForTts,
            onPaginatedStart = {
                scope.launch {
                    val token = viewModel.getAuthToken()
                    val bookPaginator = paginator as? BookPaginator ?: return@launch
                    val nativeStartLocator = if (isNativeVerticalMode) currentNativeVerticalLocator() else null
                    val currentPage = nativeStartLocator
                        ?.let { locator -> bookPaginator.findStablePageForLocator(locator) }
                        ?: if (isNativeVerticalMode) {
                            nativeVerticalCurrentPage
                        } else {
                            paginatedPagerState.currentPage
                        }
                    val chapterIndex = nativeStartLocator?.chapterIndex
                        ?: bookPaginator.findChapterIndexForPage(currentPage)
                    if (chapterIndex != null) {
                        val chapterStartPage = bookPaginator.chapterStartPageIndices[chapterIndex] ?: 0
                        val pageInChapter = currentPage - chapterStartPage

                        val allTtsChunks = bookPaginator.getTtsChunksForChapter(chapterIndex)
                        val firstChunkOnPage = if (nativeStartLocator != null && !allTtsChunks.isNullOrEmpty()) {
                            val sourceCfi = locatorConverter.getCfiFromLocator(epubBook, nativeStartLocator, bookId)
                            val target = TtsChunk(
                                text = "",
                                sourceCfi = sourceCfi?.substringBefore(':').orEmpty(),
                                startOffsetInSource = nativeStartLocator.charOffset
                            )
                            val nativeStartChunkIndex = findTtsChunkStartIndex(allTtsChunks, target)
                                ?: allTtsChunks.indexOfFirst { chunk ->
                                    nativeStartLocator.charOffset >= chunk.startOffsetInSource &&
                                        nativeStartLocator.charOffset < chunk.startOffsetInSource + chunk.text.length
                                }.takeIf { it >= 0 }
                            val nativeStartChunk = nativeStartChunkIndex?.let { allTtsChunks.getOrNull(it) }
                            if (nativeStartChunk != null) {
                                val relativeOffset = nativeStartLocator.charOffset - nativeStartChunk.startOffsetInSource
                                val safeRelativeOffset = relativeOffset.coerceIn(0, nativeStartChunk.text.length)
                                if (safeRelativeOffset > 0) {
                                    val slicedText = nativeStartChunk.text.substring(safeRelativeOffset)
                                    nativeStartChunk.copy(
                                        text = slicedText,
                                        startOffsetInSource = nativeStartLocator.charOffset,
                                        spokenText = slicedText
                                    )
                                } else {
                                    nativeStartChunk
                                }
                            } else {
                                null
                            }
                        } else if (pageInChapter > 0) {
                            bookPaginator.getTtsChunksForChapter(
                                chapterIndex = chapterIndex,
                                startingFromPageInChapter = pageInChapter
                            )?.firstOrNull()
                        } else {
                            allTtsChunks?.firstOrNull()
                        }
                        val startChunkIndex = findTtsChunkStartIndex(allTtsChunks.orEmpty(), firstChunkOnPage) ?: 0

                        if (!allTtsChunks.isNullOrEmpty() && firstChunkOnPage != null) {
                            val chapterTitle = chapters.getOrNull(chapterIndex)?.title
                            val coverUriString = coverImagePath?.let { Uri.fromFile(File(it)).toString() }
                            ttsChapterIndex = chapterIndex
                            ttsController.start(
                                chunks = allTtsChunks.withInitialChunkOverride(startChunkIndex, firstChunkOnPage)
                                    .withTtsReplacements(ttsReplacementPreferences, bookId),
                                bookTitle = epubBook.title,
                                chapterTitle = chapterTitle,
                                coverImageUri = coverUriString,
                                bookId = bookId,
                                chapterIndex = chapterIndex,
                                totalChapters = chapters.size,
                                startChunkIndex = startChunkIndex,
                                ttsMode = prefs.currentTtsMode,
                                playbackSource = "READER",
                                authToken = token
                            )
                        }
                    }
                }
            }
        )
    }

    var pendingImageDownload by remember { mutableStateOf<EpubReaderImageReference?>(null) }
    val imageSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/*"),
        onResult = { uri ->
            val image = pendingImageDownload
            pendingImageDownload = null
            if (uri != null && image != null) {
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching {
                            val bytes = image.readDownloadBytes() ?: error("Image bytes are unavailable")
                            context.contentResolver.openOutputStream(uri)?.use { output ->
                                output.write(bytes)
                            } ?: error("Could not open image destination")
                        }.isSuccess
                    }
                    val message = if (saved) {
                        context.getString(R.string.saved_image_message, image.suggestedDownloadFileName())
                    } else {
                        context.getString(R.string.error_save_image)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ ->
            startTts()
        }
    )

    fun startTtsFromSelectionPaginated(
        baseCfi: String,
        startOffset: Int,
        chapterIndexOverride: Int? = null
    ) {
        if (BuildConfig.FLAVOR != "oss" && prefs.currentTtsMode == TtsPlaybackManager.TtsMode.CLOUD && credits <= 0) {
            navigation.showInsufficientCreditsDialog = true
            return
        }

        val action = {
            scope.launch {
                val token = viewModel.getAuthToken()
                val bookPaginator = paginator as? BookPaginator
                val chapterIndex = if (isNativeVerticalMode) {
                    chapterIndexOverride ?: currentChapterIndex
                } else {
                    currentChapterInPaginatedMode ?: return@launch
                }
                val chunks = bookPaginator?.getTtsChunksForChapter(chapterIndex) ?: return@launch
                val foundIdx = findTtsChunkStartIndex(
                    chunks = chunks,
                    target = TtsChunk(
                        text = "",
                        sourceCfi = baseCfi,
                        startOffsetInSource = startOffset
                    )
                ) ?: -1

                if (foundIdx != -1) {
                    val target = chunks[foundIdx]
                    val relativeOffset = startOffset - target.startOffsetInSource
                    val safeRelativeOffset = relativeOffset.coerceIn(0, target.text.length)
                    val slicedText = target.text.substring(safeRelativeOffset)
                    val newChunk = target.copy(
                        text = slicedText,
                        startOffsetInSource = startOffset,
                        spokenText = slicedText,
                    )

                    val sessionChunks = chunks.toMutableList().also {
                        it[foundIdx] = newChunk
                    }

                    if (sessionChunks.isNotEmpty()) {
                        ttsShouldStartOnChapterLoad = false
                        ttsChapterIndex = chapterIndex
                        val chapterTitle = chapters.getOrNull(chapterIndex)?.title
                        val coverUriString = coverImagePath?.let { Uri.fromFile(File(it)).toString() }
                        ttsController.start(
                            chunks = sessionChunks.withTtsReplacements(ttsReplacementPreferences, bookId),
                            bookTitle = epubBook.title,
                            chapterTitle = chapterTitle,
                            coverImageUri = coverUriString,
                            bookId = bookId,
                            chapterIndex = chapterIndex,
                            totalChapters = chapters.size,
                            startChunkIndex = foundIdx,
                            ttsMode = prefs.currentTtsMode,
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
            if (isNativeVerticalMode) {
                requestNativeVerticalLocatorScroll(
                    locator = Locator(nextIndex, 0, 0),
                    fallbackChapterIndex = nextIndex
                )
                verticalScrollRequests.nativeVerticalProgressScrollRequest = null
                webViewRefForTts = null
            } else {
                initialScrollTargetForChapter = ChapterScrollPosition.START
                cfiToLoad = null
                currentScrollYPosition = 0
                currentScrollHeightValue = 0
                currentChapterIndex = nextIndex
            }
        },
        onToggleTtsStartOnLoad = { shouldStart ->
            Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("ttsShouldStartOnChapterLoad set to: $shouldStart")
            ttsShouldStartOnChapterLoad = shouldStart
        },
        userStoppedTts = userStoppedTts,
        scope = scope,
        currentTtsMode = prefs.currentTtsMode,
        getAuthToken = { viewModel.getAuthToken() },
        locatorConverter = locatorConverter,
        epubBook = epubBook,
        ttsReplacementPreferences = ttsReplacementPreferences,
        ttsReplacementBookId = bookId
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

    LaunchedEffect(
        isNativeVerticalMode,
        ttsState.currentText,
        ttsState.sourceCfi,
        ttsState.startOffsetInSource,
        ttsState.chapterIndex,
        ttsChapterIndex,
        isDetachedFromVerticalTts
    ) {
        if (!isNativeVerticalMode) return@LaunchedEffect
        if (isDetachedFromVerticalTts) return@LaunchedEffect
        if (!isActiveReaderTtsForCurrentBook()) return@LaunchedEffect
        if (ttsState.currentText.isNullOrBlank()) return@LaunchedEffect

        val activeTtsChapterIndex = getActiveTtsChapterIndex() ?: return@LaunchedEffect
        val sourceCfi = ttsState.sourceCfi?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val sourceOffset = ttsState.startOffsetInSource.takeIf { it >= 0 } ?: return@LaunchedEffect
        val baseLocator = locatorConverter.getLocatorFromCfi(
            epubBook,
            activeTtsChapterIndex,
            sourceCfi,
            bookId
        ) ?: run {
            logTtsChapterDiag("Native vertical TTS follow skipped: locator conversion failed. cfi=${sourceCfi.take(48)} offset=$sourceOffset")
            return@LaunchedEffect
        }
        val locator = baseLocator.copy(charOffset = sourceOffset)
        val fallbackPage = (paginator as? BookPaginator)?.findStablePageForLocator(locator)
            ?: (paginator as? BookPaginator)?.findStableChapterStartPage(activeTtsChapterIndex)

        logTtsChapterDiag(
            "Native vertical following TTS chunk. chapter=$activeTtsChapterIndex " +
                "block=${locator.blockIndex} offset=${locator.charOffset} cfi=${sourceCfi.take(48)}"
        )
        requestNativeVerticalLocatorScroll(
            locator = locator,
            fallbackPage = fallbackPage,
            fallbackChapterIndex = activeTtsChapterIndex,
            keepVisible = true
        )
    }

    EpubReaderSearchEffects(
        searchState = searchState,
        webViewRef = if (isNativeVerticalMode) null else webViewRefForTts,
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

    val nativeVerticalDisplayPageInfo = remember(
        isNativeVerticalMode,
        nativeVerticalLocation,
        nativeVerticalCurrentPage,
        nativeVerticalTotalPages,
        currentChapterIndex,
        lastKnownLocator,
        paginator
    ) {
        if (!isNativeVerticalMode) {
            null
        } else {
            nativeVerticalLocation?.chapterPageInfo ?: run {
                val bookPaginator = paginator as? BookPaginator
                val locationLocator = nativeVerticalLocation?.locator
                val chapterIndex = nativeVerticalLocation?.chapterIndex
                    ?: locationLocator?.chapterIndex
                    ?: lastKnownLocator?.chapterIndex
                    ?: currentChapterIndex
                val locatorForChapter = locationLocator
                    ?.takeIf { it.chapterIndex == chapterIndex }
                    ?: lastKnownLocator?.takeIf { it.chapterIndex == chapterIndex }
                val chapterLengthChars = chapters
                    .getOrNull(chapterIndex)
                    ?.plainTextCharacterCount()
                    ?: 0

                nativeVerticalChapterPageInfo(
                    chapterCharOffset = locatorForChapter?.charOffset,
                    chapterLengthChars = chapterLengthChars,
                    chapterPageCount = bookPaginator?.chapterPageCounts?.get(chapterIndex),
                    compatPageIndex = nativeVerticalCurrentPage,
                    chapterStartPageIndex = bookPaginator?.chapterStartPageIndices?.get(chapterIndex)
                )
            }
        }
    }

    fun currentEpubSliderPage(): Int {
        return when (currentRenderMode) {
            RenderMode.VERTICAL_SCROLL -> if (isNativeVerticalMode) {
                (nativeVerticalCurrentPage + 1).coerceAtLeast(1)
            } else {
                currentPageInChapter
            }
            RenderMode.PAGINATED -> (paginatedPagerState.currentPage + 1).coerceAtLeast(1)
        }
    }

    fun resetEpubSliderBookmark() {
        val position = readerSliderBookmarkPosition(currentEpubSliderPage())
        navigation.sliderStartPage = position.startPage
        navigation.sliderCurrentPage = position.currentPage
    }

    LaunchedEffect(bookId, isPageSliderVisible) {
        saveReaderSliderToggled(context, bookId, isPageSliderVisible)
        if (isPageSliderVisible) {
            resetEpubSliderBookmark()
        }
    }

    fun toggleEpubPageSlider() {
        if (!isPageSliderVisible && currentRenderMode == RenderMode.PAGINATED && paginatedPagerState.pageCount <= 0) {
            showBanner("Book is not paginated yet.")
            return
        }

        val nextState = readerSliderToggleState(
            isCurrentlyToggledOn = isPageSliderVisible,
            currentPage = currentEpubSliderPage()
        )
        navigation.sliderStartPage = nextState.bookmarkPosition.startPage
        navigation.sliderCurrentPage = nextState.bookmarkPosition.currentPage
        isPageSliderVisible = nextState.isToggledOn
        showBars = true
        if (nextState.isToggledOn) {
            navigation.showFormatAdjustmentBars = false
        }
    }

    LaunchedEffect(isPageSliderVisible, epubSliderChromeVisible, currentRenderMode, currentPageInChapter, nativeVerticalCurrentPage, paginatedPagerState.currentPage, navigation.isFastScrubbing) {
        if (isPageSliderVisible && !epubSliderChromeVisible) {
            resetEpubSliderBookmark()
        } else if (epubSliderChromeVisible && !navigation.isFastScrubbing) {
            navigation.sliderCurrentPage = currentEpubSliderPage().toFloat()
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
                navigation.isNavigatingToPosition || navigation.chunkTargetOverride != null || !cfiToLoad.isNullOrBlank()
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
            webViewRefForTts = null
            chapterHead = ""
            chapterChunks = emptyList()
            chapterChunkElementStartIndices = emptyList()
            chapterChunkElementCounts = emptyList()
            autoScrollResumeJob.value?.cancel()
            autoScrollResumeJob.value = null
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
                        navigation.showInsufficientCreditsDialog = true
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

    LaunchedEffect(currentChapterIndex, bookReplacementSignature) {
        isChapterParsing = true
        isChapterReadyForBookmarkCheck = false
        navigation.activeFragmentId = null

        val result = loadChapterContent(
            context = context,
            epubBook = epubBook,
            chapterIndex = currentChapterIndex,
            chunkTargetOverride = navigation.chunkTargetOverride,
            isInitialCfiLoad = isInitialCfiLoad,
            cfiToLoad = cfiToLoad,
            locatorConverter = locatorConverter,
            bookReplacementPreferences = bookReplacementPreferences,
            bookReplacementFileId = bookId
        )

        chapterHead = result.head
        chapterChunks = result.chunks
        chapterChunkElementStartIndices = result.chunkElementStartIndices
        chapterChunkElementCounts = result.chunkElementCounts
        isChapterParsing = false

        if (initialScrollTargetForChapter == ChapterScrollPosition.END) {
            loadUpToChunkIndex = max(0, result.chunks.size - 1)
            loadedChunkCount = initialReaderLoadedChunkCount(result.chunks.size, loadUpToChunkIndex)
            topVisibleChunkIndex = loadUpToChunkIndex
        } else {
            loadUpToChunkIndex = result.startChunkIndex
            loadedChunkCount = initialReaderLoadedChunkCount(result.chunks.size, result.startChunkIndex)
            topVisibleChunkIndex = 0
        }

        Timber.tag("ReflowPaginationDiag").d("EpubReaderScreen: loadChapterContent finished. chapterChunks.size=${chapterChunks.size}, isChapterParsing=$isChapterParsing")

        if (navigation.chunkTargetOverride != null) {
            navigation.chunkTargetOverride = null
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
        systemUiMode = prefs.systemUiMode
    )

    LaunchedEffect(paginator, currentRenderMode, isPagerInitialized) {
        Timber.tag("ReflowPaginationDiag").d("EpubReaderScreen: Checking paginator init. currentRenderMode=$currentRenderMode, paginator=${paginator != null}, isPagerInitialized=$isPagerInitialized")
        Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d(
            "restore_check mode=$currentRenderMode hasPaginator=${paginator != null} initialized=$isPagerInitialized " +
                "lastKnown=$lastKnownLocator chapterSwitch=$navigation.chapterToLoadOnSwitch pageCount=${paginatedPagerState.pageCount}"
        )
        if (currentRenderMode == RenderMode.PAGINATED && paginator != null && !isPagerInitialized) {
            val bookPaginator = paginator as? BookPaginator
            val targetChapterIndex = lastKnownLocator?.chapterIndex
                ?: navigation.chapterToLoadOnSwitch
                ?: initialLocator?.chapterIndex
                ?: 0

            if (bookPaginator != null) {
                Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d(
                    "restore_wait chapter=$targetChapterIndex currentCount=${bookPaginator.chapterPageCounts[targetChapterIndex]} total=${bookPaginator.totalPageCount}"
                )
                val countReady = withTimeoutOrNull(5000L) {
                    snapshotFlow { bookPaginator.chapterPageCounts[targetChapterIndex] }
                        .filter { it != null && it > 0 }
                        .first()
                }
                Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d(
                    "restore_wait_done chapter=$targetChapterIndex count=$countReady total=${bookPaginator.totalPageCount}"
                )
            }

            val restoreLocator = lastKnownLocator
            val pageToScrollTo = restoreLocator?.let { locator ->
                Timber.d("Paginator ready. Finding page for locator: $locator")
                Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d("restore_resolve_stable_locator locator=$locator")
                (paginator as? BookPaginator)?.findStablePageForLocator(locator)
            } ?: run {
                Timber.d("Paginator ready, but no locator. Falling back to chapter start.")
                val chapterToLoad = navigation.chapterToLoadOnSwitch ?: initialLocator?.chapterIndex ?: 0
                val fallbackPage = (paginator as? BookPaginator)?.findStableChapterStartPage(chapterToLoad) ?: 0
                Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d(
                    "restore_resolve_stable_fallback chapter=$chapterToLoad page=$fallbackPage"
                )
                fallbackPage
            }

            @Suppress("SENSELESS_COMPARISON")
            if (pageToScrollTo != null) {
                val readyPageCount = if (paginatedPagerState.pageCount <= pageToScrollTo) {
                    Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d(
                        "restore_wait_page_count rawPage=$pageToScrollTo currentPageCount=${paginatedPagerState.pageCount} paginatorTotal=${bookPaginator?.totalPageCount}"
                    )
                    withTimeoutOrNull(2000L) {
                        snapshotFlow { paginatedPagerState.pageCount }
                            .filter { it > pageToScrollTo }
                            .first()
                    } ?: paginatedPagerState.pageCount
                } else {
                    paginatedPagerState.pageCount
                }
                val targetPage = pageToScrollTo.coerceIn(0, (readyPageCount - 1).coerceAtLeast(0))
                Timber.d("Scrolling to page: $targetPage")
                Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d(
                    "restore_scroll page=$targetPage rawPage=$pageToScrollTo pageCount=$readyPageCount locator=$restoreLocator"
                )
                delay(16)
                bookPaginator?.onUserScrolledTo(targetPage)
                paginatedPagerState.scrollToPage(targetPage)
            } else {
                Timber.w("Could not determine a page to scroll to.")
                Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).w(
                    "restore_failed targetChapter=$targetChapterIndex locator=$restoreLocator pageCount=${paginatedPagerState.pageCount}"
                )
            }

            delay(100)
            isPagerInitialized = true
            navigation.chapterToLoadOnSwitch = null
            Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d(
                "restore_complete currentPage=${paginatedPagerState.currentPage} pageCount=${paginatedPagerState.pageCount}"
            )
        }
    }

    LaunchedEffect(paginatedPagerState, paginator, currentRenderMode, isPagerInitialized, navigation.isPaginatedReconfigurationRestoring) {
        if (currentRenderMode != RenderMode.PAGINATED || paginator == null || !isPagerInitialized) {
            return@LaunchedEffect
        }
        snapshotFlow { paginatedPagerState.currentPage }
            .collectLatest { page ->
                if (!navigation.isPaginatedReconfigurationRestoring) {
                    (paginator as? BookPaginator)?.getLocatorForPage(page)?.let { locator ->
                        lastKnownLocator = locator
                        Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d(
                            "page_observed page=$page locator=$locator"
                        )
                    }
                } else {
                    Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d(
                        "page_observed_suppressed page=$page reason=reconfiguration_restore"
                    )
                }
            }
    }
    LaunchedEffect(
        paginatedPagerState.currentPage,
        paginator,
        navigation.isPaginatedReconfigurationRestoring,
        isPagerInitialized,
        currentRenderMode,
        paginatedPagerState.pageCount
    ) {
        val pageAtLaunch = paginatedPagerState.currentPage
        if (!shouldSavePaginatedOpenPosition(
                isPaginatedMode = currentRenderMode == RenderMode.PAGINATED,
                hasPaginator = paginator != null,
                isPagerInitialized = isPagerInitialized,
                isReconfigurationRestoring = navigation.isPaginatedReconfigurationRestoring,
                pageCount = paginatedPagerState.pageCount,
                pageToSave = pageAtLaunch
            )
        ) {
            Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d(
                "save_skip_initial page=$pageAtLaunch mode=$currentRenderMode hasPaginator=${paginator != null} " +
                    "initialized=$isPagerInitialized restoring=$navigation.isPaginatedReconfigurationRestoring pageCount=${paginatedPagerState.pageCount}"
            )
            return@LaunchedEffect
        }

        delay(1500L)
        val pageToSave = paginatedPagerState.currentPage
        if (!shouldSavePaginatedOpenPosition(
                isPaginatedMode = currentRenderMode == RenderMode.PAGINATED,
                hasPaginator = paginator != null,
                isPagerInitialized = isPagerInitialized,
                isReconfigurationRestoring = navigation.isPaginatedReconfigurationRestoring,
                pageCount = paginatedPagerState.pageCount,
                pageToSave = pageToSave
            )
        ) {
            Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d(
                "save_skip_after_delay launchPage=$pageAtLaunch currentPage=$pageToSave mode=$currentRenderMode " +
                    "hasPaginator=${paginator != null} initialized=$isPagerInitialized restoring=$navigation.isPaginatedReconfigurationRestoring " +
                    "pageCount=${paginatedPagerState.pageCount}"
            )
            return@LaunchedEffect
        }

        if (pageToSave != pageAtLaunch) {
            Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d(
                "save_skip_page_changed launchPage=$pageAtLaunch currentPage=$pageToSave"
            )
            return@LaunchedEffect
        }

        val locator = (paginator as? BookPaginator)?.getLocatorForPage(pageToSave)
        val chapterIndex = paginator!!.findChapterIndexForPage(pageToSave)

        if (locator != null && chapterIndex != null) {
            lastKnownLocator = locator
            val bookPaginator = paginator as? BookPaginator
            val progress = if (totalBookLengthChars > 0 && bookPaginator != null) {
                val completedCharsInPreviousChapters = chapters.take(chapterIndex).sumOf { it.plainTextCharacterCount().toLong() }
                val currentPageInChapter = (bookPaginator.chapterStartPageIndices[chapterIndex] ?: 0).let { pageToSave - it }
                val charsScrolledInCurrentChapter = bookPaginator.getCharactersScrolledInChapter(chapterIndex, currentPageInChapter)
                val isLastPageOfBook = pageToSave == paginatedPagerState.pageCount - 1
                mobileEpubCharacterProgress(
                    totalBookCharacters = totalBookLengthChars,
                    completedChapterCharacters = completedCharsInPreviousChapters,
                    currentChapterOffset = charsScrolledInCurrentChapter,
                    isAtEndOfBook = isLastPageOfBook
                )

            } else {
                0f
            }

            Timber.d("Auto-saving paginated position. Page: $pageToSave, Locator: $locator, Progress: $progress%"
            )
            Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).d(
                "save_position page=$pageToSave locator=$locator chapter=$chapterIndex progress=$progress"
            )
            onSavePosition(locator, null, progress)
        } else {
            Timber.w("Could not auto-save paginated position. Locator or chapterIndex was null.")
            Timber.tag(TAG_EPUB_PAGINATED_OPEN_DIAG).w(
                "save_failed page=$pageToSave locator=$locator chapter=$chapterIndex"
            )
        }
    }

    val pageInfoBarHeight = PAGE_INFO_BAR_HEIGHT + pageInfoCornerBottomPadding

    val isPageInfoVisible = shouldShowEpubPageInfoBar(
        pageInfoMode = prefs.pageInfoMode,
        showReaderChrome = showBars
    )

    LaunchedEffect(showBars, prefs.pageInfoMode, currentRenderMode, isNativeVerticalMode) {
        Timber.tag("ReaderInteractionDiag").d(
            "chrome_state mode=$currentRenderMode nativeVertical=$isNativeVerticalMode showBars=$showBars " +
                "prefs.pageInfoMode=$prefs.pageInfoMode pageInfoVisible=$isPageInfoVisible " +
                "reservePageInfoSpace=${shouldReserveEpubPageInfoBarSpace(prefs.pageInfoMode, showBars, isNativeVerticalMode)} " +
                "pagerInitialized=$isPagerInitialized reconfigurationRestoring=$navigation.isPaginatedReconfigurationRestoring"
        )
    }

    fun androidLocatorCfiToLocator(cfi: String): Locator? {
        val parts = cfi.takeIf { it.startsWith("android-locator:") }?.split(':') ?: return null
        return Locator(
            chapterIndex = parts.getOrNull(1)?.toIntOrNull() ?: return null,
            blockIndex = parts.getOrNull(2)?.toIntOrNull() ?: return null,
            charOffset = parts.getOrNull(3)?.toIntOrNull() ?: return null
        )
    }

    LaunchedEffect(bookmarks, paginator) {
        paginator ?: return@LaunchedEffect
        val bookPaginator = paginator as? BookPaginator
        if (bookPaginator == null) {
            Timber.w("Paginator is not a BookPaginator instance, cannot calculate bookmark page map.")
            return@LaunchedEffect
        }
        Timber.d("Paginator or bookmarks changed. Re-calculating bookmark page map for ${bookmarks.size} bookmarks.")
        val activeBookmarkCfis = bookmarks.map { it.cfi }.toSet()
        val newMap = bookmarkPageMap.filterKeys { it in activeBookmarkCfis }.toMutableMap()
        val newLocatorMap = bookmarkLocatorMap.filterKeys { it in activeBookmarkCfis }.toMutableMap()

        bookmarks.forEach { bookmark ->
            if (newMap.containsKey(bookmark.cfi) && newLocatorMap.containsKey(bookmark.cfi)) return@forEach

            scope.launch {
                val locator = androidLocatorCfiToLocator(bookmark.cfi)
                    ?: locatorConverter.getLocatorFromCfi(
                        book = epubBook,
                        chapterIndex = bookmark.chapterIndex,
                        cfi = bookmark.cfi
                    )

                if (locator != null) {
                    Timber.d("Bookmark map: Converted CFI '${bookmark.cfi}' to Locator: $locator")
                    newLocatorMap[bookmark.cfi] = locator
                    bookmarkLocatorMap = newLocatorMap.toMap()
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
        bookmarkPageMap = newMap.toMap()
        bookmarkLocatorMap = newLocatorMap.toMap()
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
                    if (navigation.activeFragmentId != active) {
                        Timber.tag("FRAG_NAV_DEBUG").d("P-Mode Active Anchor: $active")
                        navigation.activeFragmentId = active
                    }
                } else {
                    if (navigation.activeFragmentId != null) navigation.activeFragmentId = null
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
                    if (isNativeVerticalMode) {
                        scope.launch {
                            val pageToSave = nativeVerticalCurrentPage
                            val bookPaginator = paginator as? BookPaginator
                            val locator = currentNativeVerticalLocator()
                            val chapterIndex = locator?.chapterIndex ?: bookPaginator?.findChapterIndexForPage(pageToSave)

                            if (locator != null) {
                                val progress = nativeVerticalLocation?.progressPercent ?: if (chapterIndex == null || bookPaginator == null) {
                                    saveResolvedLocatorPosition(locator, null)
                                    onNavigateBack()
                                    return@launch
                                } else if (totalBookLengthChars > 0) {
                                    val completedCharsInPreviousChapters =
                                        chapters.take(chapterIndex).sumOf { it.plainTextCharacterCount().toLong() }
                                    val chapterStartPage = bookPaginator.chapterStartPageIndices[chapterIndex] ?: 0
                                    val currentPageInChapter = pageToSave - chapterStartPage
                                    val pageCharsScrolledInCurrentChapter =
                                        bookPaginator.getCharactersScrolledInChapter(chapterIndex, currentPageInChapter)
                                    val chapterChars =
                                        chapters.getOrNull(chapterIndex)?.plainTextCharacterCount()?.toLong()
                                            ?: Long.MAX_VALUE
                                    val locatorCharsScrolledInCurrentChapter = locator
                                        .takeIf { it.chapterIndex == chapterIndex }
                                        ?.charOffset
                                        ?.toLong()
                                        ?.coerceAtLeast(0L)
                                        ?.coerceAtMost(chapterChars)
                                    val charsScrolledInCurrentChapter =
                                        locatorCharsScrolledInCurrentChapter
                                            ?.coerceAtLeast(pageCharsScrolledInCurrentChapter)
                                            ?: pageCharsScrolledInCurrentChapter
                                    val isLastPageOfBook = pageToSave == nativeVerticalTotalPages - 1
                                    mobileEpubCharacterProgress(
                                        totalBookCharacters = totalBookLengthChars,
                                        completedChapterCharacters = completedCharsInPreviousChapters,
                                        currentChapterOffset = charsScrolledInCurrentChapter,
                                        isAtEndOfBook = isLastPageOfBook
                                    )
                                } else {
                                    nativeVerticalProgress
                                }

                                Timber.d("Final save for native vertical view. Page: $pageToSave, Locator: $locator, Progress: $progress%")
                                onSavePosition(locator, null, progress)
                            } else {
                                Timber.w("Final save for native vertical view failed. Locator is null.")
                            }
                            isSavingAndExiting = false
                            onNavigateBack()
                        }
                        return
                    }
                    webViewRefForTts?.evaluateJavascript(
                        "javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());",
                        null
                    )
                }

                RenderMode.PAGINATED -> {
                    scope.launch {
                        val pageToSave = paginatedPagerState.currentPage
                        val pageLocator = if (navigation.isPaginatedReconfigurationRestoring) {
                            null
                        } else {
                            (paginator as? BookPaginator)?.getLocatorForPage(pageToSave)
                        }
                        val locator = pageLocator ?: navigation.paginatedReconfigurationAnchor ?: lastKnownLocator
                        val chapterIndex = paginator?.findChapterIndexForPage(pageToSave)

                        if (locator != null) {
                            val bookPaginator = paginator as? BookPaginator
                            val progress = if (pageLocator == null || chapterIndex == null) {
                                saveResolvedLocatorPosition(locator, null)
                                onNavigateBack()
                                return@launch
                            } else if (totalBookLengthChars > 0 && bookPaginator != null) {
                                val completedCharsInPreviousChapters = chapters.take(chapterIndex).sumOf { it.plainTextCharacterCount().toLong() }
                                val currentPageInChapter = (bookPaginator.chapterStartPageIndices[chapterIndex] ?: 0).let { pageToSave - it }
                                val charsScrolledInCurrentChapter = bookPaginator.getCharactersScrolledInChapter(chapterIndex, currentPageInChapter)
                                val isLastPageOfBook = pageToSave == paginatedPagerState.pageCount - 1
                                mobileEpubCharacterProgress(
                                    totalBookCharacters = totalBookLengthChars,
                                    completedChapterCharacters = completedCharsInPreviousChapters,
                                    currentChapterOffset = charsScrolledInCurrentChapter,
                                    isAtEndOfBook = isLastPageOfBook
                                )

                            } else {
                                0f
                            }

                            Timber.d("Final save for paginated view. Page: $pageToSave, Locator: $locator, Progress: $progress%"
                            )
                            onSavePosition(locator, null, progress)
                        } else {
                            Timber.w("Final save for paginated view failed. Locator is null."
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

    fun SharedReaderLocator.toAndroidLocatorOrNull(): Locator? {
        val chapter = chapterIndex
        val block = blockIndex
        val offset = charOffset
        if (chapter != null && block != null && offset != null) {
            return Locator(
                chapterIndex = chapter,
                blockIndex = block,
                charOffset = offset
            )
        }
        val parts = cfi
            ?.takeIf { it.startsWith("android-locator:") }
            ?.split(':')
            ?: return null
        return Locator(
            chapterIndex = parts.getOrNull(1)?.toIntOrNull() ?: return null,
            blockIndex = parts.getOrNull(2)?.toIntOrNull() ?: return null,
            charOffset = parts.getOrNull(3)?.toIntOrNull() ?: return null
        )
    }

    fun currentEpubJumpLocator(): SharedReaderLocator? {
        return when (currentRenderMode) {
            RenderMode.VERTICAL_SCROLL -> {
                if (isNativeVerticalMode) {
                    val pageIndex = nativeVerticalLocation?.compatPageIndex ?: nativeVerticalCurrentPage.takeIf { it >= 0 }
                    val locator = currentNativeVerticalLocator()
                    locator?.toEpubJumpLocator(pageIndex = pageIndex)
                } else {
                    SharedReaderLocator(
                        chapterIndex = currentChapterIndex,
                        cfi = "android-scroll:$currentScrollYPosition"
                    )
                }
            }
            RenderMode.PAGINATED -> {
                val bookPaginator = paginator as? BookPaginator
                val currentPageIndex = authoritativePaginatedPageIndex(
                    currentPageIndex = paginatedPagerState.currentPage,
                    settledPageIndex = paginatedPagerState.settledPage,
                    isScrollInProgress = paginatedPagerState.isScrollInProgress
                )
                val currentChapterIndex = currentPageIndex?.let { bookPaginator?.findChapterIndexForPage(it) }
                paginatedEpubJumpLocator(
                    currentPageIndex = paginatedPagerState.currentPage,
                    settledPageIndex = paginatedPagerState.settledPage,
                    isScrollInProgress = paginatedPagerState.isScrollInProgress,
                    locatorForPage = { pageIndex -> bookPaginator?.getLocatorForPage(pageIndex) },
                    fallbackLocator = lastKnownLocator,
                    fallbackChapterIndex = currentChapterIndex
                )
            }
        }
    }

    fun chapterStartJumpLocator(chapterIndex: Int): SharedReaderLocator {
        return SharedReaderLocator(
            chapterIndex = chapterIndex,
            href = chapters.getOrNull(chapterIndex)?.absPath,
            cfi = "android-scroll:0"
        )
    }

    fun fragmentJumpLocator(chapterIndex: Int, fragment: String?, href: String? = null): SharedReaderLocator {
        return SharedReaderLocator(
            chapterIndex = chapterIndex,
            href = href ?: chapters.getOrNull(chapterIndex)?.absPath,
            cfi = fragment?.let { "android-fragment:$it" } ?: "android-scroll:0"
        )
    }

    fun cfiJumpLocator(chapterIndex: Int, cfi: String, textQuote: String? = null): SharedReaderLocator {
        return SharedReaderLocator(
            chapterIndex = chapterIndex,
            cfi = cfi,
            textQuote = textQuote
        )
    }

    fun recordEpubJump(
        target: SharedReaderLocator?,
        currentLocator: SharedReaderLocator? = currentEpubJumpLocator()
    ) {
        epubJumpHistory = epubJumpHistory.record(
            currentLocator = currentLocator,
            targetLocator = target,
            chapterCount = chapters.size
        )
    }

    fun paginatedJumpLocatorForPage(
        pageIndex: Int,
        targetLocator: Locator? = null,
        fallbackChapterIndex: Int? = null,
        allowPageFallback: Boolean = false
    ): SharedReaderLocator? {
        val safePageIndex = when {
            pageIndex < 0 -> return null
            paginatedPagerState.pageCount > 0 -> pageIndex.coerceIn(0, paginatedPagerState.pageCount - 1)
            else -> pageIndex
        }
        val bookPaginator = paginator as? BookPaginator
        val resolvedLocator = targetLocator ?: bookPaginator?.getLocatorForPage(safePageIndex)
        if (resolvedLocator != null) {
            return resolvedLocator.toEpubJumpLocator(pageIndex = safePageIndex)
        }
        if (!allowPageFallback) return null
        val chapterIndex = fallbackChapterIndex ?: bookPaginator?.findChapterIndexForPage(safePageIndex)
        return SharedReaderLocator(
            chapterIndex = chapterIndex,
            pageIndex = safePageIndex,
            cfi = "android-page:$safePageIndex"
        )
    }

    fun sliderJumpTargetForPage(page: Int): SharedReaderLocator? {
        val pageIndex = (page - 1).takeIf { it >= 0 } ?: return null
        return when {
            isNativeVerticalMode -> {
                val bookPaginator = paginator as? BookPaginator
                val targetLocator = bookPaginator?.getLocatorForPage(pageIndex)
                targetLocator?.toEpubJumpLocator(pageIndex = pageIndex)
                    ?: SharedReaderLocator(
                        chapterIndex = bookPaginator?.findChapterIndexForPage(pageIndex),
                        pageIndex = pageIndex,
                        cfi = "android-page:$pageIndex"
                    )
            }
            currentRenderMode == RenderMode.VERTICAL_SCROLL -> {
                SharedReaderLocator(
                    chapterIndex = currentChapterIndex,
                    cfi = "android-scroll:${(page - 1) * currentClientHeightValue}"
                )
            }
            else -> {
                val targetLocator = (paginator as? BookPaginator)?.getLocatorForPage(pageIndex)
                paginatedJumpLocatorForPage(
                    pageIndex = pageIndex,
                    targetLocator = targetLocator,
                    allowPageFallback = true
                )
            }
        }
    }

    suspend fun scrollPaginatedToJumpPage(
        pageIndex: Int,
        targetLocator: Locator? = null,
        fallbackToChapterStart: Boolean = false
    ) {
        if (paginatedPagerState.pageCount <= 0) return
        val targetPageIndex = pageIndex.coerceIn(0, paginatedPagerState.pageCount - 1)
        val bookPaginator = paginator as? BookPaginator
        val resolvedLocator = targetLocator
            ?: bookPaginator?.getLocatorForPage(targetPageIndex)
            ?: if (fallbackToChapterStart) {
                bookPaginator
                    ?.findChapterIndexForPage(targetPageIndex)
                    ?.let { Locator(chapterIndex = it, blockIndex = 0, charOffset = 0) }
            } else {
                null
            }

        if (resolvedLocator != null) {
            lastKnownLocator = resolvedLocator
        }
        val navigationEpoch = System.currentTimeMillis()
        paginatedExplicitNavigationEpoch = navigationEpoch
        paginatedExplicitNavigationAnchor = resolvedLocator
        Timber.tag(TAG_STABLE_PAGE_NAV).d(
            "external_scroll_request requestedPage=$pageIndex targetPage=$targetPageIndex anchor=$resolvedLocator fallbackToChapterStart=$fallbackToChapterStart pageCount=${paginatedPagerState.pageCount} epoch=$navigationEpoch"
        )
        bookPaginator?.onUserScrolledTo(targetPageIndex)
        paginatedPagerState.scrollToPage(targetPageIndex)
        Timber.tag(TAG_STABLE_PAGE_NAV).d(
            "external_scroll_complete targetPage=$targetPageIndex currentPage=${paginatedPagerState.currentPage} anchor=$resolvedLocator epoch=$navigationEpoch"
        )
    }

    fun SharedReaderLocator.epubJumpLabel(): String {
        val targetPageIndex = pageIndex
        val targetCfi = cfi.orEmpty()
        if (targetPageIndex != null && (targetCfi.isBlank() || targetCfi.startsWith("android-page:"))) {
            return context.getString(R.string.pdf_page_short, targetPageIndex + 1)
        }
        val chapter = chapterIndex
        return if (chapter != null) {
            chapters.getOrNull(chapter)?.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.chapter_number_format, chapter + 1)
        } else {
            context.getString(R.string.location_generic)
        }
    }

    fun injectVerticalChunksThrough(targetChunk: Int) {
        if (targetChunk < loadedChunkCount) return
        (loadedChunkCount..targetChunk).forEach { idx ->
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

    fun scrollCurrentVerticalChapterToFragment(fragment: String) {
        val escapedFragment = escapeJsString(fragment)
        val js = """
            (function() {
                var targetId = '$escapedFragment';
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
                injectVerticalChunksThrough(chunkIdx)
                val scrollJs = """
                    (function() {
                        var chunkIndex = $chunkIdx;
                        var fragmentId = '$escapedFragment';
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
    }

    fun scrollCurrentVerticalChapterToImage(image: EpubReaderImageReference) {
        val targetChunk = image.chunkIndex
        if (targetChunk != null && targetChunk >= 0) {
            injectVerticalChunksThrough(targetChunk)
        }
        val escapedSource = escapeJsString(image.sourcePath)
        val escapedOriginalSource = escapeJsString(image.originalSource)
        webViewRefForTts?.evaluateJavascript(
            "javascript:window.scrollToReaderImageSource('$escapedSource', ${image.ordinalInChapter}, '$escapedOriginalSource');",
            null
        )
    }

    fun navigateVerticalToImage(image: EpubReaderImageReference) {
        scope.launch {
            recordEpubJump(chapterStartJumpLocator(image.chapterIndex))
            clearPendingTtsRelocationState("sidebar_image_vertical")
            if (isNativeVerticalMode) {
                val bookPaginator = paginator as? BookPaginator
                val imagePage = bookPaginator?.findStablePageForImageSource(
                    chapterIndex = image.chapterIndex,
                    sourcePath = image.sourcePath,
                    elementId = image.elementId,
                    ordinalInChapter = image.ordinalInChapter
                )
                val targetPage = imagePage?.first
                    ?: bookPaginator?.findStableChapterStartPage(image.chapterIndex)
                requestNativeVerticalLocatorScroll(
                    locator = imagePage?.second,
                    fallbackPage = targetPage,
                    fallbackChapterIndex = image.chapterIndex
                )
                return@launch
            }
            imageToLoad = image
            cfiToLoad = null
            fragmentToLoad = null
            initialScrollTargetForChapter = null
            if (image.chapterIndex != currentChapterIndex) {
                navigation.chunkTargetOverride = image.chunkIndex?.coerceAtLeast(0)
                Timber.tag(TAG_LINK_NAV)
                    .d("[CHAPTER-NAV] source=SIDEBAR_IMAGE, from=$currentChapterIndex, to=${image.chapterIndex}, image='${image.sourceName()}'")
                currentScrollYPosition = 0
                currentScrollHeightValue = 0
                currentChapterIndex = image.chapterIndex
            } else {
                navigation.chunkTargetOverride = null
                scrollCurrentVerticalChapterToImage(image)
                imageToLoad = null
            }
        }
    }

    fun navigateVerticalToCfi(chapterIndex: Int, cfi: String) {
        scope.launch {
            val locator = locatorConverter.getLocatorFromCfi(epubBook, chapterIndex, cfi)
            if (isNativeVerticalMode) {
                val bookPaginator = paginator as? BookPaginator
                val targetPage = locator?.let { bookPaginator?.findStablePageForLocator(it) }
                    ?: bookPaginator?.findStableChapterStartPage(chapterIndex)
                requestNativeVerticalLocatorScroll(
                    locator = locator,
                    fallbackPage = targetPage,
                    fallbackChapterIndex = chapterIndex
                )
                return@launch
            }
            val targetChunk = locator?.let { it.blockIndex / 20 }
            cfiToLoad = cfi
            initialScrollTargetForChapter = null
            if (chapterIndex != currentChapterIndex) {
                navigation.chunkTargetOverride = targetChunk?.coerceAtLeast(0) ?: 0
                currentScrollYPosition = 0
                currentScrollHeightValue = 0
                currentChapterIndex = chapterIndex
            } else {
                if (targetChunk != null && targetChunk >= 0) {
                    injectVerticalChunksThrough(targetChunk)
                }
                webViewRefForTts?.evaluateJavascript(
                    "javascript:window.scrollToCfi('${escapeJsString(cfi)}');",
                    null
                )
            }
        }
    }

    fun navigateToEpubJumpLocator(locator: SharedReaderLocator) {
        scope.launch {
            val chapterIndex = locator.chapterIndex?.coerceIn(0, max(0, chapters.lastIndex))
            val cfi = locator.cfi.orEmpty()
            when (currentRenderMode) {
                RenderMode.VERTICAL_SCROLL -> {
                    clearPendingTtsRelocationState("epub_jump_history")
                    if (isNativeVerticalMode) {
                        val bookPaginator = paginator as? BookPaginator
                        val directPage = locator.pageIndex?.takeIf {
                            nativeVerticalTotalPages <= 0 || it in 0 until nativeVerticalTotalPages
                        }
                        val targetLocator = when {
                            cfi.startsWith("android-locator:") -> locator.toAndroidLocatorOrNull()
                            cfi.isNotBlank() && !cfi.startsWith("android-") && chapterIndex != null -> {
                                locatorConverter.getLocatorFromCfi(epubBook, chapterIndex, cfi)
                            }
                            cfi.startsWith("android-search:") && chapterIndex != null -> {
                                val targetChunk = cfi.split(':').getOrNull(1)?.toIntOrNull() ?: 0
                                Locator(chapterIndex, targetChunk.coerceAtLeast(0) * 20, 0)
                            }
                            cfi.startsWith("android-fragment:") && chapterIndex != null -> {
                                val fragment = cfi.substringAfter("android-fragment:")
                                bookPaginator?.findStableLocatorForAnchor(chapterIndex, fragment)
                            }
                            else -> null
                        }
                        val targetPage = targetLocator?.let { bookPaginator?.findStablePageForLocator(it) }
                            ?: directPage
                            ?: chapterIndex?.let { bookPaginator?.findStableChapterStartPage(it) }
                        requestNativeVerticalLocatorScroll(
                            locator = targetLocator,
                            fallbackPage = targetPage,
                            fallbackChapterIndex = chapterIndex
                        )
                        if (showBars) showBars = false
                        return@launch
                    }
                    when {
                        cfi.startsWith("android-scroll:") -> {
                            val scrollY = cfi.substringAfter("android-scroll:").toIntOrNull() ?: 0
                            initialScrollTargetForChapter = null
                            if (chapterIndex != null && chapterIndex != currentChapterIndex) {
                                currentScrollYPosition = scrollY
                                currentScrollHeightValue = 0
                                currentChapterIndex = chapterIndex
                            } else {
                                webViewRefForTts?.evaluateJavascript("javascript:window.scrollTo(0, $scrollY);", null)
                            }
                        }
                        cfi.startsWith("android-fragment:") -> {
                            val fragment = cfi.substringAfter("android-fragment:")
                            initialScrollTargetForChapter = null
                            fragmentToLoad = fragment
                            if (chapterIndex != null && chapterIndex != currentChapterIndex) {
                                currentScrollYPosition = 0
                                currentScrollHeightValue = 0
                                currentChapterIndex = chapterIndex
                            } else {
                                scrollCurrentVerticalChapterToFragment(fragment)
                            }
                        }
                        cfi.startsWith("android-search:") -> {
                            val parts = cfi.split(':')
                            val targetChunk = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            val occurrence = parts.getOrNull(2)?.toIntOrNull() ?: 0
                            initialScrollTargetForChapter = null
                            if (chapterIndex != null && chapterIndex != currentChapterIndex) {
                                navigation.chunkTargetOverride = targetChunk.coerceAtLeast(0)
                                searchHighlightTarget = searchState.searchResults.firstOrNull {
                                    it.locationInSource == chapterIndex &&
                                        it.chunkIndex == targetChunk &&
                                        it.occurrenceIndexInLocation == occurrence
                                }
                                currentScrollYPosition = 0
                                currentScrollHeightValue = 0
                                currentChapterIndex = chapterIndex
                            } else {
                                injectVerticalChunksThrough(targetChunk)
                                webViewRefForTts?.evaluateJavascript(
                                    "javascript:window.scrollToOccurrence($occurrence);",
                                    null
                                )
                            }
                        }
                        cfi.startsWith("android-locator:") -> {
                            val androidLocator = locator.toAndroidLocatorOrNull()
                            val targetCfi = androidLocator?.let { locatorConverter.getCfiFromLocator(epubBook, it) }
                            if (androidLocator != null && targetCfi != null) {
                                navigateVerticalToCfi(androidLocator.chapterIndex, targetCfi)
                            } else if (chapterIndex != null) {
                                initialScrollTargetForChapter = ChapterScrollPosition.START
                                currentScrollYPosition = 0
                                currentScrollHeightValue = 0
                                currentChapterIndex = chapterIndex
                            }
                        }
                        cfi.startsWith("android-page:") && chapterIndex != null -> {
                            initialScrollTargetForChapter = ChapterScrollPosition.START
                            currentScrollYPosition = 0
                            currentScrollHeightValue = 0
                            if (chapterIndex != currentChapterIndex) {
                                currentChapterIndex = chapterIndex
                            } else {
                                webViewRefForTts?.evaluateJavascript("javascript:window.scrollTo(0,0);", null)
                            }
                        }
                        cfi.isNotBlank() && !cfi.startsWith("android-") && chapterIndex != null -> navigateVerticalToCfi(chapterIndex, cfi)
                        chapterIndex != null -> {
                            initialScrollTargetForChapter = ChapterScrollPosition.START
                            currentScrollYPosition = 0
                            currentScrollHeightValue = 0
                            if (chapterIndex != currentChapterIndex) {
                                currentChapterIndex = chapterIndex
                            } else {
                                webViewRefForTts?.evaluateJavascript("javascript:window.scrollTo(0,0);", null)
                            }
                        }
                    }
                }

                RenderMode.PAGINATED -> {
                    val bookPaginator = paginator as? BookPaginator
                    val directPage = locator.pageIndex?.takeIf { it in 0 until paginatedPagerState.pageCount }
                    navigation.isNavigatingToPosition = true
                    try {
                        when {
                            cfi.startsWith("android-locator:") && bookPaginator != null -> {
                                val androidLocator = locator.toAndroidLocatorOrNull()
                                val targetPage = androidLocator?.let { bookPaginator.findStablePageForLocator(it) }
                                if (targetPage != null) {
                                    scrollPaginatedToJumpPage(targetPage, androidLocator)
                                } else if (directPage != null) {
                                    scrollPaginatedToJumpPage(directPage)
                                }
                            }
                            cfi.isNotBlank() && !cfi.startsWith("android-") && chapterIndex != null && bookPaginator != null -> {
                                val androidLocator = locatorConverter.getLocatorFromCfi(epubBook, chapterIndex, cfi)
                                val targetPage = androidLocator?.let { bookPaginator.findStablePageForLocator(it) }
                                if (targetPage != null) {
                                    scrollPaginatedToJumpPage(targetPage, androidLocator)
                                } else if (directPage != null) {
                                    scrollPaginatedToJumpPage(directPage)
                                } else {
                                    bookPaginator.findStableChapterStartPage(chapterIndex)?.let {
                                        scrollPaginatedToJumpPage(it, Locator(chapterIndex, 0, 0), fallbackToChapterStart = true)
                                    }
                                }
                            }
                            cfi.startsWith("android-fragment:") && directPage != null -> scrollPaginatedToJumpPage(directPage)
                            cfi.startsWith("android-search:") && directPage != null -> scrollPaginatedToJumpPage(directPage)
                            cfi.startsWith("android-page:") && directPage != null -> scrollPaginatedToJumpPage(directPage)
                            directPage != null -> scrollPaginatedToJumpPage(directPage)
                            chapterIndex != null && bookPaginator != null -> {
                                bookPaginator.findStableChapterStartPage(chapterIndex)?.let {
                                    scrollPaginatedToJumpPage(it, Locator(chapterIndex, 0, 0), fallbackToChapterStart = true)
                                }
                            }
                        }
                    } finally {
                        navigation.isNavigatingToPosition = false
                    }
                }
            }
            if (showBars) showBars = false
        }
    }

    fun goBackInEpubJumpHistory() {
        val refreshedHistory = epubJumpHistory.updateCurrentLocation(
            currentLocator = currentEpubJumpLocator(),
            chapterCount = chapters.size
        )
        val target = refreshedHistory.backLocator ?: return
        epubJumpHistory = refreshedHistory.stepBack()
        navigateToEpubJumpLocator(target)
    }

    fun goForwardInEpubJumpHistory() {
        val refreshedHistory = epubJumpHistory.updateCurrentLocation(
            currentLocator = currentEpubJumpLocator(),
            chapterCount = chapters.size
        )
        val target = refreshedHistory.forwardLocator ?: return
        epubJumpHistory = refreshedHistory.stepForward()
        navigateToEpubJumpLocator(target)
    }

    fun navigateToSearchResult(index: Int) {
        Timber.tag("NavDiag").d("navigateToSearchResult index: $index")
        val targetResult = searchState.searchResults.getOrNull(index)
        if (targetResult != null && currentRenderMode == RenderMode.VERTICAL_SCROLL) {
            if (isNativeVerticalMode) {
                scope.launch {
                    searchState.currentSearchResultIndex = index
                    val bookPaginator = paginator as? BookPaginator ?: return@launch
                    val exactLocator = bookPaginator.findStableLocatorForSearchResult(targetResult)
                    val pageIdx = exactLocator?.let { bookPaginator.findStablePageForLocator(it) }
                        ?: bookPaginator.findStablePageForSearchResult(targetResult)
                        ?: bookPaginator.findStablePageForLocator(
                            Locator(
                                targetResult.locationInSource,
                                targetResult.chunkIndex.coerceAtLeast(0) * 20,
                                0
                            )
                        )
                        ?: bookPaginator.findStableChapterStartPage(targetResult.locationInSource)
                        ?: return@launch
                    val scrollLocator = exactLocator
                        ?: bookPaginator.getLocatorForPage(pageIdx)
                        ?: Locator(targetResult.locationInSource, targetResult.chunkIndex.coerceAtLeast(0) * 20, 0)
                    recordEpubJump(
                        scrollLocator.toEpubJumpLocator(pageIndex = pageIdx)
                            .copy(textQuote = targetResult.snippet.text)
                    )
                    requestNativeVerticalLocatorScroll(
                        locator = scrollLocator,
                        fallbackPage = pageIdx,
                        fallbackChapterIndex = targetResult.locationInSource
                    )
                    searchHighlightTarget = targetResult
                    if (showBars) showBars = false
                }
                return
            }
            recordEpubJump(
                SharedReaderLocator(
                    chapterIndex = targetResult.locationInSource,
                    cfi = "android-search:${targetResult.chunkIndex}:${targetResult.occurrenceIndexInLocation}",
                    textQuote = targetResult.snippet.text
                )
            )
        }
        if (targetResult != null && currentRenderMode == RenderMode.PAGINATED) {
            scope.launch {
                searchState.currentSearchResultIndex = index
                navigation.isNavigatingToPosition = true
                try {
                    val bookPaginator = paginator as? BookPaginator ?: return@launch
                    val pageIdx = bookPaginator.findStablePageForSearchResult(targetResult) ?: return@launch
                    Timber.tag("NavDiag").d("onPaginatedScrollToPage pageIdx=$pageIdx")
                    val targetLocator = bookPaginator.getLocatorForPage(pageIdx)
                    paginatedJumpLocatorForPage(pageIdx, targetLocator)
                        ?.copy(textQuote = targetResult.snippet.text)
                        ?.let { recordEpubJump(it) }
                    scrollPaginatedToJumpPage(pageIdx, targetLocator)
                } finally {
                    navigation.isNavigatingToPosition = false
                }
            }
            return
        }
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
                navigation.chunkTargetOverride = chunkIdx
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
                val targetLocator = (paginator as? BookPaginator)?.getLocatorForPage(pageIdx)
                paginatedJumpLocatorForPage(pageIdx, targetLocator)
                    ?.copy(textQuote = searchState.searchResults.getOrNull(index)?.snippet?.text)
                    ?.let { recordEpubJump(it) }
                scrollPaginatedToJumpPage(pageIdx, targetLocator)
            }
        )
    }

    LaunchedEffect(paginatedPagerState.currentPage, currentRenderMode) {
        if (currentRenderMode == RenderMode.PAGINATED && prefs.volumeScrollEnabled) {
            delay(200)
            containerFocusRequester.requestFocus()
            Timber.d("Paginated: Page changed to ${paginatedPagerState.currentPage}, re-requesting focus for volume keys.")
        }
    }

    BackHandler(enabled = true) {
        when (selectMobileEpubReaderBackAction(drawerState.isOpen, isAutoScrollModeActive, searchState.isSearchActive)) {
            MobileEpubReaderBackAction.CLOSE_DRAWER -> scope.launch {
                Timber.d("Back pressed: Closing drawer")
                drawerState.close()
            }
            MobileEpubReaderBackAction.STOP_AUTO_SCROLL -> {
                isAutoScrollModeActive = false
                isAutoScrollPlaying = false
                showBars = true
            }
            MobileEpubReaderBackAction.CLOSE_SEARCH -> {
                searchState.isSearchActive = false
                searchState.onQueryChange("")
            }
            MobileEpubReaderBackAction.SAVE_AND_EXIT -> {
                Timber.d("Back pressed: Navigating back. Position will be saved first.")
                triggerSaveAndExit()
            }
        }
    }

    SharedMobileReaderDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            EpubReaderDrawerSheet(
                chapters = chapters,
                tableOfContents = epubBook.tableOfContents,
                activeFragmentId = navigation.activeFragmentId,
                readerImages = readerImages,
                bookmarks = bookmarks,
                userHighlights = userHighlights,
                currentChapterIndex = currentChapterIndex,
                currentChapterInPaginatedMode = currentChapterInPaginatedMode,
                renderMode = currentRenderMode,
                readerMotionPolicy = motionPolicy,
                activeHighlightPalette = currentHighlightPalette,
                onOpenPaletteManager = { showPaletteManager = true },
                onHighlightColorChange = onHighlightColorChange,
                onNavigateToImage = { image ->
                    scope.launch {
                        drawerState.close()
                        when (currentRenderMode) {
                            RenderMode.VERTICAL_SCROLL -> {
                                navigateVerticalToImage(image)
                            }
                            RenderMode.PAGINATED -> {
                                val bookPaginator = paginator as? BookPaginator
                                if (bookPaginator != null) {
                                    navigation.isNavigatingByToc = true
                                    try {
                                        val imagePage = bookPaginator.findStablePageForImageSource(
                                            chapterIndex = image.chapterIndex,
                                            sourcePath = image.sourcePath,
                                            elementId = image.elementId,
                                            ordinalInChapter = image.ordinalInChapter
                                        )
                                        if (imagePage != null) {
                                            val (pageIndex, locator) = imagePage
                                            paginatedJumpLocatorForPage(
                                                pageIndex = pageIndex,
                                                targetLocator = locator,
                                                allowPageFallback = true
                                            )?.let { recordEpubJump(it) }
                                            scrollPaginatedToJumpPage(pageIndex, locator)
                                        } else {
                                            val fallbackPage = bookPaginator.findStableChapterStartPage(image.chapterIndex)
                                            if (fallbackPage != null) {
                                                recordEpubJump(chapterStartJumpLocator(image.chapterIndex).copy(pageIndex = fallbackPage))
                                                scrollPaginatedToJumpPage(
                                                    fallbackPage,
                                                    Locator(image.chapterIndex, 0, 0),
                                                    fallbackToChapterStart = true
                                                )
                                            }
                                        }
                                    } finally {
                                        navigation.isNavigatingByToc = false
                                    }
                                }
                            }
                        }
                        if (showBars) showBars = false
                    }
                },
                onDownloadImage = { image ->
                    pendingImageDownload = image
                    imageSaveLauncher.launch(image.suggestedDownloadFileName())
                },
                onNavigateToTocEntry = { entry ->
                    scope.launch {
                        drawerState.close()
                        val targetChapterIndex = chapters.indexOfFirst { it.absPath == entry.absolutePath }

                        if (targetChapterIndex != -1) {
                            if (currentRenderMode == RenderMode.VERTICAL_SCROLL) {
                                if (isNativeVerticalMode) {
                                    val bookPaginator = paginator as? BookPaginator
                                    val targetLocator = bookPaginator?.findStableLocatorForAnchor(
                                        targetChapterIndex,
                                        entry.fragmentId
                                    )
                                    val targetPage = targetLocator?.let { bookPaginator.findStablePageForLocator(it) }
                                        ?: bookPaginator?.findStablePageForAnchor(
                                            targetChapterIndex,
                                            entry.fragmentId
                                        )
                                        ?: bookPaginator?.findStableChapterStartPage(targetChapterIndex)
                                    if (targetPage != null) {
                                        recordEpubJump(
                                            fragmentJumpLocator(targetChapterIndex, entry.fragmentId, entry.absolutePath)
                                                .copy(pageIndex = targetPage)
                                        )
                                        requestNativeVerticalLocatorScroll(
                                            locator = targetLocator ?: bookPaginator?.getLocatorForPage(targetPage),
                                            fallbackPage = targetPage,
                                            fallbackChapterIndex = targetChapterIndex
                                        )
                                    }
                                    if (showBars) showBars = false
                                    return@launch
                                }
                                recordEpubJump(fragmentJumpLocator(targetChapterIndex, entry.fragmentId, entry.absolutePath))
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

                                    navigation.isNavigatingByToc = true
                                    try {
                                        val targetPage = bookPaginator.findStablePageForAnchor(targetChapterIndex, entry.fragmentId)
                                        if (targetPage != null) {
                                            recordEpubJump(
                                                fragmentJumpLocator(targetChapterIndex, entry.fragmentId, entry.absolutePath)
                                                    .copy(pageIndex = targetPage)
                                            )
                                            Timber.tag(TAG_LINK_NAV)
                                                .d("[CHAPTER-NAV] source=TOC_ENTRY_PAGINATED, from=$currentChapterIndex, to=$targetChapterIndex, page=$targetPage, anchor='${entry.fragmentId}', label='${entry.label}'")
                                            Timber.tag("TOC_NAV_DEBUG").d("Scrolling Pager to page: $targetPage")
                                            val targetLocator = bookPaginator.getLocatorForPage(targetPage)
                                                ?: if (entry.fragmentId == null) Locator(targetChapterIndex, 0, 0) else null
                                            scrollPaginatedToJumpPage(
                                                targetPage,
                                                targetLocator,
                                                fallbackToChapterStart = entry.fragmentId == null
                                            )
                                        }
                                    } finally {
                                        navigation.isNavigatingByToc = false
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
                                if (isNativeVerticalMode) {
                                    val bookPaginator = paginator as? BookPaginator
                                    val targetPage = bookPaginator?.findStableChapterStartPage(index)
                                    if (targetPage != null) {
                                        recordEpubJump(chapterStartJumpLocator(index).copy(pageIndex = targetPage))
                                        requestNativeVerticalLocatorScroll(
                                            locator = bookPaginator.getLocatorForPage(targetPage) ?: Locator(index, 0, 0),
                                            fallbackPage = targetPage,
                                            fallbackChapterIndex = index
                                        )
                                        if (showBars) showBars = false
                                    }
                                    return@launch
                                }
                                if (index != currentChapterIndex) {
                                    recordEpubJump(chapterStartJumpLocator(index))
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
                                        navigation.isNavigatingByToc = true
                                        try {
                                            val targetPage = bookPaginator.findStableChapterStartPage(index)
                                            if (targetPage != null) {
                                                recordEpubJump(chapterStartJumpLocator(index).copy(pageIndex = targetPage))
                                                Timber.tag(TAG_LINK_NAV)
                                                    .d("[CHAPTER-NAV] source=SIDEBAR_CHAPTER_PAGINATED, from=$currentFromPager, to=$index, page=$targetPage")
                                                scrollPaginatedToJumpPage(targetPage, Locator(index, 0, 0), fallbackToChapterStart = true)
                                                if (showBars) showBars = false
                                            }
                                        } finally {
                                            navigation.isNavigatingByToc = false
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
                                if (isNativeVerticalMode) {
                                    recordEpubJump(cfiJumpLocator(bookmark.chapterIndex, bookmark.cfi, bookmark.snippet))
                                    val bookPaginator = paginator as? BookPaginator
                                    val locator = androidLocatorCfiToLocator(bookmark.cfi)
                                        ?: locatorConverter.getLocatorFromCfi(
                                            epubBook,
                                            bookmark.chapterIndex,
                                            bookmark.cfi
                                        )
                                    val targetPage = locator?.let { bookPaginator?.findStablePageForLocator(it) }
                                        ?: bookPaginator?.findStableChapterStartPage(bookmark.chapterIndex)
                                    requestNativeVerticalLocatorScroll(
                                        locator = locator,
                                        fallbackPage = targetPage,
                                        fallbackChapterIndex = bookmark.chapterIndex
                                    )
                                    return@launch
                                }
                                recordEpubJump(cfiJumpLocator(bookmark.chapterIndex, bookmark.cfi, bookmark.snippet))
                                Timber.tag("BookmarkDiagnosis").d("Navigating to ${bookmark.cfi}")
                                cfiToLoad = bookmark.cfi

                                val locator = locatorConverter.getLocatorFromCfi(epubBook, bookmark.chapterIndex, bookmark.cfi)
                                val targetChunk = locator?.let { it.blockIndex / 20 }

                                if (bookmark.chapterIndex != currentChapterIndex) {
                                    Timber.tag(TAG_LINK_NAV)
                                        .d("[CHAPTER-NAV] source=BOOKMARK, from=$currentChapterIndex, to=${bookmark.chapterIndex}, cfi='${bookmark.cfi}', label='${bookmark.label}'")
                                    navigation.chunkTargetOverride = if (targetChunk != null && targetChunk >= 0) {
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
                                        navigation.isNavigatingToPosition = true

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
                                            if (navigation.isNavigatingToPosition) {
                                                navigation.isNavigatingToPosition = false
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
                                recordEpubJump(cfiJumpLocator(bookmark.chapterIndex, bookmark.cfi, bookmark.snippet))
                                Timber.d("P-Mode Click: Navigating to bookmark. Chapter: ${bookmark.chapterIndex}, CFI: '${bookmark.cfi}'")
                                navigation.isNavigatingToPosition = true
                                try {
                                    val bookPaginator = paginator as? BookPaginator
                                    val locator = androidLocatorCfiToLocator(bookmark.cfi)
                                        ?: locatorConverter.getLocatorFromCfi(
                                            book = epubBook,
                                            chapterIndex = bookmark.chapterIndex,
                                            cfi = bookmark.cfi
                                        )

                                    if (locator != null && bookPaginator != null) {
                                        Timber.d("P-Mode Click: Successfully converted CFI to Locator: $locator")
                                        val pageIndex = bookPaginator.findStablePageForLocator(locator)
                                        if (pageIndex != null) {
                                            Timber.d("P-Mode Click: Paginator found page $pageIndex for locator. Scrolling.")
                                            scrollPaginatedToJumpPage(pageIndex, locator)
                                        } else {
                                            Timber.w("P-Mode Click: Paginator could not find a page for the locator. Falling back to chapter start.")
                                            val chapterStartPage = bookPaginator.findStableChapterStartPage(bookmark.chapterIndex)
                                            if (chapterStartPage != null) {
                                                scrollPaginatedToJumpPage(chapterStartPage, Locator(bookmark.chapterIndex, 0, 0), fallbackToChapterStart = true)
                                            }
                                        }
                                    } else {
                                        Timber.w("P-Mode Click: Failed to convert CFI to Locator. Falling back to stable chapter start.")
                                        val fallbackPage = bookPaginator?.findStableChapterStartPage(bookmark.chapterIndex)
                                        if (fallbackPage != null) {
                                            scrollPaginatedToJumpPage(fallbackPage, Locator(bookmark.chapterIndex, 0, 0), fallbackToChapterStart = true)
                                        }
                                    }
                                } finally {
                                    navigation.isNavigatingToPosition = false
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
                                if (isNativeVerticalMode) {
                                    recordEpubJump(cfiJumpLocator(highlight.chapterIndex, highlight.cfi, highlight.text))
                                    val bookPaginator = paginator as? BookPaginator
                                    val locator = locatorConverter.getLocatorFromCfi(epubBook, highlight.chapterIndex, highlight.cfi)
                                    val targetPage = locator?.let { bookPaginator?.findStablePageForLocator(it) }
                                        ?: bookPaginator?.findStableChapterStartPage(highlight.chapterIndex)
                                    requestNativeVerticalLocatorScroll(
                                        locator = locator,
                                        fallbackPage = targetPage,
                                        fallbackChapterIndex = highlight.chapterIndex
                                    )
                                    return@launch
                                }
                                recordEpubJump(cfiJumpLocator(highlight.chapterIndex, highlight.cfi, highlight.text))
                                cfiToLoad = highlight.cfi
                                val locator = locatorConverter.getLocatorFromCfi(epubBook, highlight.chapterIndex, highlight.cfi)
                                val targetChunk = locator?.let { it.blockIndex / 20 }

                                if (highlight.chapterIndex != currentChapterIndex) {
                                    Timber.tag(TAG_LINK_NAV)
                                        .d("[CHAPTER-NAV] source=HIGHLIGHT, from=$currentChapterIndex, to=${highlight.chapterIndex}, cfi='${highlight.cfi}'")
                                    navigation.chunkTargetOverride = if (targetChunk != null && targetChunk >= 0) targetChunk else 0
                                    currentScrollYPosition = 0
                                    currentScrollHeightValue = 0
                                    currentChapterIndex = highlight.chapterIndex
                                } else {
                                    if (targetChunk != null && targetChunk >= 0) {
                                        navigation.isNavigatingToPosition = true

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
                                            if (navigation.isNavigatingToPosition) {
                                                navigation.isNavigatingToPosition = false
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
                                recordEpubJump(cfiJumpLocator(highlight.chapterIndex, highlight.cfi, highlight.text))
                                navigation.isNavigatingToPosition = true
                                try {
                                    val bookPaginator = paginator as? BookPaginator
                                    val locator = locatorConverter.getLocatorFromCfi(epubBook, highlight.chapterIndex, highlight.cfi)
                                    if (locator != null && bookPaginator != null) {
                                        val pageIndex = bookPaginator.findStablePageForLocator(locator)
                                        if (pageIndex != null) {
                                            scrollPaginatedToJumpPage(pageIndex, locator)
                                        } else {
                                            val chapterStartPage = bookPaginator.findStableChapterStartPage(highlight.chapterIndex)
                                            if (chapterStartPage != null) {
                                                scrollPaginatedToJumpPage(chapterStartPage, Locator(highlight.chapterIndex, 0, 0), fallbackToChapterStart = true)
                                            }
                                        }
                                    } else {
                                        val fallbackPage = bookPaginator?.findStableChapterStartPage(highlight.chapterIndex)
                                        if (fallbackPage != null) {
                                            scrollPaginatedToJumpPage(fallbackPage, Locator(highlight.chapterIndex, 0, 0), fallbackToChapterStart = true)
                                        }
                                    }
                                } finally {
                                    navigation.isNavigatingToPosition = false
                                }
                            }
                        }
                        if (showBars) showBars = false
                    }
                },
                onDeleteBookmark = { bookmarkToDelete ->
                    bookmarks = bookmarks - bookmarkToDelete
                    bookmarkPageMap = bookmarkPageMap - bookmarkToDelete.cfi
                    bookmarkLocatorMap = bookmarkLocatorMap - bookmarkToDelete.cfi
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
                    navigation.highlightToNoteCfi = highlight.cfi
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

        prefs.volumeScrollEnabled &&
                currentRenderMode == RenderMode.VERTICAL_SCROLL &&
                !isTtsSessionActive &&
                !isMusicActive

        LaunchedEffect(Unit) {
            containerFocusRequester.requestFocus()
        }

        LaunchedEffect(prefs.volumeScrollEnabled) {
            if (prefs.volumeScrollEnabled) {
                containerFocusRequester.requestFocus()
                Timber.d("Volume scroll enabled. Re-requesting focus on the reader container.")
            }
        }

        fun generateSummaryFromPlainChapter(chapterIndex: Int?, force: Boolean) {
            scope.launch {
                val resolvedChapterIndex = chapterIndex
                if (resolvedChapterIndex == null) {
                    summarizationResult =
                        SummarizationResult(error = context.getString(R.string.error_could_not_determine_chapter))
                    isSummarizationLoading = false
                    return@launch
                }

                val cached = if (!force) summaryCacheManager.getSummary(
                    epubBook.title,
                    resolvedChapterIndex
                ) else null
                if (cached != null) {
                    summarizationResult = SummarizationResult(summary = cached, isCacheHit = true)
                    isSummarizationLoading = false
                    return@launch
                }

                val token = viewModel.getAuthToken()
                val text = paginator?.getPlainTextForChapter(resolvedChapterIndex)
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
                                cost = cost,
                                freeRemaining = freeRemaining
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
                                navigation.showInsufficientCreditsDialog = true
                                showAiHubSheet = false
                                isSummarizationLoading = false
                            } else {
                                summarizationResult = SummarizationResult(error = error)
                            }
                        },
                        onFinish = {
                            isSummarizationLoading = false
                            val fullSummary = finalSummaryBuilder.toString()
                            if (fullSummary.isNotBlank()) {
                                val chapterTitle =
                                    chapters.getOrNull(resolvedChapterIndex)?.title
                                        ?: context.getString(R.string.chapter_number_format, resolvedChapterIndex + 1)
                                summaryCacheManager.saveSummary(
                                    epubBook.title,
                                    resolvedChapterIndex,
                                    chapterTitle,
                                    fullSummary
                                )
                            }
                        }
                    )
                } else {
                    summarizationResult =
                        SummarizationResult(error = context.getString(R.string.error_could_not_get_chapter_content))
                    isSummarizationLoading = false
                }
            }
        }

        val handleGenerateSummary: (Boolean) -> Unit = { force ->
            if (BuildConfig.FLAVOR != "oss" && !isProUser && credits <= 0) {
                navigation.showInsufficientCreditsDialog = true
                showAiHubSheet = false
            } else {
                showAiHubSheet = true
                isSummarizationLoading = true
                summarizationResult = null
                when (currentRenderMode) {
                    RenderMode.VERTICAL_SCROLL -> {
                        if (isNativeVerticalMode) {
                            generateSummaryFromPlainChapter(
                                currentNativeVerticalLocator()?.chapterIndex ?: currentChapterIndex,
                                force
                            )
                        } else {
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
                                        SummarizationResult(error = context.getString(R.string.error_webview_not_available))
                                }
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
                                                navigation.showInsufficientCreditsDialog = true
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
                                                        ?: context.getString(R.string.chapter_number_format, chapterIndex + 1)
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
                                        SummarizationResult(error = context.getString(R.string.error_could_not_get_chapter_content))
                                    isSummarizationLoading = false
                                }
                            } else {
                                summarizationResult =
                                    SummarizationResult(error = context.getString(R.string.error_could_not_determine_chapter))
                                isSummarizationLoading = false
                            }
                        }
                    }
                }
            }
        }

        val handleGenerateRecap: () -> Unit = {
            if (BuildConfig.FLAVOR != "oss" && credits <= 0) {
                navigation.showInsufficientCreditsDialog = true
                showAiHubSheet = false
            } else {
                showAiHubSheet = true
                when (currentRenderMode) {
                    RenderMode.VERTICAL_SCROLL -> {
                        if (isNativeVerticalMode) {
                            val bookPaginator = paginator as? BookPaginator
                            val locator = currentNativeVerticalLocator()
                            val chapterIndex = locator?.chapterIndex ?: currentChapterIndex
                            if (bookPaginator != null) {
                                val charsScrolled = locator?.charOffset?.coerceAtLeast(0)
                                    ?: run {
                                        val startPage = bookPaginator.chapterStartPageIndices[chapterIndex] ?: 0
                                        val currentPageInChapter = nativeVerticalCurrentPage - startPage
                                        bookPaginator.getCharactersScrolledInChapter(
                                            chapterIndex,
                                            currentPageInChapter
                                        ).toInt()
                                    }
                                runRecap(chapterIndex, charsScrolled)
                            } else {
                                showBanner("Wait for book to load fully.", isError = true)
                            }
                        } else {
                            isRequestingRecapCfi = true
                            webViewRefForTts?.evaluateJavascript(
                                "javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());",
                                null
                            )
                        }
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
                            showBanner("Wait for book to load fully.", isError = true)
                        }
                    }
                }
            }
        }

        SharedMobileReaderScaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.statusBars,
        ) { scaffoldPaddingValues ->
            val currentTopPadding = scaffoldPaddingValues.calculateTopPadding()
            var stableTopPadding by remember { mutableStateOf(0.dp) }
            if (currentTopPadding > stableTopPadding) {
                stableTopPadding = currentTopPadding
            }

            val stableChromeTopPadding = if (prefs.systemUiMode == SystemUiMode.HIDDEN) {
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
            val effectiveTopPadding = when {
                currentRenderMode == RenderMode.PAGINATED -> stableChromeTopPadding
                isNativeVerticalMode -> stableChromeTopPadding
                else -> currentTopPadding
            }

            val epubJumpBackLabel = epubJumpHistory.backLocator?.epubJumpLabel()
            val epubJumpForwardLabel = epubJumpHistory.forwardLocator?.epubJumpLabel()
            val isEpubJumpHistoryVisible = showBars && !searchState.isSearchActive && (epubJumpBackLabel != null || epubJumpForwardLabel != null)
            val keyboardLineScrollPx = with(density) {
                (configuration.screenHeightDp.dp.toPx() * 0.16f).roundToInt().coerceAtLeast(96)
            }
            val keyboardPageScrollPx = with(density) {
                (configuration.screenHeightDp.dp.toPx() * 0.82f).roundToInt().coerceAtLeast(keyboardLineScrollPx)
            }

            fun scrollVerticalReaderBy(deltaPx: Int) {
                if (isNativeVerticalMode) {
                    verticalScrollRequests.nativeVerticalScrollDeltaRequestId += 1L
                    verticalScrollRequests.nativeVerticalScrollDeltaAnimated = false
                    verticalScrollRequests.nativeVerticalScrollDeltaRequest = deltaPx.toFloat()
                } else {
                    webViewRefForTts?.evaluateJavascript(
                        "window.scrollBy({ top: $deltaPx, behavior: '${motionPolicy.webViewScrollBehavior()}' });",
                        null
                    )
                }
            }

            fun navigateReaderPage(targetPage: Int) {
                when {
                    isNativeVerticalMode -> {
                        val lastPage = (nativeVerticalTotalPages - 1).coerceAtLeast(0)
                        verticalScrollRequests.nativeVerticalScrollRequest = targetPage.coerceIn(0, lastPage)
                    }
                    currentRenderMode == RenderMode.VERTICAL_SCROLL -> {
                        scrollVerticalReaderBy((targetPage - nativeVerticalCurrentPage).coerceIn(-1, 1) * keyboardPageScrollPx)
                    }
                    else -> {
                        scope.launch {
                            val pageCount = paginatedPagerState.pageCount
                            if (pageCount <= 0) return@launch
                            val page = targetPage.coerceIn(0, pageCount - 1)
                            if (page != paginatedPagerState.currentPage) {
                                if (motionPolicy.shouldAnimate(prefs.isPageTurnAnimationEnabled)) {
                                    paginatedPagerState.animateScrollToPage(page, animationSpec = tween(700))
                                } else {
                                    paginatedPagerState.scrollToPage(page)
                                }
                            }
                        }
                    }
                }
            }

            fun navigateReaderPageBy(delta: Int) {
                when {
                    isNativeVerticalMode -> navigateReaderPage(nativeVerticalCurrentPage + delta)
                    currentRenderMode == RenderMode.VERTICAL_SCROLL -> scrollVerticalReaderBy(delta * keyboardPageScrollPx)
                    else -> navigateReaderPage(paginatedPagerState.currentPage + delta)
                }
            }

            fun navigateReaderBoundary(first: Boolean) {
                when {
                    isNativeVerticalMode -> navigateReaderPage(if (first) 0 else nativeVerticalTotalPages - 1)
                    currentRenderMode == RenderMode.VERTICAL_SCROLL -> {
                        val script = if (first) {
                            "window.scrollTo({ top: 0, behavior: '${motionPolicy.webViewScrollBehavior()}' });"
                        } else {
                            "window.scrollTo({ top: document.documentElement.scrollHeight, behavior: '${motionPolicy.webViewScrollBehavior()}' });"
                        }
                        webViewRefForTts?.evaluateJavascript(script, null)
                    }
                    else -> navigateReaderPage(if (first) 0 else paginatedPagerState.pageCount - 1)
                }
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
                        volumeScrollEnabled = prefs.volumeScrollEnabled,
                        renderMode = currentRenderMode,
                        isTtsActive = isTtsSessionActive,
                        isMusicActive = isMusicActive,
                        currentScrollY = currentScrollYPosition,
                        currentScrollHeight = currentScrollHeightValue,
                        currentClientHeight = currentClientHeightValue,
                        currentChapterIndex = currentChapterIndex,
                        totalChapters = chapters.size,
                        onScrollBy = { amount ->
                            if (isNativeVerticalMode) {
                                verticalScrollRequests.nativeVerticalScrollDeltaRequestId += 1L
                                verticalScrollRequests.nativeVerticalScrollDeltaAnimated = false
                                verticalScrollRequests.nativeVerticalScrollDeltaRequest = amount.toFloat()
                            } else {
                                webViewRefForTts?.evaluateJavascript(
                                    "window.scrollBy({ top: $amount, behavior: '${motionPolicy.webViewScrollBehavior()}' });",
                                    null
                                )
                            }
                        },
                        onNavigateChapter = { offset, target ->
                            scope.launch {
                                clearPendingTtsRelocationState("manual_chapter_change")
                                if (isNativeVerticalMode) {
                                    if (chapters.isNotEmpty()) {
                                        val targetChapter = (currentChapterIndex + offset).coerceIn(0, chapters.lastIndex)
                                        val targetPage = (paginator as? BookPaginator)
                                            ?.findStableChapterStartPage(targetChapter)
                                        if (targetPage != null) {
                                            requestNativeVerticalLocatorScroll(
                                                locator = Locator(targetChapter, 0, 0),
                                                fallbackPage = targetPage,
                                                fallbackChapterIndex = targetChapter
                                            )
                                        }
                                    }
                                } else {
                                    initialScrollTargetForChapter = target
                                    currentScrollYPosition = 0
                                    currentScrollHeightValue = 0
                                    currentChapterIndex += offset
                                }
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
                                        if (motionPolicy.shouldAnimate(prefs.isPageTurnAnimationEnabled)) {
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
                                    if (motionPolicy.shouldAnimate(prefs.isPageTurnAnimationEnabled)) {
                                        paginatedPagerState.animateScrollToPage(targetPage, animationSpec = tween(700))
                                    } else paginatedPagerState.scrollToPage(targetPage)
                                }
                            }
                        }
                    )
                    .epubReaderKeyboardNavigationHandler(
                        enabled = !searchState.isSearchActive,
                        renderMode = currentRenderMode,
                        isRightToLeftPagination = prefs.rightToLeftPagination,
                        verticalLineScrollPx = keyboardLineScrollPx,
                        onVerticalScrollBy = ::scrollVerticalReaderBy,
                        onNextPage = { navigateReaderPageBy(1) },
                        onPreviousPage = { navigateReaderPageBy(-1) },
                        onFirstPage = { navigateReaderBoundary(first = true) },
                        onLastPage = { navigateReaderBoundary(first = false) }
                    )
            ) {
                EpubReaderRenderSurfaces(
                    addBookmarkRequestState = addBookmarkRequestState,
                    bookReplacementPreferencesState = bookReplacementPreferencesState,
                    bookmarksState = bookmarksState,
                    cfiToLoadState = cfiToLoadState,
                    chapterChunkElementCountsState = chapterChunkElementCountsState,
                    chapterChunkElementStartIndicesState = chapterChunkElementStartIndicesState,
                    chapterChunksState = chapterChunksState,
                    chapterHeadState = chapterHeadState,
                    currentChapterIndexState = currentChapterIndexState,
                    currentClientHeightValueState = currentClientHeightValueState,
                    currentHighlightPaletteState = currentHighlightPaletteState,
                    currentRenderModeState = currentRenderModeState,
                    currentScrollHeightValueState = currentScrollHeightValueState,
                    currentScrollYPositionState = currentScrollYPositionState,
                    fragmentToLoadState = fragmentToLoadState,
                    imageToLoadState = imageToLoadState,
                    initialScrollTargetForChapterState = initialScrollTargetForChapterState,
                    isAutoScrollModeActiveState = isAutoScrollModeActiveState,
                    isAutoScrollPlayingState = isAutoScrollPlayingState,
                    isChapterParsingState = isChapterParsingState,
                    isChapterReadyForBookmarkCheckState = isChapterReadyForBookmarkCheckState,
                    isMusicianModeState = isMusicianModeState,
                    isPagerInitializedState = isPagerInitializedState,
                    isRecapLoadingState = isRecapLoadingState,
                    isRequestingRecapCfiState = isRequestingRecapCfiState,
                    isSavingAndExitingState = isSavingAndExitingState,
                    isSummarizationLoadingState = isSummarizationLoadingState,
                    isSwitchingToPaginatedState = isSwitchingToPaginatedState,
                    lastHighlightClickTimeState = lastHighlightClickTimeState,
                    lastKnownLocatorState = lastKnownLocatorState,
                    lastScrollHideTimeState = lastScrollHideTimeState,
                    loadUpToChunkIndexState = loadUpToChunkIndexState,
                    loadedChunkCountState = loadedChunkCountState,
                    nativeVerticalCurrentPageState = nativeVerticalCurrentPageState,
                    nativeVerticalLocationState = nativeVerticalLocationState,
                    nativeVerticalProgressState = nativeVerticalProgressState,
                    nativeVerticalTotalPagesState = nativeVerticalTotalPagesState,
                    paginatedExplicitNavigationAnchorState = paginatedExplicitNavigationAnchorState,
                    paginatedExplicitNavigationEpochState = paginatedExplicitNavigationEpochState,
                    paginatorState = paginatorState,
                    pullToNextProgressState = pullToNextProgressState,
                    pullToPrevProgressState = pullToPrevProgressState,
                    recapResultState = recapResultState,
                    searchHighlightTargetState = searchHighlightTargetState,
                    showAiHubSheetState = showAiHubSheetState,
                    showBarsState = showBarsState,
                    showDictionaryUpsellDialogState = showDictionaryUpsellDialogState,
                    summarizationResultState = summarizationResultState,
                    topVisibleChunkIndexState = topVisibleChunkIndexState,
                    ttsChapterIndexState = ttsChapterIndexState,
                    ttsShouldStartOnChapterLoadState = ttsShouldStartOnChapterLoadState,
                    userStoppedTtsState = userStoppedTtsState,
                    webViewRefForTtsState = webViewRefForTtsState,
                    activeTextureAlpha = activeTextureAlpha,
                    activeTextureId = activeTextureId,
                    bookId = bookId,
                    bookReplacementSignature = bookReplacementSignature,
                    chapters = chapters,
                    containerFocusRequester = containerFocusRequester,
                    context = context,
                    currentPageInChapter = currentPageInChapter,
                    dragThresholdPx = dragThresholdPx,
                    effectiveBg = effectiveBg,
                    effectiveText = effectiveText,
                    epubFontFaceCss = epubFontFaceCss,
                    focusManager = focusManager,
                    format = format,
                    pageInfoBarHeight = pageInfoBarHeight,
                    prefs = prefs,
                    readerCacheBookId = readerCacheBookId,
                    scope = scope,
                    summaryCacheManager = summaryCacheManager,
                    totalBookLengthChars = totalBookLengthChars,
                    verticalScrollRequests = verticalScrollRequests,
                    window = window,
                    motionPolicy = motionPolicy,
                    navigation = navigation,
                    searchState = searchState,
                    locatorConverter = locatorConverter,
                    paginatedPagerState = paginatedPagerState,
                    userHighlights = userHighlights,
                    epubBook = epubBook,
                    viewModel = viewModel,
                    isNativeVerticalMode = isNativeVerticalMode,
                    isDarkTheme = isDarkTheme,
                    isProUser = isProUser,
                    credits = credits,
                    coverImagePath = coverImagePath,
                    onSavePosition = onSavePosition,
                    onRenderModeChange = onRenderModeChange,
                    onNavigateBack = onNavigateBack,
                    currentChapterInPaginatedMode = currentChapterInPaginatedMode,
                    latestChapterIndex = latestChapterIndex,
                    ttsState = ttsState,
                    ttsController = ttsController,
                    ttsReplacementPreferences = ttsReplacementPreferences,
                    totalPagesInCurrentChapter = totalPagesInCurrentChapter,
                    clearPendingTtsRelocationStateFn = ::clearPendingTtsRelocationState,
                    detachVerticalReaderFromTtsFn = ::detachVerticalReaderFromTts,
                    logTtsChapterDiagFn = ::logTtsChapterDiag,
                    isActiveReaderTtsForCurrentBookFn = ::isActiveReaderTtsForCurrentBook,
                    currentEpubJumpLocatorFn = ::currentEpubJumpLocator,
                    currentNativeVerticalLocatorFn = ::currentNativeVerticalLocator,
                    requestNativeVerticalLocatorScrollFn = ::requestNativeVerticalLocatorScroll,
                    triggerAutoScrollTempPauseFn = ::triggerAutoScrollTempPause,
                    fragmentJumpLocatorFn = ::fragmentJumpLocator,
                    paginatedJumpLocatorForPageFn = ::paginatedJumpLocatorForPage,
                    recordEpubJumpFn = ::recordEpubJump,
                    startTtsFromSelectionPaginatedFn = ::startTtsFromSelectionPaginated,
                    onSearchLookupFn = { p0 -> onSearchLookup(p0) },
                    onTranslateLookupFn = { p0 -> onTranslateLookup(p0) },
                    onDictionaryLookupFn = { p0 -> onDictionaryLookup(p0) },
                    onUpdateHighlightPaletteFn = { p0, p1 -> onUpdateHighlightPalette(p0, p1) },
                    runRecapFn = { p0, p1 -> runRecap(p0, p1) }
                )

                val isBookmarked: Boolean
                val onBookmarkClick: () -> Unit

                when (currentRenderMode) {
                    RenderMode.VERTICAL_SCROLL -> {
                        if (isNativeVerticalMode) {
                            val currentNativeLocator = currentNativeVerticalLocator()
                            val bookmarkedOnPage = remember(
                                currentNativeLocator,
                                nativeVerticalLocation?.visibleTextRanges,
                                nativeVerticalCurrentPage,
                                bookmarkLocatorMap,
                                bookmarkPageMap,
                                bookmarks
                            ) {
                                findEpubBookmarkForLocation(
                                    bookmarks = bookmarks,
                                    visibleRanges = nativeVerticalLocation?.visibleTextRanges.orEmpty().map { range ->
                                        EpubVisibleTextRange(
                                            chapterIndex = range.chapterIndex,
                                            blockIndex = range.blockIndex,
                                            startCharOffset = range.startCharOffset,
                                            endCharOffset = range.endCharOffset
                                        )
                                    },
                                    currentPosition = currentNativeLocator?.let { locator ->
                                        EpubBlockPosition(locator.chapterIndex, locator.blockIndex, locator.charOffset)
                                    },
                                    currentPage = nativeVerticalCurrentPage,
                                    cfi = { it.cfi },
                                    positionForCfi = { cfi ->
                                        bookmarkLocatorMap[cfi]?.let { locator ->
                                            EpubBlockPosition(locator.chapterIndex, locator.blockIndex, locator.charOffset)
                                        }
                                    },
                                    pageForCfi = bookmarkPageMap::get
                                )
                            }

                            isBookmarked = bookmarkedOnPage != null
                            onBookmarkClick = {
                                if (isBookmarked) {
                                    bookmarkedOnPage?.let { bookmarkToRemove ->
                                        bookmarks = bookmarks - bookmarkToRemove
                                        bookmarkPageMap = bookmarkPageMap - bookmarkToRemove.cfi
                                        bookmarkLocatorMap = bookmarkLocatorMap - bookmarkToRemove.cfi
                                        Timber.d("Native vertical click: Removing bookmark: $bookmarkToRemove")
                                    }
                                } else {
                                    val bookPaginator = paginator as? BookPaginator
                                    val locator = currentNativeVerticalLocator()
                                    if (locator != null && bookPaginator != null) {
                                        scope.launch {
                                            val finalCfi = locatorConverter.getCfiFromLocator(
                                                epubBook,
                                                locator
                                            ) ?: "android-locator:${locator.chapterIndex}:${locator.blockIndex}:${locator.charOffset}"
                                            val pageContent = bookPaginator.getPageContent(nativeVerticalCurrentPage)
                                            val targetBlockForBookmark =
                                                pageContent?.content?.firstOrNull {
                                                    it is TextContentBlock && it.blockIndex == locator.blockIndex && it.cfi != null
                                                }
                                                    ?: pageContent?.content?.firstOrNull { it.blockIndex == locator.blockIndex && it.cfi != null }
                                                    ?: pageContent?.content?.firstOrNull { it is TextContentBlock && it.cfi != null }
                                                    ?: pageContent?.content?.firstOrNull { it.cfi != null }
                                            val chapterTitle =
                                                epubBook.chapters.getOrNull(locator.chapterIndex)?.title
                                                    ?: context.getString(R.string.unknown_chapter)
                                            val snippet =
                                                (targetBlockForBookmark as? TextContentBlock)?.content?.text?.take(150)
                                                    ?: chapterTitle
                                            val chapterStartPage = bookPaginator.chapterStartPageIndices[locator.chapterIndex]
                                            val totalPages = bookPaginator.chapterPageCounts[locator.chapterIndex]
                                            val pageInChapter = chapterStartPage?.let {
                                                nativeVerticalCurrentPage - it + 1
                                            }
                                            val newBookmark = Bookmark(
                                                cfi = finalCfi,
                                                chapterTitle = chapterTitle,
                                                label = null,
                                                snippet = snippet,
                                                pageInChapter = pageInChapter,
                                                totalPagesInChapter = totalPages,
                                                chapterIndex = locator.chapterIndex
                                            )
                                            bookmarks = bookmarks + newBookmark
                                            bookmarkPageMap = bookmarkPageMap + (finalCfi to nativeVerticalCurrentPage)
                                            bookmarkLocatorMap = bookmarkLocatorMap + (finalCfi to locator)
                                            Timber.d("Native vertical click: Adding bookmark: $newBookmark")
                                        }
                                    }
                                }
                            }
                        } else {
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
                                    val chapterTitle = chapterIndex?.let { epubBook.chapters.getOrNull(it)?.title } ?: context.getString(R.string.unknown_chapter)
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

                val pageInfoChromeTopPadding =
                    if (prefs.pageInfoPosition == PageInfoPosition.TOP && showBars) 55.dp else 0.dp
                val pageInfoChromeBottomPadding =
                    if (prefs.pageInfoPosition == PageInfoPosition.BOTTOM && showBars) {
                        bottomPadding + 45.dp + if (isEpubJumpHistoryVisible) 40.dp else 0.dp
                    } else {
                        0.dp
                    }
                val readerClockTime = rememberReaderClockTime()

                // Page Info Bar (Vertical)
                AnimatedVisibility(
                    visible = currentRenderMode == RenderMode.VERTICAL_SCROLL && isPageInfoVisible,
                    enter = if (motionPolicy.reduceMotion) {
                        androidx.compose.animation.EnterTransition.None
                    } else {
                        fadeIn(animationSpec = tween(200))
                    },
                    exit = if (motionPolicy.reduceMotion) {
                        androidx.compose.animation.ExitTransition.None
                    } else {
                        fadeOut(animationSpec = tween(200))
                    },
                    modifier = Modifier
                        .align(if (prefs.pageInfoPosition == PageInfoPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter)
                        .padding(top = pageInfoChromeTopPadding, bottom = pageInfoChromeBottomPadding)
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

                        val displayPageInfo = when {
                            isNativeVerticalMode && nativeVerticalDisplayPageInfo != null ->
                                " (${nativeVerticalDisplayPageInfo.currentPage}/${nativeVerticalDisplayPageInfo.totalPages})"
                            currentScrollHeightValue <= 0 || isChapterParsing -> ""
                            else -> " ($currentPageInChapter/$totalPagesInCurrentChapter)"
                        }

                        Text(
                            text = readerClockTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = effectiveText.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.CenterStart)
                        )

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

                        if (totalBookLengthChars > 0 && currentScrollHeightValue > 0 && (!isChapterParsing || isNativeVerticalMode)) {
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
                    enter = if (motionPolicy.reduceMotion) {
                        androidx.compose.animation.EnterTransition.None
                    } else {
                        fadeIn(animationSpec = tween(200))
                    },
                    exit = if (motionPolicy.reduceMotion) {
                        androidx.compose.animation.ExitTransition.None
                    } else {
                        fadeOut(animationSpec = tween(200))
                    },
                    modifier = Modifier
                        .align(if (prefs.pageInfoPosition == PageInfoPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter)
                        .padding(top = pageInfoChromeTopPadding, bottom = pageInfoChromeBottomPadding)
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
                                    ?: stringResource(R.string.chapter)
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
                            stringResource(R.string.page_number_of_total, paginatedPagerState.currentPage + 1, paginatedPagerState.pageCount)
                        }

                        Text(
                            text = readerClockTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = effectiveText.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.CenterStart)
                        )

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
                                    chapters.take(chapterIndex).sumOf { it.plainTextCharacterCount().toLong() }
                                }
                                val chapterStartPage = bookPaginator.chapterStartPageIndices[chapterIndex]
                                val currentPageInChapter = if (chapterStartPage != null) {
                                    paginatedPagerState.currentPage - chapterStartPage
                                } else {
                                    0
                                }
                                val charsScrolledInCurrentChapter = bookPaginator.getCharactersScrolledInChapter(chapterIndex, currentPageInChapter)
                                val isLastPageOfBook = paginatedPagerState.currentPage == paginatedPagerState.pageCount - 1
                                val displayProgress = mobileEpubCharacterDisplayProgress(
                                    totalBookCharacters = totalBookLengthChars,
                                    completedChapterCharacters = completedCharsInPreviousChapters,
                                    currentChapterOffset = charsScrolledInCurrentChapter,
                                    isLastPageOfBook = isLastPageOfBook
                                )

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
                        animationSpec = tween(motionPolicy.durationMillis(150)), label = "leftPulse"
                    )
                    val rightPulseAlpha by animateFloatAsState(
                        targetValue = if (System.currentTimeMillis() - rightPulseTrigger < 150) 0.3f else 0f,
                        animationSpec = tween(motionPolicy.durationMillis(150)), label = "rightPulse"
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
                                                "window.scrollBy({ top: -${amount}, behavior: '${motionPolicy.webViewScrollBehavior()}' });", null
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
                                                "window.scrollBy({ top: ${amount}, behavior: '${motionPolicy.webViewScrollBehavior()}' });", null
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
                    isSliderActive = isPageSliderVisible,
                    tapToNavigateEnabled = prefs.tapToNavigateEnabled,
                    volumeScrollEnabled = prefs.volumeScrollEnabled,
                    isPageTurnAnimationEnabled = prefs.isPageTurnAnimationEnabled,
                    isRightToLeftPagination = prefs.rightToLeftPagination,
                    useNativeVerticalRenderer = useNativeVerticalRenderer,
                    hiddenTools = dictTools.hiddenTools,
                    toolOrder = dictTools.toolOrder,
                    bottomTools = dictTools.bottomTools,
                    onCustomizeTools = { dictTools.showCustomizeToolsSheet = true },
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
                        if (!isNativeVerticalMode) {
                            webViewRefForTts?.evaluateJavascript("javascript:window.clearSearchHighlights();", null)
                        }
                    },
                    onUseNativeVerticalRendererChange = { enabled ->
                        val wasNativeVertical = isNativeVerticalMode
                        val nativeLocator = if (wasNativeVertical) {
                            currentNativeVerticalLocator() ?: lastKnownLocator
                        } else {
                            null
                        }
                        useNativeVerticalRenderer = enabled
                        if (enabled) {
                            if (currentRenderMode == RenderMode.VERTICAL_SCROLL && !wasNativeVertical) {
                                val bookPaginator = paginator as? BookPaginator
                                val chapterStartPage = bookPaginator?.chapterStartPageIndices?.get(currentChapterIndex)
                                val chapterPageCount = bookPaginator?.chapterPageCounts?.get(currentChapterIndex)
                                if (chapterStartPage != null && chapterPageCount != null && chapterPageCount > 0) {
                                    val pageRatio = if (totalPagesInCurrentChapter > 1) {
                                        (currentPageInChapter - 1).toFloat() / (totalPagesInCurrentChapter - 1).toFloat()
                                    } else {
                                        0f
                                    }
                                    verticalScrollRequests.nativeVerticalScrollRequest =
                                        chapterStartPage + (pageRatio * (chapterPageCount - 1)).roundToInt()
                                }
                            }
                            webViewRefForTts = null
                            isAutoScrollModeActive = false
                            isAutoScrollPlaying = false
                        } else if (wasNativeVertical && nativeLocator != null) {
                            lastKnownLocator = nativeLocator
                            initialScrollTargetForChapter = null
                            currentScrollYPosition = 0
                            currentScrollHeightValue = 0
                            currentChapterIndex = nativeLocator.chapterIndex
                            scope.launch {
                                val cfi = locatorConverter.getCfiFromLocator(epubBook, nativeLocator)
                                cfiToLoad = cfi
                            }
                        }
                    },
                    onChangeRenderMode = { newMode ->
                        Timber.tag("NavDiag").d("onChangeRenderMode to $newMode")
                        if (newMode != currentRenderMode) {
                            if (newMode == RenderMode.PAGINATED) {
                                isSwitchingToPaginated = true
                                if (isNativeVerticalMode) {
                                    isSwitchingToPaginated = false
                                    val locator = currentNativeVerticalLocator() ?: lastKnownLocator
                                    if (locator != null) {
                                        lastKnownLocator = locator
                                        navigation.chapterToLoadOnSwitch = locator.chapterIndex
                                    }
                                    isPagerInitialized = false
                                    currentRenderMode = RenderMode.PAGINATED
                                    onRenderModeChange(RenderMode.PAGINATED)
                                } else {
                                    webViewRefForTts?.evaluateJavascript("javascript:CfiBridge.onCfiExtracted(window.getCurrentCfi());", null)
                                }
                            } else {
                                scope.launch {
                                    Timber.tag("NavDiag").d("Mode changing to VERTICAL. lastKnownLocator=$lastKnownLocator")
                                    if (useNativeVerticalRenderer) {
                                        val locator = (paginator as? BookPaginator)?.getLocatorForPage(paginatedPagerState.currentPage)
                                            ?: lastKnownLocator
                                        if (locator != null) {
                                            lastKnownLocator = locator
                                        }
                                        verticalScrollRequests.nativeVerticalScrollRequest = paginatedPagerState.currentPage
                                        webViewRefForTts = null
                                        currentRenderMode = RenderMode.VERTICAL_SCROLL
                                        onRenderModeChange(RenderMode.VERTICAL_SCROLL)
                                        return@launch
                                    }
                                    lastKnownLocator?.let { locator ->
                                        val cfi = locatorConverter.getCfiFromLocator(epubBook, locator)
                                        Timber.tag("NavDiag").d("Converted locator to CFI: $cfi")
                                        if (cfi != null) {
                                            val targetChunk = locator.blockIndex / 20
                                            navigation.chunkTargetOverride = targetChunk
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
                        prefs.tapToNavigateEnabled = enabled
                        saveTapToNavigateSetting(context, enabled)
                    },
                    onTogglePageTurnAnimation = { enabled ->
                        prefs.isPageTurnAnimationEnabled = enabled
                        savePageTurnAnimationSetting(context, enabled)
                    },
                    onSetRightToLeftPagination = { enabled ->
                        prefs.rightToLeftPagination = enabled
                        saveEpubRightToLeftPagination(context, enabled)
                    },
                    onToggleVolumeScroll = { enabled ->
                        prefs.volumeScrollEnabled = enabled
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
                    onOpenTtsReplacements = { showTtsReplacementsSheet = true },
                    onOpenBookReplacements = { showBookReplacementsSheet = true },
                    onOpenDictionarySettings = { dictTools.showDictionarySettingsSheet = true },
                    onOpenThemeSettings = { showThemePanel = true },
                    onOpenBrightness = { showBrightnessSheet = true },
                    onOpenVisualOptions = { prefs.showVisualOptionsSheet = true },
                    onOpenScreenOrientation = { prefs.showScreenOrientationSheet = true },
                    onOpenAiHub = { showAiHubSheet = true },
                    onOpenSlider = ::toggleEpubPageSlider,
                    onOpenDrawer = {
                        scope.launch {
                            if (motionPolicy.animationsEnabled) drawerState.open() else drawerState.snapTo(DrawerValue.Open)
                        }
                    },
                    onToggleFormat = {
                        navigation.showFormatAdjustmentBars = !navigation.showFormatAdjustmentBars
                        if (navigation.showFormatAdjustmentBars) {
                            searchState.showSearchResultsPanel = false
                            resetEpubSliderBookmark()
                            isPageSliderVisible = false
                        }
                    },
                    onToggleSearch = {
                        searchState.isSearchActive = true
                        searchState.showSearchResultsPanel = true
                        showBars = true
                        navigation.showFormatAdjustmentBars = false
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
                    onOpenFileInfo = { navigation.showFileInfoDialog = true },
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
                    onDeleteReflow = onDeleteReflow,
                    readerMotionPolicy = motionPolicy,
                )

                val autoScrollPadding by animateDpAsState(
                    targetValue = if (showBars) (bottomPadding + 45.dp + 16.dp) else 32.dp,
                    animationSpec = tween(motionPolicy.durationMillis(200)),
                    label = "AutoScrollPadding"
                )

                val alignmentBias by animateFloatAsState(
                    targetValue = if (isAutoScrollCollapsed) 1f else 0f,
                    animationSpec = tween(motionPolicy.durationMillis(200)),
                    label = "AutoScrollAlignAnimation"
                )

                val ttsOverlayPadding by animateDpAsState(
                    targetValue = if (showBars) (bottomPadding + 45.dp + 16.dp) else 32.dp,
                    animationSpec = tween(motionPolicy.durationMillis(200)),
                    label = "TtsOverlayPadding"
                )

                val ttsAlignmentBias by animateFloatAsState(
                    targetValue = readerTtsOverlayAlignmentBias(ttsOverlaySize),
                    animationSpec = tween(motionPolicy.durationMillis(200)),
                    label = "TtsAlignAnimation"
                )

                AnimatedVisibility(
                    visible = isTtsSessionActive && showBars,
                    enter = if (motionPolicy.reduceMotion) {
                        androidx.compose.animation.EnterTransition.None
                    } else {
                        slideInVertically(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200))
                    },
                    exit = if (motionPolicy.reduceMotion) {
                        androidx.compose.animation.ExitTransition.None
                    } else {
                        slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(200))
                    },
                    modifier = Modifier
                        .align(BiasAlignment(ttsAlignmentBias, 1f))
                        .padding(bottom = ttsOverlayPadding)
                        .padding(horizontal = 16.dp)
                ) {
                    TtsOverlayControls(
                        ttsController = ttsController,
                        ttsState = ttsState,
                        currentTtsMode = prefs.currentTtsMode,
                        overlaySize = ttsOverlaySize,
                        onOverlaySizeChange = { newSize ->
                            ttsOverlaySize = newSize
                            saveReaderTtsOverlaySize(context, newSize)
                        },
                        onLocateCurrentChunk = {
                            logTtsChapterDiag("Locate current chunk requested from TTS overlay")
                            queuePendingTtsLocate(TTS_LOCATE_REASON_OVERLAY)
                        },
                        onOpenTtsSettings = { showTtsSettingsSheet = true },
                        onClose = {
                            userStoppedTts = true
                            ttsController.stop()
                        },
                        credits = credits,
                        readerMotionPolicy = motionPolicy
                    )
                }

                val isAutoScrollControlsVisible = isAutoScrollModeActive

                AnimatedVisibility(
                    visible = isAutoScrollControlsVisible,
                    enter = if (motionPolicy.reduceMotion) {
                        androidx.compose.animation.EnterTransition.None
                    } else {
                        slideInVertically(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200))
                    },
                    exit = if (motionPolicy.reduceMotion) {
                        androidx.compose.animation.ExitTransition.None
                    } else {
                        slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(200))
                    },
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
                        speed = autoScroll.autoScrollSpeed,
                        minSpeed = autoScroll.autoScrollMinSpeed,
                        maxSpeed = autoScroll.autoScrollMaxSpeed,
                        onSpeedChange = { autoScroll.updateSpeed(it) },
                        onMinSpeedChange = { newMin ->
                            autoScroll.updateMinSpeed(newMin)
                            if (!autoScroll.isAutoScrollLocal) {
                                if (autoScroll.autoScrollMaxSpeed < newMin) {
                                    autoScroll.autoScrollMaxSpeed = newMin
                                    saveAutoScrollMaxSpeed(context, newMin)
                                }
                                if (autoScroll.autoScrollSpeed < newMin) {
                                    autoScroll.autoScrollSpeed = newMin
                                    saveAutoScrollSpeed(context, newMin)
                                } else if (autoScroll.autoScrollSpeed > autoScroll.autoScrollMaxSpeed) {
                                    autoScroll.autoScrollSpeed = autoScroll.autoScrollMaxSpeed
                                    saveAutoScrollSpeed(context, autoScroll.autoScrollMaxSpeed)
                                }
                            }
                        },
                        onMaxSpeedChange = { newMax ->
                            autoScroll.updateMaxSpeed(newMax)
                            if (!autoScroll.isAutoScrollLocal) {
                                if (autoScroll.autoScrollMinSpeed > newMax) {
                                    autoScroll.autoScrollMinSpeed = newMax
                                    saveAutoScrollMinSpeed(context, newMax)
                                }
                                if (autoScroll.autoScrollSpeed > newMax) {
                                    autoScroll.autoScrollSpeed = newMax
                                    saveAutoScrollSpeed(context, newMax)
                                } else if (autoScroll.autoScrollSpeed < autoScroll.autoScrollMinSpeed) {
                                    autoScroll.autoScrollSpeed = autoScroll.autoScrollMinSpeed
                                    saveAutoScrollSpeed(context, autoScroll.autoScrollMinSpeed)
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
                                navigation.showFormatAdjustmentBars = false
                            }
                            Timber.d("Musician mode toggled: $newMode")
                        },
                        useSlider = autoScrollUseSlider,
                        onInputModeToggle = {
                            autoScrollUseSlider = !autoScrollUseSlider
                            saveAutoScrollUseSlider(context, autoScrollUseSlider)
                        },
                        isLocalMode = autoScroll.isAutoScrollLocal,
                        onLocalModeToggle = autoScroll::onToggleAutoScrollMode,
                        onScrollToTop = {
                            if (isAutoScrollPlaying) {
                                triggerAutoScrollTempPause(1000L)
                            }
                            scope.launch {
                                if (isNativeVerticalMode) {
                                    val chapterIndex = currentNativeVerticalLocator()?.chapterIndex ?: currentChapterIndex
                                    requestNativeVerticalLocatorScroll(
                                        locator = Locator(chapterIndex, 0, 0),
                                        fallbackPage = (paginator as? BookPaginator)?.findStableChapterStartPage(chapterIndex),
                                        fallbackChapterIndex = chapterIndex
                                    )
                                } else {
                                    webViewRefForTts?.evaluateJavascript("window.scrollTo({ top: 0, behavior: '${motionPolicy.webViewScrollBehavior()}' });", null)
                                }
                            }
                        },
                        readerMotionPolicy = motionPolicy
                    )
                }

                EpubJumpHistoryBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding + 45.dp),
                    showStandardBars = showBars,
                    searchStateActive = searchState.isSearchActive,
                    backLabel = epubJumpBackLabel,
                    forwardLabel = epubJumpForwardLabel,
                    onBack = ::goBackInEpubJumpHistory,
                    onForward = ::goForwardInEpubJumpHistory,
                    onClear = { epubJumpHistory = epubJumpHistory.clear() },
                    readerMotionPolicy = motionPolicy,
                )

                // Animated Bottom Bar
                EpubReaderBottomBar(
                    isVisible = showBars,
                    currentRenderMode = currentRenderMode,
                    isTtsSessionActive = isTtsSessionActive,
                    ttsState = ttsState,
                    isProUser = isProUser,
                    hiddenTools = dictTools.hiddenTools,
                    toolOrder = dictTools.toolOrder,
                    bottomTools = dictTools.bottomTools,
                    currentTtsMode = prefs.currentTtsMode,
                    isSliderActive = isPageSliderVisible,
                    onOpenAiHub = { showAiHubSheet = true },
                    onOpenDictionarySettings = { dictTools.showDictionarySettingsSheet = true },
                    onOpenThemeSettings = { showThemePanel = true },
                    onOpenBrightness = { showBrightnessSheet = true },
                    onOpenSlider = ::toggleEpubPageSlider,
                    onOpenDrawer = {
                        scope.launch {
                            if (motionPolicy.animationsEnabled) drawerState.open() else drawerState.snapTo(DrawerValue.Open)
                        }
                    },
                    onOpenScreenOrientation = { prefs.showScreenOrientationSheet = true },
                    onToggleFormat = {
                        navigation.showFormatAdjustmentBars = !navigation.showFormatAdjustmentBars
                        if (navigation.showFormatAdjustmentBars) {
                            searchState.showSearchResultsPanel = false
                            resetEpubSliderBookmark()
                            isPageSliderVisible = false
                        }
                    },
                    onToggleSearch = {
                        searchState.isSearchActive = true
                        searchState.showSearchResultsPanel = true
                        showBars = true
                        navigation.showFormatAdjustmentBars = false
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
                        .padding(bottom = bottomPadding),
                    readerMotionPolicy = motionPolicy
                )

                ReaderTextFormatPanel(
                    isVisible = navigation.showFormatAdjustmentBars,
                    currentFontSize = format.currentFontSizeEm,
                    onFontSizeChange = { format.currentFontSizeEm = it },
                    currentLineHeight = format.currentLineHeight,
                    onLineHeightChange = { format.currentLineHeight = it },
                    currentParagraphGap = format.currentParagraphGap,
                    onParagraphGapChange = { format.currentParagraphGap = it },
                    currentImageSize = format.currentImageSize,
                    onImageSizeChange = { format.currentImageSize = it },
                    currentHorizontalMargin = format.currentHorizontalMargin,
                    onHorizontalMarginChange = { format.currentHorizontalMargin = it },
                    currentVerticalMargin = format.currentVerticalMargin,
                    onVerticalMarginChange = { format.currentVerticalMargin = it },
                    currentFont = format.currentFontFamily,
                    currentFontWeight = format.currentFontWeight,
                    onFontWeightChange = { format.currentFontWeight = it },
                    currentLetterSpacing = format.currentLetterSpacing,
                    onLetterSpacingChange = { format.currentLetterSpacing = it },
                    previewFontFamily = format.activeFontFamily,
                    currentCustomFontName = if(format.currentCustomFontPath != null) {
                        customFonts.find { it.path == format.currentCustomFontPath }?.displayName ?: stringResource(R.string.custom_font_fallback)
                    } else null,
                    onFontOptionClick = { showFontSelectionSheet = true },
                    currentTextAlign = format.currentTextAlign,
                    onTextAlignChange = { newAlign ->
                        format.currentTextAlign = newAlign
                        if (newAlign == ReaderTextAlign.JUSTIFY && currentRenderMode == RenderMode.PAGINATED) {
                            navigation.showJustifyWarningDialog = true
                        }
                    },
                    onReset = {
                        format.currentFontSizeEm = DEFAULT_FONT_SIZE_VAL
                        format.currentLineHeight = DEFAULT_LINE_HEIGHT_VAL
                        format.currentParagraphGap = DEFAULT_PARAGRAPH_GAP_VAL
                        format.currentImageSize = DEFAULT_IMAGE_SIZE_VAL
                        format.currentHorizontalMargin = DEFAULT_HORIZONTAL_MARGIN_VAL
                        format.currentVerticalMargin = DEFAULT_VERTICAL_MARGIN_VAL
                        format.currentFontWeight = DEFAULT_FONT_WEIGHT_VAL
                        format.currentLetterSpacing = DEFAULT_LETTER_SPACING_VAL
                        format.currentFontFamily = ReaderFont.ORIGINAL
                        format.currentCustomFontPath = null
                        format.currentTextAlign = ReaderTextAlign.DEFAULT
                    },
                    isLocalMode = isFormatLocal,
                    onLocalModeToggle = {
                        isFormatLocal = it
                        saveFormatIsLocal(context, bookId, it)
                    },
                    onClose = { navigation.showFormatAdjustmentBars = false }
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
                    showAiDefinitionPopup = dictTools.showAiDefinitionPopup,
                    selectedTextForAi = dictTools.selectedTextForAi,
                    aiDefinitionResult = dictTools.aiDefinitionResult,
                    isAiDefinitionLoading = dictTools.isAiDefinitionLoading,
                    onDismissAiDefinition = {
                        dictTools.showAiDefinitionPopup = false
                        dictTools.selectedTextForAi = null
                        dictTools.aiDefinitionResult = null
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
                        if (!dictTools.selectedDictPackage.isNullOrEmpty()) {
                            ExternalDictionaryHelper.launchDictionary(
                                context,
                                dictTools.selectedDictPackage!!,
                                text
                            )
                        } else {
                            Toast.makeText(
                                context,
                                "Select an offline dictionary first.",
                                Toast.LENGTH_SHORT
                            ).show()
                            dictTools.showDictionarySettingsSheet = true
                        }
                    },
                    getAuthToken = { viewModel.getAuthToken() },
                    credits = credits,
                    isProUser = isProUser,
                    currentChapterIndex = effectiveCurrentChapterIndex,
                    chapterTitle = chapters.getOrNull(effectiveCurrentChapterIndex)?.title ?: context.getString(R.string.chapter_number_format, effectiveCurrentChapterIndex + 1),
                    showAiHubSheet = showAiHubSheet,
                    onGenerateSummary = handleGenerateSummary,
                    onGenerateRecap = handleGenerateRecap,
                    onDismissAiHub = { showAiHubSheet = false },
                    onClearSummary = { summarizationResult = null },
                    onClearRecap = { recapResult = null }
                )

                if (navigation.isNavigatingToPosition && currentRenderMode == RenderMode.PAGINATED) {
                    EpubReaderBusyScrim(label = stringResource(R.string.navigating_to_position))
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

                if (navigation.showJustifyWarningDialog) {
                    AlertDialog(
                        onDismissRequest = { navigation.showJustifyWarningDialog = false },
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        title = { Text(stringResource(R.string.dialog_justified_text_limitation)) },
                        text = { Text(stringResource(R.string.dialog_justified_text_limitation_desc)) },
                        confirmButton = {
                            TextButton(onClick = { navigation.showJustifyWarningDialog = false }) {
                                Text(stringResource(R.string.action_i_understand))
                            }
                        }
                    )
                }
                EpubReaderTocNavigationOverlay(
                    isVisible = navigation.isNavigatingByToc,
                    motionPolicy = motionPolicy
                )
                EpubHighlightNoteEditorSheet(
                    navigation = navigation,
                    userHighlights = userHighlights,
                    effectiveBg = effectiveBg,
                    effectiveText = effectiveText,
                    activeHighlightPalette = currentHighlightPalette,
                    currentRenderMode = currentRenderMode,
                    currentChapterIndex = currentChapterIndex,
                    webViewRefForTts = webViewRefForTts,
                    onOpenPaletteManager = { showPaletteManager = true },
                    onColorChange = onHighlightColorChange,
                    onStyleChange = onHighlightStyleChange,
                    onDictionaryLookup = { word -> onDictionaryLookup(word) },
                    onTranslateLookup = { text -> onTranslateLookup(text) },
                    onSearchLookup = { text -> onSearchLookup(text) }
                )

                EpubFootnoteNavigationSheet(
                    navigation = navigation,
                    epubBook = epubBook,
                    chapters = chapters,
                    paginator = paginator,
                    scope = scope,
                    currentChapterIndex = currentChapterIndex,
                    currentRenderMode = currentRenderMode,
                    isNativeVerticalMode = isNativeVerticalMode,
                    paginatedPagerState = paginatedPagerState,
                    effectiveBg = effectiveBg,
                    effectiveText = effectiveText,
                    currentJumpLocator = { currentEpubJumpLocator() },
                    onDestinationResolved = { destination, currentOrigin, targetPage, jumpTarget ->
                        lastKnownLocator = destination
                        recordEpubJump(jumpTarget, currentOrigin)
                    },
                    scrollToNativeLocator = { locator, fallbackPage, fallbackChapterIndex ->
                        requestNativeVerticalLocatorScroll(
                            locator = locator,
                            fallbackPage = fallbackPage,
                            fallbackChapterIndex = fallbackChapterIndex
                        )
                    },
                    onFragmentLoad = { fragmentToLoad = it },
                    onSelectChapter = { targetChapter -> currentChapterIndex = targetChapter },
                    onClearInitialScrollTarget = { initialScrollTargetForChapter = null }
                )

                EpubReaderPageSliderLayer(
                    navigation = navigation,
                    scope = scope,
                    isVisible = epubSliderChromeVisible,
                    totalPages = when {
                        isNativeVerticalMode -> nativeVerticalTotalPages
                        currentRenderMode == RenderMode.VERTICAL_SCROLL -> totalPagesInCurrentChapter
                        else -> paginatedPagerState.pageCount
                    },
                    isNativeVerticalMode = isNativeVerticalMode,
                    currentRenderMode = currentRenderMode,
                    nativeVerticalTotalPages = nativeVerticalTotalPages,
                    clientHeightPx = currentClientHeightValue,
                    webViewRefForTts = webViewRefForTts,
                    bottomPadding = bottomPadding,
                    isJumpHistoryVisible = isEpubJumpHistoryVisible,
                    isPageInfoVisible = isPageInfoVisible,
                    pageInfoPosition = prefs.pageInfoPosition,
                    pageInfoBarHeight = pageInfoBarHeight,
                    colors = epubReaderSliderColors,
                    motionPolicy = motionPolicy,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    currentJumpLocator = { currentEpubJumpLocator() },
                    recordJump = { target, origin -> recordEpubJump(target, origin) },
                    jumpTargetForPage = { page -> sliderJumpTargetForPage(page) },
                    scrollNativeProgress = { progress -> requestNativeVerticalProgressScroll(progress) },
                    scrollToPaginatedPage = { page ->
                        scope.launch { paginatedPagerState.scrollToPage(page) }
                    },
                    paginatedJumpLocatorForPage = { page ->
                        (paginator as? BookPaginator)?.getLocatorForPage(page)
                    },
                    jumpPaginatedToPage = { pageIndex, targetLocator ->
                        scrollPaginatedToJumpPage(pageIndex, targetLocator)
                    }
                )
            }
        }

        if (showTtsSettingsSheet) {
            TtsSettingsSheet(
                isVisible = true,
                onDismiss = { showTtsSettingsSheet = false },
                currentMode = prefs.currentTtsMode,
                onModeChange = { newMode ->
                    prefs.currentTtsMode = newMode
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

        TtsWordReplacementsSheet(
            isVisible = showTtsReplacementsSheet,
            bookId = bookId,
            bookTitle = epubBook.title,
            preferences = ttsReplacementPreferences,
            onPreferencesChange = updateTtsReplacementPreferences,
            onDismiss = { showTtsReplacementsSheet = false },
        )

        BookWordReplacementsSheet(
            isVisible = showBookReplacementsSheet,
            bookId = bookId,
            bookTitle = epubBook.title,
            preferences = bookReplacementPreferences,
            onPreferencesChange = updateBookReplacementPreferences,
            onDismiss = { showBookReplacementsSheet = false },
        )

        ReaderFileInfoDialogs(
            isFileInfoVisible = navigation.showFileInfoDialog,
            onFileInfoVisibleChange = { navigation.showFileInfoDialog = it },
            uiState = uiState,
            primaryBookId = uiState.selectedBookId ?: stableBookId,
            uriString = uiState.selectedEpubUri?.toString(),
            viewModel = viewModel
        )

        if (dictTools.showCustomizeToolsSheet) {
            CustomizeToolsSheet(
                hiddenTools = dictTools.hiddenTools,
                toolOrder = dictTools.toolOrder,
                bottomTools = dictTools.bottomTools,
                onUpdate = { newHiddenSet ->
                    dictTools.hiddenTools = newHiddenSet
                    saveHiddenTools(context, newHiddenSet)
                },
                onOrderUpdate = { newOrder ->
                    dictTools.toolOrder = newOrder
                    saveToolOrder(context, newOrder)
                },
                onPlacementUpdate = { newBottomTools ->
                    dictTools.bottomTools = newBottomTools
                    saveBottomTools(context, newBottomTools)
                },
                onDismiss = { dictTools.showCustomizeToolsSheet = false }
            )
        }

        if (showBrightnessSheet) {
            ReaderBrightnessSheet(
                settings = readerBrightnessSettings,
                onSettingsChange = updateReaderBrightness,
                onDismiss = { showBrightnessSheet = false }
            )
        }

        if (dictTools.showDictionarySettingsSheet) {
            DictionarySettingsDialog(
                isVisible = true,
                onDismiss = { dictTools.showDictionarySettingsSheet = false },
                isProUser = isProUser,
                useOnlineDictionary = dictTools.useOnlineDictionary,
                onToggleOnlineDictionary = { newState ->
                    dictTools.useOnlineDictionary = newState
                    saveUseOnlineDict(context, newState)
                },
                selectedDictionaryPackageName = dictTools.selectedDictPackage,
                onSelectDictionaryPackage = { pkg ->
                    dictTools.selectedDictPackage = pkg
                    saveExternalDictPackage(context, pkg)
                },
                selectedTranslatePackageName = dictTools.selectedTranslatePackage,
                onSelectTranslatePackage = { pkg ->
                    dictTools.selectedTranslatePackage = pkg
                    saveExternalTranslatePackage(context, pkg)
                },
                selectedSearchPackageName = dictTools.selectedSearchPackage,
                onSelectSearchPackage = { pkg ->
                    dictTools.selectedSearchPackage = pkg
                    saveExternalSearchPackage(context, pkg)
                }
            )
        }

        if (prefs.showVisualOptionsSheet) {
            VisualOptionsSheet(
                systemUiMode = prefs.systemUiMode,
                onSystemUiModeChange = {
                    prefs.systemUiMode = it
                    saveSystemUiMode(context, it)
                },
                pageInfoMode = prefs.pageInfoMode,
                onPageInfoModeChange = {
                    prefs.pageInfoMode = it
                    savePageInfoMode(context, it)
                },
                pageInfoPosition = prefs.pageInfoPosition,
                onPageInfoPositionChange = {
                    prefs.pageInfoPosition = it
                    savePageInfoPosition(context, it)
                },
                pullToTurnEnabled = prefs.pullToTurnEnabled,
                onPullToTurnChange = {
                    prefs.pullToTurnEnabled = it
                    savePullToTurn(context, it)
                },
                pullToTurnMultiplier = prefs.pullToTurnMultiplier,
                onPullToTurnMultiplierChange = {
                    prefs.pullToTurnMultiplier = it
                    savePullToTurnMultiplier(context, it)
                },
                hideImages = prefs.hideImages,
                onHideImagesChange = {
                    prefs.hideImages = it
                    saveHideImages(context, it)
                },
                onDismiss = { prefs.showVisualOptionsSheet = false }
            )
        }

        if (prefs.showScreenOrientationSheet) {
            ReaderScreenOrientationSheet(
                selectedMode = prefs.screenOrientationMode,
                onModeSelected = {
                    prefs.screenOrientationMode = it
                    saveReaderScreenOrientationMode(context, it)
                },
                onDismiss = { prefs.showScreenOrientationSheet = false }
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
                    currentFont = format.currentFontFamily,
                    currentCustomFontPath = format.currentCustomFontPath,
                    onFontSelected = { font, path ->
                        format.currentFontFamily = font
                        format.currentCustomFontPath = path
                    },
                    customFonts = customFonts,
                    onImportFonts = onImportFonts,
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

        if (navigation.showInsufficientCreditsDialog) {
            AlertDialog(
                onDismissRequest = { navigation.showInsufficientCreditsDialog = false },
                icon = { Icon(painterResource(id = R.drawable.crown), contentDescription = null) },
                title = { Text(stringResource(R.string.dialog_out_of_credits_title)) },
                text = { Text(stringResource(R.string.dialog_out_of_credits_desc)) },
                confirmButton = {
                    TextButton(onClick = {
                        navigation.showInsufficientCreditsDialog = false
                        onNavigateToPro()
                    }) { Text(stringResource(R.string.action_get_pro_or_add_credits)) }
                },
                dismissButton = {
                    TextButton(onClick = { navigation.showInsufficientCreditsDialog = false }) {
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
