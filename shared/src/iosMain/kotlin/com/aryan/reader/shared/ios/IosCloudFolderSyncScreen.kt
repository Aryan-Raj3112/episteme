package com.aryan.reader.shared.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.CloudFolderConflictResolution
import com.aryan.reader.shared.CloudFolderConflictUiItem
import com.aryan.reader.shared.CloudFolderIncomingChoice
import com.aryan.reader.shared.CloudFolderIncomingFolderPrompt
import com.aryan.reader.shared.CloudFolderSyncSelection
import com.aryan.reader.shared.CloudFolderSyncSettingsUiState
import com.aryan.reader.shared.ui.readerString

/**
 * iOS cloud-folder sync surface.
 *
 * Android is the absolute benchmark and is NOT changed. This screen mirrors
 * Android's `CloudFolderSyncSettingsDialog` (SettingsScreen.kt:477-490) and
 * the AppNavigation incoming prompt (AppNavigation.kt:618-680):
 *
 * - Selection edits call through to `onSelectionChange` with a normalized
 *   value; the caller persists via `IosCloudFolderSyncPrefs` and enqueues a
 *   cloud pass when sync is enabled (mirroring
 *   MainViewModel.setCloudFolderSyncSelection).
 * - Remote roots without a binding are inventory-only and not selectable
 *   (see `projectCloudFolderSyncOptions`); the prompt offers the three
 *   Android choices CLOUD_ONLY / DOWNLOAD_ALL / BIND_LOCAL_FOLDER.
 * - Conflicts resolve via `onConflictResolution`, mirroring
 *   MainViewModel.resolveCloudFolderConflict (worker revalidates before
 *   applying, so stale choices surface fresh conflicts).
 */
@Composable
internal fun IosCloudFolderSyncScreen(
    uiState: CloudFolderSyncSettingsUiState,
    isSyncEnabled: Boolean,
    isProUser: Boolean,
    isLoading: Boolean,
    onBack: () -> Unit,
    onSelectionChange: (CloudFolderSyncSelection) -> Unit,
    onConflictResolution: (CloudFolderConflictUiItem, CloudFolderConflictResolution) -> Unit,
    onBindLocalFolder: (CloudFolderIncomingFolderPrompt) -> Unit,
    incomingPrompt: CloudFolderIncomingFolderPrompt?,
    onIncomingChoice: (CloudFolderIncomingFolderPrompt, CloudFolderIncomingChoice) -> Unit,
    onDismissIncoming: () -> Unit,
) {
    IosUtilityPage(
        title = readerString("settings_folder_sync_title", "Folder sync"),
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                readerString("settings_folder_sync_title", "Folder sync"),
                style = MaterialTheme.typography.headlineSmall,
            )
            when {
                !isProUser -> Text(
                    readerString(
                        "settings_folder_sync_pro_required",
                        "Folder sync requires Pro. Local folders stay on this device until Pro is active.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                !isSyncEnabled -> Text(
                    readerString(
                        "settings_folder_sync_sync_off",
                        "Turn on cloud sync to choose which folders sync across devices.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isLoading && uiState.folders.isEmpty()) {
                CircularProgressIndicator()
            } else if (uiState.folders.isEmpty()) {
                Text(
                    readerString(
                        "settings_folder_sync_empty",
                        "No cloud folders yet. Add a local folder, then choose it here to sync.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    readerString(
                        "settings_folder_sync_selected_count",
                        "%1\$d of %2\$d folders selected",
                        uiState.selectedFolderCount,
                        uiState.normalizedFolders.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.normalizedFolders, key = { it.normalizedRootId }) { option ->
                        val selected = uiState.selection.includes(option.normalizedRootId)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 2.dp,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(option.normalizedDisplayName, fontWeight = FontWeight.SemiBold)
                                    val detail = when {
                                        option.isRemote && !option.isBoundLocally ->
                                            readerString("settings_folder_sync_remote", "Available in Drive")
                                        option.syncProgress != null ->
                                            readerString("settings_folder_sync_syncing", "Syncing…")
                                        !option.hasKnownStats ->
                                            readerString("settings_folder_sync_scanning", "Scanning…")
                                        option.fileCount > 0 ->
                                            readerString(
                                                "settings_folder_sync_files",
                                                "%1\$d files",
                                                option.fileCount,
                                            )
                                        else ->
                                            readerString("settings_folder_sync_device_only", "Device only")
                                    }
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    option.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                                        Text(
                                            error,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = selected,
                                    enabled = option.isAvailable && option.isSelectable && isProUser,
                                    onCheckedChange = { checked ->
                                        val next = if (checked) {
                                            uiState.includeRoot(option.normalizedRootId)
                                        } else {
                                            uiState.excludeRoot(option.normalizedRootId)
                                        }
                                        onSelectionChange(next.selection.normalized())
                                    },
                                )
                            }
                        }
                    }
                    if (uiState.conflicts.isNotEmpty()) {
                        item(key = "conflicts-header") {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                readerString("settings_folder_sync_conflicts", "Needs your decision"),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        items(uiState.conflicts, key = { it.conflictId }) { conflict ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                tonalElevation = 2.dp,
                            ) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(conflict.normalizedPath, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        conflict.type.name.lowercase().replace('_', ' '),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = {
                                                onConflictResolution(
                                                    conflict,
                                                    CloudFolderConflictResolution.KEEP_LOCAL,
                                                )
                                            },
                                        ) {
                                            Text(readerString("settings_folder_sync_keep_local", "Keep mine"))
                                        }
                                        TextButton(
                                            onClick = {
                                                onConflictResolution(
                                                    conflict,
                                                    CloudFolderConflictResolution.KEEP_REMOTE,
                                                )
                                            },
                                        ) {
                                            Text(readerString("settings_folder_sync_keep_remote", "Keep cloud"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    incomingPrompt?.let { prompt ->
        IosCloudFolderIncomingDialog(
            prompt = prompt,
            onChoice = { choice ->
                if (choice == CloudFolderIncomingChoice.BIND_LOCAL_FOLDER) {
                    onBindLocalFolder(prompt)
                } else {
                    onIncomingChoice(prompt, choice)
                }
            },
            onDismiss = onDismissIncoming,
        )
    }
}

@Composable
private fun IosCloudFolderIncomingDialog(
    prompt: CloudFolderIncomingFolderPrompt,
    onChoice: (CloudFolderIncomingChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                readerString(
                    "settings_folder_sync_incoming_title",
                    "Sync \"%1\$s\" to this device?",
                    prompt.displayName,
                ),
            )
        },
        text = {
            Text(
                readerString(
                    "settings_folder_sync_incoming_desc",
                    "Another device shared %1\$d file(s). Choose how this device keeps it.",
                    prompt.fileCount,
                ),
            )
        },
        confirmButton = {
            Column {
                TextButton(onClick = { onChoice(CloudFolderIncomingChoice.DOWNLOAD_ALL) }) {
                    Text(readerString("settings_folder_sync_download_all", "Download all"))
                }
                TextButton(onClick = { onChoice(CloudFolderIncomingChoice.BIND_LOCAL_FOLDER) }) {
                    Text(readerString("settings_folder_sync_bind_folder", "Choose local folder"))
                }
                TextButton(onClick = { onChoice(CloudFolderIncomingChoice.CLOUD_ONLY) }) {
                    Text(readerString("settings_folder_sync_cloud_only", "Keep in cloud"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(readerString("action_not_now", "Not now"))
            }
        },
    )
}
