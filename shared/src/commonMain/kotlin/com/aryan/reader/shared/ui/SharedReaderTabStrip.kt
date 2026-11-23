package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close

/**
 * Height a reader's tab strip reserves. One token for both platforms and both readers: the PDF
 * reader feeds it into its page inset, so drift here would re-paginate documents differently per
 * platform.
 */
val SharedReaderTabStripHeight = 48.dp

/** One tab row entry. Titles are already host-formatted (custom name vs. file name is a host rule). */
data class SharedReaderTab(
    val bookId: String,
    val title: String,
)

/**
 * The tab strip shared by every reader surface.
 *
 * It is deliberately a separate row from the toolbars: it belongs to the document stack rather than
 * to the current document, so it must not move when tools are customized. Callers decide whether it
 * paints over the page (reflow readers, whose pagination must stay chrome-independent) or reserves
 * space (the page renderer, which recomputes its inset from [SharedReaderTabStripHeight]).
 */
@Composable
fun SharedReaderTabStrip(
    tabs: List<SharedReaderTab>,
    activeBookId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    activeTabContentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    tabContentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /**
     * Whether the strip paints its own surface. A host that already sits on the chrome surface (the
     * reflow reader's toolbar column) passes false so the two do not stack two tonal fills.
     */
    onSurface: Boolean = false,
    newTabContentDescription: String? = null,
    closeTabContentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        // Opaque rather than translucent: page text showing through the strip read as a rendering
        // fault instead of a surface.
        modifier = modifier
            .fillMaxWidth()
            .height(SharedReaderTabStripHeight)
            .then(
                if (onSurface) {
                    Modifier
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                }
            ),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        items(tabs, key = { it.bookId }) { tab ->
            val isSelected = tab.bookId == activeBookId
            // The select target and the close button are siblings, not nested: nesting would merge
            // the close button's click into the tab node, so a screen reader (and a touch) on the
            // tab would close it instead of opening it.
            Row(
                modifier = Modifier
                    .height(if (isSelected) SharedReaderTabStripHeight else 36.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .height(if (isSelected) SharedReaderTabStripHeight else 36.dp)
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onSelect(tab.bookId) },
                        )
                        .testTag("ReaderTab_${tab.bookId}")
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = tab.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) activeTabContentColor else tabContentColor,
                    )
                }
                IconButton(
                    onClick = { onClose(tab.bookId) },
                    modifier = Modifier.size(28.dp).testTag("ReaderTabClose_${tab.bookId}"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = closeTabContentDescription,
                        modifier = Modifier.size(14.dp),
                        tint = if (isSelected) activeTabContentColor else tabContentColor,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        item {
            IconButton(
                onClick = onNewTab,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp).size(36.dp)
                    .testTag("ReaderTabNew"),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = newTabContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
