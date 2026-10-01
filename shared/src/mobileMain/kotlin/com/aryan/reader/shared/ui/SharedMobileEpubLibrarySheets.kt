package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ListItem
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderHighlightListAction
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.deduplicatedReaderBookmarks
import com.aryan.reader.shared.readerHighlightListActions
import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.SharedEpubDrawerImage
import com.aryan.reader.shared.reader.isSharedEpubSvgSource
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderSpreadLayout
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubTocEntry
import com.aryan.reader.shared.reader.effectiveReaderTocEntries
import com.aryan.reader.shared.reader.projectReaderTocEntries
import com.aryan.reader.shared.reader.readerTocLocatePlan
import com.aryan.reader.shared.reader.readerTocParentIndices
import com.aryan.reader.shared.reader.readerTocToggleExpansion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay

@Composable
internal fun SharedMobileEpubSlider(
    pageIndex: Int,
    pageCount: Int,
    settings: ReaderSettings,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onScrubPositionChange: (Int?) -> Unit = {},
) {
    val sliderStepCount = ReaderSpreadLayout.sliderStepCount(pageCount, settings)
    val lastSliderPosition = (sliderStepCount - 1).coerceAtLeast(0)
    var sliderValue by remember(pageIndex, pageCount, settings) {
        mutableStateOf(
            (ReaderSpreadLayout.sliderPositionForPage(pageIndex, pageCount, settings) - 1)
                .coerceIn(0, lastSliderPosition)
                .toFloat()
        )
    }
    val sliderChrome = sharedReaderSliderChromeColors(
        pageBackground = settings.readerBackgroundColor(),
        pageText = settings.readerTextColor(),
        themePrimary = MaterialTheme.colorScheme.primary,
    )
    val activeColor = sliderChrome.activeTrackColor
    val inactiveColor = sliderChrome.inactiveTrackColor
    val contentColor = sliderChrome.contentColor
    // Android parity (EpubReaderPageSlider): bare bottom-chrome bar with
    // prev/next steppers flanking a ReaderMinimalSlider — no card surface,
    // no page-count labels. Outer stacking padding stays caller-owned so the
    // bar sits above the jump bar exactly like the benchmark.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) {},
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { onPageSelected((pageIndex - 1).coerceAtLeast(0)) },
                enabled = pageIndex > 0,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.NavigateBefore,
                    contentDescription = readerString("desktop_previous_page", "Previous page"),
                    tint = contentColor.copy(alpha = if (pageIndex > 0) 0.9f else 0.32f)
                )
            }
            ReaderMinimalSlider(
                value = sliderValue.coerceIn(0f, lastSliderPosition.coerceAtLeast(1).toFloat()),
                onValueChange = {
                    sliderValue = it
                    onScrubPositionChange(
                        ReaderSpreadLayout.pageNumberForSliderPosition(
                            it.roundToInt().coerceIn(0, lastSliderPosition) + 1,
                            pageCount,
                            settings
                        ) - 1
                    )
                },
                onValueChangeFinished = {
                    onScrubPositionChange(null)
                    onPageSelected(
                        ReaderSpreadLayout.pageNumberForSliderPosition(
                            sliderValue.roundToInt().coerceIn(0, lastSliderPosition) + 1,
                            pageCount,
                            settings
                        ) - 1
                    )
                },
                valueRange = 0f..lastSliderPosition.coerceAtLeast(1).toFloat(),
                enabled = sliderStepCount > 1,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                thumbColor = activeColor,
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
            )
            IconButton(
                onClick = { onPageSelected((pageIndex + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0))) },
                enabled = pageIndex < pageCount - 1,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = readerString("desktop_next_page", "Next page"),
                    tint = contentColor.copy(alpha = if (pageIndex < pageCount - 1) 0.9f else 0.32f)
                )
            }
        }
    }
}

// Android parity (PageScrubbingAnimation): centered "Page X of Y" readout
// while fast-scrubbing the page slider.
@Composable
internal fun SharedMobileEpubScrubBubble(
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Icon(
            SharedReaderIcons.Slider,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * EPUB table of contents.
 *
 * Android benchmark (`EpubReaderDrawer.ChaptersList`): the search field with a clear button, a
 * "no chapters matching" empty state, expand/collapse/locate over `rememberSaveable` state, a
 * `VerticalScrollbar` slot, and rows rendered by the shared tree item. The projection logic lives
 * in the generic, unit-tested helpers in `SharedEpubTocProjection.kt`.
 *
 * Generic over the host's TOC entry type because Android persists `EpubTocEntry` (absolute paths)
 * while the shared reader uses `SharedEpubTocEntry` (hrefs); neither can be widened to the other
 * without changing navigation. The host keeps owning its entries and maps by `originalIndex`.
 */
@Composable
fun <T> SharedMobileEpubToc(
    entries: List<T>,
    activeIndex: Int?,
    onEntryClick: (Int, T) -> Unit,
    labelOf: (T) -> String,
    depthOf: (T) -> Int,
    keyOf: (Int, T) -> String,
    collapseDescription: String,
    expandDescription: String,
    scrollbar: @Composable BoxScope.(LazyListState) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(readerString("desktop_no_table_of_contents", "No table of contents"))
        }
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    var searchFieldCanFocus by remember { mutableStateOf(false) }
    // Android benchmark: expanding *every* index (not just structural parents) is what
    // "Expand All" means in the Android drawer.
    var expandedEntryIndices by rememberSaveable(entries) {
        mutableStateOf(readerTocParentIndices(entries, depthOf))
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val activeOriginalIndex = activeIndex?.takeIf { it in entries.indices }
    val visibleEntries = remember(entries, query, expandedEntryIndices, activeOriginalIndex) {
        projectReaderTocEntries(
            entries = entries,
            expandedEntryIndices = expandedEntryIndices,
            query = query,
            activeOriginalIndex = activeOriginalIndex,
            labelOf = labelOf,
            depthOf = depthOf
        )
    }
    val isSearching = query.isNotBlank()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { query = "" },
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
                .pointerInput(entries) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        searchFieldCanFocus = true
                    }
                }
                .focusProperties { canFocus = searchFieldCanFocus }
                .onFocusChanged { state ->
                    if (!state.isFocused) searchFieldCanFocus = false
                }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { expandedEntryIndices = entries.indices.toSet() }) {
                Text(readerString("action_expand_all", "Expand All"))
            }
            TextButton(onClick = { expandedEntryIndices = emptySet() }) {
                Text(readerString("action_collapse_all", "Collapse All"))
            }
            TextButton(
                onClick = {
                    query = ""
                    val plan = readerTocLocatePlan(
                        entries = entries,
                        expandedEntryIndices = expandedEntryIndices,
                        activeOriginalIndex = activeOriginalIndex,
                        depthOf = depthOf
                    )
                    expandedEntryIndices = plan.expandedEntryIndices
                    scope.launch {
                        // The plan's index is in the post-expansion projection, not the
                        // current filtered/collapsed one. Let the LazyColumn consume the
                        // new state before scrolling to it.
                        kotlinx.coroutines.yield()
                        plan.visibleIndex?.let { target ->
                            // Android benchmark: the expanded row may not be measured yet.
                            var attempts = 0
                            while (listState.layoutInfo.totalItemsCount <= target && attempts < 10) {
                                delay(30)
                                attempts++
                            }
                            listState.animateScrollToItem(target)
                        }
                    }
                }
            ) { Text(readerString("action_locate", "Locate")) }
        }
        HorizontalDivider()
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (visibleEntries.isEmpty() && isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = readerString(
                            "no_chapters_matching",
                            "No chapters matching \"%1\$s\"",
                            query.trim()
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 12.dp)
                ) {
                    items(
                        items = visibleEntries,
                        key = { projected -> keyOf(projected.originalIndex, projected.entry) }
                    ) { projected ->
                        val entry = projected.entry
                        SharedAndroidEpubTocTreeItem(
                            label = labelOf(entry),
                            depth = depthOf(entry),
                            isExpanded = projected.originalIndex in expandedEntryIndices,
                            hasChildren = projected.hasChildren,
                            isCurrent = projected.isActive,
                            collapseDescription = collapseDescription,
                            expandDescription = expandDescription,
                            onToggleExpand = {
                                expandedEntryIndices = readerTocToggleExpansion(
                                    entries = entries,
                                    expandedEntryIndices = expandedEntryIndices,
                                    originalIndex = projected.originalIndex,
                                    depthOf = depthOf
                                )
                            },
                            onClick = { onEntryClick(projected.originalIndex, entry) }
                        )
                    }
                }
            }
            scrollbar(listState)
        }
    }
}

/**
 * EPUB highlight/annotation list.
 *
 * Android benchmark (`EpubReaderDrawer.HighlightsList`): `ListItem` + `HorizontalDivider` rows with
 * a color dot, chapter title and a tinted note card. The previous shared version rendered a
 * translucent color `Surface` card with a 10dp dot and plain note text, which is a different
 * component, not a platform difference.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileEpubHighlights(
    highlights: List<UserHighlight>,
    chapterTitleOf: (Int) -> String,
    palette: ReaderHighlightPalette,
    onHighlightClick: (UserHighlight) -> Unit,
    onHighlightEdit: (UserHighlight) -> Unit,
    onHighlightColorChange: (UserHighlight, HighlightColor) -> Unit,
    onDeleteHighlight: (UserHighlight) -> Unit,
    onOpenPaletteManager: (() -> Unit)? = null,
    onExportAnnotations: (() -> Unit)? = null,
    scrollbar: @Composable BoxScope.(LazyListState) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (highlights.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = readerString("no_highlights_yet", "No highlights yet"),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    var notesOnly by remember { mutableStateOf(false) }
    var menuHighlight by remember { mutableStateOf<UserHighlight?>(null) }
    var deleteHighlight by remember { mutableStateOf<UserHighlight?>(null) }
    val listState = rememberLazyListState()
    val filteredHighlights = remember(highlights, notesOnly) {
        if (notesOnly) highlights.filter { !it.note.isNullOrBlank() } else highlights
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = !notesOnly,
                onClick = { notesOnly = false },
                label = { Text(readerString("filter_all", "All")) }
            )
            FilterChip(
                selected = notesOnly,
                onClick = { notesOnly = true },
                label = { Text(readerString("filter_with_notes", "With Notes")) }
            )
            if (onExportAnnotations != null) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onExportAnnotations) {
                    Text(readerString("action_export_annotations", "Export annotations"))
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 4.dp)
            ) {
                items(
                    items = filteredHighlights.sortedBy { it.chapterIndex },
                    key = { it.id }
                ) { highlight ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = highlight.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(highlight.effectiveColor, CircleShape)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = chapterTitleOf(highlight.chapterIndex),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                highlight.note?.takeIf { it.isNotBlank() }?.let { note ->
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = note,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontStyle = FontStyle.Italic
                                            ),
                                            modifier = Modifier.padding(12.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        trailingContent = {
                            Box {
                                IconButton(onClick = { menuHighlight = highlight }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = readerString(
                                            "content_desc_options",
                                            "Options"
                                        )
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuHighlight?.id == highlight.id,
                                    onDismissRequest = { menuHighlight = null }
                                ) {
                                    SharedMobileEpubHighlightColorRow(
                                        palette = palette,
                                        selectedHighlight = highlight,
                                        onOpenPaletteManager = if (onOpenPaletteManager != null) {
                                            {
                                                onOpenPaletteManager()
                                                menuHighlight = null
                                            }
                                        } else {
                                            null
                                        },
                                        onColorSelect = { color ->
                                            onHighlightColorChange(highlight, color)
                                            menuHighlight = null
                                        }
                                    )
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
                                            onHighlightEdit(highlight)
                                            menuHighlight = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(readerString("action_delete", "Delete")) },
                                        onClick = {
                                            deleteHighlight = highlight
                                            menuHighlight = null
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable { onHighlightClick(highlight) }
                    )
                    HorizontalDivider()
                }
            }
            scrollbar(listState)
        }
    }
    deleteHighlight?.let { highlight ->
        AlertDialog(
            onDismissRequest = { deleteHighlight = null },
            title = { Text(readerString("dialog_delete_highlight", "Delete highlight?")) },
            text = { Text(readerString("dialog_delete_highlight_desc", "This removes the highlight.")) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteHighlight(highlight)
                    deleteHighlight = null
                }) {
                    Text(readerString("action_delete", "Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteHighlight = null }) {
                    Text(readerString("action_cancel", "Cancel"))
                }
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileEpubHighlightColorRow(
    palette: ReaderHighlightPalette,
    selectedHighlight: UserHighlight,
    onOpenPaletteManager: (() -> Unit)?,
    onColorSelect: (HighlightColor) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(vertical = 8.dp, horizontal = 10.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        palette.sanitized().colors.forEach { color ->
            val selected = selectedHighlight.color == color && selectedHighlight.colorArgb == null
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color.color)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = CircleShape,
                    )
                    .clickable { onColorSelect(color) },
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = readerString("content_desc_selected_color", "Selected color"),
                        tint = if (color == HighlightColor.WHITE) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        onOpenPaletteManager?.let { openPaletteManager ->
            Spacer(Modifier.width(6.dp))
            SharedReaderHighlightPaletteSpectrumButton(
                onClick = {
                    openPaletteManager()
                },
                size = 28.dp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedMobileEpubHighlightSheet(
    highlight: UserHighlight,
    palette: ReaderHighlightPalette,
    onUpdate: (UserHighlight) -> Unit,
    onDelete: () -> Unit,
    onSpeak: () -> Unit,
    onLookup: (ReaderExternalLookupAction) -> Unit,
    onClipboardError: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val copiedTextLabel = readerString("clip_label_copied_text", "Copied Text")
    val clipboardErrorMessage = readerString("error_copy_to_clipboard", "Could not copy to clipboard")
    var note by remember(highlight.id, highlight.note) { mutableStateOf(highlight.note.orEmpty()) }
    var confirmDelete by remember(highlight.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                readerString("desktop_annotation", "Annotation"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Surface(
                color = highlight.effectiveColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = highlight.text.ifBlank { readerString("desktop_highlight", "Highlight") },
                    modifier = Modifier.padding(14.dp),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = {
                    val result = writeSharedClipboard(copiedTextLabel, highlight.text)
                    if (!result.success) onClipboardError?.invoke(clipboardErrorMessage)
                }) { Text(readerString("action_copy", "Copy")) }
                TextButton(onClick = onSpeak) { Text(readerString("label_speak", "Speak")) }
                TextButton(onClick = { onLookup(ReaderExternalLookupAction.DICTIONARY) }) {
                    Text(readerString("tooltip_dictionary", "Dictionary"))
                }
                TextButton(onClick = { onLookup(ReaderExternalLookupAction.TRANSLATE) }) {
                    Text(readerString("dict_translate", "Translate"))
                }
                TextButton(onClick = { onLookup(ReaderExternalLookupAction.SEARCH) }) {
                    Text(readerString("action_search", "Search"))
                }
            }
            Text(readerString("desktop_color", "Color"), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                palette.sanitized().colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(color.color)
                            .border(
                                width = if (color == highlight.color) 3.dp else 1.dp,
                                color = if (color == highlight.color) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clickable { onUpdate(highlight.copy(color = color, colorArgb = null)) }
                    )
                }
            }
            Text(
                readerString("desktop_label_style", "Style"),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HighlightStyle.entries.forEach { style ->
                    val label = when (style) {
                        HighlightStyle.BACKGROUND -> readerString("desktop_style_background", "Background")
                        HighlightStyle.UNDERLINE -> readerString("desktop_style_underline", "Underline")
                        HighlightStyle.WAVY_UNDERLINE -> readerString("desktop_style_wavy", "Wavy")
                        HighlightStyle.STRIKETHROUGH -> readerString("desktop_style_strike", "Strike")
                    }
                    FilterChip(
                        selected = highlight.style == style,
                        onClick = { onUpdate(highlight.copy(style = style)) },
                        label = { Text(label) }
                    )
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(readerString("desktop_label_comment", "Comment")) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(readerString("action_delete", "Delete"), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = {
                    onUpdate(highlight.copy(note = note.trim().takeIf { it.isNotBlank() }))
                    onDismiss()
                }) { Text(readerString("desktop_save_comment", "Save comment")) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(readerString("desktop_delete_annotation_title", "Delete annotation?")) },
            text = {
                Text(
                    readerString(
                        "desktop_delete_annotation_message",
                        "This removes the highlight and its comment.",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text(readerString("action_delete", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(readerString("action_cancel", "Cancel"))
                }
            }
        )
    }
}

@Composable
fun <T> SharedMobileEpubImages(
    images: List<T>,
    rowOf: (T) -> SharedEpubDrawerImage,
    onImageClick: (T) -> Unit,
    // Android benchmark (EpubReaderDrawer.ImagesList): the trailing action is a host
    // callback, not a built-in share sheet. Android saves the file; iOS shares it.
    onDownloadImage: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    if (images.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(readerString("no_images_found", "No images found"))
        }
        return
    }
    // Row projection is derived once per image list; the host keeps owning the
    // original model so click handlers can still navigate with it.
    val rows = remember(images) { images.map(rowOf) }
    val listState = rememberLazyListState()
    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 4.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(rows, key = { _, row -> row.id }) { index, image ->
                val host = images[index]
                ListItem(
                    leadingContent = {
                        SharedMobileEpubImageThumbnail(
                            image = image,
                            modifier = Modifier.size(width = 72.dp, height = 56.dp)
                        )
                    },
                    headlineContent = {
                        Text(
                            text = image.displayTitle,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                text = image.chapterTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val metadata = image.metadataLabel()
                            if (metadata.isNotBlank()) {
                                Text(
                                    text = metadata,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { onDownloadImage(host) }) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = readerString(
                                    "content_desc_download_image",
                                    "Download image"
                                )
                            )
                        }
                    },
                    modifier = Modifier.clickable { onImageClick(host) }
                )
                HorizontalDivider()
            }
        }
    }
}

/**
 * Android benchmark parity: EpubReaderDrawer.EpubReaderImageThumbnail (72x56, rounded 6dp,
 * surfaceVariant fill, Fit + index fallback). Shared-first so Android + iOS share the same
 * leading-content behavior; decoding reuses the shared [decodeSharedMobileEpubImage] actuals.
 */
@Composable
private fun SharedMobileEpubImageThumbnail(
    image: SharedEpubDrawerImage,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(image.source) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image.source) {
        bitmap = withContext(Dispatchers.Default) {
            val bytes = image.loadBytes() ?: return@withContext null
            decodeSharedMobileEpubImage(bytes, image.isSvg)
        }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = image.ordinal.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


