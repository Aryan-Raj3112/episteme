package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.SyncedFolder

data class SharedAndroidFolderStats(
    val totalBooks: Int = 0,
    val countsByType: Map<FileType, Int> = emptyMap(),
)

data class SharedAndroidFolderSyncStrings(
    val addFolder: String,
    val addDescription: String,
    val scanning: String,
    val scanAll: String,
    val syncMetadata: String,
    val emptyTitle: String,
    val emptyMessage: String,
    val selectFolder: String,
    val localSyncDisabled: String,
    val optionsDescription: String,
    val editFilters: String,
    val disableLocalSync: String,
    val enableLocalSync: String,
    val removeFolder: String,
    val lastSync: String,
    val never: String,
    val booksCount: String,
    val filterCount: (FileType, Int) -> String,
    val filterFileTypes: String,
    val filterFileTypesDescription: String,
    val save: String,
    val cancel: String,
    val disableDialogTitle: String,
    val disableDialogDescription: String,
    val disableRemoveData: String,
    val disableKeepData: String,
)

/** Exact Android folder-sync screen; storage/scanning/date formatting remain platform adapters. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedAndroidFolderSyncScreen(
    folders: List<SyncedFolder>,
    statsByFolderUri: Map<String, SharedAndroidFolderStats>,
    syncableFileTypes: List<FileType>,
    isLoading: Boolean,
    strings: SharedAndroidFolderSyncStrings,
    onAddFolder: () -> Unit,
    onRemoveFolder: (SyncedFolder) -> Unit,
    onLocalSyncChange: (SyncedFolder, enabled: Boolean, removeSyncData: Boolean) -> Unit,
    onFileTypesChange: (SyncedFolder, Set<FileType>) -> Unit,
    onScanAll: () -> Unit,
    onSyncMetadata: () -> Unit,
    formatLastScan: (Long) -> String,
    syncIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingFolder by remember { mutableStateOf<SyncedFolder?>(null) }
    var disablingFolder by remember { mutableStateOf<SyncedFolder?>(null) }
    val hasEnabledFolders = folders.any { it.localSyncEnabled }
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (folders.size < 10) {
                ExtendedFloatingActionButton(
                    text = { Text(strings.addFolder) },
                    icon = { Icon(Icons.Default.Add, strings.addDescription) },
                    onClick = onAddFolder,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (folders.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = onScanAll,
                        enabled = !isLoading && hasEnabledFolders,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isLoading) strings.scanning else strings.scanAll)
                    }
                    OutlinedButton(
                        onClick = onSyncMetadata,
                        enabled = !isLoading && hasEnabledFolders,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        syncIcon()
                        Spacer(Modifier.width(8.dp))
                        Text(strings.syncMetadata)
                    }
                }
            } else {
                SharedMobileEmptyLibrary(
                    title = strings.emptyTitle,
                    message = strings.emptyMessage,
                    actionLabel = strings.selectFolder,
                    onAction = onAddFolder,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(folders, key = { it.uriString }) { folder ->
                    SharedAndroidFolderCard(
                        folder = folder,
                        stats = statsByFolderUri[folder.uriString] ?: SharedAndroidFolderStats(),
                        strings = strings,
                        lastScanText = if (folder.lastScanTime == 0L) strings.never else formatLastScan(folder.lastScanTime),
                        onRemove = { onRemoveFolder(folder) },
                        onToggle = { if (folder.localSyncEnabled) disablingFolder = folder else onLocalSyncChange(folder, true, false) },
                        onEdit = { editingFolder = folder },
                    )
                }
            }
        }
    }
    editingFolder?.let { folder ->
        SharedAndroidFolderFiltersDialog(
            initialTypes = folder.allowedFileTypes,
            availableTypes = syncableFileTypes,
            strings = strings,
            onConfirm = { onFileTypesChange(folder, it); editingFolder = null },
            onDismiss = { editingFolder = null },
        )
    }
    disablingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { disablingFolder = null },
            title = { Text(strings.disableDialogTitle) },
            text = { Text(strings.disableDialogDescription) },
            confirmButton = {
                TextButton(onClick = { onLocalSyncChange(folder, false, true); disablingFolder = null }) {
                    Text(strings.disableRemoveData)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { disablingFolder = null }) { Text(strings.cancel) }
                    TextButton(onClick = { onLocalSyncChange(folder, false, false); disablingFolder = null }) {
                        Text(strings.disableKeepData)
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharedAndroidFolderCard(
    folder: SyncedFolder,
    stats: SharedAndroidFolderStats,
    strings: SharedAndroidFolderSyncStrings,
    lastScanText: String,
    onRemove: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FolderSpecial, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(folder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!folder.localSyncEnabled) Text(strings.localSyncDisabled, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, strings.optionsDescription) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text(strings.editFilters) }, onClick = { showMenu = false; onEdit() })
                        DropdownMenuItem(
                            text = { Text(if (folder.localSyncEnabled) strings.disableLocalSync else strings.enableLocalSync) },
                            onClick = { showMenu = false; onToggle() },
                        )
                        DropdownMenuItem(
                            text = { Text(strings.removeFolder) },
                            onClick = { showMenu = false; onRemove() },
                            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(strings.lastSync, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(lastScanText, style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(strings.booksCount, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(stats.totalBooks.toString(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (stats.countsByType.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.countsByType.forEach { (type, count) ->
                        AssistChip(onClick = {}, label = { Text(strings.filterCount(type, count)) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharedAndroidFolderFiltersDialog(
    initialTypes: Set<FileType>,
    availableTypes: List<FileType>,
    strings: SharedAndroidFolderSyncStrings,
    onConfirm: (Set<FileType>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTypes by remember { mutableStateOf(initialTypes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(strings.filterFileTypes, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(strings.filterFileTypesDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(Modifier.padding(bottom = 16.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableTypes.forEach { type ->
                        val selected = type in selectedTypes
                        FilterChip(
                            selected = selected,
                            onClick = { selectedTypes = if (selected) selectedTypes - type else selectedTypes + type },
                            label = { Text(type.name, style = MaterialTheme.typography.labelLarge) },
                            leadingIcon = if (selected) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedTypes) }, enabled = selectedTypes.isNotEmpty(), shape = MaterialTheme.shapes.medium) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
