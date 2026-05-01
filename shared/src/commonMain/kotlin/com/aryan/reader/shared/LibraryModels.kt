package com.aryan.reader.shared

enum class FileType {
    PDF, EPUB, MOBI, MD, TXT, HTML, FB2, CBZ, CBR, CB7, DOCX, ODT, FODT, UNKNOWN
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
    val directBooks: List<BookItem> = books
) {
    val bookCount: Int get() = books.size
    val topBook: BookItem? get() = books.maxByOrNull { it.timestamp }
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
