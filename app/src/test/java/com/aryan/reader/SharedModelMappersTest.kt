package com.aryan.reader

import com.aryan.reader.data.BookTagCrossRef
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.FileType as SharedFileType
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf as SharedShelf
import com.aryan.reader.shared.ShelfType as SharedShelfType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class SharedModelMappersTest {

    @Test
    fun `book mapper preserves android-only fields when shared projection maps back by id`() {
        val tag = TagEntity(id = "tag", name = "Favorite", color = 0xFFAA00AA.toInt(), createdAt = 10L)
        val original = recentFile(
            id = "book",
            type = FileType.PDF,
            displayName = "Original.pdf",
            customName = "Custom name",
            isAvailable = false,
            bookmarksJson = """[{"page":2}]""",
            sourceFolderUri = "content://folder",
            tags = listOf(tag)
        )

        val shared = original.toSharedBookItem()
        val mapped = shared.toRecentFileItem(
            androidBooksById = mapOf(original.bookId to original),
            tagEntitiesById = mapOf(tag.id to tag)
        )

        assertEquals("Custom name", shared.displayName)
        assertEquals(original.uriString, mapped.uriString)
        assertEquals(original.displayName, mapped.displayName)
        assertEquals(original.customName, mapped.customName)
        assertEquals(original.bookmarksJson, mapped.bookmarksJson)
        assertEquals(original.sourceFolderUri, mapped.sourceFolderUri)
        assertFalse(mapped.isAvailable)
        assertEquals(listOf(tag), mapped.tags)
    }

    @Test
    fun `shared projection state maps shelves tabs selections and tags back to android state`() {
        val tag = TagEntity(id = "tag", name = "Queued", createdAt = 1L)
        val book = recentFile("book", tags = listOf(tag))
        val sharedBook = book.toSharedBookItem()
        val sharedShelf = SharedShelf(
            id = "manual",
            name = "Manual",
            type = SharedShelfType.MANUAL,
            books = listOf(sharedBook),
            directBooks = listOf(sharedBook)
        )
        val projected = SharedReaderScreenState(
            recentBooks = listOf(sharedBook),
            libraryBooks = listOf(sharedBook),
            rawLibraryBooks = listOf(sharedBook),
            selectedBookIds = setOf("book", "missing"),
            selectedShelfIds = setOf("manual"),
            shelves = listOf(sharedShelf),
            openTabs = listOf(sharedBook),
            openTabIds = listOf("book"),
            activeTabBookId = "book",
            booksAvailableForAdding = listOf(sharedBook),
            allTags = listOf(tag.toSharedTag())
        )

        val android = projected.toAndroidReaderScreenState(
            base = ReaderScreenState(contextualActionItems = setOf(recentFile("missing"))),
            androidBooksById = mapOf(book.bookId to book),
            tagEntitiesById = mapOf(tag.id to tag)
        )

        assertEquals(listOf("book"), android.recentFiles.ids())
        assertEquals(listOf("book"), android.allRecentFiles.ids())
        assertEquals(listOf("book"), android.rawLibraryFiles.ids())
        assertEquals(setOf("book"), android.contextualActionItems.mapTo(mutableSetOf()) { it.bookId })
        assertEquals(setOf("manual"), android.contextualActionShelfIds)
        assertEquals(listOf("manual"), android.shelves.map { it.id })
        assertEquals(listOf("book"), android.shelves.single().books.ids())
        assertEquals(listOf("book"), android.openTabs.ids())
        assertEquals(listOf("book"), android.openTabIds)
        assertEquals("book", android.activeTabBookId)
        assertEquals(listOf("book"), android.booksAvailableForAdding.ids())
        assertEquals(listOf(tag), android.allTags)
    }

    @Test
    fun `enum filter and folder mappers round trip between android and shared`() {
        val filters = LibraryFilters(
            fileTypes = setOf(FileType.PDF, FileType.EPUB),
            sourceFolders = setOf("IN_APP_STORAGE", "content://folder"),
            readStatus = ReadStatusFilter.IN_PROGRESS,
            tagIds = setOf("tag")
        )
        val folder = SyncedFolder(
            uriString = "content://folder",
            name = "Folder",
            lastScanTime = 42L,
            allowedFileTypes = setOf(FileType.PDF, FileType.CBZ)
        )

        assertEquals(FileType.PDF, SharedFileType.PDF.toAndroidFileType())
        assertEquals(SharedFileType.CBZ, FileType.CBZ.toSharedFileType())
        assertSame(FileType.UNKNOWN, SharedFileType.UNKNOWN.toAndroidFileType())
        assertSame(filters, filters.toSharedLibraryFilters())
        assertSame(folder, folder.toSharedSyncedFolder())
        assertEquals(filters, filters.toSharedLibraryFilters().toAndroidLibraryFilters())
        assertEquals(folder, folder.toSharedSyncedFolder().toAndroidSyncedFolder())
        assertFalse(FileType.UNKNOWN in ANDROID_READABLE_FILE_TYPES)
        assertFalse(FileType.UNKNOWN in ANDROID_SYNCABLE_FILE_TYPES)
    }

    @Test
    fun `tag resolver attaches database tags before shared projection`() {
        val tag = TagEntity(id = "tag", name = "Reference", createdAt = 1L)
        val files = listOf(recentFile("book"))

        val tagged = files.withResolvedTags(
            dbTags = listOf(tag),
            tagRefs = listOf(BookTagCrossRef(bookId = "book", tagId = "tag"))
        )

        assertEquals(listOf(tag), tagged.single().tags)
    }

    private fun recentFile(
        id: String,
        type: FileType = FileType.EPUB,
        displayName: String = "$id.${type.name.lowercase()}",
        customName: String? = null,
        isAvailable: Boolean = true,
        bookmarksJson: String? = null,
        sourceFolderUri: String? = null,
        tags: List<TagEntity> = emptyList()
    ) = RecentFileItem(
        bookId = id,
        uriString = "content://$id",
        type = type,
        displayName = displayName,
        timestamp = 1L,
        isAvailable = isAvailable,
        bookmarksJson = bookmarksJson,
        sourceFolderUri = sourceFolderUri,
        customName = customName,
        tags = tags
    )

    private fun List<RecentFileItem>.ids() = map { it.bookId }
}
