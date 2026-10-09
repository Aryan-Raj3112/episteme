package com.aryan.reader

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedLibraryEditor
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.SortOrder
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.Tag
import com.aryan.reader.shared.opds.SharedOpdsScreenState
import com.aryan.reader.shared.ui.SharedMobileLibraryScreen
import com.aryan.reader.shared.ui.SharedMobileLibraryTab
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Wiring coverage for the shared library screen that the retired Android `LibraryScreen` mirrored
 * (see `docs/shared-parity-migration.md` C4): search, sort, filter chips, selection actions and the
 * shelves tab, all driven through the shared scaffold's own tags and labels.
 *
 * Filtering/sorting/pinning logic itself is unit-covered in `commonTest`
 * (`NonReaderLayoutModelsTest`), the scaffold in `SharedAndroidLibraryScaffoldTest`, and the
 * Android-hosted contextual bar in `SharedContextualActionBarTest`; what is pinned here is that
 * host state and the screen stay wired to each other.
 *
 * The screen is hosted by iOS and resolves its strings through `readerString`, so with no
 * `LocalSharedStringResolver` provided it renders the shared English fallbacks below. (The Android
 * resources for the same keys carry the same values, which is why these literals are also what an
 * Android host would show.)
 */
@RunWith(AndroidJUnit4::class)
class SharedMobileLibraryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val focusTag = TagEntity(id = "tag_focus", name = "Focus", color = null, createdAt = 1L)
    private val libraryBooks = listOf(
        libraryBook(
            bookId = "pdf_beta",
            type = FileType.PDF,
            displayName = "beta.pdf",
            title = "Beta Manual",
            author = "Mira Example",
            timestamp = 3_000L,
            progress = 84f
        ),
        libraryBook(
            bookId = "epub_gamma",
            type = FileType.EPUB,
            displayName = "gamma.epub",
            title = "Gamma Field Notes",
            author = "Nora Example",
            timestamp = 2_000L,
            progress = 47f,
            tags = listOf(focusTag)
        ),
        libraryBook(
            bookId = "epub_alpha",
            type = FileType.EPUB,
            displayName = "alpha.epub",
            title = "Alpha Orchard",
            author = "Zara Example",
            timestamp = 1_000L,
            progress = 12f,
            tags = listOf(focusTag)
        )
    )

    @Test
    fun searchFiltersAndClearRestoresLibraryList() {
        setLibraryContent()

        composeTestRule.onNodeWithText("Beta Manual").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithTag("LibrarySearchTextField").performTextInput("Gamma")

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Gamma Field Notes").fetchSemanticsNodes().isNotEmpty()
        }
        assertNoText("Alpha Orchard")
        assertNoText("Beta Manual")

        composeTestRule.onNodeWithContentDescription("Clear search").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Alpha Orchard").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText("Beta Manual").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription("Close search").performClick()
        composeTestRule.onNodeWithText("Library").assertIsDisplayed()
    }

    @Test
    fun searchMatchesAuthorAndTagNames() {
        setLibraryContent()

        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithTag("LibrarySearchTextField").performTextInput("Zara")

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Alpha Orchard").fetchSemanticsNodes().isNotEmpty()
        }
        assertNoText("Gamma Field Notes")

        composeTestRule.onNodeWithContentDescription("Clear search").performClick()
        composeTestRule.onNodeWithTag("LibrarySearchTextField").performTextInput("Focus")

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Alpha Orchard").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText("Gamma Field Notes").fetchSemanticsNodes().isNotEmpty()
        }
        assertNoText("Beta Manual")
    }

    @Test
    fun activeFileTypeFilterChipCanBeCleared() {
        setLibraryContent(initialFilters = LibraryFilters(fileTypes = setOf(FileType.EPUB)))

        composeTestRule.onNodeWithText("Alpha Orchard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gamma Field Notes").assertIsDisplayed()
        assertNoText("Beta Manual")

        composeTestRule.onNodeWithText("Types: EPUB").performClick()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Beta Manual").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun sortMenuSelectionReordersLibraryItems() {
        setLibraryContent()

        // DATE_ADDED_NEWEST: newest first — Beta (3000) above Alpha (1000).
        assertBookTitleAbove("Beta Manual", "Alpha Orchard")

        // The sort control is a TextButton labelled with the current order.
        composeTestRule.onNodeWithText("Newest").performClick()
        composeTestRule.onNodeWithText("Title A-Z").performClick()

        composeTestRule.waitUntil(5_000) {
            runCatching {
                bookTitleTop("Alpha Orchard") < bookTitleTop("Beta Manual")
            }.getOrDefault(false)
        }
        assertBookTitleAbove("Alpha Orchard", "Beta Manual")
    }

    @Test
    fun longPressBookSelectsAndContextualActionsFire() {
        var selectAllIds: Set<String>? = null
        var pinIds: Set<String>? = null
        var deleteIds: Set<String>? = null

        setLibraryContent(
            onSelectAll = { selectAllIds = it },
            onToggleSelectedPins = { pinIds = it },
            onDeleteBooks = { deleteIds = it },
        )

        composeTestRule.onNodeWithText("Alpha Orchard").performTouchInput {
            down(center)
            advanceEventTime(600)
            up()
        }

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("1 selected").fetchSemanticsNodes().isNotEmpty()
        }

        // Info opens the shared book-info dialog for the single selection.
        composeTestRule.onNodeWithContentDescription("Book info").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("In-App Storage").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Close").performClick()

        composeTestRule.onNodeWithContentDescription("Select all").performClick()
        composeTestRule.waitUntil(5_000) { selectAllIds != null }
        assertThat(selectAllIds).containsExactly("pdf_beta", "epub_gamma", "epub_alpha")

        composeTestRule.onNodeWithContentDescription("Pin").performClick()
        composeTestRule.waitUntil(5_000) { pinIds != null }
        assertThat(pinIds).containsExactly("pdf_beta", "epub_gamma", "epub_alpha")

        // Delete confirms through the shared dialog before reaching the host callback.
        composeTestRule.onNodeWithContentDescription("Remove selected").performClick()
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.waitUntil(5_000) { deleteIds != null }
        assertThat(deleteIds).containsExactly("pdf_beta", "epub_gamma", "epub_alpha")
    }

    @Test
    fun shelvesTabShowsShelfRowsAndNewShelfAction() {
        // Fully qualified: the app keeps its own `com.aryan.reader.Shelf`, and the shared screen
        // takes the shared one.
        val shelf = com.aryan.reader.shared.Shelf(
            id = "manual_favorites",
            name = "Manual Favorites",
            type = ShelfType.MANUAL,
            books = listOf(sharedBook(libraryBooks[0]), sharedBook(libraryBooks[1]))
        )
        var openedShelfId: String? = null
        var longPressedShelfId: String? = null
        var newShelfClicked = false

        setLibraryContent(
            selectedTab = SharedMobileLibraryTab.SHELVES,
            shelves = listOf(shelf),
            onOpenShelf = { openedShelfId = it.id },
            onLongPressShelf = { longPressedShelfId = it.id },
            onNewShelfClick = { newShelfClicked = true }
        )

        composeTestRule.onNodeWithText("Manual Favorites").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manual Favorites").performClick()
        composeTestRule.onNodeWithText("Manual Favorites").performTouchInput {
            down(center)
            advanceEventTime(600)
            up()
        }
        composeTestRule.onNodeWithTag("LibraryNewShelfFab").performClick()

        assertThat(openedShelfId).isEqualTo("manual_favorites")
        assertThat(longPressedShelfId).isEqualTo("manual_favorites")
        assertThat(newShelfClicked).isTrue()
    }

    private fun setLibraryContent(
        initialFilters: LibraryFilters = LibraryFilters(),
        selectedTab: SharedMobileLibraryTab = SharedMobileLibraryTab.BOOKS,
        shelves: List<com.aryan.reader.shared.Shelf> = emptyList(),
        onSelectAll: (Set<String>) -> Unit = {},
        onToggleSelectedPins: (Set<String>) -> Unit = {},
        onDeleteBooks: (Set<String>) -> Unit = {},
        onOpenShelf: (com.aryan.reader.shared.Shelf) -> Unit = {},
        onLongPressShelf: (com.aryan.reader.shared.Shelf) -> Unit = {},
        onNewShelfClick: () -> Unit = {},
    ) {
        val state = mutableStateOf(
            SharedReaderScreenState(
                rawLibraryBooks = libraryBooks.map(::sharedBook),
                // The contextual actions look the single selection up in `libraryBooks`, so the
                // harness keeps both lists populated the way the iOS host does.
                libraryBooks = libraryBooks.map(::sharedBook),
                sortOrder = SortOrder.DATE_ADDED_NEWEST,
                shelves = shelves,
                libraryFilters = initialFilters,
            )
        )

        composeTestRule.setContent {
            SharedMobileLibraryScreen(
                state = state.value,
                selectedTab = selectedTab,
                onTabChange = {},
                opdsState = SharedOpdsScreenState(),
                onImportBooks = {},
                onOpenBook = {},
                onLongPressBook = { book ->
                    val selected = state.value.selectedBookIds
                    state.value = state.value.copy(
                        selectedBookIds = if (book.id in selected) selected - book.id else selected + book.id
                    )
                },
                onSearchQueryChange = { query -> state.value = state.value.copy(searchQuery = query) },
                onSearchActiveChange = { active -> state.value = state.value.copy(isSearchActive = active) },
                onSortOrderChange = { order -> state.value = state.value.copy(sortOrder = order) },
                onClearSelection = { state.value = state.value.copy(selectedBookIds = emptySet()) },
                onSelectAll = { visibleBookIds ->
                    // Mirror the iOS host: select-all selects every visible book, or clears when
                    // they are all selected already.
                    state.value = SharedLibraryEditor.toggleVisibleBookSelectionInState(
                        state = state.value,
                        visibleBookIds = visibleBookIds,
                    )
                    onSelectAll(visibleBookIds)
                },
                onFilterClick = {},
                onClearFilters = { state.value = state.value.copy(libraryFilters = LibraryFilters()) },
                onRemoveFilters = { filters -> state.value = state.value.copy(libraryFilters = filters) },
                onSettingsClick = {},
                onNewShelfClick = onNewShelfClick,
                onOpenShelf = onOpenShelf,
                onLongPressShelf = onLongPressShelf,
                onToggleSelectedPins = onToggleSelectedPins,
                onDeleteBooks = onDeleteBooks,
                onDeleteShelves = {},
                platform = ReaderPlatform.ANDROID,
            )
        }
    }

    private fun sharedBook(item: RecentFileItem): BookItem = BookItem(
        id = item.bookId,
        path = null,
        type = item.type,
        displayName = item.displayName,
        timestamp = item.timestamp,
        title = item.title,
        author = item.author,
        progressPercentage = item.progressPercentage,
        isAvailable = item.isAvailable,
        tags = item.tags.map { Tag(it.id, it.name, it.color) },
    )

    private fun libraryBook(
        bookId: String,
        type: FileType,
        displayName: String,
        title: String,
        author: String,
        timestamp: Long,
        progress: Float,
        tags: List<TagEntity> = emptyList()
    ): RecentFileItem {
        return RecentFileItem(
            bookId = bookId,
            uriString = "content://library-test/$bookId",
            type = type,
            displayName = displayName,
            timestamp = timestamp,
            title = title,
            author = author,
            progressPercentage = progress,
            isRecent = true,
            isAvailable = true,
            fileSize = timestamp * 10,
            tags = tags
        )
    }

    private fun assertBookTitleAbove(upperTitle: String, lowerTitle: String) {
        assertThat(bookTitleTop(upperTitle)).isLessThan(bookTitleTop(lowerTitle))
    }

    private fun bookTitleTop(title: String): Float {
        return composeTestRule
            .onNodeWithText(title)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
    }

    private fun assertNoText(value: String) {
        assertThat(composeTestRule.onAllNodesWithText(value).fetchSemanticsNodes()).isEmpty()
    }
}
