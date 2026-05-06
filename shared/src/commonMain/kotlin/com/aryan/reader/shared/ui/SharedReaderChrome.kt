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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
                onToolbarPreferencesChange = onToolbarPreferencesChange,
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
                SharedReaderSettingsBar(
                    session = session,
                    toolbarPreferences = toolbarPreferences,
                    onPickCustomFont = onPickCustomFont,
                    onReaderAction = { action -> dispatch(action) }
                )

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
private fun SharedReaderSettingsBar(
    session: ReaderSessionState,
    toolbarPreferences: ReaderToolbarPreferences,
    onPickCustomFont: (() -> String?)?,
    onReaderAction: (ReaderAction) -> Unit
) {
    val settings = session.reader.settings
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (toolbarPreferences.isVisible(ReaderTool.READING_MODE)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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

        if (toolbarPreferences.isVisible(ReaderTool.FORMAT)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
            FilterChip(
                selected = settings.textAlign == SharedReaderTextAlign.START,
                onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.START))) },
                label = { Text("Left") }
            )
            FilterChip(
                selected = settings.textAlign == SharedReaderTextAlign.JUSTIFY,
                onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.JUSTIFY))) },
                label = { Text("Justify") }
            )
            FilterChip(
                selected = settings.textAlign == SharedReaderTextAlign.CENTER,
                onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.CENTER))) },
                label = { Text("Center") }
            )
            listOf("Default", "Serif", "Sans", "Mono").forEach { family ->
                FilterChip(
                    selected = settings.customFontPath == null && settings.fontFamily == family,
                    onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(fontFamily = family, customFontPath = null))) },
                    label = { Text(family) }
                )
            }
            TextButton(
                enabled = onPickCustomFont != null,
                onClick = {
                    onPickCustomFont?.invoke()?.takeIf { it.isNotBlank() }?.let { path ->
                        onReaderAction(
                            ReaderAction.SettingsChanged(
                                settings.copy(fontFamily = path.substringAfterLast('/').substringAfterLast('\\'), customFontPath = path)
                            )
                        )
                    }
                }
            ) {
                Text("Custom font")
            }
            if (settings.customFontPath != null) {
                TextButton(
                    onClick = {
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(fontFamily = "Default", customFontPath = null)))
                    }
                ) {
                    Text("Clear custom")
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Text("Font ${settings.fontSize}")
            Slider(
                value = settings.fontSize.toFloat(),
                onValueChange = { value ->
                    onReaderAction(ReaderAction.SettingsChanged(settings.copy(fontSize = value.toInt())))
                },
                valueRange = 14f..30f,
                modifier = Modifier.width(140.dp)
            )
            Text("X ${settings.resolvedHorizontalMargin}")
            Slider(
                value = settings.resolvedHorizontalMargin.toFloat(),
                onValueChange = { value ->
                    val nextHorizontal = value.toInt()
                    val nextMargin = if (nextHorizontal > settings.resolvedVerticalMargin) {
                        nextHorizontal
                    } else {
                        settings.resolvedVerticalMargin
                    }
                    onReaderAction(
                        ReaderAction.SettingsChanged(
                            settings.copy(horizontalMargin = nextHorizontal, margin = nextMargin)
                        )
                    )
                },
                valueRange = 0f..160f,
                modifier = Modifier.width(140.dp)
            )
            Text("Y ${settings.resolvedVerticalMargin}")
            Slider(
                value = settings.resolvedVerticalMargin.toFloat(),
                onValueChange = { value ->
                    val nextVertical = value.toInt()
                    val nextMargin = if (nextVertical > settings.resolvedHorizontalMargin) {
                        nextVertical
                    } else {
                        settings.resolvedHorizontalMargin
                    }
                    onReaderAction(
                        ReaderAction.SettingsChanged(
                            settings.copy(verticalMargin = nextVertical, margin = nextMargin)
                        )
                    )
                },
                valueRange = 0f..160f,
                modifier = Modifier.width(140.dp)
            )
            Text("Spacing ${settings.lineSpacing.formatTwoDecimals()}")
            Slider(
                value = settings.lineSpacing,
                onValueChange = { value ->
                    onReaderAction(ReaderAction.SettingsChanged(settings.copy(lineSpacing = value)))
                },
                valueRange = 1.1f..2.1f,
                modifier = Modifier.width(140.dp)
            )
            Text("Paragraph ${settings.paragraphSpacing.formatTwoDecimals()}")
            Slider(
                value = settings.paragraphSpacing,
                onValueChange = { value ->
                    onReaderAction(ReaderAction.SettingsChanged(settings.copy(paragraphSpacing = value)))
                },
                valueRange = 0.5f..2.5f,
                modifier = Modifier.width(140.dp)
            )
            Text("Images ${settings.imageScale.formatTwoDecimals()}x")
            Slider(
                value = settings.imageScale,
                onValueChange = { value ->
                    onReaderAction(ReaderAction.SettingsChanged(settings.copy(imageScale = value)))
                },
                valueRange = 0.5f..2.0f,
                modifier = Modifier.width(140.dp)
            )
            Text("Width ${settings.pageWidth}")
            Slider(
                value = settings.pageWidth.toFloat(),
                onValueChange = { value ->
                    onReaderAction(ReaderAction.SettingsChanged(settings.copy(pageWidth = value.toInt())))
                },
                valueRange = 520f..1100f,
                modifier = Modifier.width(140.dp)
            )
        }
        }

        if (toolbarPreferences.isVisible(ReaderTool.THEME)) {
            SharedReaderThemeControls(settings = settings, onReaderAction = onReaderAction)
        }

        if (toolbarPreferences.isVisible(ReaderTool.VISUAL_OPTIONS)) {
            SharedReaderVisualOptionsControls(settings = settings, onReaderAction = onReaderAction)
        }
    }
}

@Composable
private fun SharedReaderThemeControls(
    settings: ReaderSettings,
    onReaderAction: (ReaderAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Text("Theme")
            BuiltInReaderThemes.forEach { theme ->
                val swatch = if (theme.backgroundColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.surface
                } else {
                    theme.backgroundColor
                }
                FilterChip(
                    selected = settings.themeId == theme.id || (settings.themeId == null && theme.id == "system"),
                    onClick = { onReaderAction(ReaderAction.ThemeChanged(theme)) },
                    label = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .height(12.dp)
                                    .background(swatch, RoundedCornerShape(2.dp))
                            )
                            Text(theme.name)
                        }
                    }
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Text("Texture")
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
            Text("${(settings.textureAlpha.coerceIn(0f, 1f) * 100).roundToInt()}%")
            Slider(
                value = settings.textureAlpha.coerceIn(0f, 1f),
                onValueChange = { value -> onReaderAction(ReaderAction.SettingsChanged(settings.copy(textureAlpha = value))) },
                valueRange = 0f..1f,
                modifier = Modifier.width(130.dp)
            )
        }
    }
}

@Composable
private fun SharedReaderVisualOptionsControls(
    settings: ReaderSettings,
    onReaderAction: (ReaderAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Text("System UI")
            SystemUiMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.systemUiMode == mode,
                    onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(systemUiMode = mode))) },
                    label = { Text(mode.title) }
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Text("Page info")
            PageInfoMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.pageInfoMode == mode,
                    onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(pageInfoMode = mode))) },
                    label = { Text(mode.title) }
                )
            }
            PageInfoPosition.entries.forEach { position ->
                FilterChip(
                    selected = settings.pageInfoPosition == position,
                    onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(pageInfoPosition = position))) },
                    label = { Text(position.title) }
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            FilterChip(
                selected = settings.seamlessChapterNavigation,
                onClick = {
                    onReaderAction(
                        ReaderAction.SettingsChanged(
                            settings.copy(seamlessChapterNavigation = !settings.seamlessChapterNavigation)
                        )
                    )
                },
                label = { Text("Seamless chapters") }
            )
            Text("Pull ${settings.chapterTurnDragMultiplier.formatTwoDecimals()}x")
            Slider(
                value = settings.chapterTurnDragMultiplier.coerceIn(0.5f, 2.0f),
                onValueChange = { value ->
                    onReaderAction(ReaderAction.SettingsChanged(settings.copy(chapterTurnDragMultiplier = value)))
                },
                valueRange = 0.5f..2.0f,
                modifier = Modifier.width(140.dp)
            )
        }
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
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit,
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

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(ReaderTool.entries.toList(), key = { it.id }) { tool ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(tool.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        Text(tool.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
