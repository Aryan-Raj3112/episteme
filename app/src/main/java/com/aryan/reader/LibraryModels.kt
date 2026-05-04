package com.aryan.reader

import com.aryan.reader.data.RecentFileItem

enum class AddBooksSource {
    UNSHELVED,
    ALL_BOOKS
}

enum class FileType {
    PDF, EPUB, MOBI, MD, TXT, HTML, FB2, CBZ, CBR, CB7, DOCX, ODT, FODT
}

internal val PDF_VIEWER_FILE_TYPES = setOf(FileType.PDF, FileType.CBZ, FileType.CBR, FileType.CB7)

internal val EPUB_READER_FILE_TYPES = setOf(
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

enum class RenderMode {
    VERTICAL_SCROLL, PAGINATED
}

data class SyncedFolder(
    val uriString: String,
    val name: String,
    val lastScanTime: Long,
    val allowedFileTypes: Set<FileType> = FileType.entries.toSet()
)

enum class ShelfType { MANUAL, SMART, TAG, SERIES, FOLDER }

data class Shelf(
    val id: String,
    val name: String,
    val type: ShelfType,
    val books: List<RecentFileItem>,
    val directBooks: List<RecentFileItem> = books,
    val parentShelfId: String? = null,
    val childShelfIds: List<String> = emptyList(),
    val depth: Int = 0,
    val sortKey: String = name.lowercase()
) {
    val bookCount: Int get() = books.size
    val topBook: RecentFileItem? by lazy(LazyThreadSafetyMode.NONE) { books.maxByOrNull { it.timestamp } }
    val directBookCount: Int get() = directBooks.size
    val childShelfCount: Int get() = childShelfIds.size
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
