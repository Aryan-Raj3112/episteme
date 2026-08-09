package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

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
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = bottomBar,
        topBar = topBar,
        floatingActionButton = {
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
        ) { displayedSection -> sectionContent(displayedSection, padding) }
    }
}
