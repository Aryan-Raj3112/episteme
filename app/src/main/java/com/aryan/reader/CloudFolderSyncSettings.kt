package com.aryan.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.flow.collect

/** Cloud-folder settings are available only in the Pro build and for a
 * currently signed-in account whose entitlement is still valid. */
internal fun ReaderScreenState.canUseCloudFolderSync(): Boolean =
    BuildConfig.IS_PRO &&
        currentUser?.uid?.trim()?.isNotBlank() == true &&
        isProUser

/**
 * Full-screen folder policy surface used by Home, Library Beta, and the
 * Folders tab.  Keeping this as a route (rather than another nested dialog)
 * gives the inventory enough room to remain readable as roots and conflicts
 * are added, while the existing Settings entry can continue using the compact
 * dialog wrapper.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CloudFolderSyncSettingsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cloudFolderRootStats by viewModel.cloudFolderRootStats.collectAsStateWithLifecycle()
    val cloudFolderLocalInventories by viewModel.cloudFolderLocalInventories.collectAsStateWithLifecycle()
    val cloudFolderRoots by viewModel.cloudFolderRoots.collectAsStateWithLifecycle()
    val cloudFolderBindings by viewModel.cloudFolderBindings.collectAsStateWithLifecycle()
    val cloudFolderSyncProgress by viewModel.cloudFolderSyncProgress.collectAsStateWithLifecycle()
    val cloudFolderConflicts by viewModel.cloudFolderConflicts.collectAsStateWithLifecycle()
    val canUseCloudFolderSync = uiState.canUseCloudFolderSync()
    var cloudFolderSelection by remember(uiState.currentUser?.uid) {
        mutableStateOf(viewModel.cloudFolderSyncSelection())
    }

    if (!canUseCloudFolderSync) {
        LaunchedEffect(Unit) { onBackClick() }
        return
    }

    LaunchedEffect(uiState.currentUser?.uid) {
        cloudFolderSelection = viewModel.cloudFolderSyncSelection()
        viewModel.refreshCloudFolderSyncState()
    }
    LaunchedEffect(Unit) {
        CloudFolderSyncEvents.stateChanged.collect {
            cloudFolderSelection = viewModel.cloudFolderSyncSelection()
            viewModel.refreshCloudFolderSyncState()
        }
    }

    val cloudFolderOptions = remember(
        uiState.syncedFolders,
        uiState.rawLibraryFiles,
        cloudFolderRootStats,
        cloudFolderLocalInventories,
        cloudFolderRoots,
        cloudFolderBindings,
        cloudFolderSyncProgress,
    ) {
        cloudFolderSyncFolderOptions(
            folders = uiState.syncedFolders,
            indexedFiles = uiState.rawLibraryFiles,
            repositoryStats = cloudFolderRootStats,
            localInventories = cloudFolderLocalInventories,
            repositoryRoots = cloudFolderRoots,
            deviceBindings = cloudFolderBindings,
            syncProgress = cloudFolderSyncProgress,
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.folder_sync_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CloudFolderLibrarySyncHeader(
                enabled = uiState.isSyncEnabled,
                enabledForAccount = canUseCloudFolderSync,
                onEnabledChange = { enabled ->
                    if (viewModel.uiState.value.canUseCloudFolderSync()) {
                        viewModel.setSyncEnabled(enabled)
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            CloudFolderSyncSettingsContent(
                state = CloudFolderSyncSettingsUiState(
                    selection = cloudFolderSelection,
                    folders = cloudFolderOptions,
                    conflicts = cloudFolderConflicts,
                ).normalized(),
                onSelectionChange = { selection ->
                    if (viewModel.uiState.value.canUseCloudFolderSync()) {
                        val normalized = selection.normalized()
                        cloudFolderSelection = normalized
                        viewModel.setCloudFolderSyncSelection(normalized)
                    }
                },
                onConflictResolution = { conflict, resolution ->
                    if (viewModel.uiState.value.canUseCloudFolderSync()) {
                        viewModel.resolveCloudFolderConflict(conflict, resolution)
                    }
                },
                fullScreen = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun CloudFolderLibrarySyncHeader(
    enabled: Boolean,
    enabledForAccount: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.folder_sync_library_sync_title),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (enabled) stringResource(R.string.folder_sync_library_sync_on)
                    else stringResource(R.string.folder_sync_library_sync_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                enabled = enabledForAccount,
                onCheckedChange = onEnabledChange,
            )
        }
    }
}

/**
 * Folder-sync settings are intentionally a separate surface from the simple
 * cloud-library switch.  Changes are delivered as portable policy values;
 * the caller decides how and when to persist/schedule the sync.
 */
@Composable
internal fun CloudFolderSyncSettingsDialog(
    folders: List<CloudFolderSyncFolderOption>,
    selection: CloudFolderSyncSelection,
    conflicts: List<CloudFolderConflictUiItem> = emptyList(),
    onSelectionChange: (CloudFolderSyncSelection) -> Unit,
    onConflictResolution: (CloudFolderConflictUiItem, CloudFolderConflictResolution) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
) {
    val state = remember(folders, selection, conflicts) {
        CloudFolderSyncSettingsUiState(
            selection = selection,
            folders = folders,
            conflicts = conflicts,
        ).normalized()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_sync_settings_title)) },
        text = {
            CloudFolderSyncSettingsContent(
                state = state,
                onSelectionChange = onSelectionChange,
                onConflictResolution = onConflictResolution,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
internal fun CloudFolderSyncSettingsContent(
    state: CloudFolderSyncSettingsUiState,
    onSelectionChange: (CloudFolderSyncSelection) -> Unit,
    onConflictResolution: (CloudFolderConflictUiItem, CloudFolderConflictResolution) -> Unit = { _, _ -> },
    fullScreen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val folders = state.normalizedFolders
    val selectedCount = state.selectedFolderCount
    val selection = state.selection.toExplicitSelection(folders.map { it.normalizedRootId })
    val selectableFolderCount = folders.count { it.isAvailable && it.isSelectable }
    val selectableRootIds = folders
        .filter { it.isAvailable && it.isSelectable }
        .map { it.normalizedRootId }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fullScreen) Modifier else Modifier.heightIn(max = 560.dp))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Select the local folders you want to back up to Drive. Their folder structure is preserved on other devices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Folders", fontWeight = FontWeight.SemiBold)
        Text(
            if (selectableFolderCount == 0) {
                "Add folders from the Folders tab, then choose which ones to sync here."
            } else {
                "$selectedCount of $selectableFolderCount selected"
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

        if (folders.isEmpty()) {
            Text(
                "No local folders available.",
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
                    "Folders",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    enabled = selectableFolderCount > 0 && selectedCount < selectableFolderCount,
                    onClick = {
                        onSelectionChange(
                            CloudFolderSyncSelection(
                                mode = CloudFolderSyncSelectionMode.SELECTED,
                                selectedRootIds = selectableRootIds.toSet(),
                            )
                        )
                    },
                ) { Text("Select all") }
                TextButton(
                    enabled = selectedCount > 0,
                    onClick = {
                        onSelectionChange(
                            CloudFolderSyncSelection(
                                mode = CloudFolderSyncSelectionMode.SELECTED,
                            )
                        )
                    },
                ) { Text("Clear all") }
            }

            folders.forEach { folder ->
                val included = folder.isAvailable && folder.isSelectable &&
                    selection.includes(folder.normalizedRootId)
                CloudFolderSyncFolderRow(
                    folder = folder,
                    checked = included,
                    enabled = folder.isAvailable && folder.isSelectable,
                    onCheckedChange = { checked ->
                        val nextIds = if (checked) {
                            selection.selectedRootIds + folder.normalizedRootId
                        } else {
                            selection.selectedRootIds - folder.normalizedRootId
                        }
                        onSelectionChange(
                            CloudFolderSyncSelection(
                                mode = CloudFolderSyncSelectionMode.SELECTED,
                                selectedRootIds = nextIds,
                            ).normalized(folders.map { it.normalizedRootId })
                        )
                    },
                )
            }
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
private fun CloudFolderSyncFolderRow(
    folder: CloudFolderSyncFolderOption,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
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
                    cloudFolderDisplayError(error, folder.syncProgress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CloudFolderSyncProgressIndicator(folder.syncProgress)
    }
}

@Composable
private fun CloudFolderSyncProgressIndicator(progress: com.aryan.reader.shared.CloudFolderSyncProgress?) {
    val current = progress ?: return
    when (current.phase) {
        com.aryan.reader.shared.CloudFolderSyncPhase.SCANNING -> {
            // SAF enumeration has no reliable total until it completes.
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
        com.aryan.reader.shared.CloudFolderSyncPhase.UPLOADING -> {
            val fraction = current.fraction
            if (fraction == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        "${current.completedFiles}/${current.totalFiles}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        com.aryan.reader.shared.CloudFolderSyncPhase.FINALIZING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
        com.aryan.reader.shared.CloudFolderSyncPhase.SUCCEEDED,
        com.aryan.reader.shared.CloudFolderSyncPhase.FAILED -> Unit
    }
}

private fun cloudFolderDisplayError(
    fallback: String,
    progress: com.aryan.reader.shared.CloudFolderSyncProgress?,
): String {
    val status = progress?.errorStatus
    return when (status) {
        "forbidden", "permission_denied" -> "Drive denied access"
        "unauthorized", "unauthenticated" -> "Drive authorization expired"
        "quota" -> "Drive quota or rate limit reached"
        "network", "timeout" -> "Network problem; will retry"
        "not_found" -> "Drive item was not found"
        "invalid_data" -> "Drive rejected the folder data"
        "unsupported" -> "Drive does not support this item"
        "unknown" -> "Cloud upload failed; will retry"
        else -> fallback
    }
}

private fun buildCloudFolderRowSummary(folder: CloudFolderSyncFolderOption): String {
    val inventory = when {
        !folder.isAvailable -> "Needs local folder access"
        !folder.hasKnownStats -> "Not scanned yet"
        !folder.scanComplete -> "Scanning…"
        else -> "${folder.fileCount.coerceAtLeast(0)} files · " +
            if (folder.sizeKnown) formatCloudFolderBytes(folder.totalBytes) else "size unavailable"
    }
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
                    selection = CloudFolderSyncSelection(
                        mode = CloudFolderSyncSelectionMode.SELECTED,
                        selectedRootIds = setOf("root-books"),
                    ),
                    folders = previewFolders,
                ),
                onSelectionChange = {},
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
