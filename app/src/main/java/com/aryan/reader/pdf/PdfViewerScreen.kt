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
import android.content.ActivityNotFoundException
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
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
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
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.key
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
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
import com.aryan.reader.shared.AiDefinitionResult
import com.aryan.reader.shared.ReaderAiFeature as AiFeature
import com.aryan.reader.AiHubBottomSheet
import com.aryan.reader.BuildConfig
import com.aryan.reader.COMIC_ARCHIVE_FILE_TYPES
import com.aryan.reader.FileType
import com.aryan.reader.HighlightColorPickerDialog
import com.aryan.reader.MainViewModel
import com.aryan.reader.ReaderScreenState
import com.aryan.reader.PDF_RENAME_TRACE_TAG
import com.aryan.reader.R
import com.aryan.reader.ReaderBrightnessEffect
import com.aryan.reader.ReaderBrightnessSheet
import com.aryan.reader.ReaderFileInfoDialogs
import com.aryan.reader.ReaderScreenOrientationEffect
import com.aryan.reader.ReaderScreenOrientationSheet
import com.aryan.reader.ReaderThemePanel
import com.aryan.reader.shared.SearchResult
import com.aryan.reader.shared.ReaderSearchState
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.SummarizationResult
import com.aryan.reader.SummaryCacheManager
import com.aryan.reader.TtsSettingsSheet
import com.aryan.reader.TtsWordReplacementsSheet
import com.aryan.reader.data.CustomFontEntity
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.areReaderAiFeaturesEnabled
import com.aryan.reader.callByokGeminiInlineAi
import com.aryan.reader.cardTitle
import com.aryan.reader.epubreader.AutoScrollControls
import com.aryan.reader.epubreader.DictionarySettingsDialog
import com.aryan.reader.epubreader.ExternalDictionaryHelper
import com.aryan.reader.epubreader.SystemUiMode
import com.aryan.reader.epubreader.TtsOverlayControls
import com.aryan.reader.epubreader.loadPageTurnAnimationSetting
import com.aryan.reader.epubreader.loadTapToNavigateSetting
import com.aryan.reader.epubreader.savePageTurnAnimationSetting
import com.aryan.reader.epubreader.saveTapToNavigateSetting
import com.aryan.reader.fetchAiDefinition
import com.aryan.reader.isByokCloudTtsAvailable
import com.aryan.reader.loadCustomThemes
import com.aryan.reader.loadGlobalTextureTransparency
import com.aryan.reader.loadPdfRightToLeftPagination
import com.aryan.reader.loadReaderBrightnessSettings
import com.aryan.reader.loadReaderScreenOrientationMode
import com.aryan.reader.loadReaderSliderToggled
import com.aryan.reader.loadTtsReplacementPreferences
import com.aryan.reader.logCloudAnnotationSyncTrace
import com.aryan.reader.ml.SpeechBubble
import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.pdf.data.AnnotationSettingsRepository
import com.aryan.reader.pdf.data.AnnotationToolSettings
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfAnnotationRepository
import com.aryan.reader.pdf.data.PdfHighlightRepository
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.PdfTextBoxRepository
import com.aryan.reader.pdf.data.PdfTextRepository
import com.aryan.reader.pdf.data.SmartSearchResult
import com.aryan.reader.pdf.data.TextStyleConfig
import com.aryan.reader.pdf.data.VirtualPage
import com.aryan.reader.readerSliderBookmarkPosition
import com.aryan.reader.readerSliderChromeColors
import com.aryan.reader.readerSliderStepPage
import com.aryan.reader.readerSliderToggleState
import com.aryan.reader.rememberSearchState
import com.aryan.reader.saveCustomThemes
import com.aryan.reader.saveGlobalTextureTransparency
import com.aryan.reader.savePdfRightToLeftPagination
import com.aryan.reader.saveReaderBrightnessSettings
import com.aryan.reader.saveReaderScreenOrientationMode
import com.aryan.reader.saveReaderSliderToggled
import com.aryan.reader.saveTtsReplacementPreferences
import com.aryan.reader.scaledToCanvasLimit
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.pdf.PdfSpreadLayout
import com.aryan.reader.shared.pdf.PdfReverseColorMode
import com.aryan.reader.shared.pdf.PdfNavigationReason
import com.aryan.reader.shared.pdf.RealisticPdfPageTurnAnimationSpec
import com.aryan.reader.shared.pdf.pdfPaginatedPagePaperColor
import com.aryan.reader.shared.pdf.shouldPlayRealisticPdfPageTurn
import com.aryan.reader.shared.pdf.PDF_MAX_ZOOM_SCALE
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSessionAction
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSessionState
import com.aryan.reader.shared.pdf.SharedPdfJumpHistory
import com.aryan.reader.shared.pdf.reduce
import com.aryan.reader.shared.pdf.animatesPagination
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.ui.ReaderMinimalSlider
import com.aryan.reader.shared.ui.realisticPageCurl
import com.aryan.reader.shared.ui.SharedPdfRichTextHiddenInput
import com.aryan.reader.shared.ui.SharedMobileReaderDrawer
import com.aryan.reader.shared.ui.SharedMobileReaderScaffold
import com.aryan.reader.shared.reader.MobilePdfReaderBackAction
import com.aryan.reader.shared.reader.MobilePdfReaderBackState
import com.aryan.reader.shared.reader.selectMobilePdfReaderBackAction
import com.aryan.reader.shared.reader.mobilePdfSystemBarsVisibility
import com.aryan.reader.shared.reader.MobilePdfDocumentPresentation
import com.aryan.reader.shared.reader.selectMobilePdfDocumentPresentation
import com.aryan.reader.shared.ui.SharedMobileReaderLoadingIndicator
import com.aryan.reader.shared.ui.SharedMobileReaderCenteredError
import com.aryan.reader.shared.ui.SharedMobilePdfPasswordDialog
import com.aryan.reader.shared.ui.SharedMobilePdfPasswordLabels
import com.aryan.reader.shared.ui.SharedMobileSingleChoiceDialog
import com.aryan.reader.shared.ui.SharedMobileSingleChoiceOption
import com.aryan.reader.shared.ui.SharedMobileInfoConfirmationDialog
import com.aryan.reader.shared.ui.SharedMobileExternalLinkDialog
import com.aryan.reader.shared.ui.SharedMobileDocumentFormatDialog
import com.aryan.reader.shouldRenderReaderSlider
import com.aryan.reader.summarizationUrl
import com.aryan.reader.tts.ReaderTtsOverlaySize
import com.aryan.reader.tts.SpeakerSamplePlayer
import com.aryan.reader.tts.TtsController
import com.aryan.reader.tts.TtsPlaybackManager
import com.aryan.reader.tts.loadReaderTtsOverlaySize
import com.aryan.reader.tts.readerTtsOverlayAlignmentBias
import com.aryan.reader.tts.rememberTtsController
import com.aryan.reader.tts.saveReaderTtsOverlaySize
import com.aryan.reader.tts.splitTextIntoChunks
import com.aryan.reader.withTtsReplacements
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PDF_SPREAD_PAN_FLING_MIN_VELOCITY = 600f
private const val PDF_SPREAD_PAN_FLING_MULTIPLIER = 0.72f

/** Temporary diagnostics for the PDF TTS start regression; remove once root-caused. */
private const val TTS_DIAG_TAG = "TTS_DIAG"

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
    onSavePosition: suspend (uri: Uri, page: Int, totalPages: Int) -> Unit,
    onBookmarksChanged: (bookmarksJson: String) -> Unit,
    onNavigateToPro: () -> Unit,
    viewModel: MainViewModel,
    pane: PdfViewerPane? = null,
    isPaneFocused: Boolean = true,
    isPaneAppActive: Boolean = true,
    onOpenSplit: (() -> Unit)? = null,
    ttsControllerOverride: TtsController? = null,
) {
    val isSplitPane = pane != null
    // A split pane may remain composed while another pane owns process-global
    // resources, or while the reader route is backgrounded. Keep the legacy
    // full-screen defaults intact, but make ownership explicit for panes.
    val ownsPaneGlobals = !isSplitPane || (isPaneFocused && isPaneAppActive)
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
    var pageTurnAnimationEnabled by remember { mutableStateOf(loadPageTurnAnimationSetting(context)) }
    var showThemePanel by remember { mutableStateOf(false) }
    var currentThemeId by remember { mutableStateOf(loadPdfThemeId(context)) }
    var excludeImages by remember { mutableStateOf(com.aryan.reader.loadExcludeImages(context)) }
    var reverseColorMode by remember { mutableStateOf(com.aryan.reader.loadPdfReverseColorMode(context)) }
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
    // Reader chrome starts hidden for every document; a reader tap reveals it.
    // This is intentionally not saveable so reopening a document uses the same
    // distraction-free default.
    var showBars by remember { mutableStateOf(false) }
    var systemUiMode by remember { mutableStateOf(loadPdfSystemUiMode(context)) }
    var showVerticalPageGap by remember { mutableStateOf(loadPdfVerticalPageGapVisible(context)) }
    var showPageNumberOverlay by remember { mutableStateOf(loadPdfPageNumberOverlayVisible(context)) }
    var showTopTabStrip by remember { mutableStateOf(loadPdfTopTabStripVisible(context)) }
    var showVisualOptionsSheet by remember { mutableStateOf(false) }
    var pdfPageSpreadMode by remember { mutableStateOf(loadPdfPageSpreadMode(context)) }
    var pdfFirstPageStandaloneInSpread by remember { mutableStateOf(loadPdfFirstPageStandaloneInSpread(context)) }
    var pendingPaginationSpreadRestorePage by remember { mutableStateOf<Int?>(null) }
    var screenOrientationMode by remember { mutableStateOf(loadReaderScreenOrientationMode(context)) }
    var rightToLeftPagination by remember { mutableStateOf(loadPdfRightToLeftPagination(context)) }
    var showScreenOrientationSheet by remember { mutableStateOf(false) }
    var pendingRestorePage by rememberSaveable { mutableStateOf(initialPage) }
    var isScrollLocked by remember { mutableStateOf(false) }
    var lockedState by remember { mutableStateOf<Triple<Float, Float, Float>?>(null) }
    var currentActiveScale by remember { mutableFloatStateOf(1f) }
    var currentActiveOffset by remember { mutableStateOf(Offset.Zero) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    LocalView.current
    if (ownsPaneGlobals) {
        ReaderScreenOrientationEffect(screenOrientationMode)
    }

    var ocrLanguage by remember { mutableStateOf(loadOcrLanguage(context)) }
    var hasSelectedOcrLanguage by remember { mutableStateOf(hasUserSelectedOcrLanguage(context)) }
    var showOcrLanguageDialog by remember { mutableStateOf(false) }
    var showReindexDialog by remember { mutableStateOf<OcrLanguage?>(null) }
    var pendingActionAfterOcrSelection by remember { mutableStateOf<(() -> Unit)?>(null) }

    var showCustomizeToolsSheet by remember { mutableStateOf(false) }
    var hiddenToolNames by rememberSaveable {
        mutableStateOf(loadPdfHiddenTools(context).toList())
    }
    var toolOrderNames by rememberSaveable {
        mutableStateOf(loadPdfToolOrder(context).map { it.name })
    }
    var bottomToolNames by rememberSaveable {
        mutableStateOf(loadPdfBottomTools(context).toList())
    }
    val hiddenTools = remember(hiddenToolNames) {
        sanitizePdfHiddenToolNames(hiddenToolNames)
    }
    val toolOrder = remember(toolOrderNames) {
        restorePdfToolOrderNames(toolOrderNames)
    }
    val bottomTools = remember(bottomToolNames) {
        sanitizePdfBottomToolNames(bottomToolNames)
    }

    val onUpdateHiddenTools = { newSet: Set<String> ->
        val sanitized = sanitizePdfHiddenToolNames(newSet)
        hiddenToolNames = sanitized.toList()
        savePdfHiddenTools(context, sanitized)
    }

    val onUpdateToolOrder = { newOrder: List<PdfReaderTool> ->
        val sanitized = restorePdfToolOrderNames(newOrder.map { it.name })
        toolOrderNames = sanitized.map { it.name }
        savePdfToolOrder(context, sanitized)
    }

    val onUpdateBottomTools = { newBottomTools: Set<String> ->
        val sanitized = sanitizePdfBottomToolNames(newBottomTools)
        bottomToolNames = sanitized.toList()
        savePdfBottomTools(context, sanitized)
    }

    val isOss = BuildConfig.FLAVOR == "oss"

    val executeWithOcrCheck = remember(hasSelectedOcrLanguage, ownsPaneGlobals) {
        { action: () -> Unit ->
            if (ownsPaneGlobals) {
                if (isOss || hasSelectedOcrLanguage) {
                    action()
                } else {
                    pendingActionAfterOcrSelection = action
                    showOcrLanguageDialog = true
                }
            }
        }
    }

    var searchHighlightMode by remember { mutableStateOf(SearchHighlightMode.ALL) }

    var isBackgroundIndexing by remember { mutableStateOf(false) }
    var backgroundIndexingProgress by remember { mutableFloatStateOf(0f) }

    val uiState by viewModel.uiState.collectAsState()
    val customFonts by viewModel.customFonts.collectAsState()
    val bubbleZoomDownloadProgress by viewModel.speechBubbleModelDownloadProgress.collectAsState()
    val annotationSettingsRepo = remember(context) { AnnotationSettingsRepository(context) }
    val toolSettings by annotationSettingsRepo.settings.collectAsState()
    val ttsController = ttsControllerOverride ?: rememberTtsController()
    val ttsState by ttsController.ttsState.collectAsState()
    val surfaceState = remember { PdfViewerSurfaceState() }
    val documentSetup = remember(
        uiState,
        pdfUri,
        initialPage,
        initialBookmarksJson,
        onBookmarksChanged,
        pane,
        viewModel,
        isPaneFocused,
        isSplitPane,
        ownsPaneGlobals,
        context,
        coroutineScope,
        ttsController,
        ttsState,
        customFonts,
        bubbleZoomDownloadProgress,
        annotationSettingsRepo,
        toolSettings,
        tabStateMap
    ) {
        PdfViewerDocumentSetupInputs(
            uiState = uiState,
            pdfUri = pdfUri,
            initialPage = initialPage,
            initialBookmarksJson = initialBookmarksJson,
            onBookmarksChanged = onBookmarksChanged,
            pane = pane,
            viewModel = viewModel,
            isPaneFocused = isPaneFocused,
            isSplitPane = isSplitPane,
            ownsPaneGlobals = ownsPaneGlobals,
            context = context,
            coroutineScope = coroutineScope,
            ttsController = ttsController,
            ttsState = ttsState,
            customFonts = customFonts,
            bubbleZoomDownloadProgress = bubbleZoomDownloadProgress,
            annotationSettingsRepo = annotationSettingsRepo,
            toolSettings = toolSettings,
            tabStateMap = tabStateMap,
            displayMode = pdfViewerMutableValue({ displayMode }, { displayMode = it }),
            currentActiveOffset = pdfViewerMutableValue({ currentActiveOffset }, { currentActiveOffset = it }),
            currentActiveScale = pdfViewerMutableValue({ currentActiveScale }, { currentActiveScale = it }),
            isScrollLocked = pdfViewerMutableValue({ isScrollLocked }, { isScrollLocked = it }),
            lockedState = pdfViewerMutableValue({ lockedState }, { lockedState = it }),
            ocrLanguage = pdfViewerMutableValue({ ocrLanguage }, { ocrLanguage = it }),
            pdfFirstPageStandaloneInSpread = pdfViewerMutableValue({ pdfFirstPageStandaloneInSpread }, { pdfFirstPageStandaloneInSpread = it }),
            pdfPageSpreadMode = pdfViewerMutableValue({ pdfPageSpreadMode }, { pdfPageSpreadMode = it }),
            pendingPaginationSpreadRestorePage = pdfViewerMutableValue({ pendingPaginationSpreadRestorePage }, { pendingPaginationSpreadRestorePage = it }),
            pendingRestorePage = pdfViewerMutableValue({ pendingRestorePage }, { pendingRestorePage = it }),
            showBars = pdfViewerMutableValue({ showBars }, { showBars = it }),
            showInsufficientCreditsDialog = pdfViewerMutableValue({ showInsufficientCreditsDialog }, { showInsufficientCreditsDialog = it }),
            showTopTabStrip = pdfViewerMutableValue({ showTopTabStrip }, { showTopTabStrip = it }),
            systemUiMode = pdfViewerMutableValue({ systemUiMode }, { systemUiMode = it }),
        )
    }
    PdfViewerDocumentSetup(surfaceState = surfaceState, setup = documentSetup)

    PdfViewerScreenContent(
        surfaceState = surfaceState,
        inputs = PdfViewerScreenContentInputs(
            activeTheme = activeTheme,
            activity = activity,
            annotationSettingsRepo = annotationSettingsRepo,
            backgroundIndexingProgress = pdfViewerMutableValue({ backgroundIndexingProgress }, { backgroundIndexingProgress = it }),
            bottomTools = bottomTools,
            bubbleZoomDownloadProgress = bubbleZoomDownloadProgress,
            context = context,
            coroutineScope = coroutineScope,
            currentActiveOffset = pdfViewerMutableValue({ currentActiveOffset }, { currentActiveOffset = it }),
            currentActiveScale = pdfViewerMutableValue({ currentActiveScale }, { currentActiveScale = it }),
            currentThemeId = pdfViewerMutableValue({ currentThemeId }, { currentThemeId = it }),
            customFonts = customFonts,
            customThemes = pdfViewerMutableValue({ customThemes }, { customThemes = it }),
            displayMode = pdfViewerMutableValue({ displayMode }, { displayMode = it }),
            documentCache = documentCache,
            drawerState = drawerState,
            excludeImages = pdfViewerMutableValue({ excludeImages }, { excludeImages = it }),
            reverseColorMode = pdfViewerMutableValue({ reverseColorMode }, { reverseColorMode = it }),
            executeWithOcrCheck = executeWithOcrCheck,
            focusManager = focusManager,
            focusRequester = focusRequester,
            globalTextureTransparency = pdfViewerMutableValue({ globalTextureTransparency }, { globalTextureTransparency = it }),
            hasSelectedOcrLanguage = pdfViewerMutableValue({ hasSelectedOcrLanguage }, { hasSelectedOcrLanguage = it }),
            hiddenTools = hiddenTools,
            initialBookmarksJson = initialBookmarksJson,
            initialPage = initialPage,
            isBackgroundIndexing = pdfViewerMutableValue({ isBackgroundIndexing }, { isBackgroundIndexing = it }),
            isOss = isOss,
            isPaneFocused = isPaneFocused,
            isPasswordError = pdfViewerMutableValue({ isPasswordError }, { isPasswordError = it }),
            isPdfDarkMode = isPdfDarkMode,
            isProUser = isProUser,
            isScrollLocked = pdfViewerMutableValue({ isScrollLocked }, { isScrollLocked = it }),
            isSplitPane = isSplitPane,
            keyboardController = keyboardController,
            lockedState = pdfViewerMutableValue({ lockedState }, { lockedState = it }),
            ocrLanguage = pdfViewerMutableValue({ ocrLanguage }, { ocrLanguage = it }),
            onBookmarksChanged = onBookmarksChanged,
            onNavigateBack = onNavigateBack,
            onNavigateToPro = onNavigateToPro,
            onOpenSplit = onOpenSplit,
            onSavePosition = onSavePosition,
            onUpdateBottomTools = onUpdateBottomTools,
            onUpdateHiddenTools = onUpdateHiddenTools,
            onUpdateToolOrder = onUpdateToolOrder,
            ownsPaneGlobals = ownsPaneGlobals,
            pageAspectRatios = pdfViewerMutableValue({ pageAspectRatios }, { pageAspectRatios = it }),
            pageTurnAnimationEnabled = pdfViewerMutableValue({ pageTurnAnimationEnabled }, { pageTurnAnimationEnabled = it }),
            pdfFirstPageStandaloneInSpread = pdfViewerMutableValue({ pdfFirstPageStandaloneInSpread }, { pdfFirstPageStandaloneInSpread = it }),
            pdfPageSpreadMode = pdfViewerMutableValue({ pdfPageSpreadMode }, { pdfPageSpreadMode = it }),
            pdfUri = pdfUri,
            pendingActionAfterOcrSelection = pdfViewerMutableValue({ pendingActionAfterOcrSelection }, { pendingActionAfterOcrSelection = it }),
            pendingPaginationSpreadRestorePage = pdfViewerMutableValue({ pendingPaginationSpreadRestorePage }, { pendingPaginationSpreadRestorePage = it }),
            pendingRestorePage = pdfViewerMutableValue({ pendingRestorePage }, { pendingRestorePage = it }),
            poppedUpPanelBitmap = pdfViewerMutableValue({ poppedUpPanelBitmap }, { poppedUpPanelBitmap = it }),
            rightToLeftPagination = pdfViewerMutableValue({ rightToLeftPagination }, { rightToLeftPagination = it }),
            screenOrientationMode = pdfViewerMutableValue({ screenOrientationMode }, { screenOrientationMode = it }),
            searchHighlightMode = pdfViewerMutableValue({ searchHighlightMode }, { searchHighlightMode = it }),
            showBars = pdfViewerMutableValue({ showBars }, { showBars = it }),
            showCustomizeToolsSheet = pdfViewerMutableValue({ showCustomizeToolsSheet }, { showCustomizeToolsSheet = it }),
            showInsufficientCreditsDialog = pdfViewerMutableValue({ showInsufficientCreditsDialog }, { showInsufficientCreditsDialog = it }),
            showOcrLanguageDialog = pdfViewerMutableValue({ showOcrLanguageDialog }, { showOcrLanguageDialog = it }),
            showPageNumberOverlay = pdfViewerMutableValue({ showPageNumberOverlay }, { showPageNumberOverlay = it }),
            showPasswordDialog = pdfViewerMutableValue({ showPasswordDialog }, { showPasswordDialog = it }),
            showReindexDialog = pdfViewerMutableValue({ showReindexDialog }, { showReindexDialog = it }),
            showScreenOrientationSheet = pdfViewerMutableValue({ showScreenOrientationSheet }, { showScreenOrientationSheet = it }),
            showThemePanel = pdfViewerMutableValue({ showThemePanel }, { showThemePanel = it }),
            showTopTabStrip = pdfViewerMutableValue({ showTopTabStrip }, { showTopTabStrip = it }),
            showVerticalPageGap = pdfViewerMutableValue({ showVerticalPageGap }, { showVerticalPageGap = it }),
            showVisualOptionsSheet = pdfViewerMutableValue({ showVisualOptionsSheet }, { showVisualOptionsSheet = it }),
            summaryCacheManager = summaryCacheManager,
            systemUiMode = pdfViewerMutableValue({ systemUiMode }, { systemUiMode = it }),
            tabStateMap = tabStateMap,
            tapToNavigateEnabled = pdfViewerMutableValue({ tapToNavigateEnabled }, { tapToNavigateEnabled = it }),
            toolOrder = toolOrder,
            toolSettings = toolSettings,
            ttsController = ttsController,
            ttsState = ttsState,
            uiState = uiState,
            viewModel = viewModel,
        )
    )

}

private class PdfViewerScreenContentInputs(
    val activeTheme: ReaderTheme,
    val activity: Activity?,
    val annotationSettingsRepo: AnnotationSettingsRepository,
    val backgroundIndexingProgress: PdfViewerMutableValue<Float>,
    val bottomTools: Set<String>,
    val bubbleZoomDownloadProgress: Float?,
    val context: Context,
    val coroutineScope: CoroutineScope,
    val currentActiveOffset: PdfViewerMutableValue<Offset>,
    val currentActiveScale: PdfViewerMutableValue<Float>,
    val currentThemeId: PdfViewerMutableValue<String>,
    val customFonts: List<CustomFontEntity>,
    val customThemes: PdfViewerMutableValue<List<ReaderTheme>>,
    val displayMode: PdfViewerMutableValue<DisplayMode>,
    val documentCache: DocumentCache,
    val drawerState: DrawerState,
    val excludeImages: PdfViewerMutableValue<Boolean>,
    val reverseColorMode: PdfViewerMutableValue<PdfReverseColorMode>,
    val executeWithOcrCheck: ((() -> Unit) -> Unit),
    val focusManager: androidx.compose.ui.focus.FocusManager,
    val focusRequester: FocusRequester,
    val globalTextureTransparency: PdfViewerMutableValue<Float>,
    val hasSelectedOcrLanguage: PdfViewerMutableValue<Boolean>,
    val hiddenTools: Set<String>,
    val initialBookmarksJson: String?,
    val initialPage: Int?,
    val isBackgroundIndexing: PdfViewerMutableValue<Boolean>,
    val isOss: Boolean,
    val isPaneFocused: Boolean,
    val isPasswordError: PdfViewerMutableValue<Boolean>,
    val isPdfDarkMode: Boolean,
    val isProUser: Boolean,
    val isScrollLocked: PdfViewerMutableValue<Boolean>,
    val isSplitPane: Boolean,
    val keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    val lockedState: PdfViewerMutableValue<Triple<Float, Float, Float>?>,
    val ocrLanguage: PdfViewerMutableValue<OcrLanguage>,
    val onBookmarksChanged: (bookmarksJson: String) -> Unit,
    val onNavigateBack: () -> Unit,
    val onNavigateToPro: () -> Unit,
    val onOpenSplit: (() -> Unit)?,
    val onSavePosition: suspend (uri: Uri, page: Int, totalPages: Int) -> Unit,
    val onUpdateBottomTools: (Set<String>) -> Unit,
    val onUpdateHiddenTools: (Set<String>) -> Unit,
    val onUpdateToolOrder: (List<PdfReaderTool>) -> Unit,
    val ownsPaneGlobals: Boolean,
    val pageAspectRatios: PdfViewerMutableValue<List<Float>>,
    val pageTurnAnimationEnabled: PdfViewerMutableValue<Boolean>,
    val pdfFirstPageStandaloneInSpread: PdfViewerMutableValue<Boolean>,
    val pdfPageSpreadMode: PdfViewerMutableValue<com.aryan.reader.shared.reader.ReaderPageSpreadMode>,
    val pdfUri: Uri,
    val pendingActionAfterOcrSelection: PdfViewerMutableValue<(() -> Unit)?>,
    val pendingPaginationSpreadRestorePage: PdfViewerMutableValue<Int?>,
    val pendingRestorePage: PdfViewerMutableValue<Int?>,
    val poppedUpPanelBitmap: PdfViewerMutableValue<Bitmap?>,
    val rightToLeftPagination: PdfViewerMutableValue<Boolean>,
    val screenOrientationMode: PdfViewerMutableValue<com.aryan.reader.shared.reader.ReaderScreenOrientationMode>,
    val searchHighlightMode: PdfViewerMutableValue<SearchHighlightMode>,
    val showBars: PdfViewerMutableValue<Boolean>,
    val showCustomizeToolsSheet: PdfViewerMutableValue<Boolean>,
    val showInsufficientCreditsDialog: PdfViewerMutableValue<Boolean>,
    val showOcrLanguageDialog: PdfViewerMutableValue<Boolean>,
    val showPageNumberOverlay: PdfViewerMutableValue<Boolean>,
    val showPasswordDialog: PdfViewerMutableValue<Boolean>,
    val showReindexDialog: PdfViewerMutableValue<OcrLanguage?>,
    val showScreenOrientationSheet: PdfViewerMutableValue<Boolean>,
    val showThemePanel: PdfViewerMutableValue<Boolean>,
    val showTopTabStrip: PdfViewerMutableValue<Boolean>,
    val showVerticalPageGap: PdfViewerMutableValue<Boolean>,
    val showVisualOptionsSheet: PdfViewerMutableValue<Boolean>,
    val summaryCacheManager: SummaryCacheManager,
    val systemUiMode: PdfViewerMutableValue<SystemUiMode>,
    val tabStateMap: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Int>,
    val tapToNavigateEnabled: PdfViewerMutableValue<Boolean>,
    val toolOrder: List<PdfReaderTool>,
    val toolSettings: AnnotationToolSettings,
    val ttsController: TtsController,
    val ttsState: TtsPlaybackManager.TtsState,
    val uiState: ReaderScreenState,
    val viewModel: MainViewModel,
)

@Composable
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
private fun PdfViewerScreenContent(
    surfaceState: PdfViewerSurfaceState,
    inputs: PdfViewerScreenContentInputs,
) {
    val activeTheme = inputs.activeTheme
    val activity = inputs.activity
    val annotationSettingsRepo = inputs.annotationSettingsRepo
    var backgroundIndexingProgress by inputs.backgroundIndexingProgress
    val bottomTools = inputs.bottomTools
    val bubbleZoomDownloadProgress = inputs.bubbleZoomDownloadProgress
    val context = inputs.context
    val coroutineScope = inputs.coroutineScope
    var currentActiveOffset by inputs.currentActiveOffset
    var currentActiveScale by inputs.currentActiveScale
    var currentThemeId by inputs.currentThemeId
    val customFonts = inputs.customFonts
    var customThemes by inputs.customThemes
    var displayMode by inputs.displayMode
    val documentCache = inputs.documentCache
    val drawerState = inputs.drawerState
    var excludeImages by inputs.excludeImages
    var reverseColorMode by inputs.reverseColorMode
    val executeWithOcrCheck = inputs.executeWithOcrCheck
    val focusManager = inputs.focusManager
    val focusRequester = inputs.focusRequester
    var globalTextureTransparency by inputs.globalTextureTransparency
    var hasSelectedOcrLanguage by inputs.hasSelectedOcrLanguage
    val hiddenTools = inputs.hiddenTools
    val initialBookmarksJson = inputs.initialBookmarksJson
    val initialPage = inputs.initialPage
    var isBackgroundIndexing by inputs.isBackgroundIndexing
    val isOss = inputs.isOss
    val isPaneFocused = inputs.isPaneFocused
    var isPasswordError by inputs.isPasswordError
    val isPdfDarkMode = inputs.isPdfDarkMode
    val isProUser = inputs.isProUser
    var isScrollLocked by inputs.isScrollLocked
    val isSplitPane = inputs.isSplitPane
    val keyboardController = inputs.keyboardController
    var lockedState by inputs.lockedState
    var ocrLanguage by inputs.ocrLanguage
    val onBookmarksChanged = inputs.onBookmarksChanged
    val onNavigateBack = inputs.onNavigateBack
    val onNavigateToPro = inputs.onNavigateToPro
    val onOpenSplit = inputs.onOpenSplit
    val onSavePosition = inputs.onSavePosition
    val onUpdateBottomTools = inputs.onUpdateBottomTools
    val onUpdateHiddenTools = inputs.onUpdateHiddenTools
    val onUpdateToolOrder = inputs.onUpdateToolOrder
    val ownsPaneGlobals = inputs.ownsPaneGlobals
    var pageAspectRatios by inputs.pageAspectRatios
    var pdfFirstPageStandaloneInSpread by inputs.pdfFirstPageStandaloneInSpread
    var pdfPageSpreadMode by inputs.pdfPageSpreadMode
    val pdfUri = inputs.pdfUri
    var pendingActionAfterOcrSelection by inputs.pendingActionAfterOcrSelection
    var pendingPaginationSpreadRestorePage by inputs.pendingPaginationSpreadRestorePage
    var pendingRestorePage by inputs.pendingRestorePage
    var poppedUpPanelBitmap by inputs.poppedUpPanelBitmap
    var rightToLeftPagination by inputs.rightToLeftPagination
    var screenOrientationMode by inputs.screenOrientationMode
    var searchHighlightMode by inputs.searchHighlightMode
    var showBars by inputs.showBars
    var showCustomizeToolsSheet by inputs.showCustomizeToolsSheet
    var showInsufficientCreditsDialog by inputs.showInsufficientCreditsDialog
    var showOcrLanguageDialog by inputs.showOcrLanguageDialog
    var showPageNumberOverlay by inputs.showPageNumberOverlay
    var showPasswordDialog by inputs.showPasswordDialog
    var showReindexDialog by inputs.showReindexDialog
    var showScreenOrientationSheet by inputs.showScreenOrientationSheet
    var showThemePanel by inputs.showThemePanel
    var showTopTabStrip by inputs.showTopTabStrip
    var showVerticalPageGap by inputs.showVerticalPageGap
    var showVisualOptionsSheet by inputs.showVisualOptionsSheet
    val summaryCacheManager = inputs.summaryCacheManager
    var systemUiMode by inputs.systemUiMode
    val tabStateMap = inputs.tabStateMap
    var tapToNavigateEnabled by inputs.tapToNavigateEnabled
    var pageTurnAnimationEnabled by inputs.pageTurnAnimationEnabled
    val toolOrder = inputs.toolOrder
    val toolSettings = inputs.toolSettings
    val ttsController = inputs.ttsController
    val ttsState = inputs.ttsState
    val uiState = inputs.uiState
    val viewModel = inputs.viewModel

    val paneInitialPage = surfaceState.paneInitialPage
    val selectedBookIdForPane = surfaceState.selectedBookIdForPane
    val effectivePdfUri = surfaceState.effectivePdfUri
    val effectiveFileType = surfaceState.effectiveFileType
    val effectiveInitialPage = surfaceState.effectiveInitialPage
    val effectiveInitialBookmarksJson = surfaceState.effectiveInitialBookmarksJson
    var documentPassword by surfaceState.documentPassword
    var isPrintBlockedForPasswordProtectedPdf by surfaceState.isPrintBlockedForPasswordProtectedPdf
    val isComicFile = surfaceState.isComicFile
    var showNewTabSheet by surfaceState.showNewTabSheet
    var showFileInfoDialog by surfaceState.showFileInfoDialog
    val sheetState = surfaceState.sheetState
    val isTabsEnabled = surfaceState.isTabsEnabled
    val openTabs = surfaceState.openTabs
    val activeTabBookId = surfaceState.activeTabBookId
    val canShowPdfTabs = surfaceState.canShowPdfTabs
    val isPdfTabStripVisible = surfaceState.isPdfTabStripVisible.value
    val originalFileName = surfaceState.originalFileName
    var documentMetadataTitle by surfaceState.documentMetadataTitle
    val activeLibraryItem = surfaceState.activeLibraryItem
    val effectiveReaderBookTitle = surfaceState.effectiveReaderBookTitle
    var currentBookId by surfaceState.currentBookId
    val bookId = surfaceState.bookId
    val activeDocumentRenderKey = surfaceState.activeDocumentRenderKey
    val view = surfaceState.view
    var isDockDragging by surfaceState.isDockDragging
    var initialScrollDone by surfaceState.initialScrollDone
    val reflowBookId = surfaceState.reflowBookId
    val hasReflowFile = surfaceState.hasReflowFile
    var isAutoScrollModeActive by surfaceState.isAutoScrollModeActive
    var isAutoScrollPlaying by surfaceState.isAutoScrollPlaying
    var isAutoScrollTempPaused by surfaceState.isAutoScrollTempPaused
    val autoScrollResumeJob = surfaceState.autoScrollResumeJob
    var isAutoScrollCollapsed by surfaceState.isAutoScrollCollapsed
    var ttsOverlaySize by surfaceState.ttsOverlaySize
    var isMusicianMode by surfaceState.isMusicianMode
    var autoScrollUseSlider by surfaceState.autoScrollUseSlider
    var isStylusOnlyMode by surfaceState.isStylusOnlyMode
    var showTtsControlsSheet by surfaceState.showTtsControlsSheet
    var isKeepScreenOn by surfaceState.isKeepScreenOn
    val isTtsPlaybackForThisPane = surfaceState.isTtsPlaybackForThisPane
    var currentTtsMode by surfaceState.currentTtsMode
    var showTtsSettingsSheet by surfaceState.showTtsSettingsSheet
    var showTtsReplacementsSheet by surfaceState.showTtsReplacementsSheet
    var ttsReplacementPreferences by surfaceState.ttsReplacementPreferences
    val updateTtsReplacementPreferences = surfaceState.updateTtsReplacementPreferences
    var showDictionarySettingsSheet by surfaceState.showDictionarySettingsSheet
    var useOnlineDictionary by surfaceState.useOnlineDictionary
    var selectedDictPackage by surfaceState.selectedDictPackage
    var selectedTranslatePackage by surfaceState.selectedTranslatePackage
    var selectedSearchPackage by surfaceState.selectedSearchPackage
    val triggerAutoScrollTempPause = surfaceState.triggerAutoScrollTempPause
    val onAutoScrollInteraction = surfaceState.onAutoScrollInteraction
    var paginationDraggingBoxId by surfaceState.paginationDraggingBoxId
    val showBanner = surfaceState.showBanner
    val onOcrStateChange = surfaceState.onOcrStateChange
    var showZoomIndicator by surfaceState.showZoomIndicator
    var bookmarks by surfaceState.bookmarks
    var showPenPlayground by surfaceState.showPenPlayground
    var isEditMode by surfaceState.isEditMode
    var isDockMinimized by surfaceState.isDockMinimized
    var pendingNoteForNewHighlight by surfaceState.pendingNoteForNewHighlight
    var highlightToNoteId by surfaceState.highlightToNoteId
    val onNoteRequested = surfaceState.onNoteRequested
    val isDrawingActive = surfaceState.isDrawingActive
    var isAutoScrollLocal by surfaceState.isAutoScrollLocal
    val onPrintDocument = surfaceState.onPrintDocument
    var autoScrollSpeed by surfaceState.autoScrollSpeed
    var autoScrollMinSpeed by surfaceState.autoScrollMinSpeed
    var autoScrollMaxSpeed by surfaceState.autoScrollMaxSpeed
    val onToggleAutoScrollMode = surfaceState.onToggleAutoScrollMode
    val updateSpeed = surfaceState.updateSpeed
    val updateMinSpeed = surfaceState.updateMinSpeed
    val updateMaxSpeed = surfaceState.updateMaxSpeed
    var customHighlightColors by surfaceState.customHighlightColors
    var showHighlightColorPicker by surfaceState.showHighlightColorPicker
    var highlightColorPickerInitialSlot by surfaceState.highlightColorPickerInitialSlot
    var isBubbleZoomModeActive by surfaceState.isBubbleZoomModeActive
    var showBubbleZoomDownloadDialog by surfaceState.showBubbleZoomDownloadDialog
    var dockLocation by surfaceState.dockLocation
    var dockOffset by surfaceState.dockOffset
    var snapPreviewLocation by surfaceState.snapPreviewLocation
    var paginationDraggingOffset by surfaceState.paginationDraggingOffset
    var paginationDraggingSize by surfaceState.paginationDraggingSize
    var paginationDragPageHeight by surfaceState.paginationDragPageHeight
    var paginationOriginalRelSize by surfaceState.paginationOriginalRelSize
    val window = surfaceState.window
    // Keep all chrome visibility derived from the state this composable observes. The setup
    // sibling also computes this value for system UI effects, but its plain bridge snapshot can
    // otherwise lag behind a document tap during the same frame.
    val showStandardBars = showBars && !isEditMode
    var readerBrightnessSettings by surfaceState.readerBrightnessSettings
    var showBrightnessSheet by surfaceState.showBrightnessSheet
    val updateReaderBrightness = surfaceState.updateReaderBrightness
    val dockHeight = surfaceState.dockHeight
    val dockHeightPx = surfaceState.dockHeightPx.value
    val density = surfaceState.density.value
    val viewConfiguration = surfaceState.viewConfiguration
    val statusBarHeightDp = surfaceState.statusBarHeightDp.value
    val searchState = surfaceState.searchState
    val navBarHeight = surfaceState.navBarHeight.value
    val verticalHeaderHeight = surfaceState.verticalHeaderHeight.value
    val topOverlayInset = surfaceState.topOverlayInset.value
    val verticalFooterHeight = surfaceState.verticalFooterHeight.value
    var errorMessage by surfaceState.errorMessage
    var showToolSettings by surfaceState.showToolSettings
    val isHighlighterSnapEnabled = surfaceState.isHighlighterSnapEnabled
    val selectedTool = surfaceState.selectedTool.value
    val lastPenTool = surfaceState.lastPenTool
    val lastHighlighterTool = surfaceState.lastHighlighterTool
    val dockPenColor = surfaceState.dockPenColor
    val dockHighlighterColor = surfaceState.dockHighlighterColor
    val activeToolThickness = surfaceState.activeToolThickness
    val eraserToolThickness = surfaceState.eraserToolThickness
    val fountainPenColor = surfaceState.fountainPenColor
    val markerColor = surfaceState.markerColor
    val pencilColor = surfaceState.pencilColor
    val highlighterColor = surfaceState.highlighterColor
    val highlighterRoundColor = surfaceState.highlighterRoundColor
    val isCurrentToolHighlighter = surfaceState.isCurrentToolHighlighter
    val currentSnapEnabled = surfaceState.currentSnapEnabled
    val currentIsHighlighter = surfaceState.currentIsHighlighter
    val penPalette = surfaceState.penPalette
    val highlighterPalette = surfaceState.highlighterPalette
    val currentStrokeColor = surfaceState.currentStrokeColor
    val currentStrokeWidth = surfaceState.currentStrokeWidth
    val currentEraserStrokeWidth = surfaceState.currentEraserStrokeWidth
    val pdfTextRepository = surfaceState.pdfTextRepository
    val annotationRepository = surfaceState.annotationRepository
    val textBoxRepository = surfaceState.textBoxRepository
    val highlightRepository = surfaceState.highlightRepository
    var allAnnotations by surfaceState.allAnnotations
    val undoStack = surfaceState.undoStack
    val redoStack = surfaceState.redoStack
    val erasedAnnotationsFromStroke = surfaceState.erasedAnnotationsFromStroke
    var lastEraserPoint by surfaceState.lastEraserPoint
    var annotationSession by surfaceState.annotationSession
    val richTextRepository = surfaceState.richTextRepository
    val richTextController = surfaceState.richTextController
    var pdfDocument by surfaceState.pdfDocument
    var pfdState by surfaceState.pfdState
    var totalPages by surfaceState.totalPages
    var currentPageScale by surfaceState.currentPageScale
    val textBoxes = surfaceState.textBoxes
    var selectedTextBoxId by surfaceState.selectedTextBoxId
    val userHighlights = surfaceState.userHighlights
    val drawingState = surfaceState.drawingState
    val pdfiumCore = surfaceState.pdfiumCore
    val verticalReaderState = surfaceState.verticalReaderState
    var virtualPages by surfaceState.virtualPages
    var loadedPageLayoutBookId by surfaceState.loadedPageLayoutBookId
    var pageLayoutMutationVersion by surfaceState.pageLayoutMutationVersion
    val totalDisplayPages = surfaceState.totalDisplayPages
    val pdfSpreadSettings = surfaceState.pdfSpreadSettings
    val paginationSpreadStarts = surfaceState.paginationSpreadStarts
    val paginationPagerPageCount = surfaceState.paginationPagerPageCount
    val pagerState = surfaceState.pagerState
    val paginationDisplayPageForPagerPage = surfaceState.paginationDisplayPageForPagerPage
    val paginationPagerPageForDisplayPage = surfaceState.paginationPagerPageForDisplayPage
    val scrollPaginationToDisplayPage = surfaceState.scrollPaginationToDisplayPage
    val animatePaginationToDisplayPage = surfaceState.animatePaginationToDisplayPage
    val currentPaginationDisplayPage = surfaceState.currentPaginationDisplayPage
    val currentPage = surfaceState.currentPage
    var isDocumentReady by surfaceState.isDocumentReady
    val detectSpeechBubblesForPage = surfaceState.detectSpeechBubblesForPage
    var jumpHistory by surfaceState.jumpHistory
    val clearJumpHistory = surfaceState.clearJumpHistory
    val navigateToJumpHistoryPage = surfaceState.navigateToJumpHistoryPage
    val navigateToPdfPage = surfaceState.navigateToPdfPage
    val lifecycleOwner = surfaceState.lifecycleOwner

    val sidecarsReadyForCurrentBook = annotationSession.canUseFor(currentBookId)
    val textBoxesSnapshot by remember { derivedStateOf { textBoxes.toList() } }
    val userHighlightsSnapshot by remember { derivedStateOf { userHighlights.toList() } }
    val visibleAllAnnotations = if (sidecarsReadyForCurrentBook) allAnnotations else emptyMap()
    val visibleTextBoxes = if (sidecarsReadyForCurrentBook) textBoxesSnapshot else emptyList()
    val visibleUserHighlights = if (sidecarsReadyForCurrentBook) userHighlightsSnapshot else emptyList()
    val visibleTextBoxesByPage = remember(sidecarsReadyForCurrentBook, textBoxesSnapshot) {
        if (sidecarsReadyForCurrentBook) {
            textBoxesSnapshot.groupBy { it.pageIndex }
        } else {
            emptyMap()
        }
    }
    val visibleUserHighlightsByPage = remember(sidecarsReadyForCurrentBook, userHighlightsSnapshot) {
        if (sidecarsReadyForCurrentBook) {
            userHighlightsSnapshot.groupBy { it.pageIndex }
        } else {
            emptyMap()
        }
    }

    val currentAnnotations by rememberUpdatedState(allAnnotations)
    val currentTextBoxes by rememberUpdatedState(textBoxesSnapshot)
    val currentHighlights by rememberUpdatedState(userHighlightsSnapshot)
    val currentAnnotationSession by rememberUpdatedState(annotationSession)
    val currentBookmarks by rememberUpdatedState(bookmarks)
    val currentTotalPages by rememberUpdatedState(totalDisplayPages)
    val currentPageState by rememberUpdatedState(currentPage)
    val currentPendingPage by rememberUpdatedState(pendingRestorePage)
    val currentIsDocumentReady by rememberUpdatedState(isDocumentReady)
    val currentInitialScrollDone by rememberUpdatedState(initialScrollDone)
    val currentPdfUri by rememberUpdatedState(effectivePdfUri)
    val currentVisibleAllAnnotations by rememberUpdatedState(visibleAllAnnotations)
    val currentRichTextController by rememberUpdatedState(richTextController)

    val readerPersistence = remember(
        currentBookId,
        annotationRepository,
        textBoxRepository,
        highlightRepository,
        onBookmarksChanged,
        onSavePosition
    ) {
        PdfReaderPersistence(
            annotationRepository = annotationRepository,
            textBoxRepository = textBoxRepository,
            highlightRepository = highlightRepository,
            onBookmarksChanged = onBookmarksChanged,
            onSavePosition = onSavePosition,
            onSidecarsCommitted = viewModel::onPdfSidecarsCommitted
        )
    }

    val saveAllData = remember(currentBookId, readerPersistence) {
        { force: Boolean ->
            val bookIdSnapshot = currentBookId
            val annotationSessionSnapshot = currentAnnotationSession
            // saveAllData is remembered for the life of a document. Read these changing
            // values through rememberUpdatedState so a pause never saves the initial
            // restoration target after the reader has moved on.
            val isDocumentReadySnapshot = currentIsDocumentReady
            val initialScrollDoneSnapshot = currentInitialScrollDone
            val pdfUriSnapshot = currentPdfUri
            val annotsSnapshot = currentAnnotations
            val boxesSnapshot = currentTextBoxes
            val highlightsSnapshot = currentHighlights
            val bookmarksSnapshot = currentBookmarks
            val totalPagesSnapshot = currentTotalPages
            val currentPageSnapshot = currentPageState
            val pendingPageSnapshot = currentPendingPage
            viewModel.viewModelScope.launch {
                readerPersistence.save(
                    snapshot = PdfReaderSaveSnapshot(
                        bookId = bookIdSnapshot,
                        annotationSession = annotationSessionSnapshot,
                        isDocumentReady = isDocumentReadySnapshot,
                        initialRestorationComplete = initialScrollDoneSnapshot,
                        pdfUri = pdfUriSnapshot,
                        annotations = annotsSnapshot,
                        textBoxes = boxesSnapshot,
                        highlights = highlightsSnapshot,
                        bookmarks = bookmarksSnapshot,
                        totalPages = totalPagesSnapshot,
                        currentPage = currentPageSnapshot,
                        pendingRestorePage = pendingPageSnapshot
                    ),
                    force = force
                )
            }
        }
    }

    val persistInkAnnotationsNow = remember(currentBookId, readerPersistence) {
        { annotationsSnapshot: Map<Int, List<PdfAnnotation>>, deletedAnnotations: Collection<PdfAnnotation>, reason: String ->
            val bookIdSnapshot = currentBookId
            val annotationSessionSnapshot = currentAnnotationSession
            viewModel.viewModelScope.launch {
                readerPersistence.persistInk(
                    bookId = bookIdSnapshot,
                    annotationSession = annotationSessionSnapshot,
                    annotations = annotationsSnapshot,
                    deletedAnnotations = deletedAnnotations,
                    reason = reason
                )
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                val shouldSave = initialScrollDone || (currentPageState != 0)

                if (shouldSave) {
                    viewModel.viewModelScope.launch {
                        currentRichTextController?.let {
                            withContext(NonCancellable) { it.saveImmediate() }
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
        textBoxesSnapshot,
        userHighlightsSnapshot,
        bookmarks,
        currentPage,
        sidecarsReadyForCurrentBook
    ) {
        if (sidecarsReadyForCurrentBook && initialScrollDone) {
            delay(500) // Keep local state near-current; folder export is coalesced separately.
            saveAllData(false)
        }
    }

    val allAnnotationsProvider = remember { { currentVisibleAllAnnotations } }

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
        // Read the live surface value instead of a composition-time snapshot: this
        // closure is captured by long-lived callbacks (remember-ed TTS starters,
        // permission launchers) created before the document finishes loading.
        val displayPageCount = surfaceState.totalDisplayPages
        if (displayPageIndex !in 0 until displayPageCount) {
            Timber.tag(TTS_DIAG_TAG).w(
                "map.reject index=$displayPageIndex totalDisplayPages=$displayPageCount " +
                    "totalPdfPages=$totalPages virtual=${virtualPages.pdfLayoutDebugSummary()}"
            )
            return null
        }
        if (virtualPages.isEmpty()) return displayPageIndex.takeIf { it in 0 until totalPages }

        return when (val virtualPage = virtualPages.getOrNull(displayPageIndex)) {
            is VirtualPage.PdfPage -> virtualPage.pdfIndex.takeIf { it in 0 until totalPages }
            is VirtualPage.BlankPage -> {
                Timber.tag(TTS_DIAG_TAG).w(
                    "map.blankPage index=$displayPageIndex totalDisplayPages=$displayPageCount " +
                        "virtual=${virtualPages.pdfLayoutDebugSummary()}"
                )
                null
            }
            null -> {
                Timber.tag(TTS_DIAG_TAG).w(
                    "map.missingEntry index=$displayPageIndex totalDisplayPages=$displayPageCount " +
                        "virtual=${virtualPages.pdfLayoutDebugSummary()}"
                )
                null
            }
        }
    }

    fun pdfPageToDisplayPage(pdfPageIndex: Int): Int? {
        val displayPageCount = surfaceState.totalDisplayPages
        if (pdfPageIndex !in 0 until totalPages) return null
        if (virtualPages.isEmpty()) return pdfPageIndex.takeIf { it in 0 until displayPageCount }

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
                if (currentPaginationDisplayPage() != targetPage) {
                    Timber.tag("CursorNav").d("Cursor moved to Page $targetPage. Auto-paging.")
                    animatePaginationToDisplayPage(targetPage)
                }
            }
        }
    }

    Timber.d("Derived currentPage recomposed. New value: $currentPage (Mode: $displayMode)")

    suspend fun rebuildMissingHighlightBounds(
        document: ReaderDocument,
        highlights: List<PdfUserHighlight>
    ): List<PdfUserHighlight> = withContext(Dispatchers.IO) {
        highlights.map { highlight ->
            if (highlight.bounds.isNotEmpty()) return@map highlight
            val start = highlight.range.first
            val end = highlight.range.second
            if (highlight.pageIndex < 0 || end <= start) return@map highlight

            runCatching {
                document.openPage(highlight.pageIndex)?.use { page ->
                    page.openTextPage().use { textPage ->
                        val rects = textPage.textPageGetRectsForRanges(intArrayOf(start, end - start))
                            ?.map { it.rect }
                            .orEmpty()
                        val merged = mergePdfRectsIntoLines(rects)
                        if (merged.isEmpty()) highlight else highlight.copy(bounds = merged)
                    }
                } ?: highlight
            }.getOrDefault(highlight)
        }
    }

    val onHighlightAdd = remember(pdfDocument, currentBookId, customHighlightColors) {
        { pageIndex: Int, range: Pair<Int, Int>, text: String, color: PdfHighlightColor, style: HighlightStyle ->
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
                                    colorArgb = customHighlightColors[color]?.toArgb() ?: color.color.toArgb(),
                                    style = style,
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

    val onHighlightUpdate = remember(customHighlightColors) {
        { id: String, newColor: PdfHighlightColor, newStyle: HighlightStyle? ->
            Timber.tag("PdfHighlightDebug").d("onHighlightUpdate triggered: id=$id, newColor=$newColor")
            val index = userHighlights.indexOfFirst { it.id == id }
            if (index != -1) {
                val old = userHighlights[index]
                userHighlights[index] = old.copy(
                    color = newColor,
                    colorArgb = customHighlightColors[newColor]?.toArgb() ?: newColor.color.toArgb(),
                    style = newStyle ?: old.style
                )
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
            val activeBookId = currentBookId ?: return@launch
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                "ui.insert.request bookId=$activeBookId loadedLayoutBookId=$loadedPageLayoutBookId " +
                    "isReady=$isDocumentReady mutation=$pageLayoutMutationVersion currentPage=$currentPage " +
                    "totalPdfPages=$totalPages displayMode=$displayMode current=${virtualPages.pdfLayoutDebugSummary()}"
            )
            if (!canManagePdfVirtualPages(
                    isDocumentReady = isDocumentReady,
                    currentBookId = activeBookId,
                    loadedPageLayoutBookId = loadedPageLayoutBookId,
                    virtualPageCount = virtualPages.size
                )
            ) {
                Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).w(
                    "ui.insert.blocked bookId=$activeBookId loadedLayoutBookId=$loadedPageLayoutBookId " +
                        "isReady=$isDocumentReady virtualCount=${virtualPages.size}"
                )
                Timber.tag("RichTextMigration").w("INSERT: Ignoring page insert until saved layout is loaded.")
                return@launch
            }
            val layoutBeforeInsert = virtualPages.ifEmpty {
                (0 until totalPages).map { VirtualPage.PdfPage(it) }
            }
            val targetIndex = (currentPage + 1).coerceIn(0, layoutBeforeInsert.size)
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                "ui.insert.target bookId=$activeBookId targetIndex=$targetIndex before=${layoutBeforeInsert.pdfLayoutDebugSummary()}"
            )
            Timber.tag("RichTextMigration").i("INSERT: User requested blank page at index $targetIndex")
            pageLayoutMutationVersion++

            val (refWidth, refHeight) = withContext(Dispatchers.IO) {
                if (layoutBeforeInsert.isNotEmpty()) {
                    val refIndex = (currentPage).coerceIn(0, layoutBeforeInsert.size - 1)
                    when (val vp = layoutBeforeInsert[refIndex]) {
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

            run {
                val annotationsBeforeInsert = allAnnotations
                val undoStackBeforeInsert = undoStack.toList()
                val redoStackBeforeInsert = redoStack.toList()
                val tempNewPage = VirtualPage.BlankPage(generateShortId(), refWidth, refHeight, wasManuallyAdded = true)
                val optimisticPages = layoutBeforeInsert.toMutableList()
                optimisticPages.add(targetIndex, tempNewPage)
                Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                    "ui.insert.optimistic bookId=$activeBookId targetIndex=$targetIndex " +
                        "newBlankId=${tempNewPage.id} ref=${refWidth}x$refHeight " +
                        "optimistic=${optimisticPages.pdfLayoutDebugSummary()}"
                )

                allAnnotations = remapPdfAnnotationsForLayoutChange(
                    currentLayout = layoutBeforeInsert,
                    updatedLayout = optimisticPages,
                    annotations = annotationsBeforeInsert
                )
                val shiftedBoxes = remapPdfTextBoxesForLayoutChange(
                    currentLayout = layoutBeforeInsert,
                    updatedLayout = optimisticPages,
                    textBoxes = textBoxes
                )
                if (shiftedBoxes != textBoxes.toList()) {
                    textBoxes.clear()
                    textBoxes.addAll(shiftedBoxes)
                }

                val shiftedHighlights = remapPdfUserHighlightsForLayoutChange(
                    currentLayout = layoutBeforeInsert,
                    updatedLayout = optimisticPages,
                    highlights = userHighlights
                )
                if (shiftedHighlights != userHighlights.toList()) {
                    userHighlights.clear()
                    userHighlights.addAll(shiftedHighlights)
                }
                undoStack.clear()
                undoStack.addAll(
                    remapPdfHistoryActionsForLayoutChange(
                        currentLayout = layoutBeforeInsert,
                        updatedLayout = optimisticPages,
                        actions = undoStackBeforeInsert
                    )
                )
                redoStack.clear()
                redoStack.addAll(
                    remapPdfHistoryActionsForLayoutChange(
                        currentLayout = layoutBeforeInsert,
                        updatedLayout = optimisticPages,
                        actions = redoStackBeforeInsert
                    )
                )

                virtualPages = optimisticPages

                val objectList = bookmarks.map { bookmark ->
                    JSONObject().apply {
                        put("pageIndex", bookmark.pageIndex)
                        put("title", bookmark.title)
                        put("totalPages", bookmark.totalPages)
                    }
                }
                val currentJson = JSONArray(objectList).toString()

                val result = withContext(NonCancellable) {
                    val savedResult = viewModel.addPage(
                        bookId = activeBookId,
                        currentLayout = layoutBeforeInsert,
                        insertIndex = targetIndex,
                        currentAnnotations = annotationsBeforeInsert,
                        currentBookmarksJson = currentJson,
                        referenceWidth = refWidth,
                        referenceHeight = refHeight,
                        blankPageId = tempNewPage.id,
                        wasManuallyAdded = true
                    )
                    Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                        "ui.insert.saved bookId=$activeBookId targetIndex=$targetIndex " +
                            "result=${savedResult.layout.pdfLayoutDebugSummary()}"
                    )
                    richTextController?.remapPagesForLayoutChange(
                        currentLayout = layoutBeforeInsert,
                        updatedLayout = savedResult.layout
                    )
                    savedResult
                }

                Timber.tag("RichTextMigration").i("INSERT: Layout update complete. New virtualPages size: ${result.layout.size}")

                virtualPages = result.layout
                allAnnotations = result.annotations
                Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                    "ui.insert.applied bookId=$activeBookId mutation=$pageLayoutMutationVersion " +
                        "virtual=${virtualPages.pdfLayoutDebugSummary()}"
                )
                val remappedUndoStack = remapPdfHistoryActionsForLayoutChange(
                    currentLayout = optimisticPages,
                    updatedLayout = result.layout,
                    actions = undoStack
                )
                undoStack.clear()
                undoStack.addAll(remappedUndoStack)
                val remappedRedoStack = remapPdfHistoryActionsForLayoutChange(
                    currentLayout = optimisticPages,
                    updatedLayout = result.layout,
                    actions = redoStack
                )
                redoStack.clear()
                redoStack.addAll(remappedRedoStack)
                bookmarks = loadPdfBookmarksFromJson(result.bookmarksJson)
                onBookmarksChanged(result.bookmarksJson)

                showBanner("Page added at ${targetIndex + 1}")
                Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                    "ui.insert.scroll.start bookId=$activeBookId targetIndex=$targetIndex displayMode=$displayMode"
                )
                if (displayMode == DisplayMode.PAGINATION) {
                    pagerState.animateScrollToPage(targetIndex)
                } else {
                    verticalReaderState.scrollToPage(targetIndex)
                }
                Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                    "ui.insert.scroll.done bookId=$activeBookId targetIndex=$targetIndex displayMode=$displayMode"
                )
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
            val activeBookId = currentBookId ?: return@launch
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                "ui.delete.request bookId=$activeBookId loadedLayoutBookId=$loadedPageLayoutBookId " +
                    "isReady=$isDocumentReady mutation=$pageLayoutMutationVersion currentPage=$currentPage " +
                    "totalPdfPages=$totalPages displayMode=$displayMode current=${virtualPages.pdfLayoutDebugSummary()}"
            )
            if (!canManagePdfVirtualPages(
                    isDocumentReady = isDocumentReady,
                    currentBookId = activeBookId,
                    loadedPageLayoutBookId = loadedPageLayoutBookId,
                    virtualPageCount = virtualPages.size
                )
            ) {
                Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).w(
                    "ui.delete.blocked bookId=$activeBookId loadedLayoutBookId=$loadedPageLayoutBookId " +
                        "isReady=$isDocumentReady virtualCount=${virtualPages.size}"
                )
                Timber.tag("RichTextMigration").w("DELETE: Ignoring page delete until saved layout is loaded.")
                return@launch
            }
            val layoutBeforeDelete = virtualPages.ifEmpty {
                (0 until totalPages).map { VirtualPage.PdfPage(it) }
            }
            if (currentPage in layoutBeforeDelete.indices) {
                Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                    "ui.delete.target bookId=$activeBookId removeIndex=$currentPage " +
                        "before=${layoutBeforeDelete.pdfLayoutDebugSummary()}"
                )
                Timber.tag("RichTextMigration").i("DELETE: User requested deletion of page at index $currentPage")
                pageLayoutMutationVersion++

                val objectList = bookmarks.map { bookmark ->
                    JSONObject().apply {
                        put("pageIndex", bookmark.pageIndex)
                        put("title", bookmark.title)
                        put("totalPages", bookmark.totalPages)
                    }
                }
                val currentJson = JSONArray(objectList).toString()

                val result = withContext(NonCancellable) {
                    val savedResult = viewModel.removePage(
                        activeBookId, layoutBeforeDelete, currentPage, allAnnotations, currentJson
                    )
                    richTextController?.remapPagesForLayoutChange(
                        currentLayout = layoutBeforeDelete,
                        updatedLayout = savedResult.layout
                    )
                    Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                        "ui.delete.saved bookId=$activeBookId removeIndex=$currentPage " +
                            "result=${savedResult.layout.pdfLayoutDebugSummary()}"
                    )
                    savedResult
                }
                Timber.tag("RichTextMigration").i("DELETE: Layout update complete. New virtualPages size: ${result.layout.size}")

                val shiftedBoxes = remapPdfTextBoxesForLayoutChange(
                    currentLayout = layoutBeforeDelete,
                    updatedLayout = result.layout,
                    textBoxes = textBoxes
                )
                textBoxes.clear()
                textBoxes.addAll(shiftedBoxes)
                val shiftedHighlights = remapPdfUserHighlightsForLayoutChange(
                    currentLayout = layoutBeforeDelete,
                    updatedLayout = result.layout,
                    highlights = userHighlights
                )
                userHighlights.clear()
                userHighlights.addAll(shiftedHighlights)
                virtualPages = result.layout
                allAnnotations = result.annotations
                Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                    "ui.delete.applied bookId=$activeBookId mutation=$pageLayoutMutationVersion " +
                        "virtual=${virtualPages.pdfLayoutDebugSummary()}"
                )
                val remappedUndoStack = remapPdfHistoryActionsForLayoutChange(
                    currentLayout = layoutBeforeDelete,
                    updatedLayout = result.layout,
                    actions = undoStack
                )
                undoStack.clear()
                undoStack.addAll(remappedUndoStack)
                val remappedRedoStack = remapPdfHistoryActionsForLayoutChange(
                    currentLayout = layoutBeforeDelete,
                    updatedLayout = result.layout,
                    actions = redoStack
                )
                redoStack.clear()
                redoStack.addAll(remappedRedoStack)
                bookmarks = loadPdfBookmarksFromJson(result.bookmarksJson)
                onBookmarksChanged(result.bookmarksJson)

                showBanner("Page deleted")

                val newMax = (virtualPages.size - 1).coerceAtLeast(0)
                if (currentPage > newMax) {
                    if (displayMode == DisplayMode.PAGINATION) {
                        scrollPaginationToDisplayPage(newMax)
                    } else {
                        verticalReaderState.scrollToPage(newMax)
                    }
                }
            } else {
                Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).w(
                    "ui.delete.invalidIndex bookId=$activeBookId removeIndex=$currentPage before=${layoutBeforeDelete.pdfLayoutDebugSummary()}"
                )
            }
        }
    }

    val onInsertTextBox = {
        val currentP = if (displayMode == DisplayMode.PAGINATION) currentPaginationDisplayPage() else verticalReaderState.currentPage

        Timber.tag("PdfTextBoxDebug").d("Viewer: onInsertTextBox triggered. Target Page: $currentP, DisplayMode: $displayMode")
        Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).d(
            "event=insert_request page=$currentP displayMode=$displayMode " +
                "textBoxEditMode=$isDrawingActive selectedTextBoxId=${selectedTextBoxId ?: "none"}"
        )

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
        Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).i(
            "event=insert_created id=${newBox.id} page=${newBox.pageIndex} " +
                "selectedTextBoxId=${selectedTextBoxId ?: "none"} textLength=${newBox.text.length} " +
                "textBoxEditMode=$isDrawingActive"
        )
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
                    val showBarsBeforeTap = showBars
                    showBars = !showBars
                    Timber.tag("PdfToolbarTrace").d(
                        "singleTap dispatched before=$showBarsBeforeTap after=$showBars " +
                            "isSplitPane=$isSplitPane ownsPaneGlobals=$ownsPaneGlobals"
                    )
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

    LaunchedEffect(highestRequiredTextPageIndex, virtualPages.size, allAnnotations, loadedPageLayoutBookId) {
        val activeBookId = currentBookId
        if (
            richTextController == null ||
            !canManagePdfVirtualPages(
                isDocumentReady = isDocumentReady,
                currentBookId = activeBookId,
                loadedPageLayoutBookId = loadedPageLayoutBookId,
                virtualPageCount = virtualPages.size
            )
        ) {
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).d(
                "ui.autoPage.skip bookId=$activeBookId hasRichController=${richTextController != null} " +
                    "isReady=$isDocumentReady loadedLayoutBookId=$loadedPageLayoutBookId " +
                    "virtualCount=${virtualPages.size} highestRequired=$highestRequiredTextPageIndex"
            )
            return@LaunchedEffect
        }

        delay(500)

        @Suppress("UnusedVariable", "Unused") val lastPageIndex = virtualPages.size - 1
        val requiredPages = highestRequiredTextPageIndex + 1

        // Expansion Logic
        if (requiredPages > virtualPages.size) {
            Timber.tag("RichTextFlow").i("Text overflow detected. Required pages: $requiredPages, current: ${virtualPages.size}. Adding page.")
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                "ui.autoPage.expand.start bookId=$activeBookId requiredPages=$requiredPages " +
                    "current=${virtualPages.pdfLayoutDebugSummary()}"
            )

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
                bookId = activeBookId!!,
                currentLayout = virtualPages,
                insertIndex = virtualPages.size,
                currentAnnotations = allAnnotations,
                currentBookmarksJson = currentJson,
                referenceWidth = refWidth,
                referenceHeight = refHeight,
                wasManuallyAdded = false // Auto-added page
            )
            pageLayoutMutationVersion++

            virtualPages = result.layout
            allAnnotations = result.annotations
            bookmarks = loadPdfBookmarksFromJson(result.bookmarksJson)
            onBookmarksChanged(result.bookmarksJson)
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                "ui.autoPage.expand.done bookId=$activeBookId mutation=$pageLayoutMutationVersion " +
                    "result=${virtualPages.pdfLayoutDebugSummary()}"
            )
        }
        // Contraction Logic
        else {
            var lastPage = virtualPages.lastOrNull()
            var currentLastIndex = virtualPages.size - 1
            var pageRemoved = false

            while (shouldAutoPrunePdfBlankPage(
                    lastPage = lastPage,
                    currentLastIndex = currentLastIndex,
                    highestRequiredTextPageIndex = highestRequiredTextPageIndex,
                    hasText = hasTextOnPage(currentLastIndex),
                    hasAnnotations = !allAnnotations[currentLastIndex].isNullOrEmpty(),
                    hasTextBoxes = textBoxes.any { it.pageIndex == currentLastIndex },
                    hasHighlights = userHighlights.any { it.pageIndex == currentLastIndex },
                    hasBookmark = bookmarks.any { it.pageIndex == currentLastIndex },
                )) {
                Timber.tag("RichTextFlow").i("Auto-pruning empty page at index $currentLastIndex.")
                Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                    "ui.autoPage.prune.start bookId=$activeBookId removeIndex=$currentLastIndex " +
                        "highestRequired=$highestRequiredTextPageIndex before=${virtualPages.pdfLayoutDebugSummary()}"
                )
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
                    activeBookId!!, virtualPages, currentLastIndex, allAnnotations, currentJson
                )
                pageLayoutMutationVersion++

                virtualPages = result.layout
                allAnnotations = result.annotations
                bookmarks = loadPdfBookmarksFromJson(result.bookmarksJson)
                onBookmarksChanged(result.bookmarksJson)
                Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                    "ui.autoPage.prune.done bookId=$activeBookId mutation=$pageLayoutMutationVersion " +
                        "result=${virtualPages.pdfLayoutDebugSummary()}"
                )

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
                        if (currentPaginationDisplayPage() != targetPage) {
                            scrollPaginationToDisplayPage(targetPage)
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

    LaunchedEffect(isDocumentReady, currentBookId, totalPages) {
        val loadingBookId = currentBookId
        if (isDocumentReady && loadingBookId != null && totalPages > 0) {
            val loadMutationVersion = pageLayoutMutationVersion
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                "ui.layoutLoad.start bookId=$loadingBookId totalPdfPages=$totalPages " +
                    "mutationAtStart=$loadMutationVersion currentMutation=$pageLayoutMutationVersion " +
                    "loadedLayoutBookId=$loadedPageLayoutBookId current=${virtualPages.pdfLayoutDebugSummary()}"
            )
            val layout = viewModel.loadPageLayout(loadingBookId, totalPages)
            if (currentBookId != loadingBookId || loadMutationVersion != pageLayoutMutationVersion) {
                Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).w(
                    "ui.layoutLoad.stale bookId=$loadingBookId currentBookId=$currentBookId " +
                        "mutationAtStart=$loadMutationVersion currentMutation=$pageLayoutMutationVersion " +
                        "loaded=${layout.pdfLayoutDebugSummary()}"
                )
                Timber.tag("RichTextMigration").w(
                    "Skipping stale page layout load for $loadingBookId; mutation version changed."
                )
                return@LaunchedEffect
            }
            virtualPages = layout
            loadedPageLayoutBookId = loadingBookId
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                "ui.layoutLoad.applied bookId=$loadingBookId loadedLayoutBookId=$loadedPageLayoutBookId " +
                    "mutation=$pageLayoutMutationVersion layout=${layout.pdfLayoutDebugSummary()}"
            )

            if (initialPage != null && initialPage >= totalPages && initialPage < layout.size) {
                Timber.d("Restoring position to added page: $initialPage")
                if (displayMode == DisplayMode.PAGINATION) {
                    scrollPaginationToDisplayPage(initialPage)
                } else {
                    verticalReaderState.scrollToPage(initialPage)
                }
            }
        } else {
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).d(
                "ui.layoutLoad.skip isReady=$isDocumentReady bookId=$loadingBookId totalPdfPages=$totalPages " +
                    "loadedLayoutBookId=$loadedPageLayoutBookId current=${virtualPages.pdfLayoutDebugSummary()}"
            )
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        Timber.d("Pager state changed: pagerState.currentPage is now ${pagerState.currentPage}")
    }

    LaunchedEffect(displayMode) {
        if (initialScrollDone) {
            if (displayMode == DisplayMode.VERTICAL_SCROLL) {
                val pageToScroll = currentPaginationDisplayPage()

                var attempts = 0
                while (verticalReaderState.snapToPageHandler == null && attempts < 50) {
                    delay(16)
                    attempts++
                }
                verticalReaderState.snapToPage(pageToScroll)
            } else {
                val pageToScroll = verticalReaderState.currentPage
                scrollPaginationToDisplayPage(pageToScroll)
            }
        }
    }

    val isBookmarked by remember(
        bookmarks, currentPage, verticalReaderState.currentPage, displayMode
    ) {
        derivedStateOf {
            val currentPage = if (displayMode == DisplayMode.PAGINATION) {
                currentPaginationDisplayPage()
            } else {
                verticalReaderState.currentPage
            }
            bookmarks.any { it.pageIndex == currentPage }
        }
    }

    val zoomIndicatorPercentage = pdfZoomIndicatorPercent(currentPageScale)
    LaunchedEffect(zoomIndicatorPercentage) {
        if (shouldShowPdfZoomIndicator(zoomIndicatorPercentage)) {
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
                val updatedBookmarks = bookmarks.filterNot { it.pageIndex == pageIndex }.toSet()
                bookmarks = updatedBookmarks
                onBookmarksChanged(serializePdfBookmarksToJson(updatedBookmarks))
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
                    context.getString(R.string.pdf_page_short, pageIndex + 1)
                }

                val chapterTitle =
                    flatTableOfContents.lastOrNull { it.pageIndex <= pageIndex }?.title

                val finalTitle = if (!chapterTitle.isNullOrBlank()) {
                    "$contentTitle\n$chapterTitle"
                } else {
                    contentTitle
                }

                Timber.d("Final Bookmark Title: '$finalTitle'")

                val updatedBookmarks = bookmarks + PdfBookmark(
                    pageIndex = pageIndex, title = finalTitle, totalPages = totalPages
                )
                bookmarks = updatedBookmarks
                onBookmarksChanged(serializePdfBookmarksToJson(updatedBookmarks))
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
            currentPaginationDisplayPage()
        } else {
            verticalReaderState.currentPage
        }
        onToggleBookmark(currentPage)
    }

    LaunchedEffect(currentBookId) {
        val loadingBookId = currentBookId
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
            "ui.sidecarLoad.start bookId=$loadingBookId previousLoadedLayoutBookId=$loadedPageLayoutBookId " +
                "previousVirtual=${virtualPages.pdfLayoutDebugSummary()}"
        )

        annotationSession = annotationSession.reduce(SharedPdfAnnotationSessionAction.Reset)
        allAnnotations = emptyMap()
        textBoxes.clear()
        userHighlights.clear()
        virtualPages = emptyList()
        loadedPageLayoutBookId = null
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
            "ui.sidecarLoad.reset bookId=$loadingBookId virtualCleared=true loadedLayoutBookId=$loadedPageLayoutBookId"
        )
        selectedTextBoxId = null
        undoStack.clear()
        redoStack.clear()
        erasedAnnotationsFromStroke.clear()
        drawingState.onDrawCancel()

        if (loadingBookId == null) {
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i("ui.sidecarLoad.noBook")
            return@LaunchedEffect
        }

        annotationSession = annotationSession.reduce(
            SharedPdfAnnotationSessionAction.LoadStarted(loadingBookId),
        )

        val loaded = annotationRepository.loadAnnotations(loadingBookId)
        val loadedBoxes = textBoxRepository.loadTextBoxes(loadingBookId)
        val loadedHighlights = highlightRepository.loadHighlights(loadingBookId)

        if (currentBookId != loadingBookId) return@LaunchedEffect

        allAnnotations = loaded
        textBoxes.addAll(loadedBoxes)
        userHighlights.addAll(loadedHighlights)
        readerPersistence.recordLoadedSidecars(loaded, loadedBoxes, loadedHighlights)
        annotationSession = annotationSession.reduce(
            SharedPdfAnnotationSessionAction.LoadCompleted(
                bookId = loadingBookId,
                inkCount = loaded.values.sumOf { it.size },
                textBoxCount = loadedBoxes.size,
                highlightCount = loadedHighlights.size,
            ),
        )
        logCloudAnnotationSyncTrace {
            "android.reader.sidecar_load book=$loadingBookId inkPages=${loaded.keys.sorted()} " +
                "inkCount=${loaded.values.sumOf { it.size }} textBoxes=${loadedBoxes.size} " +
                "highlights=${loadedHighlights.size} hashes=${readerPersistence.loadedSidecarHashesLabel()}"
        }
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
            "ui.sidecarLoad.done bookId=$loadingBookId annotationPages=${loaded.keys.sorted()} " +
                "textBoxes=${loadedBoxes.size} highlights=${loadedHighlights.size}"
        )
    }

    var isRebuildingSyncedHighlightBounds by remember(currentBookId) { mutableStateOf(false) }
    LaunchedEffect(pdfDocument, currentBookId, userHighlightsSnapshot, sidecarsReadyForCurrentBook) {
        val document = pdfDocument ?: return@LaunchedEffect
        if (!sidecarsReadyForCurrentBook || isRebuildingSyncedHighlightBounds) return@LaunchedEffect
        val snapshot = userHighlightsSnapshot
        if (snapshot.none { it.bounds.isEmpty() && it.range.second > it.range.first }) return@LaunchedEffect

        isRebuildingSyncedHighlightBounds = true
        try {
            val rebuilt = rebuildMissingHighlightBounds(document, snapshot)
            if (rebuilt != snapshot) {
                userHighlights.clear()
                userHighlights.addAll(rebuilt)
            }
        } finally {
            isRebuildingSyncedHighlightBounds = false
        }
    }

    var pendingSaveMode by remember { mutableStateOf<SaveMode?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val saveMode = pendingSaveMode
        if (uri != null && saveMode != null) {
            when (saveMode) {
                SaveMode.ANNOTATED -> {
                    if (currentBookId != null) {
                        coroutineScope.launch {
                            val currentRichTextLayouts = richTextController?.pageLayouts

                            Timber.tag("PdfExportDebug").i("SAVE TRIGGERED: userHighlights count: ${visibleUserHighlights.size}")
                            if (visibleUserHighlights.isEmpty()) {
                                Timber.tag("PdfExportDebug").w("Warning: userHighlights is EMPTY during save.")
                            }

                            viewModel.savePdfWithAnnotations(
                                sourceUri = effectivePdfUri,
                                destUri = uri,
                                annotations = visibleAllAnnotations,
                                richTextPageLayouts = currentRichTextLayouts,
                                textBoxes = visibleTextBoxes,
                                highlights = visibleUserHighlights,
                                customHighlightColors = customHighlightColors,
                                bookId = currentBookId!!
                            )
                        }
                    }
                }

                SaveMode.ORIGINAL -> {
                    viewModel.saveOriginalPdf(effectivePdfUri, uri)
                }
            }
        }
        pendingSaveMode = null
    }

    var showShareDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var isShareLoading by remember { mutableStateOf(false) }
    val shouldShowAnnotationExportChoice = shouldShowPdfAnnotationExportChoice(
        sidecarsReady = sidecarsReadyForCurrentBook,
        annotations = visibleAllAnnotations,
        textBoxes = visibleTextBoxes,
        highlights = visibleUserHighlights
    )

    val launchOriginalSaveCopy: () -> Unit = {
        pendingSaveMode = SaveMode.ORIGINAL
        val suggestedName = getSuggestedFilename(
            originalFileName, isAnnotated = false
        )
        try {
            saveLauncher.launch(suggestedName)
        } catch (_: ActivityNotFoundException) {
            pendingSaveMode = null
            Toast.makeText(context, R.string.document_picker_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    val launchAnnotatedSaveCopy: () -> Unit = {
        pendingSaveMode = SaveMode.ANNOTATED
        val suggestedName = getSuggestedFilename(
            originalFileName, isAnnotated = true
        )
        try {
            saveLauncher.launch(suggestedName)
        } catch (_: ActivityNotFoundException) {
            pendingSaveMode = null
            Toast.makeText(context, R.string.document_picker_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    val shareOriginalPdf: () -> Unit = {
        isShareLoading = true
        val filename = getSuggestedFilename(
            originalFileName, isAnnotated = false
        )
        coroutineScope.launch {
            try {
                viewModel.sharePdf(
                    activityContext = context,
                    sourceUri = pdfUri,
                    annotations = emptyMap(),
                    includeAnnotations = false,
                    filename = filename
                )
            } finally {
                isShareLoading = false
            }
        }
    }

    val shareAnnotatedPdf: () -> Unit = {
        isShareLoading = true
        Timber.tag("PdfExportDebug").i("SHARE TRIGGERED: userHighlights count: ${visibleUserHighlights.size}")
        val filename = getSuggestedFilename(
            originalFileName, isAnnotated = true
        )
        coroutineScope.launch {
            try {
                val currentRichTextLayouts = richTextController?.pageLayouts

                viewModel.sharePdf(
                    activityContext = context,
                    sourceUri = effectivePdfUri,
                    annotations = visibleAllAnnotations,
                    richTextPageLayouts = currentRichTextLayouts,
                    textBoxes = visibleTextBoxes,
                    highlights = visibleUserHighlights,
                    customHighlightColors = customHighlightColors,
                    includeAnnotations = true,
                    filename = filename,
                    bookId = currentBookId
                )
            } finally {
                isShareLoading = false
            }
        }
    }

    val requestSaveCopy: () -> Unit = {
        if (ownsPaneGlobals) {
            if (shouldShowAnnotationExportChoice) {
                showSaveDialog = true
            } else {
                launchOriginalSaveCopy()
            }
        }
    }

    val requestShare: () -> Unit = {
        if (ownsPaneGlobals) {
            if (shouldShowAnnotationExportChoice) {
                showShareDialog = true
            } else {
                shareOriginalPdf()
            }
        }
    }

    var ocrUsedForCurrentPageTts by remember { mutableStateOf(false) }

    var showAiHubSheet by remember { mutableStateOf(false) }
    var summarizationResult by remember { mutableStateOf<SummarizationResult?>(null) }
    var isSummarizationLoading by remember { mutableStateOf(false) }

    var isPageSliderVisible by remember(bookId) {
        mutableStateOf(loadReaderSliderToggled(context, bookId))
    }
    var sliderStartPage by remember { mutableIntStateOf(0) }
    var sliderCurrentPage by remember { mutableFloatStateOf(0f) }
    var isFastScrubbing by remember { mutableStateOf(false) }
    val scrubDebounceJob = remember { mutableStateOf<Job?>(null) }
    val pdfSliderChromeVisible = shouldRenderReaderSlider(
        isToggledOn = isPageSliderVisible,
        isBottomChromeVisible = showStandardBars,
        isSearchActive = searchState.isSearchActive
    )

    LaunchedEffect(bookId, isPageSliderVisible) {
        saveReaderSliderToggled(context, bookId, isPageSliderVisible)
        if (isPageSliderVisible) {
            val position = readerSliderBookmarkPosition(currentPage)
            sliderStartPage = position.startPage
            sliderCurrentPage = position.currentPage
        }
    }

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
        if (ownsPaneGlobals) {
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
    }

    val onZoomChangeStable = remember { { scale: Float -> currentPageScale = scale } }

    val onHighlightLoadingStable = remember {
        { isLoading: Boolean -> isHighlightingLoading = isLoading }
    }

    val onShowDictionaryUpsellDialogStable = remember(useOnlineDictionary, ownsPaneGlobals) {
        {
            if (ownsPaneGlobals && useOnlineDictionary) {
                showDictionaryUpsellDialog = true
            }
        }
    }

    val onDictionaryLookupStable = remember(executeWithOcrCheck, useOnlineDictionary, selectedDictPackage, uiState.credits, isProUser, ownsPaneGlobals) {
        { text: String ->
            if (ownsPaneGlobals) {
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
    }

    val onTranslateTextStable = remember(selectedTranslatePackage, ownsPaneGlobals) {
        { text: String ->
            if (ownsPaneGlobals) {
                if (!selectedTranslatePackage.isNullOrEmpty()) {
                    ExternalDictionaryHelper.launchTranslate(context, selectedTranslatePackage!!, text)
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_select_translate_first), Toast.LENGTH_SHORT).show()
                    showDictionarySettingsSheet = true
                }
            }
        }
    }

    val onSearchTextStable = remember(selectedSearchPackage, ownsPaneGlobals) {
        { text: String ->
            if (ownsPaneGlobals) {
                if (!selectedSearchPackage.isNullOrEmpty()) {
                    ExternalDictionaryHelper.launchSearch(context, selectedSearchPackage!!, text)
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_select_search_first), Toast.LENGTH_SHORT).show()
                    showDictionarySettingsSheet = true
                }
            }
        }
    }

    val onLinkClickedStable = remember { { url: String -> clickedLinkUrl = url } }

    val onInternalLinkNavStable: (Int) -> Unit = { targetPage ->
        pdfPageToDisplayPage(targetPage)?.let { targetDisplayPage ->
            navigateToPdfPage(targetDisplayPage, PdfNavigationReason.INTERNAL_LINK, true)
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
            onUpdate(SummarizationResult(error = context.getString(R.string.pdf_error_blank_page_summary)))
            onFinish()
            return
        }

        val pdfPageIndex = (virtualPage as? VirtualPage.PdfPage)?.pdfIndex ?: currentPageIndex

        val doc = pdfDocument ?: run {
            onUpdate(SummarizationResult(error = context.getString(R.string.pdf_error_document_not_loaded)))
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
                                error = context.getString(R.string.ai_error_parse_summary)
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
                        context.getString(R.string.ai_error_fetch_summary)
                    }
                    onUpdate(
                        SummarizationResult(
                            error = context.getString(
                                R.string.ai_error_with_code,
                                responseCode,
                                errorDetail ?: context.getString(R.string.error_unknown_server)
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception during PDF page summarization: ${e.message}")
                onUpdate(SummarizationResult(error = context.getString(R.string.error_occurred_format, e.localizedMessage)))
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
        if (isSplitPane && !isPaneFocused) {
            return
        }
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
            // Live read: startTts can be reached through long-lived remembered callbacks
            // whose captured snapshot predates the document load.
            val requestedDisplayPage = pageToReadOverride ?: surfaceState.currentPage
            val pageToRead = displayPageToPdfPage(requestedDisplayPage)
            if (pageToRead == null) {
                Timber.tag(TTS_DIAG_TAG).w(
                    "startTts.reject requestedDisplayPage=$requestedDisplayPage " +
                        "currentPageSnapshot=$currentPage liveTotalDisplayPages=${surfaceState.totalDisplayPages} " +
                        "totalPdfPages=$totalPages virtual=${virtualPages.pdfLayoutDebugSummary()}"
                )
                Timber.w("TTS: Ignoring blank or invalid display page $requestedDisplayPage.")
                return@launch
            }
            val displayPageForTts = pdfPageToDisplayPage(pageToRead) ?: requestedDisplayPage
            Timber.tag(TTS_DIAG_TAG).i(
                "startTts.mapped displayPage=$requestedDisplayPage pdfPage=$pageToRead " +
                    "totalDisplayPages=${surfaceState.totalDisplayPages}"
            )
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
                    val mappedIndex = processedText.indexMap.indexOfFirst { it >= startCharIndex }
                    if (mappedIndex >= 0) mappedIndex else processedText.cleanText.lastIndex.coerceAtLeast(0)
                } else {
                    0
                }

                val chunks = splitTextIntoChunks(processedText.cleanText)
                val chunkStartOffsets = mutableListOf<Int>()
                var searchIndex = 0
                chunks.forEach { chunk ->
                    val foundIndex = processedText.cleanText.indexOf(chunk, searchIndex)
                        .takeIf { it >= 0 }
                        ?: searchIndex
                    chunkStartOffsets.add(foundIndex)
                    searchIndex = foundIndex + chunk.length
                }
                var startChunkIndex = 0
                for (index in chunks.indices) {
                    val chunkStart = chunkStartOffsets.getOrNull(index) ?: 0
                    val chunkEnd = chunkStart + chunks[index].length
                    if (cleanStartIndex >= chunkStart && cleanStartIndex < chunkEnd) {
                        startChunkIndex = index
                        break
                    }
                    if (cleanStartIndex < chunkStart) {
                        startChunkIndex = index
                        break
                    }
                }

                val bookTitle = (pdfDocument as? PdfDocumentWrapper)?.pdfDocument?.getDocumentMeta()?.title?.takeIf { it.isNotBlank() }
                    ?: effectivePdfUri.lastPathSegment ?: context.getString(R.string.default_document_title)
                val pageTitle = context.getString(R.string.pdf_page_short, pageToRead + 1)

                val ttsChunks = chunks.mapIndexed { index, text ->
                    val chunkStart = chunkStartOffsets.getOrNull(index) ?: 0
                    val textForChunk = if (index == startChunkIndex && cleanStartIndex > chunkStart) {
                        text.substring((cleanStartIndex - chunkStart).coerceIn(0, text.length))
                    } else {
                        text
                    }
                    TtsChunk(textForChunk, "", index)
                }

                ttsController.start(
                    chunks = ttsChunks.withTtsReplacements(ttsReplacementPreferences, bookId),
                    bookTitle = bookTitle,
                    chapterTitle = pageTitle,
                    coverImageUri = null,
                    bookId = bookId,
                    pageIndex = displayPageForTts,
                    startChunkIndex = startChunkIndex,
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
                    ocrAttempted -> context.getString(R.string.error_no_text_on_page_after_ocr)
                    else -> context.getString(R.string.error_page_text_not_extractable)
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

    val startTtsWithPermissionCheck: (Int?, Int?) -> Unit = remember(
        context,
        activity,
        executeWithOcrCheck,
        ownsPaneGlobals,
    ) {
        { pageOverride, startCharIndex ->
            if (ownsPaneGlobals) executeWithOcrCheck {
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
            if (!isSplitPane) {
                PdfBitmapPool.clear()
                PdfThumbnailCache.clear()
            }
        }
    }

    LaunchedEffect(ttsState.sessionFinished, isTtsPlaybackForThisPane, isPaneFocused) {
        if (
            isSplitPane && (!isPaneFocused || !isTtsPlaybackForThisPane)
        ) return@LaunchedEffect
        if (ttsState.sessionFinished && ttsState.playbackSource == "READER" && isTtsPlaybackForThisPane) {
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

    LaunchedEffect(isPageSliderVisible, pdfSliderChromeVisible, currentPage) {
        if (isPageSliderVisible && !pdfSliderChromeVisible) {
            val position = readerSliderBookmarkPosition(currentPage)
            sliderStartPage = position.startPage
            sliderCurrentPage = position.currentPage
        }
    }

    LaunchedEffect(ttsState.currentText, ttsPageData, ttsState.startOffsetInSource, isTtsPlaybackForThisPane) {
        if (!isTtsPlaybackForThisPane) {
            ttsHighlightData = null
            return@LaunchedEffect
        }
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
        Timber.tag(PDF_RENAME_TRACE_TAG).i(
            "pdfScreen.openEffect.start uri=$effectivePdfUri selectedBookId=$selectedBookIdForPane " +
                "activeTabBookId=$activeTabBookId currentBookId=$currentBookId " +
                "activeItemId=${activeLibraryItem?.bookId} customName=${activeLibraryItem?.customName} " +
                "displayName=${activeLibraryItem?.displayName} title=${activeLibraryItem?.title}"
        )
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
            "ui.open.start uri=$effectivePdfUri scheme=${effectivePdfUri.scheme} " +
                "selectedBookId=$selectedBookIdForPane previousBookId=$currentBookId " +
                "documentPasswordSet=${documentPassword != null}"
        )

        Timber.tag("PdfTabSync").d("UI: Loading State -> activeTabBookId: $activeTabBookId, isLoading: $isLoadingDocument")

        bookmarks = loadPdfBookmarksFromJson(effectiveInitialBookmarksJson)

        isLoadingDocument = true
        isDocumentReady = false
        errorMessage = null
        documentMetadataTitle = null
        isPrintBlockedForPasswordProtectedPdf = false
        currentBookId = null
        annotationSession = annotationSession.reduce(SharedPdfAnnotationSessionAction.Reset)
        allAnnotations = emptyMap()
        textBoxes.clear()
        userHighlights.clear()
        virtualPages = emptyList()
        loadedPageLayoutBookId = null
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
            "ui.open.reset uri=$effectivePdfUri virtualCleared=true loadedLayoutBookId=$loadedPageLayoutBookId"
        )
        selectedTextBoxId = null
        undoStack.clear()
        redoStack.clear()
        erasedAnnotationsFromStroke.clear()
        drawingState.onDrawCancel()

        if (showPasswordDialog) isPasswordError = false

        ocrUsedForCurrentPageTts = false
        flatTableOfContents = emptyList()

        val fastId = getFastFileId(context, effectivePdfUri)
        val selectedId = selectedBookIdForPane
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
            "ui.open.ids uri=$effectivePdfUri fastId=$fastId selectedId=$selectedId activeTabBookId=$activeTabBookId"
        )
        val shouldPreserveCurrentTtsSession =
            uiState.isOpeningFromTtsNotification ||
                (
                    ttsState.playbackSource == "READER" &&
                        !ttsState.bookId.isNullOrBlank() &&
                        ttsState.bookId == selectedId
                    )

        if (!isSplitPane && !shouldPreserveCurrentTtsSession) {
            ttsController.stop()
        }

        if (selectedId != null && selectedId != fastId) {
            Timber.tag("FolderAnnotationSync").i("Detected ID mismatch. Legacy: $fastId, Selected: $selectedId. Initiating migration.")
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                "ui.open.migrateFastToSelected legacyId=$fastId selectedId=$selectedId"
            )
            viewModel.checkAndMigrateLegacyBookId(fastId, selectedId)
            currentBookId = selectedId
        } else {
            currentBookId = fastId
        }
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
            "ui.open.activeId uri=$effectivePdfUri currentBookId=$currentBookId"
        )

        val activeBookIdForLoad = currentBookId!!
        val rawUriBookId = effectivePdfUri.toString()
        if (rawUriBookId != activeBookIdForLoad) {
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                "ui.open.migrateRawUri legacyId=$rawUriBookId activeId=$activeBookIdForLoad"
            )
            viewModel.checkAndMigrateLegacyBookId(rawUriBookId, activeBookIdForLoad)
        }

        val cachedItem = documentCache.get(activeBookIdForLoad)
        if (cachedItem != null) {
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                "ui.open.cacheHit bookId=$activeBookIdForLoad cachedTotalPages=${cachedItem.totalPages}"
            )
            Timber.tag(PDF_RENAME_TRACE_TAG).i(
                "pdfScreen.openEffect.cacheHit bookId=$activeBookIdForLoad " +
                    "customName=${activeLibraryItem?.customName} documentMetadataTitle=$documentMetadataTitle " +
                    "effectiveReaderBookTitle=$effectiveReaderBookTitle"
            )
            Timber.tag("PdfTabSync").i("UI: Restoring from cache for $currentBookId")
            pdfDocument = cachedItem.doc
            pfdState = cachedItem.pfd
            totalPages = cachedItem.totalPages
            pageAspectRatios = cachedItem.pageAspectRatios
            flatTableOfContents = cachedItem.flatTableOfContents
            isPrintBlockedForPasswordProtectedPdf = cachedItem.isPasswordProtectedPdf

            val mapPage = tabStateMap[currentBookId!!]
            val uiPage = if (isSplitPane) paneInitialPage else uiState.initialPageInBook
            val restorePage = if (!isSplitPane && uiState.initialPageInBookIsExplicit) {
                uiPage ?: mapPage ?: effectiveInitialPage
            } else {
                mapPage ?: uiPage ?: effectiveInitialPage
            }
            Timber.tag("PdfTabSync").d("UI: Restoring position | tabStateMap=$mapPage, uiState=$uiPage, initialPage=$effectiveInitialPage")

            pendingRestorePage = restorePage
            initialScrollDone = false
            isDocumentReady = true
            isLoadingDocument = false
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                "ui.open.cacheReady bookId=$activeBookIdForLoad totalPdfPages=$totalPages " +
                    "isReady=$isDocumentReady virtual=${virtualPages.pdfLayoutDebugSummary()}"
            )
            return@LaunchedEffect
        }
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i("ui.open.cacheMiss bookId=$activeBookIdForLoad")

        val mapPageInit = tabStateMap[currentBookId!!]
        val uiPageInit = if (isSplitPane) paneInitialPage else uiState.initialPageInBook
        val restorePageInit = if (!isSplitPane && uiState.initialPageInBookIsExplicit) {
            uiPageInit ?: mapPageInit ?: effectiveInitialPage
        } else {
            mapPageInit ?: uiPageInit ?: effectiveInitialPage
        }
        Timber.tag("PdfTabSync").d("UI: Initial position | tabStateMap=$mapPageInit, uiState=$uiPageInit, initialPage=$effectiveInitialPage")
        pendingRestorePage = restorePageInit
        initialScrollDone = false

        pdfDocument = null
        pfdState = null
        totalPages = 0

        try {
            withContext(Dispatchers.IO) {
                Timber.tag("PdfTabSync").v("UI: Opening document for $effectivePdfUri")

                val selectedDocumentType = effectiveFileType
                val doc = DocumentFactory.loadDocument(context, effectivePdfUri, selectedDocumentType, documentPassword, pdfiumCore)
                val loadedPasswordProtectedPdf = selectedDocumentType == FileType.PDF &&
                    (documentPassword != null || isPdfLikelyEncryptedForPrint(context, effectivePdfUri))

                if (!isActive) {
                    doc.close()
                    return@withContext
                }

                pdfDocument = doc
                isPrintBlockedForPasswordProtectedPdf = loadedPasswordProtectedPdf
                documentMetadataTitle = (doc as? PdfDocumentWrapper)?.let { wrapper ->
                    PdfiumEngineProvider.withPdfium {
                        wrapper.pdfDocument.getDocumentMeta().title?.takeIf { it.isNotBlank() }
                    }
                }
                Timber.tag(PDF_RENAME_TRACE_TAG).i(
                    "pdfScreen.openEffect.metadataLoaded bookId=$currentBookId uri=$effectivePdfUri " +
                        "documentMetadataTitle=$documentMetadataTitle activeCustomName=${activeLibraryItem?.customName} " +
                        "activeDisplayName=${activeLibraryItem?.displayName} activeTitle=${activeLibraryItem?.title}"
                )
                pfdState = null
                val pagesCount = doc.getPageCount()
                Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                    "ui.open.documentLoaded bookId=$currentBookId uri=$effectivePdfUri pagesCount=$pagesCount " +
                        "docType=$selectedDocumentType"
                )

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
                Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                    "ui.open.totalPagesSet bookId=$currentBookId totalPdfPages=$totalPages"
                )

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

                        computedRatios
                    }

                    pageAspectRatios = ratios
                    isDocumentReady = true
                    isLoadingDocument = false
                    Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                        "ui.open.ready bookId=$currentBookId totalPdfPages=$totalPages " +
                            "isReady=$isDocumentReady virtual=${virtualPages.pdfLayoutDebugSummary()} " +
                            "loadedLayoutBookId=$loadedPageLayoutBookId"
                    )

                    documentCache.put(
                        currentBookId!!,
                        DocumentCacheItem(
                            doc = doc,
                            pfd = null,
                            totalPages = pagesCount,
                            pageAspectRatios = ratios,
                            flatTableOfContents = flatTableOfContents,
                            isPasswordProtectedPdf = loadedPasswordProtectedPdf
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

                            } catch (e: Exception) {
                                Timber.w(e, "Failed to calculate ratio for page $i")
                            }
                        }

                        // Publish document geometry once. Incremental replacements alter the
                        // height of every following page and move content under an active scroll.
                        if (hasChanges && isActive) {
                            withContext(Dispatchers.Main) {
                                pageAspectRatios = ArrayList(refinedRatios)
                            }
                            pdfTextRepository.savePageRatios(currentBookId!!, refinedRatios)
                        }
                    }
                } else {
                    isDocumentReady = true
                    isLoadingDocument = false
                    Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).w(
                        "ui.open.readyZeroPages bookId=$currentBookId totalPdfPages=$totalPages"
                    )
                }

                Timber.tag("PdfTabSync").v("UI: Pdfium Document created. Page count: $pagesCount")
            }
        } catch (e: Throwable) {
            if (e is CancellationException || e.javaClass.name.contains("CancellationException")) throw e
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).e(
                e,
                "ui.open.failed uri=$effectivePdfUri currentBookId=$currentBookId totalPdfPages=$totalPages"
            )
            Timber.tag("PdfTabSync").e(e, "UI: Error in load effect for $effectivePdfUri")
            val errorString = e.toString()
            val causeString = e.cause?.toString() ?: ""

            if (errorString.contains("PasswordException") || causeString.contains("PasswordException")) {
                Timber.w("PDF is password protected or password incorrect.")
                withContext(Dispatchers.Main) {
                    if (documentPassword != null) {
                        isPasswordError = true
                    }
                    // The dialog is rendered only by the focused/active pane below. Keep
                    // this state pending so a pane that loses focus during loading can
                    // present the prompt when it becomes the owner again.
                    showPasswordDialog = true
                    isLoadingDocument = false
                }
            } else {
                Timber.e(e, "Error loading fixed-layout document")
                errorMessage = context.getString(R.string.error_loading_document_format, e.localizedMessage)
                isLoadingDocument = false
            }
            if (pdfDocument == null) {
                pfdState = null
            }
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress, isTtsPlaybackForThisPane, isPaneFocused) {
        if (pagerState.isScrollInProgress) {
            if (
                (!isSplitPane || isPaneFocused) &&
                isTtsPlaybackForThisPane &&
                displayMode == DisplayMode.PAGINATION &&
                !isAutoPagingForTts &&
                (ttsState.isPlaying || ttsState.isLoading)
            ) {
                ttsController.stop()
            }
        }
    }

    var previousPage by remember(displayMode) { mutableIntStateOf(-1) }
    LaunchedEffect(currentPage, isTtsPlaybackForThisPane, isPaneFocused) {
        if (previousPage != -1 && previousPage != currentPage) {
            if (isAutoPagingForTts && isTtsPlaybackForThisPane && (!isSplitPane || isPaneFocused)) {
                startTts(continueSession = true)
            } else if (
                isTtsPlaybackForThisPane &&
                (!isSplitPane || isPaneFocused) &&
                displayMode == DisplayMode.PAGINATION &&
                (ttsState.isPlaying || ttsState.isLoading)
            ) {
                Timber.d("Page changed manually while TTS active, stopping.")
                ttsController.stop()
            }
        }
        previousPage = currentPage
        summarizationResult = null
    }

    LaunchedEffect(
        currentPage,
        displayMode,
        isScrollLocked,
        lockedState,
        totalDisplayPages,
        pdfSpreadSettings.pageSpreadMode,
        pdfSpreadSettings.pdfFirstPageStandaloneInSpread
    ) {
        val nextPageScale = currentPageScaleAfterPdfPageChange(
            displayMode = displayMode,
            isScrollLocked = isScrollLocked,
            lockedState = lockedState,
            currentActiveScale = currentActiveScale
        )
        currentPageScale = nextPageScale
        val isCurrentTwoPageSpread =
            displayMode == DisplayMode.PAGINATION &&
                PdfSpreadLayout.visiblePageIndices(currentPage, totalDisplayPages, pdfSpreadSettings).size > 1
        if (isCurrentTwoPageSpread) {
            val currentLockedState = lockedState
            val nextPageOffset = if (isScrollLocked && currentLockedState != null) {
                Offset(currentLockedState.second, currentLockedState.third)
            } else {
                Offset.Zero
            }
            currentActiveScale = nextPageScale
            currentActiveOffset = nextPageOffset
        } else if (displayMode == DisplayMode.PAGINATION && !isScrollLocked) {
            currentActiveScale = 1f
            currentActiveOffset = Offset.Zero
        }
        ocrUsedForCurrentPageTts = false
    }

    LaunchedEffect(resetZoomTrigger) {
        if (
            resetZoomTrigger != 0L &&
            displayMode == DisplayMode.PAGINATION &&
            PdfSpreadLayout.visiblePageIndices(currentPage, totalDisplayPages, pdfSpreadSettings).size > 1 &&
            currentActiveScale > 1f &&
            !isScrollLocked
        ) {
            val startScale = currentActiveScale
            val startOffset = currentActiveOffset
            Animatable(0f).animateTo(1f, animationSpec = tween(durationMillis = 300)) {
                currentActiveScale = androidx.compose.ui.util.lerp(startScale, 1f, value)
                currentActiveOffset = lerp(startOffset, Offset.Zero, value)
                currentPageScale = currentActiveScale
            }
            currentActiveScale = 1f
            currentActiveOffset = Offset.Zero
            currentPageScale = 1f
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Timber.d("DisposableEffect: Screen disposing. Closing PDF document and PFD.")
            if (isSplitPane) {
                // Header close/replacement removes the composition without
                // dispatching an Android lifecycle event. Flush this pane's
                // latest session before releasing its renderer resources.
                viewModel.viewModelScope.launch {
                    currentRichTextController?.let {
                        withContext(NonCancellable) { it.saveImmediate() }
                    }
                    saveAllData(true).join()
                }
            }
            if (!isSplitPane) {
                ttsController.stop()
                PdfBitmapPool.clear()
                PdfThumbnailCache.clear()
            }
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
        val selectedDocumentType = effectiveFileType
        if (selectedDocumentType != FileType.PDF && selectedDocumentType != FileType.PPTX) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val storedLang = pdfTextRepository.getBookLanguage(currentBookId!!)
            if (storedLang == null) {
                pdfTextRepository.setBookLanguage(currentBookId!!, ocrLanguage.name)
            }

            isBackgroundIndexing = true
            var bgPfd: ParcelFileDescriptor? = null
            var bgDoc: PdfDocumentKt? = null
            var genericDoc: ReaderDocument? = null

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

                val pagesToIndex = (0 until totalPages).filter { !existingPages.contains(it) }
                val totalToDo = pagesToIndex.size
                var completed = 0

                if (selectedDocumentType == FileType.PDF) {
                    bgPfd = context.contentResolver.openFileDescriptor(effectivePdfUri, "r")
                    val openedBgPfd = bgPfd
                    if (openedBgPfd == null) return@withContext
                    bgDoc = PdfiumEngineProvider.withPdfium {
                        pdfiumCore.newDocument(openedBgPfd, documentPassword)
                    }

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
                } else {
                    val openedGenericDoc = DocumentFactory.loadDocument(
                        context = context,
                        uri = effectivePdfUri,
                        type = selectedDocumentType,
                        password = null,
                        pdfiumCore = pdfiumCore
                    )
                    genericDoc = openedGenericDoc

                    for (pageIndex in pagesToIndex) {
                        if (!isActive) break

                        try {
                            pdfTextRepository.indexReaderPage(
                                bookId = currentBookId!!,
                                document = openedGenericDoc,
                                pageIndex = pageIndex,
                                onOcrModelDownloading = { isOcrModelDownloading = true }
                            )
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
                    genericDoc?.close()
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
                            locationTitle = context.getString(R.string.pdf_page_short, match.pageIndex + 1),
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
                            locationTitle = context.getString(R.string.pdf_page_short, match.pageIndex + 1),
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
        ((ttsState.currentText != null || ttsState.isLoading) &&
            ttsState.playbackSource == "READER" &&
            isTtsPlaybackForThisPane) ||
            (isAutoPagingForTts && isTtsPlaybackForThisPane)
    val isTtsPlayingOrLoading = isTtsPlaybackForThisPane &&
        (ttsState.isPlaying || ttsState.isLoading || isAutoPagingForTts)

    val onInternalLinkNav: (Int) -> Unit = { targetPage ->
        pdfPageToDisplayPage(targetPage)?.let { targetDisplayPage ->
            navigateToPdfPage(targetDisplayPage, PdfNavigationReason.INTERNAL_LINK, true)
        }
    }

    val paginationDraggingOriginPage = remember(paginationDraggingBoxId, textBoxes) {
        if (paginationDraggingBoxId == null) null
        else textBoxes.find { it.id == paginationDraggingBoxId }?.pageIndex
    }

    val dynamicBeyondViewportPageCount = remember(
        paginationDraggingOriginPage,
        currentPaginationDisplayPage()
    ) {
        if (paginationDraggingOriginPage != null) {
            val originPagerPage = paginationPagerPageForDisplayPage(paginationDraggingOriginPage)
            val distance = abs(pagerState.currentPage - originPagerPage)
            (distance + 1).coerceAtLeast(1)
        } else {
            1
        }
    }

    fun navigateToPdfSearchResult(result: SearchResult) {
        currentPdfSearchResult = result
        searchHighlightTarget = result
        pdfPageToDisplayPage(result.locationInSource)?.let { targetDisplayPage ->
            navigateToPdfPage(targetDisplayPage, PdfNavigationReason.SEARCH_RESULT, true)
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

    // A split workspace delegates back to the focused pane first so its
    // dialogs/search/editor state close before the workspace itself exits.
    if (ownsPaneGlobals) BackHandler(enabled = true) {
        val backAction = selectMobilePdfReaderBackAction(
            MobilePdfReaderBackState(
                passwordPromptVisible = showPasswordDialog,
                visualOptionsVisible = showVisualOptionsSheet,
                reindexDialogVisible = showReindexDialog != null,
                autoScrollActive = isAutoScrollModeActive,
                drawerOpen = drawerState.isOpen,
                richTextEditing = isEditMode,
                aiHubVisible = showAiHubSheet,
                permissionRationaleVisible = showPermissionRationaleDialog,
                summarizationUpsellVisible = showSummarizationUpsellDialog,
                aiDefinitionVisible = showAiDefinitionPopup,
                dictionaryUpsellVisible = showDictionaryUpsellDialog,
                toolCustomizationVisible = showCustomizeToolsSheet,
                searchActive = searchState.isSearchActive,
                ttsSettingsVisible = showTtsSettingsSheet,
                ttsReplacementsVisible = showTtsReplacementsSheet,
                themePanelVisible = showThemePanel,
            )
        )
        when (backAction) {
            MobilePdfReaderBackAction.EXIT_PASSWORD_PROMPT -> onNavigateBack()
            MobilePdfReaderBackAction.CLOSE_VISUAL_OPTIONS -> showVisualOptionsSheet = false
            MobilePdfReaderBackAction.CLOSE_REINDEX_DIALOG -> showReindexDialog = null
            MobilePdfReaderBackAction.STOP_AUTO_SCROLL -> {
                isAutoScrollModeActive = false
                isAutoScrollPlaying = false
                showBars = true
            }
            MobilePdfReaderBackAction.CLOSE_DRAWER -> coroutineScope.launch { drawerState.close() }
            MobilePdfReaderBackAction.STOP_RICH_TEXT_EDITING -> {
                richTextController?.clearSelection()
                isEditMode = false
                showBars = true
            }
            MobilePdfReaderBackAction.CLOSE_AI_HUB -> showAiHubSheet = false
            MobilePdfReaderBackAction.CLOSE_PERMISSION_RATIONALE -> showPermissionRationaleDialog = false
            MobilePdfReaderBackAction.CLOSE_SUMMARIZATION_UPSELL -> showSummarizationUpsellDialog = false
            MobilePdfReaderBackAction.CLOSE_AI_DEFINITION -> showAiDefinitionPopup = false
            MobilePdfReaderBackAction.CLOSE_DICTIONARY_UPSELL -> showDictionaryUpsellDialog = false
            MobilePdfReaderBackAction.CLOSE_TOOL_CUSTOMIZATION -> showCustomizeToolsSheet = false
            MobilePdfReaderBackAction.CLOSE_SEARCH -> {
                searchState.isSearchActive = false
                searchState.onQueryChange("")
            }
            MobilePdfReaderBackAction.CLOSE_TTS_SETTINGS -> showTtsSettingsSheet = false
            MobilePdfReaderBackAction.CLOSE_TTS_REPLACEMENTS -> showTtsReplacementsSheet = false
            MobilePdfReaderBackAction.CLOSE_THEME_PANEL -> showThemePanel = false
            MobilePdfReaderBackAction.SAVE_AND_EXIT -> saveStateAndExit()
        }
    }

    // Keep the typed surface writes in bounded groups. A single Compose lambda
    // over all local state produces a verifier-sensitive generated constructor;
    // these groups keep each capture set small while preserving the pane-local
    // state accessors used by the extracted chrome and document composables.
    bindPdfViewerSurfaceChunk {
        surfaceState.isPageSliderVisible = pdfViewerMutableValue({ isPageSliderVisible }, { isPageSliderVisible = it })
        surfaceState.pendingRestorePage = pdfViewerMutableValue({ pendingRestorePage }, { pendingRestorePage = it })
        surfaceState.isLoadingDocument = pdfViewerMutableValue({ isLoadingDocument }, { isLoadingDocument = it })
        surfaceState.displayMode = pdfViewerMutableValue({ displayMode }, { displayMode = it })
        surfaceState.tapToNavigateEnabled = pdfViewerMutableValue({ tapToNavigateEnabled }, { tapToNavigateEnabled = it })
        surfaceState.pageTurnAnimationEnabled = pdfViewerMutableValue({ pageTurnAnimationEnabled }, { pageTurnAnimationEnabled = it })
        surfaceState.isScrollLocked = pdfViewerMutableValue({ isScrollLocked }, { isScrollLocked = it })
        surfaceState.rightToLeftPagination = pdfViewerMutableValue({ rightToLeftPagination }, { rightToLeftPagination = it })
        surfaceState.currentActiveScale = pdfViewerMutableValue({ currentActiveScale }, { currentActiveScale = it })
        surfaceState.currentActiveOffset = pdfViewerMutableValue({ currentActiveOffset }, { currentActiveOffset = it })
        surfaceState.showVerticalPageGap = pdfViewerMutableValue({ showVerticalPageGap }, { showVerticalPageGap = it })
        surfaceState.searchHighlightTarget = pdfViewerMutableValue({ searchHighlightTarget }, { searchHighlightTarget = it })
        surfaceState.isOcrModelDownloading = pdfViewerMutableValue({ isOcrModelDownloading }, { isOcrModelDownloading = it })
        surfaceState.pageAspectRatios = pdfViewerMutableValue({ pageAspectRatios }, { pageAspectRatios = it })
        surfaceState.globalTextureTransparency = pdfViewerMutableValue({ globalTextureTransparency }, { globalTextureTransparency = it })
        surfaceState.excludeImages = pdfViewerMutableValue({ excludeImages }, { excludeImages = it })
        surfaceState.reverseColorMode = pdfViewerMutableValue({ reverseColorMode }, { reverseColorMode = it })
        surfaceState.ttsDisplayPageIndex = pdfViewerMutableValue({ ttsDisplayPageIndex }, { ttsDisplayPageIndex = it })
        surfaceState.ttsHighlightData = pdfViewerMutableValue({ ttsHighlightData }, { ttsHighlightData = it })

    }

    bindPdfViewerSurfaceChunk {
        surfaceState.searchHighlightMode = pdfViewerMutableValue({ searchHighlightMode }, { searchHighlightMode = it })
        surfaceState.showAllTextHighlights = pdfViewerMutableValue({ showAllTextHighlights }, { showAllTextHighlights = it })
        surfaceState.showPageNumberOverlay = pdfViewerMutableValue({ showPageNumberOverlay }, { showPageNumberOverlay = it })
        surfaceState.selectionClearTrigger = pdfViewerMutableValue({ selectionClearTrigger }, { selectionClearTrigger = it })
        surfaceState.resetZoomTrigger = pdfViewerMutableValue({ resetZoomTrigger }, { resetZoomTrigger = it })
        surfaceState.lockedState = pdfViewerMutableValue({ lockedState }, { lockedState = it })
        surfaceState.showDictionaryUpsellDialog = pdfViewerMutableValue({ showDictionaryUpsellDialog }, { showDictionaryUpsellDialog = it })
        surfaceState.clickedLinkUrl = pdfViewerMutableValue({ clickedLinkUrl }, { clickedLinkUrl = it })
        surfaceState.poppedUpPanelBitmap = pdfViewerMutableValue({ poppedUpPanelBitmap }, { poppedUpPanelBitmap = it })
        surfaceState.ocrLanguage = pdfViewerMutableValue({ ocrLanguage }, { ocrLanguage = it })
        surfaceState.systemUiMode = pdfViewerMutableValue({ systemUiMode }, { systemUiMode = it })
        surfaceState.sliderCurrentPage = pdfViewerMutableValue({ sliderCurrentPage }, { sliderCurrentPage = it })
        surfaceState.isFastScrubbing = pdfViewerMutableValue({ isFastScrubbing }, { isFastScrubbing = it })
        surfaceState.showThemePanel = pdfViewerMutableValue({ showThemePanel }, { showThemePanel = it })
        surfaceState.showVisualOptionsSheet = pdfViewerMutableValue({ showVisualOptionsSheet }, { showVisualOptionsSheet = it })
        surfaceState.showScreenOrientationSheet = pdfViewerMutableValue({ showScreenOrientationSheet }, { showScreenOrientationSheet = it })
        surfaceState.showSummarizationUpsellDialog = pdfViewerMutableValue({ showSummarizationUpsellDialog }, { showSummarizationUpsellDialog = it })
        surfaceState.showAiHubSheet = pdfViewerMutableValue({ showAiHubSheet }, { showAiHubSheet = it })
        surfaceState.showPermissionRationaleDialog = pdfViewerMutableValue({ showPermissionRationaleDialog }, { showPermissionRationaleDialog = it })
        surfaceState.showInsufficientCreditsDialog = pdfViewerMutableValue({ showInsufficientCreditsDialog }, { showInsufficientCreditsDialog = it })

    }

    bindPdfViewerSurfaceChunk {
        surfaceState.showSaveDialog = pdfViewerMutableValue({ showSaveDialog }, { showSaveDialog = it })
        surfaceState.showShareDialog = pdfViewerMutableValue({ showShareDialog }, { showShareDialog = it })
        surfaceState.showOcrLanguageDialog = pdfViewerMutableValue({ showOcrLanguageDialog }, { showOcrLanguageDialog = it })
        surfaceState.showReindexDialog = pdfViewerMutableValue({ showReindexDialog }, { showReindexDialog = it })
        surfaceState.showCustomizeToolsSheet = pdfViewerMutableValue({ showCustomizeToolsSheet }, { showCustomizeToolsSheet = it })
        surfaceState.pendingActionAfterOcrSelection = pdfViewerMutableValue({ pendingActionAfterOcrSelection }, { pendingActionAfterOcrSelection = it })
        surfaceState.pendingSaveMode = pdfViewerMutableValue({ pendingSaveMode }, { pendingSaveMode = it })
        surfaceState.showBars = pdfViewerMutableValue({ showBars }, { showBars = it })
        surfaceState.sliderStartPage = pdfViewerMutableValue({ sliderStartPage }, { sliderStartPage = it })
        surfaceState.isHighlightingLoading = pdfViewerMutableValue({ isHighlightingLoading }, { isHighlightingLoading = it })
        surfaceState.isAutoPagingForTts = pdfViewerMutableValue({ isAutoPagingForTts }, { isAutoPagingForTts = it })
        surfaceState.hasSelectedOcrLanguage = pdfViewerMutableValue({ hasSelectedOcrLanguage }, { hasSelectedOcrLanguage = it })
        surfaceState.isBackgroundIndexing = pdfViewerMutableValue({ isBackgroundIndexing }, { isBackgroundIndexing = it })
        surfaceState.backgroundIndexingProgress = pdfViewerMutableValue({ backgroundIndexingProgress }, { backgroundIndexingProgress = it })
        surfaceState.isOcrScanning = pdfViewerMutableValue({ isOcrScanning }, { isOcrScanning = it })
        surfaceState.smartSearchResult = pdfViewerMutableValue({ smartSearchResult }, { smartSearchResult = it })
        surfaceState.currentPdfSearchResult = pdfViewerMutableValue({ currentPdfSearchResult }, { currentPdfSearchResult = it })
    }

    bindPdfViewerSurfaceChunk {
        surfaceState.activeTheme.value = activeTheme
        surfaceState.pdfSliderChromeVisible = pdfSliderChromeVisible
        surfaceState.ownsPaneGlobals = ownsPaneGlobals
        surfaceState.drawerState = drawerState
        surfaceState.ttsState = ttsState
        surfaceState.ttsController = ttsController
        surfaceState.isTtsPlayingOrLoading = isTtsPlayingOrLoading
        surfaceState.context = context
        surfaceState.coroutineScope = coroutineScope
        surfaceState.executeWithOcrCheck = executeWithOcrCheck
        surfaceState.keyboardController = keyboardController
        surfaceState.isTtsSessionActive = isTtsSessionActive
        surfaceState.startTtsWithPermissionCheck = startTtsWithPermissionCheck
        surfaceState.isSplitPane = isSplitPane
        surfaceState.onOpenSplit = onOpenSplit
        surfaceState.focusRequester = focusRequester
        surfaceState.focusManager = focusManager
        surfaceState.hiddenTools = hiddenTools
        surfaceState.toolOrder = toolOrder
        surfaceState.bottomTools = bottomTools
        surfaceState.saveStateAndExit = saveStateAndExit
        surfaceState.viewModel = viewModel
        surfaceState.onBookmarkClick = onBookmarkClick
        surfaceState.onInsertPage = onInsertPage
        surfaceState.onDeletePage = onDeletePage
        surfaceState.saveAllData = saveAllData
        surfaceState.uiState = uiState
        surfaceState.requestShare = requestShare
        surfaceState.requestSaveCopy = requestSaveCopy
        surfaceState.onNavigateBack = onNavigateBack
        surfaceState.calculateSnappedPoint = calculateSnappedPoint
        surfaceState.dynamicBeyondViewportPageCount = dynamicBeyondViewportPageCount
        surfaceState.visibleUserHighlightsByPage = visibleUserHighlightsByPage
        surfaceState.isProUser = isProUser
        surfaceState.onDictionaryLookupStable = onDictionaryLookupStable
        surfaceState.onTranslateTextStable = onTranslateTextStable
    }

    bindPdfViewerSurfaceChunk {
        surfaceState.onSearchTextStable = onSearchTextStable
        surfaceState.onInternalLinkNav = onInternalLinkNav
        surfaceState.onToggleBookmark = onToggleBookmark
        surfaceState.persistInkAnnotationsNow = persistInkAnnotationsNow
        surfaceState.displayPageRatios = displayPageRatios
        surfaceState.onHighlightAdd = onHighlightAdd
        surfaceState.onHighlightUpdate = onHighlightUpdate
        surfaceState.onHighlightDelete = onHighlightDelete
        surfaceState.allAnnotationsProvider = allAnnotationsProvider
        surfaceState.isPdfDarkMode = isPdfDarkMode
        surfaceState.onZoomChangeStable = onZoomChangeStable
        surfaceState.onHighlightLoadingStable = onHighlightLoadingStable
        surfaceState.onShowDictionaryUpsellDialogStable = onShowDictionaryUpsellDialogStable
        surfaceState.onLinkClickedStable = onLinkClickedStable
        surfaceState.onInternalLinkNavStable = onInternalLinkNavStable
        surfaceState.onBookmarkClickStable = onBookmarkClickStable
        surfaceState.onOcrStateChangeStable = onOcrStateChangeStable
        surfaceState.onGetOcrSearchRectsStable = onGetOcrSearchRectsStable
        surfaceState.textBoxSurfaceState.data.value = PdfViewerTextBoxSurfaceData(
            all = visibleTextBoxes,
            byPage = visibleTextBoxesByPage,
        )
        surfaceState.bottomScrollLimitPx.value = bottomScrollLimitPx
        surfaceState.topScrollLimitPx.value = topScrollLimitPx
        surfaceState.visibleUserHighlights = visibleUserHighlights
        surfaceState.bubbleZoomDownloadProgress = bubbleZoomDownloadProgress
        surfaceState.scrubDebounceJob = scrubDebounceJob
        surfaceState.isBookmarked = isBookmarked
        surfaceState.isReflowingThisBook = isReflowingThisBook
        surfaceState.isOss = isOss
        surfaceState.tabStateMap = tabStateMap
        surfaceState.reflowProgressValue = reflowProgressValue
        surfaceState.annotationSettingsRepo = annotationSettingsRepo
        surfaceState.zoomIndicatorPercentage = zoomIndicatorPercentage
        surfaceState.toolSettings = toolSettings
        surfaceState.onInsertTextBox = onInsertTextBox
        surfaceState.customFonts = customFonts
        surfaceState.onSingleTapStable = onSingleTapStable
    }

    bindPdfViewerSurfaceChunk {
        surfaceState.isAnnotationHit = { annotation: PdfAnnotation, hitPoint: PdfPoint, lastHitPoint: PdfPoint?, pageAspectRatio: Float, threshold: Float ->
            isAnnotationHit(annotation, hitPoint, lastHitPoint, pageAspectRatio, threshold)
        }
        surfaceState.navigateToPdfSearchResult = { result: SearchResult -> navigateToPdfSearchResult(result) }
    }

    bindPdfViewerSurfaceChunk {
        surfaceState.aiDefinitionResult = pdfViewerMutableValue({ aiDefinitionResult }, { aiDefinitionResult = it })
        surfaceState.currentThemeId = pdfViewerMutableValue({ currentThemeId }, { currentThemeId = it })
        surfaceState.customThemes = pdfViewerMutableValue({ customThemes }, { customThemes = it })
        surfaceState.flatTableOfContents = pdfViewerMutableValue({ flatTableOfContents }, { flatTableOfContents = it })
        surfaceState.isAiDefinitionLoading = pdfViewerMutableValue({ isAiDefinitionLoading }, { isAiDefinitionLoading = it })
        surfaceState.isPasswordError = pdfViewerMutableValue({ isPasswordError }, { isPasswordError = it })
        surfaceState.isShareLoading = pdfViewerMutableValue({ isShareLoading }, { isShareLoading = it })
        surfaceState.isSummarizationLoading = pdfViewerMutableValue({ isSummarizationLoading }, { isSummarizationLoading = it })
        surfaceState.launchAnnotatedSaveCopy = launchAnnotatedSaveCopy
        surfaceState.launchOriginalSaveCopy = launchOriginalSaveCopy
        surfaceState.onUpdateBottomTools = onUpdateBottomTools
        surfaceState.onUpdateHiddenTools = onUpdateHiddenTools
        surfaceState.onUpdateToolOrder = onUpdateToolOrder
        surfaceState.pdfFirstPageStandaloneInSpread = pdfViewerMutableValue({ pdfFirstPageStandaloneInSpread }, { pdfFirstPageStandaloneInSpread = it })
        surfaceState.pdfPageSpreadMode = pdfViewerMutableValue({ pdfPageSpreadMode }, { pdfPageSpreadMode = it })
        surfaceState.pendingPaginationSpreadRestorePage = pdfViewerMutableValue({ pendingPaginationSpreadRestorePage }, { pendingPaginationSpreadRestorePage = it })
        surfaceState.requestNotificationPermission = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        surfaceState.startTtsForOverlay = { startTts() }
        surfaceState.onNavigateToPro = onNavigateToPro
        surfaceState.clipboardManager = clipboardManager
        surfaceState.screenOrientationMode = pdfViewerMutableValue({ screenOrientationMode }, { screenOrientationMode = it })
        surfaceState.selectedTextForAi = pdfViewerMutableValue({ selectedTextForAi }, { selectedTextForAi = it })
        surfaceState.shareAnnotatedPdf = shareAnnotatedPdf
        surfaceState.shareOriginalPdf = shareOriginalPdf
        surfaceState.showAiDefinitionPopup = pdfViewerMutableValue({ showAiDefinitionPopup }, { showAiDefinitionPopup = it })
        surfaceState.showPasswordDialog = pdfViewerMutableValue({ showPasswordDialog }, { showPasswordDialog = it })
        surfaceState.showTopTabStrip = pdfViewerMutableValue({ showTopTabStrip }, { showTopTabStrip = it })
        surfaceState.summarizationResult = pdfViewerMutableValue({ summarizationResult }, { summarizationResult = it })
        surfaceState.summaryCacheManager = summaryCacheManager
        surfaceState.summarizeCurrentPage = { authToken, onUpdate, onFinish ->
            summarizeCurrentPage(authToken, onUpdate, onFinish)
        }
        surfaceState.uriHandler = uriHandler
    }

    PdfViewerScreenOverlays(surfaceState)
}

@Composable
private fun PdfViewerScreenOverlays(surfaceState: PdfViewerSurfaceState) {
    val activeTheme = surfaceState.activeTheme.value
    val ownsPaneGlobals = surfaceState.ownsPaneGlobals
    val drawerState = surfaceState.drawerState
    val ttsState = surfaceState.ttsState
    val bookId = surfaceState.bookId
    val ttsController = surfaceState.ttsController
    val context = surfaceState.context
    val totalDisplayPages = surfaceState.totalDisplayPages
    val verticalReaderState = surfaceState.verticalReaderState
    val coroutineScope = surfaceState.coroutineScope
    val isTtsSessionActive = surfaceState.isTtsSessionActive
    val hiddenTools = surfaceState.hiddenTools
    val toolOrder = surfaceState.toolOrder
    val bottomTools = surfaceState.bottomTools
    val openTabs = surfaceState.openTabs
    val activeTabBookId = surfaceState.activeTabBookId
    var showPenPlayground by surfaceState.showPenPlayground
    val viewModel = surfaceState.viewModel
    val saveAllData = surfaceState.saveAllData
    val currentPage = surfaceState.currentPage
    val uiState = surfaceState.uiState
    val effectivePdfUri = surfaceState.effectivePdfUri
    var currentBookId by surfaceState.currentBookId
    val onNavigateBack = surfaceState.onNavigateBack
    val activeDocumentRenderKey = surfaceState.activeDocumentRenderKey
    var pdfDocument by surfaceState.pdfDocument
    var totalPages by surfaceState.totalPages
    var displayMode by surfaceState.displayMode
    var showVerticalPageGap by surfaceState.showVerticalPageGap
    val pdfTextRepository = surfaceState.pdfTextRepository
    val isProUser = surfaceState.isProUser
    val onDictionaryLookupStable = surfaceState.onDictionaryLookupStable
    val onTranslateTextStable = surfaceState.onTranslateTextStable
    val onSearchTextStable = surfaceState.onSearchTextStable
    val onHighlightUpdate = surfaceState.onHighlightUpdate
    val onHighlightDelete = surfaceState.onHighlightDelete
    val onNoteRequested = surfaceState.onNoteRequested
    var bookmarks by surfaceState.bookmarks
    var virtualPages by surfaceState.virtualPages
    val persistBookmarksNow = surfaceState.persistBookmarksNow
    var globalTextureTransparency by surfaceState.globalTextureTransparency
    var excludeImages by surfaceState.excludeImages
    var reverseColorMode by surfaceState.reverseColorMode
    var customHighlightColors by surfaceState.customHighlightColors
    var showPageNumberOverlay by surfaceState.showPageNumberOverlay
    var useOnlineDictionary by surfaceState.useOnlineDictionary
    var showDictionaryUpsellDialog by surfaceState.showDictionaryUpsellDialog
    var clickedLinkUrl by surfaceState.clickedLinkUrl
    var highlightColorPickerInitialSlot by surfaceState.highlightColorPickerInitialSlot
    var showHighlightColorPicker by surfaceState.showHighlightColorPicker
    var poppedUpPanelBitmap by surfaceState.poppedUpPanelBitmap
    val visibleUserHighlights = surfaceState.visibleUserHighlights
    var ocrLanguage by surfaceState.ocrLanguage
    var systemUiMode by surfaceState.systemUiMode
    var showThemePanel by surfaceState.showThemePanel
    var showVisualOptionsSheet by surfaceState.showVisualOptionsSheet
    var showBrightnessSheet by surfaceState.showBrightnessSheet
    var showScreenOrientationSheet by surfaceState.showScreenOrientationSheet
    var showTtsSettingsSheet by surfaceState.showTtsSettingsSheet
    var showTtsReplacementsSheet by surfaceState.showTtsReplacementsSheet
    var showDictionarySettingsSheet by surfaceState.showDictionarySettingsSheet
    var showSummarizationUpsellDialog by surfaceState.showSummarizationUpsellDialog
    var showAiHubSheet by surfaceState.showAiHubSheet
    var showPermissionRationaleDialog by surfaceState.showPermissionRationaleDialog
    var showInsufficientCreditsDialog by surfaceState.showInsufficientCreditsDialog
    var showBubbleZoomDownloadDialog by surfaceState.showBubbleZoomDownloadDialog
    var showNewTabSheet by surfaceState.showNewTabSheet
    var showFileInfoDialog by surfaceState.showFileInfoDialog
    var showSaveDialog by surfaceState.showSaveDialog
    var showShareDialog by surfaceState.showShareDialog
    var showOcrLanguageDialog by surfaceState.showOcrLanguageDialog
    var showReindexDialog by surfaceState.showReindexDialog
    var showCustomizeToolsSheet by surfaceState.showCustomizeToolsSheet
    var pendingActionAfterOcrSelection by surfaceState.pendingActionAfterOcrSelection
    var pendingSaveMode by surfaceState.pendingSaveMode
    val isOss = surfaceState.isOss
    var hasSelectedOcrLanguage by surfaceState.hasSelectedOcrLanguage
    val tabStateMap = surfaceState.tabStateMap
    var isBackgroundIndexing by surfaceState.isBackgroundIndexing
    var backgroundIndexingProgress by surfaceState.backgroundIndexingProgress
    var currentTtsMode by surfaceState.currentTtsMode
    val scrollPaginationToDisplayPage = surfaceState.scrollPaginationToDisplayPage
    val currentPaginationDisplayPage = surfaceState.currentPaginationDisplayPage
    var aiDefinitionResult by surfaceState.aiDefinitionResult
    val canShowPdfTabs = surfaceState.canShowPdfTabs
    var currentThemeId by surfaceState.currentThemeId
    var customThemes by surfaceState.customThemes
    var documentPassword by surfaceState.documentPassword
    val effectiveReaderBookTitle = surfaceState.effectiveReaderBookTitle
    var flatTableOfContents by surfaceState.flatTableOfContents
    var highlightToNoteId by surfaceState.highlightToNoteId
    var isAiDefinitionLoading by surfaceState.isAiDefinitionLoading
    var isPasswordError by surfaceState.isPasswordError
    var isShareLoading by surfaceState.isShareLoading
    var isSummarizationLoading by surfaceState.isSummarizationLoading
    val isTabsEnabled = surfaceState.isTabsEnabled
    val launchAnnotatedSaveCopy = surfaceState.launchAnnotatedSaveCopy
    val launchOriginalSaveCopy = surfaceState.launchOriginalSaveCopy
    val onUpdateBottomTools = surfaceState.onUpdateBottomTools
    val onUpdateHiddenTools = surfaceState.onUpdateHiddenTools
    val onUpdateToolOrder = surfaceState.onUpdateToolOrder
    var pdfFirstPageStandaloneInSpread by surfaceState.pdfFirstPageStandaloneInSpread
    var pdfPageSpreadMode by surfaceState.pdfPageSpreadMode
    var pendingPaginationSpreadRestorePage by surfaceState.pendingPaginationSpreadRestorePage
    var readerBrightnessSettings by surfaceState.readerBrightnessSettings
    val requestNotificationPermission = surfaceState.requestNotificationPermission
    val startTtsForOverlay = surfaceState.startTtsForOverlay
    val navigateToPdfPage = surfaceState.navigateToPdfPage
    val onNavigateToPro = surfaceState.onNavigateToPro
    val clipboardManager = surfaceState.clipboardManager
    var screenOrientationMode by surfaceState.screenOrientationMode
    val selectedBookIdForPane = surfaceState.selectedBookIdForPane
    var selectedDictPackage by surfaceState.selectedDictPackage
    var selectedSearchPackage by surfaceState.selectedSearchPackage
    var selectedTextForAi by surfaceState.selectedTextForAi
    var selectedTranslatePackage by surfaceState.selectedTranslatePackage
    val shareAnnotatedPdf = surfaceState.shareAnnotatedPdf
    val shareOriginalPdf = surfaceState.shareOriginalPdf
    val sheetState = surfaceState.sheetState
    var showAiDefinitionPopup by surfaceState.showAiDefinitionPopup
    var showPasswordDialog by surfaceState.showPasswordDialog
    var showTopTabStrip by surfaceState.showTopTabStrip
    var summarizationResult by surfaceState.summarizationResult
    val summaryCacheManager = surfaceState.summaryCacheManager
    var ttsReplacementPreferences by surfaceState.ttsReplacementPreferences
    val updateReaderBrightness = surfaceState.updateReaderBrightness
    val updateTtsReplacementPreferences = surfaceState.updateTtsReplacementPreferences
    val summarizeCurrentPage = surfaceState.summarizeCurrentPage
    val uriHandler = surfaceState.uriHandler
    val userHighlights = surfaceState.userHighlights

    SharedMobileReaderDrawer(
        drawerState = drawerState, gesturesEnabled = drawerState.isOpen, drawerContent = {
            ModalDrawerSheet(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                PdfNavigationDrawerContent(
                    pdfDocument = pdfDocument,
                    documentKey = activeDocumentRenderKey,
                    flatTableOfContents = flatTableOfContents,
                    bookmarks = bookmarks,
                    userHighlights = visibleUserHighlights,
                    currentPage = currentPage,
                    totalPages = totalDisplayPages,
                    isTabsEnabled = canShowPdfTabs,
                    openTabs = openTabs,
                    activeTabBookId = activeTabBookId,
                    usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName,
                    isTopTabStripVisible = showTopTabStrip,
                    reverseColorMode = if (activeTheme.id == "reverse") reverseColorMode else PdfReverseColorMode.RGB,
                    excludeImages = excludeImages,
                    customHighlightColors = customHighlightColors,
                    onPageSelected = { targetPage ->
                        val targetDisplayPage = virtualPages.indexOfFirst {
                            it is VirtualPage.PdfPage && it.pdfIndex == targetPage
                        }.takeIf { it >= 0 } ?: targetPage
                        navigateToPdfPage(targetDisplayPage, PdfNavigationReason.TABLE_OF_CONTENTS, true)
                    },
                    onDisplayPageSelected = { targetPage ->
                        navigateToPdfPage(targetPage, PdfNavigationReason.TABLE_OF_CONTENTS, true)
                    },
                    onTabSelected = { tabBookId ->
                        coroutineScope.launch {
                            currentBookId?.let { tabStateMap[it] = currentPage }
                            saveAllData(true).join()
                            viewModel.switchTab(tabBookId)
                        }
                    },
                    onTabClosed = { tabBookId ->
                        coroutineScope.launch {
                            val isSelected = tabBookId == activeTabBookId
                            if (isSelected) saveAllData(true).join()
                            viewModel.closeTab(tabBookId)
                            if (isSelected && openTabs.size == 1) {
                                onNavigateBack()
                            }
                        }
                    },
                    onNewTabClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            showNewTabSheet = true
                        }
                    },
                    onTopTabStripVisibilityChange = { isVisible ->
                        showTopTabStrip = isVisible
                        savePdfTopTabStripVisible(context, isVisible)
                    },
                    onRenameBookmark = { bookmarkToRename, newTitle ->
                        if (newTitle.isNotBlank()) {
                            val updatedBookmark = bookmarkToRename.copy(title = newTitle)
                            val updatedBookmarks = (bookmarks - bookmarkToRename) + updatedBookmark
                            bookmarks = updatedBookmarks
                            persistBookmarksNow(updatedBookmarks)
                        }
                    },
                    onDeleteBookmark = { bookmarkToDelete ->
                        val updatedBookmarks = bookmarks - bookmarkToDelete
                        bookmarks = updatedBookmarks
                        persistBookmarksNow(updatedBookmarks)
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
        PdfViewerReaderSurface(surfaceState)
    }

    // Keep the long-lived reader state above separate from transient overlays.
    // Besides isolating recomposition concerns, this prevents this screen's JVM
    // method from exceeding the platform's 64 KB bytecode limit.
    if (showAiHubSheet) {
        val currentPageForDisplay = if (displayMode == DisplayMode.PAGINATION) {
            currentPaginationDisplayPage()
        } else {
            verticalReaderState.currentPage
        }
        val bookTitle = effectiveReaderBookTitle

        AiHubBottomSheet(
            bookTitle = bookTitle,
            currentChapterIndex = currentPageForDisplay,
            chapterTitle = stringResource(R.string.pdf_page_short, currentPageForDisplay + 1),
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
                            token,
                            { result ->
                                if (result.error == "INSUFFICIENT_CREDITS") {
                                    showInsufficientCreditsDialog = true
                                    showAiHubSheet = false
                                    isSummarizationLoading = false
                                } else {
                                    summarizationResult = result
                                }
                            },
                            {
                                isSummarizationLoading = false
                                val finalSummary = summarizationResult?.summary
                                if (!finalSummary.isNullOrBlank() && summarizationResult?.error == null) {
                                    summaryCacheManager.saveSummary(
                                        bookTitle,
                                        currentPageForDisplay,
                                        context.getString(R.string.pdf_page_short, currentPageForDisplay + 1),
                                        finalSummary
                                    )
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
        SharedMobileInfoConfirmationDialog(
            title = stringResource(R.string.dialog_permission_required),
            body = stringResource(R.string.dialog_permission_notification_desc),
            confirmLabel = stringResource(R.string.action_continue),
            dismissLabel = stringResource(R.string.action_not_now),
            icon = null,
            onConfirm = {
                showPermissionRationaleDialog = false
                requestNotificationPermission()
            },
            onDismiss = {
                showPermissionRationaleDialog = false
                startTtsForOverlay()
            },
        )
    }
    if (showSummarizationUpsellDialog) {
        SharedMobileInfoConfirmationDialog(
            title = stringResource(R.string.dialog_unlock_page_summarization),
            body = stringResource(R.string.dialog_unlock_page_summarization_desc),
            confirmLabel = stringResource(R.string.action_learn_more),
            dismissLabel = stringResource(R.string.action_not_now),
            icon = {
                Icon(painterResource(id = R.drawable.summarize), contentDescription = null)
            },
            onConfirm = {
                showSummarizationUpsellDialog = false
                onNavigateToPro()
            },
            onDismiss = { showSummarizationUpsellDialog = false },
        )
    }

    if (showInsufficientCreditsDialog) {
        SharedMobileInfoConfirmationDialog(
            title = stringResource(R.string.dialog_out_of_credits_title),
            body = stringResource(R.string.dialog_out_of_credits_desc),
            confirmLabel = stringResource(R.string.action_get_pro_or_add_credits),
            dismissLabel = stringResource(R.string.action_cancel),
            icon = { Icon(painterResource(id = R.drawable.crown), contentDescription = null) },
            onConfirm = {
                showInsufficientCreditsDialog = false
                onNavigateToPro()
            },
            onDismiss = { showInsufficientCreditsDialog = false },
        )
    }

    // --- PANEL POPUP ---
    if (poppedUpPanelBitmap != null) {
        val sourcePanelBitmap = poppedUpPanelBitmap
        val displayPanelBitmap = remember(sourcePanelBitmap) {
            sourcePanelBitmap?.scaledToCanvasLimit()
        }
        DisposableEffect(sourcePanelBitmap, displayPanelBitmap) {
            onDispose {
                if (displayPanelBitmap != null && displayPanelBitmap !== sourcePanelBitmap && !displayPanelBitmap.isRecycled) {
                    displayPanelBitmap.recycle()
                }
            }
        }
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
            displayPanelBitmap?.takeUnless { it.isRecycled }?.let { panelBitmap ->
                Image(
                    bitmap = panelBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.content_desc_annotated_page),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            }

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

    if (ownsPaneGlobals && showPasswordDialog) {
        SharedMobilePdfPasswordDialog(
            labels = SharedMobilePdfPasswordLabels(
                title = stringResource(R.string.title_password_protected),
                description = stringResource(R.string.desc_password_protected),
                password = stringResource(R.string.password),
                incorrectPassword = stringResource(R.string.error_incorrect_password),
                showPassword = stringResource(R.string.content_desc_show_password),
                hidePassword = stringResource(R.string.content_desc_hide_password),
                open = stringResource(R.string.action_open),
                cancel = stringResource(R.string.action_cancel),
            ),
            isError = isPasswordError,
            onDismiss = { onNavigateBack() },
            onConfirm = { password -> documentPassword = password })
    }

    if (showBubbleZoomDownloadDialog) {
        SharedMobileInfoConfirmationDialog(
            title = stringResource(R.string.dialog_download_bubble_zoom_model),
            body = stringResource(R.string.dialog_download_bubble_zoom_model_desc),
            confirmLabel = stringResource(R.string.action_download),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                showBubbleZoomDownloadDialog = false
                viewModel.downloadSpeechBubbleModel(context)
            },
            onDismiss = { showBubbleZoomDownloadDialog = false },
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
                                headlineContent = {
                                    Text(
                                        file.cardTitle(uiState.usePdfFileNameAsDisplayName),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
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
        SharedMobileInfoConfirmationDialog(
            title = stringResource(R.string.ai_unlock_smart_dict),
            body = stringResource(R.string.ai_unlock_smart_dict_desc),
            confirmLabel = stringResource(R.string.action_learn_more),
            dismissLabel = stringResource(R.string.action_not_now),
            icon = { Icon(painterResource(id = R.drawable.ai), contentDescription = null) },
            onConfirm = {
                showDictionaryUpsellDialog = false
                onNavigateToPro()
            },
            onDismiss = { showDictionaryUpsellDialog = false },
        )
    }

    showReindexDialog?.let { newLanguage ->
        if (BuildConfig.IS_PRO) {
            SharedMobileInfoConfirmationDialog(
                title = stringResource(R.string.title_reindex_document),
                body = stringResource(R.string.desc_reindex_document_warning),
                confirmLabel = stringResource(R.string.action_reindex),
                dismissLabel = stringResource(R.string.action_cancel),
                onDismiss = { showReindexDialog = null },
                onConfirm = {
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
                },
            )
        } else {
            showReindexDialog = null
        }
    }

    if (showOcrLanguageDialog && !isOss) {
        SharedMobileSingleChoiceDialog(
            title = stringResource(R.string.title_select_ocr_language),
            description = stringResource(R.string.desc_select_ocr_language),
            cancelLabel = stringResource(R.string.action_cancel),
            options = OcrLanguage.entries.map { language ->
                SharedMobileSingleChoiceOption(language, stringResource(language.displayNameRes))
            },
            selectedValue = ocrLanguage,
            firstRunMessage = if (!hasSelectedOcrLanguage) {
                stringResource(R.string.desc_ocr_language_change_later)
            } else null,
            onDismiss = {
                showOcrLanguageDialog = false
                pendingActionAfterOcrSelection = null
            },
            onSelected = { selected ->
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
        val bookTitle = effectiveReaderBookTitle
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

    TtsWordReplacementsSheet(
        isVisible = showTtsReplacementsSheet,
        bookId = bookId,
        bookTitle = effectiveReaderBookTitle,
        preferences = ttsReplacementPreferences,
        onPreferencesChange = updateTtsReplacementPreferences,
        onDismiss = { showTtsReplacementsSheet = false },
    )

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
                        newColor,
                        targetHighlight.style
                    )
                },
                onStyleChange = { newStyle ->
                    onHighlightUpdate(
                        targetHighlight.id,
                        targetHighlight.color,
                        newStyle
                    )
                },
                onDismiss = { highlightToNoteId = null },
                onSave = { noteText, comments ->
                    val index =
                        userHighlights.indexOfFirst { it.id == targetHighlight.id }
                    if (index != -1) {
                        userHighlights[index] =
                            userHighlights[index].copy(
                                note = noteText.takeIf { it.isNotBlank() },
                                comments = comments
                            )
                    }
                    highlightToNoteId = null
                },
                onUpdate = { noteText, comments ->
                    val index =
                        userHighlights.indexOfFirst { it.id == targetHighlight.id }
                    if (index != -1) {
                        userHighlights[index] =
                            userHighlights[index].copy(
                                note = noteText.takeIf { it.isNotBlank() },
                                comments = comments
                            )
                    }
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
            reverseColorMode = reverseColorMode,
            onExcludeImagesChange = {
                excludeImages = it
                com.aryan.reader.saveExcludeImages(context, it)
            },
            onReverseColorModeChange = {
                reverseColorMode = it
                com.aryan.reader.savePdfReverseColorMode(context, it)
            },
            showReverseColorOption = true,
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
        SharedMobileExternalLinkDialog(
            title = stringResource(R.string.dialog_external_link_title),
            warning = stringResource(R.string.desc_external_link_warning, url),
            visitLabel = stringResource(R.string.action_visit),
            copyLabel = stringResource(R.string.action_copy),
            cancelLabel = stringResource(R.string.action_cancel),
            onVisit = {
                try {
                    uriHandler.openUri(url)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to open URI")
                }
                clickedLinkUrl = null
            },
            onCopy = {
                clipboardManager.setText(AnnotatedString(url))
                clickedLinkUrl = null
            },
            onDismiss = { clickedLinkUrl = null },
        )
    }

    if (showSaveDialog) {
        SharedMobileDocumentFormatDialog(
            title = stringResource(R.string.title_save_to_device),
            description = stringResource(R.string.desc_choose_format_save),
            annotatedLabel = stringResource(R.string.action_with_annotations),
            originalLabel = stringResource(R.string.action_original),
            cancelLabel = stringResource(R.string.action_cancel),
            onAnnotated = {
                showSaveDialog = false
                launchAnnotatedSaveCopy()
            },
            onOriginal = {
                showSaveDialog = false
                launchOriginalSaveCopy()
            },
            onDismiss = {
                showSaveDialog = false
                pendingSaveMode = null
            },
        )
    }

    if (showShareDialog) {
        SharedMobileDocumentFormatDialog(
            title = stringResource(R.string.share_chooser_title),
            description = stringResource(R.string.desc_choose_format_share),
            annotatedLabel = stringResource(R.string.action_with_annotations),
            originalLabel = stringResource(R.string.action_original),
            cancelLabel = stringResource(R.string.action_cancel),
            onAnnotated = {
                showShareDialog = false
                shareAnnotatedPdf()
            },
            onOriginal = {
                showShareDialog = false
                shareOriginalPdf()
            },
            onDismiss = { showShareDialog = false },
        )
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

    if (showBrightnessSheet) {
        ReaderBrightnessSheet(
            settings = readerBrightnessSettings,
            onSettingsChange = updateReaderBrightness,
            onDismiss = { showBrightnessSheet = false }
        )
    }

    if (showVisualOptionsSheet) {
        PdfVisualOptionsSheet(
            displayMode = displayMode,
            systemUiMode = systemUiMode,
            pageSpreadMode = pdfPageSpreadMode,
            firstPageStandaloneInSpread = pdfFirstPageStandaloneInSpread,
            showVerticalPageGap = showVerticalPageGap,
            showPageNumberOverlay = showPageNumberOverlay,
            onPageSpreadModeChange = { mode ->
                pendingPaginationSpreadRestorePage = currentPage
                pdfPageSpreadMode = mode
                savePdfPageSpreadMode(context, mode)
            },
            onFirstPageStandaloneInSpreadChange = { enabled ->
                pendingPaginationSpreadRestorePage = currentPage
                pdfFirstPageStandaloneInSpread = enabled
                savePdfFirstPageStandaloneInSpread(context, enabled)
            },
            onSystemUiModeChange = { mode ->
                systemUiMode = mode
                savePdfSystemUiMode(context, mode)
            },
            onShowVerticalPageGapChange = { isVisible ->
                showVerticalPageGap = isVisible
                savePdfVerticalPageGapVisible(context, isVisible)
            },
            onShowPageNumberOverlayChange = { isVisible ->
                showPageNumberOverlay = isVisible
                savePdfPageNumberOverlayVisible(context, isVisible)
            },
            onDismiss = { showVisualOptionsSheet = false }
        )
    }
    if (showScreenOrientationSheet) {
        ReaderScreenOrientationSheet(
            selectedMode = screenOrientationMode,
            onModeSelected = { mode ->
                screenOrientationMode = mode
                saveReaderScreenOrientationMode(context, mode)
            },
            onDismiss = { showScreenOrientationSheet = false }
        )
    }
    ReaderFileInfoDialogs(
        isFileInfoVisible = showFileInfoDialog,
        onFileInfoVisibleChange = { showFileInfoDialog = it },
        uiState = uiState,
        primaryBookId = selectedBookIdForPane,
        secondaryBookId = currentBookId ?: activeTabBookId,
        uriString = effectivePdfUri.toString(),
        viewModel = viewModel
    )
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
private class PdfViewerDocumentSetupInputs(
    val uiState: ReaderScreenState,
    val pdfUri: Uri,
    val initialPage: Int?,
    val initialBookmarksJson: String?,
    val onBookmarksChanged: (String) -> Unit,
    val pane: PdfViewerPane?,
    val viewModel: MainViewModel,
    val isPaneFocused: Boolean,
    val isSplitPane: Boolean,
    val ownsPaneGlobals: Boolean,
    val context: Context,
    val coroutineScope: CoroutineScope,
    val ttsController: TtsController,
    val ttsState: TtsPlaybackManager.TtsState,
    val customFonts: List<CustomFontEntity>,
    val bubbleZoomDownloadProgress: Float?,
    val annotationSettingsRepo: AnnotationSettingsRepository,
    val toolSettings: AnnotationToolSettings,
    val tabStateMap: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Int>,
    val displayMode: PdfViewerMutableValue<DisplayMode>,
    val currentActiveOffset: PdfViewerMutableValue<Offset>,
    val currentActiveScale: PdfViewerMutableValue<Float>,
    val isScrollLocked: PdfViewerMutableValue<Boolean>,
    val lockedState: PdfViewerMutableValue<Triple<Float, Float, Float>?>,
    val ocrLanguage: PdfViewerMutableValue<OcrLanguage>,
    val pdfFirstPageStandaloneInSpread: PdfViewerMutableValue<Boolean>,
    val pdfPageSpreadMode: PdfViewerMutableValue<com.aryan.reader.shared.reader.ReaderPageSpreadMode>,
    val pendingPaginationSpreadRestorePage: PdfViewerMutableValue<Int?>,
    val pendingRestorePage: PdfViewerMutableValue<Int?>,
    val showBars: PdfViewerMutableValue<Boolean>,
    val showInsufficientCreditsDialog: PdfViewerMutableValue<Boolean>,
    val showTopTabStrip: PdfViewerMutableValue<Boolean>,
    val systemUiMode: PdfViewerMutableValue<SystemUiMode>,
)

@Composable
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
private fun PdfViewerDocumentSetup(
    surfaceState: PdfViewerSurfaceState,
    setup: PdfViewerDocumentSetupInputs,
) {
    val uiState = setup.uiState
    val pdfUri = setup.pdfUri
    val initialPage = setup.initialPage
    val initialBookmarksJson = setup.initialBookmarksJson
    val onBookmarksChanged = setup.onBookmarksChanged
    val pane = setup.pane
    val viewModel = setup.viewModel
    val isPaneFocused = setup.isPaneFocused
    val isSplitPane = setup.isSplitPane
    val ownsPaneGlobals = setup.ownsPaneGlobals
    val context = setup.context
    val coroutineScope = setup.coroutineScope
    val ttsController = setup.ttsController
    val ttsState = setup.ttsState
    val customFonts = setup.customFonts
    val bubbleZoomDownloadProgress = setup.bubbleZoomDownloadProgress
    val annotationSettingsRepo = setup.annotationSettingsRepo
    val toolSettings = setup.toolSettings
    val tabStateMap = setup.tabStateMap
    var displayMode by setup.displayMode
    var currentActiveOffset by setup.currentActiveOffset
    var currentActiveScale by setup.currentActiveScale
    var isScrollLocked by setup.isScrollLocked
    var lockedState by setup.lockedState
    var ocrLanguage by setup.ocrLanguage
    var pdfFirstPageStandaloneInSpread by setup.pdfFirstPageStandaloneInSpread
    var pdfPageSpreadMode by setup.pdfPageSpreadMode
    var pendingPaginationSpreadRestorePage by setup.pendingPaginationSpreadRestorePage
    var pendingRestorePage by setup.pendingRestorePage
    var showBars by setup.showBars
    var showInsufficientCreditsDialog by setup.showInsufficientCreditsDialog
    var showTopTabStrip by setup.showTopTabStrip
    var systemUiMode by setup.systemUiMode

    val paneBookId = pane?.bookId
    val paneInitialPage = pane?.initialPage
    val paneInitialBookmarksJson = pane?.initialBookmarksJson
    val selectedBookIdForPane = paneBookId ?: uiState.selectedBookId
    val effectivePdfUri = pane?.pdfUri ?: uiState.selectedPdfUri ?: pdfUri
    val effectiveFileType = if (pane != null) FileType.PDF else uiState.selectedFileType ?: FileType.PDF
    val effectiveInitialPage = paneInitialPage ?: initialPage
    val effectiveInitialBookmarksJson = paneInitialBookmarksJson ?: initialBookmarksJson
    var documentPassword by rememberSaveable(effectivePdfUri.toString()) { mutableStateOf<String?>(null) }
    var isPrintBlockedForPasswordProtectedPdf by rememberSaveable(effectivePdfUri.toString()) { mutableStateOf(false) }
    val isComicFile = effectiveFileType in COMIC_ARCHIVE_FILE_TYPES

    var showNewTabSheet by remember { mutableStateOf(false) }
    var showFileInfoDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val isTabsEnabled = !isSplitPane && uiState.isTabsEnabled
    val openTabs = if (isSplitPane) emptyList() else uiState.openTabs
    val activeTabBookId = if (isSplitPane) null else uiState.activeTabBookId
    val canShowPdfTabs = isTabsEnabled && openTabs.isNotEmpty() && effectiveFileType == FileType.PDF
    val isPdfTabStripVisible = canShowPdfTabs && showTopTabStrip
    val originalFileName by remember(uiState.allRecentFiles, uiState.rawLibraryFiles, uiState.recentFiles, selectedBookIdForPane, effectivePdfUri) {
        derivedStateOf {
            (selectedBookIdForPane?.let { selectedId ->
                uiState.allRecentFiles.find { it.bookId == selectedId }
                    ?: uiState.rawLibraryFiles.find { it.bookId == selectedId }
                    ?: uiState.recentFiles.find { it.bookId == selectedId }
            } ?: uiState.allRecentFiles.find { it.uriString == effectivePdfUri.toString() }
                ?: uiState.rawLibraryFiles.find { it.uriString == effectivePdfUri.toString() }
                ?: uiState.recentFiles.find { it.uriString == effectivePdfUri.toString() })?.displayName
                ?: effectivePdfUri.lastPathSegment ?: "Document.pdf"
        }
    }
    var documentMetadataTitle by remember { mutableStateOf<String?>(null) }
    val activeLibraryItem by remember(uiState.allRecentFiles, uiState.rawLibraryFiles, uiState.recentFiles, selectedBookIdForPane, effectivePdfUri) {
        derivedStateOf {
            selectedBookIdForPane?.let { selectedId ->
                uiState.allRecentFiles.find { it.bookId == selectedId }
                    ?: uiState.rawLibraryFiles.find { it.bookId == selectedId }
                    ?: uiState.recentFiles.find { it.bookId == selectedId }
            } ?: uiState.allRecentFiles.find { it.uriString == effectivePdfUri.toString() }
                ?: uiState.rawLibraryFiles.find { it.uriString == effectivePdfUri.toString() }
                ?: uiState.recentFiles.find { it.uriString == effectivePdfUri.toString() }
        }
    }
    val readerDisplayTitle by remember(activeLibraryItem, uiState.usePdfFileNameAsDisplayName, originalFileName) {
        derivedStateOf {
            activeLibraryItem
                ?.cardTitle(uiState.usePdfFileNameAsDisplayName)
                ?: originalFileName
        }
    }
    val effectiveReaderBookTitle by remember(activeLibraryItem, documentMetadataTitle, readerDisplayTitle) {
        derivedStateOf {
            activeLibraryItem?.customName?.takeIf { it.isNotBlank() }
                ?: documentMetadataTitle?.takeIf { it.isNotBlank() }
                ?: readerDisplayTitle
        }
    }
    var currentBookId by remember(paneBookId, effectivePdfUri) { mutableStateOf(paneBookId) }
    val bookId = currentBookId ?: effectivePdfUri.toString().hashCode().toString()
    val activeDocumentRenderKey = currentBookId ?: effectivePdfUri.toString()
    val view = LocalView.current
    var isDockDragging by remember { mutableStateOf(false) }
    var initialScrollDone by remember { mutableStateOf(false) }

    LaunchedEffect(
        activeLibraryItem,
        documentMetadataTitle,
        readerDisplayTitle,
        effectiveReaderBookTitle,
        originalFileName,
        currentBookId,
        selectedBookIdForPane,
        effectivePdfUri
    ) {
        Timber.tag(PDF_RENAME_TRACE_TAG).i(
            "pdfScreen.titleResolved selectedBookId=$selectedBookIdForPane currentBookId=$currentBookId " +
                "uri=$effectivePdfUri activeItemId=${activeLibraryItem?.bookId} " +
                "displayName=${activeLibraryItem?.displayName} title=${activeLibraryItem?.title} " +
                "customName=${activeLibraryItem?.customName} documentMetadataTitle=$documentMetadataTitle " +
                "originalFileName=$originalFileName readerDisplayTitle=$readerDisplayTitle " +
                "effectiveReaderBookTitle=$effectiveReaderBookTitle usePdfFileName=${uiState.usePdfFileNameAsDisplayName}"
        )
    }

    val reflowBookId = remember(bookId) { "${bookId}_reflow" }
    val hasReflowFile by remember(uiState.allRecentFiles, reflowBookId, isSplitPane) {
        derivedStateOf {
            !isSplitPane && uiState.allRecentFiles.any { it.bookId == reflowBookId && !it.isDeleted }
        }
    }

    LaunchedEffect(bookId) {
        showBars = false
        val savedIsScrollLocked = loadPdfScrollLocked(context, bookId)
        val savedLockedState = loadPdfLockedState(context, bookId)
        val activeCamera = activePdfCameraAfterLockPreferenceLoad(
            isScrollLocked = savedIsScrollLocked,
            lockedState = savedLockedState
        )
        isScrollLocked = savedIsScrollLocked
        lockedState = savedLockedState
        currentActiveScale = activeCamera.first
        currentActiveOffset = activeCamera.second
    }

    var isAutoScrollModeActive by remember { mutableStateOf(false) }
    var isAutoScrollPlaying by remember { mutableStateOf(false) }
    var isAutoScrollTempPaused by remember { mutableStateOf(false) }
    val autoScrollResumeJob = remember { mutableStateOf<Job?>(null) }
    var isAutoScrollCollapsed by remember { mutableStateOf(false) }
    var ttsOverlaySize by remember(context) { mutableStateOf(loadReaderTtsOverlaySize(context)) }

    var isMusicianMode by remember { mutableStateOf(loadPdfMusicianMode(context)) }
    var autoScrollUseSlider by remember { mutableStateOf(loadPdfAutoScrollUseSlider(context)) }
    var isStylusOnlyMode by remember { mutableStateOf(loadStylusOnlyMode(context)) }
    var showTtsControlsSheet by remember { mutableStateOf(false) }
    var isKeepScreenOn by remember { mutableStateOf(loadKeepScreenOn(context)) }
    val isTtsPlaybackForThisPane = !isSplitPane || (ownsPaneGlobals && ttsState.bookId == bookId)
    ttsState.currentText
    var currentTtsMode by remember {
        mutableStateOf(
            com.aryan.reader.tts.loadTtsMode(context).let {
                if (BuildConfig.FLAVOR == "oss" && !isByokCloudTtsAvailable(context)) TtsPlaybackManager.TtsMode.BASE else it
            }
        )
    }
    var showTtsSettingsSheet by remember { mutableStateOf(false) }
    var showTtsReplacementsSheet by remember { mutableStateOf(false) }
    var ttsReplacementPreferences by remember { mutableStateOf(loadTtsReplacementPreferences(context)) }
    val updateTtsReplacementPreferences: (ReaderTtsReplacementPreferences) -> Unit = { next ->
        ttsReplacementPreferences = next
        saveTtsReplacementPreferences(context, next)
    }

    DisposableEffect(isKeepScreenOn, isSplitPane, ownsPaneGlobals) {
        view.keepScreenOn = isKeepScreenOn && ownsPaneGlobals
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


    fun showBanner(message: String, isError: Boolean = false, isPersistent: Boolean = false) {
        viewModel.showBanner(message, isError, isPersistent)
    }
    val onOcrStateChange: (Boolean) -> Unit = {}

    var showZoomIndicator by remember { mutableStateOf(false) }
    var bookmarks by remember(effectivePdfUri) { mutableStateOf(loadPdfBookmarksFromJson(effectiveInitialBookmarksJson)) }

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

    val onPrintDocument: () -> Unit = onPrintDocument@{
        if (!ownsPaneGlobals) return@onPrintDocument
        if (isPrintBlockedForPasswordProtectedPdf) {
            showBanner(context.getString(R.string.error_print_password_protected), isError = true)
            return@onPrintDocument
        }
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

    LaunchedEffect(isComicFile) {
        if (!isComicFile) {
            isBubbleZoomModeActive = false
            showBubbleZoomDownloadDialog = false
        }
    }

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
    var readerBrightnessSettings by remember { mutableStateOf(loadReaderBrightnessSettings(context)) }
    var showBrightnessSheet by remember { mutableStateOf(false) }
    if (ownsPaneGlobals) {
        ReaderBrightnessEffect(window, readerBrightnessSettings)
    }

    val updateReaderBrightness: (com.aryan.reader.ReaderBrightnessSettings) -> Unit = { settings ->
        readerBrightnessSettings = settings
        saveReaderBrightnessSettings(context, settings)
    }

    DisposableEffect(window, view, isSplitPane, ownsPaneGlobals) {
        onDispose {
            if (ownsPaneGlobals) window?.let {
                WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(systemUiMode, showStandardBars, isSplitPane, ownsPaneGlobals) {
        if (!ownsPaneGlobals) return@LaunchedEffect
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            val visibility = mobilePdfSystemBarsVisibility(systemUiMode, showStandardBars)
            if (visibility.statusBarsVisible) insetsController.show(WindowInsetsCompat.Type.statusBars())
            else insetsController.hide(WindowInsetsCompat.Type.statusBars())
            if (visibility.navigationBarsVisible) insetsController.show(WindowInsetsCompat.Type.navigationBars())
            else insetsController.hide(WindowInsetsCompat.Type.navigationBars())
            if (!visibility.statusBarsVisible || !visibility.navigationBarsVisible) {
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    val dockHeight = 64.dp
    val dockHeightPx = with(LocalDensity.current) { dockHeight.toPx() }
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current

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
        statusBarHeightDp,
        isPdfTabStripVisible
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
            if (isPdfTabStripVisible) {
                inset += PdfTabStripHeight
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

    LaunchedEffect(bookId, currentActiveScale, currentActiveOffset, isScrollLocked) {
        if (isScrollLocked) {
            val requestedCamera = currentActiveScale to currentActiveOffset
            delay(500)
            Timber.tag("PdfLockDiagnostic").d("SAVING: BookId=$bookId | Scale=${requestedCamera.first} | X=${requestedCamera.second.x} | Y=${requestedCamera.second.y}")

            lockedState = Triple(requestedCamera.first, requestedCamera.second.x, requestedCamera.second.y)
            savePdfLockedState(context, bookId, requestedCamera.first, requestedCamera.second.x, requestedCamera.second.y)
        }
    }

    LaunchedEffect(ttsState.errorMessage, isTtsPlaybackForThisPane, isPaneFocused) {
        if (isSplitPane && (!isPaneFocused || !isTtsPlaybackForThisPane)) return@LaunchedEffect
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

    var showToolSettings by rememberSaveable { mutableStateOf(false) }
    val isHighlighterSnapEnabled = toolSettings.isHighlighterSnapEnabled

    val selectedTool = toolSettings.getActiveTool()

    val lastPenTool = toolSettings.getLastPenTool()
    val lastHighlighterTool = toolSettings.getLastHighlighterTool()
    val dockPenColor = toolSettings.getToolColor(lastPenTool)
    val dockHighlighterColor = toolSettings.getToolColor(lastHighlighterTool)

    val activeToolColor = toolSettings.getToolColor(selectedTool)
    val activeToolThickness = toolSettings.getToolThickness(selectedTool)
    val eraserToolThickness = toolSettings.getToolThickness(InkType.ERASER)

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
    val currentEraserStrokeWidth by remember(eraserToolThickness) { derivedStateOf { eraserToolThickness } }

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

    var annotationSession by remember { mutableStateOf(SharedPdfAnnotationSessionState()) }

    val richTextRepository = remember(context) { PdfRichTextRepository(context) }
    val richTextController = remember(currentBookId) {
        if (currentBookId != null) RichTextController(
            richTextRepository,
            coroutineScope,
            currentBookId!!,
            viewModel::onPdfSidecarsCommitted,
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
    var loadedPageLayoutBookId by remember { mutableStateOf<String?>(null) }
    var pageLayoutMutationVersion by remember(currentBookId) { mutableLongStateOf(0L) }
    val totalDisplayPages by remember(virtualPages, totalPages) {
        derivedStateOf { if (virtualPages.isNotEmpty()) virtualPages.size else totalPages }
    }
    val pdfSpreadSettings = remember(pdfPageSpreadMode, pdfFirstPageStandaloneInSpread) {
        ReaderSettings(
            pageSpreadMode = pdfPageSpreadMode,
            pdfFirstPageStandaloneInSpread = pdfFirstPageStandaloneInSpread
        )
    }
    val paginationSpreadStarts = remember(
        totalDisplayPages,
        pdfSpreadSettings.pageSpreadMode,
        pdfSpreadSettings.pdfFirstPageStandaloneInSpread
    ) {
        PdfSpreadLayout.spreadStartPageIndices(totalDisplayPages, pdfSpreadSettings)
    }
    val paginationPagerPageCount by remember(
        displayMode,
        totalDisplayPages,
        paginationSpreadStarts,
        pdfSpreadSettings.pageSpreadMode
    ) {
        derivedStateOf {
            if (displayMode == DisplayMode.PAGINATION && PdfSpreadLayout.isTwoPageSpreadEnabled(pdfSpreadSettings)) {
                paginationSpreadStarts.size.coerceAtLeast(1)
            } else {
                totalDisplayPages
            }
        }
    }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { paginationPagerPageCount })

    fun paginationDisplayPageForPagerPage(pagerPage: Int): Int {
        if (!PdfSpreadLayout.isTwoPageSpreadEnabled(pdfSpreadSettings)) {
            return pagerPage.coerceIn(0, (totalDisplayPages - 1).coerceAtLeast(0))
        }
        return paginationSpreadStarts
            .getOrElse(pagerPage.coerceIn(0, (paginationSpreadStarts.size - 1).coerceAtLeast(0))) { 0 }
    }

    fun paginationPagerPageForDisplayPage(displayPage: Int): Int {
        if (!PdfSpreadLayout.isTwoPageSpreadEnabled(pdfSpreadSettings)) {
            return displayPage.coerceIn(0, (paginationPagerPageCount - 1).coerceAtLeast(0))
        }
        val normalizedPage = PdfSpreadLayout.normalizePageIndex(displayPage, totalDisplayPages, pdfSpreadSettings)
        val spreadIndex = paginationSpreadStarts.indexOf(normalizedPage)
        return spreadIndex.coerceAtLeast(0).coerceIn(0, (paginationPagerPageCount - 1).coerceAtLeast(0))
    }

    suspend fun scrollPaginationToDisplayPage(displayPage: Int) {
        pagerState.scrollToPage(paginationPagerPageForDisplayPage(displayPage))
    }

    suspend fun animatePaginationToDisplayPage(displayPage: Int) {
        pagerState.animateScrollToPage(paginationPagerPageForDisplayPage(displayPage))
    }

    fun currentPaginationDisplayPage(): Int {
        val pagerPage = authoritativePdfPaginationPageIndex(
            currentPageIndex = pagerState.currentPage,
            settledPageIndex = pagerState.settledPage,
            isScrollInProgress = pagerState.isScrollInProgress,
        ) ?: pagerState.currentPage
        return paginationDisplayPageForPagerPage(pagerPage)
    }

    val currentPage by remember(
        displayMode,
        totalDisplayPages,
        paginationPagerPageCount,
        paginationSpreadStarts,
        pdfSpreadSettings.pageSpreadMode,
        pdfSpreadSettings.pdfFirstPageStandaloneInSpread
    ) {
        derivedStateOf {
            when (displayMode) {
                DisplayMode.PAGINATION -> currentPaginationDisplayPage()
                DisplayMode.VERTICAL_SCROLL -> verticalReaderState.currentPage
            }
        }
    }

    LaunchedEffect(
        pendingPaginationSpreadRestorePage,
        pdfSpreadSettings.pageSpreadMode,
        pdfSpreadSettings.pdfFirstPageStandaloneInSpread,
        totalDisplayPages,
        displayMode
    ) {
        val targetPage = pendingPaginationSpreadRestorePage ?: return@LaunchedEffect
        if (displayMode == DisplayMode.PAGINATION && totalDisplayPages > 0) {
            scrollPaginationToDisplayPage(targetPage)
        }
        pendingPaginationSpreadRestorePage = null
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
            val renderBitmap = createBitmap(renderWidth, renderHeight)

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
        return buildPdfBubblePrefetchOrder(
            currentPage = currentPage,
            totalPages = totalDisplayPages
        )
    }

    suspend fun detectSpeechBubblesForPage(
        sourcePageIndex: Int,
        fallbackBitmap: Bitmap,
        allowHighQualityFallback: Boolean = true
    ): List<SpeechBubble> {
        val document = pdfDocument
        val prefetchDocument = document?.takeIf {
            allowHighQualityFallback &&
                !viewModel.hasCachedSpeechBubbles(bookId, sourcePageIndex)
        }
        val detectionBitmap = prefetchDocument
            ?.let { renderSpeechBubblePrefetchBitmap(it, sourcePageIndex) }
            ?: fallbackBitmap
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

    var jumpHistory by remember(currentBookId) { mutableStateOf(SharedPdfJumpHistory()) }

    fun pruneJumpHistoryForDocument() {
        jumpHistory = jumpHistory.pruned(totalDisplayPages)
    }

    fun recordJumpHistory(currentPageIndex: Int, targetPageIndex: Int) {
        jumpHistory = jumpHistory.record(currentPageIndex, targetPageIndex, totalDisplayPages)
    }

    fun clearJumpHistory() {
        jumpHistory = SharedPdfJumpHistory()
    }

    fun currentPdfDisplayPage(): Int {
        val lastDisplayPage = (totalDisplayPages - 1).coerceAtLeast(0)
        val page = if (displayMode == DisplayMode.PAGINATION) {
            currentPaginationDisplayPage()
        } else {
            verticalReaderState.latestCurrentPage()
        }
        return page.coerceIn(0, lastDisplayPage)
    }

    fun navigateToPdfPage(
        targetPageIndex: Int,
        reason: PdfNavigationReason,
        recordHistory: Boolean = true,
    ) {
        if (totalDisplayPages <= 0) return

        val targetPage = targetPageIndex.coerceIn(0, totalDisplayPages - 1)
        if (recordHistory) {
            val currentPage = currentPdfDisplayPage()
            if (currentPage != targetPage) {
                recordJumpHistory(currentPage, targetPage)
            }
        }

        coroutineScope.launch {
            if (displayMode == DisplayMode.PAGINATION) {
                if (reason.animatesPagination()) {
                    animatePaginationToDisplayPage(targetPage)
                } else {
                    scrollPaginationToDisplayPage(targetPage)
                }
            } else {
                verticalReaderState.scrollToPage(targetPage)
            }
        }
    }

    fun navigateToJumpHistoryPage(targetPageIndex: Int) {
        if (targetPageIndex !in 0 until totalDisplayPages) {
            pruneJumpHistoryForDocument()
            return
        }
        navigateToPdfPage(targetPageIndex, PdfNavigationReason.JUMP_HISTORY, recordHistory = false)
    }

    LaunchedEffect(totalDisplayPages) {
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


    surfaceState.paneInitialPage = paneInitialPage
    surfaceState.selectedBookIdForPane = selectedBookIdForPane
    surfaceState.effectivePdfUri = effectivePdfUri
    surfaceState.effectiveFileType = effectiveFileType
    surfaceState.effectiveInitialPage = effectiveInitialPage
    surfaceState.effectiveInitialBookmarksJson = effectiveInitialBookmarksJson
    surfaceState.documentPassword = pdfViewerMutableValue({ documentPassword }, { documentPassword = it })
    surfaceState.isPrintBlockedForPasswordProtectedPdf = pdfViewerMutableValue({ isPrintBlockedForPasswordProtectedPdf }, { isPrintBlockedForPasswordProtectedPdf = it })
    surfaceState.isComicFile = isComicFile
    surfaceState.showNewTabSheet = pdfViewerMutableValue({ showNewTabSheet }, { showNewTabSheet = it })
    surfaceState.showFileInfoDialog = pdfViewerMutableValue({ showFileInfoDialog }, { showFileInfoDialog = it })
    surfaceState.sheetState = sheetState
    surfaceState.isTabsEnabled = isTabsEnabled
    surfaceState.openTabs = openTabs
    surfaceState.activeTabBookId = activeTabBookId
    surfaceState.canShowPdfTabs = canShowPdfTabs
    surfaceState.isPdfTabStripVisible.value = isPdfTabStripVisible
    surfaceState.originalFileName = originalFileName
    surfaceState.documentMetadataTitle = pdfViewerMutableValue({ documentMetadataTitle }, { documentMetadataTitle = it })
    surfaceState.activeLibraryItem = activeLibraryItem
    surfaceState.effectiveReaderBookTitle = effectiveReaderBookTitle
    surfaceState.currentBookId = pdfViewerMutableValue({ currentBookId }, { currentBookId = it })
    surfaceState.bookId = bookId
    surfaceState.activeDocumentRenderKey = activeDocumentRenderKey
    surfaceState.view = view
    surfaceState.isDockDragging = pdfViewerMutableValue({ isDockDragging }, { isDockDragging = it })
    surfaceState.initialScrollDone = pdfViewerMutableValue({ initialScrollDone }, { initialScrollDone = it })
    surfaceState.reflowBookId = reflowBookId
    surfaceState.hasReflowFile = hasReflowFile
    surfaceState.isAutoScrollModeActive = pdfViewerMutableValue({ isAutoScrollModeActive }, { isAutoScrollModeActive = it })
    surfaceState.isAutoScrollPlaying = pdfViewerMutableValue({ isAutoScrollPlaying }, { isAutoScrollPlaying = it })
    surfaceState.isAutoScrollTempPaused = pdfViewerMutableValue({ isAutoScrollTempPaused }, { isAutoScrollTempPaused = it })
    surfaceState.autoScrollResumeJob = autoScrollResumeJob
    surfaceState.isAutoScrollCollapsed = pdfViewerMutableValue({ isAutoScrollCollapsed }, { isAutoScrollCollapsed = it })
    surfaceState.ttsOverlaySize = pdfViewerMutableValue({ ttsOverlaySize }, { ttsOverlaySize = it })
    surfaceState.isMusicianMode = pdfViewerMutableValue({ isMusicianMode }, { isMusicianMode = it })
    surfaceState.autoScrollUseSlider = pdfViewerMutableValue({ autoScrollUseSlider }, { autoScrollUseSlider = it })
    surfaceState.isStylusOnlyMode = pdfViewerMutableValue({ isStylusOnlyMode }, { isStylusOnlyMode = it })
    surfaceState.showTtsControlsSheet = pdfViewerMutableValue({ showTtsControlsSheet }, { showTtsControlsSheet = it })
    surfaceState.isKeepScreenOn = pdfViewerMutableValue({ isKeepScreenOn }, { isKeepScreenOn = it })
    surfaceState.isTtsPlaybackForThisPane = isTtsPlaybackForThisPane
    surfaceState.currentTtsMode = pdfViewerMutableValue({ currentTtsMode }, { currentTtsMode = it })
    surfaceState.showTtsSettingsSheet = pdfViewerMutableValue({ showTtsSettingsSheet }, { showTtsSettingsSheet = it })
    surfaceState.showTtsReplacementsSheet = pdfViewerMutableValue({ showTtsReplacementsSheet }, { showTtsReplacementsSheet = it })
    surfaceState.ttsReplacementPreferences = pdfViewerMutableValue({ ttsReplacementPreferences }, { ttsReplacementPreferences = it })
    surfaceState.updateTtsReplacementPreferences = updateTtsReplacementPreferences
    surfaceState.showDictionarySettingsSheet = pdfViewerMutableValue({ showDictionarySettingsSheet }, { showDictionarySettingsSheet = it })
    surfaceState.useOnlineDictionary = pdfViewerMutableValue({ useOnlineDictionary }, { useOnlineDictionary = it })
    surfaceState.selectedDictPackage = pdfViewerMutableValue({ selectedDictPackage }, { selectedDictPackage = it })
    surfaceState.selectedTranslatePackage = pdfViewerMutableValue({ selectedTranslatePackage }, { selectedTranslatePackage = it })
    surfaceState.selectedSearchPackage = pdfViewerMutableValue({ selectedSearchPackage }, { selectedSearchPackage = it })
    surfaceState.triggerAutoScrollTempPause = { durationMs -> triggerAutoScrollTempPause(durationMs) }
    surfaceState.onAutoScrollInteraction = onAutoScrollInteraction
    surfaceState.paginationDraggingBoxId = pdfViewerMutableValue({ paginationDraggingBoxId }, { paginationDraggingBoxId = it })
    surfaceState.showBanner = PdfViewerBanner { message, isError, isPersistent -> showBanner(message, isError, isPersistent) }
    surfaceState.onOcrStateChange = onOcrStateChange
    surfaceState.showZoomIndicator = pdfViewerMutableValue({ showZoomIndicator }, { showZoomIndicator = it })
    surfaceState.bookmarks = pdfViewerMutableValue({ bookmarks }, { bookmarks = it })
    surfaceState.persistBookmarksNow = { updatedBookmarks ->
        onBookmarksChanged(serializePdfBookmarksToJson(updatedBookmarks))
    }
    surfaceState.showPenPlayground = pdfViewerMutableValue({ showPenPlayground }, { showPenPlayground = it })
    surfaceState.isEditMode = pdfViewerMutableValue({ isEditMode }, { isEditMode = it })
    surfaceState.isDockMinimized = pdfViewerMutableValue({ isDockMinimized }, { isDockMinimized = it })
    surfaceState.pendingNoteForNewHighlight = pdfViewerMutableValue({ pendingNoteForNewHighlight }, { pendingNoteForNewHighlight = it })
    surfaceState.highlightToNoteId = pdfViewerMutableValue({ highlightToNoteId }, { highlightToNoteId = it })
    surfaceState.onNoteRequested = onNoteRequested
    surfaceState.isDrawingActive = isDrawingActive
    surfaceState.isAutoScrollLocal = pdfViewerMutableValue({ isAutoScrollLocal }, { isAutoScrollLocal = it })
    surfaceState.onPrintDocument = onPrintDocument
    surfaceState.autoScrollSpeed = pdfViewerMutableValue({ autoScrollSpeed }, { autoScrollSpeed = it })
    surfaceState.autoScrollMinSpeed = pdfViewerMutableValue({ autoScrollMinSpeed }, { autoScrollMinSpeed = it })
    surfaceState.autoScrollMaxSpeed = pdfViewerMutableValue({ autoScrollMaxSpeed }, { autoScrollMaxSpeed = it })
    surfaceState.onToggleAutoScrollMode = onToggleAutoScrollMode
    surfaceState.updateSpeed = updateSpeed
    surfaceState.updateMinSpeed = updateMinSpeed
    surfaceState.updateMaxSpeed = updateMaxSpeed
    surfaceState.customHighlightColors = pdfViewerMutableValue({ customHighlightColors }, { customHighlightColors = it })
    surfaceState.showHighlightColorPicker = pdfViewerMutableValue({ showHighlightColorPicker }, { showHighlightColorPicker = it })
    surfaceState.highlightColorPickerInitialSlot = pdfViewerMutableValue({ highlightColorPickerInitialSlot }, { highlightColorPickerInitialSlot = it })
    surfaceState.isBubbleZoomModeActive = pdfViewerMutableValue({ isBubbleZoomModeActive }, { isBubbleZoomModeActive = it })
    surfaceState.showBubbleZoomDownloadDialog = pdfViewerMutableValue({ showBubbleZoomDownloadDialog }, { showBubbleZoomDownloadDialog = it })
    surfaceState.dockLocation = pdfViewerMutableValue({ dockLocation }, { dockLocation = it })
    surfaceState.dockOffset = pdfViewerMutableValue({ dockOffset }, { dockOffset = it })
    surfaceState.snapPreviewLocation = pdfViewerMutableValue({ snapPreviewLocation }, { snapPreviewLocation = it })
    surfaceState.paginationDraggingOffset = pdfViewerMutableValue({ paginationDraggingOffset }, { paginationDraggingOffset = it })
    surfaceState.paginationDraggingSize = pdfViewerMutableValue({ paginationDraggingSize }, { paginationDraggingSize = it })
    surfaceState.paginationDragPageHeight = pdfViewerMutableValue({ paginationDragPageHeight }, { paginationDragPageHeight = it })
    surfaceState.paginationOriginalRelSize = pdfViewerMutableValue({ paginationOriginalRelSize }, { paginationOriginalRelSize = it })
    surfaceState.window = window
    surfaceState.readerBrightnessSettings = pdfViewerMutableValue({ readerBrightnessSettings }, { readerBrightnessSettings = it })
    surfaceState.showBrightnessSheet = pdfViewerMutableValue({ showBrightnessSheet }, { showBrightnessSheet = it })
    surfaceState.updateReaderBrightness = updateReaderBrightness
    surfaceState.dockHeight = dockHeight
    surfaceState.dockHeightPx.value = dockHeightPx
    surfaceState.density.value = density
    surfaceState.viewConfiguration = viewConfiguration
    surfaceState.statusBarHeightDp.value = statusBarHeightDp
    surfaceState.searchState = searchState
    surfaceState.navBarHeight.value = navBarHeight
    surfaceState.verticalHeaderHeight.value = verticalHeaderHeight
    surfaceState.topOverlayInset.value = topOverlayInset
    surfaceState.verticalFooterHeight.value = verticalFooterHeight
    surfaceState.errorMessage = pdfViewerMutableValue({ errorMessage }, { errorMessage = it })
    surfaceState.showToolSettings = pdfViewerMutableValue({ showToolSettings }, { showToolSettings = it })
    surfaceState.isHighlighterSnapEnabled = isHighlighterSnapEnabled
    surfaceState.selectedTool.value = selectedTool
    surfaceState.lastPenTool = lastPenTool
    surfaceState.lastHighlighterTool = lastHighlighterTool
    surfaceState.dockPenColor = dockPenColor
    surfaceState.dockHighlighterColor = dockHighlighterColor
    surfaceState.activeToolThickness = activeToolThickness
    surfaceState.eraserToolThickness = eraserToolThickness
    surfaceState.fountainPenColor = fountainPenColor
    surfaceState.markerColor = markerColor
    surfaceState.pencilColor = pencilColor
    surfaceState.highlighterColor = highlighterColor
    surfaceState.highlighterRoundColor = highlighterRoundColor
    surfaceState.isCurrentToolHighlighter = isCurrentToolHighlighter
    surfaceState.currentSnapEnabled = currentSnapEnabled
    surfaceState.currentIsHighlighter = currentIsHighlighter
    surfaceState.penPalette = penPalette
    surfaceState.highlighterPalette = highlighterPalette
    surfaceState.currentStrokeColor = currentStrokeColor
    surfaceState.currentStrokeWidth = currentStrokeWidth
    surfaceState.currentEraserStrokeWidth = currentEraserStrokeWidth
    surfaceState.pdfTextRepository = pdfTextRepository
    surfaceState.annotationRepository = annotationRepository
    surfaceState.textBoxRepository = textBoxRepository
    surfaceState.highlightRepository = highlightRepository
    surfaceState.allAnnotations = pdfViewerMutableValue({ allAnnotations }, { allAnnotations = it })
    surfaceState.undoStack = undoStack
    surfaceState.redoStack = redoStack
    surfaceState.erasedAnnotationsFromStroke = erasedAnnotationsFromStroke
    surfaceState.lastEraserPoint = pdfViewerMutableValue({ lastEraserPoint }, { lastEraserPoint = it })
    surfaceState.annotationSession = pdfViewerMutableValue({ annotationSession }, { annotationSession = it })
    surfaceState.richTextRepository = richTextRepository
    surfaceState.richTextController = richTextController
    surfaceState.pdfDocument = pdfViewerMutableValue({ pdfDocument }, { pdfDocument = it })
    surfaceState.pfdState = pdfViewerMutableValue({ pfdState }, { pfdState = it })
    surfaceState.totalPages = pdfViewerMutableValue({ totalPages }, { totalPages = it })
    surfaceState.currentPageScale = pdfViewerMutableValue({ currentPageScale }, { currentPageScale = it })
    surfaceState.textBoxes = textBoxes
    surfaceState.selectedTextBoxId = pdfViewerMutableValue({ selectedTextBoxId }, { selectedTextBoxId = it })
    surfaceState.userHighlights = userHighlights
    surfaceState.drawingState = drawingState
    surfaceState.pdfiumCore = pdfiumCore
    surfaceState.verticalReaderState = verticalReaderState
    surfaceState.virtualPages = pdfViewerMutableValue({ virtualPages }, { virtualPages = it })
    surfaceState.loadedPageLayoutBookId = pdfViewerMutableValue({ loadedPageLayoutBookId }, { loadedPageLayoutBookId = it })
    surfaceState.pageLayoutMutationVersion = pdfViewerMutableValue({ pageLayoutMutationVersion }, { pageLayoutMutationVersion = it })
    if (surfaceState.totalDisplayPages != totalDisplayPages) {
        Timber.tag(TTS_DIAG_TAG).i(
            "bind.totalDisplayPages value=$totalDisplayPages totalPdfPages=$totalPages " +
                "virtual=${virtualPages.pdfLayoutDebugSummary()}"
        )
    }
    surfaceState.totalDisplayPages = totalDisplayPages
    surfaceState.pdfSpreadSettings = pdfSpreadSettings
    surfaceState.paginationSpreadStarts = paginationSpreadStarts
    surfaceState.paginationPagerPageCount = paginationPagerPageCount
    surfaceState.pagerState = pagerState
    surfaceState.paginationDisplayPageForPagerPage = { value -> paginationDisplayPageForPagerPage(value) }
    surfaceState.paginationPagerPageForDisplayPage = { value -> paginationPagerPageForDisplayPage(value) }
    surfaceState.scrollPaginationToDisplayPage = PdfViewerSuspendPageAction { displayPage -> scrollPaginationToDisplayPage(displayPage) }
    surfaceState.animatePaginationToDisplayPage = PdfViewerSuspendPageAction { displayPage -> animatePaginationToDisplayPage(displayPage) }
    surfaceState.currentPaginationDisplayPage = { currentPaginationDisplayPage() }
    surfaceState.currentPdfDisplayPage = { currentPdfDisplayPage() }
    surfaceState.currentPage = currentPage
    surfaceState.isDocumentReady = pdfViewerMutableValue({ isDocumentReady }, { isDocumentReady = it })
    surfaceState.detectSpeechBubblesForPage = PdfViewerSpeechBubbleDetector { sourcePageIndex, fallbackBitmap, allowHighQualityFallback -> detectSpeechBubblesForPage(sourcePageIndex, fallbackBitmap, allowHighQualityFallback) }
    surfaceState.jumpHistory = pdfViewerMutableValue({ jumpHistory }, { jumpHistory = it })
    surfaceState.clearJumpHistory = { clearJumpHistory() }
    surfaceState.navigateToJumpHistoryPage = { value -> navigateToJumpHistoryPage(value) }
    surfaceState.navigateToPdfPage = { value, reason, recordHistory ->
        navigateToPdfPage(value, reason, recordHistory)
    }
    surfaceState.lifecycleOwner = lifecycleOwner
}

/** Immutable per-composition context shared by each extracted pager page. */
private data class PdfViewerPaginationPageState(
    val surfaceState: PdfViewerSurfaceState,
    val stablePdfDocument: StableHolder<ReaderDocument>,
    val boxMaxWidth: Int,
    val boxMaxHeight: Int,
    val stylusButtonHovering: Boolean,
)

/**
 * The text-box snapshots consumed by the independently recomposed PDF surface.
 *
 * Keeping the complete list and its page index together ensures that a text edit publishes one
 * consistent snapshot to both vertical scrolling and pagination surfaces.
 */
internal data class PdfViewerTextBoxSurfaceData(
    val all: List<PdfTextBox> = emptyList(),
    val byPage: Map<Int, List<PdfTextBox>> = emptyMap(),
)

/** Compose-observable holder for the text-box snapshots shared by the PDF surfaces. */
internal class PdfViewerTextBoxSurfaceState {
    val data = mutableStateOf(PdfViewerTextBoxSurfaceData())
}

/**
 * Stable bridge between the reader's stateful screen and the extracted PDF surface.
 *
 * It keeps business state out of the bridge. The parent binds each current value or accessor on
 * every composition, while the surface reads the typed aliases below; the small Compose state
 * cells are limited to setup-produced metrics that must invalidate independently recomposed
 * surface groups. Keeping this bridge no-arg avoids the verifier-sensitive constructor generated
 * for the old 250-parameter Scaffold/BoxWithConstraints lambda and preserves the existing pane
 * ownership semantics.
 */
@Stable
private class PdfViewerSurfaceState {
    var richTextController: RichTextController? = null
    val selectedTool: androidx.compose.runtime.MutableState<InkType> = mutableStateOf(InkType.PEN)
    var density: androidx.compose.runtime.MutableState<androidx.compose.ui.unit.Density> =
        mutableStateOf(androidx.compose.ui.unit.Density(1f))
    lateinit var searchState: ReaderSearchState
    /**
     * The document surface is kept behind a stable bridge, so this value must be observable.
     * A plain field here lets the parent bind a new theme without invalidating already composed
     * vertical or paginated PDF pages; those pages then appear to update only after another input
     * causes an unrelated recomposition.
     */
    val activeTheme = mutableStateOf(PdfBuiltInThemes[0])
    var pdfSliderChromeVisible: Boolean by androidx.compose.runtime.mutableStateOf(false)
    lateinit var pdfSpreadSettings: ReaderSettings
    var ownsPaneGlobals: Boolean by androidx.compose.runtime.mutableStateOf(false)
    lateinit var drawerState: DrawerState

    // TTS and page-mapping values must be Compose-observable: several consumer scopes
    // read them as plain `val` snapshots, so a non-observable field would freeze those
    // snapshots until an unrelated recomposition (overlay/toolbar staleness bugs).
    var ttsState: TtsPlaybackManager.TtsState by androidx.compose.runtime.mutableStateOf(
        TtsPlaybackManager.TtsState()
    )
    lateinit var bookId: String
    lateinit var ttsController: TtsController
    var isTtsPlayingOrLoading: Boolean by androidx.compose.runtime.mutableStateOf(false)
    lateinit var context: Context
    var totalDisplayPages: Int by androidx.compose.runtime.mutableStateOf(0)
    lateinit var paginationSpreadStarts: List<Int>
    lateinit var pagerState: androidx.compose.foundation.pager.PagerState
    lateinit var verticalReaderState: VerticalPdfReaderState
    lateinit var isPageSliderVisible: PdfViewerMutableValue<Boolean>
    lateinit var coroutineScope: CoroutineScope
    lateinit var executeWithOcrCheck: ((() -> Unit) -> Unit)
    lateinit var isEditMode: PdfViewerMutableValue<Boolean>
    var keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController? = null
    var isTtsSessionActive: Boolean = false
    lateinit var startTtsWithPermissionCheck: (Int?, Int?) -> Unit
    var isSplitPane: Boolean = false
    var onOpenSplit: (() -> Unit)? = null
    var statusBarHeightDp: androidx.compose.runtime.MutableState<androidx.compose.ui.unit.Dp> = mutableStateOf(0.dp)
    lateinit var focusRequester: FocusRequester
    lateinit var focusManager: androidx.compose.ui.focus.FocusManager
    lateinit var hiddenTools: Set<String>
    lateinit var toolOrder: List<PdfReaderTool>
    lateinit var bottomTools: Set<String>
    var isPdfTabStripVisible: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(false)
    var openTabs: List<RecentFileItem> by androidx.compose.runtime.mutableStateOf(emptyList())
    var activeTabBookId: String? by androidx.compose.runtime.mutableStateOf(null)
    lateinit var effectiveFileType: FileType
    lateinit var saveStateAndExit: () -> Unit
    lateinit var showPenPlayground: PdfViewerMutableValue<Boolean>
    lateinit var viewModel: MainViewModel
    lateinit var onBookmarkClick: () -> Unit
    lateinit var onInsertPage: () -> Unit
    lateinit var onDeletePage: () -> Unit
    lateinit var saveAllData: (Boolean) -> Job
    var currentPage: Int by androidx.compose.runtime.mutableStateOf(0)
    lateinit var pendingRestorePage: PdfViewerMutableValue<Int?>
    var hasReflowFile: Boolean = false
    lateinit var uiState: ReaderScreenState
    lateinit var reflowBookId: String
    lateinit var effectivePdfUri: Uri
    lateinit var originalFileName: String
    lateinit var requestShare: () -> Unit
    lateinit var requestSaveCopy: () -> Unit
    lateinit var onPrintDocument: () -> Unit
    lateinit var currentBookId: PdfViewerMutableValue<String?>
    lateinit var onNavigateBack: () -> Unit
    lateinit var jumpHistory: PdfViewerMutableValue<SharedPdfJumpHistory>
    var isComicFile: Boolean by androidx.compose.runtime.mutableStateOf(false)
    var dockHeightPx: androidx.compose.runtime.MutableState<Float> = mutableStateOf(0f)
    var window: android.view.Window? = null
    lateinit var view: android.view.View
    lateinit var activeDocumentRenderKey: String
    var isHighlighterSnapEnabled: Boolean = false
    var isCurrentToolHighlighter: Boolean = false
    lateinit var calculateSnappedPoint: (Int, PdfPoint, PdfPoint?) -> PdfPoint
    lateinit var isLoadingDocument: PdfViewerMutableValue<Boolean>
    lateinit var errorMessage: PdfViewerMutableValue<String?>
    lateinit var pdfDocument: PdfViewerMutableValue<ReaderDocument?>
    lateinit var totalPages: PdfViewerMutableValue<Int>
    lateinit var displayMode: PdfViewerMutableValue<DisplayMode>
    lateinit var tapToNavigateEnabled: PdfViewerMutableValue<Boolean>
    lateinit var pageTurnAnimationEnabled: PdfViewerMutableValue<Boolean>
    lateinit var currentPageScale: PdfViewerMutableValue<Float>
    lateinit var isScrollLocked: PdfViewerMutableValue<Boolean>
    lateinit var rightToLeftPagination: PdfViewerMutableValue<Boolean>
    var dynamicBeyondViewportPageCount: Int by androidx.compose.runtime.mutableStateOf(0)
    lateinit var textBoxes: androidx.compose.runtime.snapshots.SnapshotStateList<PdfTextBox>
    lateinit var paginationDraggingBoxId: PdfViewerMutableValue<String?>
    var isDrawingActive: Boolean = false
    lateinit var viewConfiguration: androidx.compose.ui.platform.ViewConfiguration
    lateinit var currentActiveScale: PdfViewerMutableValue<Float>
    lateinit var currentActiveOffset: PdfViewerMutableValue<Offset>
    lateinit var showVerticalPageGap: PdfViewerMutableValue<Boolean>
    lateinit var pdfTextRepository: PdfTextRepository
    var visibleUserHighlightsByPage: Map<Int, List<PdfUserHighlight>> by androidx.compose.runtime.mutableStateOf(
        emptyMap()
    )
    val textBoxSurfaceState = PdfViewerTextBoxSurfaceState()
    var isProUser: Boolean by androidx.compose.runtime.mutableStateOf(false)
    lateinit var onDictionaryLookupStable: (String) -> Unit
    lateinit var onTranslateTextStable: (String) -> Unit
    lateinit var onSearchTextStable: (String) -> Unit
    lateinit var onInternalLinkNav: (Int) -> Unit
    lateinit var onOcrStateChange: (Boolean) -> Unit
    lateinit var onToggleBookmark: (Int) -> Unit
    var paginationPagerPageCount: Int by androidx.compose.runtime.mutableStateOf(0)
    lateinit var drawingState: PdfDrawingState
    lateinit var persistInkAnnotationsNow: (Map<Int, List<PdfAnnotation>>, Collection<PdfAnnotation>, String) -> Job
    lateinit var selectedTextBoxId: PdfViewerMutableValue<String?>
    lateinit var displayPageRatios: List<Float>
    lateinit var onHighlightAdd: (Int, Pair<Int, Int>, String, PdfHighlightColor, HighlightStyle) -> Unit
    lateinit var onHighlightUpdate: (String, PdfHighlightColor, HighlightStyle?) -> Unit
    lateinit var onHighlightDelete: (String) -> Unit
    lateinit var onNoteRequested: (String?) -> Unit
    lateinit var bookmarks: PdfViewerMutableValue<Set<PdfBookmark>>
    lateinit var persistBookmarksNow: (Set<PdfBookmark>) -> Unit
    lateinit var searchHighlightTarget: PdfViewerMutableValue<SearchResult?>
    lateinit var isOcrModelDownloading: PdfViewerMutableValue<Boolean>
    lateinit var allAnnotationsProvider: () -> Map<Int, List<PdfAnnotation>>
    var currentStrokeColor: Color = Color.Unspecified
    var currentStrokeWidth: Float = 0f
    var currentEraserStrokeWidth: Float = 0f
    lateinit var erasedAnnotationsFromStroke: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, MutableList<PdfAnnotation>>
    lateinit var pageAspectRatios: PdfViewerMutableValue<List<Float>>
    lateinit var allAnnotations: PdfViewerMutableValue<Map<Int, List<PdfAnnotation>>>
    lateinit var lastEraserPoint: PdfViewerMutableValue<PdfPoint?>
    var currentIsHighlighter: Boolean = false
    var currentSnapEnabled: Boolean = false
    lateinit var showToolSettings: PdfViewerMutableValue<Boolean>
    lateinit var virtualPages: PdfViewerMutableValue<List<VirtualPage>>
    lateinit var globalTextureTransparency: PdfViewerMutableValue<Float>
    lateinit var excludeImages: PdfViewerMutableValue<Boolean>
    lateinit var reverseColorMode: PdfViewerMutableValue<PdfReverseColorMode>
    lateinit var customHighlightColors: PdfViewerMutableValue<Map<PdfHighlightColor, Color>>
    lateinit var ttsDisplayPageIndex: PdfViewerMutableValue<Int?>
    lateinit var ttsHighlightData: PdfViewerMutableValue<TtsHighlightData?>
    lateinit var searchHighlightMode: PdfViewerMutableValue<SearchHighlightMode>
    lateinit var showAllTextHighlights: PdfViewerMutableValue<Boolean>
    lateinit var showPageNumberOverlay: PdfViewerMutableValue<Boolean>
    lateinit var selectionClearTrigger: PdfViewerMutableValue<Long>
    lateinit var resetZoomTrigger: PdfViewerMutableValue<Long>
    lateinit var lockedState: PdfViewerMutableValue<Triple<Float, Float, Float>?>
    lateinit var isStylusOnlyMode: PdfViewerMutableValue<Boolean>
    lateinit var isAutoScrollPlaying: PdfViewerMutableValue<Boolean>
    lateinit var isBubbleZoomModeActive: PdfViewerMutableValue<Boolean>
    lateinit var useOnlineDictionary: PdfViewerMutableValue<Boolean>
    lateinit var showDictionaryUpsellDialog: PdfViewerMutableValue<Boolean>
    lateinit var clickedLinkUrl: PdfViewerMutableValue<String?>
    lateinit var undoStack: androidx.compose.runtime.snapshots.SnapshotStateList<HistoryAction>
    lateinit var redoStack: androidx.compose.runtime.snapshots.SnapshotStateList<HistoryAction>
    lateinit var paginationOriginalRelSize: PdfViewerMutableValue<Size>
    lateinit var paginationDraggingSize: PdfViewerMutableValue<Size>
    lateinit var paginationDragPageHeight: PdfViewerMutableValue<Float>
    lateinit var paginationDraggingOffset: PdfViewerMutableValue<Offset>
    lateinit var highlightColorPickerInitialSlot: PdfViewerMutableValue<PdfHighlightColor>
    lateinit var showHighlightColorPicker: PdfViewerMutableValue<Boolean>
    lateinit var poppedUpPanelBitmap: PdfViewerMutableValue<Bitmap?>
    var isPdfDarkMode: Boolean = false
    // These values are produced by the setup sibling but consumed by independently recomposed
    // document/chrome groups. Keep them in stable Compose state so inset animations and system-bar
    // visibility changes invalidate the consumers exactly like the pre-extraction locals did.
    var verticalHeaderHeight: androidx.compose.runtime.MutableState<androidx.compose.ui.unit.Dp> = mutableStateOf(0.dp)
    var verticalFooterHeight: androidx.compose.runtime.MutableState<androidx.compose.ui.unit.Dp> = mutableStateOf(0.dp)
    lateinit var onZoomChangeStable: (Float) -> Unit
    lateinit var onHighlightLoadingStable: (Boolean) -> Unit
    lateinit var onShowDictionaryUpsellDialogStable: () -> Unit
    lateinit var onLinkClickedStable: (String) -> Unit
    lateinit var onInternalLinkNavStable: (Int) -> Unit
    lateinit var onBookmarkClickStable: (Int) -> Unit
    lateinit var onOcrStateChangeStable: (Boolean) -> Unit
    lateinit var onGetOcrSearchRectsStable: suspend (Int, String) -> List<RectF>
    var bottomScrollLimitPx: androidx.compose.runtime.MutableState<Float> = mutableStateOf(0f)
    var topScrollLimitPx: androidx.compose.runtime.MutableState<Float> = mutableStateOf(0f)
    lateinit var onAutoScrollInteraction: () -> Unit
    lateinit var visibleUserHighlights: List<PdfUserHighlight>
    lateinit var isAutoScrollTempPaused: PdfViewerMutableValue<Boolean>
    lateinit var autoScrollSpeed: PdfViewerMutableValue<Float>
    lateinit var isMusicianMode: PdfViewerMutableValue<Boolean>
    lateinit var isAutoScrollModeActive: PdfViewerMutableValue<Boolean>
    lateinit var autoScrollResumeJob: androidx.compose.runtime.MutableState<Job?>
    var topOverlayInset: androidx.compose.runtime.MutableState<androidx.compose.ui.unit.Dp> = mutableStateOf(0.dp)
    lateinit var ocrLanguage: PdfViewerMutableValue<OcrLanguage>
    var bubbleZoomDownloadProgress: Float? = null
    lateinit var systemUiMode: PdfViewerMutableValue<SystemUiMode>
    var navBarHeight: androidx.compose.runtime.MutableState<Int> = mutableStateOf(0)
    lateinit var sliderCurrentPage: PdfViewerMutableValue<Float>
    lateinit var scrubDebounceJob: androidx.compose.runtime.MutableState<Job?>
    lateinit var isFastScrubbing: PdfViewerMutableValue<Boolean>
    lateinit var showThemePanel: PdfViewerMutableValue<Boolean>
    lateinit var showVisualOptionsSheet: PdfViewerMutableValue<Boolean>
    lateinit var showBrightnessSheet: PdfViewerMutableValue<Boolean>
    lateinit var showScreenOrientationSheet: PdfViewerMutableValue<Boolean>
    lateinit var showTtsControlsSheet: PdfViewerMutableValue<Boolean>
    lateinit var showTtsSettingsSheet: PdfViewerMutableValue<Boolean>
    lateinit var showTtsReplacementsSheet: PdfViewerMutableValue<Boolean>
    lateinit var showDictionarySettingsSheet: PdfViewerMutableValue<Boolean>
    lateinit var showSummarizationUpsellDialog: PdfViewerMutableValue<Boolean>
    lateinit var showAiHubSheet: PdfViewerMutableValue<Boolean>
    lateinit var showPermissionRationaleDialog: PdfViewerMutableValue<Boolean>
    lateinit var showInsufficientCreditsDialog: PdfViewerMutableValue<Boolean>
    lateinit var showBubbleZoomDownloadDialog: PdfViewerMutableValue<Boolean>
    lateinit var showNewTabSheet: PdfViewerMutableValue<Boolean>
    lateinit var showFileInfoDialog: PdfViewerMutableValue<Boolean>
    lateinit var showSaveDialog: PdfViewerMutableValue<Boolean>
    lateinit var showShareDialog: PdfViewerMutableValue<Boolean>
    lateinit var showOcrLanguageDialog: PdfViewerMutableValue<Boolean>
    lateinit var showReindexDialog: PdfViewerMutableValue<OcrLanguage?>
    lateinit var showCustomizeToolsSheet: PdfViewerMutableValue<Boolean>
    lateinit var pendingActionAfterOcrSelection: PdfViewerMutableValue<(() -> Unit)?>
    lateinit var pendingSaveMode: PdfViewerMutableValue<SaveMode?>
    lateinit var showBars: PdfViewerMutableValue<Boolean>
    lateinit var sliderStartPage: PdfViewerMutableValue<Int>
    lateinit var isHighlightingLoading: PdfViewerMutableValue<Boolean>
    lateinit var isAutoPagingForTts: PdfViewerMutableValue<Boolean>
    lateinit var isKeepScreenOn: PdfViewerMutableValue<Boolean>
    var isBookmarked: Boolean = false
    var isReflowingThisBook: Boolean = false
    lateinit var isPrintBlockedForPasswordProtectedPdf: PdfViewerMutableValue<Boolean>
    var isOss: Boolean = false
    lateinit var hasSelectedOcrLanguage: PdfViewerMutableValue<Boolean>
    lateinit var initialScrollDone: PdfViewerMutableValue<Boolean>
    lateinit var tabStateMap: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Int>
    var reflowProgressValue: Float = 0f
    lateinit var isBackgroundIndexing: PdfViewerMutableValue<Boolean>
    lateinit var backgroundIndexingProgress: PdfViewerMutableValue<Float>
    lateinit var isOcrScanning: PdfViewerMutableValue<Boolean>
    lateinit var smartSearchResult: PdfViewerMutableValue<SmartSearchResult?>
    lateinit var currentPdfSearchResult: PdfViewerMutableValue<SearchResult?>
    lateinit var dockLocation: PdfViewerMutableValue<DockLocation>
    lateinit var dockOffset: PdfViewerMutableValue<Offset>
    lateinit var highlighterPalette: List<Color>
    lateinit var penPalette: List<Color>
    var activeToolThickness: Float = 0f
    var fountainPenColor: Color = Color.Unspecified
    var markerColor: Color = Color.Unspecified
    var pencilColor: Color = Color.Unspecified
    var highlighterColor: Color = Color.Unspecified
    var highlighterRoundColor: Color = Color.Unspecified
    lateinit var annotationSettingsRepo: AnnotationSettingsRepository
    lateinit var snapPreviewLocation: PdfViewerMutableValue<DockLocation?>
    var dockHeight: androidx.compose.ui.unit.Dp = 0.dp
    lateinit var isDockDragging: PdfViewerMutableValue<Boolean>
    lateinit var isDockMinimized: PdfViewerMutableValue<Boolean>
    var dockPenColor: Color = Color.Unspecified
    var dockHighlighterColor: Color = Color.Unspecified
    lateinit var lastPenTool: InkType
    lateinit var lastHighlighterTool: InkType
    lateinit var showZoomIndicator: PdfViewerMutableValue<Boolean>
    var zoomIndicatorPercentage: Int = 0
    lateinit var toolSettings: AnnotationToolSettings
    lateinit var onInsertTextBox: () -> Unit
    lateinit var customFonts: List<CustomFontEntity>
    lateinit var ttsOverlaySize: PdfViewerMutableValue<ReaderTtsOverlaySize>
    lateinit var currentTtsMode: PdfViewerMutableValue<TtsPlaybackManager.TtsMode>
    lateinit var isAutoScrollCollapsed: PdfViewerMutableValue<Boolean>
    lateinit var autoScrollMinSpeed: PdfViewerMutableValue<Float>
    lateinit var autoScrollMaxSpeed: PdfViewerMutableValue<Float>
    lateinit var autoScrollUseSlider: PdfViewerMutableValue<Boolean>
    lateinit var isAutoScrollLocal: PdfViewerMutableValue<Boolean>
    lateinit var onSingleTapStable: () -> Unit
    lateinit var updateSpeed: (Float) -> Unit
    lateinit var updateMinSpeed: (Float) -> Unit
    lateinit var updateMaxSpeed: (Float) -> Unit
    lateinit var onToggleAutoScrollMode: (Boolean) -> Unit
    lateinit var triggerAutoScrollTempPause: (Long) -> Unit
    lateinit var paginationDisplayPageForPagerPage: (Int) -> Int
    lateinit var scrollPaginationToDisplayPage: PdfViewerSuspendPageAction
    lateinit var animatePaginationToDisplayPage: PdfViewerSuspendPageAction
    lateinit var currentPaginationDisplayPage: () -> Int
    lateinit var currentPdfDisplayPage: () -> Int
    lateinit var detectSpeechBubblesForPage: PdfViewerSpeechBubbleDetector
    lateinit var clearJumpHistory: () -> Unit
    lateinit var navigateToJumpHistoryPage: (Int) -> Unit
    lateinit var navigateToPdfPage: (Int, PdfNavigationReason, Boolean) -> Unit
    lateinit var isAnnotationHit: (PdfAnnotation, PdfPoint, PdfPoint?, Float, Float) -> Boolean
    lateinit var navigateToPdfSearchResult: (SearchResult) -> Unit
    lateinit var showBanner: PdfViewerBanner
    lateinit var aiDefinitionResult: PdfViewerMutableValue<AiDefinitionResult?>
    var canShowPdfTabs: Boolean = false
    lateinit var currentThemeId: PdfViewerMutableValue<String>
    lateinit var customThemes: PdfViewerMutableValue<List<ReaderTheme>>
    lateinit var documentPassword: PdfViewerMutableValue<String?>
    lateinit var effectiveReaderBookTitle: String
    lateinit var flatTableOfContents: PdfViewerMutableValue<List<TocEntry>>
    lateinit var highlightToNoteId: PdfViewerMutableValue<String?>
    lateinit var isAiDefinitionLoading: PdfViewerMutableValue<Boolean>
    lateinit var isPasswordError: PdfViewerMutableValue<Boolean>
    lateinit var isShareLoading: PdfViewerMutableValue<Boolean>
    lateinit var isSummarizationLoading: PdfViewerMutableValue<Boolean>
    var isTabsEnabled: Boolean = false
    lateinit var launchAnnotatedSaveCopy: () -> Unit
    lateinit var launchOriginalSaveCopy: () -> Unit
    lateinit var onUpdateBottomTools: (Set<String>) -> Unit
    lateinit var onUpdateHiddenTools: (Set<String>) -> Unit
    lateinit var onUpdateToolOrder: (List<PdfReaderTool>) -> Unit
    lateinit var pdfFirstPageStandaloneInSpread: PdfViewerMutableValue<Boolean>
    lateinit var pdfPageSpreadMode: PdfViewerMutableValue<com.aryan.reader.shared.reader.ReaderPageSpreadMode>
    lateinit var pendingPaginationSpreadRestorePage: PdfViewerMutableValue<Int?>
    lateinit var readerBrightnessSettings: PdfViewerMutableValue<com.aryan.reader.ReaderBrightnessSettings>
    lateinit var requestNotificationPermission: () -> Unit
    lateinit var startTtsForOverlay: () -> Unit
    lateinit var onNavigateToPro: () -> Unit
    lateinit var clipboardManager: androidx.compose.ui.platform.ClipboardManager
    var paneInitialPage: Int? = null
    var effectiveInitialPage: Int? = null
    var effectiveInitialBookmarksJson: String? = null
    lateinit var documentMetadataTitle: PdfViewerMutableValue<String?>
    var activeLibraryItem: RecentFileItem? = null
    var isTtsPlaybackForThisPane: Boolean = false
    lateinit var pendingNoteForNewHighlight: PdfViewerMutableValue<Boolean>
    var eraserToolThickness: Float = 0f
    lateinit var annotationRepository: PdfAnnotationRepository
    lateinit var textBoxRepository: PdfTextBoxRepository
    lateinit var highlightRepository: PdfHighlightRepository
    lateinit var annotationSession: PdfViewerMutableValue<SharedPdfAnnotationSessionState>
    lateinit var richTextRepository: PdfRichTextRepository
    lateinit var pfdState: PdfViewerMutableValue<ParcelFileDescriptor?>
    lateinit var pdfiumCore: io.legere.pdfiumandroid.suspend.PdfiumCoreKt
    lateinit var loadedPageLayoutBookId: PdfViewerMutableValue<String?>
    lateinit var pageLayoutMutationVersion: PdfViewerMutableValue<Long>
    lateinit var isDocumentReady: PdfViewerMutableValue<Boolean>
    lateinit var lifecycleOwner: androidx.lifecycle.LifecycleOwner
    lateinit var paginationPagerPageForDisplayPage: (Int) -> Int
    lateinit var screenOrientationMode: PdfViewerMutableValue<com.aryan.reader.shared.reader.ReaderScreenOrientationMode>
    var selectedBookIdForPane: String? = null
    lateinit var selectedDictPackage: PdfViewerMutableValue<String?>
    lateinit var selectedSearchPackage: PdfViewerMutableValue<String?>
    lateinit var selectedTextForAi: PdfViewerMutableValue<String?>
    lateinit var selectedTranslatePackage: PdfViewerMutableValue<String?>
    lateinit var shareAnnotatedPdf: () -> Unit
    lateinit var shareOriginalPdf: () -> Unit
    lateinit var sheetState: androidx.compose.material3.SheetState
    lateinit var showAiDefinitionPopup: PdfViewerMutableValue<Boolean>
    lateinit var showPasswordDialog: PdfViewerMutableValue<Boolean>
    lateinit var showTopTabStrip: PdfViewerMutableValue<Boolean>
    lateinit var summarizationResult: PdfViewerMutableValue<SummarizationResult?>
    lateinit var summaryCacheManager: SummaryCacheManager
    lateinit var ttsReplacementPreferences: PdfViewerMutableValue<ReaderTtsReplacementPreferences>
    lateinit var updateReaderBrightness: (com.aryan.reader.ReaderBrightnessSettings) -> Unit
    lateinit var updateTtsReplacementPreferences: (ReaderTtsReplacementPreferences) -> Unit
    lateinit var summarizeCurrentPage: suspend (String?, (SummarizationResult) -> Unit, () -> Unit) -> Unit
    lateinit var uriHandler: androidx.compose.ui.platform.UriHandler
    lateinit var userHighlights: androidx.compose.runtime.snapshots.SnapshotStateList<PdfUserHighlight>
    var activity: Activity? = null
    var initialPage: Int? = null
    var isPaneFocused: Boolean = true

}

private class PdfViewerMutableValue<T>(
    private val getter: () -> T,
    private val setter: (T) -> Unit,
) : kotlin.properties.ReadWriteProperty<Any?, T> {
    override operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = getter()

    override operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
        setter(value)
    }
}

private fun <T> pdfViewerMutableValue(
    getter: () -> T,
    setter: (T) -> Unit,
): PdfViewerMutableValue<T> = PdfViewerMutableValue(getter, setter)

// Deliberately non-inline: the bounded calls above compile to small capture
// methods instead of inlining every bridge write into PdfViewerScreenContent.
private fun bindPdfViewerSurfaceChunk(block: () -> Unit) {
    block()
}

private class PdfViewerBanner(
    private val block: (String, Boolean, Boolean) -> Unit,
) {
    operator fun invoke(
        message: String,
        isError: Boolean = false,
        isPersistent: Boolean = false,
    ) = block(message, isError, isPersistent)
}

private class PdfViewerSpeechBubbleDetector(
    private val block: suspend (Int, Bitmap, Boolean) -> List<SpeechBubble>,
) {
    suspend operator fun invoke(
        sourcePageIndex: Int,
        fallbackBitmap: Bitmap,
        allowHighQualityFallback: Boolean = true,
    ): List<SpeechBubble> = block(sourcePageIndex, fallbackBitmap, allowHighQualityFallback)
}

private class PdfViewerSuspendPageAction(
    private val block: suspend (Int) -> Unit,
) {
    suspend operator fun invoke(displayPage: Int) {
        block(displayPage)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.PdfViewerSurfaceContent(
    surfaceState: PdfViewerSurfaceState,
    stylusButtonHovering: Boolean,
) {
    PdfViewerDocumentViewport(
        surfaceState = surfaceState,
        stylusButtonHovering = stylusButtonHovering,
    )
    PdfViewerChromeSurface(surfaceState = surfaceState)
}

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.PdfViewerDocumentViewport(
    surfaceState: PdfViewerSurfaceState,
    stylusButtonHovering: Boolean,
) {
    val richTextController = surfaceState.richTextController
    val selectedTool = surfaceState.selectedTool.value
    val density = surfaceState.density.value
    val searchState = surfaceState.searchState
    val activeTheme = surfaceState.activeTheme.value
    val pdfSpreadSettings = surfaceState.pdfSpreadSettings
    val isTtsPlayingOrLoading = surfaceState.isTtsPlayingOrLoading
    val totalDisplayPages = surfaceState.totalDisplayPages
    val pagerState = surfaceState.pagerState
    val verticalReaderState = surfaceState.verticalReaderState
    var isPageSliderVisible by surfaceState.isPageSliderVisible
    var isEditMode by surfaceState.isEditMode
    val startTtsWithPermissionCheck = surfaceState.startTtsWithPermissionCheck
    val activeDocumentRenderKey = surfaceState.activeDocumentRenderKey
    val isHighlighterSnapEnabled = surfaceState.isHighlighterSnapEnabled
    val isCurrentToolHighlighter = surfaceState.isCurrentToolHighlighter
    val calculateSnappedPoint = surfaceState.calculateSnappedPoint
    var isLoadingDocument by surfaceState.isLoadingDocument
    var errorMessage by surfaceState.errorMessage
    var pdfDocument by surfaceState.pdfDocument
    var totalPages by surfaceState.totalPages
    var displayMode by surfaceState.displayMode
    var currentPageScale by surfaceState.currentPageScale
    var pageTurnAnimationEnabled by surfaceState.pageTurnAnimationEnabled
    var isScrollLocked by surfaceState.isScrollLocked
    var rightToLeftPagination by surfaceState.rightToLeftPagination
    val dynamicBeyondViewportPageCount = surfaceState.dynamicBeyondViewportPageCount
    val textBoxes = surfaceState.textBoxes
    var paginationDraggingBoxId by surfaceState.paginationDraggingBoxId
    val isDrawingActive = surfaceState.isDrawingActive
    var currentActiveScale by surfaceState.currentActiveScale
    var currentActiveOffset by surfaceState.currentActiveOffset
    var showVerticalPageGap by surfaceState.showVerticalPageGap
    val visibleUserHighlightsByPage = surfaceState.visibleUserHighlightsByPage
    val textBoxSurfaceData = surfaceState.textBoxSurfaceState.data.value
    val visibleTextBoxesByPage = textBoxSurfaceData.byPage
    val isProUser = surfaceState.isProUser
    val onDictionaryLookupStable = surfaceState.onDictionaryLookupStable
    val onTranslateTextStable = surfaceState.onTranslateTextStable
    val onSearchTextStable = surfaceState.onSearchTextStable
    val onOcrStateChange = surfaceState.onOcrStateChange
    val drawingState = surfaceState.drawingState
    val persistInkAnnotationsNow = surfaceState.persistInkAnnotationsNow
    var selectedTextBoxId by surfaceState.selectedTextBoxId
    val displayPageRatios = surfaceState.displayPageRatios
    val onHighlightAdd = surfaceState.onHighlightAdd
    val onHighlightUpdate = surfaceState.onHighlightUpdate
    val onHighlightDelete = surfaceState.onHighlightDelete
    val onNoteRequested = surfaceState.onNoteRequested
    var bookmarks by surfaceState.bookmarks
    var searchHighlightTarget by surfaceState.searchHighlightTarget
    var isOcrModelDownloading by surfaceState.isOcrModelDownloading
    val allAnnotationsProvider = surfaceState.allAnnotationsProvider
    val currentStrokeColor = surfaceState.currentStrokeColor
    val currentStrokeWidth = surfaceState.currentStrokeWidth
    val currentEraserStrokeWidth = surfaceState.currentEraserStrokeWidth
    val erasedAnnotationsFromStroke = surfaceState.erasedAnnotationsFromStroke
    var pageAspectRatios by surfaceState.pageAspectRatios
    var allAnnotations by surfaceState.allAnnotations
    var lastEraserPoint by surfaceState.lastEraserPoint
    val currentIsHighlighter = surfaceState.currentIsHighlighter
    val currentSnapEnabled = surfaceState.currentSnapEnabled
    var showToolSettings by surfaceState.showToolSettings
    var virtualPages by surfaceState.virtualPages
    var globalTextureTransparency by surfaceState.globalTextureTransparency
    var excludeImages by surfaceState.excludeImages
    var reverseColorMode by surfaceState.reverseColorMode
    var customHighlightColors by surfaceState.customHighlightColors
    var ttsDisplayPageIndex by surfaceState.ttsDisplayPageIndex
    var ttsHighlightData by surfaceState.ttsHighlightData
    var searchHighlightMode by surfaceState.searchHighlightMode
    var showAllTextHighlights by surfaceState.showAllTextHighlights
    var showPageNumberOverlay by surfaceState.showPageNumberOverlay
    var resetZoomTrigger by surfaceState.resetZoomTrigger
    var lockedState by surfaceState.lockedState
    var isStylusOnlyMode by surfaceState.isStylusOnlyMode
    var isAutoScrollPlaying by surfaceState.isAutoScrollPlaying
    var isBubbleZoomModeActive by surfaceState.isBubbleZoomModeActive
    val undoStack = surfaceState.undoStack
    val redoStack = surfaceState.redoStack
    var paginationDraggingSize by surfaceState.paginationDraggingSize
    var paginationDragPageHeight by surfaceState.paginationDragPageHeight
    var paginationDraggingOffset by surfaceState.paginationDraggingOffset
    var showHighlightColorPicker by surfaceState.showHighlightColorPicker
    val isPdfDarkMode = surfaceState.isPdfDarkMode
    val verticalHeaderHeight = surfaceState.verticalHeaderHeight.value
    val verticalFooterHeight = surfaceState.verticalFooterHeight.value
    val onZoomChangeStable = surfaceState.onZoomChangeStable
    val onHighlightLoadingStable = surfaceState.onHighlightLoadingStable
    val onShowDictionaryUpsellDialogStable = surfaceState.onShowDictionaryUpsellDialogStable
    val onLinkClickedStable = surfaceState.onLinkClickedStable
    val onInternalLinkNavStable = surfaceState.onInternalLinkNavStable
    val onBookmarkClickStable = surfaceState.onBookmarkClickStable
    val onOcrStateChangeStable = surfaceState.onOcrStateChangeStable
    val onGetOcrSearchRectsStable = surfaceState.onGetOcrSearchRectsStable
    val visibleTextBoxes = textBoxSurfaceData.all
    val bottomScrollLimitPx = surfaceState.bottomScrollLimitPx.value
    val topScrollLimitPx = surfaceState.topScrollLimitPx.value
    val onAutoScrollInteraction = surfaceState.onAutoScrollInteraction
    val visibleUserHighlights = surfaceState.visibleUserHighlights
    var isAutoScrollTempPaused by surfaceState.isAutoScrollTempPaused
    var autoScrollSpeed by surfaceState.autoScrollSpeed
    val onSingleTapStable = surfaceState.onSingleTapStable
    val paginationDisplayPageForPagerPage = surfaceState.paginationDisplayPageForPagerPage
    val detectSpeechBubblesForPage = surfaceState.detectSpeechBubblesForPage
    val isAnnotationHit = surfaceState.isAnnotationHit
    val boxConstraints = constraints
    val hiddenRichTextInputEnabled = isPdfRichTextInputEnabled(
        isEditMode = isEditMode,
        selectedTool = selectedTool,
        selectedTextBoxId = selectedTextBoxId,
    )

    LaunchedEffect(isEditMode, selectedTool, selectedTextBoxId, isDrawingActive) {
        Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).d(
            "event=viewport_input_state selectedTool=$selectedTool pageRichTextEditMode=$isEditMode " +
                "textBoxEditMode=$isDrawingActive selectedTextBoxId=${selectedTextBoxId ?: "none"} " +
                "hiddenRichTextInputEnabled=$hiddenRichTextInputEnabled"
        )
    }

    if (richTextController != null) {
        SharedPdfRichTextHiddenInput(
            controller = richTextController.sharedDelegate,
            enabled = hiddenRichTextInputEnabled,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(start = 16.dp, bottom = 120.dp)
        )
    }

    // --- Main Content Area ---
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val documentPresentation = selectMobilePdfDocumentPresentation(
            loading = isLoadingDocument,
            errorPresent = errorMessage != null,
            documentPresent = pdfDocument != null,
            totalPages = totalPages,
        )
        when {
            documentPresentation == MobilePdfDocumentPresentation.LOADING -> {
                SharedMobileReaderLoadingIndicator()
            }

            documentPresentation == MobilePdfDocumentPresentation.ERROR -> {
                SharedMobileReaderCenteredError(
                    message = errorMessage ?: stringResource(R.string.error_failed_load_pdf),
                )
            }

            documentPresentation == MobilePdfDocumentPresentation.READY -> {
                when (displayMode) {
                    DisplayMode.PAGINATION -> {
                        val paginationPageState = remember(
                            surfaceState,
                            activeDocumentRenderKey,
                            pdfDocument,
                            boxConstraints.maxWidth,
                            boxConstraints.maxHeight,
                            stylusButtonHovering,
                        ) {
                            PdfViewerPaginationPageState(
                                surfaceState = surfaceState,
                                stablePdfDocument = StableHolder(pdfDocument!!),
                                boxMaxWidth = boxConstraints.maxWidth,
                                boxMaxHeight = boxConstraints.maxHeight,
                                stylusButtonHovering = stylusButtonHovering,
                            )
                        }

                        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                            var pageTurnTouchY by remember { mutableStateOf<Float?>(null) }
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds()
                                    .then(
                                        // Android benchmark: capture every gesture's touch Y so
                                        // the realistic curl folds from the corner the reader touched.
                                        if (pageTurnAnimationEnabled) {
                                            Modifier.pointerInput(Unit) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                                        event.changes.firstOrNull { it.pressed }?.let { down ->
                                                            pageTurnTouchY = down.position.y
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                key = { page ->
                                    "$activeDocumentRenderKey:${pdfSpreadSettings.pageSpreadMode}:${pdfSpreadSettings.pdfFirstPageStandaloneInSpread}:$page:${paginationDisplayPageForPagerPage(page)}"
                                },
                                beyondViewportPageCount = dynamicBeyondViewportPageCount,
                                reverseLayout = rightToLeftPagination,
                                userScrollEnabled = run {
                                    (currentPageScale == 1f || (isScrollLocked && displayMode == DisplayMode.PAGINATION)) &&
                                        !isTtsPlayingOrLoading &&
                                        !searchState.isSearchActive &&
                                        !isPageSliderVisible &&
                                        paginationDraggingBoxId == null
                                }
                            ) { pagerPageIndex ->
                                PdfViewerPaginationPage(
                                    paginationPageState = paginationPageState,
                                    pagerPageIndex = pagerPageIndex,
                                    pageTurnAnimationEnabled = pageTurnAnimationEnabled,
                                    pageTurnTouchY = pageTurnTouchY,
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
                        val currentEraserStrokeWidthState by rememberUpdatedState(
                            currentEraserStrokeWidth
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
                                            val eraserStrokeWidth = resolveEraserStrokeWidth(
                                                isEraserOverride,
                                                currentStrokeWidthState,
                                                currentEraserStrokeWidthState
                                            )

                                            val aspectRatio = pageAspectRatios.getOrElse(pageIndex) { 1f }
                                            val existing = allAnnotations[pageIndex] ?: emptyList()
                                            val toRemove = existing.filter {
                                                isAnnotationHit(it, point, lastEraserPoint, aspectRatio, eraserStrokeWidth)
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
                                    val eraserStrokeWidth = resolveEraserStrokeWidth(
                                        isEraserOverride,
                                        currentStrokeWidthState,
                                        currentEraserStrokeWidthState
                                    )
                                    val aspectRatio = pageAspectRatios.getOrElse(pageIndex) { 1f }
                                    val existing = allAnnotations[pageIndex] ?: emptyList()
                                    val toRemove = existing.filter {
                                        isAnnotationHit(it, point, lastEraserPoint, aspectRatio, eraserStrokeWidth)
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
                            val docHolder = remember(activeDocumentRenderKey, pdfDocument) {
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
                                documentKey = activeDocumentRenderKey,
                                activeTheme = activeTheme,
                                activeTextureAlpha = 1f - globalTextureTransparency,
                                excludeImages = excludeImages,
                                reverseColorMode = reverseColorMode,
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
                                userHighlights = visibleUserHighlights,
                                userHighlightsByPage = visibleUserHighlightsByPage,
                                onHighlightAdd = onHighlightAdd,
                                onHighlightUpdate = onHighlightUpdate,
                                onHighlightDelete = onHighlightDelete,
                                onNoteRequested = onNoteRequested,
                                onTts = { pageIdx, charIdx -> startTtsWithPermissionCheck(pageIdx, charIdx) },
                                activeToolThickness = currentStrokeWidthState,
                                eraserToolThickness = currentEraserStrokeWidthState,
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
                                        val nextAnnotations =
                                            allAnnotations + (pageIdx to (existing + finalAnnotation))
                                        allAnnotations = nextAnnotations
                                        persistInkAnnotationsNow(
                                            nextAnnotations,
                                            emptyList(),
                                            "draw_end"
                                        )
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
                                        persistInkAnnotationsNow(
                                            allAnnotations,
                                            removalMap.values.flatten(),
                                            "erase_end"
                                        )
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
                                stylusButtonHovering = stylusButtonHovering,
                                isEditMode = isDrawingActive,
                                textBoxes = visibleTextBoxes,
                                textBoxesByPage = visibleTextBoxesByPage,
                                selectedTextBoxId = selectedTextBoxId,
                                onTextBoxChange = { updatedBox ->
                                    val idx = textBoxes.indexOfFirst { it.id == updatedBox.id }
                                    val previousLength = textBoxes.getOrNull(idx)?.text?.length
                                    Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).d(
                                        "event=viewer_value_change path=vertical id=${updatedBox.id} " +
                                            "page=${updatedBox.pageIndex} oldLength=${previousLength ?: -1} " +
                                            "newLength=${updatedBox.text.length} matched=${idx != -1} " +
                                            "selected=${updatedBox.id == selectedTextBoxId} textBoxEditMode=$isDrawingActive"
                                    )
                                    if (idx != -1) textBoxes[idx] = updatedBox
                                },
                                onTextBoxSelect = { id ->
                                    Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).d(
                                        "event=viewer_select path=vertical id=$id " +
                                            "selectedBefore=${selectedTextBoxId ?: "none"} textBoxEditMode=$isDrawingActive"
                                    )
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
                                showPageGap = showVerticalPageGap,
                                showPageNumberOverlay = showPageNumberOverlay,
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


}

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.PdfViewerChromeSurface(
    surfaceState: PdfViewerSurfaceState,
) {
    PdfViewerChromeMusicianAndIndicators(surfaceState)
    PdfViewerChromeNavigation(surfaceState)
    PdfViewerChromeBottomAndEditing(surfaceState)
    PdfViewerChromeTts(surfaceState)
}

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.PdfViewerChromeMusicianAndIndicators(
    surfaceState: PdfViewerSurfaceState,
) {
        val density = surfaceState.density.value
        val verticalReaderState = surfaceState.verticalReaderState
        val coroutineScope = surfaceState.coroutineScope
        var totalPages by surfaceState.totalPages
        var isOcrModelDownloading by surfaceState.isOcrModelDownloading
        var isMusicianMode by surfaceState.isMusicianMode
        var isAutoScrollModeActive by surfaceState.isAutoScrollModeActive
        val topOverlayInset = surfaceState.topOverlayInset.value
        var ocrLanguage by surfaceState.ocrLanguage
        val bubbleZoomDownloadProgress = surfaceState.bubbleZoomDownloadProgress
        val triggerAutoScrollTempPause = surfaceState.triggerAutoScrollTempPause
    val boxMaxWidthFloat = constraints.maxWidth.toFloat()
    val boxMaxHeightFloat = constraints.maxHeight.toFloat()

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
}

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.PdfViewerChromeNavigation(
    surfaceState: PdfViewerSurfaceState,
) {
        val richTextController = surfaceState.richTextController
        val density = surfaceState.density.value
        val searchState = surfaceState.searchState
        val activeTheme = surfaceState.activeTheme.value
        val pdfSliderChromeVisible = surfaceState.pdfSliderChromeVisible
        val pdfSpreadSettings = surfaceState.pdfSpreadSettings
        val ownsPaneGlobals = surfaceState.ownsPaneGlobals
        val drawerState = surfaceState.drawerState
        val ttsState = surfaceState.ttsState
        val bookId = surfaceState.bookId
        val ttsController = surfaceState.ttsController
        val isTtsPlayingOrLoading = surfaceState.isTtsPlayingOrLoading
        val context = surfaceState.context
        val totalDisplayPages = surfaceState.totalDisplayPages
        val pagerState = surfaceState.pagerState
        val verticalReaderState = surfaceState.verticalReaderState
        var isPageSliderVisible by surfaceState.isPageSliderVisible
        val coroutineScope = surfaceState.coroutineScope
        val executeWithOcrCheck = surfaceState.executeWithOcrCheck
        var isEditMode by surfaceState.isEditMode
        val keyboardController = surfaceState.keyboardController
        val isTtsSessionActive = surfaceState.isTtsSessionActive
        val startTtsWithPermissionCheck = surfaceState.startTtsWithPermissionCheck
        val isSplitPane = surfaceState.isSplitPane
        val onOpenSplit = surfaceState.onOpenSplit
        val statusBarHeightDp = surfaceState.statusBarHeightDp.value
        val focusRequester = surfaceState.focusRequester
        val focusManager = surfaceState.focusManager
        val hiddenTools = surfaceState.hiddenTools
        val toolOrder = surfaceState.toolOrder
        val bottomTools = surfaceState.bottomTools
        val isPdfTabStripVisible = surfaceState.isPdfTabStripVisible.value
        val openTabs = surfaceState.openTabs
        val activeTabBookId = surfaceState.activeTabBookId
        val effectiveFileType = surfaceState.effectiveFileType
        val saveStateAndExit = surfaceState.saveStateAndExit
        var showPenPlayground by surfaceState.showPenPlayground
        val viewModel = surfaceState.viewModel
        val onBookmarkClick = surfaceState.onBookmarkClick
        val onInsertPage = surfaceState.onInsertPage
        val onDeletePage = surfaceState.onDeletePage
        val saveAllData = surfaceState.saveAllData
        val currentPage = surfaceState.currentPage
        var pendingRestorePage by surfaceState.pendingRestorePage
        val hasReflowFile = surfaceState.hasReflowFile
        val uiState = surfaceState.uiState
        val reflowBookId = surfaceState.reflowBookId
        val effectivePdfUri = surfaceState.effectivePdfUri
        val originalFileName = surfaceState.originalFileName
        val requestShare = surfaceState.requestShare
        val requestSaveCopy = surfaceState.requestSaveCopy
        val onPrintDocument = surfaceState.onPrintDocument
        var currentBookId by surfaceState.currentBookId
        val onNavigateBack = surfaceState.onNavigateBack
        var jumpHistory by surfaceState.jumpHistory
        var isLoadingDocument by surfaceState.isLoadingDocument
        var errorMessage by surfaceState.errorMessage
        var pdfDocument by surfaceState.pdfDocument
        var totalPages by surfaceState.totalPages
        var displayMode by surfaceState.displayMode
        var tapToNavigateEnabled by surfaceState.tapToNavigateEnabled
        var pageTurnAnimationEnabled by surfaceState.pageTurnAnimationEnabled
        var currentPageScale by surfaceState.currentPageScale
        var isScrollLocked by surfaceState.isScrollLocked
        var rightToLeftPagination by surfaceState.rightToLeftPagination
        var currentActiveScale by surfaceState.currentActiveScale
        var currentActiveOffset by surfaceState.currentActiveOffset
        val pdfTextRepository = surfaceState.pdfTextRepository
        val onToggleBookmark = surfaceState.onToggleBookmark
        var allAnnotations by surfaceState.allAnnotations
        var virtualPages by surfaceState.virtualPages
        var searchHighlightMode by surfaceState.searchHighlightMode
        var showAllTextHighlights by surfaceState.showAllTextHighlights
        var lockedState by surfaceState.lockedState
        var isAutoScrollPlaying by surfaceState.isAutoScrollPlaying
        var showDictionaryUpsellDialog by surfaceState.showDictionaryUpsellDialog
        val undoStack = surfaceState.undoStack
        val redoStack = surfaceState.redoStack
        var isMusicianMode by surfaceState.isMusicianMode
        var isAutoScrollModeActive by surfaceState.isAutoScrollModeActive
        val topOverlayInset = surfaceState.topOverlayInset.value
        var systemUiMode by surfaceState.systemUiMode
        val navBarHeight = surfaceState.navBarHeight.value
        var sliderCurrentPage by surfaceState.sliderCurrentPage
        val scrubDebounceJob = surfaceState.scrubDebounceJob
        var isFastScrubbing by surfaceState.isFastScrubbing
        var showThemePanel by surfaceState.showThemePanel
        var showVisualOptionsSheet by surfaceState.showVisualOptionsSheet
        var showBrightnessSheet by surfaceState.showBrightnessSheet
        var showScreenOrientationSheet by surfaceState.showScreenOrientationSheet
        var showTtsControlsSheet by surfaceState.showTtsControlsSheet
        var showTtsSettingsSheet by surfaceState.showTtsSettingsSheet
        var showTtsReplacementsSheet by surfaceState.showTtsReplacementsSheet
        var showDictionarySettingsSheet by surfaceState.showDictionarySettingsSheet
        var showSummarizationUpsellDialog by surfaceState.showSummarizationUpsellDialog
        var showAiHubSheet by surfaceState.showAiHubSheet
        var showPermissionRationaleDialog by surfaceState.showPermissionRationaleDialog
        var showInsufficientCreditsDialog by surfaceState.showInsufficientCreditsDialog
        var showBubbleZoomDownloadDialog by surfaceState.showBubbleZoomDownloadDialog
        var showNewTabSheet by surfaceState.showNewTabSheet
        var showFileInfoDialog by surfaceState.showFileInfoDialog
        var showSaveDialog by surfaceState.showSaveDialog
        var showShareDialog by surfaceState.showShareDialog
        var showOcrLanguageDialog by surfaceState.showOcrLanguageDialog
        var showReindexDialog by surfaceState.showReindexDialog
        var showCustomizeToolsSheet by surfaceState.showCustomizeToolsSheet
        var pendingActionAfterOcrSelection by surfaceState.pendingActionAfterOcrSelection
        var pendingSaveMode by surfaceState.pendingSaveMode
        var showBars by surfaceState.showBars
        // Read the pane-owned Compose state in the chrome itself. The setup and
        // surface functions are siblings, so a plain snapshot copied by setup
        // can otherwise lag behind a tap handled by the document viewport.
        val showStandardBars = showBars && !isEditMode
        LaunchedEffect(showBars, isEditMode, ownsPaneGlobals) {
            Timber.tag("PdfToolbarTrace").d(
                "chrome recomposed showBars=$showBars isEditMode=$isEditMode " +
                    "visible=$showStandardBars ownsPaneGlobals=$ownsPaneGlobals"
            )
        }
        var sliderStartPage by surfaceState.sliderStartPage
        var isHighlightingLoading by surfaceState.isHighlightingLoading
        var isAutoPagingForTts by surfaceState.isAutoPagingForTts
        var isKeepScreenOn by surfaceState.isKeepScreenOn
        val isBookmarked = surfaceState.isBookmarked
        val isReflowingThisBook = surfaceState.isReflowingThisBook
        var isPrintBlockedForPasswordProtectedPdf by surfaceState.isPrintBlockedForPasswordProtectedPdf
        val isOss = surfaceState.isOss
        var hasSelectedOcrLanguage by surfaceState.hasSelectedOcrLanguage
        var initialScrollDone by surfaceState.initialScrollDone
        val tabStateMap = surfaceState.tabStateMap
        val reflowProgressValue = surfaceState.reflowProgressValue
        var isBackgroundIndexing by surfaceState.isBackgroundIndexing
        var backgroundIndexingProgress by surfaceState.backgroundIndexingProgress
        var isOcrScanning by surfaceState.isOcrScanning
        var smartSearchResult by surfaceState.smartSearchResult
        var currentPdfSearchResult by surfaceState.currentPdfSearchResult
        val currentPaginationDisplayPage = surfaceState.currentPaginationDisplayPage
        val currentPdfDisplayPage = surfaceState.currentPdfDisplayPage
        val clearJumpHistory = surfaceState.clearJumpHistory
        val navigateToJumpHistoryPage = surfaceState.navigateToJumpHistoryPage
        val navigateToPdfPage = surfaceState.navigateToPdfPage
        val navigateToPdfSearchResult = surfaceState.navigateToPdfSearchResult
        val showBanner = surfaceState.showBanner
    val boxMaxWidthFloat = constraints.maxWidth.toFloat()
    val boxMaxHeightFloat = constraints.maxHeight.toFloat()

    val jumpBackPage = jumpHistory.backPage
    val jumpForwardPage = jumpHistory.forwardPage
    val effectiveNavBarForJumpBar = if (systemUiMode == SystemUiMode.DEFAULT || (systemUiMode == SystemUiMode.SYNC && showStandardBars)) with(density) { navBarHeight.toDp() } else 0.dp
    val isPdfJumpHistoryVisible = showStandardBars && !searchState.isSearchActive && (jumpBackPage != null || jumpForwardPage != null)
    val pdfBottomChromePadding = 56.dp + effectiveNavBarForJumpBar
    val pdfSliderBottomPadding = pdfBottomChromePadding + if (isPdfJumpHistoryVisible) 40.dp else 0.dp
    val pdfSliderPageBackground = if (activeTheme.backgroundColor == Color.Unspecified) Color.White else activeTheme.backgroundColor
    val pdfSliderPageText = if (activeTheme.textColor == Color.Unspecified) Color.Black else activeTheme.textColor
    val pdfReaderSliderColors = readerSliderChromeColors(
        pageBackground = pdfSliderPageBackground,
        pageText = pdfSliderPageText,
        themePrimary = MaterialTheme.colorScheme.primary
    )
    val pdfSliderMaxPage = (totalDisplayPages - 1).coerceAtLeast(0)
    val pdfSliderCurrentPage = sliderCurrentPage.roundToInt().coerceIn(0, pdfSliderMaxPage)

    fun jumpPdfSliderToPage(pageIndex: Int) {
        val targetPage = pageIndex.coerceIn(0, pdfSliderMaxPage)
        scrubDebounceJob.value?.cancel()
        sliderCurrentPage = targetPage.toFloat()
        isFastScrubbing = false
        navigateToPdfPage(targetPage, PdfNavigationReason.PAGE_SLIDER, true)
    }

    fun scrubPdfSliderToPage(newValue: Float) {
        sliderCurrentPage = newValue.coerceIn(0f, pdfSliderMaxPage.toFloat())
        isFastScrubbing = true
        scrubDebounceJob.value?.cancel()
        scrubDebounceJob.value = coroutineScope.launch {
            delay(200)
            if (isActive) {
                val targetPage = newValue.roundToInt().coerceIn(0, pdfSliderMaxPage)
                navigateToPdfPage(targetPage, PdfNavigationReason.PAGE_SLIDER, true)
                sliderCurrentPage = targetPage.toFloat()
                isFastScrubbing = false
            }
        }
    }

    // --- Slider UI attached to the bottom chrome ---
    AnimatedVisibility(
        visible = pdfSliderChromeVisible,
        enter = slideInVertically { fullHeight -> fullHeight } + fadeIn(),
        exit = slideOutVertically { fullHeight -> fullHeight } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = pdfSliderBottomPadding)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        jumpPdfSliderToPage(
                            readerSliderStepPage(
                                currentPage = pdfSliderCurrentPage,
                                delta = -1,
                                minPage = 0,
                                maxPage = pdfSliderMaxPage
                            )
                        )
                    },
                    enabled = pdfSliderCurrentPage > 0,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = stringResource(R.string.desktop_previous_page),
                        tint = pdfReaderSliderColors.contentColor.copy(
                            alpha = if (pdfSliderCurrentPage > 0) 0.9f else 0.32f
                        )
                    )
                }

                ReaderMinimalSlider(
                    value = sliderCurrentPage.coerceIn(0f, pdfSliderMaxPage.toFloat()),
                    onValueChange = ::scrubPdfSliderToPage,
                    valueRange = 0f..pdfSliderMaxPage.toFloat(),
                    enabled = pdfSliderMaxPage > 0,
                    activeColor = pdfReaderSliderColors.activeTrackColor,
                    inactiveColor = pdfReaderSliderColors.inactiveTrackColor,
                    thumbColor = pdfReaderSliderColors.thumbColor,
                    modifier = Modifier
                        .testTag("PdfPageSlider")
                        .weight(1f)
                        .height(32.dp)
                )

                IconButton(
                    onClick = {
                        jumpPdfSliderToPage(
                            readerSliderStepPage(
                                currentPage = pdfSliderCurrentPage,
                                delta = 1,
                                minPage = 0,
                                maxPage = pdfSliderMaxPage
                            )
                        )
                    },
                    enabled = pdfSliderCurrentPage < pdfSliderMaxPage,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = stringResource(R.string.desktop_next_page),
                        tint = pdfReaderSliderColors.contentColor.copy(
                            alpha = if (pdfSliderCurrentPage < pdfSliderMaxPage) 0.9f else 0.32f
                        )
                    )
                }
            }
        }
    }

    if (pdfSliderChromeVisible && isFastScrubbing) {
        PageScrubbingAnimation(
            pageLabel = pdfPageRangeLabel(
                pageIndex = sliderCurrentPage.roundToInt(),
                pageCount = totalDisplayPages,
                displayMode = displayMode,
                settings = pdfSpreadSettings
            )
        )
    }

    LaunchedEffect(ownsPaneGlobals) {
        if (!ownsPaneGlobals) {
            // Reader-local page/annotation state remains intact, but
            // process-global dialogs and controllers must not remain
            // owned by a pane after focus or app activity changes.
            showThemePanel = false
            showVisualOptionsSheet = false
            showBrightnessSheet = false
            showScreenOrientationSheet = false
            showTtsControlsSheet = false
            showTtsSettingsSheet = false
            showTtsReplacementsSheet = false
            showDictionarySettingsSheet = false
            showDictionaryUpsellDialog = false
            showSummarizationUpsellDialog = false
            showAiHubSheet = false
            showPermissionRationaleDialog = false
            showInsufficientCreditsDialog = false
            showBubbleZoomDownloadDialog = false
            showNewTabSheet = false
            showFileInfoDialog = false
            showSaveDialog = false
            showShareDialog = false
            showOcrLanguageDialog = false
            showReindexDialog = null
            showCustomizeToolsSheet = false
            pendingActionAfterOcrSelection = null
            pendingSaveMode = null
            drawerState.close()
            if (ttsState.bookId == bookId) ttsController.stop()
            showBars = false
        }
    }

    val isPdfTtsPlayingOrLoading = isTtsPlayingOrLoading
    val showPdfThemePanel = { if (ownsPaneGlobals) showThemePanel = true }
    val showPdfDictionarySettings = { if (ownsPaneGlobals) showDictionarySettingsSheet = true }
    val togglePdfScrollLock = {
        if (ownsPaneGlobals) {
            val nextLocked = !isScrollLocked
            isScrollLocked = nextLocked
            savePdfScrollLocked(context, bookId, nextLocked)
            if (nextLocked) {
                currentPageScale = currentActiveScale
                savePdfLockedState(context, bookId, currentActiveScale, currentActiveOffset.x, currentActiveOffset.y)
                lockedState = Triple(currentActiveScale, currentActiveOffset.x, currentActiveOffset.y)
            }
        }
    }
    val showPdfSlider = {
        if (ownsPaneGlobals) {
            val currentPageForSlider = if (displayMode == DisplayMode.PAGINATION) currentPaginationDisplayPage() else verticalReaderState.currentPage
            val nextState = readerSliderToggleState(
                isCurrentlyToggledOn = isPageSliderVisible,
                currentPage = currentPageForSlider
            )
            sliderStartPage = nextState.bookmarkPosition.startPage
            sliderCurrentPage = nextState.bookmarkPosition.currentPage
            isPageSliderVisible = nextState.isToggledOn
            showBars = true
        }
    }
    val showPdfToc = {
        if (ownsPaneGlobals) coroutineScope.launch { drawerState.open() }
        Unit
    }
    val showPdfSearch = {
        if (ownsPaneGlobals) {
            executeWithOcrCheck {
                searchState.isSearchActive = true
                showBars = true
            }
        }
    }
    val togglePdfHighlights = {
        if (ownsPaneGlobals) {
            if (!showAllTextHighlights && !isHighlightingLoading) {
                showAllTextHighlights = true
                isHighlightingLoading = true
            } else if (showAllTextHighlights) {
                showAllTextHighlights = false
                isHighlightingLoading = false
            }
        }
    }
    val showPdfAiHub = { if (ownsPaneGlobals) showAiHubSheet = true }
    val togglePdfEditMode = {
        if (ownsPaneGlobals) {
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
    }
    val togglePdfTts = {
        if (ownsPaneGlobals) {
            if (isTtsSessionActive) {
                Timber.d("TTS button clicked: Stopping TTS")
                ttsController.stop()
                isAutoPagingForTts = false
            } else {
                startTtsWithPermissionCheck(null, null)
            }
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
            currentPaginationDisplayPage()
        } else {
            verticalReaderState.currentPage
        },
        currentPageLabel = pdfPageRangeLabel(
            pageIndex = currentPage,
            pageCount = totalDisplayPages,
            displayMode = displayMode,
            settings = pdfSpreadSettings
        ),
        totalPages = totalPages,
        pagerStatePageCount = pagerState.pageCount,
        hiddenTools = hiddenTools,
        toolOrder = toolOrder,
        bottomTools = bottomTools,
        isScrollLocked = isScrollLocked,
        isEditMode = isEditMode,
        displayMode = displayMode,
        isRightToLeftPagination = rightToLeftPagination,
        isKeepScreenOn = isKeepScreenOn,
        isTtsSessionActive = isTtsSessionActive,
        isSliderActive = isPageSliderVisible,
        isBookmarked = isBookmarked,
        canDeletePage = virtualPages.getOrNull(currentPage) is VirtualPage.BlankPage,
        isReflowingThisBook = isReflowingThisBook,
        hasReflowFile = hasReflowFile,
        isPdfDocumentLoaded = pdfDocument != null,
        canPrintDocument = !isPrintBlockedForPasswordProtectedPdf,
        isTabsEnabled = isPdfTabStripVisible,
        openTabs = openTabs,
        activeTabBookId = activeTabBookId,
        usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName,
        effectiveFileType = effectiveFileType,
        onNavigateBack = { if (ownsPaneGlobals) saveStateAndExit() },
        onOpenSplit = if (!isSplitPane) onOpenSplit else null,
        onShowThemePanel = showPdfThemePanel,
        onShowBrightnessControl = { if (ownsPaneGlobals) showBrightnessSheet = true },
        onToggleScrollLock = togglePdfScrollLock,
        onShowDictionarySettings = showPdfDictionarySettings,
        onShowPenPlayground = { if (ownsPaneGlobals) showPenPlayground = true },
        onImportSvg = {
            if (!ownsPaneGlobals) return@PdfTopBar
            val page = if (displayMode == DisplayMode.PAGINATION) currentPaginationDisplayPage() else verticalReaderState.currentPage

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
        onShowCustomizeTools = { if (ownsPaneGlobals) showCustomizeToolsSheet = true },
        onShowOcrLanguage = {
            if (ownsPaneGlobals && !isOss) {
                hasSelectedOcrLanguage = true
                showOcrLanguageDialog = true
            }
        },
        onShowVisualOptions = { if (ownsPaneGlobals) showVisualOptionsSheet = true },
        onShowScreenOrientation = { if (ownsPaneGlobals) showScreenOrientationSheet = true },
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
            if (ownsPaneGlobals) {
                tapToNavigateEnabled = !tapToNavigateEnabled
                saveTapToNavigateSetting(context, tapToNavigateEnabled)
            }
        },
        pageTurnAnimationEnabled = pageTurnAnimationEnabled,
        onTogglePageTurnAnimation = {
            if (ownsPaneGlobals) {
                pageTurnAnimationEnabled = !pageTurnAnimationEnabled
                savePageTurnAnimationSetting(context, pageTurnAnimationEnabled)
            }
        },
        onChangeDisplayMode = { if (ownsPaneGlobals) displayMode = it },
        onSetRightToLeftPagination = { enabled ->
            if (ownsPaneGlobals) {
                rightToLeftPagination = enabled
                savePdfRightToLeftPagination(context, enabled)
            }
        },
        onToggleKeepScreenOn = {
            if (ownsPaneGlobals) {
                isKeepScreenOn = !isKeepScreenOn
                saveKeepScreenOn(context, isKeepScreenOn)
            }
        },
        onStartAutoScroll = {
            if (ownsPaneGlobals) {
                isAutoScrollModeActive = true
                isAutoScrollPlaying = true
                showBars = !isMusicianMode
            }
        },
        onShowTtsSettings = { if (ownsPaneGlobals) showTtsSettingsSheet = true },
        onShowTtsReplacements = { if (ownsPaneGlobals) showTtsReplacementsSheet = true },
        onToggleBookmark = onBookmarkClick,
        onShowFileInfo = { if (ownsPaneGlobals) showFileInfoDialog = true },
        onInsertPage = onInsertPage,
        onDeletePage = onDeletePage,
        onReflowAction = {
            if (!ownsPaneGlobals) return@PdfTopBar
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
        onShare = requestShare,
        onSaveCopy = requestSaveCopy,
        onPrint = onPrintDocument,
        onTabClick = { tabBookId ->
            coroutineScope.launch {
                currentBookId?.let { tabStateMap[it] = currentPage }
                saveAllData(true).join()
                viewModel.switchTab(tabBookId)
            }
        },
        onTabClose = { tabBookId ->
            if (!ownsPaneGlobals) return@PdfTopBar
            coroutineScope.launch {
                val isSelected = tabBookId == activeTabBookId
                if (isSelected) saveAllData(true).join()
                viewModel.closeTab(tabBookId)
                if (isSelected && openTabs.size == 1) {
                    onNavigateBack()
                }
            }
        },
        onNewTabClick = { if (ownsPaneGlobals) showNewTabSheet = true },
        onGenerateDemoAnnotations = {
            if (!ownsPaneGlobals) return@PdfTopBar
            val page = if (displayMode == DisplayMode.PAGINATION) currentPaginationDisplayPage() else verticalReaderState.currentPage
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
                            text = stringResource(
                                R.string.msg_indexing_pages_progress,
                                (backgroundIndexingProgress * 100f).roundToInt()
                                    .coerceIn(0, 100)
                            ),
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

    PdfJumpHistoryBar(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = pdfBottomChromePadding),
        showStandardBars = showStandardBars,
        searchStateActive = searchState.isSearchActive,
        backPage = jumpBackPage,
        forwardPage = jumpForwardPage,
        onBack = {
            val refreshedHistory = jumpHistory.updateCurrentLocation(
                currentPageIndex = currentPdfDisplayPage(),
                pageCount = totalDisplayPages,
            )
            val target = refreshedHistory.backPage
            jumpHistory = if (target != null) {
                refreshedHistory.stepBack()
            } else {
                refreshedHistory
            }
            if (target != null) {
                navigateToJumpHistoryPage(target)
            }
        },
        onForward = {
            val refreshedHistory = jumpHistory.updateCurrentLocation(
                currentPageIndex = currentPdfDisplayPage(),
                pageCount = totalDisplayPages,
            )
            val target = refreshedHistory.forwardPage
            jumpHistory = if (target != null) {
                refreshedHistory.stepForward()
            } else {
                refreshedHistory
            }
            if (target != null) {
                navigateToJumpHistoryPage(target)
            }
        },
        onClear = { clearJumpHistory() }
    )
}

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.PdfViewerChromeBottomAndEditing(
    surfaceState: PdfViewerSurfaceState,
) {
        val richTextController = surfaceState.richTextController
        val selectedTool = surfaceState.selectedTool.value
        val density = surfaceState.density.value
        val searchState = surfaceState.searchState
        val ownsPaneGlobals = surfaceState.ownsPaneGlobals
        val isTtsPlayingOrLoading = surfaceState.isTtsPlayingOrLoading
        val context = surfaceState.context
        var isPageSliderVisible by surfaceState.isPageSliderVisible
        val coroutineScope = surfaceState.coroutineScope
        var isEditMode by surfaceState.isEditMode
        val isTtsSessionActive = surfaceState.isTtsSessionActive
        val statusBarHeightDp = surfaceState.statusBarHeightDp.value
        val hiddenTools = surfaceState.hiddenTools
        val toolOrder = surfaceState.toolOrder
        val bottomTools = surfaceState.bottomTools
        val viewModel = surfaceState.viewModel
        val isComicFile = surfaceState.isComicFile
        val dockHeightPx = surfaceState.dockHeightPx.value
        val isHighlighterSnapEnabled = surfaceState.isHighlighterSnapEnabled
        val isCurrentToolHighlighter = surfaceState.isCurrentToolHighlighter
        var isScrollLocked by surfaceState.isScrollLocked
        var allAnnotations by surfaceState.allAnnotations
        var showToolSettings by surfaceState.showToolSettings
        var showAllTextHighlights by surfaceState.showAllTextHighlights
        var isStylusOnlyMode by surfaceState.isStylusOnlyMode
        var isBubbleZoomModeActive by surfaceState.isBubbleZoomModeActive
        val undoStack = surfaceState.undoStack
        val redoStack = surfaceState.redoStack
        var systemUiMode by surfaceState.systemUiMode
        val navBarHeight = surfaceState.navBarHeight.value
        var showBrightnessSheet by surfaceState.showBrightnessSheet
        var showScreenOrientationSheet by surfaceState.showScreenOrientationSheet
        var showBubbleZoomDownloadDialog by surfaceState.showBubbleZoomDownloadDialog
        var showBars by surfaceState.showBars
        val showStandardBars = showBars && !isEditMode
        var isHighlightingLoading by surfaceState.isHighlightingLoading
        val isOss = surfaceState.isOss
        var dockLocation by surfaceState.dockLocation
        var dockOffset by surfaceState.dockOffset
        val highlighterPalette = surfaceState.highlighterPalette
        val penPalette = surfaceState.penPalette
        val activeToolThickness = surfaceState.activeToolThickness
        val fountainPenColor = surfaceState.fountainPenColor
        val markerColor = surfaceState.markerColor
        val pencilColor = surfaceState.pencilColor
        val highlighterColor = surfaceState.highlighterColor
        val highlighterRoundColor = surfaceState.highlighterRoundColor
        val annotationSettingsRepo = surfaceState.annotationSettingsRepo
        var snapPreviewLocation by surfaceState.snapPreviewLocation
        val dockHeight = surfaceState.dockHeight
        var isDockDragging by surfaceState.isDockDragging
        var isDockMinimized by surfaceState.isDockMinimized
        val dockPenColor = surfaceState.dockPenColor
        val dockHighlighterColor = surfaceState.dockHighlighterColor
        val lastPenTool = surfaceState.lastPenTool
        val lastHighlighterTool = surfaceState.lastHighlighterTool
        val showBanner = surfaceState.showBanner
        val bookId = surfaceState.bookId
        val ttsState = surfaceState.ttsState
        val ttsController = surfaceState.ttsController
        val drawerState = surfaceState.drawerState
        val executeWithOcrCheck = surfaceState.executeWithOcrCheck
        val startTtsWithPermissionCheck = surfaceState.startTtsWithPermissionCheck
        var currentActiveScale by surfaceState.currentActiveScale
        var currentActiveOffset by surfaceState.currentActiveOffset
        var currentPageScale by surfaceState.currentPageScale
        var lockedState by surfaceState.lockedState
        var displayMode by surfaceState.displayMode
        val verticalReaderState = surfaceState.verticalReaderState
        val currentPaginationDisplayPage = surfaceState.currentPaginationDisplayPage
        var sliderStartPage by surfaceState.sliderStartPage
        var sliderCurrentPage by surfaceState.sliderCurrentPage
        var showThemePanel by surfaceState.showThemePanel
        var showDictionarySettingsSheet by surfaceState.showDictionarySettingsSheet
        var showAiHubSheet by surfaceState.showAiHubSheet
        var isAutoPagingForTts by surfaceState.isAutoPagingForTts
        val keyboardController = surfaceState.keyboardController
        val isPdfTtsPlayingOrLoading = isTtsPlayingOrLoading
        val showPdfThemePanel = { if (ownsPaneGlobals) showThemePanel = true }
        val showPdfDictionarySettings = { if (ownsPaneGlobals) showDictionarySettingsSheet = true }
        val togglePdfScrollLock = {
            if (ownsPaneGlobals) {
                val nextLocked = !isScrollLocked
                isScrollLocked = nextLocked
                savePdfScrollLocked(context, bookId, nextLocked)
                if (nextLocked) {
                    currentPageScale = currentActiveScale
                    savePdfLockedState(context, bookId, currentActiveScale, currentActiveOffset.x, currentActiveOffset.y)
                    lockedState = Triple(currentActiveScale, currentActiveOffset.x, currentActiveOffset.y)
                }
            }
        }
        val showPdfSlider = {
            if (ownsPaneGlobals) {
                val currentPageForSlider = if (displayMode == DisplayMode.PAGINATION) currentPaginationDisplayPage() else verticalReaderState.currentPage
                val nextState = readerSliderToggleState(
                    isCurrentlyToggledOn = isPageSliderVisible,
                    currentPage = currentPageForSlider
                )
                sliderStartPage = nextState.bookmarkPosition.startPage
                sliderCurrentPage = nextState.bookmarkPosition.currentPage
                isPageSliderVisible = nextState.isToggledOn
                showBars = true
            }
        }
        val showPdfToc = {
            if (ownsPaneGlobals) coroutineScope.launch { drawerState.open() }
            Unit
        }
        val showPdfSearch = {
            if (ownsPaneGlobals) {
                executeWithOcrCheck {
                    searchState.isSearchActive = true
                    showBars = true
                }
            }
        }
        val togglePdfHighlights = {
            if (ownsPaneGlobals) {
                if (!showAllTextHighlights && !isHighlightingLoading) {
                    showAllTextHighlights = true
                    isHighlightingLoading = true
                } else if (showAllTextHighlights) {
                    showAllTextHighlights = false
                    isHighlightingLoading = false
                }
            }
        }
        val showPdfAiHub = { if (ownsPaneGlobals) showAiHubSheet = true }
        val togglePdfEditMode = {
            if (ownsPaneGlobals) {
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
        }
        val togglePdfTts = {
            if (ownsPaneGlobals) {
                if (isTtsSessionActive) {
                    Timber.d("TTS button clicked: Stopping TTS")
                    ttsController.stop()
                    isAutoPagingForTts = false
                } else {
                    startTtsWithPermissionCheck(null, null)
                }
            }
        }
    val boxMaxWidthFloat = constraints.maxWidth.toFloat()
    val boxMaxHeightFloat = constraints.maxHeight.toFloat()

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
        isScrollLocked = isScrollLocked,
        isTtsSessionActive = isTtsSessionActive,
        isSliderActive = isPageSliderVisible,
        ttsErrorMessage = null,
        onShowThemePanel = showPdfThemePanel,
        onShowBrightnessControl = { if (ownsPaneGlobals) showBrightnessSheet = true },
        onToggleScrollLock = togglePdfScrollLock,
        onShowDictionarySettings = showPdfDictionarySettings,
        onShowSlider = showPdfSlider,
        onShowToc = showPdfToc,
        onSearchClick = showPdfSearch,
        onToggleHighlights = togglePdfHighlights,
        onShowAiHub = showPdfAiHub,
        onToggleEditMode = togglePdfEditMode,
        onToggleTts = togglePdfTts,
        onShowScreenOrientation = { if (ownsPaneGlobals) showScreenOrientationSheet = true },
        showBubbleZoom = isComicFile,
        isBubbleZoomModeActive = isBubbleZoomModeActive,
        onToggleBubbleZoom = {
            if (!ownsPaneGlobals) {
                return@PdfBottomBar
            } else if (isOss) {
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
}

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.PdfViewerChromeTts(
    surfaceState: PdfViewerSurfaceState,
) {
        val richTextController = surfaceState.richTextController
        val selectedTool = surfaceState.selectedTool.value
        val density = surfaceState.density.value
        val ownsPaneGlobals = surfaceState.ownsPaneGlobals
        val ttsState = surfaceState.ttsState
        val ttsController = surfaceState.ttsController
        val context = surfaceState.context
        val pagerState = surfaceState.pagerState
        val verticalReaderState = surfaceState.verticalReaderState
        val coroutineScope = surfaceState.coroutineScope
        var isEditMode by surfaceState.isEditMode
        val keyboardController = surfaceState.keyboardController
        val isTtsSessionActive = surfaceState.isTtsSessionActive
        val viewModel = surfaceState.viewModel
        val currentPage = surfaceState.currentPage
        val uiState = surfaceState.uiState
        val window = surfaceState.window
        val view = surfaceState.view
        var displayMode by surfaceState.displayMode
        val textBoxes = surfaceState.textBoxes
        var selectedTextBoxId by surfaceState.selectedTextBoxId
        val displayPageRatios = surfaceState.displayPageRatios
        var ttsDisplayPageIndex by surfaceState.ttsDisplayPageIndex
        var resetZoomTrigger by surfaceState.resetZoomTrigger
        var isAutoScrollPlaying by surfaceState.isAutoScrollPlaying
        var isAutoScrollTempPaused by surfaceState.isAutoScrollTempPaused
        var autoScrollSpeed by surfaceState.autoScrollSpeed
        var isMusicianMode by surfaceState.isMusicianMode
        var isAutoScrollModeActive by surfaceState.isAutoScrollModeActive
        val autoScrollResumeJob = surfaceState.autoScrollResumeJob
        var systemUiMode by surfaceState.systemUiMode
        val navBarHeight = surfaceState.navBarHeight.value
        var showTtsSettingsSheet by surfaceState.showTtsSettingsSheet
        var showBars by surfaceState.showBars
        val showStandardBars = showBars && !isEditMode
        var isAutoPagingForTts by surfaceState.isAutoPagingForTts
        var dockLocation by surfaceState.dockLocation
        val highlighterPalette = surfaceState.highlighterPalette
        val penPalette = surfaceState.penPalette
        val annotationSettingsRepo = surfaceState.annotationSettingsRepo
        var isDockMinimized by surfaceState.isDockMinimized
        var showZoomIndicator by surfaceState.showZoomIndicator
        val zoomIndicatorPercentage = surfaceState.zoomIndicatorPercentage
        val toolSettings = surfaceState.toolSettings
        val onInsertTextBox = surfaceState.onInsertTextBox
        val customFonts = surfaceState.customFonts
        var ttsOverlaySize by surfaceState.ttsOverlaySize
        var currentTtsMode by surfaceState.currentTtsMode
        var isAutoScrollCollapsed by surfaceState.isAutoScrollCollapsed
        var autoScrollMinSpeed by surfaceState.autoScrollMinSpeed
        var autoScrollMaxSpeed by surfaceState.autoScrollMaxSpeed
        var autoScrollUseSlider by surfaceState.autoScrollUseSlider
        var isAutoScrollLocal by surfaceState.isAutoScrollLocal
        val updateSpeed = surfaceState.updateSpeed
        val updateMinSpeed = surfaceState.updateMinSpeed
        val updateMaxSpeed = surfaceState.updateMaxSpeed
        val onToggleAutoScrollMode = surfaceState.onToggleAutoScrollMode
        val triggerAutoScrollTempPause = surfaceState.triggerAutoScrollTempPause
    val boxMaxWidthFloat = constraints.maxWidth.toFloat()
    val boxMaxHeightFloat = constraints.maxHeight.toFloat()

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
        ZoomPercentageIndicator(
            percentage = zoomIndicatorPercentage,
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
        val imeHeightPx = WindowInsets.ime.getBottom(currentDensity)
        val isImeVisible = imeHeightPx > 0
        val windowHeightPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window?.windowManager?.currentWindowMetrics?.bounds?.height()
        } else {
            null
        } ?: view.rootView.height
        val applyImePadding = shouldApplyPdfTextDockImePadding(
            layoutHeightPx = constraints.maxHeight,
            windowHeightPx = windowHeightPx,
            imeHeightPx = imeHeightPx
        )

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
            .then(
                when {
                    applyImePadding -> Modifier.windowInsetsPadding(
                        WindowInsets.ime.union(WindowInsets.navigationBars)
                    )
                    !isImeVisible -> Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    else -> Modifier
                }
            )
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
                    Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).d(
                        "event=style_update target=${if (selectedTextBoxId == null) "page_rich_text" else "legacy_text_box"} " +
                            "selectedTextBoxId=${selectedTextBoxId ?: "none"} fontSize=${newStyle.fontSize.value} " +
                            "bold=${newStyle.fontWeight == FontWeight.Bold} italic=${newStyle.fontStyle == FontStyle.Italic} " +
                            "underline=${newStyle.textDecoration?.contains(TextDecoration.Underline) == true} " +
                            "strike=${newStyle.textDecoration?.contains(TextDecoration.LineThrough) == true}"
                    )
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
                    Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).d(
                        "event=style_font_update target=${if (selectedTextBoxId == null) "page_rich_text" else "legacy_text_box"} " +
                            "selectedTextBoxId=${selectedTextBoxId ?: "none"}"
                    )
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
        targetValue = readerTtsOverlayAlignmentBias(ttsOverlaySize),
        label = "TtsAlignAnimation"
    )

    AnimatedVisibility(
        visible = isTtsSessionActive && showBars && ownsPaneGlobals,
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
            overlaySize = ttsOverlaySize,
            onOverlaySizeChange = { newSize ->
                ttsOverlaySize = newSize
                saveReaderTtsOverlaySize(context, newSize)
            },
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
            onOpenTtsSettings = { if (ownsPaneGlobals) showTtsSettingsSheet = true },
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
}

/**
 * Aspect-fits a page with [aspectRatio] (width/height) inside the available area.
 *
 * The realistic page curl must fold the drawn PDF page, not the letterboxed pager
 * slot, so the turn sheet is sized to this rect. Each display page is measured with
 * its own ratio from [displayPageRatios], which keeps mixed page sizes (portrait and
 * landscape pages in one document, inserted blanks) turning correctly.
 */
internal fun pdfPaginationTurnSheetSize(
    availableWidth: Dp,
    availableHeight: Dp,
    aspectRatio: Float,
): DpSize {
    val safeAspectRatio = if (aspectRatio > 0f) aspectRatio else 1f
    val widthLimited = availableWidth / availableHeight <= safeAspectRatio
    val fittedWidth = if (widthLimited) availableWidth else availableHeight * safeAspectRatio
    return DpSize(width = fittedWidth, height = fittedWidth / safeAspectRatio)
}

@Composable
private fun PdfViewerPaginationPage(
    paginationPageState: PdfViewerPaginationPageState,
    pagerPageIndex: Int,
    pageTurnAnimationEnabled: Boolean,
    pageTurnTouchY: Float?,
) {
    val surfaceState = paginationPageState.surfaceState
    val boxMaxWidthFloat = paginationPageState.boxMaxWidth.toFloat()
    val boxMaxHeightFloat = paginationPageState.boxMaxHeight.toFloat()
    val stablePdfDocument = paginationPageState.stablePdfDocument
    val stylusButtonHovering = paginationPageState.stylusButtonHovering
    val richTextController = surfaceState.richTextController
    val selectedTool = surfaceState.selectedTool.value
    val density = surfaceState.density.value
    val searchState = surfaceState.searchState
    val activeTheme = surfaceState.activeTheme.value
    val pdfSpreadSettings = surfaceState.pdfSpreadSettings
    val totalDisplayPages = surfaceState.totalDisplayPages
    val pagerState = surfaceState.pagerState
    val coroutineScope = surfaceState.coroutineScope
    var isEditMode by surfaceState.isEditMode
    val startTtsWithPermissionCheck = surfaceState.startTtsWithPermissionCheck
    val onBookmarkClick = surfaceState.onBookmarkClick
    val currentPage = surfaceState.currentPage
    val activeDocumentRenderKey = surfaceState.activeDocumentRenderKey
    val isHighlighterSnapEnabled = surfaceState.isHighlighterSnapEnabled
    val calculateSnappedPoint = surfaceState.calculateSnappedPoint
    var pdfDocument by surfaceState.pdfDocument
    var totalPages by surfaceState.totalPages
    var tapToNavigateEnabled by surfaceState.tapToNavigateEnabled
    var currentPageScale by surfaceState.currentPageScale
    var isScrollLocked by surfaceState.isScrollLocked
    var rightToLeftPagination by surfaceState.rightToLeftPagination
    // Android-benchmark realistic page turn: disabled while zoomed because the page
    // zoom transform would rescale the curl's counter-translation.
    val realisticPageTurnActive = pageTurnAnimationEnabled && currentPageScale <= 1f
    val pagePaperColor = pdfPaginatedPagePaperColor(activeTheme)
    val textBoxes = surfaceState.textBoxes
    var paginationDraggingBoxId by surfaceState.paginationDraggingBoxId
    val isDrawingActive = surfaceState.isDrawingActive
    val viewConfiguration = surfaceState.viewConfiguration
    var currentActiveScale by surfaceState.currentActiveScale
    var currentActiveOffset by surfaceState.currentActiveOffset
    var showVerticalPageGap by surfaceState.showVerticalPageGap
    val pdfTextRepository = surfaceState.pdfTextRepository
    val visibleUserHighlightsByPage = surfaceState.visibleUserHighlightsByPage
    val visibleTextBoxesByPage = surfaceState.textBoxSurfaceState.data.value.byPage
    val isProUser = surfaceState.isProUser
    val onDictionaryLookupStable = surfaceState.onDictionaryLookupStable
    val onTranslateTextStable = surfaceState.onTranslateTextStable
    val onSearchTextStable = surfaceState.onSearchTextStable
    val onInternalLinkNav = surfaceState.onInternalLinkNav
    val onOcrStateChange = surfaceState.onOcrStateChange
    val onToggleBookmark = surfaceState.onToggleBookmark
    val drawingState = surfaceState.drawingState
    val persistInkAnnotationsNow = surfaceState.persistInkAnnotationsNow
    var selectedTextBoxId by surfaceState.selectedTextBoxId
    val displayPageRatios = surfaceState.displayPageRatios
    val onHighlightAdd = surfaceState.onHighlightAdd
    val onHighlightUpdate = surfaceState.onHighlightUpdate
    val onHighlightDelete = surfaceState.onHighlightDelete
    val onNoteRequested = surfaceState.onNoteRequested
    var bookmarks by surfaceState.bookmarks
    var searchHighlightTarget by surfaceState.searchHighlightTarget
    var isOcrModelDownloading by surfaceState.isOcrModelDownloading
    val allAnnotationsProvider = surfaceState.allAnnotationsProvider
    val currentStrokeColor = surfaceState.currentStrokeColor
    val currentStrokeWidth = surfaceState.currentStrokeWidth
    val currentEraserStrokeWidth = surfaceState.currentEraserStrokeWidth
    val erasedAnnotationsFromStroke = surfaceState.erasedAnnotationsFromStroke
    var pageAspectRatios by surfaceState.pageAspectRatios
    var allAnnotations by surfaceState.allAnnotations
    var lastEraserPoint by surfaceState.lastEraserPoint
    val currentIsHighlighter = surfaceState.currentIsHighlighter
    val currentSnapEnabled = surfaceState.currentSnapEnabled
    var showToolSettings by surfaceState.showToolSettings
    var virtualPages by surfaceState.virtualPages
    var globalTextureTransparency by surfaceState.globalTextureTransparency
    var excludeImages by surfaceState.excludeImages
    var reverseColorMode by surfaceState.reverseColorMode
    var customHighlightColors by surfaceState.customHighlightColors
    var ttsDisplayPageIndex by surfaceState.ttsDisplayPageIndex
    var ttsHighlightData by surfaceState.ttsHighlightData
    var searchHighlightMode by surfaceState.searchHighlightMode
    var showAllTextHighlights by surfaceState.showAllTextHighlights
    var showPageNumberOverlay by surfaceState.showPageNumberOverlay
    var selectionClearTrigger by surfaceState.selectionClearTrigger
    var resetZoomTrigger by surfaceState.resetZoomTrigger
    var lockedState by surfaceState.lockedState
    var isStylusOnlyMode by surfaceState.isStylusOnlyMode
    var isAutoScrollPlaying by surfaceState.isAutoScrollPlaying
    var isBubbleZoomModeActive by surfaceState.isBubbleZoomModeActive
    var useOnlineDictionary by surfaceState.useOnlineDictionary
    var showDictionaryUpsellDialog by surfaceState.showDictionaryUpsellDialog
    var clickedLinkUrl by surfaceState.clickedLinkUrl
    val undoStack = surfaceState.undoStack
    val redoStack = surfaceState.redoStack
    var paginationOriginalRelSize by surfaceState.paginationOriginalRelSize
    var paginationDraggingSize by surfaceState.paginationDraggingSize
    var paginationDragPageHeight by surfaceState.paginationDragPageHeight
    var paginationDraggingOffset by surfaceState.paginationDraggingOffset
    var highlightColorPickerInitialSlot by surfaceState.highlightColorPickerInitialSlot
    var showHighlightColorPicker by surfaceState.showHighlightColorPicker
    var poppedUpPanelBitmap by surfaceState.poppedUpPanelBitmap
    val isBookmarked = surfaceState.isBookmarked
    val activeToolThickness = surfaceState.activeToolThickness
    val onSingleTapStable = surfaceState.onSingleTapStable
    val paginationDisplayPageForPagerPage = surfaceState.paginationDisplayPageForPagerPage
    val animatePaginationToDisplayPage = surfaceState.animatePaginationToDisplayPage
    val currentPaginationDisplayPage = surfaceState.currentPaginationDisplayPage
    val detectSpeechBubblesForPage = surfaceState.detectSpeechBubblesForPage
    val isAnnotationHit = surfaceState.isAnnotationHit
    val onPaginationPreSingleTap: (Offset) -> Boolean = { tapOffset ->
        val canTurnPagesByTap = tapToNavigateEnabled &&
            (currentPageScale <= 1.02f || isScrollLocked)

        if (!canTurnPagesByTap) {
            false
        } else {
            val oneQuarterWidthPx = boxMaxWidthFloat / 4f
            suspend fun turnPager(targetPage: Int) {
                if (targetPage != pagerState.currentPage) {
                    // Android-benchmark realistic turn: single-step manual turns snap
                    // with tween(700) so the curl tracks the pager animation.
                    if (shouldPlayRealisticPdfPageTurn(realisticPageTurnActive, pagerState.currentPage, targetPage)) {
                        pagerState.animateScrollToPage(targetPage, animationSpec = RealisticPdfPageTurnAnimationSpec)
                    } else {
                        pagerState.scrollToPage(targetPage)
                    }
                }
            }
            when {
                tapOffset.x < oneQuarterWidthPx -> {
                    coroutineScope.launch {
                        val targetPage =
                            if (rightToLeftPagination) {
                                (pagerState.currentPage + 1).coerceAtMost(pagerState.pageCount - 1)
                            } else {
                                (pagerState.currentPage - 1).coerceAtLeast(0)
                            }
                        turnPager(targetPage)
                    }
                    true
                }

                tapOffset.x > (boxMaxWidthFloat - oneQuarterWidthPx) -> {
                    coroutineScope.launch {
                        val targetPage =
                            if (rightToLeftPagination) {
                                (pagerState.currentPage - 1).coerceAtLeast(0)
                            } else {
                                (pagerState.currentPage + 1).coerceAtMost(
                                    pagerState.pageCount - 1
                                )
                            }
                        turnPager(targetPage)
                    }
                    true
                }

                else -> false
            }
        }
    }

    val spreadPageIndices = remember(
        pagerPageIndex,
        totalDisplayPages,
        pdfSpreadSettings.pageSpreadMode,
        pdfSpreadSettings.pdfFirstPageStandaloneInSpread
    ) {
        PdfSpreadLayout.visiblePageIndices(
            pageIndex = paginationDisplayPageForPagerPage(pagerPageIndex),
            pageCount = totalDisplayPages,
            settings = pdfSpreadSettings
        )
    }
    val isVisiblePage = remember(pagerState.currentPage, pagerPageIndex) {
        abs(pagerState.currentPage - pagerPageIndex) <= 1
    }
    val isActivePagerPage = pagerState.currentPage == pagerPageIndex
    val useSharedSpreadZoom = spreadPageIndices.size > 1
    val latestSpreadScale = rememberUpdatedState(currentActiveScale)
    val latestSpreadOffset = rememberUpdatedState(currentActiveOffset)
    val spreadPageGap = if (showVerticalPageGap) 8.dp else 0.dp
    val spreadPageGapPx = with(density) { spreadPageGap.toPx() }
    val spreadPageCount = spreadPageIndices.size
    var spreadPanFlingJob by remember { mutableStateOf<Job?>(null) }
    // Pager natural position: the curl's counter-translation cancels the pager's own
    // translation while |offset| < 1 (Android benchmark), and the curling page draws
    // above the incoming one.
    val turnPageOffset =
        if (realisticPageTurnActive) {
            (pagerPageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction
        } else {
            0f
        }
    val turnSlotModifier = if (realisticPageTurnActive) {
        Modifier
            .zIndex(-turnPageOffset)
            .graphicsLayer {
                if (turnPageOffset <= 1f && turnPageOffset > -1f) {
                    translationX = -turnPageOffset * size.width
                }
            }
    } else {
        Modifier
    }
    val turnSheetModifier = if (realisticPageTurnActive) {
        Modifier
            .graphicsLayer {
                if (turnPageOffset != 0f) {
                    shadowElevation = 10f
                    shape = RectangleShape
                    clip = false
                }
            }
            .realisticPageCurl(
                pageOffsetProvider = { turnPageOffset },
                touchYProvider = { pageTurnTouchY },
                paperColor = pagePaperColor
            )
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .then(turnSlotModifier)
            .fillMaxSize()
            .clipToBounds()
            .then(
                if (useSharedSpreadZoom) {
                    Modifier
                        .pointerInput(
                            useSharedSpreadZoom,
                            isDrawingActive,
                            isScrollLocked,
                            totalDisplayPages
                        ) {
                            if (!useSharedSpreadZoom || isDrawingActive) return@pointerInput
                            val oneHandZoomDistancePx = with(density) {
                                PDF_ONE_HAND_ZOOM_DRAG_DISTANCE_FOR_DOUBLE_DP.dp.toPx()
                            }
                            var oneHandZoomStartScale = 1f
                            var oneHandZoomStartOffset = Offset.Zero
                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                "spread.detector.enabled scrollLocked=$isScrollLocked drawing=$isDrawingActive " +
                                    "pages=$totalDisplayPages scale=${latestSpreadScale.value} offset=${latestSpreadOffset.value}"
                            )

                            fun spreadTargetOffset(
                                startScale: Float,
                                targetScale: Float,
                                startOffset: Offset,
                                pivot: Offset
                            ): Offset {
                                if (targetScale <= 1.1f) return Offset.Zero
                                val viewportSize = Size(size.width.toFloat(), size.height.toFloat())
                                return centeredPdfCameraOffsetForScaleChange(
                                    previousScale = startScale,
                                    nextScale = targetScale,
                                    previousOffset = startOffset,
                                    pivot = pivot,
                                    viewportSize = viewportSize,
                                    contentSize = viewportSize
                                )
                            }

                            detectPdfTapAndOneHandZoomGestures(
                                viewConfiguration = viewConfiguration,
                                canStartOneHandZoom = {
                                    useSharedSpreadZoom && !isDrawingActive && !isScrollLocked
                                },
                                canHandleQuickDoubleTap = { !isScrollLocked },
                                consumeSingleTap = false,
                                onTap = { offset ->
                                    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                        "spread.tap passthrough offset=$offset"
                                    )
                                },
                                onQuickDoubleTap = quickDoubleTap@{ tapOffset ->
                                    if (isScrollLocked) {
                                        Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                            "spread.quickDoubleTap.blocked scrollLocked=true offset=$tapOffset"
                                        )
                                        return@quickDoubleTap
                                    }
                                    val startScale = latestSpreadScale.value
                                    val startOffset = latestSpreadOffset.value
                                    val targetScale = if (startScale > 1.1f) 1f else 2.5f
                                    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                        "spread.quickDoubleTap offset=$tapOffset startScale=$startScale " +
                                            "targetScale=$targetScale startOffset=$startOffset"
                                    )
                                    val targetOffset = spreadTargetOffset(
                                        startScale = startScale,
                                        targetScale = targetScale,
                                        startOffset = startOffset,
                                        pivot = tapOffset
                                    )
                                    coroutineScope.launch {
                                        Animatable(0f).animateTo(
                                            1f,
                                            animationSpec = tween(durationMillis = 300)
                                        ) {
                                            currentActiveScale = androidx.compose.ui.util.lerp(
                                                startScale,
                                                targetScale,
                                                value
                                            )
                                            currentActiveOffset = lerp(
                                                startOffset,
                                                targetOffset,
                                                value
                                            )
                                            currentPageScale = currentActiveScale
                                        }
                                        if (currentActiveScale <= 1.05f) {
                                            currentActiveScale = 1f
                                            currentActiveOffset = Offset.Zero
                                            currentPageScale = 1f
                                        }
                                    }
                                },
                                onOneHandZoomHoldStart = { _ ->
                                    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                        "spread.oneHandHoldStart scale=${latestSpreadScale.value} " +
                                            "offset=${latestSpreadOffset.value}"
                                    )
                                    spreadPanFlingJob?.cancel()
                                    spreadPanFlingJob = null
                                    oneHandZoomStartScale = latestSpreadScale.value
                                    oneHandZoomStartOffset = latestSpreadOffset.value
                                },
                                onOneHandZoom = { _, totalDragY ->
                                    val viewportCenter = Offset(size.width / 2f, size.height / 2f)
                                    val nextScale = pdfOneHandZoomScale(
                                        startScale = oneHandZoomStartScale,
                                        totalDragY = totalDragY,
                                        dragDistanceForDoublePx = oneHandZoomDistancePx,
                                        minScale = 1f,
                                        maxScale = PDF_MAX_ZOOM_SCALE
                                    )
                                    currentActiveScale = nextScale
                                    currentActiveOffset = spreadTargetOffset(
                                        startScale = oneHandZoomStartScale,
                                        targetScale = nextScale,
                                        startOffset = oneHandZoomStartOffset,
                                        pivot = viewportCenter
                                    )
                                    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).v(
                                        "spread.oneHandUpdate dragY=$totalDragY startScale=$oneHandZoomStartScale " +
                                            "nextScale=$nextScale offset=$currentActiveOffset"
                                    )
                                    currentPageScale = currentActiveScale
                                },
                                onOneHandZoomEnd = { _ ->
                                    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                        "spread.oneHandEnd scale=$currentActiveScale offset=$currentActiveOffset"
                                    )
                                    if (currentActiveScale > 1f && currentActiveScale < 1.05f) {
                                        currentActiveScale = 1f
                                        currentActiveOffset = Offset.Zero
                                        currentPageScale = 1f
                                    }
                                }
                            )
                        }
                        .pointerInput(
                            useSharedSpreadZoom,
                            isDrawingActive,
                            isScrollLocked,
                            totalDisplayPages
                        ) {
                            if (!useSharedSpreadZoom || isDrawingActive) return@pointerInput
                            val touchSlop = viewConfiguration.touchSlop
                            val decay = splineBasedDecay<Float>(this)
                            val velocityTracker = VelocityTracker()

                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                spreadPanFlingJob?.cancel()
                                spreadPanFlingJob = null
                                velocityTracker.resetTracking()

                                var gestureScale = latestSpreadScale.value
                                var gestureOffset = latestSpreadOffset.value
                                var accumulatedZoom = 1f
                                var accumulatedPan = Offset.Zero
                                var velocityAccumulator = Offset.Zero
                                var mode = 0
                                var hasConsumedGesture = false

                                do {
                                    val event = awaitPointerEvent()
                                    val canceled = event.changes.any { it.isConsumed }
                                    if (canceled) {
                                        Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                            "spread.panDetector.canceledByConsumed mode=$mode scale=$gestureScale " +
                                                "changes=${event.changes.joinToString { change ->
                                                    "pressed=${change.pressed},consumed=${change.isConsumed},moved=${change.positionChanged()}"
                                                }}"
                                        )
                                    }
                                    if (!canceled) {
                                        val pointerCount = event.changes.count { it.pressed }
                                        val rawPanChange = event.calculatePan()
                                        val panChange = if (isScrollLocked && pointerCount == 1) {
                                            Offset.Zero
                                        } else {
                                            rawPanChange
                                        }
                                        val zoomChange = event.calculateZoom()
                                        accumulatedZoom *= zoomChange
                                        accumulatedPan += panChange

                                        if (gestureScale > 1f) {
                                            if (mode == 0) {
                                                mode = if (pointerCount > 1 && abs(accumulatedZoom - 1f) > 0.025f) {
                                                    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                                        "spread.panDetector.modeZoom scale=$gestureScale accumulatedZoom=$accumulatedZoom"
                                                    )
                                                    2
                                                } else if (accumulatedPan.getDistance() > touchSlop) {
                                                    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                                        "spread.panDetector.modePan scale=$gestureScale accumulatedPan=$accumulatedPan"
                                                    )
                                                    1
                                                } else {
                                                    0
                                                }
                                            }

                                            if (mode == 1 || mode == 2) {
                                                val oldScale = gestureScale
                                                val nextScale = if (mode == 2 && pointerCount > 1) {
                                                    (gestureScale * zoomChange)
                                                        .coerceIn(1f, PDF_MAX_ZOOM_SCALE)
                                                } else {
                                                    gestureScale
                                                }
                                                val ratio = if (oldScale == 0f) 1f else nextScale / oldScale
                                                val previousCentroid = event.calculateCentroid(useCurrent = false)
                                                val viewportCenter = Offset(size.width / 2f, size.height / 2f)
                                                val nextOffset = if (mode == 2 && pointerCount > 1 && previousCentroid != Offset.Unspecified) {
                                                    gestureOffset * ratio + (previousCentroid - viewportCenter) * (1 - ratio) + panChange
                                                } else {
                                                    gestureOffset + panChange
                                                }

                                                gestureScale = nextScale
                                                gestureOffset = clampPdfSpreadCameraOffset(
                                                    scale = gestureScale,
                                                    offset = nextOffset,
                                                    viewportWidth = size.width.toFloat(),
                                                    viewportHeight = size.height.toFloat()
                                                )
                                                currentActiveScale = gestureScale
                                                currentActiveOffset = gestureOffset
                                                currentPageScale = gestureScale
                                                hasConsumedGesture = true
                                                if (mode == 1 && panChange != Offset.Zero && event.changes.isNotEmpty()) {
                                                    velocityAccumulator += panChange
                                                    velocityTracker.addPosition(
                                                        event.changes[0].uptimeMillis,
                                                        velocityAccumulator
                                                    )
                                                }
                                                event.changes.forEach {
                                                    if (it.positionChanged()) it.consume()
                                                }
                                            }
                                        } else if (pointerCount > 1) {
                                            if (mode == 0) {
                                                mode = if (abs(accumulatedZoom - 1f) > 0.025f) {
                                                    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                                        "spread.panDetector.modeZoomAtBase accumulatedZoom=$accumulatedZoom"
                                                    )
                                                    2
                                                } else {
                                                    0
                                                }
                                            }

                                            if (mode == 2) {
                                                val oldScale = gestureScale
                                                val nextScale = (gestureScale * zoomChange)
                                                    .coerceIn(1f, PDF_MAX_ZOOM_SCALE)
                                                val ratio = if (oldScale == 0f) 1f else nextScale / oldScale
                                                val previousCentroid = event.calculateCentroid(useCurrent = false)
                                                val viewportCenter = Offset(size.width / 2f, size.height / 2f)
                                                val nextOffset = if (previousCentroid != Offset.Unspecified) {
                                                    gestureOffset * ratio + (previousCentroid - viewportCenter) * (1 - ratio) + panChange
                                                } else {
                                                    gestureOffset + panChange
                                                }
                                                gestureScale = nextScale
                                                gestureOffset = clampPdfSpreadCameraOffset(
                                                    scale = gestureScale,
                                                    offset = nextOffset,
                                                    viewportWidth = size.width.toFloat(),
                                                    viewportHeight = size.height.toFloat()
                                                )
                                                currentActiveScale = gestureScale
                                                currentActiveOffset = gestureOffset
                                                currentPageScale = gestureScale
                                                hasConsumedGesture = true
                                                event.changes.forEach {
                                                    if (it.positionChanged()) it.consume()
                                                }
                                            }
                                        }
                                    }
                                } while (!canceled && event.changes.any { it.pressed })

                                if (hasConsumedGesture && currentActiveScale > 1f && currentActiveScale < 1.05f) {
                                    coroutineScope.launch {
                                        val startScale = currentActiveScale
                                        val startOffset = currentActiveOffset
                                        Animatable(0f).animateTo(1f, animationSpec = tween(durationMillis = 180)) {
                                            currentActiveScale = androidx.compose.ui.util.lerp(startScale, 1f, value)
                                            currentActiveOffset =
                                                lerp(startOffset, Offset.Zero, value)
                                            currentPageScale = currentActiveScale
                                        }
                                        currentActiveScale = 1f
                                        currentActiveOffset = Offset.Zero
                                        currentPageScale = 1f
                                    }
                                } else if (hasConsumedGesture && mode == 1 && currentActiveScale > 1f) {
                                    val velocity = velocityTracker.calculateVelocity()
                                    val flingX = if (!isScrollLocked && abs(velocity.x) > PDF_SPREAD_PAN_FLING_MIN_VELOCITY) {
                                        velocity.x * PDF_SPREAD_PAN_FLING_MULTIPLIER
                                    } else {
                                        0f
                                    }
                                    val flingY = if (abs(velocity.y) > PDF_SPREAD_PAN_FLING_MIN_VELOCITY) {
                                        velocity.y * PDF_SPREAD_PAN_FLING_MULTIPLIER
                                    } else {
                                        0f
                                    }

                                    if (flingX != 0f || flingY != 0f) {
                                        spreadPanFlingJob = coroutineScope.launch {
                                            try {
                                                val startOffset = currentActiveOffset
                                                var decayedX = startOffset.x
                                                var decayedY = startOffset.y
                                                kotlinx.coroutines.coroutineScope {
                                                    launch {
                                                        if (flingX != 0f) {
                                                            Animatable(startOffset.x).animateDecay(flingX, decay) {
                                                                decayedX = value
                                                                currentActiveOffset = clampPdfSpreadCameraOffset(
                                                                    scale = currentActiveScale,
                                                                    offset = Offset(decayedX, decayedY),
                                                                    viewportWidth = size.width.toFloat(),
                                                                    viewportHeight = size.height.toFloat()
                                                                )
                                                            }
                                                        }
                                                    }
                                                    launch {
                                                        if (flingY != 0f) {
                                                            Animatable(startOffset.y).animateDecay(flingY, decay) {
                                                                decayedY = value
                                                                currentActiveOffset = clampPdfSpreadCameraOffset(
                                                                    scale = currentActiveScale,
                                                                    offset = Offset(decayedX, decayedY),
                                                                    viewportWidth = size.width.toFloat(),
                                                                    viewportHeight = size.height.toFloat()
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            } finally {
                                                spreadPanFlingJob = null
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = currentActiveScale
                            scaleY = currentActiveScale
                            translationX = currentActiveOffset.x
                            translationY = currentActiveOffset.y
                        }
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(spreadPageGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        spreadPageIndices.forEach { pageIndex ->
            key(pageIndex) {
                val spreadPageWidth = if (spreadPageCount > 1) {
                    val pageAspectRatio = displayPageRatios.getOrElse(pageIndex) { 1f }
                    with(density) {
                        pdfSpreadPageSlotWidth(
                            containerWidth = boxMaxWidthFloat,
                            containerHeight = boxMaxHeightFloat,
                            pageGap = spreadPageGapPx,
                            spreadPageCount = spreadPageCount,
                            pageAspectRatio = pageAspectRatio
                        ).toDp()
                    }
                } else {
                    with(density) { boxMaxWidthFloat.toDp() }
                }
                // Fold the drawn page, not the slot: each display page uses its own
                // aspect ratio, so mixed page sizes turn with their own fitted rect.
                val turnSheetSize = if (realisticPageTurnActive) {
                    pdfPaginationTurnSheetSize(
                        availableWidth = spreadPageWidth,
                        availableHeight = with(density) { boxMaxHeightFloat.toDp() },
                        aspectRatio = displayPageRatios.getOrElse(pageIndex) { 1f },
                    )
                } else {
                    null
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
    val currentEraserStrokeWidthState by rememberUpdatedState(
        currentEraserStrokeWidth
    )

    @Suppress("ControlFlowWithEmptyBody") val onDrawPagination =
        remember(pageIndex) {
            { point: PdfPoint, isEraserOverride: Boolean ->
                val effectiveTool = if (isEraserOverride) InkType.ERASER else currentSelectedTool
                if (effectiveTool == InkType.TEXT) {
                } else if (effectiveTool == InkType.ERASER) {
                    val eraserStrokeWidth = resolveEraserStrokeWidth(
                        isEraserOverride,
                        currentStrokeWidthState,
                        currentEraserStrokeWidthState
                    )
                    val aspectRatio = pageAspectRatios.getOrElse(pageIndex) { 1f }
                    val existing = allAnnotations[pageIndex] ?: emptyList()
                    val toRemove = existing.filter {
                        isAnnotationHit(it, point, lastEraserPoint, aspectRatio, eraserStrokeWidth)
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
                        val eraserStrokeWidth = resolveEraserStrokeWidth(
                            isEraserOverride,
                            currentStrokeWidthState,
                            currentEraserStrokeWidthState
                        )
                        val aspectRatio = pageAspectRatios.getOrElse(pageIndex) { 1f }
                        val existing = allAnnotations[pageIndex] ?: emptyList()
                        val toRemove = existing.filter {
                            isAnnotationHit(it, point, lastEraserPoint, aspectRatio, eraserStrokeWidth)
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
        documentKey = activeDocumentRenderKey,
        pageIndex = pageIndex,
        virtualPage = virtualPage,
        totalPages = totalDisplayPages,
        activeTheme = activeTheme,
        activeTextureAlpha = 1f - globalTextureTransparency,
        excludeImages = excludeImages,
        reverseColorMode = reverseColorMode,
        isScrollLocked = if (useSharedSpreadZoom) false else isScrollLocked,
        customHighlightColors = customHighlightColors,
        externalScale = if (useSharedSpreadZoom) currentActiveScale else 1f,
        onPaletteClick = {
            highlightColorPickerInitialSlot = PdfHighlightColor.YELLOW
            showHighlightColorPicker = true
        },
        onScaleChanged = { newScale ->
            if (isActivePagerPage && !useSharedSpreadZoom) {
                currentPageScale = newScale
            }
        },
        ttsHighlightData = if (ttsDisplayPageIndex == pageIndex) ttsHighlightData else null,
        searchQuery = searchState.searchQuery,
        searchHighlightMode = searchHighlightMode,
        searchResultToHighlight = if (isActivePagerPage) searchHighlightTarget else null,
        ocrHoverHighlights = stableOcrRects,
        modifier = turnSheetModifier.then(
            when {
                turnSheetSize != null -> Modifier.size(turnSheetSize)
                spreadPageIndices.size > 1 -> Modifier.width(spreadPageWidth).fillMaxHeight()
                else -> Modifier.fillMaxSize()
            }
        ),
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
        isZoomEnabled = !useSharedSpreadZoom,
        showPageNumberOverlay = showPageNumberOverlay,
        visualScaleProvider = if (useSharedSpreadZoom) {
            { currentActiveScale }
        } else {
            { 1f }
        },
        clearSelectionTrigger = selectionClearTrigger,
        resetZoomTrigger = if (useSharedSpreadZoom) 0L else resetZoomTrigger,
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
                val nextAnnotations =
                    allAnnotations + (pageIdx to (existing + finalAnnotation))
                allAnnotations = nextAnnotations
                persistInkAnnotationsNow(
                    nextAnnotations,
                    emptyList(),
                    "draw_end"
                )
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
                persistInkAnnotationsNow(
                    allAnnotations,
                    removalMap.values.flatten(),
                    "erase_end"
                )
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
        userHighlights = visibleUserHighlightsByPage[pageIndex].orEmpty(),
        onHighlightAdd = onHighlightAdd,
        onHighlightUpdate = onHighlightUpdate,
        onHighlightDelete = onHighlightDelete,
        onNoteRequested = onNoteRequested,
        onTts = { pageIdx, charIdx -> startTtsWithPermissionCheck(pageIdx, charIdx) },
        activeToolThickness = currentStrokeWidthState,
        eraserToolThickness = currentEraserStrokeWidthState,
        lockedState = if (useSharedSpreadZoom) null else lockedState,
        onZoomAndPanChanged = { newScale, newOffset ->
            if (isActivePagerPage && !useSharedSpreadZoom) {
                currentActiveScale = newScale
                currentActiveOffset = newOffset
            }
        },
        onDetectBubbles = { sourcePageIndex, bitmap ->
            detectSpeechBubblesForPage(sourcePageIndex, bitmap)
        },
        onShowPanelPopup = { bitmapWithRects ->
            val safeBitmap = bitmapWithRects.scaledToCanvasLimit()
            if (safeBitmap !== bitmapWithRects && !bitmapWithRects.isRecycled) {
                bitmapWithRects.recycle()
            }
            poppedUpPanelBitmap?.takeUnless { it.isRecycled }?.recycle()
            poppedUpPanelBitmap = safeBitmap
        },
        onTwoFingerSwipe = { direction ->
            coroutineScope.launch {
                val current = currentPaginationDisplayPage()
                val targetPage = if (direction > 0) {
                    PdfSpreadLayout.nextPageIndex(current, totalDisplayPages, pdfSpreadSettings)
                } else {
                    PdfSpreadLayout.previousPageIndex(current, totalDisplayPages, pdfSpreadSettings)
                }
                animatePaginationToDisplayPage(targetPage)
            }
        },
        richTextController = richTextController,
        isStylusOnlyMode = isStylusOnlyMode,
        stylusButtonHovering = stylusButtonHovering,
        isAutoScrollPlaying = isAutoScrollPlaying,
        isHighlighterSnapEnabled = isHighlighterSnapEnabled,
        isEditMode = isDrawingActive,
        textBoxes = visibleTextBoxesByPage[pageIndex].orEmpty(),
        selectedTextBoxId = selectedTextBoxId,
        onTextBoxChange = { updatedBox ->
            val idx = textBoxes.indexOfFirst { it.id == updatedBox.id }
            val previousLength = textBoxes.getOrNull(idx)?.text?.length
            Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).d(
                "event=viewer_value_change path=pagination id=${updatedBox.id} " +
                    "page=${updatedBox.pageIndex} oldLength=${previousLength ?: -1} " +
                    "newLength=${updatedBox.text.length} matched=${idx != -1} " +
                    "selected=${updatedBox.id == selectedTextBoxId} textBoxEditMode=$isDrawingActive"
            )
            if (idx != -1) textBoxes[idx] = updatedBox
        },
        onTextBoxSelect = { id ->
            Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).d(
                "event=viewer_select path=pagination id=$id " +
                    "selectedBefore=${selectedTextBoxId ?: "none"} textBoxEditMode=$isDrawingActive"
            )
            selectedTextBoxId = id
            richTextController?.clearSelection()
        },
        draggingBoxId = paginationDraggingBoxId,
        onTextBoxDragStart = { box, _, _ ->
            Timber.tag("PdfTextBoxDebug").d("Pagination onTextBoxDragStart [ID: ${box.id}] initialized")
            val pageAspectRatio = displayPageRatios.getOrElse(pageIndex) { 1f }

            val containerWidthPx = paginationPageState.boxMaxWidth
            val containerHeightPx = paginationPageState.boxMaxHeight

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
            val screenWidth = paginationPageState.boxMaxWidth.toFloat()

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
                    if (pagerState.currentPage < pagerState.pageCount - 1 && !pagerState.isScrollInProgress) {
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
                    val currentSpreadPageIndices = PdfSpreadLayout.visiblePageIndices(
                        pageIndex = currentPaginationDisplayPage(),
                        pageCount = totalDisplayPages,
                        settings = pdfSpreadSettings
                    )
                    val targetPage = if (pageIndex in currentSpreadPageIndices) {
                        pageIndex
                    } else {
                        currentSpreadPageIndices.firstOrNull() ?: currentPaginationDisplayPage()
                    }
                    val targetVirtualPage = virtualPages.getOrNull(targetPage)
                    val pageAspectRatio = if (targetVirtualPage is VirtualPage.BlankPage) {
                        if (targetVirtualPage.height > 0) targetVirtualPage.width.toFloat() / targetVirtualPage.height.toFloat() else 1f
                    } else {
                        displayPageRatios.getOrElse(targetPage) { 1f }
                    }

                    val containerWidthPx = paginationPageState.boxMaxWidth
                    val containerHeightPx = paginationPageState.boxMaxHeight

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
                val current = currentPaginationDisplayPage()
                val targetPage = if (direction > 0) {
                    PdfSpreadLayout.nextPageIndex(current, totalDisplayPages, pdfSpreadSettings)
                } else {
                    PdfSpreadLayout.previousPageIndex(current, totalDisplayPages, pdfSpreadSettings)
                }
                animatePaginationToDisplayPage(targetPage)
            }
        },
        isBubbleZoomModeActive = isBubbleZoomModeActive,
        isVisible = isVisiblePage,
        isActivePage = isActivePagerPage,
        isScrolling = pagerState.isScrollInProgress
    )
            }
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
private fun PdfViewerReaderSurface(surfaceState: PdfViewerSurfaceState) {
    SharedMobileReaderScaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
            val stylusButtonHoveringState = remember { mutableStateOf(false) }
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
                                        if (!change.pressed) {
                                            stylusButtonHoveringState.value = buttons.isPrimaryPressed || buttons.isSecondaryPressed
                                        }
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
                PdfViewerSurfaceContent(
                    surfaceState = surfaceState,
                    stylusButtonHovering = stylusButtonHoveringState.value,
                )
            }
    }
}

@Composable
private fun PdfViewerTransientOverlays(content: @Composable () -> Unit) {
    content()
}
