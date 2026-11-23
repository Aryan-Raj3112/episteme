/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.epubreader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import com.aryan.reader.R
import com.aryan.reader.RenderMode
import com.aryan.reader.cardTitle
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.ReaderMotionPolicy
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.epub.EpubTocEntry
import kotlinx.coroutines.launch
import com.aryan.reader.shared.ui.toBookmarkRow
import com.aryan.reader.shared.epub.EpubDrawerCapabilities
import com.aryan.reader.shared.epub.EpubDrawerSection
import com.aryan.reader.shared.epub.epubDrawerSections
import com.aryan.reader.shared.ui.SharedDrawerScrollbar
import com.aryan.reader.shared.ui.SharedDrawerTabRow
import com.aryan.reader.shared.ui.SharedDrawerTabsHeader
import com.aryan.reader.shared.ui.SharedMobileEpubToc
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
fun EpubReaderDrawerSheet(
    chapters: List<EpubChapter>,
    tableOfContents: List<EpubTocEntry>,
    activeFragmentId: String?,
    readerImages: List<EpubReaderImageReference>,
    bookmarks: Set<Bookmark>,
    userHighlights: List<UserHighlight>,
    currentChapterIndex: Int,
    currentChapterInPaginatedMode: Int?,
    renderMode: RenderMode,
    onNavigateToChapter: (Int) -> Unit,
    onNavigateToTocEntry: (EpubTocEntry) -> Unit,
    onNavigateToImage: (EpubReaderImageReference) -> Unit,
    onDownloadImage: (EpubReaderImageReference) -> Unit,
    onNavigateToBookmark: (Bookmark) -> Unit,
    onNavigateToHighlight: (UserHighlight) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onRenameBookmark: (Bookmark, String) -> Unit,
    onDeleteHighlight: (UserHighlight) -> Unit,
    onEditNote: (UserHighlight) -> Unit,
    activeHighlightPalette: List<Int>,
    onOpenPaletteManager: () -> Unit,
    onHighlightColorChange: (UserHighlight, Int) -> Unit,
    onExportAnnotations: (() -> Unit)? = null,
    readerMotionPolicy: ReaderMotionPolicy = ReaderMotionPolicy(),
    textTabs: List<RecentFileItem> = emptyList(),
    activeTextTabBookId: String? = null,
    useFileNameAsDisplayName: Boolean = false,
    onTextTabSelected: (String) -> Unit = {},
    onTextTabClosed: (String) -> Unit = {},
    onNewTextTab: () -> Unit = {},
) {
    val drawerSections = remember(textTabs.isNotEmpty()) {
        epubDrawerSections(
            EpubDrawerCapabilities(
                tabsEnabled = textTabs.isNotEmpty(),
                hasOpenTabs = textTabs.isNotEmpty(),
            )
        )
    }
    ModalDrawerSheet(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
    ) {
        val drawerPagerState = rememberPagerState(pageCount = { drawerSections.size })
        val drawerScope = rememberCoroutineScope()

        Column(modifier = Modifier.fillMaxSize()) {

            LaunchedEffect(drawerSections.size) {
                if (drawerPagerState.currentPage >= drawerSections.size) {
                    drawerPagerState.scrollToPage(drawerSections.lastIndex.coerceAtLeast(0))
                }
            }

            val selectedSectionIndex = drawerPagerState.currentPage.coerceIn(0, drawerSections.lastIndex)

            ScrollableTabRow(
                selectedTabIndex = selectedSectionIndex,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                drawerSections.forEachIndexed { index, section ->
                    Tab(
                        selected = selectedSectionIndex == index,
                        onClick = {
                            drawerScope.launch {
                                if (readerMotionPolicy.animationsEnabled) drawerPagerState.animateScrollToPage(index)
                                else drawerPagerState.scrollToPage(index)
                            }
                        },
                        text = { Text(stringResource(section.resourceId())) }
                    )
                }
            }

            HorizontalPager(
                state = drawerPagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (drawerSections[page]) {
                    EpubDrawerSection.TABS -> EpubTabsDrawerPage(
                        tabs = textTabs,
                        activeTabBookId = activeTextTabBookId,
                        useFileNameAsDisplayName = useFileNameAsDisplayName,
                        onTabSelected = onTextTabSelected,
                        onTabClosed = onTextTabClosed,
                        onNewTab = onNewTextTab,
                    )
                    EpubDrawerSection.CHAPTERS -> ChaptersList(
                        chapters = chapters,
                        tocEntries = tableOfContents,
                        currentChapterIndex = currentChapterIndex,
                        currentChapterInPaginatedMode = currentChapterInPaginatedMode,
                        renderMode = renderMode,
                        onNavigateToTocEntry = onNavigateToTocEntry,
                        onNavigateToChapter = onNavigateToChapter,
                        activeFragmentId = activeFragmentId
                    )
                    EpubDrawerSection.BOOKMARKS -> BookmarksList(
                        bookmarks = bookmarks,
                        onNavigateToBookmark = onNavigateToBookmark,
                        onRenameBookmark = onRenameBookmark,
                        onDeleteBookmark = onDeleteBookmark
                    )
                    EpubDrawerSection.ANNOTATIONS -> HighlightsList(
                        userHighlights = userHighlights,
                        chapters = chapters,
                        onNavigateToHighlight = onNavigateToHighlight,
                        onDeleteHighlight = onDeleteHighlight,
                        onEditNote = onEditNote,
                        activeHighlightPalette = activeHighlightPalette,
                        onOpenPaletteManager = onOpenPaletteManager,
                        onHighlightColorChange = onHighlightColorChange,
                        onExportAnnotations = onExportAnnotations
                    )
                    EpubDrawerSection.IMAGES -> ImagesList(
                        readerImages = readerImages,
                        onNavigateToImage = onNavigateToImage,
                        onDownloadImage = onDownloadImage
                    )
                }
            }
        }
    }
}

@StringRes
private fun EpubDrawerSection.resourceId(): Int = when (this) {
    EpubDrawerSection.TABS -> R.string.tab_tabs
    EpubDrawerSection.CHAPTERS -> R.string.tab_chapters
    EpubDrawerSection.BOOKMARKS -> R.string.tab_bookmarks
    EpubDrawerSection.ANNOTATIONS -> R.string.tab_annotations
    EpubDrawerSection.IMAGES -> R.string.tab_images
}

/**
 * The open text tabs of this reader, if any. Tapping a row switches to that tab, so it goes through
 * exactly the same path as the tab strip; closing here keeps the reader on a tab that still exists.
 */
@Composable
private fun EpubTabsDrawerPage(
    tabs: List<RecentFileItem>,
    activeTabBookId: String?,
    useFileNameAsDisplayName: Boolean,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTab: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SharedDrawerTabsHeader(
            title = stringResource(R.string.active_tabs),
            openTabCount = tabs.size,
            newTabContentDescription = stringResource(R.string.content_desc_new_tab),
            onNewTab = onNewTab,
        )

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)
        ) {
            items(tabs, key = { "epub_drawer_tab_${it.bookId}" }) { tab ->
                SharedDrawerTabRow(
                    title = tab.cardTitle(useFileNameAsDisplayName),
                    supportingText = tab.author,
                    isSelected = tab.bookId == activeTabBookId,
                    progress = tab.progressPercentage?.let { (it / 100f).coerceIn(0f, 1f) },
                    onSelect = { onTabSelected(tab.bookId) },
                    onClose = { onTabClosed(tab.bookId) },
                    closeContentDescription = stringResource(R.string.close_tab),
                    testTag = "EpubDrawerTab_${tab.bookId}",
                    closeTestTag = "EpubDrawerTabClose_${tab.bookId}",
                )
            }
        }
    }
}

/**
 * Android TOC drawer. The search/expand/locate/projection logic is the shared, unit-tested
 * implementation; this only resolves which chapter path counts as "current" (it differs between
 * paginated and vertical-scroll modes) and maps row indices back to Android navigation.
 */
@Composable
private fun ChaptersList(
    chapters: List<EpubChapter>,
    tocEntries: List<EpubTocEntry>,
    currentChapterIndex: Int,
    currentChapterInPaginatedMode: Int?,
    renderMode: RenderMode,
    activeFragmentId: String?,
    onNavigateToTocEntry: (EpubTocEntry) -> Unit,
    onNavigateToChapter: (Int) -> Unit
) {
    val effectiveToc = remember(tocEntries, chapters) {
        tocEntries.ifEmpty {
            chapters.map { EpubTocEntry(it.title, it.absPath, null, it.depth) }
        }
    }

    val currentChapterPath = remember(chapters, currentChapterIndex, currentChapterInPaginatedMode, renderMode) {
        val idx = when (renderMode) {
            RenderMode.PAGINATED -> currentChapterInPaginatedMode ?: -1
            RenderMode.VERTICAL_SCROLL -> currentChapterIndex
        }
        chapters.getOrNull(idx)?.absPath
    }

    // A fragment supplied by the renderer wins; otherwise fall back to the first entry that
    // points at the current chapter.
    val activeIndex = remember(effectiveToc, currentChapterPath, activeFragmentId) {
        val exact = effectiveToc.indexOfFirst {
            it.absolutePath == currentChapterPath && it.fragmentId == activeFragmentId
        }
        if (exact >= 0) {
            exact
        } else {
            effectiveToc.indexOfFirst { it.absolutePath == currentChapterPath }.takeIf { it >= 0 }
        }
    }

    com.aryan.reader.shared.ui.SharedMobileEpubToc(
        entries = effectiveToc,
        activeIndex = activeIndex,
        onEntryClick = { originalIndex, entry ->
            if (tocEntries.isEmpty()) {
                onNavigateToChapter(originalIndex)
            } else {
                onNavigateToTocEntry(entry)
            }
        },
        labelOf = { it.label },
        depthOf = { it.depth },
        keyOf = { index, entry -> "${entry.absolutePath}_${entry.fragmentId}_$index" },
        collapseDescription = stringResource(R.string.content_desc_collapse),
        expandDescription = stringResource(R.string.content_desc_expand),
        scrollbar = { listState ->
            SharedDrawerScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun TocTreeItem(
    label: String,
    depth: Int,
    isExpanded: Boolean,
    hasChildren: Boolean,
    isCurrent: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit
) {
    com.aryan.reader.shared.ui.SharedAndroidEpubTocTreeItem(
        label = label,
        depth = depth,
        isExpanded = isExpanded,
        hasChildren = hasChildren,
        isCurrent = isCurrent,
        collapseDescription = stringResource(R.string.content_desc_collapse),
        expandDescription = stringResource(R.string.content_desc_expand),
        onToggleExpand = onToggleExpand,
        onClick = onClick,
    )
}

@Composable
private fun BookmarksList(
    bookmarks: Set<Bookmark>,
    onNavigateToBookmark: (Bookmark) -> Unit,
    onRenameBookmark: (Bookmark, String) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit
) {
    val context = LocalContext.current
    val bookmarkDefaultLabel = stringResource(R.string.content_desc_bookmark)
    val bookmarkPageOf: (Int, Int) -> String = { page, total ->
        context.getString(R.string.page_of_format, page, total)
    }
    com.aryan.reader.shared.ui.SharedEpubBookmarksList(
        bookmarks = bookmarks.toList(),
        rowOf = { bookmark ->
            bookmark.toBookmarkRow(
                defaultLabel = bookmarkDefaultLabel,
                pageOf = bookmarkPageOf
            )
        },
        strings = com.aryan.reader.shared.ui.SharedEpubBookmarkStrings(
            empty = stringResource(R.string.no_bookmarks_yet),
            defaultLabel = stringResource(R.string.content_desc_bookmark),
            pageOf = { page, total -> context.getString(R.string.page_of_format, page, total) },
            moreOptionsDescription = stringResource(R.string.content_desc_more_options_bookmark),
            renameAction = stringResource(R.string.action_rename),
            deleteAction = stringResource(R.string.action_delete),
            renameDialogTitle = stringResource(R.string.dialog_rename_bookmark),
            newNameLabel = stringResource(R.string.label_new_name),
            saveAction = stringResource(R.string.action_save),
            cancelAction = stringResource(R.string.action_cancel),
            deleteDialogTitle = stringResource(R.string.dialog_delete_bookmark),
            deleteDialogDescription = stringResource(R.string.dialog_delete_bookmark_desc),
        ),
        onNavigateToBookmark = onNavigateToBookmark,
        onRenameBookmark = onRenameBookmark,
        onDeleteBookmark = onDeleteBookmark,
        scrollbar = { listState ->
            SharedDrawerScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        },
    )
}

@Composable
private fun ImagesList(
    readerImages: List<EpubReaderImageReference>,
    onNavigateToImage: (EpubReaderImageReference) -> Unit,
    onDownloadImage: (EpubReaderImageReference) -> Unit
) {
    com.aryan.reader.shared.ui.SharedMobileEpubImages(
        images = readerImages,
        rowOf = { it.toDrawerImage() },
        onImageClick = onNavigateToImage,
        onDownloadImage = onDownloadImage,
        modifier = Modifier.fillMaxSize()
    )
}
@Composable
private fun HighlightsList(
    userHighlights: List<UserHighlight>,
    chapters: List<EpubChapter>,
    onNavigateToHighlight: (UserHighlight) -> Unit,
    onDeleteHighlight: (UserHighlight) -> Unit,
    onEditNote: (UserHighlight) -> Unit,
    activeHighlightPalette: List<Int>,
    onOpenPaletteManager: () -> Unit,
    onHighlightColorChange: (UserHighlight, Int) -> Unit,
    onExportAnnotations: (() -> Unit)? = null
) {
    val unknownChapter = stringResource(R.string.unknown_chapter)
    com.aryan.reader.shared.ui.SharedMobileEpubHighlights(
        highlights = userHighlights,
        chapterTitleOf = { chapterIndex ->
            chapters.getOrNull(chapterIndex)?.title ?: unknownChapter
        },
        // Android's palette is already ARGB slots, so it passes straight through — the shared
        // model no longer launders it through the named-color enum.
        palette = com.aryan.reader.shared.ReaderHighlightPalette(colors = activeHighlightPalette),
        onHighlightClick = onNavigateToHighlight,
        onHighlightEdit = onEditNote,
        onHighlightColorChange = onHighlightColorChange,
        onDeleteHighlight = onDeleteHighlight,
        onOpenPaletteManager = onOpenPaletteManager,
        onExportAnnotations = onExportAnnotations,
        scrollbar = { listState ->
            SharedDrawerScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        },
        modifier = Modifier.fillMaxSize()
    )
}
