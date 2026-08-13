package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.IN_APP_STORAGE_SOURCE
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.SortOrder
import com.aryan.reader.shared.SyncedFolder
import com.aryan.reader.shared.Tag
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.cardAuthor
import com.aryan.reader.shared.cardTitle
import com.aryan.reader.shared.canAddSyncedFolder



@Composable
fun SharedMobileLibrarySortControl(
    sortOrder: SortOrder,
    labels: Map<SortOrder, String>,
    selectedContentDescription: String,
    onSortOrderChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { showSortMenu = true },
            modifier = modifier,
        ) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(labels[sortOrder].orEmpty())
        }
        DropdownMenu(
            expanded = showSortMenu,
            onDismissRequest = { showSortMenu = false },
        ) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(labels[order].orEmpty()) },
                    onClick = {
                        onSortOrderChange(order)
                        showSortMenu = false
                    },
                    trailingIcon = {
                        if (order == sortOrder) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = selectedContentDescription,
                            )
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SharedMobileLibraryBookListCardFrame(
    isAvailable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cover: @Composable BoxScope.() -> Unit,
    header: @Composable RowScope.() -> Unit,
    metadata: @Composable RowScope.() -> Unit,
    progress: @Composable () -> Unit,
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isSelected) 6.dp else 2.dp,
        ),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isAvailable) 1f else 0.8f }
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                } else {
                    Modifier
                },
            )
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .height(132.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.7f)
                    .clip(MaterialTheme.shapes.medium)
                    .border(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        MaterialTheme.shapes.medium,
                    ),
                content = cover,
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    content = header,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = metadata,
                )
                Spacer(Modifier.weight(1f))
                progress()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SharedMobileShelfListCardFrame(
    isSelected: Boolean,
    contentStartIndent: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp,
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                } else {
                    Modifier
                },
            )
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(
                start = 12.dp + contentStartIndent,
                end = 12.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun SharedMobileTopBanner(
    text: String,
    visible: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = if (isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 8.dp,
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun SharedMobileTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationIcon()
            Box(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

data class SharedMobileContextualActionLabels(
    val selectedCount: String,
    val clearSelection: String,
    val info: String,
    val pin: String,
    val selectAll: String,
    val delete: String,
    val moreOptions: String,
    val tag: String,
    val addToShelf: String,
    val save: String,
    val share: String,
    val exportAnnotations: String,
    val clear: String,
)

@Composable
fun SharedMobileContextualActionBar(
    selectedItemCount: Int,
    labels: SharedMobileContextualActionLabels,
    onNavigateBack: () -> Unit,
    onDelete: () -> Unit,
    compact: Boolean,
    onInfo: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onExportAnnotations: (() -> Unit)? = null,
    onTag: (() -> Unit)? = null,
    onAddToShelf: (() -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    tagIcon: @Composable (String?) -> Unit,
) {
    SharedMobileTopAppBar(
        title = {
            Text(labels.selectedCount, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = labels.clearSelection)
            }
        },
        actions = {
            if (compact) {
                SharedMobileCompactSelectionActions(
                    selectedItemCount = selectedItemCount,
                    labels = labels,
                    onInfo = onInfo,
                    onPin = onPin,
                    onSelectAll = onSelectAll,
                    onTag = onTag,
                    onAddToShelf = onAddToShelf,
                    onSave = onSave,
                    onShare = onShare,
                    onExportAnnotations = onExportAnnotations,
                    onClear = onClear ?: onNavigateBack,
                    onDelete = onDelete,
                    tagIcon = tagIcon,
                )
            } else {
                onTag?.let { action ->
                    IconButton(onClick = action) { tagIcon(labels.tag) }
                }
                onPin?.let { action ->
                    IconButton(onClick = action) {
                        Icon(Icons.Default.PushPin, contentDescription = labels.pin)
                    }
                }
                if (selectedItemCount == 1) {
                    onInfo?.let { action ->
                        IconButton(onClick = action) {
                            Icon(Icons.Default.Info, contentDescription = labels.info)
                        }
                    }
                    onSave?.let { action ->
                        IconButton(onClick = action) {
                            Icon(Icons.Default.Save, contentDescription = labels.save)
                        }
                    }
                    onShare?.let { action ->
                        IconButton(onClick = action) {
                            Icon(Icons.Default.Share, contentDescription = labels.share)
                        }
                    }
                }
                onSelectAll?.let { action ->
                    IconButton(onClick = action) {
                        Icon(Icons.Default.SelectAll, contentDescription = labels.selectAll)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = labels.delete)
                }
            }
        },
    )
}

@Composable
internal fun SharedMobileCompactSelectionActions(
    selectedItemCount: Int,
    labels: SharedMobileContextualActionLabels,
    onInfo: (() -> Unit)?,
    onPin: (() -> Unit)?,
    onSelectAll: (() -> Unit)?,
    onTag: (() -> Unit)?,
    onAddToShelf: (() -> Unit)?,
    onSave: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onExportAnnotations: (() -> Unit)?,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    tagIcon: @Composable (String?) -> Unit,
) {
    var showMore by remember { mutableStateOf(false) }
    if (selectedItemCount == 1) {
        onInfo?.let { action ->
            IconButton(onClick = action) { Icon(Icons.Default.Info, contentDescription = labels.info) }
        }
    }
    onPin?.let { action ->
        IconButton(onClick = action) { Icon(Icons.Default.PushPin, contentDescription = labels.pin) }
    }
    onSelectAll?.let { action ->
        IconButton(onClick = action) { Icon(Icons.Default.SelectAll, contentDescription = labels.selectAll) }
    }
    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = labels.delete) }
    Box {
        IconButton(onClick = { showMore = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = labels.moreOptions)
        }
        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
            onTag?.let { action ->
                DropdownMenuItem(
                    text = { Text(labels.tag) },
                    leadingIcon = { tagIcon(null) },
                    onClick = { showMore = false; action() },
                )
            }
            onAddToShelf?.let { action ->
                DropdownMenuItem(
                    text = { Text(labels.addToShelf) },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    onClick = { showMore = false; action() },
                )
            }
            if (selectedItemCount == 1) {
                onSave?.let { action ->
                    DropdownMenuItem(
                        text = { Text(labels.save) },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                        onClick = { showMore = false; action() },
                    )
                }
                onShare?.let { action ->
                    DropdownMenuItem(
                        text = { Text(labels.share) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = { showMore = false; action() },
                    )
                }
                onExportAnnotations?.let { action ->
                    DropdownMenuItem(
                        text = { Text(labels.exportAnnotations) },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                        onClick = { showMore = false; action() },
                    )
                }
            }
            DropdownMenuItem(
                text = { Text(labels.clear) },
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                onClick = { showMore = false; onClear() },
            )
        }
    }
}

@Composable
fun SharedMobileLibrarySearchTopBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    showClear: Boolean,
    onClose: () -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    closeContentDescription: String,
    clearContentDescription: String,
    focusRequester: FocusRequester,
    textFieldModifier: Modifier = Modifier,
) {
    Surface(shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = closeContentDescription)
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder) },
                modifier = textFieldModifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                trailingIcon = {
                    if (showClear) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Close, contentDescription = clearContentDescription)
                        }
                    }
                },
            )
        }
    }
}

data class SharedMobileLibraryFilterLabels(
    val title: String,
    val fileType: String,
    val sourceFolder: String,
    val inAppStorage: String,
    val readStatus: String,
    val tags: String,
    val clearAll: String,
    val apply: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileLibraryFilterDialog(
    filters: LibraryFilters,
    allTags: List<Tag>,
    syncedFolders: List<SyncedFolder>,
    readableFileTypes: Set<FileType>,
    fileTypeLabels: Map<FileType, String>,
    readStatusLabels: Map<ReadStatusFilter, String>,
    labels: SharedMobileLibraryFilterLabels,
    onApply: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentFilters by remember { mutableStateOf(filters) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(labels.title, style = MaterialTheme.typography.titleLarge)
            Text(labels.fileType, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                readableFileTypes.forEach { type ->
                    FilterChip(
                        selected = type in currentFilters.fileTypes,
                        onClick = {
                            currentFilters = currentFilters.copy(
                                fileTypes = currentFilters.fileTypes.toggleMember(type),
                            )
                        },
                        label = { Text(fileTypeLabels[type].orEmpty()) },
                    )
                }
            }
            Text(labels.sourceFolder, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = IN_APP_STORAGE_SOURCE in currentFilters.sourceFolders,
                    onClick = {
                        currentFilters = currentFilters.copy(
                            sourceFolders = currentFilters.sourceFolders.toggleMember(IN_APP_STORAGE_SOURCE),
                        )
                    },
                    label = { Text(labels.inAppStorage) },
                )
                syncedFolders.forEach { folder ->
                    FilterChip(
                        selected = folder.uriString in currentFilters.sourceFolders,
                        onClick = {
                            currentFilters = currentFilters.copy(
                                sourceFolders = currentFilters.sourceFolders.toggleMember(folder.uriString),
                            )
                        },
                        label = { Text(folder.name) },
                    )
                }
            }
            Text(labels.readStatus, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReadStatusFilter.entries.forEach { status ->
                    FilterChip(
                        selected = currentFilters.readStatus == status,
                        onClick = { currentFilters = currentFilters.copy(readStatus = status) },
                        label = { Text(readStatusLabels[status].orEmpty()) },
                    )
                }
            }
            if (allTags.isNotEmpty()) {
                Text(labels.tags, style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    allTags.forEach { tag ->
                        val selected = tag.id in currentFilters.tagIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                currentFilters = currentFilters.copy(
                                    tagIds = currentFilters.tagIds.toggleMember(tag.id),
                                )
                            },
                            label = { Text(tag.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier.size(10.dp).background(
                                        Color(tag.color ?: 0xFF64B5F6.toInt()),
                                        CircleShape,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { currentFilters = LibraryFilters() }) { Text(labels.clearAll) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onApply(currentFilters); onDismiss() }) { Text(labels.apply) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedMobileSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    CenterAlignedTopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(readerString("action_search", "Search")) },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = readerString("content_desc_clear_query", "Clear search"),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = readerString("content_desc_close_search", "Close search"),
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedMobileContextualTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: (() -> Unit)?,
    onPin: (() -> Unit)?,
    onAddToShelf: (() -> Unit)? = null,
    onTag: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onExportAnnotations: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    CenterAlignedTopAppBar(
        title = { Text(readerString("selected_count", "%1\$d selected", selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Clear selection")
            }
        },
        actions = {
            if (onInfo != null && selectedCount == 1) {
                IconButton(onClick = onInfo) {
                    Icon(Icons.Default.Info, contentDescription = "Book info")
                }
            }
            if (onPin != null) {
                IconButton(onClick = onPin) {
                    Icon(Icons.Default.PushPin, contentDescription = "Pin")
                }
            }
            if (onSelectAll != null) {
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove selected")
                }
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    onTag?.let { tag ->
                        DropdownMenuItem(
                            text = { Text(readerString("section_tags", "Tags")) },
                            onClick = { showMoreMenu = false; tag() }
                        )
                    }
                    onAddToShelf?.let { addToShelf ->
                        DropdownMenuItem(
                            text = { Text(readerString("desktop_add_to_shelf", "Add to shelf")) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            onClick = { showMoreMenu = false; addToShelf() }
                        )
                    }
                    onRename?.let { rename ->
                        DropdownMenuItem(
                            text = { Text(readerString("action_rename", "Rename")) },
                            onClick = { showMoreMenu = false; rename() }
                        )
                    }
                    if (selectedCount == 1) {
                        onSave?.let { save ->
                            DropdownMenuItem(
                                text = { Text(readerString("action_save_copy_to_device", "Save copy to device")) },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                onClick = { showMoreMenu = false; save() }
                            )
                        }
                        onShare?.let { share ->
                            DropdownMenuItem(
                                text = { Text(readerString("action_share_original_file", "Share original file")) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = { showMoreMenu = false; share() }
                            )
                        }
                        onExportAnnotations?.let { export ->
                            DropdownMenuItem(
                                text = { Text(readerString("action_export_annotations", "Export annotations")) },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                onClick = { showMoreMenu = false; export() }
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text(readerString("action_clear", "Clear")) },
                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                        onClick = { showMoreMenu = false; onClose() }
                    )
                }
            }
        }
    )
}

@Composable
fun SharedMobileLibraryFilterChips(
    filters: LibraryFilters,
    fileTypesLabel: String,
    foldersLabel: String,
    statusLabel: String,
    tagsLabel: String,
    clearContentDescription: String,
    onRemoveFilters: (LibraryFilters) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filters.fileTypes.isNotEmpty()) {
            AssistChip(
                onClick = { onRemoveFilters(filters.copy(fileTypes = emptySet())) },
                label = { Text(fileTypesLabel) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = clearContentDescription, modifier = Modifier.size(16.dp)) },
            )
        }
        if (filters.sourceFolders.isNotEmpty()) {
            AssistChip(
                onClick = { onRemoveFilters(filters.copy(sourceFolders = emptySet())) },
                label = { Text(foldersLabel) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = clearContentDescription, modifier = Modifier.size(16.dp)) },
            )
        }
        if (filters.readStatus != ReadStatusFilter.ALL) {
            AssistChip(
                onClick = { onRemoveFilters(filters.copy(readStatus = ReadStatusFilter.ALL)) },
                label = { Text(statusLabel) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = clearContentDescription, modifier = Modifier.size(16.dp)) },
            )
        }
        if (filters.tagIds.isNotEmpty()) {
            AssistChip(
                onClick = { onRemoveFilters(filters.copy(tagIds = emptySet())) },
                label = { Text(tagsLabel) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = clearContentDescription, modifier = Modifier.size(16.dp)) },
            )
        }
    }
}

@Composable
internal fun SharedMobileActiveTabs(
    openTabs: List<BookItem>,
    onOpenTab: (BookItem) -> Unit,
    onCloseTab: (BookItem) -> Unit,
    onCloseAllTabs: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Active tabs", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onCloseAllTabs) {
                Icon(Icons.Default.Close, contentDescription = "Close all tabs", tint = MaterialTheme.colorScheme.error)
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(openTabs, key = { "tab_${it.id}" }) { tab ->
                InputChip(
                    selected = false,
                    onClick = { onOpenTab(tab) },
                    label = {
                        Text(
                            text = tab.cardTitle(LocalUsePdfFileNameAsDisplayName.current),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 150.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onCloseTab(tab) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close tab", modifier = Modifier.size(16.dp))
                        }
                    }
                )
            }
        }
    }
}

@Composable
internal fun SharedMobileBookGridSection(
    title: String,
    books: List<BookItem>,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onShowBookInfo: (BookItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.height((((books.size + 2) / 3).coerceAtLeast(1) * 244).dp)
        ) {
            items(books, key = { it.id }) { book ->
                SharedMobileBookCard(
                    book = book,
                    selected = book.id in selectedBookIds,
                    pinned = book.id in pinnedBookIds,
                    downloading = book.id in downloadingBookIds,
                    onClick = { onOpenBook(book) },
                    onLongClick = {
                        if (shouldSelectBookOnLongPress(book.id, selectedBookIds)) {
                            onLongPressBook(book)
                        }
                    },
                    onTogglePinned = { onTogglePinned(book) },
                    onShowBookInfo = { onShowBookInfo(book) }
                )
            }
        }
    }
}

@Composable
internal fun SharedMobileBookList(
    books: List<BookItem>,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    downloadingBookIds: Set<String> = emptySet(),
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onShowBookInfo: (BookItem) -> Unit = {},
    empty: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    if (books.isEmpty()) {
        empty()
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(books, key = { it.id }) { book ->
            SharedMobileLibraryListItem(
                book = book,
                selected = book.id in selectedBookIds,
                pinned = book.id in pinnedBookIds,
                downloading = book.id in downloadingBookIds,
                onClick = { onOpenBook(book) },
                onLongClick = {
                    if (shouldSelectBookOnLongPress(book.id, selectedBookIds)) {
                        onLongPressBook(book)
                    }
                },
                onTogglePinned = { onTogglePinned(book) },
                onShowBookInfo = { onShowBookInfo(book) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SharedMobileBookCard(
    book: BookItem,
    selected: Boolean,
    pinned: Boolean,
    downloading: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onShowBookInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .graphicsLayer { alpha = if (book.isAvailable) 1f else 0.8f }
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large) else Modifier)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (selected) 6.dp else 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box {
                SharedMobileBookCover(
                    book = book,
                    selected = selected,
                    pinned = pinned,
                    onTogglePinned = onTogglePinned,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.74f)
                )
                IconButton(
                    onClick = onShowBookInfo,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.48f)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Book details",
                            tint = Color.White,
                            modifier = Modifier.padding(6.dp).size(18.dp)
                        )
                    }
                }
                if (downloading) {
                    Surface(
                        modifier = Modifier.matchParentSize(),
                        color = Color.Black.copy(alpha = 0.38f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = book.cardTitle(LocalUsePdfFileNameAsDisplayName.current),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = book.cardAuthor().ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SharedMobileLibraryListItem(
    book: BookItem,
    selected: Boolean,
    pinned: Boolean,
    downloading: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onShowBookInfo: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (book.isAvailable) 1f else 0.8f }
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large) else Modifier)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (selected) 6.dp else 2.dp),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .height(132.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            SharedMobileBookCover(
                book = book,
                selected = selected,
                pinned = pinned,
                onTogglePinned = onTogglePinned,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.7f),
                compact = true
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        book.cardTitle(LocalUsePdfFileNameAsDisplayName.current),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        mobileBookStatusBadges(book, pinned).forEach { badge ->
                            SharedMobileCoverStatusBadge(
                                badge = badge,
                                onClick = onTogglePinned.takeIf { badge == SharedMobileBookStatusBadge.PINNED },
                                overlay = false,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    book.cardAuthor(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        book.type.name,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                book.progressPercentage?.takeIf { it > 0f }?.coerceIn(0f, 100f)?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${progress.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
            if (downloading) {
                Surface(
                    modifier = Modifier.matchParentSize(),
                    color = Color.Black.copy(alpha = 0.32f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SharedMobileBookCover(
    book: BookItem,
    selected: Boolean,
    pinned: Boolean,
    onTogglePinned: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val color = sharedMobileFileTypeColor(book.type)
    val statusBadges = mobileBookStatusBadges(book, pinned)
    val progressPercent = book.progressPercentage
        ?.takeIf { it > 0f }
        ?.coerceIn(0f, 100f)
        ?.toInt()
    Surface(
        modifier = modifier,
        color = color,
        contentColor = Color.White,
        shape = RoundedCornerShape(if (compact) 8.dp else 12.dp),
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!book.coverImagePath.isNullOrBlank()) {
                LocalBookCoverImage(
                    path = book.coverImagePath,
                    contentDescription = book.cardTitle(LocalUsePdfFileNameAsDisplayName.current),
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.15f),
                                0.3f to Color.Transparent,
                                0.6f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.5f),
                            )
                        ),
                )
            } else {
                Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(if (compact) 24.dp else 38.dp))
            }
            if (!compact) {
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    statusBadges.forEach { badge ->
                        SharedMobileCoverStatusBadge(
                            badge = badge,
                            onClick = onTogglePinned.takeIf { badge == SharedMobileBookStatusBadge.PINNED },
                            overlay = true,
                        )
                    }
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        modifier = Modifier
                            .size(if (compact) 32.dp else 48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(8.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            if (!book.isAvailable) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Not available locally",
                        modifier = Modifier.size(if (compact) 32.dp else 48.dp),
                        tint = Color.White,
                    )
                }
            }
            if (!compact) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    progressPercent?.let { percent ->
                        SharedMobileCoverTextBadge(
                            text = "$percent%",
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    SharedMobileCoverTextBadge(
                        text = book.type.name,
                        containerColor = Color.Black.copy(alpha = 0.62f),
                        contentColor = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SharedMobileCoverTextBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
internal fun SharedMobileCoverStatusBadge(
    badge: SharedMobileBookStatusBadge,
    onClick: (() -> Unit)?,
    overlay: Boolean,
) {
    val icon = when (badge) {
        SharedMobileBookStatusBadge.PINNED -> Icons.Default.PushPin
        SharedMobileBookStatusBadge.FOLDER -> Icons.Default.Folder
        SharedMobileBookStatusBadge.CATALOG -> Icons.Default.Cloud
    }
    val label = when (badge) {
        SharedMobileBookStatusBadge.PINNED -> readerString("pinned", "Pinned")
        SharedMobileBookStatusBadge.FOLDER -> readerString("desktop_book_badge_folder", "Folder")
        SharedMobileBookStatusBadge.CATALOG -> readerString("action_stream", "Stream")
    }
    Surface(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (overlay) {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (overlay) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = if (overlay) 0.dp else 2.dp,
        shadowElevation = if (overlay) 0.dp else 1.dp,
        border = if (overlay) BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)) else null,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.padding(6.dp).size(14.dp),
        )
    }
}

@Composable
internal fun SharedMobileFolderSyncScreen(
    folders: List<SyncedFolder>,
    books: List<BookItem>,
    isLoading: Boolean,
    onAddFolder: () -> Unit,
    onScanAll: () -> Unit,
    onSyncMetadata: () -> Unit,
    onLocalSyncChange: (SyncedFolder, Boolean) -> Unit,
    onFileTypesChange: (SyncedFolder, Set<FileType>) -> Unit,
    onRemoveFolder: (SyncedFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingFolder by remember { mutableStateOf<SyncedFolder?>(null) }
    var disablingFolder by remember { mutableStateOf<SyncedFolder?>(null) }
    if (folders.isEmpty()) {
        SharedMobileEmptyLibrary(
            title = readerString("sync_local_folders", "Sync Local Folders"),
            message = readerString(
                "sync_folders_desc",
                "Connect local folders to create a live library. Reader will monitor supported files.",
            ),
            actionLabel = readerString("action_select_folder", "Select Folder"),
            onAction = onAddFolder,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                FilledTonalButton(
                    onClick = onScanAll,
                    enabled = !isLoading && folders.any { it.localSyncEnabled },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isLoading) readerString("scanning", "Scanning…")
                        else readerString("scan_all", "Scan All")
                    )
                }
                OutlinedButton(
                    onClick = onSyncMetadata,
                    enabled = !isLoading && folders.any { it.localSyncEnabled },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(readerString("sync_meta", "Sync Meta"))
                }
            }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(folders, key = { it.uriString }) { folder ->
                        val folderBooks = books.filter {
                            it.sourceFolder == folder.name || it.sourceFolder == folder.uriString
                        }
                        SharedMobileFolderCard(
                            folder = folder,
                            books = folderBooks,
                            onEditFilters = { editingFolder = folder },
                            onLocalSyncChange = { enabled ->
                                if (enabled) onLocalSyncChange(folder, true) else disablingFolder = folder
                            },
                            onRemove = { onRemoveFolder(folder) },
                        )
                    }
                }
            }
            if (canAddSyncedFolder(folders)) {
                ExtendedFloatingActionButton(
                    text = { Text(readerString("fab_add_folder", "Add Folder")) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = onAddFolder,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }

    editingFolder?.let { folder ->
        SharedMobileFolderFiltersDialog(
            folder = folder,
            onDismiss = { editingFolder = null },
            onSave = {
                onFileTypesChange(folder, it)
                editingFolder = null
            },
        )
    }
    disablingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { disablingFolder = null },
            title = {
                Text(readerString("dialog_disable_folder_local_sync_title", "Disable local sync?"))
            },
            text = {
                Text(
                    readerString(
                        "dialog_disable_folder_local_sync_ios_desc",
                        "Reader will stop refreshing \"%1\$s\". Existing managed copies remain available for reading.",
                        folder.name,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLocalSyncChange(folder, false)
                        disablingFolder = null
                    }
                ) {
                    Text(readerString("action_disable_keep_sync_data", "Disable and keep data"))
                }
            },
            dismissButton = {
                TextButton(onClick = { disablingFolder = null }) {
                    Text(readerString("action_cancel", "Cancel"))
                }
            },
        )
    }
}

@Composable
internal fun SharedMobileFolderCard(
    folder: SyncedFolder,
    books: List<BookItem>,
    onEditFilters: () -> Unit,
    onLocalSyncChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FolderSpecial,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        folder.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!folder.localSyncEnabled) {
                        Text(
                            readerString("folder_local_sync_disabled", "Local sync disabled"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Folder options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(readerString("menu_edit_filters", "Edit Filters")) },
                            onClick = { showMenu = false; onEditFilters() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (folder.localSyncEnabled) {
                                        readerString("menu_disable_folder_local_sync", "Disable local sync")
                                    } else {
                                        readerString("menu_enable_folder_local_sync", "Enable local sync")
                                    },
                                )
                            },
                            onClick = {
                                showMenu = false
                                onLocalSyncChange(!folder.localSyncEnabled)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(readerString("menu_remove_folder", "Remove Folder")) },
                            onClick = { showMenu = false; onRemove() },
                            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        readerString("last_sync", "LAST SYNC"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (folder.lastScanTime == 0L) {
                            readerString("never", "Never")
                        } else {
                            formatSharedMobileDateTime(folder.lastScanTime)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        readerString("books_count", "BOOKS"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(books.size.toString(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val counts = books.groupingBy { it.type }.eachCount()
            if (counts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(counts.entries.toList(), key = { it.key.name }) { (type, count) ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text("${type.name}: $count") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SharedMobileFolderFiltersDialog(
    folder: SyncedFolder,
    onDismiss: () -> Unit,
    onSave: (Set<FileType>) -> Unit,
) {
    val availableTypes = remember {
        SharedFileCapabilities.all
            .filter { it.syncEligible && it.isReadableOnIos }
            .map { it.type }
    }
    var selectedTypes by remember(folder.uriString, folder.allowedFileTypes) {
        mutableStateOf(folder.allowedFileTypes.intersect(availableTypes.toSet()))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(readerString("filter_file_types", "Filter File Types")) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableTypes, key = { it.name }) { type ->
                    FilterChip(
                        selected = type in selectedTypes,
                        onClick = {
                            selectedTypes = if (type in selectedTypes) selectedTypes - type else selectedTypes + type
                        },
                        label = { Text(type.name) },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedTypes) },
                enabled = selectedTypes.isNotEmpty(),
            ) { Text(readerString("action_save", "Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(readerString("action_cancel", "Cancel")) }
        },
    )
}

@Composable
internal fun SharedMobileShelfList(
    shelves: List<Shelf>,
    onOpenShelf: (Shelf) -> Unit,
    onLongPressShelf: (Shelf) -> Unit,
    selectedShelfIds: Set<String> = emptySet(),
    emptyTitle: String = "No shelves yet",
    emptyMessage: String = "Create shelves to organize your library.",
    modifier: Modifier = Modifier
) {
    if (shelves.isEmpty()) {
        SharedMobileEmptyLibrary(
            title = emptyTitle,
            message = emptyMessage,
            actionLabel = null,
            onAction = {},
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(shelves, key = { it.id }) { shelf ->
            SharedMobileShelfRow(
                shelf = shelf,
                selected = shelf.id in selectedShelfIds,
                onClick = { onOpenShelf(shelf) },
                onLongClick = { onLongPressShelf(shelf) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SharedMobileShelfRow(
    shelf: Shelf,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(12.dp).size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(shelf.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${shelf.bookCount} ${if (shelf.bookCount == 1) "book" else "books"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
fun SharedMobileEmptyLibrary(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.FileOpen,
            contentDescription = readerString("content_desc_no_files_icon", "No files"),
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(32.dp))
            FilledTonalButton(
                onClick = onAction,
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(actionLabel)
            }
        }
        if (secondaryActionLabel != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onSecondaryAction) {
                Text(secondaryActionLabel)
            }
        }
    }
}

internal fun sharedMobileFileTypeColor(type: FileType): Color {
    return when (type) {
        FileType.PDF -> Color(0xFFE53935)
        FileType.EPUB, FileType.MOBI, FileType.FB2 -> Color(0xFF1E88E5)
        FileType.CBZ, FileType.CBR, FileType.CB7, FileType.CBT -> Color(0xFF8E24AA)
        FileType.DOCX, FileType.ODT, FileType.FODT -> Color(0xFF3949AB)
        FileType.MD, FileType.TXT, FileType.HTML -> Color(0xFF00897B)
        FileType.PPTX -> Color(0xFFF4511E)
        FileType.AUDIOBOOK -> Color(0xFF6D4C41)
        FileType.UNKNOWN -> Color(0xFF757575)
    }
}

internal fun List<BookItem>.filteredSharedMobileBooks(query: String): List<BookItem> {
    val normalized = query.trim()
    if (normalized.isBlank()) return this
    return filter { book ->
        book.displayName.contains(normalized, ignoreCase = true) ||
            book.title?.contains(normalized, ignoreCase = true) == true ||
            book.author?.contains(normalized, ignoreCase = true) == true ||
            book.sourceFolder?.contains(normalized, ignoreCase = true) == true ||
            book.tags.any { it.name.contains(normalized, ignoreCase = true) }
    }
}

@Composable
internal fun SortOrder.sharedMobileLabel(): String {
    return when (this) {
        SortOrder.RECENT -> readerString("sort_recent", "Recent")
        SortOrder.DATE_ADDED_NEWEST -> readerString("sort_date_added_newest", "Newest")
        SortOrder.DATE_ADDED_OLDEST -> readerString("sort_date_added_oldest", "Oldest")
        SortOrder.TITLE_ASC -> readerString("sort_title_az", "Title A-Z")
        SortOrder.AUTHOR_ASC -> readerString("sort_author_az", "Author A-Z")
        SortOrder.PERCENT_ASC -> readerString("sort_percent_asc", "Percent complete 0–100")
        SortOrder.PERCENT_DESC -> readerString("sort_percent_desc", "Percent complete 100–0")
        SortOrder.SIZE_ASC -> readerString("sort_size_smallest", "Size (Smallest)")
        SortOrder.SIZE_DESC -> readerString("sort_size_biggest", "Size (Biggest)")
    }
}

@Composable
internal fun ReadStatusFilter.sharedMobileLabel(): String {
    return when (this) {
        ReadStatusFilter.ALL -> readerString("read_status_all", "All")
        ReadStatusFilter.UNREAD -> readerString("read_status_unread", "Unread")
        ReadStatusFilter.IN_PROGRESS -> readerString("read_status_in_progress", "In Progress")
        ReadStatusFilter.COMPLETED -> readerString("read_status_completed", "Completed")
    }
}
val LocalUsePdfFileNameAsDisplayName = staticCompositionLocalOf { false }
