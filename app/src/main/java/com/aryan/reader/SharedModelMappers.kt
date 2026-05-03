package com.aryan.reader

import com.aryan.reader.data.BookShelfCrossRef
import com.aryan.reader.data.BookTagCrossRef
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.ShelfEntity
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.AddBooksSource as SharedAddBooksSource
import com.aryan.reader.shared.AppContrastOption as SharedAppContrastOption
import com.aryan.reader.shared.AppThemeMode as SharedAppThemeMode
import com.aryan.reader.shared.BannerMessage as SharedBannerMessage
import com.aryan.reader.shared.BookItem as SharedBookItem
import com.aryan.reader.shared.BookShelfRef as SharedBookShelfRef
import com.aryan.reader.shared.CustomAppTheme as SharedCustomAppTheme
import com.aryan.reader.shared.FileType as SharedFileType
import com.aryan.reader.shared.LibraryFilters as SharedLibraryFilters
import com.aryan.reader.shared.ReadStatusFilter as SharedReadStatusFilter
import com.aryan.reader.shared.RenderMode as SharedRenderMode
import com.aryan.reader.shared.SharedLibraryProjectionInput
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.SortOrder as SharedSortOrder
import com.aryan.reader.shared.SyncedFolder as SharedSyncedFolder
import com.aryan.reader.shared.Tag as SharedTag

fun RecentFileItem.toSharedBookItem(): SharedBookItem {
    return SharedBookItem(
        id = bookId,
        path = uriString,
        type = type.toSharedFileType(),
        displayName = customName ?: displayName,
        timestamp = timestamp,
        title = title,
        author = author,
        progressPercentage = progressPercentage,
        isRecent = isRecent,
        fileSize = fileSize,
        sourceFolder = sourceFolderUri,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        tags = tags.map { it.toSharedTag() }
    )
}

fun TagEntity.toSharedTag(): SharedTag {
    return SharedTag(
        id = id,
        name = name,
        color = color
    )
}

fun ShelfEntity.toSharedShelfRecord(): ShelfRecord {
    return ShelfRecord(
        id = id,
        name = name,
        isSmart = isSmart,
        smartRulesJson = smartRulesJson
    )
}

fun BookShelfCrossRef.toSharedBookShelfRef(): SharedBookShelfRef {
    return SharedBookShelfRef(
        bookId = bookId,
        shelfId = shelfId,
        addedAt = addedAt
    )
}

fun ReaderScreenState.toSharedReaderScreenState(
    rawBooks: List<RecentFileItem> = rawLibraryFiles,
    dbTags: List<TagEntity> = allTags
): SharedReaderScreenState {
    return SharedReaderScreenState(
        selectedBookId = selectedBookId,
        selectedUriString = selectedPdfUri?.toString() ?: selectedEpubUri?.toString(),
        selectedFileType = selectedFileType?.toSharedFileType(),
        isLoading = isLoading,
        errorMessage = errorMessage,
        renderMode = renderMode.toSharedRenderMode(),
        sortOrder = sortOrder.toSharedSortOrder(),
        viewingShelfId = viewingShelfId,
        isAddingBooksToShelf = isAddingBooksToShelf,
        showCreateShelfDialog = showCreateShelfDialog,
        mainScreenStartPage = mainScreenStartPage,
        libraryScreenStartPage = libraryScreenStartPage,
        showRenameShelfDialogFor = showRenameShelfDialogFor,
        showDeleteShelfDialogFor = showDeleteShelfDialogFor,
        addBooksSource = addBooksSource.toSharedAddBooksSource(),
        booksSelectedForAdding = booksSelectedForAdding,
        selectedBookIds = contextualActionItems.mapTo(mutableSetOf()) { it.bookId },
        selectedShelfIds = contextualActionShelfIds,
        isProUser = isProUser,
        credits = credits,
        isSyncEnabled = isSyncEnabled,
        isFolderSyncEnabled = isFolderSyncEnabled,
        bannerMessage = bannerMessage?.toSharedBannerMessage(),
        downloadingBookIds = downloadingBookIds,
        uploadingBookIds = uploadingBookIds,
        syncedFolders = syncedFolders.map { it.toSharedSyncedFolder() },
        lastFolderScanTime = lastFolderScanTime,
        hasUnreadFeedback = hasUnreadFeedback,
        searchQuery = searchQuery,
        isSearchActive = isSearchActive,
        isRefreshing = isRefreshing,
        reflowProgress = reflowProgress,
        recentBooks = recentFiles.map { it.toSharedBookItem() },
        libraryBooks = allRecentFiles.map { it.toSharedBookItem() },
        rawLibraryBooks = rawBooks.map { it.toSharedBookItem() },
        pinnedHomeBookIds = pinnedHomeBookIds,
        pinnedLibraryBookIds = pinnedLibraryBookIds,
        libraryFilters = libraryFilters.toSharedLibraryFilters(),
        recentFilesLimit = recentFilesLimit,
        isTabsEnabled = isTabsEnabled,
        openTabIds = openTabIds,
        openTabs = openTabs.map { it.toSharedBookItem() },
        activeTabBookId = activeTabBookId,
        showExternalFileSavePromptFor = showExternalFileSavePromptFor,
        externalFileBehavior = externalFileBehavior,
        useStrictFileFilter = useStrictFileFilter,
        appThemeMode = appThemeMode.toSharedAppThemeMode(),
        appContrastOption = appContrastOption.toSharedAppContrastOption(),
        appTextDimFactorLight = appTextDimFactorLight,
        appTextDimFactorDark = appTextDimFactorDark,
        appSeedColor = appSeedColor,
        customAppThemes = customAppThemes.map { it.toSharedCustomAppTheme() },
        allTags = dbTags.map { it.toSharedTag() },
        showTagSelectionDialogFor = showTagSelectionDialogFor
    )
}

fun ReaderScreenState.toSharedLibraryProjectionInput(
    recentFilesFromDb: List<RecentFileItem>,
    dbShelves: List<ShelfEntity>,
    shelfRefs: List<BookShelfCrossRef>,
    dbTags: List<TagEntity>,
    tagRefs: List<BookTagCrossRef>
): SharedLibraryProjectionInput {
    val tagsById = dbTags.associateBy { it.id }
    val bookTagsMap = tagRefs.groupBy { it.bookId }.mapValues { entry ->
        entry.value.mapNotNull { tagsById[it.tagId] }
    }
    val taggedBooks = recentFilesFromDb.map { item ->
        item.copy(tags = bookTagsMap[item.bookId].orEmpty())
    }
    return SharedLibraryProjectionInput(
        state = toSharedReaderScreenState(
            rawBooks = taggedBooks,
            dbTags = dbTags
        ),
        booksFromStore = taggedBooks
            .filterNot { it.bookId.endsWith("_reflow") }
            .map { it.toSharedBookItem() },
        shelfRecords = dbShelves.map { it.toSharedShelfRecord() },
        shelfRefs = shelfRefs.map { it.toSharedBookShelfRef() },
        tags = dbTags.map { it.toSharedTag() }
    )
}

fun FileType.toSharedFileType(): SharedFileType {
    return runCatching { SharedFileType.valueOf(name) }.getOrDefault(SharedFileType.UNKNOWN)
}

private fun RenderMode.toSharedRenderMode(): SharedRenderMode {
    return SharedRenderMode.valueOf(name)
}

private fun AddBooksSource.toSharedAddBooksSource(): SharedAddBooksSource {
    return SharedAddBooksSource.valueOf(name)
}

private fun SortOrder.toSharedSortOrder(): SharedSortOrder {
    return SharedSortOrder.valueOf(name)
}

private fun ReadStatusFilter.toSharedReadStatusFilter(): SharedReadStatusFilter {
    return SharedReadStatusFilter.valueOf(name)
}

private fun LibraryFilters.toSharedLibraryFilters(): SharedLibraryFilters {
    return SharedLibraryFilters(
        fileTypes = fileTypes.mapTo(mutableSetOf()) { it.toSharedFileType() },
        sourceFolders = sourceFolders,
        readStatus = readStatus.toSharedReadStatusFilter(),
        tagIds = tagIds
    )
}

private fun SyncedFolder.toSharedSyncedFolder(): SharedSyncedFolder {
    return SharedSyncedFolder(
        uriString = uriString,
        name = name,
        lastScanTime = lastScanTime,
        allowedFileTypes = allowedFileTypes.mapTo(mutableSetOf()) { it.toSharedFileType() }
    )
}

private fun BannerMessage.toSharedBannerMessage(): SharedBannerMessage {
    return SharedBannerMessage(
        message = message,
        isError = isError,
        isPersistent = isPersistent
    )
}

private fun AppThemeMode.toSharedAppThemeMode(): SharedAppThemeMode {
    return SharedAppThemeMode.valueOf(name)
}

private fun AppContrastOption.toSharedAppContrastOption(): SharedAppContrastOption {
    return SharedAppContrastOption.valueOf(name)
}

private fun CustomAppTheme.toSharedCustomAppTheme(): SharedCustomAppTheme {
    return SharedCustomAppTheme(
        id = id,
        name = name,
        seedColor = seedColor
    )
}
