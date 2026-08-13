package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderAiResultState
import com.aryan.reader.shared.ReaderContextExtractor
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderAction
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsPlanner
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.reader.ReaderSessionState
import kotlinx.coroutines.delay

@Composable
internal fun SharedReaderSearchTopBar(
    session: ReaderSessionState,
    onReaderAction: (ReaderAction) -> Unit
) {
    val focusRequester = remember(session.reader.book.id) { FocusRequester() }

    LaunchedEffect(session.isSearchActive) {
        if (session.isSearchActive) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReaderTooltipIconButton(
                tooltip = readerString("tooltip_close_search_desc", "Exit search and go back to the reader"),
                onClick = { onReaderAction(ReaderAction.SearchClosed) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = readerString("content_desc_close_search", "Close search"))
            }
            SharedStableOutlinedTextField(
                value = session.searchQuery,
                onValueChange = { onReaderAction(ReaderAction.SearchChanged(it)) },
                placeholder = { Text(readerString("search_in_book", "Search in book")) },
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                trailingIcon = if (session.searchQuery.isNotEmpty()) {
                    {
                        ReaderTooltipIconButton(
                            tooltip = readerString("tooltip_clear_search_desc", "Erase your current search query and start over"),
                            onClick = { onReaderAction(ReaderAction.SearchChanged("")) }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = readerString("tooltip_clear_search", "Clear search"))
                        }
                    }
                } else {
                    null
                },
                selectionKey = session.reader.book.id
            )
            val resultsTooltip = if (session.showSearchResultsPanel) {
                readerString("tooltip_hide_results_desc", "Collapse the search results panel")
            } else {
                readerString("tooltip_show_results_desc", "Expand the panel to see all search matches")
            }
            ReaderTooltipIconButton(
                tooltip = resultsTooltip,
                onClick = { onReaderAction(ReaderAction.SearchResultsPanelToggled) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (session.showSearchResultsPanel) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (session.showSearchResultsPanel) {
                        readerString("desktop_hide_search_results", "Hide search results")
                    } else {
                        readerString("desktop_show_search_results", "Show search results")
                    }
                )
            }
    }
}
}

@Composable
internal fun BoxScope.SharedReaderSearchOverlay(
    session: ReaderSessionState,
    onResultClick: (Int) -> Unit,
    onShowResults: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    AnimatedVisibility(
        visible = session.isSearchActive && session.showSearchResultsPanel,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier.fillMaxSize().zIndex(30f)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                session.searchQuery.isBlank() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(readerString("desktop_type_to_search_book", "Type to search this book"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                session.searchResults.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(readerString("desktop_no_matches", "No matches"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                else -> {
                    Column(Modifier.fillMaxSize()) {
                        Text(
                            readerString("desktop_matches_format", "%1\$d matches", session.searchResults.size),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        HorizontalDivider()
                        LazyColumn(Modifier.fillMaxSize()) {
                            itemsIndexed(
                                items = session.searchResults,
                                key = { index, result -> "${result.pageIndex}_${result.matchIndex}_$index" }
                            ) { index, result ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { onResultClick(index) },
                                    color = if (index == session.activeSearchResultIndex) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            readerString(
                                                "desktop_pdf_page_author_format",
                                                "Page %1\$d - %2\$s",
                                                result.pageIndex + 1,
                                                result.chapterTitle
                                            ),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            result.preview,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = session.isSearchActive && !session.showSearchResultsPanel && session.searchResults.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 18.dp, bottom = 18.dp)
            .zIndex(31f)
    ) {
        SharedReaderSearchNavigationPill(
            session = session,
            onShowResults = onShowResults,
            onPrevious = onPrevious,
            onNext = onNext
        )
    }
}

@Composable
internal fun SharedReaderSearchNavigationPill(
    session: ReaderSessionState,
    onShowResults: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ReaderTooltipIconButton(
                tooltip = readerString("tooltip_prev_result_desc", "Jump to the previous search match in the document"),
                onClick = onPrevious,
                enabled = session.canGoToPreviousSearchResult,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = readerString("desktop_previous_search_result", "Previous search result"))
            }
            Text(
                text = if (session.activeSearchResultIndex in session.searchResults.indices) {
                    "${session.activeSearchResultIndex + 1}/${session.searchResults.size}"
                } else {
                    readerString("desktop_matches_format", "%1\$d matches", session.searchResults.size)
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onShowResults).padding(horizontal = 8.dp)
            )
            ReaderTooltipIconButton(
                tooltip = readerString("tooltip_next_result_desc", "Jump to the next search match in the document"),
                onClick = onNext,
                enabled = session.canGoToNextSearchResult,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = readerString("desktop_next_search_result", "Next search result"))
            }
        }
    }
}

@Composable
internal fun SharedReaderHighlightSheet(
    session: ReaderSessionState,
    highlight: UserHighlight,
    palette: ReaderHighlightPalette,
    onDismiss: () -> Unit,
    onColorChange: (HighlightColor) -> Unit,
    onStyleChange: (HighlightStyle) -> Unit,
    onOpenPaletteManager: () -> Unit,
    onSaveNote: (String) -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onSearch: () -> Unit
) {
    val locator = highlight.locator.withFallbacks(
        chapterIndex = highlight.chapterIndex,
        cfi = highlight.cfi,
        textQuote = highlight.text
    )
    val chapterTitle = session.reader.book.chapters
        .getOrNull(locator.chapterIndex ?: highlight.chapterIndex)
        ?.title
        ?: readerString("chapter_number_format", "Chapter %1\$d", (locator.chapterIndex ?: highlight.chapterIndex) + 1)
    var noteText by remember(highlight.id, highlight.note) { mutableStateOf(highlight.note.orEmpty()) }

    SharedReaderBottomSheet(
        title = readerString("label_highlight_color", "Highlight"),
        onDismiss = onDismiss
    ) {
        SharedReaderHighlightStyleSelector(
            selectedStyle = highlight.style,
            onStyleSelected = onStyleChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            palette.sanitized().colors.forEach { color ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.color)
                        .clickable { onColorChange(color) },
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .border(
                                width = if (highlight.color == color) 3.dp else 1.dp,
                                color = if (highlight.color == color) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
                                },
                                shape = CircleShape
                            )
                    )
                    if (highlight.color == color) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (color == HighlightColor.WHITE || color == HighlightColor.YELLOW) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            SharedReaderHighlightPaletteSpectrumButton(
                onClick = onOpenPaletteManager,
                size = 28.dp
            )
        }
        Surface(
            color = highlight.effectiveColor.copy(alpha = 0.10f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, highlight.effectiveColor.copy(alpha = 0.30f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.heightIn(min = 76.dp)) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(highlight.effectiveColor)
                )
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        chapterTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "\"${highlight.text}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SharedReaderBottomSheetToolButton(Icons.Default.ContentCopy, readerString("action_copy", "Copy")) {
                onCopy()
                onDismiss()
            }
            SharedReaderBottomSheetToolButton(Icons.Default.Search, readerString("action_search", "Search")) {
                onSearch()
                onDismiss()
            }
        }
        SharedStableOutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = { Text(readerString("label_note", "Note")) },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            selectionKey = highlight.id
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDelete) {
                Text(readerString("action_delete", "Delete"), color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = {
                onSaveNote(noteText)
                onDismiss()
            }) {
                Text(readerString("action_save_note", "Save note"))
            }
        }
    }
}

@Composable
internal fun SharedReaderHighlightStyleSelector(
    selectedStyle: HighlightStyle,
    onStyleSelected: (HighlightStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HighlightStyle.entries.forEach { style ->
            val label = when (style) {
                HighlightStyle.BACKGROUND -> "B"
                HighlightStyle.UNDERLINE -> "U"
                HighlightStyle.WAVY_UNDERLINE -> "~"
                HighlightStyle.STRIKETHROUGH -> "S"
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selectedStyle == style) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                    .border(
                        1.dp,
                        if (selectedStyle == style) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onStyleSelected(style) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun SharedReaderBottomSheetToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun SharedReaderAiResultSheet(
    result: ReaderAiResultState,
    onDismiss: () -> Unit
) {
    SharedReaderBottomSheet(
        title = result.title ?: readerString("desktop_ai", "AI"),
        onDismiss = onDismiss
    ) {
        val errorMessage = result.errorMessage
        when {
            result.isLoading && result.text.isBlank() -> Text(readerString("desktop_working", "Working..."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            errorMessage != null -> Text(errorMessage, color = MaterialTheme.colorScheme.error)
            else -> {
                if (result.isLoading) {
                    Text(readerString("desktop_working", "Working..."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SharedMarkdownText(result.text)
            }
        }
    }
}

@Composable
internal fun SharedReaderBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    SharedReaderModalLayer(onDismiss = onDismiss) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(40f)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            val sheetHorizontalPadding = 24.dp
            val sheetAvailableWidth = (maxWidth - sheetHorizontalPadding - sheetHorizontalPadding).coerceAtLeast(0.dp)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = sheetHorizontalPadding, vertical = 16.dp)
                    .width(sharedReaderPopupWidth(sheetAvailableWidth))
                    .heightIn(max = 560.dp),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(42.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = readerString("action_close", "Close"))
                        }
                    }
                    HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
internal fun SharedReaderQuickActions(
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
                        contentDescription = readerString("content_desc_bookmark", "Bookmark")
                    )
                }

                ReaderTool.THEME -> IconButton(onClick = onToggleTheme) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = if (isDarkMode) {
                            readerString("desktop_use_light_theme", "Use light theme")
                        } else {
                            readerString("desktop_use_dark_theme", "Use dark theme")
                        }
                    )
                }

                ReaderTool.SEARCH -> IconButton(onClick = onToggleSearch) {
                    Icon(
                        if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = readerString("action_search", "Search")
                    )
                }

                ReaderTool.DICTIONARY -> IconButton(
                    onClick = { onExternalLookup(ReaderExternalLookupAction.DICTIONARY, ReaderContextExtractor.currentPageText(session)) }
                ) {
                    Icon(Icons.Default.Translate, contentDescription = readerString("desktop_external_lookup", "External lookup"))
                }

                ReaderTool.AI_FEATURES -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        enabled = aiByokSettings.areReaderAiFeaturesAvailable &&
                            ReaderContextExtractor.currentPageText(session).isNotBlank() &&
                            !extrasState.aiResult.isLoading,
                        onClick = { onAiAction(ReaderAiFeature.DEFINE, ReaderContextExtractor.currentPageText(session).take(1200)) }
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = readerString("desktop_define_page", "Define page"))
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
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (extrasState.cloudTts.isPlaying || extrasState.cloudTts.isLoading || extrasState.cloudTts.isPaused) {
                            readerString("desktop_stop_read_aloud", "Stop read aloud")
                        } else {
                            readerString("action_read_aloud", "Read aloud")
                        }
                    )
                }

                else -> Unit
            }
        }
    }
}
