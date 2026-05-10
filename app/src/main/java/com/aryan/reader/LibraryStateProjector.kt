package com.aryan.reader

import com.aryan.reader.data.BookShelfCrossRef
import com.aryan.reader.data.BookTagCrossRef
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.ShelfEntity
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.applyLibraryFilters as sharedApplyLibraryFilters
import com.aryan.reader.shared.filterBySearch as sharedFilterBySearch
import com.aryan.reader.shared.sortBooks as sharedSortBooks

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
        val bridgeContext = AndroidSharedStateBridge.prepareLibraryProjection(input, folderPathResolver)
        val cacheKey = ProjectionCacheKey(
            recentFilesFromDb = input.recentFilesFromDb,
            dbShelves = input.dbShelves,
            shelfRefs = input.shelfRefs,
            dbTags = input.dbTags,
            tagRefs = input.tagRefs,
            folderKeys = bridgeContext.folderKeys,
            sortOrder = internalState.sortOrder,
            searchQuery = internalState.searchQuery,
            libraryFilters = internalState.libraryFilters,
            recentFilesLimit = internalState.recentFilesLimit,
            openTabIds = internalState.openTabIds,
            activeTabBookId = internalState.activeTabBookId,
            selectedBookIds = internalState.contextualActionItems.mapTo(mutableSetOf()) { it.bookId },
            selectedShelfIds = internalState.contextualActionShelfIds,
            viewingShelfId = internalState.viewingShelfId,
            isAddingBooksToShelf = internalState.isAddingBooksToShelf,
            addBooksSource = internalState.addBooksSource,
            booksSelectedForAdding = internalState.booksSelectedForAdding,
            pinnedHomeBookIds = internalState.pinnedHomeBookIds,
            pinnedLibraryBookIds = internalState.pinnedLibraryBookIds
        )

        cachedProjection?.takeIf { it.key == cacheKey }?.let { cache ->
            val result = buildStateFromCache(internalState, cache)
            val elapsed = ReaderPerfLog.elapsedMs(start)
            if (elapsed >= 8L) {
                ReaderPerfLog.d(
                    "LibraryProject cache-hit took ${elapsed}ms books=${cache.androidBooksById.size} shelves=${cache.projected.shelves.size}"
                )
            }
            return result
        }

        val projected = AndroidSharedStateBridge.projectLibrary(bridgeContext)
        val cache = CachedProjection(
            key = cacheKey,
            projected = projected,
            androidBooksById = bridgeContext.androidBooksById,
            tagEntitiesById = bridgeContext.tagEntitiesById
        )
        cachedProjection = cache

        val elapsed = ReaderPerfLog.elapsedMs(start)
        if (elapsed >= 16L || bridgeContext.androidBooksById.size >= 500) {
            ReaderPerfLog.d(
                "LibraryProject shared recompute took ${elapsed}ms books=${bridgeContext.androidBooksById.size} " +
                    "visible=${projected.libraryBooks.size} shelves=${projected.shelves.size} " +
                    "tags=${input.dbTags.size} shelfRefs=${input.shelfRefs.size} tagRefs=${input.tagRefs.size}"
            )
        }

        return buildStateFromCache(internalState, cache)
    }

    private fun buildStateFromCache(
        internalState: ReaderScreenState,
        cache: CachedProjection
    ): ReaderScreenState {
        return AndroidSharedStateBridge.toAndroidState(
            base = internalState,
            sharedState = cache.projected,
            androidBooksById = cache.androidBooksById,
            tagEntitiesById = cache.tagEntitiesById
        )
    }

    private data class ProjectionCacheKey(
        val recentFilesFromDb: List<RecentFileItem>,
        val dbShelves: List<ShelfEntity>,
        val shelfRefs: List<BookShelfCrossRef>,
        val dbTags: List<TagEntity>,
        val tagRefs: List<BookTagCrossRef>,
        val folderKeys: List<AndroidSharedFolderProjectionKey>,
        val sortOrder: SortOrder,
        val searchQuery: String,
        val libraryFilters: LibraryFilters,
        val recentFilesLimit: Int,
        val openTabIds: List<String>,
        val activeTabBookId: String?,
        val selectedBookIds: Set<String>,
        val selectedShelfIds: Set<String>,
        val viewingShelfId: String?,
        val isAddingBooksToShelf: Boolean,
        val addBooksSource: AddBooksSource,
        val booksSelectedForAdding: Set<String>,
        val pinnedHomeBookIds: Set<String>,
        val pinnedLibraryBookIds: Set<String>
    )

    private data class CachedProjection(
        val key: ProjectionCacheKey,
        val projected: SharedReaderScreenState,
        val androidBooksById: Map<String, RecentFileItem>,
        val tagEntitiesById: Map<String, TagEntity>
    )
}

fun filterBySearch(files: List<RecentFileItem>, searchQuery: String): List<RecentFileItem> {
    return files.mapSharedResults(sharedFilterBySearch(files.map { it.toSharedProjectionBookItem() }, searchQuery))
}

fun applyLibraryFilters(files: List<RecentFileItem>, filters: LibraryFilters): List<RecentFileItem> {
    return files.mapSharedResults(
        sharedApplyLibraryFilters(
            books = files.map { it.toSharedProjectionBookItem() },
            filters = filters.toSharedLibraryFilters()
        )
    )
}

fun sortFiles(files: List<RecentFileItem>, sortOrder: SortOrder): List<RecentFileItem> {
    return files.mapSharedResults(sharedSortBooks(files.map { it.toSharedProjectionBookItem() }, sortOrder.toSharedSortOrder()))
}

private fun List<RecentFileItem>.mapSharedResults(sharedBooks: List<com.aryan.reader.shared.BookItem>): List<RecentFileItem> {
    val byId = associateBy { it.bookId }
    return sharedBooks.mapNotNull { byId[it.id] }
}
