package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.only
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Ai
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderCloudTtsState
import com.aryan.reader.shared.DEFAULT_CLOUD_TTS_SPEAKER_ID
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.ReaderCloudTtsVoices
import com.aryan.reader.shared.ReaderWordReplacementEngine
import com.aryan.reader.shared.ReaderWordReplacementRule
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.reader.ReaderReadingMode
import kotlin.math.roundToInt

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
            Text("Could not open EPUB", style = MaterialTheme.typography.titleMedium)
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
    val ttsBusy = localTtsState != SharedMobileEpubLocalTtsState.IDLE ||
        cloudTtsState.isLoading || cloudTtsState.isPlaying || cloudTtsState.isPaused
    val onReadAloudToggle = if (cloudTtsAvailable) onCloudTtsToggle else onLocalTtsToggle
    val onReadAloudStop = if (cloudTtsAvailable) onCloudTtsStop else onLocalTtsStop
    val readAloudIcon = if (cloudTtsAvailable) cloudTtsState.icon() else localTtsState.icon()
    val readAloudLabel = if (cloudTtsAvailable) cloudTtsState.menuLabel() else localTtsState.menuLabel()
    Surface(modifier = modifier, tonalElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().height(55.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            topTools.forEach { tool ->
                when (tool) {
                    ReaderTool.THEME -> IconButton(onClick = onTheme) {
                        Icon(Icons.Default.Palette, contentDescription = "Theme")
                    }
                    ReaderTool.TOC -> IconButton(onClick = onOpenToc) {
                        Icon(Icons.Default.Menu, contentDescription = "Contents")
                    }
                    ReaderTool.FORMAT -> IconButton(onClick = onFormat) {
                        Text("Tᵀ", style = MaterialTheme.typography.titleLarge)
                    }
                    ReaderTool.SEARCH -> IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    ReaderTool.SLIDER -> IconButton(onClick = onOpenSlider) {
                        Icon(SharedReaderIcons.Slider, contentDescription = "Navigation slider")
                    }
                    ReaderTool.TTS_CONTROLS -> IconButton(onClick = onReadAloudToggle) {
                        Icon(readAloudIcon, contentDescription = readAloudLabel)
                    }
                    ReaderTool.BRIGHTNESS -> IconButton(onClick = onBrightness) {
                        Icon(SharedReaderIcons.Contrast, contentDescription = "Brightness")
                    }
                    ReaderTool.SCREEN_ORIENTATION -> IconButton(onClick = onScreenOrientation) {
                        Icon(SharedReaderIcons.ScreenRotation, contentDescription = "Screen orientation")
                    }
                    ReaderTool.DICTIONARY -> IconButton(onClick = onOpenDictionarySettings) {
                        Icon(SharedReaderIcons.Dictionary, contentDescription = "Dictionary")
                    }
                    ReaderTool.AI_FEATURES -> if (aiAvailable) IconButton(onClick = onOpenAiHub) {
                        Icon(Icons.Default.Ai, contentDescription = "AI features")
                    }
                    else -> Unit
                }
            }
            Box {
                IconButton(onClick = { onShowMoreChange(true) }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = showMore, onDismissRequest = { onShowMoreChange(false) }) {
                    DropdownMenuItem(
                        text = { Text("Customize Toolbar") },
                        onClick = { onShowMoreChange(false); onCustomizeTools() },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                    val hiddenToolbarTools = toolbarPreferences.sanitized().toolOrder.filter { tool ->
                        tool in SharedMobileEpubToolbarTools && !toolbarPreferences.isVisible(tool)
                    }
                    if (hiddenToolbarTools.isNotEmpty()) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Hidden tools") },
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
                                            ReaderTool.TTS_CONTROLS -> onLocalTtsToggle()
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
                                    text = { Text("Change Reading Mode") },
                                    onClick = { showReadingModeExpanded = !showReadingModeExpanded },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                                )
                                if (showReadingModeExpanded) {
                                    DropdownMenuItem(
                                        text = { Text("Vertical (WebView)") },
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
                                        text = { Text("Vertical (Native Beta)") },
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
                                        text = { Text("Paginated (left-to-right)") },
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
                                        text = { Text("Right-to-left pagination") },
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
                                "Tap to Turn Pages",
                                tapToNavigateEnabled,
                                onTapToNavigateChange,
                                enabled = readingMode == ReaderReadingMode.PAGINATED
                            )
                            ReaderTool.PAGE_TURN_ANIM -> SharedMobileEpubSwitchMenuItem(
                                "Realistic Page Turns",
                                pageTurnAnimationEnabled,
                                onPageTurnAnimationChange,
                                enabled = readingMode == ReaderReadingMode.PAGINATED
                            )
                            ReaderTool.BOOKMARK -> DropdownMenuItem(
                                text = { Text(if (isBookmarked) "Remove Bookmark" else "Bookmark this page") },
                                onClick = { onBookmark(); onShowMoreChange(false) },
                                leadingIcon = { Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null) }
                            )
                            ReaderTool.VISUAL_OPTIONS -> DropdownMenuItem(
                                text = { Text("Visual Options") }, onClick = { onVisualOptions(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                            ReaderTool.TOC -> DropdownMenuItem(
                                text = { Text("Contents") }, onClick = { onOpenToc(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.Menu, contentDescription = null) }
                            )
                            ReaderTool.FORMAT -> DropdownMenuItem(
                                text = { Text("Text formatting") }, onClick = { onFormat(); onShowMoreChange(false) }
                            )
                            ReaderTool.SEARCH -> DropdownMenuItem(
                                text = { Text("Search") }, onClick = { onSearch(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                            )
                            ReaderTool.SLIDER -> DropdownMenuItem(
                                text = { Text("Navigation slider") }, onClick = { onShowMoreChange(false); onOpenSlider() },
                                leadingIcon = { Icon(SharedReaderIcons.Slider, contentDescription = null) }
                            )
                            ReaderTool.TTS_CONTROLS -> DropdownMenuItem(
                                text = { Text(readAloudLabel) }, onClick = { onReadAloudToggle(); onShowMoreChange(false) }
                            )
                            ReaderTool.TTS_REPLACEMENTS -> DropdownMenuItem(
                                text = { Text("TTS Word Replacements") },
                                onClick = { onShowMoreChange(false); onTtsReplacements() },
                                leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) }
                            )
                            ReaderTool.TTS_SETTINGS -> DropdownMenuItem(
                                text = { Text("TTS Voice Settings") },
                                onClick = { onShowMoreChange(false); onTtsSettings() },
                                leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) }
                            )
                            ReaderTool.BOOK_REPLACEMENTS -> DropdownMenuItem(
                                text = { Text("Book Word Replacements") },
                                onClick = { onShowMoreChange(false); onBookReplacements() },
                                leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) }
                            )
                            ReaderTool.KEEP_SCREEN_ON -> SharedMobileEpubSwitchMenuItem("Keep Screen On", keepScreenOn, onKeepScreenOnChange)
                            ReaderTool.AUTO_SCROLL -> DropdownMenuItem(
                                text = { Text(if (autoScroll) "Stop Auto Scroll" else "Auto Scroll") },
                                enabled = readingMode == ReaderReadingMode.VERTICAL &&
                                    !ttsBusy,
                                onClick = { onAutoScrollChange(!autoScroll); onShowMoreChange(false) }
                            )
                            ReaderTool.BRIGHTNESS -> DropdownMenuItem(
                                text = { Text("Brightness") }, onClick = { onBrightness(); onShowMoreChange(false) }
                            )
                            ReaderTool.SCREEN_ORIENTATION -> DropdownMenuItem(
                                text = { Text("Screen Orientation") }, onClick = { onScreenOrientation(); onShowMoreChange(false) },
                                leadingIcon = { Icon(SharedReaderIcons.ScreenRotation, contentDescription = null) }
                            )
                            ReaderTool.AI_FEATURES -> if (aiAvailable) DropdownMenuItem(
                                text = { Text("AI features") },
                                onClick = { onOpenAiHub(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.Ai, contentDescription = null) }
                            )
                            ReaderTool.FILE_INFO -> DropdownMenuItem(
                                text = { Text("File Information") }, onClick = { onFileInfo(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                            )
                            ReaderTool.THEME -> Unit
                            else -> Unit
                        }
                    }
                    if (ReaderTool.TTS_CONTROLS in overflowTools && ttsBusy) {
                        DropdownMenuItem(
                            text = { Text("Stop reading") },
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
                title = "Customize Toolbar",
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
    onOpenAiHub: () -> Unit = {},
    aiAvailable: Boolean = false,
    localTtsState: SharedMobileEpubLocalTtsState,
    onLocalTtsToggle: () -> Unit,
    cloudTtsState: ReaderCloudTtsState = ReaderCloudTtsState(),
    cloudTtsAvailable: Boolean = false,
    onCloudTtsToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val onReadAloudToggle = if (cloudTtsAvailable) onCloudTtsToggle else onLocalTtsToggle
    val readAloudIcon = if (cloudTtsAvailable) cloudTtsState.icon() else localTtsState.icon()
    val readAloudLabel = if (cloudTtsAvailable) cloudTtsState.menuLabel() else localTtsState.menuLabel()
    Surface(modifier = modifier, tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().height(45.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tools.forEach { tool ->
                    when (tool) {
                        ReaderTool.TOC -> IconButton(onClick = onToc) { Icon(Icons.Default.Menu, contentDescription = "Contents") }
                        ReaderTool.FORMAT -> IconButton(onClick = onFormat) { Text("Tᵀ", style = MaterialTheme.typography.titleLarge) }
                        ReaderTool.SEARCH -> IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Search") }
                        ReaderTool.THEME -> IconButton(onClick = onTheme) { Icon(Icons.Default.Palette, contentDescription = "Theme") }
                        ReaderTool.BOOKMARK -> IconButton(onClick = onBookmark) {
                            Icon(
                                if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark this page"
                            )
                        }
                        ReaderTool.VISUAL_OPTIONS -> IconButton(onClick = onVisualOptions) { Icon(Icons.Default.Settings, contentDescription = "Visual options") }
                        ReaderTool.SLIDER -> IconButton(onClick = onOpenSlider) { Icon(SharedReaderIcons.Slider, contentDescription = "Navigation slider") }
                        ReaderTool.DICTIONARY -> IconButton(onClick = onDictionary) { Icon(SharedReaderIcons.Dictionary, contentDescription = "Dictionary") }
                        ReaderTool.AI_FEATURES -> if (aiAvailable) IconButton(onClick = onOpenAiHub) { Icon(Icons.Default.Ai, contentDescription = "AI features") }
                        ReaderTool.TTS_CONTROLS -> IconButton(onClick = onReadAloudToggle) {
                            Icon(readAloudIcon, contentDescription = readAloudLabel)
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

internal fun SharedMobileEpubLocalTtsState.menuLabel(): String = when (this) {
    SharedMobileEpubLocalTtsState.IDLE -> "Read aloud"
    SharedMobileEpubLocalTtsState.SPEAKING -> "Pause reading"
    SharedMobileEpubLocalTtsState.PAUSED -> "Resume reading"
}

internal fun SharedMobileEpubLocalTtsState.icon() = when (this) {
    SharedMobileEpubLocalTtsState.IDLE -> SharedReaderIcons.TextToSpeech
    SharedMobileEpubLocalTtsState.SPEAKING -> Icons.Default.Pause
    SharedMobileEpubLocalTtsState.PAUSED -> Icons.Default.PlayArrow
}

internal fun ReaderCloudTtsState.menuLabel(): String = when {
    isLoading -> "Preparing cloud reading"
    isPlaying -> "Pause cloud reading"
    isPaused -> "Resume cloud reading"
    else -> "Read aloud with Cloud AI"
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
    modifier: Modifier = Modifier
) {
    val progress = tts.progress
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
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
            IconButton(onClick = tts::skipPrevious, enabled = progress.currentChunkIndex > 0) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous reading part")
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
            IconButton(
                onClick = tts::skipNext,
                enabled = progress.currentChunkIndex in 0 until progress.chunks.lastIndex
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next reading part")
            }
            IconButton(onClick = onLocate, enabled = progress.currentChunk != null) {
                Icon(SharedReaderIcons.PinDrop, contentDescription = "Locate current reading part")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "TTS voice settings")
            }
            IconButton(onClick = tts::stop) {
                Icon(Icons.Default.Close, contentDescription = "Stop reading", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun SharedMobileEpubCloudTtsControls(
    tts: SharedMobileEpubCloudTts,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cloudState = tts.state
    val progress = cloudState.progress
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
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
            IconButton(onClick = tts::skipPrevious, enabled = progress.currentChunkIndex > 0 && !cloudState.isLoading) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous cloud reading part")
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
            IconButton(onClick = tts::skipNext, enabled = progress.currentChunkIndex in 0 until progress.chunks.lastIndex && !cloudState.isLoading) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next cloud reading part")
            }
            IconButton(onClick = onLocate, enabled = progress.currentChunk != null) {
                Icon(SharedReaderIcons.PinDrop, contentDescription = "Locate cloud reading part")
            }
            IconButton(onClick = tts::stop) {
                Icon(Icons.Default.Close, contentDescription = "Stop cloud reading", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("TTS Voice Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            cloudTts?.let { cloud ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Cloud AI reading", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (cloudTtsModeEnabled) "Gemini Live · ${cloud.state.cacheSummary.currentVoiceLabel}"
                            else "Use device speech",
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
                                        selectedCloudVoice?.description ?: "Gemini Live voice",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose cloud voice")
                            }
                        }
                        DropdownMenu(
                            expanded = showCloudVoices,
                            onDismissRequest = { showCloudVoices = false },
                            modifier = Modifier.heightIn(max = 360.dp),
                        ) {
                            ReaderCloudTtsVoices.forEach { voice ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(voice.name)
                                            Text(voice.description, style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = {
                                        cloud.setVoice(voice.id)
                                        onCloudTtsVoiceChange(voice.id)
                                        showCloudVoices = false
                                    },
                                    trailingIcon = if (voice.id == cloudTtsVoiceId) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null,
                                )
                            }
                        }
                    }
                    val cache = cloud.state.cacheSummary
                    Text(
                        if (cache.hasCachedAudio) {
                            "Cached cloud audio: ${cache.cachedChunkCount} chunks · ${cache.currentVoiceLabel}"
                        } else {
                            "No cached cloud audio"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (cache.hasCachedAudio) {
                        TextButton(onClick = onClearCloudTtsCache) { Text("Clear cached cloud audio") }
                    }
                }
                HorizontalDivider()
            }
            Box {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showVoices = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(selectedVoice?.name ?: "System default", fontWeight = FontWeight.SemiBold)
                            Text(
                                selectedVoice?.language ?: "Uses the voice selected by iOS",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose voice")
                    }
                }
                DropdownMenu(
                    expanded = showVoices,
                    onDismissRequest = { showVoices = false },
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    DropdownMenuItem(
                        text = { Column { Text("System default"); Text("Uses iOS settings", style = MaterialTheme.typography.bodySmall) } },
                        onClick = { tts.setVoice(null); showVoices = false },
                        trailingIcon = {
                            IconButton(onClick = { tts.previewVoice(null) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Preview system voice")
                            }
                        }
                    )
                    tts.availableVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Column { Text(voice.name); Text(voice.language, style = MaterialTheme.typography.bodySmall) } },
                            onClick = { tts.setVoice(voice.identifier); showVoices = false },
                            trailingIcon = {
                                IconButton(onClick = { tts.previewVoice(voice.identifier) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Preview ${voice.name}")
                                }
                            }
                        )
                    }
                }
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Speech rate")
                    Text("${(rate * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(value = rate, onValueChange = {
                    rate = it
                    tts.setSpeechParameters(rate, pitch)
                }, valueRange = 0.5f..3f)
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pitch")
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
            }) { Text("Reset") }
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
        Text("Book Word Replacements", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Replace visible text in this book only. Locations, bookmarks, and the original EPUB remain unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { isAdding = true; editingRuleId = null }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add rule")
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
            Text("No replacements for this book yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rules.forEach { rule ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${rule.from} → ${rule.to.ifBlank { "(remove)" }}", fontWeight = FontWeight.SemiBold)
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
                        TextButton(onClick = { editingRuleId = rule.id; isAdding = false }) { Text("Edit") }
                        TextButton(onClick = {
                            onPreferencesChange(preferences.withFileRules(bookId, rules.filterNot { it.id == rule.id }))
                        }) { Text("Delete") }
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
            OutlinedTextField(from, { from = it }, label = { Text("Replace") }, isError = !validation.isValid, modifier = Modifier.fillMaxWidth())
            validation.message?.takeIf { !validation.isValid }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(to, { to = it }, label = { Text("With") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(enabled, { enabled = !enabled }, label = { Text("Enabled") })
                FilterChip(isRegex, { isRegex = !isRegex }, label = { Text("Regex") })
                FilterChip(wholeWord, { wholeWord = !wholeWord }, label = { Text("Whole word") })
                FilterChip(matchCase, { matchCase = !matchCase }, label = { Text("Match case") })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(enabled = validation.isValid, onClick = { onSave(draft) }) { Text("Save") }
            }
        }
    }
}
