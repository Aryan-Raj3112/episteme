package com.aryan.reader

import com.aryan.reader.data.BookShelfCrossRef
import com.aryan.reader.data.BookTagCrossRef
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.ShelfEntity
import com.aryan.reader.data.TagEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStateProjectorTest {

    @Test
    fun `filterBySearch matches display name title author and tags`() {
        val sciFi = tag("tag_scifi", "Sci-Fi")
        val fantasy = tag("tag_fantasy", "Fantasy")
        val files = listOf(
            recentFile("display", displayName = "Android Patterns.pdf"),
            recentFile("title", title = "Clean Architecture"),
            recentFile("author", author = "Octavia Butler"),
            recentFile("tagged", tags = listOf(sciFi)),
            recentFile("miss", tags = listOf(fantasy))
        )

        assertEquals(listOf("display"), filterBySearch(files, "android").ids())
        assertEquals(listOf("title"), filterBySearch(files, "architecture").ids())
        assertEquals(listOf("author"), filterBySearch(files, "butler").ids())
        assertEquals(listOf("tagged"), filterBySearch(files, "sci").ids())
        assertEquals(files.ids(), filterBySearch(files, "   ").ids())
    }

    @Test
    fun `applyLibraryFilters requires all active filters to match`() {
        val activeTag = tag("active", "Active")
        val files = listOf(
            recentFile(
                id = "match",
                type = FileType.PDF,
                sourceFolderUri = "content://sync",
                progressPercentage = 50f,
                tags = listOf(activeTag)
            ),
            recentFile(
                id = "wrong_type",
                type = FileType.EPUB,
                sourceFolderUri = "content://sync",
                progressPercentage = 50f,
                tags = listOf(activeTag)
            ),
            recentFile(
                id = "wrong_source",
                type = FileType.PDF,
                sourceFolderUri = null,
                progressPercentage = 50f,
                tags = listOf(activeTag)
            ),
            recentFile(
                id = "completed",
                type = FileType.PDF,
                sourceFolderUri = "content://sync",
                progressPercentage = 100f,
                tags = listOf(activeTag)
            )
        )

        val filters = LibraryFilters(
            fileTypes = setOf(FileType.PDF),
            sourceFolders = setOf("content://sync"),
            readStatus = ReadStatusFilter.IN_PROGRESS,
            tagIds = setOf(activeTag.id)
        )

        assertEquals(listOf("match"), applyLibraryFilters(files, filters).ids())
        assertTrue(filters.isActive)
    }

    @Test
    fun `applyLibraryFilters supports in-app storage source`() {
        val localBook = recentFile("local", uriString = "content://local", sourceFolderUri = null)
        val streamedBook = recentFile("streamed", uriString = "opds-pse://book", sourceFolderUri = null)
        val syncedBook = recentFile("synced", sourceFolderUri = "content://sync")

        val result = applyLibraryFilters(
            listOf(localBook, streamedBook, syncedBook),
            LibraryFilters(sourceFolders = setOf("IN_APP_STORAGE"))
        )

        assertEquals(listOf("local"), result.ids())
    }

    @Test
    fun `applyLibraryFilters treats opds streams separately from in-app storage`() {
        val localBook = recentFile("local", uriString = "file:///local/book.epub", sourceFolderUri = null)
        val streamedBook = recentFile("streamed", uriString = "opds-pse://book", sourceFolderUri = null)

        assertEquals(
            listOf("local"),
            applyLibraryFilters(
                listOf(localBook, streamedBook),
                LibraryFilters(sourceFolders = setOf("IN_APP_STORAGE"))
            ).ids()
        )
    }

    @Test
    fun `applyLibraryFilters separates unread in progress and completed books`() {
        val unread = recentFile("unread", progressPercentage = null)
        val started = recentFile("started", progressPercentage = 1f)
        val middle = recentFile("middle", progressPercentage = 45f)
        val done = recentFile("done", progressPercentage = 100f)
        val files = listOf(unread, started, middle, done)

        assertEquals(
            listOf("unread"),
            applyLibraryFilters(files, LibraryFilters(readStatus = ReadStatusFilter.UNREAD)).ids()
        )
        assertEquals(
            listOf("started", "middle"),
            applyLibraryFilters(files, LibraryFilters(readStatus = ReadStatusFilter.IN_PROGRESS)).ids()
        )
        assertEquals(
            listOf("done"),
            applyLibraryFilters(files, LibraryFilters(readStatus = ReadStatusFilter.COMPLETED)).ids()
        )
    }

    @Test
    fun `sortFiles orders by title author progress size and recency`() {
        val files = listOf(
            recentFile("charlie", title = "Charlie", author = null, timestamp = 3L, progressPercentage = 50f, fileSize = 300L),
            recentFile("alpha", title = "Alpha", author = "Zimmer", timestamp = 1L, progressPercentage = 10f, fileSize = 100L),
            recentFile("bravo", title = "Bravo", author = "Asimov", timestamp = 2L, progressPercentage = 90f, fileSize = 200L)
        )

        assertEquals(listOf("charlie", "bravo", "alpha"), sortFiles(files, SortOrder.RECENT).ids())
        assertEquals(listOf("alpha", "bravo", "charlie"), sortFiles(files, SortOrder.TITLE_ASC).ids())
        assertEquals(listOf("bravo", "alpha", "charlie"), sortFiles(files, SortOrder.AUTHOR_ASC).ids())
        assertEquals(listOf("alpha", "charlie", "bravo"), sortFiles(files, SortOrder.PERCENT_ASC).ids())
        assertEquals(listOf("bravo", "charlie", "alpha"), sortFiles(files, SortOrder.PERCENT_DESC).ids())
        assertEquals(listOf("alpha", "bravo", "charlie"), sortFiles(files, SortOrder.SIZE_ASC).ids())
        assertEquals(listOf("charlie", "bravo", "alpha"), sortFiles(files, SortOrder.SIZE_DESC).ids())
    }

    @Test
    fun `sortFiles falls back to display names and keeps unknown authors last`() {
        val files = listOf(
            recentFile("unknown", displayName = "Zulu.epub", title = null, author = null),
            recentFile("known", displayName = "Beta.epub", title = null, author = "Ada"),
            recentFile("title", displayName = "Alpha.epub", title = "Omega", author = "Grace")
        )

        assertEquals(listOf("known", "title", "unknown"), sortFiles(files, SortOrder.AUTHOR_ASC).ids())
        assertEquals(listOf("known", "title", "unknown"), sortFiles(files, SortOrder.TITLE_ASC).ids())
    }

    @Test
    fun `project builds non-reader library state from repository data`() {
        val tag = tag("tag_favorite", "Favorite")
        val alpha = recentFile(
            id = "alpha",
            type = FileType.PDF,
            title = "Zebra",
            timestamp = 30L,
            progressPercentage = 100f
        )
        val beta = recentFile(
            id = "beta",
            type = FileType.EPUB,
            title = "Alpha",
            timestamp = 20L,
            sourceFolderUri = "content://sync",
            progressPercentage = 40f
        )
        val gamma = recentFile(
            id = "gamma",
            type = FileType.MD,
            title = "Notes",
            timestamp = 10L,
            isRecent = false
        )
        val reflowCopy = recentFile(id = "beta_reflow", title = "Alpha Reflow")
        val manualShelf = shelfEntity("manual", "Manual")

        val state = ReaderScreenState(
            sortOrder = SortOrder.TITLE_ASC,
            recentFilesLimit = 1,
            openTabIds = listOf("beta", "missing"),
            contextualActionItems = setOf(recentFile("beta"), recentFile("missing")),
            viewingShelfId = "manual",
            contextualActionShelfIds = setOf("manual", "missing")
        )

        val result = LibraryStateProjector().project(
            LibraryProjectionInput(
                state = state,
                recentFilesFromDb = listOf(alpha, beta, gamma, reflowCopy),
                dbShelves = listOf(manualShelf),
                shelfRefs = listOf(BookShelfCrossRef(bookId = "alpha", shelfId = "manual", addedAt = 1L)),
                dbTags = listOf(tag),
                tagRefs = listOf(BookTagCrossRef(bookId = "beta", tagId = tag.id))
            )
        )

        assertEquals(listOf("beta", "gamma", "alpha"), result.allRecentFiles.ids())
        assertEquals(listOf("alpha", "beta", "gamma"), result.rawLibraryFiles.ids())
        assertEquals(listOf("beta"), result.recentFiles.ids())
        assertEquals(listOf("beta"), result.openTabs.ids())
        assertEquals(setOf("beta"), result.contextualActionItems.mapTo(mutableSetOf()) { it.bookId })
        assertEquals(listOf(tag), result.contextualActionItems.first().tags)
        assertEquals("manual", result.viewingShelfId)
        assertEquals(setOf("manual"), result.contextualActionShelfIds)
        assertEquals(listOf(tag), result.allTags)
        assertFalse(result.rawLibraryFiles.any { it.bookId.endsWith("_reflow") })
    }

    @Test
    fun `project applies search filters and sort only to library results`() {
        val tag = tag("work", "Work")
        val match = recentFile(
            id = "match",
            title = "Android Work",
            type = FileType.PDF,
            progressPercentage = 80f,
            sourceFolderUri = "content://sync"
        )
        val searchMiss = recentFile(
            id = "search_miss",
            title = "Poetry",
            type = FileType.PDF,
            progressPercentage = 80f,
            sourceFolderUri = "content://sync"
        )
        val filterMiss = recentFile(
            id = "filter_miss",
            title = "Android Notes",
            type = FileType.EPUB,
            progressPercentage = 80f,
            sourceFolderUri = "content://sync"
        )

        val result = LibraryStateProjector().project(
            LibraryProjectionInput(
                state = ReaderScreenState(
                    searchQuery = "android",
                    sortOrder = SortOrder.TITLE_ASC,
                    libraryFilters = LibraryFilters(
                        fileTypes = setOf(FileType.PDF),
                        sourceFolders = setOf("content://sync"),
                        readStatus = ReadStatusFilter.IN_PROGRESS,
                        tagIds = setOf(tag.id)
                    )
                ),
                recentFilesFromDb = listOf(searchMiss, filterMiss, match),
                dbShelves = emptyList(),
                shelfRefs = emptyList(),
                dbTags = listOf(tag),
                tagRefs = listOf(BookTagCrossRef(bookId = "match", tagId = tag.id))
            )
        )

        assertEquals(listOf("match"), result.allRecentFiles.ids())
        assertEquals(listOf("search_miss", "filter_miss", "match"), result.rawLibraryFiles.ids())
        assertEquals(listOf(tag), result.allTags)
    }

    @Test
    fun `project builds manual tag series and unshelved shelves`() {
        val favorite = tag("favorite", "Favorite")
        val manualShelf = shelfEntity("manual", "Manual")
        val manualBook = recentFile("manual", title = "Manual")
        val taggedBook = recentFile("tagged", title = "Tagged")
        val seriesOne = recentFile("series_1", title = "Series One", seriesName = "Saga", seriesIndex = 1.0)
        val seriesTwo = recentFile("series_2", title = "Series Two", seriesName = "Saga", seriesIndex = 2.0)
        val loose = recentFile("loose", title = "Loose")

        val result = LibraryStateProjector().project(
            LibraryProjectionInput(
                state = ReaderScreenState(sortOrder = SortOrder.TITLE_ASC),
                recentFilesFromDb = listOf(manualBook, taggedBook, seriesTwo, loose, seriesOne),
                dbShelves = listOf(manualShelf),
                shelfRefs = listOf(BookShelfCrossRef(bookId = "manual", shelfId = "manual", addedAt = 1L)),
                dbTags = listOf(favorite),
                tagRefs = listOf(BookTagCrossRef(bookId = "tagged", tagId = favorite.id))
            )
        )

        val manual = result.shelves.first { it.id == "manual" }
        val tagShelf = result.shelves.first { it.id == "tag_favorite" }
        val series = result.shelves.first { it.id == "series_Saga" }
        val unshelved = result.shelves.first { it.id == "unshelved" }

        assertEquals(ShelfType.MANUAL, manual.type)
        assertEquals(listOf("manual"), manual.books.ids())
        assertEquals(ShelfType.TAG, tagShelf.type)
        assertEquals(listOf("tagged"), tagShelf.books.ids())
        assertEquals(ShelfType.SERIES, series.type)
        assertEquals(listOf("series_1", "series_2"), series.books.ids())
        assertEquals(listOf("loose", "tagged"), unshelved.books.ids())
    }

    @Test
    fun `project does not create series shelf for a single series book`() {
        val single = recentFile("single", title = "Only Volume", seriesName = "Solo", seriesIndex = 1.0)

        val result = LibraryStateProjector().project(
            LibraryProjectionInput(
                state = ReaderScreenState(),
                recentFilesFromDb = listOf(single),
                dbShelves = emptyList(),
                shelfRefs = emptyList(),
                dbTags = emptyList(),
                tagRefs = emptyList()
            )
        )

        assertTrue(result.shelves.none { it.type == ShelfType.SERIES })
        assertEquals(listOf("single"), result.shelves.first { it.id == "unshelved" }.books.ids())
    }

    @Test
    fun `project exposes all books for adding except books already in current shelf`() {
        val shelf = shelfEntity("manual", "Manual")
        val shelved = recentFile("shelved", title = "Shelved")
        val loose = recentFile("loose", title = "Loose")

        val result = LibraryStateProjector().project(
            LibraryProjectionInput(
                state = ReaderScreenState(
                    viewingShelfId = "manual",
                    isAddingBooksToShelf = true,
                    addBooksSource = AddBooksSource.ALL_BOOKS,
                    sortOrder = SortOrder.TITLE_ASC
                ),
                recentFilesFromDb = listOf(shelved, loose),
                dbShelves = listOf(shelf),
                shelfRefs = listOf(BookShelfCrossRef(bookId = "shelved", shelfId = "manual", addedAt = 1L)),
                dbTags = emptyList(),
                tagRefs = emptyList()
            )
        )

        assertEquals(listOf("loose"), result.booksAvailableForAdding.ids())
    }

    @Test
    fun `project exposes only unshelved books for default add books source`() {
        val shelf = shelfEntity("manual", "Manual")
        val shelved = recentFile("shelved", title = "Shelved")
        val loose = recentFile("loose", title = "Loose")
        val tagged = recentFile("tagged", title = "Tagged")
        val tag = tag("tagged", "Tagged")

        val result = LibraryStateProjector().project(
            LibraryProjectionInput(
                state = ReaderScreenState(
                    viewingShelfId = "manual",
                    isAddingBooksToShelf = true,
                    addBooksSource = AddBooksSource.UNSHELVED,
                    sortOrder = SortOrder.TITLE_ASC
                ),
                recentFilesFromDb = listOf(shelved, loose, tagged),
                dbShelves = listOf(shelf),
                shelfRefs = listOf(BookShelfCrossRef(bookId = "shelved", shelfId = "manual", addedAt = 1L)),
                dbTags = listOf(tag),
                tagRefs = listOf(BookTagCrossRef(bookId = "tagged", tagId = tag.id))
            )
        )

        assertEquals(listOf("loose", "tagged"), result.booksAvailableForAdding.ids())
    }

    @Test
    fun `project clears stale shelf mode when selected shelf disappears`() {
        val result = LibraryStateProjector().project(
            LibraryProjectionInput(
                state = ReaderScreenState(
                    viewingShelfId = "deleted",
                    isAddingBooksToShelf = true,
                    contextualActionShelfIds = setOf("deleted")
                ),
                recentFilesFromDb = listOf(recentFile("book")),
                dbShelves = emptyList(),
                shelfRefs = emptyList(),
                dbTags = emptyList(),
                tagRefs = emptyList()
            )
        )

        assertNull(result.viewingShelfId)
        assertFalse(result.isAddingBooksToShelf)
        assertTrue(result.contextualActionShelfIds.isEmpty())
    }

    @Test
    fun `project creates root and nested shelves for synced folders`() {
        val rootBook = recentFile("root", sourceFolderUri = "content://library", timestamp = 2L)
        val nestedBook = recentFile("nested", sourceFolderUri = "content://library", timestamp = 1L)
        val projector = LibraryStateProjector(
            FolderPathResolver { item ->
                when (item.bookId) {
                    "nested" -> listOf("Series", "Volume 1")
                    else -> emptyList()
                }
            }
        )

        val result = projector.project(
            LibraryProjectionInput(
                state = ReaderScreenState(
                    syncedFolders = listOf(
                        SyncedFolder(
                            uriString = "content://library",
                            name = "Library",
                            lastScanTime = 1L
                        )
                    )
                ),
                recentFilesFromDb = listOf(rootBook, nestedBook),
                dbShelves = emptyList(),
                shelfRefs = emptyList(),
                dbTags = emptyList(),
                tagRefs = emptyList()
            )
        )

        val rootShelf = result.shelves.first { it.id == "folder_content://library" }
        val seriesShelf = result.shelves.first { it.id == "folder_content://library::Series" }
        val volumeShelf = result.shelves.first { it.id == "folder_content://library::Series/Volume 1" }

        assertEquals("Library", rootShelf.name)
        assertEquals(listOf("root", "nested"), rootShelf.books.ids())
        assertEquals(listOf("root"), rootShelf.directBooks.ids())
        assertEquals(listOf(seriesShelf.id), rootShelf.childShelfIds)

        assertEquals(rootShelf.id, seriesShelf.parentShelfId)
        assertEquals(listOf("nested"), seriesShelf.books.ids())
        assertEquals(listOf(volumeShelf.id), seriesShelf.childShelfIds)

        assertEquals(seriesShelf.id, volumeShelf.parentShelfId)
        assertEquals(listOf("nested"), volumeShelf.directBooks.ids())
        assertEquals(2, volumeShelf.depth)
    }

    @Test
    fun `project names folder shelf local folder when synced folder metadata is missing`() {
        val book = recentFile("folder_book", sourceFolderUri = "content://external")

        val result = LibraryStateProjector().project(
            LibraryProjectionInput(
                state = ReaderScreenState(),
                recentFilesFromDb = listOf(book),
                dbShelves = emptyList(),
                shelfRefs = emptyList(),
                dbTags = emptyList(),
                tagRefs = emptyList()
            )
        )

        val folderShelf = result.shelves.first { it.id == "folder_content://external" }
        assertEquals("Local Folder", folderShelf.name)
        assertEquals(listOf("folder_book"), folderShelf.books.ids())
        assertEquals(listOf("folder_book"), folderShelf.directBooks.ids())
    }

    private fun recentFile(
        id: String,
        uriString: String? = "content://$id",
        type: FileType = FileType.EPUB,
        displayName: String = "$id.${type.name.lowercase()}",
        title: String? = null,
        author: String? = null,
        timestamp: Long = 1L,
        isRecent: Boolean = true,
        sourceFolderUri: String? = null,
        progressPercentage: Float? = null,
        tags: List<TagEntity> = emptyList(),
        fileSize: Long = 0L,
        seriesName: String? = null,
        seriesIndex: Double? = null
    ) = RecentFileItem(
        bookId = id,
        uriString = uriString,
        type = type,
        displayName = displayName,
        title = title,
        author = author,
        timestamp = timestamp,
        isRecent = isRecent,
        sourceFolderUri = sourceFolderUri,
        progressPercentage = progressPercentage,
        tags = tags,
        fileSize = fileSize,
        seriesName = seriesName,
        seriesIndex = seriesIndex
    )

    private fun tag(id: String, name: String) = TagEntity(
        id = id,
        name = name,
        createdAt = 1L
    )

    private fun shelfEntity(id: String, name: String) = ShelfEntity(
        id = id,
        name = name,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun List<RecentFileItem>.ids() = map { it.bookId }
}
