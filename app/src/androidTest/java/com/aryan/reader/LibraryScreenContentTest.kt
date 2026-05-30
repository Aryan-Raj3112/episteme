package com.aryan.reader

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.TagEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalFoundationApi::class)
@RunWith(AndroidJUnit4::class)
class LibraryScreenContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
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
        composeTestRule.onNodeWithContentDescription(text(R.string.action_search)).performClick()
        composeTestRule.onNodeWithTag("LibrarySearchTextField").performTextInput("Gamma")

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Gamma Field Notes").fetchSemanticsNodes().isNotEmpty()
        }
        assertNoText("Alpha Orchard")
        assertNoText("Beta Manual")

        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_clear_query)).performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Alpha Orchard").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText("Beta Manual").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_close_search)).performClick()
        composeTestRule.onNodeWithText(text(R.string.library_title)).assertIsDisplayed()
    }

    @Test
    fun searchMatchesAuthorAndTagNames() {
        setLibraryContent()

        composeTestRule.onNodeWithContentDescription(text(R.string.action_search)).performClick()
        composeTestRule.onNodeWithTag("LibrarySearchTextField").performTextInput("Zara")

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Alpha Orchard").fetchSemanticsNodes().isNotEmpty()
        }
        assertNoText("Gamma Field Notes")

        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_clear_query)).performClick()
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

        composeTestRule.onNodeWithText(text(R.string.filter_types, FileType.EPUB.name)).performClick()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Beta Manual").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun tagAndReadStatusFiltersUseSharedLibraryRules() {
        setLibraryContent(
            initialFilters = LibraryFilters(
                tagIds = setOf(focusTag.id),
                readStatus = ReadStatusFilter.IN_PROGRESS
            )
        )

        composeTestRule.onNodeWithText("Alpha Orchard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gamma Field Notes").assertIsDisplayed()
        assertNoText("Beta Manual")
        composeTestRule.onNodeWithText(text(R.string.filter_tags, focusTag.name)).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            text(R.string.filter_status, text(ReadStatusFilter.IN_PROGRESS.labelRes))
        ).assertIsDisplayed()
    }

    @Test
    fun clearSelectionReturnsToNormalToolbar() {
        setLibraryContent()

        composeTestRule.onNodeWithTag("LibraryBookItem_epub_alpha").performTouchInput {
            down(center)
            advanceEventTime(600)
            up()
        }
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(text(R.string.items_selected_count, 1)).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription(text(R.string.clear_selection)).performClick()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(text(R.string.library_title)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun sortMenuSelectionReordersLibraryItems() {
        setLibraryContent()

        assertBookAbove("pdf_beta", "epub_alpha")

        composeTestRule.onNodeWithTag("LibrarySortButton").performClick()
        composeTestRule.onNodeWithText(text(SortOrder.TITLE_ASC.labelRes)).performClick()

        composeTestRule.waitUntil(5_000) {
            runCatching {
                bookTop("epub_alpha") < bookTop("pdf_beta")
            }.getOrDefault(false)
        }
        assertBookAbove("epub_alpha", "pdf_beta")
    }

    @Test
    fun longPressBookShowsContextualToolbarActions() {
        var tagClicked = false
        var pinClicked = false
        var infoClicked = false
        var selectAllClicked = false
        var deleteClicked = false

        setLibraryContent(
            onTagClick = { tagClicked = true },
            onPinClick = { pinClicked = true },
            onInfoClick = { infoClicked = true },
            onSelectAllClick = { selectAllClicked = true },
            onDeleteClick = { deleteClicked = true }
        )

        composeTestRule.onNodeWithTag("LibraryBookItem_epub_alpha").performTouchInput {
            down(center)
            advanceEventTime(600)
            up()
        }

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(text(R.string.items_selected_count, 1)).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_tag)).performClick()
        composeTestRule.onNodeWithContentDescription(text(R.string.pin_unpin)).performClick()
        composeTestRule.onNodeWithContentDescription(text(R.string.info)).performClick()
        composeTestRule.onNodeWithContentDescription(text(R.string.select_all)).performClick()
        composeTestRule.onNodeWithContentDescription(text(R.string.action_delete)).performClick()

        assertThat(tagClicked).isTrue()
        assertThat(pinClicked).isTrue()
        assertThat(infoClicked).isTrue()
        assertThat(selectAllClicked).isTrue()
        assertThat(deleteClicked).isTrue()
    }

    @Test
    fun shelvesTabShowsShelfRowsAndNewShelfAction() {
        val shelf = Shelf(
            id = "manual_favorites",
            name = "Manual Favorites",
            type = ShelfType.MANUAL,
            books = listOf(libraryBooks[0], libraryBooks[1])
        )
        var clickedShelfId: String? = null
        var longClickedShelfId: String? = null
        var newShelfClicked = false

        setLibraryContent(
            initialPage = 1,
            shelves = listOf(shelf),
            onShelfClick = { clickedShelfId = it.id },
            onShelfLongClick = { longClickedShelfId = it.id },
            onNewShelfClick = { newShelfClicked = true }
        )

        composeTestRule.onNodeWithTag("ShelfItem_manual_favorites").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manual Favorites").assertIsDisplayed()

        composeTestRule.onNodeWithTag("ShelfItem_manual_favorites").performClick()
        composeTestRule.onNodeWithTag("ShelfItem_manual_favorites").performTouchInput {
            down(center)
            advanceEventTime(600)
            up()
        }
        composeTestRule.onNodeWithTag("LibraryNewShelfFab").performClick()

        assertThat(clickedShelfId).isEqualTo("manual_favorites")
        assertThat(longClickedShelfId).isEqualTo("manual_favorites")
        assertThat(newShelfClicked).isTrue()
    }

    private fun setLibraryContent(
        initialFilters: LibraryFilters = LibraryFilters(),
        initialPage: Int = 0,
        shelves: List<Shelf> = emptyList(),
        onTagClick: () -> Unit = {},
        onPinClick: () -> Unit = {},
        onInfoClick: () -> Unit = {},
        onSelectAllClick: () -> Unit = {},
        onDeleteClick: () -> Unit = {},
        onShelfClick: (Shelf) -> Unit = {},
        onShelfLongClick: (Shelf) -> Unit = {},
        onNewShelfClick: () -> Unit = {}
    ) {
        val searchQuery = mutableStateOf("")
        val isSearchActive = mutableStateOf(false)
        val filters = mutableStateOf(initialFilters)
        val sortOrder = mutableStateOf(SortOrder.RECENT)
        val selectedItems = mutableStateOf(emptySet<RecentFileItem>())

        composeTestRule.setContent {
            val pagerState = rememberPagerState(
                initialPage = initialPage,
                pageCount = { 3 }
            )
            val visibleBooks = sortFiles(
                applyLibraryFilters(
                    filterBySearch(libraryBooks, searchQuery.value),
                    filters.value
                ),
                sortOrder.value
            )

            MaterialTheme {
                LibraryScreenContent(
                    tabTitles = listOf(
                        text(R.string.tab_all_books),
                        text(R.string.tab_shelves),
                        text(R.string.tab_folders)
                    ),
                    recentFiles = visibleBooks,
                    rawLibraryFiles = libraryBooks,
                    shelves = shelves,
                    selectedItems = selectedItems.value,
                    selectedShelves = emptySet(),
                    sortOrder = sortOrder.value,
                    libraryFilters = filters.value,
                    allTags = listOf(focusTag),
                    pinnedLibraryBookIds = emptySet(),
                    pagerState = pagerState,
                    scope = rememberCoroutineScope(),
                    searchQuery = searchQuery.value,
                    isSearchActive = isSearchActive.value,
                    onSearchQueryChange = { searchQuery.value = it },
                    onSearchActiveChange = { isSearchActive.value = it },
                    onSortOrderChange = { sortOrder.value = it },
                    onFilterClick = {},
                    onClearFilters = { filters.value = LibraryFilters() },
                    onRemoveFilter = { filters.value = it },
                    onTagClick = onTagClick,
                    onPinClick = onPinClick,
                    onClearSelection = { selectedItems.value = emptySet() },
                    onItemClick = {},
                    onItemLongClick = { item -> selectedItems.value = setOf(item) },
                    onInfoClick = onInfoClick,
                    onDeleteClick = onDeleteClick,
                    onSelectAllClick = onSelectAllClick,
                    onShelfClick = onShelfClick,
                    onShelfLongClick = onShelfLongClick,
                    onClearShelfSelection = {},
                    onDeleteShelves = {},
                    onNewShelfClick = onNewShelfClick,
                    onSelectFileClick = {},
                    onScanNowClick = {},
                    onSyncMetadataClick = {},
                    onSelectSyncFolderClick = {},
                    onEditFolderFiltersClick = { _, _ -> },
                    onDisconnectSyncFolderClick = {},
                    downloadingBookIds = emptySet(),
                    lastFolderScanTime = null,
                    isLoading = false,
                    isRefreshing = false,
                    syncedFolders = emptyList(),
                    onRemoveFolderClick = {},
                    onFolderLocalSyncChange = { _, _, _ -> },
                    onOpdsBookDownloaded = { _, _ -> },
                    onStreamOpdsBook = { _, _ -> },
                    onDeleteCatalogStreams = {},
                    onSettingsClick = {},
                    usePdfFileNameAsDisplayName = false
                )
            }
        }
    }

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

    private fun assertBookAbove(upperBookId: String, lowerBookId: String) {
        assertThat(bookTop(upperBookId)).isLessThan(bookTop(lowerBookId))
    }

    private fun assertNoText(value: String) {
        assertThat(composeTestRule.onAllNodesWithText(value).fetchSemanticsNodes()).isEmpty()
    }

    private fun bookTop(bookId: String): Float {
        return composeTestRule
            .onNodeWithTag("LibraryBookItem_$bookId")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
    }

    private fun text(resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }
}
