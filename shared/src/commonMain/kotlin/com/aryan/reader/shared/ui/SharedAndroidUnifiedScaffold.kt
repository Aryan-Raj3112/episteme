package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedAndroidUnifiedScaffold(
    section: MobileUnifiedLibrarySection,
    showingShelf: Boolean,
    importDescription: String,
    addAudiobookDescription: String,
    newShelfLabel: String,
    onImport: () -> Unit,
    onAddAudiobook: () -> Unit,
    onNewShelf: () -> Unit,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    sectionContent: @Composable (MobileUnifiedLibrarySection, PaddingValues) -> Unit,
    showFloatingActionButton: Boolean = true,
    /** Pull-to-sync is only offered when a sync mechanism can do real work. */
    canPullToSync: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        // The top bar draws its own status-bar padding, so only the top inset is zeroed.
        // The bottom inset must stay live: Material3 offsets the FAB by it solely when no
        // bottom bar is present, which is what keeps the FAB clear of the system navigation
        // bar while the optional audiobook mini-player is hidden.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Bottom),
        bottomBar = bottomBar,
        topBar = topBar,
        floatingActionButton = if (showFloatingActionButton) {
            {
                when (section) {
                    MobileUnifiedLibrarySection.HOME -> FloatingActionButton(onClick = onImport, modifier = Modifier.testTag("UnifiedLibraryImport")) {
                        Icon(Icons.Default.Add, importDescription)
                    }
                    MobileUnifiedLibrarySection.AUDIOBOOKS -> FloatingActionButton(onClick = onAddAudiobook, modifier = Modifier.testTag("UnifiedLibraryAddAudiobook")) {
                        Icon(Icons.Default.Add, addAudiobookDescription)
                    }
                    MobileUnifiedLibrarySection.SHELVES -> if (!showingShelf) {
                        ExtendedFloatingActionButton(
                            onClick = onNewShelf,
                            modifier = Modifier.testTag("UnifiedLibraryNewShelf"),
                            icon = { Icon(Icons.Default.Add, null) },
                            text = { Text(newShelfLabel) },
                        )
                    }
                    MobileUnifiedLibrarySection.FOLDERS,
                    MobileUnifiedLibrarySection.CATALOGS -> Unit
                }
            }
        } else {
            { }
        },
    ) { padding ->
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                val direction = if (targetState.persistedValue > initialState.persistedValue) 1 else -1
                (fadeIn() + slideInHorizontally { direction * it / 5 }) togetherWith
                    (fadeOut() + slideOutHorizontally { -direction * it / 5 })
            },
            label = "UnifiedLibrarySharedAxis",
        ) { displayedSection ->
            if (canPullToSync) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize().testTag("UnifiedLibraryPullToRefresh"),
                ) { sectionContent(displayedSection, padding) }
            } else {
                sectionContent(displayedSection, padding)
            }
        }
    }
}
