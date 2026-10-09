/**
 * Shared Android chrome still owned by this file. The filename is a historical artifact: the
 * `LibraryScreen` this was named for was retired when Library Beta replaced it, and is now gone
 * (see `docs/shared-parity-migration.md` C4). What remains is the set of composables that its
 * neighbours still call, so the name no longer describes the contents.
 *
 * Live callers, for anyone re-homing these:
 * - `UnifiedLibrarySections` — `FolderSyncScreen`, `LibraryListItem`
 * - `UnifiedLibraryScreen` — `LibraryFilterSheet`, `OpdsTab`
 * - `UnifiedLibraryShelfCrud` — `LibraryListItem`
 *
 * Splitting this into per-concern files is mechanical and safe, but it is pure relocation with no
 * behavior change, so it is deliberately not done here: renaming churn is not worth a diff's worth
 * of review on its own. Do it when one of these symbols next needs to move for a real reason.
 */

package com.aryan.reader

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.TagEntity
import com.aryan.reader.opds.OpdsCatalog
import com.aryan.reader.opds.OpdsEntry
import com.aryan.reader.opds.OpdsRepository
import com.aryan.reader.opds.OpdsViewModel
import com.aryan.reader.shared.CloudFolderSyncSelection
import com.aryan.reader.shared.LOCAL_FOLDER_SYNC_DATA_DIR
import com.aryan.reader.shared.Tag
import com.aryan.reader.shared.ui.SharedAndroidFolderStats
import com.aryan.reader.shared.ui.SharedAndroidFolderSyncScreen
import com.aryan.reader.shared.ui.SharedAndroidFolderSyncStrings
import com.aryan.reader.shared.ui.SharedMobileLibraryBookListCardFrame
import com.aryan.reader.shared.ui.SharedMobileLibraryFilterDialog
import com.aryan.reader.shared.ui.SharedMobileLibraryFilterLabels
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun LibraryListItem(
    item: RecentFileItem,
    isSelected: Boolean,
    isPinned: Boolean = false,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    isDownloading: Boolean,
    usePdfFileNameAsDisplayName: Boolean = false,
) {
    SharedMobileLibraryBookListCardFrame(
        isAvailable = item.isAvailable,
        isSelected = isSelected,
        onClick = onItemClick,
        onLongClick = onItemLongClick,
        modifier = Modifier.testTag("LibraryBookItem_${item.bookId}"),
        cover = {
            ThemedBookCover(
                item = item,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isSelected) {
                Box(
                    modifier = Modifier.matchParentSize().background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.content_desc_selected),
                        modifier = Modifier.size(36.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(6.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        header = {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.cardTitle(usePdfFileNameAsDisplayName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.cardAuthor(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.sourceFolderUri != null || item.isOpdsStream() || isPinned) {
                FileStatusBadges(item = item, isPinned = isPinned)
            }
        },
        metadata = {
            FileTypeBadge(type = item.type, overlay = false)
            if (item.tags.isNotEmpty()) {
                BookTagChipsRow(
                    tags = item.tags,
                    compact = true,
                    modifier = Modifier.weight(1f, fill = false),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (!item.isAvailable) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isDownloading) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = if (isDownloading) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = stringResource(R.string.not_available_locally),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = if (isDownloading) {
                                stringResource(R.string.status_downloading)
                            } else {
                                stringResource(R.string.not_available_locally)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        progress = {
            ReadingProgressSection(
                progressPercentage = item.progressPercentage,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
internal fun FolderSyncScreen(
    syncedFolders: List<SyncedFolder>,
    allRecentFiles: List<RecentFileItem>,
    onAddFolderClick: () -> Unit,
    onRemoveFolderClick: (SyncedFolder) -> Unit,
    onFolderLocalSyncChange: (SyncedFolder, Boolean, Boolean) -> Unit,
    onEditFolderFiltersClick: (SyncedFolder, Set<FileType>) -> Unit,
    onScanNowClick: () -> Unit,
    onSyncMetadataClick: () -> Unit,
    isLoading: Boolean,
    cloudFolderSelection: CloudFolderSyncSelection? = null,
    cloudSyncEnabled: Boolean = false,
    isProUser: Boolean = false,
    onCloudFolderSettingsClick: (() -> Unit)? = null,
    onIncomingCloudFolderClick: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val folderStatsByUri = remember(allRecentFiles) {
        allRecentFiles.asSequence()
            .filter { it.sourceFolderUri != null }
            .groupBy { it.sourceFolderUri!! }
            .mapValues { (_, files) ->
                com.aryan.reader.shared.ui.SharedAndroidFolderStats(
                    totalBooks = files.size,
                    countsByType = files.groupingBy { it.type }.eachCount(),
                )
            }
    }
    com.aryan.reader.shared.ui.SharedAndroidFolderSyncScreen(
        folders = syncedFolders,
        statsByFolderUri = folderStatsByUri,
        syncableFileTypes = ANDROID_SYNCABLE_FILE_TYPES.toList(),
        isLoading = isLoading,
        strings = com.aryan.reader.shared.ui.SharedAndroidFolderSyncStrings(
            addFolder = stringResource(R.string.fab_add_folder),
            addDescription = stringResource(R.string.action_add),
            scanning = stringResource(R.string.scanning),
            scanAll = stringResource(R.string.scan_all),
            syncMetadata = stringResource(R.string.sync_meta),
            emptyTitle = stringResource(R.string.sync_local_folders),
            emptyMessage = stringResource(R.string.sync_folders_desc),
            selectFolder = stringResource(R.string.action_select_folder),
            localSyncDisabled = stringResource(R.string.folder_local_sync_disabled),
            optionsDescription = stringResource(R.string.content_desc_options),
            editFilters = stringResource(R.string.menu_edit_filters),
            disableLocalSync = stringResource(R.string.menu_disable_folder_local_sync),
            enableLocalSync = stringResource(R.string.menu_enable_folder_local_sync),
            removeFolder = stringResource(R.string.menu_remove_folder),
            lastSync = stringResource(R.string.last_sync),
            never = stringResource(R.string.never),
            booksCount = stringResource(R.string.books_count),
            filterCount = { type, count -> context.getString(R.string.folder_filter_count, type.name, count) },
            filterFileTypes = stringResource(R.string.filter_file_types),
            filterFileTypesDescription = stringResource(R.string.filter_file_types_desc),
            save = stringResource(R.string.action_save),
            cancel = stringResource(R.string.action_cancel),
            disableDialogTitle = stringResource(R.string.dialog_disable_folder_local_sync_title),
            disableDialogDescription = stringResource(R.string.dialog_disable_folder_local_sync_desc, LOCAL_FOLDER_SYNC_DATA_DIR),
            disableRemoveData = stringResource(R.string.action_disable_remove_sync_data),
            disableKeepData = stringResource(R.string.action_disable_keep_sync_data),
            cloudSettings = stringResource(R.string.folder_sync_settings_title),
            cloudSyncOn = stringResource(R.string.folder_sync_cloud_backup_on),
            cloudSyncOff = stringResource(R.string.folder_sync_cloud_sync_off),
            cloudDeviceOnly = stringResource(R.string.folder_sync_device_only),
            cloudDownloaded = stringResource(R.string.folder_sync_cloud_downloaded),
            cloudAvailable = stringResource(R.string.folder_sync_cloud_available),
            cloudChooseAction = stringResource(R.string.folder_sync_cloud_choose_action),
        ),
        onAddFolder = onAddFolderClick,
        onRemoveFolder = onRemoveFolderClick,
        onLocalSyncChange = onFolderLocalSyncChange,
        onFileTypesChange = onEditFolderFiltersClick,
        onScanAll = onScanNowClick,
        onSyncMetadata = onSyncMetadataClick,
        formatLastScan = { dateFormat.format(Date(it)) },
        syncIcon = { Icon(painterResource(R.drawable.sync), null, Modifier.size(18.dp)) },
        cloudFolderSelection = cloudFolderSelection,
        cloudSyncEnabled = cloudSyncEnabled,
        isProUser = isProUser,
        onCloudFolderSettingsClick = onCloudFolderSettingsClick,
        onOpenIncomingCloudFolder = onIncomingCloudFolderClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFilterSheet(
    filters: LibraryFilters,
    allTags: List<TagEntity>,
    syncedFolders: List<SyncedFolder>,
    onApply: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit
) {
    SharedMobileLibraryFilterDialog(
        filters = filters,
        allTags = allTags.map { Tag(it.id, it.name, it.color) },
        syncedFolders = syncedFolders,
        readableFileTypes = ANDROID_READABLE_FILE_TYPES,
        fileTypeLabels = ANDROID_READABLE_FILE_TYPES.associateWith { it.name },
        readStatusLabels = ReadStatusFilter.entries.associateWith { stringResource(it.labelRes) },
        labels = SharedMobileLibraryFilterLabels(
            title = stringResource(R.string.filter_library),
            fileType = stringResource(R.string.filter_file_type),
            sourceFolder = stringResource(R.string.filter_source_folder),
            inAppStorage = stringResource(R.string.filter_in_app_storage),
            readStatus = stringResource(R.string.filter_read_status),
            tags = stringResource(R.string.section_tags),
            clearAll = stringResource(R.string.clear_all),
            apply = stringResource(R.string.action_apply),
        ),
        onApply = onApply,
        onDismiss = onDismiss,
    )
}

@Composable
fun OpdsTab(
    localLibraryFiles: List<RecentFileItem>,
    onBookDownloaded: (Uri, String) -> Unit,
    onReadBook: (RecentFileItem) -> Unit,
    onStreamBook: (OpdsEntry, OpdsCatalog?) -> Unit,
    onDeleteCatalogStreams: (String) -> Unit,
    onShowBanner: (String) -> Unit,
    syncedFolders: List<SyncedFolder>,
    opdsViewModel: OpdsViewModel = viewModel()
) {
    val uiState by opdsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coverImageLoader = rememberOpdsCoverImageLoader(uiState.currentCatalog)
    val sharedLibraryBooks = remember(localLibraryFiles) {
        localLibraryFiles.map(RecentFileItem::toSharedBookItem)
    }

    BackHandler(enabled = uiState.isViewingCatalog) {
        opdsViewModel.navigateBack()
    }

    com.aryan.reader.shared.ui.SharedOpdsScreen(
        state = uiState,
        localLibraryBooks = sharedLibraryBooks,
        onOpenCatalog = opdsViewModel::openCatalog,
        onOpenFeedUrl = opdsViewModel::openFeedUrl,
        onNavigateBack = opdsViewModel::navigateBack,
        onSearch = opdsViewModel::search,
        onLoadNextPage = opdsViewModel::loadNextPage,
        onAddCatalog = opdsViewModel::addCatalog,
        onUpdateCatalog = opdsViewModel::updateCatalog,
        onRemoveCatalog = { opdsViewModel.removeCatalog(it.id) },
        onDeleteCatalogStreams = onDeleteCatalogStreams,
        onDownloadBook = { entry, acquisition ->
            opdsViewModel.downloadBook(
                entry, acquisition, context,
                onDownloaded = { downloadedUri ->
                    onBookDownloaded(downloadedUri, entry.title)
                },
                onDownloadedToFolder = { folderName ->
                    onShowBanner(context.getString(R.string.banner_downloaded_to_folder, entry.title, folderName))
                }
            )
        },
        onDownloadLocationChange = opdsViewModel::setDownloadLocation,
        syncedFolders = syncedFolders,
        onReadBook = { sharedBook ->
            localLibraryFiles.firstOrNull { it.bookId == sharedBook.id }?.let(onReadBook)
        },
        onStreamBook = onStreamBook,
        onClearError = opdsViewModel::clearError,
        coverContent = { entry, modifier ->
            AsyncImage(
                model = entry.coverUrl,
                contentDescription = null,
                imageLoader = coverImageLoader,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        },
        mobileLayout = true,
    )
}

@Composable
private fun rememberOpdsCoverImageLoader(catalog: OpdsCatalog?): ImageLoader {
    val context = LocalContext.current.applicationContext
    val username = catalog?.username
    val password = catalog?.password
    val imageLoader = remember(context, username, password) {
        ImageLoader.Builder(context)
            .okHttpClient {
                OpdsRepository.sharedHttpClient.newBuilder()
                    .authenticator(OpdsRepository.OpdsAuthenticator(username, password))
                    .build()
            }
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
    DisposableEffect(imageLoader) {
        onDispose { imageLoader.shutdown() }
    }
    return imageLoader
}
