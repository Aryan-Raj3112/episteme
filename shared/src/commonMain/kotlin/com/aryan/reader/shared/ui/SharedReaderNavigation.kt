package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.filterReaderTocEntries
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.reader.PaginatedReaderState
import com.aryan.reader.shared.reader.ReaderImageReference
import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderLinkTarget
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.ReaderSpreadLayout
import com.aryan.reader.shared.reader.SharedEpubTocEntry
import com.aryan.reader.shared.reader.logSharedReaderDiagnostic
import com.aryan.reader.shared.reader.readerImageReferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal const val ReaderGapChromeLogTag = "EpistemeReaderGap"
internal const val ReaderChromeWebViewLayoutLogTag = "EpistemeWebViewLayout"
internal const val ReaderOpenTraceLogTag = "EpistemeDesktopOpenTrace"
internal const val ReaderPositionTraceLogTag = "EpistemeDesktopPositionTrace"
internal const val ReaderVerticalRenderedChapterRadius = 2

internal fun readerVerticalRenderedChapterRange(chapterIndex: Int, lastChapterIndex: Int): IntRange? {
    if (lastChapterIndex < 0) return null
    val safeChapterIndex = chapterIndex.coerceIn(0, lastChapterIndex)
    return (safeChapterIndex - ReaderVerticalRenderedChapterRadius).coerceAtLeast(0)..
        (safeChapterIndex + ReaderVerticalRenderedChapterRadius).coerceAtMost(lastChapterIndex)
}

internal fun logReaderOpenTrace(message: () -> String) {
    logSharedReaderDiagnostic(ReaderOpenTraceLogTag, message)
}

internal fun logReaderPositionTrace(message: () -> String) {
    logSharedReaderDiagnostic(ReaderPositionTraceLogTag, message)
}

internal fun Long.readerOpenTraceElapsedMs(nowMillis: Long = currentTimestamp()): Long {
    return (nowMillis - this).coerceAtLeast(0L)
}

internal fun String.readerOpenTracePreview(maxLength: Int = 96): String {
    return replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
        .replace("\"", "\\\"")
}

internal fun ReaderLocator?.readerPositionTraceSummary(maxTextLength: Int = 90): String {
    if (this == null) return "null"
    return "chapter=${chapterIndex ?: "null"} page=${pageIndex ?: "null"} " +
        "offsets=${startOffset ?: "null"}..${endOffset ?: "null"} " +
        "block=${blockIndex ?: "null"} char=${charOffset ?: "null"} " +
        "chapterId=\"${chapterId.orEmpty().readerOpenTracePreview(80)}\" " +
        "href=\"${href.orEmpty().readerOpenTracePreview(120)}\" " +
        "cfi=\"${cfi.orEmpty().readerOpenTracePreview(180)}\" " +
        "text=\"${textQuote.orEmpty().readerOpenTracePreview(maxTextLength)}\""
}

internal fun logReaderGapChrome(
    layer: String,
    bounds: Rect,
    details: String = ""
) {
    val message = {
        buildString {
            append("compose_reader layer=")
            append(layer)
            append(" x=")
            append(bounds.left.roundToInt())
            append(" y=")
            append(bounds.top.roundToInt())
            append(" w=")
            append(bounds.width.roundToInt())
            append(" h=")
            append(bounds.height.roundToInt())
            append(" bottom=")
            append(bounds.bottom.roundToInt())
            if (details.isNotBlank()) {
                append(' ')
                append(details)
            }
        }
    }
    logSharedReaderDiagnostic(ReaderGapChromeLogTag, message)
    if (layer == "reader_content_column") {
        logSharedReaderDiagnostic(ReaderChromeWebViewLayoutLogTag, message)
    }
}

@Composable
internal fun SharedReaderCompactNavigation(
    session: ReaderSessionState,
    showSlider: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    pageInfoText: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageNumberChange: (Int) -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .onGloballyPositioned { coordinates ->
                logReaderGapChrome(
                    layer = "bottom_nav_row",
                    bounds = coordinates.boundsInWindow(),
                    details = "showSlider=$showSlider pageInfo=${pageInfoText != null} canPrev=$canGoPrevious canNext=$canGoNext"
                )
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReaderTooltipIconButton(
            tooltip = readerString("desktop_previous_page", "Previous page"),
            enabled = canGoPrevious,
            onClick = onPrevious,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.NavigateBefore,
                contentDescription = readerString("desktop_previous_page", "Previous page"),
                tint = contentColor.copy(alpha = if (canGoPrevious) 0.78f else 0.32f),
                modifier = Modifier.size(22.dp)
            )
        }
        if (showSlider) {
            SharedReaderPageSlider(
                session = session,
                onPageNumberChange = onPageNumberChange,
                contentColor = contentColor,
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                pageInfoText.orEmpty(),
                color = contentColor.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        ReaderTooltipIconButton(
            tooltip = readerString("desktop_next_page", "Next page"),
            enabled = canGoNext,
            onClick = onNext,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = readerString("desktop_next_page", "Next page"),
                tint = contentColor.copy(alpha = if (canGoNext) 0.78f else 0.32f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun SharedReaderFullscreenNavigation(
    session: ReaderSessionState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageNumberChange: (Int) -> Unit,
    onJumpBack: () -> Unit,
    onJumpForward: () -> Unit,
    onClearJumpHistory: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val readerState = session.reader
    val totalPages = readerState.pages.size.coerceAtLeast(1)
    val sliderSteps = ReaderSpreadLayout.sliderStepCount(totalPages, readerState.settings)
    val sliderMax = sliderSteps.coerceAtLeast(2)
    val currentSliderPosition = ReaderSpreadLayout.sliderPositionForPage(
        pageIndex = readerState.currentPageIndex,
        pageCount = totalPages,
        settings = readerState.settings
    )
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = backgroundColor,
        contentColor = contentColor,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!session.isSearchActive && session.shouldShowJumpHistory) {
                SharedReaderJumpHistoryBar(
                    session = session,
                    onBack = onJumpBack,
                    onForward = onJumpForward,
                    onClear = onClearJumpHistory
                )
                HorizontalDivider(color = contentColor.copy(alpha = 0.14f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderTooltipIconButton(
                    tooltip = readerString("desktop_previous_page", "Previous page"),
                    enabled = readerState.canGoPrevious,
                    onClick = onPrevious
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = readerString("desktop_previous_page", "Previous page"),
                        tint = contentColor.copy(alpha = if (readerState.canGoPrevious) 0.78f else 0.32f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                ReaderMinimalSlider(
                    value = currentSliderPosition.toFloat(),
                    onValueChange = { value ->
                        onPageNumberChange(
                            ReaderSpreadLayout.pageNumberForSliderPosition(
                                position = value.roundToInt(),
                                pageCount = totalPages,
                                settings = readerState.settings
                            )
                        )
                    },
                    valueRange = 1f..sliderMax.toFloat(),
                    enabled = sliderSteps > 1,
                    activeColor = contentColor.copy(alpha = 0.68f),
                    inactiveColor = contentColor.copy(alpha = 0.24f),
                    thumbColor = contentColor.copy(alpha = 0.92f),
                    modifier = Modifier.weight(1f)
                )
                ReaderTooltipIconButton(
                    tooltip = readerString("desktop_next_page", "Next page"),
                    enabled = readerState.canGoNext,
                    onClick = onNext
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = readerString("desktop_next_page", "Next page"),
                        tint = contentColor.copy(alpha = if (readerState.canGoNext) 0.78f else 0.32f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SharedReaderPageSlider(
    session: ReaderSessionState,
    onPageNumberChange: (Int) -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val readerState = session.reader
    val totalPages = readerState.pages.size.coerceAtLeast(1)
    val sliderSteps = ReaderSpreadLayout.sliderStepCount(totalPages, readerState.settings)
    val sliderMax = sliderSteps.coerceAtLeast(2)
    val currentSliderPosition = ReaderSpreadLayout.sliderPositionForPage(
        pageIndex = readerState.currentPageIndex,
        pageCount = totalPages,
        settings = readerState.settings
    )
    val pageRangeLabel = ReaderSpreadLayout.pageRangeLabel(readerState.currentPageIndex, totalPages, readerState.settings)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$pageRangeLabel / $totalPages",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.72f)
        )
        ReaderMinimalSlider(
            value = currentSliderPosition.toFloat(),
            onValueChange = { value ->
                onPageNumberChange(
                    ReaderSpreadLayout.pageNumberForSliderPosition(
                        position = value.roundToInt(),
                        pageCount = totalPages,
                        settings = readerState.settings
                    )
                )
            },
            valueRange = 1f..sliderMax.toFloat(),
            enabled = sliderSteps > 1,
            activeColor = contentColor.copy(alpha = 0.62f),
            inactiveColor = contentColor.copy(alpha = 0.18f),
            thumbColor = contentColor.copy(alpha = 0.86f),
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedReaderSidebar(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    sections: List<ReaderWorkspaceLeftSection>,
    onGoToChapter: (Int) -> Unit,
    onGoToLocator: (ReaderLocator) -> Unit,
    onGoToBookmark: (ReaderBookmark) -> Unit,
    onDownloadImage: ((ReaderImageReference) -> Unit)?,
    imagePreviewContent: (@Composable (ReaderImageReference, Modifier) -> Unit)?,
    onGoToHighlight: (UserHighlight) -> Unit,
    onEditHighlight: (UserHighlight) -> Unit,
    highlightPalette: ReaderHighlightPalette,
    onHighlightColorChange: (UserHighlight, HighlightColor) -> Unit,
    onOpenHighlightPaletteManager: () -> Unit,
    onDeleteHighlight: (UserHighlight) -> Unit
) {
    val tabs = remember(sections) {
        sections
            .filter { it.isReaderNavigationSection() }
            .distinct()
    }
    var selectedSection by remember(tabs) { mutableStateOf(tabs.firstOrNull()) }
    val selectedTabIndex = tabs.indexOf(selectedSection).takeIf { it >= 0 } ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        if (tabs.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEach { section ->
                    Tab(
                        selected = selectedSection == section,
                        onClick = { selectedSection = section },
                        text = {
                            Text(
                                section.readerNavigationTabLabel(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }

        when (selectedSection) {
            ReaderWorkspaceLeftSection.CONTENTS -> SharedReaderTocTab(
                session = session,
                readerEngine = readerEngine,
                onGoToLocator = onGoToLocator,
                onGoToChapter = onGoToChapter
            )
            ReaderWorkspaceLeftSection.NOTES -> SharedReaderAnnotationsTab(
                session = session,
                onGoToHighlight = onGoToHighlight,
                onEditHighlight = onEditHighlight,
                highlightPalette = highlightPalette,
                onHighlightColorChange = onHighlightColorChange,
                onOpenHighlightPaletteManager = onOpenHighlightPaletteManager,
                onDeleteHighlight = onDeleteHighlight
            )
            ReaderWorkspaceLeftSection.BOOKMARKS -> SharedReaderBookmarksTab(
                session = session,
                onGoToBookmark = onGoToBookmark
            )
            ReaderWorkspaceLeftSection.IMAGES -> SharedReaderImagesTab(
                session = session,
                onGoToImage = onGoToLocator,
                onDownloadImage = onDownloadImage,
                imagePreviewContent = imagePreviewContent
            )
            else -> SharedReaderEmptyNavigation(readerString("desktop_no_navigation_items", "No navigation items"))
        }
    }
}

internal fun ReaderWorkspaceLeftSection.isReaderNavigationSection(): Boolean {
    return when (this) {
        ReaderWorkspaceLeftSection.CONTENTS,
        ReaderWorkspaceLeftSection.IMAGES,
        ReaderWorkspaceLeftSection.NOTES,
        ReaderWorkspaceLeftSection.BOOKMARKS -> true
        ReaderWorkspaceLeftSection.PAGES,
        ReaderWorkspaceLeftSection.SEARCH -> false
    }
}

@Composable
internal fun SharedReaderTocTab(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    onGoToLocator: (ReaderLocator) -> Unit,
    onGoToChapter: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val chapters = session.reader.book.chapters
    val tocEntries = remember(session.reader.book.tableOfContents, chapters) {
        session.reader.book.tableOfContents.ifEmpty {
            chapters.map { chapter ->
                SharedEpubTocEntry(
                    label = chapter.title,
                    href = chapter.baseHref ?: chapter.id,
                    depth = 0
                )
            }
        }
    }
    if (tocEntries.isEmpty()) {
        SharedReaderEmptyNavigation(readerString("desktop_no_table_of_contents", "No table of contents"))
        return
    }

    val allParentIndices = remember(tocEntries) {
        tocEntries.indices.filter { index ->
            val next = tocEntries.getOrNull(index + 1)
            next != null && next.depth > tocEntries[index].depth
        }.toSet()
    }
    var expandedEntryIndices by remember(tocEntries) { mutableStateOf(allParentIndices) }
    var tocSearchQuery by remember(tocEntries) { mutableStateOf("") }
    val isSearchingToc = tocSearchQuery.isNotBlank()
    val visibleItemInfo by remember(tocEntries, tocSearchQuery) {
        derivedStateOf {
            if (tocSearchQuery.isNotBlank()) {
                filterReaderTocEntries(
                    entries = tocEntries,
                    query = tocSearchQuery,
                    labelOf = { it.label },
                    depthOf = { it.depth }
                ).map { it.originalIndex to it.entry }
            } else {
                val result = mutableListOf<Pair<Int, SharedEpubTocEntry>>()
                val visibilityStack = BooleanArray(50) { false }
                visibilityStack[0] = true

                tocEntries.forEachIndexed { index, entry ->
                    val depth = entry.depth.coerceIn(0, visibilityStack.lastIndex)
                    if (visibilityStack[depth]) {
                        result += index to entry
                        if (depth + 1 < visibilityStack.size) {
                            visibilityStack[depth + 1] = index in expandedEntryIndices
                        }
                    } else if (depth + 1 < visibilityStack.size) {
                        visibilityStack[depth + 1] = false
                    }
                }
                result
            }
        }
    }
    val currentChapterIndex = session.reader.currentPage?.chapterIndex
    val activeOriginalIndex = remember(tocEntries, chapters, currentChapterIndex) {
        tocEntries.indexOfFirst { entry ->
            val targetChapter = entry.targetChapterIndex(chapters)
            targetChapter == currentChapterIndex
        }.takeIf { it >= 0 } ?: currentChapterIndex?.takeIf { it in tocEntries.indices }
    }

    fun expandParentsFor(originalIndex: Int) {
        var currentDepth = tocEntries.getOrNull(originalIndex)?.depth ?: return
        val nextExpanded = expandedEntryIndices.toMutableSet()
        for (index in originalIndex downTo 0) {
            val entry = tocEntries[index]
            if (entry.depth < currentDepth) {
                nextExpanded += index
                currentDepth = entry.depth
            }
            if (currentDepth == 0) break
        }
        expandedEntryIndices = nextExpanded
    }

    fun locateCurrent() {
        val originalIndex = activeOriginalIndex ?: return
        coroutineScope.launch {
            expandParentsFor(originalIndex)
            repeat(4) {
                val visibleIndex = visibleItemInfo.indexOfFirst { it.first == originalIndex }
                if (visibleIndex >= 0) {
                    listState.animateScrollToItem(visibleIndex)
                    return@launch
                }
                delay(30)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SharedStableOutlinedTextField(
            value = tocSearchQuery,
            onValueChange = { tocSearchQuery = it },
            singleLine = true,
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = if (tocSearchQuery.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { tocSearchQuery = "" },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = readerString("tooltip_clear_search", "Clear search"),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            placeholder = { Text(readerString("search_chapters_placeholder", "Search chapters")) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { expandedEntryIndices = allParentIndices }) {
                Text(readerString("action_expand_all", "Expand all"))
            }
            TextButton(onClick = { expandedEntryIndices = emptySet() }) {
                Text(readerString("action_collapse_all", "Collapse all"))
            }
            TextButton(onClick = ::locateCurrent, enabled = activeOriginalIndex != null) {
                Text(readerString("action_locate", "Locate"))
            }
        }
        HorizontalDivider()
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (visibleItemInfo.isEmpty() && isSearchingToc) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = readerString("no_chapters_matching", "No chapters found for \"%1\$s\".", tocSearchQuery.trim()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedAcceleratedLazyWheelScroll(listState)
                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        visibleItemInfo,
                        key = { _, item -> "${item.first}_${item.second.href}_${item.second.fragmentId.orEmpty()}" }
                    ) { _, item ->
                        val (originalIndex, entry) = item
                        val nextItem = tocEntries.getOrNull(originalIndex + 1)
                        val hasChildren = nextItem != null && nextItem.depth > entry.depth
                        val isExpanded = originalIndex in expandedEntryIndices
                        val targetChapterIndex = entry.targetChapterIndex(chapters)
                        val selected = targetChapterIndex == currentChapterIndex

                        SharedReaderTocTreeItem(
                            title = entry.label,
                            pageLabel = targetChapterIndex?.let { readerString("desktop_chapter_short_format", "Ch. %1\$d", it + 1) },
                            depth = entry.depth,
                            isExpanded = isExpanded,
                            hasChildren = hasChildren,
                            isCurrent = selected,
                            onToggleExpand = {
                                expandedEntryIndices = if (isExpanded) {
                                    expandedEntryIndices - originalIndex
                                } else {
                                    expandedEntryIndices + originalIndex
                                }
                            },
                            onClick = {
                                val chapterIndex = targetChapterIndex
                                if (chapterIndex != null) {
                                    val fragment = entry.fragmentId
                                    if (fragment.isNullOrBlank()) {
                                        onGoToChapter(chapterIndex)
                                    } else {
                                        when (val target = readerEngine.resolveLink(session, "#$fragment", chapterIndex)) {
                                            is ReaderLinkTarget.Internal -> onGoToLocator(target.locator)
                                            else -> onGoToChapter(chapterIndex)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
            SharedReaderVerticalScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
internal fun SharedReaderTocTreeItem(
    title: String,
    pageLabel: String?,
    depth: Int,
    isExpanded: Boolean,
    hasChildren: Boolean,
    isCurrent: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .padding(start = (depth.coerceAtLeast(0) * 14).dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clickable(enabled = hasChildren) { onToggleExpand() },
                contentAlignment = Alignment.Center
            ) {
                if (hasChildren) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                title,
                fontWeight = if (isCurrent) FontWeight.Bold else if (depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (pageLabel != null) {
                Text(
                    pageLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

internal fun SharedEpubTocEntry.targetChapterIndex(
    chapters: List<com.aryan.reader.shared.reader.SharedEpubChapter>
): Int? {
    val targetPath = href.normalizedReaderTocPath()
    return chapters.indexOfFirst { chapter ->
        val chapterPath = chapter.baseHref.orEmpty().normalizedReaderTocPath()
        chapterPath == targetPath ||
            chapterPath.substringAfterLast('/') == targetPath.substringAfterLast('/') ||
            chapter.id == href
    }.takeIf { it >= 0 }
}

internal fun String.normalizedReaderTocPath(): String {
    return replace('\\', '/')
        .substringBefore('#')
        .substringBefore('?')
        .trim('/')
}

@Composable
internal fun SharedReaderBookmarksTab(
    session: ReaderSessionState,
    onGoToBookmark: (ReaderBookmark) -> Unit
) {
    if (session.bookmarks.isEmpty()) {
        SharedReaderEmptyNavigation(readerString("desktop_no_bookmarks_yet", "No bookmarks yet"))
    } else {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .sharedAcceleratedLazyWheelScroll(listState)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
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
            SharedReaderVerticalScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
internal fun SharedReaderImagesTab(
    session: ReaderSessionState,
    onGoToImage: (ReaderLocator) -> Unit,
    onDownloadImage: ((ReaderImageReference) -> Unit)?,
    imagePreviewContent: (@Composable (ReaderImageReference, Modifier) -> Unit)?
) {
    val images = remember(session.reader.book, session.reader.pages) {
        session.reader.book.readerImageReferences(session.reader.pages)
    }
    if (images.isEmpty()) {
        SharedReaderEmptyNavigation(readerString("no_images_found", "No images found."))
    } else {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .sharedAcceleratedLazyWheelScroll(listState)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(images, key = { it.id }) { image ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onGoToImage(image.locator) }
                                .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (imagePreviewContent != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.size(width = 48.dp, height = 56.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        imagePreviewContent(image, Modifier.fillMaxSize())
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    image.displayTitle,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    listOfNotNull(
                                        image.chapterTitle,
                                        image.dimensionLabel,
                                        image.sourceName()
                                    ).joinToString(" - "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (onDownloadImage != null) {
                                IconButton(
                                    onClick = { onDownloadImage(image) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = readerString("content_desc_download_image", "Download image")
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SharedReaderVerticalScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
internal fun SharedReaderAnnotationsTab(
    session: ReaderSessionState,
    onGoToHighlight: (UserHighlight) -> Unit,
    onEditHighlight: (UserHighlight) -> Unit,
    highlightPalette: ReaderHighlightPalette,
    onHighlightColorChange: (UserHighlight, HighlightColor) -> Unit,
    onOpenHighlightPaletteManager: () -> Unit,
    onDeleteHighlight: (UserHighlight) -> Unit
) {
    if (session.highlights.isEmpty()) {
        SharedReaderEmptyNavigation(readerString("desktop_no_annotations_yet", "No annotations yet"))
    } else {
        val listState = rememberLazyListState()
        var menuExpandedFor by remember { mutableStateOf<UserHighlight?>(null) }
        var deleteConfirmFor by remember { mutableStateOf<UserHighlight?>(null) }
        val colors = highlightPalette.sanitized().colors
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedAcceleratedLazyWheelScroll(listState)
                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
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
                            ?: readerString("chapter_number_format", "Chapter %1\$d", (locator.chapterIndex ?: highlight.chapterIndex) + 1)
                        val pageLabel = locator.pageIndex?.let { readerString("pdf_page_short", "Page %1\$d", it + 1) }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onGoToHighlight(highlight) },
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(12.dp)
                                                .height(12.dp)
                                                .background(highlight.effectiveColor, RoundedCornerShape(2.dp))
                                        )
                                        Text(
                                            listOfNotNull(chapterTitle, pageLabel).joinToString(" - "),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { menuExpandedFor = highlight }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = readerString("desktop_annotation_options", "Annotation options"))
                                        }
                                        DropdownMenu(
                                            expanded = menuExpandedFor == highlight,
                                            onDismissRequest = { menuExpandedFor = null }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(vertical = 8.dp, horizontal = 10.dp)
                                                    .fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                colors.forEach { color ->
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier
                                                            .padding(horizontal = 4.dp)
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(color.color)
                                                            .clickable {
                                                                menuExpandedFor = null
                                                                onHighlightColorChange(highlight, color)
                                                            }
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
                                                    onClick = {
                                                        menuExpandedFor = null
                                                        onOpenHighlightPaletteManager()
                                                    },
                                                    size = 28.dp
                                                )
                                            }
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        if (highlight.note.isNullOrBlank()) {
                                                            readerString("menu_add_note", "Add note")
                                                        } else {
                                                            readerString("menu_edit_note", "Edit note")
                                                        }
                                                    )
                                                },
                                                onClick = {
                                                    menuExpandedFor = null
                                                    onEditHighlight(highlight)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(readerString("action_delete", "Delete")) },
                                                onClick = {
                                                    menuExpandedFor = null
                                                    deleteConfirmFor = highlight
                                                }
                                            )
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onGoToHighlight(highlight) },
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(highlight.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    highlight.note?.takeIf { it.isNotBlank() }?.let { note ->
                                        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                SharedReaderVerticalScrollbar(
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
            deleteConfirmFor?.let { highlight ->
                AlertDialog(
                    onDismissRequest = { deleteConfirmFor = null },
                    title = { Text(readerString("desktop_delete_annotation_title", "Delete annotation?")) },
                    text = { Text(readerString("desktop_delete_highlight_desc", "This removes the highlight and its note.")) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                deleteConfirmFor = null
                                onDeleteHighlight(highlight)
                            }
                        ) {
                            Text(readerString("action_delete", "Delete"), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirmFor = null }) {
                            Text(readerString("action_cancel", "Cancel"))
                        }
                    }
                )
            }
        }
    }
}

@Composable
internal fun SharedReaderEmptyNavigation(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ReaderWorkspaceLeftSection.readerNavigationTabLabel(): String {
    return when (this) {
        ReaderWorkspaceLeftSection.CONTENTS -> readerString("desktop_toc", "TOC")
        ReaderWorkspaceLeftSection.IMAGES -> readerString("tab_images", "Images")
        ReaderWorkspaceLeftSection.NOTES -> readerString("tab_annotations", "Annotations")
        ReaderWorkspaceLeftSection.BOOKMARKS -> readerString("tab_bookmarks", "Bookmarks")
        ReaderWorkspaceLeftSection.PAGES -> readerString("tab_pages", "Pages")
        ReaderWorkspaceLeftSection.SEARCH -> readerString("action_search", "Search")
    }
}

@Composable
internal fun SharedReaderHighlightPaletteDialog(
    palette: ReaderHighlightPalette,
    onDismiss: () -> Unit,
    onSave: (ReaderHighlightPalette) -> Unit
) {
    var draftColors by remember(palette) { mutableStateOf(palette.sanitized().colors) }
    var selectedSlotIndex by remember { mutableIntStateOf(0) }

    fun replaceSlot(color: HighlightColor) {
        if (draftColors.isEmpty()) return
        val next = draftColors.toMutableList()
        val slot = selectedSlotIndex.coerceIn(0, next.lastIndex)
        next[slot] = color
        draftColors = next
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                readerString("dialog_customize_palette", "Customize palette"),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    readerString("palette_tap_slot_to_edit", "Tap a slot to edit it."),
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    draftColors.forEachIndexed { index, color ->
                        val selected = index == selectedSlotIndex
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .background(color.color, CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = CircleShape
                                )
                                .clip(CircleShape)
                                .clickable { selectedSlotIndex = index },
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (color == HighlightColor.WHITE) Color.Black else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    readerString("palette_select_color_for_slot", "Select a color for the slot."),
                    style = MaterialTheme.typography.bodySmall
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(200.dp)
                ) {
                    items(HighlightColor.entries) { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(color.color, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f), CircleShape)
                                .clickable { replaceSlot(color) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(ReaderHighlightPalette(draftColors).sanitized()) }) {
                Text(readerString("action_save", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(readerString("action_cancel", "Cancel"))
            }
        }
    )
}

@Composable
internal fun SharedReaderHighlightPaletteSpectrumButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val rainbowColors = listOf(
        Color.Red,
        Color(0xFFFF7F00),
        Color.Yellow,
        Color.Green,
        Color.Blue,
        Color(0xFF4B0082),
        Color(0xFF8B00FF)
    )
    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = Brush.sweepGradient(rainbowColors),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

internal fun Float.formatTwoDecimals(): String {
    val scaled = (this * 100).toInt()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}

internal fun ReaderToolbarPreferences.moveTool(tool: ReaderTool, delta: Int): ReaderToolbarPreferences {
    val order = sanitized().toolOrder.toMutableList()
    val index = order.indexOf(tool)
    if (index < 0) return this
    val target = (index + delta).coerceIn(0, order.lastIndex)
    if (index == target) return this
    val moved = order.removeAt(index)
    order.add(target, moved)
    return withToolOrder(order)
}

internal val ReaderSessionState.shouldShowJumpHistory: Boolean
    get() = reader.settings.readingMode != ReaderReadingMode.PAGINATED && jumpHistory.hasJumpTargets

@Composable
internal fun SharedReaderJumpHistoryBar(
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
            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = readerString("content_desc_jump_back", "Jump back"))
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
            Icon(Icons.Default.Close, contentDescription = readerString("desktop_clear_jump_history", "Clear jump history"))
            Text(readerString("action_clear", "Clear"), maxLines = 1)
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
            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = readerString("content_desc_jump_forward", "Jump forward"))
        }
    }
}

@Composable
internal fun ReaderLocator.jumpLabel(session: ReaderSessionState): String {
    val targetPageIndex = pageIndex
    val targetCfi = cfi.orEmpty()
    if (targetPageIndex != null && targetCfi.isBlank()) {
        return readerString("pdf_page_short", "Page %1\$d", targetPageIndex + 1)
    }
    val chapter = chapterIndex
    return if (chapter != null) {
        session.reader.book.chapters.getOrNull(chapter)?.title?.takeIf { it.isNotBlank() }
            ?: readerString("chapter_number_format", "Chapter %1\$d", chapter + 1)
    } else {
        readerString("location", "Location")
    }
}

internal fun Long.toComposeColor(): Color {
    val value = this and 0xFFFFFFFFL
    val alpha = ((value shr 24) and 0xFF) / 255f
    val red = ((value shr 16) and 0xFF) / 255f
    val green = ((value shr 8) and 0xFF) / 255f
    val blue = (value and 0xFF) / 255f
    return Color(red = red, green = green, blue = blue, alpha = alpha.takeIf { it > 0f } ?: 1f)
}

@Composable
internal fun PaginatedReaderState.pageInfoText(): String {
    val total = pages.size.coerceAtLeast(1)
    val percent = progress.roundToInt().coerceIn(0, 100)
    val mode = if (settings.readingMode == ReaderReadingMode.VERTICAL) {
        readerString("desktop_continuous", "Continuous")
    } else {
        readerString("desktop_page", "Page")
    }
    val current = ReaderSpreadLayout.pageRangeLabel(currentPageIndex, total, settings)
    val chapter = currentPage?.chapterTitle?.takeIf { it.isNotBlank() }
    val pageInfo = readerString("desktop_reader_page_info_format", "%1\$s %2\$s of %3\$d (%4\$d%%)", mode, current, total, percent)
    return listOfNotNull(pageInfo, chapter).joinToString(" - ")
}

internal fun PaginatedReaderState.currentPageLocator(): ReaderLocator? {
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
