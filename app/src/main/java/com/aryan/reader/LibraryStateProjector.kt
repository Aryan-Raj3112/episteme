package com.aryan.reader

import com.aryan.reader.data.BookShelfCrossRef
import com.aryan.reader.data.BookTagCrossRef
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.ShelfEntity
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.LibraryFeatureState
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
        val projectionContext = AndroidLibraryProjectionAdapter.prepare(input, folderPathResolver)
        val cacheKey = ProjectionCacheKey(
            recentFilesFromDb = input.recentFilesFromDb,
            dbShelves = input.dbShelves,
            shelfRefs = input.shelfRefs,
            dbTags = input.dbTags,
            tagRefs = input.tagRefs,
            folderKeys = projectionContext.folderKeys,
            sortOrder = internalState.sortOrder,
            searchQuery = internalState.searchQuery,
            libraryFilters = internalState.libraryFilters,
            openTabIds = internalState.openTabIds,
            activeTabBookId = internalState.activeTabBookId,
            textOpenTabIds = internalState.textOpenTabIds,
            activeTextTabBookId = internalState.activeTextTabBookId,
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

        val projected = AndroidLibraryProjectionAdapter.project(projectionContext)
        val cache = CachedProjection(
            key = cacheKey,
            projected = projected,
            androidBooksById = projectionContext.androidBooksById,
            tagEntitiesById = projectionContext.tagEntitiesById
        )
        cachedProjection = cache

        val elapsed = ReaderPerfLog.elapsedMs(start)
        if (elapsed >= 16L || projectionContext.androidBooksById.size >= 500) {
            ReaderPerfLog.d(
                "LibraryProject shared recompute took ${elapsed}ms books=${projectionContext.androidBooksById.size} " +
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
        return AndroidLibraryProjectionAdapter.restoreAndroidState(
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
        val folderKeys: List<AndroidFolderProjectionKey>,
        val sortOrder: SortOrder,
        val searchQuery: String,
        val libraryFilters: LibraryFilters,
        val openTabIds: List<String>,
        val activeTabBookId: String?,
        val textOpenTabIds: List<String>,
        val activeTextTabBookId: String?,
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
        val projected: LibraryFeatureState,
        val androidBooksById: Map<String, RecentFileItem>,
        val tagEntitiesById: Map<String, TagEntity>
    )
}

/**
 * One projection cache per library pass. The helpers below are called back to back on the same
 * book instances (filter -> view filter -> sort), so they must share a cache: each one builds a
 * [SharedBookItem] per book, and a per-call cache maps the whole list 3-4 times per pass.
 */
internal fun projectionCacheFor(files: List<RecentFileItem>): SharedProjectionBookItemCache =
    SharedProjectionBookItemCache(maxEntries = files.size.coerceAtLeast(1))

fun filterBySearch(files: List<RecentFileItem>, searchQuery: String): List<RecentFileItem> =
    filterBySearch(files, searchQuery, projectionCacheFor(files))

internal fun filterBySearch(
    files: List<RecentFileItem>,
    searchQuery: String,
    mapper: SharedProjectionBookItemCache,
    byId: Map<String, RecentFileItem> = files.associateBy { it.bookId },
): List<RecentFileItem> =
    files.mapSharedResults(sharedFilterBySearch(mapper.map(files), searchQuery), byId)

fun applyLibraryFilters(files: List<RecentFileItem>, filters: LibraryFilters): List<RecentFileItem> =
    applyLibraryFilters(files, filters, projectionCacheFor(files))

internal fun applyLibraryFilters(
    files: List<RecentFileItem>,
    filters: LibraryFilters,
    mapper: SharedProjectionBookItemCache,
    byId: Map<String, RecentFileItem> = files.associateBy { it.bookId },
): List<RecentFileItem> =
    files.mapSharedResults(
        sharedApplyLibraryFilters(
            books = mapper.map(files),
            filters = filters
        ),
        byId
    )

fun sortFiles(files: List<RecentFileItem>, sortOrder: SortOrder): List<RecentFileItem> =
    sortFiles(files, sortOrder, projectionCacheFor(files))

internal fun sortFiles(
    files: List<RecentFileItem>,
    sortOrder: SortOrder,
    mapper: SharedProjectionBookItemCache,
    byId: Map<String, RecentFileItem> = files.associateBy { it.bookId },
): List<RecentFileItem> =
    files.mapSharedResults(sharedSortBooks(mapper.map(files), sortOrder), byId)

private fun List<RecentFileItem>.mapSharedResults(
    sharedBooks: List<com.aryan.reader.shared.BookItem>,
    byId: Map<String, RecentFileItem> = associateBy { it.bookId },
): List<RecentFileItem> = sharedBooks.mapNotNull { byId[it.id] }
