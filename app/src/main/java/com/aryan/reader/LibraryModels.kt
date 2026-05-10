package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFileCapabilities

typealias AddBooksSource = com.aryan.reader.shared.AddBooksSource
typealias FileType = com.aryan.reader.shared.FileType
typealias RenderMode = com.aryan.reader.shared.RenderMode
typealias SortOrder = com.aryan.reader.shared.SortOrder
typealias ReadStatusFilter = com.aryan.reader.shared.ReadStatusFilter
typealias LibraryFilters = com.aryan.reader.shared.LibraryFilters
typealias SyncedFolder = com.aryan.reader.shared.SyncedFolder

internal val ANDROID_READABLE_FILE_TYPES = SharedFileCapabilities.readableTypesFor(ReaderPlatform.ANDROID)
internal val ANDROID_SYNCABLE_FILE_TYPES = SharedFileCapabilities.syncableTypesFor(ReaderPlatform.ANDROID)
internal val PDF_VIEWER_FILE_TYPES = com.aryan.reader.shared.PDF_VIEWER_FILE_TYPES
internal val EPUB_READER_FILE_TYPES = com.aryan.reader.shared.EPUB_READER_FILE_TYPES

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
