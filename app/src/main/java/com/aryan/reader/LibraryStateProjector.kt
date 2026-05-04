package com.aryan.reader

import com.aryan.reader.data.BookShelfCrossRef
import com.aryan.reader.data.BookTagCrossRef
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.ShelfEntity
import com.aryan.reader.data.SmartCollectionEngine
import com.aryan.reader.data.TagEntity

fun interface FolderPathResolver {
    fun relativeFolderSegments(item: RecentFileItem): List<String>
}

object EmptyFolderPathResolver : FolderPathResolver {
    override fun relativeFolderSegments(item: RecentFileItem): List<String> = emptyList()
}

data class LibraryProjectionInput(
    val state: ReaderScreenState,
    val recentFilesFromDb: List<RecentFileItem>,
    val dbShelves: List<ShelfEntity>,
    val shelfRefs: List<BookShelfCrossRef>,
    val dbTags: List<TagEntity>,
    val tagRefs: List<BookTagCrossRef>
)

class LibraryStateProjector(
    private val folderPathResolver: FolderPathResolver = EmptyFolderPathResolver
) {
    private var cachedProjection: CachedProjection? = null

    fun project(input: LibraryProjectionInput): ReaderScreenState {
        val start = ReaderPerfLog.nowNanos()
        val internalState = input.state
        val cacheKey = ProjectionCacheKey(
            recentFilesFromDb = input.recentFilesFromDb,
            dbShelves = input.dbShelves,
            shelfRefs = input.shelfRefs,
            dbTags = input.dbTags,
            tagRefs = input.tagRefs,
            folderKeys = internalState.syncedFolders.map { SyncedFolderProjectionKey(it.uriString, it.name) },
            sortOrder = internalState.sortOrder,
            searchQuery = internalState.searchQuery,
            libraryFilters = internalState.libraryFilters,
            recentFilesLimit = internalState.recentFilesLimit
        )

        cachedProjection?.takeIf { it.key == cacheKey }?.let { cache ->
            val result = buildStateFromCache(internalState, cache)
            val elapsed = ReaderPerfLog.elapsedMs(start)
            if (elapsed >= 8L) {
                ReaderPerfLog.d(
                    "LibraryProject cache-hit took ${elapsed}ms books=${cache.allLibraryFiles.size} shelves=${cache.shelfProjection.shelves.size}"
                )
            }
            return result
        }

        val tagsById = input.dbTags.associateBy { it.id }
        val bookTagsMap = input.tagRefs.groupBy { it.bookId }.mapValues { entry ->
            entry.value.mapNotNull { tagsById[it.tagId] }
        }

        val allLibraryFiles = input.recentFilesFromDb
            .filterNot { it.bookId.endsWith("_reflow") }
            .map { item ->
                item.copy(tags = bookTagsMap[item.bookId] ?: emptyList())
            }
        val allLibraryFilesById = allLibraryFiles.associateBy { it.bookId }

        val rawFilteredByQuery = filterBySearch(allLibraryFiles, internalState.searchQuery)
        val libraryFiltered = applyLibraryFilters(rawFilteredByQuery, internalState.libraryFilters)
        val sortedLibraryFiles = if (internalState.sortOrder == SortOrder.RECENT) {
            libraryFiltered
        } else {
            sortFiles(libraryFiltered, internalState.sortOrder)
        }
        val recentLimit = if (internalState.recentFilesLimit > 0) internalState.recentFilesLimit else Int.MAX_VALUE
        val visibleRecentFiles = if (internalState.sortOrder == SortOrder.RECENT) {
            allLibraryFiles
                .asSequence()
                .filter { it.isRecent }
                .take(recentLimit)
                .toList()
        } else {
            sortFiles(
                allLibraryFiles.filter { it.isRecent },
                internalState.sortOrder
            ).take(recentLimit)
        }

        val shelfProjection = buildShelves(
            allLibraryFiles = allLibraryFiles,
            dbShelves = input.dbShelves,
            shelfRefs = input.shelfRefs,
            dbTags = input.dbTags,
            sortOrder = internalState.sortOrder,
            syncedFolders = internalState.syncedFolders
        )

        val cache = CachedProjection(
            key = cacheKey,
            allLibraryFiles = allLibraryFiles,
            allLibraryFilesById = allLibraryFilesById,
            sortedLibraryFiles = sortedLibraryFiles,
            visibleRecentFiles = visibleRecentFiles,
            shelfProjection = shelfProjection,
            validShelfIds = shelfProjection.shelves.mapTo(mutableSetOf()) { it.id },
            dbTags = input.dbTags
        )
        cachedProjection = cache

        val elapsed = ReaderPerfLog.elapsedMs(start)
        if (elapsed >= 16L || allLibraryFiles.size >= 500) {
            ReaderPerfLog.d(
                "LibraryProject recompute took ${elapsed}ms books=${allLibraryFiles.size} " +
                    "visible=${sortedLibraryFiles.size} shelves=${shelfProjection.shelves.size} " +
                    "tags=${input.dbTags.size} shelfRefs=${input.shelfRefs.size} tagRefs=${input.tagRefs.size}"
            )
        }

        return buildStateFromCache(internalState, cache)
    }

    private fun buildStateFromCache(
        internalState: ReaderScreenState,
        cache: CachedProjection
    ): ReaderScreenState {
        val viewingShelfId = internalState.viewingShelfId?.takeIf { it in cache.validShelfIds }
        val selectedShelfIds = internalState.contextualActionShelfIds.filterTo(mutableSetOf()) { it in cache.validShelfIds }
        val booksAvailableForAdding = if (internalState.isAddingBooksToShelf && viewingShelfId != null) {
            val currentShelfBookIds = cache.shelfProjection.shelves
                .find { it.id == viewingShelfId }
                ?.books
                ?.mapTo(mutableSetOf()) { it.bookId }
                ?: emptySet()
            when (internalState.addBooksSource) {
                AddBooksSource.UNSHELVED -> cache.shelfProjection.unshelvedBooks
                AddBooksSource.ALL_BOOKS -> cache.allLibraryFiles.filter { it.bookId !in currentShelfBookIds }
            }
        } else {
            emptyList()
        }

        return internalState.copy(
            recentFiles = cache.visibleRecentFiles,
            allRecentFiles = cache.sortedLibraryFiles,
            rawLibraryFiles = cache.allLibraryFiles,
            viewingShelfId = viewingShelfId,
            isAddingBooksToShelf = internalState.isAddingBooksToShelf && viewingShelfId != null,
            contextualActionShelfIds = selectedShelfIds,
            contextualActionItems = internalState.contextualActionItems
                .mapNotNull { ctx -> cache.allLibraryFilesById[ctx.bookId] }
                .toSet(),
            shelves = cache.shelfProjection.shelves,
            openTabs = internalState.openTabIds.mapNotNull { tabId -> cache.allLibraryFilesById[tabId] },
            booksAvailableForAdding = booksAvailableForAdding,
            allTags = cache.dbTags
        )
    }

    private fun buildShelves(
        allLibraryFiles: List<RecentFileItem>,
        dbShelves: List<ShelfEntity>,
        shelfRefs: List<BookShelfCrossRef>,
        dbTags: List<TagEntity>,
        sortOrder: SortOrder,
        syncedFolders: List<SyncedFolder>
    ): ShelfProjection {
        val allShelves = mutableListOf<Shelf>()
        val shelvedBookIds = mutableSetOf<String>()
        val baseFilesMap = allLibraryFiles.associateBy { it.bookId }
        val shelfRefsByShelfId = shelfRefs.groupBy { it.shelfId }
        val taggedBookIdsByTagId = mutableMapOf<String, MutableList<String>>()

        allLibraryFiles.forEach { item ->
            item.tags.forEach { tag ->
                taggedBookIdsByTagId.getOrPut(tag.id) { mutableListOf() }.add(item.bookId)
            }
        }

        dbShelves.forEach { shelfEntity ->
            if (shelfEntity.isSmart && shelfEntity.smartRulesJson != null) {
                val rules = SmartCollectionEngine.fromJson(shelfEntity.smartRulesJson)
                if (rules != null) {
                    val matchingBooks = allLibraryFiles.filter { SmartCollectionEngine.evaluate(it, rules) }
                    allShelves.add(Shelf(shelfEntity.id, shelfEntity.name, ShelfType.SMART, sortFiles(matchingBooks, sortOrder)))
                    shelvedBookIds.addAll(matchingBooks.map { it.bookId })
                }
            } else {
                val bookIdsInShelf = shelfRefsByShelfId[shelfEntity.id].orEmpty()
                    .sortedBy { it.addedAt }
                    .map { it.bookId }
                val booksInShelf = bookIdsInShelf.mapNotNull { baseFilesMap[it] }
                allShelves.add(Shelf(shelfEntity.id, shelfEntity.name, ShelfType.MANUAL, sortFiles(booksInShelf, sortOrder)))
                shelvedBookIds.addAll(bookIdsInShelf)
            }
        }

        val tagShelves = dbTags.mapNotNull { tag ->
            val taggedBooks = taggedBookIdsByTagId[tag.id].orEmpty().mapNotNull { baseFilesMap[it] }
            if (taggedBooks.isEmpty()) {
                null
            } else {
                Shelf("tag_${tag.id}", tag.name, ShelfType.TAG, sortFiles(taggedBooks, sortOrder))
            }
        }
        allShelves.addAll(tagShelves)

        val seriesShelves = allLibraryFiles
            .filter { !it.seriesName.isNullOrBlank() }
            .groupBy { it.seriesName!! }
            .filter { it.value.size >= 2 }
            .map { (series, books) ->
                val sortedSeries = books.sortedBy { it.seriesIndex ?: 999.0 }
                shelvedBookIds.addAll(books.map { it.bookId })
                Shelf("series_$series", series, ShelfType.SERIES, sortedSeries)
            }
        allShelves.addAll(seriesShelves)

        val folderShelves = buildFolderShelves(
            allLibraryFiles = allLibraryFiles,
            syncedFolders = syncedFolders,
            sortOrder = sortOrder
        ).also { shelves ->
            shelves.forEach { shelf ->
                shelvedBookIds.addAll(shelf.books.map { it.bookId })
            }
        }
        allShelves.addAll(folderShelves)

        val unshelvedBooks = allLibraryFiles.filter { it.bookId !in shelvedBookIds }
        allShelves.add(Shelf("unshelved", "Unshelved", ShelfType.MANUAL, sortFiles(unshelvedBooks, sortOrder)))

        allShelves.sortWith(compareBy({ it.type.ordinal }, { it.sortKey }))
        return ShelfProjection(shelves = allShelves, unshelvedBooks = unshelvedBooks)
    }

    private fun buildFolderShelves(
        allLibraryFiles: List<RecentFileItem>,
        syncedFolders: List<SyncedFolder>,
        sortOrder: SortOrder
    ): List<Shelf> {
        val folderNamesByUri = syncedFolders.associate { it.uriString to it.name }
        val folderSegmentsByBookId = allLibraryFiles
            .asSequence()
            .filter { it.sourceFolderUri != null }
            .associate { it.bookId to folderPathResolver.relativeFolderSegments(it) }

        return allLibraryFiles
            .filter { it.sourceFolderUri != null }
            .groupBy { it.sourceFolderUri!! }
            .flatMap { (folderUri, books) ->
                val rootName = folderNamesByUri[folderUri] ?: "Local Folder"
                val rootShelfId = "folder_$folderUri"
                val rootAccumulator = FolderShelfAccumulator(
                    id = rootShelfId,
                    name = rootName,
                    depth = 0,
                    parentShelfId = null,
                    sortPath = ""
                )
                val rootShelf = Shelf(
                    id = rootShelfId,
                    name = rootName,
                    type = ShelfType.FOLDER,
                    books = sortFiles(books, sortOrder),
                    directBooks = emptyList(),
                    childShelfIds = emptyList(),
                    depth = 0,
                    sortKey = "folder:${rootName.lowercase()}:"
                )

                val nestedShelves = linkedMapOf<String, FolderShelfAccumulator>()
                val nestedShelvesById = mutableMapOf<String, FolderShelfAccumulator>()
                books.forEach { book ->
                    rootAccumulator.books.add(book)
                    val segments = folderSegmentsByBookId[book.bookId].orEmpty()
                    if (segments.isEmpty()) {
                        rootAccumulator.directBooks.add(book)
                    }
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
                                nestedShelvesById[parentShelfId]?.childShelfIds?.add(shelfId)
                            }
                            nestedShelvesById[shelfId] = newShelf
                            newShelf
                        }
                        accumulator.books.add(book)
                        if (index == segments.lastIndex) {
                            accumulator.directBooks.add(book)
                        }
                        parentShelfId = shelfId
                    }
                }

                val sortedNestedShelves = nestedShelves
                    .values
                    .sortedBy { it.sortPath }
                    .map { shelf ->
                        Shelf(
                            id = shelf.id,
                            name = shelf.name,
                            type = ShelfType.FOLDER,
                            books = sortFiles(shelf.books, sortOrder),
                            directBooks = sortFiles(shelf.directBooks, sortOrder),
                            parentShelfId = shelf.parentShelfId,
                            childShelfIds = shelf.childShelfIds.sortedBy { it.substringAfterLast("::").lowercase() },
                            depth = shelf.depth,
                            sortKey = "folder:${rootName.lowercase()}:${shelf.sortPath}"
                        )
                    }

                listOf(
                    rootShelf.copy(
                        directBooks = sortFiles(rootAccumulator.directBooks, sortOrder),
                        childShelfIds = rootAccumulator.childShelfIds.sortedBy { it.substringAfterLast("::").lowercase() }
                    )
                ) + sortedNestedShelves
            }
    }

    private data class FolderShelfAccumulator(
        val id: String,
        val name: String,
        val depth: Int,
        val parentShelfId: String?,
        val sortPath: String,
        val books: MutableList<RecentFileItem> = mutableListOf(),
        val directBooks: MutableList<RecentFileItem> = mutableListOf(),
        val childShelfIds: MutableList<String> = mutableListOf()
    )

    private data class ShelfProjection(
        val shelves: List<Shelf>,
        val unshelvedBooks: List<RecentFileItem>
    )

    private data class ProjectionCacheKey(
        val recentFilesFromDb: List<RecentFileItem>,
        val dbShelves: List<ShelfEntity>,
        val shelfRefs: List<BookShelfCrossRef>,
        val dbTags: List<TagEntity>,
        val tagRefs: List<BookTagCrossRef>,
        val folderKeys: List<SyncedFolderProjectionKey>,
        val sortOrder: SortOrder,
        val searchQuery: String,
        val libraryFilters: LibraryFilters,
        val recentFilesLimit: Int
    )

    private data class SyncedFolderProjectionKey(
        val uriString: String,
        val name: String
    )

    private data class CachedProjection(
        val key: ProjectionCacheKey,
        val allLibraryFiles: List<RecentFileItem>,
        val allLibraryFilesById: Map<String, RecentFileItem>,
        val sortedLibraryFiles: List<RecentFileItem>,
        val visibleRecentFiles: List<RecentFileItem>,
        val shelfProjection: ShelfProjection,
        val validShelfIds: Set<String>,
        val dbTags: List<TagEntity>
    )
}

fun filterBySearch(files: List<RecentFileItem>, searchQuery: String): List<RecentFileItem> {
    val query = searchQuery.trim()
    return if (query.isBlank()) {
        files
    } else {
        files.filter { item ->
            item.displayName.contains(query, ignoreCase = true) ||
                item.title?.contains(query, ignoreCase = true) == true ||
                item.author?.contains(query, ignoreCase = true) == true ||
                item.tags.any { tag -> tag.name.contains(query, ignoreCase = true) }
        }
    }
}

fun applyLibraryFilters(files: List<RecentFileItem>, filters: LibraryFilters): List<RecentFileItem> {
    return files.filter { item ->
        val matchType = if (filters.fileTypes.isNotEmpty()) item.type in filters.fileTypes else true
        val matchFolder = if (filters.sourceFolders.isNotEmpty()) {
            val matchesInApp = filters.sourceFolders.contains("IN_APP_STORAGE") &&
                item.sourceFolderUri == null &&
                item.uriString?.startsWith("opds-pse") != true
            val matchesSynced = item.sourceFolderUri in filters.sourceFolders
            matchesInApp || matchesSynced
        } else {
            true
        }
        val progress = item.progressPercentage ?: 0f
        val matchStatus = when (filters.readStatus) {
            ReadStatusFilter.ALL -> true
            ReadStatusFilter.UNREAD -> progress == 0f
            ReadStatusFilter.IN_PROGRESS -> progress > 0f && progress < 100f
            ReadStatusFilter.COMPLETED -> progress >= 100f
        }
        val matchTags = if (filters.tagIds.isNotEmpty()) {
            item.tags.any { it.id in filters.tagIds }
        } else {
            true
        }
        matchType && matchFolder && matchStatus && matchTags
    }
}

fun sortFiles(files: List<RecentFileItem>, sortOrder: SortOrder): List<RecentFileItem> {
    return when (sortOrder) {
        SortOrder.RECENT -> files.sortedByDescending { it.timestamp }
        SortOrder.TITLE_ASC -> files.sortedBy { it.title?.lowercase() ?: it.displayName.lowercase() }
        SortOrder.AUTHOR_ASC -> files.sortedWith(compareBy(nullsLast()) { it.author?.lowercase() })
        SortOrder.PERCENT_ASC -> files.sortedBy { it.progressPercentage ?: 0f }
        SortOrder.PERCENT_DESC -> files.sortedByDescending { it.progressPercentage ?: 0f }
        SortOrder.SIZE_ASC -> files.sortedBy { it.fileSize }
        SortOrder.SIZE_DESC -> files.sortedByDescending { it.fileSize }
    }
}
