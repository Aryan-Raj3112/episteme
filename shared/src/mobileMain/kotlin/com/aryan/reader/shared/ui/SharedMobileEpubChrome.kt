package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.ReaderCloudTtsVoices
import com.aryan.reader.shared.ReaderTtsOverlaySize
import com.aryan.reader.shared.formatReaderTtsBytes
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
}

@Composable
internal fun SharedMobileEpubLoading(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(label)
        }
    }
}

@Composable
internal fun SharedMobileEpubError(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(28.dp)
        ) {
            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(48.dp))
            Text(readerString("epub_open_failed", "Could not open EPUB"), style = MaterialTheme.typography.titleMedium)
            Text(message, color = MaterialTheme.colorScheme.error)
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
            Box {
                IconButton(
                    onClick = { onShowMoreChange(true) },
                    modifier = Modifier.testTag(SharedMobileEpubAxTags.MORE).semantics { contentDescription = moreOptionsContentDescription }
                ) { Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                DropdownMenu(expanded = showMore, onDismissRequest = { onShowMoreChange(false) }) {
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
    else -> Icons.Default.GraphicEq
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
    val progress = tts.progress
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = if (overlaySize == ReaderTtsOverlaySize.MEDIUM) 560.dp else 400.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        // Android parity (TtsOverlayControls): size changes crossfade, not snap.
        AnimatedContent(
            targetState = overlaySize,
            transitionSpec = { fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200)) },
            label = "EpubTtsOverlaySize"
        ) { size ->
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (size != ReaderTtsOverlaySize.SMALL) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable(enabled = progress.currentChunk != null, onClick = onLocate)
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            progress.currentChunk?.chapterTitle?.ifBlank { "Read aloud" } ?: "Preparing read aloud…",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            progress.currentPositionLabel ?: "Device voice",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                if (size != ReaderTtsOverlaySize.SMALL) {
                    IconButton(onClick = tts::skipPrevious, enabled = progress.currentChunkIndex > 0) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = readerString("tts_previous_part", "Previous reading part"))
                    }
                }
                IconButton(
                    onClick = {
                        if (tts.state == SharedMobileEpubLocalTtsState.SPEAKING) tts.pause() else tts.resume()
                    }
                ) {
                    Icon(
                        if (tts.state == SharedMobileEpubLocalTtsState.SPEAKING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (tts.state == SharedMobileEpubLocalTtsState.SPEAKING) "Pause reading" else "Resume reading"
                    )
                }
                if (size != ReaderTtsOverlaySize.SMALL) {
                    IconButton(
                        onClick = tts::skipNext,
                        enabled = progress.currentChunkIndex in 0 until progress.chunks.lastIndex
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = readerString("tts_next_part", "Next reading part"))
                    }
                }
                if (size == ReaderTtsOverlaySize.LARGE) {
                    IconButton(onClick = onLocate, enabled = progress.currentChunk != null) {
                        Icon(SharedReaderIcons.PinDrop, contentDescription = readerString("tts_locate_part", "Locate current reading part"))
                    }
                }
                if (size == ReaderTtsOverlaySize.LARGE) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = readerString("menu_tts_voice_settings", "TTS voice settings"))
                    }
                }
                SharedMobileEpubTtsOverlaySizeControls(size, onOverlaySizeChange)
                IconButton(onClick = tts::stop) {
                    Icon(Icons.Default.Close, contentDescription = readerString("menu_stop_reading", "Stop reading"), tint = MaterialTheme.colorScheme.error)
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
) {
    val cloudState = tts.state
    val progress = cloudState.progress
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = if (overlaySize == ReaderTtsOverlaySize.MEDIUM) 560.dp else 400.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        // Android parity (TtsOverlayControls): size changes crossfade, not snap.
        AnimatedContent(
            targetState = overlaySize,
            transitionSpec = { fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200)) },
            label = "EpubCloudTtsOverlaySize"
        ) { size ->
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (size != ReaderTtsOverlaySize.SMALL) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable(enabled = progress.currentChunk != null, onClick = onLocate)
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            progress.currentChunk?.chapterTitle?.ifBlank { "Cloud read aloud" } ?: "Preparing cloud audio…",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            cloudState.errorMessage ?: progress.currentPositionLabel ?: "Cloud AI · ${cloudState.cacheSummary.currentVoiceLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (cloudState.errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (size != ReaderTtsOverlaySize.SMALL) {
                    IconButton(onClick = tts::skipPrevious, enabled = progress.currentChunkIndex > 0 && !cloudState.isLoading) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = readerString("tts_cloud_previous_part", "Previous cloud reading part"))
                    }
                }
                IconButton(
                    onClick = { if (cloudState.isPlaying) tts.pause() else tts.resume() },
                    enabled = cloudState.isLoading || cloudState.isPlaying || cloudState.isPaused,
                ) {
                    Icon(
                        if (cloudState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (cloudState.isPlaying) "Pause cloud reading" else "Resume cloud reading",
                    )
                }
                if (size != ReaderTtsOverlaySize.SMALL) {
                    IconButton(onClick = tts::skipNext, enabled = progress.currentChunkIndex in 0 until progress.chunks.lastIndex && !cloudState.isLoading) {
                        Icon(Icons.Default.SkipNext, contentDescription = readerString("tts_cloud_next_part", "Next cloud reading part"))
                    }
                }
                if (size == ReaderTtsOverlaySize.LARGE) {
                    IconButton(onClick = onLocate, enabled = progress.currentChunk != null) {
                        Icon(SharedReaderIcons.PinDrop, contentDescription = readerString("tts_cloud_locate_part", "Locate cloud reading part"))
                    }
                }
                SharedMobileEpubTtsOverlaySizeControls(size, onOverlaySizeChange)
                IconButton(onClick = tts::stop) {
                    Icon(Icons.Default.Close, contentDescription = readerString("tts_cloud_stop_reading", "Stop cloud reading"), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * Android exposes explicit size choices rather than a cyclic toggle. Keeping
 * all three choices visible also makes the compact state recoverable without
 * requiring a particular gesture, which is important on iOS where the reader
 * chrome may be hidden while TTS is active.
 */
@Composable
private fun SharedMobileEpubTtsOverlaySizeControls(
    size: ReaderTtsOverlaySize,
    onSizeChange: (ReaderTtsOverlaySize) -> Unit,
) {
    when (size) {
        ReaderTtsOverlaySize.LARGE -> {
            IconButton(
                onClick = { onSizeChange(ReaderTtsOverlaySize.MEDIUM) },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowDown, readerString("content_desc_collapse_reading_controls", "Collapse reading controls"), modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onSizeChange(ReaderTtsOverlaySize.SMALL) },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowRight, readerString("content_desc_collapse_reading_controls", "Collapse reading controls"), modifier = Modifier.size(18.dp))
            }
        }
        ReaderTtsOverlaySize.MEDIUM -> {
            IconButton(
                onClick = { onSizeChange(ReaderTtsOverlaySize.LARGE) },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowUp, readerString("content_desc_expand_reading_controls", "Expand reading controls"), modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onSizeChange(ReaderTtsOverlaySize.SMALL) },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowRight, readerString("content_desc_collapse_reading_controls", "Collapse reading controls"), modifier = Modifier.size(18.dp))
            }
        }
        ReaderTtsOverlaySize.SMALL -> {
            IconButton(
                onClick = { onSizeChange(ReaderTtsOverlaySize.LARGE) },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowUp, readerString("content_desc_expand_reading_controls", "Expand reading controls"), modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onSizeChange(ReaderTtsOverlaySize.MEDIUM) },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, readerString("content_desc_expand_reading_controls", "Expand reading controls"), modifier = Modifier.size(18.dp))
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
) {
    var rate by remember(tts.speechRate) { mutableStateOf(tts.speechRate) }
    var pitch by remember(tts.speechPitch) { mutableStateOf(tts.speechPitch) }
    var showVoices by remember { mutableStateOf(false) }
    var showCloudVoices by remember { mutableStateOf(false) }
    val selectedVoice = tts.availableVoices.firstOrNull { it.identifier == tts.selectedVoiceIdentifier }
    // Android benchmark (AndroidTtsSettings.kt:298-307/359-392): language
    // filter over the device voice list, plus a shared quality filter fed by
    // Android Voice.getQuality and iOS AVSpeechSynthesisVoice.quality.
    val allLanguagesLabel = readerString("filter_all", "All")
    val allQualitiesLabel = readerString("filter_all", "All")
    var selectedLanguage by remember { mutableStateOf(allLanguagesLabel) }
    var selectedQuality by remember { mutableStateOf<SharedMobileEpubVoiceQuality?>(null) }
    val voiceLanguages = remember(tts.availableVoices, allLanguagesLabel) {
        sharedMobileEpubVoiceLanguageOptions(tts.availableVoices, allLanguagesLabel)
    }
    val presentQualities = remember(tts.availableVoices) {
        sharedMobileEpubVoiceQualityOptions(tts.availableVoices)
    }
    // The Android engine binds async, so a stale selection must fall back to
    // "All" instead of filtering everything out.
    val effectiveLanguage = selectedLanguage.takeIf { it in voiceLanguages } ?: allLanguagesLabel
    val effectiveQuality = selectedQuality.takeIf { it in presentQualities }
    var favoritesOnly by remember { mutableStateOf(false) }
    val favoriteIds = tts.favoriteVoiceIdentifiers
    val filteredVoices = remember(tts.availableVoices, effectiveLanguage, effectiveQuality, favoritesOnly, favoriteIds) {
        tts.availableVoices.filteredForTtsDisplay(
            effectiveLanguage,
            allLanguagesLabel,
            effectiveQuality,
            favoritesOnly,
            favoriteIds,
        )
    }
    // Android benchmark (AndroidTtsSettings.kt:153/239/317/372/409/415): voice
    // selection and previews freeze while a session is active.
    val ttsVoiceLocked = tts.isSessionActive ||
        cloudTts?.state?.isLoading == true ||
        cloudTts?.state?.isPlaying == true
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                readerString("menu_tts_voice_settings", "TTS Voice Settings"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            cloudTts?.let { cloud ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(readerString("tts_cloud_ai_reading", "Cloud AI reading"), fontWeight = FontWeight.SemiBold)
                        Text(
                            if (cloudTtsModeEnabled) {
                                readerString(
                                    "tts_cloud_mode_summary",
                                    "Gemini Live · %1\$s",
                                    cloud.state.cacheSummary.currentVoiceLabel,
                                )
                            } else {
                                readerString("tts_use_device_speech", "Use device speech")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = cloudTtsModeEnabled,
                        enabled = !tts.isSessionActive && !cloud.state.isLoading && !cloud.state.isPlaying,
                        onCheckedChange = onCloudTtsModeChange,
                    )
                }
                if (cloudTtsModeEnabled) {
                    Box {
                        val selectedCloudVoice = ReaderCloudTtsVoices.firstOrNull { it.id == cloudTtsVoiceId }
                            ?: ReaderCloudTtsVoices.firstOrNull()
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { showCloudVoices = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(selectedCloudVoice?.name ?: cloudTtsVoiceId, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        selectedCloudVoice?.description
                                            ?: readerString("tts_cloud_ai_reading", "Cloud AI reading"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = readerString(
                                        "tts_choose_cloud_voice",
                                        "Choose cloud voice",
                                    ),
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showCloudVoices,
                            onDismissRequest = { showCloudVoices = false },
                            modifier = Modifier.heightIn(max = 360.dp),
                        ) {
                            ReaderCloudTtsVoices.forEach { voice ->
                                val sampleState = cloud.voiceSampleState
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(voice.name)
                                            Text(voice.description, style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    enabled = !ttsVoiceLocked,
                                    onClick = {
                                        cloud.setVoice(voice.id)
                                        onCloudTtsVoiceChange(voice.id)
                                        showCloudVoices = false
                                    },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (voice.id == cloudTtsVoiceId) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                            // Android benchmark (AiVoicesTab): per-voice
                                            // sample preview with loading/playing states.
                                            IconButton(
                                                onClick = { cloud.playOrStopVoiceSample(voice.id) },
                                                enabled = !ttsVoiceLocked,
                                            ) {
                                                when {
                                                    sampleState.loadingVoiceId == voice.id ->
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(20.dp),
                                                            strokeWidth = 2.dp
                                                        )
                                                    sampleState.playingVoiceId == voice.id ->
                                                        Icon(
                                                            Icons.Default.Stop,
                                                            contentDescription = readerString("tts_stop_preview", "Stop preview"),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    voice.id in sampleState.cachedVoiceIds ->
                                                        Icon(
                                                            Icons.Default.PlayCircle,
                                                            contentDescription = readerString("tts_preview_voice", "Preview %1\$s", voice.name),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    else ->
                                                        Icon(
                                                            Icons.Default.PlayArrow,
                                                            contentDescription = readerString("tts_preview_voice", "Preview %1\$s", voice.name),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    // Android benchmark (TtsCacheTab): speaker filter with a
                    // per-chapter list (counts + delete) and voice-scoped clear.
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            readerString("tts_tab_cloud_cache", "Cloud audio cache"),
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
                    if (cacheVoices.size > 1) {
                        var showCacheVoices by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { showCacheVoices = true },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        selectedCacheVoice.ifBlank { cloudTtsVoiceId },
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = readerString("tts_filter_cached_voice", "Filter cached voice"),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showCacheVoices,
                                onDismissRequest = { showCacheVoices = false },
                                modifier = Modifier.heightIn(max = 360.dp),
                            ) {
                                cacheVoices.forEach { voiceId ->
                                    DropdownMenuItem(
                                        text = { Text(voiceId) },
                                        trailingIcon = if (voiceId == selectedCacheVoice) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null,
                                        onClick = {
                                            selectedCacheVoice = voiceId
                                            showCacheVoices = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (cacheChapters.isEmpty()) {
                        Text(
                            readerString("tts_no_audio_cached_for_voice", "No cached audio for this voice"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(12.dp)
                                ),
                        ) {
                            cacheChapters.forEach { chapter ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            "${chapter.chapterTitle} (${chapter.chunkCount})",
                                            fontWeight = FontWeight.Medium,
                                        )
                                    },
                                    supportingContent = {
                                        Text(formatReaderTtsBytes(chapter.sizeBytes))
                                    },
                                    trailingContent = {
                                        IconButton(onClick = {
                                            cloud.deleteCachedChapter(chapter)
                                            cacheRevision++
                                        }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = readerString("action_delete", "Delete"),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    },
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                cloud.deleteCachedVoice(selectedCacheVoice)
                                cacheRevision++
                            }
                        ) {
                            Text(
                                readerString(
                                    "tts_clear_cache_for_voice",
                                    "Clear cache for %1\$s",
                                    selectedCacheVoice,
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (cloud.state.cacheSummary.hasCachedAudio) {
                        TextButton(onClick = onClearCloudTtsCache) {
                            Text(readerString("tts_clear_cached_cloud_audio", "Clear cached cloud audio"))
                        }
                    }
                }
                HorizontalDivider()
            }
            // Local TTS only: custom preview text for voice samples. Editing
            // is harmless during a session (only previewVoice reads it), so
            // unlike voice selection it is never locked.
            var sampleDraft by remember { mutableStateOf(tts.previewSampleText) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(readerString("tts_preview_text_label", "Voice preview text"), fontWeight = FontWeight.SemiBold)
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
            }
            Box {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !ttsVoiceLocked) { showVoices = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                selectedVoice?.name ?: readerString("tts_system_default", "System default"),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                selectedVoice?.let { voice ->
                                    sharedMobileEpubVoiceSubtitle(
                                        voice,
                                        sharedMobileEpubVoiceQualityLabel(voice.quality),
                                    )
                                } ?: readerString("tts_device_voice_summary", "Uses the voice selected in system settings"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = readerString("tts_choose_voice", "Choose voice"),
                        )
                    }
                }
                DropdownMenu(
                    expanded = showVoices,
                    onDismissRequest = { showVoices = false },
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (favoriteIds.isNotEmpty()) {
                                    readerString(
                                        "tts_favorites_only_count",
                                        "Favorites only (%1\$d)",
                                        favoriteIds.size,
                                    )
                                } else {
                                    readerString("tts_favorites_only", "Favorites only")
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = if (favoritesOnly) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon = if (favoritesOnly) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                        onClick = { favoritesOnly = !favoritesOnly },
                    )
                    HorizontalDivider()
                    if (voiceLanguages.size > 2) {
                        var showLanguages by remember { mutableStateOf(false) }
                        Box {
                            DropdownMenuItem(
                                text = { Text(effectiveLanguage) },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                onClick = { showLanguages = true },
                            )
                            DropdownMenu(
                                expanded = showLanguages,
                                onDismissRequest = { showLanguages = false },
                            ) {
                                voiceLanguages.forEach { language ->
                                    DropdownMenuItem(
                                        text = { Text(language) },
                                        trailingIcon = if (language == effectiveLanguage) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null,
                                        onClick = {
                                            selectedLanguage = language
                                            showLanguages = false
                                        },
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    if (presentQualities.size > 1) {
                        var showQualities by remember { mutableStateOf(false) }
                        Box {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        effectiveQuality?.let { tier ->
                                            sharedMobileEpubVoiceQualityLabel(tier)
                                        } ?: allQualitiesLabel
                                    )
                                },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                onClick = { showQualities = true },
                            )
                            DropdownMenu(
                                expanded = showQualities,
                                onDismissRequest = { showQualities = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(allQualitiesLabel) },
                                    trailingIcon = if (effectiveQuality == null) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null,
                                    onClick = {
                                        selectedQuality = null
                                        showQualities = false
                                    },
                                )
                                presentQualities.forEach { tier ->
                                    DropdownMenuItem(
                                        text = { Text(sharedMobileEpubVoiceQualityLabel(tier)) },
                                        trailingIcon = if (tier == effectiveQuality) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null,
                                        onClick = {
                                            selectedQuality = tier
                                            showQualities = false
                                        },
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(readerString("tts_system_default", "System default"))
                                Text(
                                    readerString("tts_uses_device_settings", "Uses device settings"),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        enabled = !ttsVoiceLocked,
                        onClick = { tts.setVoice(null); showVoices = false },
                        trailingIcon = {
                            IconButton(onClick = { tts.previewVoice(null) }, enabled = !ttsVoiceLocked) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = readerString(
                                        "tts_preview_system_voice",
                                        "Preview system voice",
                                    ),
                                )
                            }
                        }
                    )
                    if (filteredVoices.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (favoritesOnly) {
                                        readerString(
                                            "tts_no_favorite_voices",
                                            "No favorite voices yet. Tap the star on any voice to add it here.",
                                        )
                                    } else {
                                        readerString(
                                            "tts_no_voices_match_filters",
                                            "No voices match these filters",
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            enabled = false,
                            onClick = {},
                        )
                    }
                    filteredVoices.forEach { voice ->
                        val isFavorite = voice.identifier in favoriteIds
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(voice.name)
                                    Text(
                                        sharedMobileEpubVoiceSubtitle(
                                            voice,
                                            sharedMobileEpubVoiceQualityLabel(voice.quality),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            },
                            enabled = !ttsVoiceLocked,
                            onClick = { tts.setVoice(voice.identifier); showVoices = false },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { tts.toggleFavoriteVoice(voice.identifier) }) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = if (isFavorite) {
                                                readerString(
                                                    "tts_remove_from_favorites_named",
                                                    "Remove %1\$s from favorites",
                                                    voice.name,
                                                )
                                            } else {
                                                readerString(
                                                    "tts_add_to_favorites_named",
                                                    "Add %1\$s to favorites",
                                                    voice.name,
                                                )
                                            },
                                            tint = if (isFavorite) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { tts.previewVoice(voice.identifier) }, enabled = !ttsVoiceLocked) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = readerString(
                                                "tts_preview_voice",
                                                "Preview %1\$s",
                                                voice.name,
                                            ),
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(readerString("tts_speech_rate", "Speech rate"))
                    Text("${(rate * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(value = rate, onValueChange = {
                    rate = it
                    tts.setSpeechParameters(rate, pitch)
                }, valueRange = 0.5f..3f)
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(readerString("tts_pitch", "Pitch"))
                    Text("${(pitch * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(value = pitch, onValueChange = {
                    pitch = it
                    tts.setSpeechParameters(rate, pitch)
                }, valueRange = 0.5f..2f)
            }
            TextButton(onClick = {
                rate = 1f
                pitch = 1f
                tts.setSpeechParameters(rate, pitch)
            }) { Text(readerString("action_reset", "Reset")) }
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
