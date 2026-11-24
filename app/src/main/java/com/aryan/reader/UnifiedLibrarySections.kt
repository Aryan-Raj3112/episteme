package com.aryan.reader

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.CloudFolderSyncSelection
import com.aryan.reader.shared.SortOrder
import com.aryan.reader.shared.search.SharedGlobalSearchBookResult
import com.aryan.reader.shared.search.SharedGlobalSearchHit
import com.aryan.reader.shared.ui.AppIcon

/**
 * Section-level composables for Library Beta.
 *
 * Split out of [UnifiedLibraryScreen] so the screen owns destination wiring while these
 * own their own layout. Every shared collaborator is injected, so the Android
 * implementation stays the benchmark for iOS parity.
 */

@Composable
internal fun unifiedShelfCountLabel(shelf: Shelf): String {
    val isFolder = shelf.type == ShelfType.FOLDER
    val folderCount = pluralStringResource(R.plurals.folder_count, shelf.childShelfCount, shelf.childShelfCount)
    val directCount = pluralStringResource(R.plurals.book_count, shelf.directBookCount, shelf.directBookCount)
    return when {
        isFolder && shelf.childShelfCount > 0 && shelf.directBookCount > 0 -> "$folderCount · $directCount"
        isFolder && shelf.childShelfCount > 0 -> folderCount
        isFolder -> directCount
        else -> pluralStringResource(R.plurals.book_count, shelf.bookCount, shelf.bookCount)
    }
}

@Composable
internal fun UnifiedFoldersSection(
    modifier: Modifier,
    folders: List<SyncedFolder>,
    allRecentFiles: List<RecentFileItem>,
    isLoading: Boolean,
    onAddFolder: () -> Unit,
    onScan: () -> Unit,
    onSyncMetadata: () -> Unit,
    onToggleLocalSync: (SyncedFolder, Boolean, Boolean) -> Unit,
    onEditFolderFilters: (SyncedFolder, Set<FileType>) -> Unit,
    onRemove: (SyncedFolder) -> Unit,
    cloudFolderSelection: CloudFolderSyncSelection? = null,
    cloudSyncEnabled: Boolean = false,
    isProUser: Boolean = false,
    onCloudFolderSettings: (() -> Unit)? = null,
    onIncomingCloudFolder: ((String) -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        FolderSyncScreen(
            syncedFolders = folders,
            allRecentFiles = allRecentFiles,
            onAddFolderClick = onAddFolder,
            onRemoveFolderClick = onRemove,
            onFolderLocalSyncChange = onToggleLocalSync,
            onEditFolderFiltersClick = onEditFolderFilters,
            onScanNowClick = onScan,
            onSyncMetadataClick = onSyncMetadata,
            isLoading = isLoading,
            cloudFolderSelection = cloudFolderSelection,
            cloudSyncEnabled = cloudSyncEnabled,
            isProUser = isProUser,
            onCloudFolderSettingsClick = onCloudFolderSettings,
            onIncomingCloudFolderClick = onIncomingCloudFolder,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnifiedLibraryControlsSheet(currentFilter: UnifiedLibraryFilter, currentSortOrder: SortOrder, onFilterChanged: (UnifiedLibraryFilter) -> Unit, onSortChanged: (SortOrder) -> Unit, onAdvancedFiltersClick: () -> Unit, onDismiss: () -> Unit) {
    com.aryan.reader.shared.ui.SharedAndroidUnifiedLibraryControlsSheet(
        currentFilter = currentFilter,
        currentSortOrder = currentSortOrder,
        strings = com.aryan.reader.shared.ui.SharedAndroidUnifiedControlsStrings(
            title = stringResource(R.string.unified_library_sort_filter),
            readStatus = stringResource(R.string.filter_read_status),
            sort = stringResource(R.string.content_desc_sort),
            advancedFilters = stringResource(R.string.filter_library),
            filterLabels = UnifiedLibraryFilter.entries.associateWith { stringResource(it.labelRes) },
            sortLabels = SortOrder.entries.associateWith { stringResource(it.labelRes) },
        ),
        onFilterChanged = onFilterChanged,
        onSortChanged = onSortChanged,
        onAdvancedFilters = onAdvancedFiltersClick,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun UnifiedCreateShelfDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    com.aryan.reader.shared.ui.SharedAndroidUnifiedCreateShelfDialog(
        title = stringResource(R.string.create_new_shelf),
        nameLabel = stringResource(R.string.shelf_name_hint),
        createLabel = stringResource(R.string.action_create),
        cancelLabel = stringResource(R.string.action_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

// Hoisted: shape allocation + clip without the offscreen shadow pass the
// scrolling continue card used to pay per recomposition.
internal val ContinueReadingCoverShape = RoundedCornerShape(18.dp)

@Composable
internal fun UnifiedContinueReadingCard(item: RecentFileItem, onClick: () -> Unit, modifier: Modifier = Modifier) {    val progress = (item.progressPercentage ?: 0f).coerceIn(0f, 100f)
    val appLayoutDirection = if (LocalConfiguration.current.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    com.aryan.reader.shared.ui.SharedAndroidUnifiedContinueCard(
        sectionLabel = stringResource(R.string.unified_library_continue_reading),
        title = item.cardTitle(),
        author = item.cardAuthor(),
        progressPercent = progress,
        progressLabel = stringResource(R.string.progress_complete, progress.toInt()),
        sourceLabel = if (item.sourceFolderUri != null) "· Local folder" else null,
        coverTone = generatedBookCoverColor(item),
        cardLayoutDirection = appLayoutDirection,
        onClick = onClick,
        modifier = modifier,
        cover = { coverModifier ->
                    ThemedBookCover(
                        item = item,
                modifier = coverModifier
                            .size(94.dp, 146.dp)
                            .clip(ContinueReadingCoverShape),
                        contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                    )
        },
        fileTypeBadge = {
                        FileTypeBadge(type = item.type, overlay = true, compact = true)
        },
    )
}

@Composable
internal fun UnifiedLibraryTopBar(
    section: UnifiedLibrarySection,
    selectedShelf: Shelf?,
    /** Null when navigation is already on screen (the wide-window rail). */
    onMenuClick: (() -> Unit)?,
    onBackFromShelf: () -> Unit,
    uiState: ReaderScreenState,
    onAccountClick: () -> Unit,
    searchQuery: String?,
    onSearchQueryChange: (String) -> Unit,
    searchInsideFiles: Boolean = false,
    onSearchInsideFilesChange: ((Boolean) -> Unit)? = null,
    /**
     * Shown when no shelf is open. Tags use this for the selected tag's name
     * without pretending the tag is a shelf in the top-bar back path.
     */
    titleOverride: String? = null,
    /** Open reader tabs, text first, as the shared tab switcher lists them. */
    openTabs: List<com.aryan.reader.shared.ui.SharedOpenTab> = emptyList(),
    onOpenTabSelected: (bookId: String) -> Unit = {},
    onOpenTabClosed: (bookId: String) -> Unit = {},
    onOpenTabMoved: ((kind: com.aryan.reader.shared.ReaderTabKind, bookId: String, targetBookId: String) -> Unit)? = null,
    onCloseAllTabs: (() -> Unit)? = null,
) {
    val title = selectedShelf?.name ?: titleOverride ?: when (section) {
        UnifiedLibrarySection.HOME -> null
        UnifiedLibrarySection.AUDIOBOOKS -> stringResource(R.string.listen_title)
        UnifiedLibrarySection.SHELVES -> stringResource(R.string.tab_shelves)
        UnifiedLibrarySection.TAGS -> stringResource(R.string.section_tags)
        UnifiedLibrarySection.FOLDERS -> stringResource(R.string.tab_folders)
        UnifiedLibrarySection.CATALOGS -> stringResource(R.string.tab_catalogs)
    }
    com.aryan.reader.shared.ui.SharedAndroidUnifiedTopBar(
        title = title,
        showingShelf = selectedShelf != null || titleOverride != null,
        drawerDescription = stringResource(R.string.unified_library_drawer_title),
        backToShelvesDescription = when {
            selectedShelf?.parentShelfId != null -> stringResource(R.string.action_back)
            titleOverride != null -> stringResource(R.string.section_tags)
            else -> stringResource(R.string.unified_library_back_to_shelves)
        },
        onMenu = onMenuClick,
        onBackFromShelf = onBackFromShelf,
        onAccount = onAccountClick,
        accountAvatar = { UnifiedProfileAvatar(uiState) },
        searchQuery = searchQuery,
        searchPlaceholder = stringResource(R.string.unified_library_search_books),
        clearSearchDescription = stringResource(R.string.content_desc_clear_query),
        onSearchQueryChange = onSearchQueryChange,
        searchInsideFiles = searchInsideFiles,
        onSearchInsideFilesChange = onSearchInsideFilesChange,
        searchInsideFilesLabel = stringResource(R.string.unified_library_search_inside_files),
        trailingActions = {
            if (openTabs.isNotEmpty()) {
                val context = LocalContext.current
                val booksById = remember(uiState.textOpenTabs, uiState.openTabs) {
                    (uiState.textOpenTabs + uiState.openTabs).associateBy { it.bookId }
                }
                com.aryan.reader.shared.ui.SharedTabsMenuButton(
                    tabs = openTabs,
                    label = stringResource(R.string.home_tabs_title),
                    openTabsDescription = stringResource(R.string.content_desc_open_tabs),
                    closeTabDescription = stringResource(R.string.close_tab),
                    dragToReorderDescription = stringResource(R.string.content_desc_drag_to_reorder),
                    openCountLabel = stringResource(R.string.home_tabs_open_count, openTabs.size),
                    epubLabel = stringResource(R.string.home_tabs_epub),
                    pdfLabel = stringResource(R.string.home_tabs_pdf),
                    chapterLabel = { number -> context.getString(R.string.home_tabs_chapter, number) },
                    pageLabel = { number -> context.getString(R.string.home_tabs_page, number) },
                    percentLabel = { percent -> context.getString(R.string.home_tabs_percent, percent) },
                    closeAllLabel = onCloseAllTabs?.let { stringResource(R.string.home_tabs_close_all) },
                    onCloseAllTabs = onCloseAllTabs,
                    onTabSelected = { tab -> onOpenTabSelected(tab.bookId) },
                    onTabClosed = { tab -> onOpenTabClosed(tab.bookId) },
                    onTabMoved = onOpenTabMoved,
                    cover = { tab, coverModifier ->
                        val item = booksById[tab.bookId]
                        if (item != null) {
                            ThemedBookCover(
                                item = item,
                                modifier = coverModifier,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(coverModifier.background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                    },
                    modifier = Modifier.padding(end = 2.dp),
                )
            }
        },
    )
}

@Composable
internal fun UnifiedProfileAvatar(uiState: ReaderScreenState) {
    val user = uiState.currentUser
    when {
        BuildConfig.FLAVOR != "pro" -> AppIcon(
            contentDescription = stringResource(R.string.content_desc_app_icon),
            size = 32.dp,
            shape = CircleShape,
        )
        user != null -> AndroidAccountAvatar(
            user = user,
            modifier = Modifier.size(32.dp),
            contentDescription = stringResource(R.string.content_desc_profile_picture),
        )
        else -> Icon(
            Icons.Outlined.AccountCircle,
            contentDescription = stringResource(R.string.content_desc_profile),
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun UnifiedLibraryHome(
    modifier: Modifier,
    books: List<RecentFileItem>,
    continueReading: RecentFileItem?,
    filter: UnifiedLibraryFilter,
    query: String,
    sortOrder: SortOrder,
    advancedFilterCount: Int,
    useListView: Boolean,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    usePdfFileNameAsDisplayName: Boolean,
    onFilterChange: (UnifiedLibraryFilter) -> Unit,
    onControlsClick: () -> Unit,
    onAdvancedFiltersClick: () -> Unit,
    onListViewChange: (Boolean) -> Unit,
    onBookClick: (RecentFileItem) -> Unit,
    onBookLongClick: (RecentFileItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    searchInsideFiles: Boolean = false,
    contentSearchResults: List<SharedGlobalSearchBookResult> = emptyList(),
    onContentHitClick: (RecentFileItem, SharedGlobalSearchHit, String) -> Unit = { _, _, _ -> },
    isLoading: Boolean = false,
    /** Host-provided "clear filters" affordance for an empty, filtered view. */
    clearFiltersLabel: String? = null,
    onClearFilters: (() -> Unit)? = null,
) {
    // Content search replaces the grid: one row per matching book, its matched
    // text, and an expand affordance for the rest of that book's matches.
    val contentSearchActive = searchInsideFiles && query.isNotBlank()
    com.aryan.reader.shared.ui.SharedAndroidUnifiedLibraryHome(
        books = books,
        continueReading = continueReading.takeIf { filter == UnifiedLibraryFilter.ALL && query.isBlank() },
        filter = filter,
        sortLabel = stringResource(sortOrder.labelRes),
        advancedFilterCount = advancedFilterCount,
        useListView = useListView,
        strings = com.aryan.reader.shared.ui.SharedAndroidUnifiedHomeStrings(
            noBooks = if (contentSearchActive) {
                stringResource(R.string.library_content_search_no_results)
            } else {
                stringResource(R.string.unified_library_no_books)
            },
            filterBooks = stringResource(R.string.content_desc_filter),
            gridView = stringResource(R.string.unified_library_grid_view),
            listView = stringResource(R.string.unified_library_list_view),
            filterLabels = UnifiedLibraryFilter.entries.associateWith { stringResource(it.labelRes) },
        ),
        itemKey = { it.bookId },
        contentSearchActive = contentSearchActive,
        contentSearchResults = contentSearchResults,
        contentResultRow = { item, result, expanded, onToggleExpanded ->
            LibraryContentSearchResultRow(
                item = item,
                result = result,
                expanded = expanded,
                onToggleExpanded = onToggleExpanded,
                onClick = { onBookClick(item) },
                onLongClick = { onBookLongClick(item) },
                onHitClick = { hit -> onContentHitClick(item, hit, query) },
                usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            )
        },
        onFilterChange = onFilterChange,
        onControls = onControlsClick,
        onAdvancedFilters = onAdvancedFiltersClick,
        onListViewChange = onListViewChange,
        continueCard = { item, cardModifier -> UnifiedContinueReadingCard(item, { onBookClick(item) }, cardModifier) },
        bookCard = { item ->
            RecentFileCard(
                item = item,
                isSelected = item.bookId in selectedBookIds,
                modifier = Modifier.fillMaxWidth(),
                isPinned = item.bookId in pinnedBookIds,
                onClick = { onBookClick(item) },
                onLongClick = { onBookLongClick(item) },
                isDownloading = item.bookId in downloadingBookIds,
                usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            )
        },
        bookListItem = { item ->
            LibraryListItem(
                item = item,
                isSelected = item.bookId in selectedBookIds,
                isPinned = item.bookId in pinnedBookIds,
                onItemClick = { onBookClick(item) },
                onItemLongClick = { onBookLongClick(item) },
                isDownloading = item.bookId in downloadingBookIds,
                usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            )
        },
        widthClass = when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> com.aryan.reader.shared.ui.SharedAndroidHomeWidthClass.COMPACT
            WindowWidthSizeClass.Medium -> com.aryan.reader.shared.ui.SharedAndroidHomeWidthClass.MEDIUM
            else -> com.aryan.reader.shared.ui.SharedAndroidHomeWidthClass.EXPANDED
        },
        isLoading = isLoading,
        // Only a filtered/searched view has something to clear; a genuinely empty library keeps the
        // import path (the empty state) instead of offering a button that would do nothing.
        emptyActionLabel = clearFiltersLabel.takeIf {
            onClearFilters != null && (filter != UnifiedLibraryFilter.ALL || query.isNotBlank())
        },
        onEmptyAction = onClearFilters,
        sectionTitle = stringResource(R.string.unified_library_your_books),
        modifier = modifier,
    )
}

@Composable
internal fun UnifiedShelvesSection(
    modifier: Modifier,
    shelves: List<Shelf>,
    selectedShelfId: String?,
    selectedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    usePdfFileNameAsDisplayName: Boolean,
    widthSizeClass: WindowWidthSizeClass,
    sortOrder: SortOrder,
    useListView: Boolean,
    onListViewChange: (Boolean) -> Unit,
    onShelfSelected: (Shelf) -> Unit,
    onBreadcrumbNavigate: (String?) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onRenameShelf: (String) -> Unit,
    onDeleteShelf: (String) -> Unit,
    onAddBooks: (String) -> Unit,
    onBookClick: (RecentFileItem) -> Unit,
    onBookLongClick: (RecentFileItem) -> Unit,
) {
    val selectedShelf = shelves.find { it.id == selectedShelfId }
    // Generated tag shelves belong to the Tags destination, not the shelf tree.
    val visibleShelves = remember(shelves) { shelves.filter { it.type != ShelfType.TAG && it.parentShelfId == null } }
    val childShelves = remember(shelves, selectedShelf) {
        selectedShelf?.childShelfIds?.mapNotNull { childId -> shelves.find { it.id == childId } } ?: emptyList()
    }
    val breadcrumbEntries = remember(shelves, selectedShelfId) {
        com.aryan.reader.shared.ui.genericShelfBreadcrumbPath(
            currentShelfId = selectedShelfId,
            lookup = { id -> shelves.find { it.id == id } },
            idOf = { it.id },
            nameOf = { it.name },
            parentIdOf = { it.parentShelfId },
        )
    }
    com.aryan.reader.shared.ui.SharedAndroidUnifiedShelves(
        visibleShelves = visibleShelves,
        selectedShelf = selectedShelf,
        selectedBooks = selectedShelf?.directBooks.orEmpty(),
        noShelvesLabel = stringResource(R.string.unified_library_no_shelves),
        shelfKey = { it.id },
        shelfName = { it.name },
        shelfBookCountLabel = { unifiedShelfCountLabel(it) },
        bookKey = { it.bookId },
        onShelfSelected = onShelfSelected,
        widthClass = when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> com.aryan.reader.shared.ui.SharedAndroidHomeWidthClass.COMPACT
            WindowWidthSizeClass.Medium -> com.aryan.reader.shared.ui.SharedAndroidHomeWidthClass.MEDIUM
            else -> com.aryan.reader.shared.ui.SharedAndroidHomeWidthClass.EXPANDED
        },
        modifier = modifier,
        childShelves = childShelves,
        onChildShelfSelected = onShelfSelected,
        selectedShelfActions = { shelf ->
            UnifiedShelfActionsMenu(
                canMutate = shelf.type == ShelfType.MANUAL &&
                    com.aryan.reader.shared.SharedLibraryEditor.canMutateShelf(shelf.id),
                onAddBooks = { onAddBooks(shelf.id) },
                onRename = { onRenameShelf(shelf.id) },
                onDelete = { onDeleteShelf(shelf.id) },
            )
        },
        foldersSectionLabel = stringResource(R.string.section_folders),
        filesSectionLabel = stringResource(R.string.section_files),
        emptyShelfLabel = stringResource(R.string.shelf_empty),
        breadcrumbContent = {
            if (selectedShelf != null) {
                com.aryan.reader.shared.ui.SharedMobileShelfBreadcrumb(
                    entries = breadcrumbEntries,
                    onNavigate = { entry -> onBreadcrumbNavigate(entry.id) },
                    homeContentDescription = stringResource(R.string.tab_shelves),
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp),
                )
            }
        },
        bookCard = { item ->
            RecentFileCard(
                item = item,
                isSelected = item.bookId in selectedBookIds,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onBookClick(item) },
                onLongClick = { onBookLongClick(item) },
                isDownloading = item.bookId in downloadingBookIds,
                usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            )
        },
        bookListItem = { item ->
            LibraryListItem(
                item = item,
                isSelected = item.bookId in selectedBookIds,
                onItemClick = { onBookClick(item) },
                onItemLongClick = { onBookLongClick(item) },
                isDownloading = item.bookId in downloadingBookIds,
                usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            )
        },
        useListView = useListView,
        onListViewChange = onListViewChange,
        listViewDescription = stringResource(R.string.unified_library_list_view),
        gridViewDescription = stringResource(R.string.unified_library_grid_view),
        sortOrder = sortOrder,
        sortLabels = SortOrder.entries.associateWith { stringResource(it.labelRes) },
        sortDescription = stringResource(R.string.content_desc_sort),
        selectedSortDescription = stringResource(R.string.content_desc_selected),
        onSortOrderChange = onSortOrderChange,
    )
}

/**
 * Tags destination for Library Beta.
 *
 * Before this section existed, tags were only reachable from the book info sheet and the
 * filter sheet: the generated `tag_*` shelves were filtered out of every shelf surface and
 * tag chips only rendered on list rows. Reading [tags] directly keeps empty tags visible too.
 */
@Composable
internal fun UnifiedTagsSection(
    modifier: Modifier,
    tags: List<com.aryan.reader.data.TagEntity>,
    books: List<RecentFileItem>,
    selectedTagId: String?,
    selectedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    usePdfFileNameAsDisplayName: Boolean,
    useListView: Boolean,
    widthSizeClass: WindowWidthSizeClass,
    sortOrder: SortOrder,
    onTagSelected: (String) -> Unit,
    onListViewChange: (Boolean) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onBookClick: (RecentFileItem) -> Unit,
    onBookLongClick: (RecentFileItem) -> Unit,
) {
    val selectedTag = selectedTagId?.let { id -> tags.find { it.id == id } }
    if (selectedTag == null) {
        com.aryan.reader.shared.ui.SharedAndroidUnifiedTags(
            tags = tags.map { it.toSharedTag() },
            bookCountLabel = { tag ->
                val count = books.count { book -> book.tags.any { it.id == tag.id } }
                pluralStringResource(R.plurals.book_count, count, count)
            },
            noTagsLabel = stringResource(R.string.desktop_no_tags_yet),
            onTagSelected = { tag -> onTagSelected(tag.id) },
            modifier = modifier,
        )
        return
    }

    val tagBooks = remember(books, selectedTag.id, sortOrder) {
        sortFiles(
            files = books.filter { book -> book.tags.any { it.id == selectedTag.id } },
            sortOrder = sortOrder,
        )
    }
    val tagShelf = remember(selectedTag, tagBooks) {
        Shelf(
            id = "tag_${selectedTag.id}",
            name = selectedTag.name,
            type = ShelfType.TAG,
            books = tagBooks,
            directBooks = tagBooks,
        )
    }

    com.aryan.reader.shared.ui.SharedAndroidUnifiedShelves(
        visibleShelves = emptyList(),
        selectedShelf = tagShelf,
        selectedBooks = tagBooks,
        noShelvesLabel = stringResource(R.string.desktop_no_tags_yet),
        shelfKey = { it.id },
        shelfName = { it.name },
        shelfBookCountLabel = { shelf ->
            pluralStringResource(R.plurals.book_count, shelf.bookCount, shelf.bookCount)
        },
        bookKey = { it.bookId },
        onShelfSelected = {},
        widthClass = when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> com.aryan.reader.shared.ui.SharedAndroidHomeWidthClass.COMPACT
            WindowWidthSizeClass.Medium -> com.aryan.reader.shared.ui.SharedAndroidHomeWidthClass.MEDIUM
            else -> com.aryan.reader.shared.ui.SharedAndroidHomeWidthClass.EXPANDED
        },
        modifier = modifier,
        emptyShelfLabel = stringResource(R.string.shelf_empty),
        bookCard = { item ->
            RecentFileCard(
                item = item,
                isSelected = item.bookId in selectedBookIds,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onBookClick(item) },
                onLongClick = { onBookLongClick(item) },
                isDownloading = item.bookId in downloadingBookIds,
                usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            )
        },
        bookListItem = { item ->
            LibraryListItem(
                item = item,
                isSelected = item.bookId in selectedBookIds,
                onItemClick = { onBookClick(item) },
                onItemLongClick = { onBookLongClick(item) },
                isDownloading = item.bookId in downloadingBookIds,
                usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            )
        },
        useListView = useListView,
        onListViewChange = onListViewChange,
        listViewDescription = stringResource(R.string.unified_library_list_view),
        gridViewDescription = stringResource(R.string.unified_library_grid_view),
        sortOrder = sortOrder,
        sortLabels = SortOrder.entries.associateWith { stringResource(it.labelRes) },
        sortDescription = stringResource(R.string.content_desc_sort),
        selectedSortDescription = stringResource(R.string.content_desc_selected),
        onSortOrderChange = onSortOrderChange,
    )
}

/**
 * Shelf-scoped actions for the shelf currently open in Library Beta.
 *
 * Add books is always offered; rename and delete apply only to manual shelves, since
 * folder-derived shelves are owned by their synced folder.
 */
@Composable
internal fun UnifiedShelfActionsMenu(
    canMutate: Boolean,
    onAddBooks: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("UnifiedShelfActions"),
        ) { Icon(Icons.Default.MoreVert, stringResource(R.string.content_desc_more_options)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fab_add_books)) },
                onClick = { expanded = false; onAddBooks() },
            )
            if (canMutate) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_rename_shelf)) },
                    onClick = { expanded = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_delete_shelf)) },
                    onClick = { expanded = false; onDelete() },
                )
            }
        }
    }
}
