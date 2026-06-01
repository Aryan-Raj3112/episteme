package com.aryan.reader.shared

data class SharedLibraryMutationResult(
    val state: SharedReaderScreenState,
    val shelfRecords: List<ShelfRecord>,
    val shelfRefs: List<BookShelfRef>
)

object SharedLibraryEditor {
    fun cleanShelfName(name: String): String? {
        return name.trim().takeIf { it.isNotBlank() }
    }

    fun canMutateShelf(shelfId: String?): Boolean {
        val trimmed = shelfId?.trim()
        return !trimmed.isNullOrBlank() && trimmed != "unshelved"
    }

    fun createShelfRecord(
        name: String,
        id: String,
        isSmart: Boolean = false,
        smartRulesJson: String? = null
    ): ShelfRecord? {
        val trimmed = cleanShelfName(name) ?: return null
        val trimmedId = id.trim().takeIf { it.isNotBlank() } ?: return null
        return ShelfRecord(
            id = trimmedId,
            name = trimmed,
            isSmart = isSmart,
            smartRulesJson = smartRulesJson
        )
    }

    fun cleanTagName(name: String): String? {
        return name.trim().takeIf { it.isNotBlank() }
    }

    fun createTag(
        name: String,
        id: String,
        color: Int? = 0xFF64B5F6.toInt()
    ): Tag? {
        val trimmed = cleanTagName(name) ?: return null
        val trimmedId = id.trim().takeIf { it.isNotBlank() } ?: return null
        return Tag(
            id = trimmedId,
            name = trimmed,
            color = color
        )
    }

    fun cleanBookIds(bookIds: Iterable<String>): Set<String> {
        return bookIds.mapTo(mutableSetOf()) { it.trim() }.filterTo(mutableSetOf()) { it.isNotBlank() }
    }

    fun removeSelectedBooks(
        state: SharedReaderScreenState,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>
    ): SharedLibraryMutationResult? {
        val selected = state.selectedBookIds
        if (selected.isEmpty()) return null
        return SharedLibraryMutationResult(
            state = state.copy(
                rawLibraryBooks = state.rawLibraryBooks.filterNot { it.id in selected },
                selectedBookIds = emptySet(),
                bannerMessage = BannerMessage.quantity(
                    "banner_books_removed_library",
                    selected.size,
                    "%1\$d book removed from library.",
                    "%1\$d books removed from library.",
                    selected.size
                )
            ),
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs.filterNot { it.bookId in selected }
        )
    }

    fun createShelf(
        state: SharedReaderScreenState,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        name: String,
        nowMillis: Long = currentTimestamp()
    ): SharedLibraryMutationResult? {
        val trimmed = cleanShelfName(name) ?: return null
        return SharedLibraryMutationResult(
            state = state.copy(
                bannerMessage = BannerMessage.string(
                    "banner_shelf_created",
                    "Created shelf \"%1\$s\".",
                    trimmed
                )
            ),
            shelfRecords = shelfRecords + ShelfRecord(id = "shelf_$nowMillis", name = trimmed),
            shelfRefs = shelfRefs
        )
    }

    fun createShelfWithBooks(
        state: SharedReaderScreenState,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        name: String,
        bookIds: Iterable<String>,
        clearSelection: Boolean = true,
        nowMillis: Long = currentTimestamp()
    ): SharedLibraryMutationResult? {
        val trimmed = cleanShelfName(name) ?: return null
        val selectedBooks = cleanBookIds(bookIds)
        val shelfId = "shelf_$nowMillis"
        val newRefs = selectedBooks.map { bookId ->
            BookShelfRef(bookId = bookId, shelfId = shelfId, addedAt = nowMillis)
        }
        return SharedLibraryMutationResult(
            state = state.copy(
                selectedBookIds = if (clearSelection && selectedBooks.isNotEmpty()) emptySet() else state.selectedBookIds,
                bannerMessage = if (selectedBooks.isEmpty()) {
                    BannerMessage.string(
                        "banner_shelf_created",
                        "Created shelf \"%1\$s\".",
                        trimmed
                    )
                } else {
                    BannerMessage.quantity(
                        "banner_shelf_created_with_books",
                        selectedBooks.size,
                        "Created shelf \"%1\$s\" with %2\$d book.",
                        "Created shelf \"%1\$s\" with %2\$d books.",
                        trimmed,
                        selectedBooks.size
                    )
                }
            ),
            shelfRecords = shelfRecords + ShelfRecord(id = shelfId, name = trimmed),
            shelfRefs = shelfRefs + newRefs
        )
    }

    fun createSmartShelf(
        state: SharedReaderScreenState,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        name: String,
        definition: SmartCollectionDefinition,
        nowMillis: Long = currentTimestamp()
    ): SharedLibraryMutationResult? {
        val trimmed = cleanShelfName(name) ?: return null
        val cleanedRules = definition.rules.mapNotNull { rule ->
            rule.value.trim().takeIf { it.isNotBlank() }?.let { value -> rule.copy(value = value) }
        }
        if (cleanedRules.isEmpty()) return null
        val cleanedDefinition = definition.copy(rules = cleanedRules)
        return SharedLibraryMutationResult(
            state = state.copy(
                bannerMessage = BannerMessage.string(
                    "banner_smart_shelf_created",
                    "Created smart shelf \"%1\$s\".",
                    trimmed
                )
            ),
            shelfRecords = shelfRecords + ShelfRecord(
                id = "smart_$nowMillis",
                name = trimmed,
                isSmart = true,
                smartRulesJson = SmartCollectionEngine.toJson(cleanedDefinition)
            ),
            shelfRefs = shelfRefs
        )
    }

    fun renameShelf(
        state: SharedReaderScreenState,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        shelf: Shelf,
        name: String
    ): SharedLibraryMutationResult? {
        val trimmed = cleanShelfName(name) ?: return null
        return SharedLibraryMutationResult(
            state = state.copy(
                bannerMessage = BannerMessage.string(
                    "banner_shelf_renamed",
                    "Renamed shelf to \"%1\$s\".",
                    trimmed
                )
            ),
            shelfRecords = shelfRecords.map { if (it.id == shelf.id) it.copy(name = trimmed) else it },
            shelfRefs = shelfRefs
        )
    }

    fun deleteShelf(
        state: SharedReaderScreenState,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        shelf: Shelf
    ): SharedLibraryMutationResult {
        return SharedLibraryMutationResult(
            state = state.copy(
                bannerMessage = BannerMessage.string(
                    "banner_shelf_deleted",
                    "Deleted shelf \"%1\$s\".",
                    shelf.name
                )
            ),
            shelfRecords = shelfRecords.filterNot { it.id == shelf.id },
            shelfRefs = shelfRefs.filterNot { it.shelfId == shelf.id }
        )
    }

    fun removeFolder(
        state: SharedReaderScreenState,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        folder: Shelf
    ): SharedLibraryMutationResult? {
        if (folder.type != ShelfType.FOLDER) return null
        val folderBookIds = cleanBookIds(folder.books.map { it.id })
        if (folderBookIds.isEmpty()) return null
        val rootSourceFolder = folder.books.firstNotNullOfOrNull { it.sourceFolder }
        val remainingTabs = state.openTabIds.filterNot { it in folderBookIds }
        return SharedLibraryMutationResult(
            state = state.copy(
                rawLibraryBooks = state.rawLibraryBooks.filterNot { it.id in folderBookIds },
                selectedBookIds = state.selectedBookIds - folderBookIds,
                pinnedHomeBookIds = state.pinnedHomeBookIds - folderBookIds,
                pinnedLibraryBookIds = state.pinnedLibraryBookIds - folderBookIds,
                openTabIds = remainingTabs,
                activeTabBookId = state.activeTabBookId?.takeUnless { it in folderBookIds },
                syncedFolders = if (folder.parentShelfId == null && rootSourceFolder != null) {
                    state.syncedFolders.filterNot { it.uriString == rootSourceFolder }
                } else {
                    state.syncedFolders
                },
                libraryFilters = if (rootSourceFolder != null) {
                    state.libraryFilters.copy(sourceFolders = state.libraryFilters.sourceFolders - rootSourceFolder)
                } else {
                    state.libraryFilters
                },
                bannerMessage = BannerMessage.quantity(
                    "banner_folder_removed_with_book_count",
                    folderBookIds.size,
                    "Removed folder \"%1\$s\" and %2\$d book from the app.",
                    "Removed folder \"%1\$s\" and %2\$d books from the app.",
                    folder.name,
                    folderBookIds.size
                )
            ),
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs.filterNot { it.bookId in folderBookIds }
        )
    }

    fun markBookOpened(
        state: SharedReaderScreenState,
        bookId: String,
        nowMillis: Long = currentTimestamp()
    ): SharedReaderScreenState {
        val cleanedBookId = bookId.trim()
        if (cleanedBookId.isBlank()) return state
        return state.copy(
            rawLibraryBooks = state.rawLibraryBooks.map { book ->
                if (book.id == cleanedBookId) {
                    book.copy(isRecent = true, timestamp = nowMillis)
                } else {
                    book
                }
            }
        )
    }

    fun addSelectedBooksToShelf(
        state: SharedReaderScreenState,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        shelfId: String,
        nowMillis: Long = currentTimestamp()
    ): SharedLibraryMutationResult? {
        return addBooksToShelves(
            state = state,
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs,
            bookIds = state.selectedBookIds,
            shelfIds = listOf(shelfId),
            clearSelection = true,
            nowMillis = nowMillis,
            bannerName = "banner_books_added_to_shelf",
            singularMessage = "%1\$d book added to shelf.",
            pluralMessage = "%1\$d books added to shelf."
        )
    }

    fun addBooksToShelves(
        state: SharedReaderScreenState,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        bookIds: Iterable<String>,
        shelfIds: Iterable<String>,
        clearSelection: Boolean = true,
        nowMillis: Long = currentTimestamp()
    ): SharedLibraryMutationResult? {
        return addBooksToShelves(
            state = state,
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs,
            bookIds = bookIds,
            shelfIds = shelfIds,
            clearSelection = clearSelection,
            nowMillis = nowMillis,
            bannerName = "banner_books_added_to_shelves",
            singularMessage = "%1\$d shelf entry added.",
            pluralMessage = "%1\$d shelf entries added."
        )
    }

    fun replaceShelfBooks(
        state: SharedReaderScreenState,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        shelfId: String,
        bookIds: Iterable<String>,
        nowMillis: Long = currentTimestamp()
    ): SharedLibraryMutationResult? {
        val cleanShelfId = shelfId.trim()
        if (!canMutateShelf(cleanShelfId)) return null
        val selectedBooks = cleanBookIds(bookIds)
        val shelfName = state.shelves.firstOrNull { it.id == cleanShelfId }?.name
            ?: shelfRecords.firstOrNull { it.id == cleanShelfId }?.name
            ?: cleanShelfId
        return SharedLibraryMutationResult(
            state = state.copy(
                bannerMessage = BannerMessage.quantity(
                    "banner_shelf_books_updated",
                    selectedBooks.size,
                    "Updated \"%1\$s\" with %2\$d book.",
                    "Updated \"%1\$s\" with %2\$d books.",
                    shelfName,
                    selectedBooks.size
                )
            ),
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs.filterNot { it.shelfId == cleanShelfId } +
                selectedBooks.map { bookId ->
                    BookShelfRef(bookId = bookId, shelfId = cleanShelfId, addedAt = nowMillis)
                }
        )
    }

    fun tagSelectedBooks(
        state: SharedReaderScreenState,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        tagName: String,
        nowMillis: Long = currentTimestamp()
    ): SharedLibraryMutationResult? {
        val selected = cleanBookIds(state.selectedBookIds)
        val trimmed = cleanTagName(tagName) ?: return null
        if (selected.isEmpty()) return null
        val existingTag = state.allTags.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        val tag = existingTag ?: Tag(
            id = trimmed.toStableTagId("tag_$nowMillis"),
            name = trimmed,
            color = 0xFF64B5F6.toInt()
        )
        val allTags = (state.allTags + tag).distinctBy { it.id }.sortedBy { it.name.lowercase() }
        val books = state.rawLibraryBooks.map { book ->
            if (book.id in selected && book.tags.none { it.id == tag.id }) {
                book.copy(tags = (book.tags + tag).sortedBy { it.name.lowercase() })
            } else {
                book
            }
        }
        return SharedLibraryMutationResult(
            state = state.copy(
                rawLibraryBooks = books,
                allTags = allTags,
                selectedBookIds = emptySet(),
                bannerMessage = BannerMessage.quantity(
                    "banner_books_tagged_with_tag",
                    selected.size,
                    "%1\$d book tagged with \"%2\$s\".",
                    "%1\$d books tagged with \"%2\$s\".",
                    selected.size,
                    tag.name
                )
            ),
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs
        )
    }

    fun updateBookMetadata(
        state: SharedReaderScreenState,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        updated: BookItem,
        nowMillis: Long = currentTimestamp()
    ): SharedLibraryMutationResult {
        return SharedLibraryMutationResult(
            state = state.copy(
                rawLibraryBooks = state.rawLibraryBooks.map { if (it.id == updated.id) updated.copy(timestamp = nowMillis) else it },
                allTags = (state.allTags + updated.tags).distinctBy { it.id }.sortedBy { it.name.lowercase() },
                bannerMessage = BannerMessage.string(
                    "banner_book_updated",
                    "Updated \"%1\$s\".",
                    updated.cardTitle()
                )
            ),
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs
        )
    }

    private fun addBooksToShelves(
        state: SharedReaderScreenState,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        bookIds: Iterable<String>,
        shelfIds: Iterable<String>,
        clearSelection: Boolean,
        nowMillis: Long,
        bannerName: String,
        singularMessage: String,
        pluralMessage: String
    ): SharedLibraryMutationResult? {
        val selectedBooks = cleanBookIds(bookIds)
        val targetShelfIds = shelfIds
            .map { it.trim() }
            .filter { canMutateShelf(it) }
            .distinct()
        if (selectedBooks.isEmpty() || targetShelfIds.isEmpty()) return null

        val existing = shelfRefs.mapTo(mutableSetOf()) { it.bookId to it.shelfId }
        val additions = targetShelfIds.flatMap { shelfId ->
            selectedBooks.mapNotNull { bookId ->
                if (!existing.add(bookId to shelfId)) {
                    null
                } else {
                    BookShelfRef(bookId = bookId, shelfId = shelfId, addedAt = nowMillis)
                }
            }
        }
        return SharedLibraryMutationResult(
            state = state.copy(
                selectedBookIds = if (clearSelection) emptySet() else state.selectedBookIds,
                bannerMessage = BannerMessage.quantity(
                    bannerName,
                    additions.size,
                    singularMessage,
                    pluralMessage,
                    additions.size
                )
            ),
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs + additions
        )
    }
}

fun parseTagList(input: String, knownTags: List<Tag>, nowMillis: Long = currentTimestamp()): List<Tag> {
    return input.split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .mapIndexed { index, name ->
            knownTags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: Tag(
                    id = name.toStableTagId("tag_${nowMillis + index}"),
                    name = name,
                    color = 0xFF64B5F6.toInt()
                )
        }
}

private fun String.toStableTagId(fallback: String): String {
    return lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { fallback }
}
