package com.aryan.reader.shared.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * File-manager style shelf breadcrumb: Home (root) > folder 1 > folder 2.
 * Tapping any ancestor jumps directly there; the current shelf is emphasized
 * and not clickable. The row scrolls horizontally and auto-scrolls to the
 * current (end) crumb so deep paths stay reachable.
 *
 * Android benchmark for shelf navigation; shared so iOS (Compose) matches.
 */
@Composable
fun SharedMobileShelfBreadcrumb(
    entries: List<SharedShelfBreadcrumbEntry>,
    onNavigate: (SharedShelfBreadcrumbEntry) -> Unit,
    homeContentDescription: String,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    val scrollState = rememberScrollState()
    LaunchedEffect(entries.map { it.id }) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Surface(
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().testTag("ShelfBreadcrumb"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                val isLast = index == entries.lastIndex
                if (index > 0) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (entry.id == null) {
                    if (isLast) {
                        FilledTonalIconButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.testTag("ShelfBreadcrumbHome"),
                        ) {
                            Icon(Icons.Default.Home, contentDescription = homeContentDescription)
                        }
                    } else {
                        IconButton(
                            onClick = { onNavigate(entry) },
                            modifier = Modifier.testTag("ShelfBreadcrumbHome"),
                        ) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = homeContentDescription,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else if (isLast) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 180.dp)
                            .padding(horizontal = 8.dp)
                            .testTag("ShelfBreadcrumbItem_${entry.id}"),
                    )
                } else {
                    TextButton(
                        onClick = { onNavigate(entry) },
                        modifier = Modifier.testTag("ShelfBreadcrumbItem_${entry.id}"),
                    ) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp),
                        )
                    }
                }
            }
        }
    }
}
