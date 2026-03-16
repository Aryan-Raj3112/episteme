/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
// HomeScreen
@file:Suppress("DEPRECATION")

package com.aryan.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aryan.reader.data.RecentFileItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel, windowSizeClass: WindowSizeClass, navController: NavHostController
) {
    val context = LocalContext.current
    val customTabUriHandler = remember { CustomTabUriHandler(context) }

    CompositionLocalProvider(LocalUriHandler provides customTabUriHandler) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val recentFilesForHome = uiState.recentFiles.filter { it.isRecent }
        val selectedContextItems = uiState.contextualActionItems
        val isContextualModeActive = selectedContextItems.isNotEmpty()
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val snackbarHostState = remember { SnackbarHostState() }
        val deviceLimitState = uiState.deviceLimitState

        var showDeleteConfirmDialog by remember { mutableStateOf(false) }
        var showClearCloudDataDialog by remember { mutableStateOf(false) }
        var showClearAllDataDialog by remember { mutableStateOf(false) }
        var showUpgradeDialog by remember { mutableStateOf(false) }
        var showSignOutConfirmDialog by remember { mutableStateOf(false) }
        var showAboutDialog by remember { mutableStateOf(false) }

        val feedbackResult =
            navController.currentBackStackEntry?.savedStateHandle?.getLiveData<String>("banner_message")
                ?.observeAsState()

        LaunchedEffect(feedbackResult) {
            feedbackResult?.value?.let { message ->
                viewModel.showBanner(message)
                navController.currentBackStackEntry?.savedStateHandle?.remove<String>("banner_message")
            }
        }

        val drivePermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onDrivePermissionResult(result.data)
            } else {
                Timber.w("Google Sign In for Drive failed with result code: ${result.resultCode}")
                viewModel.onDrivePermissionFlowCancelled()
            }
        }

        LaunchedEffect(uiState.isRequestingDrivePermission) {
            if (uiState.isRequestingDrivePermission) {
                val intent = viewModel.getDriveSignInIntent(context)
                drivePermissionLauncher.launch(intent)
            }
        }

        LaunchedEffect(uiState.bannerMessage) {
            uiState.bannerMessage?.let { msg ->
                if (!msg.isPersistent) {
                    delay(3000L)
                    viewModel.bannerMessageShown()
                }
            }
        }

        LaunchedEffect(uiState.errorMessage) {
            uiState.errorMessage?.let { message ->
                snackbarHostState.showSnackbar(message)
                viewModel.errorMessageShown()
            }
        }

        BackHandler(enabled = isContextualModeActive) {
            Timber.d("System back pressed during contextual mode.")
            viewModel.clearContextualAction()
        }

        val pickFileLauncher = rememberFilePickerLauncher { uris ->
            if (isContextualModeActive) {
                viewModel.clearContextualAction()
            }
            uris.forEach { uri ->
                viewModel.onFileSelected(uri, isFromRecent = false)
            }
        }

        val fallbackFilePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents()
        ) { uris ->
            if (isContextualModeActive) {
                viewModel.clearContextualAction()
            }
            uris.forEach { uri ->
                viewModel.onFileSelected(uri, isFromRecent = false)
            }
        }

        val onSelectFileClick = {
            if (isContextualModeActive) {
                viewModel.clearContextualAction()
            }
            try {
                pickFileLauncher.launch(arrayOf("*/*"))
            } catch (_: android.content.ActivityNotFoundException) {
                Timber.w("OpenDocument picker failed. Falling back to GetMultipleContents.")
                try {
                    fallbackFilePickerLauncher.launch("*/*")
                } catch (_: android.content.ActivityNotFoundException) {
                    viewModel.showBanner("No file manager found. Please install a file manager app.", isError = true)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState, drawerContent = {
                    val context = LocalContext.current
                    AppDrawerContent(
                        uiState = uiState,
                        onSignInClick = {
                            scope.launch {
                                context.findActivity()?.let { activity ->
                                    viewModel.signIn(activity)
                                }
                                drawerState.close()
                            }
                        },
                        onSignOutClick = {
                            showSignOutConfirmDialog = true
                        },
                        onSyncToggle = viewModel::setSyncEnabled,
                        onUpgradeClick = {
                            scope.launch {
                                drawerState.close()
                                navController.navigate(AppDestinations.PRO_SCREEN_ROUTE)
                            }
                        },
                        onSyncUpsellClick = {
                            scope.launch {
                                showUpgradeDialog = true
                            }
                        },
                        onFontsClick = {
                            scope.launch {
                                drawerState.close()
                                navController.navigate(AppDestinations.FONTS_SCREEN_ROUTE)
                            }
                        },
                        navController = navController,
                        onFolderSyncToggle = viewModel::setFolderSyncEnabled
                    )
                }) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        if (!isContextualModeActive) {
                            DefaultTopAppBar(
                                uiState = uiState,
                                onRenderModeChange = viewModel::setRenderMode,
                                onClearCache = viewModel::clearBookCache,
                                onClearCloudData = { showClearAllDataDialog = true },
                                onAboutClick = { showAboutDialog = true },
                                onDrawerClick = {
                                    scope.launch {
                                        drawerState.open()
                                    }
                                },
                                onShowDeviceManagement = viewModel::showDeviceManagementForDebug,
                                onFolderSyncToggle = viewModel::setFolderSyncEnabled,
                                onClearReflowCache = viewModel::clearReflowCache
                            )
                        } else {
                            ContextualTopAppBar(
                                selectedItemCount = selectedContextItems.size,
                                onNavIconClick = { viewModel.clearContextualAction() },
                                onPinClick = { viewModel.togglePinForContextualItems(isHome = true) },
                                onDeleteClick = { showDeleteConfirmDialog = true },
                                onSelectAllClick = { viewModel.selectAllRecentFiles() })
                        }
                    }) { paddingValues ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        ) {
                            if (recentFilesForHome.isEmpty()) {
                                if (uiState.recentFiles.isEmpty()) {
                                    EmptyState(
                                        title = "Your Library is Empty",
                                        message = "Select a file to read, or sync a local folder to automatically import books.",
                                        onSelectFileClick = onSelectFileClick,
                                        modifier = Modifier.weight(1f),
                                        secondaryButtonText = "Setup Folder Sync",
                                        onSecondaryClick = { viewModel.navigateToFolderSync() }
                                    )
                                } else {
                                    EmptyState(
                                        title = "No Recent Files",
                                        message = "Open a file from your library to see it here.",
                                        onSelectFileClick = onSelectFileClick,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                RecentFilesContent(
                                    recentFiles = recentFilesForHome,
                                    selectedContextItems = selectedContextItems,
                                    pinnedHomeBookIds = uiState.pinnedHomeBookIds,
                                    onItemClick = { item -> viewModel.onRecentFileClicked(item) },
                                    onItemLongClick = { item -> viewModel.onRecentItemLongPress(item) },
                                    onSelectFileClick = onSelectFileClick,
                                    onNavigateToFolderSync = { viewModel.navigateToFolderSync() },
                                    windowSizeClass = windowSizeClass,
                                    downloadingBookIds = uiState.downloadingBookIds,
                                    onRefresh = { viewModel.refreshLibrary() },
                                    isRefreshing = uiState.isRefreshing,
                                    isSyncEnabled = uiState.isSyncEnabled,
                                    hasSyncedFolder = uiState.syncedFolders.isNotEmpty()
                                )
                            }
                        }

                        if (uiState.isLoading) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }

                    // Dialogs
                    if (showDeleteConfirmDialog) {
                        DeleteConfirmationDialog(count = selectedContextItems.size, onConfirm = {
                            viewModel.hideItemsFromRecentsView()
                            showDeleteConfirmDialog = false
                        }, onDismiss = { showDeleteConfirmDialog = false })
                    }

                    if (showClearCloudDataDialog) {
                        ClearCloudDataConfirmationDialog(onConfirm = {
                            viewModel.deleteAllUserData()
                            showClearCloudDataDialog = false
                        }, onDismiss = { showClearCloudDataDialog = false })
                    }

                    if (showUpgradeDialog) {
                        UpgradeDialog(onDismiss = { showUpgradeDialog = false }, onConfirm = {
                            showUpgradeDialog = false
                            navController.navigate(AppDestinations.PRO_SCREEN_ROUTE)
                        })
                    }
                }
            }
            if (showAboutDialog) {
                AboutDialog(onDismiss = { showAboutDialog = false })
            }
            if (showClearAllDataDialog) {
                ClearAllDataConfirmationDialog(onConfirm = {
                    viewModel.deleteAllCloudAndLocalData()
                    showClearAllDataDialog = false
                }, onDismiss = { showClearAllDataDialog = false })
            }
            if (showSignOutConfirmDialog) {
                SignOutConfirmationDialog(onConfirm = {
                    viewModel.signOut()
                    showSignOutConfirmDialog = false
                }, onDismiss = {
                    showSignOutConfirmDialog = false
                })
            }
            if (deviceLimitState.isLimitReached) {
                DeviceManagementScreen(
                    devices = deviceLimitState.registeredDevices,
                    onRemoveDevice = { deviceId -> viewModel.replaceDevice(deviceId) },
                    isReplacing = uiState.isReplacingDevice
                )
            }
            CustomTopBanner(bannerMessage = uiState.bannerMessage)

            if (BuildConfig.DEBUG) {
                FpsMonitor(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 48.dp, start = 8.dp)
                )
            }
        }
        if (uiState.showFolderMigrationDialog) {
            FolderMigrationDialog(
                onConfirm = { viewModel.completeFolderMigration() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentFilesContent(
    recentFiles: List<RecentFileItem>,
    selectedContextItems: Collection<RecentFileItem>,
    pinnedHomeBookIds: Set<String>,
    onItemClick: (RecentFileItem) -> Unit,
    onItemLongClick: (RecentFileItem) -> Unit,
    onSelectFileClick: () -> Unit,
    onNavigateToFolderSync: () -> Unit,
    windowSizeClass: WindowSizeClass,
    downloadingBookIds: Set<String>,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    isSyncEnabled: Boolean,
    hasSyncedFolder: Boolean
) {
    val canRefresh = isSyncEnabled || hasSyncedFolder

    val content = @Composable {
        Box(modifier = Modifier.fillMaxSize()) {
            RecentFilesGrid(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                recentFiles = recentFiles,
                selectedItemUris = selectedContextItems.mapNotNull { it.uriString }.toSet(),
                pinnedHomeBookIds = pinnedHomeBookIds,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                windowSizeClass = windowSizeClass,
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                downloadingBookIds = downloadingBookIds
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Button(onClick = onSelectFileClick) {
                    Text("Select File")
                }
                androidx.compose.material3.Button(onClick = onNavigateToFolderSync) {
                    Text("Sync Folder")
                }
            }
        }
    }

    if (canRefresh) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun RecentFilesGrid(
    modifier: Modifier = Modifier,
    recentFiles: List<RecentFileItem>,
    pinnedHomeBookIds: Set<String>,
    selectedItemUris: Set<String>,
    onItemClick: (RecentFileItem) -> Unit,
    onItemLongClick: (RecentFileItem) -> Unit,
    windowSizeClass: WindowSizeClass,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    downloadingBookIds: Set<String>,
) {
    val gridCells = when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> GridCells.Fixed(3)
        WindowWidthSizeClass.Medium -> GridCells.Adaptive(minSize = 140.dp)
        else -> GridCells.Adaptive(minSize = 160.dp)
    }

    Column(modifier = modifier) {
        Text(
            text = "Recent Files",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp, top = 24.dp)
        )
        LazyVerticalGrid(
            columns = gridCells,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(recentFiles, key = { it.bookId }) { item ->
                RecentFileCard(
                    item = item,
                    isSelected = item.uriString in selectedItemUris,
                    isPinned = item.bookId in pinnedHomeBookIds,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                    isDownloading = item.bookId in downloadingBookIds
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentFileCard(
    item: RecentFileItem,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isDownloading: Boolean,
) {
    val context = LocalContext.current
    val placeholder = when (item.type) {
        FileType.PDF -> R.drawable.pdf_placeholder
        FileType.EPUB, FileType.MOBI, FileType.MD, FileType.TXT, FileType.HTML -> R.drawable.epub_placeholder
    }
    val imageModel = remember(item.coverImagePath) {
        item.coverImagePath?.let { File(it) } ?: placeholder
    }

    Surface(
        modifier = modifier.graphicsLayer { alpha = if (item.isAvailable) 1.0f else 0.8f },
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (isSelected) 8.dp else 2.dp,
        shadowElevation = 4.dp,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.combinedClickable(
                onClick = onClick, onLongClick = onLongClick
            )
        ) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(imageModel).error(placeholder)
                        .fallback(placeholder).crossfade(true).build(),
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(160.dp)
                        .fillMaxWidth(),
                )

                if (item.sourceFolderUri != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape
                            )
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Local Folder",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                if (isPinned) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            )
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (!item.isAvailable) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(color = Color.White)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Not available locally",
                                modifier = Modifier.size(48.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if ((item.type == FileType.EPUB || item.type == FileType.MOBI) && !item.title.isNullOrBlank()) {
                        item.title
                    } else {
                        item.displayName
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center
                ) {
                    item.progressPercentage?.let { progress ->
                        Text(
                            text = "${progress.toInt()}% complete",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Suppress("unused", "KotlinConstantConditions")
@Composable
fun DefaultTopAppBar(
    uiState: ReaderScreenState,
    onRenderModeChange: (RenderMode) -> Unit,
    onClearCache: () -> Unit,
    onClearCloudData: () -> Unit,
    onClearReflowCache: () -> Unit, // Add this parameter
    onDrawerClick: () -> Unit,
    onAboutClick: () -> Unit,
    onShowDeviceManagement: () -> Unit,
    onFolderSyncToggle: (Boolean) -> Unit
) {
    var showOptionsMenu by remember { mutableStateOf(false) }

    CustomTopAppBar(title = { }, navigationIcon = {
        IconButton(onClick = onDrawerClick) {
            BadgedBox(
                badge = {
                    if (uiState.hasUnreadFeedback) {
                        Badge()
                    }
                }) {
                Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
            }
        }
    }, actions = {
        // Options Menu (MoreVert)
        Box {
            IconButton(onClick = { showOptionsMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
            }
            DropdownMenu(
                expanded = showOptionsMenu, onDismissRequest = { showOptionsMenu = false }) {
                DropdownMenuItem(text = { Text("About") }, onClick = {
                    onAboutClick()
                    showOptionsMenu = false
                })

                if (BuildConfig.DEBUG && BuildConfig.FLAVOR != "oss") {
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("[Debug] Show Device Management") }, onClick = {
                        onShowDeviceManagement()
                        showOptionsMenu = false
                    })
                    DropdownMenuItem(text = { Text("[Debug] Clear Book Cache") }, onClick = {
                        onClearCache()
                        showOptionsMenu = false
                    })
                    DropdownMenuItem(text = { Text("[Debug] Clear Reflow Cache") }, onClick = {
                        onClearReflowCache()
                        showOptionsMenu = false
                    })
                    DropdownMenuItem(
                        text = { Text("[Debug] Clear Cloud & Local Data") },
                        onClick = {
                            onClearCloudData()
                            showOptionsMenu = false
                        })
                }
            }
        }
    })
}

@Suppress("KotlinConstantConditions")
@Composable
private fun AppDrawerContent(
    uiState: ReaderScreenState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onSyncToggle: (Boolean) -> Unit,
    onUpgradeClick: () -> Unit,
    onSyncUpsellClick: () -> Unit,
    onFontsClick: () -> Unit,
    navController: NavHostController,
    onFolderSyncToggle: (Boolean) -> Unit
) {
    val isOss = BuildConfig.FLAVOR == "oss"

    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxHeight()) {
            if (!isOss) {
                if (uiState.currentUser != null) {
                    // Signed-in: Show user info at the top
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val photoUrl = uiState.currentUser.photoUrl
                        if (photoUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(photoUrl)
                                    .crossfade(true).build(),
                                contentDescription = "Profile picture",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = "Profile",
                                modifier = Modifier.size(80.dp)
                            )
                        }
                        uiState.currentUser.displayName?.let { name ->
                            Text(text = name, style = MaterialTheme.typography.titleMedium)
                        }
                        uiState.currentUser.email?.let { email ->
                            Text(text = email, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    // Signed-out: Show Sign In button at the top
                    Spacer(modifier = Modifier.height(8.dp))
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                Icons.Outlined.AccountCircle, contentDescription = "Sign In"
                            )
                        },
                        label = { Text("Sign in with Google") },
                        selected = false,
                        onClick = onSignInClick,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    // LegalText
                    LegalText(
                        prefixText = "By signing in,",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        textAlign = TextAlign.Start
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Spacer(modifier = Modifier.height(16.dp))

                NavigationDrawerItem(
                    icon = {
                        Icon(
                            Icons.Default.VerifiedUser, contentDescription = "Episteme Pro"
                        )
                    },
                    label = {
                        val text =
                            if (uiState.isProUser) "Episteme Pro" else "Upgrade to Episteme Pro"
                        Text(text)
                    },
                    selected = false,
                    onClick = onUpgradeClick,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                // Sync Toggle Item
                if (uiState.currentUser != null) {
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.sync),
                                contentDescription = "Sync Library"
                            )
                        }, label = { Text("Sync Library") }, badge = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!uiState.isProUser) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = "Pro Feature",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Switch(
                                    checked = uiState.isSyncEnabled, onCheckedChange = {
                                        if (uiState.isProUser) onSyncToggle(it) else onSyncUpsellClick()
                                    }, enabled = uiState.isProUser
                                )
                            }
                        }, selected = false, onClick = {
                            if (uiState.isProUser) {
                                onSyncToggle(!uiState.isSyncEnabled)
                            } else {
                                onSyncUpsellClick()
                            }
                        }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                if (uiState.currentUser != null && uiState.isSyncEnabled) {
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = "Backup Local Folders"
                            )
                        },
                        label = {
                            Column {
                                Text("Cloud sync for Local Folders")
                                Text(
                                    "Upload books from your synced folders to Google Drive).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        badge = {
                            Switch(
                                checked = uiState.isFolderSyncEnabled,
                                onCheckedChange = { onFolderSyncToggle(it) }
                            )
                        },
                        selected = false,
                        onClick = { onFolderSyncToggle(!uiState.isFolderSyncEnabled) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            } else {
                // OSS Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = R.mipmap.ic_launcher,
                        contentDescription = "App Icon",
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Episteme OSS", style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            NavigationDrawerItem(
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.fonts),
                        contentDescription = "Custom Fonts"
                    )
                },
                label = { Text("Custom Fonts") },
                selected = false,
                onClick = onFontsClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            if (!isOss) {
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.feedback),
                            contentDescription = "Feedback"
                        )
                    },
                    label = { Text("Help & Feedback") },
                    badge = {
                        if (uiState.hasUnreadFeedback) {
                            Badge()
                        }
                    },
                    selected = false,
                    onClick = { navController.navigate("feedback_screen_route") },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                if (uiState.currentUser != null) {
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.logout),
                                contentDescription = "Sign Out"
                            )
                        },
                        label = { Text("Sign Out") },
                        selected = false,
                        onClick = onSignOutClick,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // legal links
            if (uiState.currentUser != null && !isOss) {
                val uriHandler = LocalUriHandler.current
                val baseStyle = MaterialTheme.typography.labelMedium
                var scaledTextStyle by remember { mutableStateOf(baseStyle) }

                Text(
                    text = "Privacy Policy  •  Terms of Service  •  Licenses",
                    style = scaledTextStyle,
                    maxLines = 1,
                    softWrap = false,
                    onTextLayout = {
                        if (it.didOverflowWidth) {
                            scaledTextStyle =
                                scaledTextStyle.copy(fontSize = scaledTextStyle.fontSize * 0.95)
                        }
                    },
                    modifier = Modifier.height(0.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Privacy Policy",
                        style = scaledTextStyle.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.clickable { uriHandler.openUri(PRIVACY_POLICY_URL) },
                        softWrap = false
                    )
                    Text(
                        "  •  ",
                        style = scaledTextStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        softWrap = false
                    )
                    Text(
                        text = "Terms of Service",
                        style = scaledTextStyle.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.clickable { uriHandler.openUri(TERMS_URL) },
                        softWrap = false
                    )
                    Text(
                        "  •  ",
                        style = scaledTextStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        softWrap = false
                    )
                    Text(
                        text = "Licenses",
                        style = scaledTextStyle.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.clickable { uriHandler.openUri(LICENSES_URL) },
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
fun UpgradeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
        title = { Text("Unlock Episteme Pro") },
        text = { Text("Sync across devices is a Pro feature. Unlock all pro features with a single, one-time purchase.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Upgrade") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        })
}

@Composable
fun SignOutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Sign Out") },
        text = { Text("Are you sure you want to sign out?") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Sign Out")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        })
}

@Composable
fun DeviceManagementScreen(
    devices: List<DeviceItem>, onRemoveDevice: (String) -> Unit, isReplacing: Boolean
) {
    val dateFormatter = remember {
        SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Device Limit Reached",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "To use Episteme Pro on this device, please remove one of your existing registered devices.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (isReplacing) {
                CircularProgressIndicator()
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices, key = { it.deviceId }) { device ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = "Device")
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.deviceName, fontWeight = FontWeight.SemiBold)
                                    device.lastSeen?.let {
                                        Text(
                                            "Last seen: ${dateFormatter.format(it)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TextButton(onClick = { onRemoveDevice(device.deviceId) }) {
                                    Text("Remove")
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
fun ClearAllDataConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        title = { Text("Confirm Destructive Action") },
        text = { Text("This will permanently delete all your books and reading progress from this device AND from your Google Drive account. This action cannot be undone. Are you sure?") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete Everything")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        })
}

@Composable
fun FpsMonitor(modifier: Modifier = Modifier) {
    var fps by remember { mutableLongStateOf(0L) }
    var lastFrameTime by remember { mutableLongStateOf(0L) }
    var frameCount by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { currentFrameTime ->
                frameCount++
                if (currentFrameTime - lastFrameTime >= 1_000_000_000L) {
                    fps = frameCount
                    frameCount = 0
                    lastFrameTime = currentFrameTime
                }
            }
        }
    }

    Text(
        text = "FPS: $fps",
        color = Color.Green,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(4.dp)
    )
}

@Composable
private fun FolderMigrationDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null) },
        title = { Text("Folder Sync Update") },
        text = {
            Column {
                Text(
                    "We've improved Folder Sync! Books are now read directly from your folder without duplicating files."
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "To keep your reading progress safe, your previously synced books have been converted to standard local books. You may see duplicates once the folder resyncs; you can safely delete the old copies at your convenience.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Got it")
            }
        }
    )
}