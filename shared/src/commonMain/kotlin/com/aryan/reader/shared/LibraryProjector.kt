package com.aryan.reader.shared

class LibraryProjector {
    fun home(state: LibraryState): HomeScreenModel {
        val recentBooks = sortBooks(state.books.filter { it.isRecent }, state.sortOrder)
            .take(state.recentLimit)
        return HomeScreenModel(
            recentBooks = recentBooks,
            selectedBooks = state.books.filter { it.id in state.selectedBookIds },
            isEmpty = recentBooks.isEmpty()
        )
    }

    fun library(state: LibraryState): LibraryScreenModel {
        val searched = filterBySearch(state.books, state.searchQuery)
        val filtered = applyFilters(searched, state.filters)
        val sorted = sortBooks(filtered, state.sortOrder)

        return LibraryScreenModel(
            books = sorted,
            shelves = buildShelves(state.books),
            selectedBooks = state.books.filter { it.id in state.selectedBookIds },
            filters = state.filters,
            searchQuery = state.searchQuery,
            sortOrder = state.sortOrder
        )
    }

    fun withImportedFiles(state: LibraryState, files: List<ImportedFile>): LibraryState {
        if (files.isEmpty()) return state
        val now = currentTimestamp()
        val existingIds = state.books.mapTo(mutableSetOf()) { it.id }
        val imported = files.mapIndexedNotNull { index, file ->
            val id = file.path ?: file.name
            if (!existingIds.add(id)) {
                null
            } else {
                BookItem(
                    id = id,
                    path = file.path,
                    type = file.name.toFileType(),
                    displayName = file.name,
                    timestamp = now + index,
                    title = file.name.substringBeforeLast('.'),
                    fileSize = file.size,
                    sourceFolder = file.path?.parentPath()
                )
            }
        }
        return state.copy(
            books = imported + state.books,
            message = if (imported.isEmpty()) "Those files are already in the desktop library." else "Imported ${imported.size} file(s). Reader support comes later."
        )
    }

    fun sortBooks(books: List<BookItem>, sortOrder: SortOrder): List<BookItem> {
        return when (sortOrder) {
            SortOrder.RECENT -> books.sortedByDescending { it.timestamp }
            SortOrder.TITLE_ASC -> books.sortedBy { it.title?.lowercase() ?: it.displayName.lowercase() }
            SortOrder.AUTHOR_ASC -> books.sortedBy { it.author?.lowercase() ?: "" }
            SortOrder.PERCENT_ASC -> books.sortedBy { it.progressPercentage ?: 0f }
            SortOrder.PERCENT_DESC -> books.sortedByDescending { it.progressPercentage ?: 0f }
            SortOrder.SIZE_ASC -> books.sortedBy { it.fileSize }
            SortOrder.SIZE_DESC -> books.sortedByDescending { it.fileSize }
        }
    }

    fun filterBySearch(books: List<BookItem>, query: String): List<BookItem> {
        val normalized = query.trim()
        if (normalized.isBlank()) return books
        return books.filter { book ->
            book.displayName.contains(normalized, ignoreCase = true) ||
                book.title?.contains(normalized, ignoreCase = true) == true ||
                book.author?.contains(normalized, ignoreCase = true) == true ||
                book.tags.any { it.name.contains(normalized, ignoreCase = true) }
        }
    }

    fun applyFilters(books: List<BookItem>, filters: LibraryFilters): List<BookItem> {
        return books.filter { book ->
            val matchesType = filters.fileTypes.isEmpty() || book.type in filters.fileTypes
            val matchesFolder = filters.sourceFolders.isEmpty() || book.sourceFolder in filters.sourceFolders
            val progress = book.progressPercentage ?: 0f
            val matchesStatus = when (filters.readStatus) {
                ReadStatusFilter.ALL -> true
                ReadStatusFilter.UNREAD -> progress == 0f
                ReadStatusFilter.IN_PROGRESS -> progress > 0f && progress < 100f
                ReadStatusFilter.COMPLETED -> progress >= 100f
            }
            val matchesTags = filters.tagIds.isEmpty() || book.tags.any { it.id in filters.tagIds }
            matchesType && matchesFolder && matchesStatus && matchesTags
        }
    }

    fun buildShelves(books: List<BookItem>): List<Shelf> {
        val seriesShelves = books
            .filter { !it.seriesName.isNullOrBlank() }
            .groupBy { it.seriesName.orEmpty() }
            .filter { it.value.size > 1 }
            .map { (series, seriesBooks) ->
                Shelf(
                    id = "series_$series",
                    name = series,
                    type = ShelfType.SERIES,
                    books = seriesBooks.sortedBy { it.seriesIndex ?: 999.0 }
                )
            }

        val folderShelves = books
            .filter { it.sourceFolder != null }
            .groupBy { it.sourceFolder.orEmpty() }
            .map { (folder, folderBooks) ->
                Shelf(
                    id = "folder_$folder",
                    name = folder.folderDisplayName(),
                    type = ShelfType.FOLDER,
                    books = sortBooks(folderBooks, SortOrder.TITLE_ASC)
                )
            }

        val tagShelves = books
            .flatMap { book -> book.tags.map { tag -> tag to book } }
            .groupBy({ it.first }, { it.second })
            .map { (tag, taggedBooks) ->
                Shelf(
                    id = "tag_${tag.id}",
                    name = tag.name,
                    type = ShelfType.TAG,
                    books = sortBooks(taggedBooks, SortOrder.TITLE_ASC)
                )
            }

        return (seriesShelves + folderShelves + tagShelves)
            .sortedWith(compareBy({ it.type.ordinal }, { it.name.lowercase() }))
    }
}

private fun String.parentPath(): String? {
    val normalized = replace('\\', '/')
    val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
    return parent.ifBlank { null }
}

private fun String.folderDisplayName(): String {
    return replace('\\', '/').trimEnd('/').substringAfterLast('/').ifBlank { "Local Folder" }
}

data class ImportedFile(
    val name: String,
    val path: String?,
    val size: Long
)

expect fun currentTimestamp(): Long

fun String.toFileType(): FileType {
    return when (substringAfterLast('.', "").lowercase()) {
        "pdf" -> FileType.PDF
        "epub" -> FileType.EPUB
        "mobi" -> FileType.MOBI
        "md" -> FileType.MD
        "txt" -> FileType.TXT
        "html", "htm" -> FileType.HTML
        "fb2" -> FileType.FB2
        "cbz" -> FileType.CBZ
        "cbr" -> FileType.CBR
        "cb7" -> FileType.CB7
        "docx" -> FileType.DOCX
        "odt" -> FileType.ODT
        "fodt" -> FileType.FODT
        else -> FileType.UNKNOWN
    }
}
