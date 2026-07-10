package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.BuiltInReaderThemes
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubTocEntry
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import com.aryan.reader.shared.reader.layoutSignature
import com.aryan.reader.shared.toReaderSettings
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

data class SharedMobileEpubReaderSnapshot(
    val locator: ReaderLocator,
    val settings: ReaderSettings,
    val bookmarks: List<ReaderBookmark>,
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
    modifier: Modifier = Modifier
) {
    val loadState = rememberSharedMobileEpubLoadState(book)
    val loadedBook = loadState.book
    var settings by remember(book.id) {
        mutableStateOf(
            (book.readerSettings ?: ReaderSettings()).copy(readingMode = ReaderReadingMode.VERTICAL)
        )
    }
    var pages by remember(book.id) { mutableStateOf<List<ReaderPage>>(emptyList()) }
    var currentLocator by remember(book.id) { mutableStateOf(book.readerPosition) }
    var currentPageIndex by remember(book.id) { mutableStateOf(book.lastPageIndex ?: 0) }
    var bookmarks by remember(book.id) { mutableStateOf(book.readerBookmarks) }
    var showChrome by remember(book.id) { mutableStateOf(true) }
    var showFormatSheet by remember(book.id) { mutableStateOf(false) }
    var showThemeSheet by remember(book.id) { mutableStateOf(false) }
    var showSlider by remember(book.id) { mutableStateOf(false) }
    var showMore by remember(book.id) { mutableStateOf(false) }
    var showFileInfo by remember(book.id) { mutableStateOf(false) }
    var keepScreenOn by remember(book.id) { mutableStateOf(false) }
    var autoScroll by remember(book.id) { mutableStateOf(false) }
    var drawerTab by remember(book.id) { mutableStateOf(0) }
    var explicitNavigationLocator by remember(book.id) { mutableStateOf<ReaderLocator?>(null) }
    var explicitNavigationFragment by remember(book.id) { mutableStateOf<String?>(null) }
    var navigationRequestId by remember(book.id) { mutableLongStateOf(0L) }
    var commandScript by remember(book.id) { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(loadedBook?.id) {
        loadedBook?.let { onMetadataLoaded(it.title, it.author) }
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
    DisposableEffect(book.id) {
        onDispose { onKeepScreenOnChange(false) }
    }
    LaunchedEffect(autoScroll) {
        commandScript = if (autoScroll) SharedMobileEpubAutoScrollStartScript else SharedMobileEpubAutoScrollStopScript
        navigationRequestId++
    }

    val pageCount = pages.size.coerceAtLeast(1)
    val progress = ((currentPageIndex + 1).toFloat() / pageCount) * 100f
    LaunchedEffect(currentLocator, settings, bookmarks, currentPageIndex, pageCount) {
        delay(220)
        currentLocator?.let { locator ->
            onReaderStateChange(
                SharedMobileEpubReaderSnapshot(
                    locator = locator,
                    settings = settings,
                    bookmarks = bookmarks,
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
                    progressPercent = progress.coerceIn(0f, 100f),
                    pageIndex = currentPageIndex,
                    pageCount = pageCount
                )
            )
        }
        onBack()
    }

    fun navigate(locator: ReaderLocator, fragment: String? = null) {
        explicitNavigationLocator = locator
        explicitNavigationFragment = fragment
        commandScript = null
        navigationRequestId++
        currentLocator = locator
        locator.pageIndex?.let { currentPageIndex = it.coerceIn(0, pageCount - 1) }
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
                TabRow(selectedTabIndex = drawerTab) {
                    Tab(selected = drawerTab == 0, onClick = { drawerTab = 0 }, text = { Text("Contents") })
                    Tab(selected = drawerTab == 1, onClick = { drawerTab = 1 }, text = { Text("Bookmarks") })
                }
                if (drawerTab == 0) {
                    SharedMobileEpubToc(
                        epub = loadedBook,
                        currentLocator = currentLocator,
                        onEntryClick = { entry ->
                            loadedBook?.locatorForTocEntry(entry, pages)?.let { locator ->
                                navigate(locator, entry.fragmentId)
                                scope.launch { drawerState.close() }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SharedMobileEpubBookmarks(
                        bookmarks = bookmarks,
                        onBookmarkClick = { bookmark ->
                            navigate(bookmark.locator)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        },
        modifier = modifier
    ) {
        Scaffold(
            topBar = {
                if (showChrome) {
                    SharedMobileEpubTopBar(
                        title = loadedBook?.title ?: book.displayName,
                        pageIndex = currentPageIndex,
                        pageCount = pageCount,
                        isBookmarked = isBookmarked,
                        showMore = showMore,
                        onShowMoreChange = { showMore = it },
                        onBack = ::closeReader,
                        onTheme = { showThemeSheet = true },
                        onBookmark = ::toggleBookmark,
                        onFormat = { showFormatSheet = true },
                        onOpenToc = { scope.launch { drawerState.open() } },
                        onFileInfo = { showFileInfo = true },
                        keepScreenOn = keepScreenOn,
                        onKeepScreenOnChange = { keepScreenOn = it },
                        autoScroll = autoScroll,
                        onAutoScrollChange = { autoScroll = it }
                    )
                }
            },
            bottomBar = {
                if (showChrome) {
                    SharedMobileEpubBottomBar(
                        onSlider = { showSlider = !showSlider },
                        onToc = { scope.launch { drawerState.open() } },
                        onFormat = { showFormatSheet = true }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(settings.readerBackgroundColor())
            ) {
                when {
                    loadState.isLoading -> SharedMobileEpubLoading("Opening EPUB…")
                    loadState.errorMessage != null -> SharedMobileEpubError(loadState.errorMessage)
                    loadedBook != null && pages.isEmpty() -> SharedMobileEpubLoading("Preparing book layout…")
                    loadedBook != null -> {
                        val initialHtml = remember(loadedBook.id) {
                            ReaderHtmlDocumentBuilder.verticalDocument(
                                book = loadedBook,
                                settings = settings,
                                navigationLocator = currentLocator,
                                pages = pages,
                                highlightActionsEnabled = false,
                                readerAiFeaturesEnabled = false,
                                cloudTtsEnabled = false,
                                externalLookupEnabled = false,
                                showChapterTitles = false
                            )
                        }
                        val appearanceScript = remember(settings, pages) {
                            ReaderHtmlDocumentBuilder.appearanceUpdateScript(settings) + "\n" +
                                ReaderHtmlDocumentBuilder.pageAnchorsUpdateScript(pages)
                        }
                        val navigationScript = commandScript ?: explicitNavigationLocator?.let { locator ->
                            sharedMobileEpubNavigationScript(locator, explicitNavigationFragment)
                        }
                        SharedMobileEpubWebView(
                            html = initialHtml,
                            appearanceScript = appearanceScript,
                            navigationScript = navigationScript,
                            navigationRequestId = navigationRequestId,
                            onBridgeMessage = { method, payload ->
                                when (method) {
                                    "readerPointerActivity" -> showChrome = !showChrome
                                    "readerPositionChanged" -> payload.sharedMobileEpubLocatorOrNull()?.let { position ->
                                        currentLocator = position
                                        currentPageIndex = (position.pageIndex ?: currentPageIndex).coerceIn(0, pageCount - 1)
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

                if (showChrome && showSlider && pages.isNotEmpty()) {
                    SharedMobileEpubSlider(
                        pageIndex = currentPageIndex,
                        pageCount = pageCount,
                        onPageSelected = { index -> pages.getOrNull(index)?.let { navigate(it.toMobileEpubLocator(loadedBook)) } },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    )
                }
            }
        }
    }

    if (showFormatSheet) {
        SharedMobileEpubFormatSheet(
            settings = settings,
            onSettingsChange = { settings = it.copy(readingMode = ReaderReadingMode.VERTICAL) },
            onDismiss = { showFormatSheet = false }
        )
    }
    if (showThemeSheet) {
        SharedMobileEpubThemeSheet(
            selectedThemeId = settings.themeId ?: "system",
            textureAlpha = settings.textureAlpha,
            onThemeSelected = { theme -> settings = theme.toReaderSettings(settings).copy(readingMode = ReaderReadingMode.VERTICAL) },
            onTextureAlphaChange = { settings = settings.copy(textureAlpha = it) },
            onDismiss = { showThemeSheet = false }
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
    pageIndex: Int,
    pageCount: Int,
    isBookmarked: Boolean,
    showMore: Boolean,
    onShowMoreChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onTheme: () -> Unit,
    onBookmark: () -> Unit,
    onFormat: () -> Unit,
    onOpenToc: () -> Unit,
    onFileInfo: () -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    autoScroll: Boolean,
    onAutoScrollChange: (Boolean) -> Unit
) {
    Surface(tonalElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().height(55.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text("Page ${pageIndex + 1} of $pageCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onTheme) { Icon(Icons.Default.Palette, contentDescription = "Theme") }
            Box {
                IconButton(onClick = { onShowMoreChange(true) }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = showMore, onDismissRequest = { onShowMoreChange(false) }) {
                    DropdownMenuItem(
                        text = { Text("Reading Mode") },
                        onClick = { onShowMoreChange(false) },
                        trailingIcon = { Text("Vertical") },
                        enabled = false
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (isBookmarked) "Remove Bookmark" else "Bookmark this page") },
                        onClick = { onBookmark(); onShowMoreChange(false) },
                        leadingIcon = { Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Visual Options") },
                        onClick = { onFormat(); onShowMoreChange(false) },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Contents") },
                        onClick = { onOpenToc(); onShowMoreChange(false) },
                        leadingIcon = { Icon(Icons.Default.Menu, contentDescription = null) }
                    )
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
    onSlider: () -> Unit,
    onToc: () -> Unit,
    onFormat: () -> Unit
) {
    Surface(tonalElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().height(55.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSlider) { Icon(Icons.Default.SwapHoriz, contentDescription = "Navigation slider") }
            IconButton(onClick = onToc) { Icon(Icons.Default.Menu, contentDescription = "Contents") }
            IconButton(onClick = onFormat) { Icon(Icons.Default.Settings, contentDescription = "Text formatting") }
        }
    }
}

@Composable
private fun SharedMobileEpubSlider(
    pageIndex: Int,
    pageCount: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val lastPageIndex = (pageCount - 1).coerceAtLeast(0)
    var sliderValue by remember(pageIndex, pageCount) {
        mutableStateOf(pageIndex.coerceIn(0, lastPageIndex).toFloat())
    }
    Surface(modifier, shape = RoundedCornerShape(18.dp), tonalElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${sliderValue.roundToInt().coerceIn(0, lastPageIndex) + 1}", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onPageSelected(sliderValue.roundToInt().coerceIn(0, lastPageIndex)) },
                valueRange = 0f..lastPageIndex.coerceAtLeast(1).toFloat(),
                enabled = pageCount > 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text("$pageCount")
        }
    }
}

@Composable
private fun SharedMobileEpubToc(
    epub: SharedEpubBook?,
    currentLocator: ReaderLocator?,
    onEntryClick: (SharedEpubTocEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = epub?.tableOfContents.orEmpty()
    if (entries.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No table of contents") }
        return
    }
    LazyColumn(modifier) {
        items(entries) { entry ->
            NavigationDrawerItem(
                label = { Text(entry.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                selected = currentLocator?.href?.substringBefore('#') == entry.href,
                onClick = { onEntryClick(entry) },
                modifier = Modifier.padding(start = (entry.depth * 14).dp, end = 8.dp)
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileEpubFormatSheet(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 700.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Text Formatting", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
            }
            SharedMobileEpubSettingSlider("Font size", settings.fontSize.toFloat(), 12f..36f, 23) {
                onSettingsChange(settings.copy(fontSize = it.toInt()))
            }
            SharedMobileEpubSettingSlider("Line spacing", settings.lineSpacing, 1f..2.2f, 11) {
                onSettingsChange(settings.copy(lineSpacing = it))
            }
            SharedMobileEpubSettingSlider("Horizontal margin", settings.resolvedHorizontalMargin.toFloat(), 8f..96f, 21) {
                onSettingsChange(settings.copy(horizontalMargin = it.toInt()))
            }
            SharedMobileEpubSettingSlider("Vertical margin", settings.resolvedVerticalMargin.toFloat(), 0f..96f, 23) {
                onSettingsChange(settings.copy(verticalMargin = it.toInt()))
            }
            SharedMobileEpubSettingSlider("Paragraph spacing", settings.paragraphSpacing, 0.5f..2f, 14) {
                onSettingsChange(settings.copy(paragraphSpacing = it))
            }
            SharedMobileEpubSettingSlider("Image size", settings.imageScale, 0.5f..2f, 14) {
                onSettingsChange(settings.copy(imageScale = it))
            }
            Text("Font", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Default", "Serif", "Sans", "Mono").forEach { family ->
                    FilterChip(
                        selected = settings.fontFamily == family,
                        onClick = { onSettingsChange(settings.copy(fontFamily = family)) },
                        label = { Text(family) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text("Alignment", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SharedReaderTextAlign.START to "Left",
                    SharedReaderTextAlign.JUSTIFY to "Justify",
                    SharedReaderTextAlign.CENTER to "Center",
                    SharedReaderTextAlign.RIGHT to "Right"
                ).forEach { (alignment, label) ->
                    FilterChip(
                        selected = settings.textAlign == alignment,
                        onClick = { onSettingsChange(settings.copy(textAlign = alignment)) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
    selectedThemeId: String,
    textureAlpha: Float,
    onThemeSelected: (ReaderTheme) -> Unit,
    onTextureAlphaChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Reading Themes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
            }
            BuiltInReaderThemes.chunked(4).forEach { rowThemes ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    rowThemes.forEach { theme ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(72.dp).clickable { onThemeSelected(theme) }
                        ) {
                            Box(
                                Modifier
                                    .size(50.dp)
                                    .background(
                                        theme.backgroundColor.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.surface,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (theme.id == selectedThemeId) Icon(Icons.Default.Check, contentDescription = "Selected", tint = theme.textColor.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.onSurface)
                            }
                            Text(theme.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(4 - rowThemes.size) { Spacer(Modifier.width(72.dp)) }
                }
            }
            Text("Texture strength", style = MaterialTheme.typography.titleSmall)
            Slider(value = textureAlpha.coerceIn(0f, 1f), onValueChange = onTextureAlphaChange)
        }
    }
}

private data class SharedMobileEpubLink(val href: String, val chapterHref: String?)

private val SharedMobileEpubJson = Json { ignoreUnknownKeys = true }

private fun String.sharedMobileEpubLocatorOrNull(): ReaderLocator? {
    val objectValue = runCatching { SharedMobileEpubJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    return objectValue.toMobileEpubLocator()
}

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

private fun SharedEpubBook.locatorForTocEntry(entry: SharedEpubTocEntry, pages: List<ReaderPage>): ReaderLocator? {
    val chapterIndex = chapters.indexOfFirst { it.baseHref?.normalizeMobileEpubPath() == entry.href.normalizeMobileEpubPath() }
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
    val chapterIndex = chapters.indexOfFirst { it.baseHref?.normalizeMobileEpubPath() == targetPath.normalizeMobileEpubPath() }
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

private fun sharedMobileEpubNavigationScript(locator: ReaderLocator, fragment: String?): String {
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
    return """
        (function () {
          var locator = $locatorJson;
          var fragment = $fragmentJson;
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

private fun String.isExternalEpubLink(): Boolean {
    val lower = trim().lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//") ||
        lower.startsWith("mailto:") || lower.startsWith("tel:") || lower.startsWith("sms:")
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
    val value = backgroundColorArgb ?: if (darkMode) 0xFF171A17L else 0xFFFFFCF5L
    return Color((value and 0xFFFFFFFFL).toInt())
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
