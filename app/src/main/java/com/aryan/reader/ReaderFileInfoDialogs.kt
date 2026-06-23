package com.aryan.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aryan.reader.data.RecentFileItem

@Composable
private fun rememberReaderFileInfoItem(
    uiState: ReaderScreenState,
    primaryBookId: String?,
    secondaryBookId: String? = null,
    uriString: String? = null
): RecentFileItem? {
    return remember(
        uiState.allRecentFiles,
        uiState.recentFiles,
        primaryBookId,
        secondaryBookId,
        uriString
    ) {
        findReaderFileInfoItem(
            uiState = uiState,
            primaryBookId = primaryBookId,
            secondaryBookId = secondaryBookId,
            uriString = uriString
        )
    }
}

private fun findReaderFileInfoItem(
    uiState: ReaderScreenState,
    primaryBookId: String?,
    secondaryBookId: String?,
    uriString: String?
): RecentFileItem? {
    val bookId = primaryBookId ?: secondaryBookId
    return uiState.allRecentFiles.firstOrNull { it.bookId == bookId }
        ?: uiState.recentFiles.firstOrNull { it.bookId == bookId }
        ?: uiState.allRecentFiles.firstOrNull { it.uriString == uriString }
        ?: uiState.recentFiles.firstOrNull { it.uriString == uriString }
}

@Composable
internal fun ReaderFileInfoDialogs(
    isFileInfoVisible: Boolean,
    onFileInfoVisibleChange: (Boolean) -> Unit,
    uiState: ReaderScreenState,
    primaryBookId: String?,
    secondaryBookId: String? = null,
    uriString: String? = null,
    viewModel: MainViewModel
) {
    val item = rememberReaderFileInfoItem(
        uiState = uiState,
        primaryBookId = primaryBookId,
        secondaryBookId = secondaryBookId,
        uriString = uriString
    )

    LaunchedEffect(item?.bookId) {
        if (item == null) {
            onFileInfoVisibleChange(false)
        }
    }

    HydratedFileInfoDialog(
        item = item,
        isVisible = isFileInfoVisible,
        uiState = uiState,
        viewModel = viewModel,
        onDismiss = { onFileInfoVisibleChange(false) },
        onOpenTags = { bookId ->
            onFileInfoVisibleChange(false)
            viewModel.openTagSelection(setOf(bookId))
        }
    )

    if (uiState.showTagSelectionDialogFor.isNotEmpty()) {
        TagSelectionBottomSheet(
            allTags = uiState.allTags,
            selectedBookIds = uiState.showTagSelectionDialogFor,
            booksWithTags = uiState.rawLibraryFiles,
            onCreateAndAssign = { name ->
                viewModel.createAndAssignTag(name, uiState.showTagSelectionDialogFor)
            },
            onToggleTag = { tagId, assign ->
                viewModel.toggleTagForBooks(tagId, uiState.showTagSelectionDialogFor, assign)
            },
            onDeleteTag = { tag -> viewModel.deleteTag(tag.id) },
            onDismiss = viewModel::closeTagSelection
        )
    }
}

@Composable
internal fun HydratedFileInfoDialog(
    item: RecentFileItem?,
    isVisible: Boolean,
    uiState: ReaderScreenState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onOpenTags: (String) -> Unit
) {
    var fileInfoItem by remember(item?.bookId) { mutableStateOf(item) }
    var hasResolvedFullItem by remember(item?.bookId) { mutableStateOf(false) }

    LaunchedEffect(item) {
        fileInfoItem = item
        hasResolvedFullItem = false
    }

    LaunchedEffect(isVisible, item?.bookId) {
        if (isVisible && item != null) {
            fileInfoItem = viewModel.getFileInfoItem(item.bookId)?.copy(tags = item.tags) ?: item
            hasResolvedFullItem = true
        }
    }

    val resolvedItem = fileInfoItem
    if (isVisible && resolvedItem != null && hasResolvedFullItem) {
        FileInfoDialog(
            item = resolvedItem,
            usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName,
            onDismiss = onDismiss,
            onSaveMetadata = { metadata ->
                viewModel.updateBookMetadata(resolvedItem.bookId, metadata)
            },
            onSaveDisplayName = { name ->
                viewModel.updateCustomName(resolvedItem.bookId, name)
            },
            onRestoreMetadata = {
                viewModel.restoreOriginalBookMetadata(resolvedItem.bookId)
            },
            onOpenTags = {
                onOpenTags(resolvedItem.bookId)
            }
        )
    }
}
