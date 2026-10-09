package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Ai
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderCloudTtsState
import com.aryan.reader.shared.DEFAULT_CLOUD_TTS_SPEAKER_ID
import com.aryan.reader.shared.ReaderFishVoice
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.ReaderCloudTtsVoices
import com.aryan.reader.shared.ReaderTtsOverlaySize
import com.aryan.reader.shared.formatReaderTtsBytes
import com.aryan.reader.shared.formatMicrosUsd
import com.aryan.reader.shared.formatSpendGuardCountdown
import com.aryan.reader.shared.parseSpendGuardSentinel
import com.aryan.reader.shared.spendableDisplayText
import com.aryan.reader.shared.ReaderWordReplacementEngine
import com.aryan.reader.shared.ReaderWordReplacementRule
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.reader.ReaderReadingMode
import kotlin.math.roundToInt

/** Stable identifiers for the EPUB reader chrome (automation + accessibility). */
internal object SharedMobileEpubAxTags {
    const val TOP_BAR = "EpubTopBar"
    const val BOTTOM_BAR = "EpubBottomBar"
    const val BACK = "EpubBack"
    const val TITLE = "EpubTitle"
    const val MORE = "EpubMore"
    const val CONTENT = "EpubReaderContent"
    const val PAGE_INFO = "EpubPageInfo"

    /**
     * The narration toggle, present only for a book that has media overlays.
     *
     * Conditional rather than always-rendered because a book without a publisher's recording must
     * not show a button that does nothing, so a test has to assert on its presence, not its label.
     */
    const val MEDIA_OVERLAY = "EpubMediaOverlay"
}

/**
 * Blocking busy overlay: dims the reader background, swallows taps, and shows a
 * spinner with a caption. Android benchmark
 * (`epubreader/EpubReaderOverlayLayers.kt:60`).
 */
@Composable
fun SharedMobileEpubLoading(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
            .clickable(enabled = true) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
internal fun SharedMobileEpubError(
    message: String,
    onBack: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = readerString("action_back", "Back"),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(28.dp)
        ) {
            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(48.dp))
            Text(readerString("epub_open_failed", "Could not open EPUB"), style = MaterialTheme.typography.titleMedium)
            Text(message, color = MaterialTheme.colorScheme.error)
            if (onBack != null) {
                Spacer(Modifier.height(6.dp))
                Button(onClick = onBack) {
                    Text(readerString("action_go_back", "Go Back"))
                }
            }
        }
    }
}

@Composable
internal fun SharedMobileEpubTopBar(
    title: String,
    isBookmarked: Boolean,
    topTools: List<ReaderTool>,
    overflowTools: List<ReaderTool>,
    showMore: Boolean,
    onShowMoreChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onTheme: () -> Unit,
    onFormat: () -> Unit,
    onSearch: () -> Unit,
    onBookmark: () -> Unit,
    onVisualOptions: () -> Unit,
    onBrightness: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSlider: () -> Unit,
    onFileInfo: () -> Unit,
    onCustomizeTools: () -> Unit,
    onScreenOrientation: () -> Unit,
    onTtsSettings: () -> Unit,
    onTtsReplacements: () -> Unit,
    onBookReplacements: () -> Unit,
    onOpenDictionarySettings: () -> Unit,
    onOpenAiHub: () -> Unit = {},
    aiAvailable: Boolean = false,
    onExportAnnotations: (() -> Unit)? = null,
    readingMode: ReaderReadingMode,
    rightToLeftPagination: Boolean,
    useNativeVerticalRenderer: Boolean,
    tapToNavigateEnabled: Boolean,
    pageTurnAnimationEnabled: Boolean,
    onReadingModeChange: (ReaderReadingMode) -> Unit,
    onUseNativeVerticalRendererChange: (Boolean) -> Unit,
    onRightToLeftPaginationChange: (Boolean) -> Unit,
    onTapToNavigateChange: (Boolean) -> Unit,
    onPageTurnAnimationChange: (Boolean) -> Unit,
    toolbarPreferences: ReaderToolbarPreferences,
    localTtsState: SharedMobileEpubLocalTtsState,
    onLocalTtsToggle: () -> Unit,
    onLocalTtsStop: () -> Unit,
    cloudTtsState: ReaderCloudTtsState = ReaderCloudTtsState(),
    cloudTtsAvailable: Boolean = false,
    onCloudTtsToggle: () -> Unit = {},
    onCloudTtsStop: () -> Unit = {},
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    autoScroll: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    /**
     * Whether this book narrates itself, and whether it is narrating right now.
     *
     * Android benchmark (`EpubReaderControls.kt:406`): the icon renders hardcoded, after the tool
     * loop and before the overflow button, rather than as a customizable [ReaderTool] — a book
     * either has a publisher's narration or it does not, so there is nothing for a reader to
     * arrange. Defaults keep a caller that predates the feature compiling.
     */
    hasMediaOverlayNarration: Boolean = false,
    isMediaOverlayActive: Boolean = false,
    onToggleMediaOverlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showReadingModeExpanded by remember { mutableStateOf(false) }
    var showHiddenToolsExpanded by remember { mutableStateOf(false) }
    var showTtsSettingsExpanded by remember { mutableStateOf(false) }
    val formatContentDescription = readerString("tooltip_format", "Text formatting")
    // `Modifier.semantics { }` is not a composable scope, so the accessibility
    // labels are resolved here and reused inside the semantics blocks.
    val backContentDescription = readerString("tooltip_back", "Back")
    val themeContentDescription = readerString("tooltip_theme", "Theme")
    val tocContentDescription = readerString("menu_contents", "Contents")
    val searchContentDescription = readerString("action_search", "Search")
    val sliderContentDescription = readerString("tool_navigation_slider", "Navigation slider")
    val brightnessContentDescription = readerString("tool_brightness", "Brightness")
    val orientationContentDescription =
        readerString("visual_options_screen_orientation", "Screen orientation")
    val dictionaryContentDescription = readerString("tooltip_dictionary", "Dictionary")
    val aiContentDescription = readerString("tooltip_ai", "AI features")
    val visualOptionsContentDescription = readerString("menu_visual_options", "Visual options")
    val moreOptionsContentDescription = readerString("menu_more_options", "More options")
    val ttsBusy = localTtsState != SharedMobileEpubLocalTtsState.IDLE ||
        cloudTtsState.isLoading || cloudTtsState.isPlaying || cloudTtsState.isPaused
    val onReadAloudToggle = if (cloudTtsAvailable) onCloudTtsToggle else onLocalTtsToggle
    val onReadAloudStop = if (cloudTtsAvailable) onCloudTtsStop else onLocalTtsStop
    // Android benchmark (EpubReaderControls.kt:359-369): chrome TTS tap stops
    // the active session and shows a stop-X; pause/resume lives in the overlay.
    val readAloudActive = if (cloudTtsAvailable) {
        cloudTtsState.isLoading || cloudTtsState.isPlaying || cloudTtsState.isPaused
    } else {
        localTtsState != SharedMobileEpubLocalTtsState.IDLE
    }
    val readAloudIcon = if (readAloudActive) {
        Icons.Default.Close
    } else if (cloudTtsAvailable) {
        cloudTtsState.icon()
    } else {
        localTtsState.icon()
    }
    val readAloudLabel = if (readAloudActive) {
        readerString("tooltip_tts_stop", "Stop reading")
    } else if (cloudTtsAvailable) {
        cloudTtsState.menuLabel()
    } else {
        localTtsState.menuLabel()
    }
    // Android benchmark (EpubReaderScreen.kt:279): cap the chrome title at 40 chars.
    val chromeTitle = if (title.length > 40) title.take(40) + "..." else title
    Surface(modifier = modifier.testTag(SharedMobileEpubAxTags.TOP_BAR), tonalElevation = 4.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .height(55.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag(SharedMobileEpubAxTags.BACK)
                    .semantics { contentDescription = backContentDescription }
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
            Text(
                chromeTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).testTag(SharedMobileEpubAxTags.TITLE)
                    .semantics(mergeDescendants = true) { heading(); contentDescription = title }
            )
            topTools.forEach { tool ->
                // Android benchmark (EpubReaderControls): inactive chrome icons use
                // onSurface explicitly (slider/TTS active -> primary). Explicit tint
                // keeps custom SharedReaderIcons visible on iOS even if
                // LocalContentColor propagation differs; Menu/Search already worked
                // because they are Material vectors, custom ones did not.
                when (tool) {
                    ReaderTool.THEME -> IconButton(
                        onClick = onTheme,
                        modifier = Modifier.testTag("EpubTopTheme").semantics { contentDescription = themeContentDescription }
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    ReaderTool.TOC -> IconButton(
                        onClick = onOpenToc,
                        modifier = Modifier.testTag("EpubTopToc").semantics { contentDescription = tocContentDescription }
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    ReaderTool.FORMAT -> IconButton(
                        onClick = onFormat,
                        modifier = Modifier.testTag("EpubTopFormat").semantics { contentDescription = formatContentDescription }
                    ) {
                        Icon(SharedReaderIcons.FormatSize, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    ReaderTool.SEARCH -> IconButton(
                        onClick = onSearch,
                        modifier = Modifier.testTag("EpubTopSearch").semantics { contentDescription = searchContentDescription }
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    ReaderTool.SLIDER -> IconButton(
                        onClick = onOpenSlider,
                        modifier = Modifier.testTag("EpubTopSlider").semantics { contentDescription = sliderContentDescription }
                    ) {
                        Icon(SharedReaderIcons.Slider, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    ReaderTool.TTS_CONTROLS -> IconButton(
                        onClick = { if (readAloudActive) onReadAloudStop() else onReadAloudToggle() },
                        modifier = Modifier.testTag("EpubTopTts").semantics { contentDescription = readAloudLabel }
                    ) {
                        Icon(
                            readAloudIcon,
                            contentDescription = null,
                            tint = if (readAloudActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    ReaderTool.BRIGHTNESS -> IconButton(
                        onClick = onBrightness,
                        modifier = Modifier.testTag("EpubTopBrightness").semantics { contentDescription = brightnessContentDescription }
                    ) {
                        Icon(SharedReaderIcons.Contrast, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    ReaderTool.SCREEN_ORIENTATION -> IconButton(
                        onClick = onScreenOrientation,
                        modifier = Modifier.testTag("EpubTopOrientation").semantics { contentDescription = orientationContentDescription }
                    ) {
                        Icon(SharedReaderIcons.ScreenRotation, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    ReaderTool.DICTIONARY -> IconButton(
                        onClick = onOpenDictionarySettings,
                        modifier = Modifier.testTag("EpubTopDictionary").semantics { contentDescription = dictionaryContentDescription }
                    ) {
                        Icon(SharedReaderIcons.Dictionary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    ReaderTool.AI_FEATURES -> if (aiAvailable) IconButton(
                        onClick = onOpenAiHub,
                        modifier = Modifier.testTag("EpubTopAi").semantics { contentDescription = aiContentDescription }
                    ) {
                        Icon(Icons.Default.Ai, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    else -> Unit
                }
            }
            if (hasMediaOverlayNarration) {
                // The icon's description is the *action*, following the read-aloud button above: a
                // screen reader has to say what pressing this does, and one name for both states
                // leaves the button reading identically whether narration is running or not. The
                // tooltip keeps the feature name as its label.
                val narrationActionDescription = readerString(
                    if (isMediaOverlayActive) "content_desc_media_overlay_stop" else "content_desc_media_overlay_start",
                    if (isMediaOverlayActive) "Stop narration" else "Start narration"
                )
                IconButton(
                    onClick = onToggleMediaOverlay,
                    modifier = Modifier
                        .testTag(SharedMobileEpubAxTags.MEDIA_OVERLAY)
                        .semantics { contentDescription = narrationActionDescription }
                ) {
                    Icon(
                        // AutoMirrored because the Android drawable this mirrors declares
                        // `android:autoMirrored="true"` — the speaker has to face the sound in an RTL
                        // layout, and a non-mirrored copy would point the wrong way there.
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = if (isMediaOverlayActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { onShowMoreChange(true) },
                    modifier = Modifier.testTag(SharedMobileEpubAxTags.MORE).semantics { contentDescription = moreOptionsContentDescription }
                ) { Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                SharedDropdownMenu(expanded = showMore, onDismissRequest = { onShowMoreChange(false) }) {
                    DropdownMenuItem(
                        text = { Text(readerString("title_customize_toolbar", "Customize Toolbar")) },
                        onClick = { onShowMoreChange(false); onCustomizeTools() },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                    val hiddenToolbarTools = toolbarPreferences.sanitized().toolOrder.filter { tool ->
                        tool in SharedMobileEpubToolbarTools && !toolbarPreferences.isVisible(tool) &&
                            (tool != ReaderTool.AI_FEATURES || aiAvailable)
                    }
                    if (hiddenToolbarTools.isNotEmpty()) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(readerString("toolbar_hidden_tools_menu", "Hidden tools")) },
                            onClick = { showHiddenToolsExpanded = !showHiddenToolsExpanded },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                        )
                        if (showHiddenToolsExpanded) {
                            hiddenToolbarTools.forEach { tool ->
                                DropdownMenuItem(
                                    text = { Text(tool.title) },
                                    onClick = {
                                        when (tool) {
                                            ReaderTool.THEME -> onTheme()
                                            ReaderTool.TOC -> onOpenToc()
                                            ReaderTool.FORMAT -> onFormat()
                                            ReaderTool.SEARCH -> onSearch()
                                            ReaderTool.SLIDER -> onOpenSlider()
                                            ReaderTool.TTS_CONTROLS -> if (readAloudActive) onReadAloudStop() else onLocalTtsToggle()
                                            ReaderTool.BRIGHTNESS -> onBrightness()
                                            ReaderTool.SCREEN_ORIENTATION -> onScreenOrientation()
                                            ReaderTool.DICTIONARY -> onOpenDictionarySettings()
                                            ReaderTool.AI_FEATURES -> onOpenAiHub()
                                            else -> Unit
                                        }
                                        showHiddenToolsExpanded = false
                                        onShowMoreChange(false)
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    overflowTools.forEach { tool ->
                        when (tool) {
                            ReaderTool.READING_MODE -> {
                                DropdownMenuItem(
                                    text = { Text(readerString("menu_change_reading_mode", "Change Reading Mode")) },
                                    onClick = { showReadingModeExpanded = !showReadingModeExpanded },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                                )
                                if (showReadingModeExpanded) {
                                    DropdownMenuItem(
                                        text = { Text(readerString("menu_reading_mode_vertical_webview", "Vertical (WebView)")) },
                                        enabled = !ttsBusy,
                                        onClick = {
                                            onUseNativeVerticalRendererChange(false)
                                            onReadingModeChange(ReaderReadingMode.VERTICAL)
                                            showReadingModeExpanded = false
                                            onShowMoreChange(false)
                                        },
                                        trailingIcon = {
                                            if (readingMode == ReaderReadingMode.VERTICAL && !useNativeVerticalRenderer) Text("✓")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(readerString("menu_reading_mode_vertical_native", "Vertical (Native Beta)")) },
                                        enabled = !ttsBusy,
                                        onClick = {
                                            onUseNativeVerticalRendererChange(true)
                                            onReadingModeChange(ReaderReadingMode.VERTICAL)
                                            showReadingModeExpanded = false
                                            onShowMoreChange(false)
                                        },
                                        trailingIcon = {
                                            if (readingMode == ReaderReadingMode.VERTICAL && useNativeVerticalRenderer) Text("✓")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(readerString("menu_reading_mode_paginated", "Paginated (left-to-right)")) },
                                        enabled = !ttsBusy,
                                        onClick = {
                                            onRightToLeftPaginationChange(false)
                                            onReadingModeChange(ReaderReadingMode.PAGINATED)
                                            showReadingModeExpanded = false
                                            onShowMoreChange(false)
                                        },
                                        trailingIcon = { if (readingMode == ReaderReadingMode.PAGINATED && !rightToLeftPagination) Text("✓") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(readerString("menu_right_to_left_pagination", "Paginated (right-to-left)")) },
                                        enabled = !ttsBusy,
                                        onClick = {
                                            onRightToLeftPaginationChange(true)
                                            onReadingModeChange(ReaderReadingMode.PAGINATED)
                                            showReadingModeExpanded = false
                                            onShowMoreChange(false)
                                        },
                                        trailingIcon = { if (readingMode == ReaderReadingMode.PAGINATED && rightToLeftPagination) Text("✓") }
                                    )
                                }
                            }
                            ReaderTool.TAP_TO_TURN -> SharedMobileEpubSwitchMenuItem(
                                readerString("menu_tap_to_turn_pages", "Tap to Turn Pages"),
                                tapToNavigateEnabled,
                                onTapToNavigateChange,
                                enabled = readingMode == ReaderReadingMode.PAGINATED
                            )
                            ReaderTool.PAGE_TURN_ANIM -> SharedMobileEpubSwitchMenuItem(
                                readerString("menu_realistic_page_turns", "Realistic Page Turns"),
                                pageTurnAnimationEnabled,
                                onPageTurnAnimationChange,
                                enabled = readingMode == ReaderReadingMode.PAGINATED
                            )
                            ReaderTool.BOOKMARK -> DropdownMenuItem(
                                text = {
                                    Text(
                                        readerString(
                                            if (isBookmarked) "menu_remove_bookmark" else "menu_bookmark_this_page",
                                            if (isBookmarked) "Remove bookmark" else "Bookmark this page"
                                        )
                                    )
                                },
                                onClick = { onBookmark(); onShowMoreChange(false) },
                                leadingIcon = { Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null) }
                            )
                            ReaderTool.VISUAL_OPTIONS -> DropdownMenuItem(
                                text = { Text(readerString("menu_visual_options", "Visual Options")) }, onClick = { onVisualOptions(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) }
                            )
                            ReaderTool.TOC -> DropdownMenuItem(
                                text = { Text(readerString("menu_contents", "Contents")) }, onClick = { onOpenToc(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.Menu, contentDescription = null) }
                            )
                            ReaderTool.FORMAT -> DropdownMenuItem(
                                text = { Text(readerString("content_desc_text_formatting", "Text formatting")) }, onClick = { onFormat(); onShowMoreChange(false) }
                            )
                            ReaderTool.SEARCH -> DropdownMenuItem(
                                text = { Text(readerString("action_search", "Search")) }, onClick = { onSearch(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                            )
                            ReaderTool.SLIDER -> DropdownMenuItem(
                                text = { Text(readerString("tool_navigation_slider", "Navigation slider")) }, onClick = { onShowMoreChange(false); onOpenSlider() },
                                leadingIcon = { Icon(SharedReaderIcons.Slider, contentDescription = null) }
                            )
                            ReaderTool.TTS_CONTROLS -> DropdownMenuItem(
                                text = { Text(readAloudLabel) }, onClick = { onReadAloudToggle(); onShowMoreChange(false) }
                            )
                            ReaderTool.TTS_REPLACEMENTS -> {
                                // Covered by the TTS_SETTINGS expander when both are present.
                                if (ReaderTool.TTS_SETTINGS !in overflowTools) {
                                    DropdownMenuItem(
                                        text = { Text(readerString("menu_tts_word_replacements", "TTS Word Replacements")) },
                                        onClick = { onShowMoreChange(false); onTtsReplacements() },
                                        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) }
                                    )
                                } else Unit
                            }
                            ReaderTool.TTS_SETTINGS -> {
                                if (ReaderTool.TTS_REPLACEMENTS in overflowTools) {
                                    DropdownMenuItem(
                                        text = { Text(readerString("menu_tts_settings", "TTS Settings")) },
                                        onClick = { showTtsSettingsExpanded = !showTtsSettingsExpanded },
                                        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                                    )
                                    if (showTtsSettingsExpanded) {
                                        DropdownMenuItem(
                                            text = { Text(readerString("menu_tts_voice_settings", "TTS Voice Settings")) },
                                            enabled = !ttsBusy,
                                            onClick = { onShowMoreChange(false); onTtsSettings() },
                                            leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(readerString("menu_tts_word_replacements", "TTS Word Replacements")) },
                                            onClick = { onShowMoreChange(false); onTtsReplacements() },
                                            leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) }
                                        )
                                    }
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(readerString("menu_tts_voice_settings", "TTS Voice Settings")) },
                                        enabled = !ttsBusy,
                                        onClick = { onShowMoreChange(false); onTtsSettings() },
                                        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) }
                                    )
                                }
                            }
                            ReaderTool.BOOK_REPLACEMENTS -> DropdownMenuItem(
                                text = { Text(readerString("menu_book_word_replacements", "Book Word Replacements")) },
                                onClick = { onShowMoreChange(false); onBookReplacements() },
                                leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) }
                            )
                            ReaderTool.KEEP_SCREEN_ON -> SharedMobileEpubSwitchMenuItem("Keep Screen On", keepScreenOn, onKeepScreenOnChange)
                            ReaderTool.AUTO_SCROLL -> DropdownMenuItem(
                                // Android parity (EpubReaderControls
                                // EpubOverflowMenuSection.AUTO_SCROLL): the item
                                // always reads "Auto Scroll" and is only enabled
                                // for vertical reading while TTS is idle.
                                text = { Text(readerString("menu_auto_scroll", "Auto Scroll")) },
                                enabled = readingMode == ReaderReadingMode.VERTICAL &&
                                    !ttsBusy,
                                onClick = { onAutoScrollChange(!autoScroll); onShowMoreChange(false) }
                            )
                            ReaderTool.BRIGHTNESS -> DropdownMenuItem(
                                text = { Text(readerString("tool_brightness", "Brightness")) }, onClick = { onBrightness(); onShowMoreChange(false) }
                            )
                            ReaderTool.SCREEN_ORIENTATION -> DropdownMenuItem(
                                text = { Text(readerString("visual_options_screen_orientation", "Screen Orientation")) }, onClick = { onScreenOrientation(); onShowMoreChange(false) },
                                leadingIcon = { Icon(SharedReaderIcons.ScreenRotation, contentDescription = null) }
                            )
                            ReaderTool.AI_FEATURES -> if (aiAvailable) DropdownMenuItem(
                                text = { Text(readerString("tooltip_ai", "AI features")) },
                                onClick = { onOpenAiHub(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.Ai, contentDescription = null) }
                            )
                            ReaderTool.FILE_INFO -> DropdownMenuItem(
                                text = { Text(readerString("file_information", "File Information")) }, onClick = { onFileInfo(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                            )
                            ReaderTool.THEME -> Unit
                            else -> Unit
                        }
                    }
                    if (onExportAnnotations != null) {
                        DropdownMenuItem(
                            text = { Text(readerString("action_export_annotations", "Export annotations")) },
                            onClick = { onExportAnnotations(); onShowMoreChange(false) },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
                        )
                    }
                    if (ReaderTool.TTS_CONTROLS in overflowTools && ttsBusy) {
                        DropdownMenuItem(
                            text = { Text(readerString("menu_stop_reading", "Stop reading")) },
                            onClick = { onReadAloudStop(); onShowMoreChange(false) }
                        )
                    }
                }
            }
        }
    }
}

internal val SharedMobileEpubToolbarTools = setOf(
    ReaderTool.THEME,
    ReaderTool.SLIDER,
    ReaderTool.TOC,
    ReaderTool.FORMAT,
    ReaderTool.SEARCH,
    ReaderTool.TTS_CONTROLS,
    ReaderTool.BRIGHTNESS,
    ReaderTool.SCREEN_ORIENTATION,
    ReaderTool.DICTIONARY,
    ReaderTool.AI_FEATURES
)

internal val SharedMobileEpubCustomizableTools = SharedMobileEpubToolbarTools + setOf(
    ReaderTool.READING_MODE,
    ReaderTool.BOOKMARK,
    ReaderTool.TAP_TO_TURN,
    ReaderTool.PAGE_TURN_ANIM,
    ReaderTool.KEEP_SCREEN_ON,
    ReaderTool.VISUAL_OPTIONS,
    ReaderTool.AUTO_SCROLL,
    ReaderTool.TTS_SETTINGS,
    ReaderTool.TTS_REPLACEMENTS,
    ReaderTool.BOOK_REPLACEMENTS,
    ReaderTool.FILE_INFO,
    ReaderTool.AI_FEATURES
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedMobileEpubToolbarCustomizationSheet(
    toolbarPreferences: ReaderToolbarPreferences,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    var localHiddenTools by remember { mutableStateOf(toolbarPreferences.hiddenToolIds) }
    var flatItems by remember {
        mutableStateOf(
            buildSharedEpubToolbarItems(
                preferences = toolbarPreferences,
                toolbarTools = SharedMobileEpubToolbarTools,
                availableTools = SharedMobileEpubCustomizableTools,
            )
        )
    }

    val lazyListState = rememberLazyListState()
    val dragDropState = rememberSharedToolbarDragDropState(
        lazyListState = lazyListState,
        flatItems = { flatItems },
        onFlatItemsChange = { flatItems = it },
    )

    val commitDragDrop = {
        val next = buildSharedEpubToolbarCommit(flatItems, localHiddenTools, SharedMobileEpubToolbarTools)
        localHiddenTools = next.hiddenToolIds
        onToolbarPreferencesChange(next)
    }

    val resetToDefault = {
        val defaults = ReaderToolbarPreferences()
        localHiddenTools = defaults.hiddenToolIds
        flatItems = buildSharedEpubToolbarItems(
            preferences = defaults,
            toolbarTools = SharedMobileEpubToolbarTools,
            availableTools = SharedMobileEpubCustomizableTools,
        )
        onToolbarPreferencesChange(defaults.sanitized())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
        ) {
            SharedToolbarCustomizationHeader(
                title = readerString("title_customize_toolbar", "Customize Toolbar"),
                onReset = resetToDefault,
                onDismiss = onDismiss,
            )
            SharedToolbarDragDropList(
                flatItems = flatItems,
                dragDropState = dragDropState,
                emptyPlaceholderTitle = "Drop tools here",
                moreMenuTitle = "More Menu",
                toolRow = { item, isDragging ->
                    val tool = item.toolId?.let(ReaderTool::fromId)
                    if (tool != null) {
                        SharedToolbarDragRow(
                            title = tool.title,
                            isDragging = isDragging,
                            leadingIcon = { SharedEpubToolbarDragIcon(tool) },
                            onDragStart = { dragDropState.onDragStart(item.id) },
                            onDrag = { dragDropState.onDrag(it) },
                            onDragEnd = {
                                dragDropState.onDragEnd()
                                flatItems = sanitizeSharedToolbarPlaceholders(flatItems)
                                commitDragDrop()
                            },
                        )
                    }
                },
                moreToolRow = { item ->
                    val tool = item.toolId?.let(ReaderTool::fromId)
                    if (tool != null) {
                        SharedToolbarMoreVisibilityRow(
                            title = tool.title,
                            visible = !localHiddenTools.contains(tool.id),
                            onToggle = {
                                val next = if (localHiddenTools.contains(tool.id)) {
                                    localHiddenTools - tool.id
                                } else {
                                    localHiddenTools + tool.id
                                }
                                localHiddenTools = next
                                onToolbarPreferencesChange(
                                    toolbarPreferences.copy(hiddenToolIds = next).sanitized()
                                )
                            },
                        )
                    }
                },
            )
        }
    }
}

@Composable
internal fun SharedMobileEpubSwitchMenuItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    DropdownMenuItem(
        text = { Text(label) },
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        trailingIcon = { Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange) }
    )
}

@Composable
internal fun SharedMobileEpubBottomBar(
    tools: List<ReaderTool>,
    isBookmarked: Boolean,
    onToc: () -> Unit,
    onFormat: () -> Unit,
    onSearch: () -> Unit,
    onTheme: () -> Unit,
    onBookmark: () -> Unit,
    onVisualOptions: () -> Unit,
    onOpenSlider: () -> Unit,
    onDictionary: () -> Unit,
    onBrightness: () -> Unit = {},
    onScreenOrientation: () -> Unit = {},
    onOpenAiHub: () -> Unit = {},
    aiAvailable: Boolean = false,
    localTtsState: SharedMobileEpubLocalTtsState,
    onLocalTtsToggle: () -> Unit,
    onLocalTtsStop: () -> Unit = {},
    cloudTtsState: ReaderCloudTtsState = ReaderCloudTtsState(),
    cloudTtsAvailable: Boolean = false,
    onCloudTtsToggle: () -> Unit = {},
    onCloudTtsStop: () -> Unit = {},
    // When true the bottom system-bars inset is consumed *inside* the Surface
    // so the toolbar background (with its tonal elevation) paints the full
    // toolbar rect down to the screen edge. Applying it outside the Surface
    // leaves a transparent strip where the WebView shows through below the
    // toolbar. Geometry is unchanged either way (45.dp row + inset).
    applyBottomSafeInset: Boolean = false,
    modifier: Modifier = Modifier
) {
    val formatContentDescription = readerString("tooltip_format", "Text formatting")
    // `Modifier.semantics { }` is not a composable scope, so the accessibility
    // labels are resolved here and reused inside the semantics blocks.
    val themeContentDescription = readerString("tooltip_theme", "Theme")
    val tocContentDescription = readerString("menu_contents", "Contents")
    val searchContentDescription = readerString("action_search", "Search")
    val sliderContentDescription = readerString("tool_navigation_slider", "Navigation slider")
    val brightnessContentDescription = readerString("tool_brightness", "Brightness")
    val orientationContentDescription =
        readerString("visual_options_screen_orientation", "Screen orientation")
    val dictionaryContentDescription = readerString("tooltip_dictionary", "Dictionary")
    val aiContentDescription = readerString("tooltip_ai", "AI features")
    val visualOptionsContentDescription = readerString("menu_visual_options", "Visual options")
    val bookmarkContentDescription = if (isBookmarked) {
        readerString("menu_remove_bookmark", "Remove bookmark")
    } else {
        readerString("menu_bookmark_this_page", "Bookmark this page")
    }
    val onReadAloudToggle = if (cloudTtsAvailable) onCloudTtsToggle else onLocalTtsToggle
    val onReadAloudStop = if (cloudTtsAvailable) onCloudTtsStop else onLocalTtsStop
    // Android benchmark (EpubReaderControls.kt:359-369): chrome TTS tap stops
    // the active session and shows a stop-X; pause/resume lives in the overlay.
    val readAloudActive = if (cloudTtsAvailable) {
        cloudTtsState.isLoading || cloudTtsState.isPlaying || cloudTtsState.isPaused
    } else {
        localTtsState != SharedMobileEpubLocalTtsState.IDLE
    }
    val readAloudIcon = if (readAloudActive) {
        Icons.Default.Close
    } else if (cloudTtsAvailable) {
        cloudTtsState.icon()
    } else {
        localTtsState.icon()
    }
    val readAloudLabel = if (readAloudActive) {
        readerString("tooltip_tts_stop", "Stop reading")
    } else if (cloudTtsAvailable) {
        cloudTtsState.menuLabel()
    } else {
        localTtsState.menuLabel()
    }
    Surface(modifier = modifier.testTag(SharedMobileEpubAxTags.BOTTOM_BAR), tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .height(45.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tools.forEach { tool ->
                    // Android benchmark (EpubReaderControls): inactive icons use
                    // onSurface explicitly, active slider/TTS use primary. Explicit
                    // tint keeps custom icons visible on iOS.
                    when (tool) {
                        ReaderTool.TOC -> IconButton(
                            onClick = onToc,
                            modifier = Modifier.testTag("EpubBottomToc").semantics { contentDescription = tocContentDescription }
                        ) { Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                        ReaderTool.FORMAT -> IconButton(
                            onClick = onFormat,
                            modifier = Modifier.testTag("EpubBottomFormat").semantics { contentDescription = formatContentDescription }
                        ) {
                            Icon(SharedReaderIcons.FormatSize, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                        ReaderTool.SEARCH -> IconButton(
                            onClick = onSearch,
                            modifier = Modifier.testTag("EpubBottomSearch").semantics { contentDescription = searchContentDescription }
                        ) { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                        ReaderTool.THEME -> IconButton(
                            onClick = onTheme,
                            modifier = Modifier.testTag("EpubBottomTheme").semantics { contentDescription = themeContentDescription }
                        ) { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                        ReaderTool.BOOKMARK -> {
                            IconButton(
                                onClick = onBookmark,
                                modifier = Modifier.testTag("EpubBottomBookmark")
                                    .semantics { contentDescription = bookmarkContentDescription }
                            ) {
                                Icon(
                                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        ReaderTool.VISUAL_OPTIONS -> IconButton(
                            onClick = onVisualOptions,
                            modifier = Modifier.testTag("EpubBottomVisual").semantics { contentDescription = visualOptionsContentDescription }
                        ) { Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                        ReaderTool.SLIDER -> IconButton(
                            onClick = onOpenSlider,
                            modifier = Modifier.testTag("EpubBottomSlider").semantics { contentDescription = sliderContentDescription }
                        ) { Icon(SharedReaderIcons.Slider, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                        ReaderTool.DICTIONARY -> IconButton(
                            onClick = onDictionary,
                            modifier = Modifier.testTag("EpubBottomDictionary").semantics { contentDescription = dictionaryContentDescription }
                        ) { Icon(SharedReaderIcons.Dictionary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                        ReaderTool.BRIGHTNESS -> IconButton(
                            onClick = onBrightness,
                            modifier = Modifier.testTag("EpubBottomBrightness").semantics { contentDescription = brightnessContentDescription }
                        ) { Icon(SharedReaderIcons.Contrast, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                        ReaderTool.SCREEN_ORIENTATION -> IconButton(
                            onClick = onScreenOrientation,
                            modifier = Modifier.testTag("EpubBottomOrientation").semantics { contentDescription = orientationContentDescription }
                        ) { Icon(SharedReaderIcons.ScreenRotation, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                        ReaderTool.AI_FEATURES -> if (aiAvailable) IconButton(
                            onClick = onOpenAiHub,
                            modifier = Modifier.testTag("EpubBottomAi").semantics { contentDescription = aiContentDescription }
                        ) { Icon(Icons.Default.Ai, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                        ReaderTool.TTS_CONTROLS -> IconButton(
                            onClick = { if (readAloudActive) onReadAloudStop() else onReadAloudToggle() },
                            modifier = Modifier.testTag("EpubBottomTts").semantics { contentDescription = readAloudLabel }
                        ) {
                            Icon(
                                readAloudIcon,
                                contentDescription = null,
                                tint = if (readAloudActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        else -> Unit
                    }
                }
            }
            if (applyBottomSafeInset) {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
            }
        }
    }
}

@Composable
internal fun SharedMobileEpubLocalTtsState.menuLabel(): String = when (this) {
    SharedMobileEpubLocalTtsState.IDLE -> readerString("action_read_aloud", "Read aloud")
    SharedMobileEpubLocalTtsState.SPEAKING -> readerString("tts_pause_reading", "Pause reading")
    SharedMobileEpubLocalTtsState.PAUSED -> readerString("tts_resume_reading", "Resume reading")
}

internal fun SharedMobileEpubLocalTtsState.icon() = when (this) {
    SharedMobileEpubLocalTtsState.IDLE -> SharedReaderIcons.TextToSpeech
    SharedMobileEpubLocalTtsState.SPEAKING -> Icons.Default.Pause
    SharedMobileEpubLocalTtsState.PAUSED -> Icons.Default.PlayArrow
}

@Composable
internal fun ReaderCloudTtsState.menuLabel(): String = when {
    isLoading -> readerString("tts_preparing_cloud_reading", "Preparing cloud reading")
    isPlaying -> readerString("tts_pause_cloud_reading", "Pause cloud reading")
    isPaused -> readerString("tts_resume_cloud_reading", "Resume cloud reading")
    else -> readerString("tts_read_aloud_cloud", "Read aloud with Cloud AI")
}

internal fun ReaderCloudTtsState.icon() = when {
    isPlaying -> Icons.Default.Pause
    isPaused -> Icons.Default.PlayArrow
    // Android benchmark (EpubReaderControls): the idle chrome TTS entry uses
    // the same text-to-speech glyph for both engines; only play/pause swap.
    else -> SharedReaderIcons.TextToSpeech
}

@Composable
internal fun SharedMobileEpubTtsControls(
    tts: SharedMobileEpubLocalTts,
    onLocate: () -> Unit,
    onOpenSettings: () -> Unit,
    overlaySize: ReaderTtsOverlaySize = ReaderTtsOverlaySize.LARGE,
    onOverlaySizeChange: (ReaderTtsOverlaySize) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // onOpenSettings is kept for callers, but the overlay itself follows the
    // Android benchmark (TtsOverlayControls has no settings gear): voice
    // settings stay reachable through the reader toolbar TTS entry, and the
    // overlay only shows while the chrome is visible.
    val progress = tts.progress
    var rate by remember(tts.speechRate) { mutableStateOf(tts.speechRate) }
    var pitch by remember(tts.speechPitch) { mutableStateOf(tts.speechPitch) }
    val totalChunks = progress.chunks.size
    val chunkIndex = progress.currentChunkIndex
    val cleanChapterTitle = remember(progress.currentChunk?.chapterTitle) {
        progress.currentChunk?.chapterTitle
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.joinToString(" - ")
            ?.takeIf { it.isNotBlank() }
    }
    val chunkLabel = remember(chunkIndex, totalChunks) {
        if (chunkIndex in 0 until totalChunks) "Part ${chunkIndex + 1}/$totalChunks" else null
    }
    val isSpeaking = tts.state == SharedMobileEpubLocalTtsState.SPEAKING
    val canSkipPrevious = chunkIndex > 0 && totalChunks > 0
    val canSkipNext = chunkIndex >= 0 && chunkIndex < totalChunks - 1
    val rawVoiceId = remember(tts.selectedVoiceIdentifier) {
        tts.selectedVoiceIdentifier
            ?.substringAfterLast(".")
            ?.substringBefore("-")
            ?.trim()
            .takeIf { !it.isNullOrBlank() }
    }
    val voiceShortName = rawVoiceId ?: readerString("label_default", "Default")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = if (overlaySize == ReaderTtsOverlaySize.MEDIUM) 560.dp else 400.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
    ) {
        // Android parity (TtsOverlayControls): size changes crossfade, not snap.
        AnimatedContent(
            targetState = overlaySize,
            transitionSpec = { fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200)) },
            label = "EpubTtsOverlaySize"
        ) { size ->
            when (size) {
                ReaderTtsOverlaySize.SMALL -> Row(
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
                            readerString("content_desc_expand_reading_controls", "Expand reading controls"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.MEDIUM) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            readerString("content_desc_expand_reading_controls", "Expand reading controls"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SharedEpubTtsPlayButton(
                        isPlaying = isSpeaking,
                        isLoading = progress.currentChunk == null,
                        buttonSize = 36.dp,
                        filledSize = 36.dp,
                        iconSize = 20.dp,
                        ringStroke = 2.dp,
                        ringColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                        pauseDescription = readerString("tts_pause_reading", "Pause reading"),
                        resumeDescription = readerString("tts_resume_reading", "Resume reading"),
                        onClick = { if (isSpeaking) tts.pause() else tts.resume() },
                    )
                }
                ReaderTtsOverlaySize.MEDIUM -> Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onLocate)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = cleanChapterTitle ?: readerString("action_read_aloud", "Read aloud"),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val mediumSubtitle = chunkLabel
                            ?: progress.currentPositionLabel
                            ?: readerString("tts_device_voice", "Device voice")
                        if (mediumSubtitle.isNotBlank()) {
                            Text(
                                text = mediumSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        enabled = canSkipPrevious,
                        onClick = tts::skipPrevious,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = readerString("tts_previous_part", "Previous reading part"),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    SharedEpubTtsPlayButton(
                        isPlaying = isSpeaking,
                        isLoading = progress.currentChunk == null,
                        buttonSize = 48.dp,
                        filledSize = 44.dp,
                        iconSize = 22.dp,
                        ringStroke = 2.dp,
                        ringColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        pauseDescription = readerString("tts_pause_reading", "Pause reading"),
                        resumeDescription = readerString("tts_resume_reading", "Resume reading"),
                        onClick = { if (isSpeaking) tts.pause() else tts.resume() },
                    )
                    IconButton(
                        enabled = canSkipNext,
                        onClick = tts::skipNext,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = readerString("tts_next_part", "Next reading part"),
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
                                contentDescription = readerString("content_desc_expand_reading_controls", "Expand reading controls"),
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
                                contentDescription = readerString("content_desc_collapse_reading_controls", "Collapse reading controls"),
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
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
                            SharedEpubTtsChip(
                                text = readerString("tts_mode_device_native", "Device Native"),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            SharedEpubTtsChip(
                                text = voiceShortName,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxWidth = 90.dp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = tts::stop, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.Close,
                                readerString("content_desc_stop_tts", "Stop read aloud"),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (cleanChapterTitle != null || chunkLabel != null) {
                            Text(
                                cleanChapterTitle ?: readerString("label_reading", "Reading"),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            if (chunkLabel != null) {
                                Text(
                                    chunkLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = onLocate, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    SharedReaderIcons.PinDrop,
                                    readerString("tts_locate_part", "Locate current reading part"),
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
                                    readerString("content_desc_collapse_reading_controls", "Collapse reading controls"),
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
                                    readerString("content_desc_collapse_reading_controls", "Collapse reading controls"),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                enabled = canSkipPrevious,
                                onClick = tts::skipPrevious,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = readerString("tts_previous_part", "Previous reading part"),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            SharedEpubTtsPlayButton(
                                isPlaying = isSpeaking,
                                isLoading = progress.currentChunk == null,
                                buttonSize = 56.dp,
                                filledSize = 56.dp,
                                iconSize = 28.dp,
                                ringStroke = 3.dp,
                                ringColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                pauseDescription = readerString("tts_pause_reading", "Pause reading"),
                                resumeDescription = readerString("tts_resume_reading", "Resume reading"),
                                onClick = { if (isSpeaking) tts.pause() else tts.resume() },
                            )
                            IconButton(
                                enabled = canSkipNext,
                                onClick = tts::skipNext,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = readerString("tts_next_part", "Next reading part"),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SharedEpubTtsSliderBlock(
                                labelKey = "tts_speed_label",
                                labelFallback = "Speed %1\$s×",
                                value = rate,
                                range = 0.5f..3.0f,
                                resetDescription = readerString("content_desc_reset_speed", "Reset speed"),
                                onChange = { newRate ->
                                    rate = newRate
                                    tts.setSpeechParameters(newRate, pitch)
                                },
                                onReset = {
                                    rate = 1.0f
                                    tts.setSpeechParameters(1.0f, pitch)
                                },
                            )
                            SharedEpubTtsSliderBlock(
                                labelKey = "tts_pitch_label",
                                labelFallback = "Pitch %1\$s×",
                                value = pitch,
                                range = 0.5f..2.0f,
                                resetDescription = readerString("content_desc_reset_pitch", "Reset pitch"),
                                onChange = { newPitch ->
                                    pitch = newPitch
                                    tts.setSpeechParameters(rate, newPitch)
                                },
                                onReset = {
                                    pitch = 1.0f
                                    tts.setSpeechParameters(rate, 1.0f)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SharedMobileEpubCloudTtsControls(
    tts: SharedMobileEpubCloudTts,
    onLocate: () -> Unit,
    overlaySize: ReaderTtsOverlaySize = ReaderTtsOverlaySize.LARGE,
    onOverlaySizeChange: (ReaderTtsOverlaySize) -> Unit = {},
    modifier: Modifier = Modifier,
    // Android parity (TtsOverlayControls balance chip + session spend):
    // credited-cloud balance + accrued session cost. Null credits hides the
    // chip (matches the AI hub aiCredits=null badge rule); BYOK/native never
    // accrue session spend so the line stays hidden for them.
    credits: Int? = null,
    walletMicros: Long = 0L,
    walletMigrated: Boolean = false,
    isCloudSpendable: Boolean = true,
) {
    val cloudState = tts.state
    val progress = cloudState.progress
    val totalChunks = progress.chunks.size
    val chunkIndex = progress.currentChunkIndex
    val cleanChapterTitle = remember(progress.currentChunk?.chapterTitle) {
        progress.currentChunk?.chapterTitle
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.joinToString(" - ")
            ?.takeIf { it.isNotBlank() }
    }
    val chunkLabel = remember(chunkIndex, totalChunks) {
        if (chunkIndex in 0 until totalChunks) "Part ${chunkIndex + 1}/$totalChunks" else null
    }
    // Android parity (EpubReaderControls spend notices): the engine preserves
    // guard sentinels verbatim so the overlay can render countdown copy.
    val guardNotice = remember(cloudState.errorMessage) {
        parseSpendGuardSentinel(cloudState.errorMessage)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = if (overlaySize == ReaderTtsOverlaySize.MEDIUM) 560.dp else 400.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
    ) {
        // Android parity (TtsOverlayControls): size changes crossfade, not snap.
        AnimatedContent(
            targetState = overlaySize,
            transitionSpec = { fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200)) },
            label = "EpubCloudTtsOverlaySize"
        ) { size ->
            when (size) {
                ReaderTtsOverlaySize.SMALL -> Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.LARGE) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            readerString("content_desc_expand_reading_controls", "Expand reading controls"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.MEDIUM) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            readerString("content_desc_expand_reading_controls", "Expand reading controls"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SharedEpubTtsPlayButton(
                        isPlaying = cloudState.isPlaying,
                        isLoading = cloudState.isLoading,
                        buttonSize = 36.dp,
                        filledSize = 36.dp,
                        iconSize = 20.dp,
                        ringStroke = 2.dp,
                        ringColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                        pauseDescription = readerString("tts_cloud_pause_reading", "Pause cloud reading"),
                        resumeDescription = readerString("tts_cloud_resume_reading", "Resume cloud reading"),
                        onClick = { if (cloudState.isPlaying) tts.pause() else tts.resume() },
                    )
                }
                ReaderTtsOverlaySize.MEDIUM -> Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onLocate)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = cleanChapterTitle ?: readerString("action_read_aloud", "Read aloud"),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val mediumSubtitle = chunkLabel
                            ?: cloudState.errorMessage
                            ?: progress.currentPositionLabel
                            ?: "Cloud AI · ${cloudState.cacheSummary.currentVoiceLabel}"
                        if (mediumSubtitle.isNotBlank()) {
                            Text(
                                text = mediumSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (cloudState.errorMessage != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = tts::skipPrevious,
                        enabled = chunkIndex > 0 && totalChunks > 0 && !cloudState.isLoading,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = readerString("tts_cloud_previous_part", "Previous cloud reading part"),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    SharedEpubTtsPlayButton(
                        isPlaying = cloudState.isPlaying,
                        isLoading = cloudState.isLoading,
                        buttonSize = 48.dp,
                        filledSize = 44.dp,
                        iconSize = 22.dp,
                        ringStroke = 2.dp,
                        ringColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        pauseDescription = readerString("tts_cloud_pause_reading", "Pause cloud reading"),
                        resumeDescription = readerString("tts_cloud_resume_reading", "Resume cloud reading"),
                        onClick = { if (cloudState.isPlaying) tts.pause() else tts.resume() },
                    )
                    IconButton(
                        onClick = tts::skipNext,
                        enabled = chunkIndex in 0 until totalChunks - 1 && !cloudState.isLoading,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = readerString("tts_cloud_next_part", "Next cloud reading part"),
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
                                contentDescription = readerString("content_desc_expand_reading_controls", "Expand reading controls"),
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
                                contentDescription = readerString("content_desc_collapse_reading_controls", "Collapse reading controls"),
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
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
                            SharedEpubTtsChip(
                                text = readerString("tts_mode_cloud_ai", "Cloud AI"),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            if (isCloudSpendable && credits != null) {
                                SharedEpubTtsChip(
                                    text = spendableDisplayText(credits, walletMicros, walletMigrated),
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = tts::stop, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.Close,
                                readerString("content_desc_stop_tts", "Stop read aloud"),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (guardNotice != null && isCloudSpendable) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (guardNotice.first == "RATE_LIMITED") {
                                    readerString(
                                        "tts_notice_rate_limited",
                                        "Slowing down — retrying in %1\$s…",
                                        formatSpendGuardCountdown(guardNotice.second),
                                    )
                                } else {
                                    readerString(
                                        "tts_notice_spend_limit",
                                        "Daily cap reached — resets in %1\$s",
                                        formatSpendGuardCountdown(guardNotice.second),
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    } else if (isCloudSpendable && walletMigrated && cloudState.cloudSessionSpendMicros > 0) {
                        Text(
                            text = readerString(
                                "tts_session_spend",
                                "This session %1\$s • ~\$0.04/min",
                                formatMicrosUsd(cloudState.cloudSessionSpendMicros),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Android parity: a single 6dp rhythm between the header,
                    // notice/spend line, chapter row, and controls — the old
                    // 8dp spacers on every branch wasted vertical space.
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (cleanChapterTitle != null || chunkLabel != null) {
                            Text(
                                cleanChapterTitle ?: readerString("label_reading", "Reading"),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            if (chunkLabel != null) {
                                Text(
                                    chunkLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = onLocate, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    SharedReaderIcons.PinDrop,
                                    readerString("tts_cloud_locate_part", "Locate cloud reading part"),
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
                                    readerString("content_desc_collapse_reading_controls", "Collapse reading controls"),
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
                                    readerString("content_desc_collapse_reading_controls", "Collapse reading controls"),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // No speed/pitch sliders: the cloud engine (AVAudioPlayer
                    // chunk playback) exposes no rate API, unlike the local
                    // AVSpeech engine. Android's shared overlay shows them
                    // because ExoPlayer applies speed to both paths.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = tts::skipPrevious,
                            enabled = chunkIndex > 0 && totalChunks > 0 && !cloudState.isLoading,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = readerString("tts_cloud_previous_part", "Previous cloud reading part"),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        SharedEpubTtsPlayButton(
                            isPlaying = cloudState.isPlaying,
                            isLoading = cloudState.isLoading,
                            buttonSize = 56.dp,
                            filledSize = 56.dp,
                            iconSize = 28.dp,
                            ringStroke = 3.dp,
                            ringColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            pauseDescription = readerString("tts_cloud_pause_reading", "Pause cloud reading"),
                            resumeDescription = readerString("tts_cloud_resume_reading", "Resume cloud reading"),
                            onClick = { if (cloudState.isPlaying) tts.pause() else tts.resume() },
                        )
                        IconButton(
                            onClick = tts::skipNext,
                            enabled = chunkIndex in 0 until totalChunks - 1 && !cloudState.isLoading,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = readerString("tts_cloud_next_part", "Next cloud reading part"),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Android benchmark (TtsOverlayControls mode/voice/balance chips):
 * 8dp label chip. The voice chip caps at 90dp so a long voice name never
 * pushes the close button off the card.
 */
@Composable
private fun SharedEpubTtsChip(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    maxWidth: androidx.compose.ui.unit.Dp? = null,
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Android benchmark (TtsOverlayControls play buttons): tinted
 * FilledIconButton with a loading ring sized to the variant
 * (SMALL 36, MEDIUM 48/44, LARGE 56).
 */
@Composable
private fun SharedEpubTtsPlayButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    buttonSize: androidx.compose.ui.unit.Dp,
    filledSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    ringStroke: androidx.compose.ui.unit.Dp,
    ringColor: androidx.compose.ui.graphics.Color,
    pauseDescription: String,
    resumeDescription: String,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.size(buttonSize), contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(filledSize),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) pauseDescription else resumeDescription,
                modifier = Modifier.size(iconSize)
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(buttonSize),
                color = ringColor,
                strokeWidth = ringStroke
            )
        }
    }
}

/**
 * Android benchmark (TtsOverlayControls speed/pitch blocks): value + reset
 * header with -/slider/+ stepper rows and the 2dp custom track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedEpubTtsSliderBlock(
    labelKey: String,
    labelFallback: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    resetDescription: String,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val step = { delta: Float ->
        onChange((((value * 10f).roundToInt() / 10f) + delta).coerceIn(range.start, range.endInclusive))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                readerString(labelKey, labelFallback, ((value * 10f).roundToInt() / 10f).toString()),
                style = MaterialTheme.typography.labelMedium
            )
            IconButton(onClick = onReset, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Refresh, resetDescription, modifier = Modifier.size(16.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { step(-0.1f) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                modifier = Modifier.weight(1f).height(20.dp),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                },
                track = { sliderState ->
                    val span = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                    val fraction = if (span == 0f) 0f else {
                        ((sliderState.value - sliderState.valueRange.start) / span).coerceIn(0f, 1f)
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
            IconButton(onClick = { step(0.1f) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun sharedMobileEpubVoiceQualityLabel(tier: SharedMobileEpubVoiceQuality): String =
    when (tier) {
        SharedMobileEpubVoiceQuality.STANDARD -> readerString("tts_voice_quality_standard", "Standard")
        SharedMobileEpubVoiceQuality.ENHANCED -> readerString("tts_voice_quality_enhanced", "Enhanced")
        SharedMobileEpubVoiceQuality.PREMIUM -> readerString("tts_voice_quality_premium", "Premium")
    }

/** One selectable cloud voice. [fishReferenceId] is null for Gemini prebuilt voices. */
private data class SharedCloudVoiceRow(
    val id: String,
    val name: String,
    val description: String,
    val fishReferenceId: String?,
    val languages: List<String> = emptyList(),
    // Free pre-generated Fish preview audio; null = synthesize on demand.
    val sampleAudioUrl: String? = null,
)

/**
 * Optional diagnostic sink for the TTS settings panels.
 *
 * The panels are shared, but "where do these lines go" is a host concern: iOS streams them into
 * its device log, Android has Timber. Defaults to dropping them.
 */
typealias SharedTtsSettingsTrace = (String) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedMobileReaderTtsSettingsSheet(
    tts: SharedMobileEpubLocalTts,
    onDismiss: () -> Unit,
    cloudTts: SharedMobileEpubCloudTts? = null,
    cloudTtsModeEnabled: Boolean = false,
    onCloudTtsModeChange: (Boolean) -> Unit = {},
    cloudTtsVoiceId: String = DEFAULT_CLOUD_TTS_SPEAKER_ID,
    onCloudTtsVoiceChange: (String) -> Unit = {},
    onClearCloudTtsCache: () -> Unit = {},
    // Android benchmark (AiVoicesTab): the reader sheet lists the Fish
    // catalog (credited worker or Fish BYOK) with language filter +
    // favorites, falling back to the static Gemini list only when the host
    // expects no Fish catalog (Gemini BYOK backend).
    fishVoices: List<ReaderFishVoice> = emptyList(),
    expectFishVoices: Boolean = false,
    fishVoicesLoading: Boolean = false,
    favoriteCloudVoiceIds: Set<String> = emptySet(),
    onToggleFavoriteCloudVoice: (String) -> Unit = {},
    cloudVoiceLanguage: String? = null,
    onCloudVoiceLanguageChange: (String) -> Unit = {},
    onClearCloudVoiceSamples: () -> Unit = {},
    trace: SharedTtsSettingsTrace = {},
) {
    // A voice sample must not keep talking once the sheet is gone. Keyed on the engine instance
    // so a preview stops on close, on dismiss, and on navigation away — otherwise the sample
    // outlives the UI that started it. Deliberately `stopVoicePreview()` and not `stop()`:
    // playback is a separate concern that closing this sheet must not end.
    DisposableEffect(tts) {
        onDispose { tts.stopVoicePreview() }
    }
    // Android benchmark (TtsSettingsSheet): the engine pill drives the tab —
    // Cloud AI opens Cloud Voices, Device Native opens Device Voices.
    var selectedTtsTab by remember(cloudTtsModeEnabled) {
        mutableStateOf(if (cloudTtsModeEnabled) 0 else 1)
    }
    // Android benchmark (AndroidTtsSettings.kt:153/239/317/372/409/415): voice
    // selection and previews freeze while a session is active.
    val ttsVoiceLocked = tts.isVoiceSelectionLocked ||
        cloudTts?.state?.isLoading == true ||
        cloudTts?.state?.isPlaying == true
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Android benchmark (TtsSettingsSheet): 8dp section rhythm — the old
        // 16dp spacing pushed the voice list down and shrank it on phones.
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                readerString("tts_settings", "Text-to-Speech Settings"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            // Android benchmark: red banner while anything is playing.
            if (ttsVoiceLocked) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            readerString(
                                "tts_stop_to_change_settings",
                                "Please stop playback to change settings.",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            val cloud = cloudTts
            if (cloud == null) {
                // Android benchmark (OSS branch): device voices only, no
                // engine switcher or tabs.
                SharedTtsDeviceVoicesPanel(
                    tts = tts,
                    deviceModeActive = true,
                    locked = tts.isVoiceSelectionLocked,
                    trace = trace,
                )
            } else {
                Text(
                    readerString("tts_active_engine", "Active TTS Engine"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(24.dp),
                        )
                        .padding(4.dp),
                ) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (cloudTtsModeEnabled) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                                RoundedCornerShape(20.dp),
                            )
                            .clickable(enabled = !ttsVoiceLocked) {
                                onCloudTtsModeChange(true)
                                if (selectedTtsTab == 1) selectedTtsTab = 0
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            readerString("tts_mode_cloud_ai", "Cloud AI"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (cloudTtsModeEnabled) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (!cloudTtsModeEnabled) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                                RoundedCornerShape(20.dp),
                            )
                            .clickable(enabled = !ttsVoiceLocked) {
                                onCloudTtsModeChange(false)
                                if (selectedTtsTab != 1) selectedTtsTab = 1
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            readerString("tts_mode_device_native", "Device Native"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (!cloudTtsModeEnabled) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SharedTtsTabStrip(
                    selectedIndex = selectedTtsTab,
                    labels = listOf(
                        readerString("tts_tab_cloud_voices", "Cloud Voices"),
                        readerString("tts_tab_device_voices", "Device Voices"),
                        readerString("tts_tab_cloud_cache", "Cloud Cache"),
                    ),
                    onSelect = { selectedTtsTab = it },
                )
                Spacer(Modifier.height(8.dp))
                when (selectedTtsTab) {
                    0 -> SharedTtsCloudVoicesPanel(
                        trace = trace,
                        cloud = cloud,
                        cloudTtsVoiceId = cloudTtsVoiceId,
                        onCloudTtsVoiceChange = onCloudTtsVoiceChange,
                        fishVoices = fishVoices,
                        expectFishVoices = expectFishVoices,
                        fishVoicesLoading = fishVoicesLoading,
                        favoriteCloudVoiceIds = favoriteCloudVoiceIds,
                        onToggleFavoriteCloudVoice = onToggleFavoriteCloudVoice,
                        cloudVoiceLanguage = cloudVoiceLanguage,
                        onCloudVoiceLanguageChange = onCloudVoiceLanguageChange,
                        onClearCloudVoiceSamples = onClearCloudVoiceSamples,
                        previewSampleText = tts.previewSampleText,
                        cloudModeActive = cloudTtsModeEnabled,
                        locked = ttsVoiceLocked,
                    )
                    1 -> SharedTtsDeviceVoicesPanel(
                        tts = tts,
                        deviceModeActive = !cloudTtsModeEnabled,
                        locked = ttsVoiceLocked,
                        trace = trace,
                    )
                    else -> SharedTtsCloudCachePanel(
                        cloud = cloud,
                        cloudTtsVoiceId = cloudTtsVoiceId,
                        onClearCloudTtsCache = onClearCloudTtsCache,
                    )
                }
            }
        }
    }
}

/**
 * Android benchmark (`TabRow` look): three equal tabs with an indicator
 * under the selected one. Plain Row + clickable on purpose — full control
 * of padding so labels never truncate on narrow phones.
 */
@Composable
private fun SharedTtsTabStrip(
    selectedIndex: Int,
    labels: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Column(
                modifier = Modifier.weight(1f)
                    .padding(vertical = 12.dp)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selectedIndex == index) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().height(2.dp)
                        .background(
                            if (selectedIndex == index) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                        ),
                )
            }
        }
    }
}

/**
 * Android benchmark (`TtsCacheTab`): total badge, voice filter, chapter
 * rows with chunk counts, per-chapter delete, and a full-width
 * voice-scoped clear button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedTtsCloudCachePanel(
    cloud: SharedMobileEpubCloudTts,
    cloudTtsVoiceId: String,
    onClearCloudTtsCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cacheVoices = remember(cloud.state.cacheSummary) { cloud.cachedChapterVoices() }
    var selectedCacheVoice by remember(cloudTtsVoiceId, cacheVoices) {
        mutableStateOf(
            cloudTtsVoiceId.takeIf { it in cacheVoices }
                ?: cacheVoices.firstOrNull().orEmpty()
        )
    }
    var cacheRevision by remember { mutableIntStateOf(0) }
    val cacheChapters = remember(cloud.state.cacheSummary, selectedCacheVoice, cacheRevision) {
        if (selectedCacheVoice.isBlank()) emptyList()
        else cloud.cachedChapters(selectedCacheVoice)
    }
    val cacheTotalBytes = cacheChapters.sumOf { it.sizeBytes }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                readerString("tts_tab_cloud_cache", "Cloud Cache"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (cacheTotalBytes > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        formatReaderTtsBytes(cacheTotalBytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        if (cacheVoices.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                val filterInteraction = remember { MutableInteractionSource() }
                LaunchedEffect(filterInteraction) {
                    filterInteraction.interactions.collect {
                        if (it is PressInteraction.Release) filterMenuExpanded = true
                    }
                }
                OutlinedTextField(
                    value = selectedCacheVoice.ifBlank { cloudTtsVoiceId },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(readerString("tts_voice_filter", "Voice Filter")) },
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    },
                        modifier = Modifier.fillMaxWidth(),
                )
                SharedDropdownMenu(
                    expanded = filterMenuExpanded,
                    onDismissRequest = { filterMenuExpanded = false },
                ) {
                    cacheVoices.forEach { voiceId ->
                        DropdownMenuItem(
                            text = { Text(voiceId) },
                            trailingIcon = if (voiceId == selectedCacheVoice) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                            onClick = {
                                selectedCacheVoice = voiceId
                                filterMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }
        if (cacheChapters.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text(
                    readerString(
                        "tts_no_audio_cached_for_voice",
                        "No audio cached for this voice.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            ) {
                items(cacheChapters.size) { index ->
                    val chapter = cacheChapters[index]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "${chapter.chapterTitle} " + readerQuantityString(
                                    "tts_cache_chunk_count",
                                    chapter.chunkCount,
                                    "(%1\$d chunk)",
                                    "(%1\$d chunks)",
                                    chapter.chunkCount,
                                ),
                                fontWeight = FontWeight.Medium,
                            )
                            Text(formatReaderTtsBytes(chapter.sizeBytes))
                        }
                        Box(
                            modifier = Modifier.size(48.dp)
                                .clickable {
                                    cloud.deleteCachedChapter(chapter)
                                    cacheRevision++
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = readerString("action_delete", "Delete"),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    cloud.deleteCachedVoice(selectedCacheVoice)
                    cacheRevision++
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    readerString(
                        "tts_clear_cache_for_voice",
                        "Clear Cache for %1\$s",
                        selectedCacheVoice,
                    )
                )
            }
        }
    }
}

/**
 * Android benchmark (`AiVoicesTab`): header with Clear Samples, language
 * filter, and a bordered voice list. Fish rows key on referenceId; Gemini
 * rows stay static prebuilts. Selection/highlight only applies while the
 * cloud engine is active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedTtsCloudVoicesPanel(
    trace: SharedTtsSettingsTrace = {},
    cloud: SharedMobileEpubCloudTts,
    cloudTtsVoiceId: String,
    onCloudTtsVoiceChange: (String) -> Unit,
    fishVoices: List<ReaderFishVoice>,
    expectFishVoices: Boolean,
    fishVoicesLoading: Boolean,
    favoriteCloudVoiceIds: Set<String>,
    onToggleFavoriteCloudVoice: (String) -> Unit,
    cloudVoiceLanguage: String?,
    onCloudVoiceLanguageChange: (String) -> Unit,
    onClearCloudVoiceSamples: () -> Unit,
    previewSampleText: String,
    cloudModeActive: Boolean,
    locked: Boolean,
    modifier: Modifier = Modifier,
) {
    val cloudVoiceRows = remember(fishVoices, expectFishVoices) {
        if (expectFishVoices) {
            fishVoices.map {
                SharedCloudVoiceRow(
                    id = it.referenceId.ifBlank { it.id },
                    name = it.title.ifBlank { it.referenceId.ifBlank { it.id } },
                    description = it.description.ifBlank { it.referenceId.ifBlank { it.id } },
                    fishReferenceId = it.referenceId.ifBlank { it.id },
                    languages = it.languages,
                    sampleAudioUrl = it.sampleAudioUrl.ifBlank { null },
                )
            }
        } else {
            ReaderCloudTtsVoices.map {
                SharedCloudVoiceRow(it.id, it.name, it.description, null, emptyList(), null)
            }
        }
    }
    val cloudFavoritesLabel = readerString("tts_favorites", "Favorites")
    val cloudAllLanguagesLabel = readerString("filter_all", "All")
    val cloudLanguageOptions = remember(cloudVoiceRows, cloudFavoritesLabel, cloudAllLanguagesLabel) {
        listOf(cloudFavoritesLabel, cloudAllLanguagesLabel) +
            cloudVoiceRows.flatMap { it.languages }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val effectiveCloudLanguage = cloudVoiceLanguage.takeIf { it in cloudLanguageOptions } ?: cloudAllLanguagesLabel
    val showingCloudFavorites = effectiveCloudLanguage == cloudFavoritesLabel
    val filteredCloudVoiceRows = remember(cloudVoiceRows, effectiveCloudLanguage, showingCloudFavorites, favoriteCloudVoiceIds) {
        val base = when {
            showingCloudFavorites || effectiveCloudLanguage == cloudAllLanguagesLabel -> cloudVoiceRows
            else -> cloudVoiceRows.filter { effectiveCloudLanguage in it.languages }
        }
        if (showingCloudFavorites) base.filter { it.id in favoriteCloudVoiceIds } else base
    }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val sampleState = cloud.voiceSampleState
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                readerString("tts_select_cloud_voice", "Select High-Quality Cloud Voice"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (sampleState.cachedVoiceIds.isNotEmpty()) {
                TextButton(
                    onClick = onClearCloudVoiceSamples,
                    modifier = Modifier.heightIn(min = 24.dp),
                ) {
                    Text(
                        readerString("tts_clear_samples", "Clear Samples"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            // Android benchmark: the whole field opens the menu — a press
            // interaction beats an arrow-only target for small trailing icons.
            OutlinedTextField(
                value = effectiveCloudLanguage,
                onValueChange = {},
                readOnly = true,
                label = { Text(readerString("tts_language_filter", "Language Filter")) },
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                },
                enabled = !locked,
                modifier = Modifier.fillMaxWidth(),
            )
            // Transparent click layer: a readOnly OutlinedTextField consumes the tap itself, so
            // the clickable has to sit on top of it rather than wrap it.
            if (!locked) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable {
                            trace(
                                "cloud.languageFilter.tap expandedBefore=$languageMenuExpanded " +
                                    "locked=$locked options=${cloudLanguageOptions.size} " +
                                    "selected=$effectiveCloudLanguage"
                            )
                            languageMenuExpanded = true
                        }
                )
            }
            SharedDropdownMenu(
                expanded = languageMenuExpanded,
                onDismissRequest = { languageMenuExpanded = false },
            ) {
                // Composed only when the popup actually renders.
                trace("cloud.languageFilter.menuComposed options=${cloudLanguageOptions.size}")
                cloudLanguageOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        leadingIcon = if (option == cloudFavoritesLabel) {
                            {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                        trailingIcon = if (option == effectiveCloudLanguage) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                        onClick = {
                            onCloudVoiceLanguageChange(option)
                            languageMenuExpanded = false
                        },
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = 300.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
        ) {
            if (fishVoicesLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
            if (!fishVoicesLoading && expectFishVoices && cloudVoiceRows.isEmpty()) {
                item {
                    Text(
                        readerString(
                            "tts_no_cloud_voices",
                            "No cloud voices available right now. Check your connection or API key.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (!fishVoicesLoading && filteredCloudVoiceRows.isEmpty() &&
                !(expectFishVoices && cloudVoiceRows.isEmpty())
            ) {
                item {
                    Text(
                        if (showingCloudFavorites) {
                            readerString(
                                "tts_no_favorite_voices",
                                "No favorite voices yet. Tap the star on any voice to add it here.",
                            )
                        } else {
                            readerString(
                                "tts_no_voices_for_language",
                                "No voices found for this language.",
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(filteredCloudVoiceRows.size) { index ->
                val voice = filteredCloudVoiceRows[index]
                val isSelected = cloudTtsVoiceId == voice.id
                val isFavorite = voice.id in favoriteCloudVoiceIds
                // Plain Row + Box click targets on purpose: they use the
                // same foundation clickable as the working tab strip/pill.
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            if (isSelected && cloudModeActive) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else MaterialTheme.colorScheme.surface,
                        )
                        .clickable(enabled = !locked && cloudModeActive) {
                            cloud.setVoice(voice.id)
                            onCloudTtsVoiceChange(voice.id)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.padding(start = 16.dp).size(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected && cloudModeActive) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            voice.name,
                            fontWeight = if (isSelected && cloudModeActive) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            voice.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier.size(48.dp)
                            .clickable { onToggleFavoriteCloudVoice(voice.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = readerString(
                                if (isFavorite) "tts_remove_favorite" else "tts_add_favorite",
                                if (isFavorite) "Remove from favorites" else "Add to favorites",
                            ),
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!locked) {
                        Box(
                            modifier = Modifier.size(48.dp)
                                .clickable {
                                    cloud.playOrStopVoiceSample(
                                        voice.id,
                                        fishReferenceId = voice.fishReferenceId,
                                        sampleAudioUrl = voice.sampleAudioUrl,
                                        sampleText = previewSampleText,
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                sampleState.loadingVoiceId == voice.id ->
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                sampleState.playingVoiceId == voice.id ->
                                    Icon(
                                        Icons.Default.Stop,
                                        contentDescription = readerString("tts_stop_preview", "Stop preview"),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                voice.id in sampleState.cachedVoiceIds ->
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = readerString("tts_preview_voice", "Preview %1\$s", voice.name),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                else ->
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = readerString("tts_preview_voice", "Preview %1\$s", voice.name),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

/**
 * Android benchmark (`DeviceVoicesTab`): system-default card, preview-text
 * editor, language filter, and voice list. The language filter is transient
 * (Android never persists it); favorites are shared with the cloud tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedTtsDeviceVoicesPanel(
    tts: SharedMobileEpubLocalTts,
    deviceModeActive: Boolean,
    locked: Boolean,
    trace: SharedTtsSettingsTrace = {},
    modifier: Modifier = Modifier,
) {
    val allLanguagesLabel = readerString("filter_all", "All")
    val favoritesLabel = readerString("tts_favorites", "Favorites")
    var sampleDraft by remember { mutableStateOf(tts.previewSampleText) }
    var selectedLanguage by remember { mutableStateOf(allLanguagesLabel) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val voiceLanguages = remember(tts.availableVoices, allLanguagesLabel, favoritesLabel) {
        listOf(favoritesLabel, allLanguagesLabel) +
            sharedMobileEpubVoiceLanguageOptions(tts.availableVoices, allLanguagesLabel)
                .filter { it != allLanguagesLabel }
    }
    val effectiveLanguage = selectedLanguage.takeIf { it in voiceLanguages } ?: allLanguagesLabel
    val showingFavorites = effectiveLanguage == favoritesLabel
    val favoriteIds = tts.favoriteVoiceIdentifiers
    val filteredVoices = remember(tts.availableVoices, effectiveLanguage, showingFavorites, favoriteIds) {
        val base = when {
            showingFavorites || effectiveLanguage == allLanguagesLabel -> tts.availableVoices
            else -> tts.availableVoices.filter { it.language == effectiveLanguage }
        }
        if (showingFavorites) base.filter { it.identifier in favoriteIds } else base
    }
    val systemDefaultSelected = deviceModeActive && tts.selectedVoiceIdentifier == null
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (systemDefaultSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().clickable(enabled = !locked && deviceModeActive) {
                tts.setVoice(null)
            },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = if (systemDefaultSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        readerString("tts_system_default_voice", "System Default Voice"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        readerString("tts_uses_device_settings", "Uses device settings"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (systemDefaultSelected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                readerString("tts_preview_text_label", "Voice preview text"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(
                onClick = {
                    tts.setPreviewSampleText("")
                    sampleDraft = tts.previewSampleText
                },
                enabled = tts.previewSampleText != SHARED_MOBILE_TTS_SAMPLE_DEFAULT ||
                    sampleDraft != SHARED_MOBILE_TTS_SAMPLE_DEFAULT,
            ) { Text(readerString("action_reset", "Reset")) }
        }
        OutlinedTextField(
            value = sampleDraft,
            onValueChange = { next ->
                sampleDraft = next.take(SHARED_MOBILE_TTS_SAMPLE_MAX_LENGTH)
                tts.setPreviewSampleText(sampleDraft)
            },
            placeholder = { Text(SHARED_MOBILE_TTS_SAMPLE_DEFAULT) },
            supportingText = { Text("${sampleDraft.length}/${SHARED_MOBILE_TTS_SAMPLE_MAX_LENGTH}") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = effectiveLanguage,
                onValueChange = {},
                readOnly = true,
                label = { Text(readerString("tts_language_filter", "Language Filter")) },
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                },
                enabled = !locked,
                modifier = Modifier.fillMaxWidth(),
            )
            // Transparent click layer over the whole field.
            //
            // A readOnly OutlinedTextField still installs its own pointer input (focus and text
            // selection), so it consumes the tap and a `clickable` on the wrapper Box never sees
            // it — the menu silently never opened. Drawing the field non-interactively and
            // putting the clickable on top keeps the exact visuals and makes the whole field the
            // target, matching the Android benchmark behaviour.
            if (!locked) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable {
                            trace(
                                "device.languageFilter.tap expandedBefore=$languageMenuExpanded " +
                                    "locked=$locked options=${voiceLanguages.size} " +
                                    "selected=$effectiveLanguage"
                            )
                            languageMenuExpanded = true
                        }
                )
            }
            SharedDropdownMenu(
                expanded = languageMenuExpanded,
                onDismissRequest = { languageMenuExpanded = false },
            ) {
                // Composed only when the popup actually renders. A tap with no matching
                // "menuComposed" line means the Popup is not being presented at all
                // (DropdownMenu inside a ModalBottomSheet is unreliable on iOS/CMP).
                trace("device.languageFilter.menuComposed options=${voiceLanguages.size}")
                voiceLanguages.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language) },
                        leadingIcon = if (language == favoritesLabel) {
                            {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                        trailingIcon = if (language == effectiveLanguage) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                        onClick = {
                            selectedLanguage = language
                            languageMenuExpanded = false
                        },
                    )
                }
            }
        }
        if (showingFavorites && filteredVoices.isEmpty()) {
            Text(
                readerString(
                    "tts_no_favorite_voices",
                    "No favorite voices yet. Tap the star on any voice to add it here.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = 200.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        ) {
            items(filteredVoices.size) { index ->
                val voice = filteredVoices[index]
                val isSelected = deviceModeActive && voice.identifier == tts.selectedVoiceIdentifier
                val isFavorite = voice.identifier in favoriteIds
                // Plain Row + Box click targets on purpose: they use the
                // same foundation clickable as the working tab strip/pill.
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surface,
                        )
                        .clickable(enabled = !locked && deviceModeActive) {
                            tts.setVoice(voice.identifier)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.padding(start = 16.dp).size(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            voice.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            sharedMobileEpubVoiceSubtitle(
                                voice,
                                sharedMobileEpubVoiceQualityLabel(voice.quality),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier.size(48.dp)
                            .clickable { tts.toggleFavoriteVoice(voice.identifier) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = if (isFavorite) {
                                readerString("tts_remove_favorite", "Remove from favorites")
                            } else {
                                readerString("tts_add_favorite", "Add to favorites")
                            },
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!locked) {
                        Box(
                            modifier = Modifier.size(48.dp)
                                .clickable { tts.previewVoice(voice.identifier) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = readerString("tts_play_sample", "Play Sample"),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
internal fun SharedMobileEpubBookReplacementControls(
    preferences: ReaderBookReplacementPreferences,
    bookId: String,
    onPreferencesChange: (ReaderBookReplacementPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    val rules = preferences.rulesForFile(bookId)
    var editingRuleId by remember(bookId) { mutableStateOf<String?>(null) }
    var isAdding by remember(bookId) { mutableStateOf(false) }
    val editingRule = editingRuleId?.let { id -> rules.firstOrNull { it.id == id } }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            readerString("menu_book_word_replacements", "Book Word Replacements"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Replace visible text in this book only. Locations, bookmarks, and the original EPUB remain unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { isAdding = true; editingRuleId = null }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(readerString("book_replacements_add_rule", "Add rule"))
        }
        if (isAdding || editingRule != null) {
            SharedMobileEpubBookReplacementEditor(
                seed = editingRule,
                newRuleId = "book_${currentTimestamp()}_${rules.size}",
                onCancel = { isAdding = false; editingRuleId = null },
                onSave = { saved ->
                    val updated = if (editingRule == null) rules + saved else rules.map { if (it.id == editingRule.id) saved else it }
                    onPreferencesChange(preferences.withFileRules(bookId, updated))
                    isAdding = false
                    editingRuleId = null
                }
            )
        }
        if (rules.isEmpty()) {
                Text(
                    readerString("tts_replacements_empty", "No replacements for this book yet."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        } else {
            rules.forEach { rule ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${rule.from} → ${
                                    rule.to.ifBlank {
                                        readerString("tts_replacements_remove_marker", "(remove)")
                                    }
                                }",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                buildList {
                                    add(if (rule.isRegex) "Regex" else "Plain text")
                                    if (rule.wholeWord) add("whole word")
                                    if (rule.matchCase) add("case-sensitive")
                                }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { enabled ->
                                onPreferencesChange(
                                    preferences.withFileRules(bookId, rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                                )
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editingRuleId = rule.id; isAdding = false }) {
                Text(readerString("action_edit", "Edit"))
            }
                        TextButton(onClick = {
                            onPreferencesChange(preferences.withFileRules(bookId, rules.filterNot { it.id == rule.id }))
                        }) { Text(readerString("action_delete", "Delete")) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
internal fun SharedMobileEpubBookReplacementEditor(
    seed: ReaderWordReplacementRule?,
    newRuleId: String,
    onCancel: () -> Unit,
    onSave: (ReaderWordReplacementRule) -> Unit
) {
    val ruleId = seed?.id ?: newRuleId
    var from by remember(ruleId) { mutableStateOf(seed?.from.orEmpty()) }
    var to by remember(ruleId) { mutableStateOf(seed?.to.orEmpty()) }
    var enabled by remember(ruleId) { mutableStateOf(seed?.enabled ?: true) }
    var isRegex by remember(ruleId) { mutableStateOf(seed?.isRegex ?: false) }
    var wholeWord by remember(ruleId) { mutableStateOf(seed?.wholeWord ?: true) }
    var matchCase by remember(ruleId) { mutableStateOf(seed?.matchCase ?: false) }
    val draft = ReaderWordReplacementRule(ruleId, from, to, enabled, isRegex, matchCase, wholeWord)
    val validation = ReaderWordReplacementEngine.validate(draft)

    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (seed == null) "New replacement" else "Edit replacement", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(from, { from = it }, label = { Text(readerString("tts_replacements_label_replace", "Replace")) }, isError = !validation.isValid, modifier = Modifier.fillMaxWidth())
            validation.message?.takeIf { !validation.isValid }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(to, { to = it }, label = { Text(readerString("book_replacements_label_with", "With")) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(enabled, { enabled = !enabled }, label = { Text(readerString("tts_replacements_chip_enabled", "Enabled")) })
                FilterChip(isRegex, { isRegex = !isRegex }, label = { Text(readerString("tts_replacements_chip_regex", "Regex")) })
                FilterChip(wholeWord, { wholeWord = !wholeWord }, label = { Text(readerString("tts_replacements_chip_whole_word", "Whole word")) })
                FilterChip(matchCase, { matchCase = !matchCase }, label = { Text(readerString("tts_replacements_chip_match_case", "Match case")) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(readerString("action_cancel", "Cancel")) }
            TextButton(enabled = validation.isValid, onClick = { onSave(draft) }) {
                Text(readerString("action_save", "Save"))
            }
            }
        }
    }
}
