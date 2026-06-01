package com.aryan.reader.desktop

import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.AppAction
import com.aryan.reader.shared.BannerMessage
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.ReaderPlatform
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
import com.aryan.reader.shared.ui.SharedLibraryScreen
import com.aryan.reader.shared.ui.SharedStableOutlinedTextField
import com.aryan.reader.shared.ui.readerString
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
    readerSettings: ReaderSettings? = null
) {
    LaunchedEffect(opening.requestId) {
        logDesktopReaderOpenTrace {
            opening.openTracePrefix("desktop_opening_screen_composed")
        }
    }
    val background = readerSettings?.desktopOpeningBackgroundColor() ?: MaterialTheme.colorScheme.background
    val foreground = readerSettings?.desktopOpeningForegroundColor() ?: MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = foreground)
            Text(
                text = readerString("desktop_opening_title", "Opening %1\$s", opening.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = foreground,
                textAlign = TextAlign.Center
            )
            Text(
                text = opening.formatLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = foreground.copy(alpha = 0.72f),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun ReaderSettings.desktopOpeningBackgroundColor(): Color {
    return backgroundColorArgb?.toDesktopOpeningComposeColor()
        ?: if (darkMode) Color(0xFF171A17) else Color(0xFFFFFCF5)
}

private fun ReaderSettings.desktopOpeningForegroundColor(): Color {
    return textColorArgb?.toDesktopOpeningComposeColor()
        ?: if (darkMode) Color(0xFFE7E3D8) else Color(0xFF24231F)
}

private fun Long.toDesktopOpeningComposeColor(): Color {
    val value = this and 0xFFFFFFFFL
    val alpha = ((value shr 24) and 0xFF) / 255f
    val red = ((value shr 16) and 0xFF) / 255f
    val green = ((value shr 8) and 0xFF) / 255f
    val blue = (value and 0xFF) / 255f
    return Color(red = red, green = green, blue = blue, alpha = alpha.takeIf { it > 0f } ?: 1f)
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
    onCreateShelfWithBooks: (String, Set<String>) -> Unit,
    onCreateSmartShelf: () -> Unit,
    onRenameShelf: (Shelf) -> Unit,
    onDeleteShelf: (Shelf) -> Unit,
    onRemoveFolder: (Shelf) -> Unit,
    onTagSelectedBooks: () -> Unit,
    onAddSelectedBooksToShelf: () -> Unit,
    onAddBooksToShelf: (Set<String>) -> Unit,
    onManageShelfBooks: (Shelf) -> Unit,
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
        onCreateShelfWithBooks = onCreateShelfWithBooks,
        onCreateSmartShelf = onCreateSmartShelf,
        onRenameShelf = onRenameShelf,
        onDeleteShelf = onDeleteShelf,
        onRemoveFolder = onRemoveFolder,
        onTagSelectedBooks = onTagSelectedBooks,
        onAddSelectedBooksToShelf = onAddSelectedBooksToShelf,
        onAddBooksToShelf = onAddBooksToShelf,
        onManageShelfBooks = onManageShelfBooks,
        onImportFolder = onImportFolder,
        onSyncFolderMetadata = onSyncFolderMetadata,
        onScanFolders = onScanFolders,
        onTogglePinned = onTogglePinned,
        platform = ReaderPlatform.DESKTOP,
        useImportEmptyStateWhenLibraryEmpty = true
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
        title = { Text(readerString("desktop_create_smart_shelf", "Create smart shelf")) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SharedStableOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(readerString("shelf_name_hint", "Shelf name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = matchAll,
                        onClick = { matchAll = true },
                        label = { Text(readerString("filter_all", "All")) }
                    )
                    FilterChip(
                        selected = !matchAll,
                        onClick = { matchAll = false },
                        label = { Text(readerString("desktop_match_any", "Any")) }
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { rules = rules + DesktopSmartRuleDraft() },
                        enabled = rules.size < 4
                    ) {
                        Text(readerString("tts_replacements_add_rule", "Add rule"))
                    }
                }
                rules.forEachIndexed { index, draft ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SmartRuleDropdown(
                                label = readerString("desktop_field", "Field"),
                                selected = draft.field,
                                options = SmartField.entries.toList(),
                                optionLabel = { it.localizedLabel() },
                                onSelected = { field ->
                                    rules = rules.updateAt(index) {
                                        val operator = smartOperatorsFor(field).first()
                                        copy(field = field, operator = operator, value = "")
                                    }
                                }
                            )
                            SmartRuleDropdown(
                                label = readerString("desktop_operator", "Operator"),
                                selected = draft.operator,
                                options = smartOperatorsFor(draft.field),
                                optionLabel = { it.localizedLabel() },
                                onSelected = { operator ->
                                    rules = rules.updateAt(index) { copy(operator = operator) }
                                }
                            )
                            if (rules.size > 1) {
                                TextButton(onClick = { rules = rules.filterIndexed { i, _ -> i != index } }) {
                                    Text(readerString("action_remove", "Remove"))
                                }
                            }
                        }
                        SharedStableOutlinedTextField(
                            value = draft.value,
                            onValueChange = { value -> rules = rules.updateAt(index) { copy(value = value) } },
                            label = { Text(draft.field.localizedValueLabel()) },
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
                Text(readerString("action_create", "Create"))
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
private fun <T> SmartRuleDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            val selectedLabel = optionLabel(selected)
            Text(readerString("filter_facet", "%1\$s: %2\$s", label, selectedLabel))
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

@Composable
private fun SmartField.localizedLabel(): String {
    return when (this) {
        SmartField.TITLE -> readerString("label_title", "Title")
        SmartField.AUTHOR -> readerString("author", "Author")
        SmartField.PROGRESS -> readerString("desktop_progress", "Progress")
        SmartField.FILE_TYPE -> readerString("filter_file_type", "File type")
        SmartField.FOLDER -> readerString("desktop_smart_field_folder", "Folder")
        SmartField.TAG -> readerString("content_desc_tag", "Tag")
    }
}

@Composable
private fun SmartField.localizedValueLabel(): String {
    return when (this) {
        SmartField.PROGRESS -> readerString("desktop_percent", "Percent")
        SmartField.FILE_TYPE -> readerString("desktop_type_example_pdf", "Type, e.g. PDF")
        SmartField.FOLDER -> readerString("desktop_folder_path", "Folder path")
        SmartField.TAG -> readerString("desktop_tag_name", "Tag name")
        SmartField.TITLE -> readerString("desktop_title_text", "Title text")
        SmartField.AUTHOR -> readerString("desktop_author_text", "Author text")
    }
}

@Composable
private fun SmartOperator.localizedLabel(): String {
    return when (this) {
        SmartOperator.EQUALS -> readerString("desktop_equals", "Equals")
        SmartOperator.CONTAINS -> readerString("desktop_contains", "Contains")
        SmartOperator.GREATER_THAN -> readerString("desktop_greater_than", "Greater than")
        SmartOperator.LESS_THAN -> readerString("desktop_less_than", "Less than")
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
