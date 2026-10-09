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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import com.aryan.reader.R
import com.aryan.reader.RenderMode
import com.aryan.reader.shared.ReaderMotionPolicy
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.epub.EpubTocEntry
import kotlinx.coroutines.launch
import com.aryan.reader.shared.ui.toBookmarkRow
import com.aryan.reader.shared.ui.SharedDrawerScrollbar
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
) {
    ModalDrawerSheet(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
    ) {
        val drawerPagerState = rememberPagerState(pageCount = { 4 })
        val drawerScope = rememberCoroutineScope()

        Column(modifier = Modifier.fillMaxSize()) {

            ScrollableTabRow(
                selectedTabIndex = drawerPagerState.currentPage,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = drawerPagerState.currentPage == 0,
                    onClick = {
                        drawerScope.launch {
                            if (readerMotionPolicy.animationsEnabled) drawerPagerState.animateScrollToPage(0)
                            else drawerPagerState.scrollToPage(0)
                        }
                    },
                    text = { Text(stringResource(R.string.tab_chapters)) }
                )
                Tab(
                    selected = drawerPagerState.currentPage == 1,
                    onClick = {
                        drawerScope.launch {
                            if (readerMotionPolicy.animationsEnabled) drawerPagerState.animateScrollToPage(1)
                            else drawerPagerState.scrollToPage(1)
                        }
                    },
                    text = { Text(stringResource(R.string.tab_bookmarks)) }
                )
                Tab(
                    selected = drawerPagerState.currentPage == 2,
                    onClick = {
                        drawerScope.launch {
                            if (readerMotionPolicy.animationsEnabled) drawerPagerState.animateScrollToPage(2)
                            else drawerPagerState.scrollToPage(2)
                        }
                    },
                    text = { Text(stringResource(R.string.tab_annotations)) }
                )
                Tab(
                    selected = drawerPagerState.currentPage == 3,
                    onClick = {
                        drawerScope.launch {
                            if (readerMotionPolicy.animationsEnabled) drawerPagerState.animateScrollToPage(3)
                            else drawerPagerState.scrollToPage(3)
                        }
                    },
                    text = { Text(stringResource(R.string.tab_images)) }
                )
            }

            HorizontalPager(
                state = drawerPagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> ChaptersList(
                        chapters = chapters,
                        tocEntries = tableOfContents,
                        currentChapterIndex = currentChapterIndex,
                        currentChapterInPaginatedMode = currentChapterInPaginatedMode,
                        renderMode = renderMode,
                        onNavigateToTocEntry = onNavigateToTocEntry,
                        onNavigateToChapter = onNavigateToChapter,
                        activeFragmentId = activeFragmentId
                    )
                    1 -> BookmarksList(
                        bookmarks = bookmarks,
                        onNavigateToBookmark = onNavigateToBookmark,
                        onRenameBookmark = onRenameBookmark,
                        onDeleteBookmark = onDeleteBookmark
                    )
                    2 -> HighlightsList(
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
                    3 -> ImagesList(
                        readerImages = readerImages,
                        onNavigateToImage = onNavigateToImage,
                        onDownloadImage = onDownloadImage
                    )
                }
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
