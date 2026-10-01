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
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.aryan.reader.shared.SharedLibraryEditor
import com.aryan.reader.shared.Shelf as SharedShelf
import com.aryan.reader.shared.ui.SharedAddToShelfDialog
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
    // Library Beta is the only main destination; the bottom navigation bar was removed.
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        SharedMobileMainScaffold { innerPadding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                UnifiedLibraryScreen(
                    viewModel = viewModel,
                    navController = navController,
                    widthSizeClass = windowSizeClass.widthSizeClass
                )
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
                    viewModel.showCreateShelfDialogForSelectedBooks(selectedBookIds)
                },
                onShelvesSelected = { shelfIds ->
                    viewModel.addSelectedBooksToShelves(shelfIds, uiState.showAddSelectedToShelfDialogFor)
                }
            )
        }
    }
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
