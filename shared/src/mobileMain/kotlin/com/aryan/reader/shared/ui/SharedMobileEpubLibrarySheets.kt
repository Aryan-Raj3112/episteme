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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.deduplicatedReaderBookmarks
import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.ReaderImageReference
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderSpreadLayout
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubTocEntry
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun SharedMobileEpubSlider(
    pageIndex: Int,
    pageCount: Int,
    settings: ReaderSettings,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
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
    Surface(modifier, shape = RoundedCornerShape(18.dp), tonalElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                ReaderSpreadLayout.pageRangeLabel(
                    ReaderSpreadLayout.pageNumberForSliderPosition(
                        sliderValue.roundToInt().coerceIn(0, lastSliderPosition) + 1,
                        pageCount,
                        settings
                    ) - 1,
                    pageCount,
                    settings
                ),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(12.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
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
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text("$sliderStepCount")
        }
    }
}

@Composable
internal fun SharedMobileEpubToc(
    epub: SharedEpubBook?,
    selectedIndex: Int,
    onEntryClick: (Int, SharedEpubTocEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = epub?.tableOfContents.orEmpty()
    if (entries.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No table of contents") }
        return
    }
    var query by remember(epub?.id) { mutableStateOf("") }
    var expanded by remember(epub?.id) { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val visibleEntries = remember(entries, query, expanded) {
        entries.withIndex().filter { indexed ->
            val matches = query.isBlank() || indexed.value.label.contains(query, ignoreCase = true)
            matches && (expanded || indexed.value.depth == 0)
        }
    }
    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search chapters") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { expanded = true }) { Text("Expand All") }
            TextButton(onClick = { expanded = false }) { Text("Collapse All") }
            TextButton(
                onClick = {
                    query = ""
                    expanded = true
                    scope.launch { listState.animateScrollToItem(selectedIndex.coerceAtLeast(0)) }
                }
            ) { Text("Locate") }
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(visibleEntries, key = { it.index }) { indexed ->
                val entry = indexed.value
                NavigationDrawerItem(
                    label = {
                        Text(
                            entry.label,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (entry.depth == 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    selected = indexed.index == selectedIndex,
                    onClick = { onEntryClick(indexed.index, entry) },
                    modifier = Modifier.padding(start = (entry.depth * 18).dp, end = 8.dp)
                )
            }
        }
    }
}

@Composable
internal fun SharedMobileEpubBookmarks(
    bookmarks: List<ReaderBookmark>,
    onBookmarkClick: (ReaderBookmark) -> Unit,
    onBookmarkRename: (ReaderBookmark, String) -> Unit,
    onBookmarkDelete: (ReaderBookmark) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuBookmark by remember { mutableStateOf<ReaderBookmark?>(null) }
    var renameBookmark by remember { mutableStateOf<ReaderBookmark?>(null) }
    var deleteBookmark by remember { mutableStateOf<ReaderBookmark?>(null) }
    if (bookmarks.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No bookmarks yet") }
        return
    }
    LazyColumn(modifier) {
        items(
            bookmarks
                .deduplicatedReaderBookmarks()
                .sortedWith(
                    compareBy<ReaderBookmark> { it.locator.chapterIndex ?: Int.MAX_VALUE }
                        .thenBy { it.locator.startOffset ?: Int.MAX_VALUE }
                        .thenBy(ReaderBookmark::pageIndex)
                ),
            key = ReaderBookmark::id,
        ) { bookmark ->
            NavigationDrawerItem(
                label = {
                    Column {
                        Text(bookmark.label?.takeIf { it.isNotBlank() } ?: bookmark.preview.ifBlank { "Bookmark" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Text(bookmark.chapterTitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                selected = false,
                onClick = { onBookmarkClick(bookmark) },
                icon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                badge = {
                    Box {
                        IconButton(onClick = { menuBookmark = bookmark }) { Icon(Icons.Default.MoreVert, contentDescription = "Bookmark options") }
                        DropdownMenu(expanded = menuBookmark?.id == bookmark.id, onDismissRequest = { menuBookmark = null }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { renameBookmark = bookmark; menuBookmark = null })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { deleteBookmark = bookmark; menuBookmark = null })
                        }
                    }
                }
            )
        }
    }
    renameBookmark?.let { bookmark ->
        var label by remember(bookmark.id) { mutableStateOf(bookmark.label ?: bookmark.preview) }
        AlertDialog(
            onDismissRequest = { renameBookmark = null },
            title = { Text("Rename Bookmark") },
            text = { OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("New name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onBookmarkRename(bookmark, label); renameBookmark = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameBookmark = null }) { Text("Cancel") } }
        )
    }
    deleteBookmark?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { deleteBookmark = null },
            title = { Text("Delete Bookmark?") },
            text = { Text("This bookmark will be removed from the book.") },
            confirmButton = { TextButton(onClick = { onBookmarkDelete(bookmark); deleteBookmark = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteBookmark = null }) { Text("Cancel") } }
        )
    }
}

@Composable
internal fun SharedMobileEpubHighlights(
    highlights: List<UserHighlight>,
    chapters: List<com.aryan.reader.shared.reader.SharedEpubChapter>,
    onHighlightClick: (UserHighlight) -> Unit,
    onHighlightEdit: (UserHighlight) -> Unit,
    modifier: Modifier = Modifier
) {
    var notesOnly by remember { mutableStateOf(false) }
    if (highlights.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No annotations yet") }
        return
    }
    val filteredHighlights = if (notesOnly) highlights.filter { !it.note.isNullOrBlank() } else highlights
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = !notesOnly, onClick = { notesOnly = false }, label = { Text("All") })
            FilterChip(selected = notesOnly, onClick = { notesOnly = true }, label = { Text("With Notes") })
        }
        if (filteredHighlights.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No annotations with notes") }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                items(filteredHighlights.sortedBy { it.chapterIndex }, key = { it.id }) { highlight ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onHighlightClick(highlight) },
                        shape = RoundedCornerShape(12.dp),
                        color = highlight.effectiveColor.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = highlight.text.ifBlank { "Highlight" },
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.padding(top = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .background(highlight.effectiveColor, CircleShape)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        chapters.getOrNull(highlight.chapterIndex)
                                            ?.title
                                            ?.takeIf(String::isNotBlank)
                                            ?: "Chapter ${highlight.chapterIndex + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                highlight.note?.takeIf { it.isNotBlank() }?.let { note ->
                                    Text(note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                            IconButton(onClick = { onHighlightEdit(highlight) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit annotation")
                            }
                        }
                    }
                }
            }
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
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
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
            Text("Annotation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Surface(
                color = highlight.effectiveColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = highlight.text.ifBlank { "Highlight" },
                    modifier = Modifier.padding(14.dp),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(highlight.text)) }) { Text("Copy") }
                TextButton(onClick = onSpeak) { Text("Speak") }
                TextButton(onClick = { onLookup(ReaderExternalLookupAction.DICTIONARY) }) { Text("Dictionary") }
                TextButton(onClick = { onLookup(ReaderExternalLookupAction.TRANSLATE) }) { Text("Translate") }
                TextButton(onClick = { onLookup(ReaderExternalLookupAction.SEARCH) }) { Text("Search") }
            }
            Text("Color", style = MaterialTheme.typography.titleSmall)
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
            Text("Style", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HighlightStyle.entries.forEach { style ->
                    val label = when (style) {
                        HighlightStyle.BACKGROUND -> "Background"
                        HighlightStyle.UNDERLINE -> "Underline"
                        HighlightStyle.WAVY_UNDERLINE -> "Wavy"
                        HighlightStyle.STRIKETHROUGH -> "Strike"
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
                label = { Text("Comment") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = {
                    onUpdate(highlight.copy(note = note.trim().takeIf { it.isNotBlank() }))
                    onDismiss()
                }) { Text("Save comment") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete annotation?") },
            text = { Text("This removes the highlight and its comment.") },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
internal fun SharedMobileEpubImages(
    images: List<ReaderImageReference>,
    onImageClick: (ReaderImageReference) -> Unit,
    modifier: Modifier = Modifier
) {
    if (images.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No images in this book") }
        return
    }
    LazyColumn(modifier) {
        items(images, key = { it.id }) { image ->
            val downloadableBytes = remember(image.source) { image.downloadBytes() }
            NavigationDrawerItem(
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                        Text(image.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            image.chapterTitle,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                            listOfNotNull(image.dimensionLabel, image.sourceName()).joinToString(" · ").takeIf { it.isNotBlank() }?.let { metadata ->
                                Text(metadata, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(
                            onClick = {
                                downloadableBytes?.let { bytes ->
                                    shareSharedMobileEpubImage(bytes, image.suggestedDownloadFileName())
                                }
                            },
                            enabled = downloadableBytes != null
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Save or share image")
                        }
                    }
                },
                selected = false,
                onClick = { onImageClick(image) },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}
