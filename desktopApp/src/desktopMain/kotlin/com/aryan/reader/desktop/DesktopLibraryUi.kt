package com.aryan.reader.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.AppAction
import com.aryan.reader.shared.BannerMessage
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.SharedFolderPathResolver
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.SmartCollectionDefinition
import com.aryan.reader.shared.SmartField
import com.aryan.reader.shared.SmartOperator
import com.aryan.reader.shared.SmartRule
import com.aryan.reader.shared.Tag
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.ui.NonReaderLibraryTab
import com.aryan.reader.shared.ui.SharedHomeScreen
import com.aryan.reader.shared.ui.SharedLibraryScreen
import com.aryan.reader.shared.ui.SharedShelvesScreen
import com.aryan.reader.shared.ui.SharedStableOutlinedTextField
import java.io.File

internal fun BookItem.hasEmbeddedMetadataChange(updated: BookItem): Boolean {
    return title != updated.title ||
        author != updated.author ||
        description != updated.description ||
        seriesName != updated.seriesName ||
        seriesIndex != updated.seriesIndex
}

internal fun String.toDesktopSafeFileName(): String {
    return replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "book" }
}

internal fun BookItem.withDesktopImportMetadata(
    enriched: BookItem,
    original: BookItem?
): BookItem {
    fun shouldApplyText(current: String?, originalValue: String?): Boolean {
        return current.isNullOrBlank() || current == originalValue
    }

    return copy(
        title = if (shouldApplyText(title, original?.title)) {
            enriched.title ?: title
        } else {
            title
        },
        author = if (shouldApplyText(author, original?.author)) {
            enriched.author ?: author
        } else {
            author
        },
        description = if (shouldApplyText(description, original?.description)) {
            enriched.description ?: description
        } else {
            description
        },
        seriesName = if (shouldApplyText(seriesName, original?.seriesName)) {
            enriched.seriesName ?: seriesName
        } else {
            seriesName
        },
        seriesIndex = if (seriesIndex == null || seriesIndex == original?.seriesIndex) {
            enriched.seriesIndex ?: seriesIndex
        } else {
            seriesIndex
        },
        originalTitle = originalTitle ?: enriched.originalTitle ?: enriched.title,
        originalAuthor = originalAuthor ?: enriched.originalAuthor ?: enriched.author,
        originalSeriesName = originalSeriesName ?: enriched.originalSeriesName ?: enriched.seriesName,
        originalSeriesIndex = originalSeriesIndex ?: enriched.originalSeriesIndex ?: enriched.seriesIndex,
        originalDescription = originalDescription ?: enriched.originalDescription ?: enriched.description,
        fileSize = enriched.fileSize.takeIf { it > 0L } ?: fileSize,
        fileContentModifiedTimestamp = enriched.fileContentModifiedTimestamp.takeIf { it > 0L }
            ?: fileContentModifiedTimestamp,
        coverImagePath = coverImagePath?.takeIf { File(it).isFile } ?: enriched.coverImagePath,
        folderTextMetadataParsed = folderTextMetadataParsed || enriched.folderTextMetadataParsed
    )
}

internal fun resolvedDesktopReaderSettings(
    book: BookItem,
    readerDefaultSettings: ReaderSettings
): ReaderSettings {
    return book.readerSettings ?: readerDefaultSettings
}

@Composable
internal fun DesktopReaderOpeningScreen(
    opening: DesktopReaderOpening,
    onReturnToLibrary: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Opening ${opening.title}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = opening.formatLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onReturnToLibrary) {
                Text("Return to library")
            }
        }
    }
}

@Composable
internal fun HomeScreen(
    state: SharedReaderScreenState,
    onImportBooks: () -> Unit,
    onImportFolder: () -> Unit,
    onRead: (BookItem) -> Unit,
    onSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTagSelectedBooks: () -> Unit,
    onAddSelectedBooksToShelf: () -> Unit,
    onOpenTab: (BookItem) -> Unit,
    onCloseTab: (BookItem) -> Unit,
    onCloseAllTabs: () -> Unit,
    onRecentLimitChange: (Int) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    SharedHomeScreen(
        state = state,
        onImportBooks = onImportBooks,
        onImportFolder = onImportFolder,
        onOpenBook = onRead,
        onToggleSelection = onSelect,
        onClearSelection = onClearSelection,
        onRemoveSelected = onRemoveSelected,
        onShowBookInfo = onShowBookInfo,
        onEditBook = onEditBook,
        onTagSelectedBooks = onTagSelectedBooks,
        onAddSelectedBooksToShelf = onAddSelectedBooksToShelf,
        onOpenTab = onOpenTab,
        onCloseTab = onCloseTab,
        onCloseAllTabs = onCloseAllTabs,
        onRecentLimitChange = onRecentLimitChange,
        onTogglePinned = onTogglePinned,
        onOpenSettings = onOpenSettings,
        showActiveTabs = false
    )
}

@Composable
internal fun LibraryScreen(
    state: SharedReaderScreenState,
    selectedLibraryTab: NonReaderLibraryTab,
    onLibraryTabChange: (NonReaderLibraryTab) -> Unit,
    onStateChange: (SharedReaderScreenState) -> Unit,
    onImportBooks: () -> Unit,
    onRead: (BookItem) -> Unit,
    onSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onCreateShelf: () -> Unit,
    onCreateSmartShelf: () -> Unit,
    onRenameShelf: (Shelf) -> Unit,
    onDeleteShelf: (Shelf) -> Unit,
    onRemoveFolder: (Shelf) -> Unit,
    onTagSelectedBooks: () -> Unit,
    onAddSelectedBooksToShelf: () -> Unit,
    onImportFolder: () -> Unit,
    onSyncFolderMetadata: () -> Unit,
    onScanFolders: () -> Unit,
    onTogglePinned: (BookItem) -> Unit
) {
    SharedLibraryScreen(
        state = state,
        selectedTab = selectedLibraryTab,
        onTabChange = onLibraryTabChange,
        onStateChange = onStateChange,
        onImportBooks = onImportBooks,
        onOpenBook = onRead,
        onToggleSelection = onSelect,
        onClearSelection = onClearSelection,
        onRemoveSelected = onRemoveSelected,
        onShowBookInfo = onShowBookInfo,
        onEditBook = onEditBook,
        onCreateShelf = onCreateShelf,
        onCreateSmartShelf = onCreateSmartShelf,
        onRenameShelf = onRenameShelf,
        onDeleteShelf = onDeleteShelf,
        onRemoveFolder = onRemoveFolder,
        onTagSelectedBooks = onTagSelectedBooks,
        onAddSelectedBooksToShelf = onAddSelectedBooksToShelf,
        onImportFolder = onImportFolder,
        onSyncFolderMetadata = onSyncFolderMetadata,
        onScanFolders = onScanFolders,
        onTogglePinned = onTogglePinned,
        useImportEmptyStateWhenLibraryEmpty = true
    )
}

@Composable
internal fun ShelvesScreen(
    shelves: List<Shelf>,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    onRead: (BookItem) -> Unit,
    onSelect: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onCreateShelf: () -> Unit,
    onCreateSmartShelf: () -> Unit,
    onRenameShelf: (Shelf) -> Unit,
    onDeleteShelf: (Shelf) -> Unit,
    onRemoveFolder: (Shelf) -> Unit
) {
    SharedShelvesScreen(
        shelves = shelves,
        selectedBookIds = selectedBookIds,
        pinnedBookIds = pinnedBookIds,
        onOpenBook = onRead,
        onToggleSelection = onSelect,
        onShowBookInfo = onShowBookInfo,
        onEditBook = onEditBook,
        onTogglePinned = onTogglePinned,
        onCreateShelf = onCreateShelf,
        onCreateSmartShelf = onCreateSmartShelf,
        onRenameShelf = onRenameShelf,
        onDeleteShelf = onDeleteShelf,
        onRemoveFolder = onRemoveFolder
    )
}

private data class DesktopSmartRuleDraft(
    val field: SmartField = SmartField.TITLE,
    val operator: SmartOperator = SmartOperator.CONTAINS,
    val value: String = ""
) {
    fun toRule(): SmartRule? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        return SmartRule(field = field, operator = operator, value = trimmed)
    }
}

@Composable
internal fun SmartShelfDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, SmartCollectionDefinition) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var matchAll by remember { mutableStateOf(true) }
    var rules by remember { mutableStateOf(listOf(DesktopSmartRuleDraft())) }
    val validRules = rules.mapNotNull { it.toRule() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create smart shelf") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SharedStableOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Shelf name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = matchAll,
                        onClick = { matchAll = true },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = !matchAll,
                        onClick = { matchAll = false },
                        label = { Text("Any") }
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { rules = rules + DesktopSmartRuleDraft() },
                        enabled = rules.size < 4
                    ) {
                        Text("Add rule")
                    }
                }
                rules.forEachIndexed { index, draft ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SmartRuleDropdown(
                                label = "Field",
                                selected = draft.field,
                                options = SmartField.entries.toList(),
                                optionLabel = { it.desktopLabel() },
                                onSelected = { field ->
                                    rules = rules.updateAt(index) {
                                        val operator = smartOperatorsFor(field).first()
                                        copy(field = field, operator = operator, value = "")
                                    }
                                }
                            )
                            SmartRuleDropdown(
                                label = "Operator",
                                selected = draft.operator,
                                options = smartOperatorsFor(draft.field),
                                optionLabel = { it.desktopLabel() },
                                onSelected = { operator ->
                                    rules = rules.updateAt(index) { copy(operator = operator) }
                                }
                            )
                            if (rules.size > 1) {
                                TextButton(onClick = { rules = rules.filterIndexed { i, _ -> i != index } }) {
                                    Text("Remove")
                                }
                            }
                        }
                        SharedStableOutlinedTextField(
                            value = draft.value,
                            onValueChange = { value -> rules = rules.updateAt(index) { copy(value = value) } },
                            label = { Text(draft.field.valueLabel()) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            selectionKey = index
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, SmartCollectionDefinition(matchAll = matchAll, rules = validRules))
                },
                enabled = name.isNotBlank() && validRules.isNotEmpty()
            ) {
                Text("Create")
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
private fun <T> SmartRuleDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("$label: ${optionLabel(selected)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

private fun smartOperatorsFor(field: SmartField): List<SmartOperator> {
    return when (field) {
        SmartField.PROGRESS -> listOf(SmartOperator.GREATER_THAN, SmartOperator.LESS_THAN, SmartOperator.EQUALS)
        else -> listOf(SmartOperator.CONTAINS, SmartOperator.EQUALS)
    }
}

private fun SmartField.desktopLabel(): String {
    return when (this) {
        SmartField.TITLE -> "Title"
        SmartField.AUTHOR -> "Author"
        SmartField.PROGRESS -> "Progress"
        SmartField.FILE_TYPE -> "File type"
        SmartField.FOLDER -> "Folder"
        SmartField.TAG -> "Tag"
    }
}

private fun SmartField.valueLabel(): String {
    return when (this) {
        SmartField.PROGRESS -> "Percent"
        SmartField.FILE_TYPE -> "Type, e.g. PDF"
        SmartField.FOLDER -> "Folder path"
        SmartField.TAG -> "Tag name"
        SmartField.TITLE -> "Title text"
        SmartField.AUTHOR -> "Author text"
    }
}

private fun SmartOperator.desktopLabel(): String {
    return when (this) {
        SmartOperator.EQUALS -> "Equals"
        SmartOperator.CONTAINS -> "Contains"
        SmartOperator.GREATER_THAN -> "Greater than"
        SmartOperator.LESS_THAN -> "Less than"
    }
}

private inline fun List<DesktopSmartRuleDraft>.updateAt(
    index: Int,
    transform: DesktopSmartRuleDraft.() -> DesktopSmartRuleDraft
): List<DesktopSmartRuleDraft> {
    return mapIndexed { i, draft -> if (i == index) draft.transform() else draft }
}

internal fun SharedReaderScreenState.withBanner(message: String, isError: Boolean = false): SharedReaderScreenState {
    return reduce(AppAction.BannerShown(BannerMessage(message, isError = isError)))
}

internal object DesktopFolderPathResolver : SharedFolderPathResolver {
    override fun relativeFolderSegments(item: BookItem): List<String> {
        val sourceFolder = item.sourceFolder ?: return emptyList()
        val bookPath = item.path ?: return emptyList()
        val parentFile = File(bookPath).parentFile ?: return emptyList()
        val paths = runCatching {
            File(sourceFolder).toPath().toAbsolutePath().normalize() to
                parentFile.toPath().toAbsolutePath().normalize()
        }.getOrNull() ?: return emptyList()
        val (root, parent) = paths
        if (!parent.startsWith(root) || parent == root) return emptyList()
        return root.relativize(parent).map { it.toString() }.filter { it.isNotBlank() }
    }
}

internal fun List<BookItem>.collectTags(): List<Tag> {
    return flatMap { it.tags }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
}

internal fun BookItem.cardTitleForMessage(): String {
    return title?.takeIf { it.isNotBlank() } ?: displayName
}

internal fun Long.toReadableSize(): String {
    if (this <= 0L) return "Unknown"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = this.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "$this ${units[unitIndex]}"
    } else {
        "${String.format("%.1f", value)} ${units[unitIndex]}"
    }
}
