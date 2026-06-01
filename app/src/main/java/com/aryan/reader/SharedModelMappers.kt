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
import com.aryan.reader.shared.ReaderLocator as SharedReaderLocator
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf as SharedShelf
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.SyncedFolder as SharedSyncedFolder
import com.aryan.reader.shared.Tag as SharedTag
import com.aryan.reader.shared.toStablePositionCfi

fun FileType.toSharedFileType(): SharedFileType = this

fun SharedFileType.toAndroidFileType(): FileType = this

fun LibraryFilters.toSharedLibraryFilters(): SharedLibraryFilters = this

fun SharedLibraryFilters.toAndroidLibraryFilters(): LibraryFilters = this

fun SyncedFolder.toSharedSyncedFolder(): SharedSyncedFolder = this

fun SharedSyncedFolder.toAndroidSyncedFolder(): SyncedFolder = this

fun RecentFileItem.toSharedBookItem(): SharedBookItem {
    return toSharedBookItem(
        displayName = customName ?: displayName,
        includeReaderAnnotations = true
    )
}

private fun RecentFileItem.toSharedBookItem(
    displayName: String,
    includeReaderAnnotations: Boolean
): SharedBookItem {
    return SharedBookItem(
        id = bookId,
        path = uriString,
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
        progressPercentage = progressPercentage,
        isRecent = isRecent,
        fileSize = fileSize,
        fileContentModifiedTimestamp = fileContentModifiedTimestamp,
        sourceFolder = sourceFolderUri,
        folderTextMetadataParsed = folderTextMetadataParsed,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        lastPageIndex = lastPage,
        readerPosition = toSharedReaderLocatorOrNull(),
        tags = tags.map { it.toSharedTag() },
        readerHighlights = if (includeReaderAnnotations) {
            EpubAnnotationSerializer.parseHighlightsJson(highlightsJson)
        } else {
            emptyList()
        },
        readingPositionModifiedTimestamp = readingPositionModifiedTimestamp
    )
}

fun RecentFileItem.toSharedProjectionBookItem(): SharedBookItem {
    return toSharedBookItem(
        displayName = displayName,
        includeReaderAnnotations = false
    )
}

fun SharedBookItem.toRecentFileItem(
    androidBooksById: Map<String, RecentFileItem> = emptyMap(),
    tagEntitiesById: Map<String, TagEntity> = emptyMap()
): RecentFileItem {
    val resolvedTags = tags.map { tag -> tagEntitiesById[tag.id] ?: tag.toTagEntity(createdAt = 0L) }
    val positionCfi = readerPosition?.toSharedPositionCfi()
    androidBooksById[id]?.let { existing ->
        val mappedLastChapterIndex = readerPosition?.chapterIndex ?: existing.lastChapterIndex
        val mappedLastPositionCfi = positionCfi ?: existing.lastPositionCfi
        val mappedLocatorBlockIndex = readerPosition?.blockIndex ?: existing.locatorBlockIndex
        val mappedLocatorCharOffset = readerPosition?.charOffset ?: existing.locatorCharOffset

        if (
            existing.uriString == path &&
            existing.type == type &&
            existing.timestamp == timestamp &&
            existing.coverImagePath == coverImagePath &&
            existing.title == title &&
            existing.author == author &&
            existing.description == description &&
            existing.originalTitle == originalTitle &&
            existing.originalAuthor == originalAuthor &&
            existing.originalSeriesName == originalSeriesName &&
            existing.originalSeriesIndex == originalSeriesIndex &&
            existing.originalDescription == originalDescription &&
            existing.lastPage == lastPageIndex &&
            existing.progressPercentage == progressPercentage &&
            existing.isRecent == isRecent &&
            existing.sourceFolderUri == sourceFolder &&
            existing.fileSize == fileSize &&
            existing.fileContentModifiedTimestamp == fileContentModifiedTimestamp &&
            existing.seriesName == seriesName &&
            existing.seriesIndex == seriesIndex &&
            existing.folderTextMetadataParsed == folderTextMetadataParsed &&
            existing.lastChapterIndex == mappedLastChapterIndex &&
            existing.lastPositionCfi == mappedLastPositionCfi &&
            existing.locatorBlockIndex == mappedLocatorBlockIndex &&
            existing.locatorCharOffset == mappedLocatorCharOffset &&
            existing.readingPositionModifiedTimestamp == readingPositionModifiedTimestamp &&
            existing.tags == resolvedTags
        ) {
            return existing
        }

        return existing.copy(
            uriString = path,
            type = type,
            displayName = existing.displayName,
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
            lastChapterIndex = mappedLastChapterIndex,
            lastPositionCfi = mappedLastPositionCfi,
            locatorBlockIndex = mappedLocatorBlockIndex,
            locatorCharOffset = mappedLocatorCharOffset,
            readingPositionModifiedTimestamp = readingPositionModifiedTimestamp,
            tags = resolvedTags
        )
    }

    return RecentFileItem(
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
        lastChapterIndex = readerPosition?.chapterIndex,
        lastPositionCfi = positionCfi,
        locatorBlockIndex = readerPosition?.blockIndex,
        locatorCharOffset = readerPosition?.charOffset,
        readingPositionModifiedTimestamp = readingPositionModifiedTimestamp,
        tags = resolvedTags
    )
}

private fun RecentFileItem.toSharedReaderLocatorOrNull(): SharedReaderLocator? {
    if (
        lastChapterIndex == null &&
        lastPage == null &&
        lastPositionCfi.isNullOrBlank() &&
        locatorBlockIndex == null &&
        locatorCharOffset == null
    ) {
        return null
    }
    return SharedReaderLocator.fromLegacy(
        chapterIndex = lastChapterIndex,
        cfi = lastPositionCfi,
        pageIndex = lastPage
    ).withFallbacks(
        blockIndex = locatorBlockIndex,
        charOffset = locatorCharOffset
    )
}

private fun SharedReaderLocator.toSharedPositionCfi(): String? {
    return toStablePositionCfi()
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
    dbTags: List<TagEntity> = allTags,
    includeReaderAnnotations: Boolean = true
): SharedReaderScreenState {
    fun RecentFileItem.toStateSharedBookItem(): SharedBookItem {
        return toSharedBookItem(
            displayName = customName ?: displayName,
            includeReaderAnnotations = includeReaderAnnotations
        )
    }

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
        recentBooks = recentFiles.map { it.toStateSharedBookItem() },
        libraryBooks = allRecentFiles.map { it.toStateSharedBookItem() },
        rawLibraryBooks = rawBooks.map { it.toStateSharedBookItem() },
        pinnedHomeBookIds = pinnedHomeBookIds,
        pinnedLibraryBookIds = pinnedLibraryBookIds,
        libraryFilters = libraryFilters,
        recentFilesLimit = recentFilesLimit,
        isTabsEnabled = isTabsEnabled,
        openTabIds = openTabIds,
        openTabs = openTabs.map { it.toStateSharedBookItem() },
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
    return map { item ->
        val resolvedTags = bookTagsMap[item.bookId].orEmpty()
        if (item.tags == resolvedTags) {
            item
        } else {
            item.copy(tags = resolvedTags)
        }
    }
}

fun SharedReaderScreenState.toAndroidReaderScreenState(
    base: ReaderScreenState,
    androidBooksById: Map<String, RecentFileItem>,
    tagEntitiesById: Map<String, TagEntity> = emptyMap()
): ReaderScreenState {
    val fallbackBooksById = rawLibraryBooks.associateBy { it.id }
    val mappedBooksById = LinkedHashMap<String, RecentFileItem>()
    fun SharedBookItem.toAndroidBook(): RecentFileItem {
        return mappedBooksById.getOrPut(id) {
            toRecentFileItem(androidBooksById, tagEntitiesById)
        }
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
        shelves = shelves.map { shelf -> shelf.toAndroidShelf { book -> book.toAndroidBook() } },
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
    return toAndroidShelf { it.toRecentFileItem(androidBooksById, tagEntitiesById) }
}

private fun SharedShelf.toAndroidShelf(
    resolveBook: (SharedBookItem) -> RecentFileItem
): Shelf {
    return Shelf(
        id = id,
        name = name,
        type = type,
        books = books.map(resolveBook),
        directBooks = directBooks.map(resolveBook),
        parentShelfId = parentShelfId,
        childShelfIds = childShelfIds,
        depth = depth,
        sortKey = sortKey
    )
}
