package com.aryan.reader

import com.aryan.reader.data.BookTagCrossRef
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.AddBooksSource
import com.aryan.reader.shared.AppPinState
import com.aryan.reader.shared.booksAvailableForShelfAddition
import com.aryan.reader.shared.BookItem as SharedBookItem
import com.aryan.reader.shared.LibraryFeatureState
import com.aryan.reader.shared.Shelf as SharedShelf
import java.util.LinkedHashMap

internal fun ReaderScreenState.toSharedLibraryFeatureState(
    rawBooks: List<RecentFileItem> = rawLibraryFiles,
    includeReaderAnnotations: Boolean = true,
): LibraryFeatureState {
    fun RecentFileItem.toFeatureBook() = toSharedBookItem(
        displayName = customName ?: displayName,
        includeReaderAnnotations = includeReaderAnnotations,
    )
    return LibraryFeatureState(
        sortOrder = sortOrder,
        searchQuery = searchQuery,
        filters = libraryFilters,
        syncedFolders = syncedFolders,
        pinnedHomeBookIds = pinnedHomeBookIds,
        pinnedLibraryBookIds = pinnedLibraryBookIds,
        recentLimit = recentFilesLimit,
        tabs = tabState,
        viewingShelfId = viewingShelfId,
        isAddingBooksToShelf = isAddingBooksToShelf,
        addBooksSource = addBooksSource,
        selectedBookIdsForAdding = booksSelectedForAdding,
        selectedBookIds = contextualActionItems.mapTo(mutableSetOf()) { it.bookId },
        selectedShelfIds = contextualActionShelfIds,
        recentBooks = recentFiles.map { it.toFeatureBook() },
        libraryBooks = allRecentFiles.map { it.toFeatureBook() },
        rawBooks = rawBooks.map { it.toFeatureBook() },
        openTabs = openTabs.map { it.toFeatureBook() },
    )
}

fun List<RecentFileItem>.withResolvedTags(
    dbTags: List<TagEntity>,
    tagRefs: List<BookTagCrossRef>,
): List<RecentFileItem> {
    val tagsById = dbTags.associateBy { it.id }
    val bookTagsMap = tagRefs.groupBy { it.bookId }.mapValues { entry ->
        entry.value.mapNotNull { tagsById[it.tagId] }
    }
    return map { item ->
        val resolvedTags = bookTagsMap[item.bookId].orEmpty()
        if (item.tags == resolvedTags) item else item.copy(tags = resolvedTags)
    }
}

internal fun LibraryFeatureState.applyToAndroidLibraryState(
    base: ReaderScreenState,
    androidBooksById: Map<String, RecentFileItem>,
    tagEntitiesById: Map<String, TagEntity> = emptyMap(),
): ReaderScreenState {
    val fallbackBooksById = rawBooks.associateBy { it.id }
    val mappedBooksById = LinkedHashMap<String, RecentFileItem>()
    fun SharedBookItem.toAndroidBook(): RecentFileItem = mappedBooksById.getOrPut(id) {
        toRecentFileItem(androidBooksById, tagEntitiesById)
    }
    fun bookById(bookId: String): RecentFileItem? =
        androidBooksById[bookId] ?: fallbackBooksById[bookId]?.toAndroidBook()

    return base.copy(
        recentFiles = recentBooks.map { it.toAndroidBook() },
        allRecentFiles = libraryBooks.map { it.toAndroidBook() },
        rawLibraryFiles = rawBooks.map { it.toAndroidBook() },
        libraryState = base.libraryState.copy(
            searchQuery = searchQuery,
            sortOrder = sortOrder,
            filters = filters,
            selectedBookIds = selectedBookIds,
            selectedShelfIds = selectedShelfIds,
            recentLimit = recentLimit,
        ),
        shelfState = base.shelfState.copy(
            viewingShelfId = viewingShelfId,
            isAddingBooks = isAddingBooksToShelf,
            addBooksSource = addBooksSource,
            selectedBookIdsForAdding = selectedBookIdsForAdding,
        ),
        contextualActionShelfIds = selectedShelfIds,
        contextualActionItems = selectedBookIds.mapNotNullTo(mutableSetOf(), ::bookById),
        shelves = shelves.map { shelf -> shelf.toAndroidShelf { it.toAndroidBook() } },
        openTabs = openTabs.map { it.toAndroidBook() },
        tabState = tabs,
        pinState = AppPinState(pinnedHomeBookIds, pinnedLibraryBookIds),
        booksAvailableForAdding = booksAvailableForAdding.map { it.toAndroidBook() },
        allTags = tags.map { tag -> tagEntitiesById[tag.id] ?: tag.toTagEntity(createdAt = 0L) },
        syncedFolders = syncedFolders,
    )
}

fun SharedShelf.toAndroidShelf(
    androidBooksById: Map<String, RecentFileItem> = emptyMap(),
    tagEntitiesById: Map<String, TagEntity> = emptyMap(),
): Shelf = toAndroidShelf { it.toRecentFileItem(androidBooksById, tagEntitiesById) }

private fun SharedShelf.toAndroidShelf(resolveBook: (SharedBookItem) -> RecentFileItem): Shelf = Shelf(
    id = id,
    name = name,
    type = type,
    books = books.map(resolveBook),
    directBooks = directBooks.map(resolveBook),
    parentShelfId = parentShelfId,
    childShelfIds = childShelfIds,
    depth = depth,
    sortKey = sortKey,
)

fun Shelf.toSharedShelf(mapBook: (RecentFileItem) -> SharedBookItem): SharedShelf = SharedShelf(
    id = id,
    name = name,
    type = type,
    books = books.map(mapBook),
    directBooks = directBooks.map(mapBook),
    parentShelfId = parentShelfId,
    childShelfIds = childShelfIds,
    depth = depth,
    sortKey = sortKey,
)

/**
 * Library Beta's "add books to this shelf" candidate list.
 *
 * `booksAvailableForAdding` on the projected state is only filled for the legacy
 * `isAddingBooksToShelf`/`viewingShelfId` flow, which Library Beta does not drive, so the
 * candidates are derived here with the same shared helper iOS uses. Mapping the result back
 * through the id of the source list keeps the original `RecentFileItem` instances, so no
 * extra allocation survives the round trip.
 */
internal fun androidBooksAvailableForShelfAddition(
    allLibraryBooks: List<RecentFileItem>,
    shelves: List<Shelf>,
    shelfId: String,
    source: AddBooksSource,
): List<RecentFileItem> {
    if (allLibraryBooks.isEmpty()) return emptyList()
    val bookItemCache = SharedProjectionBookItemCache(maxEntries = allLibraryBooks.size)
    val androidBooksById = allLibraryBooks.associateBy { it.bookId }
    val sharedShelves = shelves.map { shelf -> shelf.toSharedShelf(bookItemCache::map) }
    return booksAvailableForShelfAddition(
        allLibraryBooks = bookItemCache.map(allLibraryBooks),
        shelves = sharedShelves,
        shelfId = shelfId,
        source = source,
    ).mapNotNull { sharedBook -> androidBooksById[sharedBook.id] }
}
