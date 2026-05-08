package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.opds.OpdsAcquisition
import com.aryan.reader.shared.opds.OpdsCatalog
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.SharedOpdsDownloadState
import com.aryan.reader.shared.opds.SharedOpdsScreenState
import com.aryan.reader.shared.opds.SharedOpdsText

@Composable
fun SharedOpdsScreen(
    state: SharedOpdsScreenState,
    localLibraryBooks: List<BookItem>,
    onOpenCatalog: (OpdsCatalog) -> Unit,
    onOpenFeedUrl: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onSearch: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    onAddCatalog: (String, String, String?, String?) -> Unit,
    onUpdateCatalog: (String, String, String, String?, String?) -> Unit,
    onRemoveCatalog: (OpdsCatalog) -> Unit,
    onDownloadBook: (OpdsEntry, OpdsAcquisition) -> Unit,
    onReadBook: (BookItem) -> Unit,
    onStreamBook: (OpdsEntry, OpdsCatalog?) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedEntry by remember { mutableStateOf<OpdsEntry?>(null) }
    var showCatalogDialog by remember { mutableStateOf(false) }
    var editingCatalog by remember { mutableStateOf<OpdsCatalog?>(null) }
    var catalogToDelete by remember { mutableStateOf<OpdsCatalog?>(null) }

    Box(modifier.fillMaxSize()) {
        if (!state.isViewingCatalog) {
            SharedOpdsCatalogList(
                catalogs = state.catalogs,
                onOpenCatalog = onOpenCatalog,
                onEditCatalog = { catalog ->
                    editingCatalog = catalog
                    showCatalogDialog = true
                },
                onDeleteCatalog = { catalogToDelete = it },
                onAddCatalog = {
                    editingCatalog = null
                    showCatalogDialog = true
                }
            )
        } else {
            SharedOpdsFeedView(
                state = state,
                localLibraryBooks = localLibraryBooks,
                onNavigateBack = onNavigateBack,
                onSearch = onSearch,
                onOpenFeedUrl = onOpenFeedUrl,
                onLoadNextPage = onLoadNextPage,
                onDownloadBook = onDownloadBook,
                onReadBook = onReadBook,
                onStreamBook = { entry -> onStreamBook(entry, state.currentCatalog) },
                onEntrySelected = { selectedEntry = it }
            )
        }

        state.errorMessage?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearError) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }

    if (showCatalogDialog) {
        SharedOpdsCatalogDialog(
            catalog = editingCatalog,
            onDismiss = {
                showCatalogDialog = false
                editingCatalog = null
            },
            onSave = { title, url, username, password ->
                val editing = editingCatalog
                if (editing == null) {
                    onAddCatalog(title, url, username, password)
                } else {
                    onUpdateCatalog(editing.id, title, url, username, password)
                }
                showCatalogDialog = false
                editingCatalog = null
            }
        )
    }

    catalogToDelete?.let { catalog ->
        AlertDialog(
            onDismissRequest = { catalogToDelete = null },
            title = { Text("Delete catalog") },
            text = { Text("Delete \"${catalog.title}\"? Streamed books from this catalog may stop opening if credentials change later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveCatalog(catalog)
                        catalogToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { catalogToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedEntry?.let { entry ->
        SharedOpdsEntryDetailsDialog(
            entry = entry,
            localLibraryBook = entry.findLocalBook(localLibraryBooks),
            downloadState = state.downloadingState[entry.id],
            onDismiss = { selectedEntry = null },
            onDownloadBook = { acquisition -> onDownloadBook(entry, acquisition) },
            onReadBook = onReadBook,
            onStreamBook = {
                onStreamBook(entry, state.currentCatalog)
                selectedEntry = null
            },
            onOpenFeedUrl = { url ->
                onOpenFeedUrl(url)
                selectedEntry = null
            },
            onSearch = { query ->
                onSearch(query)
                selectedEntry = null
            }
        )
    }
}

@Composable
private fun SharedOpdsCatalogList(
    catalogs: List<OpdsCatalog>,
    onOpenCatalog: (OpdsCatalog) -> Unit,
    onEditCatalog: (OpdsCatalog) -> Unit,
    onDeleteCatalog: (OpdsCatalog) -> Unit,
    onAddCatalog: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        SharedScreenScaffold(
            title = "OPDS",
            subtitle = "Browse catalogs, streams, and downloads",
            trailing = {
                Button(onClick = onAddCatalog) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Catalog")
                }
            }
        ) {
            if (catalogs.isEmpty()) {
                SharedOpdsEmptyState(onAddCatalog = onAddCatalog, modifier = Modifier.weight(1f))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(320.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(catalogs, key = { it.id }) { catalog ->
                        SharedOpdsCatalogCard(
                            catalog = catalog,
                            onOpenCatalog = { onOpenCatalog(catalog) },
                            onEditCatalog = { onEditCatalog(catalog) },
                            onDeleteCatalog = { onDeleteCatalog(catalog) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedOpdsFeedView(
    state: SharedOpdsScreenState,
    localLibraryBooks: List<BookItem>,
    onNavigateBack: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenFeedUrl: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    onDownloadBook: (OpdsEntry, OpdsAcquisition) -> Unit,
    onReadBook: (BookItem) -> Unit,
    onStreamBook: (OpdsEntry) -> Unit,
    onEntrySelected: (OpdsEntry) -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (showSearch) {
                            showSearch = false
                            query = ""
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    if (showSearch) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search catalog") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (query.isNotBlank()) {
                                        onSearch(query)
                                        query = ""
                                        showSearch = false
                                    }
                                }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                }
                            }
                        )
                    } else {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = state.currentFeed?.title ?: "Loading",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            state.currentCatalog?.title?.let { catalogTitle ->
                                Text(
                                    catalogTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (state.searchUrlTemplate != null) {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                    }
                }
                if (state.isLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }

        val facets = state.currentFeed?.facets.orEmpty()
        if (facets.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                facets.groupBy { it.group }.forEach { (groupName, groupFacets) ->
                    item(key = groupName) {
                        SharedOpdsFacetMenu(
                            groupName = groupName,
                            facets = groupFacets,
                            onOpenFeedUrl = onOpenFeedUrl
                        )
                    }
                }
            }
        }

        val entries = state.currentFeed?.entries.orEmpty()
        if (entries.isEmpty() && !state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This feed is empty.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(entries, key = { index, entry -> "${entry.id}_$index" }) { index, entry ->
                    val nextUrl = state.currentFeed?.nextUrl
                    if (index == entries.lastIndex && nextUrl != null) {
                        LaunchedEffect(index, nextUrl) {
                            onLoadNextPage()
                        }
                    }
                    if (entry.isNavigation) {
                        SharedOpdsNavigationCard(entry, onOpenFeedUrl)
                    } else {
                        SharedOpdsBookCard(
                            entry = entry,
                            localLibraryBook = entry.findLocalBook(localLibraryBooks),
                            downloadState = state.downloadingState[entry.id],
                            onDownloadBook = { acquisition -> onDownloadBook(entry, acquisition) },
                            onReadBook = onReadBook,
                            onStreamBook = { onStreamBook(entry) },
                            onClick = { onEntrySelected(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedOpdsCatalogCard(
    catalog: OpdsCatalog,
    onOpenCatalog: () -> Unit,
    onEditCatalog: () -> Unit,
    onDeleteCatalog: () -> Unit
) {
    Surface(
        onClick = onOpenCatalog,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(catalog.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        catalog.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (catalog.isDefault) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "Preset",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!catalog.isDefault) {
                    IconButton(onClick = onEditCatalog) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDeleteCatalog) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedOpdsEmptyState(onAddCatalog: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text("No catalogs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Add an OPDS catalog to browse remote books.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onAddCatalog) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add catalog")
                }
            }
        }
    }
}

@Composable
private fun SharedOpdsFacetMenu(
    groupName: String,
    facets: List<com.aryan.reader.shared.opds.OpdsFacet>,
    onOpenFeedUrl: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val activeFacet = facets.firstOrNull { it.isActive } ?: facets.firstOrNull()
    Box {
        FilterChip(
            selected = activeFacet?.isActive == true,
            onClick = { expanded = true },
            label = { Text("$groupName: ${activeFacet?.title ?: "Select"}") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            facets.forEach { facet ->
                DropdownMenuItem(
                    text = { Text(facet.title) },
                    onClick = {
                        expanded = false
                        onOpenFeedUrl(facet.url)
                    },
                    trailingIcon = if (facet.isActive) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun SharedOpdsNavigationCard(entry: OpdsEntry, onOpenFeedUrl: (String) -> Unit) {
    Surface(
        onClick = { entry.navigationUrl?.let(onOpenFeedUrl) },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val summary = SharedOpdsText.cleanSummary(entry.summary)
                if (summary.isNotBlank()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedOpdsBookCard(
    entry: OpdsEntry,
    localLibraryBook: BookItem?,
    downloadState: SharedOpdsDownloadState?,
    onDownloadBook: (OpdsAcquisition) -> Unit,
    onReadBook: (BookItem) -> Unit,
    onStreamBook: () -> Unit,
    onClick: () -> Unit
) {
    val uniqueAcquisitions = remember(entry.acquisitions) {
        entry.acquisitions.distinctBy { it.formatName }.sortedByDescending { it.priority }
    }
    val isDownloading = downloadState?.isDownloading == true
    var showFormatMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(width = 70.dp, height = 100.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(entry.title.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium)
            }
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                entry.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                val summary = SharedOpdsText.cleanSummary(entry.summary)
                if (summary.isNotBlank()) {
                    Text(summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                when {
                    localLibraryBook != null -> {
                        OutlinedButton(onClick = { onReadBook(localLibraryBook) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Read")
                        }
                    }
                    isDownloading -> SharedOpdsDownloadProgress(downloadState)
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (entry.isStreamable) {
                            FilledTonalButton(onClick = onStreamBook, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stream")
                            }
                        }
                        Box {
                            FilledTonalButton(
                                onClick = {
                                    when (uniqueAcquisitions.size) {
                                        0 -> Unit
                                        1 -> onDownloadBook(uniqueAcquisitions.first())
                                        else -> showFormatMenu = true
                                    }
                                },
                                enabled = uniqueAcquisitions.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    if (uniqueAcquisitions.isEmpty()) Icons.Default.Info else Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (uniqueAcquisitions.isEmpty()) "Unavailable" else "Download")
                            }
                            DropdownMenu(expanded = showFormatMenu, onDismissRequest = { showFormatMenu = false }) {
                                uniqueAcquisitions.forEach { acquisition ->
                                    DropdownMenuItem(
                                        text = { Text(acquisition.formatName) },
                                        onClick = {
                                            showFormatMenu = false
                                            onDownloadBook(acquisition)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedOpdsDownloadProgress(downloadState: SharedOpdsDownloadState?) {
    val progress = downloadState?.progress
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Downloading", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            if (progress != null) {
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(4.dp))
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SharedOpdsEntryDetailsDialog(
    entry: OpdsEntry,
    localLibraryBook: BookItem?,
    downloadState: SharedOpdsDownloadState?,
    onDismiss: () -> Unit,
    onDownloadBook: (OpdsAcquisition) -> Unit,
    onReadBook: (BookItem) -> Unit,
    onStreamBook: () -> Unit,
    onOpenFeedUrl: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    val uniqueAcquisitions = remember(entry.acquisitions) {
        entry.acquisitions.distinctBy { it.formatName }.sortedByDescending { it.priority }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                entry.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                localLibraryBook?.let { book ->
                    Button(onClick = { onReadBook(book) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Read")
                    }
                }
                if (downloadState?.isDownloading == true) {
                    SharedOpdsDownloadProgress(downloadState)
                } else {
                    if (entry.isStreamable) {
                        Button(onClick = onStreamBook, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Cloud, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stream now")
                        }
                    }
                    if (uniqueAcquisitions.isNotEmpty()) {
                        Text("Download format", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            uniqueAcquisitions.take(4).forEach { acquisition ->
                                FilledTonalButton(onClick = { onDownloadBook(acquisition) }) {
                                    Text(acquisition.formatName)
                                }
                            }
                        }
                    }
                }
                entry.series?.takeIf { it.isNotBlank() }?.let { series ->
                    Text(
                        text = if (entry.seriesIndex.isNullOrBlank()) series else "$series #${entry.seriesIndex}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (entry.authors.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Authors", style = MaterialTheme.typography.labelLarge)
                        entry.authors.forEach { author ->
                            TextButton(
                                onClick = {
                                    if (author.url != null) onOpenFeedUrl(author.url) else onSearch(author.name)
                                }
                            ) {
                                Text(author.name)
                            }
                        }
                    }
                }
                if (entry.categories.isNotEmpty()) {
                    Text("Categories", style = MaterialTheme.typography.labelLarge)
                    entry.categories.distinct().take(8).forEach { category ->
                        TextButton(onClick = { onSearch(category) }) {
                            Text(category)
                        }
                    }
                }
                val secondary = listOfNotNull(
                    entry.publisher?.takeIf { it.isNotBlank() }?.let { "Publisher: $it" },
                    entry.published?.takeIf { it.isNotBlank() }?.substringBefore("T")?.let { "Published: $it" },
                    entry.language?.takeIf { it.isNotBlank() }?.uppercase()?.let { "Language: $it" }
                )
                secondary.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val summary = SharedOpdsText.cleanSummary(entry.summary)
                if (summary.isNotBlank()) {
                    Text("Synopsis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SharedOpdsCatalogDialog(
    catalog: OpdsCatalog?,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String?) -> Unit
) {
    var title by remember(catalog) { mutableStateOf(catalog?.title.orEmpty()) }
    var url by remember(catalog) { mutableStateOf(catalog?.url.orEmpty()) }
    var username by remember(catalog) { mutableStateOf(catalog?.username.orEmpty()) }
    var password by remember(catalog) { mutableStateOf(catalog?.password.orEmpty()) }
    val isEditMode = catalog != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "Edit catalog" else "Add OPDS catalog") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Catalog name") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, singleLine = true)
                Text("Authentication optional", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, url, username, password) },
                enabled = title.isNotBlank() && url.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun OpdsEntry.findLocalBook(localLibraryBooks: List<BookItem>): BookItem? {
    return localLibraryBooks.firstOrNull {
        it.title.equals(title, ignoreCase = true) || it.displayName.equals(title, ignoreCase = true)
    }
}
