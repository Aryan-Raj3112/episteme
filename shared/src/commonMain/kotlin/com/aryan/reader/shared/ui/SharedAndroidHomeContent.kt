package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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

/**
 * Compact open-reader tabs strip with per-tab and close-all affordances.
 *
 * Rendered above the library grid so a reader opened in multi-tab mode stays visible and
 * dismissable from the library surface that replaced the standalone Home screen.
 *
 * Deliberately a single short row with no header: the retired layout stacked a
 * `titleLarge` label plus a 48dp close button above the chips (~120dp before the
 * Continue Reading card). Now the scrollable chips and a fixed close-all share one line
 * (~44dp), and a lone open tab is hidden because the Continue Reading card already
 * surfaces that book.
 */
@Composable
fun <T> SharedAndroidActiveTabsRow(
    openTabs: List<T>,
    tabsEnabled: Boolean,
    closeAllTabsDescription: String,
    closeTabDescription: String,
    itemKey: (T) -> String,
    itemTitle: (T) -> String,
    onItemClick: (T) -> Unit,
    onCloseTab: (T) -> Unit,
    onCloseAllTabs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!tabsEnabled || openTabs.size < 2) return
    Row(
        modifier = modifier.fillMaxWidth().testTag("ActiveTabsRow").padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(openTabs, key = { "tab_${itemKey(it)}" }) { tab ->
                CompactTabChip(
                    title = itemTitle(tab),
                    closeDescription = closeTabDescription,
                    onClick = { onItemClick(tab) },
                    onClose = { onCloseTab(tab) },
                    modifier = Modifier.testTag("LibraryTab_${itemKey(tab)}"),
                    closeModifier = Modifier.testTag("LibraryTabClose_${itemKey(tab)}"),
                )
            }
        }
        // Fixed at the trailing edge so a long tab list cannot scroll it out of reach.
        // A plain clickable box keeps the touch target at 32dp: IconButton's 48dp minimum
        // would expand over the last chip and swallow taps meant to open it.
        Box(
            modifier = Modifier
                .size(32.dp)
                .testTag("CloseAllTabs")
                .clickable(role = Role.Button, onClick = onCloseAllTabs),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = closeAllTabsDescription,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 32dp pill keeps one-tap switching and per-tab closing without the chip's extra height. */
@Composable
private fun CompactTabChip(
    title: String,
    closeDescription: String,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeModifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(32.dp).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // A minimum label width keeps the close target clear of the chip's
                // centre, so a short title never closes itself when tapped.
                modifier = Modifier.widthIn(min = 48.dp, max = 132.dp),
            )
            Box(
                modifier = closeModifier
                    .size(24.dp)
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, closeDescription, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Exact Android active-tabs header and recent-books grid; Android supplies its existing cards. */
@Composable
fun <T> SharedAndroidHomeRecentGrid(
    recentItems: List<T>,
    openTabs: List<T>,
    tabsEnabled: Boolean,
    widthClass: SharedAndroidHomeWidthClass,
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
    val gridCells = widthClass.bookGridCells()
    val showTabs = tabsEnabled && openTabs.size >= 2
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        SharedAndroidActiveTabsRow(
            openTabs = openTabs,
            tabsEnabled = tabsEnabled,
            closeAllTabsDescription = closeAllTabsDescription,
            closeTabDescription = closeTabDescription,
            itemKey = itemKey,
            itemTitle = itemTitle,
            onItemClick = onItemClick,
            onCloseTab = onCloseTab,
            onCloseAllTabs = onCloseAllTabs,
        )
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
