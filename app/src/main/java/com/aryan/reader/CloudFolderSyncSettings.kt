package com.aryan.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.CloudFolderIncomingChoice
import com.aryan.reader.shared.CloudFolderIncomingFolderPrompt
import com.aryan.reader.shared.CloudFolderConflictResolution
import com.aryan.reader.shared.CloudFolderConflictUiItem
import com.aryan.reader.shared.CloudFolderMaterializationMode
import com.aryan.reader.shared.CloudFolderRoot
import com.aryan.reader.shared.CloudFolderRootStats
import com.aryan.reader.shared.CloudFolderSyncFolderOption
import com.aryan.reader.shared.CloudFolderSyncSelection
import com.aryan.reader.shared.CloudFolderSyncSelectionMode
import com.aryan.reader.shared.CloudFolderSyncSettingsUiState
import com.aryan.reader.shared.effectiveResolution
import com.aryan.reader.shared.supportsKeepBoth

/**
 * Folder-sync settings are intentionally a separate surface from the simple
 * cloud-library switch.  Changes are delivered as portable policy values;
 * the caller decides how and when to persist/schedule the sync.
 */
@Composable
internal fun CloudFolderSyncSettingsDialog(
    folders: List<CloudFolderSyncFolderOption>,
    selection: CloudFolderSyncSelection,
    localFolderIndexingEnabled: Boolean,
    conflicts: List<CloudFolderConflictUiItem> = emptyList(),
    onSelectionChange: (CloudFolderSyncSelection) -> Unit,
    onLocalFolderIndexingChange: (Boolean) -> Unit,
    onConflictResolution: (CloudFolderConflictUiItem, CloudFolderConflictResolution) -> Unit = { _, _ -> },
    onAddFolder: () -> Unit,
    onSetMaterializationMode: (String, CloudFolderMaterializationMode) -> Unit = { _, _ -> },
    onManageIncomingFolder: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val state = remember(folders, selection, localFolderIndexingEnabled, conflicts) {
        CloudFolderSyncSettingsUiState(
            localFolderIndexingEnabled = localFolderIndexingEnabled,
            selection = selection,
            folders = folders,
            conflicts = conflicts,
        ).normalized()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Folder backup and sync") },
        text = {
            CloudFolderSyncSettingsContent(
                state = state,
                onSelectionChange = onSelectionChange,
                onLocalFolderIndexingChange = onLocalFolderIndexingChange,
                onConflictResolution = onConflictResolution,
                onAddFolder = onAddFolder,
                onSetMaterializationMode = onSetMaterializationMode,
                onManageIncomingFolder = onManageIncomingFolder,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun CloudFolderSyncSettingsContent(
    state: CloudFolderSyncSettingsUiState,
    onSelectionChange: (CloudFolderSyncSelection) -> Unit,
    onLocalFolderIndexingChange: (Boolean) -> Unit,
    onConflictResolution: (CloudFolderConflictUiItem, CloudFolderConflictResolution) -> Unit = { _, _ -> },
    onAddFolder: () -> Unit,
    onSetMaterializationMode: (String, CloudFolderMaterializationMode) -> Unit = { _, _ -> },
    onManageIncomingFolder: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val folders = state.normalizedFolders
    val selectedCount = state.selectedFolderCount
    val selection = state.selection
    val mode = state.selection.mode
    val selectableFolderCount = folders.count { it.isAvailable && it.isSelectable }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Local folders are opt-in for cloud sync. The app uploads the folder manifest and files directly; it does not first import them into the library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLocalFolderIndexingChange(!state.localFolderIndexingEnabled) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show local folders in library", fontWeight = FontWeight.SemiBold)
                Text(
                    "Keep local-folder metadata available on this device. This does not select a folder for cloud upload.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.localFolderIndexingEnabled,
                onCheckedChange = onLocalFolderIndexingChange,
            )
        }

        HorizontalDivider()

        Text("Folders and cloud roots", fontWeight = FontWeight.SemiBold)
        Text(
            when (mode) {
                CloudFolderSyncSelectionMode.EXCLUDED -> "No local folder is uploaded."
                CloudFolderSyncSelectionMode.SELECTED -> "$selectedCount of $selectableFolderCount local folders selected."
                CloudFolderSyncSelectionMode.ALL -> "All current and future local folders are selected."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.conflicts.isNotEmpty()) {
            HorizontalDivider()
            Text("Conflicts needing review", fontWeight = FontWeight.SemiBold)
            Text(
                "Sync pauses until each changed item has an explicit decision. If the folder changes again, the decision is safely reset.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.conflicts.forEach { conflict ->
                CloudFolderConflictRow(
                    conflict = conflict,
                    onResolution = { resolution -> onConflictResolution(conflict, resolution) },
                )
            }
        }

        CloudFolderSyncSelectionMode.entries.forEach { option ->
            CloudFolderSyncModeRow(
                mode = option,
                selected = mode == option,
                onClick = {
                    val next = when (option) {
                        CloudFolderSyncSelectionMode.EXCLUDED -> selection.excludeAllRoots()
                        CloudFolderSyncSelectionMode.SELECTED -> selection.copy(
                            mode = CloudFolderSyncSelectionMode.SELECTED,
                        ).normalized()
                        CloudFolderSyncSelectionMode.ALL -> selection.includeAllRoots()
                    }
                    onSelectionChange(next)
                },
            )
        }

        if (folders.isEmpty()) {
            Text(
                "No local or cloud folders have been added yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Folder inventory",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    enabled = folders.isNotEmpty() && mode != CloudFolderSyncSelectionMode.ALL,
                    onClick = { onSelectionChange(selection.includeAllRoots()) },
                ) { Text("Select all") }
                TextButton(
                    enabled = mode != CloudFolderSyncSelectionMode.EXCLUDED,
                    onClick = { onSelectionChange(selection.excludeAllRoots()) },
                ) { Text("Clear all") }
            }

            folders.forEach { folder ->
                val included = folder.isAvailable && folder.isSelectable &&
                    selection.includes(folder.normalizedRootId)
                CloudFolderSyncFolderRow(
                    folder = folder,
                    checked = included,
                    // ALL is a default for current and future roots, but an
                    // individual row must remain actionable so a user can
                    // exclude just this root without accidentally clearing
                    // every other folder.
                    enabled = folder.isAvailable && folder.isSelectable &&
                        mode != CloudFolderSyncSelectionMode.EXCLUDED,
                    onCheckedChange = { checked ->
                        val next = if (checked) {
                            selection.withRootIncluded(folder.normalizedRootId)
                        } else {
                            selection.withoutRoot(
                                rootId = folder.normalizedRootId,
                                knownRootIds = folders.map { it.normalizedRootId },
                            )
                        }
                        onSelectionChange(next)
                    },
                    onSetMaterializationMode = onSetMaterializationMode,
                    onManageIncomingFolder = onManageIncomingFolder,
                )
            }
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onAddFolder,
        ) {
            Text("Add or manage local folders")
        }
    }
}

@Composable
private fun CloudFolderConflictRow(
    conflict: CloudFolderConflictUiItem,
    onResolution: (CloudFolderConflictResolution) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val selectedResolution = conflict.type.effectiveResolution(conflict.resolution)
        Text(
            "${conflict.normalizedFolderName} · ${conflict.normalizedPath}",
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            conflict.type.name.replace('_', ' ').lowercase()
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (conflict.type == com.aryan.reader.shared.CloudFolderConflictType.DELETE_VS_UPDATE ||
            conflict.type == com.aryan.reader.shared.CloudFolderConflictType.UPDATE_VS_DELETE
        ) {
            Text(
                "Both versions cannot be retained because one side deleted this item. Choose the local version or the cloud update.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CloudFolderConflictAction(
                title = "Local",
                selected = selectedResolution == CloudFolderConflictResolution.KEEP_LOCAL,
                onClick = { onResolution(CloudFolderConflictResolution.KEEP_LOCAL) },
                modifier = Modifier.weight(1f),
            )
            CloudFolderConflictAction(
                title = "Cloud",
                selected = selectedResolution == CloudFolderConflictResolution.KEEP_REMOTE,
                onClick = { onResolution(CloudFolderConflictResolution.KEEP_REMOTE) },
                modifier = Modifier.weight(1f),
            )
            if (conflict.type.supportsKeepBoth()) {
                CloudFolderConflictAction(
                    title = "Both",
                    selected = selectedResolution == CloudFolderConflictResolution.KEEP_BOTH,
                    onClick = { onResolution(CloudFolderConflictResolution.KEEP_BOTH) },
                    modifier = Modifier.weight(1f),
                )
            }
            CloudFolderConflictAction(
                title = "Later",
                selected = selectedResolution == CloudFolderConflictResolution.DEFER,
                onClick = { onResolution(CloudFolderConflictResolution.DEFER) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CloudFolderConflictAction(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    if (selected) {
        Button(modifier = modifier, onClick = onClick) { Text(title) }
    } else {
        OutlinedButton(modifier = modifier, onClick = onClick) { Text(title) }
    }
}

@Composable
private fun CloudFolderSyncModeRow(
    mode: CloudFolderSyncSelectionMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val (title, summary) = when (mode) {
        CloudFolderSyncSelectionMode.EXCLUDED ->
            "Don't sync local folders" to "Local folders remain device-only."
        CloudFolderSyncSelectionMode.SELECTED ->
            "Selected folders only" to "Choose individual folders below."
        CloudFolderSyncSelectionMode.ALL ->
            "All local folders" to "Newly added folders are included automatically."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CloudFolderSyncFolderRow(
    folder: CloudFolderSyncFolderOption,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onSetMaterializationMode: (String, CloudFolderMaterializationMode) -> Unit,
    onManageIncomingFolder: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
        Column(Modifier.weight(1f)) {
            Text(
                folder.normalizedDisplayName,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildCloudFolderRowSummary(folder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            folder.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (folder.isRemote) {
            when (folder.materializationMode) {
                CloudFolderMaterializationMode.CLOUD_ONLY -> {
                    TextButton(onClick = {
                        onSetMaterializationMode(
                            folder.normalizedRootId,
                            CloudFolderMaterializationMode.KEEP_OFFLINE,
                        )
                    }) {
                        Text("Download all")
                    }
                }
                CloudFolderMaterializationMode.KEEP_OFFLINE -> {
                    TextButton(onClick = {
                        onSetMaterializationMode(
                            folder.normalizedRootId,
                            CloudFolderMaterializationMode.CLOUD_ONLY,
                        )
                    }) {
                        Text("Cloud only")
                    }
                }
                CloudFolderMaterializationMode.LOCAL_MIRROR -> {
                    TextButton(onClick = {
                        onManageIncomingFolder(folder.normalizedRootId)
                    }) {
                        Text("Manage")
                    }
                }
            }
        }
    }
}

private fun buildCloudFolderRowSummary(folder: CloudFolderSyncFolderOption): String {
    val inventory = "${folder.fileCount.coerceAtLeast(0)} files · ${formatCloudFolderBytes(folder.totalBytes)}"
    if (!folder.isRemote) return inventory
    val status = when {
        !folder.isBoundLocally -> "Available in cloud"
        folder.materializationMode == CloudFolderMaterializationMode.CLOUD_ONLY ->
            "Cloud only on this device"
        folder.materializationMode == CloudFolderMaterializationMode.KEEP_OFFLINE ->
            "Downloaded for offline access"
        else -> "Linked local folder"
    }
    return "$status · $inventory"
}

/**
 * Device 2 uses this prompt when a manifest arrives without a local binding.
 * The caller owns the actual download/grant work and may close the prompt after
 * recording the selected choice.
 */
@Composable
internal fun CloudFolderIncomingFolderPromptDialog(
    prompt: CloudFolderIncomingFolderPrompt,
    onChoice: (CloudFolderIncomingChoice) -> Unit,
    onBindLocalFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Folder available from another device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    buildString {
                        append(prompt.displayName)
                        prompt.sourceDeviceName?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${prompt.fileCount} files · ${formatCloudFolderBytes(prompt.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Choose whether this device should keep the folder in the cloud, download a complete local copy, or bind an existing local folder.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                IncomingFolderChoiceButton(
                    title = "Keep cloud-only",
                    summary = "Keep metadata only; files stay in Drive until you choose a copy.",
                    onClick = { onChoice(CloudFolderIncomingChoice.CLOUD_ONLY) },
                )
                IncomingFolderChoiceButton(
                    title = "Download all files",
                    summary = "Create an app-managed offline copy of the complete tree.",
                    onClick = { onChoice(CloudFolderIncomingChoice.DOWNLOAD_ALL) },
                )
                IncomingFolderChoiceButton(
                    title = "Bind a local folder",
                    summary = "Choose a local folder and mirror the remote structure into it.",
                    onClick = {
                        onChoice(CloudFolderIncomingChoice.BIND_LOCAL_FOLDER)
                        onBindLocalFolder()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}

@Composable
private fun IncomingFolderChoiceButton(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title)
            Text(summary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatCloudFolderBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L)
    return when {
        value < 1_024L -> "$value B"
        value < 1_024L * 1_024L -> "${value / 1_024L} KB"
        value < 1_024L * 1_024L * 1_024L -> "${value / (1_024L * 1_024L)} MB"
        else -> "${value / (1_024L * 1_024L * 1_024L)} GB"
    }
}

private val previewFolders = listOf(
    CloudFolderSyncFolderOption("root-books", "Books", fileCount = 18, totalBytes = 24_000_000L),
    CloudFolderSyncFolderOption("root-comics", "Comics", fileCount = 6, totalBytes = 140_000_000L),
    CloudFolderSyncFolderOption("root-notes", "Notes", fileCount = 42, totalBytes = 2_000_000L),
)

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun CloudFolderSyncSettingsPreview() {
    MaterialTheme {
        Surface {
            CloudFolderSyncSettingsContent(
                state = CloudFolderSyncSettingsUiState(
                    localFolderIndexingEnabled = true,
                    selection = CloudFolderSyncSelection(
                        mode = CloudFolderSyncSelectionMode.SELECTED,
                        selectedRootIds = setOf("root-books"),
                    ),
                    folders = previewFolders,
                ),
                onSelectionChange = {},
                onLocalFolderIndexingChange = {},
                onAddFolder = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun CloudFolderIncomingFolderPromptPreview() {
    MaterialTheme {
        Surface {
            CloudFolderIncomingFolderPromptDialog(
                prompt = CloudFolderIncomingFolderPrompt(
                    root = CloudFolderRoot(
                        rootId = "root-books",
                        name = "Books",
                        stats = CloudFolderRootStats(fileCount = 18, totalBytes = 24_000_000L),
                    ),
                    sourceDeviceName = "Pixel 9",
                ),
                onChoice = {},
                onBindLocalFolder = {},
                onDismiss = {},
            )
        }
    }
}
