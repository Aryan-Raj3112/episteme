package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.BuiltInReaderThemes
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ReaderTexture
import com.aryan.reader.shared.ReaderFont
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.PageInfoMode
import com.aryan.reader.shared.PageInfoPosition
import com.aryan.reader.shared.SystemUiMode
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSearchOptions
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderSpreadLayout
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubTocEntry
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import com.aryan.reader.shared.reader.layoutSignature
import com.aryan.reader.shared.toReaderSettings
import com.aryan.reader.shared.generated.resources.Res
import com.aryan.reader.shared.generated.resources.classy_fabric
import com.aryan.reader.shared.generated.resources.ep_naturalwhite
import com.aryan.reader.shared.generated.resources.format_align_justify
import com.aryan.reader.shared.generated.resources.format_align_left
import com.aryan.reader.shared.generated.resources.format_align_right
import com.aryan.reader.shared.generated.resources.grey_wash_wall
import com.aryan.reader.shared.generated.resources.light_veneer
import com.aryan.reader.shared.generated.resources.retina_wood
import com.aryan.reader.shared.generated.resources.retro_intro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.roundToInt
import kotlin.math.min
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.painterResource

data class SharedMobileEpubReaderSnapshot(
    val locator: ReaderLocator,
    val settings: ReaderSettings,
    val bookmarks: List<ReaderBookmark>,
    val highlights: List<UserHighlight>,
    val progressPercent: Float,
    val pageIndex: Int,
    val pageCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileEpubReaderScreen(
    book: BookItem,
    onBack: () -> Unit,
    onReaderStateChange: (SharedMobileEpubReaderSnapshot) -> Unit = {},
    onMetadataLoaded: (title: String, author: String?) -> Unit = { _, _ -> },
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onSystemUiAppearanceChange: (hidden: Boolean, lightContent: Boolean, backgroundArgb: Long) -> Unit = { _, _, _ -> },
    customReaderThemes: List<ReaderTheme> = emptyList(),
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit = {},
    readerDefaultSettings: ReaderSettings = ReaderSettings(),
    onReaderDefaultSettingsChange: (ReaderSettings) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val loadState = rememberSharedMobileEpubLoadState(book)
    val loadedBook = loadState.book
    val localTts = rememberSharedMobileEpubLocalTts()
    var settings by remember(book.id) {
        mutableStateOf(
            book.readerSettings ?: readerDefaultSettings
        )
    }
    var pages by remember(book.id) { mutableStateOf<List<ReaderPage>>(emptyList()) }
    var currentLocator by remember(book.id) { mutableStateOf(book.readerPosition) }
    var currentPageIndex by remember(book.id) { mutableStateOf(book.lastPageIndex ?: 0) }
    var currentChapterIndex by remember(book.id) {
        mutableIntStateOf(book.readerPosition?.chapterIndex?.coerceAtLeast(0) ?: 0)
    }
    var bookmarks by remember(book.id) { mutableStateOf(book.readerBookmarks) }
    var highlights by remember(book.id) { mutableStateOf(book.readerHighlights) }
    // Match Android's distraction-free reader entry; a reader tap reveals chrome.
    var showChrome by remember(book.id) { mutableStateOf(false) }
    var showFormatSheet by remember(book.id) { mutableStateOf(false) }
    var isLocalFormatMode by remember(book.id) { mutableStateOf(false) }
    var showThemeSheet by remember(book.id) { mutableStateOf(false) }
    var showVisualOptionsSheet by remember(book.id) { mutableStateOf(false) }
    var showSearch by remember(book.id) { mutableStateOf(false) }
    var searchQuery by remember(book.id) { mutableStateOf("") }
    var searchResults by remember(book.id) { mutableStateOf<List<SharedMobileEpubSearchResult>>(emptyList()) }
    var searchResultIndex by remember(book.id) { mutableIntStateOf(-1) }
    var pullDirection by remember(book.id) { mutableStateOf<String?>(null) }
    var pullProgress by remember(book.id) { mutableStateOf(0f) }
    var showSlider by remember(book.id) { mutableStateOf(false) }
    var showMore by remember(book.id) { mutableStateOf(false) }
    var showFileInfo by remember(book.id) { mutableStateOf(false) }
    var keepScreenOn by remember(book.id) { mutableStateOf(false) }
    var autoScroll by remember(book.id) { mutableStateOf(false) }
    var drawerTab by remember(book.id) { mutableStateOf(0) }
    var selectedTocIndex by remember(book.id) { mutableIntStateOf(-1) }
    var explicitNavigationLocator by remember(book.id) { mutableStateOf<ReaderLocator?>(null) }
    var explicitNavigationFragment by remember(book.id) { mutableStateOf<String?>(null) }
    var explicitNavigationChunkIndex by remember(book.id) { mutableStateOf<Int?>(null) }
    var explicitNavigationChunkHtml by remember(book.id) { mutableStateOf<String?>(null) }
    var navigationRequestId by remember(book.id) { mutableLongStateOf(0L) }
    var commandScript by remember(book.id) { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(book.id, currentChapterIndex) {
        if (localTts.state != SharedMobileEpubLocalTtsState.IDLE) localTts.stop()
    }

    fun openReaderDrawer(tab: Int? = null) {
        tab?.let { drawerTab = it }
        scope.launch {
            drawerState.open()
            focusManager.clearFocus(force = true)
        }
    }

    LaunchedEffect(loadedBook?.id) {
        loadedBook?.let {
            currentChapterIndex = currentChapterIndex.coerceIn(0, it.chapters.lastIndex.coerceAtLeast(0))
            if (selectedTocIndex < 0) {
                val currentHref = currentLocator?.href?.normalizeMobileEpubPath()
                selectedTocIndex = it.tableOfContents.indexOfFirst { entry ->
                    currentHref != null && entry.href.normalizeMobileEpubPath() == currentHref
                }
            }
            onMetadataLoaded(it.title, it.author)
        }
    }

    LaunchedEffect(loadedBook, settings.layoutSignature()) {
        val epub = loadedBook ?: return@LaunchedEffect
        if (pages.isNotEmpty()) delay(180)
        val locator = currentLocator ?: book.readerPosition
        val readerState = withContext(Dispatchers.Default) {
            ReaderEngine().createSession(
                book = epub,
                settings = settings,
                initialPageIndex = currentPageIndex,
                initialLocator = locator
            ).reader
        }
        pages = readerState.pages
        currentPageIndex = readerState.currentPageIndex.coerceIn(0, readerState.pages.lastIndex.coerceAtLeast(0))
    }

    LaunchedEffect(keepScreenOn) { onKeepScreenOnChange(keepScreenOn) }
    LaunchedEffect(settings.systemUiMode, settings.darkMode, showChrome) {
        val hidden = when (settings.systemUiMode) {
            SystemUiMode.DEFAULT -> false
            SystemUiMode.SYNC -> !showChrome
            SystemUiMode.HIDDEN -> true
        }
        val backgroundArgb = settings.backgroundColorArgb ?: if (settings.darkMode) 0xFF121212L else 0xFFFFFFFFL
        onSystemUiAppearanceChange(hidden, settings.darkMode, backgroundArgb)
    }
    DisposableEffect(book.id) {
        onDispose {
            onKeepScreenOnChange(false)
            onSystemUiAppearanceChange(false, false, 0xFFFFFFFFL)
        }
    }
    LaunchedEffect(autoScroll) {
        commandScript = if (autoScroll) SharedMobileEpubAutoScrollStartScript else SharedMobileEpubAutoScrollStopScript
        navigationRequestId++
    }
    LaunchedEffect(loadedBook, searchQuery) {
        val epub = loadedBook
        val query = searchQuery.trim()
        searchResults = if (epub == null || query.length < 2) emptyList() else withContext(Dispatchers.Default) {
            epub.searchMobileEpub(query)
        }
        searchResultIndex = -1
    }

    val pageCount = pages.size.coerceAtLeast(1)
    val progress = ((currentPageIndex + 1).toFloat() / pageCount) * 100f
    LaunchedEffect(currentLocator, settings, bookmarks, highlights, currentPageIndex, pageCount) {
        delay(220)
        currentLocator?.let { locator ->
            onReaderStateChange(
                SharedMobileEpubReaderSnapshot(
                    locator = locator,
                    settings = settings,
                    bookmarks = bookmarks,
                    highlights = highlights,
                    progressPercent = progress.coerceIn(0f, 100f),
                    pageIndex = currentPageIndex,
                    pageCount = pageCount
                )
            )
        }
    }

    fun closeReader() {
        currentLocator?.let { locator ->
            onReaderStateChange(
                SharedMobileEpubReaderSnapshot(
                    locator = locator,
                    settings = settings,
                    bookmarks = bookmarks,
                    highlights = highlights,
                    progressPercent = progress.coerceIn(0f, 100f),
                    pageIndex = currentPageIndex,
                    pageCount = pageCount
                )
            )
        }
        onBack()
    }

    fun navigate(locator: ReaderLocator, fragment: String? = null) {
        val epub = loadedBook
        locator.chapterIndex?.let { chapterIndex ->
            epub?.chapters?.lastIndex?.let { lastIndex ->
                currentChapterIndex = chapterIndex.coerceIn(0, lastIndex.coerceAtLeast(0))
            }
        }
        val targetChapterIndex = locator.chapterIndex?.coerceIn(0, epub?.chapters?.lastIndex ?: 0)
        val targetChunks = if (epub != null && targetChapterIndex != null) {
            ReaderHtmlDocumentBuilder.verticalChapterChunks(epub, targetChapterIndex)
        } else {
            emptyList()
        }
        val targetChunkIndex = when {
            fragment != null -> targetChunks.indexOfFirst { it.containsReaderFragment(fragment) }.takeIf { it >= 0 }
            locator.startOffset != null && epub != null && targetChapterIndex != null && targetChunks.isNotEmpty() -> {
                val textLength = epub.chapters[targetChapterIndex].plainText.length.coerceAtLeast(1)
                ((locator.startOffset.toDouble() / textLength.toDouble()) * targetChunks.size)
                    .toInt()
                    .coerceIn(0, targetChunks.lastIndex)
            }
            else -> null
        }
        explicitNavigationLocator = locator
        explicitNavigationFragment = fragment
        explicitNavigationChunkIndex = targetChunkIndex
        explicitNavigationChunkHtml = targetChunkIndex?.let(targetChunks::getOrNull)
        commandScript = null
        navigationRequestId++
        currentLocator = locator
        locator.pageIndex?.let { currentPageIndex = it.coerceIn(0, pageCount - 1) }
    }

    fun navigateChapter(direction: Int) {
        val epub = loadedBook ?: return
        val targetChapterIndex = (currentChapterIndex + direction).coerceIn(0, epub.chapters.lastIndex)
        if (targetChapterIndex == currentChapterIndex) return
        val chapterPages = pages.filter { it.chapterIndex == targetChapterIndex }
        val targetPage = if (direction < 0) chapterPages.lastOrNull() else chapterPages.firstOrNull()
        val locator = targetPage?.toMobileEpubLocator(epub) ?: ReaderLocator(
            chapterIndex = targetChapterIndex,
            chapterId = epub.chapters[targetChapterIndex].id,
            href = epub.chapters[targetChapterIndex].baseHref,
            pageIndex = targetPage?.pageIndex,
            startOffset = targetPage?.startOffset ?: 0,
            endOffset = targetPage?.startOffset ?: 0,
            textQuote = targetPage?.text?.take(120)
        )
        currentChapterIndex = targetChapterIndex
        currentLocator = locator
        currentPageIndex = (targetPage?.pageIndex ?: currentPageIndex).coerceIn(0, pageCount - 1)
        explicitNavigationLocator = if (direction < 0) null else locator
        explicitNavigationFragment = null
        explicitNavigationChunkIndex = null
        explicitNavigationChunkHtml = null
        commandScript = when {
            direction < 0 -> {
                val chunks = ReaderHtmlDocumentBuilder.verticalChapterChunks(epub, targetChapterIndex)
                sharedMobileEpubScrollToEndScript(chunks.lastIndex, chunks.lastOrNull())
            }
            autoScroll -> SharedMobileEpubAutoScrollStartScript
            else -> null
        }
        navigationRequestId++
        selectedTocIndex = epub.tableOfContents.indexOfLast { entry ->
            entry.href.normalizeMobileEpubPath() == epub.chapters[targetChapterIndex].baseHref.orEmpty().normalizeMobileEpubPath() &&
                entry.fragmentId == epub.chapters[targetChapterIndex].fragmentId
        }
    }

    fun navigatePage(direction: Int) {
        val epub = loadedBook ?: return
        if (pages.isEmpty()) return
        val targetPageIndex = when {
            direction < 0 && ReaderSpreadLayout.normalizePageIndex(currentPageIndex, pageCount, settings) > 0 -> ReaderSpreadLayout.previousPageIndex(
                currentPageIndex,
                pageCount,
                settings
            )
            direction > 0 && ReaderSpreadLayout.canGoNext(currentPageIndex, pageCount, settings) ->
                ReaderSpreadLayout.nextPageIndex(currentPageIndex, pageCount, settings)
            else -> return
        }
        pages.getOrNull(targetPageIndex)?.let { page ->
            navigate(page.toMobileEpubLocator(epub))
        }
    }

    fun navigateSearchResult(result: SharedMobileEpubSearchResult) {
        val epub = loadedBook ?: return
        val chapter = epub.chapters.getOrNull(result.chapterIndex) ?: return
        val chunks = ReaderHtmlDocumentBuilder.verticalChapterChunks(epub, result.chapterIndex)
        currentChapterIndex = result.chapterIndex
        currentLocator = ReaderLocator(
            chapterIndex = result.chapterIndex,
            chapterId = chapter.id,
            href = chapter.baseHref,
            startOffset = 0,
            endOffset = 0,
            textQuote = result.snippet
        )
        explicitNavigationLocator = null
        explicitNavigationChunkIndex = result.chunkIndex
        explicitNavigationChunkHtml = chunks.getOrNull(result.chunkIndex)
        commandScript = sharedMobileEpubSearchNavigationScript(result, searchQuery, chunks.getOrNull(result.chunkIndex))
        navigationRequestId++
        showSearch = false
    }

    fun toggleBookmark() {
        val locator = currentLocator ?: return
        val existing = bookmarks.indexOfFirst { it.locator.sameLocation(locator) }
        bookmarks = if (existing >= 0) {
            bookmarks.filterIndexed { index, _ -> index != existing }
        } else {
            val chapterIndex = locator.chapterIndex?.coerceIn(0, loadedBook?.chapters?.lastIndex ?: 0) ?: 0
            bookmarks + ReaderBookmark(
                id = "ios_epub_bookmark_${currentTimestamp()}",
                pageIndex = currentPageIndex,
                chapterTitle = loadedBook?.chapters?.getOrNull(chapterIndex)?.title ?: "Chapter ${chapterIndex + 1}",
                preview = locator.textQuote.orEmpty().ifBlank { "Page ${currentPageIndex + 1}" },
                locator = locator
            )
        }
    }

    val isBookmarked = currentLocator?.let { locator -> bookmarks.any { it.locator.sameLocation(locator) } } == true

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxWidth(0.86f)) {
                Text(
                    loadedBook?.title ?: book.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(20.dp)
                )
                ScrollableTabRow(selectedTabIndex = drawerTab, edgePadding = 0.dp) {
                    listOf("Chapters", "Bookmarks", "Annotations", "Images").forEachIndexed { index, label ->
                        Tab(selected = drawerTab == index, onClick = { drawerTab = index }, text = { Text(label, maxLines = 1) })
                    }
                }
                if (drawerTab == 0) {
                    SharedMobileEpubToc(
                        epub = loadedBook,
                        selectedIndex = selectedTocIndex,
                        onEntryClick = { index, entry ->
                            selectedTocIndex = index
                            loadedBook?.locatorForTocEntry(entry, pages)?.let { locator ->
                                navigate(locator, entry.fragmentId)
                                scope.launch { drawerState.close() }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (drawerTab == 1) {
                    SharedMobileEpubBookmarks(
                        bookmarks = bookmarks,
                        onBookmarkClick = { bookmark ->
                            navigate(bookmark.locator)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (drawerTab == 2) {
                    SharedMobileEpubHighlights(
                        highlights = highlights,
                        onHighlightClick = { highlight ->
                            navigate(highlight.locator)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No extracted images yet")
                    }
                }
            }
        },
        modifier = modifier
    ) {
        Box(Modifier.fillMaxSize().background(settings.readerBackgroundColor())) {
                when {
                    loadState.isLoading -> SharedMobileEpubLoading("Opening EPUB…")
                    loadState.errorMessage != null -> SharedMobileEpubError(loadState.errorMessage)
                    loadedBook != null && pages.isEmpty() -> SharedMobileEpubLoading("Preparing book layout…")
                    loadedBook != null -> {
                        if (settings.readingMode == ReaderReadingMode.PAGINATED) {
                            val visiblePages = ReaderSpreadLayout.visiblePageIndicesForDisplay(
                                currentPageIndex,
                                pages.size,
                                settings
                            ).mapNotNull(pages::getOrNull)
                            SharedNativePaginatedReader(
                                renderPlan = ReaderContentRenderPlan.NativePaginatedPages(
                                    visiblePages = visiblePages,
                                    settings = settings,
                                    searchQuery = searchQuery,
                                    searchOptions = ReaderSearchOptions(),
                                    highlightPalette = ReaderHighlightPalette(),
                                    background = settings.readerBackgroundColor(),
                                    foreground = settings.readerTextColor(),
                                    navigationTarget = ReaderContentNavigationTarget(
                                        locator = explicitNavigationLocator ?: currentLocator,
                                        requestId = navigationRequestId,
                                        readingMode = settings.readingMode
                                    ),
                                    highlights = highlights
                                ),
                                readerFontFamily = FontFamily.Default,
                                searchHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                onVisiblePageChanged = { pageIndex, locator ->
                                    currentPageIndex = pageIndex.coerceIn(0, pageCount - 1)
                                    currentChapterIndex = pages.getOrNull(currentPageIndex)?.chapterIndex
                                        ?: currentChapterIndex
                                    currentLocator = locator ?: currentLocator
                                },
                                onHighlightCreated = { highlight ->
                                    highlights = highlights.filterNot { it.id == highlight.id } + highlight
                                },
                                onReaderTap = { showChrome = !showChrome },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                        val chapterChunks = remember(loadedBook.id, currentChapterIndex) {
                            ReaderHtmlDocumentBuilder.verticalChapterChunks(loadedBook, currentChapterIndex)
                        }
                        val initialVirtualChunkIndex = remember(loadedBook.id, currentChapterIndex) {
                            val textLength = loadedBook.chapters[currentChapterIndex].plainText.length.coerceAtLeast(1)
                            val offset = currentLocator?.takeIf { it.chapterIndex == currentChapterIndex }?.startOffset ?: 0
                            if (chapterChunks.isEmpty()) 0 else {
                                ((offset.toDouble() / textLength.toDouble()) * chapterChunks.size)
                                    .toInt()
                                    .coerceIn(0, chapterChunks.lastIndex)
                            }
                        }
                        val navigationChunkIndex = explicitNavigationChunkIndex ?: initialVirtualChunkIndex
                        val navigationChunkHtml = explicitNavigationChunkHtml ?: chapterChunks.getOrNull(navigationChunkIndex)
                        val initialHtml = remember(loadedBook.id, currentChapterIndex, chapterChunks) {
                            ReaderHtmlDocumentBuilder.verticalDocument(
                                book = loadedBook,
                                settings = settings,
                                navigationLocator = currentLocator,
                                pages = pages,
                                highlightActionsEnabled = false,
                                readerAiFeaturesEnabled = false,
                                cloudTtsEnabled = false,
                                externalLookupEnabled = false,
                                renderedChapterRange = currentChapterIndex..currentChapterIndex,
                                virtualizedChapterChunks = mapOf(currentChapterIndex to chapterChunks),
                                virtualizedInitialChunkIndex = initialVirtualChunkIndex,
                                showChapterTitles = false
                            )
                        }
                        val appearanceScript = remember(settings, pages, currentChapterIndex, loadedBook.id) {
                            ReaderHtmlDocumentBuilder.appearanceUpdateScript(settings) + "\n" +
                                ReaderHtmlDocumentBuilder.pageAnchorsUpdateScript(pages) + "\n" +
                                sharedMobileEpubActiveTocScript(loadedBook, currentChapterIndex) + "\n" +
                                "window.readerIosPullEnabled=${settings.seamlessChapterNavigation};" +
                                "window.readerIosPullMultiplier=${settings.chapterTurnDragMultiplier.coerceIn(0.5f, 2f)};"
                        }
                        val navigationScript = commandScript ?: (explicitNavigationLocator ?: currentLocator)?.let { locator ->
                            sharedMobileEpubNavigationScript(
                                locator = locator,
                                fragment = explicitNavigationFragment,
                                targetChunkIndex = navigationChunkIndex,
                                targetChunkHtml = navigationChunkHtml
                            )
                        }
                        SharedMobileEpubWebView(
                            html = initialHtml,
                            contentChunks = chapterChunks,
                            appearanceScript = appearanceScript,
                            navigationScript = navigationScript,
                            navigationRequestId = navigationRequestId,
                            onBridgeMessage = { method, payload ->
                                when (method) {
                                    "readerPointerActivity" -> showChrome = !showChrome
                                    "readerPositionChanged" -> payload.sharedMobileEpubLocatorOrNull()?.let { position ->
                                        val reportedChapter = position.chapterIndex
                                        if (reportedChapter == null || reportedChapter == currentChapterIndex) {
                                            currentLocator = position
                                            currentPageIndex = (position.pageIndex ?: currentPageIndex).coerceIn(0, pageCount - 1)
                                            commandScript = null
                                        }
                                    }
                                    "readerChapterBoundary" -> when (payload.sharedMobileEpubDirectionOrNull()) {
                                        "previous" -> navigateChapter(-1)
                                        "next" -> navigateChapter(1)
                                    }
                                    "readerChapterPull" -> payload.sharedMobileEpubPullOrNull()?.let { pull ->
                                        pullDirection = pull.first
                                        pullProgress = pull.second
                                    }
                                    "readerActiveTocChanged" -> payload.sharedMobileEpubActiveTocOrNull()?.let { active ->
                                        val activePath = active.href.normalizeMobileEpubPath()
                                        selectedTocIndex = loadedBook.tableOfContents.indexOfFirst { entry ->
                                            entry.href.normalizeMobileEpubPath() == activePath &&
                                                if (active.fragmentId == null) entry.fragmentId == null
                                                else entry.fragmentId == active.fragmentId
                                        }.takeIf { it >= 0 } ?: loadedBook.tableOfContents.indexOfFirst { entry ->
                                            entry.href.normalizeMobileEpubPath() == activePath
                                        }
                                    }
                                    "readerLinkClicked" -> payload.sharedMobileEpubLinkOrNull()?.let { link ->
                                        if (link.href.isExternalEpubLink()) {
                                            openSharedMobileEpubExternalLink(link.href)
                                        } else {
                                            loadedBook.locatorForLink(link.href, link.chapterHref, pages)?.let { (locator, fragment) ->
                                                navigate(locator, fragment)
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        }
                    }
                }

                val chapterTitle = loadedBook?.tableOfContents?.getOrNull(selectedTocIndex)?.label
                    ?: loadedBook?.chapters?.getOrNull(currentChapterIndex)?.title
                    ?: "Chapter ${currentChapterIndex + 1}"
                val pageInfoVisible = when (settings.pageInfoMode) {
                    PageInfoMode.DEFAULT -> true
                    PageInfoMode.SYNC -> showChrome
                    PageInfoMode.HIDDEN -> false
                } && loadedBook != null && pages.isNotEmpty()

                if (pageInfoVisible) {
                    SharedMobileEpubPageInfo(
                        chapterTitle = chapterTitle,
                        chapterIndex = currentChapterIndex,
                        chapterCount = loadedBook?.chapters?.size ?: 0,
                        progressPercent = progress,
                        settings = settings,
                        modifier = Modifier.align(if (settings.pageInfoPosition == PageInfoPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter)
                            .offset(y = if (settings.pageInfoPosition == PageInfoPosition.TOP && showChrome) 55.dp else if (settings.pageInfoPosition == PageInfoPosition.BOTTOM && showChrome) (-45).dp else 0.dp)
                    )
                }
                if (showChrome) {
                    SharedMobileEpubTopBar(
                        title = loadedBook?.title ?: book.displayName,
                        isBookmarked = isBookmarked,
                        showMore = showMore,
                        onShowMoreChange = { showMore = it },
                        onBack = ::closeReader,
                        onTheme = { showThemeSheet = true },
                        onBookmark = ::toggleBookmark,
                        onFormat = { showFormatSheet = true },
                        onVisualOptions = { showVisualOptionsSheet = true },
                        onOpenToc = { openReaderDrawer(0) },
                        onFileInfo = { showFileInfo = true },
                        readingMode = settings.readingMode,
                        onReadingModeChange = { mode ->
                            if (settings.readingMode != mode) {
                                settings = settings.copy(readingMode = mode)
                                showSlider = false
                                autoScroll = false
                            }
                        },
                        localTtsState = localTts.state,
                        onLocalTtsToggle = {
                            when (localTts.state) {
                                SharedMobileEpubLocalTtsState.IDLE -> loadedBook
                                    ?.chapters
                                    ?.getOrNull(currentChapterIndex)
                                    ?.plainText
                                    ?.let(localTts::speak)
                                SharedMobileEpubLocalTtsState.SPEAKING -> localTts.pause()
                                SharedMobileEpubLocalTtsState.PAUSED -> localTts.resume()
                            }
                        },
                        onLocalTtsStop = localTts::stop,
                        keepScreenOn = keepScreenOn,
                        onKeepScreenOnChange = { keepScreenOn = it },
                        autoScroll = autoScroll,
                        onAutoScrollChange = { autoScroll = it },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                    if (loadedBook != null && pages.isNotEmpty()) {
                        SharedMobileEpubBottomBar(
                            canGoPrevious = ReaderSpreadLayout.normalizePageIndex(currentPageIndex, pageCount, settings) > 0,
                            canGoNext = ReaderSpreadLayout.canGoNext(currentPageIndex, pageCount, settings),
                            onPreviousPage = { navigatePage(-1) },
                            onNextPage = { navigatePage(1) },
                            onSlider = { showSlider = !showSlider },
                            onToc = { openReaderDrawer(0) },
                            onFormat = { showFormatSheet = true },
                            onSearch = { showSearch = true },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
                val canPullDirection = (pullDirection == "previous" && currentChapterIndex > 0) ||
                    (pullDirection == "next" && currentChapterIndex < (loadedBook?.chapters?.lastIndex ?: -1))
                if (pullProgress > 0.05f && settings.seamlessChapterNavigation && canPullDirection) {
                    SharedMobileEpubChapterChangeIndicator(
                        direction = pullDirection.orEmpty(),
                        progress = pullProgress,
                        modifier = Modifier.align(if (pullDirection == "previous") Alignment.TopCenter else Alignment.BottomCenter).padding(8.dp)
                    )
                }
                if (showSearch && loadedBook != null) {
                    SharedMobileEpubSearchOverlay(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        results = searchResults,
                        onResultClick = { result ->
                            searchResultIndex = searchResults.indexOf(result)
                            navigateSearchResult(result)
                        },
                        onDismiss = { showSearch = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (!showSearch && searchResultIndex >= 0 && searchResults.isNotEmpty()) {
                    SharedMobileEpubSearchNavigation(
                        current = searchResultIndex,
                        total = searchResults.size,
                        onPrevious = {
                            searchResultIndex = (searchResultIndex - 1).coerceAtLeast(0)
                            navigateSearchResult(searchResults[searchResultIndex])
                        },
                        onNext = {
                            searchResultIndex = (searchResultIndex + 1).coerceAtMost(searchResults.lastIndex)
                            navigateSearchResult(searchResults[searchResultIndex])
                        },
                        onClose = { searchResults = emptyList(); searchResultIndex = -1; searchQuery = "" },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
                    )
                }
                if (showChrome && showSlider && pages.isNotEmpty()) {
                    SharedMobileEpubSlider(
                        pageIndex = currentPageIndex,
                        pageCount = pageCount,
                        settings = settings,
                        onPageSelected = { index -> pages.getOrNull(index)?.let { navigate(it.toMobileEpubLocator(loadedBook)) } },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 60.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }
    }

    if (showFormatSheet) {
        SharedMobileEpubFormatSheet(
            settings = settings,
            isLocalMode = isLocalFormatMode,
            onLocalModeChange = { isLocalFormatMode = it },
            onSettingsChange = {
                val next = it
                settings = next
                if (!isLocalFormatMode) onReaderDefaultSettingsChange(next)
            },
            onDismiss = { showFormatSheet = false }
        )
    }
    if (showThemeSheet) {
        SharedMobileEpubThemeSheet(
            settings = settings,
            customReaderThemes = customReaderThemes,
            onCustomReaderThemesChange = onCustomReaderThemesChange,
            onSettingsChange = { settings = it },
            onDismiss = { showThemeSheet = false }
        )
    }
    if (showVisualOptionsSheet) {
        SharedMobileEpubVisualOptionsSheet(
            settings = settings,
            onSettingsChange = { settings = it },
            onDismiss = { showVisualOptionsSheet = false }
        )
    }
    if (showFileInfo) {
        ModalBottomSheet(onDismissRequest = { showFileInfo = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("File Information", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(loadedBook?.title ?: book.displayName, style = MaterialTheme.typography.titleMedium)
                loadedBook?.author?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("${loadedBook?.chapters?.size ?: 0} chapters · $pageCount reader pages")
                Text(book.displayName, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SharedMobileEpubLoading(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(label)
        }
    }
}

@Composable
private fun SharedMobileEpubError(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(28.dp)
        ) {
            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(48.dp))
            Text("Could not open EPUB", style = MaterialTheme.typography.titleMedium)
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SharedMobileEpubTopBar(
    title: String,
    isBookmarked: Boolean,
    showMore: Boolean,
    onShowMoreChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onTheme: () -> Unit,
    onBookmark: () -> Unit,
    onFormat: () -> Unit,
    onVisualOptions: () -> Unit,
    onOpenToc: () -> Unit,
    onFileInfo: () -> Unit,
    readingMode: ReaderReadingMode,
    onReadingModeChange: (ReaderReadingMode) -> Unit,
    localTtsState: SharedMobileEpubLocalTtsState,
    onLocalTtsToggle: () -> Unit,
    onLocalTtsStop: () -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    autoScroll: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, tonalElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().height(55.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onFormat) { Text("A▤", style = MaterialTheme.typography.titleMedium) }
            IconButton(onClick = onTheme) { Icon(Icons.Default.Palette, contentDescription = "Theme") }
            Box {
                IconButton(onClick = { onShowMoreChange(true) }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = showMore, onDismissRequest = { onShowMoreChange(false) }) {
                    DropdownMenuItem(
                        text = { Text(if (readingMode == ReaderReadingMode.VERTICAL) "Use paginated mode" else "Use vertical mode") },
                        onClick = {
                            onReadingModeChange(
                                if (readingMode == ReaderReadingMode.VERTICAL) ReaderReadingMode.PAGINATED
                                else ReaderReadingMode.VERTICAL
                            )
                            onShowMoreChange(false)
                        },
                        trailingIcon = { Text(if (readingMode == ReaderReadingMode.VERTICAL) "Vertical" else "Pages") }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (isBookmarked) "Remove Bookmark" else "Bookmark this page") },
                        onClick = { onBookmark(); onShowMoreChange(false) },
                        leadingIcon = { Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Visual Options") },
                        onClick = { onVisualOptions(); onShowMoreChange(false) },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Contents") },
                        onClick = { onOpenToc(); onShowMoreChange(false) },
                        leadingIcon = { Icon(Icons.Default.Menu, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (localTtsState) {
                                    SharedMobileEpubLocalTtsState.IDLE -> "Read aloud"
                                    SharedMobileEpubLocalTtsState.SPEAKING -> "Pause reading"
                                    SharedMobileEpubLocalTtsState.PAUSED -> "Resume reading"
                                }
                            )
                        },
                        onClick = { onLocalTtsToggle(); onShowMoreChange(false) }
                    )
                    if (localTtsState != SharedMobileEpubLocalTtsState.IDLE) {
                        DropdownMenuItem(
                            text = { Text("Stop reading") },
                            onClick = { onLocalTtsStop(); onShowMoreChange(false) }
                        )
                    }
                    SharedMobileEpubSwitchMenuItem("Keep Screen On", keepScreenOn, onKeepScreenOnChange)
                    SharedMobileEpubSwitchMenuItem("Auto Scroll", autoScroll, onAutoScrollChange)
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("File Information") },
                        onClick = { onFileInfo(); onShowMoreChange(false) },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedMobileEpubSwitchMenuItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = { onCheckedChange(!checked) },
        trailingIcon = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}

@Composable
private fun SharedMobileEpubBottomBar(
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onSlider: () -> Unit,
    onToc: () -> Unit,
    onFormat: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().height(45.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousPage, enabled = canGoPrevious) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                }
                IconButton(onClick = onSlider) { Icon(Icons.Default.SwapHoriz, contentDescription = "Navigation slider") }
                IconButton(onClick = onToc) { Icon(Icons.Default.Menu, contentDescription = "Contents") }
                IconButton(onClick = onFormat) { Text("Tᵀ", style = MaterialTheme.typography.titleLarge) }
                IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Search") }
                IconButton(onClick = onNextPage, enabled = canGoNext) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                }
            }
        }
    }
}

@Composable
private fun SharedMobileEpubSlider(
    pageIndex: Int,
    pageCount: Int,
    settings: ReaderSettings,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val sliderStepCount = ReaderSpreadLayout.sliderStepCount(pageCount, settings)
    val lastSliderPosition = (sliderStepCount - 1).coerceAtLeast(0)
    var sliderValue by remember(pageIndex, pageCount, settings) {
        mutableStateOf(
            (ReaderSpreadLayout.sliderPositionForPage(pageIndex, pageCount, settings) - 1)
                .coerceIn(0, lastSliderPosition)
                .toFloat()
        )
    }
    Surface(modifier, shape = RoundedCornerShape(18.dp), tonalElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                ReaderSpreadLayout.pageRangeLabel(
                    ReaderSpreadLayout.pageNumberForSliderPosition(
                        sliderValue.roundToInt().coerceIn(0, lastSliderPosition) + 1,
                        pageCount,
                        settings
                    ) - 1,
                    pageCount,
                    settings
                ),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(12.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    onPageSelected(
                        ReaderSpreadLayout.pageNumberForSliderPosition(
                            sliderValue.roundToInt().coerceIn(0, lastSliderPosition) + 1,
                            pageCount,
                            settings
                        ) - 1
                    )
                },
                valueRange = 0f..lastSliderPosition.coerceAtLeast(1).toFloat(),
                enabled = sliderStepCount > 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text("$sliderStepCount")
        }
    }
}

@Composable
private fun SharedMobileEpubToc(
    epub: SharedEpubBook?,
    selectedIndex: Int,
    onEntryClick: (Int, SharedEpubTocEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = epub?.tableOfContents.orEmpty()
    if (entries.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No table of contents") }
        return
    }
    var query by remember(epub?.id) { mutableStateOf("") }
    var expanded by remember(epub?.id) { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val visibleEntries = remember(entries, query, expanded) {
        entries.withIndex().filter { indexed ->
            val matches = query.isBlank() || indexed.value.label.contains(query, ignoreCase = true)
            matches && (expanded || indexed.value.depth == 0)
        }
    }
    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search chapters") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { expanded = true }) { Text("Expand All") }
            TextButton(onClick = { expanded = false }) { Text("Collapse All") }
            TextButton(
                onClick = {
                    query = ""
                    expanded = true
                    scope.launch { listState.animateScrollToItem(selectedIndex.coerceAtLeast(0)) }
                }
            ) { Text("Locate") }
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(visibleEntries, key = { it.index }) { indexed ->
                val entry = indexed.value
                NavigationDrawerItem(
                    label = {
                        Text(
                            entry.label,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (entry.depth == 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    selected = indexed.index == selectedIndex,
                    onClick = { onEntryClick(indexed.index, entry) },
                    modifier = Modifier.padding(start = (entry.depth * 18).dp, end = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SharedMobileEpubBookmarks(
    bookmarks: List<ReaderBookmark>,
    onBookmarkClick: (ReaderBookmark) -> Unit,
    modifier: Modifier = Modifier
) {
    if (bookmarks.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No bookmarks yet") }
        return
    }
    LazyColumn(modifier) {
        items(bookmarks.sortedBy { it.pageIndex }, key = ReaderBookmark::id) { bookmark ->
            NavigationDrawerItem(
                label = {
                    Column {
                        Text(bookmark.chapterTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(bookmark.preview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                },
                selected = false,
                onClick = { onBookmarkClick(bookmark) },
                icon = { Icon(Icons.Default.Bookmark, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun SharedMobileEpubHighlights(
    highlights: List<UserHighlight>,
    onHighlightClick: (UserHighlight) -> Unit,
    modifier: Modifier = Modifier
) {
    if (highlights.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No annotations yet") }
        return
    }
    LazyColumn(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        items(highlights, key = { it.id }) { highlight ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onHighlightClick(highlight) },
                shape = RoundedCornerShape(12.dp),
                color = highlight.effectiveColor.copy(alpha = 0.16f)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = highlight.text.ifBlank { "Highlight" },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    highlight.note?.takeIf { it.isNotBlank() }?.let { note ->
                        Text(note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileEpubFormatSheet(
    settings: ReaderSettings,
    isLocalMode: Boolean,
    onLocalModeChange: (Boolean) -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showModeMenu by remember { mutableStateOf(false) }
    var showFontSheet by remember { mutableStateOf(false) }
    var alignmentChoice by remember(settings.textAlign) {
        mutableStateOf(
            when (settings.textAlign) {
                SharedReaderTextAlign.RIGHT -> "Right"
                SharedReaderTextAlign.JUSTIFY -> "Justify"
                else -> "Default"
            }
        )
    }
    val defaults = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.70f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Row(Modifier.clickable { showModeMenu = true }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isLocalMode) "Local Format" else "Global Format", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select format mode", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                        DropdownMenuItem(text = { Column { Text("Global Format", fontWeight = FontWeight.Bold); Text("Applies to all files", style = MaterialTheme.typography.bodySmall) } }, onClick = { onLocalModeChange(false); showModeMenu = false })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Column { Text("Local Format", fontWeight = FontWeight.Bold); Text("Saved for this file", style = MaterialTheme.typography.bodySmall) } }, onClick = { onLocalModeChange(true); showModeMenu = false })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        onSettingsChange(settings.copy(fontSize = defaults.fontSize, lineSpacing = defaults.lineSpacing, paragraphSpacing = defaults.paragraphSpacing, imageScale = defaults.imageScale, horizontalMargin = defaults.resolvedHorizontalMargin, verticalMargin = defaults.resolvedVerticalMargin, fontFamily = defaults.fontFamily, customFontPath = null, textAlign = defaults.textAlign))
                        alignmentChoice = "Default"
                    }) { Text("Reset") }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("FONT & ALIGNMENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            Surface(onClick = { showFontSheet = true }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Aa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
                        Text(settings.fontFamily.takeUnless { it.isBlank() || it == "Default" } ?: "Original", style = MaterialTheme.typography.titleSmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Row {
                    listOf("Default", "Left", "Right", "Justify").forEach { label ->
                        val selected = alignmentChoice == label
                        val iconResource = when (label) {
                            "Right" -> Res.drawable.format_align_right
                            "Justify" -> Res.drawable.format_align_justify
                            else -> Res.drawable.format_align_left
                        }
                        Column(
                            Modifier.fillMaxHeight().weight(1f).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp)).clickable {
                                alignmentChoice = label
                                onSettingsChange(settings.copy(textAlign = when (label) { "Right" -> SharedReaderTextAlign.RIGHT; "Justify" -> SharedReaderTextAlign.JUSTIFY; else -> SharedReaderTextAlign.START }))
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(iconResource),
                                contentDescription = label,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("LAYOUT & SPACING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SharedMobileEpubFormatSlider("Font Size", settings.fontSize / defaults.fontSize.toFloat(), 0.5f..3f) { onSettingsChange(settings.copy(fontSize = (defaults.fontSize * it).roundToInt())) }
                SharedMobileEpubFormatSlider("Line Height", settings.lineSpacing / defaults.lineSpacing, 1f..3f) { onSettingsChange(settings.copy(lineSpacing = defaults.lineSpacing * it)) }
                SharedMobileEpubFormatSlider("Paragraph Gap", settings.paragraphSpacing / defaults.paragraphSpacing, 0f..3f) { onSettingsChange(settings.copy(paragraphSpacing = defaults.paragraphSpacing * it)) }
                SharedMobileEpubFormatSlider("Image Size", settings.imageScale / defaults.imageScale, 0.5f..2f) { onSettingsChange(settings.copy(imageScale = defaults.imageScale * it)) }
                SharedMobileEpubFormatSlider("Horizontal Margin", settings.resolvedHorizontalMargin / defaults.resolvedHorizontalMargin.toFloat(), 0f..3f, allowNone = true) { onSettingsChange(settings.copy(horizontalMargin = (defaults.resolvedHorizontalMargin * it).roundToInt())) }
                SharedMobileEpubFormatSlider("Vertical Margin", settings.resolvedVerticalMargin / defaults.resolvedVerticalMargin.toFloat(), 0f..3f, allowNone = true) { onSettingsChange(settings.copy(verticalMargin = (defaults.resolvedVerticalMargin * it).roundToInt())) }
            }
        }
    }
    if (showFontSheet) {
        ModalBottomSheet(onDismissRequest = { showFontSheet = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Select Font", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showFontSheet = false }) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                LazyColumn {
                    items(ReaderFont.entries) { font ->
                        NavigationDrawerItem(label = { Text(font.displayName) }, selected = if (font == ReaderFont.ORIGINAL) settings.fontFamily == "Default" || settings.fontFamily == "Original" else settings.fontFamily == font.fontFamilyName, onClick = { onSettingsChange(settings.copy(fontFamily = if (font == ReaderFont.ORIGINAL) "Default" else font.fontFamilyName, customFontPath = null)); showFontSheet = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedMobileEpubFormatSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, allowNone: Boolean = false, onValueChange: (Float) -> Unit) {
    val current = value.coerceIn(range)
    val valueLabel = when { allowNone && current <= 0.01f -> "None"; current in 0.99f..1.01f -> "Orig"; else -> "${((current * 10).roundToInt() / 10f)}x" }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = { onValueChange(((current - 0.1f).coerceAtLeast(range.start) * 10).roundToInt() / 10f) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.primary) }
            SharedMobileEpubCustomCanvasSlider(value = current, onValueChange = onValueChange, valueRange = range, modifier = Modifier.weight(1f))
            IconButton(onClick = { onValueChange(((current + 0.1f).coerceAtMost(range.endInclusive) * 10).roundToInt() / 10f) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, contentDescription = "Increase", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun SharedMobileEpubCustomCanvasSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier.height(24.dp).pointerInput(valueRange) {
            awaitEachGesture {
                val down = awaitFirstDown()
                fun update(offset: Offset) {
                    val newFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val rawValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                    onValueChange((rawValue * 10f).roundToInt() / 10f)
                }
                update(down.position)
                drag(down.id) { change ->
                    change.consume()
                    update(change.position)
                }
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackHeight = 4.dp.toPx()
            val trackY = (size.height - trackHeight) / 2f
            val corners = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            drawRoundRect(inactiveColor, Offset(0f, trackY), Size(size.width, trackHeight), corners)
            val activeWidth = fraction * size.width
            drawRoundRect(activeColor, Offset(0f, trackY), Size(activeWidth, trackHeight), corners)
            val thumbRadius = 8.dp.toPx()
            drawCircle(activeColor, thumbRadius, Offset(activeWidth.coerceIn(thumbRadius, size.width - thumbRadius), size.height / 2f))
        }
    }
}

@Composable
private fun SharedMobileEpubSettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(if (value % 1f == 0f) value.toInt().toString() else ((value * 10).toInt() / 10f).toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range), onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileEpubThemeSheet(
    settings: ReaderSettings,
    customReaderThemes: List<ReaderTheme>,
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val allThemes = BuiltInReaderThemes + customReaderThemes
    val selectedTheme = allThemes.firstOrNull { it.id == settings.themeId }
    var selectedTab by remember(settings.themeId) { mutableIntStateOf(if (selectedTheme?.textureId != null) 1 else 0) }
    var editingTheme by remember { mutableStateOf<ReaderTheme?>(null) }
    var showBuilder by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val builtIns = BuiltInReaderThemes.filter { (it.textureId != null) == (selectedTab == 1) }
    val customThemes = customReaderThemes.filter { (it.textureId != null) == (selectedTab == 1) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp)) {
            Text("Reading Themes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, divider = {}) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("Solid Colors", modifier = Modifier.padding(12.dp)) }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("Textured", modifier = Modifier.padding(12.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            if (selectedTab == 1) {
                val transparency = 1f - settings.textureAlpha.coerceIn(0f, 1f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Texture Transparency", style = MaterialTheme.typography.labelMedium)
                    Text("${(transparency * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(value = transparency, onValueChange = { onSettingsChange(settings.copy(textureAlpha = 1f - it)) })
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text("Presets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                gridItems(builtIns, key = { it.id }) { theme ->
                    SharedMobileEpubThemeGridItem(
                        theme = theme,
                        selected = settings.themeId == theme.id || (settings.themeId == null && theme.id == "system"),
                        textureAlpha = settings.textureAlpha,
                        onSelected = { onSettingsChange(theme.toReaderSettings(settings)) }
                    )
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("My Themes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { editingTheme = null; showBuilder = true }) { Icon(Icons.Default.Add, contentDescription = "New", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
                if (customThemes.isEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text("No custom themes yet. Tap '+' to create one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    gridItems(customThemes, key = { it.id }) { theme ->
                        SharedMobileEpubThemeGridItem(
                            theme = theme,
                            selected = settings.themeId == theme.id,
                            textureAlpha = settings.textureAlpha,
                            onSelected = { onSettingsChange(theme.toReaderSettings(settings)) },
                            onEdit = { editingTheme = theme; showBuilder = true },
                            onDelete = {
                                onCustomReaderThemesChange(customReaderThemes.filterNot { it.id == theme.id })
                                if (settings.themeId == theme.id) BuiltInReaderThemes.first().let { onSettingsChange(it.toReaderSettings(settings)) }
                            }
                        )
                    }
                }
            }
        }
    }
    if (showBuilder) {
        SharedReaderCustomThemeDialog(
            initialTheme = editingTheme,
            isTexturedMode = selectedTab == 1,
            customThemes = customReaderThemes,
            customTextureIds = emptyList(),
            onImportTexture = null,
            texturePreviewContent = null,
            onDismiss = { showBuilder = false; editingTheme = null },
            onSave = { saved ->
                val updated = if (editingTheme == null) customReaderThemes + saved else customReaderThemes.map { if (it.id == saved.id) saved else it }
                onCustomReaderThemesChange(updated)
                onSettingsChange(saved.toReaderSettings(settings))
                showBuilder = false
                editingTheme = null
            }
        )
    }
}

@Composable
private fun SharedMobileEpubThemeGridItem(
    theme: ReaderTheme,
    selected: Boolean,
    textureAlpha: Float,
    onSelected: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val background = if (theme.id == "system") MaterialTheme.colorScheme.surfaceVariant else theme.backgroundColor
    val foreground = if (theme.id == "system") MaterialTheme.colorScheme.onSurfaceVariant else theme.textColor
    val texture = sharedMobileEpubTextureBitmap(theme.textureId)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).background(background, CircleShape)
                .then(texture?.let { bitmap -> Modifier.drawBehind { drawRect(ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)), alpha = textureAlpha.coerceIn(0f, 1f), blendMode = if (theme.isDark) BlendMode.Screen else BlendMode.Multiply) } } ?: Modifier)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(onClick = onSelected),
            contentAlignment = Alignment.Center
        ) { Text("Aa", color = foreground, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        Text(theme.name, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = onSelected))
        if (onEdit != null && onDelete != null) {
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Icon(Icons.Default.Edit, "Edit", Modifier.size(28.dp).clickable(onClick = onEdit).padding(6.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Delete, "Delete", Modifier.size(28.dp).clickable(onClick = onDelete).padding(6.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SharedMobileEpubPageInfo(
    chapterTitle: String,
    chapterIndex: Int,
    chapterCount: Int,
    progressPercent: Float,
    settings: ReaderSettings,
    modifier: Modifier = Modifier
) {
    val background = settings.readerPageInfoBackgroundColor()
    val foreground = settings.readerTextColor().copy(alpha = 0.8f)
    val texture = sharedMobileEpubTextureBitmap(settings.textureId)
    Box(
        modifier.fillMaxWidth().height(25.dp).background(background)
            .then(texture?.let { bitmap -> Modifier.drawBehind { drawRect(ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)), alpha = settings.textureAlpha.coerceIn(0f, 1f), blendMode = if (settings.darkMode) BlendMode.Screen else BlendMode.Multiply) } } ?: Modifier)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
            Text(
                "$chapterTitle (${chapterIndex + 1}/${chapterCount.coerceAtLeast(1)})",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = foreground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp)
            )
            Text(
                "${progressPercent.coerceIn(0f, 100f).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = foreground,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
    }
}

@Composable
private fun SharedMobileEpubChapterChangeIndicator(direction: String, progress: Float, modifier: Modifier = Modifier) {
    val alpha = (progress * 1.5f).coerceIn(0f, 1f)
    if (alpha <= 0.1f) return
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).graphicsLayer { this.alpha = alpha },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        tonalElevation = 4.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (direction == "previous") Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.size(20.dp * min(1f, progress + 0.2f))
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (progress >= 1f) if (direction == "previous") "Release for previous chapter" else "Release for next chapter" else "Pull further... (${(progress * 100).toInt()}%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileEpubVisualOptionsSheet(settings: ReaderSettings, onSettingsChange: (ReaderSettings) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Visual Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("System bars", style = MaterialTheme.typography.titleSmall)
            SharedMobileEpubEnumChoices(SystemUiMode.entries, settings.systemUiMode, { it.title }) { onSettingsChange(settings.copy(systemUiMode = it)) }
            Text("Page info", style = MaterialTheme.typography.titleSmall)
            SharedMobileEpubEnumChoices(PageInfoMode.entries, settings.pageInfoMode, { it.title }) { onSettingsChange(settings.copy(pageInfoMode = it)) }
            Text("Page info position", style = MaterialTheme.typography.titleSmall)
            SharedMobileEpubEnumChoices(PageInfoPosition.entries, settings.pageInfoPosition, { it.title }) { onSettingsChange(settings.copy(pageInfoPosition = it)) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Pull to change chapter"); Text("Pull beyond the chapter edge", style = MaterialTheme.typography.bodySmall) }
                Switch(checked = settings.seamlessChapterNavigation, onCheckedChange = { onSettingsChange(settings.copy(seamlessChapterNavigation = it)) })
            }
            SharedMobileEpubSettingSlider("Pull distance", settings.chapterTurnDragMultiplier, 0.5f..2f, 14) { onSettingsChange(settings.copy(chapterTurnDragMultiplier = it)) }
        }
    }
}

@Composable
private fun <T> SharedMobileEpubEnumChoices(values: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected = value == selected, onClick = { onSelected(value) }, label = { Text(label(value), maxLines = 1) }, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun SharedMobileEpubSearchOverlay(query: String, onQueryChange: (String) -> Unit, results: List<SharedMobileEpubSearchResult>, onResultClick: (SharedMobileEpubSearchResult) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, tonalElevation = 8.dp) {
        Column {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") }
                OutlinedTextField(value = query, onValueChange = onQueryChange, placeholder = { Text("Search in book") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Text(if (query.length < 2) "Type at least 2 characters" else "${results.size} results", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxSize()) {
                items(results) { result ->
                    Column(Modifier.fillMaxWidth().clickable { onResultClick(result) }.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(result.chapterTitle, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(result.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SharedMobileEpubSearchNavigation(current: Int, total: Int, onPrevious: () -> Unit, onNext: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), tonalElevation = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
            IconButton(onClick = onPrevious, enabled = current > 0) { Icon(Icons.Default.ArrowUpward, contentDescription = "Previous result") }
            Text("${current + 1}/$total", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onNext, enabled = current < total - 1) { Icon(Icons.Default.ArrowDownward, contentDescription = "Next result") }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close search results") }
        }
    }
}

private data class SharedMobileEpubSearchResult(val chapterIndex: Int, val chapterTitle: String, val chunkIndex: Int, val occurrenceIndex: Int, val snippet: String)
private data class SharedMobileEpubLink(val href: String, val chapterHref: String?)
private data class SharedMobileEpubActiveToc(val href: String, val fragmentId: String?)

private val SharedMobileEpubJson = Json { ignoreUnknownKeys = true }

private fun String.sharedMobileEpubLocatorOrNull(): ReaderLocator? {
    val objectValue = runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    return objectValue.toMobileEpubLocator()
}

private fun String.sharedMobileEpubPullOrNull(): Pair<String, Float>? {
    val value = runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    val direction = value["direction"]?.jsonPrimitive?.contentOrNull ?: return null
    val progress = value["progress"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: return null
    return direction to progress.coerceIn(0f, 1.25f)
}

private fun SharedEpubBook.searchMobileEpub(query: String): List<SharedMobileEpubSearchResult> {
    val needle = query.lowercase()
    return buildList {
        chapters.forEachIndexed { chapterIndex, chapter ->
            ReaderHtmlDocumentBuilder.verticalChapterChunks(this@searchMobileEpub, chapterIndex).forEachIndexed { chunkIndex, html ->
                val text = html.mobileEpubPlainText()
                val lower = text.lowercase()
                var from = 0
                var chunkOccurrence = 0
                while (from < lower.length) {
                    val found = lower.indexOf(needle, from)
                    if (found < 0) break
                    val wordStart = found == 0 || !lower[found - 1].isLetterOrDigit()
                    if (wordStart) {
                        val snippetStart = (found - 35).coerceAtLeast(0)
                        val snippetEnd = (found + needle.length + 35).coerceAtMost(text.length)
                        add(SharedMobileEpubSearchResult(chapterIndex, chapter.title.ifBlank { "Chapter ${chapterIndex + 1}" }, chunkIndex, chunkOccurrence, text.substring(snippetStart, snippetEnd).trim()))
                        chunkOccurrence++
                    }
                    from = found + needle.length.coerceAtLeast(1)
                }
            }
        }
    }
}

private fun String.mobileEpubPlainText(): String =
    replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ").trim()

private fun JsonObject.toMobileEpubLocator(): ReaderLocator {
    fun int(name: String): Int? = get(name)?.jsonPrimitive?.intOrNull
    fun string(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull
    return ReaderLocator(
        chapterIndex = int("chapterIndex"),
        chapterId = string("chapterId"),
        href = string("href"),
        pageIndex = int("pageIndex"),
        startOffset = int("startOffset"),
        endOffset = int("endOffset"),
        blockIndex = int("blockIndex"),
        charOffset = int("charOffset"),
        textQuote = string("textQuote"),
        cfi = string("cfi")
    )
}

private fun String.sharedMobileEpubLinkOrNull(): SharedMobileEpubLink? {
    val objectValue = runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    val href = objectValue["href"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
    return SharedMobileEpubLink(
        href = href,
        chapterHref = objectValue["chapterHref"]?.jsonPrimitive?.contentOrNull
    )
}

private fun String.sharedMobileEpubDirectionOrNull(): String? {
    val objectValue = runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    return objectValue["direction"]?.jsonPrimitive?.contentOrNull
        ?.takeIf { it == "previous" || it == "next" }
}

private fun String.sharedMobileEpubActiveTocOrNull(): SharedMobileEpubActiveToc? {
    val objectValue = runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    val href = objectValue["href"]?.jsonPrimitive?.contentOrNull ?: return null
    return SharedMobileEpubActiveToc(
        href = href,
        fragmentId = objectValue["fragmentId"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
    )
}

private fun sharedMobileEpubActiveTocScript(book: SharedEpubBook, chapterIndex: Int): String {
    val chapterHref = book.chapters.getOrNull(chapterIndex)?.baseHref.orEmpty()
    val fragments = book.tableOfContents
        .filter { it.href.normalizeMobileEpubPath() == chapterHref.normalizeMobileEpubPath() }
        .mapNotNull(SharedEpubTocEntry::fragmentId)
        .distinct()
    val hrefJson = JsonPrimitive(chapterHref).toString()
    val fragmentsJson = fragments.joinToString(prefix = "[", postfix = "]") { JsonPrimitive(it).toString() }
    return """
        (function () {
          if (window.readerIosTocTrackerCleanup) window.readerIosTocTrackerCleanup();
          var href = $hrefJson;
          var fragments = $fragmentsJson;
          var lastFragment = '__reader_unset__';
          var timer = null;
          function report() {
            timer = null;
            var best = null;
            var bestTop = -Infinity;
            for (var index = 0; index < fragments.length; index++) {
              var fragment = fragments[index];
              var decoded = fragment;
              try { decoded = decodeURIComponent(fragment); } catch (_) {}
              var element = document.getElementById(fragment) || document.getElementById(decoded);
              if (!element) continue;
              var top = element.getBoundingClientRect().top;
              if (top <= 18 && top > bestTop) { best = fragment; bestTop = top; }
            }
            var current = best || '';
            if (current === lastFragment) return;
            lastFragment = current;
            if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
              window.kmpJsBridge.callNative('readerActiveTocChanged', JSON.stringify({ href: href, fragmentId: best }));
            }
          }
          function schedule() {
            if (timer !== null) window.clearTimeout(timer);
            timer = window.setTimeout(report, 90);
          }
          window.addEventListener('scroll', schedule, { passive: true });
          window.readerIosTocTrackerCleanup = function () {
            window.removeEventListener('scroll', schedule);
            if (timer !== null) window.clearTimeout(timer);
          };
          window.setTimeout(report, 0);
        })();
    """.trimIndent()
}

private fun SharedEpubBook.locatorForTocEntry(entry: SharedEpubTocEntry, pages: List<ReaderPage>): ReaderLocator? {
    val chapterIndex = chapters.indexOfFirst {
        it.baseHref?.normalizeMobileEpubPath() == entry.href.normalizeMobileEpubPath() &&
            it.fragmentId == entry.fragmentId
    }.takeIf { it >= 0 } ?: chapters.indexOfFirst {
        it.baseHref?.normalizeMobileEpubPath() == entry.href.normalizeMobileEpubPath()
    }
        .takeIf { it >= 0 } ?: return null
    val page = pages.firstOrNull { it.chapterIndex == chapterIndex }
    return ReaderLocator(
        chapterIndex = chapterIndex,
        chapterId = chapters[chapterIndex].id,
        href = chapters[chapterIndex].baseHref,
        pageIndex = page?.pageIndex,
        startOffset = page?.startOffset ?: 0,
        endOffset = page?.startOffset ?: 0,
        textQuote = page?.text?.take(120)
    )
}

private fun SharedEpubBook.locatorForLink(
    rawHref: String,
    ownerHref: String?,
    pages: List<ReaderPage>
): Pair<ReaderLocator, String?>? {
    val fragment = rawHref.substringAfter('#', missingDelimiterValue = "")
        .substringBefore('?')
        .percentDecodeMobileEpubPath()
        .takeIf(String::isNotBlank)
    val reference = rawHref.substringBefore('#').substringBefore('?').percentDecodeMobileEpubPath()
    val targetPath = if (reference.isBlank()) ownerHref.orEmpty() else resolveMobileEpubPath(ownerHref.orEmpty(), reference)
    val chapterIndex = chapters.indexOfFirst {
        it.baseHref?.normalizeMobileEpubPath() == targetPath.normalizeMobileEpubPath() &&
            it.fragmentId == fragment
    }.takeIf { it >= 0 } ?: chapters.indexOfFirst {
        it.baseHref?.normalizeMobileEpubPath() == targetPath.normalizeMobileEpubPath()
    }
        .takeIf { it >= 0 } ?: return null
    val page = pages.firstOrNull { it.chapterIndex == chapterIndex }
    return ReaderLocator(
        chapterIndex = chapterIndex,
        chapterId = chapters[chapterIndex].id,
        href = chapters[chapterIndex].baseHref,
        pageIndex = page?.pageIndex,
        startOffset = page?.startOffset ?: 0,
        endOffset = page?.startOffset ?: 0,
        textQuote = page?.text?.take(120)
    ) to fragment
}

private fun ReaderPage.toMobileEpubLocator(book: SharedEpubBook?): ReaderLocator {
    val chapter = book?.chapters?.getOrNull(chapterIndex)
    return ReaderLocator(
        chapterIndex = chapterIndex,
        chapterId = chapter?.id,
        href = chapter?.baseHref,
        pageIndex = pageIndex,
        startOffset = startOffset,
        endOffset = startOffset,
        textQuote = text.take(120)
    )
}

private fun sharedMobileEpubNavigationScript(
    locator: ReaderLocator,
    fragment: String?,
    targetChunkIndex: Int?,
    targetChunkHtml: String?
): String {
    val locatorJson = buildJsonObject {
        locator.chapterIndex?.let { put("chapterIndex", it) }
        locator.chapterId?.let { put("chapterId", it) }
        locator.href?.let { put("href", it) }
        locator.pageIndex?.let { put("pageIndex", it) }
        locator.startOffset?.let { put("startOffset", it) }
        locator.endOffset?.let { put("endOffset", it) }
        locator.blockIndex?.let { put("blockIndex", it) }
        locator.charOffset?.let { put("charOffset", it) }
        locator.textQuote?.let { put("textQuote", it) }
        locator.cfi?.let { put("cfi", it) }
    }
    val fragmentJson = fragment?.let(::JsonPrimitive)?.toString() ?: "null"
    val chunkInjection = if (targetChunkIndex != null && targetChunkHtml != null) {
        "if (window.readerVirtualization) window.readerVirtualization.provideChunk($targetChunkIndex, ${JsonPrimitive(targetChunkHtml)});"
    } else {
        ""
    }
    return """
        (function () {
          var locator = $locatorJson;
          var fragment = $fragmentJson;
          $chunkInjection
          if (fragment) {
            var chapter = null;
            if (locator.chapterIndex !== undefined && locator.chapterIndex !== null) {
              chapter = document.querySelector('[data-reader-chapter-index="' + locator.chapterIndex + '"]');
            }
            var target = null;
            var candidates = (chapter || document).querySelectorAll('[id]');
            for (var index = 0; index < candidates.length; index++) {
              if (candidates[index].id === fragment) { target = candidates[index]; break; }
            }
            if (target) {
              target.scrollIntoView({ block: 'start', inline: 'nearest', behavior: 'auto' });
              return;
            }
          }
          if (window.readerScrollToLocator) window.readerScrollToLocator(locator, { source: 'ios_mobile' });
        })();
    """.trimIndent()
}

private fun sharedMobileEpubSearchNavigationScript(result: SharedMobileEpubSearchResult, query: String, chunkHtml: String?): String {
    val injection = chunkHtml?.let { "if(window.readerVirtualization)window.readerVirtualization.provideChunk(${result.chunkIndex},${JsonPrimitive(it)});" }.orEmpty()
    return """
        (function(){
          $injection
          var chunk=document.querySelector('[data-reader-chunk-index="${result.chunkIndex}"]');
          var query=${JsonPrimitive(query)};
          document.querySelectorAll('.reader-ios-search-hit').forEach(function(hit){
            var parent=hit.parentNode; while(hit.firstChild)parent.insertBefore(hit.firstChild,hit); parent.removeChild(hit); parent.normalize();
          });
          if(!chunk)return;
          var walker=document.createTreeWalker(chunk,NodeFilter.SHOW_TEXT);
          var node,occurrence=0,target=null,needle=query.toLocaleLowerCase();
          while((node=walker.nextNode())&&!target){
            var value=node.nodeValue||'',lower=value.toLocaleLowerCase(),from=0,found;
            while((found=lower.indexOf(needle,from))>=0){
              var wordStart=found===0||!/[\p{L}\p{N}]/u.test(lower.charAt(found-1));
              if(wordStart){
                if(occurrence===${result.occurrenceIndex}){
                  var range=document.createRange(); range.setStart(node,found); range.setEnd(node,found+needle.length);
                  var mark=document.createElement('mark'); mark.className='reader-ios-search-hit';
                  mark.style.background='#ffdf5d'; mark.style.color='inherit'; range.surroundContents(mark); target=mark; break;
                }
                occurrence++;
              }
              from=found+Math.max(1,needle.length);
            }
          }
          (target||chunk).scrollIntoView({block:'center',behavior:'auto'});
        })();
    """.trimIndent()
}

private fun String.isExternalEpubLink(): Boolean {
    val lower = trim().lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//") ||
        lower.startsWith("mailto:") || lower.startsWith("tel:") || lower.startsWith("sms:")
}

private fun String.containsReaderFragment(fragment: String): Boolean {
    val escaped = Regex.escape(fragment)
    return Regex("""\bid\s*=\s*([\"'])$escaped\1""", RegexOption.IGNORE_CASE).containsMatchIn(this)
}

private fun resolveMobileEpubPath(owner: String, reference: String): String {
    if (reference.startsWith('/')) return reference.removePrefix("/").normalizeMobileEpubPath()
    val base = owner.substringBeforeLast('/', missingDelimiterValue = "")
    return (if (base.isBlank()) reference else "$base/$reference").normalizeMobileEpubPath()
}

private fun String.percentDecodeMobileEpubPath(): String {
    val bytes = ArrayList<Byte>(length)
    var index = 0
    while (index < length) {
        if (this[index] == '%' && index + 2 < length) {
            val decoded = substring(index + 1, index + 3).toIntOrNull(16)
            if (decoded != null) {
                bytes += decoded.toByte()
                index += 3
                continue
            }
        }
        bytes += this[index].toString().encodeToByteArray().toList()
        index++
    }
    return bytes.toByteArray().decodeToString()
}

private fun String.normalizeMobileEpubPath(): String {
    val parts = ArrayDeque<String>()
    replace('\\', '/').split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeLast()
            else -> parts.addLast(part)
        }
    }
    return parts.joinToString("/")
}

private fun ReaderSettings.readerBackgroundColor(): Color {
    val value = backgroundColorArgb ?: if (darkMode) 0xFF121212L else 0xFFFFFFFFL
    return Color((value and 0xFFFFFFFFL).toInt())
}

private fun ReaderSettings.readerTextColor(): Color {
    val value = textColorArgb ?: if (darkMode) 0xFFE0E0E0L else 0xFF000000L
    return Color((value and 0xFFFFFFFFL).toInt())
}

private fun ReaderSettings.readerPageInfoBackgroundColor(): Color {
    val base = readerBackgroundColor()
    val overlayAlpha = if (darkMode) 0.08f else 0.06f
    val overlay = if (darkMode) Color.White else Color.Black
    return Color(
        red = overlay.red * overlayAlpha + base.red * (1f - overlayAlpha),
        green = overlay.green * overlayAlpha + base.green * (1f - overlayAlpha),
        blue = overlay.blue * overlayAlpha + base.blue * (1f - overlayAlpha),
        alpha = 0.95f
    )
}

@Composable
private fun sharedMobileEpubTextureBitmap(textureId: String?): ImageBitmap? {
    val resource = when (textureId) {
        ReaderTexture.NATURAL_WHITE.id -> Res.drawable.ep_naturalwhite
        ReaderTexture.RETINA_WOOD.id -> Res.drawable.retina_wood
        ReaderTexture.LIGHT_VENEER.id -> Res.drawable.light_veneer
        ReaderTexture.GREY_WASH.id -> Res.drawable.grey_wash_wall
        ReaderTexture.CLASSY_FABRIC.id -> Res.drawable.classy_fabric
        ReaderTexture.RETRO_INTRO.id -> Res.drawable.retro_intro
        else -> null
    }
    return resource?.let { imageResource(it) }
}

private val SharedMobileEpubAutoScrollStartScript = """
    (function () {
      if (window.readerIosAutoScrollTimer) return;
      window.readerIosAutoScrollTimer = window.setInterval(function () {
        window.scrollBy(0, 1);
        var root = document.documentElement;
        if (window.scrollY + window.innerHeight >= root.scrollHeight - 2) {
          window.clearInterval(window.readerIosAutoScrollTimer);
          window.readerIosAutoScrollTimer = null;
          if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
            window.kmpJsBridge.callNative('readerChapterBoundary', JSON.stringify({ direction: 'next' }));
          }
        }
      }, 24);
    })();
""".trimIndent()

private val SharedMobileEpubAutoScrollStopScript = """
    (function () {
      if (window.readerIosAutoScrollTimer) window.clearInterval(window.readerIosAutoScrollTimer);
      window.readerIosAutoScrollTimer = null;
    })();
""".trimIndent()

private fun sharedMobileEpubScrollToEndScript(chunkIndex: Int, chunkHtml: String?): String {
    val chunkInjection = if (chunkIndex >= 0 && chunkHtml != null) {
        "if (window.readerVirtualization) window.readerVirtualization.provideChunk($chunkIndex, ${JsonPrimitive(chunkHtml)});"
    } else {
        ""
    }
    return """
        (function () {
          $chunkInjection
          var root = document.scrollingElement || document.documentElement;
          window.scrollTo(0, Math.max(0, root.scrollHeight - window.innerHeight));
        })();
    """.trimIndent()
}
