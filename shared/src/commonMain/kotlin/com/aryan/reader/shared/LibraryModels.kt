package com.aryan.reader.shared

enum class FileType {
    PDF, EPUB, MOBI, MD, TXT, HTML, FB2, CBZ, CBR, CB7, DOCX, ODT, FODT, UNKNOWN
}

val PDF_VIEWER_FILE_TYPES = setOf(FileType.PDF, FileType.CBZ, FileType.CBR, FileType.CB7)

val EPUB_READER_FILE_TYPES = setOf(
    FileType.EPUB,
    FileType.MOBI,
    FileType.MD,
    FileType.TXT,
    FileType.HTML,
    FileType.FB2,
    FileType.DOCX,
    FileType.ODT,
    FileType.FODT
)

enum class AddBooksSource {
    UNSHELVED,
    ALL_BOOKS
}

enum class RenderMode {
    VERTICAL_SCROLL,
    PAGINATED
}

enum class SortOrder {
    RECENT,
    TITLE_ASC,
    AUTHOR_ASC,
    PERCENT_ASC,
    PERCENT_DESC,
    SIZE_ASC,
    SIZE_DESC
}

enum class ReadStatusFilter {
    ALL,
    UNREAD,
    IN_PROGRESS,
    COMPLETED
}

enum class ShelfType {
    MANUAL,
    SMART,
    TAG,
    SERIES,
    FOLDER
}

data class Tag(
    val id: String,
    val name: String,
    val color: Int? = null
)

data class SyncedFolder(
    val uriString: String,
    val name: String,
    val lastScanTime: Long,
    val allowedFileTypes: Set<FileType> = FileType.entries.toSet()
)

data class BookItem(
    val id: String,
    val path: String?,
    val type: FileType,
    val displayName: String,
    val timestamp: Long,
    val title: String? = null,
    val author: String? = null,
    val progressPercentage: Float? = null,
    val isRecent: Boolean = true,
    val fileSize: Long = 0L,
    val sourceFolder: String? = null,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val tags: List<Tag> = emptyList()
)

data class Shelf(
    val id: String,
    val name: String,
    val type: ShelfType,
    val books: List<BookItem>,
    val directBooks: List<BookItem> = books,
    val parentShelfId: String? = null,
    val childShelfIds: List<String> = emptyList(),
    val depth: Int = 0,
    val sortKey: String = name.lowercase()
) {
    val bookCount: Int get() = books.size
    val topBook: BookItem? get() = books.maxByOrNull { it.timestamp }
    val directBookCount: Int get() = directBooks.size
    val childShelfCount: Int get() = childShelfIds.size
}

data class LibraryFilters(
    val fileTypes: Set<FileType> = emptySet(),
    val sourceFolders: Set<String> = emptySet(),
    val readStatus: ReadStatusFilter = ReadStatusFilter.ALL,
    val tagIds: Set<String> = emptySet()
) {
    val isActive: Boolean
        get() = fileTypes.isNotEmpty() ||
            sourceFolders.isNotEmpty() ||
            readStatus != ReadStatusFilter.ALL ||
            tagIds.isNotEmpty()
}

data class LibraryState(
    val books: List<BookItem> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.RECENT,
    val filters: LibraryFilters = LibraryFilters(),
    val selectedBookIds: Set<String> = emptySet(),
    val recentLimit: Int = 12,
    val message: String? = null
)

data class HomeScreenModel(
    val recentBooks: List<BookItem>,
    val selectedBooks: List<BookItem>,
    val isEmpty: Boolean
)

data class LibraryScreenModel(
    val books: List<BookItem>,
    val shelves: List<Shelf>,
    val selectedBooks: List<BookItem>,
    val filters: LibraryFilters,
    val searchQuery: String,
    val sortOrder: SortOrder
)
