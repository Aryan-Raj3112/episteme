package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BuiltInReaderThemes
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.PageInfoMode
import com.aryan.reader.shared.PageInfoPosition
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderAutoScrollState
import com.aryan.reader.shared.ReaderContextExtractor
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderAction
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderTexture
import com.aryan.reader.shared.ReaderTextureFilePrefix
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsPlanner
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.ReaderTtsReplacementBookSettings
import com.aryan.reader.shared.ReaderTtsReplacementEngine
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.ReaderTtsReplacementRule
import com.aryan.reader.shared.ReaderTtsReplacementSuggestions
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.SystemUiMode
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.readerTextureDisplayName
import com.aryan.reader.shared.toReaderSettings
import com.aryan.reader.shared.reader.PaginatedReaderState
import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class ReaderContentNavigationTarget(
    val locator: ReaderLocator?,
    val requestId: Long,
    val readingMode: ReaderReadingMode,
    val autoScroll: ReaderAutoScrollState = ReaderAutoScrollState(),
    val ttsLocator: ReaderLocator? = null,
    val ttsRequestId: Long = 0L
)

@Composable
fun SharedScreenScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
        content()
    }
}

@Composable
fun SharedReaderScreen(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    onSessionChange: (ReaderSessionState) -> Unit,
    onOpenBook: () -> Unit,
    onOpenPdf: () -> Unit,
    onReturnToLibrary: (() -> Unit)? = null,
    toolbarPreferences: ReaderToolbarPreferences = ReaderToolbarPreferences(),
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit = {},
    highlightPalette: ReaderHighlightPalette = ReaderHighlightPalette(),
    onHighlightPaletteChange: (ReaderHighlightPalette) -> Unit = {},
    ttsReplacementPreferences: ReaderTtsReplacementPreferences = ReaderTtsReplacementPreferences(),
    ttsReplacementBookId: String? = null,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit = {},
    onPickCustomFont: (() -> String?)? = null,
    customFonts: List<CustomFontItem> = emptyList(),
    readerExtrasState: ReaderExtrasState = ReaderExtrasState(),
    aiByokSettings: ReaderAiByokSettings = ReaderAiByokSettings(),
    externalLookupAvailable: Boolean = true,
    cloudTtsControlsAvailable: Boolean = true,
    onExternalLookup: (ReaderExternalLookupAction, String) -> Unit = { _, _ -> },
    onAiAction: (ReaderAiFeature, String) -> Unit = { _, _ -> },
    onCloudTtsStart: (ReaderTtsReadScope, List<ReaderTtsChunk>) -> Unit = { _, _ -> },
    onCloudTtsPauseResume: () -> Unit = {},
    onCloudTtsStop: () -> Unit = {},
    onCloudTtsClearCache: () -> Unit = {},
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit = {},
    readerTextureDataUri: (String) -> String? = { null },
    readerCustomTextureIds: List<String> = emptyList(),
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)? = null,
    readerContent: @Composable ColumnScope.(
        html: String,
        background: Color,
        navigationTarget: ReaderContentNavigationTarget,
        highlights: List<UserHighlight>,
        onVisiblePageChanged: (Int, ReaderLocator?) -> Unit
    ) -> Unit
) {
    val readerState = session.reader
    val page = readerState.currentPage
    val settings = readerState.settings
    val byokSettings = aiByokSettings.sanitized()
    val background = settings.backgroundColorArgb?.toComposeColor() ?: if (settings.darkMode) Color(0xFF171A17) else Color(0xFFFFFCF5)
    val pageInfoText = readerState.pageInfoText()
    val shouldShowPageInfo = settings.pageInfoMode != PageInfoMode.HIDDEN
    val activeTtsProgress = readerExtrasState.cloudTts.progress
    val activeTtsChunk = activeTtsProgress.currentChunk
    val activeTtsLocator = activeTtsChunk?.toLocator()
    val ttsRequestId = activeTtsChunk?.let { activeTtsProgress.sessionId + it.index + 1L } ?: 0L
    val navigationLocator = session.navigationLocator ?: session.activeSearchResult?.locator ?: readerState.currentPageLocator()
    val effectiveCloudTtsAvailable = cloudTtsControlsAvailable && byokSettings.isCloudTtsAvailable
    fun dispatch(action: ReaderAction) {
        onSessionChange(session.reduce(action, readerEngine))
    }
    val workspaceModel = epubReaderWorkspaceModel(
        session = session,
        toolbarPreferences = toolbarPreferences,
        extrasState = readerExtrasState,
        aiAvailable = byokSettings.areReaderAiFeaturesAvailable,
        cloudTtsAvailable = effectiveCloudTtsAvailable,
        externalLookupAvailable = externalLookupAvailable
    )

    LaunchedEffect(
        readerExtrasState.autoScroll.sanitized(),
        settings.readingMode,
        readerState.currentPageIndex,
        readerState.canGoNext
    ) {
        val autoScroll = readerExtrasState.autoScroll.sanitized()
        if (!autoScroll.enabled || settings.readingMode != ReaderReadingMode.PAGINATED || !readerState.canGoNext) return@LaunchedEffect
        val delayMs = (180_000f / autoScroll.speed).roundToInt().coerceIn(1_200, 12_000)
        delay(delayMs.toLong())
        dispatch(ReaderAction.NextPage)
    }

    ReaderWorkspaceShell(
        model = workspaceModel,
        title = readerState.book.title,
        subtitle = listOfNotNull(readerState.book.author, page?.chapterTitle).joinToString(" - "),
        progressLabel = "${readerState.progress.toInt()}%",
        onReturnToLibrary = onReturnToLibrary,
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.DirectionRight || event.key == Key.PageDown -> {
                        dispatch(ReaderAction.NextPage)
                        true
                    }

                    event.key == Key.DirectionLeft || event.key == Key.PageUp -> {
                        dispatch(ReaderAction.PreviousPage)
                        true
                    }

                    event.key == Key.MoveHome -> {
                        dispatch(ReaderAction.JumpToPage(0))
                        true
                    }

                    event.key == Key.MoveEnd -> {
                        dispatch(ReaderAction.JumpToPage(readerState.pages.lastIndex))
                        true
                    }

                    event.isCtrlPressed && event.key == Key.G -> {
                        dispatch(ReaderAction.JumpToNextSearchResult)
                        true
                    }

                    event.isCtrlPressed && event.key == Key.F -> {
                        dispatch(ReaderAction.SearchOpened)
                        true
                    }

                    else -> false
                }
            }
            .focusable(),
        topActions = {
            TextButton(onClick = onOpenBook) {
                Text("Open Book")
            }
            TextButton(onClick = onOpenPdf) {
                Text("Open PDF")
            }
            SharedReaderQuickActions(
                toolbarPreferences = toolbarPreferences,
                bottom = false,
                isBookmarked = session.currentBookmark != null,
                isDarkMode = settings.darkMode,
                isSearchActive = session.isSearchActive,
                onToggleBookmark = { dispatch(ReaderAction.ToggleBookmark) },
                onToggleTheme = { dispatch(ReaderAction.SettingsChanged(settings.copy(darkMode = !settings.darkMode))) },
                onToggleSearch = {
                    dispatch(if (session.isSearchActive) ReaderAction.SearchClosed else ReaderAction.SearchOpened)
                },
                onExternalLookup = onExternalLookup,
                onAiAction = onAiAction,
                onCloudTtsStart = onCloudTtsStart,
                onCloudTtsPauseResume = onCloudTtsPauseResume,
                onCloudTtsStop = onCloudTtsStop,
                onCloudTtsClearCache = onCloudTtsClearCache,
                onAutoScrollChange = onAutoScrollChange,
                session = session,
                extrasState = readerExtrasState,
                aiByokSettings = byokSettings,
                cloudTtsControlsAvailable = cloudTtsControlsAvailable,
                externalLookupAvailable = externalLookupAvailable
            )
            SharedReaderQuickActions(
                toolbarPreferences = toolbarPreferences,
                bottom = true,
                isBookmarked = session.currentBookmark != null,
                isDarkMode = settings.darkMode,
                isSearchActive = session.isSearchActive,
                onToggleBookmark = { dispatch(ReaderAction.ToggleBookmark) },
                onToggleTheme = { dispatch(ReaderAction.SettingsChanged(settings.copy(darkMode = !settings.darkMode))) },
                onToggleSearch = {
                    dispatch(if (session.isSearchActive) ReaderAction.SearchClosed else ReaderAction.SearchOpened)
                },
                onExternalLookup = onExternalLookup,
                onAiAction = onAiAction,
                onCloudTtsStart = onCloudTtsStart,
                onCloudTtsPauseResume = onCloudTtsPauseResume,
                onCloudTtsStop = onCloudTtsStop,
                onCloudTtsClearCache = onCloudTtsClearCache,
                onAutoScrollChange = onAutoScrollChange,
                session = session,
                extrasState = readerExtrasState,
                aiByokSettings = byokSettings,
                cloudTtsControlsAvailable = cloudTtsControlsAvailable,
                externalLookupAvailable = externalLookupAvailable
            )
        },
        leftSidebar = {
            SharedReaderSidebar(
                session = session,
                sections = workspaceModel.leftSections,
                onGoToChapter = { dispatch(ReaderAction.JumpToChapter(it)) },
                onGoToBookmark = { dispatch(ReaderAction.JumpToLocator(it.locator)) },
                onGoToHighlight = { dispatch(ReaderAction.JumpToLocator(it.locator)) }
            )
        },
        rightInspector = {
            SharedReaderControlPanel(
                session = session,
                toolbarPreferences = toolbarPreferences,
                onToolbarPreferencesChange = onToolbarPreferencesChange,
                onPickCustomFont = onPickCustomFont,
                customFonts = customFonts,
                extrasState = readerExtrasState,
                aiByokSettings = byokSettings,
                externalLookupAvailable = externalLookupAvailable,
                cloudTtsControlsAvailable = cloudTtsControlsAvailable,
                onExternalLookup = onExternalLookup,
                onAiAction = onAiAction,
                onCloudTtsStart = onCloudTtsStart,
                onCloudTtsPauseResume = onCloudTtsPauseResume,
                onCloudTtsStop = onCloudTtsStop,
                onCloudTtsClearCache = onCloudTtsClearCache,
                onAutoScrollChange = onAutoScrollChange,
                ttsReplacementPreferences = ttsReplacementPreferences,
                ttsReplacementBookId = ttsReplacementBookId ?: session.reader.book.title,
                onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange,
                readerCustomTextureIds = readerCustomTextureIds,
                onImportReaderTexture = onImportReaderTexture,
                onReaderAction = { action -> dispatch(action) }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SharedReaderCompactNavigation(
                        session = session,
                        showSlider = toolbarPreferences.isVisible(ReaderTool.SLIDER),
                        canGoPrevious = readerState.canGoPrevious,
                        canGoNext = readerState.canGoNext,
                        pageInfoText = if (shouldShowPageInfo && settings.pageInfoPosition == PageInfoPosition.BOTTOM) pageInfoText else null,
                        onPrevious = { dispatch(ReaderAction.PreviousPage) },
                        onNext = { dispatch(ReaderAction.NextPage) },
                        onPageNumberChange = { pageNumber -> dispatch(ReaderAction.GoToPageNumber(pageNumber)) }
                    )
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (shouldShowPageInfo && settings.pageInfoPosition == PageInfoPosition.TOP) {
                Text(pageInfoText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val html = if (settings.readingMode == ReaderReadingMode.VERTICAL) {
                remember(
                    readerState.book,
                    settings,
                    session.searchQuery,
                    session.searchOptions,
                    highlightPalette,
                    readerState.pages,
                    byokSettings.areReaderAiFeaturesAvailable,
                    effectiveCloudTtsAvailable,
                    externalLookupAvailable
                ) {
                    ReaderHtmlDocumentBuilder.verticalDocument(
                        book = readerState.book,
                        settings = settings,
                        searchQuery = session.searchQuery,
                        searchOptions = session.searchOptions,
                        highlights = emptyList(),
                        highlightPalette = highlightPalette,
                        navigationLocator = null,
                        pages = readerState.pages,
                        readerAiFeaturesEnabled = byokSettings.areReaderAiFeaturesAvailable,
                        cloudTtsEnabled = effectiveCloudTtsAvailable,
                        externalLookupEnabled = externalLookupAvailable,
                        textureDataUri = settings.textureId?.let(readerTextureDataUri)
                    )
                }
            } else {
                remember(
                    readerState.book,
                    page,
                    settings,
                    session.searchQuery,
                    session.searchOptions,
                    session.highlights,
                    highlightPalette,
                    navigationLocator,
                    byokSettings.areReaderAiFeaturesAvailable,
                    effectiveCloudTtsAvailable,
                    externalLookupAvailable
                ) {
                    ReaderHtmlDocumentBuilder.pageDocument(
                        book = readerState.book,
                        page = page,
                        settings = settings,
                        searchQuery = session.searchQuery,
                        searchOptions = session.searchOptions,
                        highlights = session.highlights,
                        highlightPalette = highlightPalette,
                        navigationLocator = navigationLocator,
                        readerAiFeaturesEnabled = byokSettings.areReaderAiFeaturesAvailable,
                        cloudTtsEnabled = effectiveCloudTtsAvailable,
                        externalLookupEnabled = externalLookupAvailable,
                        textureDataUri = settings.textureId?.let(readerTextureDataUri)
                    )
                }
            }
            readerContent(
                html,
                background,
                ReaderContentNavigationTarget(
                    locator = navigationLocator,
                    requestId = session.navigationRequestId,
                    readingMode = settings.readingMode,
                    autoScroll = readerExtrasState.autoScroll.sanitized(),
                    ttsLocator = activeTtsLocator,
                    ttsRequestId = ttsRequestId
                ),
                if (settings.readingMode == ReaderReadingMode.VERTICAL) session.highlights else emptyList(),
                { pageIndex, locator -> dispatch(ReaderAction.VisiblePageChanged(pageIndex, locator)) }
            )
        }
    }
}

@Composable
private fun SharedReaderQuickActions(
    toolbarPreferences: ReaderToolbarPreferences,
    bottom: Boolean,
    isBookmarked: Boolean,
    isDarkMode: Boolean,
    isSearchActive: Boolean,
    onToggleBookmark: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleSearch: () -> Unit,
    onExternalLookup: (ReaderExternalLookupAction, String) -> Unit,
    onAiAction: (ReaderAiFeature, String) -> Unit,
    onCloudTtsStart: (ReaderTtsReadScope, List<ReaderTtsChunk>) -> Unit,
    onCloudTtsPauseResume: () -> Unit,
    onCloudTtsStop: () -> Unit,
    onCloudTtsClearCache: () -> Unit,
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit,
    session: ReaderSessionState,
    extrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    cloudTtsControlsAvailable: Boolean,
    externalLookupAvailable: Boolean
) {
    val tools = readerWorkspaceQuickActionTools(
        toolbarPreferences = toolbarPreferences,
        bottom = bottom,
        aiAvailable = aiByokSettings.areReaderAiFeaturesAvailable,
        cloudTtsAvailable = cloudTtsControlsAvailable && aiByokSettings.isCloudTtsAvailable,
        externalLookupAvailable = externalLookupAvailable
    )
    if (tools.isEmpty()) return

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        tools.forEach { tool ->
            when (tool) {
                ReaderTool.BOOKMARK -> IconButton(onClick = onToggleBookmark) {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Bookmark"
                    )
                }

                ReaderTool.THEME -> IconButton(onClick = onToggleTheme) {
                    Icon(Icons.Default.Palette, contentDescription = if (isDarkMode) "Use light theme" else "Use dark theme")
                }

                ReaderTool.SEARCH -> IconButton(onClick = onToggleSearch) {
                    Icon(
                        if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search"
                    )
                }

                ReaderTool.DICTIONARY -> IconButton(
                    onClick = { onExternalLookup(ReaderExternalLookupAction.DICTIONARY, ReaderContextExtractor.currentPageText(session)) }
                ) {
                    Icon(Icons.Default.Translate, contentDescription = "External lookup")
                }

                ReaderTool.AI_FEATURES -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        enabled = aiByokSettings.areReaderAiFeaturesAvailable &&
                            ReaderContextExtractor.currentPageText(session).isNotBlank() &&
                            !extrasState.aiResult.isLoading,
                        onClick = { onAiAction(ReaderAiFeature.DEFINE, ReaderContextExtractor.currentPageText(session).take(1200)) }
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = "Define page")
                    }
                    TextButton(
                        enabled = aiByokSettings.areReaderAiFeaturesAvailable &&
                            ReaderContextExtractor.currentChapterText(session).isNotBlank() &&
                            !extrasState.aiResult.isLoading,
                        onClick = { onAiAction(ReaderAiFeature.SUMMARIZE, ReaderContextExtractor.currentChapterText(session)) }
                    ) {
                        Text("Summary")
                    }
                }

                ReaderTool.TTS_CONTROLS -> IconButton(
                    enabled = cloudTtsControlsAvailable && (
                        extrasState.cloudTts.isAvailable ||
                        extrasState.cloudTts.isPlaying ||
                        extrasState.cloudTts.isLoading ||
                        extrasState.cloudTts.isPaused
                    ),
                    onClick = {
                        if (extrasState.cloudTts.isPlaying || extrasState.cloudTts.isLoading || extrasState.cloudTts.isPaused) {
                            onCloudTtsStop()
                        } else {
                            onCloudTtsStart(
                                ReaderTtsReadScope.BOOK,
                                ReaderTtsPlanner.chunksFromCurrentLocation(session)
                            )
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = if (extrasState.cloudTts.isPlaying || extrasState.cloudTts.isLoading || extrasState.cloudTts.isPaused) "Stop read aloud" else "Read aloud")
                }

                ReaderTool.AUTO_SCROLL -> IconButton(
                    onClick = {
                        val autoScroll = extrasState.autoScroll.sanitized()
                        onAutoScrollChange(autoScroll.copy(enabled = !autoScroll.enabled))
                    }
                ) {
                    Icon(Icons.Default.Speed, contentDescription = if (extrasState.autoScroll.enabled) "Stop auto scroll" else "Start auto scroll")
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun SharedReaderControlPanel(
    session: ReaderSessionState,
    toolbarPreferences: ReaderToolbarPreferences,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit,
    onPickCustomFont: (() -> String?)?,
    customFonts: List<CustomFontItem>,
    extrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    externalLookupAvailable: Boolean,
    cloudTtsControlsAvailable: Boolean,
    onExternalLookup: (ReaderExternalLookupAction, String) -> Unit,
    onAiAction: (ReaderAiFeature, String) -> Unit,
    onCloudTtsStart: (ReaderTtsReadScope, List<ReaderTtsChunk>) -> Unit,
    onCloudTtsPauseResume: () -> Unit,
    onCloudTtsStop: () -> Unit,
    onCloudTtsClearCache: () -> Unit,
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    ttsReplacementBookId: String,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    readerCustomTextureIds: List<String>,
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)?,
    onReaderAction: (ReaderAction) -> Unit
) {
    val sections = toolbarPreferences.availableReaderControlSections()
    if (sections.isEmpty()) return
    val defaultSection = if (session.isSearchActive && ReaderControlSection.SEARCH in sections) {
        ReaderControlSection.SEARCH
    } else {
        sections.firstOrNull { it != ReaderControlSection.SEARCH } ?: sections.first()
    }
    var selectedSection by remember(sections) { mutableStateOf(defaultSection) }
    LaunchedEffect(session.isSearchActive, sections) {
        if (session.isSearchActive && ReaderControlSection.SEARCH in sections) {
            selectedSection = ReaderControlSection.SEARCH
        } else if (selectedSection !in sections) {
            selectedSection = defaultSection
        }
    }
    val activeSection = selectedSection.takeIf { it in sections } ?: defaultSection

    Surface(
        modifier = Modifier
            .width(340.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        LazyColumn(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Reader controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    sections.forEach { section ->
                        FilterChip(
                            selected = activeSection == section,
                            onClick = { selectedSection = section },
                            label = { Text(section.title) }
                        )
                    }
                }
            }
            item {
                HorizontalDivider()
            }
            item {
                when (activeSection) {
                    ReaderControlSection.SEARCH -> SharedReaderSearchControls(
                        session = session,
                        onReaderAction = onReaderAction
                    )

                    ReaderControlSection.FORMAT -> SharedReaderFormatControls(
                        settings = session.reader.settings,
                        toolbarPreferences = toolbarPreferences,
                        onPickCustomFont = onPickCustomFont,
                        customFonts = customFonts,
                        onReaderAction = onReaderAction
                    )

                    ReaderControlSection.THEME -> SharedReaderThemeControls(
                        settings = session.reader.settings,
                        customTextureIds = readerCustomTextureIds,
                        onImportTexture = onImportReaderTexture,
                        onSettingsChange = { onReaderAction(ReaderAction.SettingsChanged(it)) }
                    )

                    ReaderControlSection.VISUAL -> SharedReaderVisualOptionsControls(
                        settings = session.reader.settings,
                        onReaderAction = onReaderAction
                    )

                    ReaderControlSection.EXTRAS -> SharedReaderExtrasControls(
                        session = session,
                        extrasState = extrasState,
                        aiByokSettings = aiByokSettings,
                        toolbarPreferences = toolbarPreferences,
                        externalLookupAvailable = externalLookupAvailable,
                        cloudTtsControlsAvailable = cloudTtsControlsAvailable,
                        onExternalLookup = onExternalLookup,
                        onAiAction = onAiAction,
                        onCloudTtsStart = onCloudTtsStart,
                        onCloudTtsPauseResume = onCloudTtsPauseResume,
                        onCloudTtsStop = onCloudTtsStop,
                        onCloudTtsClearCache = onCloudTtsClearCache,
                        onAutoScrollChange = onAutoScrollChange,
                        ttsReplacementPreferences = ttsReplacementPreferences,
                        ttsReplacementBookId = ttsReplacementBookId,
                        onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange
                    )

                    ReaderControlSection.TOOLBAR -> SharedReaderToolbarControls(
                        toolbarPreferences = toolbarPreferences,
                        onToolbarPreferencesChange = onToolbarPreferencesChange
                    )
                }
            }
        }
    }
}

private enum class ReaderControlSection(val title: String) {
    SEARCH("Search"),
    FORMAT("Format"),
    THEME("Theme"),
    VISUAL("Visual"),
    EXTRAS("Extras"),
    TOOLBAR("Toolbar")
}

private fun ReaderToolbarPreferences.availableReaderControlSections(): List<ReaderControlSection> {
    return buildList {
        if (isVisible(ReaderTool.SEARCH)) add(ReaderControlSection.SEARCH)
        if (isVisible(ReaderTool.FORMAT) || isVisible(ReaderTool.READING_MODE)) add(ReaderControlSection.FORMAT)
        if (isVisible(ReaderTool.THEME)) add(ReaderControlSection.THEME)
        if (isVisible(ReaderTool.VISUAL_OPTIONS)) add(ReaderControlSection.VISUAL)
        if (
            isVisible(ReaderTool.DICTIONARY) ||
            isVisible(ReaderTool.AI_FEATURES) ||
            isVisible(ReaderTool.TTS_CONTROLS) ||
            isVisible(ReaderTool.TTS_SETTINGS) ||
            isVisible(ReaderTool.TTS_REPLACEMENTS) ||
            isVisible(ReaderTool.AUTO_SCROLL)
        ) {
            add(ReaderControlSection.EXTRAS)
        }
        add(ReaderControlSection.TOOLBAR)
    }
}

@Composable
private fun SharedReaderSearchControls(
    session: ReaderSessionState,
    onReaderAction: (ReaderAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Find in book", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    onReaderAction(if (session.isSearchActive) ReaderAction.SearchClosed else ReaderAction.SearchOpened)
                }
            ) {
                Text(if (session.isSearchActive) "Close" else "Open")
            }
        }

        if (session.isSearchActive) {
            OutlinedTextField(
                value = session.searchQuery,
                onValueChange = { onReaderAction(ReaderAction.SearchChanged(it)) },
                label = { Text("Search text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = session.searchOptions.matchCase,
                    onClick = {
                        onReaderAction(ReaderAction.SearchOptionsChanged(session.searchOptions.copy(matchCase = !session.searchOptions.matchCase)))
                    },
                    label = { Text("Match case") }
                )
                FilterChip(
                    selected = session.searchOptions.wholeWords,
                    onClick = {
                        onReaderAction(ReaderAction.SearchOptionsChanged(session.searchOptions.copy(wholeWords = !session.searchOptions.wholeWords)))
                    },
                    label = { Text("Whole words") }
                )
            }

            if (session.searchQuery.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when {
                            session.searchResults.isEmpty() -> "No matches"
                            else -> "${session.activeSearchResultIndex + 1} of ${session.searchResults.size}"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = session.canGoToPreviousSearchResult,
                        onClick = { onReaderAction(ReaderAction.JumpToPreviousSearchResult) }
                    ) {
                        Text("Prev")
                    }
                    TextButton(
                        enabled = session.canGoToNextSearchResult,
                        onClick = { onReaderAction(ReaderAction.JumpToNextSearchResult) }
                    ) {
                        Text("Next")
                    }
                }

                if (session.searchResults.isNotEmpty()) {
                    TextButton(onClick = { onReaderAction(ReaderAction.SearchResultsPanelToggled) }) {
                        Text(if (session.showSearchResultsPanel) "Hide results" else "Show results")
                    }
                }
            }

            if (session.searchQuery.isNotBlank() && session.showSearchResultsPanel) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    session.searchResults.forEachIndexed { index, result ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                onReaderAction(ReaderAction.JumpToSearchResult(index))
                            }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    "Page ${result.pageIndex + 1} - ${result.chapterTitle}",
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(result.preview, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SharedReaderFormatControls(
    settings: ReaderSettings,
    toolbarPreferences: ReaderToolbarPreferences,
    onPickCustomFont: (() -> String?)?,
    customFonts: List<CustomFontItem>,
    onReaderAction: (ReaderAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (toolbarPreferences.isVisible(ReaderTool.READING_MODE)) {
            SharedReaderPanelSection("Reading") {
                SharedReaderChoiceRow {
                    FilterChip(
                        selected = settings.readingMode == ReaderReadingMode.PAGINATED,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(readingMode = ReaderReadingMode.PAGINATED)))
                        },
                        label = { Text("Pages") }
                    )
                    FilterChip(
                        selected = settings.readingMode == ReaderReadingMode.VERTICAL,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(readingMode = ReaderReadingMode.VERTICAL)))
                        },
                        label = { Text("Vertical") }
                    )
                }
            }
        }

        if (toolbarPreferences.isVisible(ReaderTool.FORMAT)) {
            SharedReaderPanelSection("Font & Alignment") {
                val customFontName = settings.customFontPath
                    ?.substringAfterLast('/')
                    ?.substringAfterLast('\\')
                    ?.takeIf { it.isNotBlank() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(42.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aa", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(customFontName ?: settings.fontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Font", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(
                        enabled = onPickCustomFont != null,
                        onClick = {
                            onPickCustomFont?.invoke()?.takeIf { it.isNotBlank() }?.let { path ->
                                onReaderAction(
                                    ReaderAction.SettingsChanged(
                                        settings.copy(
                                            fontFamily = path.substringAfterLast('/').substringAfterLast('\\'),
                                            customFontPath = path
                                        )
                                    )
                                )
                            }
                        }
                    ) {
                        Text("Choose")
                    }
                }

                SharedReaderChoiceRow {
                    listOf("Default", "Serif", "Sans", "Mono").forEach { family ->
                        FilterChip(
                            selected = settings.customFontPath == null && settings.fontFamily == family,
                            onClick = {
                                onReaderAction(
                                    ReaderAction.SettingsChanged(settings.copy(fontFamily = family, customFontPath = null))
                                )
                            },
                            label = { Text(family) }
                        )
                    }
                    if (settings.customFontPath != null) {
                        TextButton(
                            onClick = {
                                onReaderAction(
                                    ReaderAction.SettingsChanged(settings.copy(fontFamily = "Default", customFontPath = null))
                                )
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                }

                val activeCustomFonts = customFonts.filterNot { it.isDeleted }.sortedBy { it.displayName.lowercase() }
                if (activeCustomFonts.isNotEmpty()) {
                    Text(
                        "Imported fonts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SharedReaderChoiceRow {
                        activeCustomFonts.forEach { font ->
                            FilterChip(
                                selected = settings.customFontPath == font.path,
                                onClick = {
                                    onReaderAction(
                                        ReaderAction.SettingsChanged(
                                            settings.copy(
                                                fontFamily = font.displayName,
                                                customFontPath = font.path
                                            )
                                        )
                                    )
                                },
                                label = { Text(font.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }

                SharedReaderChoiceRow {
                    FilterChip(
                        selected = settings.textAlign == SharedReaderTextAlign.START,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.START)))
                        },
                        label = { Text("Left") }
                    )
                    FilterChip(
                        selected = settings.textAlign == SharedReaderTextAlign.JUSTIFY,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.JUSTIFY)))
                        },
                        label = { Text("Justify") }
                    )
                    FilterChip(
                        selected = settings.textAlign == SharedReaderTextAlign.CENTER,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.CENTER)))
                        },
                        label = { Text("Center") }
                    )
                }
            }

            SharedReaderPanelSection("Layout & Spacing") {
                SharedReaderSettingSlider(
                    label = "Font size",
                    value = settings.fontSize.toFloat(),
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(fontSize = value.toInt())))
                    },
                    valueRange = 14f..30f,
                    valueLabel = settings.fontSize.toString()
                )
                SharedReaderSettingSlider(
                    label = "Line height",
                    value = settings.lineSpacing,
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(lineSpacing = value)))
                    },
                    valueRange = 1.1f..2.1f,
                    valueLabel = "${settings.lineSpacing.formatTwoDecimals()}x"
                )
                SharedReaderSettingSlider(
                    label = "Paragraph gap",
                    value = settings.paragraphSpacing,
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(paragraphSpacing = value)))
                    },
                    valueRange = 0.5f..2.5f,
                    valueLabel = "${settings.paragraphSpacing.formatTwoDecimals()}x"
                )
                SharedReaderSettingSlider(
                    label = "Image size",
                    value = settings.imageScale,
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(imageScale = value)))
                    },
                    valueRange = 0.5f..2.0f,
                    valueLabel = "${settings.imageScale.formatTwoDecimals()}x"
                )
                SharedReaderSettingSlider(
                    label = "Horizontal margin",
                    value = settings.resolvedHorizontalMargin.toFloat(),
                    onValueChange = { value ->
                        val nextHorizontal = value.toInt()
                        val nextMargin = maxOf(nextHorizontal, settings.resolvedVerticalMargin)
                        onReaderAction(
                            ReaderAction.SettingsChanged(
                                settings.copy(horizontalMargin = nextHorizontal, margin = nextMargin)
                            )
                        )
                    },
                    valueRange = 0f..160f,
                    valueLabel = settings.resolvedHorizontalMargin.toString()
                )
                SharedReaderSettingSlider(
                    label = "Vertical margin",
                    value = settings.resolvedVerticalMargin.toFloat(),
                    onValueChange = { value ->
                        val nextVertical = value.toInt()
                        val nextMargin = maxOf(settings.resolvedHorizontalMargin, nextVertical)
                        onReaderAction(
                            ReaderAction.SettingsChanged(
                                settings.copy(verticalMargin = nextVertical, margin = nextMargin)
                            )
                        )
                    },
                    valueRange = 0f..160f,
                    valueLabel = settings.resolvedVerticalMargin.toString()
                )
                SharedReaderSettingSlider(
                    label = "Page width",
                    value = settings.pageWidth.toFloat(),
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(pageWidth = value.toInt())))
                    },
                    valueRange = 520f..1100f,
                    valueLabel = settings.pageWidth.toString()
                )
            }
        }
    }
}

@Composable
fun SharedReaderThemeControls(
    settings: ReaderSettings,
    builtInThemes: List<ReaderTheme> = BuiltInReaderThemes,
    customTextureIds: List<String> = emptyList(),
    onImportTexture: ((ReaderSettings) -> ReaderSettings?)? = null,
    onSettingsChange: (ReaderSettings) -> Unit
) {
    var textured by remember(settings.themeId, settings.textureId) { mutableStateOf(settings.textureId != null) }
    val activeThemes = builtInThemes.filter { (it.textureId != null) == textured }
    val visibleCustomTextureIds = remember(customTextureIds, settings.textureId) {
        buildList {
            addAll(customTextureIds.distinct())
            settings.textureId
                ?.takeIf { it.startsWith(ReaderTextureFilePrefix) && it !in this }
                ?.let(::add)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SharedReaderPanelSection("Reading Themes") {
            SharedReaderChoiceRow {
                FilterChip(
                    selected = !textured,
                    onClick = { textured = false },
                    label = { Text("Solid") }
                )
                FilterChip(
                    selected = textured,
                    onClick = { textured = true },
                    label = { Text("Textured") }
                )
            }
            activeThemes.chunked(3).forEach { rowThemes ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    rowThemes.forEach { theme ->
                        SharedReaderThemeChoice(
                            theme = theme,
                            selected = settings.themeId == theme.id || (settings.themeId == null && theme.id == "system"),
                            onSelected = { onSettingsChange(theme.toReaderSettings(settings)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowThemes.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (textured) {
            SharedReaderPanelSection("Texture") {
                SharedReaderChoiceRow {
                    FilterChip(
                        selected = settings.textureId == null,
                        onClick = { onSettingsChange(settings.copy(textureId = null)) },
                        label = { Text("None") }
                    )
                    if (onImportTexture != null) {
                        FilterChip(
                            selected = settings.textureId?.startsWith(ReaderTextureFilePrefix) == true,
                            onClick = {
                                onImportTexture(settings)?.let(onSettingsChange)
                            },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            label = { Text("Import") }
                        )
                    }
                    ReaderTexture.entries.forEach { texture ->
                        FilterChip(
                            selected = settings.textureId == texture.id,
                            onClick = { onSettingsChange(settings.copy(textureId = texture.id)) },
                            label = { Text(texture.displayName) }
                        )
                    }
                    visibleCustomTextureIds.forEach { textureId ->
                        FilterChip(
                            selected = settings.textureId == textureId,
                            onClick = { onSettingsChange(settings.copy(textureId = textureId)) },
                            label = { Text(readerTextureDisplayName(textureId)) }
                        )
                    }
                }
                if (settings.textureId != null) {
                    SharedReaderSettingSlider(
                        label = "Texture strength",
                        value = settings.textureAlpha.coerceIn(0f, 1f),
                        onValueChange = { value ->
                            onSettingsChange(settings.copy(textureAlpha = value))
                        },
                        valueRange = 0f..1f,
                        valueLabel = "${(settings.textureAlpha.coerceIn(0f, 1f) * 100).roundToInt()}%"
                    )
                }
            }
        }
    }
}

@Composable
fun SharedReaderVisualOptionsControls(
    settings: ReaderSettings,
    onReaderAction: (ReaderAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SharedReaderPanelSection("System UI") {
            SharedReaderChoiceRow {
                SystemUiMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.systemUiMode == mode,
                        onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(systemUiMode = mode))) },
                        label = { Text(mode.title) }
                    )
                }
            }
        }

        SharedReaderPanelSection("Page Info") {
            SharedReaderChoiceRow {
                PageInfoMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.pageInfoMode == mode,
                        onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(pageInfoMode = mode))) },
                        label = { Text(mode.title) }
                    )
                }
            }
            SharedReaderChoiceRow {
                PageInfoPosition.entries.forEach { position ->
                    FilterChip(
                        selected = settings.pageInfoPosition == position,
                        onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(pageInfoPosition = position))) },
                        label = { Text(position.title) }
                    )
                }
            }
        }

        SharedReaderPanelSection("Chapter Turns") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Seamless chapters", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.seamlessChapterNavigation,
                    onCheckedChange = { enabled ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(seamlessChapterNavigation = enabled)))
                    }
                )
            }
            SharedReaderSettingSlider(
                label = "Pull distance",
                value = settings.chapterTurnDragMultiplier.coerceIn(0.5f, 2.0f),
                onValueChange = { value ->
                    onReaderAction(ReaderAction.SettingsChanged(settings.copy(chapterTurnDragMultiplier = value)))
                },
                valueRange = 0.5f..2.0f,
                valueLabel = "${settings.chapterTurnDragMultiplier.formatTwoDecimals()}x"
            )
        }
    }
}

@Composable
private fun SharedReaderExtrasControls(
    session: ReaderSessionState,
    extrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    toolbarPreferences: ReaderToolbarPreferences,
    externalLookupAvailable: Boolean,
    cloudTtsControlsAvailable: Boolean,
    onExternalLookup: (ReaderExternalLookupAction, String) -> Unit,
    onAiAction: (ReaderAiFeature, String) -> Unit,
    onCloudTtsStart: (ReaderTtsReadScope, List<ReaderTtsChunk>) -> Unit,
    onCloudTtsPauseResume: () -> Unit,
    onCloudTtsStop: () -> Unit,
    onCloudTtsClearCache: () -> Unit,
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    ttsReplacementBookId: String,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit
) {
    val settings = aiByokSettings.sanitized()
    val currentPageText = ReaderContextExtractor.currentPageText(session)
    val currentChapterText = ReaderContextExtractor.currentChapterText(session)
    val recapText = ReaderContextExtractor.textBeforeCurrentLocation(session)

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (externalLookupAvailable) {
            SharedReaderPanelSection("External Apps") {
                SharedReaderChoiceRow {
                    ReaderExternalLookupAction.entries.forEach { action ->
                        FilterChip(
                            selected = false,
                            enabled = currentPageText.isNotBlank(),
                            onClick = { onExternalLookup(action, currentPageText) },
                            label = { Text(action.title) }
                        )
                    }
                }
            }
        }

        SharedReaderPanelSection("Auto Scroll") {
            val autoScroll = extrasState.autoScroll.sanitized()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto scroll", modifier = Modifier.weight(1f))
                Switch(
                    checked = autoScroll.enabled,
                    onCheckedChange = { enabled -> onAutoScrollChange(autoScroll.copy(enabled = enabled)) }
                )
            }
            SharedReaderSettingSlider(
                label = "Speed",
                value = autoScroll.speed,
                onValueChange = { speed -> onAutoScrollChange(autoScroll.copy(speed = speed).sanitized()) },
                valueRange = 12f..160f,
                valueLabel = "${autoScroll.speed.roundToInt()}"
            )
        }

        if (cloudTtsControlsAvailable) {
            SharedReaderPanelSection("Cloud TTS") {
                val ttsBusy = extrasState.cloudTts.isLoading || extrasState.cloudTts.isPlaying || extrasState.cloudTts.isPaused
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when {
                                extrasState.cloudTts.isLoading -> "Preparing audio"
                                extrasState.cloudTts.isPaused -> "Paused"
                                extrasState.cloudTts.isPlaying -> "Reading"
                                settings.isCloudTtsAvailable -> "Ready"
                                else -> "Needs Gemini key"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                        val errorMessage = extrasState.cloudTts.errorMessage?.takeIf { it.isNotBlank() }
                        val statusMessage = extrasState.cloudTts.progress.currentPositionLabel
                            ?: extrasState.cloudTts.statusMessage?.takeIf { it.isNotBlank() }
                        when {
                            errorMessage != null -> Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            statusMessage != null -> Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(
                        enabled = settings.isCloudTtsAvailable || ttsBusy,
                        onClick = {
                            if (ttsBusy) {
                                onCloudTtsStop()
                            } else {
                                onCloudTtsStart(
                                    ReaderTtsReadScope.BOOK,
                                    ReaderTtsPlanner.chunksFromCurrentLocation(session)
                                )
                            }
                        }
                    ) {
                        Text(if (ttsBusy) "Stop" else "Read")
                    }
                }
                if (extrasState.cloudTts.isPlaying || extrasState.cloudTts.isPaused) {
                    SharedReaderChoiceRow {
                        TextButton(onClick = onCloudTtsPauseResume) {
                            Text(if (extrasState.cloudTts.isPaused) "Resume" else "Pause")
                        }
                    }
                }
                SharedReaderChoiceRow {
                    TextButton(
                        enabled = settings.isCloudTtsAvailable && !ttsBusy && currentPageText.isNotBlank(),
                        onClick = {
                            onCloudTtsStart(
                                ReaderTtsReadScope.PAGE,
                                ReaderTtsPlanner.chunksForCurrentPage(session)
                            )
                        }
                    ) {
                        Text("Page")
                    }
                    TextButton(
                        enabled = settings.isCloudTtsAvailable && !ttsBusy && currentChapterText.isNotBlank(),
                        onClick = {
                            onCloudTtsStart(
                                ReaderTtsReadScope.CHAPTER,
                                ReaderTtsPlanner.chunksForCurrentChapter(session)
                            )
                        }
                    ) {
                        Text("Chapter")
                    }
                    TextButton(
                        enabled = settings.isCloudTtsAvailable && !ttsBusy && currentPageText.isNotBlank(),
                        onClick = {
                            onCloudTtsStart(
                                ReaderTtsReadScope.BOOK,
                                ReaderTtsPlanner.chunksFromCurrentLocation(session)
                            )
                        }
                    ) {
                        Text("From here")
                    }
                }
                val cacheSummary = extrasState.cloudTts.cacheSummary
                if (cacheSummary.hasCachedAudio) {
                    Text(
                        "Cache: ${cacheSummary.currentVoiceLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (cacheSummary.hasCurrentVoiceCachedAudio) {
                        TextButton(onClick = onCloudTtsClearCache) {
                            Text("Clear voice cache")
                        }
                    }
                }
            }
        }

        if (toolbarPreferences.isVisible(ReaderTool.TTS_REPLACEMENTS)) {
            SharedReaderTtsReplacementControls(
                preferences = ttsReplacementPreferences,
                bookId = ttsReplacementBookId,
                onPreferencesChange = onTtsReplacementPreferencesChange
            )
        }

        if (settings.areReaderAiFeaturesAvailable) {
            SharedReaderPanelSection("AI") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    TextButton(
                        enabled = currentPageText.isNotBlank() && !extrasState.aiResult.isLoading,
                        onClick = { onAiAction(ReaderAiFeature.DEFINE, currentPageText.take(1200)) }
                    ) {
                        Text("Define page")
                    }
                    TextButton(
                        enabled = currentChapterText.isNotBlank() && !extrasState.aiResult.isLoading,
                        onClick = { onAiAction(ReaderAiFeature.SUMMARIZE, currentChapterText) }
                    ) {
                        Text("Summarize chapter")
                    }
                    TextButton(
                        enabled = recapText.isNotBlank() && !extrasState.aiResult.isLoading,
                        onClick = { onAiAction(ReaderAiFeature.RECAP, recapText) }
                    ) {
                        Text("Recap")
                    }
                }
                if (extrasState.aiResult.hasContent) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(extrasState.aiResult.title ?: "AI", fontWeight = FontWeight.SemiBold)
                            when {
                                extrasState.aiResult.isLoading -> Text("Working...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                extrasState.aiResult.errorMessage != null -> Text(extrasState.aiResult.errorMessage, color = MaterialTheme.colorScheme.error)
                                else -> SharedMarkdownText(extrasState.aiResult.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class SharedTtsReplacementScope {
    GLOBAL,
    BOOK
}

@Composable
fun SharedReaderTtsReplacementControls(
    preferences: ReaderTtsReplacementPreferences,
    bookId: String,
    onPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    allowBookScope: Boolean = true
) {
    var selectedScope by remember(bookId, allowBookScope) { mutableStateOf(SharedTtsReplacementScope.GLOBAL) }
    val effectiveScope = if (allowBookScope) selectedScope else SharedTtsReplacementScope.GLOBAL
    var editingRuleId by remember(bookId, effectiveScope) { mutableStateOf<String?>(null) }
    var isAddingRule by remember(bookId, effectiveScope) { mutableStateOf(false) }
    val bookSettings = preferences.settingsForBook(bookId)
    val bookRules = preferences.rulesForBook(bookId)

    SharedReaderPanelSection("TTS Word Replacements") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Replace only what is spoken", fontWeight = FontWeight.SemiBold)
                Text(
                    "Reader text, highlights, and locations stay unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = preferences.isEnabled,
                onCheckedChange = { onPreferencesChange(preferences.copy(isEnabled = it)) }
            )
        }

        if (allowBookScope) {
            SharedReaderChoiceRow {
                FilterChip(
                    selected = selectedScope == SharedTtsReplacementScope.GLOBAL,
                    onClick = {
                        selectedScope = SharedTtsReplacementScope.GLOBAL
                        editingRuleId = null
                        isAddingRule = false
                    },
                    label = { Text("Global") }
                )
                FilterChip(
                    selected = selectedScope == SharedTtsReplacementScope.BOOK,
                    onClick = {
                        selectedScope = SharedTtsReplacementScope.BOOK
                        editingRuleId = null
                        isAddingRule = false
                    },
                    label = { Text("This book") }
                )
            }
        }

        when (effectiveScope) {
            SharedTtsReplacementScope.GLOBAL -> {
                SharedTtsReplacementSuggestionsRow { suggestion ->
                    onPreferencesChange(
                        preferences.copy(
                            globalRules = preferences.globalRules + suggestion.asDesktopEditableRule(
                                prefix = "global",
                                existingRules = preferences.globalRules
                            )
                        )
                    )
                }
                TextButton(onClick = { isAddingRule = true; editingRuleId = null }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add rule")
                }
                val editingRule = editingRuleId?.let { id -> preferences.globalRules.firstOrNull { it.id == id } }
                if (isAddingRule || editingRule != null) {
                    SharedTtsReplacementRuleEditor(
                        seedRule = editingRule,
                        newRuleId = newSharedReplacementRuleId("global", preferences.globalRules),
                        onCancel = { isAddingRule = false; editingRuleId = null },
                        onSave = { rule ->
                            val updated = if (editingRule == null) {
                                preferences.globalRules + rule
                            } else {
                                preferences.globalRules.map { if (it.id == editingRule.id) rule else it }
                            }
                            onPreferencesChange(preferences.copy(globalRules = updated))
                            isAddingRule = false
                            editingRuleId = null
                        }
                    )
                }
                SharedTtsReplacementRuleList(
                    rules = preferences.globalRules,
                    emptyText = "No global rules yet.",
                    onToggle = { rule, enabled ->
                        onPreferencesChange(
                            preferences.copy(
                                globalRules = preferences.globalRules.map {
                                    if (it.id == rule.id) it.copy(enabled = enabled) else it
                                }
                            )
                        )
                    },
                    onEdit = { rule -> editingRuleId = rule.id; isAddingRule = false },
                    onDelete = { rule ->
                        onPreferencesChange(preferences.copy(globalRules = preferences.globalRules.filterNot { it.id == rule.id }))
                    }
                )
            }

            SharedTtsReplacementScope.BOOK -> {
                SharedTtsBookReplacementSettings(
                    settings = bookSettings,
                    onSettingsChange = { onPreferencesChange(preferences.withBookSettings(bookId, it)) }
                )
                SharedTtsInheritedGlobalRules(
                    globalRules = preferences.globalRules,
                    settings = bookSettings,
                    onSettingsChange = { onPreferencesChange(preferences.withBookSettings(bookId, it)) }
                )
                SharedTtsReplacementSuggestionsRow { suggestion ->
                    onPreferencesChange(
                        preferences.withBookRules(
                            bookId,
                            bookRules + suggestion.asDesktopEditableRule(
                                prefix = "book",
                                existingRules = bookRules
                            )
                        )
                    )
                }
                TextButton(onClick = { isAddingRule = true; editingRuleId = null }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add book rule")
                }
                val editingRule = editingRuleId?.let { id -> bookRules.firstOrNull { it.id == id } }
                if (isAddingRule || editingRule != null) {
                    SharedTtsReplacementRuleEditor(
                        seedRule = editingRule,
                        newRuleId = newSharedReplacementRuleId("book", bookRules),
                        onCancel = { isAddingRule = false; editingRuleId = null },
                        onSave = { rule ->
                            val updated = if (editingRule == null) {
                                bookRules + rule
                            } else {
                                bookRules.map { if (it.id == editingRule.id) rule else it }
                            }
                            onPreferencesChange(preferences.withBookRules(bookId, updated))
                            isAddingRule = false
                            editingRuleId = null
                        }
                    )
                }
                SharedTtsReplacementRuleList(
                    rules = bookRules,
                    emptyText = "No book rules yet.",
                    onToggle = { rule, enabled ->
                        onPreferencesChange(
                            preferences.withBookRules(
                                bookId,
                                bookRules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it }
                            )
                        )
                    },
                    onEdit = { rule -> editingRuleId = rule.id; isAddingRule = false },
                    onDelete = { rule ->
                        onPreferencesChange(preferences.withBookRules(bookId, bookRules.filterNot { it.id == rule.id }))
                    }
                )
            }
        }
    }
}

@Composable
private fun SharedTtsReplacementSuggestionsRow(
    onSuggestionClick: (ReaderTtsReplacementRule) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Suggestions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            ReaderTtsReplacementSuggestions.presets.forEach { suggestion ->
                FilterChip(
                    selected = false,
                    onClick = { onSuggestionClick(suggestion) },
                    label = { Text(suggestion.desktopSummary(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
    }
}

@Composable
private fun SharedTtsBookReplacementSettings(
    settings: ReaderTtsReplacementBookSettings,
    onSettingsChange: (ReaderTtsReplacementBookSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Use global rules here", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.globalRulesEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(globalRulesEnabled = it)) }
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Enable book rules", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.localRulesEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(localRulesEnabled = it)) }
            )
        }
    }
}

@Composable
private fun SharedTtsInheritedGlobalRules(
    globalRules: List<ReaderTtsReplacementRule>,
    settings: ReaderTtsReplacementBookSettings,
    onSettingsChange: (ReaderTtsReplacementBookSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Inherited global rules", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (globalRules.isEmpty()) {
            Text("No global rules to inherit.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            globalRules.forEach { rule ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rule.desktopSummary(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (rule.id in settings.disabledGlobalRuleIds) "Disabled for this book" else "Enabled for this book",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = rule.id !in settings.disabledGlobalRuleIds,
                        onCheckedChange = { enabled ->
                            val disabledIds = if (enabled) {
                                settings.disabledGlobalRuleIds - rule.id
                            } else {
                                settings.disabledGlobalRuleIds + rule.id
                            }
                            onSettingsChange(settings.copy(disabledGlobalRuleIds = disabledIds))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedTtsReplacementRuleEditor(
    seedRule: ReaderTtsReplacementRule?,
    newRuleId: String,
    onCancel: () -> Unit,
    onSave: (ReaderTtsReplacementRule) -> Unit
) {
    val seedId = seedRule?.id ?: newRuleId
    var from by remember(seedId) { mutableStateOf(seedRule?.from.orEmpty()) }
    var to by remember(seedId) { mutableStateOf(seedRule?.to.orEmpty()) }
    var enabled by remember(seedId) { mutableStateOf(seedRule?.enabled ?: true) }
    var isRegex by remember(seedId) { mutableStateOf(seedRule?.isRegex ?: false) }
    var wholeWord by remember(seedId) { mutableStateOf(seedRule?.wholeWord ?: true) }
    var matchCase by remember(seedId) { mutableStateOf(seedRule?.matchCase ?: false) }
    var previewText by remember(seedId) { mutableStateOf(seedRule?.from?.takeIf { it.isNotBlank() } ?: "Dr. Smith met NASA.") }
    val draft = ReaderTtsReplacementRule(
        id = seedId,
        from = from,
        to = to,
        enabled = enabled,
        isRegex = isRegex,
        matchCase = matchCase,
        wholeWord = wholeWord
    )
    val validation = ReaderTtsReplacementEngine.validate(draft)
    val previewOutput = if (validation.isValid) {
        ReaderTtsReplacementEngine.apply(
            text = previewText,
            preferences = ReaderTtsReplacementPreferences(globalRules = listOf(draft.copy(enabled = true)))
        ).text
    } else {
        previewText
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (seedRule == null) "New rule" else "Edit rule", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = from,
                onValueChange = { from = it },
                label = { Text("Replace") },
                modifier = Modifier.fillMaxWidth(),
                isError = !validation.isValid
            )
            if (!validation.isValid && validation.message != null) {
                Text(validation.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("Speak as") },
                modifier = Modifier.fillMaxWidth()
            )
            SharedReaderChoiceRow {
                FilterChip(selected = enabled, onClick = { enabled = !enabled }, label = { Text("Enabled") })
                FilterChip(selected = isRegex, onClick = { isRegex = !isRegex }, label = { Text("Regex") })
                FilterChip(selected = wholeWord, onClick = { wholeWord = !wholeWord }, label = { Text("Whole word") })
                FilterChip(selected = matchCase, onClick = { matchCase = !matchCase }, label = { Text("Match case") })
            }
            OutlinedTextField(
                value = previewText,
                onValueChange = { previewText = it },
                label = { Text("Preview") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(previewOutput, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(enabled = validation.isValid, onClick = { onSave(draft) }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun SharedTtsReplacementRuleList(
    rules: List<ReaderTtsReplacementRule>,
    emptyText: String,
    onToggle: (ReaderTtsReplacementRule, Boolean) -> Unit,
    onEdit: (ReaderTtsReplacementRule) -> Unit,
    onDelete: (ReaderTtsReplacementRule) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rules.isEmpty()) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rules.forEach { rule ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.desktopSummary(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(rule.desktopOptions(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = rule.enabled, onCheckedChange = { onToggle(rule, it) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onEdit(rule) }) { Text("Edit") }
                        TextButton(onClick = { onDelete(rule) }) { Text("Delete") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun ReaderTtsReplacementRule.asDesktopEditableRule(
    prefix: String,
    existingRules: List<ReaderTtsReplacementRule>
): ReaderTtsReplacementRule {
    return copy(
        id = newSharedReplacementRuleId(prefix, existingRules + this),
        enabled = true
    )
}

private fun ReaderTtsReplacementRule.desktopSummary(): String {
    val replacement = to.ifBlank { "silence" }
    return "$from -> $replacement"
}

private fun ReaderTtsReplacementRule.desktopOptions(): String {
    val options = buildList {
        add(if (isRegex) "Regex" else "Plain text")
        if (wholeWord) add("whole word")
        if (matchCase) add("case-sensitive")
    }
    return options.joinToString(" - ")
}

private fun newSharedReplacementRuleId(
    prefix: String,
    existingRules: List<ReaderTtsReplacementRule>
): String {
    val stableSuffix = existingRules.joinToString("|") { it.id }.hashCode().toString().replace("-", "n")
    return "${prefix}_${existingRules.size + 1}_$stableSuffix"
}

@Composable
fun SharedReaderToolbarControls(
    toolbarPreferences: ReaderToolbarPreferences,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit
) {
    val orderedTools = toolbarPreferences.sanitized().toolOrder
    val toolbarTools = orderedTools.filter { it.category != "Overflow Menu" }
    val moreTools = orderedTools.filter { it.category == "Overflow Menu" }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SharedToolbarSection(
            title = "Top Bar",
            tools = toolbarTools.filter {
                toolbarPreferences.isVisible(it) && !toolbarPreferences.isBottom(it)
            },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
        SharedToolbarSection(
            title = "Bottom Bar",
            tools = toolbarTools.filter {
                toolbarPreferences.isVisible(it) && toolbarPreferences.isBottom(it)
            },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
        SharedToolbarSection(
            title = "More Menu",
            tools = moreTools.filter { toolbarPreferences.isVisible(it) },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
        SharedToolbarSection(
            title = "Hidden Tools",
            tools = orderedTools.filterNot { toolbarPreferences.isVisible(it) },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
    }
}

@Composable
private fun SharedToolbarSection(
    title: String,
    tools: List<ReaderTool>,
    toolbarPreferences: ReaderToolbarPreferences,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit
) {
    SharedReaderPanelSection(title) {
        if (tools.isEmpty()) {
            Text("No tools", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            tools.forEach { tool ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(tool.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = toolbarPreferences.isVisible(tool),
                            onClick = {
                                onToolbarPreferencesChange(
                                    toolbarPreferences.withVisibility(tool, hidden = toolbarPreferences.isVisible(tool))
                                )
                            },
                            label = { Text("Visible") }
                        )
                        FilterChip(
                            selected = toolbarPreferences.isBottom(tool),
                            enabled = tool.category != "Overflow Menu",
                            onClick = {
                                onToolbarPreferencesChange(
                                    toolbarPreferences.withBottomPlacement(tool, bottom = !toolbarPreferences.isBottom(tool))
                                )
                            },
                            label = { Text("Bottom") }
                        )
                        TextButton(
                            enabled = toolbarPreferences.toolOrder.indexOf(tool) > 0,
                            onClick = { onToolbarPreferencesChange(toolbarPreferences.moveTool(tool, -1)) }
                        ) {
                            Text("Up")
                        }
                        TextButton(
                            enabled = toolbarPreferences.toolOrder.indexOf(tool) in 0 until toolbarPreferences.toolOrder.lastIndex,
                            onClick = { onToolbarPreferencesChange(toolbarPreferences.moveTool(tool, 1)) }
                        ) {
                            Text("Down")
                        }
                    }
                }
                if (tool != tools.last()) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SharedReaderPanelSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SharedReaderChoiceRow(
    content: @Composable () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        content()
    }
}

@Composable
private fun SharedReaderSettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@Composable
private fun SharedReaderThemeChoice(
    theme: com.aryan.reader.shared.ReaderTheme,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swatch = if (theme.backgroundColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else {
        theme.backgroundColor
    }
    val textColor = if (theme.textColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        theme.textColor
    }
    Column(
        modifier = modifier.clickable(onClick = onSelected),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else swatch,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(32.dp)
                    .background(swatch, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Aa", color = textColor, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            theme.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SharedReaderCompactNavigation(
    session: ReaderSessionState,
    showSlider: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    pageInfoText: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageNumberChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            enabled = canGoPrevious,
            onClick = onPrevious
        ) {
            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous page")
        }
        if (showSlider) {
            SharedReaderPageSlider(
                session = session,
                onPageNumberChange = onPageNumberChange,
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                pageInfoText.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        IconButton(
            enabled = canGoNext,
            onClick = onNext
        ) {
            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next page")
        }
    }
}

@Composable
private fun SharedReaderPageSlider(
    session: ReaderSessionState,
    onPageNumberChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val readerState = session.reader
    val totalPages = readerState.pages.size.coerceAtLeast(1)
    val sliderMax = totalPages.coerceAtLeast(2)
    val currentPageNumber = (readerState.currentPageIndex + 1).coerceIn(1, totalPages)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$currentPageNumber / $totalPages",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ReaderMinimalSlider(
            value = currentPageNumber.toFloat(),
            onValueChange = { value -> onPageNumberChange(value.roundToInt().coerceIn(1, totalPages)) },
            valueRange = 1f..sliderMax.toFloat(),
            enabled = totalPages > 1,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SharedReaderSidebar(
    session: ReaderSessionState,
    sections: List<ReaderWorkspaceLeftSection>,
    onGoToChapter: (Int) -> Unit,
    onGoToBookmark: (ReaderBookmark) -> Unit,
    onGoToHighlight: (UserHighlight) -> Unit
) {
    val tabs = remember(sections) {
        listOf(
            ReaderWorkspaceLeftSection.CONTENTS,
            ReaderWorkspaceLeftSection.NOTES,
            ReaderWorkspaceLeftSection.BOOKMARKS
        ).filter { it in sections }
    }
    var selectedSection by remember(tabs) { mutableStateOf(tabs.firstOrNull()) }
    val selectedTabIndex = tabs.indexOf(selectedSection).takeIf { it >= 0 } ?: 0

    Surface(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            if (tabs.isNotEmpty()) {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEach { section ->
                        Tab(
                            selected = selectedSection == section,
                            onClick = { selectedSection = section },
                            text = { Text(section.readerNavigationTabLabel()) }
                        )
                    }
                }
            }

            when (selectedSection) {
                ReaderWorkspaceLeftSection.CONTENTS -> SharedReaderTocTab(
                    session = session,
                    onGoToChapter = onGoToChapter
                )
                ReaderWorkspaceLeftSection.NOTES -> SharedReaderAnnotationsTab(
                    session = session,
                    onGoToHighlight = onGoToHighlight
                )
                ReaderWorkspaceLeftSection.BOOKMARKS -> SharedReaderBookmarksTab(
                    session = session,
                    onGoToBookmark = onGoToBookmark
                )
                else -> SharedReaderEmptyNavigation("No navigation items")
            }
        }
    }
}

@Composable
private fun SharedReaderTocTab(
    session: ReaderSessionState,
    onGoToChapter: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(session.reader.book.chapters.indices.toList()) { index ->
            val chapter = session.reader.book.chapters[index]
            val selected = session.reader.currentPage?.chapterIndex == index
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().clickable { onGoToChapter(index) }
            ) {
                Text(
                    chapter.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SharedReaderBookmarksTab(
    session: ReaderSessionState,
    onGoToBookmark: (ReaderBookmark) -> Unit
) {
    if (session.bookmarks.isEmpty()) {
        SharedReaderEmptyNavigation("No bookmarks yet")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(session.bookmarks, key = { it.id }) { bookmark ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onGoToBookmark(bookmark) }
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                    ) {
                        Text(bookmark.chapterTitle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(bookmark.preview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedReaderAnnotationsTab(
    session: ReaderSessionState,
    onGoToHighlight: (UserHighlight) -> Unit
) {
    if (session.highlights.isEmpty()) {
        SharedReaderEmptyNavigation("No annotations yet")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(session.highlights, key = { it.id }) { highlight ->
                val locator = highlight.locator.withFallbacks(
                    chapterIndex = highlight.chapterIndex,
                    cfi = highlight.cfi,
                    textQuote = highlight.text
                )
                val chapterTitle = session.reader.book.chapters
                    .getOrNull(locator.chapterIndex ?: highlight.chapterIndex)
                    ?.title
                    ?: "Chapter ${(locator.chapterIndex ?: highlight.chapterIndex) + 1}"
                val pageLabel = locator.pageIndex?.let { "Page ${it + 1}" }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onGoToHighlight(highlight) }
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .height(12.dp)
                                    .background(highlight.color.color, RoundedCornerShape(2.dp))
                            )
                            Text(
                                listOfNotNull(chapterTitle, pageLabel).joinToString(" - "),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(highlight.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        highlight.note?.takeIf { it.isNotBlank() }?.let { note ->
                            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedReaderEmptyNavigation(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun ReaderWorkspaceLeftSection.readerNavigationTabLabel(): String {
    return when (this) {
        ReaderWorkspaceLeftSection.CONTENTS -> "TOC"
        ReaderWorkspaceLeftSection.NOTES -> "Annotations"
        ReaderWorkspaceLeftSection.BOOKMARKS -> "Bookmarks"
        ReaderWorkspaceLeftSection.PAGES -> "Pages"
        ReaderWorkspaceLeftSection.SEARCH -> "Search"
    }
}

@Composable
private fun SharedHighlightListItem(
    session: ReaderSessionState,
    highlight: UserHighlight,
    palette: ReaderHighlightPalette,
    onGoToHighlight: (UserHighlight) -> Unit,
    onColorChange: (UserHighlight, HighlightColor) -> Unit,
    onNoteChange: (UserHighlight, String) -> Unit,
    onDelete: (UserHighlight) -> Unit
) {
    val locator = highlight.locator.withFallbacks(
        chapterIndex = highlight.chapterIndex,
        cfi = highlight.cfi,
        textQuote = highlight.text
    )
    val chapterTitle = session.reader.book.chapters
        .getOrNull(locator.chapterIndex ?: highlight.chapterIndex)
        ?.title
        ?: "Chapter ${(locator.chapterIndex ?: highlight.chapterIndex) + 1}"
    val pageLabel = locator.pageIndex?.let { "Page ${it + 1}" }
    val colors = palette.sanitized().colors

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().clickable { onGoToHighlight(highlight) }
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(12.dp)
                        .background(highlight.color.color, RoundedCornerShape(2.dp))
                )
                Text(
                    listOfNotNull(chapterTitle, pageLabel).joinToString(" - "),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(highlight.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                colors.forEach { color ->
                    FilterChip(
                        selected = highlight.color == color,
                        onClick = { onColorChange(highlight, color) },
                        label = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(10.dp)
                                        .height(10.dp)
                                        .background(color.color, RoundedCornerShape(2.dp))
                                )
                                Text(color.id)
                            }
                        }
                    )
                }
            }
            OutlinedTextField(
                value = highlight.note.orEmpty(),
                onValueChange = { onNoteChange(highlight, it) },
                label = { Text("Note") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { onDelete(highlight) }) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun SharedHighlightPaletteEditor(
    palette: ReaderHighlightPalette,
    onPaletteChange: (ReaderHighlightPalette) -> Unit
) {
    val sanitized = palette.sanitized()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Palette", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            HighlightColor.entries.forEach { color ->
                FilterChip(
                    selected = sanitized.contains(color),
                    onClick = {
                        onPaletteChange(sanitized.withColor(color, enabled = !sanitized.contains(color)))
                    },
                    label = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(10.dp)
                                    .background(color.color, RoundedCornerShape(2.dp))
                            )
                            Text(color.id)
                        }
                    }
                )
            }
        }
    }
}

private fun Float.formatTwoDecimals(): String {
    val scaled = (this * 100).toInt()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}

private fun ReaderToolbarPreferences.moveTool(tool: ReaderTool, delta: Int): ReaderToolbarPreferences {
    val order = sanitized().toolOrder.toMutableList()
    val index = order.indexOf(tool)
    if (index < 0) return this
    val target = (index + delta).coerceIn(0, order.lastIndex)
    if (index == target) return this
    val moved = order.removeAt(index)
    order.add(target, moved)
    return withToolOrder(order)
}

@Composable
private fun SharedReaderJumpHistoryBar(
    session: ReaderSessionState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClear: () -> Unit
) {
    val history = session.jumpHistory
    val back = history.backLocator
    val forward = history.forwardLocator
    if (back == null && forward == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(
            onClick = onBack,
            enabled = back != null,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Jump back")
            Text(
                back?.jumpLabel(session).orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(
            onClick = onClear,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Clear jump history")
            Text("Clear", maxLines = 1)
        }
        TextButton(
            onClick = onForward,
            enabled = forward != null,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                forward?.jumpLabel(session).orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Jump forward")
        }
    }
}

private fun ReaderLocator.jumpLabel(session: ReaderSessionState): String {
    val targetPageIndex = pageIndex
    val targetCfi = cfi.orEmpty()
    if (targetPageIndex != null && targetCfi.isBlank()) {
        return "Page ${targetPageIndex + 1}"
    }
    val chapter = chapterIndex
    return if (chapter != null) {
        session.reader.book.chapters.getOrNull(chapter)?.title?.takeIf { it.isNotBlank() } ?: "Chapter ${chapter + 1}"
    } else {
        "Location"
    }
}

private fun Long.toComposeColor(): Color {
    val value = this and 0xFFFFFFFFL
    val alpha = ((value shr 24) and 0xFF) / 255f
    val red = ((value shr 16) and 0xFF) / 255f
    val green = ((value shr 8) and 0xFF) / 255f
    val blue = (value and 0xFF) / 255f
    return Color(red = red, green = green, blue = blue, alpha = alpha.takeIf { it > 0f } ?: 1f)
}

private fun PaginatedReaderState.pageInfoText(): String {
    val current = currentPageIndex + 1
    val total = pages.size.coerceAtLeast(1)
    val percent = progress.roundToInt().coerceIn(0, 100)
    val mode = if (settings.readingMode == ReaderReadingMode.VERTICAL) "Continuous" else "Page"
    val chapter = currentPage?.chapterTitle?.takeIf { it.isNotBlank() }
    return listOfNotNull("$mode $current of $total ($percent%)", chapter).joinToString(" - ")
}

private fun PaginatedReaderState.currentPageLocator(): ReaderLocator? {
    val page = currentPage ?: return null
    val chapter = book.chapters.getOrNull(page.chapterIndex)
    return ReaderLocator(
        chapterIndex = page.chapterIndex,
        chapterId = chapter?.id,
        href = chapter?.baseHref,
        pageIndex = page.pageIndex,
        startOffset = page.startOffset,
        endOffset = page.endOffset,
        textQuote = page.text.trim().replace(Regex("\\s+"), " ").take(140),
        cfi = "desktop:${page.chapterIndex}:${page.startOffset}:${page.endOffset}"
    )
}
