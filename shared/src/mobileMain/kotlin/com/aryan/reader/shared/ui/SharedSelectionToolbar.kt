package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The actions that apply to a selection, as a Material 3 Expressive floating toolbar.
 *
 * Selection used to be a top app bar carrying up to eight icon buttons plus a menu, all in the one
 * place on the screen a thumb cannot reach while the other hand holds the phone. The identity of
 * the mode - how many books are selected, and how to leave it - stays at the top; the actions float
 * at the bottom where the fingers already are, and the four most used are one tap away instead of
 * two.
 *
 * Everything optional is null-able and simply absent when the caller cannot offer it, exactly like
 * the top bar it replaces, so a shelf's screen and the library can pass different action sets.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SharedSelectionToolbar(
    selectedCount: Int,
    onSelectAll: (() -> Unit)?,
    onPin: (() -> Unit)?,
    onAddToShelf: (() -> Unit)? = null,
    onTag: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onExportAnnotations: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    // Everything that only makes sense for one file is hidden for a multi-selection, as before.
    val singleSelection = selectedCount == 1

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.testTag("UnifiedLibrarySelectionToolbar"),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (onPin != null) {
            IconButton(onClick = onPin, modifier = Modifier.testTag("SelectionToolbarPin")) {
                Icon(Icons.Default.PushPin, contentDescription = readerString("pin_unpin", "Pin"))
            }
        }
        if (onTag != null) {
            IconButton(onClick = onTag, modifier = Modifier.testTag("SelectionToolbarTag")) {
                Icon(Icons.Default.Tag, contentDescription = readerString("section_tags", "Tags"))
            }
        }
        if (onAddToShelf != null) {
            IconButton(onClick = onAddToShelf, modifier = Modifier.testTag("SelectionToolbarShelf")) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = readerString("desktop_add_to_shelf", "Add to shelf"),
                )
            }
        }
        // Delete is the only destructive action here, so it is the only one that stays red.
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("SelectionToolbarDelete"),
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.Delete, contentDescription = readerString("action_delete", "Delete"))
            }
        }
        Box {
            IconButton(
                onClick = { showMoreMenu = true },
                modifier = Modifier.testTag("SelectionToolbarMore"),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = readerString("content_desc_more_options", "More actions"),
                )
            }
            SharedDropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
            ) {
                SharedSelectionOverflowItems(
                    selectedCount = selectedCount,
                    onSelectAll = onSelectAll,
                    onInfo = onInfo,
                    onSave = onSave,
                    onShare = onShare,
                    onExportAnnotations = onExportAnnotations,
                    onClear = onClear,
                    onDismiss = { showMoreMenu = false },
                )
            }
        }
    }
}

/**
 * The overflow of [SharedSelectionToolbar], split out so it can be rendered (and tested) without a
 * popup around it. Everything that only makes sense for one book is hidden for a multi-selection.
 */
@Composable
fun SharedSelectionOverflowItems(
    selectedCount: Int,
    onSelectAll: (() -> Unit)?,
    onInfo: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onExportAnnotations: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
) {
    val singleSelection = selectedCount == 1

    if (singleSelection) {
        onInfo?.let { info ->
            DropdownMenuItem(
                text = { Text(readerString("info", "Book info")) },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                onClick = { onDismiss(); info() },
            )
        }
    }
    onSelectAll?.let { selectAll ->
        DropdownMenuItem(
            text = { Text(readerString("select_all", "Select all")) },
            leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
            onClick = { onDismiss(); selectAll() },
        )
    }
    if (singleSelection) {
        onSave?.let { save ->
            DropdownMenuItem(
                text = { Text(readerString("action_save_copy_to_device", "Save copy to device")) },
                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                onClick = { onDismiss(); save() },
            )
        }
        onShare?.let { share ->
            DropdownMenuItem(
                text = { Text(readerString("action_share_original_file", "Share original file")) },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = { onDismiss(); share() },
            )
        }
        onExportAnnotations?.let { export ->
            DropdownMenuItem(
                text = { Text(readerString("action_export_annotations", "Export annotations")) },
                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                onClick = { onDismiss(); export() },
            )
        }
    }
    onClear?.let { clear ->
        DropdownMenuItem(
            text = { Text(readerString("action_clear", "Clear")) },
            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
            onClick = { onDismiss(); clear() },
        )
    }
}

/**
 * Whether a screen should float the selection toolbar.
 *
 * The action set is built from the screen's *capabilities*, which stay on for the whole session;
 * only the selection count says whether there is anything to act on. A screen that shows the bar
 * whenever capabilities exist leaves a floating toolbar over an unselected library.
 */
fun sharedSelectionToolbarVisible(selectedCount: Int): Boolean = selectedCount > 0

/**
 * The selection's top bar: what is selected, and the way out of the mode. Everything that acts on
 * the selection itself lives in [SharedSelectionToolbar], within reach of a thumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedSelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenterAlignedTopAppBar(
        modifier = modifier.testTag("UnifiedLibrarySelectionTopBar"),
        title = { Text(readerString("selected_count", "%1\$d selected", selectedCount)) },
        navigationIcon = {
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("SelectionTopBarClose"),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = readerString("action_clear_selection", "Clear selection"),
                )
            }
        },
    )
}
