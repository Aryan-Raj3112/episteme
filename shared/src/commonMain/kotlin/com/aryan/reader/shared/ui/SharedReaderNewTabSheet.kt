package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A book offered by the "new tab" sheet: the openable identity plus what the row shows. */
data class SharedReaderNewTabCandidate(
    val bookId: String,
    val title: String,
    val subtitle: String? = null,
)

/**
 * Body of a reader's "add a tab" sheet, shared so both readers and both platforms offer the same
 * rows. Hosts own the sheet chrome and pass only the books of their own tab kind, which is what
 * keeps a reader from suggesting a book it cannot open in place.
 */
@Composable
fun SharedReaderNewTabSheetContent(
    title: String,
    emptyMessage: String,
    candidates: List<SharedReaderNewTabCandidate>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        if (candidates.isEmpty()) {
            Text(
                text = emptyMessage,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(candidates, key = { it.bookId }) { candidate ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = candidate.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = candidate.subtitle?.let { subtitle ->
                        { Text(text = subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                    modifier = Modifier.clickable { onPick(candidate.bookId) },
                )
                HorizontalDivider()
            }
        }
    }
}
