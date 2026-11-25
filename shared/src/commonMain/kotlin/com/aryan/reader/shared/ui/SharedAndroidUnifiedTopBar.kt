package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SharedAndroidUnifiedTopBar(
    title: String?,
    showingShelf: Boolean,
    drawerDescription: String,
    backToShelvesDescription: String,
    /**
     * Opens the navigation drawer. Null when navigation is already visible (the Duo
     * inner display's permanent rail), in which case no menu button is drawn.
     */
    onMenu: (() -> Unit)?,
    onBackFromShelf: () -> Unit,
    onAccount: () -> Unit,
    accountAvatar: @Composable () -> Unit,
    searchQuery: String? = null,
    searchPlaceholder: String = "",
    clearSearchDescription: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    /**
     * Inside-files search toggle. Rendered as a small labelled chip under the
     * search field, visible as soon as the field takes focus (and while a
     * query remains) so the option is discoverable before anything is typed.
     * Hosts without a content index leave the callback null and see no change.
     */
    searchInsideFiles: Boolean = false,
    onSearchInsideFilesChange: ((Boolean) -> Unit)? = null,
    searchInsideFilesLabel: String = "Search inside files",
    /**
     * Extra control between the search field and the profile avatar, e.g. the library's
     * open-tabs button. Hosts that draw nothing keep the bar unchanged.
     */
    trailingActions: @Composable () -> Unit = {},
    density: SharedAndroidHomeDensity = SharedAndroidHomeDensity.Standard,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var searchFocused by remember { mutableStateOf(false) }
    val showContentScopeToggle = onSearchInsideFilesChange != null &&
        searchQuery != null && !showingShelf && (searchFocused || searchQuery.isNotEmpty())
    // A foldable iPhone has no top status-bar inset while the status bar sits on the side
    // of the display, so without an explicit margin the bar's content touches the
    // display's rounded top-left corner. Apple's guidance is to keep the normal layout
    // margin on the side opposite the controls, and to leave room for the corner. This is
    // identity on every other device, where the status-bar inset already provides it.
    val duoDisplay = LocalSharedDeviceLayout.current.isDuoDevice
    val hasLeadingControl = showingShelf || onMenu != null
    val startPadding = if (duoDisplay && !hasLeadingControl) 20.dp else 8.dp
    val endPadding = if (duoDisplay) 16.dp else 8.dp
    val topPadding = if (duoDisplay) density.topBarTopPadding else 0.dp
    Surface(shadowElevation = 1.dp, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = startPadding, end = endPadding)
                .padding(top = topPadding)
        ) {
            Row(
                Modifier.fillMaxWidth().height(density.searchRowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            if (showingShelf) {
                IconButton(onClick = onBackFromShelf) { Icon(Icons.AutoMirrored.Filled.ArrowBack, backToShelvesDescription) }
            } else if (onMenu != null) {
                IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, drawerDescription) }
            }
            if (searchQuery != null && !showingShelf) {
                SharedSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = searchPlaceholder,
                    onSearch = { focusManager.clearFocus() },
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { searchFocused = it.isFocused }
                        .testTag("UnifiedLibrarySearch"),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty() || searchFocused) {
                            IconButton(onClick = {
                                if (searchQuery.isNotEmpty()) onSearchQueryChange("")
                                focusManager.clearFocus()
                            }) {
                                Icon(Icons.Default.Close, clearSearchDescription)
                            }
                        }
                    },
                )
            } else if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            trailingActions()
            IconButton(onClick = onAccount, modifier = Modifier.testTag("UnifiedLibraryProfile")) { accountAvatar() }
        }
            if (showContentScopeToggle) {
                val onToggle: (Boolean) -> Unit = onSearchInsideFilesChange
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = searchInsideFiles,
                        onClick = { onToggle(!searchInsideFiles) },
                        modifier = Modifier.testTag("UnifiedLibrarySearchInsideFiles"),
                        label = {
                            Text(
                                searchInsideFilesLabel,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
    }
}
