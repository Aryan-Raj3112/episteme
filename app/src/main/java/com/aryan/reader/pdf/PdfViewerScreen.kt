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
) @file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.aryan.reader.pdf

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.print.PrintManager
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.work.WorkInfo
import com.aryan.reader.AiDefinitionPopup
import com.aryan.reader.AiFeature
import com.aryan.reader.AiDefinitionResult
import com.aryan.reader.AiHubBottomSheet
import com.aryan.reader.BannerMessage
import com.aryan.reader.BuildConfig
import com.aryan.reader.CustomTopBanner
import com.aryan.reader.FileType
import com.aryan.reader.HighlightColorPickerDialog
import com.aryan.reader.MainViewModel
import com.aryan.reader.R
import com.aryan.reader.ReaderThemePanel
import com.aryan.reader.SearchResult
import com.aryan.reader.SummarizationResult
import com.aryan.reader.SummaryCacheManager
import com.aryan.reader.TtsSettingsSheet
import com.aryan.reader.ml.SpeechBubble
import com.aryan.reader.epubreader.AutoScrollControls
import com.aryan.reader.epubreader.DictionarySettingsDialog
import com.aryan.reader.epubreader.ExternalDictionaryHelper
import com.aryan.reader.epubreader.SystemUiMode
import com.aryan.reader.epubreader.TtsOverlayControls
import com.aryan.reader.epubreader.loadTapToNavigateSetting
import com.aryan.reader.epubreader.saveTapToNavigateSetting
import com.aryan.reader.fetchAiDefinition
import com.aryan.reader.areReaderAiFeaturesEnabled
import com.aryan.reader.callByokGeminiInlineAi
import com.aryan.reader.isByokCloudTtsAvailable
import com.aryan.reader.loadCustomThemes
import com.aryan.reader.loadGlobalTextureTransparency
import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.pdf.data.AnnotationSettingsRepository
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfAnnotationRepository
import com.aryan.reader.pdf.data.PdfHighlightRepository
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.PdfTextBoxRepository
import com.aryan.reader.pdf.data.PdfTextRepository
import com.aryan.reader.pdf.data.SmartSearchResult
import com.aryan.reader.pdf.data.TextStyleConfig
import com.aryan.reader.pdf.data.VirtualPage
import com.aryan.reader.rememberSearchState
import com.aryan.reader.saveCustomThemes
import com.aryan.reader.saveGlobalTextureTransparency
import com.aryan.reader.summarizationUrl
import com.aryan.reader.tts.SpeakerSamplePlayer
import com.aryan.reader.tts.TtsPlaybackManager
import com.aryan.reader.tts.rememberTtsController
import com.aryan.reader.tts.splitTextIntoChunks
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.LinkedHashSet
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isForwardPressed

@Suppress("KotlinConstantConditions")
@SuppressLint("UnusedBoxWithConstraintsScope", "ObsoleteSdkInt", "LocalContextGetResourceValueCall")
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
    var tapToNavigateEnabled by remember { mutableStateOf(loadTapToNavigateSetting(context)) }
    var showThemePanel by remember { mutableStateOf(false) }
    var currentThemeId by remember { mutableStateOf(loadPdfThemeId(context)) }
    var excludeImages by remember { mutableStateOf(com.aryan.reader.loadExcludeImages(context)) }
    var customThemes by remember { mutableStateOf(loadCustomThemes(context)) }
    var globalTextureTransparency by remember { mutableFloatStateOf(loadGlobalTextureTransparency(context)) }
    val documentCache = remember { DocumentCache(3) }
    val summaryCacheManager = remember(context) { SummaryCacheManager(context) }
    val tabStateMap = remember { mutableStateMapOf<String, Int>() }
    var showInsufficientCreditsDialog by remember { mutableStateOf(false) }
    var poppedUpPanelBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val activeTheme = remember(currentThemeId, customThemes) {
        PdfBuiltInThemes.find { it.id == currentThemeId }
            ?: customThemes.find { it.id == currentThemeId }
            ?: PdfBuiltInThemes[0]
    }
    val isPdfDarkMode = activeTheme.isDark || activeTheme.id == "reverse"
    var pageAspectRatios by remember { mutableStateOf<List<Float>>(emptyList()) }
    var showBars by rememberSaveable { mutableStateOf(true) }
    var systemUiMode by remember { mutableStateOf(loadPdfSystemUiMode(context)) }
    var showVisualOptionsSheet by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }
    var documentPassword by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRestorePage by rememberSaveable { mutableStateOf(initialPage) }
    var isScrollLocked by remember { mutableStateOf(false) }
    var lockedState by remember { mutableStateOf<Triple<Float, Float, Float>?>(null) }
    var currentActiveScale by remember { mutableFloatStateOf(1f) }
    var currentActiveOffset by remember { mutableStateOf(Offset.Zero) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    LocalView.current

    var ocrLanguage by remember { mutableStateOf(loadOcrLanguage(context)) }
    var hasSelectedOcrLanguage by remember { mutableStateOf(hasUserSelectedOcrLanguage(context)) }
    var showOcrLanguageDialog by remember { mutableStateOf(false) }
    var showReindexDialog by remember { mutableStateOf<OcrLanguage?>(null) }
    var pendingActionAfterOcrSelection by remember { mutableStateOf<(() -> Unit)?>(null) }

    var showCustomizeToolsSheet by remember { mutableStateOf(false) }
    var hiddenTools by remember { mutableStateOf(loadPdfHiddenTools(context)) }
    var toolOrder by remember { mutableStateOf(loadPdfToolOrder(context)) }
    var bottomTools by remember { mutableStateOf(loadPdfBottomTools(context)) }

    val onUpdateHiddenTools = { newSet: Set<String> ->
        hiddenTools = newSet
        savePdfHiddenTools(context, newSet)
    }

    val onUpdateToolOrder = { newOrder: List<PdfReaderTool> ->
        toolOrder = newOrder
        savePdfToolOrder(context, newOrder)
    }

    val onUpdateBottomTools = { newBottomTools: Set<String> ->
        bottomTools = newBottomTools
        savePdfBottomTools(context, newBottomTools)
    }

    val isOss = BuildConfig.FLAVOR == "oss"

    val executeWithOcrCheck = remember(hasSelectedOcrLanguage) {
        { action: () -> Unit ->
            if (isOss || hasSelectedOcrLanguage) {
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

    val uiState by viewModel.uiState.collectAsState()
    val effectivePdfUri = uiState.selectedPdfUri ?: pdfUri
    val effectiveFileType = uiState.selectedFileType ?: FileType.PDF

    var showNewTabSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val isTabsEnabled = uiState.isTabsEnabled
    val openTabs = uiState.openTabs
    val activeTabBookId = uiState.activeTabBookId
    val originalFileName by remember(uiState.recentFiles,  effectivePdfUri) {
        derivedStateOf {
            uiState.recentFiles.find { it.uriString == effectivePdfUri.toString() }?.displayName
                ?: effectivePdfUri.lastPathSegment ?: "Document.pdf"
        }
    }
    var currentBookId by remember { mutableStateOf<String?>(null) }
    val bookId = currentBookId ?: effectivePdfUri.toString().hashCode().toString()
    var documentMetadataTitle by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current
    var isDockDragging by remember { mutableStateOf(false) }
    var initialScrollDone by remember { mutableStateOf(false) }

    val reflowBookId = remember(bookId) { "${bookId}_reflow" }
    val hasReflowFile by remember(uiState.allRecentFiles, reflowBookId) {
        derivedStateOf {
            uiState.allRecentFiles.any { it.bookId == reflowBookId && !it.isDeleted }
        }
    }

    LaunchedEffect(bookId) {
        isScrollLocked = loadPdfScrollLocked(context, bookId)
        lockedState = loadPdfLockedState(context, bookId)
    }

    var isAutoScrollModeActive by remember { mutableStateOf(false) }
    var isAutoScrollPlaying by remember { mutableStateOf(false) }
    var isAutoScrollTempPaused by remember { mutableStateOf(false) }
    val autoScrollResumeJob = remember { mutableStateOf<Job?>(null) }
    var isAutoScrollCollapsed by remember { mutableStateOf(false) }
    var isTtsCollapsed by remember { mutableStateOf(false) }

    var isMusicianMode by remember { mutableStateOf(loadPdfMusicianMode(context)) }
    var autoScrollUseSlider by remember { mutableStateOf(loadPdfAutoScrollUseSlider(context)) }
    var isStylusOnlyMode by remember { mutableStateOf(loadStylusOnlyMode(context)) }
    var showTtsControlsSheet by remember { mutableStateOf(false) }
    var isKeepScreenOn by remember { mutableStateOf(loadKeepScreenOn(context)) }
    val ttsController = rememberTtsController()
    val ttsState by ttsController.ttsState.collectAsState()
    ttsState.currentText
    var currentTtsMode by remember {
        mutableStateOf(
            com.aryan.reader.tts.loadTtsMode(context).let {
                if (BuildConfig.FLAVOR == "oss" && !isByokCloudTtsAvailable(context)) TtsPlaybackManager.TtsMode.BASE else it
            }
        )
    }
    var showTtsSettingsSheet by remember { mutableStateOf(false) }

    DisposableEffect(isKeepScreenOn) {
        view.keepScreenOn = isKeepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }

    var showDictionarySettingsSheet by remember { mutableStateOf(false) }
    var useOnlineDictionary by remember { mutableStateOf(loadUseOnlineDict(context)) }
    var selectedDictPackage by remember { mutableStateOf(loadExternalDictPackage(context)) }
    var selectedTranslatePackage by remember { mutableStateOf(loadExternalTranslatePackage(context)) }
    var selectedSearchPackage by remember { mutableStateOf(loadExternalSearchPackage(context)) }

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

    var bannerMessage by remember { mutableStateOf<BannerMessage?>(null) }
    fun showBanner(message: String, isError: Boolean = false) {
        bannerMessage = BannerMessage(message, isError = isError)
    }
    val onOcrStateChange: (Boolean) -> Unit = {}

    var showZoomIndicator by remember { mutableStateOf(false) }
    var bookmarks by remember(pdfUri) { mutableStateOf(loadPdfBookmarksFromJson(initialBookmarksJson)) }

    var showPenPlayground by rememberSaveable { mutableStateOf(false) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    var isDockMinimized by rememberSaveable { mutableStateOf(false) }

    var pendingNoteForNewHighlight by remember { mutableStateOf(false) }
    var highlightToNoteId by remember { mutableStateOf<String?>(null) }
    val onNoteRequested: (String?) -> Unit = { id ->
        if (id != null) {
            highlightToNoteId = id
        } else {
            pendingNoteForNewHighlight = true
        }
    }

    val isDrawingActive by remember(isEditMode, isDockMinimized) {
        derivedStateOf { isEditMode && !isDockMinimized }
    }

    var isAutoScrollLocal by remember { mutableStateOf(loadPdfAutoScrollLocalMode(context, bookId)) }

    LaunchedEffect(bookId) {
        isAutoScrollLocal = loadPdfAutoScrollLocalMode(context, bookId)
    }

    val onPrintDocument: () -> Unit = {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "${context.getString(R.string.app_name)} - $originalFileName"

        try {
            Timber.tag("PdfPrint").d("Starting print job: $jobName")
            printManager.print(
                jobName,
                PdfPrintDocumentAdapter(context, effectivePdfUri, originalFileName),
                null
            )
        } catch (e: Exception) {
            Timber.tag("PdfPrint").e(e, "Failed to initialize print job")
            showBanner(context.getString(R.string.error_open_print_settings), isError = true)
        }
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

    var customHighlightColors by remember { mutableStateOf(loadCustomHighlightColors(context)) }
    var showHighlightColorPicker by remember { mutableStateOf(false) }
    var highlightColorPickerInitialSlot by remember { mutableStateOf(PdfHighlightColor.YELLOW) }
    var isBubbleZoomModeActive by remember { mutableStateOf(false) }
    var showBubbleZoomDownloadDialog by remember { mutableStateOf(false) }
    val bubbleZoomDownloadProgress by viewModel.speechBubbleModelDownloadProgress.collectAsState()

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

    val window = (view.context as? Activity)?.window
    val showStandardBars = showBars && !isEditMode

    DisposableEffect(window, view) {
        onDispose {
            window?.let {
                WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(systemUiMode, showStandardBars) {
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            when (systemUiMode) {
                SystemUiMode.DEFAULT -> {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
                SystemUiMode.SYNC -> {
                    if (showStandardBars) {
                        insetsController.show(WindowInsetsCompat.Type.systemBars())
                    } else {
                        insetsController.hide(WindowInsetsCompat.Type.systemBars())
                        insetsController.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
                SystemUiMode.HIDDEN -> {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
    }

    val dockHeight = 64.dp
    val dockHeightPx = with(LocalDensity.current) { dockHeight.toPx() }
    val density = LocalDensity.current

    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val dummySearcher: suspend (String) -> List<SearchResult> = { emptyList() }
    val searchState = rememberSearchState(scope = coroutineScope, searcher = dummySearcher)
    val navBarHeight = WindowInsets.systemBars.getBottom(density)

    val targetVerticalHeaderHeight = remember(
        dockLocation,
        snapPreviewLocation,
        isEditMode,
        isDockDragging,
        systemUiMode,
        statusBarHeightDp
    ) {
        if (!isEditMode) {
            0.dp
        } else {
            val isStickyTop = dockLocation == DockLocation.TOP && !isDockDragging
            val isPreviewingTop = snapPreviewLocation == DockLocation.TOP
            if (isStickyTop || isPreviewingTop) {
                dockHeight + if (systemUiMode == SystemUiMode.DEFAULT) statusBarHeightDp else 0.dp
            } else 0.dp
        }
    }

    val verticalHeaderHeight by animateDpAsState(
        targetValue = targetVerticalHeaderHeight,
        animationSpec = tween(durationMillis = 200),
        label = "verticalHeaderHeight"
    )

    val targetTopOverlayInset = remember(
        showStandardBars,
        systemUiMode,
        statusBarHeightDp
    ) {
        if (!showStandardBars) {
            0.dp
        } else {
            var inset = 56.dp
            val isStatusBarVisible =
                systemUiMode == SystemUiMode.DEFAULT || (systemUiMode == SystemUiMode.SYNC && showStandardBars)

            if (isStatusBarVisible) {
                inset += statusBarHeightDp
            }
            inset
        }
    }

    val topOverlayInset by animateDpAsState(
        targetValue = targetTopOverlayInset,
        animationSpec = tween(durationMillis = 200),
        label = "topOverlayInset"
    )

    val verticalFooterHeight by remember(
        dockLocation,
        snapPreviewLocation,
        isEditMode,
        isDockDragging,
        systemUiMode,
        navBarHeight,
        density
    ) {
        derivedStateOf {
            if (!isEditMode) {
                0.dp
            } else {
                val isStickyBottom = dockLocation == DockLocation.BOTTOM && !isDockDragging
                val isPreviewingBottom = snapPreviewLocation == DockLocation.BOTTOM

                if (isStickyBottom || isPreviewingBottom) {
                    dockHeight + if (systemUiMode == SystemUiMode.DEFAULT) with(density) { navBarHeight.toDp() } else 0.dp
                } else 0.dp
            }
        }
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ocrLanguage) { OcrHelper.init(ocrLanguage) }

    LaunchedEffect(displayMode) { saveDisplayMode(context, displayMode) }

    LaunchedEffect(currentActiveScale, currentActiveOffset, isScrollLocked) {
        if (isScrollLocked) {
            delay(500)
            Timber.tag("PdfLockDiagnostic").d("SAVING: BookId=$bookId | Scale=$currentActiveScale | X=${currentActiveOffset.x} | Y=${currentActiveOffset.y}")

            lockedState = Triple(currentActiveScale, currentActiveOffset.x, currentActiveOffset.y)
            savePdfLockedState(context, bookId, currentActiveScale, currentActiveOffset.x, currentActiveOffset.y)
        }
    }

    LaunchedEffect(ttsState.errorMessage) {
        ttsState.errorMessage?.let { message ->
            if (message == "INSUFFICIENT_CREDITS") {
                showInsufficientCreditsDialog = true
                ttsController.stop()
            } else {
                showBanner(message, isError = true)
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { showBanner(it, isError = true) }
    }

    LaunchedEffect(bannerMessage) {
        val message = bannerMessage ?: return@LaunchedEffect
        if (!message.isPersistent) {
            delay(2500L)
            bannerMessage = null
        }
    }

    val annotationSettingsRepo = remember(context) { AnnotationSettingsRepository(context) }
    val toolSettings by annotationSettingsRepo.settings.collectAsState()
    var showToolSettings by rememberSaveable { mutableStateOf(false) }
    val isHighlighterSnapEnabled = toolSettings.isHighlighterSnapEnabled

    val selectedTool = toolSettings.getActiveTool()

    val lastPenTool = toolSettings.getLastPenTool()
    val lastHighlighterTool = toolSettings.getLastHighlighterTool()
    val dockPenColor = toolSettings.getToolColor(lastPenTool)
    val dockHighlighterColor = toolSettings.getToolColor(lastHighlighterTool)

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
    val highlightRepository = remember(context) { PdfHighlightRepository(context) }

    var allAnnotations by remember { mutableStateOf<Map<Int, List<PdfAnnotation>>>(emptyMap()) }

    val undoStack = remember { mutableStateListOf<HistoryAction>() }
    val redoStack = remember { mutableStateListOf<HistoryAction>() }

    val erasedAnnotationsFromStroke = remember {
        mutableStateMapOf<Int, MutableList<PdfAnnotation>>()
    }

    var lastEraserPoint by remember { mutableStateOf<PdfPoint?>(null) }

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
    var pdfDocument by remember { mutableStateOf<ReaderDocument?>(null) }
    var pfdState by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var totalPages by remember { mutableIntStateOf(0) }
    var currentPageScale by remember { mutableFloatStateOf(1f) }
    val textBoxes = remember { mutableStateListOf<PdfTextBox>() }
    var selectedTextBoxId by rememberSaveable { mutableStateOf<String?>(null) }
    val userHighlights = remember { mutableStateListOf<PdfUserHighlight>() }
    val drawingState = remember { PdfDrawingState() }
    val pdfiumCore = remember { PdfiumCoreProvider.core }
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
    var isDocumentReady by remember { mutableStateOf(false) }

    suspend fun renderSpeechBubblePrefetchBitmap(
        document: ReaderDocument,
        sourcePageIndex: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        document.openPage(sourcePageIndex)?.use { page ->
            val pageWidth = page.getPageWidthPoint()
            val pageHeight = page.getPageHeightPoint()
            if (pageWidth <= 0 || pageHeight <= 0) {
                return@withContext null
            }

            val longEdge = max(pageWidth, pageHeight).toFloat()
            val targetLongEdge = when (document) {
                is PdfDocumentWrapper -> 1600f.coerceAtLeast(longEdge)
                else -> min(longEdge, 1600f)
            }
            val renderScale = (targetLongEdge / longEdge).coerceAtLeast(1f)
            val renderWidth = (pageWidth * renderScale).roundToInt().coerceAtLeast(1)
            val renderHeight = (pageHeight * renderScale).roundToInt().coerceAtLeast(1)
            val renderBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)

            try {
                page.renderPageBitmap(
                    bitmap = renderBitmap,
                    startX = 0,
                    startY = 0,
                    drawSizeX = renderWidth,
                    drawSizeY = renderHeight,
                    renderAnnot = true
                )
                renderBitmap
            } catch (t: Throwable) {
                renderBitmap.recycle()
                Timber.tag("BubbleZoom").w(t, "Failed to render bubble prefetch bitmap for page $sourcePageIndex")
                null
            }
        }
    }

    fun buildSpeechBubblePrefetchOrder(): List<Int> {
        if (totalDisplayPages <= 0) return emptyList()
        val ordered = LinkedHashSet<Int>()
        ordered += currentPage.coerceIn(0, totalDisplayPages - 1)
        for (distance in 1 until totalDisplayPages) {
            val next = currentPage + distance
            val previous = currentPage - distance
            if (next in 0 until totalDisplayPages) ordered += next
            if (previous in 0 until totalDisplayPages) ordered += previous
        }
        return ordered.toList()
    }

    suspend fun detectSpeechBubblesForPage(
        sourcePageIndex: Int,
        fallbackBitmap: Bitmap,
        allowHighQualityFallback: Boolean = true
    ): List<SpeechBubble> {
        val document = pdfDocument
        val shouldUsePrefetchBitmap =
            allowHighQualityFallback &&
                document != null &&
                !viewModel.hasCachedSpeechBubbles(bookId, sourcePageIndex)
        val detectionBitmap = if (shouldUsePrefetchBitmap) {
            renderSpeechBubblePrefetchBitmap(document!!, sourcePageIndex) ?: fallbackBitmap
        } else {
            fallbackBitmap
        }
        val ownsBitmap = detectionBitmap !== fallbackBitmap

        return try {
            val detected = viewModel.detectSpeechBubblesCached(
                documentId = bookId,
                pageIndex = sourcePageIndex,
                bitmap = detectionBitmap,
                context = context
            )
            if (ownsBitmap) {
                viewModel.detectSpeechBubblesCached(
                    documentId = bookId,
                    pageIndex = sourcePageIndex,
                    bitmap = fallbackBitmap,
                    context = context
                )
            } else {
                detected
            }
        } finally {
            if (ownsBitmap && !detectionBitmap.isRecycled) {
                detectionBitmap.recycle()
            }
        }
    }

    LaunchedEffect(
        isBubbleZoomModeActive,
        isDocumentReady,
        pdfDocument,
        bookId,
        currentPage,
        totalDisplayPages,
        virtualPages
    ) {
        val document = pdfDocument ?: return@LaunchedEffect
        if (!isBubbleZoomModeActive || !isDocumentReady || totalDisplayPages <= 0) {
            return@LaunchedEffect
        }

        for (displayPageIndex in buildSpeechBubblePrefetchOrder()) {
            if (!isActive) break

            val sourcePageIndex = when (val virtualPage = virtualPages.getOrNull(displayPageIndex)) {
                is VirtualPage.PdfPage -> virtualPage.pdfIndex
                null -> displayPageIndex
                else -> continue
            }

            if (viewModel.hasCachedSpeechBubbles(bookId, sourcePageIndex)) {
                continue
            }

            val prefetchBitmap = renderSpeechBubblePrefetchBitmap(document, sourcePageIndex) ?: continue
            try {
                detectSpeechBubblesForPage(
                    sourcePageIndex = sourcePageIndex,
                    fallbackBitmap = prefetchBitmap,
                    allowHighQualityFallback = false
                )
            } finally {
                if (!prefetchBitmap.isRecycled) {
                    prefetchBitmap.recycle()
                }
            }

            kotlinx.coroutines.yield()
        }
    }

    val jumpHistory = remember { mutableStateListOf<Int>() }
    var jumpHistoryCursor by remember { mutableIntStateOf(-1) }

    fun pruneJumpHistoryForDocument() {
        if (totalPages <= 0) {
            jumpHistory.clear()
            jumpHistoryCursor = -1
            return
        }

        var index = jumpHistory.lastIndex
        while (index >= 0) {
            if (jumpHistory[index] !in 0 until totalPages) {
                jumpHistory.removeAt(index)
                if (jumpHistoryCursor >= index) jumpHistoryCursor--
            }
            index--
        }
        jumpHistoryCursor = jumpHistoryCursor.coerceIn(-1, jumpHistory.lastIndex)
    }

    fun recordJumpHistory(currentPageIndex: Int, targetPageIndex: Int) {
        if (totalPages <= 0 || currentPageIndex !in 0 until totalPages || targetPageIndex !in 0 until totalPages || currentPageIndex == targetPageIndex) {
            return
        }

        pruneJumpHistoryForDocument()
        while (jumpHistory.lastIndex > jumpHistoryCursor) {
            jumpHistory.removeAt(jumpHistory.lastIndex)
        }

        if (jumpHistoryCursor > 0 && jumpHistory.getOrNull(jumpHistoryCursor - 1) == currentPageIndex) {
            jumpHistory[jumpHistoryCursor] = targetPageIndex
            return
        }

        if (jumpHistoryCursor == -1 || jumpHistory.getOrNull(jumpHistoryCursor) != currentPageIndex) {
            jumpHistory.add(currentPageIndex)
            jumpHistoryCursor = jumpHistory.lastIndex
        }

        if (jumpHistory.lastOrNull() != targetPageIndex) {
            jumpHistory.add(targetPageIndex)
            jumpHistoryCursor = jumpHistory.lastIndex
        }

        while (jumpHistory.size > 21) {
            jumpHistory.removeAt(0)
            jumpHistoryCursor--
        }
        jumpHistoryCursor = jumpHistoryCursor.coerceIn(0, jumpHistory.lastIndex)
    }

    fun clearJumpHistory() {
        jumpHistory.clear()
        jumpHistoryCursor = -1
    }

    fun navigateToJumpHistoryPage(targetPageIndex: Int) {
        if (targetPageIndex !in 0 until totalPages) {
            pruneJumpHistoryForDocument()
            return
        }

        coroutineScope.launch {
            if (displayMode == DisplayMode.PAGINATION) {
                pagerState.animateScrollToPage(targetPageIndex)
            } else {
                verticalReaderState.scrollToPage(targetPageIndex)
            }
        }
    }

    LaunchedEffect(totalPages) {
        pruneJumpHistoryForDocument()
    }

    LaunchedEffect(currentPage, isDocumentReady, totalPages, initialScrollDone) {
        if (isDocumentReady && totalPages > 0) {
            if (initialScrollDone) {
                Timber.tag("PdfPositionDebug").v("UI: Tracking | currentPage: $currentPage | pendingRestorePage updated")
                pendingRestorePage = currentPage
                currentBookId?.let { tabStateMap[it] = currentPage }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val saveMutex = remember { Mutex() }

    val lastSavedHashes = remember(currentBookId) { IntArray(5) { -1 } }

    val currentAnnotations by rememberUpdatedState(allAnnotations)
    val currentTextBoxes by rememberUpdatedState(textBoxes.toList())
    val currentHighlights by rememberUpdatedState(userHighlights.toList())
    val currentBookmarks by rememberUpdatedState(bookmarks)
    val currentTotalPages by rememberUpdatedState(totalDisplayPages)
    val currentPageState by rememberUpdatedState(currentPage)
    val currentPendingPage by rememberUpdatedState(pendingRestorePage)

    val saveAllData = remember(currentBookId, annotationRepository, textBoxRepository, highlightRepository) {
        { force: Boolean ->
            viewModel.viewModelScope.launch {
                val bookId = currentBookId ?: return@launch

                if (!isDocumentReady && !force) {
                    Timber.tag("PdfPositionDebug").w("UI: Save ignored. Document not ready.")
                    return@launch
                }

                val annots = currentAnnotations
                val boxes = currentTextBoxes
                val highlights = currentHighlights
                val bms = currentBookmarks
                val totalPgs = currentTotalPages

                val restoreTarget = currentPendingPage ?: 0
                val page = if (!initialScrollDone) {
                    Timber.tag("PdfPositionDebug").i("UI: Save during restoration | Using restoreTarget: $restoreTarget (CurrentUI: $currentPageState)")
                    restoreTarget
                } else {
                    currentPageState
                }

                Timber.tag("PdfPositionDebug").v("UI: Save logic | Choosing: $page (UI: $currentPageState, Target: $restoreTarget, Done: $initialScrollDone)")

                val annotsHash = annots.hashCode()
                val boxesHash = boxes.hashCode()
                val highlightsHash = highlights.hashCode()
                val bmsHash = bms.hashCode()

                withContext(NonCancellable) {
                    saveMutex.withLock {
                        withContext(Dispatchers.IO) {
                            @Suppress("VariableNeverRead") var didSave = false

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
                                withContext(Dispatchers.Main) { onBookmarksChanged(bookmarksJson) }
                                lastSavedHashes[3] = bmsHash
                                didSave = true
                            }

                            if (force || page != lastSavedHashes[4]) {
                                Timber.tag("PdfPositionDebug").d("UI: COMMIT SAVE | Page: $page | Total: $totalPgs | Force: $force")
                                if (totalPgs > 0) {
                                    withContext(Dispatchers.Main) {
                                        onSavePosition(page, totalPgs)
                                    }
                                }
                                lastSavedHashes[4] = page
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
                val shouldSave = initialScrollDone || (currentPageState != 0)

                if (shouldSave) {
                    viewModel.viewModelScope.launch {
                        if (richTextController != null) {
                            withContext(NonCancellable) { richTextController.saveImmediate() }
                        }
                        saveAllData(true).join()
                    }
                } else {
                    Timber.tag("PdfPositionDebug").w("Lifecycle $event triggered: skipping save (initial settling).")
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
    var ttsDisplayPageIndex by remember { mutableStateOf<Int?>(null) }
    var ttsHighlightData by remember { mutableStateOf<TtsHighlightData?>(null) }
    var isLoadingDocument by remember { mutableStateOf(true) }

    var selectionClearTrigger by remember { mutableLongStateOf(0L) }
    var resetZoomTrigger by remember { mutableLongStateOf(0L) }

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

    fun displayPageToPdfPage(displayPageIndex: Int): Int? {
        if (displayPageIndex !in 0 until totalDisplayPages) return null
        if (virtualPages.isEmpty()) return displayPageIndex.takeIf { it in 0 until totalPages }

        return when (val virtualPage = virtualPages.getOrNull(displayPageIndex)) {
            is VirtualPage.PdfPage -> virtualPage.pdfIndex.takeIf { it in 0 until totalPages }
            is VirtualPage.BlankPage -> null
            null -> null
        }
    }

    fun pdfPageToDisplayPage(pdfPageIndex: Int): Int? {
        if (pdfPageIndex !in 0 until totalPages) return null
        if (virtualPages.isEmpty()) return pdfPageIndex.takeIf { it in 0 until totalDisplayPages }

        return virtualPages.indexOfFirst {
            it is VirtualPage.PdfPage && it.pdfIndex == pdfPageIndex
        }.takeIf { it >= 0 }
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
                        doc.openPage(pageIndex)?.use { page ->
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
                                    if (pendingNoteForNewHighlight) {
                                        pendingNoteForNewHighlight = false
                                        highlightToNoteId = newHighlight.id
                                    }
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
            val targetIndex = (currentPage + 1).coerceIn(0, virtualPages.size)
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

                showBanner("Page added at ${targetIndex + 1}")
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

                val angleRad = atan2(dy, dx)
                val angleDeg = (angleRad * 180 / PI)
                val absAngle = abs(angleDeg)

                val threshold = 10.0

                val isHorizontal = absAngle < threshold || abs(absAngle - 180.0) < threshold
                val isVertical = abs(absAngle - 90.0) < threshold

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

                showBanner("Page deleted")

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

        val pageRatio = displayPageRatios.getOrElse(currentP) { 1f }
        val screenWidthPx = view.width.toFloat().takeIf { it > 0f } ?: with(density) { 360.dp.toPx() }
        val estimatedPageHeightPx = if (pageRatio > 0) screenWidthPx / pageRatio else screenWidthPx
        val newFontSizePx = with(density) { newStyle.fontSize.sp.toPx() }
        val fontSizeNorm = if (estimatedPageHeightPx > 0) newFontSizePx / estimatedPageHeightPx else 0.02f

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
            isStrikeThrough = newStyle.isStrikeThrough,
            fontPath = newStyle.fontPath,
            fontName = newStyle.fontName
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
                if (!(isMusicianMode && isAutoScrollModeActive))  {
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
                showBanner("Extra page removed")
            }
        }
    }

    LaunchedEffect(isDocumentReady, totalDisplayPages, displayMode, currentBookId) {
        if (isDocumentReady && !initialScrollDone) {
            val pageCount = totalDisplayPages
            if (pageCount <= 0) return@LaunchedEffect

            val targetPage = pendingRestorePage?.coerceIn(0, pageCount - 1) ?: 0
            Timber.tag("PdfPositionDebug").i("UI: Restoration Start | Target: $targetPage | Mode: $displayMode | Total: $pageCount | BookId: $currentBookId")

            delay(100)

            try {
                when (displayMode) {
                    DisplayMode.PAGINATION -> {
                        if (pagerState.currentPage != targetPage) {
                            pagerState.scrollToPage(targetPage)
                        }
                    }
                    DisplayMode.VERTICAL_SCROLL -> {
                        var attempts = 0
                        while (verticalReaderState.snapToPageHandler == null && attempts < 100) {
                            delay(16)
                            attempts++
                        }
                        if (verticalReaderState.snapToPageHandler != null) {
                            if (!isScrollLocked) {
                                Timber.tag("PdfPositionDebug").d("UI: Executing Vertical snapToPage($targetPage)")
                                verticalReaderState.snapToPage(targetPage)
                            } else {
                                Timber.tag("PdfLockDiagnostic").d("UI: Skipping snapToPage request because Scroll is Locked.")
                            }
                        }
                    }
                }

                delay(50)
                initialScrollDone = true
                Timber.tag("PdfPositionDebug").i("UI: Restoration Complete | Now at Page: $currentPage | initialScrollDone: $initialScrollDone")
            } catch (e: Exception) {
                if (e is CancellationException || e.javaClass.name.contains("CancellationException")) {
                    Timber.tag("PdfPositionDebug").w("UI: Restoration cancelled (likely new recomposition)")
                    throw e
                } else {
                    Timber.tag("PdfPositionDebug").e(e, "UI: Restoration error.")
                    initialScrollDone = true
                }
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
        if (initialScrollDone) {
            if (displayMode == DisplayMode.VERTICAL_SCROLL) {
                val pageToScroll = pagerState.currentPage

                var attempts = 0
                while (verticalReaderState.snapToPageHandler == null && attempts < 50) {
                    delay(16)
                    attempts++
                }
                verticalReaderState.snapToPage(pageToScroll)
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
                            pdfDocument!!.openPage(pageIndex)?.use { page ->
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
                        val pdfDocKt = (pdfDocument as? PdfDocumentWrapper)?.pdfDocument
                        if (pdfDocKt != null) {
                            extractedText = pdfTextRepository.getOrExtractText(
                                currentBookId!!, pdfDocKt, pageIndex
                            )
                            Timber.d("Repository: Extracted text length: ${extractedText.length}")
                        }
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
                                sourceUri = effectivePdfUri,
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
                    viewModel.saveOriginalPdf(effectivePdfUri, uri)
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

    var showAiHubSheet by remember { mutableStateOf(false) }
    var summarizationResult by remember { mutableStateOf<SummarizationResult?>(null) }
    var isSummarizationLoading by remember { mutableStateOf(false) }

    var isPageSliderVisible by remember { mutableStateOf(false) }
    var sliderStartPage by remember { mutableIntStateOf(0) }
    var sliderCurrentPage by remember { mutableFloatStateOf(0f) }
    var isFastScrubbing by remember { mutableStateOf(false) }
    val scrubDebounceJob = remember { mutableStateOf<Job?>(null) }
    var startPageThumbnail by remember { mutableStateOf<Bitmap?>(null) }

    val speakerPlayer = remember(context, coroutineScope) {
        SpeakerSamplePlayer(
            context = context,
            scope = coroutineScope,
            getAuthToken = { viewModel.getAuthToken() }
        )
    }

    var clickedLinkUrl by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION") val clipboardManager = LocalClipboardManager.current

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

            viewModel.viewModelScope.launch {
                initialScrollDone = true

                if (richTextController != null) {
                    withContext(NonCancellable) {
                        richTextController.saveImmediate()
                    }
                }

                saveAllData(true).join()

                withContext(Dispatchers.Main) {
                    Timber.tag("PdfPositionDebug").d("Exit save complete. Navigating back.")
                    onNavigateBack()
                }
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

    val onDictionaryLookupStable = remember(executeWithOcrCheck, useOnlineDictionary, selectedDictPackage, uiState.credits, isProUser) {
        { text: String ->
            executeWithOcrCheck {
                val effectiveUseOnline = areReaderAiFeaturesEnabled(context) && useOnlineDictionary

                if (effectiveUseOnline) {
                    val wordCount = com.aryan.reader.countWords(text)
                    if (BuildConfig.FLAVOR != "oss" && wordCount > 1 && !isProUser) {
                        showDictionaryUpsellDialog = true
                    } else {
                        selectedTextForAi = text
                        showAiDefinitionPopup = true
                        coroutineScope.launch {
                            val token = viewModel.getAuthToken()
                            isAiDefinitionLoading = true
                            aiDefinitionResult = null
                            fetchAiDefinition(
                                text = text,
                                authToken = token,
                                onUpdate = { chunk ->
                                    val currentDefinition = aiDefinitionResult?.definition ?: ""
                                    aiDefinitionResult = AiDefinitionResult(definition = currentDefinition + chunk)
                                },
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
                        ExternalDictionaryHelper.launchDictionary(context, selectedDictPackage!!, text)
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_select_dictionary_first), Toast.LENGTH_SHORT).show()
                        showDictionarySettingsSheet = true
                    }
                }
            }
        }
    }

    val onTranslateTextStable = remember(selectedTranslatePackage) {
        { text: String ->
            if (!selectedTranslatePackage.isNullOrEmpty()) {
                ExternalDictionaryHelper.launchTranslate(context, selectedTranslatePackage!!, text)
            } else {
                Toast.makeText(context, context.getString(R.string.toast_select_translate_first), Toast.LENGTH_SHORT).show()
                showDictionarySettingsSheet = true
            }
        }
    }

    val onSearchTextStable = remember(selectedSearchPackage) {
        { text: String ->
            if (!selectedSearchPackage.isNullOrEmpty()) {
                ExternalDictionaryHelper.launchSearch(context, selectedSearchPackage!!, text)
            } else {
                Toast.makeText(context, context.getString(R.string.toast_select_search_first), Toast.LENGTH_SHORT).show()
                showDictionarySettingsSheet = true
            }
        }
    }

    val onLinkClickedStable = remember { { url: String -> clickedLinkUrl = url } }

    val onInternalLinkNavStable = remember(displayMode) {
        { targetPage: Int ->
            coroutineScope.launch {
                if (targetPage in 0 until totalPages) {
                    val current = if (displayMode == DisplayMode.PAGINATION) pagerState.currentPage else verticalReaderState.currentPage

                    if (current != targetPage) {
                        recordJumpHistory(current, targetPage)
                    }

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
            val pdfDocKt = (pdfDocument as? PdfDocumentWrapper)?.pdfDocument
            if (pdfDocKt != null) {
                val hasNative = pdfTextRepository.hasNativeText(pdfDocKt, page)
                if (!hasNative) {
                    pdfTextRepository.getOcrSearchRects(
                        document = pdfDocKt,
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
        authToken: String?,
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

                @Suppress("KotlinConstantConditions")
                if (BuildConfig.FLAVOR == "oss") {
                    val fullText = StringBuilder()
                    callByokGeminiInlineAi(
                        context = context,
                        feature = AiFeature.SUMMARIZE,
                        mimeType = "image/jpeg",
                        base64Data = base64Image,
                        systemInstruction = "You are an expert in analyzing visual content. You will be given an image of a page. Describe what is happening, identify key information, and summarize the text. Do not add a preamble.",
                        temperature = 0.2,
                        maxTokens = 8192,
                        onUpdate = {
                            fullText.append(it)
                            onUpdate(SummarizationResult(summary = fullText.toString()))
                        },
                        onError = { onUpdate(SummarizationResult(error = it)) }
                    )
                    onFinish()
                    return@withContext
                }

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
                if (authToken != null) {
                    connection.setRequestProperty("Authorization", "Bearer $authToken")
                }
                connection.outputStream.use { os ->
                    os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                Timber.d("Summarization API response code: $responseCode")
                if (responseCode == 402) {
                    onUpdate(SummarizationResult(error = "INSUFFICIENT_CREDITS"))
                    onFinish()
                    return@withContext
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val fullText = StringBuilder()
                    var lastResult: SummarizationResult? = null
                    var currentCost: Double? = null
                    var currentFreeRemaining: Int? = null

                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            try {
                                val jsonResponse = JSONObject(line!!)

                                val cost = if (jsonResponse.has("cost_deducted")) jsonResponse.optDouble("cost_deducted", -1.0) else -1.0
                                val freeRemaining = jsonResponse.optInt("free_summaries_remaining", -1)

                                if (cost > -1.0 || freeRemaining > -1) {
                                    if (cost > -1.0) currentCost = cost
                                    if (freeRemaining > -1) currentFreeRemaining = freeRemaining
                                    lastResult = SummarizationResult(summary = fullText.toString(), cost = currentCost, freeRemaining = currentFreeRemaining)
                                    onUpdate(lastResult)
                                }

                                jsonResponse.optString("chunk").takeIf { it.isNotEmpty() }?.let {
                                    fullText.append(it)
                                    lastResult = SummarizationResult(summary = fullText.toString(), cost = currentCost, freeRemaining = currentFreeRemaining)
                                    onUpdate(lastResult!!)
                                }
                                jsonResponse.optString("error").takeIf { it.isNotEmpty() }?.let {
                                    lastResult = SummarizationResult(error = it, cost = currentCost, freeRemaining = currentFreeRemaining)
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
        lastHitPoint: PdfPoint?,
        pageAspectRatio: Float,
        threshold: Float
    ): Boolean {
        if (annotation.points.isEmpty()) return false

        val effectiveThreshold = threshold + (annotation.strokeWidth / 2f)
        val thresholdSq = effectiveThreshold * effectiveThreshold

        fun distSqToEraser(px: Float, pyScaled: Float): Float {
            val e1x = hitPoint.x
            val e1yScaled = hitPoint.y / pageAspectRatio
            if (lastHitPoint == null) {
                val dx = px - e1x
                val dy = pyScaled - e1yScaled
                return dx * dx + dy * dy
            }
            val e0x = lastHitPoint.x
            val e0yScaled = lastHitPoint.y / pageAspectRatio

            val ex = e1x - e0x
            val ey = e1yScaled - e0yScaled
            val segLenSq = (ex * ex + ey * ey)
            if (segLenSq < 1e-8f) {
                val dx = px - e1x
                val dy = pyScaled - e1yScaled
                return dx * dx + dy * dy
            }
            val t = ((px - e0x) * ex + (pyScaled - e0yScaled) * ey) / segLenSq
            val tClamped = t.coerceIn(0f, 1f)
            val closestX = e0x + ex * tClamped
            val closestY = e0yScaled + ey * tClamped
            val dx = px - closestX
            val dy = pyScaled - closestY
            return dx * dx + dy * dy
        }

        if (annotation.points.size == 1) {
            val p = annotation.points[0]
            return distSqToEraser(p.x, p.y / pageAspectRatio) < thresholdSq
        }

        for (i in 0 until annotation.points.size - 1) {
            val a = annotation.points[i]
            val b = annotation.points[i + 1]

            val pax = (hitPoint.x - a.x)
            val pay = (hitPoint.y - a.y) / pageAspectRatio
            val bax = (b.x - a.x)
            val bay = (b.y - a.y) / pageAspectRatio

            val segmentLenSq = (bax * bax + bay * bay).coerceAtLeast(1e-6f)
            val t = (pax * bax + pay * bay) / segmentLenSq
            val tClamped = t.coerceIn(0f, 1f)

            val closestX = bax * tClamped
            val closestY = bay * tClamped

            val distSq = (pax - closestX) * (pax - closestX) + (pay - closestY) * (pay - closestY)

            if (distSq < thresholdSq) return true

            if (lastHitPoint != null) {
                if (distSqToEraser(a.x, a.y / pageAspectRatio) < thresholdSq) return true
                if (distSqToEraser(b.x, b.y / pageAspectRatio) < thresholdSq) return true
            }
        }

        return false
    }

    fun startTts(
        pageToReadOverride: Int? = null,
        startCharIndex: Int? = null,
        continueSession: Boolean = false
    ) {
        if (BuildConfig.FLAVOR != "oss" && currentTtsMode == TtsPlaybackManager.TtsMode.CLOUD && uiState.credits <= 0) {
            showInsufficientCreditsDialog = true
            return
        }

        Timber.d("TTS button clicked: Starting TTS for current page/selection")
        if (pdfDocument == null || totalPages == 0) {
            return
        }
        coroutineScope.launch {
            val token = viewModel.getAuthToken()
            val requestedDisplayPage = pageToReadOverride ?: currentPage
            val pageToRead = displayPageToPdfPage(requestedDisplayPage)
            if (pageToRead == null) {
                Timber.w("TTS: Ignoring blank or invalid display page $requestedDisplayPage.")
                return@launch
            }
            val displayPageForTts = pdfPageToDisplayPage(pageToRead) ?: requestedDisplayPage
            var rawPageText: String? = null
            var tempPage: ReaderPage? = null
            var tempTextPage: ReaderTextPage? = null
            @Suppress("CanBeVal") var ocrAttempted = false

            try {
                withContext(Dispatchers.IO) {
                    Timber.d("TTS: Opening page $pageToRead for Pdfium text extraction.")
                    tempPage = pdfDocument!!.openPage(pageToRead)
                    tempTextPage = tempPage?.openTextPage()
                    val charCount = tempTextPage?.textPageCountChars() ?: 0
                    if (charCount > 0) {
                        rawPageText = tempTextPage?.textPageGetText(0, charCount)?.trim()
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

            ocrUsedForCurrentPageTts = false
            withContext(Dispatchers.IO) {
                tempPage?.close()
            }
            if (rawPageText.isNullOrBlank()) {
                Timber.i("TTS: Pdfium text is blank or extraction failed. OCR fallback is temporarily disabled.")
            } else {
                Timber.d("TTS: Closed page $pageToRead after successful Pdfium text extraction.")
            }

            if (rawPageText != null && rawPageText!!.isNotBlank()) {
                val processedText = preprocessTextForTts(rawPageText!!)
                ttsPageData = TtsPageData(pageToRead, processedText, ocrUsedForCurrentPageTts)
                ttsDisplayPageIndex = displayPageForTts

                val cleanStartIndex = if (startCharIndex != null && startCharIndex >= 0) {
                    processedText.indexMap.indexOfFirst { it >= startCharIndex }.coerceAtLeast(0)
                } else {
                    0
                }

                val textToChunk = processedText.cleanText.substring(cleanStartIndex)

                val chunks = splitTextIntoChunks(textToChunk)

                val bookTitle = (pdfDocument as? PdfDocumentWrapper)?.pdfDocument?.getDocumentMeta()?.title?.takeIf { it.isNotBlank() }
                    ?: effectivePdfUri.lastPathSegment ?: "Document"
                val pageTitle = "Page ${pageToRead + 1}"

                val ttsChunks = chunks.mapIndexed { index, text -> TtsChunk(text, "", index) }

                ttsController.start(
                    chunks = ttsChunks,
                    bookTitle = bookTitle,
                    chapterTitle = pageTitle,
                    coverImageUri = null,
                    continueSession = continueSession,
                    ttsMode = currentTtsMode,
                    playbackSource = "READER",
                    authToken = token
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

                val nextPage = pageToRead + 1
                if (nextPage < totalPages) {
                    Timber.i("TTS found no text on page $pageToRead. Skipping to $nextPage.")
                    isAutoPagingForTts = true
                    val nextDisplayPage = pdfPageToDisplayPage(nextPage)
                    if (displayMode == DisplayMode.PAGINATION) {
                        nextDisplayPage?.let { coroutineScope.launch { pagerState.animateScrollToPage(it) } }
                    } else {
                        if (nextDisplayPage != null) {
                            startTts(pageToReadOverride = nextDisplayPage, continueSession = true)
                        } else {
                            ttsController.stop()
                            isAutoPagingForTts = false
                        }
                    }
                } else {
                    ttsController.stop()
                    isAutoPagingForTts = false
                    Timber.w("TTS start failed (reached end of document): $finalError")
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(), onResult = { _ -> startTts() })

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }

    val startTtsWithPermissionCheck: (Int?, Int?) -> Unit = remember(context, activity, executeWithOcrCheck) {
        { pageOverride, startCharIndex ->
            executeWithOcrCheck {
                when {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        startTts(pageOverride, startCharIndex)
                    }

                    activity?.shouldShowRequestPermissionRationale(
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == true -> {
                        showPermissionRationaleDialog = true
                    }

                    else -> {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }

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
                        val nextDisplayPage = pdfPageToDisplayPage(nextPage)
                        if (nextDisplayPage != null) {
                            coroutineScope.launch { pagerState.animateScrollToPage(nextDisplayPage) }
                        } else {
                            ttsController.stop()
                            isAutoPagingForTts = false
                        }
                    }

                    DisplayMode.VERTICAL_SCROLL -> {
                        Timber.d("TTS auto-starting on next page (no scroll): ${nextPage + 1}")
                        val nextDisplayPage = pdfPageToDisplayPage(nextPage)
                        if (nextDisplayPage != null) {
                            startTts(pageToReadOverride = nextDisplayPage, continueSession = true)
                        } else {
                            ttsController.stop()
                            isAutoPagingForTts = false
                        }
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

    LaunchedEffect(effectivePdfUri, pdfiumCore, documentPassword) {
        Timber.tag("PdfTabSync").i("UI: LaunchedEffect triggered by URI change: $effectivePdfUri")

        Timber.tag("PdfTabSync").d("UI: Loading State -> activeTabBookId: ${uiState.activeTabBookId}, isLoading: $isLoadingDocument")

        bookmarks = loadPdfBookmarksFromJson(uiState.initialBookmarksJson ?: initialBookmarksJson)

        isLoadingDocument = true
        isDocumentReady = false
        errorMessage = null
        documentMetadataTitle = null

        if (showPasswordDialog) isPasswordError = false

        ttsController.stop()
        ocrUsedForCurrentPageTts = false
        flatTableOfContents = emptyList()

        val fastId = getFastFileId(context, effectivePdfUri)
        val selectedId = uiState.selectedBookId

        if (selectedId != null && selectedId != fastId) {
            Timber.tag("FolderAnnotationSync").i("Detected ID mismatch. Legacy: $fastId, Selected: $selectedId. Initiating migration.")
            viewModel.checkAndMigrateLegacyBookId(fastId, selectedId)
            currentBookId = selectedId
        } else {
            currentBookId = fastId
        }

        val cachedItem = documentCache.get(currentBookId!!)
        if (cachedItem != null) {
            Timber.tag("PdfTabSync").i("UI: Restoring from cache for $currentBookId")
            pdfDocument = cachedItem.doc
            pfdState = cachedItem.pfd
            totalPages = cachedItem.totalPages
            pageAspectRatios = cachedItem.pageAspectRatios
            flatTableOfContents = cachedItem.flatTableOfContents

            val mapPage = tabStateMap[currentBookId!!]
            val uiPage = uiState.initialPageInBook
            Timber.tag("PdfTabSync").d("UI: Restoring position | tabStateMap=$mapPage, uiState=$uiPage, initialPage=$initialPage")

            pendingRestorePage = mapPage ?: uiPage ?: initialPage
            initialScrollDone = false
            isDocumentReady = true
            isLoadingDocument = false
            return@LaunchedEffect
        }

        val mapPageInit = tabStateMap[currentBookId!!]
        val uiPageInit = uiState.initialPageInBook
        Timber.tag("PdfTabSync").d("UI: Initial position | tabStateMap=$mapPageInit, uiState=$uiPageInit, initialPage=$initialPage")
        pendingRestorePage = mapPageInit ?: uiPageInit ?: initialPage
        initialScrollDone = false

        pdfDocument = null
        pfdState = null
        totalPages = 0

        var currentPfdOpened: ParcelFileDescriptor? = null
        try {
            withContext(Dispatchers.IO) {
                Timber.tag("PdfTabSync").v("UI: Opening PFD for $effectivePdfUri")

                if (pdfUri.scheme != "opds-pse") {
                    currentPfdOpened = context.contentResolver.openFileDescriptor(effectivePdfUri, "r")
                    if (currentPfdOpened == null) throw Exception("Failed to open ParcelFileDescriptor")
                }

                val doc = DocumentFactory.loadDocument(context, effectivePdfUri, uiState.selectedFileType ?: FileType.PDF, documentPassword, pdfiumCore)

                if (!isActive) {
                    doc.close()
                    currentPfdOpened?.close()
                    return@withContext
                }

                pdfDocument = doc
                documentMetadataTitle = (doc as? PdfDocumentWrapper)?.let { wrapper ->
                    PdfiumEngineProvider.withPdfium {
                        wrapper.pdfDocument.getDocumentMeta().title?.takeIf { it.isNotBlank() }
                    }
                }
                pfdState = currentPfdOpened
                val pagesCount = doc.getPageCount()

                if (pagesCount > 0) {
                    try {
                        val tableOfContents = doc.getTableOfContents()
                        val flattened = flattenToc(tableOfContents)
                        withContext(Dispatchers.Main) { flatTableOfContents = flattened }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to load TOC")
                    }
                }

                totalPages = pagesCount

                if (pagesCount > 0) {
                    val cachedRatios = pdfTextRepository.getPageRatios(currentBookId!!)

                    val ratios = if (cachedRatios != null && cachedRatios.size == pagesCount) {
                        Timber.i("Loaded ${cachedRatios.size} page ratios from cache.")
                        cachedRatios
                    } else {
                        val computedRatios = ArrayList<Float>(pagesCount)
                        doc.openPage(0)?.use { page ->
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
                                    doc.openPage(i)?.use { page ->
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

                    documentCache.put(
                        currentBookId!!,
                        DocumentCacheItem(
                            doc = doc,
                            pfd = currentPfdOpened!!,
                            totalPages = pagesCount,
                            pageAspectRatios = ratios,
                            flatTableOfContents = flatTableOfContents
                        )
                    )

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
                                doc.openPage(i)?.use { page ->
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
                } else {
                    isDocumentReady = true
                    isLoadingDocument = false
                }

                Timber.tag("PdfTabSync").v("UI: Pdfium Document created. Page count: $pagesCount")
            }
        } catch (e: Throwable) {
            if (e is CancellationException || e.javaClass.name.contains("CancellationException")) throw e
            Timber.tag("PdfTabSync").e(e, "UI: Error in load effect for $effectivePdfUri")
            val errorString = e.toString()
            val causeString = e.cause?.toString() ?: ""

            if (errorString.contains("PasswordException") || causeString.contains("PasswordException")) {
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
        if (pagerState.isScrollInProgress) {
            if (displayMode == DisplayMode.PAGINATION && !isAutoPagingForTts && (ttsState.isPlaying || ttsState.isLoading)) {
                ttsController.stop()
            }
        }
    }

    var previousPage by remember(displayMode) { mutableIntStateOf(-1) }
    LaunchedEffect(currentPage) {
        if (previousPage != -1 && previousPage != currentPage) {
            if (isAutoPagingForTts) {
                startTts(continueSession = true)
            } else if (displayMode == DisplayMode.PAGINATION && (ttsState.isPlaying || ttsState.isLoading)) {
                Timber.d("Page changed manually while TTS active, stopping.")
                ttsController.stop()
            }
        }
        previousPage = currentPage
        summarizationResult = null
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
            documentCache.evictAll()

            val docToClose = pdfDocument
            val pfdToClose = pfdState
            pdfDocument = null
            pfdState = null

            if (docToClose != null || pfdToClose != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    docToClose?.let {
                        Timber.d("Closing PDF document in onDispose.")
                        try { it.close() } catch (e: Exception) { Timber.e(e, "Error closing document") }
                    }
                    pfdToClose?.let {
                        Timber.d("Closing ParcelFileDescriptor in onDispose: $it")
                        try { it.close() } catch (e: Exception) { Timber.e(e, "Error closing ParcelFileDescriptor") }
                    }
                }
            }
        }
    }

    var searchHighlightTarget by remember { mutableStateOf<SearchResult?>(null) }

    var isOcrScanning by remember { mutableStateOf(false) }

    LaunchedEffect(effectivePdfUri, currentBookId, totalPages) {
        if (currentBookId == null || totalPages == 0) return@LaunchedEffect
        if (isBackgroundIndexing && backgroundIndexingProgress > 0f) return@LaunchedEffect
        if (uiState.selectedFileType != FileType.PDF) return@LaunchedEffect

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

                bgPfd = context.contentResolver.openFileDescriptor(effectivePdfUri, "r")
                val openedBgPfd = bgPfd
                if (openedBgPfd != null) {
                    bgDoc = PdfiumEngineProvider.withPdfium {
                        pdfiumCore.newDocument(openedBgPfd, documentPassword)
                    }

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
                    PdfiumEngineProvider.withPdfium {
                        bgDoc?.close()
                    }
                    bgPfd?.close()
                } catch (e: Exception) {
                    Timber.e(e, "Indexer: Cleanup failed")
                }
                isBackgroundIndexing = false
            }
        }
    }

    var activeQuery by remember { mutableStateOf("") }

    var smartSearchResult by remember { mutableStateOf<SmartSearchResult?>(null) }
    var currentPdfSearchResult by remember { mutableStateOf<SearchResult?>(null) }

    val imeHeight = WindowInsets.ime.getBottom(density)

    val bottomScrollLimitPx = remember(isEditMode, imeHeight, navBarHeight, dockLocation, isDockMinimized, systemUiMode, showStandardBars) {
        val effectiveNavBar = if (systemUiMode == SystemUiMode.DEFAULT || (systemUiMode == SystemUiMode.SYNC && showStandardBars)) navBarHeight else 0
        if (isEditMode) {
            if (imeHeight > 0) {
                imeHeight.toFloat()
            } else {
                if (dockLocation == DockLocation.BOTTOM && !isDockMinimized) {
                    with(density) { 64.dp.toPx() } + effectiveNavBar
                } else {
                    with(density) { 16.dp.toPx() } + effectiveNavBar
                }
            }
        } else {
            if (showStandardBars) {
                with(density) { 56.dp.toPx() } + effectiveNavBar
            } else {
                effectiveNavBar.toFloat()
            }
        }
    }

    val topScrollLimitPx = with(density) { verticalHeaderHeight.toPx() }

    LaunchedEffect(imeHeight, navBarHeight, systemUiMode, showStandardBars, isEditMode, bottomScrollLimitPx) {
        Timber.tag(PDF_LAYOUT_DEBUG_TAG).d("""
            [Global Metrics]
            - IME Height: ${imeHeight}px (${with(density) { imeHeight.toDp() }})
            - Nav Bar Height: ${navBarHeight}px (${with(density) { navBarHeight.toDp() }})
            - System UI Mode: $systemUiMode
            - Show Standard Bars: $showStandardBars
            - Is Edit Mode: $isEditMode
            - Bottom Scroll Limit: ${bottomScrollLimitPx}px
        """.trimIndent())
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
        ((ttsState.currentText != null || ttsState.isLoading) && ttsState.playbackSource == "READER") || isAutoPagingForTts

    val onInternalLinkNav: (Int) -> Unit = { targetPage ->
        coroutineScope.launch {
            if (targetPage in 0 until totalPages) {
                val current = if (displayMode == DisplayMode.PAGINATION) pagerState.currentPage else verticalReaderState.currentPage

                if (current != targetPage) {
                    recordJumpHistory(current, targetPage)
                }

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
            val distance = abs(pagerState.currentPage - paginationDraggingOriginPage)
            (distance + 1).coerceAtLeast(1)
        } else {
            1
        }
    }

    fun navigateToPdfSearchResult(result: SearchResult) {
        currentPdfSearchResult = result
        searchHighlightTarget = result

        coroutineScope.launch {
            val targetPage = result.locationInSource
            val current = if (displayMode == DisplayMode.PAGINATION) pagerState.currentPage else verticalReaderState.currentPage

            if (current != targetPage) {
                recordJumpHistory(current, targetPage)
            }

            if (displayMode == DisplayMode.PAGINATION) {
                if (pagerState.currentPage != targetPage) {
                    pagerState.scrollToPage(targetPage)
                }
            } else {
                verticalReaderState.scrollToPage(targetPage)
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

            showVisualOptionsSheet -> showVisualOptionsSheet = false

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

            showAiHubSheet -> showAiHubSheet = false
            showPermissionRationaleDialog -> showPermissionRationaleDialog = false
            showSummarizationUpsellDialog -> showSummarizationUpsellDialog = false
            showAiDefinitionPopup -> showAiDefinitionPopup = false
            showDictionaryUpsellDialog -> showDictionaryUpsellDialog = false
            showCustomizeToolsSheet -> showCustomizeToolsSheet = false
            isPageSliderVisible -> {
                isPageSliderVisible = false
                showBars = true
            }

            searchState.isSearchActive -> {
                searchState.isSearchActive = false
                searchState.onQueryChange("")
            }

            showTtsSettingsSheet -> showTtsSettingsSheet = false
            showThemePanel -> showThemePanel = false

            else -> {
                saveStateAndExit()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState, gesturesEnabled = drawerState.isOpen, drawerContent = {
            ModalDrawerSheet(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                PdfNavigationDrawerContent(
                    pdfDocument = pdfDocument,
                    flatTableOfContents = flatTableOfContents,
                    bookmarks = bookmarks,
                    userHighlights = userHighlights,
                    currentPage = currentPage,
                    totalPages = totalDisplayPages,
                    customHighlightColors = customHighlightColors,
                    onPageSelected = { targetPage ->
                        coroutineScope.launch {
                            val current = if (displayMode == DisplayMode.PAGINATION) pagerState.currentPage else verticalReaderState.currentPage

                            if (current != targetPage) {
                                recordJumpHistory(current, targetPage)
                            }

                            if (displayMode == DisplayMode.PAGINATION) {
                                pagerState.scrollToPage(targetPage)
                            } else {
                                verticalReaderState.scrollToPage(targetPage)
                            }
                        }
                    },
                    onRenameBookmark = { bookmarkToRename, newTitle ->
                        if (newTitle.isNotBlank()) {
                            val updatedBookmark = bookmarkToRename.copy(title = newTitle)
                            bookmarks = (bookmarks - bookmarkToRename) + updatedBookmark
                        }
                    },
                    onDeleteBookmark = { bookmarkToDelete ->
                        bookmarks = bookmarks - bookmarkToDelete
                    },
                    onDeleteHighlight = { highlightToDelete ->
                        onHighlightDelete(highlightToDelete.id)
                    },
                    onNoteRequested = onNoteRequested,
                    onCloseDrawer = {
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        }) {
        @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { _ ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                event.changes.forEach { change ->
                                    if (change.type == androidx.compose.ui.input.pointer.PointerType.Stylus ||
                                        change.type == androidx.compose.ui.input.pointer.PointerType.Eraser) {
                                        val buttons = event.buttons
                                        Timber.tag("StylusDebug").d(
                                            "GlobalPointer | type=${change.type}, pressed=${change.pressed}, " +
                                                    "primary=${buttons.isPrimaryPressed}, secondary=${buttons.isSecondaryPressed}, " +
                                                    "tertiary=${buttons.isTertiaryPressed}, back=${buttons.isBackPressed}, " +
                                                    "forward=${buttons.isForwardPressed}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        Timber.tag("StylusDebug").d("GlobalKey | key=${keyEvent.key}, type=${keyEvent.type}")
                        false
                    }
            ) {
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
                            .align(Alignment.BottomStart)
                            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                            .padding(start = 16.dp, bottom = 120.dp)
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
                            },
                    )
                }

                // --- Main Content Area ---
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                                    text = errorMessage ?: stringResource(R.string.error_failed_load_pdf),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }

                        pdfDocument != null && totalPages > 0 -> {
                            val stablePdfDocument = remember(pdfDocument) { StableHolder(pdfDocument!!) }
                            when (displayMode) {
                                DisplayMode.PAGINATION -> {
                                    val onPaginationPreSingleTap: (Offset) -> Boolean = { tapOffset ->
                                        val canTurnPagesByTap = tapToNavigateEnabled &&
                                            (currentPageScale <= 1.02f || isScrollLocked)

                                        if (!canTurnPagesByTap) {
                                            false
                                        } else {
                                            val oneQuarterWidthPx = boxMaxWidthFloat / 4f
                                            when {
                                                tapOffset.x < oneQuarterWidthPx -> {
                                                    coroutineScope.launch {
                                                        val targetPage =
                                                            (pagerState.currentPage - 1).coerceAtLeast(0)
                                                        if (targetPage != pagerState.currentPage) {
                                                            pagerState.scrollToPage(targetPage)
                                                        }
                                                    }
                                                    true
                                                }

                                                tapOffset.x > (boxMaxWidthFloat - oneQuarterWidthPx) -> {
                                                    coroutineScope.launch {
                                                        val targetPage =
                                                            (pagerState.currentPage + 1).coerceAtMost(
                                                                pagerState.pageCount - 1
                                                            )
                                                        if (targetPage != pagerState.currentPage) {
                                                            pagerState.scrollToPage(targetPage)
                                                        }
                                                    }
                                                    true
                                                }

                                                else -> false
                                            }
                                        }
                                    }

                                    Box(modifier = Modifier.fillMaxSize()) {
                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier.fillMaxSize(),
                                            key = { it },
                                            beyondViewportPageCount = dynamicBeyondViewportPageCount,
                                            userScrollEnabled = run {
                                                val enabled = (currentPageScale == 1f || (isScrollLocked && displayMode == DisplayMode.PAGINATION)) && !(ttsState.isPlaying || ttsState.isLoading || searchState.isSearchActive) && !isPageSliderVisible && paginationDraggingBoxId == null
                                                SideEffect {
                                                    Timber.tag("PdfZoomDebug").v("Pager Scroll Enabled: $enabled (Scale: $currentPageScale, Playing: ${ttsState.isPlaying}, Slider: $isPageSliderVisible, DraggingBox: $paginationDraggingBoxId)")
                                                }
                                                enabled
                                            }
                                        ) { pageIndex ->
                                            val isVisiblePage = remember(pagerState.currentPage, pageIndex) {
                                                abs(pagerState.currentPage - pageIndex) <= 1
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
                                                    val pdfDocKt = (pdfDocument as? PdfDocumentWrapper)?.pdfDocument
                                                    val hasNative = if (pdfDocKt != null) pdfTextRepository.hasNativeText(
                                                        pdfDocKt, pageIndex
                                                    ) else false
                                                    Timber.d(
                                                        "Page $pageIndex Has Native Text: $hasNative"
                                                    )

                                                    if (!hasNative) {
                                                        Timber.d(
                                                            "Fetching OCR rects for query: '${target.query}'"
                                                        )
                                                        val rects = if (pdfDocKt != null) pdfTextRepository.getOcrSearchRects(
                                                            document = pdfDocKt,
                                                            pageIndex = pageIndex,
                                                            query = target.query,
                                                            onModelDownloading = {
                                                                isOcrModelDownloading = true
                                                            }) else emptyList()
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

                                            val currentStrokeColorState by rememberUpdatedState(
                                                currentStrokeColor
                                            )
                                            val currentStrokeWidthState by rememberUpdatedState(
                                                currentStrokeWidth
                                            )

                                            @Suppress("ControlFlowWithEmptyBody") val onDrawPagination =
                                                remember(pageIndex) {
                                                    { point: PdfPoint, isEraserOverride: Boolean ->
                                                        val effectiveTool = if (isEraserOverride) InkType.ERASER else currentSelectedTool
                                                        if (effectiveTool == InkType.TEXT) {
                                                        } else if (effectiveTool == InkType.ERASER) {
                                                            val aspectRatio = pageAspectRatios.getOrElse(pageIndex) { 1f }
                                                            val existing = allAnnotations[pageIndex] ?: emptyList()
                                                            val toRemove = existing.filter {
                                                                isAnnotationHit(it, point, lastEraserPoint, aspectRatio, currentStrokeWidthState)
                                                            }
                                                            lastEraserPoint = point
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

                                            @Suppress("ControlFlowWithEmptyBody") val onDrawStartPagination =
                                                remember(pageIndex) {
                                                    { point: PdfPoint, isEraserOverride: Boolean ->
                                                        if (showToolSettings) {
                                                            showToolSettings = false
                                                        } else {
                                                            val effectiveTool = if (isEraserOverride) InkType.ERASER else currentSelectedTool
                                                            if (effectiveTool == InkType.TEXT) {
                                                            } else if (effectiveTool == InkType.ERASER) {
                                                                lastEraserPoint = point
                                                                erasedAnnotationsFromStroke.clear()
                                                                val aspectRatio = pageAspectRatios.getOrElse(pageIndex) { 1f }
                                                                val existing = allAnnotations[pageIndex] ?: emptyList()
                                                                val toRemove = existing.filter {
                                                                    isAnnotationHit(it, point, lastEraserPoint, aspectRatio, currentStrokeWidthState)
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
                                                                    effectiveTool,
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
                                                activeTheme = activeTheme,
                                                activeTextureAlpha = 1f - globalTextureTransparency,
                                                excludeImages = excludeImages,
                                                isScrollLocked = isScrollLocked,
                                                customHighlightColors = customHighlightColors,
                                                onPaletteClick = {
                                                    highlightColorPickerInitialSlot = PdfHighlightColor.YELLOW
                                                    showHighlightColorPicker = true
                                                },
                                                onScaleChanged = { newScale ->
                                                    if (pagerState.currentPage == pageIndex) {
                                                        currentPageScale = newScale
                                                    }
                                                },
                                                ttsHighlightData = if (ttsDisplayPageIndex == pageIndex) ttsHighlightData else null,
                                                searchQuery = searchState.searchQuery,
                                                searchHighlightMode = searchHighlightMode,
                                                searchResultToHighlight = if (pagerState.currentPage == pageIndex) searchHighlightTarget else null,
                                                ocrHoverHighlights = stableOcrRects,
                                                modifier = Modifier.fillMaxSize(),
                                                showAllTextHighlights = showAllTextHighlights,
                                                onHighlightLoading = { /* no-op for paginated mode */ },
                                                onPreSingleTap = onPaginationPreSingleTap,
                                                onSingleTap = { _ -> onSingleTapStable() },
                                                isProUser = isProUser,
                                                onShowDictionaryUpsellDialog = {
                                                    if (useOnlineDictionary) {
                                                        showDictionaryUpsellDialog = true
                                                    }
                                                },
                                                onWordSelectedForAiDefinition = onDictionaryLookupStable,
                                                onTranslateText = onTranslateTextStable,
                                                onSearchText = onSearchTextStable,
                                                onOcrStateChange = onOcrStateChange,
                                                onLinkClicked = { url -> clickedLinkUrl = url },
                                                onInternalLinkClicked = onInternalLinkNav,
                                                isBookmarked = isPageBookmarked,
                                                onBookmarkClick = { onToggleBookmark(pageIndex) },
                                                isZoomEnabled = true,
                                                clearSelectionTrigger = selectionClearTrigger,
                                                resetZoomTrigger = resetZoomTrigger,
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
                                                onNoteRequested = onNoteRequested,
                                                onTts = { pageIdx, charIdx -> startTtsWithPermissionCheck(pageIdx, charIdx) },
                                                activeToolThickness = currentStrokeWidthState,
                                                lockedState = lockedState,
                                                onZoomAndPanChanged = { newScale, newOffset ->
                                                    if (pagerState.currentPage == pageIndex) {
                                                        currentActiveScale = newScale
                                                        currentActiveOffset = newOffset
                                                    }
                                                },
                                                onDetectBubbles = { sourcePageIndex, bitmap ->
                                                    detectSpeechBubblesForPage(sourcePageIndex, bitmap)
                                                },
                                                onShowPanelPopup = { bitmapWithRects ->
                                                    poppedUpPanelBitmap = bitmapWithRects
                                                },
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
                                                isAutoScrollPlaying = isAutoScrollPlaying,
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
                                                    Timber.tag("PdfTextBoxDebug").d("Pagination onTextBoxDragStart [ID: ${box.id}] initialized")
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
                                                    paginationDragPageHeight = renderedHeight

                                                    val baseBoxLeft = offsetX + (box.relativeBounds.left * renderedWidth)
                                                    val baseBoxTop = offsetY + (box.relativeBounds.top * renderedHeight)
                                                    val centerX = containerWidthPx / 2f
                                                    val centerY = containerHeightPx / 2f
                                                    val screenX = (baseBoxLeft - centerX) * currentActiveScale + centerX + currentActiveOffset.x
                                                    val screenY = (baseBoxTop - centerY) * currentActiveScale + centerY + currentActiveOffset.y

                                                    paginationDraggingOffset = Offset(screenX, screenY)
                                                },
                                                onTextBoxDrag = { dragDelta ->
                                                    Timber.tag("PdfTextBoxDebug").v("Pagination onTextBoxDrag delta=$dragDelta | currentOffset=$paginationDraggingOffset")
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
                                                    Timber.tag("PdfTextBoxDebug").d("Pagination onTextBoxDragEnd called [ID: $paginationDraggingBoxId] | EndOffset=$paginationDraggingOffset")
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

                                                            val centerX = containerWidthPx / 2f
                                                            val centerY = containerHeightPx / 2f

                                                            val unzoomedX = (paginationDraggingOffset.x - currentActiveOffset.x - centerX) / currentActiveScale + centerX
                                                            val unzoomedY = (paginationDraggingOffset.y - currentActiveOffset.y - centerY) / currentActiveScale + centerY

                                                            val rawRelX = (unzoomedX - offsetX) / renderedWidth
                                                            val rawRelY = (unzoomedY - offsetY) / renderedHeight

                                                            val maxRelX = (1f - relW - padRelX).coerceAtLeast(padRelX)
                                                            val maxRelY = (1f - relH - padRelY).coerceAtLeast(padRelY)

                                                            val finalRelX = rawRelX.coerceIn(padRelX, maxRelX)
                                                            val finalRelY = rawRelY.coerceIn(padRelY, maxRelY)

                                                            val targetOffsetUnzoomedX = offsetX + (finalRelX * renderedWidth)
                                                            val targetOffsetUnzoomedY = offsetY + (finalRelY * renderedHeight)
                                                            val targetOffset = Offset(
                                                                (targetOffsetUnzoomedX - centerX) * currentActiveScale + centerX + currentActiveOffset.x,
                                                                (targetOffsetUnzoomedY - centerY) * currentActiveScale + centerY + currentActiveOffset.y
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
                                                onDragPageTurn = { direction ->
                                                    coroutineScope.launch {
                                                        val targetPage = pagerState.currentPage + direction
                                                        if (targetPage in 0 until totalDisplayPages) {
                                                            pagerState.animateScrollToPage(targetPage)
                                                        }
                                                    }
                                                },
                                                isBubbleZoomModeActive = isBubbleZoomModeActive,
                                                isVisible = isVisiblePage,
                                                isActivePage = pagerState.currentPage == pageIndex,
                                                isScrolling = pagerState.isScrollInProgress
                                            )
                                        }

                                        if (paginationDraggingBoxId != null) {
                                            val draggedBox = textBoxes.find { it.id == paginationDraggingBoxId }
                                            if (draggedBox != null) {
                                                val fontScaleRatio = if (paginationDraggingSize.height > 0)
                                                    paginationDragPageHeight / paginationDraggingSize.height else 1f

                                                val screenHeight = boxConstraints.maxHeight.toFloat()
                                                val boxBottomY = paginationDraggingOffset.y + (paginationDraggingSize.height * currentActiveScale)
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
                                                        .graphicsLayer {
                                                            scaleX = currentActiveScale
                                                            scaleY = currentActiveScale
                                                            transformOrigin = TransformOrigin(0f, 0f)
                                                        }
                                                ) {
                                                    ResizableTextBox(
                                                        box = draggedBox.copy(
                                                            relativeBounds = Rect(0f, 0f, 1f, 1f),
                                                            fontSize = draggedBox.fontSize * fontScaleRatio
                                                        ),
                                                        isSelected = true,
                                                        isEditMode = false,
                                                        isDarkMode = isPdfDarkMode,
                                                        pageWidthPx = paginationDraggingSize.width,
                                                        pageHeightPx = paginationDraggingSize.height,
                                                        scale = currentActiveScale,
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
                                    val headerHeight = verticalHeaderHeight
                                    val footerHeight = verticalFooterHeight

                                    val currentSelectedTool by rememberUpdatedState(selectedTool)
                                    val currentStrokeColorState by rememberUpdatedState(
                                        currentStrokeColor
                                    )
                                    val currentStrokeWidthState by rememberUpdatedState(
                                        currentStrokeWidth
                                    )

                                    @Suppress("ControlFlowWithEmptyBody") val onDrawStartStable =
                                        remember {
                                            { pageIndex: Int, point: PdfPoint, isEraserOverride: Boolean ->
                                                if (showToolSettings) {
                                                    showToolSettings = false
                                                } else {
                                                    val effectiveTool = if (isEraserOverride) InkType.ERASER else currentSelectedTool
                                                    if (effectiveTool == InkType.TEXT) {
                                                    } else if (effectiveTool == InkType.ERASER) {
                                                        lastEraserPoint = point
                                                        erasedAnnotationsFromStroke.clear()

                                                        val aspectRatio = pageAspectRatios.getOrElse(pageIndex) { 1f }
                                                        val existing = allAnnotations[pageIndex] ?: emptyList()
                                                        val toRemove = existing.filter {
                                                            isAnnotationHit(it, point, lastEraserPoint, aspectRatio, currentStrokeWidthState)
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
                                                            effectiveTool,
                                                            currentStrokeColorState,
                                                            currentStrokeWidthState
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                    val onDrawStable = remember(isHighlighterSnapEnabled, isCurrentToolHighlighter, calculateSnappedPoint) {
                                        { pageIndex: Int, point: PdfPoint, isEraserOverride: Boolean ->
                                            val effectiveTool = if (isEraserOverride) InkType.ERASER else currentSelectedTool
                                            if (effectiveTool == InkType.ERASER) {
                                                val aspectRatio = pageAspectRatios.getOrElse(pageIndex) { 1f }
                                                val existing = allAnnotations[pageIndex] ?: emptyList()
                                                val toRemove = existing.filter {
                                                    isAnnotationHit(it, point, lastEraserPoint, aspectRatio, currentStrokeWidthState)
                                                }
                                                lastEraserPoint = point
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
                                            activeTheme = activeTheme,
                                            activeTextureAlpha = 1f - globalTextureTransparency,
                                            excludeImages = excludeImages,
                                            isScrollLocked = isScrollLocked,
                                            customHighlightColors = customHighlightColors,
                                            onPaletteClick = { showHighlightColorPicker = true },
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
                                            onTranslateText = onTranslateTextStable,
                                            onSearchText = onSearchTextStable,
                                            ttsHighlightData = ttsHighlightData,
                                            ttsReadingPage = ttsDisplayPageIndex,
                                            userHighlights = userHighlights,
                                            onHighlightAdd = onHighlightAdd,
                                            onHighlightUpdate = onHighlightUpdate,
                                            onHighlightDelete = onHighlightDelete,
                                            onNoteRequested = onNoteRequested,
                                            onTts = { pageIdx, charIdx -> startTtsWithPermissionCheck(pageIdx, charIdx) },
                                            activeToolThickness = currentStrokeWidthState,
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
                                                Timber.tag("PdfTextBoxDebug").d("Vertical Reader onTextBoxMoved[ID: $boxId] newPage=$newPageIndex bounds=$newBounds")
                                                val idx = textBoxes.indexOfFirst { it.id == boxId }
                                                if (idx != -1) {
                                                    val oldBox = textBoxes[idx]
                                                    textBoxes[idx] = oldBox.copy(pageIndex = newPageIndex, relativeBounds = newBounds)
                                                }
                                            },
                                            isAutoScrollPlaying = isAutoScrollPlaying,
                                            isAutoScrollTempPaused = isAutoScrollTempPaused,
                                            autoScrollSpeed = autoScrollSpeed * 0.5f,
                                            onInteractionListener = onAutoScrollInteraction,
                                            lockedState = lockedState,
                                            onZoomAndPanChanged = { newScale, newOffset ->
                                                currentActiveScale = newScale
                                                currentActiveOffset = newOffset
                                            },
                                            resetZoomTrigger = resetZoomTrigger,
                                            isBubbleZoomModeActive = isBubbleZoomModeActive,
                                            onDetectBubbles = { sourcePageIndex, bitmap ->
                                                detectSpeechBubblesForPage(sourcePageIndex, bitmap)
                                            }
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
                                                        verticalReaderState.scrollToTop()
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
                                                        verticalReaderState.scrollToBottom()
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
                        .padding(top = topOverlayInset)
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
                                text = stringResource(
                                    R.string.msg_downloading_language_pack,
                                    stringResource(ocrLanguage.displayNameRes).substringBefore("(").trim()
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = bubbleZoomDownloadProgress != null,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        // shift down slightly if the OCR indicator is also showing
                        .padding(top = topOverlayInset + if (isOcrModelDownloading) 64.dp else 0.dp)
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
                            val progress = bubbleZoomDownloadProgress ?: 0f
                            if (progress > 0f) {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(
                                    R.string.msg_downloading_bubble_zoom_model_progress,
                                    (progress * 100).toInt()
                                ),
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
                                contentDescription = stringResource(R.string.content_desc_exit_slider_navigation)
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
                                    }) {},
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 16.dp)
                                    .padding(bottom = if (systemUiMode == SystemUiMode.DEFAULT || (systemUiMode == SystemUiMode.SYNC && showStandardBars)) with(density) { navBarHeight.toDp() } else 0.dp),
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
                                                    val targetPage = newValue.roundToInt()

                                                    if (targetPage != sliderStartPage) {
                                                        recordJumpHistory(sliderStartPage, targetPage)
                                                    }
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

                val isPdfTtsPlayingOrLoading = ttsState.isPlaying || ttsState.isLoading
                val showPdfThemePanel = { showThemePanel = true }
                val showPdfDictionarySettings = { showDictionarySettingsSheet = true }
                val togglePdfScrollLock = {
                    isScrollLocked = !isScrollLocked
                    savePdfScrollLocked(context, bookId, isScrollLocked)
                    if (isScrollLocked) {
                        savePdfLockedState(context, bookId, currentActiveScale, currentActiveOffset.x, currentActiveOffset.y)
                        lockedState = Triple(currentActiveScale, currentActiveOffset.x, currentActiveOffset.y)
                    }
                }
                val showPdfSlider = {
                    val currentPageForSlider = if (displayMode == DisplayMode.PAGINATION) pagerState.currentPage else verticalReaderState.currentPage
                    sliderStartPage = currentPageForSlider
                    sliderCurrentPage = currentPageForSlider.toFloat()
                    isPageSliderVisible = true
                    showBars = false
                }
                val showPdfToc = {
                    coroutineScope.launch { drawerState.open() }
                    Unit
                }
                val showPdfSearch = {
                    executeWithOcrCheck {
                        searchState.isSearchActive = true
                        showBars = true
                    }
                }
                val togglePdfHighlights = {
                    if (!showAllTextHighlights && !isHighlightingLoading) {
                        showAllTextHighlights = true
                        isHighlightingLoading = true
                    } else if (showAllTextHighlights) {
                        showAllTextHighlights = false
                        isHighlightingLoading = false
                    }
                }
                val showPdfAiHub = { showAiHubSheet = true }
                val togglePdfEditMode = {
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
                val togglePdfTts = {
                    if (isTtsSessionActive) {
                        Timber.d("TTS button clicked: Stopping TTS")
                        ttsController.stop()
                        isAutoPagingForTts = false
                    } else {
                        startTtsWithPermissionCheck(null, null)
                    }
                }

                // Custom Top Bar
                PdfTopBar(
                    modifier = Modifier.align(Alignment.TopCenter),
                    showStandardBars = showStandardBars,
                    systemUiMode = systemUiMode,
                    statusBarHeightDp = statusBarHeightDp,
                    searchState = searchState,
                    focusRequester = focusRequester,
                    onCloseSearch = {
                        searchState.isSearchActive = false
                        searchState.onQueryChange("")
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    },
                    isLoadingDocument = isLoadingDocument,
                    errorMessage = errorMessage,
                    currentPageForDisplay = if (displayMode == DisplayMode.PAGINATION) {
                        pagerState.currentPage
                    } else {
                        verticalReaderState.currentPage
                    },
                    totalPages = totalPages,
                    pagerStatePageCount = pagerState.pageCount,
                    hiddenTools = hiddenTools,
                    toolOrder = toolOrder,
                    bottomTools = bottomTools,
                    isScrollLocked = isScrollLocked,
                    isEditMode = isEditMode,
                    displayMode = displayMode,
                    isKeepScreenOn = isKeepScreenOn,
                    isTtsSessionActive = isTtsSessionActive,
                    isBookmarked = isBookmarked,
                    canDeletePage = virtualPages.getOrNull(currentPage) is VirtualPage.BlankPage,
                    isReflowingThisBook = isReflowingThisBook,
                    hasReflowFile = hasReflowFile,
                    isPdfDocumentLoaded = pdfDocument != null,
                    isTabsEnabled = isTabsEnabled,
                    openTabs = openTabs,
                    activeTabBookId = activeTabBookId,
                    effectiveFileType = effectiveFileType,
                    onNavigateBack = { saveStateAndExit() },
                    onShowThemePanel = showPdfThemePanel,
                    onToggleScrollLock = togglePdfScrollLock,
                    onShowDictionarySettings = showPdfDictionarySettings,
                    onShowPenPlayground = { showPenPlayground = true },
                    onImportSvg = {
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

                                    showBanner(context.getString(R.string.msg_imported_svg_strokes))
                                } else {
                                    showBanner(
                                        context.getString(R.string.error_import_svg_failed),
                                        isError = true
                                    )
                                }
                            }
                        }
                    },
                    onShowCustomizeTools = { showCustomizeToolsSheet = true },
                    onShowOcrLanguage = {
                        if (!isOss) {
                            hasSelectedOcrLanguage = true
                            showOcrLanguageDialog = true
                        }
                    },
                    onShowVisualOptions = { showVisualOptionsSheet = true },
                    isTtsPlayingOrLoading = isPdfTtsPlayingOrLoading,
                    showAllTextHighlights = showAllTextHighlights,
                    isHighlightingLoading = isHighlightingLoading,
                    onShowSlider = showPdfSlider,
                    onShowToc = showPdfToc,
                    onSearchClick = showPdfSearch,
                    onToggleHighlights = togglePdfHighlights,
                    onShowAiHub = showPdfAiHub,
                    onToggleEditMode = togglePdfEditMode,
                    onToggleTts = togglePdfTts,
                    tapToNavigateEnabled = tapToNavigateEnabled,
                    onToggleTapToNavigate = {
                        tapToNavigateEnabled = !tapToNavigateEnabled
                        saveTapToNavigateSetting(context, tapToNavigateEnabled)
                    },
                    onChangeDisplayMode = { displayMode = it },
                    onToggleKeepScreenOn = {
                        isKeepScreenOn = !isKeepScreenOn
                        saveKeepScreenOn(context, isKeepScreenOn)
                    },
                    onStartAutoScroll = {
                        isAutoScrollModeActive = true
                        isAutoScrollPlaying = true
                        showBars = !isMusicianMode
                    },
                    onShowTtsSettings = { showTtsSettingsSheet = true },
                    onToggleBookmark = onBookmarkClick,
                    onInsertPage = onInsertPage,
                    onDeletePage = onDeletePage,
                    onReflowAction = {
                        coroutineScope.launch {
                            if (richTextController != null) {
                                withContext(NonCancellable) { richTextController.saveImmediate() }
                            }
                            saveAllData(true).join()

                            val resolvedPage = if (!initialScrollDone && currentPage == 0) {
                                pendingRestorePage ?: 0
                            } else {
                                currentPage
                            }

                            if (hasReflowFile) {
                                val item = uiState.allRecentFiles.find { it.bookId == reflowBookId }
                                if (item != null) {
                                    viewModel.switchToFileSeamlessly(item, resolvedPage)
                                } else {
                                    viewModel.generateAndImportReflowFile(bookId, effectivePdfUri, originalFileName, resolvedPage)
                                }
                            } else {
                                viewModel.generateAndImportReflowFile(bookId, effectivePdfUri, originalFileName, resolvedPage)
                            }
                        }
                    },
                    onShare = { showShareDialog = true },
                    onSaveCopy = { showSaveDialog = true },
                    onPrint = onPrintDocument,
                    onTabClick = { tabBookId ->
                        coroutineScope.launch {
                            currentBookId?.let { tabStateMap[it] = currentPage }
                            saveAllData(true).join()
                            viewModel.switchTab(tabBookId)
                        }
                    },
                    onTabClose = { tabBookId ->
                        coroutineScope.launch {
                            val isSelected = tabBookId == activeTabBookId
                            if (isSelected) saveAllData(true).join()
                            viewModel.closeTab(tabBookId)
                            if (isSelected && openTabs.size == 1) {
                                onNavigateBack()
                            }
                        }
                    },
                    onNewTabClick = { showNewTabSheet = true },
                    onGenerateDemoAnnotations = {
                        val page = if (displayMode == DisplayMode.PAGINATION) pagerState.currentPage else verticalReaderState.currentPage
                        val demoAnnots = DemoAnnotationGenerator.generateDemoAnnotations(page)

                        if (demoAnnots.isNotEmpty()) {
                            Timber.d("Debug: Generating ${demoAnnots.size} demo annotations for page $page")
                            val existing = allAnnotations[page] ?: emptyList()
                            allAnnotations = allAnnotations + (page to (existing + demoAnnots))

                            demoAnnots.forEach { annot ->
                                undoStack.add(HistoryAction.Add(page, annot))
                            }
                            redoStack.clear()
                        }
                    }
                )

                ReflowProgressOverlay(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topOverlayInset)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    showStandardBars = showStandardBars,
                    isReflowingThisBook = isReflowingThisBook,
                    reflowProgressValue = reflowProgressValue
                )

                // Search Results Panel
                AnimatedVisibility(
                    visible = searchState.isSearchActive && searchState.showSearchResultsPanel,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topOverlayInset)
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
                                        text = stringResource(R.string.msg_indexing_pages_progress),
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
                        .padding(bottom = 24.dp + if (systemUiMode == SystemUiMode.DEFAULT || (systemUiMode == SystemUiMode.SYNC && showStandardBars)) with(density) { navBarHeight.toDp() } else 0.dp)
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
                                    if (index >= 0) context.getString(
                                        R.string.pdf_search_result_position,
                                        index + 1,
                                        searchData.matches.size
                                    )
                                    else context.resources.getQuantityString(
                                        R.plurals.search_results_count,
                                        searchData.matches.size,
                                        searchData.matches.size
                                    )
                                Triple(text, index > 0, index < searchData.matches.size - 1)
                            }

                            is SmartSearchResult.Paged -> {
                                val page = currentResult?.locationInSource
                                val text = if (page != null) context.getString(R.string.pdf_page_short, page + 1)
                                else context.getString(R.string.msg_search_pages_count, searchData.totalPageCount)
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

                val jumpBackPage = jumpHistory.getOrNull(jumpHistoryCursor - 1)
                val jumpForwardPage = jumpHistory.getOrNull(jumpHistoryCursor + 1)
                val effectiveNavBarForJumpBar = if (systemUiMode == SystemUiMode.DEFAULT || (systemUiMode == SystemUiMode.SYNC && showStandardBars)) with(density) { navBarHeight.toDp() } else 0.dp

                PdfJumpHistoryBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 56.dp + effectiveNavBarForJumpBar),
                    showStandardBars = showStandardBars,
                    searchStateActive = searchState.isSearchActive,
                    backPage = jumpBackPage,
                    forwardPage = jumpForwardPage,
                    onBack = {
                        jumpBackPage?.let { target ->
                            jumpHistoryCursor = (jumpHistoryCursor - 1).coerceAtLeast(0)
                            navigateToJumpHistoryPage(target)
                        }
                    },
                    onForward = {
                        jumpForwardPage?.let { target ->
                            jumpHistoryCursor = (jumpHistoryCursor + 1).coerceAtMost(jumpHistory.lastIndex)
                            navigateToJumpHistoryPage(target)
                        }
                    },
                    onClear = { clearJumpHistory() }
                )

                // Bottom Bar
                PdfBottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    showStandardBars = showStandardBars,
                    searchStateActive = searchState.isSearchActive,
                    systemUiMode = systemUiMode,
                    navBarHeightDp = with(density) { navBarHeight.toDp() },
                    hiddenTools = hiddenTools,
                    toolOrder = toolOrder,
                    bottomTools = bottomTools,
                    isTtsPlayingOrLoading = isPdfTtsPlayingOrLoading,
                    showAllTextHighlights = showAllTextHighlights,
                    isHighlightingLoading = isHighlightingLoading,
                    isEditMode = isEditMode,
                    isTtsSessionActive = isTtsSessionActive,
                    ttsErrorMessage = null,
                    onShowThemePanel = showPdfThemePanel,
                    onToggleScrollLock = togglePdfScrollLock,
                    onShowDictionarySettings = showPdfDictionarySettings,
                    onShowSlider = showPdfSlider,
                    onShowToc = showPdfToc,
                    onSearchClick = showPdfSearch,
                    onToggleHighlights = togglePdfHighlights,
                    onShowAiHub = showPdfAiHub,
                    onToggleEditMode = togglePdfEditMode,
                    onToggleTts = togglePdfTts,
                    isBubbleZoomModeActive = isBubbleZoomModeActive,
                    onToggleBubbleZoom = {
                        if (isOss) {
                            showBanner(
                                "Bubble Zoom is only available in Playstore version of Episteme",
                                isError = true
                            )
                        } else if (!isBubbleZoomModeActive && !viewModel.isSpeechBubbleModelAvailable(context)) {
                            showBubbleZoomDownloadDialog = true
                        } else {
                            isBubbleZoomModeActive = !isBubbleZoomModeActive
                        }
                    }
                )

                if (isEditMode) {
                    val density = LocalDensity.current

                    val popupPlacementConfig =
                        remember(dockLocation, dockOffset, boxMaxHeightFloat, dockHeightPx) {
                            val margin = 16.dp
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
                                Triple(Alignment.BottomCenter, 0.dp, paddingBottom.coerceAtLeast(0.dp))
                            } else {
                                val paddingTop = with(density) { dockBottomY.toDp() } + margin
                                Triple(Alignment.TopCenter, paddingTop.coerceAtLeast(0.dp), 0.dp)
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
                                        isDockDragging -> Modifier
                                        dockLocation == DockLocation.TOP -> Modifier
                                        dockLocation == DockLocation.BOTTOM -> Modifier
                                        else -> Modifier
                                    }
                                )
                        ) {
                            val dragModifier =
                                if (isDockDragging || dockLocation == DockLocation.FLOATING) {
                                    Modifier.offset {
                                        IntOffset(
                                            dockOffset.x.roundToInt(), dockOffset.y.roundToInt()
                                        )
                                    }
                                } else {
                                    Modifier
                                }

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
                                    )
                                }

                            val effectiveNavBarForDock = if (systemUiMode == SystemUiMode.DEFAULT) with(density) { navBarHeight.toDp() } else 0.dp
                            val paddingModifier =
                                if ((dockLocation == DockLocation.TOP || dockLocation == DockLocation.BOTTOM) && !isDockDragging) {
                                    Modifier.padding(
                                        bottom = if (dockLocation == DockLocation.BOTTOM) effectiveNavBarForDock else 0.dp,
                                        top = if (dockLocation == DockLocation.TOP && systemUiMode == SystemUiMode.DEFAULT) statusBarHeightDp else 0.dp
                                    )
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
                                    lastHighlighterTool = lastHighlighterTool,
                                    isStylusOnlyMode = isStylusOnlyMode,
                                    onToggleStylusOnlyMode = {
                                        isStylusOnlyMode = !isStylusOnlyMode
                                        saveStylusOnlyMode(context, isStylusOnlyMode)
                                    },
                                    onToolClick = { clickedTool ->
                                        if (clickedTool == InkType.TEXT) {
                                            annotationSettingsRepo.updateSelectedTool(
                                                clickedTool
                                            )
                                            showToolSettings = false
                                        } else if (selectedTool == clickedTool) {
                                            if (clickedTool == InkType.PEN || clickedTool == InkType.FOUNTAIN_PEN || clickedTool == InkType.PENCIL || clickedTool == InkType.HIGHLIGHTER || clickedTool == InkType.HIGHLIGHTER_ROUND || clickedTool == InkType.ERASER) {
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

                val ttsReadingPage = ttsDisplayPageIndex

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
                            contentDescription = stringResource(R.string.content_desc_scroll_to_reading_page)
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
                    ZoomPercentageIndicator(
                        percentage = percentage,
                        onResetZoomClick = {
                            resetZoomTrigger = System.currentTimeMillis()
                        }
                    )
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
                    val isImeVisible = WindowInsets.ime.getBottom(currentDensity) > 0

                    val extraPadding = if (isImeVisible) 0.dp else bottomPadding

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
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                        .padding(bottom = extraPadding)
                    ) {
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
                            bottomDockPadding = 0.dp,
                            customFonts = customFonts,
                            onImportFont = viewModel::importFont,
                            onFontSelected = { name, path ->
                                Timber.tag("PdfFontDebug").i("UI Action: Font Selected -> Name: $name, Path: $path")
                                val currentConfig = toolSettings.textStyle
                                val newConfig = currentConfig.copy(fontPath = path, fontName = name)
                                annotationSettingsRepo.updateTextStyle(newConfig)

                                if (selectedTextBoxId != null) {
                                    val idx = textBoxes.indexOfFirst { it.id == selectedTextBoxId }
                                    if (idx != -1) {
                                        val oldBox = textBoxes[idx]
                                        textBoxes[idx] = oldBox.copy(fontPath = path, fontName = name)
                                    }
                                } else {
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

                val effectiveNavBarPaddingForOverlays = if (systemUiMode == SystemUiMode.DEFAULT || (systemUiMode == SystemUiMode.SYNC && showStandardBars)) with(density) { navBarHeight.toDp() } else 0.dp
                val autoScrollPadding by animateDpAsState(
                    targetValue = if (showStandardBars) (56.dp + 16.dp + effectiveNavBarPaddingForOverlays) else (16.dp + effectiveNavBarPaddingForOverlays),
                    label = "AutoScrollPadding"
                )

                val ttsOverlayPadding by animateDpAsState(
                    targetValue = if (showStandardBars) (56.dp + 16.dp + effectiveNavBarPaddingForOverlays) else (16.dp + effectiveNavBarPaddingForOverlays),
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
                            ttsDisplayPageIndex?.let { targetPage ->
                                coroutineScope.launch {
                                    if (displayMode == DisplayMode.PAGINATION) {
                                        pagerState.scrollToPage(targetPage)
                                    } else {
                                        verticalReaderState.scrollToPage(targetPage)
                                    }
                                }
                            }
                        },
                        onOpenTtsSettings = { showTtsSettingsSheet = true },
                        onClose = {
                            ttsController.stop()
                            isAutoPagingForTts = false
                        },
                        credits = uiState.credits
                    )
                }

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
                        onLocalModeToggle = onToggleAutoScrollMode,
                        onScrollToTop = {
                            if (isAutoScrollPlaying) {
                                triggerAutoScrollTempPause(1000L)
                            }
                            coroutineScope.launch {
                                verticalReaderState.scrollToTop()
                            }
                        }
                    )
                }

                CustomTopBanner(bannerMessage = bannerMessage)
            }
        }
    }

    if (showAiHubSheet) {
        val currentPageForDisplay = if (displayMode == DisplayMode.PAGINATION) {
            pagerState.currentPage
        } else {
            verticalReaderState.currentPage
        }
        val bookTitle = documentMetadataTitle ?: originalFileName

        AiHubBottomSheet(
            bookTitle = bookTitle,
            currentChapterIndex = currentPageForDisplay,
            chapterTitle = "Page ${currentPageForDisplay + 1}",
            summaryCacheManager = summaryCacheManager,
            summarizationResult = summarizationResult,
            isSummarizationLoading = isSummarizationLoading,
            onClearSummary = { summarizationResult = null },
            onGenerateSummary = { force ->
                if (BuildConfig.FLAVOR != "oss" && !isProUser && uiState.credits <= 0) {
                    showInsufficientCreditsDialog = true
                    showAiHubSheet = false
                } else {
                    coroutineScope.launch {
                        isSummarizationLoading = true
                        summarizationResult = null

                        val cached = if (!force) summaryCacheManager.getSummary(bookTitle, currentPageForDisplay) else null
                        if (cached != null) {
                            summarizationResult = SummarizationResult(summary = cached, isCacheHit = true)
                            isSummarizationLoading = false
                            return@launch
                        }

                        val token = viewModel.getAuthToken()
                        summarizeCurrentPage(
                            authToken = token,
                            onUpdate = { result ->
                                if (result.error == "INSUFFICIENT_CREDITS") {
                                    showInsufficientCreditsDialog = true
                                    showAiHubSheet = false
                                    isSummarizationLoading = false
                                } else {
                                    summarizationResult = result
                                }
                            }, onFinish = {
                                isSummarizationLoading = false
                                val finalSummary = summarizationResult?.summary
                                if (!finalSummary.isNullOrBlank() && summarizationResult?.error == null) {
                                    summaryCacheManager.saveSummary(bookTitle, currentPageForDisplay, "Page ${currentPageForDisplay + 1}", finalSummary)
                                }
                            }
                        )
                    }
                }
            },
            recapResult = null,
            isRecapLoading = false,
            onGenerateRecap = null,
            onDismiss = { showAiHubSheet = false },
            isMainTtsActive = isTtsSessionActive,
            getAuthToken = { viewModel.getAuthToken() },
            credits = uiState.credits,
            isProUser = isProUser
        )
    }

    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = { Text(stringResource(R.string.dialog_permission_required)) },
            text = {
                Text(
                    stringResource(R.string.dialog_permission_notification_desc)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionRationaleDialog = false
                        permissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionRationaleDialog = false
                        startTts()
                    }) { Text(stringResource(R.string.action_not_now)) }
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
            title = { Text(stringResource(R.string.dialog_unlock_page_summarization)) },
            text = {
                Text(
                    stringResource(R.string.dialog_unlock_page_summarization_desc)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSummarizationUpsellDialog = false
                        onNavigateToPro()
                    }) { Text(stringResource(R.string.action_learn_more)) }
            },
            dismissButton = {
                TextButton(onClick = { showSummarizationUpsellDialog = false }) {
                    Text(stringResource(R.string.action_not_now))
                }
            })
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

    // --- PANEL POPUP ---
    if (poppedUpPanelBitmap != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    poppedUpPanelBitmap?.recycle()
                    poppedUpPanelBitmap = null
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = poppedUpPanelBitmap!!.asImageBitmap(),
                contentDescription = stringResource(R.string.content_desc_annotated_page),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )

            IconButton(
                onClick = {
                    poppedUpPanelBitmap?.recycle()
                    poppedUpPanelBitmap = null
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.content_desc_close_image),
                    tint = Color.White
                )
            }
        }
    }
    // --- END PANEL POPUP ---

    if (showPasswordDialog) {
        PasswordDialog(
            isError = isPasswordError,
            onDismiss = { onNavigateBack() },
            onConfirm = { password -> documentPassword = password })
    }

    if (showBubbleZoomDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showBubbleZoomDownloadDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.dialog_download_bubble_zoom_model)) },
            text = {
                Text(stringResource(R.string.dialog_download_bubble_zoom_model_desc))
            },
            confirmButton = {
                TextButton(onClick = {
                    showBubbleZoomDownloadDialog = false
                    viewModel.downloadSpeechBubbleModel(context)
                }) {
                    Text(stringResource(R.string.action_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBubbleZoomDownloadDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showNewTabSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNewTabSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            val pdfFiles = remember(uiState.rawLibraryFiles, openTabs) {
                val openIds = openTabs.map { it.bookId }
                uiState.rawLibraryFiles
                    .filter { it.type == FileType.PDF && it.bookId !in openIds }
                    .sortedByDescending { it.timestamp }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.title_add_pdf_to_tab),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                if (pdfFiles.isEmpty()) {
                    Text(
                        stringResource(R.string.msg_no_other_pdfs_found),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(pdfFiles, key = { it.bookId }) { file ->
                            ListItem(
                                headlineContent = { Text(file.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { file.author?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        sheetState.hide()
                                        showNewTabSheet = false
                                        viewModel.switchTab(file.bookId)
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
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
                    if (!selectedDictPackage.isNullOrEmpty()) {
                        ExternalDictionaryHelper.launchDictionary(context, selectedDictPackage!!, text)
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_select_offline_dict_first), Toast.LENGTH_SHORT).show()
                        showDictionarySettingsSheet = true
                    }
                }
            },
            getAuthToken = { viewModel.getAuthToken() }
        )
    }
    if (showDictionaryUpsellDialog) {
        AlertDialog(onDismissRequest = { showDictionaryUpsellDialog = false }, icon = {
            Icon(
                painter = painterResource(id = R.drawable.ai),
                contentDescription = null
            )
        }, title = { Text(stringResource(R.string.ai_unlock_smart_dict)) }, text = {
            Text(
                stringResource(R.string.ai_unlock_smart_dict_desc)
            )
        }, confirmButton = {
            TextButton(
                onClick = {
                    showDictionaryUpsellDialog = false
                    onNavigateToPro()
                }) { Text(stringResource(R.string.action_learn_more)) }
        }, dismissButton = {
            TextButton(onClick = { showDictionaryUpsellDialog = false }) {
                Text(stringResource(R.string.action_not_now))
            }
        })
    }

    showReindexDialog?.let { newLanguage ->
        if (BuildConfig.IS_PRO) {
            AlertDialog(
                onDismissRequest = { showReindexDialog = null },
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                title = { Text(stringResource(R.string.title_reindex_document)) },
                text = {
                    Text(
                        stringResource(R.string.desc_reindex_document_warning)
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
                    ) { Text(stringResource(R.string.action_reindex)) }
                },
                dismissButton = {
                    TextButton(onClick = { showReindexDialog = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        } else {
            showReindexDialog = null
        }
    }

    if (showOcrLanguageDialog && !isOss) {
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
        val bookTitle = documentMetadataTitle ?: originalFileName
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
            isTtsActive = isTtsSessionActive,
            getAuthToken = { viewModel.getAuthToken() },
            bookTitle = bookTitle
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

    if (highlightToNoteId != null) {
        val targetHighlight = userHighlights.find { it.id == highlightToNoteId }
        if (targetHighlight != null) {
            val effectiveBg = if (activeTheme.backgroundColor == Color.Unspecified) MaterialTheme.colorScheme.surface else activeTheme.backgroundColor
            val effectiveText = if (activeTheme.textColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else activeTheme.textColor

            PdfAnnotationBottomSheet(
                highlight = targetHighlight,
                effectiveBg = effectiveBg,
                effectiveText = effectiveText,
                customHighlightColors = customHighlightColors,
                onPaletteClick = {
                    highlightColorPickerInitialSlot = targetHighlight.color
                    showHighlightColorPicker = true
                },
                onColorChange = { newColor ->
                    onHighlightUpdate(
                        targetHighlight.id,
                        newColor
                    )
                },
                onDismiss = { highlightToNoteId = null },
                onSave = { noteText ->
                    val index =
                        userHighlights.indexOfFirst { it.id == targetHighlight.id }
                    if (index != -1) {
                        userHighlights[index] =
                            targetHighlight.copy(note = noteText.takeIf { it.isNotBlank() })
                    }
                    highlightToNoteId = null
                },
                onDelete = {
                    onHighlightDelete(targetHighlight.id)
                    highlightToNoteId = null
                },
                onCopy = {
                    val clip = ClipData.newPlainText(
                        "Copied Text",
                        targetHighlight.text
                    )
                    clipboardManager.setText(
                        androidx.compose.ui.text.AnnotatedString(
                            targetHighlight.text
                        )
                    )
                    highlightToNoteId = null
                },
                onDictionary = {
                    onDictionaryLookupStable(targetHighlight.text)
                    highlightToNoteId = null
                },
                onTranslate = {
                    onTranslateTextStable(targetHighlight.text)
                    highlightToNoteId = null
                },
                onSearch = {
                    onSearchTextStable(targetHighlight.text)
                    highlightToNoteId = null
                }
            )
        } else {
            highlightToNoteId = null
        }
    }

    if (showHighlightColorPicker) {
        HighlightColorPickerDialog(
            initialColors = customHighlightColors,
            initialSelection = highlightColorPickerInitialSlot,
            onDismiss = { showHighlightColorPicker = false },
            onSave = { newColors ->
                customHighlightColors = newColors
                saveCustomHighlightColors(context, newColors)
                showHighlightColorPicker = false
            }
        )
    }

    if (showThemePanel) {
        ReaderThemePanel(
            isVisible = true,
            currentThemeId = currentThemeId,
            excludeImages = excludeImages,
            onExcludeImagesChange = {
                excludeImages = it
                com.aryan.reader.saveExcludeImages(context, it)
            },
            showExcludeImagesOption = true,
            builtInThemes = PdfBuiltInThemes,
            globalTextureTransparency = globalTextureTransparency,
            onGlobalTextureTransparencyChange = {
                globalTextureTransparency = it
                saveGlobalTextureTransparency(context, it)
            },
            onThemeSelected = {
                currentThemeId = it
                savePdfThemeId(context, it)
                showThemePanel = false
            },
            onDismiss = { showThemePanel = false },
            customThemes = customThemes,
            onCustomThemesUpdated = {
                customThemes = it
                saveCustomThemes(context, it)
            }
        )
    }

    if (clickedLinkUrl != null) {
        val url = clickedLinkUrl!!
        AlertDialog(
            onDismissRequest = { clickedLinkUrl = null },
            title = { Text(stringResource(R.string.dialog_external_link_title)) },
            text = { Text(stringResource(R.string.desc_external_link_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            uriHandler.openUri(url)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to open URI")
                        }
                        clickedLinkUrl = null
                    }) { Text(stringResource(R.string.action_visit)) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(url))
                            clickedLinkUrl = null
                        }) { Text(stringResource(R.string.action_copy)) }
                    TextButton(onClick = { clickedLinkUrl = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            })
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.title_save_to_device)) },
            text = { Text(stringResource(R.string.desc_choose_format_save)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        pendingSaveMode = SaveMode.ANNOTATED
                        val suggestedName = getSuggestedFilename(
                            originalFileName, isAnnotated = true
                        )
                        saveLauncher.launch(suggestedName)
                    }) { Text(stringResource(R.string.action_with_annotations)) }
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
                        }) { Text(stringResource(R.string.action_original)) }

                    Spacer(Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            showSaveDialog = false
                            pendingSaveMode = null
                        }) { Text(stringResource(R.string.action_cancel)) }
                }
            })
    }

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text(stringResource(R.string.share_chooser_title)) },
            text = { Text(stringResource(R.string.desc_choose_format_share)) },
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
                                sourceUri = effectivePdfUri,
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
                    }) { Text(stringResource(R.string.action_with_annotations)) }
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
                        }) { Text(stringResource(R.string.action_original)) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showShareDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
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
                        text = stringResource(R.string.msg_preparing_pdf),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    if (showVisualOptionsSheet) {
        PdfVisualOptionsSheet(
            systemUiMode = systemUiMode,
            onSystemUiModeChange = { mode ->
                systemUiMode = mode
                savePdfSystemUiMode(context, mode)
            },
            onDismiss = { showVisualOptionsSheet = false }
        )
    }
    if (showCustomizeToolsSheet) {
        PdfCustomizeToolsSheet(
            hiddenTools = hiddenTools,
            toolOrder = toolOrder,
            bottomTools = bottomTools,
            onUpdate = onUpdateHiddenTools,
            onOrderUpdate = onUpdateToolOrder,
            onPlacementUpdate = onUpdateBottomTools,
            onDismiss = { showCustomizeToolsSheet = false }
        )
    }
}
