package com.aryan.reader.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ImportedFile
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.LibraryProjector
import com.aryan.reader.shared.LibraryState
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.SortOrder
import com.aryan.reader.shared.sampleLibraryState
import com.aryan.reader.shared.reader.PaginatedReaderState
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SampleReaderBooks
import com.aryan.reader.shared.reader.SimplePaginator
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Episteme",
    ) {
        EpistemeDesktopApp()
    }
}

private enum class DesktopTab { HOME, LIBRARY, SHELVES, READER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpistemeDesktopApp() {
    val projector = remember { LibraryProjector() }
    val paginator = remember { SimplePaginator() }
    var state by remember { mutableStateOf(sampleLibraryState()) }
    var selectedTab by remember { mutableStateOf(DesktopTab.HOME) }
    var readerState by remember {
        val book = SampleReaderBooks.desktopWelcomeBook()
        mutableStateOf(PaginatedReaderState(book = book, pages = paginator.paginate(book, ReaderSettings())))
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun openReader(book: BookItem) {
        if (book.type != FileType.EPUB) {
            state = state.copy(message = "${book.type.name} reader support comes later. EPUB is the first desktop reader target.")
            return
        }

        val loadedBook = runCatching {
            val path = book.path
            if (path.isNullOrBlank()) {
                SampleReaderBooks.desktopWelcomeBook()
            } else {
                DesktopEpubLoader.load(File(path))
            }
        }.getOrElse { error ->
            state = state.copy(message = "Could not open EPUB: ${error.message ?: "unknown error"}")
            return
        }

        readerState = PaginatedReaderState(
            book = loadedBook,
            pages = paginator.paginate(loadedBook, readerState.settings)
        )
        selectedTab = DesktopTab.READER
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            state = state.copy(message = null)
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF006C4C),
            secondary = Color(0xFF705D49),
            tertiary = Color(0xFF9C4146),
            surface = Color(0xFFFCFCF8),
            surfaceVariant = Color(0xFFE5E8DE)
        )
    ) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationRailItem(
                        selected = selectedTab == DesktopTab.HOME,
                        onClick = { selectedTab = DesktopTab.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Home") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == DesktopTab.LIBRARY,
                        onClick = { selectedTab = DesktopTab.LIBRARY },
                        icon = { Icon(Icons.Default.LibraryBooks, contentDescription = null) },
                        label = { Text("Library") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == DesktopTab.SHELVES,
                        onClick = { selectedTab = DesktopTab.SHELVES },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        label = { Text("Shelves") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == DesktopTab.READER,
                        onClick = { selectedTab = DesktopTab.READER },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                        label = { Text("Reader") }
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            state = projector.withImportedFiles(state, chooseFiles())
                        }
                    ) {
                        Icon(Icons.Default.ImportExport, contentDescription = "Import files")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Cloud sync is Android-only for now. Desktop sync will need a separate backend adapter.")
                            }
                        }
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync")
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        DesktopTab.HOME -> HomeScreen(
                            state = state,
                            projector = projector,
                            onRead = ::openReader,
                            onSelect = { id -> state = state.toggleSelection(id) }
                        )

                        DesktopTab.LIBRARY -> LibraryScreen(
                            state = state,
                            projector = projector,
                            onStateChange = { state = it },
                            onRead = ::openReader
                        )

                        DesktopTab.SHELVES -> ShelvesScreen(
                            shelves = projector.library(state).shelves,
                            onRead = ::openReader,
                            onSelect = { id -> state = state.toggleSelection(id) },
                            selectedBookIds = state.selectedBookIds
                        )

                        DesktopTab.READER -> ReaderScreen(
                            readerState = readerState,
                            paginator = paginator,
                            onReaderStateChange = { readerState = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: LibraryState,
    projector: LibraryProjector,
    onRead: (BookItem) -> Unit,
    onSelect: (String) -> Unit
) {
    val model = projector.home(state)
    ScreenScaffold(
        title = "Home",
        subtitle = "Recent books and quick access",
        trailing = {
            AssistChip(onClick = {}, label = { Text("${state.books.size} books") })
        }
    ) {
        if (model.isEmpty) {
            EmptyState("No recent files", "Import a few books to populate the Windows shell.")
        } else {
            BookList(
                books = model.recentBooks,
                selectedBookIds = state.selectedBookIds,
                onRead = onRead,
                onSelect = onSelect
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    state: LibraryState,
    projector: LibraryProjector,
    onStateChange: (LibraryState) -> Unit,
    onRead: (BookItem) -> Unit
) {
    val model = projector.library(state)
    ScreenScaffold(
        title = "Library",
        subtitle = "Search, sort, and filter local metadata",
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SortMenu(state.sortOrder) { onStateChange(state.copy(sortOrder = it)) }
                if (state.selectedBookIds.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            onStateChange(
                                state.copy(
                                    books = state.books.filterNot { it.id in state.selectedBookIds },
                                    selectedBookIds = emptySet(),
                                    message = "Removed selected books from the desktop library."
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove")
                    }
                }
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onStateChange(state.copy(searchQuery = it)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FilterRow(
                filters = state.filters,
                onFiltersChange = { onStateChange(state.copy(filters = it)) }
            )
            BookList(
                books = model.books,
                selectedBookIds = state.selectedBookIds,
                onRead = onRead,
                onSelect = { onStateChange(state.toggleSelection(it)) }
            )
        }
    }
}

@Composable
private fun ShelvesScreen(
    shelves: List<Shelf>,
    selectedBookIds: Set<String>,
    onRead: (BookItem) -> Unit,
    onSelect: (String) -> Unit
) {
    ScreenScaffold(title = "Shelves", subtitle = "Series, folders, and tags from library metadata") {
        if (shelves.isEmpty()) {
            EmptyState("No shelves yet", "Add metadata or import folders later to populate shelves.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(shelves, key = { it.id }) { shelf ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(shelf.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Text("${shelf.bookCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        BookList(
                            books = shelf.books,
                            selectedBookIds = selectedBookIds,
                            onRead = onRead,
                            onSelect = onSelect
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderScreen(
    readerState: PaginatedReaderState,
    paginator: SimplePaginator,
    onReaderStateChange: (PaginatedReaderState) -> Unit
) {
    val page = readerState.currentPage
    val settings = readerState.settings
    val background = if (settings.darkMode) Color(0xFF171A17) else Color(0xFFFFFCF5)
    val foreground = if (settings.darkMode) Color(0xFFE7E3D8) else Color(0xFF24231F)

    ScreenScaffold(
        title = readerState.book.title,
        subtitle = listOfNotNull(readerState.book.author, page?.chapterTitle).joinToString(" - "),
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${readerState.progress.toInt()}%")
                TextButton(
                    onClick = {
                        onReaderStateChange(readerState.copy(settings = settings.copy(darkMode = !settings.darkMode)))
                    }
                ) {
                    Text(if (settings.darkMode) "Light" else "Dark")
                }
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Font ${settings.fontSize}")
                Slider(
                    value = settings.fontSize.toFloat(),
                    onValueChange = { value ->
                        onReaderStateChange(paginator.repaginate(readerState, settings.copy(fontSize = value.toInt())))
                    },
                    valueRange = 14f..28f,
                    modifier = Modifier.width(180.dp)
                )
                Text("Margin ${settings.margin}")
                Slider(
                    value = settings.margin.toFloat(),
                    onValueChange = { value ->
                        onReaderStateChange(paginator.repaginate(readerState, settings.copy(margin = value.toInt())))
                    },
                    valueRange = 24f..96f,
                    modifier = Modifier.width(180.dp)
                )
            }

            Surface(
                color = background,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier.padding(PaddingValues(settings.margin.dp)),
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        text = page?.text.orEmpty(),
                        color = foreground,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = settings.fontSize.sp,
                            lineHeight = (settings.fontSize * settings.lineSpacing).sp
                        )
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = readerState.canGoPrevious,
                    onClick = { onReaderStateChange(readerState.copy(currentPageIndex = readerState.currentPageIndex - 1)) }
                ) {
                    Icon(Icons.Default.NavigateBefore, contentDescription = null)
                    Text("Previous")
                }
                Spacer(Modifier.weight(1f))
                Text("Page ${readerState.currentPageIndex + 1} of ${readerState.pages.size}")
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = readerState.canGoNext,
                    onClick = { onReaderStateChange(readerState.copy(currentPageIndex = readerState.currentPageIndex + 1)) }
                ) {
                    Text("Next")
                    Icon(Icons.Default.NavigateNext, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun ScreenScaffold(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
        content()
    }
}

@Composable
private fun BookList(
    books: List<BookItem>,
    selectedBookIds: Set<String>,
    onRead: (BookItem) -> Unit,
    onSelect: (String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(books, key = { it.id }) { book ->
            BookRow(
                book = book,
                selected = book.id in selectedBookIds,
                onRead = { onRead(book) },
                onSelect = { onSelect(book.id) }
            )
        }
    }
}

@Composable
private fun BookRow(
    book: BookItem,
    selected: Boolean,
    onRead: () -> Unit,
    onSelect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRead)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(width = 44.dp, height = 58.dp),
                color = fileTypeColor(book.type),
                shape = RoundedCornerShape(6.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Book, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title ?: book.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(book.author, book.type.name, book.progressPercentage?.let { "${it.toInt()}%" }).joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onSelect) {
                Text(if (selected) "Selected" else "Select")
            }
        }
    }
}

@Composable
private fun FilterRow(
    filters: LibraryFilters,
    onFiltersChange: (LibraryFilters) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(FileType.PDF, FileType.EPUB, FileType.DOCX, FileType.TXT).forEach { type ->
            FilterChip(
                selected = type in filters.fileTypes,
                onClick = {
                    val updated = if (type in filters.fileTypes) filters.fileTypes - type else filters.fileTypes + type
                    onFiltersChange(filters.copy(fileTypes = updated))
                },
                label = { Text(type.name) }
            )
        }
        FilterChip(
            selected = filters.readStatus == ReadStatusFilter.UNREAD,
            onClick = {
                onFiltersChange(
                    filters.copy(
                        readStatus = if (filters.readStatus == ReadStatusFilter.UNREAD) ReadStatusFilter.ALL else ReadStatusFilter.UNREAD
                    )
                )
            },
            label = { Text("Unread") }
        )
        if (filters.isActive) {
            TextButton(onClick = { onFiltersChange(LibraryFilters()) }) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun SortMenu(
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }) {
            Text(sortOrder.label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.label) },
                    onClick = {
                        expanded = false
                        onSortOrderChange(order)
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun chooseFiles(): List<ImportedFile> {
    val dialog = FileDialog(null as Frame?, "Import books", FileDialog.LOAD).apply {
        isMultipleMode = true
        isVisible = true
    }
    return dialog.files.orEmpty().map { file ->
        ImportedFile(name = file.name, path = file.absolutePath, size = file.length())
    }
}

private fun LibraryState.toggleSelection(bookId: String): LibraryState {
    val selected = if (bookId in selectedBookIds) selectedBookIds - bookId else selectedBookIds + bookId
    return copy(selectedBookIds = selected)
}

private val SortOrder.label: String
    get() = when (this) {
        SortOrder.RECENT -> "Recent"
        SortOrder.TITLE_ASC -> "Title A-Z"
        SortOrder.AUTHOR_ASC -> "Author A-Z"
        SortOrder.PERCENT_ASC -> "Progress low"
        SortOrder.PERCENT_DESC -> "Progress high"
        SortOrder.SIZE_ASC -> "Size small"
        SortOrder.SIZE_DESC -> "Size large"
    }

private fun fileTypeColor(type: FileType): Color {
    return when (type) {
        FileType.PDF -> Color(0xFF9C4146)
        FileType.EPUB, FileType.MOBI -> Color(0xFF006C4C)
        FileType.DOCX, FileType.ODT, FileType.FODT -> Color(0xFF0F52BA)
        FileType.CBZ, FileType.CBR, FileType.CB7 -> Color(0xFF705D49)
        else -> Color(0xFF5D6B82)
    }
}
