package com.aryan.reader.shared

import com.aryan.reader.shared.pdf.SharedPdfReaderViewport
import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.ReaderSettings

enum class FileType {
    PDF, EPUB, MOBI, MD, TXT, HTML, FB2, CBZ, CBR, CB7, CBT, DOCX, ODT, FODT, PPTX, UNKNOWN
}

val PDF_VIEWER_FILE_TYPES: Set<FileType>
    get() = SharedFileCapabilities.readableTypesFor(
        ReaderPlatform.ANDROID,
        ReaderFeatureSurface.PDF_VIEWER
    )

val EPUB_READER_FILE_TYPES: Set<FileType>
    get() = SharedFileCapabilities.readableTypesFor(
        ReaderPlatform.ANDROID,
        ReaderFeatureSurface.EPUB_READER
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

const val IN_APP_STORAGE_SOURCE = "IN_APP_STORAGE"

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
    val allowedFileTypes: Set<FileType> = SharedFileCapabilities.knownFileTypes,
    val localSyncEnabled: Boolean = true
)

data class BookItem(
    val id: String,
    val path: String?,
    val type: FileType,
    val displayName: String,
    val timestamp: Long,
    val coverImagePath: String? = null,
    val title: String? = null,
    val author: String? = null,
    val description: String? = null,
    val originalTitle: String? = null,
    val originalAuthor: String? = null,
    val originalSeriesName: String? = null,
    val originalSeriesIndex: Double? = null,
    val originalDescription: String? = null,
    val progressPercentage: Float? = null,
    val isRecent: Boolean = true,
    val fileSize: Long = 0L,
    val fileContentModifiedTimestamp: Long = 0L,
    val sourceFolder: String? = null,
    val folderTextMetadataParsed: Boolean = false,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val tags: List<Tag> = emptyList(),
    val lastPageIndex: Int? = null,
    val readerPosition: ReaderLocator? = null,
    val readerSettings: ReaderSettings? = null,
    val readerBookmarks: List<ReaderBookmark> = emptyList(),
    val readerHighlights: List<UserHighlight> = emptyList(),
    val pdfReaderViewport: SharedPdfReaderViewport? = null,
    val readingPositionModifiedTimestamp: Long = 0L
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
    val message: String? = null,
    val messageText: SharedText? = null
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
