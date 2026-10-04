package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * `ExposedDropdownMenuBox` + `ExposedDropdownMenu` equivalent, for the exposed
 * (full-width text-field) dropdowns in this codebase.
 *
 * Material's `ExposedDropdownMenu` cannot be reused directly:
 *  - it is a plain `DropdownMenu`, so it inherits the iOS outside-tap dismissal
 *    bug that [SharedDropdownMenu] exists to fix; and
 *  - it opens from the readOnly `OutlinedTextField`'s own interaction source. A
 *    readOnly field installs focus/selection pointer input that consumes the
 *    tap, so on iOS the menu never opened at all.
 *
 * So the field is laid out non-interactively and a transparent tap layer is
 * drawn *on top of it*, and the menu is rendered by [SharedDropdownMenu], which
 * owns the outside-tap dismissal.
 *
 * @param expanded whether the menu is open.
 * @param onExpandedChange called with the requested open state when the field is tapped.
 * @param field the readOnly field to display. Do not make it interactive.
 * @param content the menu items.
 */
@Composable
fun SharedExposedDropdownMenuField(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    field: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier) {
        field()
        // Tap layer above the field: the field's own focus/selection pointer input would
        // otherwise consume the press before it reached a clickable.
        //
        // `matchParentSize` (not `fillMaxSize`) is required here: these fields live inside
        // vertically scrolling columns, so the incoming height constraint is unbounded and
        // `fillMaxHeight` collapses to `minHeight` = 0. A zero-height overlay never receives
        // the press, the readOnly field swallows it instead, and the menu never opens.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    enabled = !expanded,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onExpandedChange(!expanded) },
        )
        SharedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            content = content,
        )
    }
}
