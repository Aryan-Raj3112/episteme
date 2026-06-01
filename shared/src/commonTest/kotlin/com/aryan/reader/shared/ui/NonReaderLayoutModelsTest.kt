package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFeaturePolicy
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SyncedFolder
import com.aryan.reader.shared.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NonReaderLayoutModelsTest {

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
    fun `desktop book overflow exposes add to shelf action without changing android`() {
        assertEquals(
            setOf(NonReaderBookOverflowAction.ADD_TO_SHELF),
            bookOverflowActionsForPlatform(ReaderPlatform.DESKTOP)
        )
        assertEquals(emptySet<NonReaderBookOverflowAction>(), bookOverflowActionsForPlatform(ReaderPlatform.ANDROID))
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
                .any { it.title == "Comics" && FileType.CBR in it.fileTypes && FileType.CB7 in it.fileTypes && FileType.CBT in it.fileTypes }
        )
    }

    @Test
    fun `home layout separates active tab pinned and recent books`() {
        val activeTab = book("tab", title = "Open Tab", progress = 12f)
        val inProgress = book("continue", title = "Continue", progress = 40f)
        val pinned = book("pinned", title = "Pinned")
        val recent = book("recent", title = "Recent")

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
        type: FileType = FileType.EPUB,
        progress: Float? = null,
        tags: List<Tag> = emptyList(),
        sourceFolder: String? = null,
        path: String? = "/books/$id.epub",
        coverImagePath: String? = null
    ) = BookItem(
        id = id,
        path = path,
        type = type,
        displayName = "$id.epub",
        timestamp = 1L,
        coverImagePath = coverImagePath,
        title = title,
        progressPercentage = progress,
        tags = tags,
        sourceFolder = sourceFolder
    )
}
