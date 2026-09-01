package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.IN_APP_STORAGE_SOURCE
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFeaturePolicy
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SortOrder
import com.aryan.reader.shared.SyncedFolder
import com.aryan.reader.shared.Tag
import com.aryan.reader.shared.matchesSourceFolders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NonReaderLayoutModelsTest {

    @Test
    fun `unified library model only exposes continue reading on unfiltered home`() {
        val book = book(id = "reading", progress = 42f)

        assertTrue(
            mobileUnifiedLibraryModel(
                viewState = MobileUnifiedLibraryViewState(
                    filter = MobileUnifiedLibraryFilter.ALL,
                    query = "",
                    searchActive = false,
                ),
                visibleBooks = listOf(book),
                continueReading = book,
            ).showContinueReading,
        )
        assertFalse(
            mobileUnifiedLibraryModel(
                viewState = MobileUnifiedLibraryViewState(
                    filter = MobileUnifiedLibraryFilter.READING,
                    query = "",
                    searchActive = false,
                ),
                visibleBooks = listOf(book),
                continueReading = book,
            ).showContinueReading,
        )
        assertFalse(
            mobileUnifiedLibraryModel(
                viewState = MobileUnifiedLibraryViewState(
                    filter = MobileUnifiedLibraryFilter.ALL,
                    query = "reading",
                    searchActive = true,
                ),
                visibleBooks = listOf(book),
                continueReading = book,
            ).showContinueReading,
        )
    }

    @Test
    fun `unified library model marks active home search as results mode`() {
        val model = mobileUnifiedLibraryModel(
            viewState = MobileUnifiedLibraryViewState(
                query = "missing",
                searchActive = true,
            ),
            visibleBooks = emptyList(),
            continueReading = null,
        )

        assertTrue(model.showSearchResults)
        assertFalse(model.showContinueReading)
    }

    @Test
    fun `unified library filtering matches Android progress and text rules`() {
        val unread = book(id = "unread", displayName = "Alpha.epub", progress = 0f)
        val reading = book(id = "reading", displayName = "Beta.pdf", progress = 42f, author = "Ada")
        val finished = book(id = "finished", displayName = "Gamma.epub", progress = 100f)
        val books = listOf(unread, reading, finished)

        assertEquals(listOf(reading), mobileUnifiedLibraryBooks(books, MobileUnifiedLibraryFilter.READING, ""))
        assertEquals(listOf(finished), mobileUnifiedLibraryBooks(books, MobileUnifiedLibraryFilter.FINISHED, ""))
        assertEquals(listOf(unread), mobileUnifiedLibraryBooks(books, MobileUnifiedLibraryFilter.UNREAD, ""))
        assertEquals(listOf(reading), mobileUnifiedLibraryBooks(books, MobileUnifiedLibraryFilter.ALL, "ada"))
    }

    @Test
    fun `unified library applies persisted filters and sort order`() {
        val folder = SyncedFolder("folder://downloads", "Downloads", lastScanTime = 0L)
        val beta = book("beta", title = "Beta", sourceFolder = "Downloads")
        val alpha = book("alpha", title = "Alpha", sourceFolder = "Downloads")
        val outsideFolder = book("outside", title = "Outside")

        val visible = mobileUnifiedLibraryBooks(
            books = listOf(beta, outsideFolder, alpha),
            filter = MobileUnifiedLibraryFilter.ALL,
            query = "",
            libraryFilters = LibraryFilters(sourceFolders = setOf(folder.uriString))
                .withIosFolderFilterIdentities(listOf(folder)),
            sortOrder = SortOrder.TITLE_ASC,
        )

        assertEquals(listOf("alpha", "beta"), visible.map { it.id })
    }

    @Test
    fun `unified continue reading prefers most recently positioned active book`() {
        val older = book(id = "older", displayName = "Older.epub", progress = 20f, positionModified = 20L)
        val newer = book(id = "newer", displayName = "Newer.epub", progress = 80f, positionModified = 40L)
        val finished = book(id = "finished", displayName = "Finished.epub", progress = 100f, positionModified = 80L)

        assertEquals(newer, mobileUnifiedContinueReadingBook(listOf(older, newer, finished)))
    }

    @Test
    fun `book taps toggle selection instead of opening while contextual mode is active`() {
        assertEquals(SharedMobileBookTapIntent.OPEN, mobileBookTapIntent(emptySet()))
        assertEquals(
            SharedMobileBookTapIntent.TOGGLE_SELECTION,
            mobileBookTapIntent(setOf("selected-book")),
        )
    }

    @Test
    fun `book long press adds selection but never removes an existing selection`() {
        assertTrue(shouldSelectBookOnLongPress("book", emptySet()))
        assertFalse(shouldSelectBookOnLongPress("book", setOf("book")))
        assertTrue(shouldSelectBookOnLongPress("other", setOf("book")))
    }

    @Test
    fun `mobile book status badges follow Android order and sources`() {
        assertEquals(
            listOf(
                SharedMobileBookStatusBadge.FOLDER,
                SharedMobileBookStatusBadge.CATALOG,
                SharedMobileBookStatusBadge.PINNED,
            ),
            mobileBookStatusBadges(
                book("catalog", sourceFolder = "Downloads", path = "opds-pse://catalog/book"),
                pinned = true,
            ),
        )
        assertTrue(mobileBookStatusBadges(book("plain"), pinned = false).isEmpty())
    }

    @Test
    fun `mobile library distinguishes empty search results from an empty library`() {
        assertEquals(
            SharedMobileLibraryBooksState.CONTENT,
            mobileLibraryBooksState(visibleBookCount = 1, searchQuery = "missing"),
        )
        assertEquals(
            SharedMobileLibraryBooksState.SEARCH_NO_RESULTS,
            mobileLibraryBooksState(visibleBookCount = 0, searchQuery = "  missing  "),
        )
        assertEquals(
            SharedMobileLibraryBooksState.EMPTY_LIBRARY,
            mobileLibraryBooksState(visibleBookCount = 0, searchQuery = "  "),
        )
    }

    @Test
    fun `ios folder filters normalize legacy uri selections to scanned book identities`() {
        val folder = SyncedFolder("ios-local-folder://downloads", "Downloads", lastScanTime = 0L)

        assertEquals(
            setOf("Downloads", IN_APP_STORAGE_SOURCE),
            LibraryFilters(sourceFolders = setOf(folder.uriString, IN_APP_STORAGE_SOURCE))
                .withIosFolderFilterIdentities(listOf(folder))
                .sourceFolders,
        )
        val selected = LibraryFilters().toggleIosFolderFilter(folder)
        assertEquals(setOf("Downloads"), selected.sourceFolders)
        assertTrue(book("folder-book", sourceFolder = "Downloads").matchesSourceFolders(selected.sourceFolders))
        assertTrue(selected.toggleIosFolderFilter(folder).sourceFolders.isEmpty())
    }

    @Test
    fun `removing an ios folder clears both uri and name filter identities`() {
        val folder = SyncedFolder("ios-local-folder://downloads", "Downloads", lastScanTime = 0L)

        assertEquals(
            setOf(IN_APP_STORAGE_SOURCE, "Other"),
            LibraryFilters(
                sourceFolders = setOf(
                    folder.uriString,
                    folder.name,
                    IN_APP_STORAGE_SOURCE,
                    "Other",
                )
            ).withoutIosFolderFilter(folder).sourceFolders,
        )
    }

    @Test
    fun `mobile book info presents sources without exposing container paths`() {
        assertEquals(
            "Source: OPDS Stream",
            mobileBookInfoDisplayLocation(
                book("stream", path = "opds-pse://catalog/book"),
                opdsLabel = "Source: OPDS Stream",
                inAppLabel = "In-App Storage",
            ),
        )
        assertEquals(
            "In-App Storage",
            mobileBookInfoDisplayLocation(
                book("import", path = "/private/container/Application Support/Imports/book.epub"),
                opdsLabel = "Source: OPDS Stream",
                inAppLabel = "In-App Storage",
            ),
        )
        assertEquals(
            "Downloads/folder-book.epub",
            mobileBookInfoDisplayLocation(
                book("folder-book", sourceFolder = "Downloads", path = "/private/container/book.epub"),
                opdsLabel = "Source: OPDS Stream",
                inAppLabel = "In-App Storage",
            ),
        )
    }

    @Test
    fun `ios library projection filters raw folder books and keeps pinned books first like Android`() {
        val folder = SyncedFolder("ios-local-folder://downloads", "Downloads", lastScanTime = 0L)
        val alpha = book("alpha", title = "Alpha", sourceFolder = "Downloads")
        val beta = book("beta", title = "Beta", sourceFolder = "Downloads")
        val unrelated = book("other", title = "Other")
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(beta, unrelated, alpha),
            libraryFilters = LibraryFilters(sourceFolders = setOf(folder.uriString)),
            syncedFolders = listOf(folder),
            sortOrder = SortOrder.TITLE_ASC,
            pinnedLibraryBookIds = setOf("beta"),
        )

        assertEquals(listOf("beta", "alpha"), state.visibleIosLibraryBooks().map { it.id })
        assertTrue(
            state.copy(searchQuery = "Downloads").visibleIosLibraryBooks().isEmpty(),
            "Android searches book metadata and tags, not the source-folder label",
        )
    }

    @Test
    fun `mobile shelves landing matches Android root shelf projection`() {
        val manual = shelf("manual", ShelfType.MANUAL)
        val rootFolder = shelf("root", ShelfType.FOLDER)
        val nestedFolder = shelf("nested", ShelfType.FOLDER, parentShelfId = rootFolder.id)
        val tag = shelf("tag", ShelfType.TAG)

        assertEquals(
            listOf("manual", "root"),
            topLevelMobileShelves(listOf(manual, rootFolder, nestedFolder, tag)).map { it.id },
        )
    }

    @Test
    fun `shelf taps toggle selection instead of opening while contextual mode is active`() {
        assertEquals(SharedMobileShelfTapIntent.OPEN, mobileShelfTapIntent(emptySet()))
        assertEquals(
            SharedMobileShelfTapIntent.TOGGLE_SELECTION,
            mobileShelfTapIntent(setOf("selected-shelf")),
        )
    }

    private fun shelf(id: String, type: ShelfType, parentShelfId: String? = null) = Shelf(
        id = id,
        name = id,
        type = type,
        books = emptyList(),
        parentShelfId = parentShelfId,
    )

    @Test
    fun `shelf long press adds selection but never removes an existing selection`() {
        assertTrue(shouldSelectShelfOnLongPress("shelf", emptySet()))
        assertFalse(shouldSelectShelfOnLongPress("shelf", setOf("shelf")))
        assertTrue(shouldSelectShelfOnLongPress("other", setOf("shelf")))
    }

    @Test
    fun `add books source changes preserve the pending selection`() {
        val selection = linkedSetOf("unshelved-book", "all-books-result")

        assertEquals(selection, mobileAddBooksSelectionAfterSourceChange(selection))
    }

    @Test
    fun `android library keeps the simple top level organization tabs`() {
        val visibleTabs = visibleNonReaderLibraryTabs(ReaderPlatform.ANDROID)

        assertEquals(
            listOf(
                NonReaderLibraryTab.BOOKS,
                NonReaderLibraryTab.SHELVES,
                NonReaderLibraryTab.FOLDERS
            ),
            visibleTabs
        )
        assertFalse(NonReaderLibraryTab.SMART_SHELVES in visibleTabs)
        assertFalse(NonReaderLibraryTab.TAGS in visibleTabs)
        assertFalse(NonReaderLibraryTab.UNREAD in visibleTabs)
        assertFalse(NonReaderLibraryTab.IN_PROGRESS in visibleTabs)
        assertFalse(NonReaderLibraryTab.COMPLETED in visibleTabs)
    }

    @Test
    fun `desktop library includes organization and reading status tabs`() {
        val visibleTabs = visibleNonReaderLibraryTabs(ReaderPlatform.DESKTOP)

        assertEquals(
            listOf(
                NonReaderLibraryTab.BOOKS,
                NonReaderLibraryTab.SHELVES,
                NonReaderLibraryTab.FOLDERS,
                NonReaderLibraryTab.UNREAD,
                NonReaderLibraryTab.IN_PROGRESS,
                NonReaderLibraryTab.COMPLETED
            ),
            visibleTabs
        )
        assertFalse(NonReaderLibraryTab.SMART_SHELVES in visibleTabs)
        assertFalse(NonReaderLibraryTab.TAGS in visibleTabs)
    }

    @Test
    fun `more menu keeps dev tools out of the base shell actions`() {
        val model = sharedAppShellModel(
            selectedTab = SharedAppTab.LIBRARY,
            aiSettingsAvailable = false
        )

        assertFalse(model.toolActions.contains(SharedAppToolAction.DEV_TOOLS))
        assertFalse(model.moreSections.any { it.group == SharedAppMoreGroup.DEV_TOOLS })
    }

    @Test
    fun `more menu exposes dev tools in its own section when action is provided`() {
        val sections = sharedAppMoreSections(
            listOf(
                SharedAppToolAction.SETTINGS,
                SharedAppToolAction.DEV_TOOLS,
                SharedAppToolAction.ABOUT
            )
        )

        assertEquals(
            listOf(
                SharedAppMoreGroup.PREFERENCES,
                SharedAppMoreGroup.DEV_TOOLS,
                SharedAppMoreGroup.HELP
            ),
            sections.map { it.group }
        )
        assertEquals(
            listOf(SharedAppToolAction.DEV_TOOLS),
            sections.first { it.group == SharedAppMoreGroup.DEV_TOOLS }.actions
        )
    }

    @Test
    fun `desktop shelves tab exposes primary new shelf action only on desktop`() {
        assertEquals(
            listOf(NonReaderLibraryPrimaryAction.NEW_SHELF),
            primaryLibraryActionsForTab(NonReaderLibraryTab.SHELVES, ReaderPlatform.DESKTOP)
        )
        assertEquals(
            emptyList<NonReaderLibraryPrimaryAction>(),
            primaryLibraryActionsForTab(NonReaderLibraryTab.SHELVES, ReaderPlatform.ANDROID)
        )
        assertEquals(
            emptyList<NonReaderLibraryPrimaryAction>(),
            primaryLibraryActionsForTab(NonReaderLibraryTab.BOOKS, ReaderPlatform.DESKTOP)
        )
    }

    @Test
    fun `book overflow exposes platform save and share actions`() {
        assertEquals(
            setOf(
                NonReaderBookOverflowAction.ADD_TO_SHELF,
                NonReaderBookOverflowAction.SAVE_ORIGINAL
            ),
            bookOverflowActionsForPlatform(ReaderPlatform.DESKTOP)
        )
        assertEquals(
            setOf(
                NonReaderBookOverflowAction.SAVE_ORIGINAL,
                NonReaderBookOverflowAction.SHARE_ORIGINAL
            ),
            bookOverflowActionsForPlatform(ReaderPlatform.ANDROID)
        )
    }

    @Test
    fun `desktop library command bar uses inline layout only on wide panes`() {
        assertEquals(
            LibraryCommandBarLayout.STACKED,
            libraryCommandBarLayoutForWidth(widthDp = 979f, platform = ReaderPlatform.DESKTOP)
        )
        assertEquals(
            LibraryCommandBarLayout.INLINE,
            libraryCommandBarLayoutForWidth(widthDp = 980f, platform = ReaderPlatform.DESKTOP)
        )
        assertEquals(
            LibraryCommandBarLayout.STACKED,
            libraryCommandBarLayoutForWidth(widthDp = 1200f, platform = ReaderPlatform.ANDROID)
        )
    }

    @Test
    fun `desktop library filter file type groups include every shared readable format`() {
        val groupedTypes = nonReaderLibraryFileTypeGroups().flatMap { it.fileTypes }

        assertEquals(
            SharedFileCapabilities.readableTypesFor(ReaderPlatform.DESKTOP),
            groupedTypes.toSet()
        )
        assertEquals(groupedTypes.size, groupedTypes.toSet().size)
        assertTrue(FileType.DOCX in groupedTypes)
        assertTrue(FileType.FODT in groupedTypes)
        assertTrue(FileType.PPTX in groupedTypes)
        assertTrue(
            nonReaderLibraryFileTypeGroups()
                .any { it.titleFallback == "Comics" && FileType.CBR in it.fileTypes && FileType.CB7 in it.fileTypes && FileType.CBT in it.fileTypes }
        )
    }

    @Test
    fun `home layout separates active tab pinned and recent books`() {
        val activeTab = book("tab", title = "Open Tab", progress = 12f).copy(timestamp = 40L)
        val inProgress = book("continue", title = "Continue", progress = 40f).copy(timestamp = 30L)
        val pinned = book("pinned", title = "Pinned").copy(timestamp = 20L)
        val recent = book("recent", title = "Recent").copy(timestamp = 10L)

        val layout = SharedReaderScreenState(
            rawLibraryBooks = listOf(activeTab, inProgress, pinned, recent),
            recentBooks = listOf(inProgress, pinned, recent),
            openTabs = listOf(activeTab),
            openTabIds = listOf(activeTab.id),
            activeTabBookId = activeTab.id,
            isTabsEnabled = true,
            pinnedHomeBookIds = setOf(pinned.id),
            selectedBookIds = setOf(recent.id)
        ).toNonReaderHomeLayoutModel()

        assertEquals(activeTab.id, layout.continueBook?.id)
        assertEquals(listOf(activeTab.id), layout.activeTabs.map { it.id })
        assertEquals(listOf(pinned.id), layout.pinnedBooks.map { it.id })
        assertEquals(listOf(inProgress.id, recent.id), layout.recentBooks.map { it.id })
        assertEquals(
            listOf(pinned.id, inProgress.id, recent.id),
            SharedReaderScreenState(
                rawLibraryBooks = listOf(activeTab.copy(isRecent = false), inProgress, pinned, recent),
                pinnedHomeBookIds = setOf(pinned.id),
            ).mobileRecentBooks().map { it.id },
        )
        assertEquals(
            listOf(pinned.id, inProgress.id),
            SharedReaderScreenState(
                rawLibraryBooks = listOf(activeTab.copy(isRecent = false), inProgress, pinned, recent),
                recentBooks = listOf(inProgress, pinned, recent),
                pinnedHomeBookIds = setOf(pinned.id),
                recentFilesLimit = 2,
            ).mobileRecentBooks().map { it.id },
        )
        assertEquals(listOf(recent.id), layout.selectedBooks.map { it.id })
        assertTrue(layout.isContextualModeActive)
        assertFalse(layout.isEmpty)
    }

    @Test
    fun `home layout ignores open tabs when tabs are disabled`() {
        val activeTab = book("tab", title = "Open Tab", progress = 12f)

        val layout = SharedReaderScreenState(
            rawLibraryBooks = listOf(activeTab),
            openTabs = listOf(activeTab),
            openTabIds = listOf(activeTab.id),
            activeTabBookId = activeTab.id,
            isTabsEnabled = false
        ).toNonReaderHomeLayoutModel()

        assertEquals(null, layout.continueBook)
        assertTrue(layout.activeTabs.isEmpty())
        assertTrue(layout.isEmpty)
        assertFalse(layout.isLibraryEmpty)
    }

    @Test
    fun `library organization counts shelves tags folders status and filters`() {
        val favorite = Tag("favorite", "Favorite")
        val unread = book("unread", type = FileType.EPUB, progress = 0f)
        val inProgress = book("progress", type = FileType.PDF, progress = 50f, tags = listOf(favorite), sourceFolder = "/sync")
        val complete = book("complete", type = FileType.CBZ, progress = 100f, path = "opds-pse://stream")

        val organization = SharedReaderScreenState(
            rawLibraryBooks = listOf(unread, inProgress, complete),
            allTags = listOf(favorite),
            syncedFolders = listOf(SyncedFolder("/sync", "Sync", lastScanTime = 1L)),
            shelves = listOf(
                Shelf("manual", "Manual", ShelfType.MANUAL, listOf(unread)),
                Shelf("series", "Series", ShelfType.SERIES, listOf(inProgress)),
                Shelf("smart", "Smart", ShelfType.SMART, listOf(complete)),
                Shelf("tag_favorite", "Favorite", ShelfType.TAG, listOf(inProgress)),
                Shelf("folder_root", "Sync", ShelfType.FOLDER, listOf(inProgress)),
                Shelf("folder_child", "Nested", ShelfType.FOLDER, listOf(inProgress), parentShelfId = "folder_root")
            ),
            libraryFilters = LibraryFilters(
                fileTypes = setOf(FileType.PDF),
                sourceFolders = setOf("/sync"),
                readStatus = ReadStatusFilter.IN_PROGRESS,
                tagIds = setOf(favorite.id)
            )
        ).toNonReaderLibraryOrganizationModel()

        assertEquals(3, organization.allBooksCount)
        assertEquals(2, organization.shelfCount)
        assertEquals(1, organization.smartShelfCount)
        assertEquals(1, organization.tagCount)
        assertEquals(1, organization.folderCount)
        assertEquals(1, organization.unreadCount)
        assertEquals(1, organization.inProgressCount)
        assertEquals(1, organization.completedCount)
        assertEquals(4, organization.activeFilterCount)
        assertEquals(listOf(FileType.PDF, FileType.EPUB, FileType.CBZ), organization.availableFileTypes)
        assertTrue(organization.hasInAppBooks)
        assertTrue(organization.hasOpdsStreams)
    }

    @Test
    fun `library organization falls back to book tags and synced folders`() {
        val favorite = Tag("favorite", "Favorite")
        val tagged = book("tagged", tags = listOf(favorite), sourceFolder = "/sync")

        val organization = SharedReaderScreenState(
            rawLibraryBooks = listOf(tagged),
            syncedFolders = listOf(SyncedFolder("/sync", "Sync", lastScanTime = 1L))
        ).toNonReaderLibraryOrganizationModel()

        assertEquals(1, organization.tagCount)
        assertEquals(1, organization.folderCount)
    }

    @Test
    fun `desktop status tabs filter books while android hidden statuses fall back`() {
        val unread = book("unread", type = FileType.EPUB, progress = 0f)
        val inProgress = book("progress", type = FileType.PDF, progress = 44f)
        val complete = book("complete", type = FileType.CBZ, progress = 100f)
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(unread, inProgress, complete),
            libraryBooks = listOf(unread, inProgress, complete)
        )

        assertEquals(
            listOf("unread"),
            state.booksForNonReaderLibraryTab(NonReaderLibraryTab.UNREAD, ReaderPlatform.DESKTOP).map { it.id }
        )
        assertEquals(
            listOf("progress"),
            state.visibleBooksForLibrarySelection(NonReaderLibraryTab.IN_PROGRESS, ReaderPlatform.DESKTOP).map { it.id }
        )
        assertEquals(
            listOf("complete"),
            state.booksForNonReaderLibraryTab(NonReaderLibraryTab.COMPLETED, ReaderPlatform.DESKTOP).map { it.id }
        )
        assertEquals(
            listOf("unread", "progress", "complete"),
            state.booksForNonReaderLibraryTab(NonReaderLibraryTab.UNREAD, ReaderPlatform.ANDROID).map { it.id }
        )
    }

    @Test
    fun `library visible selection follows folder shelf navigation`() {
        val rootBook = book("root", sourceFolder = "/sync")
        val childBook = book("child", sourceFolder = "/sync")
        val rootShelf = Shelf(
            id = "folder_/sync",
            name = "Sync",
            type = ShelfType.FOLDER,
            books = listOf(rootBook, childBook),
            directBooks = listOf(rootBook),
            childShelfIds = listOf("folder_/sync::Nested")
        )
        val childShelf = Shelf(
            id = "folder_/sync::Nested",
            name = "Nested",
            type = ShelfType.FOLDER,
            books = listOf(childBook),
            directBooks = listOf(childBook),
            parentShelfId = rootShelf.id,
            depth = 1
        )

        val rootState = SharedReaderScreenState(
            shelves = listOf(rootShelf, childShelf),
            libraryBooks = listOf(rootBook, childBook)
        )
        val childState = rootState.copy(viewingShelfId = childShelf.id)

        assertEquals(
            listOf("root", "child"),
            rootState.visibleBooksForLibrarySelection(NonReaderLibraryTab.FOLDERS).map { it.id }
        )
        assertEquals(
            listOf("child"),
            childState.visibleBooksForLibrarySelection(NonReaderLibraryTab.FOLDERS).map { it.id }
        )
    }

    @Test
    fun `library organization does not expose unknown as an available file type`() {
        val organization = SharedReaderScreenState(
            rawLibraryBooks = listOf(
                book("known", type = FileType.PDF),
                book("unknown", type = FileType.UNKNOWN)
            )
        ).toNonReaderLibraryOrganizationModel()

        assertEquals(listOf(FileType.PDF), organization.availableFileTypes)
    }

    @Test
    fun `shell model keeps account in primary navigation and exposes more actions`() {
        val model = sharedAppShellModel(
            selectedTab = SharedAppTab.CUSTOM_FONTS,
            aiSettingsAvailable = true
        )

        assertEquals(
            listOf(SharedAppTab.LIBRARY, SharedAppTab.CATALOGS, SharedAppTab.PRO),
            model.primaryTabs
        )
        assertEquals(listOf(SharedAppToolAction.AI_SETTINGS), model.primaryActions)
        assertEquals(SharedAppTab.LIBRARY, model.selectedPrimaryTab)
        assertTrue(SharedAppToolAction.SETTINGS in model.toolActions)
        assertTrue(SharedAppToolAction.APP_THEME in model.toolActions)
        assertTrue(SharedAppToolAction.AI_SETTINGS in model.toolActions)
        assertTrue(SharedAppToolAction.CUSTOM_FONTS in model.toolActions)
        assertTrue(SharedAppToolAction.HELP_FEEDBACK in model.toolActions)
        assertTrue(SharedAppToolAction.SUPPORT in model.toolActions)
        assertTrue(SharedAppToolAction.ABOUT in model.toolActions)
        assertFalse(SharedAppToolAction.IMPORT_FILES in model.toolActions)
        assertFalse(SharedAppToolAction.IMPORT_FOLDER in model.toolActions)
        assertFalse(SharedAppToolAction.SYNC in model.toolActions)
        assertFalse(SharedAppToolAction.PRO in model.toolActions)
        assertFalse(SharedAppToolAction.TABS_TOGGLE in model.toolActions)
        assertTrue(model.showPrimaryNavigation)

        assertEquals(
            listOf(
                SharedAppMoreGroup.PREFERENCES,
                SharedAppMoreGroup.HELP
            ),
            model.moreSections.map { it.group }
        )

        val accountModel = sharedAppShellModel(SharedAppTab.PRO, aiSettingsAvailable = true)
        assertEquals(SharedAppTab.PRO, accountModel.selectedPrimaryTab)

        val withoutAi = sharedAppShellModel(SharedAppTab.SHELVES, aiSettingsAvailable = false)
        assertEquals(SharedAppTab.LIBRARY, withoutAi.selectedPrimaryTab)
        assertEquals(emptyList(), withoutAi.primaryActions)
        assertFalse(SharedAppToolAction.AI_SETTINGS in withoutAi.toolActions)

        val byokModel = sharedAppShellModel(
            selectedTab = SharedAppTab.LIBRARY,
            aiSettingsAvailable = true,
            featurePolicy = SharedFeaturePolicy.OssOnline
        )
        assertEquals(emptyList(), byokModel.primaryActions)
    }

    @Test
    fun `shell model groups more menu preferences and help only`() {
        val model = sharedAppShellModel(
            selectedTab = SharedAppTab.LIBRARY,
            aiSettingsAvailable = true
        )

        assertFalse(model.moreSections.any { it.group == SharedAppMoreGroup.LIBRARY })
        assertFalse(model.moreSections.any { it.group == SharedAppMoreGroup.ACCOUNT })
        assertEquals(
            listOf(
                SharedAppToolAction.SETTINGS,
                SharedAppToolAction.APP_THEME,
                SharedAppToolAction.AI_SETTINGS,
                SharedAppToolAction.CUSTOM_FONTS
            ),
            model.moreSections.single { it.group == SharedAppMoreGroup.PREFERENCES }.actions
        )
        assertEquals(
            listOf(
                SharedAppToolAction.HELP_FEEDBACK,
                SharedAppToolAction.SUPPORT,
                SharedAppToolAction.ABOUT
            ),
            model.moreSections.single { it.group == SharedAppMoreGroup.HELP }.actions
        )

        val legacyActions = sharedAppMoreSections(
            listOf(
                SharedAppToolAction.IMPORT_FILES,
                SharedAppToolAction.PRO,
                SharedAppToolAction.SETTINGS,
                SharedAppToolAction.TABS_TOGGLE,
                SharedAppToolAction.ABOUT
            )
        )
        assertEquals(
            listOf(SharedAppMoreGroup.PREFERENCES, SharedAppMoreGroup.HELP),
            legacyActions.map { it.group }
        )
        assertEquals(
            listOf(SharedAppToolAction.SETTINGS),
            legacyActions.single { it.group == SharedAppMoreGroup.PREFERENCES }.actions
        )
    }

    @Test
    fun `shell model hides primary navigation while reading`() {
        val readerModel = sharedAppShellModel(
            selectedTab = SharedAppTab.READER,
            aiSettingsAvailable = true
        )
        val libraryModel = sharedAppShellModel(
            selectedTab = SharedAppTab.LIBRARY,
            aiSettingsAvailable = true
        )

        assertFalse(readerModel.showPrimaryNavigation)
        assertTrue(libraryModel.showPrimaryNavigation)
    }

    @Test
    fun `offline shell model hides network backed navigation and tools`() {
        val model = sharedAppShellModel(
            selectedTab = SharedAppTab.CATALOGS,
            aiSettingsAvailable = true,
            featurePolicy = SharedFeaturePolicy.OssOffline
        )

        assertEquals(listOf(SharedAppTab.LIBRARY), model.primaryTabs)
        assertEquals(emptyList(), model.primaryActions)
        assertEquals(SharedAppTab.LIBRARY, model.selectedPrimaryTab)
        assertFalse(SharedAppToolAction.AI_SETTINGS in model.toolActions)
        assertFalse(SharedAppToolAction.HELP_FEEDBACK in model.toolActions)
        assertFalse(SharedAppToolAction.SUPPORT in model.toolActions)
        assertFalse(SharedAppToolAction.PRO in model.toolActions)
        assertFalse(model.moreSections.any { it.group == SharedAppMoreGroup.ACCOUNT })
        assertFalse(model.moreSections.any { it.group == SharedAppMoreGroup.LIBRARY })
        assertFalse(model.moreSections.any { it.group == SharedAppMoreGroup.HELP && SharedAppToolAction.SUPPORT in it.actions })
        assertTrue(SharedAppToolAction.SETTINGS in model.toolActions)
        assertTrue(SharedAppToolAction.CUSTOM_FONTS in model.toolActions)
        assertTrue(SharedAppToolAction.ABOUT in model.toolActions)
        assertTrue(model.showPrimaryNavigation)
    }

    @Test
    fun `sidebar sync toggle is visible only for signed in account builds and follows pro gating`() {
        assertEquals(
            SharedSidebarSyncToggleModel(visible = false, enabled = false, checked = false),
            sharedSidebarSyncToggleModel(
                isSignedIn = false,
                accountAvailable = true,
                syncAvailable = true,
                isProUser = true,
                isSyncEnabled = true
            )
        )

        assertEquals(
            SharedSidebarSyncToggleModel(visible = true, enabled = true, checked = true),
            sharedSidebarSyncToggleModel(
                isSignedIn = true,
                accountAvailable = true,
                syncAvailable = true,
                isProUser = true,
                isSyncEnabled = true
            )
        )

        assertEquals(
            SharedSidebarSyncToggleModel(visible = true, enabled = false, checked = true),
            sharedSidebarSyncToggleModel(
                isSignedIn = true,
                accountAvailable = true,
                syncAvailable = true,
                isProUser = false,
                isSyncEnabled = true
            )
        )

        assertFalse(
            sharedSidebarSyncToggleModel(
                isSignedIn = true,
                accountAvailable = false,
                syncAvailable = true,
                isProUser = true,
                isSyncEnabled = true,
                featurePolicy = SharedFeaturePolicy.OssOffline
            ).visible
        )
    }

    @Test
    fun `collection cover stack uses Android cover order and limit`() {
        val books = listOf(
            book("one", coverImagePath = "/covers/one.png"),
            book("two", coverImagePath = "/covers/two.png"),
            book("three", coverImagePath = "/covers/three.png"),
            book("four", coverImagePath = "/covers/four.png"),
            book("five", coverImagePath = "/covers/five.png")
        )

        val coverBooks = collectionCoverStackBooks(
            Shelf("manual", "Manual", ShelfType.MANUAL, books)
        )

        assertEquals(listOf("four", "three", "two", "one"), coverBooks.map { it.id })
        assertEquals(
            listOf("/covers/four.png", "/covers/three.png", "/covers/two.png", "/covers/one.png"),
            coverBooks.map { it.coverImagePath }
        )
        assertTrue(collectionCoverStackBooks(Shelf("empty", "Empty", ShelfType.FOLDER, emptyList())).isEmpty())
    }

    private fun book(
        id: String,
        title: String = id,
        displayName: String = "$id.epub",
        author: String? = null,
        type: FileType = FileType.EPUB,
        progress: Float? = null,
        positionModified: Long = 0L,
        tags: List<Tag> = emptyList(),
        sourceFolder: String? = null,
        path: String? = "/books/$id.epub",
        coverImagePath: String? = null
    ) = BookItem(
        id = id,
        path = path,
        type = type,
        displayName = displayName,
        timestamp = 1L,
        coverImagePath = coverImagePath,
        title = title,
        author = author,
        progressPercentage = progress,
        readingPositionModifiedTimestamp = positionModified,
        tags = tags,
        sourceFolder = sourceFolder
    )
}
