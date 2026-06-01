package com.aryan.reader.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aryan.reader.shared.SearchHighlightMode
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.ui.ReaderMinimalSlider
import com.aryan.reader.shared.ui.ReaderTooltipIconButton
import com.aryan.reader.shared.ui.SharedStableOutlinedTextField
import com.aryan.reader.shared.ui.readerString
import kotlinx.coroutines.delay

@Composable
internal fun DesktopPdfFullscreenBottomChrome(
    pageIndex: Int,
    pageCount: Int,
    pageLabel: String = "Page ${pageIndex + 1} of $pageCount",
    canGoPrevious: Boolean = pageIndex > 0,
    canGoNext: Boolean = pageIndex < pageCount - 1,
    showJumpHistory: Boolean,
    jumpBackPage: Int?,
    jumpForwardPage: Int?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageScrub: (Float) -> Unit,
    onPageScrubFinished: () -> Unit,
    onJumpBack: () -> Unit,
    onJumpForward: () -> Unit,
    onClearJumpHistory: () -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    val chromeBackground = MaterialTheme.colorScheme.surfaceVariant
    val chromeContent = MaterialTheme.colorScheme.onSurface
    val sliderActive = MaterialTheme.colorScheme.primary
    val sliderInactive = chromeContent.copy(alpha = if (chromeBackground.luminance() > 0.5f) 0.44f else 0.52f)
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = chromeBackground,
        contentColor = chromeContent,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            extraContent()
            val hasJumpTargets = jumpBackPage != null || jumpForwardPage != null
            DesktopPdfJumpHistoryControls(
                visible = showJumpHistory,
                backPage = jumpBackPage,
                forwardPage = jumpForwardPage,
                onBack = onJumpBack,
                onForward = onJumpForward,
                onClear = onClearJumpHistory
            )
            if (showJumpHistory && hasJumpTargets) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderTooltipIconButton(
                    tooltip = readerString("desktop_previous_page", "Previous page"),
                    onClick = onPrevious,
                    enabled = canGoPrevious
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = readerString("desktop_previous_page", "Previous page"),
                        tint = chromeContent.copy(alpha = if (canGoPrevious) 0.78f else 0.32f)
                    )
                }
                Text(
                    pageLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = chromeContent.copy(alpha = 0.72f)
                )
                ReaderMinimalSlider(
                    value = pageIndex.toFloat(),
                    onValueChange = onPageScrub,
                    onValueChangeFinished = onPageScrubFinished,
                    valueRange = 0f..(pageCount - 1).coerceAtLeast(0).toFloat(),
                    enabled = pageCount > 1,
                    activeColor = sliderActive,
                    inactiveColor = sliderInactive,
                    thumbColor = sliderActive,
                    modifier = Modifier.weight(1f)
                )
                ReaderTooltipIconButton(
                    tooltip = readerString("desktop_next_page", "Next page"),
                    onClick = onNext,
                    enabled = canGoNext
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = readerString("desktop_next_page", "Next page"),
                        tint = chromeContent.copy(alpha = if (canGoNext) 0.78f else 0.32f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DesktopPdfBottomChrome(
    pageIndex: Int,
    pageCount: Int,
    pageLabel: String = "Page ${pageIndex + 1} of $pageCount",
    progressPercent: Float,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    showJumpHistory: Boolean,
    jumpBackPage: Int?,
    jumpForwardPage: Int?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageScrub: (Float) -> Unit,
    onPageScrubFinished: () -> Unit,
    onJumpBack: () -> Unit,
    onJumpForward: () -> Unit,
    onClearJumpHistory: () -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    val chromeBackground = MaterialTheme.colorScheme.surfaceVariant
    val chromeContent = MaterialTheme.colorScheme.onSurface
    val sliderActive = MaterialTheme.colorScheme.primary
    val sliderInactive = chromeContent.copy(alpha = if (chromeBackground.luminance() > 0.5f) 0.44f else 0.52f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = chromeBackground,
        contentColor = chromeContent,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            extraContent()
            DesktopPdfJumpHistoryControls(
                visible = showJumpHistory,
                backPage = jumpBackPage,
                forwardPage = jumpForwardPage,
                onBack = onJumpBack,
                onForward = onJumpForward,
                onClear = onClearJumpHistory
            )
            if (showJumpHistory && (jumpBackPage != null || jumpForwardPage != null)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderTooltipIconButton(
                    tooltip = readerString("desktop_previous_page", "Previous page"),
                    onClick = onPrevious,
                    enabled = canGoPrevious
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = readerString("desktop_previous_page", "Previous page"),
                        tint = chromeContent.copy(alpha = if (canGoPrevious) 0.78f else 0.32f)
                    )
                }
                Text(
                    pageLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = chromeContent.copy(alpha = 0.72f)
                )
                if (pageCount > 1) {
                    ReaderMinimalSlider(
                        value = pageIndex.toFloat(),
                        onValueChange = onPageScrub,
                        onValueChangeFinished = onPageScrubFinished,
                        valueRange = 0f..(pageCount - 1).toFloat(),
                        activeColor = sliderActive,
                        inactiveColor = sliderInactive,
                        thumbColor = sliderActive,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    "${progressPercent.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = chromeContent.copy(alpha = 0.72f)
                )
                ReaderTooltipIconButton(
                    tooltip = readerString("desktop_next_page", "Next page"),
                    onClick = onNext,
                    enabled = canGoNext
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = readerString("desktop_next_page", "Next page"),
                        tint = chromeContent.copy(alpha = if (canGoNext) 0.78f else 0.32f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DesktopPdfZoomPercentageIndicator(
    percentage: Int,
    onResetZoomClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "$percentage%",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(Color.White.copy(alpha = 0.5f))
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ZoomOut,
                contentDescription = readerString("content_desc_reset_zoom", "Reset zoom"),
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onResetZoomClick)
            )
        }
    }
}

@Composable
internal fun DesktopPdfSearchTopBar(
    query: String,
    showResultsPanel: Boolean,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onToggleResults: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focusRequester.requestFocus() }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReaderTooltipIconButton(
                tooltip = readerString("tooltip_close_search_desc", "Exit search and go back to the reader"),
                onClick = onClose,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = readerString("content_desc_close_search", "Close search"))
            }
            SharedStableOutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(readerString("desktop_search_in_pdf", "Search in PDF")) },
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        ReaderTooltipIconButton(
                            tooltip = readerString("tooltip_clear_search_desc", "Erase your current search query and start over"),
                            onClick = { onQueryChange("") }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = readerString("tooltip_clear_search", "Clear search"))
                        }
                    }
                } else {
                    null
                },
                selectionKey = "desktop-pdf-search"
            )
            val resultsTooltip = if (showResultsPanel) {
                readerString("tooltip_hide_results_desc", "Collapse the search results panel")
            } else {
                readerString("tooltip_show_results_desc", "Expand the panel to see all search matches")
            }
            ReaderTooltipIconButton(
                tooltip = resultsTooltip,
                onClick = onToggleResults,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (showResultsPanel) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (showResultsPanel) {
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
internal fun BoxScope.DesktopPdfSearchOverlay(
    isSearchActive: Boolean,
    showResultsPanel: Boolean,
    query: String,
    results: List<SharedPdfSearchResult>,
    activeSearchIndex: Int,
    highlightMode: SearchHighlightMode,
    isIndexing: Boolean,
    indexedPageCount: Int,
    pageCount: Int,
    onResultClick: (Int) -> Unit,
    onShowResults: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleHighlightMode: () -> Unit
) {
    AnimatedVisibility(
        visible = isSearchActive && showResultsPanel,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier.fillMaxSize().zIndex(30f)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                if (isIndexing) {
                    val progress = indexedPageCount.toFloat() / pageCount.coerceAtLeast(1).toFloat()
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                readerString(
                                    "desktop_indexing_pages_format",
                                    "Indexing %1\$d/%2\$d pages",
                                    indexedPageCount.coerceAtMost(pageCount),
                                    pageCount
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                    }
                }

                when {
                    query.isBlank() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(readerString("desktop_type_to_search_pdf", "Type to search this PDF"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    results.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (isIndexing) {
                                    readerString("desktop_no_matches_indexed_pages_yet", "No matches in indexed pages yet")
                                } else {
                                    readerString("desktop_no_matches", "No matches")
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> {
                        Text(
                            when {
                                isIndexing -> readerString("desktop_matches_so_far_format", "%1\$d matches so far", results.size)
                                else -> readerString("desktop_matches_format", "%1\$d matches", results.size)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        HorizontalDivider()
                        LazyColumn(Modifier.fillMaxSize()) {
                            itemsIndexed(
                                items = results,
                                key = { index, result -> "${result.pageIndex}_${result.matchIndex}_$index" }
                            ) { index, result ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { onResultClick(index) },
                                    color = if (index == activeSearchIndex) {
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
                                            readerString("pdf_page_short", "Page %1\$d", result.pageIndex + 1),
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
        visible = isSearchActive && !showResultsPanel && results.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 18.dp)
            .zIndex(31f)
    ) {
        DesktopPdfSearchNavigationPill(
            activeSearchIndex = activeSearchIndex,
            resultCount = results.size,
            highlightMode = highlightMode,
            onShowResults = onShowResults,
            onPrevious = onPrevious,
            onNext = onNext,
            onToggleHighlightMode = onToggleHighlightMode
        )
    }
}

@Composable
private fun DesktopPdfSearchNavigationPill(
    activeSearchIndex: Int,
    resultCount: Int,
    highlightMode: SearchHighlightMode,
    onShowResults: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleHighlightMode: () -> Unit
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
                tooltip = if (highlightMode == SearchHighlightMode.ALL) {
                    readerString("desktop_show_current_match_only", "Show current match only")
                } else {
                    readerString("desktop_show_all_search_matches", "Show all search matches")
                },
                onClick = onToggleHighlightMode,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (highlightMode == SearchHighlightMode.ALL) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = readerString("content_desc_toggle_search_highlights", "Toggle search highlights"),
                    tint = if (highlightMode == SearchHighlightMode.ALL) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            ReaderTooltipIconButton(
                tooltip = readerString("tooltip_prev_result_desc", "Jump to the previous search match in the document"),
                onClick = onPrevious,
                enabled = resultCount > 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = readerString("desktop_previous_search_result", "Previous search result"))
            }
            Text(
                text = if (activeSearchIndex in 0 until resultCount) {
                    "${activeSearchIndex + 1}/$resultCount"
                } else {
                    readerString("desktop_matches_format", "%1\$d matches", resultCount)
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onShowResults).padding(horizontal = 8.dp)
            )
            ReaderTooltipIconButton(
                tooltip = readerString("tooltip_next_result_desc", "Jump to the next search match in the document"),
                onClick = onNext,
                enabled = resultCount > 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = readerString("desktop_next_search_result", "Next search result"))
            }
        }
    }
}
