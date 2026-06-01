package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.Tag
import com.aryan.reader.shared.cardAuthor
import com.aryan.reader.shared.cardTitle
import com.aryan.reader.shared.formatFileSize
import com.aryan.reader.shared.parseTagList

@Composable
fun SharedTextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SharedStableOutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmLabel)
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
fun SharedConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
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
fun SharedAddToShelfDialog(
    shelves: List<Shelf>,
    onDismiss: () -> Unit,
    onCreateShelf: () -> Unit,
    onShelvesSelected: (Set<String>) -> Unit
) {
    var selectedShelfIds by remember(shelves) { mutableStateOf(emptySet<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(readerString("desktop_add_to_shelf", "Add to shelf")) },
        text = {
            if (shelves.isEmpty()) {
                Text(readerString("desktop_create_shelf_for_books", "Create a shelf and the chosen books will be added to it."))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        readerString("desktop_select_shelves_to_add", "Choose one or more shelves."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 360.dp)) {
                        items(shelves, key = { it.id }) { shelf ->
                            val selected = shelf.id in selectedShelfIds
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedShelfIds = selectedShelfIds.toggle(shelf.id)
                                }
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = {
                                            selectedShelfIds = selectedShelfIds.toggle(shelf.id)
                                        }
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        shelf.name,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("${shelf.bookCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (shelves.isEmpty()) {
                Button(onClick = onCreateShelf) {
                    Text(readerString("fab_new_shelf", "New shelf"))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCreateShelf) {
                        Text(readerString("fab_new_shelf", "New shelf"))
                    }
                    Button(
                        onClick = { onShelvesSelected(selectedShelfIds) },
                        enabled = selectedShelfIds.isNotEmpty()
                    ) {
                        Text(readerString("action_add", "Add"))
                    }
                }
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
fun SharedManageShelfBooksDialog(
    shelf: Shelf,
    books: List<BookItem>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var query by remember(shelf.id) { mutableStateOf("") }
    var selectedBookIds by remember(shelf.id, shelf.books) {
        mutableStateOf(shelf.books.mapTo(linkedSetOf<String>()) { it.id }.toSet())
    }
    val normalizedQuery = query.trim()
    val visibleBooks = remember(books, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            books
        } else {
            books.filter { book ->
                book.cardTitle().contains(normalizedQuery, ignoreCase = true) ||
                    book.cardAuthor().contains(normalizedQuery, ignoreCase = true) ||
                    book.displayName.contains(normalizedQuery, ignoreCase = true) ||
                    book.tags.any { tag -> tag.name.contains(normalizedQuery, ignoreCase = true) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(readerString("desktop_manage_shelf_books_title", "Manage %1\$s", shelf.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SharedStableOutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(readerString("library_search_placeholder", "Search books, authors, or tags")) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    readerString("desktop_shelf_selected_book_count", "%1\$d selected", selectedBookIds.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (books.isEmpty()) {
                    Text(readerString("your_library_empty", "Your library is empty"))
                } else if (visibleBooks.isEmpty()) {
                    Text(readerString("desktop_no_books_match", "No books match."))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(visibleBooks, key = { it.id }) { book ->
                            val selected = book.id in selectedBookIds
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedBookIds = selectedBookIds.toggle(book.id)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = {
                                            selectedBookIds = selectedBookIds.toggle(book.id)
                                        }
                                    )
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(book.cardTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            book.cardAuthor(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedBookIds) }) {
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

private fun Set<String>.toggle(value: String): Set<String> {
    return if (value in this) this - value else this + value
}

@Composable
fun SharedBookInfoDialog(
    book: BookItem,
    knownTags: List<Tag> = emptyList(),
    initiallyEditing: Boolean = false,
    canEditEmbeddedMetadata: Boolean = book.type == FileType.EPUB,
    canRenameDisplayName: Boolean = true,
    canRestoreEmbeddedMetadata: Boolean = canEditEmbeddedMetadata,
    onDismiss: () -> Unit,
    onSave: (BookItem) -> Unit,
    onRestore: (BookItem) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var isEditing by remember(book.id, initiallyEditing) { mutableStateOf(initiallyEditing) }
    var titleInput by remember(book.id, book.title) { mutableStateOf(book.title.orEmpty()) }
    var authorInput by remember(book.id, book.author) { mutableStateOf(book.author.orEmpty()) }
    var seriesInput by remember(book.id, book.seriesName) { mutableStateOf(book.seriesName.orEmpty()) }
    var seriesIndexInput by remember(book.id, book.seriesIndex) {
        mutableStateOf(book.seriesIndex?.formatMetadataNumber().orEmpty())
    }
    var descriptionInput by remember(book.id, book.description) { mutableStateOf(book.description.orEmpty()) }
    var displayNameInput by remember(book.id, book.displayName) { mutableStateOf(book.displayName) }
    var tagInput by remember(book.id, book.tags) { mutableStateOf(book.tags.joinToString(", ") { it.name }) }
    var showRestoreConfirmation by remember(book.id) { mutableStateOf(false) }

    val hasOriginalMetadata = book.hasOriginalMetadata()
    val hasMetadataChanges = book.hasMetadataChanges()

    Dialog(
        onDismissRequest = {
            if (isEditing) {
                isEditing = false
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize()) {
                SharedBookInfoTopBar(
                    title = if (isEditing) {
                        if (canEditEmbeddedMetadata) {
                            readerString("desktop_edit_epub_metadata", "Edit EPUB metadata")
                        } else {
                            readerString("desktop_rename_in_app", "Rename in app")
                        }
                    } else {
                        readerString("file_information", "Book information")
                    },
                    subtitle = book.cardTitle(),
                    onClose = {
                        if (isEditing) {
                            isEditing = false
                        } else {
                            onDismiss()
                        }
                    }
                )

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isEditing) {
                        if (canEditEmbeddedMetadata) {
                            SharedBookMetadataEditContent(
                                titleInput = titleInput,
                                onTitleChange = { titleInput = it },
                                authorInput = authorInput,
                                onAuthorChange = { authorInput = it },
                                seriesInput = seriesInput,
                                onSeriesChange = { seriesInput = it },
                                seriesIndexInput = seriesIndexInput,
                                onSeriesIndexChange = { seriesIndexInput = it },
                                descriptionInput = descriptionInput,
                                onDescriptionChange = { descriptionInput = it },
                                tagInput = tagInput,
                                onTagChange = { tagInput = it },
                                knownTags = knownTags
                            )
                        } else if (canRenameDisplayName) {
                            SharedBookDisplayNameEditContent(
                                displayNameInput = displayNameInput,
                                onDisplayNameChange = { displayNameInput = it },
                                tagInput = tagInput,
                                onTagChange = { tagInput = it },
                                knownTags = knownTags
                            )
                        }
                    } else {
                        SharedBookMetadataInfoContent(
                            book = book,
                            hasMetadataChanges = hasMetadataChanges,
                            onCopyPath = {
                                book.path?.takeIf { it.isNotBlank() }?.let { clipboard.setText(AnnotatedString(it)) }
                            }
                        )
                    }
                }

                HorizontalDivider()

                SharedBookInfoBottomBar(
                    isEditing = isEditing,
                    canEdit = canEditEmbeddedMetadata || canRenameDisplayName,
                    canRestore = canRestoreEmbeddedMetadata && hasOriginalMetadata && (hasMetadataChanges || isEditing),
                    editLabel = if (canEditEmbeddedMetadata) readerString("action_edit", "Edit") else readerString("action_rename", "Rename"),
                    onCancel = {
                        if (isEditing) {
                            isEditing = false
                        } else {
                            onDismiss()
                        }
                    },
                    onRestore = { showRestoreConfirmation = true },
                    onSave = {
                        val updated = if (canEditEmbeddedMetadata) {
                            book.copy(
                                title = titleInput.toMetadataValue()
                                    ?: book.displayName.substringBeforeLast('.', book.displayName),
                                author = authorInput.toMetadataValue(),
                                seriesName = seriesInput.toMetadataValue(),
                                seriesIndex = seriesIndexInput.toSeriesIndexOrNull(),
                                description = descriptionInput.toMetadataValue(),
                                originalTitle = book.originalTitle ?: book.title,
                                originalAuthor = book.originalAuthor ?: book.author,
                                originalSeriesName = book.originalSeriesName ?: book.seriesName,
                                originalSeriesIndex = book.originalSeriesIndex ?: book.seriesIndex,
                                originalDescription = book.originalDescription ?: book.description,
                                tags = parseTagList(tagInput, knownTags)
                            )
                        } else {
                            book.copy(
                                displayName = displayNameInput.toMetadataValue() ?: book.displayName,
                                tags = parseTagList(tagInput, knownTags)
                            )
                        }
                        onSave(updated)
                        onDismiss()
                    },
                    onEdit = { isEditing = true }
                )
            }
        }
    }

    if (showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmation = false },
            icon = { Icon(Icons.Default.Restore, contentDescription = null) },
            title = { Text(readerString("dialog_restore_original_metadata", "Restore original metadata?")) },
            text = {
                Text(
                    readerString(
                        "dialog_restore_original_metadata_desc",
                        "This will write the original title, author, series, and summary back into the EPUB file. Reading progress, tags, and notes will not change."
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmation = false
                        onRestore(book.restoredOriginalMetadata())
                        onDismiss()
                    }
                ) {
                    Text(readerString("action_restore", "Restore"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmation = false }) {
                    Text(readerString("action_cancel", "Cancel"))
                }
            }
        )
    }
}

@Composable
private fun SharedBookInfoTopBar(
    title: String,
    subtitle: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = readerString("action_close", "Close"))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SharedBookMetadataInfoContent(
    book: BookItem,
    hasMetadataChanges: Boolean,
    onCopyPath: () -> Unit
) {
    SharedInfoCard {
        Text(
            book.cardTitle(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        book.author
            ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
            ?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        val provenance = when {
            book.type == FileType.EPUB && hasMetadataChanges -> readerString("metadata_provenance_epub_edited", "EPUB metadata edited")
            book.type == FileType.EPUB -> readerString("metadata_provenance_from_epub", "Metadata from EPUB file")
            hasMetadataChanges -> readerString("metadata_provenance_display_name_changed", "Display name changed in app")
            else -> readerString("metadata_provenance_from_file", "Metadata from file")
        }
        Text(
            provenance,
            style = MaterialTheme.typography.labelMedium,
            color = if (hasMetadataChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SharedInfoSection(title = readerString("section_metadata", "Metadata")) {
        SharedInfoRowDetailed(readerString("label_title", "Title"), book.title?.takeIf { it.isNotBlank() } ?: book.displayName, maxLines = 3)
        book.author?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }?.let {
            SharedInfoRowDetailed(readerString("author", "Author"), it, maxLines = 2)
        }
        book.seriesLabel()?.let {
            SharedInfoRowDetailed(readerString("label_series", "Series"), it, maxLines = 2)
        }
        SharedInfoRowDetailed(readerString("format", "Format"), book.type.name)
        SharedInfoRowDetailed(readerString("size", "Size"), formatFileSize(book.fileSize))
        SharedInfoRowDetailed(readerString("label_reading", "Reading"), book.readingProgressText(), maxLines = 2)
    }

    SharedInfoSection(title = readerString("section_file", "File")) {
        SharedInfoRowDetailed(readerString("label_file_name_simple", "File name"), book.displayName, maxLines = 2)
        SharedInfoRowDetailed(readerString("location", "Location"), book.path.orEmpty().ifBlank { readerString("not_available_locally", "Not available") }, maxLines = 4, onCopy = onCopyPath)
        book.sourceFolder?.takeIf { it.isNotBlank() }?.let {
            SharedInfoRowDetailed(readerString("filter_source_folder", "Source folder"), it, maxLines = 3)
        }
    }

    book.description?.takeIf { it.isNotBlank() }?.let { summary ->
        SharedInfoSection(title = readerString("label_summary", "Summary")) {
            SharedExpandableSummaryText(summary, collapsedMaxLines = 4)
        }
    }

    SharedInfoSection(title = readerString("section_tags", "Tags")) {
        if (book.tags.isEmpty()) {
            Text(
                readerString("msg_no_tags_assigned", "No tags assigned."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                book.tags.forEach { tag ->
                    AssistChip(onClick = {}, label = { Text(tag.name) })
                }
            }
        }
    }
}

@Composable
private fun SharedBookMetadataEditContent(
    titleInput: String,
    onTitleChange: (String) -> Unit,
    authorInput: String,
    onAuthorChange: (String) -> Unit,
    seriesInput: String,
    onSeriesChange: (String) -> Unit,
    seriesIndexInput: String,
    onSeriesIndexChange: (String) -> Unit,
    descriptionInput: String,
    onDescriptionChange: (String) -> Unit,
    tagInput: String,
    onTagChange: (String) -> Unit,
    knownTags: List<Tag>
) {
    SharedInfoSection(title = readerString("label_editable_metadata", "Editable metadata")) {
        SharedStableOutlinedTextField(
            value = titleInput,
            onValueChange = onTitleChange,
            label = { Text(readerString("label_title", "Title")) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            selectionKey = "title"
        )
        SharedStableOutlinedTextField(
            value = authorInput,
            onValueChange = onAuthorChange,
            label = { Text(readerString("author", "Author")) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            selectionKey = "author"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SharedStableOutlinedTextField(
                value = seriesInput,
                onValueChange = onSeriesChange,
                label = { Text(readerString("label_series", "Series")) },
                modifier = Modifier.weight(1f),
                maxLines = 2,
                selectionKey = "series"
            )
            SharedStableOutlinedTextField(
                value = seriesIndexInput,
                onValueChange = onSeriesIndexChange,
                label = { Text("#") },
                modifier = Modifier.width(96.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                selectionKey = "seriesIndex"
            )
        }
        SharedStableOutlinedTextField(
            value = descriptionInput,
            onValueChange = onDescriptionChange,
            label = { Text(readerString("label_summary", "Summary")) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 128.dp),
            minLines = 4,
            maxLines = 10,
            selectionKey = "description"
        )
    }

    SharedInfoSection(title = readerString("label_library_tags", "Library tags")) {
        SharedStableOutlinedTextField(
            value = tagInput,
            onValueChange = onTagChange,
            label = { Text(readerString("desktop_tags_comma_separated", "Tags, comma separated")) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            selectionKey = "tags"
        )
        if (knownTags.isNotEmpty()) {
            Text(
                readerString("desktop_existing_tags_format", "Existing: %1\$s", knownTags.joinToString { it.name }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SharedBookDisplayNameEditContent(
    displayNameInput: String,
    onDisplayNameChange: (String) -> Unit,
    tagInput: String,
    onTagChange: (String) -> Unit,
    knownTags: List<Tag>
) {
    SharedInfoSection(title = readerString("label_display_name", "Display name")) {
        SharedStableOutlinedTextField(
            value = displayNameInput,
            onValueChange = onDisplayNameChange,
            label = { Text(readerString("label_name_shown_in_reader", "Name shown in Reader")) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            selectionKey = "displayName"
        )
    }

    SharedInfoSection(title = readerString("label_library_tags", "Library tags")) {
        SharedStableOutlinedTextField(
            value = tagInput,
            onValueChange = onTagChange,
            label = { Text(readerString("desktop_tags_comma_separated", "Tags, comma separated")) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            selectionKey = "renameTags"
        )
        if (knownTags.isNotEmpty()) {
            Text(
                readerString("desktop_existing_tags_format", "Existing: %1\$s", knownTags.joinToString { it.name }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SharedBookInfoBottomBar(
    isEditing: Boolean,
    canEdit: Boolean,
    canRestore: Boolean,
    editLabel: String,
    onCancel: () -> Unit,
    onRestore: () -> Unit,
    onSave: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canRestore) {
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(readerString("action_restore", "Restore"))
            }
        }
        TextButton(onClick = onCancel) {
            Text(if (isEditing) readerString("action_cancel", "Cancel") else readerString("action_close", "Close"))
        }
        Spacer(Modifier.width(8.dp))
        if (isEditing) {
            Button(onClick = onSave) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(readerString("action_save", "Save"))
            }
        } else if (canEdit) {
            Button(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(editLabel)
            }
        }
    }
}

@Composable
private fun SharedInfoSection(
    title: String,
    content: @Composable () -> Unit
) {
    SharedInfoCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun SharedInfoCard(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SharedInfoRowDetailed(
    label: String,
    value: String,
    maxLines: Int = 1,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(112.dp)
                .padding(top = 2.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            SharedExpandableValueText(value, collapsedMaxLines = maxLines)
        }
        if (onCopy != null && value != readerString("not_available_locally", "Not available")) {
            TextButton(
                onClick = onCopy,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(readerString("action_copy", "Copy"))
            }
        }
    }
}

@Composable
private fun SharedExpandableValueText(
    value: String,
    collapsedMaxLines: Int
) {
    var expanded by remember(value) { mutableStateOf(false) }
    val canExpand = collapsedMaxLines < Int.MAX_VALUE && (value.length > 120 || value.contains('\n'))
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp)
    )
    if (canExpand) {
        SharedMoreButton(expanded = expanded, onClick = { expanded = !expanded })
    }
}

@Composable
private fun SharedExpandableSummaryText(
    value: String,
    collapsedMaxLines: Int
) {
    var expanded by remember(value) { mutableStateOf(false) }
    val renderableSummary = remember(value) {
        if (value.looksLikeHtml()) value.htmlToMarkdownSummary() else value
    }
    val canExpand = value.length > 220 || value.count { it == '\n' } >= collapsedMaxLines || value.looksLikeHtml()
    val contentModifier = if (expanded) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .heightIn(max = (collapsedMaxLines * 26).dp)
            .clipToBounds()
    }

    Box(modifier = contentModifier) {
        SharedMarkdownText(
            markdown = renderableSummary,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium
        )
    }

    if (canExpand) {
        SharedMoreButton(expanded = expanded, onClick = { expanded = !expanded })
    }
}

@Composable
private fun SharedMoreButton(
    expanded: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(if (expanded) readerString("desktop_less", "Less") else readerString("desktop_more", "...more"))
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun BookItem.hasOriginalMetadata(): Boolean {
    return listOf(originalTitle, originalAuthor, originalSeriesName, originalDescription).any { !it.isNullOrBlank() } ||
        originalSeriesIndex != null
}

private fun BookItem.hasMetadataChanges(): Boolean {
    return metadataValueChanged(title, originalTitle) ||
        metadataValueChanged(author, originalAuthor) ||
        metadataValueChanged(seriesName, originalSeriesName) ||
        seriesIndex != originalSeriesIndex ||
        metadataValueChanged(description, originalDescription)
}

private fun BookItem.restoredOriginalMetadata(): BookItem {
    return copy(
        title = originalTitle?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.', displayName),
        author = originalAuthor,
        seriesName = originalSeriesName,
        seriesIndex = originalSeriesIndex,
        description = originalDescription
    )
}

private fun metadataValueChanged(current: String?, original: String?): Boolean {
    return current.orEmpty().trim() != original.orEmpty().trim()
}

private fun BookItem.seriesLabel(): String? {
    val series = seriesName?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return seriesIndex?.takeIf { it > 0.0 }?.let { "$series #${it.formatMetadataNumber()}" } ?: series
}

private fun BookItem.readingProgressText(): String {
    val progress = progressPercentage?.coerceIn(0f, 100f)
    val progressText = progress?.toDouble()?.formatMetadataNumber()?.let { "$it%" } ?: "Not started"
    val chapterIndex = readerPosition?.chapterIndex
    val locatorText = when {
        lastPageIndex != null -> "Last page ${lastPageIndex + 1}"
        chapterIndex != null -> "Chapter ${chapterIndex + 1}"
        else -> null
    }
    return listOfNotNull(progressText, locatorText).joinToString(" - ")
}

private fun String.toMetadataValue(): String? {
    return trim().takeIf { it.isNotEmpty() }
}

private fun String.toSeriesIndexOrNull(): Double? {
    return trim()
        .replace(',', '.')
        .takeIf { it.isNotEmpty() }
        ?.toDoubleOrNull()
        ?.takeIf { it > 0.0 }
}

private fun Double.formatMetadataNumber(): String {
    val whole = toLong()
    return if (this == whole.toDouble()) whole.toString() else toString().trimEnd('0').trimEnd('.')
}

private fun String.looksLikeHtml(): Boolean {
    return contains(Regex("<\\s*/?\\s*(p|br|div|span|strong|em|ul|ol|li|h[1-6]|blockquote|a|b|i)\\b", RegexOption.IGNORE_CASE)) ||
        contains(Regex("&(#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);"))
}

private fun String.htmlToMarkdownSummary(): String {
    var text = decodeHtmlEntities()
        .replace(Regex("(?is)<script\\b.*?</script>"), "")
        .replace(Regex("(?is)<style\\b.*?</style>"), "")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|div|section|article)\\s*>"), "\n\n")
        .replace(Regex("(?i)<li\\b[^>]*>"), "\n- ")
        .replace(Regex("(?i)</li\\s*>"), "")
        .replace(Regex("(?i)</?(ul|ol)\\b[^>]*>"), "\n")
        .replace(Regex("(?is)<h1\\b[^>]*>(.*?)</h1>")) { "# ${it.groupValues[1].stripHtmlTags()}\n\n" }
        .replace(Regex("(?is)<h2\\b[^>]*>(.*?)</h2>")) { "## ${it.groupValues[1].stripHtmlTags()}\n\n" }
        .replace(Regex("(?is)<h[3-6]\\b[^>]*>(.*?)</h[3-6]>")) { "### ${it.groupValues[1].stripHtmlTags()}\n\n" }
        .replace(Regex("(?is)<(strong|b)\\b[^>]*>(.*?)</(strong|b)>")) { "**${it.groupValues[2].stripHtmlTags()}**" }
        .replace(Regex("(?is)<(em|i)\\b[^>]*>(.*?)</(em|i)>")) { "*${it.groupValues[2].stripHtmlTags()}*" }
        .replace(Regex("(?is)<blockquote\\b[^>]*>(.*?)</blockquote>")) {
            it.groupValues[1].stripHtmlTags().lines().joinToString("\n") { line -> "> $line" } + "\n\n"
        }
        .replace(Regex("(?is)<a\\b[^>]*>(.*?)</a>")) { it.groupValues[1].stripHtmlTags() }
        .stripHtmlTags()
        .decodeHtmlEntities()

    text = text
        .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        .replace(Regex(" *\\n *"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
    return text
}

private fun String.stripHtmlTags(): String {
    return replace(Regex("<[^>]+>"), " ")
}

private fun String.decodeHtmlEntities(): String {
    return replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            match.groupValues[1].toIntOrNull(16)?.toChar()?.toString().orEmpty()
        }
        .replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
        }
}
