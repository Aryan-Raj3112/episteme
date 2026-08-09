package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Exact Android Home content state switch and blocking loading overlay. */
@Composable
fun SharedAndroidHomeBody(
    isHomeEmpty: Boolean,
    isLibraryEmpty: Boolean,
    isLoading: Boolean,
    contentPadding: PaddingValues,
    emptyLibrary: @Composable (Modifier) -> Unit,
    emptyRecents: @Composable (Modifier) -> Unit,
    recentContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(contentPadding)) {
            when {
                isHomeEmpty && isLibraryEmpty -> emptyLibrary(Modifier.weight(1f))
                isHomeEmpty -> emptyRecents(Modifier.weight(1f))
                else -> recentContent()
            }
        }
        if (isLoading) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

/** Android-parity shell around the Home recent-files grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedAndroidHomeRecentContent(
    canRefresh: Boolean,
    isRefreshing: Boolean,
    selectFileLabel: String,
    syncFolderLabel: String,
    onRefresh: () -> Unit,
    onSelectFile: () -> Unit,
    onSyncFolder: () -> Unit,
    recentGrid: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            recentGrid(Modifier.fillMaxSize())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onSelectFile) { Text(selectFileLabel) }
                Button(onClick = onSyncFolder) { Text(syncFolderLabel) }
            }
        }
    }

    if (canRefresh) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier.fillMaxSize(),
        ) { content() }
    } else {
        Box(modifier = modifier.fillMaxSize()) { content() }
    }
}

enum class SharedAndroidHomeWidthClass { COMPACT, MEDIUM, EXPANDED }

/** Exact Android active-tabs header and recent-books grid; Android supplies its existing cards. */
@Composable
fun <T> SharedAndroidHomeRecentGrid(
    recentItems: List<T>,
    openTabs: List<T>,
    tabsEnabled: Boolean,
    widthClass: SharedAndroidHomeWidthClass,
    activeTabsLabel: String,
    recentFilesLabel: String,
    closeAllTabsDescription: String,
    closeTabDescription: String,
    itemKey: (T) -> String,
    itemTitle: (T) -> String,
    onItemClick: (T) -> Unit,
    onCloseTab: (T) -> Unit,
    onCloseAllTabs: () -> Unit,
    recentCard: @Composable (T) -> Unit,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    modifier: Modifier = Modifier,
) {
    val gridCells = when (widthClass) {
        SharedAndroidHomeWidthClass.COMPACT -> GridCells.Fixed(3)
        SharedAndroidHomeWidthClass.MEDIUM -> GridCells.Adaptive(minSize = 140.dp)
        SharedAndroidHomeWidthClass.EXPANDED -> GridCells.Adaptive(minSize = 160.dp)
    }
    val showTabs = tabsEnabled && openTabs.isNotEmpty()
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        if (showTabs) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(activeTabsLabel, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onCloseAllTabs) {
                    Icon(Icons.Default.Close, closeAllTabsDescription, tint = MaterialTheme.colorScheme.error)
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
            ) {
                items(openTabs, key = { "tab_${itemKey(it)}" }) { tab ->
                    InputChip(
                        selected = false,
                        onClick = { onItemClick(tab) },
                        label = { Text(itemTitle(tab), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { onCloseTab(tab) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, closeTabDescription, modifier = Modifier.size(16.dp))
                            }
                        },
                        modifier = Modifier.testTag("HomeTab_${itemKey(tab)}"),
                    )
                }
            }
        }
        Text(
            recentFilesLabel,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp, top = if (showTabs) 8.dp else 24.dp),
        )
        LazyVerticalGrid(
            columns = gridCells,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(recentItems, key = itemKey) { recentCard(it) }
        }
    }
}
