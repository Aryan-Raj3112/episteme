package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.AppAction as SharedAppAction
import com.aryan.reader.shared.LibraryAction as SharedLibraryAction
import com.aryan.reader.shared.SharedFolderPathResolver
import com.aryan.reader.shared.SharedLibraryProjectionInput
import com.aryan.reader.shared.SharedLibraryStateProjector
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.reduce

internal object AndroidSharedStateBridge {
    fun prepareLibraryProjection(
        input: LibraryProjectionInput,
        folderPathResolver: FolderPathResolver
    ): AndroidSharedLibraryProjectionContext {
        val taggedBooks = input.recentFilesFromDb.withResolvedTags(input.dbTags, input.tagRefs)
        val androidBooksById = taggedBooks
            .filterNot { it.bookId.endsWith("_reflow") }
            .associateBy { it.bookId }
        val projectionState = input.state.withAndroidFolderFallbacks(androidBooksById.values)
        val sharedInput = SharedLibraryProjectionInput(
            state = projectionState.toSharedReaderScreenState(
                rawBooks = taggedBooks,
                dbTags = input.dbTags,
                includeReaderAnnotations = false
            ),
            booksFromStore = taggedBooks
                .filterNot { it.bookId.endsWith("_reflow") }
                .map { it.toSharedProjectionBookItem() },
            shelfRecords = input.dbShelves.map { it.toSharedShelfRecord() },
            shelfRefs = input.shelfRefs.map { it.toSharedBookShelfRef() },
            tags = input.dbTags.map { it.toSharedTag() }
        )
        return AndroidSharedLibraryProjectionContext(
            projectionState = projectionState,
            sharedInput = sharedInput,
            androidBooksById = androidBooksById,
            tagEntitiesById = input.dbTags.associateBy { it.id },
            folderKeys = projectionState.syncedFolders.map { AndroidSharedFolderProjectionKey(it.uriString, it.name) },
            folderPathResolver = SharedFolderPathResolver { item ->
                androidBooksById[item.id]?.let(folderPathResolver::relativeFolderSegments).orEmpty()
            }
        )
    }

    fun projectLibrary(context: AndroidSharedLibraryProjectionContext): SharedReaderScreenState {
        return SharedLibraryStateProjector(context.folderPathResolver).project(context.sharedInput)
    }

    fun toAndroidState(
        base: ReaderScreenState,
        sharedState: SharedReaderScreenState,
        androidBooksById: Map<String, RecentFileItem>,
        tagEntitiesById: Map<String, TagEntity>
    ): ReaderScreenState {
        return sharedState.toAndroidReaderScreenState(
            base = base,
            androidBooksById = androidBooksById,
            tagEntitiesById = tagEntitiesById
        )
    }

    fun reduceLibraryAction(
        current: ReaderScreenState,
        projectedState: ReaderScreenState,
        action: SharedLibraryAction
    ): ReaderScreenState {
        val rawBooks = projectedState.rawLibraryFiles.ifEmpty { current.rawLibraryFiles }
        val androidBooksById = (rawBooks + current.contextualActionItems).associateBy { it.bookId }
        val reduced = current.toBridgeSharedState(projectedState).reduce(action)
        return current.copy(
            searchQuery = reduced.searchQuery,
            sortOrder = reduced.sortOrder,
            libraryFilters = reduced.libraryFilters,
            contextualActionItems = reduced.selectedBookIds.mapNotNullTo(mutableSetOf()) { androidBooksById[it] },
            contextualActionShelfIds = reduced.selectedShelfIds,
            libraryScreenStartPage = reduced.libraryScreenStartPage,
            recentFilesLimit = reduced.recentFilesLimit
        )
    }

    fun reduceAppAction(
        current: ReaderScreenState,
        projectedState: ReaderScreenState,
        action: SharedAppAction
    ): ReaderScreenState {
        val reduced = current.toBridgeSharedState(projectedState).reduce(action)
        return current.copy(
            appThemeMode = reduced.appThemeMode,
            appContrastOption = reduced.appContrastOption,
            appTextDimFactorLight = reduced.appTextDimFactorLight,
            appTextDimFactorDark = reduced.appTextDimFactorDark,
            appSeedColor = reduced.appSeedColor,
            appFontPreference = reduced.appFontPreference,
            customAppThemes = reduced.customAppThemes
        )
    }

    fun setTabsEnabled(
        current: ReaderScreenState,
        projectedState: ReaderScreenState,
        enabled: Boolean
    ): ReaderScreenState {
        val reduced = current.toBridgeSharedState(projectedState).reduce(SharedAppAction.TabsEnabledChanged(enabled))
        if (enabled) return current.withTabStateFrom(reduced)

        val activeTab = current.activeTabBookId
        return current.copy(
            isTabsEnabled = reduced.isTabsEnabled,
            openTabIds = if (activeTab == null) emptyList() else listOf(activeTab),
            activeTabBookId = activeTab
        )
    }

    fun openBookTab(
        current: ReaderScreenState,
        projectedState: ReaderScreenState,
        bookId: String
    ): ReaderScreenState {
        val reduced = current.toBridgeSharedState(projectedState).reduce(SharedAppAction.BookTabOpened(bookId))
        return current.withTabStateFrom(reduced)
    }

    fun closeBookTab(
        current: ReaderScreenState,
        projectedState: ReaderScreenState,
        bookId: String
    ): ReaderScreenState {
        val reduced = current.toBridgeSharedState(projectedState).reduce(SharedAppAction.BookTabClosed(bookId))
        return current.withTabStateFrom(reduced)
    }

    fun closeAllTabs(current: ReaderScreenState, projectedState: ReaderScreenState): ReaderScreenState {
        val reduced = current.toBridgeSharedState(projectedState).reduce(SharedAppAction.AllTabsClosed)
        return current.withTabStateFrom(reduced)
    }

    fun togglePinsForSelectedBooks(
        current: ReaderScreenState,
        projectedState: ReaderScreenState,
        isHome: Boolean
    ): ReaderScreenState {
        val selectedIds = current.contextualActionItems.mapTo(linkedSetOf()) { it.bookId }
        if (selectedIds.isEmpty()) return current

        val currentPins = if (isHome) current.pinnedHomeBookIds else current.pinnedLibraryBookIds
        val idsToToggle = if (selectedIds.all { it in currentPins }) {
            selectedIds
        } else {
            selectedIds - currentPins
        }
        val reduced = idsToToggle.fold(current.toBridgeSharedState(projectedState)) { state, bookId ->
            state.reduce(
                if (isHome) {
                    SharedAppAction.HomePinToggled(bookId)
                } else {
                    SharedAppAction.LibraryPinToggled(bookId)
                }
            )
        }

        return if (isHome) {
            current.copy(
                pinnedHomeBookIds = reduced.pinnedHomeBookIds,
                contextualActionItems = emptySet()
            )
        } else {
            current.copy(
                pinnedLibraryBookIds = reduced.pinnedLibraryBookIds,
                contextualActionItems = emptySet()
            )
        }
    }

    fun replaceBookSelectionWithVisibleBooks(
        current: ReaderScreenState,
        projectedState: ReaderScreenState,
        visibleBooks: Collection<RecentFileItem>
    ): ReaderScreenState {
        val visibleIds = visibleBooks.mapTo(linkedSetOf()) { it.bookId }
        val selectedIds = current.contextualActionItems.mapTo(linkedSetOf()) { it.bookId }
        val action = if (visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds)) {
            SharedLibraryAction.SelectionCleared
        } else {
            SharedLibraryAction.BookSelectionReplaced(visibleIds)
        }
        return reduceLibraryAction(
            current = current,
            projectedState = projectedState,
            action = action
        )
    }

    private fun ReaderScreenState.toBridgeSharedState(projectedState: ReaderScreenState): SharedReaderScreenState {
        return toSharedReaderScreenState(
            rawBooks = projectedState.rawLibraryFiles.ifEmpty { rawLibraryFiles },
            dbTags = projectedState.allTags.ifEmpty { allTags },
            includeReaderAnnotations = false
        )
    }

    private fun ReaderScreenState.withTabStateFrom(sharedState: SharedReaderScreenState): ReaderScreenState {
        return copy(
            isTabsEnabled = sharedState.isTabsEnabled,
            openTabIds = sharedState.openTabIds,
            activeTabBookId = sharedState.activeTabBookId
        )
    }
}

internal data class AndroidSharedLibraryProjectionContext(
    val projectionState: ReaderScreenState,
    val sharedInput: SharedLibraryProjectionInput,
    val androidBooksById: Map<String, RecentFileItem>,
    val tagEntitiesById: Map<String, TagEntity>,
    val folderKeys: List<AndroidSharedFolderProjectionKey>,
    val folderPathResolver: SharedFolderPathResolver
)

internal data class AndroidSharedFolderProjectionKey(
    val uriString: String,
    val name: String
)

private fun ReaderScreenState.withAndroidFolderFallbacks(books: Collection<RecentFileItem>): ReaderScreenState {
    val knownFolders = syncedFolders.mapTo(mutableSetOf()) { it.uriString }
    val missingFolders = books
        .mapNotNull { it.sourceFolderUri }
        .filterTo(linkedSetOf()) { it !in knownFolders }
        .map { uri -> SyncedFolder(uriString = uri, name = "Local Folder", lastScanTime = 0L) }
    return if (missingFolders.isEmpty()) {
        this
    } else {
        copy(syncedFolders = syncedFolders + missingFolders)
    }
}
