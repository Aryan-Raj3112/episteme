package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A row inside the reader's tool sheet.
 *
 * The tool menus used to be `DropdownMenuItem`s: 48dp of plain text with a 20dp glyph, no
 * supporting line, and no way to show that a row is a switch rather than a panel. A sheet can
 * afford the settings-hub language - tonal badge, title, one line of explanation, trailing
 * control - so a reader can tell "Realistic Page Turns" (a switch) from "Theme" (a panel) before
 * tapping, and destructive rows read as destructive.
 */
@Composable
fun SharedReaderToolSheetRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    selected: Boolean = false,
    selectedContentDescription: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val contentColor = (
        when {
            destructive -> MaterialTheme.colorScheme.error
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }
        ).let { if (enabled) it else it.copy(alpha = 0.38f) }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (icon != null) {
                val badgeColor = when {
                    destructive -> MaterialTheme.colorScheme.errorContainer
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }
                val iconColor = when {
                    destructive -> MaterialTheme.colorScheme.onErrorContainer
                    selected -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(badgeColor, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                trailing != null -> trailing()
                selected -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = selectedContentDescription,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Drop-in replacement for `DropdownMenuItem` inside the tool sheet.
 *
 * The reader menus are built from long `when`s of `DropdownMenuItem`s whose rows carry their own
 * icon and text lambdas. Keeping that call shape means the menus did not have to be rewritten to
 * move into a sheet - only the container - while every row still gains the sheet's row language
 * (tonal icon badge, full-width tap target, destructive colouring).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedReaderToolSheetRowItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        contentColor = colors.textColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (leadingIcon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        // The badge is decoration: the row's title already names the action, and the
                        // icon composables the menus pass carry their own descriptions.
                        .clearAndSetSemantics {}
                        .background(
                            if (enabled) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides if (enabled) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ) { leadingIcon() }
                }
            }
            Box(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalContentColor provides colors.textColor) { text() }
            }
            if (trailingIcon != null) {
                CompositionLocalProvider(LocalContentColor provides colors.trailingIconColor) { trailingIcon() }
            }
        }
    }
}

/**
 * A toggle in the tool sheet.
 *
 * Toggling used to close the menu, so flipping page turns *and* keep-screen-on meant reopening the
 * menu twice. The row is a switch now: the state is visible before it is tapped, and the sheet stays
 * open so several settings can be changed in one visit.
 */
@Composable
fun SharedReaderToolSheetSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
    icon: ImageVector? = null,
) {
    SharedReaderToolSheetRow(
        title = title,
        onClick = { if (enabled) onCheckedChange(!checked) },
        modifier = modifier,
        supporting = supporting,
        icon = icon,
        enabled = enabled,
        trailing = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

/** Section label between groups of tools, so "Read", "Motion" and "File" are scannable. */
@Composable
fun SharedReaderToolSheetSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}
