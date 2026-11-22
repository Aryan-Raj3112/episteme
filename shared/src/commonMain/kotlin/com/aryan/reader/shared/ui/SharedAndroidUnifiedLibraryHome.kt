package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.aryan.reader.shared.search.SharedGlobalSearchBookResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class SharedAndroidUnifiedHomeStrings(
    val noBooks: String,
    val filterBooks: String,
    val gridView: String,
    val listView: String,
    val filterLabels: Map<MobileUnifiedLibraryFilter, String>,
)

@Composable
fun <T> SharedAndroidUnifiedLibraryHome(
    books: List<T>,
    continueReading: T?,
    filter: MobileUnifiedLibraryFilter,
    sortLabel: String,
    advancedFilterCount: Int,
    useListView: Boolean,
    strings: SharedAndroidUnifiedHomeStrings,
    itemKey: (T) -> String,
    onFilterChange: (MobileUnifiedLibraryFilter) -> Unit,
    onControls: () -> Unit,
    onAdvancedFilters: () -> Unit,
    onListViewChange: (Boolean) -> Unit,
    continueCard: @Composable (T, Modifier) -> Unit,
    bookCard: @Composable (T) -> Unit,
    bookListItem: @Composable (T) -> Unit,
    widthClass: SharedAndroidHomeWidthClass = SharedAndroidHomeWidthClass.COMPACT,
    /** Host override (Duo). Null keeps the shared width-class mapping. */
    gridCells: GridCells? = null,
    /** Host override (Duo). Standard keeps today's spacing on every other device. */
    density: SharedAndroidHomeDensity = SharedAndroidHomeDensity.Standard,
    /** Rendered above the continue-reading card; empty when multi-tab reading is idle. */
    openTabsContent: @Composable () -> Unit = {},
    /**
     * Inside-file content search. When [contentSearchActive] is true the grid
     * is replaced by one row per matching book, expanded to show more of that
     * book's matches on demand.
     */
    contentSearchActive: Boolean = false,
    contentSearchResults: List<SharedGlobalSearchBookResult> = emptyList(),
    contentResultRow: @Composable (T, SharedGlobalSearchBookResult, Boolean, () -> Unit) -> Unit = { _, _, _, _ -> },
    /** True while the first library scan is still running and there is nothing to show yet. */
    isLoading: Boolean = false,
    /** Empty-state action ("Clear filters"), shown only when the host reports one. */
    emptyActionLabel: String? = null,
    onEmptyAction: (() -> Unit)? = null,
    /**
     * Section title above the grid ("Your books"). Null keeps the header row for the actions but
     * leaves the title out, which is what hosts that already show a title elsewhere want.
     */
    sectionTitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val resolvedGridCells = gridCells ?: widthClass.bookGridCells()
    // Expansion lives here so the row composable stays stateless and the host
    // only supplies layout.
    val expandedBooks = remember { mutableStateMapOf<String, Boolean>() }
    val contentResultsById = remember(contentSearchResults) {
        contentSearchResults.associateBy { it.bookId }
    }
    Column(modifier.fillMaxSize().padding(horizontal = SharedSpacing.Lg)) {
        openTabsContent()
        continueReading?.let { continueCard(it, Modifier.padding(top = density.continueCardTopGap)) }
        // Hierarchy is carried by one header row: what this section is, how it is ordered, and the
        // two view controls. The status filters get their own row underneath, which is also what
        // lets the chip row fit on a 360dp phone now that sort no longer competes for its width.
        SharedHomeSectionHeader(
            title = sectionTitle,
            sortLabel = sortLabel,
            advancedFilterCount = advancedFilterCount,
            useListView = useListView,
            contentSearchActive = contentSearchActive,
            strings = strings,
            onControls = onControls,
            onAdvancedFilters = onAdvancedFilters,
            onListViewChange = onListViewChange,
            modifier = Modifier.padding(top = density.filterRowTopGap),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SharedSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(
                start = SharedSpacing.Xxs,
                end = SharedSpacing.Xxs,
                top = SharedSpacing.Xs,
            ),
        ) {
            items(MobileUnifiedLibraryFilter.entries) { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(strings.filterLabels.getValue(option)) },
                )
            }
        }
        when {
            isLoading && books.isEmpty() -> {
                Surface(
                    Modifier.weight(1f).padding(top = density.gridCardTopGap),
                    shape = sharedContentSheetShape(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    SharedHomeGridSkeleton(
                        gridCells = resolvedGridCells,
                        contentTopPadding = density.gridContentTopPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            books.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = SharedSpacing.Xl)
                        .testTag("UnifiedLibraryHomeEmpty"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // A tonal disc instead of a bare sentence: an empty screen is the one place the
                    // library has room to look designed rather than unfinished.
                    Surface(
                        modifier = Modifier.size(96.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.LibraryBooks,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(SharedSpacing.Lg))
                    Text(
                        strings.noBooks,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    if (emptyActionLabel != null && onEmptyAction != null) {
                        Spacer(Modifier.height(SharedSpacing.Lg))
                        FilledTonalButton(onClick = onEmptyAction) { Text(emptyActionLabel) }
                    }
                }
            }

            contentSearchActive -> {
                Surface(
                    Modifier.weight(1f).padding(top = density.gridCardTopGap),
                    shape = sharedContentSheetShape(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            SharedSpacing.Md,
                            density.gridContentTopPadding,
                            SharedSpacing.Md,
                            SharedGridBottomClearance,
                        ),
                        verticalArrangement = Arrangement.spacedBy(SharedSpacing.Md),
                    ) {
                        // Iterate the merged book list, not the content results: books matching
                        // metadata only must still appear, just without the matched-text body.
                        items(books, key = itemKey) { book ->
                            val result = contentResultsById[itemKey(book)]
                            if (result != null) {
                                val expanded = expandedBooks[result.bookId] == true
                                contentResultRow(
                                    book,
                                    result,
                                    expanded,
                                    { expandedBooks[result.bookId] = !expanded },
                                )
                            } else {
                                bookListItem(book)
                            }
                        }
                    }
                }
            }

            else -> {
                Surface(
                    Modifier.weight(1f).padding(top = density.gridCardTopGap),
                    shape = sharedContentSheetShape(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    if (useListView) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                SharedSpacing.Md,
                                density.gridContentTopPadding,
                                SharedSpacing.Md,
                                SharedGridBottomClearance,
                            ),
                            verticalArrangement = Arrangement.spacedBy(SharedSpacing.Md),
                        ) { items(books, key = itemKey) { bookListItem(it) } }
                    } else {
                        LazyVerticalGrid(
                            columns = resolvedGridCells,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                SharedSpacing.Md,
                                density.gridContentTopPadding,
                                SharedSpacing.Md,
                                SharedGridBottomClearance,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(SharedSpacing.Lg),
                            verticalArrangement = Arrangement.spacedBy(SharedSpacing.Lg),
                        ) { items(books, key = itemKey) { bookCard(it) } }
                    }
                }
            }
        }
    }
}

/**
 * Home's header: the section name on the left, then order and view controls on the right.
 *
 * Sort is a menu trigger rather than a filter, so it reads as a text button with a disclosure
 * caret instead of a chip that looks like a fifth status filter.
 */
@Composable
private fun SharedHomeSectionHeader(
    title: String?,
    sortLabel: String,
    advancedFilterCount: Int,
    useListView: Boolean,
    contentSearchActive: Boolean,
    strings: SharedAndroidUnifiedHomeStrings,
    onControls: () -> Unit,
    onAdvancedFilters: () -> Unit,
    onListViewChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("UnifiedLibraryHomeSectionTitle"),
        )
        TextButton(
            onClick = onControls,
            contentPadding = PaddingValues(horizontal = SharedSpacing.Sm),
            modifier = Modifier.testTag("UnifiedLibraryHomeSort"),
        ) {
            Text(sortLabel, maxLines = 1)
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        }
        BadgedBox(badge = { if (advancedFilterCount > 0) Badge { Text(advancedFilterCount.toString()) } }) {
            IconButton(onClick = onAdvancedFilters) {
                Icon(Icons.Default.FilterList, contentDescription = strings.filterBooks)
            }
        }
        if (!contentSearchActive) {
            IconButton(onClick = { onListViewChange(!useListView) }) {
                Icon(
                    if (useListView) Icons.AutoMirrored.Filled.LibraryBooks else Icons.Default.FormatListNumbered,
                    contentDescription = if (useListView) strings.gridView else strings.listView,
                )
            }
        }
    }
}

/** Clearance under the grid so the last row can scroll clear of the FAB. */
private val SharedGridBottomClearance = 96.dp

/** Two rows of three at compact width; the grid's own column math trims the rest. */
private const val SharedHomeSkeletonCellCount = 6

/**
 * The grid sits in a tonal sheet with rounded top corners. M3 has no "top corners only" shape test,
 * so the token's corner sizes are applied to the top pair and the bottom pair stays square (the
 * sheet runs off the bottom of the window).
 */
@Composable
private fun sharedContentSheetShape(): Shape {
    val sheet = MaterialTheme.shapes.extraLarge
    return RoundedCornerShape(
        topStart = sheet.topStart,
        topEnd = sheet.topEnd,
        bottomEnd = CornerSize(0.dp),
        bottomStart = CornerSize(0.dp),
    )
}

/**
 * Placeholder grid for a running first scan. It mirrors the real grid's geometry instead of showing
 * a spinner, so the screen does not jump when the books arrive.
 */
@Composable
private fun SharedHomeGridSkeleton(
    gridCells: GridCells,
    contentTopPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHigh
    LazyVerticalGrid(
        columns = gridCells,
        modifier = modifier.testTag("UnifiedLibraryHomeLoading"),
        contentPadding = PaddingValues(SharedSpacing.Md, contentTopPadding, SharedSpacing.Md, SharedGridBottomClearance),
        horizontalArrangement = Arrangement.spacedBy(SharedSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(SharedSpacing.Lg),
        userScrollEnabled = false,
    ) {
        items(SharedHomeSkeletonCellCount) {
            Column(verticalArrangement = Arrangement.spacedBy(SharedSpacing.Sm)) {
                Surface(
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.74f),
                    shape = MaterialTheme.shapes.large,
                    color = placeholder,
                ) {}
                Surface(
                    modifier = Modifier.fillMaxWidth(0.8f).height(12.dp),
                    shape = MaterialTheme.shapes.small,
                    color = placeholder,
                ) {}
                Surface(
                    modifier = Modifier.fillMaxWidth(0.5f).height(10.dp),
                    shape = MaterialTheme.shapes.small,
                    color = placeholder,
                ) {}
            }
        }
    }
}

@Composable
fun <T> SharedAndroidUnifiedLibrarySearch(
    books: List<T>,
    query: String,
    searchPlaceholder: String,
    clearDescription: String,
    closeDescription: String,
    resultLabel: String,
    noResultsLabel: String,
    showSearchField: Boolean = true,
    itemKey: (T) -> String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    bookCard: @Composable (T) -> Unit,
    widthClass: SharedAndroidHomeWidthClass = SharedAndroidHomeWidthClass.COMPACT,
    /** Host override (Duo). Null keeps the shared width-class mapping. */
    gridCells: GridCells? = null,
    modifier: Modifier = Modifier,
) {
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }
    if (showSearchField) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    Column(modifier.fillMaxSize().padding(horizontal = SharedSpacing.Lg)) {
        if (showSearchField) {
            Row(Modifier.fillMaxWidth().padding(top = SharedSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                SharedSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = searchPlaceholder,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("UnifiedLibrarySearch"),
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, clearDescription) }
                    },
                )
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, closeDescription) }
            }
        }
        Text(
            resultLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = SharedSpacing.Md, bottom = SharedSpacing.Sm),
        )
        if (books.isEmpty() && query.isNotBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(noResultsLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyVerticalGrid(
                columns = gridCells ?: widthClass.bookGridCells(),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = SharedSpacing.Xl),
                horizontalArrangement = Arrangement.spacedBy(SharedSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(SharedSpacing.Lg),
            ) { items(books, key = itemKey) { bookCard(it) } }
        }
    }
}
