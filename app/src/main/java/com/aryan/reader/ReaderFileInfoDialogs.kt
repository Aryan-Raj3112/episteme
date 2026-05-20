package com.aryan.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

    item?.let { fileInfoItem ->
        if (isFileInfoVisible) {
            FileInfoDialog(
                item = fileInfoItem,
                usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName,
                onDismiss = { onFileInfoVisibleChange(false) },
                onSaveMetadata = { metadata ->
                    viewModel.updateBookMetadata(fileInfoItem.bookId, metadata)
                },
                onSaveDisplayName = { name ->
                    viewModel.updateCustomName(fileInfoItem.bookId, name)
                },
                onRestoreMetadata = {
                    viewModel.restoreOriginalBookMetadata(fileInfoItem.bookId)
                },
                onOpenTags = {
                    onFileInfoVisibleChange(false)
                    viewModel.openTagSelection(setOf(fileInfoItem.bookId))
                }
            )
        }
    }

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
            onDismiss = viewModel::closeTagSelection
        )
    }
}
