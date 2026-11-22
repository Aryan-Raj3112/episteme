package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The non-reader screens' shared visual language.
 *
 * Home established it — tonal cards with a hairline instead of drop shadows, an expressive corner
 * scale, and empty states that carry an illustration instead of a bare sentence — and the library
 * sections, settings and catalogs all use these helpers so a screen cannot drift back to the
 * baseline look by hand-rolling its own card.
 */

/** Card corner for content surfaces: 20dp (M3 expressive "large increased"). */
@Composable
internal fun sharedCardShape(): Shape = RoundedCornerShape(SharedUiTokens.cardRadius)

/**
 * Hairline that separates a tonal card from the surface behind it. Elevation is reserved for
 * things that genuinely float (menus, sheets); cards use tone plus this line.
 */
@Composable
internal fun sharedCardBorder(alpha: Float = 0.55f): BorderStroke =
    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha))

/**
 * An icon in a tonal container — the settings/library row leading element. It gives every row the
 * same optical weight, which a bare 24dp glyph next to two lines of text never had.
 */
@Composable
fun SharedTonalIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size * 0.32f),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
        }
    }
}

/**
 * The one empty state: a tonal disc, a title, an optional body line and an optional action.
 *
 * Every section used to improvise this (a centred sentence in three files, a bare icon in two
 * others), which is why the same "nothing here yet" moment looked different depending on where the
 * reader hit it.
 */
@Composable
fun SharedEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    testTag: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    /** Set only where the glyph carries meaning the title does not; otherwise it is decorative. */
    iconContentDescription: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SharedSpacing.Xl)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = iconContentDescription,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(SharedSpacing.Lg))
        Text(
            title,
            style = titleStyle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (!body.isNullOrBlank()) {
            Spacer(Modifier.height(SharedSpacing.Sm))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(SharedSpacing.Lg))
            Row(horizontalArrangement = Arrangement.spacedBy(SharedSpacing.Sm)) {
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    TextButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
                }
            }
        }
    }
}

/**
 * Section title plus trailing controls, matching Home's "Your books" row so the library sections
 * read as pages of one app.
 */
@Composable
fun SharedSectionHeader(
    title: String?,
    modifier: Modifier = Modifier,
    titleTestTag: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .then(if (titleTestTag != null) Modifier.testTag(titleTestTag) else Modifier),
        )
        actions()
    }
}
