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
// MainScreen.kt
package com.aryan.reader

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.aryan.reader.shared.SharedLibraryEditor
import com.aryan.reader.shared.Shelf as SharedShelf
import com.aryan.reader.shared.ui.SharedAddToShelfDialog
import com.aryan.reader.shared.ui.SharedMobileMainDestination
import com.aryan.reader.shared.ui.SharedMobileMainScaffold

@OptIn(UnstableApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    windowSizeClass: WindowSizeClass,
    navController: NavHostController
) {
    val context = LocalContext.current

    SideEffect {
        val activity = context as? ComponentActivity
        activity?.enableEdgeToEdge()
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val viewingShelfName = uiState.viewingShelfId

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        if (viewingShelfName != null) {
            ShelfScreen(viewModel = viewModel)
        } else {
            val selectedDestination = SharedMobileMainDestination.fromPageIndex(uiState.mainScreenStartPage)

            SharedMobileMainScaffold(
                selectedDestination = selectedDestination,
                onDestinationSelected = { destination ->
                    val index = destination.ordinal
                    ReaderPerfLog.d("MainPager click page=$index route=${destination.androidRoute}")
                    if (selectedDestination != destination) {
                        val animStart = ReaderPerfLog.nowNanos()
                        viewModel.setMainScreenPage(index)
                        ReaderPerfLog.d("MainPager settled page=$index elapsed=${ReaderPerfLog.elapsedMs(animStart)}ms")
                    }
                },
                destinationIcon = { destination ->
                    val label = stringResource(destination.androidLabelRes)
                    Icon(painterResource(destination.androidIconRes), contentDescription = label)
                },
                destinationLabel = { destination -> Text(stringResource(destination.androidLabelRes)) }
            ) { innerPadding ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedDestination) {
                        SharedMobileMainDestination.HOME -> HomeScreen(
                            viewModel = viewModel,
                            windowSizeClass = windowSizeClass,
                            navController = navController
                        )
                        SharedMobileMainDestination.LIBRARY -> LibraryScreen(
                            viewModel = viewModel,
                            navController = navController
                        )
                        SharedMobileMainDestination.UNIFIED_LIBRARY -> UnifiedLibraryScreen(
                            viewModel = viewModel,
                            navController = navController
                        )
                    }
                }
            }
        }

        if (uiState.showTagSelectionDialogFor.isNotEmpty()) {
            TagSelectionBottomSheet(
                allTags = uiState.allTags,
                selectedBookIds = uiState.showTagSelectionDialogFor,
                booksWithTags = uiState.rawLibraryFiles,
                onCreateAndAssign = { name ->
                    viewModel.createAndAssignTag(name, uiState.showTagSelectionDialogFor)
                },
                onToggleTag = { tagId, assign ->
                    viewModel.toggleTagForBooks(tagId, uiState.showTagSelectionDialogFor, assign)
                },
                onDeleteTag = { tag -> viewModel.deleteTag(tag.id) },
                onDismiss = viewModel::closeTagSelection
            )
        }
        if (uiState.showAddSelectedToShelfDialogFor.isNotEmpty()) {
            SharedAddToShelfDialog(
                shelves = uiState.shelves
                    .filter { shelf -> shelf.type == ShelfType.MANUAL && SharedLibraryEditor.canMutateShelf(shelf.id) }
                    .map { shelf -> shelf.toSharedShelfForAddDialog() },
                onDismiss = viewModel::closeAddSelectedToShelf,
                onCreateShelf = {
                    val selectedBookIds = uiState.showAddSelectedToShelfDialogFor
                    viewModel.closeAddSelectedToShelf()
                    viewModel.setMainScreenPage(1)
                    viewModel.showCreateShelfDialogForSelectedBooks(selectedBookIds)
                },
                onShelvesSelected = { shelfIds ->
                    viewModel.addSelectedBooksToShelves(shelfIds, uiState.showAddSelectedToShelfDialogFor)
                }
            )
        }
    }
}

private val SharedMobileMainDestination.androidRoute: String
    get() = when (this) {
        SharedMobileMainDestination.HOME -> "home"
        SharedMobileMainDestination.LIBRARY -> "library"
        SharedMobileMainDestination.UNIFIED_LIBRARY -> "unified_library"
    }

private val SharedMobileMainDestination.androidLabelRes: Int
    get() = when (this) {
        SharedMobileMainDestination.HOME -> R.string.nav_home
        SharedMobileMainDestination.LIBRARY -> R.string.nav_library
        SharedMobileMainDestination.UNIFIED_LIBRARY -> R.string.nav_unified_library
    }

private val SharedMobileMainDestination.androidIconRes: Int
    get() = when (this) {
        SharedMobileMainDestination.HOME -> R.drawable.home
        SharedMobileMainDestination.LIBRARY,
        SharedMobileMainDestination.UNIFIED_LIBRARY -> R.drawable.library_books
    }

private fun Shelf.toSharedShelfForAddDialog(): SharedShelf {
    return SharedShelf(
        id = id,
        name = name,
        type = type,
        books = books.map { it.toSharedBookItem() },
        directBooks = directBooks.map { it.toSharedBookItem() },
        parentShelfId = parentShelfId,
        childShelfIds = childShelfIds,
        depth = depth,
        sortKey = sortKey
    )
}
