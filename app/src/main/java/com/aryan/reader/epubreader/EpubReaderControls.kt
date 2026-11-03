package com.aryan.reader.epubreader

import com.aryan.reader.shared.readerTtsChunkLabel
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.BuildConfig
import com.aryan.reader.R
import com.aryan.reader.RenderMode
import com.aryan.reader.shared.ReaderMotionPolicy
import com.aryan.reader.shared.formatMicrosUsd
import com.aryan.reader.shared.formatSpendGuardCountdown
import com.aryan.reader.shared.parseSpendGuardSentinel
import com.aryan.reader.shared.spendableDisplayText
import com.aryan.reader.shared.ReaderSearchState as SearchState
import com.aryan.reader.SearchTopBar
import com.aryan.reader.TooltipIconButton
import com.aryan.reader.areReaderAiFeaturesEnabled
import com.aryan.reader.androidSharedStringResolver
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.aryan.reader.shared.ReaderHiddenToolsDefaultsVersion
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ui.LocalSharedStringResolver
import com.aryan.reader.shared.ui.SharedMobileEpubBottomBar
import com.aryan.reader.shared.ui.SharedMobileEpubLocalTtsState
import com.aryan.reader.shared.ui.SharedMobileEpubToolbarTools
import com.aryan.reader.shared.ui.SharedMobileEpubTopBar
import com.aryan.reader.shared.ui.SharedReaderBarEdge
import com.aryan.reader.shared.ui.SharedReaderBarVisibility
import com.aryan.reader.shared.ui.SharedReaderToolbarPlacement
import com.aryan.reader.shared.ui.sharedEpubToolbarTools
import com.aryan.reader.loadNativeVoice
import com.aryan.reader.paginatedreader.EpubPageSpread
import com.aryan.reader.shared.ui.SharedMobileAutoScrollControls
import com.aryan.reader.shared.ui.SharedMobileAutoScrollLabels
import com.aryan.reader.shared.ui.SharedMobileEpubJumpHistoryBar
import com.aryan.reader.shared.ui.SharedMobileEpubScrubBubble
import com.aryan.reader.shared.ui.SharedMobileEpubSlider
import com.aryan.reader.tts.GEMINI_TTS_SPEAKERS
import com.aryan.reader.tts.isFishReferenceId
import com.aryan.reader.tts.loadTtsSpeaker
import com.aryan.reader.tts.loadTtsSpeakerName
import com.aryan.reader.tts.normalizeTtsSpeakerId
import com.aryan.reader.tts.ReaderTtsOverlaySize
import com.aryan.reader.tts.TtsPlaybackManager.TtsState
import kotlin.math.roundToInt

typealias ReaderTool = com.aryan.reader.shared.ReaderTool

val ReaderTool.titleRes: Int
    @StringRes get() = when (this) {
        ReaderTool.DICTIONARY -> R.string.tool_external_apps
        ReaderTool.THEME -> R.string.tooltip_theme_desc
        ReaderTool.BRIGHTNESS -> R.string.tool_brightness
        ReaderTool.SLIDER -> R.string.tool_navigation_slider
        ReaderTool.TOC -> R.string.tool_sidebar
        ReaderTool.FORMAT -> R.string.content_desc_text_formatting
        ReaderTool.SEARCH -> R.string.action_search
        ReaderTool.AI_FEATURES -> R.string.ai_features_title
        ReaderTool.TTS_CONTROLS -> R.string.tool_tts_controls
        ReaderTool.FILE_INFO -> R.string.file_information
        ReaderTool.READING_MODE -> R.string.tool_reading_mode
        ReaderTool.BOOKMARK -> R.string.content_desc_bookmark
        ReaderTool.TAP_TO_TURN -> R.string.menu_tap_to_turn_pages
        ReaderTool.VOLUME_SCROLL -> R.string.menu_volume_button_scrolling
        ReaderTool.PAGE_TURN_ANIM -> R.string.menu_realistic_page_turns
        ReaderTool.KEEP_SCREEN_ON -> R.string.menu_keep_screen_on
        ReaderTool.VISUAL_OPTIONS -> R.string.menu_visual_options
        ReaderTool.SCREEN_ORIENTATION -> R.string.menu_screen_orientation
        ReaderTool.AUTO_SCROLL -> R.string.menu_auto_scroll
        ReaderTool.TTS_SETTINGS -> R.string.menu_tts_settings
        ReaderTool.TTS_REPLACEMENTS -> R.string.menu_tts_word_replacements
        ReaderTool.BOOK_REPLACEMENTS -> R.string.menu_book_word_replacements
    }

/**
 * The tools a bar can host - Android benchmark `epubToolbarTools`, now the shared module's own set.
 *
 * This used to be a second copy of the same ten tools; keeping an alias means every Android call
 * site (the customize sheet, the tests, the bar adapters below) keeps compiling while a tool can
 * only ever be listed once.
 */
internal val epubToolbarTools: Set<ReaderTool> = SharedMobileEpubToolbarTools

internal typealias EpubOverflowMenuSection = com.aryan.reader.shared.EpubOverflowMenuSection

internal fun epubOverflowMenuSections(
    hiddenTools: Set<String>,
    hasHiddenToolbarTools: Boolean,
    hasToggleReflow: Boolean,
    hasDeleteReflow: Boolean,
    hasFileInfo: Boolean = true,
    hasExportAnnotations: Boolean = false,
): List<EpubOverflowMenuSection> = com.aryan.reader.shared.epubOverflowMenuSections(
    hiddenTools = hiddenTools,
    hasHiddenToolbarTools = hasHiddenToolbarTools,
    hasToggleReflow = hasToggleReflow,
    hasDeleteReflow = hasDeleteReflow,
    hasFileInfo = hasFileInfo,
    hasExportAnnotations = hasExportAnnotations,
)

internal fun defaultReaderHiddenTools(): Set<String> = setOf(
    ReaderTool.SCREEN_ORIENTATION.name,
    ReaderTool.BRIGHTNESS.name
)

internal fun defaultReaderToolOrder(): List<ReaderTool> = ReaderTool.entries.toList()

internal fun defaultReaderBottomTools(): Set<String> {
    return ReaderTool.entries.filter { it.category == "Bottom Bar" }.map { it.name }.toSet()
}

/**
 * Android's reader preferences are flat `SharedPreferences` keyed by tool *name*
 * (`ReaderTool.name`), while [ReaderToolbarPreferences] - the model the shared bars render from - is
 * keyed by id. This is the only conversion between the two spellings, so a name/id mismatch cannot
 * hide inside a composable.
 *
 * `hiddenToolsDefaultsVersion` is pinned to the current version on purpose: Android's own preference
 * layer has already applied its migrations by the time the reader composes, and re-running shared's
 * upgrade would re-hide tools the reader deliberately restored.
 */
internal fun epubReaderToolbarPreferences(
    hiddenTools: Set<String>,
    toolOrder: List<ReaderTool>,
    bottomTools: Set<String>,
): ReaderToolbarPreferences = ReaderToolbarPreferences(
    hiddenToolIds = hiddenTools.mapNotNullTo(mutableSetOf()) { ReaderTool.fromId(it)?.id },
    toolOrder = toolOrder,
    bottomToolIds = bottomTools.mapNotNullTo(mutableSetOf()) { ReaderTool.fromId(it)?.id },
    hiddenToolsDefaultsVersion = ReaderHiddenToolsDefaultsVersion,
).sanitized()

/**
 * Android's chrome read-aloud entry is a boolean session flag plus one toggle callback; the shared
 * bar renders a three-state engine. Android has no *paused* chrome state (pause/resume lives in the
 * TTS overlay), so the projection is IDLE/SPEAKING only - which is exactly what makes the shared
 * button draw Android's stop-X and primary tint for the same reason Android's own bar did.
 */
internal fun epubChromeReadAloudState(isTtsActive: Boolean): SharedMobileEpubLocalTtsState =
    if (isTtsActive) SharedMobileEpubLocalTtsState.SPEAKING else SharedMobileEpubLocalTtsState.IDLE

/**
 * Android adapter over the shared EPUB top bar.
 *
 * The bar itself - the back/title row, the tool buttons with their tooltips and accessibility
 * labels, the narration entry, the overflow menu and its sections - lives once in
 * [SharedMobileEpubTopBar]. This keeps Android's public parameter list (the reader already owns all
 * of this state) and does the three things only Android can: convert the reader's flat, name-keyed
 * preferences into [ReaderToolbarPreferences], hand over its own [SearchTopBar] through the bar's
 * search slot, and install [androidSharedStringResolver] so every shared label renders the app's
 * translation instead of the English fallback.
 */
@Composable
fun EpubReaderTopBar(
    isVisible: Boolean,
    searchState: SearchState,
    bookTitle: String,
    currentRenderMode: RenderMode,
    isBookmarked: Boolean,
    isTtsActive: Boolean,
    /**
     * Whether this book narrates itself, i.e. its OPF declares media overlays.
     *
     * The button is hidden rather than disabled for a book without overlays: narration is a
     * property of the book, and a permanently dead control on every book in the library is a
     * worse answer than no control.
     */
    hasMediaOverlayNarration: Boolean = false,
    isMediaOverlayActive: Boolean = false,
    isSliderActive: Boolean,
    tapToNavigateEnabled: Boolean,
    volumeScrollEnabled: Boolean,
    isPageTurnAnimationEnabled: Boolean,
    isRightToLeftPagination: Boolean,
    useNativeVerticalRenderer: Boolean,
    onNavigateBack: () -> Unit,
    isKeepScreenOn: Boolean,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onCloseSearch: () -> Unit,
    onChangeRenderMode: (RenderMode) -> Unit,
    onUseNativeVerticalRendererChange: (Boolean) -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleTapToNavigate: (Boolean) -> Unit,
    onToggleVolumeScroll: (Boolean) -> Unit,
    onTogglePageTurnAnimation: (Boolean) -> Unit,
    onSetRightToLeftPagination: (Boolean) -> Unit,
    onStartAutoScroll: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onOpenTtsReplacements: () -> Unit,
    onOpenBookReplacements: () -> Unit,
    onOpenDictionarySettings: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenBrightness: () -> Unit,
    onOpenVisualOptions: () -> Unit,
    onOpenScreenOrientation: () -> Unit,
    onOpenSlider: () -> Unit,
    onOpenDrawer: () -> Unit,
    onToggleFormat: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenAiHub: () -> Unit,
    onToggleTts: () -> Unit,
    onToggleMediaOverlay: () -> Unit = {},
    onOpenFileInfo: () -> Unit,
    searchFocusRequester: androidx.compose.ui.focus.FocusRequester,
    hiddenTools: Set<String>,
    toolOrder: List<ReaderTool>,
    bottomTools: Set<String>,
    onCustomizeTools: () -> Unit,
    /**
     * Chrome-stable status-bar height the bar paints inside its own surface. The reader computes
     * it (and keeps it stable while transient bars show/hide); the shared bar must not apply it as
     * outer padding or the strip above the toolbar stays reader-coloured.
     */
    topInset: Dp = 0.dp,
    /**
     * Top inset of the layer hosting this bar. The reader insets its chrome layer by the status-bar
     * height so controls clear the system bar, which would leave the bar's surface starting below
     * the screen edge; the bar lifts itself by the same amount so the surface reaches the edge and
     * [topInset] paints the strip. Hosts that already place bars at the edge pass 0.dp.
     */
    hostTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    onToggleReflow: (() -> Unit)? = null,
    onDeleteReflow: (() -> Unit)? = null,
    onExportAnnotations: (() -> Unit)? = null,
    readerMotionPolicy: ReaderMotionPolicy = ReaderMotionPolicy(),
) {
    val context = LocalContext.current
    // The popup's own state: Android has always owned it inside the bar (there is no screen-level
    // menu flag to mirror), so the adapter keeps it local and hands the shared bar both halves.
    var showMore by remember { mutableStateOf(false) }
    val toolbarPreferences = remember(hiddenTools, toolOrder, bottomTools) {
        epubReaderToolbarPreferences(
            hiddenTools = hiddenTools,
            toolOrder = toolOrder,
            bottomTools = bottomTools,
        )
    }
    val topTools = remember(toolbarPreferences) {
        sharedEpubToolbarTools(
            toolOrder = toolbarPreferences.toolOrder,
            toolbarTools = epubToolbarTools,
            hiddenToolNamesOrIds = toolbarPreferences.hiddenToolIds,
            bottomToolNamesOrIds = toolbarPreferences.bottomToolIds,
            placement = SharedReaderToolbarPlacement.TOP,
        )
    }
    val aiAvailable = areReaderAiFeaturesEnabled(context)
    SharedReaderBarVisibility(
        visible = isVisible,
        edge = SharedReaderBarEdge.TOP,
        motionPolicy = readerMotionPolicy,
        // The host layer insets the chrome so its controls clear the status bar; undoing that
        // inset here lets the bar's own surface start at the screen edge (topInset then paints
        // the strip behind the status bar).
        modifier = modifier.offset(y = -hostTopInset)
    ) {
        CompositionLocalProvider(
            LocalSharedStringResolver provides androidSharedStringResolver(context)
        ) {
            SharedMobileEpubTopBar(
                title = bookTitle,
                isBookmarked = isBookmarked,
                topTools = topTools,
                topInset = topInset,
                showMore = showMore,
                onShowMoreChange = { showMore = it },
                onBack = onNavigateBack,
                onTheme = onOpenThemeSettings,
                onFormat = onToggleFormat,
                onSearch = onToggleSearch,
                onBookmark = onToggleBookmark,
                onVisualOptions = onOpenVisualOptions,
                onBrightness = onOpenBrightness,
                onOpenToc = onOpenDrawer,
                onOpenSlider = onOpenSlider,
                onFileInfo = onOpenFileInfo,
                onCustomizeTools = onCustomizeTools,
                onScreenOrientation = onOpenScreenOrientation,
                onTtsSettings = onOpenTtsSettings,
                onTtsReplacements = onOpenTtsReplacements,
                onBookReplacements = onOpenBookReplacements,
                onOpenDictionarySettings = onOpenDictionarySettings,
                onOpenAiHub = onOpenAiHub,
                aiAvailable = aiAvailable,
                onExportAnnotations = onExportAnnotations,
                onToggleReflow = onToggleReflow,
                onDeleteReflow = onDeleteReflow,
                onToggleVolumeScroll = onToggleVolumeScroll,
                volumeScrollEnabled = volumeScrollEnabled,
                // Android's hidden-tools rows keep their own localized titles and sheet routing.
                hiddenToolItem = { tool, dismiss ->
                    HiddenEpubToolMenuItem(
                        tool = tool,
                        isSliderActive = isSliderActive,
                        showMoreMenu = dismiss,
                        onOpenDictionarySettings = onOpenDictionarySettings,
                        onOpenThemeSettings = onOpenThemeSettings,
                        onOpenBrightness = onOpenBrightness,
                        onOpenSlider = onOpenSlider,
                        onOpenDrawer = onOpenDrawer,
                        onToggleFormat = onToggleFormat,
                        onToggleSearch = onToggleSearch,
                        onOpenAiHub = onOpenAiHub,
                        onToggleTts = onToggleTts,
                        onOpenScreenOrientation = onOpenScreenOrientation,
                    )
                },
                readingMode = if (currentRenderMode == RenderMode.PAGINATED) {
                    com.aryan.reader.shared.reader.ReaderReadingMode.PAGINATED
                } else {
                    com.aryan.reader.shared.reader.ReaderReadingMode.VERTICAL
                },
                rightToLeftPagination = isRightToLeftPagination,
                useNativeVerticalRenderer = useNativeVerticalRenderer,
                tapToNavigateEnabled = tapToNavigateEnabled,
                pageTurnAnimationEnabled = isPageTurnAnimationEnabled,
                onReadingModeChange = { mode ->
                    onChangeRenderMode(
                        if (mode == com.aryan.reader.shared.reader.ReaderReadingMode.PAGINATED) {
                            RenderMode.PAGINATED
                        } else {
                            RenderMode.VERTICAL_SCROLL
                        }
                    )
                },
                onUseNativeVerticalRendererChange = onUseNativeVerticalRendererChange,
                onRightToLeftPaginationChange = onSetRightToLeftPagination,
                onTapToNavigateChange = onToggleTapToNavigate,
                onPageTurnAnimationChange = onTogglePageTurnAnimation,
                toolbarPreferences = toolbarPreferences,
                localTtsState = epubChromeReadAloudState(isTtsActive),
                onLocalTtsToggle = onToggleTts,
                onLocalTtsStop = onToggleTts,
                // The reader's chrome entry is the local engine's; cloud TTS is reached through the
                // overflow sheet (the shared bar's cloud path is the iOS reader's).
                cloudTtsAvailable = false,
                keepScreenOn = isKeepScreenOn,
                onKeepScreenOnChange = onToggleKeepScreenOn,
                // Android starts auto-scroll through the overflow row's callback; it has no separate
                // "currently scrolling" chrome state to render.
                autoScroll = false,
                onAutoScrollChange = { onStartAutoScroll() },
                hasMediaOverlayNarration = hasMediaOverlayNarration,
                isMediaOverlayActive = isMediaOverlayActive,
                onToggleMediaOverlay = onToggleMediaOverlay,
                isSliderActive = isSliderActive,
                isSearchActive = searchState.isSearchActive,
                searchContent = {
                    SearchTopBar(
                        searchState = searchState,
                        focusRequester = searchFocusRequester,
                        onCloseSearch = onCloseSearch
                    )
                },
            )
        }
    }
}

/**
 * Android adapter over the shared jump-history bar.
 *
 * Layout, sizes and icons live once in [SharedMobileEpubJumpHistoryBar]; Android keeps only the
 * host-owned visibility animation (`AnimatedVisibility` + [ReaderMotionPolicy]). Both hosts now
 * share Android's accessibility labels, so nothing Android renders changes.
 */
@Composable
fun EpubJumpHistoryBar(
    modifier: Modifier = Modifier,
    showStandardBars: Boolean,
    searchStateActive: Boolean,
    backLabel: String?,
    forwardLabel: String?,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClear: () -> Unit,
    readerMotionPolicy: ReaderMotionPolicy = ReaderMotionPolicy(),
) {
    AnimatedVisibility(
        visible = showStandardBars && !searchStateActive && (backLabel != null || forwardLabel != null),
        enter = if (readerMotionPolicy.reduceMotion) {
            androidx.compose.animation.EnterTransition.None
        } else {
            slideInVertically(animationSpec = tween(200)) { fullHeight -> fullHeight } + fadeIn(animationSpec = tween(200))
        },
        exit = if (readerMotionPolicy.reduceMotion) {
            androidx.compose.animation.ExitTransition.None
        } else {
            slideOutVertically(animationSpec = tween(200)) { fullHeight -> fullHeight } + fadeOut(animationSpec = tween(200))
        },
        modifier = modifier
    ) {
        SharedMobileEpubJumpHistoryBar(
            backLabel = backLabel,
            forwardLabel = forwardLabel,
            onBack = onBack,
            onForward = onForward,
            onClear = onClear
        )
    }
}

/**
 * Android adapter over the shared EPUB bottom bar.
 *
 * Same contract as [EpubReaderTopBar]: the shared widget owns the row, its order, the tooltips and
 * the read-aloud entry, and Android supplies what only it has - the flat preferences converted by
 * [epubReaderToolbarPreferences], the AI availability gate and the string resolver.
 *
 * The reader's tool row never contains the bookmark or visual-options tools (both are
 * overflow-category tools, and only tools in [epubToolbarTools] can be placed in a bar), so the two
 * callbacks the shared bar takes for them are unreachable here.
 */
@Composable
fun EpubReaderBottomBar(
    isVisible: Boolean,
    isTtsSessionActive: Boolean,
    isSliderActive: Boolean,
    onOpenSlider: () -> Unit,
    onOpenDrawer: () -> Unit,
    onToggleFormat: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenAiHub: () -> Unit,
    onOpenDictionarySettings: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenBrightness: () -> Unit,
    onToggleTts: () -> Unit,
    onOpenScreenOrientation: () -> Unit,
    hiddenTools: Set<String>,
    toolOrder: List<ReaderTool>,
    bottomTools: Set<String>,
    /** Navigation-bar height painted by the bar's own surface; 0.dp when the bars are hidden. */
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    readerMotionPolicy: ReaderMotionPolicy = ReaderMotionPolicy(),
) {
    val context = LocalContext.current
    val toolbarPreferences = remember(hiddenTools, toolOrder, bottomTools) {
        epubReaderToolbarPreferences(
            hiddenTools = hiddenTools,
            toolOrder = toolOrder,
            bottomTools = bottomTools,
        )
    }
    val tools = remember(toolbarPreferences) {
        sharedEpubToolbarTools(
            toolOrder = toolbarPreferences.toolOrder,
            toolbarTools = epubToolbarTools,
            hiddenToolNamesOrIds = toolbarPreferences.hiddenToolIds,
            bottomToolNamesOrIds = toolbarPreferences.bottomToolIds,
            placement = SharedReaderToolbarPlacement.BOTTOM,
        )
    }
    SharedReaderBarVisibility(
        visible = isVisible,
        edge = SharedReaderBarEdge.BOTTOM,
        motionPolicy = readerMotionPolicy,
        modifier = modifier
    ) {
        CompositionLocalProvider(
            LocalSharedStringResolver provides androidSharedStringResolver(context)
        ) {
            SharedMobileEpubBottomBar(
                tools = tools,
                isBookmarked = false,
                onBookmark = {},
                onVisualOptions = {},
                onToc = onOpenDrawer,
                onFormat = onToggleFormat,
                onSearch = onToggleSearch,
                onTheme = onOpenThemeSettings,
                onOpenSlider = onOpenSlider,
                onDictionary = onOpenDictionarySettings,
                onBrightness = onOpenBrightness,
                onScreenOrientation = onOpenScreenOrientation,
                onOpenAiHub = onOpenAiHub,
                aiAvailable = areReaderAiFeaturesEnabled(context),
                localTtsState = epubChromeReadAloudState(isTtsSessionActive),
                onLocalTtsToggle = onToggleTts,
                onLocalTtsStop = onToggleTts,
                bottomInset = bottomInset,
                isSliderActive = isSliderActive,
            )
        }
    }
}

/**
 * Android adapter over the shared page slider.
 *
 * The bar, steppers, insets, slider geometry and accessibility labels (`desktop_previous_page` /
 * `desktop_next_page`) live once in [SharedMobileEpubSlider]; Android keeps only the host-owned
 * visibility animation (`AnimatedVisibility` + [ReaderMotionPolicy]) and translates between its own
 * 1-based spread-step space and the shared widget's 0-based book-page space.
 *
 * The conversion is identity unless a two-page spread is in play. That matters beyond paginated
 * mode: native vertical reading and per-chapter scroll each carry their own page total in
 * [totalPages], and the shared `EpubPageSpread.spreadToBookPage` helper *clamps* to the book
 * paginator's count, so feeding it the wrong total there would pin the thumb to page 0.
 *
 * [sliderCurrentPage] is the committed step, never the live scrub position: the wrapper feeds scrub
 * updates out through [onScrub] only, so the shared widget keeps its own thumb state and the two
 * cannot fight (see the caller's `scrubStep`).
 */
@Composable
fun EpubReaderPageSlider(
    isVisible: Boolean,
    totalPages: Int,
    sliderCurrentPage: Float,
    isTwoPageSpread: Boolean,
    totalBookPageCount: Int,
    onScrub: (Float) -> Unit,
    onJumpToPage: (Int) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.Unspecified,
    inactiveColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    readerMotionPolicy: ReaderMotionPolicy = ReaderMotionPolicy(),
) {
    val current = epubSliderBookPage(
        totalPages = totalPages,
        sliderCurrentPage = sliderCurrentPage,
        isTwoPageSpread = isTwoPageSpread,
        totalBookPageCount = totalBookPageCount,
    )
    val stepForBookPage = { bookPage: Int ->
        epubSliderStepForBookPage(bookPage, isTwoPageSpread, totalBookPageCount)
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = if (readerMotionPolicy.reduceMotion) {
            androidx.compose.animation.EnterTransition.None
        } else {
            slideInVertically(animationSpec = tween(200)) { fullHeight -> fullHeight } + fadeIn(animationSpec = tween(200))
        },
        exit = if (readerMotionPolicy.reduceMotion) {
            androidx.compose.animation.ExitTransition.None
        } else {
            slideOutVertically(animationSpec = tween(200)) { fullHeight -> fullHeight } + fadeOut(animationSpec = tween(200))
        },
        modifier = modifier
    ) {
        SharedMobileEpubSlider(
            pageIndex = current.pageIndex,
            pageCount = current.pageCount,
            isTwoPageSpread = isTwoPageSpread,
            activeColor = if (activeColor == Color.Unspecified) {
                MaterialTheme.colorScheme.primary
            } else {
                activeColor
            },
            inactiveColor = if (inactiveColor == Color.Unspecified) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                inactiveColor
            },
            contentColor = if (contentColor == Color.Unspecified) {
                MaterialTheme.colorScheme.onSurface
            } else {
                contentColor
            },
            onPageSelected = { bookPage -> onJumpToPage(stepForBookPage(bookPage)) },
            onScrubPositionChange = { bookPage ->
                if (bookPage == null) {
                    onScrubFinished()
                } else {
                    onScrub(stepForBookPage(bookPage).toFloat())
                }
            },
        )
    }
}

// --- Helpers moved from Screen ---

/**
 * Android adapter over the shared scrub readout.
 *
 * The bubble's surface, icon and type scale live once in
 * [SharedMobileEpubScrubBubble]; only the localized `page_of_format` label stays
 * here. The full-screen, touch-swallowing Box stays Android-side because the
 * shared bubble is deliberately unpositioned, and the shared
 * `SharedReaderIcons.Slider` is byte-identical to `R.drawable.slider`.
 */
@Composable
fun PageScrubbingAnimation(currentPage: Int, totalPages: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center
    ) {
        SharedMobileEpubScrubBubble(
            label = stringResource(R.string.page_of_format, currentPage, totalPages)
        )
    }
}

/**
 * Android adapter over the shared auto-scroll surface.
 *
 * Layout, sizes, icons, colours and animation live once in
 * [SharedMobileAutoScrollControls]; only the localized labels stay here. The
 * Android reader composes without a `LocalSharedStringResolver`, so a shared
 * `readerString` lookup would render English inside an otherwise translated
 * reader — hence the explicit [SharedMobileAutoScrollLabels] table, and hence
 * the Android-shaped callback names (`onPlayPauseToggle`, `onLocalModeToggle`)
 * this wrapper keeps for its two call sites (EpubReaderScreen, PdfViewerScreen).
 */
@Composable
fun AutoScrollControls(
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    speed: Float,
    minSpeed: Float,
    maxSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onMinSpeedChange: (Float) -> Unit,
    onMaxSpeedChange: (Float) -> Unit,
    onClose: () -> Unit,
    isCollapsed: Boolean,
    onCollapseChange: (Boolean) -> Unit,
    isMusicianMode: Boolean,
    onMusicianModeToggle: () -> Unit,
    useSlider: Boolean,
    onInputModeToggle: () -> Unit,
    isLocalMode: Boolean,
    onLocalModeToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isTempPaused: Boolean = false,
    onScrollToTop: (() -> Unit)? = null,
    readerMotionPolicy: ReaderMotionPolicy = ReaderMotionPolicy(),
) {
    SharedMobileAutoScrollControls(
        labels = SharedMobileAutoScrollLabels(
            expand = stringResource(R.string.content_desc_expand),
            collapse = stringResource(R.string.content_desc_collapse),
            pause = stringResource(R.string.tooltip_tts_pause),
            play = stringResource(R.string.content_desc_start_playback),
            globalSpeed = stringResource(R.string.auto_scroll_global_speed),
            localSpeed = stringResource(R.string.auto_scroll_local_speed),
            selectMode = stringResource(R.string.content_desc_select_mode),
            appliesToAllFiles = stringResource(R.string.auto_scroll_applies_all_files),
            savedForThisFile = stringResource(R.string.auto_scroll_saved_for_file),
            scrollToTop = stringResource(R.string.action_scroll_to_top),
            enableMusicianMode = stringResource(R.string.content_desc_enable_musician_mode),
            disableMusicianMode = stringResource(R.string.content_desc_disable_musician_mode),
            swapControls = stringResource(R.string.content_desc_swap_controls),
            close = stringResource(R.string.action_close),
            min = stringResource(R.string.label_min),
            max = stringResource(R.string.label_max),
            slower = stringResource(R.string.content_desc_slower),
            faster = stringResource(R.string.content_desc_faster)
        ),
        readerMotionPolicy = readerMotionPolicy,
        isPlaying = isPlaying,
        isTempPaused = isTempPaused,
        speed = speed,
        minSpeed = minSpeed,
        maxSpeed = maxSpeed,
        isLocalMode = isLocalMode,
        isMusicianMode = isMusicianMode,
        useSlider = useSlider,
        isCollapsed = isCollapsed,
        onPlayPause = onPlayPauseToggle,
        onSpeedChange = onSpeedChange,
        onMinSpeedChange = onMinSpeedChange,
        onMaxSpeedChange = onMaxSpeedChange,
        onScrollToTop = onScrollToTop,
        onMusicianModeToggle = onMusicianModeToggle,
        onInputModeToggle = onInputModeToggle,
        onCollapseChange = onCollapseChange,
        onLocalModeChange = onLocalModeToggle,
        onClose = onClose,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeToolsSheet(
    hiddenTools: Set<String>,
    toolOrder: List<ReaderTool>,
    bottomTools: Set<String>,
    onUpdate: (Set<String>) -> Unit,
    onOrderUpdate: (List<ReaderTool>) -> Unit,
    onPlacementUpdate: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    com.aryan.reader.shared.ui.SharedEpubToolbarCustomizationDialog(
        hiddenToolIds = hiddenTools.mapNotNullTo(mutableSetOf()) { ReaderTool.fromId(it)?.id },
        toolOrder = toolOrder,
        bottomToolIds = bottomTools.mapNotNullTo(mutableSetOf()) { ReaderTool.fromId(it)?.id },
        toolbarTools = epubToolbarTools,
        availableTools = ReaderTool.entries.toSet(),
        labels = com.aryan.reader.shared.ui.SharedEpubToolbarCustomizationLabels(
            title = stringResource(R.string.title_customize_toolbar),
            reset = stringResource(R.string.action_reset),
            close = stringResource(R.string.action_close),
            topBar = stringResource(R.string.toolbar_top_bar),
            bottomBar = stringResource(R.string.toolbar_bottom_bar),
            hiddenTools = stringResource(R.string.toolbar_hidden_tools),
            dropToolsHere = stringResource(R.string.toolbar_drop_tools_here),
            moreMenu = stringResource(R.string.toolbar_more_menu),
        ),
        toolTitle = { context.getString(it.titleRes) },
        onHiddenToolsUpdate = { ids ->
            onUpdate(ids.mapNotNullTo(mutableSetOf()) { ReaderTool.fromId(it)?.name })
        },
        onToolOrderUpdate = onOrderUpdate,
        onBottomToolsUpdate = { ids ->
            onPlacementUpdate(ids.mapNotNullTo(mutableSetOf()) { ReaderTool.fromId(it)?.name })
        },
        onDismiss = onDismiss,
        toolIcon = { ToolPreviewIcon(it) },
    )
}

@Composable
private fun ToolPreviewIcon(tool: ReaderTool, isSliderActive: Boolean = false) {
    val title = stringResource(tool.titleRes)
    when (tool) {
        ReaderTool.DICTIONARY -> Icon(painterResource(id = R.drawable.dictionary), contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.THEME -> Icon(painterResource(id = R.drawable.palette), contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.BRIGHTNESS -> Icon(painterResource(id = R.drawable.contrast), contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.SLIDER -> Icon(
            painterResource(id = R.drawable.slider),
            contentDescription = title,
            modifier = Modifier.size(20.dp),
            tint = if (isSliderActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        ReaderTool.TOC -> Icon(Icons.Default.Menu, contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.FORMAT -> Icon(painterResource(id = R.drawable.format_size), contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.SEARCH -> Icon(Icons.Default.Search, contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.AI_FEATURES -> Icon(painterResource(id = R.drawable.ai), contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.TTS_CONTROLS -> Icon(painterResource(id = R.drawable.text_to_speech), contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.BOOK_REPLACEMENTS -> Icon(painterResource(id = R.drawable.text_fields), contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.FILE_INFO -> Icon(Icons.Default.Info, contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.SCREEN_ORIENTATION -> Icon(Icons.Default.ScreenRotation, contentDescription = title, modifier = Modifier.size(20.dp))
        else -> Icon(Icons.Default.MoreVert, contentDescription = title, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HiddenEpubToolMenuItem(
    tool: ReaderTool,
    isSliderActive: Boolean,
    showMoreMenu: () -> Unit,
    onOpenDictionarySettings: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenBrightness: () -> Unit,
    onOpenSlider: () -> Unit,
    onOpenDrawer: () -> Unit,
    onToggleFormat: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenAiHub: () -> Unit,
    onToggleTts: () -> Unit,
    onOpenScreenOrientation: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(tool.titleRes)) },
        onClick = {
            showMoreMenu()
            when (tool) {
                ReaderTool.DICTIONARY -> onOpenDictionarySettings()
                ReaderTool.THEME -> onOpenThemeSettings()
                ReaderTool.BRIGHTNESS -> onOpenBrightness()
                ReaderTool.SLIDER -> onOpenSlider()
                ReaderTool.TOC -> onOpenDrawer()
                ReaderTool.FORMAT -> onToggleFormat()
                ReaderTool.SEARCH -> onToggleSearch()
                ReaderTool.AI_FEATURES -> onOpenAiHub()
                ReaderTool.TTS_CONTROLS -> onToggleTts()
                ReaderTool.SCREEN_ORIENTATION -> onOpenScreenOrientation()
                else -> Unit
            }
        },
        leadingIcon = { ToolPreviewIcon(tool, isSliderActive = isSliderActive) },
        trailingIcon = if (tool == ReaderTool.SLIDER && isSliderActive) {
            {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.content_desc_enabled))
            }
        } else null
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsOverlayControls(
    ttsController: com.aryan.reader.tts.TtsController,
    ttsState: TtsState,
    currentTtsMode: com.aryan.reader.tts.TtsPlaybackManager.TtsMode,
    overlaySize: ReaderTtsOverlaySize,
    onOverlaySizeChange: (ReaderTtsOverlaySize) -> Unit,
    onLocateCurrentChunk: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    credits: Int,
    walletMicros: Long = 0L,
    walletMigrated: Boolean = false,
    readerMotionPolicy: ReaderMotionPolicy = ReaderMotionPolicy(),
) {
    val context = LocalContext.current
    var rate by remember { mutableFloatStateOf(loadTtsSpeechRate(context)) }
    var pitch by remember { mutableFloatStateOf(loadTtsPitch(context)) }

    val activeMode = try { com.aryan.reader.tts.TtsPlaybackManager.TtsMode.valueOf(ttsState.ttsMode) } catch(_: Exception) { com.aryan.reader.tts.TtsPlaybackManager.TtsMode.CLOUD }
    val progressPercent = ttsState.bookProgressPercent
    val cleanChapterTitle = remember(ttsState.chapterTitle) {
        ttsState.chapterTitle
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.joinToString(" - ")
            ?.takeIf { it.isNotBlank() }
    }
    val chapterLabel = remember(ttsState.chapterIndex, ttsState.totalChapters, cleanChapterTitle) {
        val chapterNumber = ttsState.chapterIndex?.plus(1)
        val totalChapters = ttsState.totalChapters
        when {
            chapterNumber != null && totalChapters != null -> buildString {
                append(context.getString(R.string.chapter_of_chapters, chapterNumber, totalChapters))
                if (!cleanChapterTitle.isNullOrBlank()) append(": $cleanChapterTitle")
            }
            chapterNumber != null -> buildString {
                append(context.getString(R.string.chapter_number, chapterNumber))
                if (!cleanChapterTitle.isNullOrBlank()) append(": $cleanChapterTitle")
            }
            !cleanChapterTitle.isNullOrBlank() -> cleanChapterTitle
            else -> null
        }
    }
    val chunkLabel = remember(ttsState.currentChunkIndex, ttsState.totalChunks) {
        readerTtsChunkLabel(ttsState.currentChunkIndex, ttsState.totalChunks)
    }
    val miniBarTitle = ttsState.bookTitle
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.action_read_aloud)
    val miniBarSubtitle = remember(chapterLabel, chunkLabel, progressPercent, miniBarTitle) {
        listOfNotNull(
            chunkLabel,
            progressPercent?.let { "$it%" },
            chapterLabel?.takeIf { it != miniBarTitle }
        ).joinToString(" - ")
    }
    val canSkipPreviousChunk = !ttsState.isLoading &&
        ttsState.currentChunkIndex > 0 &&
        ttsState.totalChunks > 0
    val canSkipNextChunk = !ttsState.isLoading &&
        ttsState.currentChunkIndex >= 0 &&
        ttsState.currentChunkIndex < ttsState.totalChunks - 1

    val saveAndApply = {
        saveTtsSpeechRate(context, rate)
        saveTtsPitch(context, pitch)
        ttsController.setPlaybackParameters(rate, pitch)
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        modifier = modifier
            .widthIn(max = if (overlaySize == ReaderTtsOverlaySize.MEDIUM) 560.dp else 400.dp)
            .then(if (readerMotionPolicy.reduceMotion) Modifier else Modifier.animateContentSize())
    ) {
        AnimatedContent(
            targetState = overlaySize,
            transitionSpec = {
                if (readerMotionPolicy.reduceMotion) {
                    androidx.compose.animation.EnterTransition.None togetherWith
                        androidx.compose.animation.ExitTransition.None
                } else {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                }
            },
            label = "TtsOverlayUnified"
        ) { size ->
            if (size == ReaderTtsOverlaySize.SMALL) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.LARGE) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            stringResource(R.string.content_desc_expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.MEDIUM) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            stringResource(R.string.content_desc_expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        FilledIconButton(
                            onClick = { if (ttsState.isPlaying) ttsController.pause() else ttsController.resume() },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                painterResource(if (ttsState.isPlaying) R.drawable.pause else R.drawable.play),
                                stringResource(R.string.content_desc_play_pause),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (ttsState.isLoading) CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                            strokeWidth = 2.dp
                        )
                    }
                }
            } else if (size == ReaderTtsOverlaySize.MEDIUM) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onLocateCurrentChunk)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = miniBarTitle,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (miniBarSubtitle.isNotBlank()) {
                            Text(
                                text = miniBarSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    IconButton(
                        enabled = canSkipPreviousChunk,
                        onClick = { ttsController.skipToPreviousChunk() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.content_desc_tts_previous_chunk),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        FilledIconButton(
                            onClick = { if (ttsState.isPlaying) ttsController.pause() else ttsController.resume() },
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                painterResource(if (ttsState.isPlaying) R.drawable.pause else R.drawable.play),
                                stringResource(R.string.content_desc_play_pause),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (ttsState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    IconButton(
                        enabled = canSkipNextChunk,
                        onClick = { ttsController.skipToNextChunk() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.content_desc_tts_next_chunk),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        IconButton(
                            onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.LARGE) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.content_desc_expand),
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.SMALL) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.content_desc_collapse),
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    if (activeMode == com.aryan.reader.tts.TtsPlaybackManager.TtsMode.CLOUD) {
                                        stringResource(R.string.tts_mode_cloud_ai)
                                    } else {
                                        stringResource(R.string.tts_mode_device_native)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                modifier = Modifier.weight(1f, fill = false),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                val voiceName = if (activeMode == com.aryan.reader.tts.TtsPlaybackManager.TtsMode.CLOUD) {
                                    val speakerId = ttsState.speakerId
                                    val savedName =
                                        // Saved at pick time; guarded by id so a stale name
                                        // never labels a different voice.
                                        if (normalizeTtsSpeakerId(loadTtsSpeaker(context)) == normalizeTtsSpeakerId(speakerId)) {
                                            loadTtsSpeakerName(context)
                                        } else null
                                    GEMINI_TTS_SPEAKERS.find { it.id == speakerId }?.name
                                        ?: savedName
                                        // Last resort: short id, never a raw 32-hex wall.
                                        ?: if (isFishReferenceId(speakerId)) speakerId.trim().take(6) + "…" else speakerId
                                } else loadNativeVoice(context)?.split("-")?.lastOrNull() ?: stringResource(R.string.label_default)

                                Text(
                                    voiceName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).widthIn(max = 90.dp)
                                )
                            }

                            if (BuildConfig.FLAVOR != "oss" && activeMode == com.aryan.reader.tts.TtsPlaybackManager.TtsMode.CLOUD) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        spendableDisplayText(credits, walletMicros, walletMigrated),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.content_desc_stop_tts), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Spend-guard notice (rate-limit countdown / daily cap) and the
                    // live session-spend line for the USD wallet. Compact by design:
                    // one caption row, no extra sections.
                    val isCloudSpendable = BuildConfig.FLAVOR != "oss" && activeMode == com.aryan.reader.tts.TtsPlaybackManager.TtsMode.CLOUD
                    val spendGuard = remember(ttsState.errorMessage) { parseSpendGuardSentinel(ttsState.errorMessage) }
                    if (spendGuard != null && isCloudSpendable) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (spendGuard.first == "RATE_LIMITED") {
                                    stringResource(R.string.tts_notice_rate_limited, formatSpendGuardCountdown(spendGuard.second))
                                } else {
                                    stringResource(R.string.tts_notice_spend_limit, formatSpendGuardCountdown(spendGuard.second))
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    } else if (isCloudSpendable && walletMigrated && ttsState.cloudSessionSpendMicros > 0) {
                        Text(
                            text = stringResource(R.string.tts_session_spend, formatMicrosUsd(ttsState.cloudSessionSpendMicros)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (chapterLabel != null || progressPercent != null || chunkLabel != null) {
                            Text(
                                chapterLabel ?: "Reading",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Text(
                                listOfNotNull(progressPercent?.let { "$it%" }, chunkLabel).joinToString(" - "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = onLocateCurrentChunk, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    painterResource(R.drawable.pin_drop),
                                    "Locate current chunk",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.MEDIUM) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    stringResource(R.string.content_desc_collapse),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.SMALL) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowRight,
                                    stringResource(R.string.content_desc_collapse),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Middle Section: Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                enabled = canSkipPreviousChunk,
                                onClick = { ttsController.skipToPreviousChunk() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = stringResource(R.string.content_desc_tts_previous_chunk),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Giant Play/Pause
                            Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                FilledIconButton(
                                    onClick = { if (ttsState.isPlaying) ttsController.pause() else ttsController.resume() },
                                    modifier = Modifier.size(56.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        painterResource(if (ttsState.isPlaying) R.drawable.pause else R.drawable.play),
                                        stringResource(R.string.content_desc_play_pause),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                if (ttsState.isLoading) CircularProgressIndicator(
                                    modifier = Modifier.size(56.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    strokeWidth = 3.dp
                                )
                            }

                            IconButton(
                                enabled = canSkipNextChunk,
                                onClick = { ttsController.skipToNextChunk() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = stringResource(R.string.content_desc_tts_next_chunk),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        // Unified Sliders Block
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.tts_speed_short, "%.1f".format(rate)),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    IconButton(onClick = { rate = 1.0f; saveAndApply() }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.Refresh, stringResource(R.string.content_desc_reset_speed), modifier = Modifier.size(16.dp))
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { rate = ((rate * 10f).roundToInt() / 10f - 0.1f).coerceAtLeast(0.5f); saveAndApply() },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                    Slider(
                                        value = rate,
                                        onValueChange = { rate = it },
                                        onValueChangeFinished = saveAndApply,
                                        valueRange = 0.5f..3.0f,
                                        modifier = Modifier.weight(1f).height(20.dp),
                                        thumb = {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                        },
                                        track = { sliderState ->
                                            val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                                            val fraction = if (range == 0f) 0f else {
                                                ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(2.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                                                        RoundedCornerShape(1.dp)
                                                    ),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(fraction)
                                                        .height(2.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.primary,
                                                            RoundedCornerShape(1.dp)
                                                        )
                                                )
                                            }
                                        }
                                    )
                                    IconButton(
                                        onClick = { rate = ((rate * 10f).roundToInt() / 10f + 0.1f).coerceAtMost(3.0f); saveAndApply() },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.tts_pitch_short, "%.1f".format(pitch)),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    IconButton(onClick = { pitch = 1.0f; saveAndApply() }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.Refresh, stringResource(R.string.content_desc_reset_pitch), modifier = Modifier.size(16.dp))
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { pitch = ((pitch * 10f).roundToInt() / 10f - 0.1f).coerceAtLeast(0.5f); saveAndApply() },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                    Slider(
                                        value = pitch,
                                        onValueChange = { pitch = it },
                                        onValueChangeFinished = saveAndApply,
                                        valueRange = 0.5f..2.0f,
                                        modifier = Modifier.weight(1f).height(20.dp),
                                        thumb = {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                        },
                                        track = { sliderState ->
                                            val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                                            val fraction = if (range == 0f) 0f else {
                                                ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(2.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                                                        RoundedCornerShape(1.dp)
                                                    ),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(fraction)
                                                        .height(2.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.primary,
                                                            RoundedCornerShape(1.dp)
                                                        )
                                                )
                                            }
                                        }
                                    )
                                    IconButton(
                                        onClick = { pitch = ((pitch * 10f).roundToInt() / 10f + 0.1f).coerceAtMost(2.0f); saveAndApply() },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
