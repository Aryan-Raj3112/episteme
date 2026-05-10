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
        val plan = SharedImportPlanner.plan(
            files = files.map { it.toImportedBookFile() },
            existingBookIds = state.books.mapTo(mutableSetOf()) { it.id },
            platform = ReaderPlatform.DESKTOP
        )
        return state.copy(
            books = plan.importedBooks + state.books,
            message = when {
                plan.importedCount > 0 -> "Imported ${plan.importedCount} file(s). Reader support comes later."
                plan.unsupportedCount > 0 -> "No supported files were imported."
                else -> "Those files are already in the desktop library."
            }
        )
    }

    fun sortBooks(books: List<BookItem>, sortOrder: SortOrder): List<BookItem> {
        return when (sortOrder) {
            SortOrder.RECENT -> books.sortedByDescending { it.timestamp }
            SortOrder.TITLE_ASC -> books.sortedBy { it.title?.lowercase() ?: it.displayName.lowercase() }
            SortOrder.AUTHOR_ASC -> books.sortedWith(compareBy(nullsLast()) { it.author?.lowercase() })
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
            val matchesFolder = book.matchesSourceFolders(filters.sourceFolders)
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

private fun String.folderDisplayName(): String {
    return replace('\\', '/').trimEnd('/').substringAfterLast('/').ifBlank { "Local Folder" }
}

data class ImportedFile(
    val name: String,
    val path: String?,
    val size: Long,
    val sourceFolder: String? = null
)

private fun ImportedFile.toImportedBookFile(): ImportedBookFile {
    return ImportedBookFile(
        name = name,
        uriString = null,
        localPath = path,
        size = size,
        sourceFolder = sourceFolder
    )
}

expect fun currentTimestamp(): Long

fun String.toFileType(): FileType {
    return SharedFileCapabilities.fileTypeForName(this)
}
