package com.aryan.reader

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.AddBooksSource
import com.aryan.reader.shared.SortOrder

/**
 * Shelf CRUD dialogs, relocated from the retired Library screen.
 *
 * Only the localization layer lives here; the dialog bodies are shared so Library Beta
 * and any other host render identical copy and confirm-button enablement.
 */
@Composable
internal fun ShelfRenameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    com.aryan.reader.shared.ui.SharedRenameShelfDialog(
        initialName = initialName,
        title = stringResource(R.string.menu_rename_shelf),
        namePlaceholder = stringResource(R.string.shelf_name_hint),
        confirmLabel = stringResource(R.string.action_rename),
        cancelLabel = stringResource(R.string.action_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun ShelfDeleteDialog(
    shelfName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.aryan.reader.shared.ui.SharedShelfConfirmationDialog(
        title = stringResource(R.string.dialog_delete_shelf),
        body = stringResource(R.string.dialog_delete_shelf_desc, shelfName),
        confirmLabel = stringResource(R.string.action_delete),
        cancelLabel = stringResource(R.string.action_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun ShelfRemoveSelectedDialog(
    count: Int,
    shelfName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.aryan.reader.shared.ui.SharedShelfConfirmationDialog(
        title = stringResource(R.string.dialog_remove_from_shelf),
        body = pluralStringResource(R.plurals.dialog_remove_from_shelf_desc, count, count, shelfName),
        confirmLabel = stringResource(R.string.action_remove),
        cancelLabel = stringResource(R.string.action_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun unifiedShelfScreenStrings(): com.aryan.reader.shared.ui.SharedAndroidShelfScreenStrings {
    val context = androidx.compose.ui.platform.LocalContext.current
    return com.aryan.reader.shared.ui.SharedAndroidShelfScreenStrings(
        back = stringResource(R.string.action_back),
        closeSearch = stringResource(R.string.content_desc_close_search),
        clearQuery = stringResource(R.string.content_desc_clear_query),
        searchPlaceholder = stringResource(R.string.search_placeholder),
        sortDescription = stringResource(R.string.content_desc_sort),
        selectedDescription = stringResource(R.string.content_desc_selected),
        searchShelfDescription = stringResource(R.string.content_desc_search_shelf),
        moreOptionsDescription = stringResource(R.string.content_desc_more_options),
        renameShelf = stringResource(R.string.menu_rename_shelf),
        deleteShelf = stringResource(R.string.menu_delete_shelf),
        addBooks = stringResource(R.string.fab_add_books),
        emptyShelf = stringResource(R.string.shelf_empty),
        noResults = { context.getString(R.string.no_results_found, it) },
        foldersSection = stringResource(R.string.section_folders),
        filesSection = stringResource(R.string.section_files),
        addToShelfTitle = { context.getString(R.string.add_to_shelf, it) },
        addCount = { context.getString(R.string.fab_add_count, it) },
        noUnshelvedBooks = stringResource(R.string.no_unshelved_books),
        allBooksInShelf = stringResource(R.string.all_books_in_shelf),
        sortLabels = SortOrder.entries.associateWith { stringResource(it.labelRes) },
        sourceLabels = AddBooksSource.entries.associateWith { stringResource(it.labelRes) },
    )
}

/**
 * Multi-select "add books to this shelf" mode inside Library Beta's Shelves section.
 *
 * Presentation is the shared Android shelf screen; this only binds Library Beta's own
 * navigation so leaving the mode returns to the shelf that opened it.
 */
/** Confirmation for dismissing every open reader tab; relocated from the retired Home screen. */
@Composable
internal fun LibraryCloseAllTabsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_close_all_tabs)) },
        text = { Text(stringResource(R.string.dialog_close_all_tabs_desc)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Multi-select "add books to this shelf" mode inside Library Beta's Shelves section. */
@Composable
internal fun UnifiedAddBooksSection(
    modifier: Modifier,
    shelf: Shelf,
    availableBooks: List<RecentFileItem>,
    selectedBookIds: Set<String>,
    addBooksSource: AddBooksSource,
    sortOrder: SortOrder,
    downloadingBookIds: Set<String>,
    usePdfFileNameAsDisplayName: Boolean,
    onSortOrderChange: (SortOrder) -> Unit,
    onSourceChange: (AddBooksSource) -> Unit,
    onBookClick: (RecentFileItem) -> Unit,
    onAddSelectedBooks: () -> Unit,
    onBack: () -> Unit,
) {
    com.aryan.reader.shared.ui.SharedAndroidAddBooksModeScreen(
        modifier = modifier,
        shelfName = shelf.name,
        books = availableBooks,
        selectedCount = selectedBookIds.size,
        source = addBooksSource,
        sortOrder = sortOrder,
        strings = unifiedShelfScreenStrings(),
        bookKey = { it.bookId },
        onSortOrderChange = onSortOrderChange,
        onSourceChange = onSourceChange,
        onBack = onBack,
        onAddSelectedBooks = onAddSelectedBooks,
        bookRow = { item ->
            LibraryListItem(
                item = item,
                isSelected = item.bookId in selectedBookIds,
                onItemClick = { onBookClick(item) },
                onItemLongClick = { onBookClick(item) },
                isDownloading = item.bookId in downloadingBookIds,
                usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            )
        },
        sortIcon = {
            Icon(
                painterResource(R.drawable.sort),
                stringResource(R.string.content_desc_sort),
                Modifier.size(20.dp),
            )
        },
    )
}