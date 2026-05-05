package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.Tag
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
            OutlinedTextField(
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
                Text("Cancel")
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
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SharedAddToShelfDialog(
    shelves: List<Shelf>,
    onDismiss: () -> Unit,
    onCreateShelf: () -> Unit,
    onShelfSelected: (Shelf) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to shelf") },
        text = {
            if (shelves.isEmpty()) {
                Text("Create a shelf first, then add selected books to it.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(shelves, key = { it.id }) { shelf ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().clickable { onShelfSelected(shelf) }
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(shelf.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${shelf.bookCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateShelf) {
                Text("New shelf")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SharedBookInfoDialog(
    book: BookItem,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.cardTitle()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SharedInfoRow("File", book.displayName)
                SharedInfoRow("Type", book.type.name)
                SharedInfoRow("Author", book.author.orEmpty().ifBlank { "Unknown" })
                SharedInfoRow("Path", book.path.orEmpty().ifBlank { "Not available" })
                SharedInfoRow("Size", formatFileSize(book.fileSize))
                SharedInfoRow("Progress", "${(book.progressPercentage ?: 0f).toInt()}%")
                if (!book.seriesName.isNullOrBlank()) {
                    SharedInfoRow("Series", listOfNotNull(book.seriesName, book.seriesIndex?.toString()).joinToString(" #"))
                }
                if (book.tags.isNotEmpty()) {
                    SharedInfoRow("Tags", book.tags.joinToString { it.name })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) {
                Text("Edit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun SharedBookEditDialog(
    book: BookItem,
    knownTags: List<Tag>,
    onDismiss: () -> Unit,
    onSave: (BookItem) -> Unit
) {
    var title by remember(book.id) { mutableStateOf(book.title.orEmpty()) }
    var author by remember(book.id) { mutableStateOf(book.author.orEmpty()) }
    var seriesName by remember(book.id) { mutableStateOf(book.seriesName.orEmpty()) }
    var seriesIndex by remember(book.id) { mutableStateOf(book.seriesIndex?.toString().orEmpty()) }
    var tagText by remember(book.id) { mutableStateOf(book.tags.joinToString(", ") { it.name }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit book") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Author") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = seriesName, onValueChange = { seriesName = it }, label = { Text("Series") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = seriesIndex, onValueChange = { seriesIndex = it }, label = { Text("Series index") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tagText, onValueChange = { tagText = it }, label = { Text("Tags, comma separated") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (knownTags.isNotEmpty()) {
                    Text("Existing: ${knownTags.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        book.copy(
                            title = title.trim().ifBlank { null },
                            author = author.trim().ifBlank { null },
                            seriesName = seriesName.trim().ifBlank { null },
                            seriesIndex = seriesIndex.toDoubleOrNull(),
                            tags = parseTagList(tagText, knownTags)
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SharedInfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
