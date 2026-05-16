package com.aryan.reader.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.PdfTocEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DesktopPdfJumpHistoryControls(
    visible: Boolean,
    modifier: Modifier = Modifier,
    backPage: Int?,
    forwardPage: Int?,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClear: () -> Unit
) {
    val hasJumpTargets = backPage != null || forwardPage != null
    AnimatedVisibility(
        visible = visible && hasJumpTargets,
        enter = slideInVertically { fullHeight -> fullHeight } + fadeIn(),
        exit = slideOutVertically { fullHeight -> fullHeight } + fadeOut(),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = onBack,
                enabled = backPage != null,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Jump back",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    backPage?.let { "P. ${it + 1}" } ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TextButton(
                onClick = onClear,
                modifier = Modifier.weight(0.8f)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear jump history",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Clear", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            TextButton(
                onClick = onForward,
                enabled = forwardPage != null,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    forwardPage?.let { "P. ${it + 1}" } ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Jump forward",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

internal fun desktopPdfTocParentIndices(toc: List<PdfTocEntry>): Set<Int> {
    return toc.indices.filter { index ->
        val next = toc.getOrNull(index + 1)
        next != null && next.nestLevel > toc[index].nestLevel
    }.toSet()
}

internal fun desktopPdfTocAncestorIndices(
    toc: List<PdfTocEntry>,
    originalIndex: Int
): Set<Int> {
    val targetDepth = toc.getOrNull(originalIndex)?.nestLevel ?: return emptySet()
    val ancestors = mutableSetOf<Int>()
    var currentDepth = targetDepth
    for (index in originalIndex downTo 0) {
        val entry = toc[index]
        if (entry.nestLevel < currentDepth) {
            ancestors += index
            currentDepth = entry.nestLevel
        }
        if (currentDepth == 0) break
    }
    return ancestors
}

internal fun desktopVisiblePdfTocEntries(
    toc: List<PdfTocEntry>,
    expandedIndices: Set<Int>
): List<Pair<Int, PdfTocEntry>> {
    val result = mutableListOf<Pair<Int, PdfTocEntry>>()
    val visibilityStack = BooleanArray(50) { false }
    visibilityStack[0] = true

    toc.forEachIndexed { index, entry ->
        val depth = entry.nestLevel.coerceIn(0, visibilityStack.lastIndex)
        if (visibilityStack[depth]) {
            result += index to entry
            if (depth + 1 < visibilityStack.size) {
                visibilityStack[depth + 1] = index in expandedIndices
            }
        } else if (depth + 1 < visibilityStack.size) {
            visibilityStack[depth + 1] = false
        }
    }
    return result
}

@Composable
internal fun DesktopPdfTocTreeItem(
    entry: PdfTocEntry,
    selected: Boolean,
    hasChildren: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .padding(start = (entry.nestLevel.coerceAtLeast(0) * 14).dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clickable(enabled = hasChildren) { onToggleExpand() },
                contentAlignment = Alignment.Center
            ) {
                if (hasChildren) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                entry.title,
                fontWeight = if (selected) FontWeight.Bold else if (entry.nestLevel == 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "p. ${entry.pageIndex + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
internal fun DesktopPdfNavigationEmpty(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DesktopPdfThumbnailTile(
    document: DesktopPdfDocument,
    pageIndex: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val documentHandleId = document.handleId
    var thumbnail by remember(documentHandleId, pageIndex) { mutableStateOf<DesktopPdfPageRender?>(null) }
    var renderFailed by remember(documentHandleId, pageIndex) { mutableStateOf(false) }
    val pageSize = document.pageSizes.getOrNull(pageIndex)
    val thumbnailScale = remember(pageSize) {
        val width = pageSize?.width?.coerceAtLeast(1f) ?: 612f
        (120f / width).coerceIn(0.08f, 0.35f)
    }

    LaunchedEffect(documentHandleId, pageIndex, thumbnailScale) {
        thumbnail = null
        renderFailed = false
        val rendered = withContext(Dispatchers.IO) {
            runCatching {
                DesktopPdfium.renderPage(
                    document = document,
                    pageIndex = pageIndex,
                    scale = thumbnailScale,
                    renderAnnotations = false
                )
            }.getOrNull()
        }
        thumbnail = rendered
        renderFailed = rendered == null
    }

    Surface(
        modifier = modifier.aspectRatio(0.707f).clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val render = thumbnail
            if (render != null) {
                Image(
                    bitmap = render.image,
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(3.dp)
                )
            } else {
                Text(
                    if (renderFailed) "!" else "${pageIndex + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
internal fun DesktopPdfPageScrubOverlay(
    pageIndex: Int?,
    pageCount: Int
) {
    if (pageIndex == null || pageCount <= 0) return
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Text(
                text = "Page ${pageIndex + 1} of $pageCount",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
    }
}
