// EpubReaderRenderSurfaces.kt
//
// Reader render surfaces extracted from EpubReaderHost's scaffold content
// closure. The monolithic closure compiled into an ART-hostile method that
// some verifiers reject at class load (VerifyError). State objects created in
// the host are passed in and re-delegated locally so the moved body keeps its
// original spelling.
package com.aryan.reader.epubreader

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.Window
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
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.BookWordReplacementsSheet
import com.aryan.reader.BuildConfig
import com.aryan.reader.BuiltInThemes
import com.aryan.reader.MainViewModel
import com.aryan.reader.R
import com.aryan.reader.ReaderBrightnessEffect
import com.aryan.reader.ReaderBrightnessSheet
import com.aryan.reader.ReaderFileInfoDialogs
import com.aryan.reader.ReaderScreenOrientationEffect
import com.aryan.reader.ReaderScreenOrientationSheet
import com.aryan.reader.ReaderThemePanel
import com.aryan.reader.RenderMode
import com.aryan.reader.SummaryCacheManager
import com.aryan.reader.TtsSettingsSheet
import com.aryan.reader.TtsWordReplacementsSheet
import com.aryan.reader.areReaderAiFeaturesEnabled
import com.aryan.reader.copyPlainTextToClipboard
import com.aryan.reader.countWords
import com.aryan.reader.data.CustomFontEntity
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.epub.hasReadableExtractedContent
import com.aryan.reader.epub.plainTextCharacterCount
import com.aryan.reader.fetchAiDefinition
import com.aryan.reader.isByokCloudTtsAvailable
import com.aryan.reader.loadBookReplacementPreferences
import com.aryan.reader.loadCustomThemes
import com.aryan.reader.loadEpubRightToLeftPagination
import com.aryan.reader.loadGlobalTextureTransparency
import com.aryan.reader.loadReaderBrightnessSettings
import com.aryan.reader.loadReaderScreenOrientationMode
import com.aryan.reader.loadReaderSliderToggled
import com.aryan.reader.loadReaderTextureBitmap
import com.aryan.reader.loadReaderThemeId
import com.aryan.reader.loadTtsReplacementPreferences
import com.aryan.reader.paginatedreader.BookPaginator
import com.aryan.reader.paginatedreader.CssParser
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
import com.aryan.reader.readerSliderBookmarkPosition
import com.aryan.reader.readerSliderChromeColors
import com.aryan.reader.readerSliderToggleState
import com.aryan.reader.rememberSearchState
import com.aryan.reader.saveBookReplacementPreferences
import com.aryan.reader.saveCustomThemes
import com.aryan.reader.saveEpubRightToLeftPagination
import com.aryan.reader.saveGlobalTextureTransparency
import com.aryan.reader.saveReaderBrightnessSettings
import com.aryan.reader.saveReaderScreenOrientationMode
import com.aryan.reader.saveReaderSliderToggled
import com.aryan.reader.saveReaderThemeId
import com.aryan.reader.saveTtsReplacementPreferences
import com.aryan.reader.shared.AiDefinitionResult
import com.aryan.reader.shared.EpubBlockPosition
import com.aryan.reader.shared.EpubVisibleTextRange
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.ReaderLocator as SharedReaderLocator
import com.aryan.reader.shared.ReaderMotionPolicy
import com.aryan.reader.shared.ReaderSearchState as SearchState
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.SearchResult
import com.aryan.reader.shared.SummarizationResult
import com.aryan.reader.shared.findEpubBookmarkForLocation
import com.aryan.reader.shared.reader.MobileEpubReaderBackAction
import com.aryan.reader.shared.reader.ReaderJumpHistory
import com.aryan.reader.shared.reader.mobileEpubChapterScrollFraction
import com.aryan.reader.shared.reader.mobileEpubCharacterDisplayProgress
import com.aryan.reader.shared.reader.mobileEpubCharacterProgress
import com.aryan.reader.shared.reader.selectMobileEpubReaderBackAction
import com.aryan.reader.shared.ui.SharedMobileReaderDrawer
import com.aryan.reader.shared.ui.SharedMobileReaderRecoveryGate
import com.aryan.reader.shared.ui.SharedMobileReaderScaffold
import com.aryan.reader.shared.ui.rememberReaderMotionPolicy
import com.aryan.reader.shouldRenderReaderSlider
import com.aryan.reader.tts.SpeakerSamplePlayer
import com.aryan.reader.tts.TtsController
import com.aryan.reader.tts.TtsPlaybackManager
import com.aryan.reader.tts.loadReaderTtsOverlaySize
import com.aryan.reader.tts.loadTtsMode
import com.aryan.reader.tts.readerTtsOverlayAlignmentBias
import com.aryan.reader.tts.saveReaderTtsOverlaySize
import com.aryan.reader.tts.splitTextIntoChunks
import com.aryan.reader.withTtsReplacements
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import timber.log.Timber

private const val TAG_LINK_NAV = "LINK_NAV"
private const val TAG_VERTICAL_JITTER = "EpubVerticalJitter"
private const val TAG_STABLE_PAGE_NAV = "StablePageNav"
private const val TAG_PAGINATED_HIGHLIGHT_DIAG = "PaginatedHighlightDiag"

@Suppress("UNUSED_PARAMETER", "LargeClass", "UnusedVariable")
@Composable
internal fun EpubReaderRenderSurfaces(
    addBookmarkRequestState: MutableState<Boolean>,
    bookReplacementPreferencesState: MutableState<ReaderBookReplacementPreferences>,
    bookmarksState: MutableState<Set<Bookmark>>,
    cfiToLoadState: MutableState<String?>,
    chapterChunkElementCountsState: MutableState<List<Int>>,
    chapterChunkElementStartIndicesState: MutableState<List<Int>>,
    chapterChunksState: MutableState<List<String>>,
    chapterHeadState: MutableState<String>,
    currentChapterIndexState: MutableState<Int>,
    currentClientHeightValueState: MutableState<Int>,
    currentHighlightPaletteState: MutableState<List<Int>>,
    currentRenderModeState: MutableState<RenderMode>,
    currentScrollHeightValueState: MutableState<Int>,
    currentScrollYPositionState: MutableState<Int>,
    fragmentToLoadState: MutableState<String?>,
    imageToLoadState: MutableState<EpubReaderImageReference?>,
    initialScrollTargetForChapterState: MutableState<ChapterScrollPosition?>,
    isAutoScrollModeActiveState: MutableState<Boolean>,
    isAutoScrollPlayingState: MutableState<Boolean>,
    isChapterParsingState: MutableState<Boolean>,
    isChapterReadyForBookmarkCheckState: MutableState<Boolean>,
    isMusicianModeState: MutableState<Boolean>,
    isPagerInitializedState: MutableState<Boolean>,
    isRecapLoadingState: MutableState<Boolean>,
    isRequestingRecapCfiState: MutableState<Boolean>,
    isSavingAndExitingState: MutableState<Boolean>,
    isSummarizationLoadingState: MutableState<Boolean>,
    isSwitchingToPaginatedState: MutableState<Boolean>,
    lastHighlightClickTimeState: MutableState<Long>,
    lastKnownLocatorState: MutableState<Locator?>,
    lastScrollHideTimeState: MutableState<Long>,
    loadUpToChunkIndexState: MutableState<Int>,
    loadedChunkCountState: MutableState<Int>,
    nativeVerticalCurrentPageState: MutableState<Int>,
    nativeVerticalLocationState: MutableState<NativeVerticalLocation?>,
    nativeVerticalProgressState: MutableState<Float>,
    nativeVerticalTotalPagesState: MutableState<Int>,
    paginatedExplicitNavigationAnchorState: MutableState<Locator?>,
    paginatedExplicitNavigationEpochState: MutableState<Long>,
    paginatorState: MutableState<IPaginator?>,
    pullToNextProgressState: MutableState<Float>,
    pullToPrevProgressState: MutableState<Float>,
    recapResultState: MutableState<SummarizationResult?>,
    searchHighlightTargetState: MutableState<SearchResult?>,
    showAiHubSheetState: MutableState<Boolean>,
    showBarsState: MutableState<Boolean>,
    showDictionaryUpsellDialogState: MutableState<Boolean>,
    summarizationResultState: MutableState<SummarizationResult?>,
    topVisibleChunkIndexState: MutableState<Int>,
    ttsChapterIndexState: MutableState<Int?>,
    ttsShouldStartOnChapterLoadState: MutableState<Boolean>,
    userStoppedTtsState: MutableState<Boolean>,
    webViewRefForTtsState: MutableState<WebView?>,
    activeTextureAlpha: Float,
    activeTextureId: String?,
    bookId: String,
    bookReplacementSignature: String,
    chapters: List<EpubChapter>,
    containerFocusRequester: FocusRequester,
    context: Context,
    currentPageInChapter: Int,
    dragThresholdPx: Float,
    effectiveBg: Color,
    effectiveText: Color,
    epubFontFaceCss: String,
    focusManager: FocusManager,
    format: EpubReaderFormatState,
    pageInfoBarHeight: Dp,
    prefs: EpubReaderReadingPrefsState,
    readerCacheBookId: String,
    scope: CoroutineScope,
    summaryCacheManager: SummaryCacheManager,
    totalBookLengthChars: Long,
    verticalScrollRequests: EpubReaderVerticalScrollRequests,
    window: Window?,
    motionPolicy: ReaderMotionPolicy,
    navigation: EpubReaderNavigationState,
    searchState: SearchState,
    locatorConverter: LocatorConverter,
    paginatedPagerState: PagerState,
    userHighlights: SnapshotStateList<UserHighlight>,
    epubBook: EpubBook,
    viewModel: MainViewModel,
    isNativeVerticalMode: Boolean,
    isDarkTheme: Boolean,
    isProUser: Boolean,
    credits: Int,
    coverImagePath: String?,
    onSavePosition: (Locator, String?, Float) -> Unit,
    onRenderModeChange: (RenderMode) -> Unit,
    onNavigateBack: () -> Unit,
    currentChapterInPaginatedMode: Int?,
    latestChapterIndex: Int,
    ttsState: TtsPlaybackManager.TtsState,
    ttsController: TtsController,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    totalPagesInCurrentChapter: Int,
    clearPendingTtsRelocationStateFn: (String) -> Unit,
    detachVerticalReaderFromTtsFn: (String) -> Unit,
    logTtsChapterDiagFn: (String) -> Unit,
    isActiveReaderTtsForCurrentBookFn: () -> Boolean,
    currentEpubJumpLocatorFn: () -> SharedReaderLocator?,
    currentNativeVerticalLocatorFn: () -> Locator?,
    requestNativeVerticalLocatorScrollFn: (Locator?, Int?, Int?, Boolean) -> Unit,
    triggerAutoScrollTempPauseFn: (Long) -> Unit,
    fragmentJumpLocatorFn: (Int, String?, String?) -> SharedReaderLocator,
    paginatedJumpLocatorForPageFn: (Int, Locator?, Int?, Boolean) -> SharedReaderLocator?,
    recordEpubJumpFn: (SharedReaderLocator?, SharedReaderLocator?) -> Unit,
    startTtsFromSelectionPaginatedFn: (String, Int, Int?) -> Unit,
    onSearchLookupFn: (String)->Unit,
    onTranslateLookupFn: (String)->Unit,
    onDictionaryLookupFn: (String)->Unit,
    onUpdateHighlightPaletteFn: (Int, Int) -> Unit,
    runRecapFn: (Int, Int) -> Unit
) {
    var addBookmarkRequest by addBookmarkRequestState
    var bookReplacementPreferences by bookReplacementPreferencesState
    var bookmarks by bookmarksState
    var cfiToLoad by cfiToLoadState
    var chapterChunkElementCounts by chapterChunkElementCountsState
    var chapterChunkElementStartIndices by chapterChunkElementStartIndicesState
    var chapterChunks by chapterChunksState
    var chapterHead by chapterHeadState
    var currentChapterIndex by currentChapterIndexState
    var currentClientHeightValue by currentClientHeightValueState
    var currentHighlightPalette by currentHighlightPaletteState
    var currentRenderMode by currentRenderModeState
    var currentScrollHeightValue by currentScrollHeightValueState
    var currentScrollYPosition by currentScrollYPositionState
    var fragmentToLoad by fragmentToLoadState
    var imageToLoad by imageToLoadState
    var initialScrollTargetForChapter by initialScrollTargetForChapterState
    var isAutoScrollModeActive by isAutoScrollModeActiveState
    var isAutoScrollPlaying by isAutoScrollPlayingState
    var isChapterParsing by isChapterParsingState
    var isChapterReadyForBookmarkCheck by isChapterReadyForBookmarkCheckState
    var isMusicianMode by isMusicianModeState
    var isPagerInitialized by isPagerInitializedState
    var isRecapLoading by isRecapLoadingState
    var isRequestingRecapCfi by isRequestingRecapCfiState
    var isSavingAndExiting by isSavingAndExitingState
    var isSummarizationLoading by isSummarizationLoadingState
    var isSwitchingToPaginated by isSwitchingToPaginatedState
    var lastHighlightClickTime by lastHighlightClickTimeState
    var lastKnownLocator by lastKnownLocatorState
    var lastScrollHideTime by lastScrollHideTimeState
    var loadUpToChunkIndex by loadUpToChunkIndexState
    var loadedChunkCount by loadedChunkCountState
    var nativeVerticalCurrentPage by nativeVerticalCurrentPageState
    var nativeVerticalLocation by nativeVerticalLocationState
    var nativeVerticalProgress by nativeVerticalProgressState
    var nativeVerticalTotalPages by nativeVerticalTotalPagesState
    var paginatedExplicitNavigationAnchor by paginatedExplicitNavigationAnchorState
    var paginatedExplicitNavigationEpoch by paginatedExplicitNavigationEpochState
    var paginator by paginatorState
    var pullToNextProgress by pullToNextProgressState
    var pullToPrevProgress by pullToPrevProgressState
    var recapResult by recapResultState
    var searchHighlightTarget by searchHighlightTargetState
    var showAiHubSheet by showAiHubSheetState
    var showBars by showBarsState
    var showDictionaryUpsellDialog by showDictionaryUpsellDialogState
    var summarizationResult by summarizationResultState
    var topVisibleChunkIndex by topVisibleChunkIndexState
    var ttsChapterIndex by ttsChapterIndexState
    var ttsShouldStartOnChapterLoad by ttsShouldStartOnChapterLoadState
    var userStoppedTts by userStoppedTtsState
    var webViewRefForTts by webViewRefForTtsState
    fun clearPendingTtsRelocationState(reason: String) = clearPendingTtsRelocationStateFn(reason)
    fun detachVerticalReaderFromTts(reason: String) = detachVerticalReaderFromTtsFn(reason)
    fun logTtsChapterDiag(message: String) = logTtsChapterDiagFn(message)
    fun isActiveReaderTtsForCurrentBook() = isActiveReaderTtsForCurrentBookFn()
    fun currentEpubJumpLocator() = currentEpubJumpLocatorFn()
    fun currentNativeVerticalLocator() = currentNativeVerticalLocatorFn()
    fun requestNativeVerticalLocatorScroll(locator: Locator?, fallbackPage: Int? = null, fallbackChapterIndex: Int? = locator?.chapterIndex, keepVisible: Boolean = false) = requestNativeVerticalLocatorScrollFn(locator, fallbackPage, fallbackChapterIndex, keepVisible)
    fun triggerAutoScrollTempPause(durationMs: Long) = triggerAutoScrollTempPauseFn(durationMs)
    fun fragmentJumpLocator(chapterIndex: Int, fragment: String?, href: String? = null) = fragmentJumpLocatorFn(chapterIndex, fragment, href)
    fun paginatedJumpLocatorForPage(pageIndex: Int, targetLocator: Locator? = null, fallbackChapterIndex: Int? = null, allowPageFallback: Boolean = false) = paginatedJumpLocatorForPageFn(pageIndex, targetLocator, fallbackChapterIndex, allowPageFallback)
    fun recordEpubJump(target: SharedReaderLocator?, currentLocator: SharedReaderLocator? = currentEpubJumpLocator()) = recordEpubJumpFn(target, currentLocator)
    fun startTtsFromSelectionPaginated(baseCfi: String, startOffset: Int, chapterIndexOverride: Int? = null) = startTtsFromSelectionPaginatedFn(baseCfi, startOffset, chapterIndexOverride)
    val onSearchLookup: (String)->Unit = { p0 -> onSearchLookupFn(p0) }
    val onTranslateLookup: (String)->Unit = { p0 -> onTranslateLookupFn(p0) }
    val onDictionaryLookup: (String)->Unit = { p0 -> onDictionaryLookupFn(p0) }
    val onUpdateHighlightPalette: (Int, Int) -> Unit = { p0, p1 -> onUpdateHighlightPaletteFn(p0, p1) }
    val runRecap: (Int, Int) -> Unit = { p0, p1 -> runRecapFn(p0, p1) }

                when (currentRenderMode) {
                    RenderMode.VERTICAL_SCROLL -> {
                        val pageInfoReserve = if (shouldReserveEpubPageInfoBarSpace(prefs.pageInfoMode, showBars, isNativeVerticalMode)) pageInfoBarHeight else 0.dp
                        val contentTopPadding = if (prefs.pageInfoPosition == PageInfoPosition.TOP) pageInfoReserve else 0.dp
                        val contentBottomPadding = if (prefs.pageInfoPosition == PageInfoPosition.BOTTOM) pageInfoReserve else 0.dp

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
                            } else if (isNativeVerticalMode) {
                                LaunchedEffect(currentChapterIndex, isNativeVerticalMode) {
                                    webViewRefForTts = null
                                    isChapterParsing = false
                                    isChapterReadyForBookmarkCheck = true
                                }
                                NativeVerticalReaderScreen(
                                    book = epubBook,
                                    bookId = readerCacheBookId,
                                    isDarkTheme = isDarkTheme,
                                    effectiveBg = effectiveBg,
                                    effectiveText = effectiveText,
                                    searchQuery = searchState.searchQuery,
                                    fontSizeMultiplier = format.currentFontSizeEm,
                                    lineHeightMultiplier = format.currentLineHeight,
                                    paragraphGapMultiplier = format.currentParagraphGap,
                                    imageSizeMultiplier = format.currentImageSize,
                                    hideImages = prefs.hideImages,
                                    horizontalMarginMultiplier = format.currentHorizontalMargin,
                                    verticalMarginMultiplier = format.currentVerticalMargin,
                                    fontFamily = format.activeFontFamily,
                                    fontWeight = format.currentFontWeight,
                                    letterSpacing = format.currentLetterSpacing,
                                    textAlign = format.currentTextAlign,
                                    bookReplacementPreferences = bookReplacementPreferences,
                                    bookReplacementFileId = bookId,
                                    activeHighlightPalette = currentHighlightPalette,
                                    onUpdatePalette = onUpdateHighlightPalette,
                                    ttsHighlightInfo = TtsHighlightInfo(
                                        text = ttsState.currentText ?: "",
                                        cfi = ttsState.sourceCfi ?: "",
                                        offset = ttsState.startOffsetInSource
                                    ).takeIf { ttsState.currentText != null && ttsState.sourceCfi != null && ttsState.startOffsetInSource != -1 },
                                    activeTextureId = activeTextureId,
                                    activeTextureAlpha = activeTextureAlpha,
                                    initialLocator = lastKnownLocator,
                                    initialPageIndexInBook = nativeVerticalCurrentPage,
                                    scrollRequestPage = verticalScrollRequests.nativeVerticalScrollRequest,
                                    scrollRequestPageAnimated = motionPolicy.animationsEnabled,
                                    scrollRequestLocator = verticalScrollRequests.nativeVerticalLocatorScrollRequest,
                                    scrollRequestLocatorId = verticalScrollRequests.nativeVerticalLocatorScrollRequestId,
                                    scrollRequestLocatorKeepVisible = verticalScrollRequests.nativeVerticalLocatorScrollKeepVisible,
                                    scrollRequestLocatorAnimated = motionPolicy.animationsEnabled,
                                    scrollRequestProgressPercent = verticalScrollRequests.nativeVerticalProgressScrollRequest,
                                    scrollRequestProgressId = verticalScrollRequests.nativeVerticalProgressScrollRequestId,
                                    scrollDeltaRequest = verticalScrollRequests.nativeVerticalScrollDeltaRequest,
                                    scrollDeltaRequestId = verticalScrollRequests.nativeVerticalScrollDeltaRequestId,
                                    scrollDeltaRequestAnimated = verticalScrollRequests.nativeVerticalScrollDeltaAnimated,
                                    onScrollRequestConsumed = { verticalScrollRequests.nativeVerticalScrollRequest = null },
                                    onScrollLocatorRequestConsumed = {
                                        verticalScrollRequests.nativeVerticalLocatorScrollRequest = null
                                        verticalScrollRequests.nativeVerticalLocatorScrollKeepVisible = false
                                    },
                                    onScrollProgressRequestConsumed = { verticalScrollRequests.nativeVerticalProgressScrollRequest = null },
                                    onScrollDeltaConsumed = { verticalScrollRequests.nativeVerticalScrollDeltaRequest = null },
                                    modifier = Modifier.fillMaxSize(),
                                    onPaginatorReady = { newPaginator ->
                                        paginator = newPaginator
                                    },
                                    onVisiblePageChanged = { pageIndex, chapterIndex, locator ->
                                        nativeVerticalCurrentPage = pageIndex
                                        if (chapterIndex != null) {
                                            currentChapterIndex = chapterIndex
                                        }
                                        if (locator != null) {
                                            lastKnownLocator = locator
                                        }
                                        currentScrollYPosition = pageIndex
                                        currentClientHeightValue = 1
                                        currentScrollHeightValue = nativeVerticalTotalPages.coerceAtLeast(1)
                                    },
                                    onProgressChanged = { pageIndex, totalPages, progressPercent ->
                                        nativeVerticalCurrentPage = pageIndex
                                        nativeVerticalTotalPages = totalPages
                                        nativeVerticalProgress = progressPercent.coerceIn(0f, 100f)
                                        currentScrollYPosition = pageIndex
                                        currentClientHeightValue = 1
                                        currentScrollHeightValue = totalPages.coerceAtLeast(1)
                                    },
                                    onLocationChanged = { location ->
                                        nativeVerticalLocation = location
                                        location.locatorForPersistence()?.let { lastKnownLocator = it }
                                    },
                                    onTap = {
                                        focusManager.clearFocus()
                                        if (prefs.volumeScrollEnabled && !searchState.isSearchActive) {
                                            containerFocusRequester.requestFocus()
                                        }
                                        if (showBars || navigation.showFormatAdjustmentBars) {
                                            showBars = false
                                            navigation.showFormatAdjustmentBars = false
                                        } else {
                                            showBars = true
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
                                    onStartTtsFromSelection = { cfi, offset, chapterIndex ->
                                        startTtsFromSelectionPaginated(cfi, offset, chapterIndex)
                                    },
                                    userHighlights = userHighlights.filter { highlight ->
                                        highlight.chapterIndex in (currentChapterIndex - 1)..(currentChapterIndex + 1)
                                    },
                                    onHighlightCreated = { cfi, text, colorId, locator, style ->
                                        val chapterIndex = locator.chapterIndex ?: currentChapterIndex
                                        val (color, colorArgb) = highlightColorFromToken(colorId)
                                        val finalCfi = processAndAddHighlight(
                                            newCfi = cfi,
                                            newText = text,
                                            newColor = color,
                                            chapterIndex = chapterIndex,
                                            currentList = userHighlights,
                                            locator = locator.withFallbacks(
                                                chapterIndex = chapterIndex,
                                                cfi = cfi,
                                                textQuote = text
                                            ),
                                            newColorArgb = colorArgb,
                                            newStyle = style
                                        )
                                        if (navigation.pendingNoteForNewHighlight) {
                                            navigation.pendingNoteForNewHighlight = false
                                            navigation.highlightToNoteCfi = finalCfi
                                        }
                                    },
                                    onNoteRequested = { cfi ->
                                        if (cfi != null) {
                                            navigation.highlightToNoteCfi = cfi
                                        } else {
                                            navigation.pendingNoteForNewHighlight = true
                                        }
                                    },
                                    onFootnoteRequested = { html ->
                                        navigation.activeFootnoteHtml = html
                                    },
                                    onInternalLinkNavigated = { targetPageIndex, targetLocatorFromLink ->
                                        val currentLocator = currentEpubJumpLocator()
                                        val bookPaginator = paginator as? BookPaginator
                                        val targetChapter = targetLocatorFromLink?.chapterIndex
                                            ?: bookPaginator?.findChapterIndexForPage(targetPageIndex)
                                        val targetLocator = targetLocatorFromLink ?: bookPaginator?.getLocatorForPage(targetPageIndex)
                                        if (targetChapter != null) {
                                            currentChapterIndex = targetChapter
                                        }
                                        if (targetLocator != null) {
                                            lastKnownLocator = targetLocator
                                        }
                                        paginatedJumpLocatorForPage(
                                            pageIndex = targetPageIndex,
                                            targetLocator = targetLocator,
                                            fallbackChapterIndex = targetChapter
                                        )?.let { recordEpubJump(it, currentLocator) }
                                    },
                                    onHighlightDeleted = { cfi ->
                                        userHighlights.find { it.cfi == cfi }?.let { userHighlights.remove(it) }
                                    }
                                )
                            } else {
                                AnimatedContent(
                                    targetState = currentChapterIndex,
                                    transitionSpec = {
                                        if (motionPolicy.reduceMotion) {
                                            androidx.compose.animation.EnterTransition.None togetherWith
                                                androidx.compose.animation.ExitTransition.None
                                        } else if (!prefs.pullToTurnEnabled) {
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
                                    // AnimatedContent may keep composing its outgoing state after a
                                    // replacement book has supplied a shorter chapter list.
                                    val chapterToRender = chapters.getOrNull(targetChapterIndex)
                                        ?: return@AnimatedContent
                                    if (isChapterParsing) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    } else if (chapterChunks.isNotEmpty()) {
                                        var hasRequestedExtractionForThisChapter by remember(targetChapterIndex) { mutableStateOf(false) }

                                        val initialContentToLoad = remember(
                                            loadUpToChunkIndex,
                                            chapterChunks,
                                            chapterChunkElementStartIndices,
                                            chapterChunkElementCounts
                                        ) {
                                            val targetIdx = loadUpToChunkIndex

                                            chapterChunks.indices.joinToString(separator = "\n") { index ->
                                                val attributes = readerChunkContainerAttributes(
                                                    index,
                                                    chapterChunkElementStartIndices,
                                                    chapterChunkElementCounts
                                                )
                                                if (shouldInlineInitialReaderChunk(index, chapterChunks.size, targetIdx)) {
                                                    "<div class='chunk-container' $attributes>${chapterChunks[index]}</div>"
                                                } else {
                                                    val placeholderHeightPx = readerChunkPlaceholderHeightPx(
                                                        index,
                                                        chapterChunkElementCounts
                                                    )
                                                    "<div class='chunk-container' $attributes style='height: ${placeholderHeightPx}px'></div>"
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

                                        val chapterFontFaceCss = remember(
                                            chapterHead,
                                            chapterToRender.absPath,
                                            epubBook.extractionBasePath
                                        ) {
                                            val fontFaces = Jsoup.parse("<head>$chapterHead</head>")
                                                .head()
                                                .getElementsByTag("style")
                                                .flatMap { styleElement ->
                                                    CssParser.parseFontFaces(
                                                        cssContent = styleElement.data(),
                                                        cssPath = chapterToRender.absPath,
                                                        constraints = Constraints(maxWidth = 1, maxHeight = 1),
                                                        isDarkTheme = false,
                                                        adaptThemeColors = false
                                                    )
                                                }
                                            buildEpubFontFaceCss(fontFaces, epubBook.extractionBasePath)
                                        }
                                        fun isCurrentRenderedChapter(): Boolean =
                                            targetChapterIndex == currentChapterIndex

                                        val chapterKeyForWebView =
                                            remember(
                                                chapterToRender.htmlFilePath,
                                                epubBook.extractionBasePath,
                                                bookReplacementSignature
                                            ) {
                                                "${epubBook.extractionBasePath}/${chapterToRender.htmlFilePath}?bookReplacements=${bookReplacementSignature.hashCode()}"
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

                                                    val js = "javascript:window.CURRENT_SEARCH_QUERY = '${escapedQuery}'; window.highlightAllOccurrences('${escapedQuery}'); window.scrollToChunkOccurrence($targetChunk, $relativeIdx);"
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

                                        val currentChapterTocFragments = remember(epubBook.tableOfContents, targetChapterIndex) {
                                            val chapterPath = chapters.getOrNull(targetChapterIndex)?.absPath
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
                                            initialImageSource = imageToLoad?.sourcePath,
                                            initialImageOriginalSource = imageToLoad?.originalSource,
                                            initialImageOrdinal = imageToLoad?.ordinalInChapter ?: 0,
                                            userHighlights = userHighlights.filter { it.chapterIndex == targetChapterIndex },
                                            activeHighlightPalette = currentHighlightPalette,
                                            onUpdatePalette = onUpdateHighlightPalette,
                                            onHighlightCreated = { cfi, text, colorId, style ->
                                                Timber.d("Vertical Mode (Source): Creating Highlight. CFI: $cfi")
                                                Timber.d("Vertical Mode (Source): Text Snippet: '${text.take(50)}...'")
                                                val (color, colorArgb) = highlightColorFromToken(colorId)

                                                val finalCfi = processAndAddHighlight(
                                                    newCfi = cfi,
                                                    newText = text,
                                                    newColor = color,
                                                    chapterIndex = currentChapterIndex,
                                                    currentList = userHighlights,
                                                    newColorArgb = colorArgb,
                                                    newStyle = style
                                                )

                                                if (navigation.pendingNoteForNewHighlight) {
                                                    navigation.pendingNoteForNewHighlight = false
                                                    navigation.highlightToNoteCfi = finalCfi
                                                }
                                            },
                                            onNoteRequested = { cfi ->
                                                if (cfi != null) {
                                                    navigation.highlightToNoteCfi = cfi
                                                } else {
                                                    navigation.pendingNoteForNewHighlight = true
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
                                                if (!isCurrentRenderedChapter()) {
                                                    Timber.tag(TAG_VERTICAL_JITTER).d(
                                                        "ignored stale initiallyScrolled rendered=$targetChapterIndex current=$currentChapterIndex chapter='${chapterToRender.title}'"
                                                    )
                                                } else {
                                                    val wasCfiScroll = cfiToLoad != null
                                                    val wasImageScroll = imageToLoad != null
                                                    Timber.tag("NavDiag").d("onChapterInitiallyScrolled for chapter $targetChapterIndex. Was CFI scroll: $wasCfiScroll, Was image scroll: $wasImageScroll")
                                                    logTtsChapterDiag("Chapter initially scrolled. targetChapter=$targetChapterIndex wasCfiScroll=$wasCfiScroll wasImageScroll=$wasImageScroll")
                                                    initialScrollTargetForChapter = null
                                                    cfiToLoad = null
                                                    fragmentToLoad = null
                                                    imageToLoad = null
                                                    Timber.d("Initial scroll consumed for chapter $targetChapterIndex. Was CFI scroll: $wasCfiScroll, Was image scroll: $wasImageScroll")
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
                                                }
                                            },
                                            onTap = {
                                                if (isAutoScrollModeActive) {
                                                    isAutoScrollPlaying = !isAutoScrollPlaying
                                                    Timber.d("Auto-scroll toggled via tap: $isAutoScrollPlaying")
                                                }

                                                if (!(isMusicianMode && isAutoScrollModeActive) && System.currentTimeMillis() - lastHighlightClickTime > 500) {
                                                    focusManager.clearFocus()
                                                    if (prefs.volumeScrollEnabled && !searchState.isSearchActive) {
                                                        containerFocusRequester.requestFocus()
                                                    }

                                                    if (System.currentTimeMillis() - lastScrollHideTime < 400) {
                                                        Timber.d("Ignoring tap toggle because bars were just hidden by scroll (sloppy tap).")
                                                    } else {
                                                        if (showBars || navigation.showFormatAdjustmentBars) {
                                                            showBars = false
                                                            navigation.showFormatAdjustmentBars = false
                                                            Timber.d("Chapter tapped, hiding all bars.")
                                                        } else {
                                                            showBars = true
                                                            Timber.d("Chapter tapped, showing main bars.")
                                                        }
                                                    }
                                                }
                                            },
                                            onPotentialScroll = {
                                                if (showBars || navigation.showFormatAdjustmentBars) {
                                                    showBars = false
                                                    navigation.showFormatAdjustmentBars = false
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
                                                if (prefs.pullToTurnEnabled) {
                                                    if (targetChapterIndex > 0) {
                                                        pullToPrevProgress = dragAmount / dragThresholdPx
                                                    }
                                                } else {
                                                    if (targetChapterIndex > 0 && dragAmount > 20f && !navigation.isSeamlessTransitioning) {
                                                        navigation.isSeamlessTransitioning = true
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
                                                            navigation.isSeamlessTransitioning = false
                                                        }
                                                    }
                                                }
                                            },
                                            onOverScrollBottom = { dragAmount ->
                                                if (prefs.pullToTurnEnabled) {
                                                    if (targetChapterIndex < chapters.size - 1) {
                                                        pullToNextProgress = dragAmount / dragThresholdPx
                                                    }
                                                } else {
                                                    if (targetChapterIndex < chapters.size - 1 && dragAmount > 20f && !navigation.isSeamlessTransitioning) {
                                                        navigation.isSeamlessTransitioning = true
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
                                                            navigation.isSeamlessTransitioning = false
                                                        }
                                                    }
                                                }
                                            },
                                            onReleaseOverScrollTop = {
                                                if (prefs.pullToTurnEnabled && targetChapterIndex > 0 && pullToPrevProgress >= 1.0f) {
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
                                                if (prefs.pullToTurnEnabled && targetChapterIndex < chapters.size - 1 && pullToNextProgress >= 1.0f) {
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
                                                if (!isCurrentRenderedChapter()) {
                                                    Timber.tag(TAG_VERTICAL_JITTER).d(
                                                        "ignored stale scrollState rendered=$targetChapterIndex current=$currentChapterIndex y=$scrollY height=$scrollHeight chapter='${chapterToRender.title}'"
                                                    )
                                                } else {
                                                    currentScrollYPosition = scrollY
                                                    currentScrollHeightValue = scrollHeight
                                                    currentClientHeightValue = clientHeight

                                                    if (navigation.activeFragmentId != fragId) {
                                                        Timber.tag("FRAG_NAV_DEBUG").d("State updated to: $fragId")
                                                        navigation.activeFragmentId = fragId
                                                    }

                                                    if (prefs.volumeScrollEnabled && !searchState.isSearchActive) {
                                                        navigation.volumeScrollFocusDebounceJob?.cancel()
                                                        navigation.volumeScrollFocusDebounceJob = scope.launch {
                                                            delay(300L)
                                                            if (isActive) {
                                                                containerFocusRequester.requestFocus()
                                                                Timber.d("Refocusing container after scroll to re-enable volume keys.")
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            currentFontSize = format.currentFontSizeEm,
                                            currentLineHeight = format.currentLineHeight,
                                            currentParagraphGap = format.currentParagraphGap,
                                            currentImageSize = format.currentImageSize,
                                            currentHorizontalMargin = format.currentHorizontalMargin,
                                            currentVerticalMargin = format.currentVerticalMargin,
                                            currentFontFamily = format.currentFontFamily,
                                            currentFontWeight = format.currentFontWeight,
                                            currentLetterSpacing = format.currentLetterSpacing,
                                            hideImages = prefs.hideImages,
                                            customFontPath = format.currentCustomFontPath,
                                            epubFontFaceCss = listOf(epubFontFaceCss, chapterFontFaceCss)
                                                .filter { it.isNotBlank() }
                                                .joinToString(separator = " "),
                                            currentTextAlign = format.currentTextAlign,
                                            activeTextureId = activeTextureId,
                                            activeTextureAlpha = activeTextureAlpha,
                                            onHighlightClicked = {
                                                lastHighlightClickTime = System.currentTimeMillis()
                                                showBars = false
                                                navigation.showFormatAdjustmentBars = false
                                                Timber.d("Highlight clicked - Forcing bars hidden")
                                            },
                                            onInternalLinkClick = { url ->
                                                scope.launch {
                                                    val basePath = "file://${epubBook.extractionBasePath}/"
                                                    val rawRelativeUrl = url.removePrefix(basePath)
                                                    val relativeUrl = if (rawRelativeUrl != url) {
                                                        rawRelativeUrl
                                                    } else {
                                                        val decodedUrl = try {
                                                            java.net.URLDecoder.decode(url, "UTF-8")
                                                        } catch (_: Exception) {
                                                            url
                                                        }
                                                        decodedUrl.removePrefix(basePath)
                                                    }
                                                    val pathPart = relativeUrl.substringBefore('#')
                                                    val fragmentPart = relativeUrl.substringAfter('#', "").takeIf { it.isNotEmpty() }

                                                    val decodedPath = try { java.net.URLDecoder.decode(pathPart, "UTF-8") } catch(e: Exception) { pathPart }
                                                    val renderedChapter = chapters.getOrNull(targetChapterIndex)
                                                    val renderedChapterDirectory = renderedChapter
                                                        ?.htmlFilePath
                                                        ?.substringBeforeLast('/', "")
                                                        .orEmpty()
                                                        .trim('/')
                                                    val decodedPathDirectory = decodedPath.trim('/')
                                                    val resolvedTargetChapterIndex = when {
                                                        pathPart.isBlank() -> targetChapterIndex
                                                        decodedPath.isBlank() -> targetChapterIndex
                                                        renderedChapterDirectory.isNotBlank() && decodedPathDirectory == renderedChapterDirectory -> targetChapterIndex
                                                        else -> chapters.indexOfFirst {
                                                            it.absPath == decodedPath ||
                                                                it.htmlFilePath == decodedPath ||
                                                                it.absPath.trim('/') == decodedPathDirectory ||
                                                                it.htmlFilePath.trim('/') == decodedPathDirectory
                                                        }
                                                    }

                                                    Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> url: $url")
                                                    Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> basePath: $basePath")
                                                    Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> relativeUrl: $relativeUrl")
                                                    Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> pathPart: $pathPart")
                                                    Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> decodedPath: $decodedPath")
                                                    Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> fragmentPart: $fragmentPart")
                                                    Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> targetChapterIndex: $resolvedTargetChapterIndex (current is $currentChapterIndex)")

                                                    if (resolvedTargetChapterIndex != -1) {
                                                        recordEpubJump(fragmentJumpLocator(resolvedTargetChapterIndex, fragmentPart, chapters.getOrNull(resolvedTargetChapterIndex)?.absPath ?: decodedPath))
                                                        if (resolvedTargetChapterIndex != currentChapterIndex) {
                                                            Timber.tag(TAG_LINK_NAV).d("[CHAPTER-NAV] source=INTERNAL_LINK, from=$currentChapterIndex, to=$resolvedTargetChapterIndex, fragment='$fragmentPart'")
                                                            initialScrollTargetForChapter = null
                                                            fragmentToLoad = fragmentPart
                                                            currentScrollYPosition = 0
                                                            currentScrollHeightValue = 0
                                                            currentChapterIndex = resolvedTargetChapterIndex
                                                        } else {
                                                            Timber.tag(TAG_LINK_NAV).d("InternalLinkClick -> Target is current chapter. Evaluating JS for fragment.")
                                                            if (fragmentPart != null) {
                                                                val escapedFragment = escapeJsString(fragmentPart)
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
                                                if (isCurrentRenderedChapter()) {
                                                    webViewRefForTts = webView
                                                } else {
                                                    Timber.tag(TAG_VERTICAL_JITTER).d(
                                                        "ignored stale webViewRef rendered=$targetChapterIndex current=$currentChapterIndex chapter='${chapterToRender.title}'"
                                                    )
                                                }
                                                webView.evaluateJavascript(
                                                    "javascript:window.setViewportPadding(${topPaddingPx}, 0);",
                                                    null
                                                )
                                            },
                                            onWebViewDisposed = { webView ->
                                                if (webViewRefForTts === webView) {
                                                    webViewRefForTts = null
                                                }
                                            },
                                            onScrollFinished = { success ->
                                                Timber.tag("BookmarkDiagnosis").d("Scroll finished callback. Success: $success")
                                                navigation.isNavigatingToPosition = false
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
                                                        if (BuildConfig.FLAVOR != "oss" && prefs.currentTtsMode == TtsPlaybackManager.TtsMode.CLOUD && credits <= 0) {
                                                            navigation.showInsufficientCreditsDialog = true
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
                                                        val nativeChapterChunks = locatorConverter
                                                            .getTtsChunksForChapter(epubBook, targetChapterIndex, bookId)
                                                            .orEmpty()
                                                        val extractedStartChunk = ttsChunks.firstOrNull()
                                                        val nativeStartChunkIndex = findTtsChunkStartIndex(nativeChapterChunks, extractedStartChunk)
                                                        val sessionChunks = if (nativeChapterChunks.isNotEmpty() && nativeStartChunkIndex != null) {
                                                            nativeChapterChunks.withInitialChunkOverride(nativeStartChunkIndex, extractedStartChunk)
                                                        } else {
                                                            ttsChunks
                                                        }
                                                        val startChunkIndex = nativeStartChunkIndex ?: 0

                                                        ttsController.start(
                                                            chunks = sessionChunks.withTtsReplacements(ttsReplacementPreferences, bookId),
                                                            bookTitle = epubBook.title,
                                                            chapterTitle = chapterTitle,
                                                            coverImageUri = coverUriString,
                                                            bookId = bookId,
                                                            chapterIndex = targetChapterIndex,
                                                            totalChapters = chapters.size,
                                                            startChunkIndex = startChunkIndex,
                                                            ttsMode = prefs.currentTtsMode,
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
                                                                navigation.showInsufficientCreditsDialog = true
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
                                                    val chapterTitle = chapters.getOrNull(chapterIndexToSave)?.title ?: context.getString(R.string.chapter_number_format, chapterIndexToSave + 1)
                                                                summaryCacheManager.saveSummary(bookTitleToSave, chapterIndexToSave, chapterTitle, fullSummary)
                                                            }
                                                        }
                                                    )
                                                }
                                            },
                                            onFootnoteRequested = { html ->
                                                navigation.activeFootnoteHtml = html
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

                                                        val progressWithinChapter = mobileEpubChapterScrollFraction(
                                                            scrollY = currentScrollYPosition,
                                                            scrollHeight = currentScrollHeightValue,
                                                            clientHeight = currentClientHeightValue
                                                        )

                                                        val currentChapterLengthChars =
                                                            chapters.getOrNull(
                                                                latestChapterIndex
                                                            )?.plainTextCharacterCount()?.toLong()
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
                                                                    .sumOf { it.plainTextCharacterCount().toLong() }

                                                            val charsScrolledInCurrentChapter =
                                                                (progressWithinChapter * currentChapterLengthChars).toLong()
                                                            val isLastChapter =
                                                                latestChapterIndex == chapters.size - 1
                                                            val isAtEndOfBook =
                                                                isLastChapter && (currentScrollYPosition + currentClientHeightValue) >= (currentScrollHeightValue - 2)
                                                            mobileEpubCharacterProgress(
                                                                totalBookCharacters = totalBookLengthChars,
                                                                completedChapterCharacters = completedCharsInPreviousChapters,
                                                                currentChapterOffset = charsScrolledInCurrentChapter,
                                                                isAtEndOfBook = isAtEndOfBook
                                                            )

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
                                                        navigation.chapterToLoadOnSwitch = latestChapterIndex
                                                        isPagerInitialized = false
                                                        Timber.d("V->P: Locator generated (success=${locator != null}). Switching to paginated mode for chapter $navigation.chapterToLoadOnSwitch."
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
                                                        ?: context.getString(R.string.unknown_chapter)
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
                                                if (isCurrentRenderedChapter()) {
                                                    topVisibleChunkIndex = chunkIndex
                                                } else {
                                                    Timber.tag(TAG_VERTICAL_JITTER).d(
                                                        "ignored stale topChunk rendered=$targetChapterIndex current=$currentChapterIndex chunk=$chunkIndex chapter='${chapterToRender.title}'"
                                                    )
                                                }
                                            },
                                            initialHtmlContent = initialHtml,
                                            baseUrl = baseUrl,
                                            totalChunks = chapterChunks.size,
                                            initialChunkIndex = loadUpToChunkIndex,
                                            onChunkRequested = { index ->
                                                if (!isCurrentRenderedChapter()) {
                                                    Timber.tag(TAG_VERTICAL_JITTER).d(
                                                        "ignored stale chunkRequest rendered=$targetChapterIndex current=$currentChapterIndex chunk=$index chapter='${chapterToRender.title}'"
                                                    )
                                                } else {
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
                                                }
                                            },
                                        )
                                    }
                                }

                                if (prefs.pullToTurnEnabled && currentChapterIndex > 0) {
                                    ChapterChangeIndicator(
                                        text = stringResource(R.string.release_for_previous_chapter),
                                        progress = pullToPrevProgress,
                                        isPullingDown = true,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 8.dp)
                                    )
                                }

                                if (prefs.pullToTurnEnabled && currentChapterIndex < chapters.size - 1) {
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
                        val pageInfoReserve = if (
                            shouldReserveEpubPageInfoBarSpace(prefs.pageInfoMode, showBars, isNativeVerticalMode)
                        ) pageInfoBarHeight else 0.dp
                        val contentTopPadding = if (prefs.pageInfoPosition == PageInfoPosition.TOP) pageInfoReserve else 0.dp
                        val contentBottomPadding = if (prefs.pageInfoPosition == PageInfoPosition.BOTTOM) pageInfoReserve else 0.dp

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = contentTopPadding)
                                .padding(bottom = contentBottomPadding)
                                .testTag("ReaderContainer")
                        ) {
                            PaginatedReaderScreen(
                                book = epubBook,
                                bookId = readerCacheBookId,
                                isDarkTheme = isDarkTheme,
                                effectiveBg = effectiveBg,
                                effectiveText = effectiveText,
                                pagerState = paginatedPagerState,
                                isRightToLeftPagination = prefs.rightToLeftPagination,
                                searchQuery = searchState.searchQuery,
                                fontSizeMultiplier = format.currentFontSizeEm,
                                lineHeightMultiplier = format.currentLineHeight,
                                paragraphGapMultiplier = format.currentParagraphGap,
                                imageSizeMultiplier = format.currentImageSize,
                                hideImages = prefs.hideImages,
                                horizontalMarginMultiplier = format.currentHorizontalMargin,
                                verticalMarginMultiplier = format.currentVerticalMargin,
                                fontFamily = format.activeFontFamily,
                                fontWeight = format.currentFontWeight,
                                letterSpacing = format.currentLetterSpacing,
                                textAlign = format.currentTextAlign,
                                bookReplacementPreferences = bookReplacementPreferences,
                                bookReplacementFileId = bookId,
                                activeHighlightPalette = currentHighlightPalette,
                                onUpdatePalette = onUpdateHighlightPalette,
                                isPageTurnAnimationEnabled = prefs.isPageTurnAnimationEnabled,
                                ttsHighlightInfo = TtsHighlightInfo(
                                    text = ttsState.currentText ?: "",
                                    cfi = ttsState.sourceCfi ?: "",
                                    offset = ttsState.startOffsetInSource
                                ).takeIf { ttsState.currentText != null && ttsState.sourceCfi != null && ttsState.startOffsetInSource != -1 },
                                activeTextureId = activeTextureId,
                                activeTextureAlpha = activeTextureAlpha,
                                initialChapterIndexInBook = lastKnownLocator?.chapterIndex,
                                fallbackLocatorForReconfiguration = navigation.paginatedReconfigurationAnchor ?: lastKnownLocator,
                                explicitNavigationAnchor = paginatedExplicitNavigationAnchor,
                                explicitNavigationEpoch = paginatedExplicitNavigationEpoch,
                                isExternalNavigationInProgress = navigation.isNavigatingToPosition || navigation.isNavigatingByToc,
                                onReconfigurationAnchorCaptured = { locator ->
                                    navigation.paginatedReconfigurationAnchor = locator
                                    lastKnownLocator = locator
                                },
                                onReconfigurationRestoreActiveChanged = { isActive ->
                                    navigation.isPaginatedReconfigurationRestoring = isActive
                                    if (!isActive) {
                                        navigation.paginatedReconfigurationAnchor = null
                                    }
                                },
                                modifier = Modifier.alpha(if (isPagerInitialized && !navigation.isPaginatedReconfigurationRestoring) 1f else 0f),
                                onPaginatorReady = { newPaginator ->
                                    paginator = newPaginator
                                },
                                onTap = { tapOffset ->
                                    Timber.d("PaginatedReaderScreen onTap called with offset: $tapOffset")

                                    if (prefs.volumeScrollEnabled) {
                                        containerFocusRequester.requestFocus()
                                    }

                                    if (tapOffset == null || !prefs.tapToNavigateEnabled) {
                                        focusManager.clearFocus()
                                        if (prefs.volumeScrollEnabled) containerFocusRequester.requestFocus()

                                        if (showBars || navigation.showFormatAdjustmentBars) {
                                            showBars = false
                                            navigation.showFormatAdjustmentBars = false
                                        } else {
                                            showBars = true
                                        }
                                    } else {
                                        val oneQuarterWidthPx = constraints.maxWidth / 4f
                                        when {
                                            tapOffset.x < oneQuarterWidthPx -> {
                                                scope.launch {
                                                    val targetPage = if (prefs.rightToLeftPagination) {
                                                        (paginatedPagerState.currentPage + 1).coerceAtMost(paginatedPagerState.pageCount - 1)
                                                    } else {
                                                        (paginatedPagerState.currentPage - 1).coerceAtLeast(0)
                                                    }
                                                    if (targetPage != paginatedPagerState.currentPage) {
                                                        if (motionPolicy.shouldAnimate(prefs.isPageTurnAnimationEnabled)) {
                                                            paginatedPagerState.animateScrollToPage(targetPage, animationSpec = tween(700))
                                                        } else paginatedPagerState.scrollToPage(targetPage)
                                                    }
                                                }
                                            }
                                            tapOffset.x > (constraints.maxWidth - oneQuarterWidthPx) -> {
                                                scope.launch {
                                                    val pageCount = paginatedPagerState.pageCount
                                                    if (pageCount > 0) {
                                                        val targetPage = if (prefs.rightToLeftPagination) {
                                                            (paginatedPagerState.currentPage - 1).coerceAtLeast(0)
                                                        } else {
                                                            (paginatedPagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                                                        }
                                                        if (targetPage != paginatedPagerState.currentPage) {
                                                            if (motionPolicy.shouldAnimate(prefs.isPageTurnAnimationEnabled)) {
                                                                paginatedPagerState.animateScrollToPage(targetPage, animationSpec = tween(700))
                                                            } else paginatedPagerState.scrollToPage(targetPage)
                                                        }
                                                    }
                                                }
                                            }
                                            else -> {
                                                focusManager.clearFocus()
                                                if (prefs.volumeScrollEnabled) containerFocusRequester.requestFocus()
                                                if (showBars || navigation.showFormatAdjustmentBars) {
                                                    showBars = false
                                                    navigation.showFormatAdjustmentBars = false
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
                                onHighlightCreated = { cfi, text, colorId, locator, style ->
                                    val chapterIndex = locator.chapterIndex ?: currentChapterInPaginatedMode ?: 0
                                    Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                        "persist_request cfi=$cfi colorId=$colorId chapter=$chapterIndex " +
                                            "existingCount=${userHighlights.size} textLen=${text.length} " +
                                            "text='${epubHighlightDiagSnippet(text)}'"
                                    )
                                    Timber.d("EpubReaderScreen: onHighlightCreated. CFI: $cfi")
                                    val (color, colorArgb) = highlightColorFromToken(colorId)
                                    val finalCfi = processAndAddHighlight(
                                        newCfi = cfi,
                                        newText = text,
                                        newColor = color,
                                        chapterIndex = chapterIndex,
                                        currentList = userHighlights,
                                        locator = locator.withFallbacks(
                                            chapterIndex = chapterIndex,
                                            cfi = cfi,
                                            textQuote = text
                                        ),
                                        newColorArgb = colorArgb,
                                        newStyle = style
                                    )
                                    val savedHighlight = userHighlights.find {
                                        it.chapterIndex == chapterIndex && it.cfi == finalCfi
                                    }
                                    Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                        "persist_result finalCfi=$finalCfi chapter=$chapterIndex " +
                                            "savedId=${savedHighlight?.id} totalCount=${userHighlights.size} " +
                                            "matchingCfiCount=${userHighlights.count { it.chapterIndex == chapterIndex && it.cfi == finalCfi }} " +
                                            "locatorStart=${savedHighlight?.locator?.startOffset} " +
                                            "locatorEnd=${savedHighlight?.locator?.endOffset} " +
                                            "locatorPage=${savedHighlight?.locator?.pageIndex} " +
                                            "locatorCfi=${savedHighlight?.locator?.cfi}"
                                    )
                                    if (navigation.pendingNoteForNewHighlight) {
                                        navigation.pendingNoteForNewHighlight = false
                                        navigation.highlightToNoteCfi = finalCfi
                                    }
                                },
                                onNoteRequested = { cfi ->
                                    if (cfi != null) {
                                        navigation.highlightToNoteCfi = cfi
                                    } else {
                                        navigation.pendingNoteForNewHighlight = true
                                    }
                                },
                                onFootnoteRequested = { html ->
                                    navigation.activeFootnoteHtml = html
                                },
                                onInternalLinkNavigated = { targetPageIndex, targetLocatorFromLink ->
                                    val currentLocator = currentEpubJumpLocator()
                                    val bookPaginator = paginator as? BookPaginator
                                    val targetChapter = targetLocatorFromLink?.chapterIndex
                                        ?: bookPaginator?.findChapterIndexForPage(targetPageIndex)
                                    val targetLocator = targetLocatorFromLink ?: bookPaginator?.getLocatorForPage(targetPageIndex)
                                    val navigationEpoch = System.currentTimeMillis()
                                    paginatedExplicitNavigationEpoch = navigationEpoch
                                    paginatedExplicitNavigationAnchor = targetLocator
                                    Timber.tag(TAG_STABLE_PAGE_NAV).d(
                                        "internal_link_target targetPage=$targetPageIndex targetChapter=$targetChapter anchor=$targetLocator epoch=$navigationEpoch"
                                    )
                                    if (targetLocator != null) {
                                        lastKnownLocator = targetLocator
                                    }
                                    bookPaginator?.onUserScrolledTo(targetPageIndex)
                                    paginatedJumpLocatorForPage(
                                        pageIndex = targetPageIndex,
                                        targetLocator = targetLocator,
                                        fallbackChapterIndex = targetChapter
                                    )?.let { recordEpubJump(it, currentLocator) }
                                },
                                onHighlightDeleted = { cfi ->
                                    val beforeCount = userHighlights.size
                                    val toRemove = userHighlights.find { it.cfi == cfi }
                                    if (toRemove != null) {
                                        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                            "delete_request cfi=$cfi matchedId=${toRemove.id} " +
                                                "matchedChapter=${toRemove.chapterIndex} beforeCount=$beforeCount " +
                                                "locatorStart=${toRemove.locator.startOffset} " +
                                                "locatorEnd=${toRemove.locator.endOffset}"
                                        )
                                        userHighlights.remove(toRemove)
                                        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                            "delete_result cfi=$cfi removedId=${toRemove.id} " +
                                                "afterCount=${userHighlights.size}"
                                        )
                                    } else {
                                        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).w(
                                            "delete_request cfi=$cfi matchedId=null beforeCount=$beforeCount"
                                        )
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
}
