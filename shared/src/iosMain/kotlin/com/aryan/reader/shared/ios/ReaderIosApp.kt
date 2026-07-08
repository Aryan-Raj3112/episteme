package com.aryan.reader.shared.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.aryan.reader.shared.AppAction
import com.aryan.reader.shared.BannerMessage
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.sampleReaderScreenState
import com.aryan.reader.shared.opds.SharedOpdsCatalogs
import com.aryan.reader.shared.opds.SharedOpdsScreenState
import com.aryan.reader.shared.ui.SharedAppTheme
import com.aryan.reader.shared.ui.SharedMobileAppDrawerContent
import com.aryan.reader.shared.ui.SharedMobileHomeScreen
import com.aryan.reader.shared.ui.SharedMobileLibraryScreen
import com.aryan.reader.shared.ui.SharedMobileLibraryTab
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

class ReaderIosBridge {
    internal var importedFileNames by mutableStateOf<List<String>>(emptyList())
        private set

    internal var latestNativeEvent by mutableStateOf<String?>(null)
        private set

    fun recordImportedFiles(fileNames: List<String>) {
        importedFileNames = fileNames
        latestNativeEvent = if (fileNames.isEmpty()) {
            "Import cancelled"
        } else {
            "Selected ${fileNames.size} file(s) from iOS"
        }
    }

    fun recordNativeEvent(message: String) {
        latestNativeEvent = message
    }
}

fun readerComposeViewController(
    bridge: ReaderIosBridge,
    onImportBooks: () -> Unit
): UIViewController = ComposeUIViewController {
    ReaderIosApp(
        bridge = bridge,
        onImportBooks = onImportBooks
    )
}

private enum class ReaderIosMainPage(
    val label: String
) {
    HOME("Home"),
    LIBRARY("Library")
}

@Composable
private fun ReaderIosApp(
    bridge: ReaderIosBridge,
    onImportBooks: () -> Unit
) {
    var state by remember {
        mutableStateOf(
            sampleReaderScreenState().copy(
                isProUser = true,
                credits = 0
            )
        )
    }
    var selectedPage by remember { mutableStateOf(ReaderIosMainPage.HOME) }
    var selectedLibraryTab by remember { mutableStateOf(SharedMobileLibraryTab.BOOKS) }
    var opdsState by remember {
        var index = 0
        mutableStateOf(
            SharedOpdsScreenState(
                catalogs = SharedOpdsCatalogs.defaultCatalogs {
                    "ios_default_catalog_${index++}"
                }
            )
        )
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun showMessage(message: String) {
        state = state.withMessage(message)
        bridge.recordNativeEvent(message)
    }

    fun runDrawerAction(action: () -> Unit) {
        action()
        scope.launch { drawerState.close() }
    }

    fun openBook(book: BookItem) {
        state = state.copy(
            selectedBookId = book.id,
            openTabIds = if (book.id in state.openTabIds) state.openTabIds else state.openTabIds + book.id,
            activeTabBookId = book.id,
            bannerMessage = BannerMessage("Opening ${book.cardTitle()} comes next")
        )
    }

    LaunchedEffect(bridge.importedFileNames) {
        val importedBooks = bridge.importedFileNames.toImportedBooks(existingBooks = state.rawLibraryBooks)
        if (importedBooks.isNotEmpty()) {
            val existingIds = state.rawLibraryBooks.mapTo(mutableSetOf()) { it.id }
            val newBooks = importedBooks.filterNot { it.id in existingIds }
            if (newBooks.isNotEmpty()) {
                val nextRawBooks = newBooks + state.rawLibraryBooks
                state = state.copy(
                    rawLibraryBooks = nextRawBooks,
                    recentBooks = newBooks + state.recentBooks,
                    libraryBooks = nextRawBooks,
                    bannerMessage = BannerMessage("Added ${newBooks.size} import(s)")
                )
                selectedPage = ReaderIosMainPage.LIBRARY
            }
        }
    }

    SharedAppTheme(
        appThemeMode = state.appThemeMode,
        appContrastOption = state.appContrastOption,
        appTextDimFactorLight = state.appTextDimFactorLight,
        appTextDimFactorDark = state.appTextDimFactorDark,
        appSeedColor = state.appSeedColor
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    SharedMobileAppDrawerContent(
                        currentUser = state.currentUser,
                        isProUser = true,
                        credits = state.credits,
                        isSyncEnabled = state.isSyncEnabled,
                        isFolderSyncEnabled = state.isFolderSyncEnabled,
                        onSignInClick = { runDrawerAction { showMessage("Sign-in bridge is next for iOS") } },
                        onSignOutClick = { runDrawerAction { showMessage("Sign-out bridge is next for iOS") } },
                        onSyncToggle = { enabled -> state = state.reduce(AppAction.SyncEnabledChanged(enabled)) },
                        onFolderSyncToggle = { enabled -> state = state.reduce(AppAction.FolderSyncEnabledChanged(enabled)) },
                        onProClick = { runDrawerAction { showMessage("Pro is enabled in the iOS standard build") } },
                        onFontsClick = { runDrawerAction { showMessage("Font importer bridge is next for iOS") } },
                        onAiSettingsClick = { runDrawerAction { showMessage("AI settings bridge is next for iOS") } },
                        onSettingsClick = { runDrawerAction { showMessage("Settings bridge is next for iOS") } },
                        onAppThemeClick = { runDrawerAction { showMessage("App theme panel is next for iOS") } },
                        onFeedbackClick = { runDrawerAction { showMessage("Feedback bridge is next for iOS") } }
                    )
                }
            ) {
                ReaderIosMobileScaffold(
                    selectedPage = selectedPage,
                    onPageSelected = { page ->
                        if (selectedPage != page) {
                            state = state.copy(selectedBookIds = emptySet())
                        }
                        selectedPage = page
                    },
                    onDrawerClick = { scope.launch { drawerState.open() } },
                    onImportBooks = onImportBooks,
                    onSearchSelected = { showMessage("Search bridge is next for iOS") }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        ImportedFilesStrip(
                            fileNames = bridge.importedFileNames,
                            modifier = Modifier.fillMaxWidth()
                        )
                        when (selectedPage) {
                            ReaderIosMainPage.HOME -> SharedMobileHomeScreen(
                                state = state,
                                onImportBooks = onImportBooks,
                                onOpenBook = ::openBook,
                                onLongPressBook = { book -> state = state.toggleBookSelection(book.id) },
                                onOpenTab = ::openBook,
                                onCloseTab = { book -> state = state.closeTab(book.id) },
                                onCloseAllTabs = { state = state.copy(openTabIds = emptyList(), activeTabBookId = null) },
                                onTogglePinned = { book -> state = state.toggleHomePinned(book.id) },
                                modifier = Modifier.fillMaxSize()
                            )

                            ReaderIosMainPage.LIBRARY -> SharedMobileLibraryScreen(
                                state = state,
                                selectedTab = selectedLibraryTab,
                                onTabChange = { selectedLibraryTab = it },
                                opdsState = opdsState,
                                onImportBooks = onImportBooks,
                                onOpenBook = ::openBook,
                                onLongPressBook = { book -> state = state.toggleBookSelection(book.id) },
                                onOpenShelf = { shelf -> showMessage("${shelf.name} shelf bridge is next") },
                                onLongPressShelf = { shelf -> showMessage("${shelf.name} actions bridge is next") },
                                onTogglePinned = { book -> state = state.toggleLibraryPinned(book.id) },
                                onOpenCatalog = { catalog -> showMessage("${catalog.title} network bridge is next") },
                                onOpenFeedUrl = { showMessage("OPDS feed bridge is next") },
                                onOpdsNavigateBack = { opdsState = opdsState.copy(isViewingCatalog = false, currentCatalog = null, currentFeed = null) },
                                onOpdsSearch = { query -> showMessage("OPDS search bridge is next: $query") },
                                onOpdsLoadNextPage = { showMessage("OPDS pagination bridge is next") },
                                onAddCatalog = { title, url, username, password ->
                                    opdsState = opdsState.copy(
                                        catalogs = SharedOpdsCatalogs.addCatalog(
                                            catalogs = opdsState.catalogs,
                                            title = title,
                                            url = url,
                                            username = username,
                                            password = password,
                                            idFactory = { "ios_catalog_${currentTimestamp()}" }
                                        )
                                    )
                                },
                                onUpdateCatalog = { id, title, url, username, password ->
                                    opdsState = opdsState.copy(
                                        catalogs = SharedOpdsCatalogs.updateCatalog(
                                            catalogs = opdsState.catalogs,
                                            id = id,
                                            title = title,
                                            url = url,
                                            username = username,
                                            password = password
                                        )
                                    )
                                },
                                onRemoveCatalog = { catalog ->
                                    opdsState = opdsState.copy(
                                        catalogs = SharedOpdsCatalogs.removeCatalog(opdsState.catalogs, catalog.id)
                                    )
                                },
                                onDownloadOpdsBook = { entry, _ -> showMessage("${entry.title} download bridge is next") },
                                onStreamOpdsBook = { entry, _ -> showMessage("${entry.title} stream bridge is next") },
                                onClearOpdsError = { opdsState = opdsState.copy(errorMessage = null) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderIosMobileScaffold(
    selectedPage: ReaderIosMainPage,
    onPageSelected: (ReaderIosMainPage) -> Unit,
    onDrawerClick: () -> Unit,
    onImportBooks: () -> Unit,
    onSearchSelected: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(selectedPage.label) },
                navigationIcon = {
                    IconButton(onClick = onDrawerClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = onSearchSelected) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onImportBooks) {
                        Icon(Icons.Default.Add, contentDescription = "Import books")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                ReaderIosMainPage.entries.forEach { page ->
                    NavigationBarItem(
                        selected = selectedPage == page,
                        onClick = { onPageSelected(page) },
                        icon = {
                            Icon(
                                imageVector = when (page) {
                                    ReaderIosMainPage.HOME -> Icons.Default.Home
                                    ReaderIosMainPage.LIBRARY -> Icons.AutoMirrored.Filled.LibraryBooks
                                },
                                contentDescription = page.label
                            )
                        },
                        label = { Text(page.label) }
                    )
                }
            }
        },
        content = content
    )
}

@Composable
private fun ImportedFilesStrip(
    fileNames: List<String>,
    modifier: Modifier = Modifier
) {
    if (fileNames.isEmpty()) return

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Selected from iOS picker",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        items(fileNames.take(3)) { fileName ->
            AssistChip(
                onClick = {},
                label = { Text(fileName, maxLines = 1) }
            )
        }
        if (fileNames.size > 3) {
            item {
                Text(
                    text = "+${fileNames.size - 3} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

private fun SharedReaderScreenState.toggleBookSelection(bookId: String): SharedReaderScreenState {
    val nextSelection = if (bookId in selectedBookIds) {
        selectedBookIds - bookId
    } else {
        selectedBookIds + bookId
    }
    return copy(selectedBookIds = nextSelection)
}

private fun SharedReaderScreenState.toggleHomePinned(bookId: String): SharedReaderScreenState {
    val nextPinned = if (bookId in pinnedHomeBookIds) {
        pinnedHomeBookIds - bookId
    } else {
        pinnedHomeBookIds + bookId
    }
    return copy(pinnedHomeBookIds = nextPinned)
}

private fun SharedReaderScreenState.toggleLibraryPinned(bookId: String): SharedReaderScreenState {
    val nextPinned = if (bookId in pinnedLibraryBookIds) {
        pinnedLibraryBookIds - bookId
    } else {
        pinnedLibraryBookIds + bookId
    }
    return copy(pinnedLibraryBookIds = nextPinned)
}

private fun SharedReaderScreenState.closeTab(bookId: String): SharedReaderScreenState {
    val nextOpenTabIds = openTabIds.filterNot { it == bookId }
    return copy(
        openTabIds = nextOpenTabIds,
        activeTabBookId = if (activeTabBookId == bookId) nextOpenTabIds.lastOrNull() else activeTabBookId
    )
}

private fun SharedReaderScreenState.withMessage(message: String): SharedReaderScreenState {
    return reduce(AppAction.BannerShown(BannerMessage(message)))
}

private fun List<String>.toImportedBooks(existingBooks: List<BookItem>): List<BookItem> {
    if (isEmpty()) return emptyList()
    val existingIds = existingBooks.mapTo(mutableSetOf()) { it.id }
    val now = currentTimestamp()
    return distinct()
        .mapIndexed { index, fileName ->
            val baseId = "ios_import_${fileName.normalizedId()}"
            var id = baseId
            var suffix = 1
            while (id in existingIds) {
                id = "${baseId}_${suffix++}"
            }
            existingIds += id
            BookItem(
                id = id,
                path = fileName,
                type = fileName.fileTypeFromExtension(),
                displayName = fileName,
                timestamp = now - index,
                title = fileName.substringBeforeLast('.', fileName),
                sourceFolder = "iOS import",
                progressPercentage = 0f
            )
        }
}

private fun String.fileTypeFromExtension(): FileType {
    return when (substringAfterLast('.', "").lowercase()) {
        "pdf" -> FileType.PDF
        "epub" -> FileType.EPUB
        "mobi" -> FileType.MOBI
        "md", "markdown" -> FileType.MD
        "txt" -> FileType.TXT
        "html", "htm" -> FileType.HTML
        "fb2" -> FileType.FB2
        "cbz" -> FileType.CBZ
        "cbr" -> FileType.CBR
        "cb7" -> FileType.CB7
        "cbt" -> FileType.CBT
        "docx" -> FileType.DOCX
        "odt" -> FileType.ODT
        "fodt" -> FileType.FODT
        "pptx" -> FileType.PPTX
        else -> FileType.UNKNOWN
    }
}

private fun String.normalizedId(): String {
    return lowercase()
        .map { char -> if (char.isLetterOrDigit()) char else '_' }
        .joinToString("")
        .trim('_')
        .ifBlank { "file" }
}

private fun BookItem.cardTitle(): String {
    return title ?: displayName
}
