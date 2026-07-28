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

    fun removeBooksFromShelf(
        state: SharedReaderScreenState,
        shelfId: String,
        bookIds: Iterable<String>
    ): SharedReaderScreenState? {
        val cleanShelfId = shelfId.trim()
        if (!canMutateShelf(cleanShelfId)) return null
        val shelf = state.shelves.firstOrNull { it.id == cleanShelfId && it.type == ShelfType.MANUAL }
            ?: return null
        val selectedBooks = cleanBookIds(bookIds)
        if (selectedBooks.isEmpty()) return null
        val existingIds = shelf.books.mapTo(mutableSetOf()) { it.id }
        val removedIds = selectedBooks.intersect(existingIds)
        if (removedIds.isEmpty()) return null
        return state.copy(
            shelves = state.shelves.map { current ->
                if (current.id == cleanShelfId) {
                    current.copy(
                        books = current.books.filterNot { it.id in removedIds },
                        directBooks = current.directBooks.filterNot { it.id in removedIds },
                        directBookAddedAt = current.directBookAddedAt - removedIds,
                    )
                } else {
                    current
                }
            },
            selectedBookIds = emptySet(),
            bannerMessage = BannerMessage.quantity(
                "banner_books_removed_from_shelf",
                removedIds.size,
                "Removed %1\$d book from \"%2\$s\".",
                "Removed %1\$d books from \"%2\$s\".",
                removedIds.size,
                shelf.name,
            ),
        )
    }

    fun addBooksToShelvesInState(
        state: SharedReaderScreenState,
        bookIds: Iterable<String>,
        shelfIds: Iterable<String>,
        nowMillis: Long = currentTimestamp(),
    ): SharedReaderScreenState? {
        val selectedBooks = cleanBookIds(bookIds)
        if (selectedBooks.isEmpty()) return null
        val requestedShelves = shelfIds.mapTo(mutableSetOf()) { it.trim() }
        val mutableShelfIds = state.shelves
            .filter { it.type == ShelfType.MANUAL && canMutateShelf(it.id) && it.id in requestedShelves }
            .mapTo(mutableSetOf()) { it.id }
        if (mutableShelfIds.isEmpty()) return null
        val booksById = state.rawLibraryBooks.associateBy { it.id }
        var addedEntries = 0
        val nextShelves = state.shelves.map { shelf ->
            if (shelf.id !in mutableShelfIds) return@map shelf
            val existingIds = shelf.books.mapTo(mutableSetOf()) { it.id }
            val additions = selectedBooks
                .asSequence()
                .filterNot(existingIds::contains)
                .mapNotNull(booksById::get)
                .toList()
            addedEntries += additions.size
            shelf.copy(
                books = shelf.books + additions,
                directBooks = shelf.directBooks + additions.filterNot { addition ->
                    shelf.directBooks.any { it.id == addition.id }
                },
                directBookAddedAt = shelf.directBookAddedAt + additions.associate { it.id to nowMillis },
            )
        }
        return state.copy(
            shelves = nextShelves,
            selectedBookIds = emptySet(),
            bannerMessage = BannerMessage.quantity(
                "banner_books_added_to_shelves",
                addedEntries,
                "%1\$d shelf entry added.",
                "%1\$d shelf entries added.",
                addedEntries,
            ),
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

    fun renameShelfInState(
        state: SharedReaderScreenState,
        shelfId: String,
        name: String,
    ): SharedReaderScreenState? {
        val target = state.shelves.firstOrNull {
            it.id == shelfId && it.type == ShelfType.MANUAL && canMutateShelf(it.id)
        } ?: return null
        val trimmed = cleanShelfName(name) ?: return null
        return state.copy(
            shelves = state.shelves.map { shelf ->
                if (shelf.id == target.id) shelf.copy(name = trimmed) else shelf
            },
            selectedShelfIds = emptySet(),
            bannerMessage = BannerMessage.string(
                "banner_shelf_renamed",
                "Renamed shelf to \"%1\$s\".",
                trimmed,
            ),
        )
    }

    fun deleteShelvesInState(
        state: SharedReaderScreenState,
        shelfIds: Iterable<String>,
    ): SharedReaderScreenState? {
        val requestedIds = shelfIds.map(String::trim).filter(String::isNotBlank).toSet()
        val removableShelves = state.shelves.filter {
            it.id in requestedIds && it.type == ShelfType.MANUAL && canMutateShelf(it.id)
        }
        if (removableShelves.isEmpty()) return null
        val removableIds = removableShelves.mapTo(mutableSetOf()) { it.id }
        return state.copy(
            shelves = state.shelves.filterNot { it.id in removableIds },
            selectedShelfIds = emptySet(),
            viewingShelfId = state.viewingShelfId?.takeUnless { it in removableIds },
            bannerMessage = if (removableShelves.size == 1) {
                BannerMessage.string(
                    "banner_shelf_deleted",
                    "Deleted shelf \"%1\$s\".",
                    removableShelves.single().name,
                )
            } else {
                BannerMessage.string(
                    "banner_shelves_deleted",
                    "Deleted %1\$d shelves.",
                    removableShelves.size,
                )
            },
        )
    }

    fun deleteTag(
        state: SharedReaderScreenState,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        tagId: String
    ): SharedLibraryMutationResult? {
        val cleanTagId = tagId.trim().takeIf { it.isNotBlank() } ?: return null
        val tag = state.allTags.firstOrNull { it.id == cleanTagId }
            ?: state.rawLibraryBooks.asSequence()
                .flatMap { it.tags.asSequence() }
                .firstOrNull { it.id == cleanTagId }
            ?: return null
        return SharedLibraryMutationResult(
            state = state.copy(
                rawLibraryBooks = state.rawLibraryBooks.map { book ->
                    book.copy(tags = book.tags.filterNot { it.id == cleanTagId })
                },
                allTags = state.allTags.filterNot { it.id == cleanTagId },
                libraryFilters = state.libraryFilters.copy(
                    tagIds = state.libraryFilters.tagIds - cleanTagId
                ),
                bannerMessage = BannerMessage.string(
                    "banner_tag_deleted",
                    "Deleted tag \"%1\$s\".",
                    tag.name
                )
            ),
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs
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

    fun removeBooksFromRecentsInState(
        state: SharedReaderScreenState,
        bookIds: Iterable<String>,
        nowMillis: Long = currentTimestamp(),
    ): SharedReaderScreenState? {
        val cleanedBookIds = cleanBookIds(bookIds)
        if (cleanedBookIds.isEmpty()) return null
        val existingIds = state.rawLibraryBooks
            .asSequence()
            .filter { it.id in cleanedBookIds && it.isRecent }
            .mapTo(mutableSetOf()) { it.id }
        if (existingIds.isEmpty()) return null

        fun List<BookItem>.updated(): List<BookItem> = map { book ->
            if (book.id in existingIds) {
                book.copy(
                    isRecent = false,
                    metadataModifiedTimestamp = maxOf(book.metadataModifiedTimestamp, nowMillis),
                )
            } else {
                book
            }
        }

        return state.copy(
            rawLibraryBooks = state.rawLibraryBooks.updated(),
            libraryBooks = state.libraryBooks.updated(),
            recentBooks = state.recentBooks.filterNot { it.id in existingIds },
            openTabs = state.openTabs.updated(),
            shelves = state.shelves.map { shelf ->
                shelf.copy(
                    books = shelf.books.updated(),
                    directBooks = shelf.directBooks.updated(),
                )
            },
            selectedBookIds = emptySet(),
        )
    }

    fun toggleSelectedPinsInState(
        state: SharedReaderScreenState,
        bookIds: Iterable<String>,
        isHome: Boolean,
    ): SharedReaderScreenState? {
        val selectedIds = cleanBookIds(bookIds)
        if (selectedIds.isEmpty()) return null
        val currentPins = if (isHome) state.pinnedHomeBookIds else state.pinnedLibraryBookIds
        val nextPins = if (selectedIds.all { it in currentPins }) {
            currentPins - selectedIds
        } else {
            currentPins + selectedIds
        }
        return if (isHome) {
            state.copy(
                pinnedHomeBookIds = nextPins,
                selectedBookIds = emptySet(),
            )
        } else {
            state.copy(
                pinnedLibraryBookIds = nextPins,
                selectedBookIds = emptySet(),
            )
        }
    }

    fun toggleVisibleBookSelectionInState(
        state: SharedReaderScreenState,
        visibleBookIds: Iterable<String>,
    ): SharedReaderScreenState {
        val visibleIds = cleanBookIds(visibleBookIds)
        val nextSelection = if (
            visibleIds.isNotEmpty() && state.selectedBookIds.containsAll(visibleIds)
        ) {
            emptySet()
        } else {
            visibleIds
        }
        return state.copy(selectedBookIds = nextSelection)
    }

    fun createAndAssignTagInState(
        state: SharedReaderScreenState,
        name: String,
        bookIds: Iterable<String>,
        nowMillis: Long = currentTimestamp(),
    ): SharedReaderScreenState? {
        val selectedIds = cleanBookIds(bookIds)
        val trimmed = cleanTagName(name) ?: return null
        if (selectedIds.isEmpty()) return null
        val tag = state.allTags.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            ?: Tag(
                id = trimmed.toStableTagId("tag_$nowMillis"),
                name = trimmed,
                color = 0xFF64B5F6.toInt(),
            )
        return setTagAssignmentInState(
            state = state.copy(
                allTags = (state.allTags + tag).distinctBy { it.id }.sortedBy { it.name.lowercase() }
            ),
            tag = tag,
            bookIds = selectedIds,
            assign = true,
            nowMillis = nowMillis,
        )
    }

    fun toggleTagForBooksInState(
        state: SharedReaderScreenState,
        tagId: String,
        bookIds: Iterable<String>,
        assign: Boolean,
        nowMillis: Long = currentTimestamp(),
    ): SharedReaderScreenState? {
        val cleanTagId = tagId.trim().takeIf(String::isNotBlank) ?: return null
        val tag = state.allTags.firstOrNull { it.id == cleanTagId }
            ?: state.rawLibraryBooks.asSequence()
                .flatMap { it.tags.asSequence() }
                .firstOrNull { it.id == cleanTagId }
            ?: return null
        val selectedIds = cleanBookIds(bookIds)
        if (selectedIds.isEmpty()) return null
        return setTagAssignmentInState(state, tag, selectedIds, assign, nowMillis)
    }

    fun deleteTagInState(
        state: SharedReaderScreenState,
        tagId: String,
        nowMillis: Long = currentTimestamp(),
    ): SharedReaderScreenState? {
        val cleanTagId = tagId.trim().takeIf(String::isNotBlank) ?: return null
        val tag = state.allTags.firstOrNull { it.id == cleanTagId }
            ?: state.rawLibraryBooks.asSequence()
                .flatMap { it.tags.asSequence() }
                .firstOrNull { it.id == cleanTagId }
            ?: return null
        val affectedIds = state.rawLibraryBooks
            .filter { book -> book.tags.any { it.id == cleanTagId } }
            .mapTo(mutableSetOf()) { it.id }
        fun List<BookItem>.updated(): List<BookItem> = map { book ->
            if (book.id in affectedIds) {
                book.copy(
                    tags = book.tags.filterNot { it.id == cleanTagId },
                    metadataModifiedTimestamp = maxOf(book.metadataModifiedTimestamp, nowMillis),
                )
            } else {
                book
            }
        }
        return state.copy(
            rawLibraryBooks = state.rawLibraryBooks.updated(),
            libraryBooks = state.libraryBooks.updated(),
            recentBooks = state.recentBooks.updated(),
            openTabs = state.openTabs.updated(),
            shelves = state.shelves
                .filterNot { it.type == ShelfType.TAG && it.id == "tag_$cleanTagId" }
                .map { shelf ->
                    shelf.copy(
                        books = shelf.books.updated(),
                        directBooks = shelf.directBooks.updated(),
                    )
                },
            allTags = state.allTags.filterNot { it.id == cleanTagId },
            libraryFilters = state.libraryFilters.copy(tagIds = state.libraryFilters.tagIds - cleanTagId),
            bannerMessage = BannerMessage.string(
                "banner_tag_deleted",
                "Deleted tag \"%1\$s\".",
                tag.name,
            ),
        )
    }

    private fun setTagAssignmentInState(
        state: SharedReaderScreenState,
        tag: Tag,
        bookIds: Set<String>,
        assign: Boolean,
        nowMillis: Long,
    ): SharedReaderScreenState {
        fun BookItem.updated(): BookItem {
            if (id !in bookIds) return this
            val nextTags = if (assign) {
                (tags + tag).distinctBy { it.id }.sortedBy { it.name.lowercase() }
            } else {
                tags.filterNot { it.id == tag.id }
            }
            return if (nextTags == tags) this else copy(
                tags = nextTags,
                metadataModifiedTimestamp = maxOf(metadataModifiedTimestamp, nowMillis),
            )
        }
        fun List<BookItem>.updated(): List<BookItem> = map { it.updated() }
        return state.copy(
            rawLibraryBooks = state.rawLibraryBooks.updated(),
            libraryBooks = state.libraryBooks.updated(),
            recentBooks = state.recentBooks.updated(),
            openTabs = state.openTabs.updated(),
            shelves = state.shelves.map { shelf ->
                shelf.copy(
                    books = shelf.books.updated(),
                    directBooks = shelf.directBooks.updated(),
                )
            },
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
