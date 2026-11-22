package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Tag as TagIcon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.Tag

/**
 * Tags destination for the unified library.
 *
 * Tags were generated as `ShelfType.TAG` shelves but the Library Beta shelf
 * surfaces filter those out, and tag chips only exist on list rows.  That made
 * every existing tag invisible and made a freshly assigned tag look like it
 * was never saved.  This section reads `allTags` directly so empty tags stay
 * visible and manageable as well, and tapping a tag opens its books.
 */
@Composable
fun SharedAndroidUnifiedTags(
    tags: List<Tag>,
    bookCountLabel: @Composable (Tag) -> String,
    noTagsLabel: String,
    onTagSelected: (Tag) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SharedEmptyState(
                icon = Icons.Default.TagIcon,
                title = noTagsLabel,
                testTag = "UnifiedTagsEmpty",
            )
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(SharedSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(SharedSpacing.Md),
    ) {
        items(tags, key = { it.id }) { tag ->
            UnifiedTagCard(
                tag = tag,
                countLabel = bookCountLabel(tag),
                onClick = { onTagSelected(tag) },
            )
        }
    }
}

@Composable
private fun UnifiedTagCard(
    tag: Tag,
    countLabel: String,
    onClick: () -> Unit,
) {
    val tagColor = tag.color?.let(::Color) ?: SharedTagColorFallback
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("UnifiedTagRow_${tag.id}"),
        shape = sharedCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = sharedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = SharedSpacing.Lg, vertical = SharedSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedSpacing.Lg),
        ) {
            // The tag's own colour is the row's identity, so it stays the badge container rather
            // than being flattened into a neutral one.
            Surface(shape = RoundedCornerShape(12.dp), color = tagColor.copy(alpha = 0.20f)) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.TagIcon,
                        contentDescription = null,
                        tint = tagColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedSpacing.Xxs)) {
                Text(
                    tag.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    countLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
