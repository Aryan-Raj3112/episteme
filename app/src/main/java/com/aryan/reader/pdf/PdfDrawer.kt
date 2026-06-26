package com.aryan.reader.pdf

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.R
import io.legere.pdfiumandroid.api.Bookmark
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber
import androidx.core.graphics.createBitmap
import com.aryan.reader.cardTitle
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.pdf.data.VirtualPage
import com.aryan.reader.shared.filterReaderTocEntries

private const val MAX_FIXED_RECURSION = 128

internal data class PdfBookmark(val pageIndex: Int, val title: String, val totalPages: Int)

internal data class TocEntry(val title: String, val pageIndex: Int, val nestLevel: Int)

private enum class PdfDrawerSection {
    TABS,
    CHAPTERS,
    BOOKMARKS,
    HIGHLIGHTS,
    PAGES
}

private val PdfDrawerSection.titleResId: Int
    get() = when (this) {
        PdfDrawerSection.TABS -> R.string.tab_tabs
        PdfDrawerSection.CHAPTERS -> R.string.tab_chapters
        PdfDrawerSection.BOOKMARKS -> R.string.tab_bookmarks
        PdfDrawerSection.HIGHLIGHTS -> R.string.tab_highlights
        PdfDrawerSection.PAGES -> R.string.tab_pages
    }

private val PdfDrawerSection.testTag: String?
    get() = when (this) {
        PdfDrawerSection.TABS -> "TabsTab"
        PdfDrawerSection.BOOKMARKS -> "BookmarksTab"
        PdfDrawerSection.HIGHLIGHTS -> "HighlightsTab"
        PdfDrawerSection.PAGES -> "PagesTab"
        PdfDrawerSection.CHAPTERS -> null
    }

/**
 * Patches the library bug where siblings are truncated due to depth-state leakage.
 */
suspend fun PdfDocumentKt.getFixedTableOfContents(): List<Bookmark> {
    val tag = "PdfTocFix"
    Timber.tag(tag).i("Starting Pure Reflection Traversal...")

    return try {
        // 1. Get the 'document' field (PdfDocumentU) from PdfDocumentKt
        val documentField = PdfDocumentKt::class.java.getDeclaredField("document").apply { isAccessible = true }
        val docUInstance = documentField.get(this) ?: return getTableOfContents()

        // 2. Get the 'nativeDocument' field from PdfDocumentU
        val nativeDocField = docUInstance.javaClass.getDeclaredField("nativeDocument").apply { isAccessible = true }
        val nativeDocInstance = nativeDocField.get(docUInstance) ?: return getTableOfContents()

        // 3. Get the native pointer (long) from PdfDocumentU
        val ptrField = docUInstance.javaClass.getDeclaredField("mNativeDocPtr").apply { isAccessible = true }
        val mNativeDocPtr = ptrField.get(docUInstance) as Long

        // 4. Look up native methods using primitive 'long' types (mandatory for JNI)
        val nClass = nativeDocInstance.javaClass
        val lp = Long::class.javaPrimitiveType!! // Shorthand for 'long'

        val getTitleM = nClass.getMethod("getBookmarkTitle", lp)
        val getDestIdxM = nClass.getMethod("getBookmarkDestIndex", lp, lp)
        val getFirstChildM = nClass.getMethod("getFirstChildBookmark", lp, lp)
        val getSiblingM = nClass.getMethod("getSiblingBookmark", lp, lp)

        val topLevel = mutableListOf<Bookmark>()
        val visited = mutableSetOf<Long>()

        /**
         * Corrected traversal: Iterative for siblings, recursive for children.
         */
        fun walk(parentList: MutableList<Bookmark>, startPtr: Long, level: Int) {
            var currentPtr = startPtr
            var itemIndex = 0

            while (currentPtr != 0L) {
                if (visited.contains(currentPtr)) break
                visited.add(currentPtr)

                val title = getTitleM.invoke(nativeDocInstance, currentPtr) as? String ?: "Untitled"
                val pageIdx = getDestIdxM.invoke(nativeDocInstance, mNativeDocPtr, currentPtr) as Long

                Timber.tag(tag).v("Lvl $level | Item $itemIndex | Ptr: 0x${java.lang.Long.toHexString(currentPtr)} | $title")

                val bookmark = Bookmark().apply {
                    this.mNativePtr = currentPtr
                    this.title = title
                    this.pageIdx = pageIdx
                }
                parentList.add(bookmark)

                // Recursive dive into children
                val firstChild = getFirstChildM.invoke(nativeDocInstance, mNativeDocPtr, currentPtr) as Long
                if (firstChild != 0L && level < MAX_FIXED_RECURSION) {
                    walk(bookmark.children, firstChild, level + 1)
                }

                // Iterative move to next sibling
                currentPtr = getSiblingM.invoke(nativeDocInstance, mNativeDocPtr, currentPtr) as Long
                itemIndex++
            }
        }

        // 5. Start from the root (Pass 0L as primitive long)
        val firstRoot = getFirstChildM.invoke(nativeDocInstance, mNativeDocPtr, 0L) as Long
        if (firstRoot != 0L) {
            walk(topLevel, firstRoot, 0)
        }

        if (topLevel.isEmpty()) {
            Timber.tag(tag).w("No items found, falling back to library.")
            getTableOfContents()
        } else {
            Timber.tag(tag).i("TOC Successfully Patched! Nodes: ${visited.size}")
            topLevel
        }
    } catch (e: Exception) {
        Timber.tag(tag).e(e, "Reflection traversal critical error.")
        this.getTableOfContents()
    }
}

internal fun flattenToc(bookmarks: List<Bookmark>, level: Int = 0): List<TocEntry> {
    Timber.tag("PdfTocDebug").d("Processing level $level with ${bookmarks.size} items")
    val entries = mutableListOf<TocEntry>()
    for ((index, bookmark) in bookmarks.withIndex()) {
        val title = bookmark.title ?: "Untitled Chapter"
        val childCount = bookmark.children.size

        Timber.tag("PdfTocDebug").d(
            "Lvl $level | Item $index: \"$title\" (Page: ${bookmark.pageIdx}) | Children: $childCount"
        )

        entries.add(
            TocEntry(
                title = title,
                pageIndex = bookmark.pageIdx.toInt(),
                nestLevel = level
            )
        )

        if (childCount > 0) {
            Timber.tag("PdfTocDebug").v("Entering children of \"$title\"")
            entries.addAll(flattenToc(bookmark.children, level + 1))
            Timber.tag("PdfTocDebug").v("Returned to Lvl $level from \"$title\"")
        }
    }
    return entries
}

internal fun loadPdfBookmarksFromJson(bookmarksJson: String?): Set<PdfBookmark> {
    if (bookmarksJson.isNullOrBlank()) return emptySet()
    return try {
        val jsonArray = JSONArray(bookmarksJson)
        (0 until jsonArray.length()).mapNotNull { i ->
            try {
                val json = jsonArray.getJSONObject(i)
                PdfBookmark(
                    pageIndex = json.getInt("pageIndex"),
                    title = json.getString("title"),
                    totalPages = json.getInt("totalPages")
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse bookmark from JSON object")
                null
            }
        }.toSet()
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse bookmarks from JSON string: $bookmarksJson")
        emptySet()
    }
}

@Composable
internal fun PdfTocTreeItem(
    label: String,
    nestLevel: Int,
    isExpanded: Boolean,
    hasChildren: Boolean,
    isCurrent: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent,
        label = "TocItemBackground"
    )

    val contentColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width((16 * nestLevel).dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(enabled = hasChildren, onClick = onToggleExpand),
            contentAlignment = Alignment.Center
        ) {
            if (hasChildren) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(
                        if (isExpanded) R.string.content_desc_collapse else R.string.content_desc_expand
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = label,
            style = if (nestLevel == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else if (nestLevel == 0) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 16.dp)
        )
    }
}

@Composable
private fun PdfTabsDrawerPage(
    openTabs: List<RecentFileItem>,
    activeTabBookId: String?,
    currentPage: Int,
    totalPages: Int,
    isTopTabStripVisible: Boolean,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTabClick: () -> Unit,
    onTopTabStripVisibilityChange: (Boolean) -> Unit,
    usePdfFileNameAsDisplayName: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.active_tabs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Text(
                    text = openTabs.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            IconButton(
                onClick = onNewTabClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.content_desc_new_tab)
                )
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTopTabStripVisibilityChange(!isTopTabStripVisible) }
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
                .testTag("PdfTopTabStripVisibilityToggle"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.pdf_tabs_show_top_app_bar_tabs),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isTopTabStripVisible,
                onCheckedChange = null
            )
        }

        HorizontalDivider()

        if (openTabs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.msg_no_other_pdfs_found),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp)
            ) {
                items(openTabs, key = { it.bookId }) { tab ->
                    PdfDrawerTabItem(
                        tab = tab,
                        isSelected = tab.bookId == activeTabBookId,
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onTabSelected = onTabSelected,
                        onTabClosed = onTabClosed,
                        usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfDrawerTabItem(
    tab: RecentFileItem,
    isSelected: Boolean,
    currentPage: Int,
    totalPages: Int,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    usePdfFileNameAsDisplayName: Boolean
) {
    val shape = RoundedCornerShape(8.dp)
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "PdfDrawerTabContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        },
        label = "PdfDrawerTabBorder"
    )
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val progressPercent = remember(isSelected, currentPage, totalPages, tab.progressPercentage) {
        when {
            isSelected && totalPages > 0 -> (((currentPage + 1).toFloat() / totalPages.toFloat()) * 100f)
                .coerceIn(0f, 100f)
                .toInt()
            else -> tab.progressPercentage
                ?.coerceIn(0f, 100f)
                ?.toInt()
        }
    }
    val pageLabel = when {
        isSelected && totalPages > 0 -> stringResource(R.string.page_of_pages, currentPage + 1, totalPages)
        tab.lastPage != null -> stringResource(R.string.pdf_page_short, tab.lastPage + 1)
        else -> null
    }
    val progressLabel = progressPercent
        ?.takeIf { it > 0 }
        ?.let { stringResource(R.string.progress_complete, it) }
    val supportingText = remember(pageLabel, progressLabel, tab.author) {
        listOfNotNull(pageLabel, progressLabel, tab.author)
            .distinct()
            .joinToString(" - ")
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .clickable { onTabSelected(tab.bookId) }
            .testTag("PdfDrawerTab_${tab.bookId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tab.cardTitle(usePdfFileNameAsDisplayName),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (supportingText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = { onTabClosed(tab.bookId) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close_tab),
                    tint = contentColor.copy(alpha = 0.8f)
                )
            }
        }

        progressPercent
            ?.takeIf { it > 0 }
            ?.let { percent ->
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    trackColor = Color.Transparent
                )
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfNavigationDrawerContent(
    pdfDocument: ReaderDocument?,
    documentKey: String,
    flatTableOfContents: List<TocEntry>,
    bookmarks: Set<PdfBookmark>,
    userHighlights: List<PdfUserHighlight>,
    currentPage: Int,
    totalPages: Int,
    isTabsEnabled: Boolean = false,
    openTabs: List<RecentFileItem> = emptyList(),
    activeTabBookId: String? = null,
    usePdfFileNameAsDisplayName: Boolean = false,
    isTopTabStripVisible: Boolean = true,
    customHighlightColors: Map<PdfHighlightColor, Color>,
    onPageSelected: (Int) -> Unit,
    onTabSelected: (String) -> Unit = {},
    onTabClosed: (String) -> Unit = {},
    onNewTabClick: () -> Unit = {},
    onTopTabStripVisibilityChange: (Boolean) -> Unit = {},
    onRenameBookmark: (PdfBookmark, String) -> Unit,
    onDeleteBookmark: (PdfBookmark) -> Unit,
    onDeleteHighlight: (PdfUserHighlight) -> Unit,
    onNoteRequested: (String?) -> Unit,
    onCloseDrawer: () -> Unit
) {
    val showTabsPane = isTabsEnabled && openTabs.isNotEmpty()
    val drawerSections = remember(showTabsPane) {
        buildList {
            if (showTabsPane) add(PdfDrawerSection.TABS)
            add(PdfDrawerSection.CHAPTERS)
            add(PdfDrawerSection.BOOKMARKS)
            add(PdfDrawerSection.HIGHLIGHTS)
            add(PdfDrawerSection.PAGES)
        }
    }
    val drawerPagerState = rememberPagerState(pageCount = { drawerSections.size })
    val drawerScope = rememberCoroutineScope()

    LaunchedEffect(drawerSections.size) {
        if (drawerPagerState.currentPage >= drawerSections.size) {
            drawerPagerState.scrollToPage(drawerSections.lastIndex.coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val selectedDrawerTabIndex = drawerPagerState.currentPage.coerceIn(0, drawerSections.lastIndex)
        ScrollableTabRow(
            selectedTabIndex = selectedDrawerTabIndex,
            edgePadding = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            drawerSections.forEachIndexed { index, section ->
                Tab(
                    selected = selectedDrawerTabIndex == index,
                    onClick = {
                        drawerScope.launch { drawerPagerState.animateScrollToPage(index) }
                    },
                    text = { Text(stringResource(section.titleResId)) },
                    modifier = section.testTag?.let { Modifier.testTag(it) } ?: Modifier
                )
            }
        }

        HorizontalPager(
            state = drawerPagerState,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) { page ->
            when (drawerSections[page]) {
                PdfDrawerSection.TABS -> PdfTabsDrawerPage(
                    openTabs = openTabs,
                    activeTabBookId = activeTabBookId,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    isTopTabStripVisible = isTopTabStripVisible,
                    onTabSelected = { bookId ->
                        if (bookId == activeTabBookId) {
                            onCloseDrawer()
                        } else {
                            onCloseDrawer()
                            onTabSelected(bookId)
                        }
                    },
                    onTabClosed = onTabClosed,
                    onNewTabClick = onNewTabClick,
                    onTopTabStripVisibilityChange = onTopTabStripVisibilityChange,
                    usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName
                )

                PdfDrawerSection.CHAPTERS -> { // Chapters Page
                    if (flatTableOfContents.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.msg_chapters_not_available),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        val listState = rememberLazyListState()
                        val focusManager = LocalFocusManager.current

                        val allParentIndices = remember(flatTableOfContents) {
                            flatTableOfContents.indices.filter { i ->
                                val next = flatTableOfContents.getOrNull(i + 1)
                                next != null && next.nestLevel > flatTableOfContents[i].nestLevel
                            }.toSet()
                        }

                        var expandedEntryIndices by rememberSaveable(flatTableOfContents) {
                            mutableStateOf(allParentIndices)
                        }
                        var tocSearchQuery by rememberSaveable(flatTableOfContents) { mutableStateOf("") }
                        var searchFieldCanFocus by remember(flatTableOfContents) { mutableStateOf(false) }
                        val isSearchingToc = tocSearchQuery.isNotBlank()

                        LaunchedEffect(flatTableOfContents) {
                            searchFieldCanFocus = false
                            focusManager.clearFocus(force = true)
                        }

                        val visibleItemInfo by remember(flatTableOfContents, tocSearchQuery) {
                            derivedStateOf {
                                if (tocSearchQuery.isNotBlank()) {
                                    filterReaderTocEntries(
                                        entries = flatTableOfContents,
                                        query = tocSearchQuery,
                                        labelOf = { it.title },
                                        depthOf = { it.nestLevel }
                                    ).map { it.originalIndex to it.entry }
                                } else {
                                    val result = mutableListOf<Pair<Int, TocEntry>>()
                                    val visibilityStack = BooleanArray(20) { false }
                                    visibilityStack[0] = true

                                    for (i in flatTableOfContents.indices) {
                                        val entry = flatTableOfContents[i]
                                        val level = entry.nestLevel.coerceIn(0, 19)

                                        if (visibilityStack[level]) {
                                            result.add(i to entry)
                                            val isExpanded = expandedEntryIndices.contains(i)
                                            if (level + 1 < visibilityStack.size) {
                                                visibilityStack[level + 1] = isExpanded
                                            }
                                        } else {
                                            if (level + 1 < visibilityStack.size) {
                                                visibilityStack[level + 1] = false
                                            }
                                        }
                                    }
                                    result
                                }
                            }
                        }
                        val currentTocEntry by remember(currentPage, flatTableOfContents) {
                            derivedStateOf {
                                flatTableOfContents.lastOrNull { it.pageIndex <= currentPage }
                            }
                        }

                        val onScrollToCurrent = {
                            drawerScope.launch {
                                val targetEntry = currentTocEntry ?: return@launch
                                val targetOriginalIndex = flatTableOfContents.indexOf(targetEntry)
                                if (targetOriginalIndex != -1) {
                                    var currentLevel = targetEntry.nestLevel
                                    val newExpanded = expandedEntryIndices.toMutableSet()

                                    for (i in targetOriginalIndex downTo 0) {
                                        val entry = flatTableOfContents[i]
                                        if (entry.nestLevel < currentLevel) {
                                            newExpanded.add(i)
                                            currentLevel = entry.nestLevel
                                        }
                                        if (currentLevel == 0) break
                                    }

                                    expandedEntryIndices = newExpanded

                                    val visibleIdx = visibleItemInfo.indexOfFirst { it.second == targetEntry }

                                    if (visibleIdx != -1) {
                                        var attempts = 0
                                        while (listState.layoutInfo.totalItemsCount <= visibleIdx && attempts < 10) {
                                            delay(30)
                                            attempts++
                                        }

                                        listState.animateScrollToItem(visibleIdx)
                                    }
                                }
                            }
                            Unit
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            OutlinedTextField(
                                value = tocSearchQuery,
                                onValueChange = { tocSearchQuery = it },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = if (tocSearchQuery.isNotEmpty()) {
                                    {
                                        IconButton(
                                            onClick = { tocSearchQuery = "" },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.tooltip_clear_search),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                } else null,
                                placeholder = { Text(stringResource(R.string.search_chapters_placeholder)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .pointerInput(flatTableOfContents) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            searchFieldCanFocus = true
                                        }
                                    }
                                    .focusProperties { canFocus = searchFieldCanFocus }
                                    .onFocusChanged { state ->
                                        if (!state.isFocused) searchFieldCanFocus = false
                                    }
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TextButton(onClick = { expandedEntryIndices = flatTableOfContents.indices.toSet() }) {
                                    Text(stringResource(R.string.action_expand_all))
                                }
                                TextButton(onClick = { expandedEntryIndices = emptySet() }) {
                                    Text(stringResource(R.string.action_collapse_all))
                                }
                                TextButton(onClick = onScrollToCurrent) {
                                    Text(stringResource(R.string.action_locate))
                                }
                            }

                            HorizontalDivider()

                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                if (visibleItemInfo.isEmpty() && isSearchingToc) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.no_chapters_matching, tocSearchQuery.trim()),
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(end = 12.dp)
                                    ) {
                                        items(
                                            items = visibleItemInfo,
                                            key = { it.second.title + it.first }
                                        ) { item ->
                                            val (originalIndex, entry) = item

                                            val nextItem = flatTableOfContents.getOrNull(originalIndex + 1)
                                            val hasChildren = nextItem != null && nextItem.nestLevel > entry.nestLevel
                                            val isExpanded = expandedEntryIndices.contains(originalIndex)
                                            val isCurrentChapter = entry == currentTocEntry

                                            PdfTocTreeItem(
                                                label = entry.title,
                                                nestLevel = entry.nestLevel,
                                                isExpanded = isExpanded,
                                                hasChildren = hasChildren,
                                                isCurrent = isCurrentChapter,
                                                onToggleExpand = {
                                                    expandedEntryIndices = if (isExpanded) {
                                                        expandedEntryIndices - originalIndex
                                                    } else {
                                                        expandedEntryIndices + originalIndex
                                                    }
                                                },
                                                onClick = {
                                                    onCloseDrawer()
                                                    onPageSelected(entry.pageIndex)
                                                }
                                            )
                                        }
                                    }
                                }

                                VerticalScrollbar(
                                    listState = listState,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        }
                    }
                }

                PdfDrawerSection.BOOKMARKS -> { // Bookmarks Page
                    if (bookmarks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.no_bookmarks_yet),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        var bookmarkMenuExpandedFor by remember { mutableStateOf<PdfBookmark?>(null) }
                        var showDeleteConfirmDialogFor by remember { mutableStateOf<PdfBookmark?>(null) }
                        var showRenameBookmarkDialog by remember { mutableStateOf<PdfBookmark?>(null) }

                        val sortedBookmarks = remember(bookmarks) {
                            bookmarks.sortedBy { it.pageIndex }
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(
                                items = sortedBookmarks, key = { index, bookmark ->
                                    "bm_${index}_${bookmark.pageIndex}"
                                }) { _, bookmark ->
                                ListItem(headlineContent = {
                                    Text(
                                        bookmark.title,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }, supportingContent = {
                                    Text(
                                        stringResource(R.string.page_of_pages, bookmark.pageIndex + 1, bookmark.totalPages),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }, trailingContent = {
                                    Box {
                                        IconButton(
                                            onClick = {
                                                bookmarkMenuExpandedFor = bookmark
                                            }) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = stringResource(R.string.content_desc_more_options_bookmark)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = bookmarkMenuExpandedFor == bookmark,
                                            onDismissRequest = {
                                                bookmarkMenuExpandedFor = null
                                            }) {
                                            DropdownMenuItem(text = {
                                                Text(stringResource(R.string.action_rename))
                                            }, onClick = {
                                                showRenameBookmarkDialog = bookmark
                                                bookmarkMenuExpandedFor = null
                                            })
                                            DropdownMenuItem(text = {
                                                Text(stringResource(R.string.action_delete))
                                            }, onClick = {
                                                showDeleteConfirmDialogFor = bookmark
                                                bookmarkMenuExpandedFor = null
                                            })
                                        }
                                    }
                                }, modifier = Modifier
                                    .clickable {
                                        onCloseDrawer()
                                        onPageSelected(bookmark.pageIndex)
                                    }
                                    .testTag(
                                        "BookmarkItem_${bookmark.pageIndex}"
                                    ))
                                HorizontalDivider()
                            }
                        }

                        showRenameBookmarkDialog?.let { bookmarkToRename ->
                            var newTitle by remember(bookmarkToRename) { mutableStateOf(bookmarkToRename.title) }

                            AlertDialog(onDismissRequest = {
                                showRenameBookmarkDialog = null
                            }, title = { Text(stringResource(R.string.dialog_rename_bookmark)) }, text = {
                                OutlinedTextField(
                                    value = newTitle,
                                    onValueChange = { newTitle = it },
                                    label = { Text(stringResource(R.string.label_new_title)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }, confirmButton = {
                                TextButton(
                                    onClick = {
                                        onRenameBookmark(bookmarkToRename, newTitle)
                                        showRenameBookmarkDialog = null
                                    }) { Text(stringResource(R.string.action_save)) }
                            }, dismissButton = {
                                TextButton(
                                    onClick = {
                                        showRenameBookmarkDialog = null
                                    }) { Text(stringResource(R.string.action_cancel)) }
                            })
                        }

                        showDeleteConfirmDialogFor?.let { bookmarkToDelete ->
                            AlertDialog(onDismissRequest = {
                                showDeleteConfirmDialogFor = null
                            }, title = { Text(stringResource(R.string.dialog_delete_bookmark)) }, text = {
                                Text(
                                    stringResource(R.string.dialog_delete_bookmark_desc)
                                )
                            }, confirmButton = {
                                TextButton(
                                    onClick = {
                                        onDeleteBookmark(bookmarkToDelete)
                                        showDeleteConfirmDialogFor = null
                                    }) { Text(stringResource(R.string.action_delete)) }
                            }, dismissButton = {
                                TextButton(
                                    onClick = {
                                        showDeleteConfirmDialogFor = null
                                    }) { Text(stringResource(R.string.action_cancel)) }
                            })
                        }
                    }
                }
                PdfDrawerSection.HIGHLIGHTS -> { // Highlights Page
                    if (userHighlights.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.no_highlights_yet),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        var showDeleteConfirmDialogFor by remember { mutableStateOf<PdfUserHighlight?>(null) }
                        var filterWithNotesOnly by remember { mutableStateOf(false) }

                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !filterWithNotesOnly,
                                    onClick = { filterWithNotesOnly = false },
                                    label = { Text(stringResource(R.string.read_status_all)) }
                                )
                                FilterChip(
                                    selected = filterWithNotesOnly,
                                    onClick = { filterWithNotesOnly = true },
                                    label = { Text(stringResource(R.string.filter_with_notes)) }
                                )
                            }

                            val filteredHighlights = if (filterWithNotesOnly) {
                                userHighlights.filter { !it.note.isNullOrBlank() }
                            } else {
                                userHighlights.toList()
                            }

                            val sortedHighlights = remember(filteredHighlights) {
                                filteredHighlights.sortedBy { it.pageIndex }
                            }

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(
                                    items = sortedHighlights,
                                    key = { _, highlight -> highlight.id }
                                ) { _, highlight ->
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                text = highlight.text.ifBlank { stringResource(R.string.msg_highlighted_section_default) },
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        supportingContent = {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    val displayColor = highlight.resolvedColor(customHighlightColors)

                                                    Box(
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .background(displayColor, CircleShape)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        stringResource(R.string.pdf_page_short, highlight.pageIndex + 1),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (!highlight.note.isNullOrBlank()) {
                                                    Spacer(Modifier.height(8.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = highlight.note,
                                                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                                            modifier = Modifier.padding(12.dp),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        trailingContent = {
                                            Box {
                                                var highlightMenuExpanded by remember { mutableStateOf(false) }
                                                IconButton(onClick = { highlightMenuExpanded = true }) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.content_desc_options))
                                                }
                                                DropdownMenu(
                                                    expanded = highlightMenuExpanded,
                                                    onDismissRequest = { highlightMenuExpanded = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                stringResource(
                                                                    if (highlight.note.isNullOrBlank()) R.string.menu_add_note else R.string.menu_edit_note
                                                                )
                                                            )
                                                        },
                                                        onClick = {
                                                            onNoteRequested(highlight.id)
                                                            highlightMenuExpanded = false
                                                            onCloseDrawer()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.action_delete)) },
                                                        onClick = {
                                                            showDeleteConfirmDialogFor = highlight
                                                            highlightMenuExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.clickable {
                                            onCloseDrawer()
                                            onPageSelected(highlight.pageIndex)
                                        }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }

                        showDeleteConfirmDialogFor?.let { highlightToDelete ->
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirmDialogFor = null },
                                title = { Text(stringResource(R.string.dialog_delete_highlight)) },
                                text = { Text(stringResource(R.string.dialog_delete_highlight_desc)) },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            onDeleteHighlight(highlightToDelete)
                                            showDeleteConfirmDialogFor = null
                                        }
                                    ) { Text(stringResource(R.string.action_delete)) }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showDeleteConfirmDialogFor = null }
                                    ) { Text(stringResource(R.string.action_cancel)) }
                                }
                            )
                        }
                    }
                }
                PdfDrawerSection.PAGES -> { // Pages Page
                    val listState = rememberLazyListState()
                    val pageRows = remember(totalPages) { (0 until totalPages).chunked(3) }

                    val currentRowIndex = currentPage / 3

                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(
                                onClick = {
                                    drawerScope.launch {
                                        if (currentRowIndex in pageRows.indices) {
                                            listState.animateScrollToItem(currentRowIndex)
                                        }
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.action_locate))
                            }
                        }

                        HorizontalDivider()

                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(end = 12.dp)
                            ) {
                                items(pageRows, key = { it.firstOrNull() ?: 0 }) { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        row.forEach { pageIdx ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(0.707f)
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .border(
                                                        width = if (currentPage == pageIdx) 2.dp else 1.dp,
                                                        color = if (currentPage == pageIdx) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.1f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .clickable {
                                                        onCloseDrawer()
                                                        onPageSelected(pageIdx)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val thumbPageId = remember(documentKey, pageIdx) {
                                                    pdfRenderPageId(documentKey, pageIdx, VirtualPage.PdfPage(pageIdx))
                                                }
                                                var thumb by remember(thumbPageId) { mutableStateOf(PdfThumbnailCache.get(thumbPageId)) }

                                                LaunchedEffect(thumbPageId, pdfDocument) {
                                                    if (thumb == null && pdfDocument != null) {
                                                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                            try {
                                                                val cached = PdfThumbnailCache.get(thumbPageId)
                                                                if (cached != null) {
                                                                    thumb = cached
                                                                } else {
                                                                    pdfDocument.openPage(pageIdx)?.use { p ->
                                                                        val w = p.getPageWidthPoint()
                                                                        val h = p.getPageHeightPoint()
                                                                        val ratio = if (h > 0) w.toFloat() / h.toFloat() else 1f
                                                                        val thumbW = 200
                                                                        val thumbH = (thumbW / ratio).toInt().coerceAtLeast(1)
                                                                        val bmp = createBitmap(thumbW, thumbH)
                                                                        bmp.eraseColor(android.graphics.Color.WHITE)
                                                                        p.renderPageBitmap(bmp, 0, 0, thumbW, thumbH, false)
                                                                        PdfThumbnailCache.put(thumbPageId, bmp)
                                                                        thumb = bmp
                                                                    }
                                                                }
                                                            } catch (_: Exception) { }
                                                        }
                                                    }
                                                }

                                                if (thumb != null) {
                                                    Image(
                                                        bitmap = thumb!!.asImageBitmap(),
                                                        contentDescription = stringResource(R.string.pdf_page_short, pageIdx + 1),
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }

                                                Text(
                                                    text = "${pageIdx + 1}",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(4.dp)
                                                        .background(
                                                            Color.Black.copy(alpha = 0.5f),
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            }
                            VerticalScrollbar(
                                listState = listState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }
            }
        }
    }
}
