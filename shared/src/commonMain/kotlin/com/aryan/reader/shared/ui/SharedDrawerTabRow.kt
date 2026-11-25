package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One open reader tab inside a navigation drawer.
 *
 * Both readers and both platforms draw tabs the same way: the *container* carries the selected
 * state (tone plus a primary hairline) while the row stays a plain list item, so a tab never looks
 * like a floating surface. Reading progress is a thin line, never a badge, because the drawer is a
 * dense list where a second visual language would compete with the titles.
 */
@Composable
fun SharedDrawerTabRow(
    title: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    closeContentDescription: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    /** 0f..1f reading progress; null hides the line. */
    progress: Float? = null,
    testTag: String? = null,
    closeTestTag: String? = null,
) {
    val shape = sharedCardShape()
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val border: BorderStroke = if (isSelected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
    } else {
        sharedCardBorder(0.45f)
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val supportingColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(shape)
            .background(containerColor)
            .border(border, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The select target and the close button are siblings: a close button nested inside the
            // selectable row would swallow taps meant for the row itself.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = onSelect)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!supportingText.isNullOrBlank()) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = supportingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = onClose,
                modifier = if (closeTestTag != null) Modifier.testTag(closeTestTag) else Modifier,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = closeContentDescription,
                    tint = supportingColor,
                )
            }
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                trackColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}
