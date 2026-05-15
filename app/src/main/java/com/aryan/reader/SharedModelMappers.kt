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
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.FileType as SharedFileType
import com.aryan.reader.shared.LibraryFilters as SharedLibraryFilters
import com.aryan.reader.shared.ReadStatusFilter as SharedReadStatusFilter
import com.aryan.reader.shared.RenderMode as SharedRenderMode
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf as SharedShelf
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.ShelfType as SharedShelfType
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
            type = type.toAndroidFileType(),
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
            type = type.toAndroidFileType(),
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
        type = type.toAndroidShelfType(),
        books = books.map { it.toRecentFileItem(androidBooksById, tagEntitiesById) },
        directBooks = directBooks.map { it.toRecentFileItem(androidBooksById, tagEntitiesById) },
        parentShelfId = parentShelfId,
        childShelfIds = childShelfIds,
        depth = depth,
        sortKey = sortKey
    )
}

fun FileType.toSharedFileType(): SharedFileType {
    return this
}

fun SharedFileType.toAndroidFileType(): FileType {
    return this
}

fun RenderMode.toSharedRenderMode(): SharedRenderMode {
    return this
}

fun SharedRenderMode.toAndroidRenderMode(): RenderMode {
    return this
}

fun AddBooksSource.toSharedAddBooksSource(): SharedAddBooksSource {
    return this
}

fun SharedAddBooksSource.toAndroidAddBooksSource(): AddBooksSource {
    return this
}

fun SortOrder.toSharedSortOrder(): SharedSortOrder {
    return this
}

fun SharedSortOrder.toAndroidSortOrder(): SortOrder {
    return this
}

fun ReadStatusFilter.toSharedReadStatusFilter(): SharedReadStatusFilter {
    return this
}

fun SharedReadStatusFilter.toAndroidReadStatusFilter(): ReadStatusFilter {
    return this
}

fun LibraryFilters.toSharedLibraryFilters(): SharedLibraryFilters {
    return this
}

fun SharedLibraryFilters.toAndroidLibraryFilters(): LibraryFilters {
    return this
}

fun SyncedFolder.toSharedSyncedFolder(): SharedSyncedFolder {
    return this
}

fun SharedSyncedFolder.toAndroidSyncedFolder(): SyncedFolder {
    return this
}

private fun SharedShelfType.toAndroidShelfType(): ShelfType {
    return ShelfType.valueOf(name)
}

fun BannerMessage.toSharedBannerMessage(): SharedBannerMessage {
    return this
}

fun AppThemeMode.toSharedAppThemeMode(): SharedAppThemeMode {
    return this
}

fun SharedAppThemeMode.toAndroidAppThemeMode(): AppThemeMode {
    return this
}

fun AppContrastOption.toSharedAppContrastOption(): SharedAppContrastOption {
    return this
}

fun SharedAppContrastOption.toAndroidAppContrastOption(): AppContrastOption {
    return this
}

fun CustomAppTheme.toSharedCustomAppTheme(): SharedCustomAppTheme {
    return this
}

fun SharedCustomAppTheme.toAndroidCustomAppTheme(): CustomAppTheme {
    return this
}
