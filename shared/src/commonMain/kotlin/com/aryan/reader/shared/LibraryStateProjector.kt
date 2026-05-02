package com.aryan.reader.shared

data class ShelfRecord(
    val id: String,
    val name: String,
    val isSmart: Boolean = false,
    val smartRulesJson: String? = null
)

data class BookShelfRef(
    val bookId: String,
    val shelfId: String,
    val addedAt: Long
)

fun interface SharedFolderPathResolver {
    fun relativeFolderSegments(item: BookItem): List<String>
}

object EmptySharedFolderPathResolver : SharedFolderPathResolver {
    override fun relativeFolderSegments(item: BookItem): List<String> = emptyList()
}

data class SharedLibraryProjectionInput(
    val state: SharedReaderScreenState,
    val booksFromStore: List<BookItem>,
    val shelfRecords: List<ShelfRecord>,
    val shelfRefs: List<BookShelfRef>,
    val tags: List<Tag>
)

class SharedLibraryStateProjector(
    private val folderPathResolver: SharedFolderPathResolver = EmptySharedFolderPathResolver
) {
    fun project(input: SharedLibraryProjectionInput): SharedReaderScreenState {
        val current = input.state
        val allLibraryBooks = input.booksFromStore
        val queried = filterBySearch(allLibraryBooks, current.searchQuery)
        val filtered = applyLibraryFilters(queried, current.libraryFilters)
        val sortedLibraryBooks = sortBooks(filtered, current.sortOrder)
        val visibleRecentBooks = sortBooks(
            allLibraryBooks.filter { it.isRecent },
            current.sortOrder
        ).take(if (current.recentFilesLimit > 0) current.recentFilesLimit else Int.MAX_VALUE)
        val openTabs = current.openTabIds.mapNotNull { tabId -> allLibraryBooks.find { it.id == tabId } }
        val shelfProjection = buildShelves(
            allLibraryBooks = allLibraryBooks,
            shelfRecords = input.shelfRecords,
            shelfRefs = input.shelfRefs,
            tags = input.tags,
            sortOrder = current.sortOrder,
            syncedFolders = current.syncedFolders
        )
        val validShelfIds = shelfProjection.shelves.mapTo(mutableSetOf()) { it.id }
        val viewingShelfId = current.viewingShelfId?.takeIf { it in validShelfIds }
        val selectedShelfIds = current.selectedShelfIds.filterTo(mutableSetOf()) { it in validShelfIds }
        val booksAvailableForAdding = if (current.isAddingBooksToShelf && viewingShelfId != null) {
            val currentShelfBookIds = shelfProjection.shelves
                .find { it.id == viewingShelfId }
                ?.books
                ?.map { it.id }
                ?.toSet()
                ?: emptySet()
            when (current.addBooksSource) {
                AddBooksSource.UNSHELVED -> shelfProjection.unshelvedBooks
                AddBooksSource.ALL_BOOKS -> allLibraryBooks.filter { it.id !in currentShelfBookIds }
            }
        } else {
            emptyList()
        }

        return current.copy(
            recentBooks = visibleRecentBooks,
            libraryBooks = sortedLibraryBooks,
            rawLibraryBooks = allLibraryBooks,
            viewingShelfId = viewingShelfId,
            isAddingBooksToShelf = current.isAddingBooksToShelf && viewingShelfId != null,
            selectedShelfIds = selectedShelfIds,
            selectedBookIds = current.selectedBookIds.filterTo(mutableSetOf()) { selectedId ->
                allLibraryBooks.any { it.id == selectedId }
            },
            shelves = shelfProjection.shelves,
            openTabs = openTabs,
            booksAvailableForAdding = booksAvailableForAdding,
            allTags = input.tags
        )
    }

    private fun buildShelves(
        allLibraryBooks: List<BookItem>,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        tags: List<Tag>,
        sortOrder: SortOrder,
        syncedFolders: List<SyncedFolder>
    ): ShelfProjection {
        val shelves = mutableListOf<Shelf>()
        val shelvedBookIds = mutableSetOf<String>()
        val booksById = allLibraryBooks.associateBy { it.id }

        shelfRecords.forEach { shelf ->
            val bookIds = shelfRefs
                .filter { it.shelfId == shelf.id }
                .sortedBy { it.addedAt }
                .map { it.bookId }
            val books = bookIds.mapNotNull { booksById[it] }
            shelves.add(Shelf(shelf.id, shelf.name, ShelfType.MANUAL, sortBooks(books, sortOrder)))
            shelvedBookIds.addAll(bookIds)
        }

        val tagShelves = tags.mapNotNull { tag ->
            val taggedBooks = allLibraryBooks.filter { book -> book.tags.any { it.id == tag.id } }
            if (taggedBooks.isEmpty()) {
                null
            } else {
                Shelf("tag_${tag.id}", tag.name, ShelfType.TAG, sortBooks(taggedBooks, sortOrder))
            }
        }
        shelves.addAll(tagShelves)

        val seriesShelves = allLibraryBooks
            .filter { !it.seriesName.isNullOrBlank() }
            .groupBy { it.seriesName.orEmpty() }
            .filter { it.value.size >= 2 }
            .map { (series, books) ->
                val sortedSeries = books.sortedBy { it.seriesIndex ?: 999.0 }
                shelvedBookIds.addAll(books.map { it.id })
                Shelf("series_$series", series, ShelfType.SERIES, sortedSeries)
            }
        shelves.addAll(seriesShelves)

        val folderShelves = buildFolderShelves(allLibraryBooks, syncedFolders, sortOrder)
        folderShelves.forEach { shelf -> shelvedBookIds.addAll(shelf.books.map { it.id }) }
        shelves.addAll(folderShelves)

        val unshelvedBooks = allLibraryBooks.filter { it.id !in shelvedBookIds }
        shelves.add(Shelf("unshelved", "Unshelved", ShelfType.MANUAL, sortBooks(unshelvedBooks, sortOrder)))

        shelves.sortWith(compareBy({ it.type.ordinal }, { it.sortKey }))
        return ShelfProjection(shelves = shelves, unshelvedBooks = unshelvedBooks)
    }

    private fun buildFolderShelves(
        allLibraryBooks: List<BookItem>,
        syncedFolders: List<SyncedFolder>,
        sortOrder: SortOrder
    ): List<Shelf> {
        val folderNamesByUri = syncedFolders.associate { it.uriString to it.name }
        return allLibraryBooks
            .filter { it.sourceFolder != null }
            .groupBy { it.sourceFolder.orEmpty() }
            .flatMap { (folderUri, books) ->
                val rootName = folderNamesByUri[folderUri] ?: folderUri.folderDisplayName()
                val rootShelfId = "folder_$folderUri"
                val rootAccumulator = FolderShelfAccumulator(
                    id = rootShelfId,
                    name = rootName,
                    depth = 0,
                    parentShelfId = null,
                    sortPath = ""
                )
                val nestedShelves = linkedMapOf<String, FolderShelfAccumulator>()
                books.forEach { book ->
                    rootAccumulator.books.add(book)
                    val segments = folderPathResolver.relativeFolderSegments(book)
                    if (segments.isEmpty()) rootAccumulator.directBooks.add(book)
                    var currentPath = ""
                    var parentShelfId = rootShelfId
                    segments.forEachIndexed { index, segment ->
                        currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
                        val shelfId = "folder_$folderUri::$currentPath"
                        val accumulator = nestedShelves.getOrPut(currentPath) {
                            val newShelf = FolderShelfAccumulator(
                                id = shelfId,
                                name = segment,
                                depth = index + 1,
                                parentShelfId = parentShelfId,
                                sortPath = currentPath.lowercase()
                            )
                            if (parentShelfId == rootShelfId) {
                                rootAccumulator.childShelfIds.add(shelfId)
                            } else {
                                nestedShelves.values.find { it.id == parentShelfId }?.childShelfIds?.add(shelfId)
                            }
                            newShelf
                        }
                        accumulator.books.add(book)
                        if (index == segments.lastIndex) accumulator.directBooks.add(book)
                        parentShelfId = shelfId
                    }
                }

                val rootShelf = Shelf(
                    id = rootShelfId,
                    name = rootName,
                    type = ShelfType.FOLDER,
                    books = sortBooks(books, sortOrder),
                    directBooks = sortBooks(rootAccumulator.directBooks, sortOrder),
                    childShelfIds = rootAccumulator.childShelfIds.sortedBy { it.substringAfterLast("::").lowercase() },
                    depth = 0,
                    sortKey = "folder:${rootName.lowercase()}:"
                )

                val childShelves = nestedShelves.values.sortedBy { it.sortPath }.map { shelf ->
                    Shelf(
                        id = shelf.id,
                        name = shelf.name,
                        type = ShelfType.FOLDER,
                        books = sortBooks(shelf.books, sortOrder),
                        directBooks = sortBooks(shelf.directBooks, sortOrder),
                        parentShelfId = shelf.parentShelfId,
                        childShelfIds = shelf.childShelfIds.sortedBy { it.substringAfterLast("::").lowercase() },
                        depth = shelf.depth,
                        sortKey = "folder:${rootName.lowercase()}:${shelf.sortPath}"
                    )
                }

                listOf(rootShelf) + childShelves
            }
    }

    private data class ShelfProjection(
        val shelves: List<Shelf>,
        val unshelvedBooks: List<BookItem>
    )

    private data class FolderShelfAccumulator(
        val id: String,
        val name: String,
        val depth: Int,
        val parentShelfId: String?,
        val sortPath: String,
        val books: MutableList<BookItem> = mutableListOf(),
        val directBooks: MutableList<BookItem> = mutableListOf(),
        val childShelfIds: MutableList<String> = mutableListOf()
    )
}

private fun String.folderDisplayName(): String {
    return replace('\\', '/').trimEnd('/').substringAfterLast('/').ifBlank { "Local Folder" }
}

fun filterBySearch(books: List<BookItem>, searchQuery: String): List<BookItem> {
    val query = searchQuery.trim()
    return if (query.isBlank()) {
        books
    } else {
        books.filter { book ->
            book.displayName.contains(query, ignoreCase = true) ||
                book.title?.contains(query, ignoreCase = true) == true ||
                book.author?.contains(query, ignoreCase = true) == true ||
                book.tags.any { tag -> tag.name.contains(query, ignoreCase = true) }
        }
    }
}

fun applyLibraryFilters(books: List<BookItem>, filters: LibraryFilters): List<BookItem> {
    return books.filter { book ->
        val matchType = filters.fileTypes.isEmpty() || book.type in filters.fileTypes
        val matchFolder = filters.sourceFolders.isEmpty() || book.sourceFolder in filters.sourceFolders
        val progress = book.progressPercentage ?: 0f
        val matchStatus = when (filters.readStatus) {
            ReadStatusFilter.ALL -> true
            ReadStatusFilter.UNREAD -> progress == 0f
            ReadStatusFilter.IN_PROGRESS -> progress > 0f && progress < 100f
            ReadStatusFilter.COMPLETED -> progress >= 100f
        }
        val matchTags = filters.tagIds.isEmpty() || book.tags.any { it.id in filters.tagIds }
        matchType && matchFolder && matchStatus && matchTags
    }
}

fun sortBooks(books: List<BookItem>, sortOrder: SortOrder): List<BookItem> {
    return when (sortOrder) {
        SortOrder.RECENT -> books.sortedByDescending { it.timestamp }
        SortOrder.TITLE_ASC -> books.sortedBy { it.title?.lowercase() ?: it.displayName.lowercase() }
        SortOrder.AUTHOR_ASC -> books.sortedBy { it.author?.lowercase() ?: "" }
        SortOrder.PERCENT_ASC -> books.sortedBy { it.progressPercentage ?: 0f }
        SortOrder.PERCENT_DESC -> books.sortedByDescending { it.progressPercentage ?: 0f }
        SortOrder.SIZE_ASC -> books.sortedBy { it.fileSize }
        SortOrder.SIZE_DESC -> books.sortedByDescending { it.fileSize }
    }
}

fun SharedReaderScreenState.withImportedFiles(
    files: List<ImportedBookFile>,
    now: Long = currentTimestamp()
): SharedReaderScreenState {
    if (files.isEmpty()) return this
    val existingIds = rawLibraryBooks.mapTo(mutableSetOf()) { it.id }
    val imported = files.mapIndexedNotNull { index, file ->
        val id = file.localPath ?: file.uriString ?: file.name
        if (!existingIds.add(id)) {
            null
        } else {
            BookItem(
                id = id,
                path = file.localPath ?: file.uriString,
                type = file.name.toFileType(),
                displayName = file.name,
                timestamp = now + index,
                title = file.name.substringBeforeLast('.'),
                fileSize = file.size,
                sourceFolder = file.localPath?.parentPath()
            )
        }
    }
    return copy(
        rawLibraryBooks = imported + rawLibraryBooks,
        bannerMessage = BannerMessage(
            if (imported.isEmpty()) {
                "Those files are already in the library."
            } else {
                "Imported ${imported.size} file(s)."
            }
        )
    )
}

private fun String.parentPath(): String? {
    val normalized = replace('\\', '/')
    val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
    return parent.ifBlank { null }
}
