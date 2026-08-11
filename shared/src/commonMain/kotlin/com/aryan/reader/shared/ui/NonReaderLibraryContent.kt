package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SortOrder
import com.aryan.reader.shared.cardAuthor
import com.aryan.reader.shared.cardTitle
import com.aryan.reader.shared.isOpdsStream
import com.aryan.reader.shared.progressPercentValue


@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun BookGrid(
    books: List<BookItem>,
    viewMode: BookViewMode,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onSaveOriginalFile: ((BookItem) -> Unit)? = null,
    onShareOriginalFile: ((BookItem) -> Unit)? = null,
    onAddToShelf: ((BookItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (viewMode == BookViewMode.LIST) {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(books, key = { it.id }) { book ->
                BookListItem(
                    book = book,
                    selected = book.id in selectedBookIds,
                    pinned = book.id in pinnedBookIds,
                    selectionModeActive = selectedBookIds.isNotEmpty(),
                    onOpen = { onOpenBook(book) },
                    onToggleSelection = { onToggleSelection(book.id) },
                    onShowInfo = { onShowBookInfo(book) },
                    onEdit = { onEditBook(book) },
                    onTogglePinned = { onTogglePinned(book) },
                    onSaveOriginalFile = onSaveOriginalFile?.let { save -> { save(book) } },
                    onShareOriginalFile = onShareOriginalFile?.let { share -> { share(book) } },
                    onAddToShelf = onAddToShelf?.let { addToShelf -> { addToShelf(book) } }
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(148.dp),
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(books, key = { it.id }) { book ->
                BookTile(
                    book = book,
                    selected = book.id in selectedBookIds,
                    pinned = book.id in pinnedBookIds,
                    selectionModeActive = selectedBookIds.isNotEmpty(),
                    onOpen = { onOpenBook(book) },
                    onToggleSelection = { onToggleSelection(book.id) },
                    onShowInfo = { onShowBookInfo(book) },
                    onEdit = { onEditBook(book) },
                    onTogglePinned = { onTogglePinned(book) },
                    onSaveOriginalFile = onSaveOriginalFile?.let { save -> { save(book) } },
                    onShareOriginalFile = onShareOriginalFile?.let { share -> { share(book) } },
                    onAddToShelf = onAddToShelf?.let { addToShelf -> { addToShelf(book) } }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun BookTile(
    book: BookItem,
    selected: Boolean,
    pinned: Boolean,
    selectionModeActive: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onShowInfo: () -> Unit,
    onEdit: () -> Unit,
    onTogglePinned: () -> Unit,
    onSaveOriginalFile: (() -> Unit)? = null,
    onShareOriginalFile: (() -> Unit)? = null,
    onAddToShelf: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionModeActive) onToggleSelection() else onOpen()
                },
                onLongClick = onToggleSelection
            )
    ) {
        Column {
            Box {
                BookCoverArt(
                    book = book,
                    selected = selected,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)
                )
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (pinned) {
                        OverlayBadge(Icons.Default.PushPin, readerString("pinned", "Pinned"))
                    }
                    if (book.sourceFolder != null) {
                        OverlayBadge(Icons.Default.Folder, readerString("desktop_book_badge_folder", "Folder"))
                    }
                    if (book.isOpdsStream()) {
                        OverlayBadge(Icons.Default.Cloud, readerString("action_stream", "Stream"))
                    }
                }
                Box(Modifier.align(Alignment.TopEnd).padding(3.dp)) {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = readerString("desktop_book_actions", "Book actions"))
                    }
                    BookActionMenu(
                        expanded = menuExpanded,
                        pinned = pinned,
                        selected = selected,
                        onDismiss = { menuExpanded = false },
                        onTogglePinned = onTogglePinned,
                        onShowInfo = onShowInfo,
                        onEdit = onEdit,
                        onToggleSelection = onToggleSelection,
                        onSaveOriginalFile = onSaveOriginalFile.takeIf { !book.isOpdsStream() && book.path != null },
                        onShareOriginalFile = onShareOriginalFile.takeIf { !book.isOpdsStream() && book.path != null },
                        onAddToShelf = onAddToShelf
                    )
                }
                TypeBadge(book.type, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
                val percent = progressPercentValue(book.progressPercentage)
                if (percent > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text("$percent%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(book.cardTitle(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.cardAuthor(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, minLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun BookListItem(
    book: BookItem,
    selected: Boolean,
    pinned: Boolean,
    selectionModeActive: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onShowInfo: () -> Unit,
    onEdit: () -> Unit,
    onTogglePinned: () -> Unit,
    onSaveOriginalFile: (() -> Unit)? = null,
    onShareOriginalFile: (() -> Unit)? = null,
    onAddToShelf: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionModeActive) onToggleSelection() else onOpen()
                },
                onLongClick = onToggleSelection
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookCoverArt(book = book, selected = selected, modifier = Modifier.size(width = 52.dp, height = 76.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(book.cardTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.cardAuthor(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypeBadge(book.type)
                    if (pinned) StatusBadge(Icons.Default.PushPin, readerString("pinned", "Pinned"))
                    if (book.sourceFolder != null) StatusBadge(Icons.Default.Folder, readerString("desktop_book_badge_folder", "Folder"))
                    if (book.isOpdsStream()) StatusBadge(Icons.Default.Cloud, readerString("action_stream", "Stream"))
                }
                ProgressSection(book.progressPercentage)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = readerString("desktop_book_actions", "Book actions"))
                }
                BookActionMenu(
                    expanded = menuExpanded,
                    pinned = pinned,
                    selected = selected,
                    onDismiss = { menuExpanded = false },
                    onTogglePinned = onTogglePinned,
                    onShowInfo = onShowInfo,
                    onEdit = onEdit,
                    onToggleSelection = onToggleSelection,
                    onSaveOriginalFile = onSaveOriginalFile.takeIf { !book.isOpdsStream() && book.path != null },
                    onShareOriginalFile = onShareOriginalFile.takeIf { !book.isOpdsStream() && book.path != null },
                    onAddToShelf = onAddToShelf
                )
            }
        }
    }
}

@Composable
internal fun BookActionMenu(
    expanded: Boolean,
    pinned: Boolean,
    selected: Boolean,
    onDismiss: () -> Unit,
    onTogglePinned: () -> Unit,
    onShowInfo: () -> Unit,
    onEdit: () -> Unit,
    onToggleSelection: () -> Unit,
    onSaveOriginalFile: (() -> Unit)? = null,
    onShareOriginalFile: (() -> Unit)? = null,
    onAddToShelf: (() -> Unit)? = null
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
            text = { Text(if (pinned) readerString("desktop_unpin", "Unpin") else readerString("desktop_pin", "Pin")) },
            onClick = {
                onDismiss()
                onTogglePinned()
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
            text = { Text(readerString("info", "Info")) },
            onClick = {
                onDismiss()
                onShowInfo()
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            text = { Text(readerString("action_edit", "Edit")) },
            onClick = {
                onDismiss()
                onEdit()
            }
        )
        if (onSaveOriginalFile != null) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                text = { Text(readerString("action_save_copy_to_device", "Save copy to device")) },
                onClick = {
                    onDismiss()
                    onSaveOriginalFile()
                }
            )
        }
        if (onShareOriginalFile != null) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                text = { Text(readerString("action_share", "Share")) },
                onClick = {
                    onDismiss()
                    onShareOriginalFile()
                }
            )
        }
        if (onAddToShelf != null) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                text = { Text(readerString("desktop_add_to_shelf", "Add to shelf")) },
                onClick = {
                    onDismiss()
                    onAddToShelf()
                }
            )
        }
        DropdownMenuItem(
            leadingIcon = { Icon(if (selected) Icons.Default.Check else Icons.AutoMirrored.Filled.List, contentDescription = null) },
            text = { Text(if (selected) readerString("clear_selection", "Clear selection") else readerString("action_select", "Select")) },
            onClick = {
                onDismiss()
                onToggleSelection()
            }
        )
    }
}

@Composable
internal fun BookCoverArt(
    book: BookItem,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val color = fileTypeColor(book.type)
    val coverPath = book.coverImagePath?.takeIf { it.isNotBlank() }
    Surface(
        modifier = modifier,
        color = color,
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(34.dp))
            Text(
                text = book.type.name,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
            )
            if (coverPath != null) {
                LocalBookCoverImage(
                    path = coverPath,
                    contentDescription = book.cardTitle(),
                    modifier = Modifier.matchParentSize()
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(8.dp).size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun OverlayBadge(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.52f),
        contentColor = Color.White
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.padding(5.dp).size(13.dp))
    }
}

@Composable
internal fun TypeBadge(type: FileType, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            type.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
internal fun StatusBadge(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun TagChip(name: String, color: Int?) {
    val tagColor = Color(color ?: 0xFF64B5F6.toInt())
    Surface(
        shape = RoundedCornerShape(50),
        color = tagColor.copy(alpha = 0.14f),
        contentColor = tagColor
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
internal fun ProgressSection(progressPercentage: Float?) {
    val percent = progressPercentValue(progressPercentage)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(readerString("desktop_progress", "Progress"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("$percent%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    }
}

@Composable
internal fun BrowseByTagRow(
    tagShelves: List<Shelf>,
    onTagShelfSelected: (Shelf) -> Unit
) {
    if (tagShelves.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            readerString("section_browse_by_tag", "Browse by tag"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tagShelves.forEach { shelf ->
                FilterChip(
                    selected = false,
                    onClick = { onTagShelfSelected(shelf) },
                    leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(shelf.name) }
                )
            }
        }
    }
}

@Composable
internal fun ShelfCollection(
    shelves: List<Shelf>,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onSaveOriginalFile: ((BookItem) -> Unit)? = null,
    onShareOriginalFile: ((BookItem) -> Unit)? = null,
    onAddBooksToShelf: ((Set<String>) -> Unit)? = null,
    onManageShelfBooks: ((Shelf) -> Unit)? = null,
    onRenameShelf: (Shelf) -> Unit = {},
    onDeleteShelf: (Shelf) -> Unit = {},
    onDeleteTag: (Shelf) -> Unit = {},
    onRemoveFolder: (Shelf) -> Unit = {},
    onOpenShelf: ((Shelf) -> Unit)? = null,
    onCreateShelf: (() -> Unit)? = null,
    emptyTitle: String,
    emptyBody: String,
    emptyActionLabel: String? = null,
    modifier: Modifier = Modifier
) {
    if (shelves.isEmpty()) {
        SharedEmptyState(
            icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(56.dp)) },
            title = emptyTitle,
            body = emptyBody,
            actionLabel = emptyActionLabel,
            onAction = onCreateShelf,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(shelves, key = { it.id }) { shelf ->
            ShelfSection(
                shelf = shelf,
                selectedBookIds = selectedBookIds,
                pinnedBookIds = pinnedBookIds,
                onOpenBook = onOpenBook,
                onToggleSelection = onToggleSelection,
                onShowBookInfo = onShowBookInfo,
                onEditBook = onEditBook,
                onTogglePinned = onTogglePinned,
                onSaveOriginalFile = onSaveOriginalFile,
                onShareOriginalFile = onShareOriginalFile,
                onAddBooksToShelf = onAddBooksToShelf,
                onManageShelfBooks = onManageShelfBooks,
                onRenameShelf = onRenameShelf,
                onDeleteShelf = onDeleteShelf,
                onDeleteTag = onDeleteTag,
                onRemoveFolder = onRemoveFolder,
                onOpenShelf = onOpenShelf
            )
        }
    }
}

@Composable
internal fun ShelfSection(
    shelf: Shelf,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onSaveOriginalFile: ((BookItem) -> Unit)?,
    onShareOriginalFile: ((BookItem) -> Unit)?,
    onAddBooksToShelf: ((Set<String>) -> Unit)?,
    onManageShelfBooks: ((Shelf) -> Unit)?,
    onRenameShelf: (Shelf) -> Unit,
    onDeleteShelf: (Shelf) -> Unit,
    onDeleteTag: (Shelf) -> Unit,
    onRemoveFolder: (Shelf) -> Unit,
    onOpenShelf: ((Shelf) -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val openShelf = onOpenShelf
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (openShelf != null) Modifier.clickable { openShelf(shelf) } else Modifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CollectionCoverStack(shelf)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = shelf.type.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(shelf.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(shelf.subtitleLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (openShelf != null) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = readerString("desktop_open_folder", "Open folder"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (shelf.type == ShelfType.MANUAL && shelf.id != "unshelved") {
                    if (onManageShelfBooks != null) {
                        OutlinedButton(onClick = { onManageShelfBooks(shelf) }) {
                            Icon(
                                if (shelf.bookCount == 0) Icons.Default.Add else Icons.Default.FormatListNumbered,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (shelf.bookCount == 0) {
                                    readerString("fab_add_books", "Add books")
                                } else {
                                    readerString("desktop_manage_books", "Manage books")
                                }
                            )
                        }
                    }
                    IconButton(onClick = { onRenameShelf(shelf) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = readerString("menu_rename_shelf", "Rename shelf"), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onDeleteShelf(shelf) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = readerString("menu_delete_shelf", "Delete shelf"), modifier = Modifier.size(18.dp))
                    }
                } else if (shelf.type == ShelfType.TAG) {
                    IconButton(onClick = { onDeleteTag(shelf) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = readerString("menu_delete_tag", "Delete tag"), modifier = Modifier.size(18.dp))
                    }
                } else if (shelf.type == ShelfType.FOLDER && shelf.parentShelfId == null) {
                    IconButton(onClick = { onRemoveFolder(shelf) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = readerString("menu_remove_folder", "Remove folder"), modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (shelf.books.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shelf.books.take(12), key = { it.id }) { book ->
                        BookTile(
                            book = book,
                            selected = book.id in selectedBookIds,
                            pinned = book.id in pinnedBookIds,
                            selectionModeActive = selectedBookIds.isNotEmpty(),
                            onOpen = { onOpenBook(book) },
                            onToggleSelection = { onToggleSelection(book.id) },
                            onShowInfo = { onShowBookInfo(book) },
                            onEdit = { onEditBook(book) },
                            onTogglePinned = { onTogglePinned(book) },
                            onSaveOriginalFile = onSaveOriginalFile?.let { save -> { save(book) } },
                            onShareOriginalFile = onShareOriginalFile?.let { share -> { share(book) } },
                            onAddToShelf = onAddBooksToShelf?.let { addToShelf -> { addToShelf(setOf(book.id)) } },
                            modifier = Modifier.width(148.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FolderShelfDetail(
    shelf: Shelf,
    childShelves: List<Shelf>,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onSaveOriginalFile: ((BookItem) -> Unit)? = null,
    onShareOriginalFile: ((BookItem) -> Unit)? = null,
    onAddBooksToShelf: ((Set<String>) -> Unit)? = null,
    onOpenShelf: (Shelf) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = readerString("action_back", "Back"))
                }
                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(shelf.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(shelf.subtitleLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (childShelves.isEmpty() && shelf.directBooks.isEmpty()) {
            SharedEmptyState(
                icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(56.dp)) },
                title = readerString("desktop_folder_empty", "Folder is empty"),
                body = readerString("desktop_folder_empty_desc", "No supported files or subfolders are available here."),
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (childShelves.isNotEmpty()) {
                    item(key = "folders_header") {
                        SectionLabel(readerString("section_folders", "Folders"))
                    }
                    items(childShelves, key = { it.id }) { childShelf ->
                        FolderShelfListItem(
                            shelf = childShelf,
                            onOpenShelf = { onOpenShelf(childShelf) }
                        )
                    }
                }
                if (shelf.directBooks.isNotEmpty()) {
                    item(key = "files_header") {
                        SectionLabel(readerString("section_files", "Files"))
                    }
                    items(shelf.directBooks, key = { it.id }) { book ->
                        BookListItem(
                            book = book,
                            selected = book.id in selectedBookIds,
                            pinned = book.id in pinnedBookIds,
                            selectionModeActive = selectedBookIds.isNotEmpty(),
                            onOpen = { onOpenBook(book) },
                            onToggleSelection = { onToggleSelection(book.id) },
                            onShowInfo = { onShowBookInfo(book) },
                            onEdit = { onEditBook(book) },
                            onTogglePinned = { onTogglePinned(book) },
                            onSaveOriginalFile = onSaveOriginalFile?.let { save -> { save(book) } },
                            onShareOriginalFile = onShareOriginalFile?.let { share -> { share(book) } },
                            onAddToShelf = onAddBooksToShelf?.let { addToShelf -> { addToShelf(setOf(book.id)) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun FolderShelfListItem(
    shelf: Shelf,
    onOpenShelf: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenShelf),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CollectionCoverStack(shelf)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(shelf.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(shelf.subtitleLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = readerString("desktop_open_folder", "Open folder"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun Shelf.subtitleLabel(): String {
    if (type != ShelfType.FOLDER) return bookCountLabel(bookCount)
    return when {
        childShelfCount > 0 && directBookCount > 0 -> readerString(
            "desktop_folder_subtitle_folder_file_counts",
            "%1\$s, %2\$s",
            folderCountLabel(childShelfCount),
            fileCountLabel(directBookCount)
        )
        childShelfCount > 0 -> folderCountLabel(childShelfCount)
        directBookCount > 0 -> fileCountLabel(directBookCount)
        else -> bookCountLabel(bookCount)
    }
}

@Composable
internal fun bookCountLabel(count: Int): String {
    return readerQuantityString("book_count", count, "%1\$d book", "%1\$d books", count)
}

@Composable
internal fun folderCountLabel(count: Int): String {
    return readerQuantityString("folder_count", count, "%1\$d folder", "%1\$d folders", count)
}

@Composable
internal fun fileCountLabel(count: Int): String {
    return readerQuantityString("file_count", count, "%1\$d file", "%1\$d files", count)
}

@Composable
internal fun CollectionCoverStack(shelf: Shelf) {
    val booksForCovers = collectionCoverStackBooks(shelf)
    if (booksForCovers.isEmpty()) {
        EmptyCollectionCoverStack(shelf)
        return
    }

    val coverWidth = 38.dp
    val coverHeight = 56.dp
    val horizontalOffset = 7.dp
    val stackWidth = coverWidth + (horizontalOffset * (booksForCovers.size - 1))

    Box(
        modifier = Modifier.size(width = 54.dp, height = 66.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(stackWidth)
                .height(coverHeight)
        ) {
            booksForCovers.forEachIndexed { index, book ->
                CollectionCoverBook(
                    book = book,
                    contentDescription = if (booksForCovers.size == 1) shelf.name else null,
                    modifier = Modifier
                        .size(width = coverWidth, height = coverHeight)
                        .align(Alignment.CenterEnd)
                        .offset(x = -horizontalOffset * index)
                )
            }
        }
    }
}

@Composable
internal fun CollectionCoverBook(
    book: BookItem,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val coverPath = book.coverImagePath?.takeIf { it.isNotBlank() }
    Surface(
        modifier = modifier,
        color = fileTypeColor(book.type),
        contentColor = Color.White,
        shape = RoundedCornerShape(7.dp),
        shadowElevation = 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(18.dp))
            if (coverPath != null) {
                LocalBookCoverImage(
                    path = coverPath,
                    contentDescription = contentDescription,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

@Composable
internal fun EmptyCollectionCoverStack(shelf: Shelf) {
    Box(Modifier.size(width = 54.dp, height = 66.dp)) {
        val colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.36f)
        )
        colors.forEachIndexed { index, color ->
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 56.dp)
                    .align(Alignment.Center)
                    .padding(start = (index * 4).dp, top = (index * 2).dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp))
            )
        }
        Icon(shelf.type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center).size(22.dp))
    }
}

internal fun collectionCoverStackBooks(shelf: Shelf): List<BookItem> {
    val booksForCovers = shelf.books.take(CollectionCoverStackBookLimit).reversed()
    return if (booksForCovers.size <= 1) {
        listOfNotNull(shelf.topBook)
    } else {
        booksForCovers
    }
}

private const val CollectionCoverStackBookLimit = 4

@Composable
internal fun SortMenu(
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(sortOrder.label())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.label()) },
                    onClick = {
                        expanded = false
                        onSortOrderChange(order)
                    },
                    trailingIcon = if (sortOrder == order) {
                        { Icon(Icons.Default.Check, contentDescription = readerString("content_desc_selected", "Selected")) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
internal fun LibraryImportEmptyState(
    onImportBooks: () -> Unit,
    onImportFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    SharedEmptyState(
        icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(56.dp)) },
        title = readerString("your_library_empty", "Your library is empty"),
        body = readerString("desktop_library_empty_desc", "Import files into app storage or add a folder to read files in place."),
        actionLabel = readerString("desktop_import_files", "Import files"),
        onAction = onImportBooks,
        secondaryActionLabel = readerString("fab_add_folder", "Add folder"),
        onSecondaryAction = onImportFolder,
        modifier = modifier
    )
}

@Composable
internal fun SharedEmptyState(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.padding(18.dp), contentAlignment = Alignment.Center) {
                        icon()
                    }
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp)
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = onAction) {
                            Text(actionLabel)
                        }
                        if (secondaryActionLabel != null && onSecondaryAction != null) {
                            OutlinedButton(onClick = onSecondaryAction) {
                                Text(secondaryActionLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NonReaderLibraryTab.label(): String {
    return when (this) {
        NonReaderLibraryTab.BOOKS -> readerString("tab_all_books", "All Books")
        NonReaderLibraryTab.SHELVES -> readerString("tab_shelves", "Shelves")
        NonReaderLibraryTab.SMART_SHELVES -> readerString("desktop_smart_shelves", "Smart")
        NonReaderLibraryTab.TAGS -> readerString("section_tags", "Tags")
        NonReaderLibraryTab.FOLDERS -> readerString("tab_folders", "Folders")
        NonReaderLibraryTab.UNREAD -> readerString("read_status_unread", "Unread")
        NonReaderLibraryTab.IN_PROGRESS -> readerString("read_status_in_progress", "In progress")
        NonReaderLibraryTab.COMPLETED -> readerString("read_status_completed", "Complete")
    }
}

internal val NonReaderLibraryTab.icon: ImageVector
    get() = when (this) {
        NonReaderLibraryTab.BOOKS -> Icons.Default.Book
        NonReaderLibraryTab.SHELVES -> Icons.AutoMirrored.Filled.LibraryBooks
        NonReaderLibraryTab.SMART_SHELVES -> Icons.Default.FilterList
        NonReaderLibraryTab.TAGS -> Icons.Default.Tag
        NonReaderLibraryTab.FOLDERS -> Icons.Default.Folder
        NonReaderLibraryTab.UNREAD -> Icons.Default.Book
        NonReaderLibraryTab.IN_PROGRESS -> Icons.AutoMirrored.Filled.MenuBook
        NonReaderLibraryTab.COMPLETED -> Icons.Default.Check
    }

internal fun NonReaderLibraryTab.count(organization: NonReaderLibraryOrganizationModel): Int {
    return when (this) {
        NonReaderLibraryTab.BOOKS -> organization.allBooksCount
        NonReaderLibraryTab.SHELVES -> organization.shelfCount
        NonReaderLibraryTab.SMART_SHELVES -> organization.smartShelfCount
        NonReaderLibraryTab.TAGS -> organization.tagCount
        NonReaderLibraryTab.FOLDERS -> organization.folderCount
        NonReaderLibraryTab.UNREAD -> organization.unreadCount
        NonReaderLibraryTab.IN_PROGRESS -> organization.inProgressCount
        NonReaderLibraryTab.COMPLETED -> organization.completedCount
    }
}

@Composable
internal fun NonReaderLibraryTab.labelWithCount(organization: NonReaderLibraryOrganizationModel): String {
    val count = count(organization)
    return when (this) {
        NonReaderLibraryTab.BOOKS -> readerQuantityString(
            "desktop_library_tab_books_count",
            count,
            "All Books %1\$d",
            "All Books %1\$d",
            count
        )
        NonReaderLibraryTab.SHELVES -> readerQuantityString(
            "desktop_library_tab_shelves_count",
            count,
            "Shelves %1\$d",
            "Shelves %1\$d",
            count
        )
        NonReaderLibraryTab.SMART_SHELVES -> readerString("desktop_library_tab_smart_shelves_count", "Smart %1\$d", count)
        NonReaderLibraryTab.TAGS -> readerQuantityString(
            "desktop_library_tab_tags_count",
            count,
            "Tags %1\$d",
            "Tags %1\$d",
            count
        )
        NonReaderLibraryTab.FOLDERS -> readerQuantityString(
            "desktop_library_tab_folders_count",
            count,
            "Folders %1\$d",
            "Folders %1\$d",
            count
        )
        NonReaderLibraryTab.UNREAD -> readerString("desktop_library_tab_unread_count", "Unread %1\$d", count)
        NonReaderLibraryTab.IN_PROGRESS -> readerString("desktop_library_tab_in_progress_count", "In progress %1\$d", count)
        NonReaderLibraryTab.COMPLETED -> readerString("desktop_library_tab_completed_count", "Complete %1\$d", count)
    }
}

internal fun NonReaderLibraryTab.readStatusFilter(): ReadStatusFilter? {
    return when (this) {
        NonReaderLibraryTab.UNREAD -> ReadStatusFilter.UNREAD
        NonReaderLibraryTab.IN_PROGRESS -> ReadStatusFilter.IN_PROGRESS
        NonReaderLibraryTab.COMPLETED -> ReadStatusFilter.COMPLETED
        else -> null
    }
}

@Composable
internal fun SortOrder.label(): String {
    return when (this) {
        SortOrder.RECENT -> readerString("sort_recent", "Recent")
        SortOrder.DATE_ADDED_NEWEST -> readerString("sort_date_added_newest", "Newest")
        SortOrder.DATE_ADDED_OLDEST -> readerString("sort_date_added_oldest", "Oldest")
        SortOrder.TITLE_ASC -> readerString("sort_title_az", "Title A-Z")
        SortOrder.AUTHOR_ASC -> readerString("sort_author_az", "Author A-Z")
        SortOrder.PERCENT_ASC -> readerString("sort_percent_asc", "Progress low")
        SortOrder.PERCENT_DESC -> readerString("sort_percent_desc", "Progress high")
        SortOrder.SIZE_ASC -> readerString("sort_size_smallest", "Size small")
        SortOrder.SIZE_DESC -> readerString("sort_size_biggest", "Size large")
    }
}

@Composable
internal fun ReadStatusFilter.label(): String {
    return when (this) {
        ReadStatusFilter.ALL -> readerString("filter_all", "All")
        ReadStatusFilter.UNREAD -> readerString("read_status_unread", "Unread")
        ReadStatusFilter.IN_PROGRESS -> readerString("read_status_in_progress", "In progress")
        ReadStatusFilter.COMPLETED -> readerString("read_status_completed", "Complete")
    }
}

private val ShelfType.icon: ImageVector
    get() = when (this) {
        ShelfType.FOLDER -> Icons.Default.Folder
        ShelfType.TAG -> Icons.Default.Tag
        ShelfType.SMART -> Icons.Default.FilterList
        else -> Icons.AutoMirrored.Filled.LibraryBooks
    }

internal fun LibraryFilters.activeFilterBadge(): String {
    val count = fileTypes.size +
        sourceFolders.size +
        tagIds.size +
        if (readStatus == ReadStatusFilter.ALL) 0 else 1
    return count.toString()
}

internal fun fileTypeColor(type: FileType): Color {
    return when (type) {
        FileType.PDF -> Color(0xFF9C4146)
        FileType.EPUB, FileType.MOBI -> Color(0xFF006C4C)
        FileType.DOCX, FileType.ODT, FileType.FODT, FileType.PPTX -> Color(0xFF0F52BA)
        FileType.CBZ, FileType.CBR, FileType.CB7, FileType.CBT -> Color(0xFF705D49)
        else -> Color(0xFF5D6B82)
    }
}
