package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The building blocks the two drawer surfaces share: the library navigation on the leading edge
 * and the account sheet on the trailing edge.
 *
 * Both drawers used to be a plain `Column` with a `Spacer(weight = 1f)`, which could not scroll, so
 * at a large font scale the last rows were clipped and the drawer simply ended at the navigation
 * bar. They also disagreed with each other about header, rhythm and insets, and the library drawer
 * opened with a full-width tinted slab that competed with the selected-item indicator inside the
 * body. Everything below fixes that in one place:
 *
 *  - the header is a row on the drawer's own container, with the app/account mark in a tonal badge;
 *  - sections are labelled and separated, and every row keeps the same height and trailing inset;
 *  - the list scrolls, and the footer (account, legal links) stays put;
 *  - switches are whole-row targets rather than a `Switch` squeezed into a badge slot.
 */

/** Fixed header + scrolling body + pinned footer. Every drawer is this shape. */
@Composable
fun SharedDrawerLayout(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    /** The footer takes a divider only when it holds rows; a lone caption does not need one. */
    showFooterDivider: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        header()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = SharedSpacing.Md),
            content = content,
        )
        if (footer != null) {
            if (showFooterDivider) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = SharedSpacing.Lg),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
            footer()
            Spacer(Modifier.heightIn(min = SharedSpacing.Sm))
        }
    }
}

/**
 * Drawer header: a mark, a title and - for the library drawer - the destination the reader is
 * currently on, so the header says where you are instead of repeating the app name over the body.
 */
@Composable
fun SharedDrawerHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SharedSpacing.Xl,
                end = SharedSpacing.Lg,
                top = SharedSpacing.Lg,
                bottom = SharedSpacing.Md,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SharedSpacing.Md),
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Group label inside a drawer. One inset for every drawer: the same 28dp the item icons start at. */
@Composable
fun SharedDrawerSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            start = 28.dp,
            end = SharedSpacing.Lg,
            top = SharedSpacing.Lg,
            bottom = SharedSpacing.Xs,
        ),
    )
}

/** Separates groups that do not carry a label of their own. */
@Composable
fun SharedDrawerGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = SharedSpacing.Lg, vertical = SharedSpacing.Sm),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

/**
 * A drawer destination. [supporting] is a second line inside the label, which is where the folder
 * and sync rows put their explanation; the trailing slot is for a badge or a switch.
 */
@Composable
fun SharedDrawerItem(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    NavigationDrawerItem(
        icon = icon,
        label = {
            if (supporting == null) {
                Text(label)
            } else {
                Column {
                    Text(label)
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        selected = selected,
        onClick = onClick,
        badge = trailing,
        colors = NavigationDrawerItemDefaults.colors(),
        modifier = modifier.padding(horizontal = SharedSpacing.Md, vertical = SharedSpacing.Xxs),
    )
}

/**
 * A switch row whose whole row is the target.
 *
 * `NavigationDrawerItem(badge = { Switch(...) })` reads badly: the switch only responds on its own
 * 52dp, the row's tap does something else, and a screen reader announces two controls for one
 * setting. Here the row owns the toggle and the switch is decoration for it.
 */
@Composable
fun SharedDrawerSwitchRow(
    icon: @Composable () -> Unit,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SharedSpacing.Xs)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = 56.dp)
            .padding(start = SharedSpacing.Lg, end = SharedSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SharedSpacing.Md),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        ) { icon() }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The row already carries the toggle semantics, so the switch itself stays decorative.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * The library drawer's footer: who is signed in (or the way to sign in) and where to go for the
 * account. It fills what used to be an empty band above the navigation bar.
 */
@Composable
fun SharedDrawerAccountFooter(
    title: String,
    supporting: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SharedSpacing.Md, vertical = SharedSpacing.Sm),
        shape = RoundedCornerShape(SharedUiTokens.cardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(SharedSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedSpacing.Md),
        ) {
            leading()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Privacy / terms / licences, centred and shrunk to fit rather than wrapped onto three lines.
 * The measuring pass writes a zero-height layout so the fitted size can be measured before the
 * real row is drawn.
 */
@Composable
fun SharedDrawerLegalLinks(
    onPrivacyPolicyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onLicensesClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.primary,
) {
    val baseStyle = MaterialTheme.typography.labelMedium
    var fittedStyle by remember(baseStyle) { mutableStateOf(baseStyle) }
    Text(
        text = readerString("legal_footer_combined", "Privacy Policy  •  Terms of Service  •  Licenses"),
        style = fittedStyle,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { result ->
            if (result.didOverflowWidth) {
                fittedStyle = fittedStyle.copy(fontSize = fittedStyle.fontSize * 0.95)
            }
        },
        modifier = Modifier.heightIn(max = 0.dp),
    )
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = SharedSpacing.Sm, vertical = SharedSpacing.Sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = readerString("legal_privacy_policy", "Privacy Policy"),
            style = fittedStyle,
            color = textColor,
            maxLines = 1,
            modifier = Modifier.clickable(onClick = onPrivacyPolicyClick),
        )
        Text(
            text = "  •  ",
            style = fittedStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = readerString("legal_terms_of_service", "Terms of Service"),
            style = fittedStyle,
            color = textColor,
            maxLines = 1,
            modifier = Modifier.clickable(onClick = onTermsClick),
        )
        Text(
            text = "  •  ",
            style = fittedStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = readerString("legal_licenses", "Licenses"),
            style = fittedStyle,
            color = textColor,
            maxLines = 1,
            modifier = Modifier.clickable(onClick = onLicensesClick),
        )
    }
}

/** Small caption used for the build version at the bottom of the library drawer. */
@Composable
fun SharedDrawerVersionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, end = SharedSpacing.Xl, bottom = SharedSpacing.Md),
    )
}
