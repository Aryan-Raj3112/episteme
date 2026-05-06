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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.PageInfoMode
import com.aryan.reader.shared.PageInfoPosition
import com.aryan.reader.shared.ReaderAction
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderTexture
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.SystemUiMode
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.reader.PaginatedReaderState
import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSearchOptions
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import kotlin.math.roundToInt

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
    onOpenEpub: () -> Unit,
    onOpenPdf: () -> Unit,
    toolbarPreferences: ReaderToolbarPreferences = ReaderToolbarPreferences(),
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit = {},
    highlightPalette: ReaderHighlightPalette = ReaderHighlightPalette(),
    onHighlightPaletteChange: (ReaderHighlightPalette) -> Unit = {},
    onPickCustomFont: (() -> String?)? = null,
    readerContent: @Composable ColumnScope.(html: String, background: Color) -> Unit
) {
    val readerState = session.reader
    val page = readerState.currentPage
    val settings = readerState.settings
    val background = settings.backgroundColorArgb?.toComposeColor() ?: if (settings.darkMode) Color(0xFF171A17) else Color(0xFFFFFCF5)
    val pageInfoText = readerState.pageInfoText()
    val shouldShowPageInfo = settings.pageInfoMode != PageInfoMode.HIDDEN
    fun dispatch(action: ReaderAction) {
        onSessionChange(session.reduce(action, readerEngine))
    }

    SharedScreenScaffold(
        title = readerState.book.title,
        subtitle = listOfNotNull(readerState.book.author, page?.chapterTitle).joinToString(" - "),
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenEpub) {
                    Text("Open EPUB")
                }
                TextButton(onClick = onOpenPdf) {
                    Text("Open PDF")
                }
                Text("${readerState.progress.toInt()}%")
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
                    }
                )
            }
        }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                            dispatch(ReaderAction.GoToPage(0))
                            true
                        }

                        event.key == Key.MoveEnd -> {
                            dispatch(ReaderAction.GoToPage(readerState.pages.lastIndex))
                            true
                        }

                        event.isCtrlPressed && event.key == Key.G -> {
                            dispatch(ReaderAction.NextSearchResult)
                            true
                        }

                        event.isCtrlPressed && event.key == Key.F -> {
                            dispatch(ReaderAction.SearchOpened)
                            true
                        }

                        else -> false
                    }
                }
                .focusable()
        ) {
            SharedReaderSidebar(
                session = session,
                onSearchChange = { dispatch(ReaderAction.SearchChanged(it)) },
                onPreviousSearchResult = { dispatch(ReaderAction.PreviousSearchResult) },
                onNextSearchResult = { dispatch(ReaderAction.NextSearchResult) },
                onOpenSearch = { dispatch(ReaderAction.SearchOpened) },
                onCloseSearch = { dispatch(ReaderAction.SearchClosed) },
                onToggleSearchResultsPanel = { dispatch(ReaderAction.SearchResultsPanelToggled) },
                onSearchOptionsChange = { dispatch(ReaderAction.SearchOptionsChanged(it)) },
                onGoToChapter = { dispatch(ReaderAction.GoToChapter(it)) },
                onGoToBookmark = { dispatch(ReaderAction.GoToLocator(it.locator)) },
                onGoToSearchResult = { dispatch(ReaderAction.GoToSearchResult(it)) },
                toolbarPreferences = toolbarPreferences,
                highlightPalette = highlightPalette,
                onHighlightPaletteChange = onHighlightPaletteChange,
                onGoToHighlight = { dispatch(ReaderAction.GoToLocator(it.locator)) },
                onHighlightColorChange = { highlight, color ->
                    dispatch(ReaderAction.HighlightUpdated(highlight.id, color = color))
                },
                onHighlightNoteChange = { highlight, note ->
                    dispatch(ReaderAction.HighlightUpdated(highlight.id, note = note))
                },
                onHighlightDelete = { highlight ->
                    dispatch(ReaderAction.HighlightDeleted(highlight.id))
                }
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (shouldShowPageInfo && settings.pageInfoPosition == PageInfoPosition.TOP) {
                    Text(pageInfoText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                val html = if (settings.readingMode == ReaderReadingMode.VERTICAL) {
                    ReaderHtmlDocumentBuilder.verticalDocument(
                        book = readerState.book,
                        settings = settings,
                        searchQuery = session.searchQuery,
                        searchOptions = session.searchOptions,
                        highlights = session.highlights,
                        highlightPalette = highlightPalette
                    )
                } else {
                    ReaderHtmlDocumentBuilder.pageDocument(
                        book = readerState.book,
                        page = page,
                        settings = settings,
                        searchQuery = session.searchQuery,
                        searchOptions = session.searchOptions,
                        highlights = session.highlights,
                        highlightPalette = highlightPalette
                    )
                }
                readerContent(html, background)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (toolbarPreferences.isVisible(ReaderTool.SLIDER)) {
                        SharedReaderPageSlider(
                            session = session,
                            onPageNumberChange = { pageNumber -> dispatch(ReaderAction.GoToPageNumber(pageNumber)) }
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            enabled = readerState.canGoPrevious,
                            onClick = { dispatch(ReaderAction.PreviousPage) }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                            Text("Previous")
                        }
                        Spacer(Modifier.weight(1f))
                        if (shouldShowPageInfo && settings.pageInfoPosition == PageInfoPosition.BOTTOM) {
                            Text(pageInfoText)
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            enabled = readerState.canGoNext,
                            onClick = { dispatch(ReaderAction.NextPage) }
                        ) {
                            Text("Next")
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                        }
                    }
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
                        }
                    )
                }
            }

            SharedReaderControlPanel(
                session = session,
                toolbarPreferences = toolbarPreferences,
                onToolbarPreferencesChange = onToolbarPreferencesChange,
                onPickCustomFont = onPickCustomFont,
                onReaderAction = { action -> dispatch(action) }
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
    onToggleSearch: () -> Unit
) {
    val tools = toolbarPreferences.orderedVisibleTools()
        .filter { it.supportsDesktopQuickAction && toolbarPreferences.isBottom(it) == bottom }
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

                ReaderTool.THEME -> TextButton(onClick = onToggleTheme) {
                    Text(if (isDarkMode) "Light" else "Dark")
                }

                ReaderTool.SEARCH -> IconButton(onClick = onToggleSearch) {
                    Icon(
                        if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search"
                    )
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
    onReaderAction: (ReaderAction) -> Unit
) {
    val sections = toolbarPreferences.availableReaderControlSections()
    if (sections.isEmpty()) return
    var selectedSection by remember { mutableStateOf(sections.first()) }
    val activeSection = selectedSection.takeIf { it in sections } ?: sections.first()

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
                    ReaderControlSection.FORMAT -> SharedReaderFormatControls(
                        settings = session.reader.settings,
                        toolbarPreferences = toolbarPreferences,
                        onPickCustomFont = onPickCustomFont,
                        onReaderAction = onReaderAction
                    )

                    ReaderControlSection.THEME -> SharedReaderThemeControls(
                        settings = session.reader.settings,
                        onReaderAction = onReaderAction
                    )

                    ReaderControlSection.VISUAL -> SharedReaderVisualOptionsControls(
                        settings = session.reader.settings,
                        onReaderAction = onReaderAction
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
    FORMAT("Format"),
    THEME("Theme"),
    VISUAL("Visual"),
    TOOLBAR("Toolbar")
}

private fun ReaderToolbarPreferences.availableReaderControlSections(): List<ReaderControlSection> {
    return buildList {
        if (isVisible(ReaderTool.FORMAT) || isVisible(ReaderTool.READING_MODE)) add(ReaderControlSection.FORMAT)
        if (isVisible(ReaderTool.THEME)) add(ReaderControlSection.THEME)
        if (isVisible(ReaderTool.VISUAL_OPTIONS)) add(ReaderControlSection.VISUAL)
        add(ReaderControlSection.TOOLBAR)
    }
}

@Composable
private fun SharedReaderFormatControls(
    settings: ReaderSettings,
    toolbarPreferences: ReaderToolbarPreferences,
    onPickCustomFont: (() -> String?)?,
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
private fun SharedReaderThemeControls(
    settings: ReaderSettings,
    onReaderAction: (ReaderAction) -> Unit
) {
    var textured by remember(settings.themeId, settings.textureId) { mutableStateOf(settings.textureId != null) }
    val activeThemes = BuiltInReaderThemes.filter { (it.textureId != null) == textured }

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
                            onSelected = { onReaderAction(ReaderAction.ThemeChanged(theme)) },
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
                        onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(textureId = null))) },
                        label = { Text("None") }
                    )
                    ReaderTexture.entries.forEach { texture ->
                        FilterChip(
                            selected = settings.textureId == texture.id,
                            onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(textureId = texture.id))) },
                            label = { Text(texture.displayName) }
                        )
                    }
                }
                if (settings.textureId != null) {
                    SharedReaderSettingSlider(
                        label = "Texture strength",
                        value = settings.textureAlpha.coerceIn(0f, 1f),
                        onValueChange = { value ->
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textureAlpha = value)))
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
private fun SharedReaderVisualOptionsControls(
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
private fun SharedReaderToolbarControls(
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
private fun SharedReaderPageSlider(
    session: ReaderSessionState,
    onPageNumberChange: (Int) -> Unit
) {
    val readerState = session.reader
    val totalPages = readerState.pages.size.coerceAtLeast(1)
    val sliderMax = totalPages.coerceAtLeast(2)
    val currentPageNumber = (readerState.currentPageIndex + 1).coerceIn(1, totalPages)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$currentPageNumber / $totalPages")
        Slider(
            value = currentPageNumber.toFloat(),
            onValueChange = { value -> onPageNumberChange(value.roundToInt().coerceIn(1, totalPages)) },
            valueRange = 1f..sliderMax.toFloat(),
            steps = if (totalPages > 2) totalPages - 2 else 0,
            enabled = totalPages > 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            readerState.currentPage?.chapterTitle.orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(180.dp)
        )
    }
}

@Composable
private fun SharedReaderSidebar(
    session: ReaderSessionState,
    onSearchChange: (String) -> Unit,
    onPreviousSearchResult: () -> Unit,
    onNextSearchResult: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onToggleSearchResultsPanel: () -> Unit,
    onSearchOptionsChange: (ReaderSearchOptions) -> Unit,
    onGoToChapter: (Int) -> Unit,
    onGoToBookmark: (ReaderBookmark) -> Unit,
    onGoToSearchResult: (Int) -> Unit,
    toolbarPreferences: ReaderToolbarPreferences,
    highlightPalette: ReaderHighlightPalette,
    onHighlightPaletteChange: (ReaderHighlightPalette) -> Unit,
    onGoToHighlight: (UserHighlight) -> Unit,
    onHighlightColorChange: (UserHighlight, HighlightColor) -> Unit,
    onHighlightNoteChange: (UserHighlight, String) -> Unit,
    onHighlightDelete: (UserHighlight) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        LazyColumn(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (toolbarPreferences.isVisible(ReaderTool.TOC)) {
                item {
                    Text("Contents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
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
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (toolbarPreferences.isVisible(ReaderTool.BOOKMARK)) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Bookmarks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (session.bookmarks.isEmpty()) {
                    item {
                        Text("No bookmarks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
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

            if (toolbarPreferences.isVisible(ReaderTool.BOOKMARK)) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Highlights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (session.highlights.isEmpty()) {
                    item {
                        Text("No highlights yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(session.highlights, key = { it.id }) { highlight ->
                        SharedHighlightListItem(
                            session = session,
                            highlight = highlight,
                            palette = highlightPalette,
                            onGoToHighlight = onGoToHighlight,
                            onColorChange = onHighlightColorChange,
                            onNoteChange = onHighlightNoteChange,
                            onDelete = onHighlightDelete
                        )
                    }
                }
                item {
                    SharedHighlightPaletteEditor(
                        palette = highlightPalette,
                        onPaletteChange = onHighlightPaletteChange
                    )
                }
            }

            if (toolbarPreferences.isVisible(ReaderTool.SEARCH)) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Search", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = if (session.isSearchActive) onCloseSearch else onOpenSearch) {
                            Text(if (session.isSearchActive) "Close" else "Open")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (session.isSearchActive) {
                        OutlinedTextField(
                            value = session.searchQuery,
                            onValueChange = onSearchChange,
                            label = { Text("Find in book") },
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
                                    onSearchOptionsChange(session.searchOptions.copy(matchCase = !session.searchOptions.matchCase))
                                },
                                label = { Text("Match case") }
                            )
                            FilterChip(
                                selected = session.searchOptions.wholeWords,
                                onClick = {
                                    onSearchOptionsChange(session.searchOptions.copy(wholeWords = !session.searchOptions.wholeWords))
                                },
                                label = { Text("Whole words") }
                            )
                            if (session.searchQuery.isNotBlank()) {
                                TextButton(onClick = onToggleSearchResultsPanel) {
                                    Text(if (session.showSearchResultsPanel) "Hide results" else "Show results")
                                }
                            }
                        }
                    }
                    if (session.isSearchActive && session.searchQuery.isNotBlank() && session.searchResults.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${session.activeSearchResultIndex + 1} of ${session.searchResults.size}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                enabled = session.canGoToPreviousSearchResult,
                                onClick = onPreviousSearchResult
                            ) {
                                Text("Prev")
                            }
                            TextButton(
                                enabled = session.canGoToNextSearchResult,
                                onClick = onNextSearchResult
                            ) {
                                Text("Next")
                            }
                        }
                    }
                }
                if (session.isSearchActive && session.searchQuery.isNotBlank() && session.searchResults.isEmpty()) {
                    item {
                        Text("No matches", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (session.isSearchActive && session.showSearchResultsPanel) {
                    itemsIndexed(
                        session.searchResults,
                        key = { _, result -> "${result.pageIndex}_${result.matchIndex}_${result.chapterIndex}_${result.preview}" }
                    ) { index, result ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable { onGoToSearchResult(index) }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Page ${result.pageIndex + 1} - ${result.chapterTitle}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val mode = if (settings.readingMode == ReaderReadingMode.VERTICAL) "Continuous" else "Page"
    val chapter = currentPage?.chapterTitle?.takeIf { it.isNotBlank() }
    return listOfNotNull("$mode $current of $total", chapter).joinToString(" - ")
}
