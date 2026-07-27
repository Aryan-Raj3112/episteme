package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.BuiltInReaderThemes
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ReaderTexture
import com.aryan.reader.shared.ReaderFont
import com.aryan.reader.shared.ReaderTtsPlanner
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.readerExternalLookupActionForSelectionId
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.PageInfoMode
import com.aryan.reader.shared.PageInfoPosition
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.ReaderBookReplacementEngine
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.ReaderWordReplacementEngine
import com.aryan.reader.shared.ReaderWordReplacementRule
import com.aryan.reader.shared.SystemUiMode
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.toSharedReaderFontFamily
import com.aryan.reader.shared.withTtsReplacements
import com.aryan.reader.shared.withReaderFormatFrom
import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderImageReference
import com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderPageInfo
import com.aryan.reader.shared.reader.sharedReaderPageInfo
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderScreenOrientationMode
import com.aryan.reader.shared.reader.ReaderSearchOptions
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderSpreadLayout
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubTocEntry
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import com.aryan.reader.shared.reader.layoutSignature
import com.aryan.reader.shared.reader.readerImageReferences
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
import kotlin.math.pow
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
    val pageCount: Int,
    val formatIsLocal: Boolean,
    val localFormatSettings: ReaderSettings?,
    val autoScrollIsLocal: Boolean,
    val autoScrollLocalSpeed: Float?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileEpubReaderScreen(
    book: BookItem,
    onBack: () -> Unit,
    onReaderStateChange: (SharedMobileEpubReaderSnapshot) -> Unit = {},
    onMetadataLoaded: (title: String, author: String?) -> Unit = { _, _ -> },
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onSystemUiAppearanceChange: (statusHidden: Boolean, navigationHidden: Boolean, lightContent: Boolean, backgroundArgb: Long) -> Unit = { _, _, _, _ -> },
    customReaderThemes: List<ReaderTheme> = emptyList(),
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit = {},
    customFonts: List<CustomFontItem> = emptyList(),
    onImportFont: () -> Unit = {},
    readerDefaultSettings: ReaderSettings = ReaderSettings(),
    onReaderDefaultSettingsChange: (ReaderSettings) -> Unit = {},
    readerHighlightPalette: ReaderHighlightPalette = ReaderHighlightPalette(),
    readerToolbarPreferences: ReaderToolbarPreferences = ReaderToolbarPreferences(),
    onReaderToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit = {},
    readerTtsReplacementPreferences: ReaderTtsReplacementPreferences = ReaderTtsReplacementPreferences(),
    onReaderTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit = {},
    readerBookReplacementPreferences: ReaderBookReplacementPreferences = ReaderBookReplacementPreferences(),
    onReaderBookReplacementPreferencesChange: (ReaderBookReplacementPreferences) -> Unit = {},
    readerBrightness: Float? = null,
    readerBrightnessSupported: Boolean = false,
    onReaderBrightnessChange: (Float?) -> Unit = {},
    readerAutoScrollSpeed: Float = 36f,
    onReaderAutoScrollSpeedChange: (Float) -> Unit = {},
    readerScreenOrientationMode: ReaderScreenOrientationMode = ReaderScreenOrientationMode.FOLLOW_SYSTEM,
    onReaderScreenOrientationModeChange: (ReaderScreenOrientationMode) -> Unit = {},
    onApplyReaderScreenOrientation: (ReaderScreenOrientationMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val loadState = rememberSharedMobileEpubLoadState(book)
    val rawLoadedBook = loadState.book
    val bookReplacementSignature = readerBookReplacementPreferences.signatureForFile(book.id)
    val loadedBook = remember(rawLoadedBook, book.id, bookReplacementSignature) {
        rawLoadedBook?.copy(
            chapters = rawLoadedBook.chapters.map { chapter ->
                chapter.copy(
                    plainText = ReaderBookReplacementEngine.apply(
                        chapter.plainText,
                        readerBookReplacementPreferences,
                        book.id
                    ).text,
                    htmlContent = ReaderBookReplacementEngine.applyToHtml(
                        chapter.htmlContent,
                        readerBookReplacementPreferences,
                        book.id
                    )
                )
            }
        )
    }
    val localTts = rememberSharedMobileEpubLocalTts()
    val activeTtsChunk = localTts.progress.currentChunk
    val storedBookSettings = book.readerSettings ?: readerDefaultSettings
    var isLocalFormatMode by remember(book.id) { mutableStateOf(book.readerFormatIsLocal) }
    var localFormatSettings by remember(book.id) { mutableStateOf(book.readerLocalFormatSettings) }
    var settings by remember(book.id) {
        mutableStateOf(
            if (book.readerFormatIsLocal) {
                storedBookSettings.withReaderFormatFrom(book.readerLocalFormatSettings ?: storedBookSettings)
            } else {
                storedBookSettings.withReaderFormatFrom(readerDefaultSettings)
            }
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
    var editingHighlight by remember(book.id) { mutableStateOf<UserHighlight?>(null) }
    // Match Android's distraction-free reader entry; a reader tap reveals chrome.
    var showChrome by remember(book.id) { mutableStateOf(false) }
    var showFormatSheet by remember(book.id) { mutableStateOf(false) }
    var showThemeSheet by remember(book.id) { mutableStateOf(false) }
    var showVisualOptionsSheet by remember(book.id) { mutableStateOf(false) }
    var showSearch by remember(book.id) { mutableStateOf(false) }
    var showSearchResultsPanel by remember(book.id) { mutableStateOf(true) }
    var searchQuery by remember(book.id) { mutableStateOf("") }
    var searchResults by remember(book.id) { mutableStateOf<List<SharedMobileEpubSearchResult>>(emptyList()) }
    var isSearchInProgress by remember(book.id) { mutableStateOf(false) }
    var searchResultIndex by remember(book.id) { mutableIntStateOf(-1) }
    var pullDirection by remember(book.id) { mutableStateOf<String?>(null) }
    var pullProgress by remember(book.id) { mutableStateOf(0f) }
    var showSlider by remember(book.id) { mutableStateOf(false) }
    var showMore by remember(book.id) { mutableStateOf(false) }
    var showFileInfo by remember(book.id) { mutableStateOf(false) }
    var showCustomizeToolsSheet by remember(book.id) { mutableStateOf(false) }
    var showScreenOrientationSheet by remember(book.id) { mutableStateOf(false) }
    var showTtsReplacementsSheet by remember(book.id) { mutableStateOf(false) }
    var showTtsSettingsSheet by remember(book.id) { mutableStateOf(false) }
    var showBookReplacementsSheet by remember(book.id) { mutableStateOf(false) }
    var keepScreenOn by remember(book.id) { mutableStateOf(false) }
    var autoScrollModeActive by remember(book.id) { mutableStateOf(false) }
    var autoScroll by remember(book.id) { mutableStateOf(false) }
    var autoScrollIsLocal by remember(book.id) { mutableStateOf(book.readerAutoScrollIsLocal) }
    var autoScrollLocalSpeed by remember(book.id) { mutableStateOf(book.readerAutoScrollLocalSpeed) }
    var autoScrollSpeed by remember(book.id) {
        mutableStateOf(
            if (book.readerAutoScrollIsLocal) book.readerAutoScrollLocalSpeed ?: readerAutoScrollSpeed
            else readerAutoScrollSpeed
        )
    }
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
    val clipboard = LocalClipboardManager.current
    val sanitizedToolbarPreferences = readerToolbarPreferences.sanitized()
    val visibleToolbarTools = sanitizedToolbarPreferences.orderedVisibleTools().filter { it in SharedMobileEpubCustomizableTools }
    val mobileBottomToolIds = if (readerToolbarPreferences.bottomToolIds == ReaderToolbarPreferences.defaultBottomToolIds) {
        readerToolbarPreferences.bottomToolIds + ReaderTool.TTS_CONTROLS.id
    } else {
        readerToolbarPreferences.bottomToolIds
    }
    val bottomToolbarTools = visibleToolbarTools.filter { tool ->
        tool in SharedMobileEpubToolbarTools && tool.id in mobileBottomToolIds
    }
    val topToolbarTools = visibleToolbarTools.filter { tool ->
        tool in SharedMobileEpubToolbarTools && tool.id !in mobileBottomToolIds
    }
    val overflowMenuTools = visibleToolbarTools.filterNot { it in SharedMobileEpubToolbarTools }
    val systemUiHidden = when (settings.systemUiMode) {
        SystemUiMode.DEFAULT -> false
        SystemUiMode.SYNC -> !showChrome
        SystemUiMode.HIDDEN -> true
    }
    val navigationUiHidden = settings.systemUiMode == SystemUiMode.HIDDEN || !showChrome

    DisposableEffect(readerScreenOrientationMode, onApplyReaderScreenOrientation) {
        onApplyReaderScreenOrientation(readerScreenOrientationMode)
        onDispose { onApplyReaderScreenOrientation(ReaderScreenOrientationMode.FOLLOW_SYSTEM) }
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
    LaunchedEffect(settings.systemUiMode, settings.darkMode, settings.backgroundColorArgb, showChrome) {
        val backgroundArgb = settings.backgroundColorArgb ?: if (settings.darkMode) 0xFF121212L else 0xFFFFFFFFL
        onSystemUiAppearanceChange(
            systemUiHidden,
            navigationUiHidden,
            backgroundArgb.hasDarkReaderBackground(),
            backgroundArgb
        )
    }
    DisposableEffect(book.id) {
        onDispose {
            onKeepScreenOnChange(false)
            onSystemUiAppearanceChange(false, false, false, 0xFFFFFFFFL)
        }
    }
    LaunchedEffect(autoScroll, autoScrollSpeed) {
        commandScript = if (autoScroll) sharedMobileEpubAutoScrollStartScript(autoScrollSpeed) else SharedMobileEpubAutoScrollStopScript
        navigationRequestId++
    }
    LaunchedEffect(loadedBook, searchQuery) {
        val epub = loadedBook
        val query = searchQuery.trim()
        if (epub == null || query.isBlank()) {
            searchResults = emptyList()
            searchResultIndex = -1
            isSearchInProgress = false
            return@LaunchedEffect
        }
        delay(350)
        isSearchInProgress = true
        try {
            searchResults = withContext(Dispatchers.Default) { epub.searchMobileEpub(query) }
            searchResultIndex = -1
            showSearchResultsPanel = true
        } finally {
            isSearchInProgress = false
        }
    }

    val pageCount = pages.size.coerceAtLeast(1)
    val liveVerticalLocator = currentLocator.takeIf { settings.readingMode == ReaderReadingMode.VERTICAL }
    val pageInfo = remember(loadedBook, pages, currentPageIndex, liveVerticalLocator) {
        loadedBook?.let { sharedReaderPageInfo(it, pages, currentPageIndex, liveVerticalLocator) }
    }
    val progress = pageInfo?.progressPercent?.toFloat()
        ?: ((currentPageIndex + 1).toFloat() / pageCount) * 100f
    LaunchedEffect(currentLocator, settings, bookmarks, highlights, currentPageIndex, pageCount, isLocalFormatMode, localFormatSettings, autoScrollIsLocal, autoScrollLocalSpeed) {
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
                    pageCount = pageCount,
                    formatIsLocal = isLocalFormatMode,
                    localFormatSettings = localFormatSettings,
                    autoScrollIsLocal = autoScrollIsLocal,
                    autoScrollLocalSpeed = autoScrollLocalSpeed
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
                    pageCount = pageCount,
                    formatIsLocal = isLocalFormatMode,
                    localFormatSettings = localFormatSettings,
                    autoScrollIsLocal = autoScrollIsLocal,
                    autoScrollLocalSpeed = autoScrollLocalSpeed
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
            autoScroll -> sharedMobileEpubAutoScrollStartScript(autoScrollSpeed)
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

    LaunchedEffect(activeTtsChunk?.index, activeTtsChunk?.chapterIndex, activeTtsChunk?.pageIndex) {
        val chunk = activeTtsChunk
        if (chunk == null || loadedBook == null) return@LaunchedEffect
        // Speech chunks have source offsets and page indices from the same shared planner used
        // by Android. Let them own navigation while reading, so a spoken sentence is always
        // visible in either reader mode.
        navigate(chunk.toLocator())
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

    fun speakSelectedText(text: String, locator: ReaderLocator) {
        val epub = loadedBook ?: return
        val chapterIndex = locator.chapterIndex ?: currentChapterIndex
        val startOffset = locator.startOffset ?: 0
        localTts.start(
            chunks = listOf(
                ReaderTtsChunk(
                    index = 0,
                    pageIndex = locator.pageIndex ?: currentPageIndex,
                    chapterIndex = chapterIndex,
                    chapterTitle = epub.chapters.getOrNull(chapterIndex)?.title.orEmpty(),
                    text = text,
                    startOffset = startOffset,
                    endOffset = locator.endOffset ?: startOffset + text.length,
                    sourceCfi = locator.cfi
                )
            ).withTtsReplacements(readerTtsReplacementPreferences, book.id),
            bookTitle = epub.title
        )
    }

    fun createNoteForSelection(text: String, locator: ReaderLocator) {
        val chapterIndex = locator.chapterIndex ?: currentChapterIndex
        val startOffset = locator.startOffset ?: 0
        val endOffset = locator.endOffset ?: (startOffset + text.length)
        val timestamp = currentTimestamp()
        val highlight = UserHighlight(
            id = "ios_epub_note_$timestamp",
            cfi = locator.cfi ?: "desktop:$chapterIndex:$startOffset:$endOffset",
            text = text,
            color = readerHighlightPalette.sanitized().colors.first(),
            chapterIndex = chapterIndex,
            locator = locator.withFallbacks(
                chapterIndex = chapterIndex,
                startOffset = startOffset,
                endOffset = endOffset,
                textQuote = text
            )
        )
        highlights = highlights.filterNot { it.cfi == highlight.cfi } + highlight
        editingHighlight = highlight
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
                        onBookmarkRename = { bookmark, label ->
                            bookmarks = bookmarks.map { existing ->
                                if (existing.id == bookmark.id) existing.copy(label = label.trim().ifBlank { null }) else existing
                            }
                        },
                        onBookmarkDelete = { bookmark -> bookmarks = bookmarks.filterNot { it.id == bookmark.id } },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (drawerTab == 2) {
                    SharedMobileEpubHighlights(
                        highlights = highlights,
                        onHighlightClick = { highlight ->
                            navigate(highlight.locator)
                            scope.launch { drawerState.close() }
                        },
                        onHighlightEdit = { editingHighlight = it },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SharedMobileEpubImages(
                        images = loadedBook?.readerImageReferences(pages).orEmpty(),
                        onImageClick = { image ->
                            loadedBook?.chapters?.getOrNull(image.chapterIndex)?.let {
                                navigate(image.locator)
                                scope.launch { drawerState.close() }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        },
        modifier = modifier
    ) {
        Box(Modifier.fillMaxSize().background(settings.readerBackgroundColor())) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (!systemUiHidden) {
                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        } else {
                            Modifier
                        }
                    )
            ) {
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
                            AnimatedContent(
                                targetState = visiblePages,
                                transitionSpec = {
                                    if (!settings.pageTurnAnimationEnabled) {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    } else {
                                        val direction = sharedPaginatedTransitionDirection(
                                            initialState.minOfOrNull { it.pageIndex } ?: 0,
                                            targetState.minOfOrNull { it.pageIndex } ?: 0,
                                            settings.rightToLeftPagination
                                        )
                                        slideInHorizontally(tween(700)) { width -> width * direction } togetherWith
                                            slideOutHorizontally(tween(700)) { width -> -width * direction }
                                    }
                                },
                                label = "EPUB page turn"
                            ) { animatedPages ->
                            SharedNativePaginatedReader(
                                renderPlan = ReaderContentRenderPlan.NativePaginatedPages(
                                    visiblePages = animatedPages,
                                    settings = settings,
                                    searchQuery = searchQuery,
                                    searchOptions = ReaderSearchOptions(),
                                    highlightPalette = readerHighlightPalette,
                                    background = settings.readerBackgroundColor(),
                                    foreground = settings.readerTextColor(),
                                    navigationTarget = ReaderContentNavigationTarget(
                                        locator = explicitNavigationLocator ?: currentLocator,
                                        requestId = navigationRequestId,
                                        readingMode = settings.readingMode
                                    ),
                                    highlights = activeTtsChunk?.let { chunk ->
                                        highlights + chunk.toHighlight(localTts.progress.sessionId)
                                    } ?: highlights
                                ),
                                readerFontFamily = settings.toSharedReaderFontFamily(),
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
                                onHighlightSelected = { id ->
                                    editingHighlight = highlights.firstOrNull { it.id == id }
                                },
                                enabledSelectionActions = SharedNativeReaderSelectionAction.entries.toSet(),
                                onCopyText = { text -> clipboard.setText(AnnotatedString(text)) },
                                onSelectionAction = { action, text, locator ->
                                    val selectionLocator = locator ?: currentLocator ?: return@SharedNativePaginatedReader
                                    val lookupAction = action.externalLookupActionOrNull()
                                    when {
                                        lookupAction != null -> openSharedMobileEpubLookup(lookupAction, text)
                                        action == SharedNativeReaderSelectionAction.SPEAK -> speakSelectedText(text, selectionLocator)
                                        action == SharedNativeReaderSelectionAction.NOTE -> createNoteForSelection(text, selectionLocator)
                                        else -> Unit
                                    }
                                },
                                onLinkClicked = { link ->
                                    if (link.href.isExternalEpubLink()) {
                                        openSharedMobileEpubExternalLink(link.href)
                                    } else {
                                        val sourceChapter = link.chapterIndex ?: currentChapterIndex
                                        val sourceHref = loadedBook.chapters.getOrNull(sourceChapter)?.baseHref
                                        loadedBook.locatorForLink(link.href, sourceHref, pages)?.let { (locator, fragment) ->
                                            navigate(locator, fragment)
                                        }
                                    }
                                },
                                onReaderTap = { showChrome = !showChrome },
                                onReaderHorizontalTap = { horizontalFraction ->
                                    when (sharedPaginatedTapAction(horizontalFraction, settings.tapToNavigateEnabled, settings.rightToLeftPagination)) {
                                        SharedPaginatedTapAction.PREVIOUS_PAGE -> navigatePage(-1)
                                        SharedPaginatedTapAction.NEXT_PAGE -> navigatePage(1)
                                        SharedPaginatedTapAction.TOGGLE_CHROME -> showChrome = !showChrome
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            }
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
                        // A persisted WebView highlight needs the complete chapter DOM so its
                        // offsets remain stable after reopening the reader. Keep virtualization
                        // for ordinary chapters, where it protects large EPUBs from WebView
                        // memory spikes.
                        val currentChapterHasHighlights = highlights.any { highlight ->
                            (highlight.locator.chapterIndex ?: highlight.chapterIndex) == currentChapterIndex
                        }
                        val initialHtml = remember(
                            loadedBook.id,
                            currentChapterIndex,
                            chapterChunks,
                            highlights,
                            currentChapterHasHighlights
                        ) {
                            ReaderHtmlDocumentBuilder.verticalDocument(
                                book = loadedBook,
                                settings = settings,
                                highlights = highlights,
                                highlightPalette = readerHighlightPalette,
                                navigationLocator = currentLocator,
                                pages = pages,
                                highlightActionsEnabled = true,
                                readerAiFeaturesEnabled = false,
                                // This flag controls the selection-menu Speak action. iOS handles
                                // it with local device speech rather than the paid cloud service.
                                cloudTtsEnabled = true,
                                externalLookupEnabled = true,
                                renderedChapterRange = currentChapterIndex..currentChapterIndex,
                                virtualizedChapterChunks = if (currentChapterHasHighlights) {
                                    emptyMap()
                                } else {
                                    mapOf(currentChapterIndex to chapterChunks)
                                },
                                virtualizedInitialChunkIndex = initialVirtualChunkIndex,
                                showChapterTitles = false
                            )
                        }
                        val appearanceScript = remember(settings, pages, currentChapterIndex, loadedBook.id) {
                            ReaderHtmlDocumentBuilder.appearanceUpdateScript(settings) + "\n" +
                                ReaderHtmlDocumentBuilder.pageAnchorsUpdateScript(pages) + "\n" +
                                sharedMobileEpubActiveTocScript(loadedBook, currentChapterIndex) + "\n" +
                                "window.readerIosPullEnabled=${settings.seamlessChapterNavigation};" +
                                "window.readerIosSeamlessChapter=${!settings.seamlessChapterNavigation};" +
                                "window.readerIosPullMultiplier=${settings.chapterTurnDragMultiplier.coerceIn(0.5f, 2f)};"
                        }
                        val navigationScript = buildList {
                            commandScript?.let(::add)
                            (explicitNavigationLocator ?: currentLocator)?.let { locator ->
                                add(
                                    sharedMobileEpubNavigationScript(
                                        locator = locator,
                                        fragment = explicitNavigationFragment,
                                        targetChunkIndex = navigationChunkIndex,
                                        targetChunkHtml = navigationChunkHtml
                                    )
                                )
                            }
                            add(sharedMobileEpubTtsNavigationScript(activeTtsChunk?.toLocator()))
                        }.joinToString(separator = "\n")
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
                                    "readerHighlightCreated" -> payload.sharedMobileEpubHighlightOrNull()?.let { highlight ->
                                        highlights = highlights
                                            .filterNot { existing -> existing.cfi == highlight.cfi }
                                            .plus(highlight)
                                    }
                                    "readerHighlightClicked" -> payload.sharedMobileEpubHighlightIdOrNull()?.let { id ->
                                        editingHighlight = highlights.firstOrNull { it.id == id }
                                    }
                                    "readerSelectionAction" -> payload.sharedMobileEpubSelectionActionOrNull()?.let { selection ->
                                        val lookupAction = readerExternalLookupActionForSelectionId(selection.action)
                                        when {
                                            lookupAction != null -> openSharedMobileEpubLookup(lookupAction, selection.text)
                                            selection.action == "speak" -> {
                                                val locator = selection.locator ?: currentLocator ?: return@let
                                                speakSelectedText(selection.text, locator)
                                            }
                                            selection.action == "note" -> {
                                                val locator = selection.locator ?: return@let
                                                createNoteForSelection(selection.text, locator)
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
                        pageInfo = pageInfo,
                        progressPercent = progress,
                        settings = settings,
                        modifier = Modifier.align(if (settings.pageInfoPosition == PageInfoPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter)
                            .then(
                                if (
                                    settings.pageInfoPosition == PageInfoPosition.TOP && !systemUiHidden ||
                                    settings.pageInfoPosition == PageInfoPosition.BOTTOM && !navigationUiHidden
                                ) {
                                    Modifier.windowInsetsPadding(
                                        WindowInsets.safeDrawing.only(
                                            if (settings.pageInfoPosition == PageInfoPosition.TOP) {
                                                WindowInsetsSides.Top
                                            } else {
                                                WindowInsetsSides.Bottom
                                            }
                                        )
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .offset(y = if (settings.pageInfoPosition == PageInfoPosition.TOP && showChrome) 55.dp else if (settings.pageInfoPosition == PageInfoPosition.BOTTOM && showChrome) (-45).dp else 0.dp)
                    )
                }
                if (showChrome) {
                    SharedMobileEpubTopBar(
                        title = loadedBook?.title ?: book.displayName,
                        isBookmarked = isBookmarked,
                        topTools = topToolbarTools,
                        overflowTools = overflowMenuTools,
                        showMore = showMore,
                        onShowMoreChange = { showMore = it },
                        onBack = ::closeReader,
                        onTheme = { showThemeSheet = true },
                        onFormat = { showFormatSheet = true },
                        onSearch = { showSearchResultsPanel = true; showSearch = true },
                        onBookmark = ::toggleBookmark,
                        onVisualOptions = { showVisualOptionsSheet = true },
                        onOpenToc = { openReaderDrawer(0) },
                        onOpenSlider = { showSlider = !showSlider },
                        onFileInfo = { showFileInfo = true },
                        onCustomizeTools = { showCustomizeToolsSheet = true },
                        onScreenOrientation = { showScreenOrientationSheet = true },
                        onTtsReplacements = { showTtsReplacementsSheet = true },
                        onTtsSettings = { showTtsSettingsSheet = true },
                        onBookReplacements = { showBookReplacementsSheet = true },
                        readingMode = settings.readingMode,
                        rightToLeftPagination = settings.rightToLeftPagination,
                        tapToNavigateEnabled = settings.tapToNavigateEnabled,
                        pageTurnAnimationEnabled = settings.pageTurnAnimationEnabled,
                        onReadingModeChange = { mode ->
                            if (settings.readingMode != mode) {
                                settings = settings.copy(readingMode = mode)
                                showSlider = false
                                autoScrollModeActive = false
                                autoScroll = false
                            }
                        },
                        onRightToLeftPaginationChange = { settings = settings.copy(rightToLeftPagination = it) },
                        onTapToNavigateChange = { settings = settings.copy(tapToNavigateEnabled = it) },
                        onPageTurnAnimationChange = { settings = settings.copy(pageTurnAnimationEnabled = it) },
                        toolbarPreferences = sanitizedToolbarPreferences,
                        localTtsState = localTts.state,
                        onLocalTtsToggle = {
                            when (localTts.state) {
                                SharedMobileEpubLocalTtsState.IDLE -> loadedBook?.let { epub ->
                                    val session = ReaderEngine().createSession(
                                        book = epub,
                                        settings = settings,
                                        initialPageIndex = currentPageIndex,
                                        initialLocator = currentLocator
                                    )
                                    localTts.start(
                                        chunks = ReaderTtsPlanner.chunksFromCurrentLocation(session)
                                            .ifEmpty { ReaderTtsPlanner.chunksForCurrentChapter(session) }
                                            .withTtsReplacements(readerTtsReplacementPreferences, book.id),
                                        bookTitle = epub.title
                                    )
                                }
                                SharedMobileEpubLocalTtsState.SPEAKING -> localTts.pause()
                                SharedMobileEpubLocalTtsState.PAUSED -> localTts.resume()
                            }
                        },
                        onLocalTtsStop = localTts::stop,
                        keepScreenOn = keepScreenOn,
                        onKeepScreenOnChange = { keepScreenOn = it },
                        autoScroll = autoScrollModeActive,
                        onAutoScrollChange = { active ->
                            autoScrollModeActive = active
                            autoScroll = active
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .then(
                                if (!systemUiHidden) {
                                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                                } else {
                                    Modifier
                                }
                            )
                    )
                    if (loadedBook != null && pages.isNotEmpty()) {
                        SharedMobileEpubBottomBar(
                            tools = bottomToolbarTools,
                            readingMode = settings.readingMode,
                            isBookmarked = isBookmarked,
                            onReadingModeToggle = {
                                settings = settings.copy(
                                    readingMode = if (settings.readingMode == ReaderReadingMode.VERTICAL) {
                                        ReaderReadingMode.PAGINATED
                                    } else {
                                        ReaderReadingMode.VERTICAL
                                    }
                                )
                                showSlider = false
                                autoScrollModeActive = false
                                autoScroll = false
                            },
                            onToc = { openReaderDrawer(0) },
                            onFormat = { showFormatSheet = true },
                            onSearch = { showSearchResultsPanel = true; showSearch = true },
                            onTheme = { showThemeSheet = true },
                            onBookmark = ::toggleBookmark,
                            onVisualOptions = { showVisualOptionsSheet = true },
                            onOpenSlider = { showSlider = !showSlider },
                            localTtsState = localTts.state,
                            onLocalTtsToggle = {
                                when (localTts.state) {
                                    SharedMobileEpubLocalTtsState.IDLE -> loadedBook?.let { epub ->
                                        val session = ReaderEngine().createSession(
                                            book = epub,
                                            settings = settings,
                                            initialPageIndex = currentPageIndex,
                                            initialLocator = currentLocator
                                        )
                                        localTts.start(
                                            chunks = ReaderTtsPlanner.chunksFromCurrentLocation(session)
                                                .ifEmpty { ReaderTtsPlanner.chunksForCurrentChapter(session) }
                                                .withTtsReplacements(readerTtsReplacementPreferences, book.id),
                                            bookTitle = epub.title
                                        )
                                    }
                                    SharedMobileEpubLocalTtsState.SPEAKING -> localTts.pause()
                                    SharedMobileEpubLocalTtsState.PAUSED -> localTts.resume()
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .then(
                                    if (!navigationUiHidden) {
                                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
                if (localTts.isSessionActive) {
                    SharedMobileEpubTtsControls(
                        tts = localTts,
                        onOpenSettings = { showTtsSettingsSheet = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp)
                            .offset(y = if (showChrome) (-52).dp else (-12).dp)
                    )
                }
                if (autoScrollModeActive && settings.readingMode == ReaderReadingMode.VERTICAL) {
                    SharedMobileEpubAutoScrollControls(
                        isPlaying = autoScroll,
                        speed = autoScrollSpeed,
                        isLocalMode = autoScrollIsLocal,
                        onPlayPause = { autoScroll = !autoScroll },
                        onSpeedChange = { requested ->
                            val next = requested.coerceIn(12f, 160f)
                            autoScrollSpeed = next
                            if (autoScrollIsLocal) {
                                autoScrollLocalSpeed = next
                            } else {
                                onReaderAutoScrollSpeedChange(next)
                            }
                        },
                        onLocalModeChange = { useLocal ->
                            if (useLocal != autoScrollIsLocal) {
                                if (useLocal) {
                                    val local = autoScrollLocalSpeed ?: autoScrollSpeed
                                    autoScrollLocalSpeed = local
                                    autoScrollSpeed = local
                                } else {
                                    autoScrollLocalSpeed = autoScrollSpeed
                                    autoScrollSpeed = readerAutoScrollSpeed
                                }
                                autoScrollIsLocal = useLocal
                            }
                        },
                        onClose = { autoScroll = false; autoScrollModeActive = false },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp)
                            .offset(y = if (showChrome) (-52).dp else (-12).dp)
                    )
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
                        isSearching = isSearchInProgress,
                        showResults = showSearchResultsPanel,
                        onShowResultsChange = { showSearchResultsPanel = it },
                        onResultClick = { result ->
                            searchResultIndex = searchResults.indexOf(result)
                            navigateSearchResult(result)
                        },
                        onDismiss = { showSearch = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if ((!showSearch || !showSearchResultsPanel) && searchResultIndex >= 0 && searchResults.isNotEmpty()) {
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
            customFonts = customFonts,
            onImportFont = onImportFont,
            onLocalModeChange = { useLocal ->
                if (useLocal != isLocalFormatMode) {
                    if (useLocal) {
                        val local = localFormatSettings ?: settings
                        localFormatSettings = local
                        settings = settings.withReaderFormatFrom(local)
                    } else {
                        localFormatSettings = settings
                        settings = settings.withReaderFormatFrom(readerDefaultSettings)
                    }
                    isLocalFormatMode = useLocal
                }
            },
            onSettingsChange = {
                val next = it
                settings = next
                if (isLocalFormatMode) {
                    localFormatSettings = next
                } else {
                    onReaderDefaultSettingsChange(next)
                }
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
            readerBrightness = readerBrightness,
            readerBrightnessSupported = readerBrightnessSupported,
            onReaderBrightnessChange = onReaderBrightnessChange,
            onSettingsChange = { settings = it },
            onDismiss = { showVisualOptionsSheet = false }
        )
    }
    if (showCustomizeToolsSheet) {
        ModalBottomSheet(onDismissRequest = { showCustomizeToolsSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Customize Toolbar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                SharedReaderToolbarControls(
                    toolbarPreferences = sanitizedToolbarPreferences,
                    onToolbarPreferencesChange = onReaderToolbarPreferencesChange,
                    availableTools = SharedMobileEpubCustomizableTools
                )
            }
        }
    }
    if (showScreenOrientationSheet) {
        SharedMobileEpubScreenOrientationSheet(
            selectedMode = readerScreenOrientationMode,
            onModeSelected = onReaderScreenOrientationModeChange,
            onDismiss = { showScreenOrientationSheet = false }
        )
    }
    if (showTtsReplacementsSheet) {
        ModalBottomSheet(onDismissRequest = { showTtsReplacementsSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)
            ) {
                SharedReaderTtsReplacementControls(
                    preferences = readerTtsReplacementPreferences,
                    bookId = book.id,
                    onPreferencesChange = onReaderTtsReplacementPreferencesChange
                )
            }
        }
    }
    if (showTtsSettingsSheet) {
        SharedMobileEpubTtsSettingsSheet(
            tts = localTts,
            onDismiss = { showTtsSettingsSheet = false }
        )
    }
    if (showBookReplacementsSheet) {
        ModalBottomSheet(onDismissRequest = { showBookReplacementsSheet = false }) {
            SharedMobileEpubBookReplacementControls(
                preferences = readerBookReplacementPreferences,
                bookId = book.id,
                onPreferencesChange = onReaderBookReplacementPreferencesChange,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(bottom = 32.dp)
            )
        }
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
    editingHighlight?.let { highlight ->
        SharedMobileEpubHighlightSheet(
            highlight = highlight,
            palette = readerHighlightPalette,
            onUpdate = { updated ->
                highlights = highlights.map { current ->
                    if (current.id == updated.id) updated else current
                }
                editingHighlight = updated
            },
            onDelete = {
                highlights = highlights.filterNot { it.id == highlight.id }
                editingHighlight = null
            },
            onSpeak = { speakSelectedText(highlight.text, highlight.locator) },
            onLookup = { action ->
                openSharedMobileEpubLookup(action, highlight.text)
            },
            onDismiss = { editingHighlight = null }
        )
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
    topTools: List<ReaderTool>,
    overflowTools: List<ReaderTool>,
    showMore: Boolean,
    onShowMoreChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onTheme: () -> Unit,
    onFormat: () -> Unit,
    onSearch: () -> Unit,
    onBookmark: () -> Unit,
    onVisualOptions: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSlider: () -> Unit,
    onFileInfo: () -> Unit,
    onCustomizeTools: () -> Unit,
    onScreenOrientation: () -> Unit,
    onTtsSettings: () -> Unit,
    onTtsReplacements: () -> Unit,
    onBookReplacements: () -> Unit,
    readingMode: ReaderReadingMode,
    rightToLeftPagination: Boolean,
    tapToNavigateEnabled: Boolean,
    pageTurnAnimationEnabled: Boolean,
    onReadingModeChange: (ReaderReadingMode) -> Unit,
    onRightToLeftPaginationChange: (Boolean) -> Unit,
    onTapToNavigateChange: (Boolean) -> Unit,
    onPageTurnAnimationChange: (Boolean) -> Unit,
    toolbarPreferences: ReaderToolbarPreferences,
    localTtsState: SharedMobileEpubLocalTtsState,
    onLocalTtsToggle: () -> Unit,
    onLocalTtsStop: () -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    autoScroll: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showReadingModeExpanded by remember { mutableStateOf(false) }
    var showHiddenToolsExpanded by remember { mutableStateOf(false) }
    Surface(modifier = modifier, tonalElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().height(55.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            topTools.forEach { tool ->
                when (tool) {
                    ReaderTool.THEME -> IconButton(onClick = onTheme) {
                        Icon(Icons.Default.Palette, contentDescription = "Theme")
                    }
                    ReaderTool.TOC -> IconButton(onClick = onOpenToc) {
                        Icon(Icons.Default.Menu, contentDescription = "Contents")
                    }
                    ReaderTool.FORMAT -> IconButton(onClick = onFormat) {
                        Text("Tᵀ", style = MaterialTheme.typography.titleLarge)
                    }
                    ReaderTool.SEARCH -> IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    ReaderTool.SLIDER -> IconButton(onClick = onOpenSlider) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Navigation slider")
                    }
                    ReaderTool.TTS_CONTROLS -> IconButton(onClick = onLocalTtsToggle) {
                        Icon(localTtsState.icon(), contentDescription = localTtsState.menuLabel())
                    }
                    ReaderTool.BRIGHTNESS -> IconButton(onClick = onVisualOptions) {
                        Icon(Icons.Default.Visibility, contentDescription = "Brightness")
                    }
                    ReaderTool.SCREEN_ORIENTATION -> IconButton(onClick = onScreenOrientation) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Screen orientation")
                    }
                    else -> Unit
                }
            }
            Box {
                IconButton(onClick = { onShowMoreChange(true) }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = showMore, onDismissRequest = { onShowMoreChange(false) }) {
                    DropdownMenuItem(
                        text = { Text("Customize Toolbar") },
                        onClick = { onShowMoreChange(false); onCustomizeTools() },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                    val hiddenToolbarTools = toolbarPreferences.sanitized().toolOrder.filter { tool ->
                        tool in SharedMobileEpubToolbarTools && !toolbarPreferences.isVisible(tool)
                    }
                    if (hiddenToolbarTools.isNotEmpty()) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Hidden tools") },
                            onClick = { showHiddenToolsExpanded = !showHiddenToolsExpanded },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                        )
                        if (showHiddenToolsExpanded) {
                            hiddenToolbarTools.forEach { tool ->
                                DropdownMenuItem(
                                    text = { Text(tool.title) },
                                    onClick = {
                                        when (tool) {
                                            ReaderTool.THEME -> onTheme()
                                            ReaderTool.TOC -> onOpenToc()
                                            ReaderTool.FORMAT -> onFormat()
                                            ReaderTool.SEARCH -> onSearch()
                                            ReaderTool.SLIDER -> onOpenSlider()
                                            ReaderTool.TTS_CONTROLS -> onLocalTtsToggle()
                                            ReaderTool.BRIGHTNESS -> onVisualOptions()
                                            ReaderTool.SCREEN_ORIENTATION -> onScreenOrientation()
                                            else -> Unit
                                        }
                                        showHiddenToolsExpanded = false
                                        onShowMoreChange(false)
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    overflowTools.forEach { tool ->
                        when (tool) {
                            ReaderTool.READING_MODE -> {
                                DropdownMenuItem(
                                    text = { Text("Change Reading Mode") },
                                    onClick = { showReadingModeExpanded = !showReadingModeExpanded },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                                )
                                if (showReadingModeExpanded) {
                                    DropdownMenuItem(
                                        text = { Text("Vertical scroll") },
                                        onClick = {
                                            onReadingModeChange(ReaderReadingMode.VERTICAL)
                                            showReadingModeExpanded = false
                                            onShowMoreChange(false)
                                        },
                                        trailingIcon = { if (readingMode == ReaderReadingMode.VERTICAL) Text("✓") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Paginated") },
                                        onClick = {
                                            onRightToLeftPaginationChange(false)
                                            onReadingModeChange(ReaderReadingMode.PAGINATED)
                                            showReadingModeExpanded = false
                                            onShowMoreChange(false)
                                        },
                                        trailingIcon = { if (readingMode == ReaderReadingMode.PAGINATED && !rightToLeftPagination) Text("✓") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Right-to-left pagination") },
                                        onClick = {
                                            onRightToLeftPaginationChange(true)
                                            onReadingModeChange(ReaderReadingMode.PAGINATED)
                                            showReadingModeExpanded = false
                                            onShowMoreChange(false)
                                        },
                                        trailingIcon = { if (readingMode == ReaderReadingMode.PAGINATED && rightToLeftPagination) Text("✓") }
                                    )
                                }
                            }
                            ReaderTool.TAP_TO_TURN -> SharedMobileEpubSwitchMenuItem(
                                "Tap to Turn Pages",
                                tapToNavigateEnabled,
                                onTapToNavigateChange,
                                enabled = readingMode == ReaderReadingMode.PAGINATED
                            )
                            ReaderTool.PAGE_TURN_ANIM -> SharedMobileEpubSwitchMenuItem(
                                "Realistic Page Turns",
                                pageTurnAnimationEnabled,
                                onPageTurnAnimationChange,
                                enabled = readingMode == ReaderReadingMode.PAGINATED
                            )
                            ReaderTool.BOOKMARK -> DropdownMenuItem(
                                text = { Text(if (isBookmarked) "Remove Bookmark" else "Bookmark this page") },
                                onClick = { onBookmark(); onShowMoreChange(false) },
                                leadingIcon = { Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null) }
                            )
                            ReaderTool.VISUAL_OPTIONS -> DropdownMenuItem(
                                text = { Text("Visual Options") }, onClick = { onVisualOptions(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                            ReaderTool.TOC -> DropdownMenuItem(
                                text = { Text("Contents") }, onClick = { onOpenToc(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.Menu, contentDescription = null) }
                            )
                            ReaderTool.FORMAT -> DropdownMenuItem(
                                text = { Text("Text formatting") }, onClick = { onFormat(); onShowMoreChange(false) }
                            )
                            ReaderTool.SEARCH -> DropdownMenuItem(
                                text = { Text("Search") }, onClick = { onSearch(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                            )
                            ReaderTool.SLIDER -> DropdownMenuItem(
                                text = { Text("Navigation slider") }, onClick = { onShowMoreChange(false); onOpenSlider() },
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) }
                            )
                            ReaderTool.TTS_CONTROLS -> DropdownMenuItem(
                                text = { Text(localTtsState.menuLabel()) }, onClick = { onLocalTtsToggle(); onShowMoreChange(false) }
                            )
                            ReaderTool.TTS_REPLACEMENTS -> DropdownMenuItem(
                                text = { Text("TTS Word Replacements") },
                                onClick = { onShowMoreChange(false); onTtsReplacements() }
                            )
                            ReaderTool.TTS_SETTINGS -> DropdownMenuItem(
                                text = { Text("TTS Voice Settings") },
                                onClick = { onShowMoreChange(false); onTtsSettings() }
                            )
                            ReaderTool.BOOK_REPLACEMENTS -> DropdownMenuItem(
                                text = { Text("Book Word Replacements") },
                                onClick = { onShowMoreChange(false); onBookReplacements() }
                            )
                            ReaderTool.KEEP_SCREEN_ON -> SharedMobileEpubSwitchMenuItem("Keep Screen On", keepScreenOn, onKeepScreenOnChange)
                            ReaderTool.AUTO_SCROLL -> DropdownMenuItem(
                                text = { Text(if (autoScroll) "Stop Auto Scroll" else "Auto Scroll") },
                                enabled = readingMode == ReaderReadingMode.VERTICAL,
                                onClick = { onAutoScrollChange(!autoScroll); onShowMoreChange(false) }
                            )
                            ReaderTool.BRIGHTNESS -> DropdownMenuItem(
                                text = { Text("Brightness") }, onClick = { onVisualOptions(); onShowMoreChange(false) }
                            )
                            ReaderTool.SCREEN_ORIENTATION -> DropdownMenuItem(
                                text = { Text("Screen Orientation") }, onClick = { onScreenOrientation(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) }
                            )
                            ReaderTool.FILE_INFO -> DropdownMenuItem(
                                text = { Text("File Information") }, onClick = { onFileInfo(); onShowMoreChange(false) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                            )
                            ReaderTool.THEME -> Unit
                            else -> Unit
                        }
                    }
                    if (ReaderTool.TTS_CONTROLS in overflowTools && localTtsState != SharedMobileEpubLocalTtsState.IDLE) {
                        DropdownMenuItem(
                            text = { Text("Stop reading") },
                            onClick = { onLocalTtsStop(); onShowMoreChange(false) }
                        )
                    }
                }
            }
        }
    }
}

private val SharedMobileEpubToolbarTools = setOf(
    ReaderTool.THEME,
    ReaderTool.SLIDER,
    ReaderTool.TOC,
    ReaderTool.FORMAT,
    ReaderTool.SEARCH,
    ReaderTool.TTS_CONTROLS,
    ReaderTool.BRIGHTNESS,
    ReaderTool.SCREEN_ORIENTATION
)

private val SharedMobileEpubCustomizableTools = SharedMobileEpubToolbarTools + setOf(
    ReaderTool.READING_MODE,
    ReaderTool.BOOKMARK,
    ReaderTool.TAP_TO_TURN,
    ReaderTool.PAGE_TURN_ANIM,
    ReaderTool.KEEP_SCREEN_ON,
    ReaderTool.VISUAL_OPTIONS,
    ReaderTool.AUTO_SCROLL,
    ReaderTool.TTS_SETTINGS,
    ReaderTool.TTS_REPLACEMENTS,
    ReaderTool.BOOK_REPLACEMENTS,
    ReaderTool.FILE_INFO
)

@Composable
private fun SharedMobileEpubSwitchMenuItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    DropdownMenuItem(
        text = { Text(label) },
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        trailingIcon = { Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileEpubScreenOrientationSheet(
    selectedMode: ReaderScreenOrientationMode,
    onModeSelected: (ReaderScreenOrientationMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Screen Orientation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Choose how the reader responds when you rotate your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderScreenOrientationMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.title, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedMobileEpubBottomBar(
    tools: List<ReaderTool>,
    readingMode: ReaderReadingMode,
    isBookmarked: Boolean,
    onReadingModeToggle: () -> Unit,
    onToc: () -> Unit,
    onFormat: () -> Unit,
    onSearch: () -> Unit,
    onTheme: () -> Unit,
    onBookmark: () -> Unit,
    onVisualOptions: () -> Unit,
    onOpenSlider: () -> Unit,
    localTtsState: SharedMobileEpubLocalTtsState,
    onLocalTtsToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().height(45.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tools.forEach { tool ->
                    when (tool) {
                        ReaderTool.READING_MODE -> IconButton(onClick = onReadingModeToggle) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = if (readingMode == ReaderReadingMode.VERTICAL) "Use paginated mode" else "Use vertical mode")
                        }
                        ReaderTool.TOC -> IconButton(onClick = onToc) { Icon(Icons.Default.Menu, contentDescription = "Contents") }
                        ReaderTool.FORMAT -> IconButton(onClick = onFormat) { Text("Tᵀ", style = MaterialTheme.typography.titleLarge) }
                        ReaderTool.SEARCH -> IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Search") }
                        ReaderTool.THEME -> IconButton(onClick = onTheme) { Icon(Icons.Default.Palette, contentDescription = "Theme") }
                        ReaderTool.BOOKMARK -> IconButton(onClick = onBookmark) {
                            Icon(
                                if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark this page"
                            )
                        }
                        ReaderTool.VISUAL_OPTIONS -> IconButton(onClick = onVisualOptions) { Icon(Icons.Default.Settings, contentDescription = "Visual options") }
                        ReaderTool.SLIDER -> IconButton(onClick = onOpenSlider) { Icon(Icons.Default.SwapHoriz, contentDescription = "Navigation slider") }
                        ReaderTool.TTS_CONTROLS -> IconButton(onClick = onLocalTtsToggle) {
                            Icon(localTtsState.icon(), contentDescription = localTtsState.menuLabel())
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

private fun SharedMobileEpubLocalTtsState.menuLabel(): String = when (this) {
    SharedMobileEpubLocalTtsState.IDLE -> "Read aloud"
    SharedMobileEpubLocalTtsState.SPEAKING -> "Pause reading"
    SharedMobileEpubLocalTtsState.PAUSED -> "Resume reading"
}

private fun SharedMobileEpubLocalTtsState.icon() = when (this) {
    SharedMobileEpubLocalTtsState.IDLE -> Icons.AutoMirrored.Filled.VolumeUp
    SharedMobileEpubLocalTtsState.SPEAKING -> Icons.Default.Pause
    SharedMobileEpubLocalTtsState.PAUSED -> Icons.Default.PlayArrow
}

@Composable
private fun SharedMobileEpubTtsControls(
    tts: SharedMobileEpubLocalTts,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = tts.progress
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    progress.currentChunk?.chapterTitle?.ifBlank { "Read aloud" } ?: "Preparing read aloud…",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    progress.currentPositionLabel ?: "Device voice",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = tts::skipPrevious, enabled = progress.currentChunkIndex > 0) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous reading part")
            }
            IconButton(
                onClick = {
                    if (tts.state == SharedMobileEpubLocalTtsState.SPEAKING) tts.pause() else tts.resume()
                }
            ) {
                Icon(
                    if (tts.state == SharedMobileEpubLocalTtsState.SPEAKING) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (tts.state == SharedMobileEpubLocalTtsState.SPEAKING) "Pause reading" else "Resume reading"
                )
            }
            IconButton(
                onClick = tts::skipNext,
                enabled = progress.currentChunkIndex in 0 until progress.chunks.lastIndex
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next reading part")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "TTS voice settings")
            }
            IconButton(onClick = tts::stop) {
                Icon(Icons.Default.Close, contentDescription = "Stop reading", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileEpubTtsSettingsSheet(
    tts: SharedMobileEpubLocalTts,
    onDismiss: () -> Unit
) {
    var rate by remember(tts.speechRate) { mutableStateOf(tts.speechRate) }
    var pitch by remember(tts.speechPitch) { mutableStateOf(tts.speechPitch) }
    var showVoices by remember { mutableStateOf(false) }
    val selectedVoice = tts.availableVoices.firstOrNull { it.identifier == tts.selectedVoiceIdentifier }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("TTS Voice Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Box {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showVoices = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(selectedVoice?.name ?: "System default", fontWeight = FontWeight.SemiBold)
                            Text(
                                selectedVoice?.language ?: "Uses the voice selected by iOS",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose voice")
                    }
                }
                DropdownMenu(
                    expanded = showVoices,
                    onDismissRequest = { showVoices = false },
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    DropdownMenuItem(
                        text = { Column { Text("System default"); Text("Uses iOS settings", style = MaterialTheme.typography.bodySmall) } },
                        onClick = { tts.setVoice(null); showVoices = false },
                        trailingIcon = {
                            IconButton(onClick = { tts.previewVoice(null) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Preview system voice")
                            }
                        }
                    )
                    tts.availableVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Column { Text(voice.name); Text(voice.language, style = MaterialTheme.typography.bodySmall) } },
                            onClick = { tts.setVoice(voice.identifier); showVoices = false },
                            trailingIcon = {
                                IconButton(onClick = { tts.previewVoice(voice.identifier) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Preview ${voice.name}")
                                }
                            }
                        )
                    }
                }
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Speech rate")
                    Text("${(rate * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(value = rate, onValueChange = {
                    rate = it
                    tts.setSpeechParameters(rate, pitch)
                }, valueRange = 0.5f..3f)
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pitch")
                    Text("${(pitch * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(value = pitch, onValueChange = {
                    pitch = it
                    tts.setSpeechParameters(rate, pitch)
                }, valueRange = 0.5f..2f)
            }
            TextButton(onClick = {
                rate = 1f
                pitch = 1f
                tts.setSpeechParameters(rate, pitch)
            }) { Text("Reset") }
        }
    }
}

@Composable
private fun SharedMobileEpubBookReplacementControls(
    preferences: ReaderBookReplacementPreferences,
    bookId: String,
    onPreferencesChange: (ReaderBookReplacementPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    val rules = preferences.rulesForFile(bookId)
    var editingRuleId by remember(bookId) { mutableStateOf<String?>(null) }
    var isAdding by remember(bookId) { mutableStateOf(false) }
    val editingRule = editingRuleId?.let { id -> rules.firstOrNull { it.id == id } }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Book Word Replacements", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Replace visible text in this book only. Locations, bookmarks, and the original EPUB remain unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { isAdding = true; editingRuleId = null }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add rule")
        }
        if (isAdding || editingRule != null) {
            SharedMobileEpubBookReplacementEditor(
                seed = editingRule,
                newRuleId = "book_${currentTimestamp()}_${rules.size}",
                onCancel = { isAdding = false; editingRuleId = null },
                onSave = { saved ->
                    val updated = if (editingRule == null) rules + saved else rules.map { if (it.id == editingRule.id) saved else it }
                    onPreferencesChange(preferences.withFileRules(bookId, updated))
                    isAdding = false
                    editingRuleId = null
                }
            )
        }
        if (rules.isEmpty()) {
            Text("No replacements for this book yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rules.forEach { rule ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${rule.from} → ${rule.to.ifBlank { "(remove)" }}", fontWeight = FontWeight.SemiBold)
                            Text(
                                buildList {
                                    add(if (rule.isRegex) "Regex" else "Plain text")
                                    if (rule.wholeWord) add("whole word")
                                    if (rule.matchCase) add("case-sensitive")
                                }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { enabled ->
                                onPreferencesChange(
                                    preferences.withFileRules(bookId, rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                                )
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editingRuleId = rule.id; isAdding = false }) { Text("Edit") }
                        TextButton(onClick = {
                            onPreferencesChange(preferences.withFileRules(bookId, rules.filterNot { it.id == rule.id }))
                        }) { Text("Delete") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SharedMobileEpubBookReplacementEditor(
    seed: ReaderWordReplacementRule?,
    newRuleId: String,
    onCancel: () -> Unit,
    onSave: (ReaderWordReplacementRule) -> Unit
) {
    val ruleId = seed?.id ?: newRuleId
    var from by remember(ruleId) { mutableStateOf(seed?.from.orEmpty()) }
    var to by remember(ruleId) { mutableStateOf(seed?.to.orEmpty()) }
    var enabled by remember(ruleId) { mutableStateOf(seed?.enabled ?: true) }
    var isRegex by remember(ruleId) { mutableStateOf(seed?.isRegex ?: false) }
    var wholeWord by remember(ruleId) { mutableStateOf(seed?.wholeWord ?: true) }
    var matchCase by remember(ruleId) { mutableStateOf(seed?.matchCase ?: false) }
    val draft = ReaderWordReplacementRule(ruleId, from, to, enabled, isRegex, matchCase, wholeWord)
    val validation = ReaderWordReplacementEngine.validate(draft)

    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (seed == null) "New replacement" else "Edit replacement", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(from, { from = it }, label = { Text("Replace") }, isError = !validation.isValid, modifier = Modifier.fillMaxWidth())
            validation.message?.takeIf { !validation.isValid }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(to, { to = it }, label = { Text("With") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(enabled, { enabled = !enabled }, label = { Text("Enabled") })
                FilterChip(isRegex, { isRegex = !isRegex }, label = { Text("Regex") })
                FilterChip(wholeWord, { wholeWord = !wholeWord }, label = { Text("Whole word") })
                FilterChip(matchCase, { matchCase = !matchCase }, label = { Text("Match case") })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(enabled = validation.isValid, onClick = { onSave(draft) }) { Text("Save") }
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
    onBookmarkRename: (ReaderBookmark, String) -> Unit,
    onBookmarkDelete: (ReaderBookmark) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuBookmark by remember { mutableStateOf<ReaderBookmark?>(null) }
    var renameBookmark by remember { mutableStateOf<ReaderBookmark?>(null) }
    var deleteBookmark by remember { mutableStateOf<ReaderBookmark?>(null) }
    if (bookmarks.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No bookmarks yet") }
        return
    }
    LazyColumn(modifier) {
        items(bookmarks.sortedBy { it.pageIndex }, key = ReaderBookmark::id) { bookmark ->
            NavigationDrawerItem(
                label = {
                    Column {
                        Text(bookmark.label?.takeIf { it.isNotBlank() } ?: bookmark.preview.ifBlank { "Bookmark" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Text(bookmark.chapterTitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                selected = false,
                onClick = { onBookmarkClick(bookmark) },
                icon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                badge = {
                    Box {
                        IconButton(onClick = { menuBookmark = bookmark }) { Icon(Icons.Default.MoreVert, contentDescription = "Bookmark options") }
                        DropdownMenu(expanded = menuBookmark?.id == bookmark.id, onDismissRequest = { menuBookmark = null }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { renameBookmark = bookmark; menuBookmark = null })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { deleteBookmark = bookmark; menuBookmark = null })
                        }
                    }
                }
            )
        }
    }
    renameBookmark?.let { bookmark ->
        var label by remember(bookmark.id) { mutableStateOf(bookmark.label ?: bookmark.preview) }
        AlertDialog(
            onDismissRequest = { renameBookmark = null },
            title = { Text("Rename Bookmark") },
            text = { OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("New name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onBookmarkRename(bookmark, label); renameBookmark = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameBookmark = null }) { Text("Cancel") } }
        )
    }
    deleteBookmark?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { deleteBookmark = null },
            title = { Text("Delete Bookmark?") },
            text = { Text("This bookmark will be removed from the book.") },
            confirmButton = { TextButton(onClick = { onBookmarkDelete(bookmark); deleteBookmark = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteBookmark = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SharedMobileEpubHighlights(
    highlights: List<UserHighlight>,
    onHighlightClick: (UserHighlight) -> Unit,
    onHighlightEdit: (UserHighlight) -> Unit,
    modifier: Modifier = Modifier
) {
    var notesOnly by remember { mutableStateOf(false) }
    if (highlights.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No annotations yet") }
        return
    }
    val filteredHighlights = if (notesOnly) highlights.filter { !it.note.isNullOrBlank() } else highlights
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = !notesOnly, onClick = { notesOnly = false }, label = { Text("All") })
            FilterChip(selected = notesOnly, onClick = { notesOnly = true }, label = { Text("With Notes") })
        }
        if (filteredHighlights.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No annotations with notes") }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                items(filteredHighlights.sortedBy { it.chapterIndex }, key = { it.id }) { highlight ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onHighlightClick(highlight) },
                        shape = RoundedCornerShape(12.dp),
                        color = highlight.effectiveColor.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = highlight.text.ifBlank { "Highlight" },
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                highlight.note?.takeIf { it.isNotBlank() }?.let { note ->
                                    Text(note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                            IconButton(onClick = { onHighlightEdit(highlight) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit annotation")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileEpubHighlightSheet(
    highlight: UserHighlight,
    palette: ReaderHighlightPalette,
    onUpdate: (UserHighlight) -> Unit,
    onDelete: () -> Unit,
    onSpeak: () -> Unit,
    onLookup: (ReaderExternalLookupAction) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    var note by remember(highlight.id, highlight.note) { mutableStateOf(highlight.note.orEmpty()) }
    var confirmDelete by remember(highlight.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Annotation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Surface(
                color = highlight.effectiveColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = highlight.text.ifBlank { "Highlight" },
                    modifier = Modifier.padding(14.dp),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(highlight.text)) }) { Text("Copy") }
                TextButton(onClick = onSpeak) { Text("Speak") }
                TextButton(onClick = { onLookup(ReaderExternalLookupAction.DICTIONARY) }) { Text("Dictionary") }
                TextButton(onClick = { onLookup(ReaderExternalLookupAction.TRANSLATE) }) { Text("Translate") }
                TextButton(onClick = { onLookup(ReaderExternalLookupAction.SEARCH) }) { Text("Search") }
            }
            Text("Color", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                palette.sanitized().colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(color.color)
                            .border(
                                width = if (color == highlight.color) 3.dp else 1.dp,
                                color = if (color == highlight.color) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clickable { onUpdate(highlight.copy(color = color, colorArgb = null)) }
                    )
                }
            }
            Text("Style", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HighlightStyle.entries.forEach { style ->
                    val label = when (style) {
                        HighlightStyle.BACKGROUND -> "Background"
                        HighlightStyle.UNDERLINE -> "Underline"
                        HighlightStyle.WAVY_UNDERLINE -> "Wavy"
                        HighlightStyle.STRIKETHROUGH -> "Strike"
                    }
                    FilterChip(
                        selected = highlight.style == style,
                        onClick = { onUpdate(highlight.copy(style = style)) },
                        label = { Text(label) }
                    )
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Comment") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = {
                    onUpdate(highlight.copy(note = note.trim().takeIf { it.isNotBlank() }))
                    onDismiss()
                }) { Text("Save comment") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete annotation?") },
            text = { Text("This removes the highlight and its comment.") },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SharedMobileEpubImages(
    images: List<ReaderImageReference>,
    onImageClick: (ReaderImageReference) -> Unit,
    modifier: Modifier = Modifier
) {
    if (images.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No images in this book") }
        return
    }
    LazyColumn(modifier) {
        items(images, key = { it.id }) { image ->
            val downloadableBytes = remember(image.source) { image.downloadBytes() }
            NavigationDrawerItem(
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                        Text(image.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            image.chapterTitle,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                            listOfNotNull(image.dimensionLabel, image.sourceName()).joinToString(" · ").takeIf { it.isNotBlank() }?.let { metadata ->
                                Text(metadata, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(
                            onClick = {
                                downloadableBytes?.let { bytes ->
                                    shareSharedMobileEpubImage(bytes, image.suggestedDownloadFileName())
                                }
                            },
                            enabled = downloadableBytes != null
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Save or share image")
                        }
                    }
                },
                selected = false,
                onClick = { onImageClick(image) },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileEpubFormatSheet(
    settings: ReaderSettings,
    isLocalMode: Boolean,
    customFonts: List<CustomFontItem>,
    onImportFont: () -> Unit,
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
                SharedReaderTextAlign.LEFT -> "Left"
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
                                onSettingsChange(settings.copy(textAlign = when (label) {
                                    "Left" -> SharedReaderTextAlign.LEFT
                                    "Right" -> SharedReaderTextAlign.RIGHT
                                    "Justify" -> SharedReaderTextAlign.JUSTIFY
                                    else -> SharedReaderTextAlign.START
                                }))
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
                    if (customFonts.any { !it.isDeleted }) {
                        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                        item {
                            Text(
                                "Imported Fonts",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                        items(customFonts.filterNot { it.isDeleted }.sortedBy { it.displayName.lowercase() }, key = { it.id }) { font ->
                            NavigationDrawerItem(
                                label = { Text(font.displayName) },
                                selected = settings.customFontPath == font.path,
                                onClick = {
                                    onSettingsChange(settings.copy(fontFamily = font.displayName, customFontPath = font.path))
                                    showFontSheet = false
                                }
                            )
                        }
                    }
                    item {
                        TextButton(
                            onClick = onImportFont,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Import Font")
                        }
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
    pageInfo: ReaderPageInfo?,
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
                pageInfo?.let {
                    "$chapterTitle (${it.currentPageInChapter}/${it.totalPagesInChapter})"
                } ?: chapterTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = foreground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp)
            )
            Text(
                "${formatReaderProgress(progressPercent)}%",
                style = MaterialTheme.typography.bodySmall,
                color = foreground,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
    }
}

internal fun formatReaderProgress(progressPercent: Float): String {
    val tenths = kotlin.math.floor(progressPercent.coerceIn(0f, 100f) * 10f).toInt()
    return "${tenths / 10}.${tenths % 10}"
}

private fun Long.hasDarkReaderBackground(): Boolean {
    fun channel(shift: Int): Double {
        val value = ((this ushr shift) and 0xFF).toDouble() / 255.0
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0) < 0.5
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
private fun SharedMobileEpubVisualOptionsSheet(
    settings: ReaderSettings,
    readerBrightness: Float?,
    readerBrightnessSupported: Boolean,
    onReaderBrightnessChange: (Float?) -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Visual Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Text("System UI", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose when the status and navigation bars are visible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SharedMobileEpubEnumChoices(SystemUiMode.entries, settings.systemUiMode, { it.title }) { onSettingsChange(settings.copy(systemUiMode = it)) }
            Spacer(Modifier.height(8.dp))
            Text("Progress Bar", style = MaterialTheme.typography.titleMedium)
            Text(
                "Show the current chapter page and reading percentage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SharedMobileEpubEnumChoices(PageInfoMode.entries, settings.pageInfoMode, { it.title }) { onSettingsChange(settings.copy(pageInfoMode = it)) }
            Text("Progress Bar Position", style = MaterialTheme.typography.titleSmall)
            SharedMobileEpubEnumChoices(PageInfoPosition.entries, settings.pageInfoPosition, { it.title }) { onSettingsChange(settings.copy(pageInfoPosition = it)) }
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onSettingsChange(settings.copy(seamlessChapterNavigation = !settings.seamlessChapterNavigation))
                        }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Seamless Chapter Transition", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Turn this off to pull past the edge before changing chapters.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Switch(
                            checked = !settings.seamlessChapterNavigation,
                            onCheckedChange = { seamless -> onSettingsChange(settings.copy(seamlessChapterNavigation = !seamless)) }
                        )
                    }
                    if (settings.seamlessChapterNavigation) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Column(Modifier.padding(16.dp)) {
                            SharedMobileEpubSettingSlider(
                                "Pull Distance to Change Chapter",
                                settings.chapterTurnDragMultiplier,
                                0.5f..2f,
                                14
                            ) { onSettingsChange(settings.copy(chapterTurnDragMultiplier = it)) }
                        }
                    }
                }
            }
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
private fun SharedMobileEpubSearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<SharedMobileEpubSearchResult>,
    isSearching: Boolean,
    showResults: Boolean,
    onShowResultsChange: (Boolean) -> Unit,
    onResultClick: (SharedMobileEpubSearchResult) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Surface(tonalElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search in book") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onShowResultsChange(!showResults) },
                    enabled = results.isNotEmpty()
                ) {
                    Icon(
                        if (showResults) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = if (showResults) "Hide results" else "Show results"
                    )
                }
            }
        }
        if (showResults) {
            Surface(Modifier.fillMaxWidth().weight(1f), tonalElevation = 8.dp) {
                Column {
                    if (isSearching) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Searching…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(
                            if (query.isBlank()) "Enter a search term" else "${results.size} results",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
        } else {
            Spacer(Modifier.weight(1f))
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
private data class SharedMobileEpubSelectionAction(val action: String, val text: String, val locator: ReaderLocator?)

private val SharedMobileEpubJson = Json { ignoreUnknownKeys = true }

private fun String.sharedMobileEpubLocatorOrNull(): ReaderLocator? {
    val objectValue = runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    return objectValue.toMobileEpubLocator()
}

private fun String.sharedMobileEpubHighlightOrNull(): UserHighlight? {
    val objectValue = runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    val text = objectValue["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val cfi = objectValue["cfi"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
    val locator = (objectValue["locator"] as? JsonObject)?.toMobileEpubLocator()
        ?: ReaderLocator(cfi = cfi, textQuote = text)
    val chapterIndex = locator.chapterIndex
        ?: objectValue["chapterIndex"]?.jsonPrimitive?.intOrNull
        ?: return null
    if (text.isBlank()) return null
    val color = HighlightColor.entries.firstOrNull {
        it.id == objectValue["colorId"]?.jsonPrimitive?.contentOrNull
    } ?: HighlightColor.YELLOW
    val normalizedLocator = locator.withFallbacks(
        chapterIndex = chapterIndex,
        cfi = cfi,
        textQuote = text
    )
    val stableId = "mobile-web-$chapterIndex-${cfi.hashCode()}-${normalizedLocator.startOffset ?: -1}-${normalizedLocator.endOffset ?: -1}"
    return UserHighlight(
        id = stableId,
        cfi = cfi,
        text = text,
        color = color,
        chapterIndex = chapterIndex,
        locator = normalizedLocator
    )
}

private fun String.sharedMobileEpubSelectionActionOrNull(): SharedMobileEpubSelectionAction? {
    val objectValue = runCatching { Json.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    val action = objectValue["action"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase().orEmpty()
    val text = objectValue["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (action.isBlank() || text.isBlank()) return null
    val locator = objectValue["locator"]?.let { element ->
        runCatching { element.jsonObject.toMobileEpubLocator() }.getOrNull()
    }
    return SharedMobileEpubSelectionAction(action = action, text = text, locator = locator)
}

private fun String.sharedMobileEpubHighlightIdOrNull(): String? {
    return runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }
        .getOrNull()
        ?.get("id")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
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

private fun sharedMobileEpubTtsNavigationScript(locator: ReaderLocator?): String {
    val locatorJson = locator?.let { target ->
        buildJsonObject {
            target.chapterIndex?.let { put("chapterIndex", it) }
            target.pageIndex?.let { put("pageIndex", it) }
            target.startOffset?.let { put("startOffset", it) }
            target.endOffset?.let { put("endOffset", it) }
            target.textQuote?.let { put("textQuote", it) }
            target.cfi?.let { put("cfi", it) }
        }.toString()
    } ?: "null"
    return "if (window.readerSetTtsLocator) window.readerSetTtsLocator($locatorJson, true);"
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
private fun SharedMobileEpubAutoScrollControls(
    isPlaying: Boolean,
    speed: Float,
    isLocalMode: Boolean,
    onPlayPause: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onLocalModeChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showModeMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlayPause) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pause auto scroll" else "Resume auto scroll")
                }
                Box {
                    TextButton(onClick = { showModeMenu = true }) {
                        Text(if (isLocalMode) "Local Speed" else "Global Speed")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select auto-scroll mode")
                    }
                    DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                        DropdownMenuItem(
                            text = { Column { Text("Global Speed", fontWeight = FontWeight.Bold); Text("Applies to all files", style = MaterialTheme.typography.bodySmall) } },
                            trailingIcon = { if (!isLocalMode) Text("✓") },
                            onClick = { onLocalModeChange(false); showModeMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Column { Text("Local Speed", fontWeight = FontWeight.Bold); Text("Saved for this file", style = MaterialTheme.typography.bodySmall) } },
                            trailingIcon = { if (isLocalMode) Text("✓") },
                            onClick = { onLocalModeChange(true); showModeMenu = false }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("${speed.roundToInt()} px/s", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Stop auto scroll") }
            }
            Slider(value = speed.coerceIn(12f, 160f), onValueChange = onSpeedChange, valueRange = 12f..160f)
        }
    }
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

private fun sharedMobileEpubAutoScrollStartScript(speed: Float): String {
    val intervalMillis = (1000f / speed.coerceIn(12f, 160f)).roundToInt().coerceAtLeast(6)
    return """
    (function () {
      if (window.readerIosAutoScrollTimer) window.clearInterval(window.readerIosAutoScrollTimer);
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
      }, $intervalMillis);
    })();
""".trimIndent()
}

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
