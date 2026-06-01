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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.pdf.DEFAULT_SHARED_PDF_COMMENT_AUTHOR
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationComment
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.pdf.pdfCommentChildren
import com.aryan.reader.shared.pdf.sharedPdfStrokePercent
import com.aryan.reader.shared.pdf.sharedPdfStrokeWidthRange
import com.aryan.reader.shared.pdf.sharedPdfTextStyle
import com.aryan.reader.shared.pdf.visiblePdfAnnotationComments
import com.aryan.reader.shared.pdf.withoutPdfCommentThread
import com.aryan.reader.shared.pdf.withSharedPdfTextStyle
import com.aryan.reader.shared.ui.SharedHsvColorPickerDialog
import com.aryan.reader.shared.ui.SharedPdfTextStyleControls
import com.aryan.reader.shared.ui.SharedStableOutlinedTextField
import com.aryan.reader.shared.ui.readerString
import java.text.DateFormat
import java.util.Date
import java.util.UUID

internal val DesktopPdfAnnotationTools = listOf(
    PdfInkTool.PEN,
    PdfInkTool.FOUNTAIN_PEN,
    PdfInkTool.PENCIL,
    PdfInkTool.HIGHLIGHTER,
    PdfInkTool.HIGHLIGHTER_ROUND,
    PdfInkTool.TEXT,
    PdfInkTool.ERASER
)

private enum class DesktopPdfAnnotationSheetSection {
    NOTE,
    COMMENTS
}

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
        SharedPdfHighlighterPalette(highlighterPalette).sanitized().colors
    }
    var editingHighlighterSlot by remember(annotation.id, highlighterColors) { mutableStateOf<Int?>(null) }
    var editingHighlighterDraftColors by remember(annotation.id, highlighterColors) {
        mutableStateOf<List<Int>>(emptyList())
    }
    val isHighlighterAnnotation = annotation.kind == PdfAnnotationKind.HIGHLIGHT ||
        annotation.tool == PdfInkTool.HIGHLIGHTER ||
        annotation.tool == PdfInkTool.HIGHLIGHTER_ROUND
    var selectedSection by remember(annotation.id) { mutableStateOf(DesktopPdfAnnotationSheetSection.NOTE) }
    var commentText by remember(annotation.id) { mutableStateOf("") }
    var replyTargetId by remember(annotation.id) { mutableStateOf<String?>(null) }
    var editingCommentId by remember(annotation.id) { mutableStateOf<String?>(null) }
    var commentAuthor by remember(annotation.id) {
        mutableStateOf(
            annotation.comments
                .lastOrNull { it.author.isNotBlank() }
                ?.author
                ?: DEFAULT_SHARED_PDF_COMMENT_AUTHOR
        )
    }

    fun updateComments(nextComments: List<SharedPdfAnnotationComment>) {
        onUpdate(annotation.copy(comments = nextComments))
    }

    fun highlighterDraftColors(): List<Int> {
        return editingHighlighterDraftColors.ifEmpty { highlighterColors }
    }

    fun updateHighlighterDraft(slotIndex: Int, color: Color): List<Int> {
        val nextColors = highlighterDraftColors().toMutableList()
        if (slotIndex in nextColors.indices) {
            nextColors[slotIndex] = color.copy(alpha = SharedPdfHighlighterPalette.DefaultAlpha / 255f).toArgb()
            editingHighlighterDraftColors = nextColors
        }
        return nextColors
    }

    fun openHighlighterEditor(slotIndex: Int) {
        if (editingHighlighterSlot == null) {
            editingHighlighterDraftColors = highlighterColors
        }
        editingHighlighterSlot = slotIndex
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(2.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    readerString("desktop_selected_annotation_format", "Selected %1\$s", annotation.desktopLabel()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClose) {
                    Text(readerString("action_close", "Close"))
                }
            }
            Text(
                readerString("pdf_page_short", "Page %1\$d", annotation.pageIndex + 1),
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
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
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
                        label = readerString("action_copy", "Copy"),
                        onClick = onCopy
                    )
                    if (showSearch) {
                        DesktopBottomSheetToolButton(
                            icon = Icons.Default.Search,
                            label = readerString("action_search", "Search"),
                            onClick = onSearch
                        )
                    }
                }
            }
            if (annotation.kind == PdfAnnotationKind.TEXT) {
                SharedStableOutlinedTextField(
                    value = annotation.text,
                    onValueChange = { onUpdate(annotation.copy(text = it)) },
                    label = { Text(readerString("desktop_text_note", "Text note")) },
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
                Text(readerString("desktop_color", "Color"), style = MaterialTheme.typography.labelLarge)
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
                                    onUpdate(annotation.copy(colorArgb = argb))
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
                                    openHighlighterEditor(
                                        highlighterColors
                                            .indexOf(annotation.colorArgb)
                                            .takeIf { it >= 0 }
                                            ?: 0
                                    )
                                }
                        )
                    }
                }
                DesktopPdfAnnotationSheetTabs(
                    selectedSection = selectedSection,
                    commentCount = annotation.comments.count { it.contents.isNotBlank() },
                    onSectionChange = { selectedSection = it }
                )
                if (selectedSection == DesktopPdfAnnotationSheetSection.NOTE) {
                    SharedStableOutlinedTextField(
                        value = annotation.note.orEmpty(),
                        onValueChange = { note -> onUpdate(annotation.copy(note = note.takeIf { it.isNotBlank() })) },
                        label = { Text(readerString("label_note", "Note")) },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        selectionKey = annotation.id
                    )
                } else {
                    DesktopPdfHighlightCommentsEditor(
                        comments = annotation.comments,
                        commentText = commentText,
                        commentAuthor = commentAuthor,
                        replyTargetId = replyTargetId,
                        editingCommentId = editingCommentId,
                        onCommentTextChange = { commentText = it },
                        onCommentAuthorChange = { commentAuthor = it },
                        onReply = { comment ->
                            editingCommentId = null
                            replyTargetId = comment.id
                            commentText = ""
                        },
                        onCancelReply = { replyTargetId = null },
                        onEdit = { comment ->
                            editingCommentId = comment.id
                            replyTargetId = null
                            commentText = comment.contents
                            commentAuthor = comment.author.ifBlank { DEFAULT_SHARED_PDF_COMMENT_AUTHOR }
                        },
                        onCancelEdit = {
                            editingCommentId = null
                            commentText = ""
                        },
                        onDelete = { comment ->
                            val nextComments = annotation.comments.withoutPdfCommentThread(comment.id)
                            updateComments(nextComments)
                            if (replyTargetId != null && (replyTargetId == comment.id || nextComments.none { it.id == replyTargetId })) {
                                replyTargetId = null
                            }
                            if (editingCommentId != null && (editingCommentId == comment.id || nextComments.none { it.id == editingCommentId })) {
                                editingCommentId = null
                                commentText = ""
                            }
                        },
                        onAddComment = {
                            val contents = commentText.trim()
                            if (contents.isNotBlank()) {
                                val now = System.currentTimeMillis()
                                val author = commentAuthor.trim().ifBlank { DEFAULT_SHARED_PDF_COMMENT_AUTHOR }
                                val nextComments = if (editingCommentId != null) {
                                    annotation.comments.map { comment ->
                                        if (comment.id == editingCommentId) {
                                            comment.copy(
                                                author = author,
                                                contents = contents,
                                                modifiedAt = now
                                            )
                                        } else {
                                            comment
                                        }
                                    }
                                } else {
                                    annotation.comments + SharedPdfAnnotationComment(
                                        id = UUID.randomUUID().toString(),
                                        parentId = replyTargetId,
                                        author = author,
                                        contents = contents,
                                        createdAt = now,
                                        modifiedAt = now
                                    )
                                }
                                updateComments(nextComments)
                                commentText = ""
                                replyTargetId = null
                                editingCommentId = null
                            }
                        }
                    )
                }
            }
            if (annotation.kind == PdfAnnotationKind.INK) {
                val strokeRange = annotation.tool.sharedPdfStrokeWidthRange()
                val strokeValue = annotation.strokeWidth.coerceIn(strokeRange.start, strokeRange.endInclusive)
                Text(
                    readerString(
                        "desktop_thickness_format",
                        "Thickness %1\$s",
                        strokeValue.sharedPdfStrokePercent(strokeRange)
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = strokeValue,
                    onValueChange = { onUpdate(annotation.copy(strokeWidth = it.coerceAtLeast(0.0001f))) },
                    valueRange = strokeRange
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Text(readerString("action_delete", "Delete"))
                }
            }
        }
    }
    editingHighlighterSlot?.let { requestedSlot ->
        val draftColors = highlighterDraftColors()
        val safeDraftColors = draftColors.ifEmpty { SharedPdfHighlighterPalette.defaultColors }
        val slot = requestedSlot.coerceIn(0, safeDraftColors.lastIndex)
        val initialColor = remember(slot) { Color(safeDraftColors[slot]).copy(alpha = 1f) }
        SharedHsvColorPickerDialog(
            initialColor = initialColor,
            title = readerString("desktop_highlight_color_format", "Highlight color %1\$d", slot + 1),
            onDismiss = { editingHighlighterSlot = null },
            onSave = { color ->
                val nextArgb = color.copy(alpha = SharedPdfHighlighterPalette.DefaultAlpha / 255f).toArgb()
                val nextColors = updateHighlighterDraft(slot, color)
                onHighlighterPaletteChange(
                    SharedPdfHighlighterPalette(nextColors).sanitized()
                )
                onUpdate(annotation.copy(colorArgb = nextArgb))
                editingHighlighterSlot = null
            },
            resetColor = Color(SharedPdfHighlighterPalette.defaultColors.getOrElse(slot) {
                SharedPdfHighlighterPalette.defaultColors.first()
            }).copy(alpha = 1f),
            stateKey = slot,
            onLiveColorChange = { color ->
                updateHighlighterDraft(slot, color)
            }
        ) { liveColor ->
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                highlighterDraftColors().forEachIndexed { index, argb ->
                    val color = if (index == slot) liveColor else Color(argb).copy(alpha = 1f)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(color)
                            .border(
                                width = if (index == slot) 3.dp else 1.dp,
                                color = if (index == slot) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                },
                                shape = RoundedCornerShape(21.dp)
                            )
                            .clickable { openHighlighterEditor(index) },
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
private fun DesktopPdfAnnotationSheetTabs(
    selectedSection: DesktopPdfAnnotationSheetSection,
    commentCount: Int,
    onSectionChange: (DesktopPdfAnnotationSheetSection) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            DesktopPdfAnnotationSheetTab(
                label = readerString("label_note", "Note"),
                selected = selectedSection == DesktopPdfAnnotationSheetSection.NOTE,
                modifier = Modifier.weight(1f),
                onClick = { onSectionChange(DesktopPdfAnnotationSheetSection.NOTE) }
            )
            DesktopPdfAnnotationSheetTab(
                label = "${readerString("label_comments", "Comments")} ($commentCount)",
                selected = selectedSection == DesktopPdfAnnotationSheetSection.COMMENTS,
                modifier = Modifier.weight(1f),
                onClick = { onSectionChange(DesktopPdfAnnotationSheetSection.COMMENTS) }
            )
        }
    }
}

@Composable
private fun DesktopPdfAnnotationSheetTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DesktopPdfHighlightCommentsEditor(
    comments: List<SharedPdfAnnotationComment>,
    commentText: String,
    commentAuthor: String,
    replyTargetId: String?,
    editingCommentId: String?,
    onCommentTextChange: (String) -> Unit,
    onCommentAuthorChange: (String) -> Unit,
    onReply: (SharedPdfAnnotationComment) -> Unit,
    onCancelReply: () -> Unit,
    onEdit: (SharedPdfAnnotationComment) -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: (SharedPdfAnnotationComment) -> Unit,
    onAddComment: () -> Unit
) {
    val visibleComments = comments.visiblePdfAnnotationComments()
    val replyTarget = visibleComments.firstOrNull { it.id == replyTargetId }
    val editingComment = visibleComments.firstOrNull { it.id == editingCommentId }

    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
        ) {
            DesktopPdfHighlightCommentThread(
                comments = visibleComments,
                parentId = null,
                depth = 0,
                visitedIds = emptySet(),
                onReply = onReply,
                onEdit = onEdit,
                onDelete = onDelete
            )
        }

        if (editingComment != null || replyTarget != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingComment != null) {
                        readerString("label_editing_comment", "Editing comment")
                    } else {
                        readerString(
                            "label_replying_to",
                            "Replying to %1\$s",
                            replyTarget?.author?.ifBlank { DEFAULT_SHARED_PDF_COMMENT_AUTHOR }.orEmpty()
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = if (editingComment != null) onCancelEdit else onCancelReply) {
                    Text(readerString("action_cancel", "Cancel"))
                }
            }
        }

        SharedStableOutlinedTextField(
            value = commentAuthor,
            onValueChange = onCommentAuthorChange,
            label = { Text(readerString("author", "Author")) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            selectionKey = "comment-author-${editingCommentId ?: replyTargetId ?: "new"}"
        )

        Spacer(Modifier.height(8.dp))

        SharedStableOutlinedTextField(
            value = commentText,
            onValueChange = onCommentTextChange,
            placeholder = { Text(readerString("placeholder_add_comment", "Add a comment...")) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
            minLines = 3,
            maxLines = 4,
            shape = RoundedCornerShape(12.dp),
            selectionKey = "comment-text-${editingCommentId ?: replyTargetId ?: "new"}"
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onAddComment, enabled = commentText.isNotBlank()) {
                Text(
                    readerString(
                        if (editingComment != null) "action_save_comment" else "action_add_comment",
                        if (editingComment != null) "Save Comment" else "Add Comment"
                    )
                )
            }
        }
    }
}

@Composable
private fun DesktopPdfHighlightCommentThread(
    comments: List<SharedPdfAnnotationComment>,
    parentId: String?,
    depth: Int,
    visitedIds: Set<String>,
    onReply: (SharedPdfAnnotationComment) -> Unit,
    onEdit: (SharedPdfAnnotationComment) -> Unit,
    onDelete: (SharedPdfAnnotationComment) -> Unit
) {
    comments.pdfCommentChildren(parentId).forEach { comment ->
        if (comment.id in visitedIds) return@forEach
        DesktopPdfHighlightCommentItem(
            comment = comment,
            depth = depth,
            onReply = { onReply(comment) },
            onEdit = { onEdit(comment) },
            onDelete = { onDelete(comment) }
        )
        DesktopPdfHighlightCommentThread(
            comments = comments,
            parentId = comment.id,
            depth = depth + 1,
            visitedIds = visitedIds + comment.id,
            onReply = onReply,
            onEdit = onEdit,
            onDelete = onDelete
        )
    }
}

@Composable
private fun DesktopPdfHighlightCommentItem(
    comment: SharedPdfAnnotationComment,
    depth: Int,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val indentSize = (depth * 16).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentSize, top = 6.dp, bottom = 6.dp)
    ) {
        if (depth > 0) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author.ifBlank { DEFAULT_SHARED_PDF_COMMENT_AUTHOR },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val timestamp = comment.createdAt.formatDesktopPdfCommentTimestamp()
                if (timestamp.isNotBlank()) {
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = comment.contents,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row {
                TextButton(onClick = onReply) {
                    Text(readerString("action_reply", "Reply"))
                }
                TextButton(onClick = onEdit) {
                    Text(readerString("label_edit", "Edit"))
                }
                TextButton(onClick = onDelete) {
                    Text(readerString("action_delete", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun Long.formatDesktopPdfCommentTimestamp(): String {
    if (this <= 0L) return ""
    return runCatching {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(this))
    }.getOrDefault("")
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
                    readerString("desktop_embedded_pdf_comment", "Embedded PDF comment"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClose) {
                    Text(readerString("action_close", "Close"))
                }
            }
            Text(
                annotation.author.takeIf { it.isNotBlank() }?.let { author ->
                    readerString(
                        "desktop_pdf_page_author_format",
                        "Page %1\$d - %2\$s",
                        annotation.pageIndex + 1,
                        author
                    )
                } ?: readerString("pdf_page_short", "Page %1\$d", annotation.pageIndex + 1),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            DesktopPdfEmbeddedComment(
                author = annotation.author,
                contents = annotation.contents,
                depth = 0
            )
            DesktopPdfEmbeddedReplies(annotation.replies, depth = 1)
            TextButton(onClick = onCopy) {
                Text(readerString("action_copy_thread", "Copy thread"))
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
            author.ifBlank { readerString("unknown", "Unknown") },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            contents.ifBlank { readerString("desktop_no_comment", "No comment") },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun SharedPdfAnnotation.desktopLabel(): String {
    return when (kind) {
        PdfAnnotationKind.HIGHLIGHT -> readerString("label_highlight_color", "highlight")
        PdfAnnotationKind.INK -> tool.desktopLabel()
        PdfAnnotationKind.TEXT -> readerString("desktop_text_note_lowercase", "text note")
    }
}

@Composable
internal fun SharedPdfAnnotation.desktopSheetTitle(): String {
    return when (kind) {
        PdfAnnotationKind.HIGHLIGHT -> readerString("label_highlight_color", "Highlight")
        PdfAnnotationKind.INK -> readerString("desktop_annotation", "Annotation")
        PdfAnnotationKind.TEXT -> readerString("desktop_text_note", "Text note")
    }
}

@Composable
private fun PdfInkTool.desktopLabel(): String {
    return when (this) {
        PdfInkTool.PEN -> readerString("content_desc_pen", "Pen")
        PdfInkTool.FOUNTAIN_PEN -> readerString("desktop_fountain_pen", "Fountain pen")
        PdfInkTool.PENCIL -> readerString("desktop_pencil", "Pencil")
        PdfInkTool.HIGHLIGHTER -> readerString("content_desc_highlighter", "Highlighter")
        PdfInkTool.HIGHLIGHTER_ROUND -> readerString("desktop_round_highlighter", "Round highlighter")
        PdfInkTool.TEXT -> readerString("desktop_text_note", "Text note")
        PdfInkTool.ERASER -> readerString("content_desc_eraser", "Eraser")
        PdfInkTool.NONE -> readerString("label_none", "None")
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
