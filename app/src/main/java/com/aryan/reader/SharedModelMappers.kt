package com.aryan.reader

import com.aryan.reader.data.BookShelfCrossRef
import com.aryan.reader.data.BookTagCrossRef
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.ShelfEntity
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.BookItem as SharedBookItem
import com.aryan.reader.shared.BookShelfRef as SharedBookShelfRef
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.FileType as SharedFileType
import com.aryan.reader.shared.LibraryFilters as SharedLibraryFilters
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf as SharedShelf
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.SyncedFolder as SharedSyncedFolder
import com.aryan.reader.shared.Tag as SharedTag

fun FileType.toSharedFileType(): SharedFileType = this

fun SharedFileType.toAndroidFileType(): FileType = this

fun LibraryFilters.toSharedLibraryFilters(): SharedLibraryFilters = this

fun SharedLibraryFilters.toAndroidLibraryFilters(): LibraryFilters = this

fun SyncedFolder.toSharedSyncedFolder(): SharedSyncedFolder = this

fun SharedSyncedFolder.toAndroidSyncedFolder(): SyncedFolder = this

fun RecentFileItem.toSharedBookItem(): SharedBookItem {
    return SharedBookItem(
        id = bookId,
        path = uriString,
        type = type,
        displayName = customName ?: displayName,
        timestamp = timestamp,
        coverImagePath = coverImagePath,
        title = title,
        author = author,
        description = description,
        originalTitle = originalTitle,
        originalAuthor = originalAuthor,
        originalSeriesName = originalSeriesName,
        originalSeriesIndex = originalSeriesIndex,
        originalDescription = originalDescription,
        progressPercentage = progressPercentage,
        isRecent = isRecent,
        fileSize = fileSize,
        fileContentModifiedTimestamp = fileContentModifiedTimestamp,
        sourceFolder = sourceFolderUri,
        folderTextMetadataParsed = folderTextMetadataParsed,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        lastPageIndex = lastPage,
        tags = tags.map { it.toSharedTag() },
        readerHighlights = EpubAnnotationSerializer.parseHighlightsJson(highlightsJson)
    )
}

fun RecentFileItem.toSharedProjectionBookItem(): SharedBookItem {
    return toSharedBookItem().copy(displayName = displayName)
}

fun SharedBookItem.toRecentFileItem(
    androidBooksById: Map<String, RecentFileItem> = emptyMap(),
    tagEntitiesById: Map<String, TagEntity> = emptyMap()
): RecentFileItem {
    val resolvedTags = tags.map { tag -> tagEntitiesById[tag.id] ?: tag.toTagEntity(createdAt = 0L) }
    return androidBooksById[id]?.copy(tags = resolvedTags)
        ?.copy(
            uriString = path,
            type = type,
            displayName = androidBooksById[id]?.displayName ?: displayName,
            timestamp = timestamp,
            coverImagePath = coverImagePath,
            title = title,
            author = author,
            description = description,
            originalTitle = originalTitle,
            originalAuthor = originalAuthor,
            originalSeriesName = originalSeriesName,
            originalSeriesIndex = originalSeriesIndex,
            originalDescription = originalDescription,
            lastPage = lastPageIndex,
            progressPercentage = progressPercentage,
            isRecent = isRecent,
            sourceFolderUri = sourceFolder,
            fileSize = fileSize,
            fileContentModifiedTimestamp = fileContentModifiedTimestamp,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            folderTextMetadataParsed = folderTextMetadataParsed
        )
        ?: RecentFileItem(
            bookId = id,
            uriString = path,
            type = type,
            displayName = displayName,
            timestamp = timestamp,
            coverImagePath = coverImagePath,
            title = title,
            author = author,
            description = description,
            originalTitle = originalTitle,
            originalAuthor = originalAuthor,
            originalSeriesName = originalSeriesName,
            originalSeriesIndex = originalSeriesIndex,
            originalDescription = originalDescription,
            lastPage = lastPageIndex,
            progressPercentage = progressPercentage,
            isRecent = isRecent,
            sourceFolderUri = sourceFolder,
            fileSize = fileSize,
            fileContentModifiedTimestamp = fileContentModifiedTimestamp,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            folderTextMetadataParsed = folderTextMetadataParsed,
            tags = resolvedTags
        )
}

fun TagEntity.toSharedTag(): SharedTag {
    return SharedTag(
        id = id,
        name = name,
        color = color
    )
}

fun SharedTag.toTagEntity(createdAt: Long): TagEntity {
    return TagEntity(
        id = id,
        name = name,
        color = color,
        createdAt = createdAt
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

fun ShelfRecord.toShelfEntity(createdAt: Long, updatedAt: Long = createdAt): ShelfEntity {
    return ShelfEntity(
        id = id,
        name = name,
        isSmart = isSmart,
        smartRulesJson = smartRulesJson,
        createdAt = createdAt,
        updatedAt = updatedAt
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
        selectedFileType = selectedFileType,
        isLoading = isLoading,
        errorMessage = errorMessage,
        renderMode = renderMode,
        sortOrder = sortOrder,
        viewingShelfId = viewingShelfId,
        isAddingBooksToShelf = isAddingBooksToShelf,
        showCreateShelfDialog = showCreateShelfDialog,
        mainScreenStartPage = mainScreenStartPage,
        libraryScreenStartPage = libraryScreenStartPage,
        showRenameShelfDialogFor = showRenameShelfDialogFor,
        showDeleteShelfDialogFor = showDeleteShelfDialogFor,
        addBooksSource = addBooksSource,
        booksSelectedForAdding = booksSelectedForAdding,
        selectedBookIds = contextualActionItems.mapTo(mutableSetOf()) { it.bookId },
        selectedShelfIds = contextualActionShelfIds,
        isProUser = isProUser,
        credits = credits,
        isSyncEnabled = isSyncEnabled,
        isFolderSyncEnabled = isFolderSyncEnabled,
        bannerMessage = bannerMessage,
        downloadingBookIds = downloadingBookIds,
        uploadingBookIds = uploadingBookIds,
        syncedFolders = syncedFolders,
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
        libraryFilters = libraryFilters,
        recentFilesLimit = recentFilesLimit,
        isTabsEnabled = isTabsEnabled,
        openTabIds = openTabIds,
        openTabs = openTabs.map { it.toSharedBookItem() },
        activeTabBookId = activeTabBookId,
        showExternalFileSavePromptFor = showExternalFileSavePromptFor,
        externalFileBehavior = externalFileBehavior,
        useStrictFileFilter = useStrictFileFilter,
        usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
        appThemeMode = appThemeMode,
        appContrastOption = appContrastOption,
        appTextDimFactorLight = appTextDimFactorLight,
        appTextDimFactorDark = appTextDimFactorDark,
        appSeedColor = appSeedColor,
        appFontPreference = appFontPreference,
        customAppThemes = customAppThemes,
        allTags = dbTags.map { it.toSharedTag() },
        showTagSelectionDialogFor = showTagSelectionDialogFor
    )
}

fun List<RecentFileItem>.withResolvedTags(
    dbTags: List<TagEntity>,
    tagRefs: List<BookTagCrossRef>
): List<RecentFileItem> {
    val tagsById = dbTags.associateBy { it.id }
    val bookTagsMap = tagRefs.groupBy { it.bookId }.mapValues { entry ->
        entry.value.mapNotNull { tagsById[it.tagId] }
    }
    return map { item -> item.copy(tags = bookTagsMap[item.bookId].orEmpty()) }
}

fun SharedReaderScreenState.toAndroidReaderScreenState(
    base: ReaderScreenState,
    androidBooksById: Map<String, RecentFileItem>,
    tagEntitiesById: Map<String, TagEntity> = emptyMap()
): ReaderScreenState {
    val fallbackBooksById = rawLibraryBooks.associateBy { it.id }
    fun SharedBookItem.toAndroidBook(): RecentFileItem {
        return toRecentFileItem(androidBooksById, tagEntitiesById)
    }
    fun bookById(bookId: String): RecentFileItem? {
        return androidBooksById[bookId] ?: fallbackBooksById[bookId]?.toAndroidBook()
    }
    return base.copy(
        recentFiles = recentBooks.map { it.toAndroidBook() },
        allRecentFiles = libraryBooks.map { it.toAndroidBook() },
        rawLibraryFiles = rawLibraryBooks.map { it.toAndroidBook() },
        viewingShelfId = viewingShelfId,
        isAddingBooksToShelf = isAddingBooksToShelf,
        contextualActionShelfIds = selectedShelfIds,
        contextualActionItems = selectedBookIds.mapNotNullTo(mutableSetOf()) { bookById(it) },
        shelves = shelves.map { it.toAndroidShelf(androidBooksById, tagEntitiesById) },
        openTabs = openTabs.map { it.toAndroidBook() },
        openTabIds = openTabIds,
        activeTabBookId = activeTabBookId,
        booksAvailableForAdding = booksAvailableForAdding.map { it.toAndroidBook() },
        allTags = allTags.map { tag -> tagEntitiesById[tag.id] ?: tag.toTagEntity(createdAt = 0L) }
    )
}

fun SharedShelf.toAndroidShelf(
    androidBooksById: Map<String, RecentFileItem> = emptyMap(),
    tagEntitiesById: Map<String, TagEntity> = emptyMap()
): Shelf {
    return Shelf(
        id = id,
        name = name,
        type = type,
        books = books.map { it.toRecentFileItem(androidBooksById, tagEntitiesById) },
        directBooks = directBooks.map { it.toRecentFileItem(androidBooksById, tagEntitiesById) },
        parentShelfId = parentShelfId,
        childShelfIds = childShelfIds,
        depth = depth,
        sortKey = sortKey
    )
}
