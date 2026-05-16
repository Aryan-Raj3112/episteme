package com.aryan.reader.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.SharedPdfAndroidHighlightColors
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.pdf.sharedPdfStrokePercent
import com.aryan.reader.shared.pdf.sharedPdfStrokeWidthRange
import com.aryan.reader.shared.pdf.sharedPdfTextStyle
import com.aryan.reader.shared.pdf.withSharedPdfTextStyle
import com.aryan.reader.shared.ui.SharedHsvColorPickerDialog
import com.aryan.reader.shared.ui.SharedPdfTextStyleControls
import com.aryan.reader.shared.ui.SharedStableOutlinedTextField

@Composable
internal fun DesktopPdfAnnotationEditor(
    annotation: SharedPdfAnnotation,
    onUpdate: (SharedPdfAnnotation) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    showSearch: Boolean,
    highlighterPalette: List<Int> = SharedPdfHighlighterPalette.defaultColors,
    onHighlighterPaletteChange: (SharedPdfHighlighterPalette) -> Unit = {},
    onSearch: () -> Unit
) {
    val highlighterColors = remember(highlighterPalette) {
        SharedPdfAndroidHighlightColors.palette
    }
    var editingHighlighterSlot by remember(annotation.id, highlighterColors) { mutableStateOf<Int?>(null) }
    val isHighlighterAnnotation = annotation.kind == PdfAnnotationKind.HIGHLIGHT ||
        annotation.tool == PdfInkTool.HIGHLIGHTER ||
        annotation.tool == PdfInkTool.HIGHLIGHTER_ROUND

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(2.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Selected ${annotation.desktopLabel()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClose) {
                    Text("Close")
                }
            }
            Text(
                "Page ${annotation.pageIndex + 1}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            if (annotation.text.isNotBlank()) {
                Surface(
                    color = Color(annotation.colorArgb).copy(alpha = 0.10f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(annotation.colorArgb).copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.heightIn(min = 72.dp)) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight()
                                .background(Color(annotation.colorArgb))
                        )
                        Text(
                            "\"${annotation.text}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DesktopBottomSheetToolButton(
                        icon = Icons.Default.ContentCopy,
                        label = "Copy",
                        onClick = onCopy
                    )
                    if (showSearch) {
                        DesktopBottomSheetToolButton(
                            icon = Icons.Default.Search,
                            label = "Search",
                            onClick = onSearch
                        )
                    }
                }
            }
            if (annotation.kind == PdfAnnotationKind.TEXT) {
                SharedStableOutlinedTextField(
                    value = annotation.text,
                    onValueChange = { onUpdate(annotation.copy(text = it)) },
                    label = { Text("Text note") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    selectionKey = annotation.id
                )
                SharedPdfTextStyleControls(
                    style = annotation.sharedPdfTextStyle(),
                    onStyleChange = { onUpdate(annotation.withSharedPdfTextStyle(it)) }
                )
            }
            if (annotation.kind != PdfAnnotationKind.TEXT) {
                val palette = if (isHighlighterAnnotation) {
                    highlighterColors
                } else {
                    SharedPdfAnnotationDefaults.penPalette
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    palette.forEachIndexed { _, argb ->
                        Surface(
                            modifier = Modifier
                                .size(26.dp)
                                .clickable {
                                    val nextColor = if (isHighlighterAnnotation) {
                                        SharedPdfAndroidHighlightColors.nearestArgb(argb)
                                    } else {
                                        argb
                                    }
                                    onUpdate(annotation.copy(colorArgb = nextColor))
                                },
                            color = Color(argb),
                            shape = RoundedCornerShape(13.dp),
                            content = {}
                        )
                    }
                    if (isHighlighterAnnotation) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                            Color.Red,
                                            Color.Yellow,
                                            Color.Green,
                                            Color.Cyan,
                                            Color.Blue,
                                            Color.Magenta,
                                            Color.Red
                                        )
                                    )
                                )
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f), RoundedCornerShape(15.dp))
                                .clickable {
                                    editingHighlighterSlot = highlighterColors
                                        .indexOf(annotation.colorArgb)
                                        .takeIf { it >= 0 }
                                        ?: 0
                                }
                        )
                    }
                }
                SharedStableOutlinedTextField(
                    value = annotation.note.orEmpty(),
                    onValueChange = { note -> onUpdate(annotation.copy(note = note.takeIf { it.isNotBlank() })) },
                    label = { Text("Note") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    selectionKey = annotation.id
                )
            }
            if (annotation.kind == PdfAnnotationKind.INK) {
                val strokeRange = annotation.tool.sharedPdfStrokeWidthRange()
                val strokeValue = annotation.strokeWidth.coerceIn(strokeRange.start, strokeRange.endInclusive)
                Text("Thickness ${strokeValue.sharedPdfStrokePercent(strokeRange)}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = strokeValue,
                    onValueChange = { onUpdate(annotation.copy(strokeWidth = it.coerceAtLeast(0.0001f))) },
                    valueRange = strokeRange
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
    editingHighlighterSlot?.let { requestedSlot ->
        val slot = requestedSlot.coerceIn(0, highlighterColors.lastIndex)
        val initialColor = Color(highlighterColors[slot]).copy(alpha = 1f)
        SharedHsvColorPickerDialog(
            initialColor = initialColor,
            title = "Highlight color ${slot + 1}",
            onDismiss = { editingHighlighterSlot = null },
            onSave = { color ->
                val nextArgb = color.copy(alpha = SharedPdfHighlighterPalette.DefaultAlpha / 255f).toArgb()
                val syncedArgb = SharedPdfAndroidHighlightColors.nearestArgb(nextArgb)
                onHighlighterPaletteChange(
                    SharedPdfHighlighterPalette(highlighterColors).withColorAt(
                        slotIndex = slot,
                        colorArgb = nextArgb
                    )
                )
                onUpdate(annotation.copy(colorArgb = syncedArgb))
                editingHighlighterSlot = null
            }
        ) { liveColor ->
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                highlighterColors.forEachIndexed { index, argb ->
                    val color = if (index == slot) liveColor else Color(argb).copy(alpha = 1f)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(color)
                            .border(
                                width = if (index == slot) 3.dp else 1.dp,
                                color = if (index == slot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(21.dp)
                            )
                            .clickable { editingHighlighterSlot = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopBottomSheetToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun DesktopPdfEmbeddedAnnotationPanel(
    annotation: SharedPdfEmbeddedAnnotation,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Embedded PDF comment",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClose) {
                    Text("Close")
                }
            }
            Text(
                "Page ${annotation.pageIndex + 1}${annotation.author.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            DesktopPdfEmbeddedComment(
                author = annotation.author,
                contents = annotation.contents.ifBlank { "No comment" },
                depth = 0
            )
            DesktopPdfEmbeddedReplies(annotation.replies, depth = 1)
            TextButton(onClick = onCopy) {
                Text("Copy thread")
            }
        }
    }
}

@Composable
private fun DesktopPdfEmbeddedReplies(
    replies: List<SharedPdfEmbeddedAnnotation>,
    depth: Int
) {
    replies.forEach { reply ->
        HorizontalDivider()
        DesktopPdfEmbeddedComment(
            author = reply.author,
            contents = reply.contents,
            depth = depth
        )
        if (reply.replies.isNotEmpty()) {
            DesktopPdfEmbeddedReplies(reply.replies, depth + 1)
        }
    }
}

@Composable
private fun DesktopPdfEmbeddedComment(
    author: String,
    contents: String,
    depth: Int
) {
    Column(
        modifier = Modifier.padding(start = (depth * 12).dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            author.ifBlank { "Unknown" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            contents.ifBlank { "No comment" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun SharedPdfAnnotation.desktopLabel(): String {
    return when (kind) {
        PdfAnnotationKind.HIGHLIGHT -> "highlight"
        PdfAnnotationKind.INK -> tool.name.lowercase().replace('_', ' ')
        PdfAnnotationKind.TEXT -> "text note"
    }
}

internal fun SharedPdfAnnotation.desktopSheetTitle(): String {
    return when (kind) {
        PdfAnnotationKind.HIGHLIGHT -> "Highlight"
        PdfAnnotationKind.INK -> "Annotation"
        PdfAnnotationKind.TEXT -> "Text note"
    }
}

internal fun SharedPdfEmbeddedAnnotation.threadText(): String {
    return buildString {
        append(author.ifBlank { "Unknown" })
        append(": ")
        appendLine(contents.ifBlank { "No comment" })
        fun appendReplies(replies: List<SharedPdfEmbeddedAnnotation>, indent: String) {
            replies.forEach { reply ->
                append(indent)
                append(reply.author.ifBlank { "Unknown" })
                append(": ")
                appendLine(reply.contents.ifBlank { "No comment" })
                appendReplies(reply.replies, "$indent  ")
            }
        }
        appendReplies(replies, "  ")
    }.trimEnd()
}
